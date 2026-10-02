"""Two owned native instances. No process termination; only normal-close request files."""
from pathlib import Path
import argparse,json,os,time,subprocess,sys
parser=argparse.ArgumentParser();parser.add_argument('--manifest',type=Path,required=True);options=parser.parse_args()
ROOT=Path(os.environ.get('ALBUM_QA_WORKDIR',str(Path(__file__).resolve().parents[2]/'build/album-native-qa'))).resolve()
data=json.loads(options.manifest.read_text(encoding='utf-8'))
peer=Path(data['peer']).resolve();lab_a=Path(data['labA']).resolve();lab_b=Path(data['labB']).resolve();runner=str(Path(__file__).resolve().with_name('run_native_qa.py'))
if any(p.parent!=ROOT or not p.name.startswith('album-runtime-') for p in (lab_a,lab_b)):raise SystemExit('Own lab boundary required')
if peer.parent!=ROOT or not peer.name.startswith('album-peer-') or (peer/'pair-started.json').exists():raise SystemExit('Own new pair boundary required')
env=dict(os.environ);env['ALBUM_QA_WORKDIR']=str(ROOT)
def read(path):
 try:return json.loads(path.read_text(encoding='utf-8'))
 except (OSError,ValueError):return None
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def normal_close(lab,reason):write(lab/'request-normal-close.json',{'reason':reason})
def start(lab,args,role):
 log=(peer/('runner-'+role+'.log')).open('w',encoding='utf-8')
 process=subprocess.Popen(['python',runner,'--lab',str(lab),*args],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
 return process,log
write(peer/'pair-started.json',{'started':True,'noProcessKill':True})
b,blog=start(lab_b,data['argsB'],'B');a=None;alog=None;deadline=time.monotonic()+1440
last=None;reported_pids=set();a_exit_recorded=False;close_requested=False
try:
 while True:
  for role,lab in (('B',lab_b),('A',lab_a)):
   pid=read(lab/'pid.json')
   if pid and role not in reported_pids:
    print(json.dumps({'role':role,'nativePid':pid['pid'],'lab':str(lab)},ensure_ascii=False),flush=True);reported_pids.add(role)
  progress=read(lab_b/'album-progress.json')
  if progress:
   state=(progress.get('stage'),progress.get('cycle'),progress.get('phase'))
   if state!=last:print(json.dumps({'Bstage':state[0],'Bcycle':state[1],'Bphase':state[2],'Bfocused':progress.get('focused')},ensure_ascii=False),flush=True);last=state
  if a is None and (peer/'peer-b-ready.json').exists():
   if b.poll() is not None:raise RuntimeError('B exited before A could start')
   a,alog=start(lab_a,data['argsA'],'A');print('B native album ready; starting owned A with same guard candidate',flush=True)
  if a is not None and a.poll() is not None and not a_exit_recorded:
   result=read(lab_a/'run-summary.json') or {}
   proof={'success':a.returncode==0 and result.get('success',False),'cleanExit':result.get('cleanExit',False),'exitCode':result.get('exitCode'),'ownedHelperResidue':len(result.get('ownedHelperResidue',[{}])),'nativeChecks':len(result.get('checks',[])),'actualNativeAResult':str(lab_a/'run-summary.json')}
   write(peer/'peer-a-exit.json',proof);a_exit_recorded=True;print(json.dumps({'AexitProof':proof}),flush=True)
   if not proof['success'] or not proof['cleanExit']:normal_close(lab_b,'peer A failed real native guard/normal exit; stop pair normally')
  if b.poll() is not None:
   if a is not None and a.poll() is None:normal_close(lab_a,'peer B ended; normal pair cleanup only')
   break
  if not close_requested and time.monotonic()>deadline-90:
   close_requested=True;normal_close(lab_b,'bounded pair deadline')
   if a is not None and a.poll() is None:normal_close(lab_a,'bounded pair deadline')
  if time.monotonic()>deadline:raise RuntimeError('Pair deadline; native processes need normal close; no kill performed')
  time.sleep(1)
 if a is not None:
  # B finishing first is a failure unless A already exited. No forced cleanup fallback.
  try:a.wait(timeout=45)
  except subprocess.TimeoutExpired:raise RuntimeError('Owned A normal close pending; no kill performed')
 sa=read(lab_a/'run-summary.json') or {};sb=read(lab_b/'run-summary.json') or {}
 success=bool(sa.get('success') and sa.get('cleanExit') and sb.get('success') and sb.get('cleanExit') and sb.get('peerExitSurvival') and not sa.get('ownedHelperResidue') and not sb.get('ownedHelperResidue'))
 summary={'success':success,'labA':str(lab_a),'labB':str(lab_b),'Achecks':len(sa.get('checks',[])),'Bchecks':len(sb.get('checks',[])),'Aexit':sa.get('exitCode'),'Bexit':sb.get('exitCode'),'BpeerSurvival':sb.get('peerExitSurvival'),'BreopenCycles':sb.get('reopenCycles'),'AhelpersRemaining':sa.get('ownedHelperResidue'),'BhelpersRemaining':sb.get('ownedHelperResidue'),'normalOnly':True}
 write(peer/'pair-summary.json',summary);print(json.dumps(summary,ensure_ascii=False),flush=True)
 if not success:raise SystemExit(1)
except Exception as error:
 normal_close(lab_b,str(error))
 if a is not None and a.poll() is None:normal_close(lab_a,str(error))
 write(peer/'pair-monitor-error.json',{'error':str(error),'noForceTermination':True});raise
finally:
 blog.close()
 if alog:alog.close()

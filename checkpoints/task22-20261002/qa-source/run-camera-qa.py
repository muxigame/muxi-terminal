"""131 task22-owned clone/preparation and bounded native-camera launch. Never touches another QA task/lab."""
from pathlib import Path
import argparse,ctypes,datetime,hashlib,json,os,shutil,subprocess,sys,time,uuid
from qa_fml_config import configure_file
from shared_progress_io import read_shared_text
ROOT=Path(__file__).resolve().parent
ALLOWED=Path(r'C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002')
TEMPLATE_BASE=Path(r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001')
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,data):p.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def under(p,root):return os.path.commonpath([str(p.resolve()),str(root.resolve())]).lower()==str(root.resolve()).lower()
def check(value,text):
    if not value:raise SystemExit(text)
def refuse_own_live_client():
    active=ROOT/'active.json'
    if not active.exists():return
    previous=json.loads(active.read_text(encoding='utf-8'));pid=previous.get('privatePid')
    if not pid:return
    # Same established kernel32 inspection as task14, scoped ONLY to task22's own receipt.
    kernel=ctypes.windll.kernel32;kernel.OpenProcess.restype=ctypes.c_void_p
    kernel.GetExitCodeProcess.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)]
    kernel.QueryFullProcessImageNameW.argtypes=[ctypes.c_void_p,ctypes.c_ulong,ctypes.c_wchar_p,ctypes.POINTER(ctypes.c_ulong)]
    kernel.CloseHandle.argtypes=[ctypes.c_void_p]
    handle=kernel.OpenProcess(0x1000,False,int(pid))
    if not handle:return
    try:
        code=ctypes.c_ulong();name=ctypes.create_unicode_buffer(32768);size=ctypes.c_ulong(len(name))
        check(kernel.GetExitCodeProcess(handle,ctypes.byref(code)),'Cannot verify previous own QA PID; no duplicate launch')
        if code.value==259:
            check(kernel.QueryFullProcessImageNameW(handle,0,name,ctypes.byref(size)),'Cannot verify previous own process image; no launch')
            check(Path(name.value).name.lower() not in ('java.exe','javaw.exe'),'Previous OWN camera QA Java PID '+str(pid)+' is still running; normally close it before retrying. Other task clients are unaffected.')
    finally:kernel.CloseHandle(handle)
def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--prepare',type=Path);parser.add_argument('--run',type=Path);parser.add_argument('--assigned-slot',default='')
    args=parser.parse_args();check(os.environ.get('COMPUTERNAME','').upper()=='JBC_FCRL','131 only; no client launched')
    check(ROOT.resolve()==ALLOWED.resolve(),'Run only in task22 dedicated root')
    pins=json.loads((ROOT/'candidate-pins.json').read_text(encoding='utf-8'))
    product=ROOT/pins['product'];qa=ROOT/pins['qa'];check(digest(product)==pins['productSha256'] and digest(qa)==pins['qaSha256'],'Candidate input changed')
    session=ctypes.c_ulong();check(ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session)),'Session ID not available')
    if args.prepare:
        template=args.prepare.resolve();check(under(template,TEMPLATE_BASE),'Template outside reviewed task14 isolated QA root')
        original=json.loads((template/'inputs.json').read_text(encoding='utf-8'));check(original.get('mode')=='full','Only full-pack isolated template allowed')
        lab=ROOT/('run-'+datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]);lab.mkdir()
        check(under(lab,ROOT),'Private lab boundary invalid')
        # Copy stable pack/runtime inputs only. No prior worlds, browser cache, screenshots or personal photo library.
        for name in ['natives','config','shaderpacks','resourcepacks','defaultconfigs','kubejs','tacz']:
            source=template/name
            if source.is_dir():shutil.copytree(source,lab/name)
        def skip(directory,names):
            return [n for n in names if n.startswith('muxi-terminal-') and n.endswith('.jar') or n in ('terminal-qa-only.jar','tcp-selector-qa-only.jar')]
        shutil.copytree(template/'mods',lab/'mods',ignore=skip)
        shutil.copy2(product,lab/'mods'/product.name);shutil.copy2(qa,lab/'mods'/qa.name)
        configure_file(lab/'config/fml.toml')
        write(lab/'config/muxi-game-core.json',{'schema':1,'features':{'identity':{'enabled':False},'login':{'enabled':False}}})
        (lab/'options.txt').write_text('lang:zh_cn\nguiScale:2\nmaxFps:45\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:3\nsimulationDistance:5\ngraphicsMode:0\n',encoding='utf-8')
        launch=(template/'launch.args').read_text(encoding='utf-8').replace(str(template).replace('\\','/'),str(lab).replace('\\','/')).replace(str(template),str(lab))
        launch=launch.replace('-Dqa.mode=full','-Dqa.mode=task22-camera-native')
        check(str(template).replace('\\','/') not in launch and str(template) not in launch,'Old private game-directory reference survived')
        (lab/'launch.args').write_text(launch,encoding='utf-8')
        receipt={'lab':str(lab),'prepared':True,'clientStarted':False,'template':str(template),'templateTerminalSha256':original.get('terminalSha256'),'productSha256':pins['productSha256'],'qaSha256':pins['qaSha256'],'sessionPrepared':session.value,'productionChanged':False,'browserCachesCopied':False,'photosCopied':False,'mods':[{'name':p.name,'sha256':digest(p)} for p in sorted((lab/'mods').glob('*.jar'))]}
        write(lab/'inputs.json',receipt);write(ROOT/'prepared.json',receipt);print(json.dumps(receipt,ensure_ascii=False));return
    check(args.run is not None,'Specify --prepare or --run')
    check(session.value==2 and args.assigned_slot=='task22-camera-native','Assigned 131 Session2 camera window slot required; no client launched')
    refuse_own_live_client()
    lab=args.run.resolve();check(under(lab,ROOT) and lab.name.startswith('run-'),'Private lab boundary invalid')
    receipt=json.loads((lab/'inputs.json').read_text(encoding='utf-8'));check(receipt['prepared'] and not receipt['clientStarted'],'Lab already launched; no duplicate client')
    check(digest(lab/'mods'/product.name)==pins['productSha256'] and digest(lab/'mods'/qa.name)==pins['qaSha256'],'Prepared candidate differs')
    check((JDK/'bin/java.exe').is_file(),'Reviewed JDK unavailable')
    with (lab/'launch-reservation.json').open('x',encoding='utf-8') as reservation:
        json.dump({'attemptUtc':datetime.datetime.utcnow().isoformat()+'Z','session':session.value,'productSha256':pins['productSha256']},reservation)
    env=dict(os.environ);env.pop('MUXI_TERMINAL_GAME_CREDENTIAL',None);env.pop('JAVA_TOOL_OPTIONS',None)
    with (lab/'boot.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([str(JDK/'bin/java.exe'),'@'+str(lab/'launch.args')],cwd=lab,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        receipt.update(clientStarted=True,privatePid=process.pid,session=session.value,assignedSlot=args.assigned_slot,startedUtc=datetime.datetime.utcnow().isoformat()+'Z');write(lab/'inputs.json',receipt);write(ROOT/'active.json',receipt)
        print(json.dumps({'launched':True,'privatePid':process.pid,'lab':str(lab),'session':session.value}),flush=True)
        started=time.monotonic();last_progress='';last_sample_log=0
        while process.poll() is None:
            if time.monotonic()-started>1140:
                write(lab/'exit.json',{'completed':False,'timeout':True,'processStillRunning':True,'privatePid':process.pid,'normalCloseRequired':True});raise SystemExit('Private camera QA exceeded deadline; no force kill/relaunch. Normally close ONLY this owned PID.')
            elapsed=time.monotonic()-started
            try:
                progress=read_shared_text(lab/'camera-progress.json')
                if progress!=last_progress and elapsed-last_sample_log>=5:
                    print('QA_PROGRESS='+progress,flush=True);last_progress=progress;last_sample_log=elapsed
            except OSError as unavailable:
                if elapsed-last_sample_log>=10:
                    print('QA_PROGRESS_SAMPLE_UNAVAILABLE='+str(unavailable),flush=True);last_sample_log=elapsed
            time.sleep(1)
        code=process.returncode;write(lab/'exit.json',{'exitCode':code,'timeout':False})
    try:result=json.loads(read_shared_text(lab/'camera-result.json'))
    except (OSError,ValueError) as unavailable:result={'completed':False,'error':'Camera result unavailable: '+str(unavailable)}
    result.update(exitCode=code,session=session.value,productSha256=pins['productSha256'],qaSha256=pins['qaSha256'],lab=str(lab),productionChanged=False)
    result['artifactSha256']={p.name:digest(p) for p in lab.glob('*.png')};write(lab/'summary.json',result);print(json.dumps(result,ensure_ascii=False));check(code==0 and result.get('completed'),'Actual native camera acceptance incomplete')
if __name__=='__main__':main()

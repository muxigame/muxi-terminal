"""Prepare a fresh private QA attempt ONLY after the exact previous owned client exited normally. No launch."""
from pathlib import Path
import base64,hashlib,json,subprocess,sys,zipfile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(__file__).resolve().parent;qa=root/'camera-native-qa';evidence=root/'camera-repair/evidence'
old=json.loads((qa/'candidate-pins.json').read_text(encoding='utf-8'))
meta=json.loads((root/'camera-native-worktree/build/release.json').read_text(encoding='utf-8'))
product=root/'camera-native-worktree/build/libs'/meta['artifact'];test=qa/'build/task22-camera-native-qa-only.jar'
new={'product':product.name,'productSha256':hashlib.sha256(product.read_bytes()).hexdigest(),'qa':test.name,'qaSha256':hashlib.sha256(test.read_bytes()).hexdigest()}
bundle=qa/'camera-native-retry-inputs.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
 z.write(product,product.name);z.write(test,test.name);z.writestr('candidate-pins.json',json.dumps(new,indent=2))
 for name in ['run-camera-qa.py','shared_progress_io.py','qa_fml_config.py']:z.write(qa/name,name)
sha=hashlib.sha256(bundle.read_bytes()).hexdigest();remotezip='C:/Users/ranzh/Documents/Codex/camera-native-task22-20261002/retry-'+sha[:12]+'.zip'
flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8'];host='jbc-1@192.168.110.131'
subprocess.run(['scp',*flags,str(bundle),host+':'+remotezip],capture_output=True,timeout=40,check=True)
remote=r'''
from pathlib import Path
import ctypes,json,hashlib,os,shutil,subprocess,sys,zipfile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(r'C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002');sys.path.insert(0,str(root));from shared_progress_io import read_shared_text
if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL':raise SystemExit('Wrong host')
active=json.loads(read_shared_text(root/'active.json'));previous=root/'run-20261002-045106-15b18303'
if active['privatePid']!=15204 or Path(active['lab'])!=previous:raise SystemExit('Previous owner PID/lab changed')
exit=json.loads(read_shared_text(previous/'exit.json'))
if exit.get('exitCode')!=0 or exit.get('timeout'):raise SystemExit('Prior client did not exit normally')
k=ctypes.WinDLL('kernel32',use_last_error=True);k.OpenProcess.restype=ctypes.c_void_p;k.GetExitCodeProcess.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)];k.CloseHandle.argtypes=[ctypes.c_void_p]
handle=k.OpenProcess(0x1000,False,15204)
if handle:
 try:
  code=ctypes.c_ulong()
  if not k.GetExitCodeProcess(handle,ctypes.byref(code)) or code.value==259:raise SystemExit('Previous owned PID still live; no input change or retry')
 finally:k.CloseHandle(handle)
elif ctypes.get_last_error()!=87:raise SystemExit('Cannot verify previous owned PID: '+str(ctypes.get_last_error()))
before=json.loads(read_shared_text(root/'candidate-pins.json'))
if before['productSha256']!='__OLD_PRODUCT__' or before['qaSha256']!='__OLD_QA__':raise SystemExit('Own pinned inputs changed')
bundle=Path(r'__ZIP__')
if hashlib.sha256(bundle.read_bytes()).hexdigest()!='__BUNDLE__':raise SystemExit('Transport hash changed')
stage=root/'retry-inputs-__TAG__';stage.mkdir()
with zipfile.ZipFile(bundle) as z:
 for name in z.namelist():
  dest=(stage/name).resolve()
  if stage.resolve() not in dest.parents:raise SystemExit('Archive escapes private stage')
  dest.write_bytes(z.read(name))
after=json.loads((stage/'candidate-pins.json').read_text(encoding='utf-8'))
for name,key in [(after['product'],'productSha256'),(after['qa'],'qaSha256')]:
 if hashlib.sha256((stage/name).read_bytes()).hexdigest()!=after[key]:raise SystemExit('Candidate checksum changed')
for p in stage.iterdir():shutil.copy2(p,root/p.name)
template=r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001\appfn-v1\qa-runtime\full-20261002-042133-f2e209bb'
run=subprocess.run([sys.executable,'-B',str(root/'run-camera-qa.py'),'--prepare',template],capture_output=True,timeout=180,check=True)
prepared=json.loads(run.stdout.decode('utf-8'));lab=Path(prepared['lab'])
if root not in lab.parents or prepared['clientStarted']:raise SystemExit('Unexpected private lab')
fixed=r"""# Task14 alone triggers its existing Session2 entry. No scheduled task or focus changes.
$ErrorActionPreference='Stop'
$root='C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002'
$lab='__LAB__'
if($env:COMPUTERNAME -ne 'JBC_FCRL' -or (Get-Process -Id $PID).SessionId -ne 2){throw 'Assigned 131 Session2 required'}
& 'C:\Python38\python.exe' -B "$root\run-camera-qa.py" --run $lab --assigned-slot task22-camera-native *> "$lab\desktop-entry.log"
exit $LASTEXITCODE
""".replace('__LAB__',str(lab))
(root/'NativeCameraSession2Fixed.ps1').write_text(fixed,encoding='utf-8')
result={'lab':str(lab),'clientStarted':False,'prepared':True,'previousPid':15204,'previousExitCode':0,'previousPidNotRunning':True,'previousLabPreserved':True,'productSha256':after['productSha256'],'qaSha256':after['qaSha256'],'entry':str(root/'NativeCameraSession2Fixed.ps1'),'productionChanged':False,'otherTaskTouched':False,'newTaskRegistered':False,'fixedScript':fixed}
(root/'retry-prepared.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,ensure_ascii=False))
'''
for token,value in {'__OLD_PRODUCT__':old['productSha256'],'__OLD_QA__':old['qaSha256'],'__ZIP__':remotezip.replace('/',chr(92)),'__BUNDLE__':sha,'__TAG__':sha[:12]}.items():remote=remote.replace(token,value)
remote_source=qa/'retry-prepare-131.py';remote_source.write_text(remote,encoding='utf-8')
source_sha=hashlib.sha256(remote_source.read_bytes()).hexdigest();remote_py='C:/Users/ranzh/Documents/Codex/camera-native-task22-20261002/prepare-retry-'+source_sha[:12]+'.py'
subprocess.run(['scp',*flags,str(remote_source),host+':'+remote_py],capture_output=True,timeout=40,check=True)
remote_ps_path=remote_py.replace('/',chr(92))
ps="$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.Encoding]::UTF8;if((Get-FileHash -LiteralPath '"+remote_ps_path+"' -Algorithm SHA256).Hash.ToLowerInvariant() -ne '"+source_sha+"'){throw 'Prepared script transport changed'};& 'C:\\Python38\\python.exe' -B '"+remote_ps_path+"'"
run=subprocess.run(['ssh',*flags,host,'powershell','-NoProfile','-NonInteractive','-EncodedCommand',base64.b64encode(ps.encode('utf-16le')).decode()],capture_output=True,timeout=210)
(evidence/'131-native-retry-prepare.log').write_bytes(run.stdout+b'\n'+run.stderr)
if run.returncode:print(run.stderr.decode('utf-8',errors='replace'));raise SystemExit(run.returncode)
data=json.loads(run.stdout.decode('utf-8-sig'));(evidence/'131-native-retry-prepared.json').write_text(json.dumps(data,indent=2),encoding='utf-8');(qa/'candidate-pins.json').write_text(json.dumps(new,indent=2),encoding='utf-8');(qa/'NativeCameraSession2Fixed.ps1').write_text(data['fixedScript'],encoding='utf-8');print(json.dumps({k:v for k,v in data.items() if k!='fixedScript'},indent=2))

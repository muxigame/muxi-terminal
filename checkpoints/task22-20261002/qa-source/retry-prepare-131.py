
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
if before['productSha256']!='12486281f751cf90c4392c2ad71f009f680efb77bae2a13bc6b9a7dafdfa3516' or before['qaSha256']!='9f70a49bfaba6b2d1b84cf93a3840b0629c85f1ba4336d6b8ddd77b57a35b92a':raise SystemExit('Own pinned inputs changed')
bundle=Path(r'C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002\retry-1cdb22a6a606.zip')
if hashlib.sha256(bundle.read_bytes()).hexdigest()!='1cdb22a6a6069564edf6c2b62e985b37298af610808f91866bdc1a46240ba289':raise SystemExit('Transport hash changed')
stage=root/'retry-inputs-1cdb22a6a606';stage.mkdir()
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

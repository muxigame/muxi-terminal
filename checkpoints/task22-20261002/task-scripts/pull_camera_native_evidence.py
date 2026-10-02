from pathlib import Path
import base64,hashlib,json,subprocess,sys,zipfile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(__file__).resolve().parent;out=root/'camera-repair/evidence/131-native-real';out.mkdir(exist_ok=True)
remote=r'''
from pathlib import Path
import zipfile,hashlib,json,sys,ctypes,os,msvcrt,time
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(r'C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002');lab=root/'run-20261002-045106-15b18303'
sys.path.insert(0,str(root));from shared_progress_io import read_shared_text
def shared_bytes(p):
 k=ctypes.WinDLL('kernel32',use_last_error=True)
 k.CreateFileW.argtypes=[ctypes.c_wchar_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_void_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_void_p]
 k.CreateFileW.restype=ctypes.c_void_p;k.CloseHandle.argtypes=[ctypes.c_void_p]
 for attempt in range(20):
  handle=k.CreateFileW(str(p),0x80000000,7,None,3,0x80,None)
  if handle==ctypes.c_void_p(-1).value:
   error=ctypes.get_last_error()
   if error in (2,3,5,32) and attempt<19:time.sleep(.005);continue
   raise ctypes.WinError(error)
  try:fd=msvcrt.open_osfhandle(handle,os.O_RDONLY|os.O_BINARY)
  except BaseException:k.CloseHandle(handle);raise
  with os.fdopen(fd,'rb') as f:return f.read()
active=json.loads(read_shared_text(root/'active.json'))
if active.get('privatePid')!=15204 or Path(active['lab'])!=lab:raise SystemExit('Unexpected own QA PID/lab; no collection')
archive=lab/'task22-native-real-evidence.zip'
files=list(lab.glob('*.png'))+[lab/name for name in ['inputs.json','camera-progress.json','camera-result.json','exit.json','summary.json','boot.log','desktop-entry.log','logs/latest.log']]
files=[p for p in files if p.is_file()]
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for p in files:
  # Open with sharing so collection cannot block the actual test's progress updates.
  data=shared_bytes(p);z.writestr(p.relative_to(lab).as_posix(),data)
print(json.dumps({'archive':str(archive),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'files':[p.relative_to(lab).as_posix() for p in files],'privatePid':15204}))
'''
ps="$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.Encoding]::UTF8;& 'C:\\Python38\\python.exe' -B -c '"+remote.replace("'","''")+"'"
flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8'];host='jbc-1@192.168.110.131'
run=subprocess.run(['ssh',*flags,host,'powershell','-NoProfile','-NonInteractive','-EncodedCommand',base64.b64encode(ps.encode('utf-16le')).decode()],capture_output=True,timeout=40,check=True)
receipt=json.loads(run.stdout.decode('utf-8-sig'));archive=out/'task22-native-real-evidence.zip'
subprocess.run(['scp',*flags,host+':'+receipt['archive'].replace(chr(92),'/'),str(archive)],capture_output=True,timeout=40,check=True)
assert hashlib.sha256(archive.read_bytes()).hexdigest()==receipt['sha256']
with zipfile.ZipFile(archive) as z:
 for name in z.namelist():
  dest=(out/name).resolve();assert dest.is_relative_to(out.resolve());dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(name))
(out/'collection-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt,indent=2))

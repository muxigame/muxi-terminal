from pathlib import Path
import json,zipfile,hashlib,ctypes,datetime
root=Path(r'C:\Users\ranzh\Documents\Codex\task6-native-map-qa-20261002').resolve()
assert (root/'task6-owner.txt').is_file()
kernel=ctypes.windll.kernel32;kernel.OpenProcess.restype=ctypes.c_void_p
kernel.GetExitCodeProcess.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)]
kernel.CloseHandle.argtypes=[ctypes.c_void_p]
handle=kernel.OpenProcess(0x1000,False,34736)
if handle:
 try:
  status=ctypes.c_ulong()
  if not kernel.GetExitCodeProcess(handle,ctypes.byref(status)) or status.value==259:raise SystemExit('Previous own PID is active; refusing kit replacement')
 finally:kernel.CloseHandle(handle)
archive=root/'qa-kit-stage0-fix-v3.zip'
expected='fa4843f56e8c13621ad4997a4247d6cfb16697c2a49e0a896e1f0bdf74f9ba6d'
assert hashlib.sha256(archive.read_bytes()).hexdigest()==expected
with zipfile.ZipFile(archive) as z:
 for entry in z.namelist():
  assert root in (root/entry).resolve().parents
  assert entry.startswith(('desktop/','inputs/','java/')) or entry in ['qa-input-lock.json','build_kit.py','RUN-COORDINATION.txt','test_normal_close_monitor.py']
 z.extractall(root)
lock=json.loads((root/'qa-input-lock.json').read_text(encoding='utf-8'))
for entry in lock['files']:assert hashlib.sha256((root/entry['path']).read_bytes()).hexdigest()==entry['sha256']
receipt={'owner':'task6','kitSha256':expected,'preparedUtc':datetime.datetime.utcnow().isoformat()+'Z','clientStarted':False,'oldPid34736Cleared':True,'sharedTaskChanged':False,'productPatchesChanged':False,'nativeQaComplete':False,'nextEntry':str(root/'desktop/run_task6_native_map.ps1'),'arguments':'-Mode all','ownerMustReviewNativeResults':True}
(root/'qa-prepared-receipt-stage0-v3.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
print(json.dumps(receipt))

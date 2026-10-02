from pathlib import Path
import json,zipfile,hashlib,datetime,ctypes
root=Path(r'C:\Users\ranzh\Documents\Codex\task6-native-map-qa-20261002').resolve()
lab=root/'qa-runtime/all-20261002-052451-d188b089'
assert root in lab.resolve().parents
kernel=ctypes.windll.kernel32;kernel.OpenProcess.restype=ctypes.c_void_p
kernel.GetExitCodeProcess.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)]
kernel.CloseHandle.argtypes=[ctypes.c_void_p]
handle=kernel.OpenProcess(0x1000,False,34736)
alive=False
if handle:
 try:
  status=ctypes.c_ulong();alive=not kernel.GetExitCodeProcess(handle,ctypes.byref(status)) or status.value==259
 finally:kernel.CloseHandle(handle)
assert not alive,'Own failed process still active; cannot claim slot released'
native=json.loads((lab/'native-map-result.json').read_text(encoding='utf-8'))
assert native.get('failedStage')==0 and not native.get('success')
log=(lab/'logs/latest.log').read_text(encoding='utf-8',errors='replace')
assert 'Saving players' in log and 'Saving chunks' in log and 'Stopping!' in log
paths=[p for p in lab.iterdir() if p.is_file() and p.suffix in ['.json','.txt','.log','.png']]
paths.extend([lab/'logs/latest.log',lab/'logs/debug.log'])
paths=[p for p in paths if p.is_file()]
receipt={'owner':'task6','pid':34736,'session':2,'mode':'all','lab':str(lab),
 'recordedUtc':datetime.datetime.utcnow().isoformat()+'Z',
 'result':'Failed at fixture stage 0; optional native white sharestone placement returned empty',
 'secondaryFailure':'Synchronous disconnect in ClientTick.Post delayed normal cleanup',
 'nativeUiTestsExecuted':False,'nativeMapFunctionTestsExecuted':False,'XAcceptance':False,'mapAcceptance':False,
 'normalCleanupObserved':True,'pidCleared':True,'exitCode':None,
 'exitCodeNote':'Original runner already recorded timeout and ended monitoring; no exit code inferred',
 'monitorTimeoutPreserved':True,'minecraftNormalFullRunAccepted':False,'forceOsTermination':False,
 'cleanup':'Owner/PID/world guarded MinecraftServer.halt(false); vanilla saving players/chunks; Minecraft Stopping! 05:42:21 UTC',
 'activeQaKitSha256':'9e70846b9099541cefb40c26d4e3d6106b71d7313cb08d2184f28ee69a97eb69',
 'productPatchesChanged':False}
(lab/'SECOND-RUN-OUTCOME.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');paths.append(lab/'SECOND-RUN-OUTCOME.json')
receipt['files']=[{'path':p.relative_to(root).as_posix(),'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(set(paths))]
out=root/'task6-native-131-second-run-evidence.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
 archive.writestr('SECOND-RUN-MANIFEST.json',json.dumps(receipt,indent=2))
 for p in sorted(set(paths)):archive.write(p,p.relative_to(root).as_posix())
print(json.dumps({'archive':str(out),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'size':out.stat().st_size,'pidCleared':True,'nativeQaComplete':False}))

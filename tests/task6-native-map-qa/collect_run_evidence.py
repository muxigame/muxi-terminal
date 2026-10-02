from pathlib import Path
import json,zipfile,hashlib,datetime
root=Path(r'C:\Users\ranzh\Documents\Codex\task6-native-map-qa-20261002').resolve()
lab=root/'qa-runtime/all-20261002-050954-5de742e6'
if root not in lab.resolve().parents:raise SystemExit('Unexpected lab')
summary=json.loads((lab/'run-summary.json').read_text(encoding='utf-8'))
if summary['exitCode']!=2 or summary['timeout'] or summary['cleanExit']:raise SystemExit('Unexpected first failed bootstrap outcome')
paths=[p for p in lab.iterdir() if p.is_file() and p.suffix in ['.json','.txt','.log','.png']]
preflight=json.loads((root/'qa-runtime/last-preflight.json').read_text(encoding='utf-8'))
preflight_lab=Path(preflight['lab']).resolve()
if (root/'qa-runtime') not in preflight_lab.parents:raise SystemExit('Unexpected preflight directory')
paths.extend(p for p in preflight_lab.iterdir() if p.is_file() and p.suffix in ['.json','.log','.png'])
paths.extend((root/'qa-runtime').glob('desktop-entry*'))
paths.append(root/'qa-runtime/last-preflight.json')
out=root/'task6-native-131-first-run-evidence.zip'
manifest={'owner':'task6','pid':17272,'session':2,'mode':'all','lab':str(lab),'result':'failed pre-window Minecraft bootstrap OSHI Win32_Processor WMI stall','nativeFunctionTestsExecuted':False,'nativeUiTestsExecuted':False,'minecraftNormalExitAccepted':False,'bootstrapJvmShutdownHookRan':True,'exitCode':2,'timeout':False,'forceOsTermination':False,'preflightHardwareOnlyPassed':preflight.get('success',False),'recordedUtc':datetime.datetime.utcnow().isoformat()+'Z','files':[{'path':p.relative_to(root).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':p.stat().st_size} for p in sorted(set(paths))]}
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
 archive.writestr('FIRST-RUN-MANIFEST.json',json.dumps(manifest,indent=2))
 for p in sorted(set(paths)):archive.write(p,p.relative_to(root).as_posix())
print(json.dumps({'archive':str(out),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'size':out.stat().st_size,'result':manifest['result']}))

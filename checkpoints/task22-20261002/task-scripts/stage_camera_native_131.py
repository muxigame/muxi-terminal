"""Stage dedicated task22 candidate and PREPARE only; no task registration, process launch or focus action."""
from pathlib import Path
import base64,hashlib,json,shutil,subprocess,sys,zipfile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(__file__).resolve().parent;qa=root/'camera-native-qa';evidence=root/'camera-repair/evidence';evidence.mkdir(parents=True,exist_ok=True)
meta=json.loads((root/'camera-native-worktree/build/release.json').read_text());product=root/'camera-native-worktree/build/libs'/meta['artifact'];test=qa/'build/task22-camera-native-qa-only.jar'
pins={'product':product.name,'productSha256':hashlib.sha256(product.read_bytes()).hexdigest(),'qa':test.name,'qaSha256':hashlib.sha256(test.read_bytes()).hexdigest()}
(qa/'candidate-pins.json').write_text(json.dumps(pins,indent=2),encoding='utf-8')
bundle=qa/'camera-native-inputs.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    z.write(product,product.name);z.write(test,test.name)
    for name in ['candidate-pins.json','run-camera-qa.py','qa_fml_config.py','NativeCameraEntry.ps1','RegisterAndRunOneShot.ps1']:z.write(qa/name,name)
sha=hashlib.sha256(bundle.read_bytes()).hexdigest();flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8'];host='jbc-1@192.168.110.131'
remoteZip='C:/Users/ranzh/Documents/Codex/camera-native-task22-20261002-input-'+sha[:12]+'.zip'
subprocess.run(['scp',*flags,str(bundle),host+':'+remoteZip],capture_output=True,timeout=30,check=True)
script=r'''
$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
if($env:COMPUTERNAME -ne 'JBC_FCRL'){throw 'Wrong host'}
$root='C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002'
$zip='__ZIP__'
if(Test-Path -LiteralPath $root){throw 'Dedicated task22 stage already exists; preserve it'}
if((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne '__SHA__'){throw 'Input transport hash differs'}
Expand-Archive -LiteralPath $zip -DestinationPath $root
$template='C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001\appfn-v1\qa-runtime\full-20261002-042133-f2e209bb'
& 'C:\Python38\python.exe' -B "$root\run-camera-qa.py" --prepare $template
if($LASTEXITCODE -ne 0){throw 'Private preparation incomplete; no client launched'}
'''.replace('__ZIP__',remoteZip.replace('/',chr(92))).replace('__SHA__',sha)
result=subprocess.run(['ssh',*flags,host,'powershell','-NoProfile','-NonInteractive','-EncodedCommand',base64.b64encode(script.encode('utf-16le')).decode()],capture_output=True,timeout=240)
(evidence/'131-native-prepare.log').write_bytes(result.stdout+b'\n'+result.stderr)
if result.returncode:print('131 preparation failed; inspect camera-repair/evidence/131-native-prepare.log. No launch requested.');raise SystemExit(result.returncode)
data=json.loads(result.stdout.decode('utf-8-sig').splitlines()[-1]);(evidence/'131-native-prepared.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in data.items() if k!='mods'},ensure_ascii=False,indent=2));print('Copied mod count:',len(data.get('mods',[])))

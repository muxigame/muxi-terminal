"""Update task22's NEVER-LAUNCHED private candidate inputs with exact old/new hash guards."""
from pathlib import Path
import base64,hashlib,json,subprocess,sys,zipfile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(__file__).resolve().parent;qa=root/'camera-native-qa';evidence=root/'camera-repair/evidence'
old=json.loads((qa/'candidate-pins.json').read_text(encoding='utf-8'));meta=json.loads((root/'camera-native-worktree/build/release.json').read_text(encoding='utf-8'));product=root/'camera-native-worktree/build/libs'/meta['artifact'];test=qa/'build/task22-camera-native-qa-only.jar'
new={'product':product.name,'productSha256':hashlib.sha256(product.read_bytes()).hexdigest(),'qa':test.name,'qaSha256':hashlib.sha256(test.read_bytes()).hexdigest()}
lab=json.loads((evidence/'131-native-prepared.json').read_text(encoding='utf-8'))['lab'];flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8'];host='jbc-1@192.168.110.131'
bundle=qa/'camera-native-update.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    z.write(product,product.name);z.write(test,test.name)
    z.writestr('candidate-pins.json',json.dumps(new,indent=2))
    for name in ['NativeCameraSession2Fixed.ps1','shared_progress_io.py','run-camera-qa.py']:z.write(qa/name,name)
sha=hashlib.sha256(bundle.read_bytes()).hexdigest();remote='C:/Users/ranzh/Documents/Codex/camera-native-task22-20261002/update-'+sha[:12]+'.zip'
subprocess.run(['scp',*flags,str(bundle),host+':'+remote],capture_output=True,timeout=30,check=True)
script=r'''
$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
if($env:COMPUTERNAME -ne 'JBC_FCRL'){throw 'Wrong host'}
$root='C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002';$lab='__LAB__';$zip='__ZIP__'
$inputs=[IO.File]::ReadAllText("$lab\inputs.json")|ConvertFrom-Json
if($inputs.clientStarted -or !$inputs.prepared){throw 'Only never-launched own input may be updated'}
$before=[IO.File]::ReadAllText("$root\candidate-pins.json")|ConvertFrom-Json
if($before.productSha256 -ne '__OLD_PRODUCT__' -or $before.qaSha256 -ne '__OLD_QA__'){throw 'Own manifest unexpectedly changed'}
foreach($name in @($before.product,$before.qa)){
 $expected=if($name -eq $before.product){$before.productSha256}else{$before.qaSha256}
 if((Get-FileHash -LiteralPath (Join-Path "$lab\mods" $name) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected){throw 'Prepared own mod unexpectedly changed'}
}
if((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne '__BUNDLE__'){throw 'Update transport differs'}
Expand-Archive -LiteralPath $zip -DestinationPath "$root\update-__TAG__"
$stage="$root\update-__TAG__";$after=[IO.File]::ReadAllText("$stage\candidate-pins.json")|ConvertFrom-Json
foreach($name in @($after.product,$after.qa)){
 $expected=if($name -eq $after.product){$after.productSha256}else{$after.qaSha256}
 if((Get-FileHash -LiteralPath (Join-Path $stage $name) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected){throw 'Unpacked own mod differs'}
 Copy-Item -LiteralPath (Join-Path $stage $name) -Destination (Join-Path $root $name) -Force
 Copy-Item -LiteralPath (Join-Path $stage $name) -Destination (Join-Path "$lab\mods" $name) -Force
}
foreach($name in @('candidate-pins.json','NativeCameraSession2Fixed.ps1','shared_progress_io.py','run-camera-qa.py')){Copy-Item -LiteralPath (Join-Path $stage $name) -Destination (Join-Path $root $name) -Force}
$inputs.productSha256=$after.productSha256;$inputs.qaSha256=$after.qaSha256
foreach($entry in $inputs.mods){if($entry.name -eq $after.product){$entry.sha256=$after.productSha256};if($entry.name -eq $after.qa){$entry.sha256=$after.qaSha256}}
[IO.File]::WriteAllText("$lab\inputs.json",($inputs|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllText("$root\prepared.json",($inputs|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
@{lab=$lab;clientStarted=$false;prepared=$true;productSha256=$after.productSha256;qaSha256=$after.qaSha256;otherTaskTouched=$false;productionChanged=$false}|ConvertTo-Json -Compress
'''
for key,val in {'__LAB__':lab,'__ZIP__':remote.replace('/',chr(92)),'__OLD_PRODUCT__':old['productSha256'],'__OLD_QA__':old['qaSha256'],'__BUNDLE__':sha,'__TAG__':sha[:12]}.items():script=script.replace(key,val)
run=subprocess.run(['ssh',*flags,host,'powershell','-NoProfile','-NonInteractive','-EncodedCommand',base64.b64encode(script.encode('utf-16le')).decode()],capture_output=True,timeout=45)
(evidence/'131-native-update.log').write_bytes(run.stdout+b'\n'+run.stderr)
if run.returncode:print('Private input update failed; inspect own log. No game/task launch.');raise SystemExit(run.returncode)
data=json.loads(run.stdout.decode('utf-8-sig'));(evidence/'131-native-inputs-updated.json').write_text(json.dumps(data,indent=2),encoding='utf-8');(qa/'candidate-pins.json').write_text(json.dumps(new,indent=2),encoding='utf-8');print(json.dumps(data,indent=2))

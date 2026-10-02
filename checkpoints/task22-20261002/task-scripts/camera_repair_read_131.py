"""Authorized read-only 131 inspection. No launches, focus actions, task edits or remote writes."""
from pathlib import Path
import base64,json,subprocess,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(__file__).resolve().parent/'camera-repair/evidence';root.mkdir(parents=True,exist_ok=True)
script=r'''
$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
if($env:COMPUTERNAME -ne 'JBC_FCRL'){throw 'Wrong host'}
$base='C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001'
$records=@()
foreach($name in @('appfn-v1','unified-app-qa-v2','qa-runtime')){
 $dir=if($name -eq 'qa-runtime'){Join-Path $base $name}else{Join-Path (Join-Path $base $name) 'qa-runtime'}
 if(!(Test-Path -LiteralPath $dir)){continue}
 foreach($file in @(Get-ChildItem -LiteralPath $dir -Filter 'active-*.json' -File)){
  $a=[IO.File]::ReadAllText($file.FullName)|ConvertFrom-Json
  $entry=[ordered]@{activeFile=$file.FullName;active=$a}
  $lab=$a.lab
  if($lab -and $lab.StartsWith($base+'\',[StringComparison]::OrdinalIgnoreCase)){
   foreach($f in @('inputs.json','runtime-progress.json','runtime-result.json','exit.json','run-summary.json')){
    $p=Join-Path $lab $f;if(Test-Path -LiteralPath $p){
     $data=[IO.File]::ReadAllText($p)|ConvertFrom-Json
     if($f -eq 'inputs.json'){$entry[$f]=[ordered]@{terminal=$data.terminal;terminalSha256=$data.terminalSha256;privatePid=$data.privatePid;gitHead=$data.gitHead}}
     else{$entry[$f]=$data}
    }
   }
   $p=Join-Path $lab 'logs/latest.log'
   if(Test-Path -LiteralPath $p){$entry['recentCameraLog']=@(Get-Content -LiteralPath $p -Tail 80 -Encoding UTF8|Where-Object {$_ -match 'camera\.|Camera|Preview paused|APP request failed'})}
  }
  $records+=$entry
 }
}
$processes=@(Get-Process java,javaw -ErrorAction SilentlyContinue|ForEach-Object{[ordered]@{pid=$_.Id;session=$_.SessionId;createdUtc=$_.StartTime.ToUniversalTime().ToString('o')}})
[ordered]@{observedUtc=[DateTime]::UtcNow.ToString('o');records=$records;processes=$processes;readOnly=$true;newClientLaunched=$false;taskChanged=$false}|ConvertTo-Json -Depth 15
'''
cmd=['ssh','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=10','jbc-1@192.168.110.131','powershell','-NoProfile','-NonInteractive','-OutputFormat','Text','-EncodedCommand',base64.b64encode(script.encode('utf-16le')).decode()]
run=subprocess.run(cmd,capture_output=True,timeout=45)
if run.returncode:print(run.stderr.decode('utf-8',errors='replace'));raise SystemExit(run.returncode)
data=json.loads(run.stdout.decode('utf-8-sig'));(root/'131-readonly-state.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(data,ensure_ascii=False,indent=2))

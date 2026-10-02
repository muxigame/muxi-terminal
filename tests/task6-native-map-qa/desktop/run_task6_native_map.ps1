param([ValidateSet('ui','network','all')][string]$Mode='all')
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
if($env:COMPUTERNAME -ne 'JBC_FCRL' -or (Get-Process -Id $PID).SessionId -eq 0){throw '131 interactive desktop only. SSH/session 0 is preparation only.'}
$logDirectory=Join-Path $PSScriptRoot '../qa-runtime'
$null=New-Item -ItemType Directory -Path $logDirectory -Force
$logPath=Join-Path $logDirectory ('desktop-entry-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')+'-'+$PID+'.log')
Start-Transcript -LiteralPath $logPath
try {
  & 'C:\Python38\python.exe' -B (Join-Path $PSScriptRoot 'run_131_qa.py') preflight --assigned-slot task6-native-map --visible
  if($LASTEXITCODE -ne 0){throw 'Preflight failed; no Minecraft started.'}
  & 'C:\Python38\python.exe' -B (Join-Path $PSScriptRoot 'run_131_qa.py') $Mode --assigned-slot task6-native-map --visible
  if($LASTEXITCODE -ne 0){throw 'Task6 native QA incomplete. Preserve private evidence.'}
}finally{Stop-Transcript}

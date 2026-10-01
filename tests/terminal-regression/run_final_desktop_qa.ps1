param([ValidateSet('preflight','full','firstperson','all')][string]$Mode='all')
$ErrorActionPreference='Stop'
if($env:COMPUTERNAME -ne 'JBC_FCRL' -or (Get-Process -Id $PID).SessionId -eq 0){throw 'Run visibly on the 131 desktop; session 0 cannot launch QA'}
$python='C:\Python38\python.exe'
$runner=Join-Path $PSScriptRoot 'run_131_qa.py'
$modes=if($Mode -eq 'all'){@('preflight','full','firstperson')}else{@($Mode)}
foreach($item in $modes){
  & $python $runner $item --assigned-slot task14-production-assigned --visible
  if($LASTEXITCODE -ne 0){throw "QA failed at $item; preserve logs and do not release"}
}
Write-Host 'Desktop QA commands finished. Raw screenshots and normal exit receipts still require review.'

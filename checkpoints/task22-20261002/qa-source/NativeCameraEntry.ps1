param([Parameter(Mandatory=$true)][string]$Lab)
$ErrorActionPreference='Stop'
$taskName='MuxiCameraTask22NativeQA20261002'
$root='C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002'
try{
  $resolved=[IO.Path]::GetFullPath($Lab)
  if(!$resolved.StartsWith($root+'\run-',[StringComparison]::OrdinalIgnoreCase)){throw 'Wrong private lab'}
  if($env:COMPUTERNAME -ne 'JBC_FCRL' -or (Get-Process -Id $PID).SessionId -ne 2){throw 'Assigned 131 Session2 required'}
  & 'C:\Python38\python.exe' -B "$root\run-camera-qa.py" --run $resolved --assigned-slot task22-camera-native *> "$resolved\desktop-entry.log"
  $code=$LASTEXITCODE
}finally{
  # Remove this one-shot entry after the runner exits. Never inspect/change MuxiDesktopQA.
  $service=New-Object -ComObject Schedule.Service;$service.Connect();$service.GetFolder('\').DeleteTask($taskName,0)
}
exit $code

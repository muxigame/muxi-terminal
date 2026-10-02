param([Parameter(Mandatory=$true)][string]$Lab,[Parameter(Mandatory=$true)][string]$ApprovedSlot)
$ErrorActionPreference='Stop'
$root='C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002'
$name='MuxiCameraTask22NativeQA20261002'
if($env:COMPUTERNAME -ne 'JBC_FCRL' -or $ApprovedSlot -ne 'task22-camera-native'){throw 'Parent-coordinated camera slot required'}
$resolved=[IO.Path]::GetFullPath($Lab)
if(!$resolved.StartsWith($root+'\run-',[StringComparison]::OrdinalIgnoreCase)){throw 'Wrong private lab'}
$inputData=[IO.File]::ReadAllText("$resolved\inputs.json")|ConvertFrom-Json
if(!$inputData.prepared -or $inputData.clientStarted){throw 'Lab must be prepared and never launched'}
if(Test-Path -LiteralPath "$resolved\launch-reservation.json"){throw 'This private lab was already attempted; preserve it, no duplicate GUI'}
$service=New-Object -ComObject Schedule.Service;$service.Connect();$folder=$service.GetFolder('\')
try{$null=$folder.GetTask($name);throw 'Own task already exists; preserve it and do not overwrite'}catch [Runtime.InteropServices.COMException]{if($_.Exception.HResult -ne -2147024894){throw}}
$definition=$service.NewTask(0)
$definition.RegistrationInfo.Description='Task22 isolated native camera QA, one-shot, self-removes. No production/other task changes.'
$definition.Principal.UserId='S-1-5-21-478624270-3686733854-325980151-1001'
$definition.Principal.LogonType=3 # Existing interactive user token. No passwords/login/session or privacy changes.
$definition.Principal.RunLevel=0
$definition.Settings.Enabled=$true;$definition.Settings.Hidden=$true
$definition.Settings.AllowDemandStart=$true
$definition.Settings.DisallowStartIfOnBatteries=$false;$definition.Settings.StopIfGoingOnBatteries=$false
$definition.Settings.ExecutionTimeLimit='PT0S'
$action=$definition.Actions.Create(0);$action.Path='powershell.exe'
$action.Arguments='-NoProfile -WindowStyle Hidden -File "'+$root+'\NativeCameraEntry.ps1" -Lab "'+$resolved+'"'
$action.WorkingDirectory=$root
$task=$folder.RegisterTaskDefinition($name,$definition,2,$null,$null,3,$null) # CREATE, never overwrite.
try{
  # TASK_RUN_USE_SESSION_ID=4: explicitly target the existing logged-on Session2.
  # https://learn.microsoft.com/en-us/windows/win32/api/taskschd/nf-taskschd-iregisteredtask-runex
  $running=$task.RunEx($null,4,2,$null)
}catch{$folder.DeleteTask($name,0);throw}
@{task=$name;instance=$running.InstanceGuid;lab=$resolved;sessionRequired=2;oneShot=$true;selfRemove=$true;existingMuxiDesktopQATouched=$false;productionChanged=$false}|ConvertTo-Json -Compress

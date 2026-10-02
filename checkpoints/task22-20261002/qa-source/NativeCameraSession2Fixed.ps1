# Task14 alone triggers its existing Session2 entry. No scheduled task or focus changes.
$ErrorActionPreference='Stop'
$root='C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002'
$lab='C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002\run-20261002-055245-420455d3'
if($env:COMPUTERNAME -ne 'JBC_FCRL' -or (Get-Process -Id $PID).SessionId -ne 2){throw 'Assigned 131 Session2 required'}
& 'C:\Python38\python.exe' -B "$root\run-camera-qa.py" --run $lab --assigned-slot task22-camera-native *> "$lab\desktop-entry.log"
exit $LASTEXITCODE

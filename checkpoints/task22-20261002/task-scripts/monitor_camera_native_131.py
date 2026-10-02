from pathlib import Path
import base64,json,subprocess,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(__file__).resolve().parent;evidence=root/'camera-repair/evidence'
remote=r'''
from pathlib import Path
import json,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=Path(r'C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002');sys.path.insert(0,str(root))
from shared_progress_io import read_shared_text
lab=root/'run-20261002-045106-15b18303';out={}
for name in ['active.json','run-20261002-045106-15b18303/camera-progress.json','run-20261002-045106-15b18303/camera-result.json','run-20261002-045106-15b18303/exit.json','run-20261002-045106-15b18303/summary.json']:
 try:out[name]=json.loads(read_shared_text(root/name))
 except (OSError,ValueError) as e:out[name]={'unavailable':str(e)}
for name in ['boot.log','desktop-entry.log','logs/latest.log']:
 try:
  try:content=read_shared_text(lab/name,encoding='utf-16' if name=='desktop-entry.log' else 'utf-8')
  except UnicodeError:content=read_shared_text(lab/name,encoding='latin1').encode('latin1').decode('utf-8',errors='replace')
  lines=content.splitlines()
  selected=[l for l in lines if len(l)<1000 and any(w in l for w in ['QA_HARDWARE','Shaderpack','shaderpack','[YSM','yes_steve_model','FATAL','NativeCameraQA','task22_camera_qa','GLFW error','OpenGL Renderer','Creating level','Starting integrated'])]
  out[name]={'tail':lines[-60:],'wmiApplied':any('QA_HARDWARE_WMI_TIMEOUT_MS=2000' in l for l in lines),'selected':selected[-15:]}
 except OSError as e:out[name]={'unavailable':str(e)}
print(json.dumps(out,ensure_ascii=False))
'''
quoted="'"+remote.replace("'","''")+"'"
ps="$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.Encoding]::UTF8;& 'C:\\Python38\\python.exe' -B -c "+quoted
flags=['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=8']
run=subprocess.run(['ssh',*flags,'jbc-1@192.168.110.131','powershell','-NoProfile','-NonInteractive','-EncodedCommand',base64.b64encode(ps.encode('utf-16le')).decode()],capture_output=True,timeout=30)
if run.returncode:print(run.stderr.decode('utf-8',errors='replace'));raise SystemExit(run.returncode)
data=json.loads(run.stdout.decode('utf-8-sig'));(evidence/'131-native-monitor.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
display={}
for name,value in data.items():
    if name=='active.json':value={k:value.get(k) for k in ['privatePid','session','startedUtc','productSha256','qaSha256']}
    elif name.endswith('.log'):value={'wmiApplied':value.get('wmiApplied'),'tail':value.get('tail',[])[-8:],'selected':value.get('selected',[])}
    elif isinstance(value,dict) and 'mods' in value:value=dict(value,modsCount=len(value['mods']));value.pop('mods',None)
    display[name]=value
print(json.dumps(display,ensure_ascii=False,indent=2))

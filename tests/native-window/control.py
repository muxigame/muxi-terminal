import json,time,os,uuid,psutil
from pathlib import Path
T=Path(__file__).resolve().parents[2];N=T/'build/native-final'
LAB=Path(os.environ.get('MUXI_NATIVE_LAB',str(T/'build/local-mc-debug/window-locale-graceful3-20261013')))
def read(path):
 try:return json.loads(path.read_text(encoding='utf-8'))
 except (OSError,json.JSONDecodeError):return None
def alive():
 owner=read(LAB/'local-mc-owner.json')
 if not owner:raise RuntimeError('Owned lab marker missing')
 if (LAB/'stop-request.json').exists() or (LAB/'run-result.json').exists():raise RuntimeError('Owned lab already stopping/stopped')
 for role,row in owner['processes'].items():
  try:
   p=psutil.Process(row['pid'])
   if abs(p.create_time()-row['processCreateTime'])>.01:raise RuntimeError('Owned PID reused: '+role)
   if not p.is_running():raise RuntimeError('Owned JVM stopped: '+role)
  except psutil.NoSuchProcess:raise RuntimeError('Owned JVM stopped: '+role)
 return owner
def cmd(channel,action,**body):
 owner=alive();path=LAB/'coordinator'/(channel+'-host.json');old=read(path) or {};idx=old.get('id',0)+1
 body.update(id=idx,runId=owner['runId'],action=action);tmp=path.with_suffix('.tmp');tmp.write_text(json.dumps(body),encoding='utf-8');tmp.replace(path)
 result=LAB/'coordinator'/(channel+'-result-'+str(idx)+'.json');end=time.monotonic()+20
 while time.monotonic()<end:
  alive();data=read(result)
  if data and data.get('runId')==owner['runId']:break
  time.sleep(.05)
 else:raise TimeoutError(body)
 with (LAB/'controller-receipts.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(data,ensure_ascii=True)+'\n')
 if not data.get('ok'):raise RuntimeError(data)
 return data['status']
def w(action,**body):return cmd('window',action,**body)
def s(action,**body):return cmd('settings',action,**body)
def wait(test,timeout=15):
 end=time.monotonic()+timeout;state=w('observe')
 while not test(state) and time.monotonic()<end:time.sleep(.1);state=w('observe')
 if not test(state):raise AssertionError(state)
 return state
def ev(expression):
 tag=uuid.uuid4().hex;w('eval',expression='Object.assign({qaToken:'+json.dumps(tag)+'},('+expression+'))');state=wait(lambda x:x.get('dom',{}).get('qaToken')==tag);return state['dom']
def click(x,y):w('cursor',x=x,y=y);w('mouse',button=0,pressed=True);time.sleep(.06);return w('mouse',button=0,pressed=False)
def chrome(name):
 state=w('observe');r=state['layout'][name];return click(r['x']+r['width']/2,r['y']+r['height']/2)
def domclick(selector):
 q=ev('(()=>{const r=document.querySelector('+json.dumps(selector)+').getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}})()');state=w('observe');r=state['layout']['content'];g=state['guiScale'];return click(r['x']+(q['x']+q['w']/2)/g,r['y']+(22 if 'appId' in state else 0)+(q['y']+q['h']/2)/g)
def shot(name):w('screenshot',filename=name);time.sleep(.15)

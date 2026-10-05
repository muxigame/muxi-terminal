"""Private two-client waiting-room protocol verification; no production or visual/SSO claim."""
from pathlib import Path
import argparse,sys,subprocess,time,json,zipfile,tomllib,datetime

def main():
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--workspace',type=Path,required=True);ap.add_argument('--candidate',type=Path,required=True);ap.add_argument('--java-home',type=Path,required=True);ap.add_argument('--port',type=int,default=25943);ap.add_argument('--games',nargs='+',default=['flight','horse_racing','zombie-challenge','outbreak']);ap.add_argument('--flight-mode',choices=['pve','pvp'],default='pve');ap.add_argument('--outbreak-mode',choices=['CAMPAIGN','SURVIVAL'],default='CAMPAIGN');a=ap.parse_args()
 root=a.workspace.resolve();project=root/'better-mc-remake';sys.path.insert(0,str(project/'scripts'));import local_mc_debug as debug
 evidence_dir=a.candidate.resolve();manifest=json.loads((evidence_dir/'CANDIDATE.json').read_text(encoding='utf-8'));lab=project/'build/local-mc-debug'/('room-ui-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
 mods=[Path(x['path']) for x in manifest['artifacts'] if x['module']!='muxi-terminal']
 wanted={'warfare_wings','immersive_aircraft','touhou_little_maid','yes_steve_model','tacz','lrtactical'};found=set()
 for folder in [root/'bmc5server/mods',root/'muxi-outbreak/build/equipment-research']:
  for jar in folder.glob('*.jar'):
   try:
    with zipfile.ZipFile(jar) as z:
     meta=next((n for n in ['META-INF/neoforge.mods.toml','META-INF/mods.toml'] if n in z.namelist()),None)
     if not meta:continue
     ids={m['modId'] for m in tomllib.loads(z.read(meta).decode('utf-8')).get('mods',[])}
     if ids&wanted and not ids&found:mods.append(jar);found|=ids
   except (ValueError,zipfile.BadZipFile):continue
 if wanted-found:raise RuntimeError('Missing explicitly required native dependencies: '+str(wanted-found))
 args=[sys.executable,str(project/'scripts/local_mc_debug.py'),'run','--project-root',str(project),'--instance-root',str(lab),'--server-runtime',str(root/'bmc5server'),'--client-game',str(root/'_client_test/game'),'--java-home',str(a.java_home),'--port',str(a.port),'--clients','2','--client-name','RoomHost','--client-name','RoomGuest','--accept-eula','--unix-temp','C:/Temp','--mode','hold','--hold-seconds','900','--boot-timeout','240','--shutdown-timeout','120']
 for mod in mods:args+=['--mod',str(mod)]
 report={'lab':str(lab),'scope':'Two actual hidden Minecraft network clients; waiting-room actions/snapshots only. No OS/visual/MCEF/SSO/combat/settlement acceptance.','checks':[],'receipts':[],'passed':False}
 log=(evidence_dir/'native-runner.log').open('w',encoding='utf-8');proc=subprocess.Popen(args,cwd=project,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 print('ROOM_NATIVE_LAB '+str(lab),flush=True)
 def save(): (evidence_dir/'NATIVE-ROOMS.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 def check(value,label):
  if not value:raise AssertionError(label)
  report['checks'].append(label);save();print('PASS '+label,flush=True)
 def command(role,body):
  r=debug.command(lab,role,body,30);report['receipts'].append(r);save();return r
 def snapshot(player='RoomHost'):return command('server',{'type':'game-snapshot','player':player})['snapshot']
 def game(s,id):return next(g for g in s['games'] if g['id']==id)
 def own(s,id):return next((r for r in game(s,id)['state'].get('rooms',[]) if r['mine']),None)
 def wait_for(predicate,seconds=30):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   s=snapshot()
   if predicate(s):return s
   time.sleep(.4)
  raise TimeoutError('Room state did not reach asserted condition')
 def action(role,id,op,value=''):return command(role,{'type':'game-action','game':id,'action':op,'value':value})
 def packet(value):return json.dumps(value,ensure_ascii=False,separators=(',',':'))
 try:
  end=time.monotonic()+520
  while time.monotonic()<end:
   if proc.poll() is not None:raise RuntimeError('Native runner exited before connections; inspect native-runner.log')
   c=lab/'coordinator';server=debug.rt.read_json(c/'status-server.json') or {};host=debug.rt.read_json(c/'status-host.json') or {};guest=debug.rt.read_json(c/'status-guest.json') or {}
   if server.get('players')==2 and host.get('connected') and guest.get('connected'):break
   time.sleep(1)
  else:raise TimeoutError('Actual two-client connection deadline exceeded')
  for role in ['host','guest']:
   r=command(role,{'type':'observe'});check(r['nativeMinecraftClient'] and r['status']['connected'] and not r['status']['glfwVisible'],role+' is an actual hidden connected client')
  s=snapshot();check(s['roomUiVersion']==1 and s['self']['name']=='RoomHost','actual room protocol and nickname');check({g['id'] for g in s['games']}=={'flight','horse_racing','zombie-challenge','outbreak'},'all four actual game modules registered')
  host_id=s['self']['uuid'];guest_id=snapshot('RoomGuest')['self']['uuid']
  for id in a.games:
   s=snapshot();g=game(s,id);create=next(section for section in g['ui']['lobby']['sections'] if section.get('role')=='create');fields=create.get('fields',[]);selected={f['id']:str(f['selected']) for f in fields}
   if id=='flight':selected.update(mode=a.flight_mode,difficulty='1')
   if id=='outbreak':selected.update(mode=a.outbreak_mode)
   for field in fields:
    options=[o for o in field['options'] if not o.get('modes') or selected.get('mode') in o['modes']]
    if selected[field['id']] not in [str(o['value']) for o in options]:selected[field['id']]=str(options[0]['value'])
   action('host',id,'roomCreate',packet(['RoomHost的房间',[selected[f['id']] for f in fields]]));s=wait_for(lambda s:own(s,id) is not None,60);r=own(s,id)
   check(r['roomName']=='RoomHost的房间' and r['phase'] in ['WAITING','LOBBY','BUILDING'],'%s actual create names room without auto-start'%id);session=r['session'];short=r['id']
   action('host',id,'invite',guest_id);time.sleep(.3);action('guest',id,'join',short);s=wait_for(lambda s:own(s,id) and own(s,id)['count']==2,60);r=own(s,id)
   check({m['uuid'] for m in r['roster']}=={host_id,guest_id} and all(m['online'] for m in r['roster']),'%s invite and join yields two real members'%id)
   s=wait_for(lambda s:own(s,id)['phase'] in ['WAITING','LOBBY'],90);g=game(s,id);r=own(s,id)
   entry=next((section for section in g['ui']['lobby']['sections'] if section.get('role') in ['room','settings'] and any(f['id']=='roomDifficulty' for f in section.get('fields',[]))),None)
   field=next(f for f in entry['fields'] if f['id']=='roomDifficulty');choice=str(field['options'][-1]['value'])
   action('guest',id,'roomSettings',packet([session,'GuestOverride',[]]));time.sleep(.3);check(own(snapshot(),id)['roomName']=='RoomHost的房间','%s actual nonhost settings rejected'%id)
   action('host',id,'roomSettings',packet(['00000000-0000-4000-8000-000000000000','StaleOverride',[]]));time.sleep(.3);check(own(snapshot(),id)['roomName']=='RoomHost的房间','%s stale room generation rejected'%id)
   action('host',id,'roomSettings',packet([session,'Room '+id,['roomDifficulty',choice]]));s=wait_for(lambda s:own(s,id)['roomName']=='Room '+id,60);r=own(s,id)
   check(str(r.get('difficulty',r.get('enemyTier')))==choice,'%s host name and difficulty applied by actual adapter'%id)
   if id=='outbreak':check(r['mode']==a.outbreak_mode,'outbreak selected mode reaches actual room')
   if id=='flight' and a.flight_mode=='pvp':check(r['mode']=='pvp' and r['redHumans']==1 and r['blueHumans']==1 and r['redReserve']==8 and r['blueReserve']==8 and set(r['sides'].values())=={'red','blue'},'flight PVP retains two balanced human sides and hangar preset')
   if id=='flight' and a.flight_mode=='pve':check(r['redHumans']==4 and r['blueHumans']==0 and r['blueReserve']==100 and r['blueAi']==6,'flight difficulty maps seats, reserves and AI preset')
   action('guest',id,'leave');wait_for(lambda s:own(s,id)['count']==1,60);action('host',id,'leave');wait_for(lambda s:s['activeGame']=='',60);check(snapshot('RoomGuest')['activeGame']=='','%s both clients leave without residual membership'%id)
  report['passed']=True
 except Exception as error:
  report['error']=str(error);print('NATIVE_FAILURE '+str(error),flush=True)
 finally:
  if (lab/'local-mc-owner.json').exists():
   try:debug.stop_files(lab,debug.rt.read_json(lab/'local-mc-owner.json'))
   except Exception as error:report['stopRequestError']=str(error)
  try:proc.wait(timeout=140)
  except subprocess.TimeoutExpired:report['normalStopBlocked']=True
  log.close();result=debug.rt.read_json(lab/'run-result.json') or {};report['normalExit']=result.get('normalExit',False);report['exitCodes']=result.get('exitCodes',{});report['passed']=report['passed'] and report['normalExit'];save();print(json.dumps({k:v for k,v in report.items() if k not in ['receipts','checks']},ensure_ascii=True),flush=True)
 if not report['passed']:raise SystemExit(1)
if __name__=='__main__':main()

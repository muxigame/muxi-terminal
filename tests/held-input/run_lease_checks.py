from pathlib import Path
import sys,json,time,traceback
ROOT=Path(__file__).resolve().parents[2];W=ROOT.parent;sys.path.insert(0,str(W/'better-mc-remake/scripts'))
import local_mc_debug as debug,local_mc_runtime as rt
m=json.loads((ROOT/'build/131-held-runtime.json').read_text());lab=Path(m['lab']);coordinator=lab/'coordinator';evidence=[];receipts=[];sequence=0
def wait(label,predicate,timeout=60):
 until=time.monotonic()+timeout
 while time.monotonic()<until:
  value=predicate()
  if value:return value
  if (lab/'run-result.json').exists():raise RuntimeError('Unified runner exited before '+label)
  time.sleep(.25)
 raise TimeoutError(label)
marker=wait('owned marked lab',lambda:rt.read_json(lab/'local-mc-owner.json'),30);run=marker['runId']
def check(label,ok,detail=None):
 evidence.append({'check':label,'passed':bool(ok),'detail':detail});rt.write_json(lab/'held-lease-evidence.json',{'checks':evidence,'physicalOSInput':False})
 print('HELD_ASSERT',label,bool(ok),flush=True)
 if not ok:raise AssertionError(label)
def command(kind,**extra):
 global sequence
 sequence+=1;rt.write_json(coordinator/'held-command.json',{'runId':run,'id':sequence,'type':kind,**extra})
 value=wait('held command '+str(sequence),lambda:rt.read_json(coordinator/f'held-result-{sequence}.json'),45);receipts.append(value);check('native.'+str(sequence)+'.'+kind,value.get('ok') is True,value)
 check('coreFinally.'+str(sequence),value['sample']['coreFrameCleared']);return value
def server(kind,**extra):
 value=debug.command(lab,'server',{'type':kind,**extra},45);receipts.append(value);check('server.'+str(len(receipts))+'.'+kind,value.get('ok') is True,value);return value
def inventory(main,off):
 for slot,item in [(0,main),(40,off)]:server('inventory-set',player='HeldHostQA',slot=slot,item=item or 'minecraft:air',count=1 if item else 0)
def dom(code=''):return command('script',code=code)
def key(code,mods=0,action=None):
 values={'key':code,'modifiers':mods}
 if action is not None:values['action']=action
 return command('key',**values)
def presses(row):return sum(x['action']==1 for x in row['sample']['keys'])
result={'runId':run,'lab':str(lab),'candidate':m['candidate'],'candidateSha256':m['candidateSha256'],'physicalOSInput':False,'nativeKeyboardCallbacks':True,'assistedNativeFocus':True,'passed':False}
try:
 wait('actual connected native client',lambda:(rt.read_json(coordinator/'status-host.json') or {}).get('connected'),300)
 command('focus',active=True);inventory('muxi_terminal:player_terminal',None)
 wait('actual world Screen closed and held browser focused',lambda:command('sample')['sample']['screen']=='' and command('sample')['sample']['heldTarget']!=0,60)
 a=dom("selectNav(navItems().findIndex(el=>el.dataset.open==='guide'),true)");shell=a['sample']['shell']
 key(257,action=1)
 wait('native guide view visible',lambda:command('sample')['sample']['contentVisible'] and command('sample')['sample']['kind']=='BUILTIN',30)
 a=command('sample');view=a['sample']['content'];count=presses(a)
 key(257,action=2);key(257,action=2);key(257,action=2);a=command('sample')
 check('heldEnterRepeatsCannotConfirmNewView',a['sample']['kind']=='BUILTIN' and a['sample']['content']==view and presses(a)==count)
 key(257,action=0);command('home');a=dom("selectNav(navItems().findIndex(el=>el.dataset.open==='guide'),true)")
 key(335,action=1)
 wait('keypad Enter opens native guide',lambda:command('sample')['sample']['kind']=='BUILTIN',30)
 a=command('sample');view=a['sample']['content'];count=presses(a);key(335,action=2);a=command('sample')
 check('keypadEnterConfirmsOnce',a['sample']['kind']=='BUILTIN' and a['sample']['content']==view and presses(a)==count);key(335,action=0)
 command('home');dom("window.__heldEvents=[];window.addEventListener('keydown',e=>window.__heldEvents.push(e.key));selectNav(0,true)")
 inventory(None,None);command('arrow-binding',active=True);a=key(262,action=1);check('notHeldCustomRightActuallyDown',a['sample']['rightGameDown'])
 inventory('muxi_terminal:player_terminal',None);a=key(262,action=2)
 check('pickupCapturedArrowReleasesGameBinding',not a['sample']['rightGameDown'] and a['sample']['rightGameClicks']==0)
 a=dom();check('heldArrowRepeatReachesRealDOM',a['dom']['events'][-1]=='ArrowRight')
 a=key(262,action=0);check('capturedReleaseLeavesNoGameGhost',not a['sample']['rightGameDown'] and a['sample']['heldPressed']==0)
 command('arrow-binding',active=False)
 before=command('sample');key(70);key(66);key(87);key(258,2);after=command('sample');check('F_B_WASD_CtrlTabKeepOriginalRoutes',presses(after)==presses(before))
 # A normal arrow still reaches the active page after the new guard, and full Screen stays intact.
 command('home');dom("selectNav(0,true)");before=dom();key(262);after=dom();check('normalHeldArrowStillMovesFocus',after['dom']['selected']!=before['dom']['selected'])
 command('screen',value='terminal');before=command('sample');key(263);after=command('sample');check('openedScreenArrowUnaffected',presses(after)==presses(before)+1);key(256)
 before=command('sample');owned=1+(1 if before['sample']['content'] else 0);baseline=before['sample']['cefClients']-owned;command('logout')
 def released():
  a=command('sample')['sample'];return a if a['liveTextures']==0 and a['cefClients']==baseline else None
 final=wait('new candidate actual logout releases resources',released,45);check('leaseExitResourceBaseline',final['shell']==0 and final['content']==0 and final['heldTarget']==0 and final['heldPressed']==0)
 result['exitResourceBaseline']=final;result['passed']=True
except BaseException as failure:result['error']=repr(failure);print('HELD_FAILURE',traceback.format_exc(),flush=True)
finally:
 result['checks']=len(evidence);result['passedChecks']=sum(x['passed'] for x in evidence);result['evidence']=evidence;result['receipts']=receipts;rt.write_json(lab/'held-lease-result.json',result);rt.write_json(ROOT/'build/131-held-lease-result.json',result);rt.write_json(lab/'stop-request.json',{'runId':run,'reason':'held input native acceptance finished; own normal stop only'});print('HELD_RESULT',json.dumps({k:v for k,v in result.items() if k not in ['receipts','evidence','exitResourceBaseline']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)

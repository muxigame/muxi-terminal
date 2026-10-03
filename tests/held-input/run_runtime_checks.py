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
 evidence.append({'check':label,'passed':bool(ok),'detail':detail});rt.write_json(lab/'held-input-evidence.json',{'checks':evidence,'physicalOSInput':False})
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
 server('observe');command('focus',active=True)
 inventory('muxi_terminal:player_terminal',None)
 wait('actual world Screen closed and held browser focused',lambda:command('sample')['sample']['screen']=='' and command('sample')['sample']['heldTarget']!=0,60)
 a=command('sample');check('directHeldHasNoScreen',a['sample']['screen']=='');check('directHeldHasRealShellTexture',a['sample']['textureWidth']==640 and a['sample']['textureHeight']==400)
 init="window.__heldEvents=[];window.addEventListener('keydown',e=>window.__heldEvents.push(e.key));window.__heldPulse=0;let badge=document.createElement('div');badge.id='heldQaPulse';badge.style.cssText='position:fixed;right:0;top:0;z-index:99999;padding:12px;color:white;background:red';document.body.append(badge);setInterval(()=>{badge.style.background=(++window.__heldPulse%2)?'red':'blue';badge.textContent='Live '+window.__heldPulse},180);selectNav(0,true)"
 a=dom(init);check('realLocalHomeDOM',a['dom']['page']=='home');check('mainTwoHandPoseActuallyRendered',a['sample']['mainDraws']>0 and a['sample']['twoArmDraws']>0)
 b=dom();check('heldDOMTimerActuallyAdvances',b['dom']['pulse']>a['dom']['pulse']);check('heldGPUTextureActuallyChanges',b['sample']['textureHash']!=a['sample']['textureHash']);check('heldCEFActuallyPaints',b['sample']['paints']>0)
 for code,label in [(262,'ArrowRight'),(264,'ArrowDown'),(263,'ArrowLeft'),(265,'ArrowUp')]:
  before=dom();key(code);after=dom();check('directHeldCEFKey.'+label,len(after['dom']['events'])==len(before['dom']['events'])+1 and after['dom']['events'][-1]==label);check('directHeldFocusMoves.'+label,after['dom']['selected']!=before['dom']['selected']);check('directHeldStaysScreenless.'+label,after['sample']['screen']=='')
 command('screenshot')
 before=dom();n=presses(before);key(87);key(258,2);key(262,2);after=dom();check('WASD_CtrlTab_ModifiedArrowNotSentToCEF',presses(after)==n)
 key(258);actual=server('observe')['status']['byName']['HeldHostQA']['inventoryBySlot'];check('heldTabStillSwapsOffhand',actual['40']['item']=='muxi_terminal:player_terminal' and '0' not in actual)
 before=command('sample');key(262);after=dom();check('offhandArrowOneCEFSend',presses(after)==presses(before)+1 and after['sample']['offDraws']>before['sample']['offDraws'])
 inventory('minecraft:diamond','muxi_terminal:player_terminal');before=command('sample');key(263);after=command('sample');check('singleHandWithOtherItemActuallyRendered',after['sample']['oneArmDraws']>before['sample']['oneArmDraws'] and after['sample']['offDraws']>before['sample']['offDraws']);check('singleHandInputOneCEFSend',presses(after)==presses(before)+1)
 inventory('muxi_terminal:player_terminal','muxi_terminal:player_terminal');before=command('sample');key(262);after=command('sample');check('twoTerminalsShareOneShell',after['sample']['shell']==before['sample']['shell'] and after['sample']['cefClients']==before['sample']['cefClients']);check('twoTerminalsDoNotDoubleDispatch',presses(after)==presses(before)+1)
 inventory(None,None);before=command('sample');key(264);after=command('sample');check('notHeldNoCEFSends',presses(after)==presses(before));check('notHeldFocusAndKeysReleased',after['sample']['heldTarget']==0 and after['sample']['heldPressed']==0)
 inventory('muxi_terminal:player_terminal',None);command('focus',active=False);before=command('sample');key(262);after=command('sample');check('inactiveWindowNoCEFSends',presses(after)==presses(before) and after['sample']['heldTarget']==0);command('focus',active=True)
 for screen in ['chat','other']:
  command('screen',value=screen);before=command('sample');key(262);key(257);after=command('sample');check('screenOwnsArrowEnter.'+screen,presses(after)==presses(before));command('screen',value='none')
 command('home');a=dom("selectNav(navItems().findIndex(el=>el.dataset.open==='guide'),true)");check('guideButtonFocusedInHeldDOM','指南' in a['dom']['label'])
 oldShell=a['sample']['shell'];key(257,action=1);wait('native builtin opened without Screen',lambda:command('sample')['sample']['kind']=='BUILTIN',30);a=command('sample');newView=a['sample']['content'];check('heldEnterOpensNativeGuideWithoutScreen',newView!=0 and a['sample']['screen']=='' and a['sample']['shell']==oldShell);key(257,action=0);a=dom();check('newGuideReceivesNoOldEnterRelease',not any(x['browser']==newView and x['key']==257 and x['action']==0 for x in a['sample']['keys']));check('heldNativeGuideDOMIsLive',a['dom']['page']=='guide')
 before=a;key(264);after=dom();check('heldGuideArrowArrivesInActiveContent',presses(after)==presses(before)+1 and after['sample']['keys'][-2]['browser']==newView)
 key(262,action=1);command('screen',value='terminal');a=command('sample');check('rightClickScreenKeepsSameViews',a['sample']['shell']==oldShell and a['sample']['content']==newView and a['sample']['screen'].endswith('TerminalScreen'));check('openingScreenDisarmsHeldCaptureBeforeNewFocus',a['sample']['heldTarget']==0 and a['sample']['heldPressed']==0)
 before=a;key(265);after=dom();check('openedTerminalOriginalArrowStillWorks',presses(after)==presses(before)+1)
 key(256);a=command('sample');check('escapeReturnsToLiveHeldView',a['sample']['screen']=='' and a['sample']['content']==newView and a['sample']['heldTarget']==newView)
 before=a;key(263);after=dom();check('heldArrowWorksAgainAfterScreenClose',presses(after)==presses(before)+1)
 command('screenshot');before=command('sample');owned=1+(1 if before['sample']['content'] else 0);baseline=before['sample']['cefClients']-owned;command('logout')
 def released():
  a=command('sample')['sample'];return a if a['liveTextures']==0 and a['cefClients']==baseline else None
 final=wait('actual logout resources reach baseline',released,45);check('logoutNoShellContentOrHeldTarget',final['shell']==0 and final['content']==0 and final['heldTarget']==0 and final['heldPressed']==0);check('logoutCEFTexturesAndClientsAtBaseline',final['liveTextures']==0 and final['cefClients']==baseline);result['exitResourceBaseline']=final;result['passed']=True
except BaseException as failure:result['error']=repr(failure);print('HELD_FAILURE',traceback.format_exc(),flush=True)
finally:
 result['checks']=len(evidence);result['passedChecks']=sum(x['passed'] for x in evidence);result['evidence']=evidence;result['receipts']=receipts;rt.write_json(lab/'held-input-result.json',result);rt.write_json(ROOT/'build/131-held-result.json',result);rt.write_json(lab/'stop-request.json',{'runId':run,'reason':'held input native acceptance finished; own normal stop only'});print('HELD_RESULT',json.dumps({k:v for k,v in result.items() if k not in ['receipts','evidence','exitResourceBaseline']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)

import json,time,math,traceback
from pathlib import Path
from control import qa,native,wait,portable_fixture,LAB,OWN,PLAYER,ASSETS
checks=[];samples=[]
def check(value,label):
    if not value:raise AssertionError(label)
    checks.append(label);print('PASS '+label,flush=True)
def observe(label):
    state=qa('observe');samples.append({'label':label,'state':state});return state
def api(command):
    state=qa('api',command=command)
    if state.get('ok') is False:raise AssertionError(state)
    return state
def control(action):
    time.sleep(.23);state=qa('observe');return api('music.control:'+json.dumps({'action':action,'target':state['target']}))
def own_playing(state):return any(row['own'] and row['state']==0x1012 for row in state['audio'])
report={'scope':'Actual isolated MC/MCEF/OpenAL on 131; hidden client, assisted native callbacks; no OS file-picker/physical input acceptance','lab':str(LAB),'checks':checks,'samples':samples,'productionWrites':False,'forceKill':False}
assets=ASSETS
index=json.loads((assets/'indexes/17.json').read_text(encoding='utf8'));h=index['objects']['minecraft/sounds.json']['hash']
native_definitions=json.loads((assets/'objects'/h[:2]/h).read_text(encoding='utf8'))
def base_volume(row,state):
    if 'baseVolume' in row:return row['baseVolume']
    event=row['event'].split(':',1)[1]
    for entry in native_definitions[event]['sounds']:
        if isinstance(entry,str):entry={'name':entry}
        if entry['name'].rsplit('/',1)[-1].replace('_',' ')==state['title']:return entry.get('volume',1)
    raise AssertionError('Native resource metadata does not match current actual sound '+str(state))
try:
    qa('setup');wait(lambda s:not s['busy'] and not s['catalogLoading'])
    check(len(qa('observe')['tracks'])==60,'final candidate exposes the 60 actual native resource files')
    qa('background',volume=math.sqrt(.05));time.sleep(2.4);state=observe('final-low-volume')
    row=next(r for r in state['audio'] if not r['own'] and r['source']=='music' and r['state']==0x1012)
    check(abs(row['gain']-state['actualMusicGain']*row['baseVolume'])<.001,'final candidate preserves one application of actual low category gain')
    qa('background-stop');time.sleep(2.2)
    qa('import-fixture',seconds=3);local=qa('observe')['localTracks']
    check(len(local)==2,'final candidate stores actual local decoded files only in the private instance')
    portable_fixture();state=wait(lambda s:any(r['title']=='Portable fixture 0' for r in s['tracks']))
    group=next(r['group'] for r in state['tracks'] if r['title']=='Portable fixture 0')
    check(sum(r.get('group')==group for r in state['tracks'])==2,'final candidate portable rows have exact group IDs')
    api('music.playlist:'+group)
    wait(lambda s:s['kind']=='netmusic' and any(r['source']=='record' and r['state']==0x1012 for r in s['audio']))
    check(not qa('observe')['capabilities']['pause'],'final native player has no fabricated pause API')
    wait(lambda s:s['queueIndex']==1 and s['title']=='Portable fixture 1',12)
    check(True,'final native whole playlist advances after EOF')
    wait(lambda s:s['queueSize']==0 and not any(r['source']=='record' for r in s['audio']),12)
    check(True,'final native whole playlist ends and releases its stream')
    api('music.local:'+local[0]['id']);playing=wait(own_playing,5)
    samples.append({'label':'portable-eof-to-local','state':playing})
    check(True,'local playback starts after native portable EOF without waiting for the 30-second timeout')
    control('stop');time.sleep(.25);control('play')
    wait(lambda s:s['kind']=='netmusic' and any(r['source']=='record' and r['state']==0x1012 for r in s['audio']),5)
    control('stop');api('music.local:'+local[0]['id']);playing=wait(own_playing,5)
    samples.append({'label':'portable-stop-to-local','state':playing})
    check(sum(r['own'] and r['state']==0x1012 for r in playing['audio'])==1,'manual native stop followed immediately by local play produces exactly one owned stream')
    qa('detach');qa('select-slot',slot=1);check(own_playing(qa('observe')),'final local session survives APP detach and held item changes')
    qa('disconnect');time.sleep(.6);state=observe('final-disconnected')
    check(state['queueSize']==0 and not any(r['own'] for r in state['audio']),'final disconnect clears owned streams and queue')
    qa('reconnect');time.sleep(2);state=wait(lambda s:s['selectedSlot']>=0 and not s['busy'],30)
    check(state['queueSize']==0 and not any(r['own'] for r in state['audio']),'final reconnect does not resume a previous playback session')
    report['success']=True
except BaseException as error:
    report.update(success=False,error=repr(error),traceback=traceback.format_exc());print(report['traceback'],flush=True)
finally:
    (OWN/'native-final-targeted-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'success':report.get('success'),'checks':len(checks),'lab':str(LAB)}),flush=True)
if not report.get('success'):raise SystemExit(1)

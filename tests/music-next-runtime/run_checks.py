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
    qa('setup')
    wait(lambda s:not s['busy'] and not s['catalogLoading'])
    first=api('music.refresh');partial=[];until=time.monotonic()+15
    while time.monotonic()<until:
        state=observe('catalog-batch');partial.append((state['catalogLoading'],len(state['tracks'])))
        if not state['catalogLoading']:break
        time.sleep(.1)
    final=state;check(len(final['tracks'])>12,'real resource catalog contains individually selectable tracks')
    check(any(loading and 0<count<len(final['tracks']) for loading,count in partial),'actual partial batches are observable before scan completion')
    check(len({row['id'] for row in final['tracks']})==len(final['tracks']),'catalog contains no duplicate opaque IDs')
    check(any(g['name']=='Minecraft' for g in final['groups']),'runtime provider label comes from official Minecraft metadata')
    scans=final['catalogScans'];qa('detach');check(qa('observe')['catalogScans']==scans,'APP detach/reopen snapshots reuse the existing catalog cache')
    for volume in (.5,math.sqrt(.1),math.sqrt(.05)):
        qa('background',volume=volume)
        time.sleep(2.5);before=observe('background-volume-'+str(volume))
        rows=[r for r in before['audio'] if not r['own'] and r['source']=='music' and r['state']==0x1012]
        check(bool(rows),'background remains active after low-volume fade-in '+str(volume))
        baseline=rows[0]['gain'];expected=before['actualMusicGain']*base_volume(rows[0],before)
        check(abs(baseline-expected)<.001,'background file volume and category gain are each applied once '+str(volume))
        qa('legacy-gain');time.sleep(.25);legacy=observe('biome-write-'+str(volume))
        qa('refresh-gain');time.sleep(.25);fresh=observe('native-write-'+str(volume))
        for data in (legacy,fresh):
            row=next(r for r in data['audio'] if not r['own'] and r['source']=='music' and r['state']==0x1012)
            check(abs(row['gain']-expected)<.001,'Biome and native volume writes agree at '+str(volume))
    check(observe('compat-hooks')['continuity']['legacyGainWritesReplaced']>=3,'real BiomeMusic legacy gain interception is applied')
    qa('background',volume=.5);time.sleep(2.5);initial=observe('before-smooth-stop')
    qa('background-stop');time.sleep(.2);tail=observe('smooth-stop-tail')
    check(any(not r['own'] and r['state']==0x1012 and 0<r['gain']<initial['actualMusicGain'] for r in tail['audio']),'manager stop retains a decreasing audible OpenAL stream instead of abrupt cut')
    time.sleep(2.2);done=observe('after-smooth-stop')
    check(done['continuity']['activeEnvelopes']==0,'completed stop releases its per-stream envelope')
    native('server','inventory-set',player=PLAYER,slot=0,item='muxi_terminal:player_terminal',count=1)
    track=qa('observe')['tracks'][0];api('music.select:'+track['id']);playing=wait(own_playing);samples.append({'label':'game-select-playing','state':playing})
    check(playing['kind']=='game','selected actual game track reaches OpenAL PLAYING')
    group=playing['groups'][0]['id'];control('stop');api('music.playlist:'+group)
    whole=wait(own_playing);check(whole['queueSize']==len(final['tracks']) and whole['queueIndex']==0,'game provider whole-list builds the actual native queue')
    control('stop');api('music.select:'+track['id']);wait(own_playing)
    control('next');changed=wait(lambda s:own_playing(s) and s['title']!=track['title']);check(changed['title']!=track['title'],'next switches the actual selected stream')
    control('previous');wait(lambda s:own_playing(s) and s['title']==track['title']);check(True,'previous returns to the selected actual track')
    qa('detach');qa('select-slot',slot=1);continued=wait(own_playing)
    check(continued['screen']=='NONE' and continued['selectedSlot']==1 and continued['kind']=='game','closing APP/terminal and switching held item retains player-owned playback')
    native('server','inventory-set',player=PLAYER,slot=2,item='muxi_terminal:player_terminal',count=1)
    check(sum(r['own'] and r['state']==0x1012 for r in observe('two-terminals')['audio'])==1,'two carried terminals share one actual playback stream')
    native('server','inventory-set',player=PLAYER,slot=0,item='minecraft:air',count=0)
    check(own_playing(observe('one-terminal-left')),'remaining carried terminal retains playback')
    native('server','inventory-set',player=PLAYER,slot=2,item='minecraft:air',count=0)
    wait(lambda s:not any(r['own'] for r in s['audio']));check(True,'removing all terminals releases owned streams')
    native('server','inventory-set',player=PLAYER,slot=0,item='muxi_terminal:player_terminal',count=1)
    wait(lambda s:s['capabilities']['import']);qa('import-fixture',seconds=3)
    local=qa('observe')['localTracks'];check(len(local)==2,'real local copy/decode/store import yields two selectable local rows')
    api('music.playlist:local');wait(own_playing);qa('detach')
    second=wait(lambda s:own_playing(s) and s['queueIndex']==1,12)
    check(second['queueSize']==2 and second['kind']=='local','whole-list natural EOF advances to the second decoded local file with APP closed')
    end=wait(lambda s:s['queueSize']==0 and not any(r['own'] for r in s['audio']),12)
    check(True,'one-pass whole playlist stops and releases local stream after final EOF')
    api('music.local:'+local[0]['id']);wait(own_playing);control('stop')
    wait(lambda s:not any(r['own'] for r in s['audio']));check(True,'manual stop releases local playback and queue')
    api('music.select:'+qa('observe')['tracks'][0]['id']);wait(own_playing)
    title=qa('observe')['title'];api('music.refresh');unchanged=wait(lambda s:own_playing(s) and not s['catalogLoading'])
    check(unchanged['title']==title and any(r['active'] for r in unchanged['tracks']),'manual catalog refresh preserves the actual playing file and current-row marker')
    generation=unchanged['catalogGeneration'];qa('reload')
    restored=wait(lambda s:not s.get('reloading',False) and s['catalogGeneration']>generation and own_playing(s),30)
    check(restored['title']==title,'actual resource reload rebuilds the same owned game stream after the engine is ready')
    control('stop')
    scans=qa('observe')['catalogScans'];generation=qa('observe')['catalogGeneration'];qa('reload')
    reloaded=wait(lambda s:s['catalogGeneration']>generation and not s['catalogLoading'] and not s.get('reloading',False),30)
    check(reloaded['catalogScans']==scans+1 and len(reloaded['tracks'])==len(final['tracks']),'actual resource reload invalidates once and rebuilds without duplicates')
    qa('setup');portable_fixture();portable=wait(lambda s:any(r['title']=='Portable fixture 0' for r in s['tracks']))
    group=next(r['group'] for r in portable['tracks'] if r['title']=='Portable fixture 0')
    check(next(g['count'] for g in portable['groups'] if g['id']==group)==2 and sum(r.get('group')==group for r in portable['tracks'])==2,'portable group contains exactly its two actual native rows')
    api('music.playlist:'+group)
    original=wait(lambda s:s['kind']=='netmusic' and any(r['source']=='record' or r['source']=='records' for r in s['audio']),20)
    check(not original['capabilities']['pause'] and original['queueSize']==2,'real original player starts a two-track native queue without fabricated pause API')
    next_original=wait(lambda s:s['kind']=='netmusic' and s['queueIndex']==1 and s['title']=='Portable fixture 1',20)
    check(True,'original native loop/EOF advances to the explicit next playlist index')
    wait(lambda s:s['queueSize']==0 and not any(r['source'] in ('record','records') for r in s['audio']),20)
    check(True,'original whole-list final EOF stops the native portable player')
    qa('setup');api('music.local:'+local[0]['id']);wait(own_playing);qa('disconnect');time.sleep(1)
    disconnected=observe('disconnected');check(disconnected['queueSize']==0 and not any(r['own'] for r in disconnected['audio']),'disconnect clears queue and local resources without menu playback')
    qa('reconnect');time.sleep(2);again=wait(lambda s:s['selectedSlot']>=0 and not s['busy'],30)
    check(again['queueSize']==0 and not any(r['own'] for r in again['audio']),'same-player reconnect reloads the local library without resuming a previous queue')
    report['success']=True
except BaseException as error:
    report.update(success=False,error=repr(error),traceback=traceback.format_exc());print(report['traceback'],flush=True)
finally:
    (OWN/'native-music-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'success':report.get('success'),'checks':len(checks),'lab':str(LAB)}),flush=True)
if not report.get('success'):raise SystemExit(1)

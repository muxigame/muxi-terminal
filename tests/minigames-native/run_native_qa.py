"""Run prepared two-client QA on the 131 desktop; normal stop, no kill fallback."""
from __future__ import annotations
import ctypes
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone

REPO=Path(__file__).resolve().parent.parent.parent
FROZEN=Path(r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001')
sys.path.insert(0,str(FROZEN/'appfn-v1/desktop'))
from shared_progress_io import read_shared_text

def write(path,row):
    temporary=path.with_suffix('.tmp')
    temporary.write_text(json.dumps(row,ensure_ascii=False,indent=2),encoding='utf-8')
    os.replace(temporary,path)
def read(path):return json.loads(read_shared_text(path,encoding='utf-8'))

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL':raise RuntimeError('131 only')
    session=ctypes.c_ulong();ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session))
    if session.value==0:raise RuntimeError('Actual desktop session required')
    def live_clients():
        existing=subprocess.run(['powershell.exe','-NoProfile','-Command',"Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^(java|javaw)(\\.exe)?$'} | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"],capture_output=True,text=True,check=True).stdout.strip()
        rows=json.loads(existing) if existing else []
        if isinstance(rows,dict):rows=[rows]
        return [row['ProcessId'] for row in rows if not any(marker in (row.get('CommandLine') or '') for marker in ['nogui','server-launch.args']) and any(marker in (row.get('CommandLine') or '') for marker in ['launch.args','neoforgeclient','forgeclient','net.minecraft.client.main.Main','--gameDir '])]
    prepared=read(REPO/'build/minigames-native/latest-prepared.json')
    home=Path(prepared['home']);coordinator=Path(prepared['coordinator'])
    if (home/'run-result.json').exists() or any(coordinator.glob('ready-*.json')):raise RuntimeError('This prepared instance already ran; results will not be overwritten')
    # Preserve the reviewed selected equipment asset required by actual inventory setup.
    import hashlib
    extras=read(FROZEN/'appfn-v1/desktop/unified-inputs.json')
    gunpack=next(p for p in Path(extras['originalFixDirectory']).glob('*.zip') if hashlib.sha256(p.read_bytes()).hexdigest()==extras['gunSha256'])
    for row in prepared['clients']:
        destination=Path(row['lab'])/'tacz/muxi-phoenix-six-netnew-20261001.zip'
        destination.parent.mkdir(exist_ok=True);shutil.copy2(gunpack,destination)
    dedicated=prepared.get('dedicatedServer')
    if dedicated:
        owner=read(Path(dedicated['sharedBackendOwner']));backend=read(Path(owner['privateReadyPath']))
        launcher=read(Path(owner['home'])/'shared-launcher-preparation.json')
        dotnet=r'C:\Users\ranzh\Documents\Codex\2026-10-02\task-3\tools\dotnet\dotnet.exe'
    processes={};logs={};ids={'host':0,'guest':0,'server':0};checks=[]
    result={'started_utc':datetime.now(timezone.utc).isoformat(),'session':session.value,'prepared':prepared,'checks':checks,'gunpackSha256':extras['gunSha256'],'fakePlayers':False,'syntheticSSO':False,'productionOperations':False}
    def wait_file(path,timeout=120):
        deadline=time.monotonic()+timeout;last=0
        while time.monotonic()<deadline:
            if (coordinator/'cancel-run.json').exists():raise RuntimeError('QA canceled: '+str(read(coordinator/'cancel-run.json')))
            if path.exists():return read(path)
            for role,process in processes.items():
                fatal=coordinator/f'fatal-{role}.json'
                if fatal.exists():raise RuntimeError(read(fatal))
                if process.poll() is not None:raise RuntimeError(f'{role} exited before result: {process.returncode}')
            if time.monotonic()-last>10:print(json.dumps({'waiting':path.name,'pids':{role:p.pid for role,p in processes.items()}}),flush=True);last=time.monotonic()
            time.sleep(.2)
        raise TimeoutError(str(path))
    def command(role,kind,body='',timeout=90):
        ids[role]+=1;ident=ids[role];write(coordinator/f'command-{role}.json',{'id':ident,'type':kind,'body':body})
        data=wait_file(coordinator/f'result-{role}-{ident}.json',timeout)
        if not data.get('ok'):raise RuntimeError(data)
        return data.get('value',data)
    def launch(role):
        until=time.monotonic()+360
        while True:
            if (coordinator/'cancel-run.json').exists():raise RuntimeError('QA canceled: '+str(read(coordinator/'cancel-run.json')))
            for loaded_role,loaded in processes.items():
                if loaded.poll() is not None:raise RuntimeError(f'{loaded_role} exited while waiting for a client slot: {loaded.returncode}')
            slots=live_clients()
            if len(slots)<4:break
            if time.monotonic()>until:raise TimeoutError('Four client slots occupied by PIDs '+str(slots))
            print(json.dumps({'waitingForClientSlot':role,'occupiedPids':slots,'maximum':4}),flush=True);time.sleep(5)
        row=next(r for r in prepared['clients'] if r['role']==role);lab=Path(row['lab'])
        logs[role]=(lab/'boot.log').open('w',encoding='utf-8')
        env={key:value for key,value in os.environ.items() if not key.startswith('MUXI_')}
        if dedicated:
            config={'controller':backend['url'],'authURL':backend['auth_url'],'siteURL':backend['site_url'],'spki':backend['spki'],
                'accountIndex':0 if role=='host' else 1,'role':role,'launcherState':str(lab/'fresh-launcher-state'),
                'report':str(lab/'actual-launcher-result.json'),'java':row['java'],'gameDir':str(lab),'argFile':row['args'],'coordinator':str(coordinator)}
            config_path=lab/'public-launcher-config.json';write(config_path,config)
            env['MUXI_LAUNCHER_QA_CONTROL']=backend['launcher_capability']
            processes[role]=subprocess.Popen([dotnet,launcher['launcherDll'],str(config_path)],cwd=lab,env=env,stdin=subprocess.PIPE,stdout=logs[role],stderr=subprocess.STDOUT,text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW)
            processes[role].stdin.write('GO\n');processes[role].stdin.flush()
        else:processes[role]=subprocess.Popen([row['java'],'@'+row['args']],cwd=lab,env=env,stdin=subprocess.DEVNULL,stdout=logs[role],stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        write(home/'active.json',{'pids':{key:p.pid for key,p in processes.items()},'session':session.value,'home':str(home)})
        print(json.dumps({'launch':role,'pid':processes[role].pid,'visibleMinecraft':True}),flush=True)
    def same_inventory_and_location(before,after):
        assert len(before['players'])==len(after['players'])==2
        for original in before['players']:
            player=next(p for p in after['players'] if p['uuid']==original['uuid'])
            assert player['inventory']==original['inventory'],(original['name'],'inventory changed before start')
            assert player['dimension']==original['dimension'],(original['name'],'dimension changed before start')
            assert all(abs(player[axis]-original[axis])<.6 for axis in ['x','y','z']),(original['name'],'position changed before start')
            assert player['returnsPending']==original['returnsPending']
    try:
        # Reserve enough visible-client capacity for a genuine pair before loading
        # either JVM; do not occupy the fourth slot indefinitely with only a host.
        until=time.monotonic()+360
        while len(live_clients())>2:
            if (coordinator/'cancel-run.json').exists():raise RuntimeError('QA canceled: '+str(read(coordinator/'cancel-run.json')))
            if time.monotonic()>until:raise TimeoutError('Two client slots unavailable; existing PIDs '+str(live_clients()))
            print(json.dumps({'waitingForTwoClientSlots':live_clients(),'maximum':4}),flush=True);time.sleep(5)
        if dedicated:
            server_root=Path(dedicated['root']);logs['server']=(server_root/'boot.log').open('w',encoding='utf-8')
            env={key:value for key,value in os.environ.items() if not key.startswith('MUXI_')}
            env.update(MUXI_GAME_SOCIAL_ENABLED='1',MUXI_GAME_SOCIAL_URL=backend['site_url']+'/api/internal/game/',MUXI_GAME_SOCIAL_KEY=backend['social_key'],MUXI_GAME_PLATFORM_URL=backend['site_url']+'/api/internal/game/',MUXI_GAME_PLATFORM_KEY=backend['game_key'])
            processes['server']=subprocess.Popen([dedicated['java'],'@'+dedicated['args'],'nogui'],cwd=server_root,env=env,stdin=subprocess.PIPE,stdout=logs['server'],stderr=subprocess.STDOUT,text=True,encoding='utf-8',creationflags=subprocess.CREATE_NO_WINDOW)
            wait_file(coordinator/'server-ready.json',900)
        launch('host');launch('guest')
        wait_file(coordinator/'server-ready.json',900);wait_file(coordinator/'ready-host.json',900)
        wait_file(coordinator/'ready-guest.json',900)
        if '--await-ui-window' in sys.argv:
            write(coordinator/'ready-for-ui.json',{'pids':{role:p.pid for role,p in processes.items()},'ready':True,'requestedSeconds':120,'grantFile':str(coordinator/'ui-go.json')})
            wait_file(coordinator/'ui-go.json',360)
        time.sleep(2)
        observer='server' if dedicated else 'host'
        baseline=command(observer,'observe');write(home/'baseline-real-players.json',baseline)
        assert len(baseline['players'])==2 and all(p['connected'] for p in baseline['players'])
        assert any('127.0.0.1' in p['transport'] for p in baseline['players'])
        checks.append('Two genuinely connected players, including a TCP localhost guest; no fabricated ServerPlayers')
        if dedicated:
            for attempt in range(100):
                baseline=command(observer,'observe')
                if all(p['admittedUid']==int(p['name']) and p['socialUid']==int(p['name']) for p in baseline['players']):break
                time.sleep(.2)
            assert all(p['admittedUid']==int(p['name']) and p['socialUid']==int(p['name']) for p in baseline['players']), 'Actual login and social proof not both verified'
            checks.append('Both actual Core LoginGate admissions and independently consumed terminal social proofs match each real connection')
            write(home/'baseline-real-players.json',baseline)
        for role in ['host','guest']:command(role,'open')
        command('host','js',"await wait(1000);return {snapshot:await q('games.snapshot'),clean:document.getElementById('mg-games').hidden&&document.getElementById('mg-room-invitations').hidden&&!document.querySelector('[data-mg-choice]')};")
        for game in ['outbreak','zombie-challenge']:
            print(json.dumps({'testing':game}),flush=True)
            created=command('host','js',"document.getElementById('mg-back').click();document.querySelector('[data-mg-create]').click();document.querySelector('[data-mg-game=\""+game+"\"]').click();const maps=[...document.querySelectorAll('[data-mg-map]')];const map=maps.find(b=>['campaign_city_zero','metro_escape','research_lab'].includes(b.dataset.mgMap))||maps[0];if(!map)throw Error('No map');map.click();document.querySelector('[data-mg-action]').click();document.querySelector('[data-mg-action]').click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');const g=s.games.find(g=>g.id==='"+game+"');if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed'&&g.state.rooms.some(r=>r.mine)){return {snapshot:s,room:g.state.rooms.find(r=>r.mine),selected:map.dataset.mgMap};}}throw Error('Create not confirmed');")
            write(home/(game+'-create.json'),created)
            assert created['room']['phase'] in ['WAITING','BUILDING','LOBBY']
            before_join=command(observer,'observe');same_inventory_and_location(baseline,before_join)
            checks.append(game+': UI create confirmed a waiting room without teleport or inventory changes')
            if dedicated:
                friend_list=command('host','js',"document.getElementById('friendsInviteFriends').click();for(let i=0;i<100;i++){await wait(200);const peer=document.querySelector('[data-invite-uid=\"10001\"]');if(peer&&!peer.disabled)return {friendUid:peer.dataset.inviteUid,source:'actual authenticated Web friend list',runtime:await q('friends.invites.snapshot')};}throw Error('Actual online friend absent from invitation UI');")
                write(home/(game+'-actual-friend-list.json'),friend_list)
                checks.append(game+': actual authenticated A/B friendship appears in the room invitation UI')
                invited=command('host','js',"document.getElementById('friendsInviteOnline').click();await wait(500);const peer=document.querySelector('[data-invite-uid=\"10001\"]');if(!peer||peer.disabled)throw Error('No actual online peer');peer.click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');const ticket=s.social?.invitations?.find(t=>t.game==='"+game+"'&&t.status==='PENDING');if(ticket)return {snapshot:s,ticket};}throw Error('Actual online invitation not confirmed');")
            else:invited=command('host','js',"await window.MuxiMinigamesApp.activate();const prior=(await q('games.snapshot')).operation?.request;const invite=[...document.querySelectorAll('[data-mg-action]')].find(b=>!b.disabled&&b.textContent.includes('\u9080\u8bf7'));if(!invite)throw Error('No native online invitation control');invite.click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');if(s.operation?.request===prior)continue;if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed')return s;}throw Error('Online invitation not confirmed');")
            write(home/(game+'-online-invite.json'),invited)
            checks.append(game+': real online guest invitation sent through the room UI and confirmed by the server')
            joined=command('guest','js',"document.getElementById('mg-back').click();await window.MuxiMinigamesApp.activate();let join;for(let i=0;i<100;i++){join="+("document.querySelector('[data-mg-invite-op=\"accept\"]')" if dedicated else "[...document.querySelectorAll('[data-mg-action]')].find(b=>!b.disabled)")+";if(join&&!join.disabled)break;await wait(200);await window.MuxiMinigamesApp.activate();}if(!join)throw Error('No valid room invitation/join action');join.click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');const g=s.games.find(g=>g.id==='"+game+"');if(s.operation?.status==='failed')throw Error(s.operation.notice);if(g.state.rooms.some(r=>r.mine))return {snapshot:s,room:g.state.rooms.find(r=>r.mine)};}throw Error('Join not confirmed');")
            write(home/(game+'-join.json'),joined)
            waiting=command(observer,'observe');same_inventory_and_location(baseline,waiting)
            checks.append(game+': second real client joined waiting room; both retained original world and inventory')
            command('host','js',"await window.MuxiMinigamesApp.activate();for(let i=0;i<150;i++){await wait(200);await window.MuxiMinigamesApp.activate();const start=[...document.querySelectorAll('[data-mg-action]')].find(b=>b.textContent.includes('\u5f00\u59cb'));if(start&&!start.disabled){const prior=(await q('games.snapshot')).operation?.request;start.click();for(let j=0;j<50;j++){await wait(200);const s=await q('games.snapshot');if(s.operation?.request===prior)continue;if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed')return s;}throw Error('Start not confirmed');}}throw Error('Host start unavailable');",timeout=120)
            started=command(observer,'observe');write(home/(game+'-started-real-players.json'),started)
            assert all(p['dimension']!=next(b for b in baseline['players'] if b['uuid']==p['uuid'])['dimension'] for p in started['players'])
            checks.append(game+': only explicit host Start sent both real players into the map')
            for role in ['guest','host']:
                command(role,'js',"await window.MuxiMinigamesApp.activate();const leave=[...document.querySelectorAll('[data-mg-action]')].find(b=>/\u9000\u51fa|\u79bb\u5f00/.test(b.textContent));if(!leave)throw Error('No leave action');leave.click();document.getElementById('mg-confirm-ok').click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed'&&!s.activeGame)return s;}throw Error('Leave not confirmed');")
            restored=command(observer,'observe');write(home/(game+'-restored-real-players.json'),restored)
            same_inventory_and_location(baseline,restored)
            checks.append(game+': confirmed Leave restored both original inventories and locations')
        result['success']=True
        result['unverified']=['Full in-map campaign completion and durable settlement/duplicate results still require their own real observations'] if dedicated else ['Trusted account friends and SSO are unavailable in isolated offline QA; no trust grants enabled']
    except Exception as error:
        result['success']=False;result['error']=str(error);print('REAL_QA_FAILED '+str(error),flush=True)
    finally:
        for role,process in processes.items():
            if role=='server':continue
            if process.poll() is None:
                ids[role]+=1;write(coordinator/f'command-{role}.json',{'id':ids[role],'type':'stop'})
        for role,process in processes.items():
            if role=='server':continue
            try:process.wait(timeout=120)
            except subprocess.TimeoutExpired:result.setdefault('normalCloseBlocked',[]).append({'role':role,'pid':process.pid})
            result.setdefault('exitCodes',{})[role]=process.poll();logs[role].close()
        if 'server' in processes:
            server=processes['server']
            if server.poll() is None:server.stdin.write('stop\n');server.stdin.flush()
            try:server.wait(timeout=180)
            except subprocess.TimeoutExpired:result.setdefault('normalCloseBlocked',[]).append({'role':'server','pid':server.pid})
            result.setdefault('exitCodes',{})['server']=server.poll();logs['server'].close()
        result['clientStarted']=any(role in processes for role in ['host','guest'])
        result['normalExit']=result['clientStarted'] and all(code==0 for code in result.get('exitCodes',{}).values())
        result['finished_utc']=datetime.now(timezone.utc).isoformat();write(home/'run-result.json',result)
        print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
    if not result.get('success') or not result.get('normalExit'):raise SystemExit(1)

if __name__=='__main__':main()

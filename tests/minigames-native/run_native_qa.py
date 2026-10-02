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
    existing=subprocess.run(['powershell.exe','-NoProfile','-Command',"Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^(java|javaw)(\\.exe)?$'} | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"],capture_output=True,text=True,check=True).stdout.strip()
    rows=json.loads(existing) if existing else []
    if isinstance(rows,dict):rows=[rows]
    blocking=[row['ProcessId'] for row in rows if 'nogui' not in (row.get('CommandLine') or '')]
    if blocking:
        write(REPO/'build/minigames-native/blocked-start.json',{'clientStarted':False,'blockingPids':blocking,'reason':'Other QA clients own the desktop; no extra instances or focus changes performed','utc':datetime.now(timezone.utc).isoformat()})
        raise RuntimeError('Another QA client is active; no new clients started')
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
    processes={};logs={};ids={'host':0,'guest':0};checks=[]
    result={'started_utc':datetime.now(timezone.utc).isoformat(),'session':session.value,'prepared':prepared,'checks':checks,'gunpackSha256':extras['gunSha256'],'fakePlayers':False,'syntheticSSO':False,'productionOperations':False}
    def wait_file(path,timeout=120):
        deadline=time.monotonic()+timeout;last=0
        while time.monotonic()<deadline:
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
        row=next(r for r in prepared['clients'] if r['role']==role);lab=Path(row['lab'])
        logs[role]=(lab/'boot.log').open('w',encoding='utf-8')
        env={key:value for key,value in os.environ.items() if not key.startswith('MUXI_')}
        processes[role]=subprocess.Popen([row['java'],'@'+row['args']],cwd=lab,env=env,stdin=subprocess.DEVNULL,stdout=logs[role],stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
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
        launch('host');wait_file(coordinator/'server-ready.json',360);wait_file(coordinator/'ready-host.json',60)
        launch('guest');wait_file(coordinator/'ready-guest.json',360)
        time.sleep(2)
        baseline=command('host','observe');write(home/'baseline-real-players.json',baseline)
        assert len(baseline['players'])==2 and all(p['connected'] for p in baseline['players'])
        assert any('127.0.0.1' in p['transport'] for p in baseline['players'])
        checks.append('Two genuinely connected players, including a TCP localhost guest; no fabricated ServerPlayers')
        for role in ['host','guest']:command(role,'open')
        command('host','js',"await wait(1000);return {snapshot:await q('games.snapshot'),clean:document.getElementById('mg-games').hidden&&document.getElementById('mg-room-invitations').hidden&&!document.querySelector('[data-mg-choice]')};")
        for game in ['outbreak','zombie-challenge']:
            print(json.dumps({'testing':game}),flush=True)
            created=command('host','js',"document.getElementById('mg-back').click();document.querySelector('[data-mg-create]').click();document.querySelector('[data-mg-game=\""+game+"\"]').click();const maps=[...document.querySelectorAll('[data-mg-map]')];const map=maps.find(b=>['campaign_city_zero','metro_escape','research_lab'].includes(b.dataset.mgMap))||maps[0];if(!map)throw Error('No map');map.click();document.querySelector('[data-mg-action]').click();document.querySelector('[data-mg-action]').click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');const g=s.games.find(g=>g.id==='"+game+"');if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed'&&g.state.rooms.some(r=>r.mine)){return {snapshot:s,room:g.state.rooms.find(r=>r.mine),selected:map.dataset.mgMap};}}throw Error('Create not confirmed');")
            write(home/(game+'-create.json'),created)
            assert created['room']['phase'] in ['WAITING','BUILDING','LOBBY']
            before_join=command('host','observe');same_inventory_and_location(baseline,before_join)
            checks.append(game+': UI create confirmed a waiting room without teleport or inventory changes')
            invited=command('host','js',"await window.MuxiMinigamesApp.activate();const prior=(await q('games.snapshot')).operation?.request;const invite=[...document.querySelectorAll('[data-mg-action]')].find(b=>!b.disabled&&b.textContent.includes('\u9080\u8bf7'));if(!invite)throw Error('No native online invitation control');invite.click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');if(s.operation?.request===prior)continue;if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed')return s;}throw Error('Online invitation not confirmed');")
            write(home/(game+'-online-invite.json'),invited)
            checks.append(game+': real online guest invitation sent through the room UI and confirmed by the server')
            joined=command('guest','js',"document.getElementById('mg-back').click();await window.MuxiMinigamesApp.activate();const buttons=[...document.querySelectorAll('[data-mg-action]')];const join=buttons.find(b=>!b.disabled);if(!join)throw Error('No joinable room');join.click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');const g=s.games.find(g=>g.id==='"+game+"');if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed'&&g.state.rooms.some(r=>r.mine))return {snapshot:s,room:g.state.rooms.find(r=>r.mine)};}throw Error('Join not confirmed');")
            write(home/(game+'-join.json'),joined)
            waiting=command('host','observe');same_inventory_and_location(baseline,waiting)
            checks.append(game+': second real client joined waiting room; both retained original world and inventory')
            command('host','js',"await window.MuxiMinigamesApp.activate();for(let i=0;i<150;i++){await wait(200);await window.MuxiMinigamesApp.activate();const start=[...document.querySelectorAll('[data-mg-action]')].find(b=>b.textContent.includes('\u5f00\u59cb'));if(start&&!start.disabled){const prior=(await q('games.snapshot')).operation?.request;start.click();for(let j=0;j<50;j++){await wait(200);const s=await q('games.snapshot');if(s.operation?.request===prior)continue;if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed')return s;}throw Error('Start not confirmed');}}throw Error('Host start unavailable');",timeout=120)
            started=command('host','observe');write(home/(game+'-started-real-players.json'),started)
            assert all(p['dimension']!=next(b for b in baseline['players'] if b['uuid']==p['uuid'])['dimension'] for p in started['players'])
            checks.append(game+': only explicit host Start sent both real players into the map')
            for role in ['guest','host']:
                command(role,'js',"await window.MuxiMinigamesApp.activate();const leave=[...document.querySelectorAll('[data-mg-action]')].find(b=>/\u9000\u51fa|\u79bb\u5f00/.test(b.textContent));if(!leave)throw Error('No leave action');leave.click();document.getElementById('mg-confirm-ok').click();for(let i=0;i<100;i++){await wait(200);const s=await q('games.snapshot');if(s.operation?.status==='failed')throw Error(s.operation.notice);if(s.operation?.status==='completed'&&!s.activeGame)return s;}throw Error('Leave not confirmed');")
            restored=command('host','observe');write(home/(game+'-restored-real-players.json'),restored)
            same_inventory_and_location(baseline,restored)
            checks.append(game+': confirmed Leave restored both original inventories and locations')
        result['success']=True
        result['unverified']=['Trusted account friends and SSO are unavailable in isolated offline QA; no trust grants enabled']
    except Exception as error:
        result['success']=False;result['error']=str(error);print('REAL_QA_FAILED '+str(error),flush=True)
    finally:
        for role,process in processes.items():
            if process.poll() is None:
                ids[role]+=1;write(coordinator/f'command-{role}.json',{'id':ids[role],'type':'stop'})
        for role,process in processes.items():
            try:process.wait(timeout=120)
            except subprocess.TimeoutExpired:result.setdefault('normalCloseBlocked',[]).append({'role':role,'pid':process.pid})
            result.setdefault('exitCodes',{})[role]=process.poll();logs[role].close()
        result['normalExit']=all(code==0 for code in result.get('exitCodes',{}).values())
        result['finished_utc']=datetime.now(timezone.utc).isoformat();write(home/'run-result.json',result)
        print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
    if not result.get('success') or not result.get('normalExit'):raise SystemExit(1)

if __name__=='__main__':main()

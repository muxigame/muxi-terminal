from pathlib import Path
import concurrent.futures, hashlib, json, os, shutil, subprocess, sys, time

sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[3]
TASK=Path(__file__).parent
OUT=Path(os.environ.get('MUXI_SHOP_NATIVE_EVIDENCE',str(ROOT/'muxi-terminal/build/shop-native-evidence')))
STAGE=Path(r'C:\Users\ranzh\AppData\Local\Temp\jui14-frozen')
LOCK=ROOT/'better-mc-remake/pack/candidates/joint-ui-parkour-20261013/input-lock.json'
EXPECTED='e036daf0aff1236c0fc559b0aeef9e85faab1975a33dff29a242c105a2236917'

def digest(p,alg):
    with p.open('rb') as f:return hashlib.file_digest(f,alg).hexdigest()

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert digest(LOCK,'sha256')==EXPECTED
    lock=json.loads(LOCK.read_text(encoding='utf-8'))
    assert len(lock['serverMods'])==352 and len(lock['clientFiles'])==9259
    prior=ROOT/'better-mc-remake/build/local-mc-debug/music043-r7-20261014-0407'
    roots=[Path(lock['releaseDeltaRoot'])/'client-overlay',Path(lock['supplement28Root']),
           Path(r'C:\Users\ranzh\Documents\Codex\2026-10-09\task-3\fullpack-frozen\client'),
           prior/'host',ROOT/'_client_test/game',ROOT/'bmc5client']
    print(json.dumps({'stage':str(STAGE),'clientBytes':sum(x['size'] for x in lock['clientFiles']),
        'diskFree':shutil.disk_usage(str(STAGE.parent)).free,'maxClientRelativeLength':max(len(x['path']) for x in lock['clientFiles']),
        'roots':[{'path':str(p),'exists':p.exists()} for p in roots]},ensure_ascii=False),flush=True)
    def client(row):
        choices=([Path(row['localFrozenFile'])] if row.get('localFrozenFile') else [])+[p/row['path'] for p in roots]
        for p in choices:
            if p.is_file() and p.stat().st_size==row['size'] and digest(p,'sha1')==row['sha1']:
                dest=STAGE/'client'/row['path'];dest.parent.mkdir(parents=True,exist_ok=True)
                if not dest.exists() or digest(dest,'sha1')!=row['sha1']:shutil.copyfile(p,dest)
                assert digest(dest,'sha1')==row['sha1']
                return {'path':row['path'],'sourcePath':str(p),'frozenPath':str(dest),'sha1':row['sha1']}
        return {'path':row['path'],'missing':True,'tried':[str(p) for p in choices]}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        clients=[]
        for idx,item in enumerate(ex.map(client,lock['clientFiles']),1):
            clients.append(item)
            if idx%1500==0:print('CLIENT_FROZEN',idx,flush=True)
    server=[]
    for row in lock['serverMods']:
        choices=[ROOT/'better-mc-remake/pack/staging/joint-ui-parkour-20261013/server-overlay/mods'/row['name'],
                 Path(lock['releaseDeltaRoot'])/'server-overlay/mods'/row['name'],prior/'server/mods'/row['name'],
                 STAGE/'client/mods'/row['name'],ROOT/'bmc5server/mods'/row['name'],
                 Path(r'C:\Users\ranzh\Documents\Codex\2026-10-09\task-3\fullpack-frozen\server\mods')/row['name']]
        source=next((p for p in choices if p.is_file() and p.stat().st_size==row['size'] and digest(p,'sha256')==row['sha256']),None)
        if source is None:server.append({'path':'mods/'+row['name'],'missing':True});continue
        dest=STAGE/'server/mods'/row['name'];dest.parent.mkdir(parents=True,exist_ok=True)
        if not dest.exists() or digest(dest,'sha256')!=row['sha256']:shutil.copyfile(source,dest)
        assert digest(dest,'sha256')==row['sha256']
        server.append({'path':'mods/'+row['name'],'sourcePath':str(source),'frozenPath':str(dest),'sha256':row['sha256']})
    missing=[x for x in clients+server if x.get('missing')]
    receipt={'inputLock':str(LOCK),'inputLockSha256':EXPECTED,'client':clients,'server':server,'missing':missing,
             'complete':not missing,'mcStarted':False,'sourceCopies':False,'stage':str(STAGE)}
    (OUT/'frozen-inputs.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'complete':not missing,'clientFiles':len(clients),'serverMods':len(server),'missing':missing},ensure_ascii=False,indent=2),flush=True)
    if missing:return 1
    check=subprocess.run([sys.executable,str(LOCK.with_name('verify-isolated-inputs.py')),'--server',str(STAGE/'server'),'--client',str(STAGE/'client')],capture_output=True,encoding='utf-8',errors='replace')
    (OUT/'full-input-check.json').write_text(check.stdout,encoding='utf-8')
    print(check.stdout,flush=True)
    if check.returncode:print(check.stderr,flush=True)
    return check.returncode

if __name__=='__main__':sys.exit(main())

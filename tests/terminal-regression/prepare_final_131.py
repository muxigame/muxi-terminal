"""Offline compilation and exact pack preparation only. Never launches a game/client."""
from __future__ import annotations
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,urllib.parse,urllib.request
OWN=Path(r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001')
ROOT=Path(r'C:\Users\ranzh\workspace\dev\muxigame')
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
COMMITS={'muxi-minigames':'4cac96a711bd077b5b8b2452f418a16866c3d34f','muxi-zombie-challenge':'a3e70ae03be27942cbd4f46c08b0cff48f92081f','muxi-game-core':'030fad9714af275f9ea6c89457f4847d8052a428','muxi-outbreak':'148e436b8209c77fed4ba4b6409e799277fd66e5','muxi-terminal':'bc415775dd50762262098d6a41f0a942df7ef157'}
def digest(path,kind='sha256'):return hashlib.new(kind,path.read_bytes()).hexdigest()
def command(args,cwd=None):return subprocess.run(list(map(str,args)),cwd=cwd,check=True)
def main():
    if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL':raise SystemExit('131 only')
    sys.stdout.reconfigure(encoding='utf-8')
    report={'productCommits':COMMITS,'artifacts':{},'clientStarted':False,'productionChanged':False}
    for name,commit in COMMITS.items():
        repo=OWN/name
        command(['git','-C',repo,'merge-base','--is-ancestor',commit,'HEAD'])
        difference=subprocess.check_output(['git','-C',str(repo),'diff',commit,'--','src','mod.json'],encoding='utf-8')
        dirty=subprocess.check_output(['git','-C',str(repo),'status','--porcelain','--','src','mod.json'],encoding='utf-8')
        if difference or dirty:raise SystemExit('Product sources changed: '+name)
        args=[sys.executable,repo/'build.py','--server',ROOT/'bmc5server','--java-home',JDK]
        if name!='muxi-outbreak':args+=['--client-game',ROOT/'_client_test/game']
        if name in ('muxi-game-core','muxi-zombie-challenge','muxi-terminal'):args+=['--pack-mods',ROOT/'better-mc-remake/pack/staging/files/mods']
        if name!='muxi-terminal':args+=['--test']
        if name=='muxi-outbreak':
            dep=json.loads((repo/'dependencies.json').read_text(encoding='utf-8'))['externalDownloads'][0]
            source=ROOT/'bmc5server/mods'/dep['file']['filename']
            if not source.is_file() or digest(source)!=dep['sha256']:raise SystemExit('Pinned installed equipment dependency unavailable')
            target=repo/'build/equipment-research';target.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target/source.name)
        command(args,repo)
        release=json.loads((repo/'build/release.json').read_text(encoding='utf-8'))
        artifact=repo/'build/libs'/release['artifact']
        report['artifacts'][name]={'path':str(artifact),'name':artifact.name,'sha256':digest(artifact),'sha1':digest(artifact,'sha1'),'size':artifact.stat().st_size}
    base=json.loads((Path(__file__).parent/'release-baseline.json').read_text(encoding='utf-8'))
    destination=OWN/'candidate-pack';destination.mkdir(exist_ok=True)
    oldprefix=('muxi-game-core-','muxi-terminal-','muxi-outbreak-','muxi-minigames-','muxi-zombie-challenge-')
    expected=[]
    for entry in base['files']:
        relative=Path(entry['path'])
        if relative.is_absolute() or '..' in relative.parts:raise SystemExit('Unsafe baseline path')
        if relative.name.startswith(oldprefix):continue
        if relative.parts[0] not in ('mods','config','shaderpacks','resourcepacks','defaultconfigs','kubejs'):continue
        target=destination/relative;target.parent.mkdir(parents=True,exist_ok=True)
        if not target.is_file() or digest(target,'sha1')!=entry['sha1']:
            sources=[ROOT/'better-mc-remake/pack/staging/files'/relative,ROOT/'better-mc-remake/pack/source/Better MC Remake [FORGE]'/relative]
            source=next((p for p in sources if p.is_file() and digest(p,'sha1')==entry['sha1']),None)
            if source:shutil.copy2(source,target)
            else:
                with urllib.request.urlopen('https://muxigame-prod-static-cn.oss-cn-hangzhou.aliyuncs.com/bmc/release/latest/files/'+urllib.parse.quote(entry['path']),timeout=30) as response:data=response.read()
                if hashlib.sha1(data).hexdigest()!=entry['sha1']:raise SystemExit('Published baseline changed: '+entry['path'])
                target.write_bytes(data)
        if relative.parts[0]=='mods' and relative.suffix=='.jar':expected.append(entry)
    for name,item in report['artifacts'].items():
        shutil.copy2(item['path'],destination/'mods'/item['name'])
        if name!='muxi-terminal':expected.append({'path':'mods/'+item['name'],'sha1':item['sha1'],'policy':'Managed'})
    (OWN/'release-lock.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    (OWN/'expected-pack.json').write_text(json.dumps({'referencePack':'1.4.26','files':expected},ensure_ascii=False,indent=2),encoding='utf-8')
    command([sys.executable,Path(__file__).parent/'run_131_qa.py','preflight','--prepare-only'])
    command([sys.executable,Path(__file__).parent/'run_131_qa.py','full','--prepare-only'])
    command([sys.executable,Path(__file__).parent/'run_131_qa.py','firstperson','--prepare-only'])
    print(json.dumps(report,ensure_ascii=False),flush=True)
if __name__=='__main__':main()

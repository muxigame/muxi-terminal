"""Offline compilation and exact pack preparation only. Never launches a game/client."""
from __future__ import annotations
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,urllib.parse,urllib.request
OWN=Path(r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001')
ROOT=Path(r'C:\Users\ranzh\workspace\dev\muxigame')
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
COMMITS={'muxi-minigames': 'de711f9fe93a614efee9d3c95d6d37e6d31b9238', 'muxi-zombie-challenge': 'b39845e8f3a3209b3754e703dbba61a0d33b4e1d', 'muxi-game-core': '5c45ca34877a3ead61a71a0bba794a2491e3d937', 'muxi-outbreak': 'afbeb63afd735499e1ef9d1a039f681c84aad208', 'muxi-terminal': 'bc415775dd50762262098d6a41f0a942df7ef157'}
PACK_COMMIT='SET_AFTER_REVIEWED_SOURCE_COMMIT'
def digest(path,kind='sha256'):return hashlib.new(kind,path.read_bytes()).hexdigest()
def command(args,cwd=None):return subprocess.run(list(map(str,args)),cwd=cwd,check=True)
def main():
    if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL':raise SystemExit('131 only')
    sys.stdout.reconfigure(encoding='utf-8')
    report={'productCommits':COMMITS,'artifacts':{},'clientStarted':False,'productionChanged':False}
    previous=json.loads((OWN/'release-lock.json').read_text(encoding='utf-8')) if (OWN/'release-lock.json').exists() else {}
    for name,commit in COMMITS.items():
        repo=OWN/name
        command(['git','-C',repo,'merge-base','--is-ancestor',commit,'HEAD'])
        difference=subprocess.check_output(['git','-C',str(repo),'diff',commit,'--','src','mod.json'],encoding='utf-8')
        dirty=subprocess.check_output(['git','-C',str(repo),'status','--porcelain','--','src','mod.json'],encoding='utf-8')
        if difference or dirty:raise SystemExit('Product sources changed: '+name)
        old=previous.get('artifacts',{}).get(name)
        if old and previous.get('productCommits',{}).get(name)==commit and Path(old['path']).is_file() and digest(Path(old['path']))==old['sha256']:
            report['artifacts'][name]=old
            print('Retained exact unchanged 131 artifact: '+name,flush=True)
            continue
        args=[sys.executable,repo/'build.py','--server',ROOT/'bmc5server','--java-home',JDK]
        if name!='muxi-outbreak':args+=['--client-game',ROOT/'_client_test/game']
        if name in ('muxi-game-core','muxi-zombie-challenge','muxi-terminal'):args+=['--pack-mods',ROOT/'better-mc-remake/pack/staging/files/mods']
        if name!='muxi-terminal':args+=['--test']
        if name=='muxi-outbreak':
            dep=json.loads((repo/'dependencies.json').read_text(encoding='utf-8'))['externalDownloads'][0]
            source=ROOT/'bmc5server/mods'/dep['file']['filename']
            target=repo/'build/equipment-research';target.mkdir(parents=True,exist_ok=True)
            pinned=target/dep['file']['filename']
            if source.is_file() and digest(source)==dep['sha256']:shutil.copy2(source,pinned)
            elif not pinned.is_file() or digest(pinned)!=dep['sha256']:
                # Existing published-server integration dependency, same official pinned URL as task2.
                with urllib.request.urlopen(dep['file']['url'],timeout=30) as response:data=response.read()
                if hashlib.sha256(data).hexdigest()!=dep['sha256']:raise SystemExit('Pinned equipment dependency hash mismatch')
                pinned.write_bytes(data)
        command(args,repo)
        release=json.loads((repo/'build/release.json').read_text(encoding='utf-8'))
        artifact=repo/'build/libs'/release['artifact']
        report['artifacts'][name]={'path':str(artifact),'name':artifact.name,'sha256':digest(artifact),'sha1':digest(artifact,'sha1'),'size':artifact.stat().st_size}
    platform=OWN/'better-mc-remake'
    command(['git','-C',platform,'merge-base','--is-ancestor',PACK_COMMIT,'HEAD'])
    changed=subprocess.check_output(['git','-C',str(platform),'diff',PACK_COMMIT,'--','pack/patches/champions-companions/src','pack/packspec.json','pack/curated-gunpacks'],encoding='utf-8')
    if changed:raise SystemExit('Added release source changed')
    balance=platform/'pack/patches/champions-companions'
    core=Path(report['artifacts']['muxi-game-core']['path'])
    command([sys.executable,balance/'build_companions.py','--server',ROOT/'bmc5server','--java-home',JDK,'--core-jar',core])
    release=json.loads((balance/'build/release.json').read_text(encoding='utf-8'));artifact=balance/'build'/release['artifact']
    report['artifacts']['muxi-champion-companions']={'path':str(artifact),'name':artifact.name,'sha256':digest(artifact),'sha1':digest(artifact,'sha1'),'size':artifact.stat().st_size}
    report['productCommits']={**COMMITS,'muxi-champion-companions':PACK_COMMIT}
    base=json.loads((Path(__file__).parent/'release-baseline.json').read_text(encoding='utf-8'))
    destination=OWN/'candidate-pack';destination.mkdir(exist_ok=True)
    oldprefix=('muxi-game-core-','muxi-terminal-','muxi-outbreak-','muxi-minigames-','muxi-zombie-challenge-','muxi-champion-companions-')
    for old in (destination/'mods').glob('*.jar'):
        if old.name.startswith(oldprefix):old.unlink()
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
    gun=json.loads((platform/'pack/curated-gunpacks/phoenix-nine.json').read_text(encoding='utf-8'))
    asset=OWN/'release-assets'/gun['packPath']
    if not asset.is_file() or digest(asset)!=gun['sha256']:raise SystemExit('Selected nine-gun binary asset missing or changed')
    gunTarget=destination/gun['packPath'];gunTarget.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(asset,gunTarget)
    report['additionalFiles']=[{'path':gun['packPath'],'sha256':gun['sha256'],'size':asset.stat().st_size}]
    fps=destination/'config/sodiumextras-client.toml'
    if fps.is_file():
        import re
        fps.write_text(re.sub(r'(fpsDisplay\s*=\s*)"[^"]*"',r'\1"OFF"',fps.read_text(encoding='utf-8')),encoding='utf-8')
    report['fpsSeed']='OFF; one-time launcher overlay migration, not permanent enforcement'
    dependency=OWN/'muxi-outbreak/build/equipment-research'/dep['file']['filename']
    shutil.copy2(dependency,destination/'mods'/dependency.name)
    expected.append({'path':'mods/'+dependency.name,'sha1':digest(dependency,'sha1'),'policy':'Managed'})
    report['existingProductionDependency']={'name':dependency.name,'sha256':digest(dependency),'newGunPacksAdded':False,'publicRedistributionApprovedByThisScript':False}
    (OWN/'release-lock.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    (OWN/'expected-pack.json').write_text(json.dumps({'referencePack':'1.4.26','files':expected},ensure_ascii=False,indent=2),encoding='utf-8')
    command([sys.executable,Path(__file__).parent/'run_131_qa.py','preflight','--prepare-only'])
    command([sys.executable,Path(__file__).parent/'run_131_qa.py','full','--prepare-only'])
    command([sys.executable,Path(__file__).parent/'run_131_qa.py','firstperson','--prepare-only'])
    print(json.dumps(report,ensure_ascii=False),flush=True)
if __name__=='__main__':main()

"""Prepare isolated two-client Minecraft/MCEF QA, without launching either client.

Uses existing installed libraries and the reviewed QA-only native downloader/WMI
timeout mixins. No fabricated players, authentication grants, or focus bypass.
"""
from __future__ import annotations
import ctypes
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import socket
import sys
import tempfile
import uuid
import zipfile
from datetime import datetime, timezone

HERE=Path(__file__).resolve().parent
REPO=HERE.parent.parent
WORK=Path(r'C:\Users\ranzh\workspace\dev\muxigame')
FROZEN=Path(r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001')
DESKTOP=FROZEN/'appfn-v1/desktop'
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
sys.path.insert(0,str(DESKTOP))
import run_131_qa as reviewed

def load_build():
    spec=importlib.util.spec_from_file_location('terminal_native_build',REPO/'build.py')
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    build=load_build()
    compiler,runtime=build.java_tools(JDK)
    game=WORK/'_client_test/game'
    pack=FROZEN/'candidate-pack'
    extras=json.loads((DESKTOP/'unified-inputs.json').read_text(encoding='utf-8'))
    home=REPO/'build/minigames-native'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')
    home.mkdir(parents=True)
    coordinator=home/'coordinator';coordinator.mkdir()
    artifact_dir=home/'pinned-artifacts';artifact_dir.mkdir()
    # Pin the existing compiled terminal, then overlay only this owner's UI resources.
    terminal_source=REPO/'build/libs/muxi-terminal-0.2.4-task14-social-qa.1.jar'
    terminal=artifact_dir/terminal_source.name
    own_resources=['apps/minigames/app.js','apps/minigames/app.css','apps/minigames/model.js','friends-invites.js','index.html']
    prefix='assets/muxi_terminal/html/terminal/'
    overrides={prefix+name:(REPO/'src/main/resources'/prefix/name).read_bytes() for name in own_resources}
    with zipfile.ZipFile(terminal_source) as source,zipfile.ZipFile(terminal,'w',zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():target.writestr(entry,overrides.pop(entry.filename,source.read(entry.filename)))
        for name,data in overrides.items():target.writestr(name,data)
    artifacts={'muxi_terminal':terminal}
    for row in extras['artifacts']:
        if row['id']=='muxi_terminal':continue
        source=Path(extras['stage'])/row['path']
        if row['id']=='muxi_minigames':source=REPO.parent/'muxi-minigames/build/libs'/row['artifact']
        elif row['id']=='muxi_outbreak':source=FROZEN/'muxi-outbreak/build/libs'/row['artifact']
        elif digest(source)!=row['sha256']:raise ValueError('Frozen artifact mismatch: '+row['id'])
        destination=artifact_dir/source.name;shutil.copy2(source,destination);artifacts[row['id']]=destination
    fixes=Path(extras['originalFixDirectory'])
    audio=next(p for p in fixes.glob('*.jar') if digest(p)==extras['audioSha256'])
    shutil.copy2(audio,artifact_dir/audio.name)
    gunpack=next(p for p in fixes.glob('*.zip') if digest(p)==extras['gunSha256'])
    meta=json.loads((game/'versions/BatterMC5Remake/BatterMC5Remake.json').read_text(encoding='utf-8'))
    libs=[game/'libraries'/x['downloads']['artifact']['path'] for x in meta['libraries'] if reviewed.allowed(x.get('rules')) and x.get('downloads',{}).get('artifact')]
    libs=list(dict.fromkeys(libs+[game/'versions/BatterMC5Remake/BatterMC5Remake.jar']))
    if any(not p.is_file() for p in libs):raise ValueError('Installed client library missing')
    expected=json.loads((DESKTOP/'expected-pack.json').read_text(encoding='utf-8'))['files']
    with socket.socket() as listener:listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    classes=home/'classes'
    sources=[HERE/'MinigamesNativeQA.java']+[DESKTOP/'java/net/muxigame/terminal/qa/mixin'/name for name in ['OfflineMcefMixin.java','HardwareWmiTimeoutQAMixin.java']]
    cp=[terminal,artifacts['muxi_minigames'],game/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar',game/'libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar',*libs,*list((WORK/'bmc5server/libraries').rglob('*.jar')),*list((pack/'mods').glob('*.jar'))]
    build.compile_java(compiler,sources,classes,os.pathsep.join(map(str,cp)),home/'compile.args')
    clients=[]
    for role,name in [('host','10000'),('guest','10001')]:
        lab=home/role;lab.mkdir();(lab/'mods').mkdir();(lab/'natives').mkdir();(lab/'config').mkdir()
        shutil.copytree(game/'versions/BatterMC5Remake/BatterMC5Remake-natives',lab/'natives',dirs_exist_ok=True)
        shutil.copytree(game/'mods/mcef-libraries',lab/'mods/mcef-libraries',dirs_exist_ok=True)
        for row in expected:
            if row['policy']=='Optional' and not row.get('defaultOn',False):continue
            source=pack/row['path']
            if hashlib.sha1(source.read_bytes()).hexdigest()!=row['sha1']:raise ValueError('Frozen pack mismatch: '+row['path'])
            if source.name.startswith(('muxi-terminal-','muxi-minigames-','muxi-game-core-','muxi-outbreak-','muxi-zombie-challenge-')):continue
            shutil.copy2(source,lab/'mods'/source.name)
        for directory in ['config','resourcepacks','defaultconfigs','kubejs','tacz']:
            if (pack/directory).is_dir():shutil.copytree(pack/directory,lab/directory,dirs_exist_ok=True)
        for source in artifact_dir.glob('*.jar'):shutil.copy2(source,lab/'mods'/source.name)
        (lab/'tacz').mkdir(exist_ok=True)
        shutil.copy2(gunpack,lab/'tacz/muxi-phoenix-six-netnew-20261001.zip')
        reviewed.config(lab)
        (lab/'config/iris.properties').write_text('enableShaders=false\ndisableUpdateMessage=true\n',encoding='utf-8')
        with zipfile.ZipFile(lab/'mods/minigames-qa-only.jar','w',zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="minigames_native_qa"\nversion="1.0.0"\ndisplayName="Private real minigames QA"\n[[mixins]]\nconfig="minigames_native_qa.mixins.json"\n')
            archive.writestr('minigames_native_qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['OfflineMcefMixin','HardwareWmiTimeoutQAMixin'],'injectors':{'defaultRequire':1}}))
            for source in classes.rglob('*.class'):archive.write(source,source.relative_to(classes).as_posix())
        offline_uuid=uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode()).digest(),version=3)
        subs={'auth_player_name':name,'auth_uuid':offline_uuid.hex,'auth_access_token':'0','version_name':'BatterMC5Remake','game_directory':str(lab),'assets_root':str(game/'assets'),'assets_index_name':meta['assetIndex']['id'],'clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release','resolution_width':'1280','resolution_height':'720','natives_directory':str(lab/'natives'),'launcher_name':'private-real-minigames-qa','launcher_version':'1','classpath':os.pathsep.join(map(str,libs)),'library_directory':str(game/'libraries'),'classpath_separator':os.pathsep}
        def expand(items):
            result=[]
            for item in items:
                if isinstance(item,dict):
                    if not reviewed.allowed(item.get('rules')):continue
                    values=item['value'] if isinstance(item['value'],list) else [item['value']]
                else:values=[item]
                for value in values:
                    for key,replacement in subs.items():value=value.replace('${'+key+'}',replacement)
                    if '${' in value:raise ValueError('Unresolved argument: '+value)
                    result.append(value)
            return result
        args=['-Xms512M','-Xmx3G','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=9','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=9','-Dqa.minigames.role='+role,'-Dqa.minigames.coordinator='+str(coordinator),'-Dqa.minigames.port='+str(port)]
        args+=expand(meta['arguments']['jvm'])+[meta['mainClass']]+expand(meta['arguments']['game'])
        reviewed.argfile(lab/'launch.args',args)
        clients.append({'role':role,'username':name,'uuid':str(offline_uuid),'lab':str(lab),'java':str(runtime),'args':str(lab/'launch.args')})
    report={'prepared':True,'clientStarted':False,'home':str(home),'coordinator':str(coordinator),'port':port,'clients':clients,'fakePlayers':False,'syntheticSSO':False,'productionOperations':False,'terminalCompiledSource':str(terminal_source),'terminalCompiledSha256':digest(terminal_source),'terminalResourcesOverlaid':own_resources,'gunpackSha256':digest(gunpack),'artifacts':{key:{'path':str(value),'sha256':digest(value)} for key,value in artifacts.items()}}
    write(home/'prepared.json',report);write(REPO/'build/minigames-native/latest-prepared.json',report)
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()

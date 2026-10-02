"""Prepare isolated two-client Minecraft/MCEF QA, without launching either client.

Uses existing installed libraries and the reviewed QA-only native downloader/WMI
timeout mixins. No fabricated players, authentication grants, or focus bypass.
"""
from __future__ import annotations
import ctypes
import hashlib
import importlib.util
import io
import json
import os
import random
from pathlib import Path
import shutil
import socket
import sys
import tempfile
import tomllib
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
    dedicated='--dedicated' in sys.argv
    backend=None
    if dedicated:
        owner=json.loads((REPO/'build/minigames-native/shared-backend-owner.json').read_text(encoding='utf-8'))
        backend=json.loads(Path(owner['privateReadyPath']).read_text(encoding='utf-8'))
    build=load_build()
    candidate=None
    if '--terminal-candidate' in sys.argv:
        candidate=json.loads(Path(sys.argv[sys.argv.index('--terminal-candidate')+1]).read_text(encoding='utf-8'))
        if not candidate.get('privateOnly') or candidate.get('published'):raise ValueError('Only an unpublished owner QA candidate may be selected')
        for row in candidate['artifacts'].values():
            if digest(Path(row['path']))!=row['sha256']:raise ValueError('Owner candidate artifact hash mismatch')
    compiler,runtime=build.java_tools(JDK)
    game=WORK/'_client_test/game'
    pack=FROZEN/'candidate-pack'
    extras=json.loads((DESKTOP/'unified-inputs.json').read_text(encoding='utf-8'))
    # Keep copied shader/material paths below Windows MAX_PATH without system changes.
    home=Path(tempfile.gettempdir())/('mgqa-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
    home.mkdir(parents=True)
    coordinator=home/'coordinator';coordinator.mkdir()
    artifact_dir=home/'pinned-artifacts';artifact_dir.mkdir()
    # Pin the existing compiled terminal, then overlay only this owner's UI resources.
    terminal_source=REPO/'build/libs/muxi-terminal-0.2.4-task14-social-qa.1.jar'
    if candidate:terminal_source=Path(candidate['artifacts']['muxi-terminal']['path'])
    compiled_bytes=terminal_source.read_bytes()
    compiled_sha256=hashlib.sha256(compiled_bytes).hexdigest()
    terminal=artifact_dir/terminal_source.name
    own_resources=['apps/minigames/app.js','apps/minigames/app.css','apps/minigames/model.js','friends-invites.js','index.html']
    if candidate:own_resources=[]
    prefix='assets/muxi_terminal/html/terminal/'
    overrides={prefix+name:(REPO/'src/main/resources'/prefix/name).read_bytes() for name in own_resources}
    if candidate:shutil.copy2(terminal_source,terminal)
    else:
        with zipfile.ZipFile(io.BytesIO(compiled_bytes)) as source,zipfile.ZipFile(terminal,'w',zipfile.ZIP_DEFLATED) as target:
            for entry in source.infolist():target.writestr(entry,overrides.pop(entry.filename,source.read(entry.filename)))
            for name,data in overrides.items():target.writestr(name,data)
    artifacts={'muxi_terminal':terminal}
    for row in extras['artifacts']:
        if row['id']=='muxi_terminal':continue
        source=Path(extras['stage'])/row['path']
        owner_repo={'muxi_minigames':REPO.parent/'muxi-minigames',
                    'muxi_game_core':REPO.parent/'muxi-game-core',
                    'muxi_zombie_challenge':FROZEN/'muxi-zombie-challenge',
                    'muxi_outbreak':FROZEN/'muxi-outbreak'}.get(row['id'])
        if candidate:source=Path(candidate['artifacts'][row['id'].replace('_','-')]['path'])
        elif owner_repo:
            release=json.loads((owner_repo/'build/release.json').read_text(encoding='utf-8'))
            source=owner_repo/'build/libs'/release['artifact']
            if digest(source)!=release['sha256']:raise ValueError('Owner build receipt mismatch: '+row['id'])
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
    required_dependencies=[]
    dependency_reasons={}
    with zipfile.ZipFile(artifacts['muxi_outbreak']) as archive:
        metadata=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
    if any(row.get('modId')=='lrtactical' and row.get('type')=='required' for row in metadata.get('dependencies',{}).get('muxi_outbreak',[])):
        dependency=pack/'mods/LesRaisins-Tactical-Equipements-1.21.1-0.4.3.jar'
        if not dependency.is_file():raise ValueError('Selected Outbreak requires missing LR Tactical 0.4.3')
        with zipfile.ZipFile(dependency) as archive:
            mod=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
            if not any(row.get('modId')=='lrtactical' and row.get('version')=='0.4.3' for row in mod.get('mods',[])):raise ValueError('LR Tactical dependency identity/version mismatch')
        required_dependencies.append(dependency)
        dependency_reasons[str(dependency)]='Selected Outbreak requires LR Tactical 0.4.3 on both sides; existing selected equipment dependency'
    if dedicated:
        aircraft=WORK/'bmc5server/mods/immersive_aircraft-1.5.2+1.21.1-neoforge.jar'
        with zipfile.ZipFile(aircraft) as archive:
            mod=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
            if not any(row.get('modId')=='immersive_aircraft' and row.get('version')=='1.5.2+1.21.1' for row in mod.get('mods',[])):raise ValueError('Installed flight integration dependency identity/version mismatch')
        pinned_aircraft=artifact_dir/aircraft.name;shutil.copy2(aircraft,pinned_aircraft)
        required_dependencies.append(pinned_aircraft)
        dependency_reasons[str(pinned_aircraft)]='Existing installed flight integration server requires Immersive Aircraft channels; same unchanged JAR pinned for both actual clients and server after observed handshake mismatch'
        wings=WORK/'bmc5server/mods/warfare_wings-1.1.4-1.21.1-neoforge.jar'
        with zipfile.ZipFile(wings) as archive:
            mod=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
            if not any(row.get('modId')=='warfare_wings' and row.get('version')=='1.1.4' for row in mod.get('mods',[])):raise ValueError('Installed flight registry dependency mismatch')
        pinned_wings=artifact_dir/wings.name;shutil.copy2(wings,pinned_wings);required_dependencies.append(pinned_wings)
        dependency_reasons[str(pinned_wings)]='Existing installed flight integration requires matching Warfare Wings registries; same unchanged JAR on both sides after actual registry rejection'
        companion=pack/'mods/muxi-champion-companions-1.0.5.jar'
        pinned_companion=artifact_dir/companion.name;shutil.copy2(companion,pinned_companion);required_dependencies.append(pinned_companion)
        dependency_reasons[str(pinned_companion)]='Preserve existing managed candidate companion 1.0.5 on both sides; private installed server baseline had stale 1.0.3'
    # Sable binds UDP as well as Minecraft TCP. Avoid the Windows dynamic-port
    # range and prove both transports are free before selecting a private port.
    for port in random.sample(range(24000,40000),100):
        try:
            with socket.socket() as tcp,socket.socket(type=socket.SOCK_DGRAM) as udp:
                tcp.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
                udp.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
                tcp.bind(('127.0.0.1',port));udp.bind(('127.0.0.1',port))
            break
        except OSError:continue
    else:raise RuntimeError('No private TCP and UDP port pair available')
    classes=home/'classes'
    sources=[HERE/name for name in (['MinigamesNativeQA.java','MinigamesDedicatedServerQA.java'] if dedicated else ['MinigamesNativeQA.java','LanPublishDiagnosticsMixin.java','PrivateRelayIsolationMixin.java','PrivateRelayIsolationPlugin.java'])]+[DESKTOP/'java/net/muxigame/terminal/qa/mixin'/name for name in ['OfflineMcefMixin.java','HardwareWmiTimeoutQAMixin.java']]
    native_sources=REPO.parent/'better-mc-remake/tests/terminal-mcef-qa/java/net/muxigame/terminal/smoke'
    if dedicated:sources += [native_sources/name for name in ['NativeIssuerEndpoint.java','NativeProofDiagnostics.java','mixin/NativeClientTransportMixin.java','mixin/NativeServerTransportMixin.java']]+[REPO.parent/'better-mc-remake/tests/terminal-mcef-environment/DirectTlsCefMixin.java',HERE/'NativeFriendsTransportMixin.java']
    cp=[terminal,artifacts['muxi_minigames'],artifacts['muxi_game_core'],game/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar',game/'libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar',*libs,*list((WORK/'bmc5server/libraries').rglob('*.jar')),*list((pack/'mods').glob('*.jar'))]
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
        for directory in ['config','shaderpacks','resourcepacks','defaultconfigs','kubejs','tacz']:
            if (pack/directory).is_dir():shutil.copytree(pack/directory,lab/directory,dirs_exist_ok=True)
        for source in [*artifact_dir.glob('*.jar'),*required_dependencies]:shutil.copy2(source,lab/'mods'/source.name)
        (lab/'tacz').mkdir(exist_ok=True)
        shutil.copy2(gunpack,lab/'tacz/muxi-phoenix-six-netnew-20261001.zip')
        reviewed.config(lab)
        (lab/'config/iris.properties').write_text('enableShaders=false\ndisableUpdateMessage=true\n',encoding='utf-8')
        with zipfile.ZipFile(lab/'mods/minigames-qa-only.jar','w',zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="minigames_native_qa"\nversion="1.0.0"\ndisplayName="Private real minigames QA"\n[[mixins]]\nconfig="minigames_native_qa.mixins.json"\n'+('[[mixins]]\nconfig="native_sso_transport.mixins.json"\n' if dedicated else ''))
            mixins={'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['OfflineMcefMixin','HardwareWmiTimeoutQAMixin'],'injectors':{'defaultRequire':1}}
            if dedicated:mixins['client']+=['DirectTlsCefMixin','NativeFriendsTransportMixin']
            else:mixins.update(plugin='net.muxigame.terminal.qa.mixin.PrivateRelayIsolationPlugin');mixins['client']+=['LanPublishDiagnosticsMixin','PrivateRelayIsolationMixin']
            archive.writestr('minigames_native_qa.mixins.json',json.dumps(mixins))
            if dedicated:
                archive.writestr('native_sso_transport.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.smoke.mixin','compatibilityLevel':'JAVA_21','client':['NativeClientTransportMixin'],'injectors':{'defaultRequire':1}}))
                # The transport rewrite preserves issuer proof and server packet validation.
            for source in classes.rglob('*.class'):
                if source.stem.split('$')[0]=='MinigamesDedicatedServerQA':continue
                archive.write(source,source.relative_to(classes).as_posix())
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
        args=['-Xms512M','-Xmx6G','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=9','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=9','-Dqa.minigames.role='+role,'-Dqa.minigames.coordinator='+str(coordinator),'-Dqa.minigames.port='+str(port)]
        if dedicated:
            args=[a for a in args if not a.startswith(('-Dhttp.proxy','-Dhttps.proxy'))]
            args+=['-Dqa.minigames.dedicated=true','-Dmuxi.sso.nativeAuthURL='+backend['auth_url'],'-Dmuxi.sso.siteURL='+backend['site_url'],'-Dmuxi.sso.directTls=true','-Dmuxi.sso.sitePort='+str(backend['site_port']),'-Dmuxi.sso.spki='+backend['spki'],'-Djavax.net.ssl.trustStore='+backend['truststore'],'-Djavax.net.ssl.trustStorePassword=isolated-synthetic']
        inherited_jvm=expand(meta['arguments']['jvm'])
        if dedicated:inherited_jvm=[a for a in inherited_jvm if not a.startswith('-Djava.net.preferIPv6Addresses=')]+['-Djava.net.preferIPv6Addresses=false']
        args+=inherited_jvm+[meta['mainClass']]+expand(meta['arguments']['game'])
        reviewed.argfile(lab/'launch.args',args)
        clients.append({'role':role,'username':name,'uuid':str(offline_uuid),'lab':str(lab),'java':str(runtime),'args':str(lab/'launch.args')})
    report={'prepared':True,'clientStarted':False,'home':str(home),'coordinator':str(coordinator),'port':port,'clients':clients,'heapPerClient':'6G','fakePlayers':False,'syntheticSSO':False,'productionOperations':False,'accountAuthentication':'private-offline-LAN; not trusted account acceptance','terminalCompiledSource':str(terminal_source),'terminalCompiledSha256':compiled_sha256,'terminalResourcesOverlaid':own_resources,'gunpackSha256':digest(gunpack),'artifacts':{key:{'path':str(value),'sha256':digest(value)} for key,value in artifacts.items()}}
    report['qaNetworkingIsolation']={'e4mcPublicRelay':'one source-tagged startup hook skipped in QA-only mod','originalModJarsModified':False,'sableUdpDisabled':False,'candidateDefaultLanAcceptance':False,'knownDefaultLanFailure':'Luna/e4mc recursively invokes startTcpServerListener; Sable binds the same UDP port twice'}
    report['additionalRequiredDependencies']=[{'path':str(p),'sha256':digest(p),'reason':dependency_reasons[str(p)]} for p in required_dependencies]
    if dedicated:
        server=home/'dedicated-server';server.mkdir();(server/'mods').mkdir();(server/'config').mkdir()
        for source in (WORK/'bmc5server/mods').glob('*.jar'):
            if source.name.startswith(('muxi-terminal-','muxi-minigames-','muxi-game-core-','muxi-outbreak-','muxi-zombie-challenge-','muxi-champion-companions-')):continue
            shutil.copy2(source,server/'mods'/source.name)
        for source in [*artifacts.values(),*required_dependencies]:shutil.copy2(source,server/'mods'/source.name)
        for directory in ['config','defaultconfigs','kubejs','tacz']:
            if (pack/directory).is_dir():shutil.copytree(pack/directory,server/directory,dirs_exist_ok=True)
        (server/'tacz').mkdir(exist_ok=True);shutil.copy2(gunpack,server/'tacz/muxi-phoenix-six-netnew-20261001.zip')
        (server/'eula.txt').write_text('eula=true\n',encoding='utf-8')
        (server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port='+str(port)+'\nonline-mode=false\nenforce-secure-profile=false\nmax-players=4\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nlevel-name=minigames-private-qa\nlevel-type=minecraft:flat\nlevel-seed=987654321\ngamemode=survival\ndifficulty=peaceful\nallow-flight=true\nenable-rcon=false\nenable-query=false\n',encoding='utf-8')
        write(server/'config/muxi-game-core.json',{'schema':1,'features':{'identity':{'enabled':True,'endpoint':backend['auth_url']+'/api/internal/minecraft/identity/','serverKey':backend['profile_key']},'login':{'enabled':True,'endpoint':backend['auth_url']+'/api/internal/minecraft/join/','serverKey':backend['profile_key']},'opSync':{'enabled':False},'terminalSso':{'enabled':True,'endpoint':'https://account.muxigame.com/api/internal/minecraft/','serverKey':backend['terminal_key']}}})
        with zipfile.ZipFile(server/'mods/minigames-server-qa-only.jar','w',zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="minigames_native_server_qa"\nversion="1.0.0"\ndisplayName="Private minigames server observations"\n[[mixins]]\nconfig="server_sso_transport.mixins.json"\n')
            archive.writestr('server_sso_transport.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.smoke.mixin','compatibilityLevel':'JAVA_21','mixins':['NativeServerTransportMixin'],'injectors':{'defaultRequire':1}}))
            for source in classes.rglob('*.class'):
                if source.stem.split('$')[0] in ['MinigamesDedicatedServerQA','NativeIssuerEndpoint','NativeServerTransportMixin']:archive.write(source,source.relative_to(classes).as_posix())
        installed=WORK/'bmc5server'
        server_args=(installed/'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').read_text(encoding='utf-8').split()
        library_prefix=(installed/'libraries').as_posix()
        server_args=[a.replace('libraries/',library_prefix+'/') if 'libraries/' in a else a for a in server_args]
        server_args=[('-DlibraryDirectory='+library_prefix) if a=='-DlibraryDirectory=libraries' else a for a in server_args]
        server_args=[a for a in server_args if not a.startswith('-Djava.net.preferIPv6Addresses=')]
        reviewed.argfile(server/'server-launch.args',['-Xms512M','-Xmx4G','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-Djava.net.preferIPv6Addresses=false','-Dqa.minigames.coordinator='+str(coordinator),'-Dmuxi.sso.nativeAuthURL='+backend['auth_url'],'-Djavax.net.ssl.trustStore='+backend['truststore'],'-Djavax.net.ssl.trustStorePassword=isolated-synthetic',*server_args])
        report['qaNetworkingIsolation']['loopbackAddressPreference']='IPv4 first only in private dedicated/client JVMs; same localhost TLS origin, CA and SPKI remain enforced. Actual JDK probe proves inherited system preference connects to unbound ::1.'
        report['dedicatedServer']={'root':str(server),'java':str(runtime),'args':str(server/'server-launch.args'),'nogui':True,'loginGateEnabled':True,'sharedBackendOwner':str(REPO/'build/minigames-native/shared-backend-owner.json'),'mods':len(list((server/'mods').glob('*.jar')))}
        report['qaNetworkingIsolation']['e4mcPublicRelay']='No hook isolation used; external dedicated server. Original installed server pack has no Luna jar.'
        report['accountAuthentication']='actual RpcHost A/B with late join grants and actual Core LoginGate; not yet tested'
    write(home/'prepared.json',report);write(REPO/'build/minigames-native/latest-prepared.json',report)
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()

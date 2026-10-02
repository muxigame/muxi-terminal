from pathlib import Path
import hashlib,json,os,shutil,subprocess,zipfile
HERE=Path(__file__).resolve().parent
WORK=HERE.parent
REPO=WORK/'teleport-network-implementation/muxi-terminal'
INPUTS=HERE/'inputs';INPUTS.mkdir(exist_ok=True)
desktop=HERE/'desktop'
shutil.copyfile(REPO/'tests/terminal-regression/qa_fml_config.py',desktop/'qa_fml_config.py')
offline=HERE/'java/net/muxigame/terminal/qa/mixin/OfflineMcefMixin.java';offline.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(REPO/'tests/terminal-regression/java/net/muxigame/terminal/qa/mixin/OfflineMcefMixin.java',offline)
preflight=HERE/'java/net/muxigame/terminal/qa/TerminalRenderPreflight.java'
shutil.copyfile(REPO/'tests/terminal-regression/preflight-java/net/muxigame/terminal/qa/TerminalRenderPreflight.java',preflight)
core=WORK/'native-map-full-review/muxi-game-core-native-map-full-review.jar'
terminal=WORK/'native-map-full-review/muxi-terminal-native-map-full-review.jar'
x=WORK/'close-x-review/muxi-terminal-close-x-review.jar'
target=INPUTS/'muxi-terminal-native-map-x-qa.jar'
with zipfile.ZipFile(terminal) as origin,zipfile.ZipFile(x) as close,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as out:
    name='net/muxigame/terminal/client/TerminalScreen.class'
    for entry in origin.infolist():out.writestr(entry,close.read(name) if entry.filename==name else origin.read(entry.filename))
shutil.copyfile(core,INPUTS/core.name)
classes=HERE/'build/classes';classes.mkdir(parents=True,exist_ok=True)
pack=Path('C:/Users/Administrator/WorkSpace/muxigame/better-mc-remake/pack/source/Better MC Remake [FORGE]/mods')
sdk=Path('C:/Users/Administrator/WorkSpace/muxigame/_client_test/game/libraries')
nested=HERE/'build/native-dependencies';nested.mkdir(exist_ok=True)
for jar in pack.glob('xaero*.jar'):
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.endswith('.jar') and 'xaerolib' in name.lower():(nested/Path(name).name).write_bytes(archive.read(name))
cp=os.pathsep.join(map(str,[core,target,WORK.parent/'task-21/music-app/build/compiler-dependencies.jar',*nested.glob('*.jar'),*pack.glob('*.jar'),*sdk.rglob('*.jar')]))
args=['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',str(classes),*map(str,(HERE/'java').rglob('*.java'))]
argfile=HERE/'build/compile.args';argfile.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in args),encoding='utf-8')
subprocess.run(['C:/Program Files/Java/jdk-24/bin/javac.exe','@'+str(argfile)],check=True)
with zipfile.ZipFile(INPUTS/'task6-native-qa-only.jar','w',zipfile.ZIP_DEFLATED) as out:
    toml='modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="task6_native_qa"\nversion="1.0.0"\ndisplayName="Task6 isolated native QA only"\n'
    for package,names,name in [('net.muxigame.terminal.qa.mixin',['OfflineMcefMixin','HardwareWmiTimeoutQAMixin'],'task6_offline.mixins.json'),('net.muxigame.terminal.nativeqa.mixin',['NetworkProbeMixin','RendererProbeMixin'],'task6_native_qa.mixins.json')]:
        out.writestr(name,json.dumps({'required':True,'minVersion':'0.8','package':package,'compatibilityLevel':'JAVA_21','client':names,'injectors':{'defaultRequire':1}}));toml+='[[mixins]]\nconfig="'+name+'"\n'
    out.writestr('META-INF/neoforge.mods.toml',toml)
    for path in classes.rglob('*.class'):out.write(path,path.relative_to(classes).as_posix())
files=sorted([*INPUTS.glob('*.jar'),*desktop.glob('*.py'),*desktop.glob('*.ps1')])
lock={'privateOnly':True,'reviewTerminalSourceSha256':hashlib.sha256(terminal.read_bytes()).hexdigest(),'reviewXSourceSha256':hashlib.sha256(x.read_bytes()).hexdigest(),'combination':'map jar with exactly TerminalScreen.class replaced from independent X review jar; source patches remain separate','files':[{'path':p.relative_to(HERE).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':p.stat().st_size} for p in files]}
(HERE/'qa-input-lock.json').write_text(json.dumps(lock,indent=2),encoding='utf-8')
with zipfile.ZipFile(WORK/'task6-native-map-131-qa-kit.zip','w',zipfile.ZIP_DEFLATED) as out:
    for path in [*files,HERE/'qa-input-lock.json',*sorted((HERE/'java').rglob('*.java')),HERE/'build_kit.py',HERE/'RUN-COORDINATION.txt',HERE/'test_normal_close_monitor.py']:out.write(path,path.relative_to(HERE).as_posix())
print(json.dumps(lock,indent=2))

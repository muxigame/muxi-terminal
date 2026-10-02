from pathlib import Path
import importlib.util,json,zipfile,hashlib
root=Path(__file__).resolve().parent;terminal=root.parent/'camera-native-worktree'
spec=importlib.util.spec_from_file_location('build',terminal/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
javac,java=b.java_tools(None);out=root/'build';out.mkdir(exist_ok=True)
meta=json.loads((terminal/'build/release.json').read_text());candidate=terminal/'build/libs'/meta['artifact']
dependency=Path('C:/Users/Administrator/Documents/Codex/2026-10-01/task-21/music-app/build/compiler-dependencies.jar')
lwjgl=Path('C:/Users/Administrator/WorkSpace/muxigame/_client_test/game/libraries/org/lwjgl/lwjgl-opengl/3.3.3/lwjgl-opengl-3.3.3.jar')
b.compile_java(javac,list((root/'java').rglob('*.java')),out/'classes',';'.join(map(str,[candidate,dependency,lwjgl])),out/'compile.args')
jar=out/'task22-camera-native-qa-only.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mixins]]\nconfig="task22_camera_qa.mixins.json"\n[[mods]]\nmodId="camera_native_qa"\nversion="1.0.0"\ndisplayName="Task22 isolated native camera QA"\n')
    z.writestr('task22_camera_qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['OfflineMcefMixin','HardwareWmiTimeoutQAMixin','AlbumAuthorityQAMixin'],'injectors':{'defaultRequire':1}}))
    for p in (out/'classes').rglob('*.class'):z.write(p,p.relative_to(out/'classes').as_posix())
print(json.dumps({'qaOnlyJar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'productJar':meta['artifact'],'productSha256':meta['sha256']}))

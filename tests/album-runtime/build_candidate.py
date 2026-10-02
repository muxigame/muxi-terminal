from pathlib import Path
import importlib.util,zipfile,json,hashlib,subprocess,os
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(os.environ.get('ALBUM_QA_WORKDIR',str(ROOT/'build/album-native-qa')))/'album-build';OUT.mkdir(parents=True,exist_ok=True)
JDK=Path(os.environ.get('ALBUM_QA_JDK',r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1'))
GAME=Path(os.environ.get('ALBUM_QA_GAME',r'C:\Users\ranzh\workspace\dev\muxigame\_client_test\game'))
spec=importlib.util.spec_from_file_location('build',ROOT/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
# Flatten compile-only dependencies to avoid unrelated Java module descriptor collisions.
cp=OUT/'dependencies.jar';seen=set()
for source in (GAME/'libraries/net/neoforged/neoforge').rglob('*.jar'):
    with zipfile.ZipFile(source) as z:
        for name in z.namelist():
            if 'mixinextras' in name.lower() and name.endswith('.jar'):
                (OUT/'mixinextras.jar').write_bytes(z.read(name))
with zipfile.ZipFile(cp,'w',zipfile.ZIP_DEFLATED) as out:
    for source in [ROOT/'build/music-test-dependencies.jar',next((GAME/'mods').glob('*mcef*.jar')),OUT/'mixinextras.jar',*(GAME/'libraries/org/lwjgl').rglob('*.jar')]:
        with zipfile.ZipFile(source) as z:
            for name in z.namelist():
                if name.endswith('.class') and not name.startswith('META-INF/') and not name.endswith('module-info.class') and name not in seen:
                    out.writestr(name,z.read(name));seen.add(name)
classes=OUT/'classes';b.compile_java(JDK/'bin/javac.exe',list((ROOT/'src/main/java').rglob('*.java')),classes,str(cp),OUT/'compile.args')
meta=json.loads((ROOT/'mod.json').read_text(encoding='utf-8'));jar=OUT/'muxi-terminal-album-dev-qa.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
    for path in classes.rglob('*.class'):z.write(path,path.relative_to(classes).as_posix())
    for path in (ROOT/'src/main/resources').rglob('*'):
        if path.is_file():
            name=path.relative_to(ROOT/'src/main/resources').as_posix();data=path.read_bytes()
            if name=='META-INF/neoforge.mods.toml':data=data.replace(b'${mod_version}',meta['version'].encode())
            z.writestr(name,data)
git=['git','-c','safe.directory='+str(ROOT)]
report={'jar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'head':subprocess.check_output(git+['rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceDirty':subprocess.check_output(git+['status','--short'],cwd=ROOT,text=True),'scope':'private QA candidate from shared dev; not released'}
(OUT/'build.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))

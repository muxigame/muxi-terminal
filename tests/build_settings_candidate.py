"""Isolated candidate build. Extract compile classes to avoid sandbox/JDK ZipFS close errors."""
from pathlib import Path
import importlib.util,json,os,zipfile

ROOT=Path(__file__).resolve().parents[1]
PACK=Path(r'C:\Users\Administrator\WorkSpace\muxigame')
spec=importlib.util.spec_from_file_location('terminal_build',ROOT/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
original_compile=build.compile_java
def compile_classes(compiler,sources,output,classpath,argfile):
    cp=ROOT/'build/compiler-dependencies';cp.mkdir(parents=True,exist_ok=True)
    # Preserve classpath priority: first definition wins, identical to javac's jar search.
    seen=set()
    for raw in classpath.split(os.pathsep):
        with zipfile.ZipFile(raw) as archive:
            for name in archive.namelist():
                if not name.endswith('.class') or name.startswith('META-INF/') or name in seen:continue
                seen.add(name);p=cp/name;p.parent.mkdir(parents=True,exist_ok=True)
                data=archive.read(name)
                if not p.exists() or p.read_bytes()!=data:p.write_bytes(data)
    original_compile(compiler,sources,output,str(cp),argfile)
build.compile_java=compile_classes
metadata=ROOT/'mod.json';before=metadata.read_bytes()
try:
    m=json.loads(before);m['version']='0.2.2-settings-candidate'
    metadata.write_text(json.dumps(m,indent=2)+'\n',encoding='utf-8')
    build.build(PACK/'bmc5server',client_game=PACK/'_client_test/game',pack_mods=PACK/'better-mc-remake/pack/source/Better MC Remake [FORGE]/mods')
finally:metadata.write_bytes(before)

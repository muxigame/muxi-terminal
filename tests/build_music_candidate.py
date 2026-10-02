"""Offline isolated candidate build; never writes source runtimes or launches Minecraft."""
from pathlib import Path
import importlib.util, json, os, zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = Path(r'C:/Users/Administrator/WorkSpace/muxigame')
spec = importlib.util.spec_from_file_location('terminal_build', ROOT / 'build.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)
compile_original = build.compile_java

def compile_extracted(compiler, sources, output, classpath, argfile):
    cp = ROOT / 'build/compiler-dependencies'
    cp.mkdir(parents=True, exist_ok=True)
    seen = set()
    for raw in classpath.split(os.pathsep):
        with zipfile.ZipFile(raw) as archive:
            for name in archive.namelist():
                if not name.endswith('.class') or name.startswith('META-INF/') or name in seen:
                    continue
                seen.add(name)
                target = cp / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
    local_jar = ROOT / 'build/compiler-dependencies.jar'
    with zipfile.ZipFile(local_jar, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for file in cp.rglob('*.class'):
            if file.name == 'module-info.class': continue
            archive.write(file, file.relative_to(cp).as_posix())
    compile_original(compiler, sources, output, str(local_jar), argfile)

build.compile_java = compile_extracted
metadata = ROOT / 'mod.json'
before = metadata.read_bytes()
try:
    data = json.loads(before)
    data['version'] = '0.2.2-music-candidate'
    metadata.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    build.build(PACK / 'bmc5server', client_game=PACK / '_client_test/game',
                pack_mods=PACK / 'better-mc-remake/pack/source/Better MC Remake [FORGE]/mods')
finally:
    metadata.write_bytes(before)

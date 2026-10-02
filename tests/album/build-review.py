from pathlib import Path
import importlib.util
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('terminal_build',root/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
original=build.compile_java
dependency=Path('C:/Users/Administrator/Documents/Codex/2026-10-01/task-21/music-app/build/compiler-dependencies.jar')
assert dependency.is_file()
def compile_local(compiler,sources,output,classpath,argfile):return original(compiler,sources,output,str(dependency),argfile)
build.compile_java=compile_local
pack=Path('C:/Users/Administrator/WorkSpace/muxigame')
build.build(pack/'bmc5server',java_home=Path('C:/Program Files/Java/jdk-24'),client_game=pack/'_client_test/game',pack_mods=pack/'better-mc-remake/pack/source/Better MC Remake [FORGE]/mods')

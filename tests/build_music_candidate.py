"""Build the current music candidate against installed libraries, without editing source metadata."""
from pathlib import Path
import argparse, importlib.util, os, zipfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace',type=Path,required=True)
    parser.add_argument('--java-home',type=Path,required=True)
    args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('terminal_build',ROOT/'build.py')
    build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
    compile_original=build.compile_java
    def capture_dependencies(compiler,sources,output,classpath,argfile):
        destination=ROOT/'build/music-test-dependencies.jar'
        destination.parent.mkdir(parents=True,exist_ok=True)
        seen=set()
        with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
            for entry in classpath.split(os.pathsep):
                with zipfile.ZipFile(entry) as dependency:
                    for name in dependency.namelist():
                        if name.endswith('.class') and not name.startswith('META-INF/') and not name.endswith('module-info.class') and name not in seen:
                            seen.add(name);archive.writestr(name,dependency.read(name))
        compile_original(compiler,sources,output,classpath,argfile)
    build.compile_java=capture_dependencies
    build.build(args.workspace/'bmc5server',java_home=args.java_home,client_game=args.workspace/'_client_test/game',pack_mods=args.workspace/'_client_test/game/mods')

if __name__=='__main__':main()

"""Build the current music candidate against installed libraries, without editing source metadata."""
from pathlib import Path
import argparse, importlib.util, os, zipfile, shutil, hashlib, json

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace',type=Path,required=True)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True,help='Private build root; required to protect shared publisher outputs')
    parser.add_argument('--pin-output',type=Path,help='Save a music-owned copy of the exact compiled candidate')
    args=parser.parse_args()
    spec=importlib.util.spec_from_file_location('terminal_build',ROOT/'build.py')
    build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
    private=args.output.resolve(); private.mkdir(parents=True,exist_ok=True)
    class SourceRoot:
        def __truediv__(self,part): return private if part == 'build' else ROOT/part
    build.ROOT=SourceRoot()
    compile_original=build.compile_java
    def capture_dependencies(compiler,sources,output,classpath,argfile):
        destination=private/'music-test-dependencies.jar'
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
    candidate=build.build(args.workspace/'bmc5server',java_home=args.java_home,client_game=args.workspace/'_client_test/game',pack_mods=args.workspace/'_client_test/game/mods')

    if args.pin_output:
        args.pin_output.mkdir(parents=True,exist_ok=True)
        target=args.pin_output/candidate.name
        shutil.copy2(candidate,target)
        # Verify that the pinned jar contains the current music resources.
        with zipfile.ZipFile(target) as archive:
            for name in ('music-app.js','music-app.css'):
                relative='assets/muxi_terminal/html/terminal/'+name
                if archive.read(relative)!=(ROOT/'src/main/resources'/relative).read_bytes():
                    raise RuntimeError('Pinned candidate music resource changed during build: '+name)
        receipt={'artifact':str(target),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'size':target.stat().st_size}
        (args.pin_output/'candidate.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf8')
        print(json.dumps(receipt))

if __name__=='__main__':main()

from pathlib import Path
import argparse,subprocess,zipfile,io,importlib.util,json,hashlib,time
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description="Freeze terminal HEAD plus explicitly owned handheld files into a private QA candidate")
parser.add_argument('--java-home',type=Path,required=True)
parser.add_argument('--server-runtime',type=Path,default=ROOT.parent/'bmc5server')
parser.add_argument('--client-game',type=Path,default=ROOT.parent/'_client_test/game')
args=parser.parse_args()
owned=['src/main/java/net/muxigame/terminal/client/TerminalClient.java','src/main/java/net/muxigame/terminal/client/TerminalHeldInput.java','src/main/java/net/muxigame/terminal/client/input/mixin/HeldTerminalKeyboardMixin.java','src/main/resources/muxi_terminal.input.mixins.json','src/main/resources/META-INF/neoforge.mods.toml']
git=['git','-c','safe.directory='+str(ROOT),'-C',str(ROOT)]
head=subprocess.check_output(git+['rev-parse','HEAD'],text=True).strip()
out=ROOT/'build'/('131-held-input-'+time.strftime('%Y%m%d-%H%M%S'));out.mkdir(parents=True)
snapshot=out/'source';snapshot.mkdir()
zipfile.ZipFile(io.BytesIO(subprocess.check_output(git+['archive','--format=zip','HEAD']))).extractall(snapshot)
sourceHashes={}
for rel in owned:
 data=(ROOT/rel).read_bytes();p=snapshot/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data);sourceHashes[rel]=hashlib.sha256(data).hexdigest()
spec=importlib.util.spec_from_file_location('held_candidate_build',snapshot/'build.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
jar=module.build(args.server_runtime.resolve(),args.java_home.resolve(),args.client_game.resolve(),args.client_game.resolve()/'mods')
manifest={'candidate':str(jar),'candidateSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'head':head,'ownedSourceHashes':sourceHashes,'directory':str(out),'noDeployment':True}
(out/'candidate.json').write_text(json.dumps(manifest,indent=2))
(ROOT/'build/131-held-latest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest),flush=True)

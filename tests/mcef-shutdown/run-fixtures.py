"""Compile the production guard and exercise installed handler bytecode without Minecraft."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--jdk', type=Path, required=True, help='JDK 21 directory')
parser.add_argument('--classpath', type=Path, required=True, help='existing ASM/Mixin dependency jar')
parser.add_argument('--mcef', type=Path, required=True, help='existing installed MCEF jar; read only')
parser.add_argument('--output', type=Path, required=True, help='private writable fixture output directory')
args = parser.parse_args()
here = Path(__file__).resolve().parent
repo = here.parents[1]
source = repo / 'src/main/java/net/muxigame/terminal/client/compat/McefShutdownGuard.java'
fixture = here / 'ShutdownGuardTests.java'
if not source.is_file() or not fixture.is_file():
    raise SystemExit('Run the checked-in script from tests/mcef-shutdown')
for file in (args.classpath, args.mcef):
    if not file.is_file():
        raise SystemExit('Missing existing input: ' + str(file))
javac, java = (args.jdk / 'bin' / (name + ('.exe' if os.name == 'nt' else '')) for name in ('javac', 'java'))
for binary in (javac, java):
    if not binary.is_file():
        raise SystemExit('Missing JDK binary: ' + str(binary))
args.output.mkdir(parents=True, exist_ok=True)
classes = Path(tempfile.mkdtemp(prefix='guard-fixture-', dir=str(args.output)))
before = hashlib.sha256(args.mcef.read_bytes()).hexdigest()
subprocess.run([str(javac), '--release', '21', '-encoding', 'UTF-8', '-cp', str(args.classpath), '-d', str(classes), str(source), str(fixture)], check=True)
result = subprocess.run([str(java), '-cp', str(classes) + os.pathsep + str(args.classpath), 'net.muxigame.terminal.compatqa.ShutdownGuardTests', str(args.mcef)], check=True, capture_output=True, text=True)
after = hashlib.sha256(args.mcef.read_bytes()).hexdigest()
if before != after:
    raise SystemExit('Read-only MCEF input unexpectedly changed')
proof = {'fixtureSuccess': True, 'minecraftStarted': False, 'processCommandsExecuted': False, 'mcefSha256': before, 'mcefUnchanged': True, 'result': result.stdout.strip(), 'classes': str(classes)}
(classes / 'result.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8')
print(json.dumps(proof, indent=2))

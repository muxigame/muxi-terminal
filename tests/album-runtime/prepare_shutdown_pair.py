"""Prepare two private guarded labs. This command never starts Minecraft."""
from pathlib import Path
import argparse, datetime, json, os, subprocess, sys, uuid

parser = argparse.ArgumentParser()
parser.add_argument('--full-template', type=Path, required=True, help='approved existing full-pack QA template')
parser.add_argument('--other-instance', type=Path, required=True, help='earlier owned lab screenshots directory')
options = parser.parse_args()
runner = Path(__file__).resolve().with_name('run_native_qa.py')
root = Path(os.environ.get('ALBUM_QA_WORKDIR', str(Path(__file__).resolve().parents[2] / 'build/album-native-qa'))).resolve()
root.mkdir(parents=True, exist_ok=True)
peer = root / ('album-peer-' + datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])
peer.mkdir()
env = dict(os.environ)
env['ALBUM_QA_WORKDIR'] = str(root)
common = ['--require-shutdown-guard', '--export-mixins']
a = ['--cycles', '8', *common]
b = ['--cycles', '24', '--phased', '--full-template', str(options.full_template), '--other-instance', str(options.other_instance), '--peer-coordinator', str(peer), *common]

def prepare(arguments):
    subprocess.run([sys.executable, str(runner), '--prepare-only', *arguments], env=env, check=True)
    return json.loads((root / 'album-runtime-latest.json').read_text(encoding='utf-8'))['lab']

lab_a, lab_b = prepare(a), prepare(b)
result = {'peer': str(peer), 'runner': str(runner), 'labA': lab_a, 'labB': lab_b, 'argsA': a, 'argsB': b, 'startsPerformed': False, 'mcefJarModified': False}
manifest = peer / 'pair-inputs.json'
manifest.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'prepared': True, 'minecraftStarted': False, 'manifest': str(manifest)}, indent=2))

"""Read the actual Mixin-exported Minecraft class; never launch Minecraft or modify MCEF."""
from pathlib import Path
import argparse, datetime, hashlib, json, re, subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--lab', type=Path, required=True)
parser.add_argument('--jdk', type=Path, required=True)
args = parser.parse_args()
export = args.lab / '.mixin.out/class/net/minecraft/client/Minecraft.class'
out = subprocess.check_output([str(args.jdk / 'bin/javap.exe'), '-c', '-p', str(export)]).decode('utf-8', errors='replace')
match = re.search(r'  public void (handler\$[^\s]+\$mcef\$close)\(org\.spongepowered\.asm\.mixin\.injection\.callback\.CallbackInfo\);\s+Code:\s+0: return\s+(?=\S)', out)
if not match or 'Method ' + match.group(1) + ':' not in out:
    raise SystemExit('Actual exported MCEF handler or preserved close callsite did not match')
mods = list((args.lab / 'mods').glob('*mcef*.jar'))
if len(mods) != 1:
    raise SystemExit('Expected one original MCEF jar in this lab')
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
original = '0c7696216fa5cfee659d687475873c847a9a17cc8ce3a56119a69c946eeb8772'
if sha(mods[0]) != original:
    raise SystemExit('Installed MCEF input is not the verified original package')
status = 'helper-kill hook: disabled:1' in (args.lab / 'boot.log').read_text(encoding='utf-8', errors='replace')
if not status:
    raise SystemExit('Actual runtime guard status missing')
proof = {'observedUtc': datetime.datetime.utcnow().isoformat() + 'Z', 'lab': str(args.lab), 'actualExportSha256': sha(export), 'mcefJarSha256': original, 'mcefJarUnchanged': True, 'handler': match.group(1), 'handlerInstruction': '0: return', 'closeInjectionCallsitePreserved': True, 'noMcefProcessCommandsInActualMethod': True, 'bootStatusDisabled1': status}
(args.lab / 'actual-shutdown-guard.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8')
print(json.dumps(proof, indent=2))

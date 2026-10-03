from pathlib import Path
import argparse,sys,json
ROOT=Path(__file__).resolve().parents[2];W=ROOT.parent;P=W/'better-mc-remake'
parser=argparse.ArgumentParser(description="Run the unified private MC debug tool with existing local MCEF cache")
parser.add_argument('--java-home',type=Path,required=True);args=parser.parse_args()
sys.path.insert(0,str(P/'scripts'));import local_mc_runtime as rt,local_mc_debug as debug
manifest=json.loads((ROOT/'build/131-held-runtime.json').read_text());labroot=Path(manifest['lab']).resolve()
copy_inputs=rt.copy_inputs
def with_local_cef(lab,mods,data):
 copy_inputs(lab,mods,data)
 lab=lab.resolve();assert lab.parent==labroot and lab.name in ('server','host')
 marker=rt.read_json(labroot/'local-mc-owner.json');assert marker and marker['instanceRoot']==str(labroot)
 source=(lab/'mcef-libraries').resolve();target=(lab/'mods/mcef-libraries').resolve()
 assert source.is_relative_to(lab) and target.is_relative_to(lab) and source.is_dir() and not target.exists()
 source.rename(target)
 config=lab/'config/mcef';config.mkdir()
 (config/'mcef.properties').write_text('skip-download=true\nuse-cache=false\n')
rt.copy_inputs=with_local_cef
launch=['run','--project-root',str(P),'--instance-root',str(labroot),'--server-runtime',str(W/'bmc5server'),'--client-game',str(W/'_client_test/game'),'--java-home',str(args.java_home),'--port',str(manifest['port']),'--clients','1','--client-name','HeldHostQA','--mode','hold','--hold-seconds','1200','--boot-timeout','300','--shutdown-timeout','180','--server-memory-mb','3072','--client-memory-mb','4096','--accept-eula','--data-dir',str(W/'_client_test/game/mods/mcef-libraries'),'--data-dir',str(W/'bmc5server/tacz'),'--unix-temp',str(P/'build/ut-held-131')]
for jar in manifest['mods']:launch+=['--mod',jar]
print('UNIFIED_HELD_LAB',labroot,flush=True)
raise SystemExit(debug.main(launch))

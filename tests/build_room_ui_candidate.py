"""Build a room UI candidate from tracked HEAD plus an explicit room-only overlay.

This deliberately excludes unrelated dirty drafts (including suspended Endgame).
It creates build copies, never branches, worktrees, published packages or gitlinks.
"""
from pathlib import Path
import argparse,subprocess,json,hashlib,io,zipfile,shutil,sys,importlib.util,datetime

OVERLAYS={
 'muxi-minigames':['src/main/java/net/muxigame/minigames/GameRuntime.java','src/main/java/net/muxigame/minigames/RoomTeam.java','src/main/java/net/muxigame/minigames/RoomPresentation.java'],
 'muxi-flight':['src/main/java/net/muxigame/minigames/flight/FlightRooms.java'],
 'muxi-horse-racing':['src/main/java/net/muxigame/minigames/horse/HorseRooms.java'],
 'muxi-outbreak':['src/main/java/net/muxigame/outbreak/OutbreakTerminalUi.java'],
 'muxi-zombie-challenge':[],
 'muxi-terminal':['src/main/resources/assets/muxi_terminal/html/terminal/'+p for p in ['apps/minigames/app.js','apps/minigames/app.css','apps/minigames/model.js','apps/minigames/dialog.js','friends-invites.js','friends-invites-controller.js','index.html']],
}
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--workspace',type=Path,required=True);parser.add_argument('--java-home',type=Path,required=True);parser.add_argument('--output',type=Path);args=parser.parse_args()
 root=args.workspace.resolve();output=(args.output or root/'muxi-terminal/build'/('room-ui-candidate-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))).resolve();output.mkdir(parents=True,exist_ok=False)
 evidence={'scope':'Candidate only; tracked HEAD plus listed room changes; Endgame and other owner dirty changes excluded.','modules':{},'artifacts':[]}
 modules={}
 for name,files in OVERLAYS.items():
  source=root/name;stage=output/name;stage.mkdir()
  base=['git','-c',f'safe.directory={source.as_posix()}'];head=subprocess.check_output(base+['rev-parse','HEAD'],cwd=source,text=True).strip()
  archive=subprocess.check_output(base+['archive','--format=zip','HEAD'],cwd=source)
  with zipfile.ZipFile(io.BytesIO(archive)) as z:z.extractall(stage)
  hashes={}
  for rel in files:
   data=(source/rel).read_bytes();target=stage/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);hashes[rel]=hashlib.sha256(data).hexdigest()
  if name=='muxi-outbreak':
   rel='src/main/java/net/muxigame/outbreak/OutbreakGame.java';line=next(s for s in (source/rel).read_text(encoding='utf-8').splitlines() if 'case "difficulty"->' in s)
   target=stage/rel;s=target.read_text(encoding='utf-8');marker='            case "join"';assert marker in s
   if 'case "difficulty"->' not in s:s=s.replace(marker,line+'\n'+marker,1)
   else:s='\n'.join(line if 'case "difficulty"->' in existing else existing for existing in s.split('\n'))
   target.write_text(s,encoding='utf-8');hashes[rel]=hashlib.sha256(target.read_bytes()).hexdigest()
   research=source/'build/equipment-research'
   if research.exists():
    (stage/'build/equipment-research').mkdir(parents=True)
    for jar in research.glob('*.jar'):shutil.copy2(jar,stage/'build/equipment-research'/jar.name)
  evidence['modules'][name]={'baseHead':head,'overlays':hashes,'source':str(stage)}
  spec=importlib.util.spec_from_file_location(name.replace('-','_'),stage/'build.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);modules[name]=module
 server=root/'bmc5server';client=root/'_client_test/game';jdk=args.java_home
 framework=modules['muxi-minigames'].build(server,client,jdk)
 for name in ['muxi-minigames','muxi-flight','muxi-horse-racing','muxi-outbreak','muxi-zombie-challenge','muxi-terminal']:
  module=modules[name]
  if name=='muxi-minigames':jar=framework
  elif name in ['muxi-flight','muxi-horse-racing']:jar=module.build(server,client,jdk,framework)
  elif name=='muxi-outbreak':jar=module.build(server,jdk,framework)
  elif name=='muxi-zombie-challenge':
   # Its adapter is unchanged; include baseline to validate the shared room protocol.
   jar=module.build(server,client,jdk,pack_mods=server/'mods',framework_jar=framework)
  else:jar=module.build(server,jdk,client,server/'mods')
  evidence['artifacts'].append({'module':name,'path':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'bytes':jar.stat().st_size})
  (output/'CANDIDATE.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
 print('ROOM_UI_CANDIDATE '+str(output),flush=True)
if __name__=='__main__':main()

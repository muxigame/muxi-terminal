from pathlib import Path
import datetime,hashlib,io,zipfile,subprocess,importlib.util,json,argparse
parser=argparse.ArgumentParser(description='Build shared AI framework and terminal from tracked HEAD plus explicit AI overlays; no Endgame/game owner drafts.')
parser.add_argument('--workspace',type=Path,required=True);parser.add_argument('--java-home',type=Path,required=True);args=parser.parse_args()
ROOT=args.workspace.resolve();JDK=args.java_home.resolve()
OUT=ROOT/'muxi-terminal/build'/('room-ai-candidate-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
OUT.mkdir(parents=True)
OVERLAYS={
 'muxi-minigames':['src/main/java/net/muxigame/minigames/'+n+'.java' for n in ['GameRuntime','GameModule','RoomTeam','Memberships','GameSocial','RoomPresentation','RoomAiSeat','RoomAiService']],
 'muxi-terminal':['src/main/resources/assets/muxi_terminal/html/terminal/apps/minigames/'+n for n in ['app.js','model.js','app.css']]
}
evidence={'scope':'Shared AI framework and terminal only, tracked HEAD plus explicit AI overlays; no Endgame drafts, game owner code or deployment.','modules':{},'artifacts':[]}
modules={}
for name,paths in OVERLAYS.items():
 source=ROOT/name;stage=OUT/name;stage.mkdir()
 command=['git','-c',f'safe.directory={source.as_posix()}','-C',str(source)]
 head=subprocess.check_output(command+['rev-parse','HEAD'],text=True).strip()
 with zipfile.ZipFile(io.BytesIO(subprocess.check_output(command+['archive','--format=zip','HEAD']))) as z:z.extractall(stage)
 hashes={}
 for rel in paths:
  data=(source/rel).read_bytes();target=stage/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);hashes[rel]=hashlib.sha256(data).hexdigest()
 evidence['modules'][name]={'baseHead':head,'overlays':hashes}
 spec=importlib.util.spec_from_file_location(name.replace('-','_'),stage/'build.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);modules[name]=module
server=ROOT/'bmc5server';client=ROOT/'_client_test/game'
framework=modules['muxi-minigames'].build(server,client,JDK)
for name in ['muxi-minigames','muxi-terminal']:
 jar=framework if name=='muxi-minigames' else modules[name].build(server,JDK,client,server/'mods')
 evidence['artifacts'].append({'module':name,'path':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'bytes':jar.stat().st_size})
 (OUT/'CANDIDATE.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
 print(name+' ARTIFACT '+str(jar),flush=True)
print('CANDIDATE '+str(OUT),flush=True)

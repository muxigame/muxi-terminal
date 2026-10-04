from pathlib import Path
import json, subprocess, zipfile, os, shutil, time,sys,argparse

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description='Real installed music decoders and native API signature verification')
parser.add_argument('--workspace',type=Path,default=Path(r'C:/Users/Administrator/WorkSpace/muxigame'))
parser.add_argument('--java-home',type=Path,default=Path(r'C:/Program Files/Java/jdk-24'))
parser.add_argument('--candidate',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--dependencies',type=Path)
args=parser.parse_args()
PACK=args.workspace
lab=args.output;lab.mkdir(parents=True,exist_ok=True)
java=args.java_home/'bin'
jar=args.candidate
deps=args.dependencies or ROOT/'build/compiler-dependencies.jar'
sources=list((ROOT/'tests/java').rglob('*.java'))
subprocess.run([str(java/'javac.exe'),'--release','21','-encoding','UTF-8','-proc:none','-classpath',os.pathsep.join(map(str,[jar,deps])),'-d',str(lab),*map(str,sources)],check=True)
assets=PACK/'_client_test/game/assets'
index=json.loads(next((assets/'indexes').glob('*.json')).read_text(encoding='utf-8'))
record=index['objects']['minecraft/sounds/ambient/cave/cave1.ogg']['hash']
sample=assets/'objects'/record[:2]/record
named_sample=lab/'probe-vorbis.ogg';shutil.copyfile(sample,named_sample)
jorbis=PACK/'_client_test/game/libraries/org/jcraft/jorbis/0.0.17/jorbis-0.0.17.jar'
lwjgl=PACK/'_client_test/game/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar'
jna=PACK/'bmc5server/libraries/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar'
run=subprocess.run([str(java/'java.exe'),'-cp',os.pathsep.join(map(str,[lab,jar,deps,jorbis,lwjgl,jna])),
    'net.muxigame.terminal.client.music.MusicModuleTest',str(lab/f'run-{time.time_ns()}'),str(named_sample)],capture_output=True,encoding='utf-8',errors='replace')
(lab/'output.txt').write_text(run.stdout+run.stderr,encoding='utf-8')
if run.returncode:raise SystemExit(run.stdout+run.stderr)
result=json.loads(run.stdout.strip().splitlines()[-1]);(lab/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
mods=PACK/'_client_test/game/mods'
music_jars=[next(p for p in mods.glob('*.jar') if name+'-' in p.name) for name in ['net_music_list','netmusic']]
binding=subprocess.run([str(java/'java.exe'),'-cp',os.pathsep.join(map(str,[lab,jar,deps,jorbis,lwjgl,jna,*music_jars])),
    'net.muxigame.terminal.client.music.MusicBindingTest'],capture_output=True,encoding='utf-8',errors='replace')
(lab/'bindings-output.txt').write_text(binding.stdout+binding.stderr,encoding='utf-8')
if binding.returncode:raise SystemExit(binding.stdout+binding.stderr)
bind_result=json.loads(binding.stdout.strip().splitlines()[-1]);(lab/'bindings-result.json').write_text(json.dumps(bind_result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(bind_result,ensure_ascii=False,indent=2))

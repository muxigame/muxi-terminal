from pathlib import Path
import importlib.util,os,json,zipfile,shutil,subprocess,uuid
import argparse
parser=argparse.ArgumentParser(description="Compile a music-only QA helper outside production outputs")
parser.add_argument('--java-home',type=Path,required=True)
parser.add_argument('--candidate',type=Path,required=True)
parser.add_argument('--dependencies',type=Path,required=True)
parser.add_argument('--mcef',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
R=Path(__file__).resolve().parents[2]
source=Path(__file__).resolve().parent
out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('builder',R/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
jdk=args.java_home
candidate=args.candidate;deps=args.dependencies;mcef=args.mcef
classes=out/('classes-'+uuid.uuid4().hex[:8])
b.compile_java(jdk/'bin/javac.exe',list(source.glob('*.java')),classes,os.pathsep.join(map(str,[candidate,deps,mcef])),out/'compile.args')
jar=out/'music-next-qa-only.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="music_next_qa"\nversion="1.0.0"\ndisplayName="Music next isolated QA"\n[[mixins]]\nconfig="music_next_gain.mixins.json"\n[[mixins]]\nconfig="music_next_offline.mixins.json"\n')
    z.writestr('music_next_gain.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.musicnextqa.mixin','compatibilityLevel':'JAVA_21','client':['ChannelGainAccessor'],'injectors':{'defaultRequire':1}}))
    z.writestr('music_next_offline.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['OfflineMcefMixin'],'injectors':{'defaultRequire':1}}))
    for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes).as_posix())
print(jar)

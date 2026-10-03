from pathlib import Path
import sys,os,json,subprocess,zipfile,time,argparse
ROOT=Path(__file__).resolve().parents[2];W=ROOT.parent;PROJECT=W/'better-mc-remake'
sys.path.insert(0,str(PROJECT/'scripts'));import local_mc_runtime as rt
parser=argparse.ArgumentParser(description="Build the marked private native held probe with explicit existing dependency artifacts")
for option in ['java-home','core','framework','zombie']:parser.add_argument('--'+option,type=Path,required=True)
args=parser.parse_args()
J=args.java_home.resolve(strict=True);GAME=W/'_client_test/game';SERVER=W/'bmc5server'
manifest=json.loads((ROOT/'build/131-held-latest.json').read_text());OUT=Path(manifest['directory']);out=OUT/('qa-'+time.strftime('%Y%m%d-%H%M%S'));out.mkdir()
# Reuse already validated committed dependency artifacts, without compiling shader dirty.
core=args.core.resolve(strict=True)
framework=args.framework.resolve(strict=True)
zombie=args.zombie.resolve(strict=True)
def select(base,pattern):
 found=list(base.glob(pattern));assert len(found)==1,(pattern,found);return found[0]
mods=[Path(manifest['candidate']),core,framework,zombie,select(GAME/'mods','*mcef-neoforge*.jar')]
mods += [select(SERVER/'mods',pattern) for pattern in ['tacz-neoforge-*.jar','balm-neoforge-*.jar','waystones-neoforge-*.jar','architectury-*.jar','*champions-neoforge*.jar','*sophisticatedbackpacks*.jar','*sophisticatedcore*.jar','curios-*.jar']]
mods += [W/'muxi-outbreak/build/equipment-research/LesRaisins-Tactical-Equipements-1.21.1-0.4.3.jar',W/'muxi-outbreak/build/libs/muxi-outbreak-0.4.0-equipment.1.jar']
meta=rt.client_metadata(GAME,'BatterMC5Remake');libs=rt.client_libraries(GAME,'BatterMC5Remake',meta);cp=rt.javac_classpath(SERVER,GAME,'21.1.250',libs)+os.pathsep+os.pathsep.join(map(str,mods))
classes=out/'classes';classes.mkdir();sources=list((ROOT/'tests/held-input/java').glob('*.java'))+[PROJECT/'tests/terminal-mcef-environment/OfflineMcefMixin.java']
args=out/'javac.args';rt.argfile(args,['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',classes,*sources])
with (out/'compile.log').open('wb') as log:result=subprocess.run([str(J/'bin/javac.exe'),'@'+str(args)],stdout=log,stderr=subprocess.STDOUT)
if result.returncode:raise RuntimeError('Native held QA compilation failed: '+str(out/'compile.log'))
jar=out/'terminal-held-QA-ONLY.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="terminal_held_qa"\nversion="0.0.1"\ndisplayName="Held terminal QA ONLY"\n[[mixins]]\nconfig="held-qa.mixins.json"\n[[mixins]]\nconfig="held-offline.mixins.json"\n')
 z.writestr('held-qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.heldqa.mixin','compatibilityLevel':'JAVA_21','client':['HeldBrowserMixin','HeldBridgeMixin','HeldRendererMixin','HeldHandMixin'],'injectors':{'defaultRequire':1}}))
 z.writestr('held-offline.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['OfflineMcefMixin'],'injectors':{'defaultRequire':1}}))
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
mods.append(jar);manifest['mods']=[str(x) for x in mods];manifest['qaJar']=str(jar);manifest['port']=25732;manifest['lab']=str(PROJECT/'build/local-mc-debug'/('131-held-'+time.strftime('%Y%m%d-%H%M%S')));manifest['physicalOSInput']=False
(ROOT/'build/131-held-runtime.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps({'manifest':str(ROOT/'build/131-held-runtime.json'),'qaJar':str(jar),'lab':manifest['lab']}),flush=True)

import sys,os,json,shutil,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[3];T=R/'muxi-terminal';C=R/'muxi-localization';N=T/'build/native-final'
sys.path.insert(0,str(R/'better-mc-remake/scripts'));import local_mc_debug as main,local_mc_runtime as rt
terminal=Path(os.environ.get('MUXI_TERMINAL_CANDIDATE',str(T/'build/libs/muxi-terminal-0.2.4-window-ui.3.jar')));localization=C/'build/libs/muxi-localization-0.1.0-settings.3.jar';mcef=next((R/'bmc5server/mods').glob('*mcef*.jar'))
orig_compile=rt.compile_agent

def compile_agent(project,instance,jdk,classpath):
 jar=orig_compile(project,instance,jdk,classpath+os.pathsep+str(terminal)+os.pathsep+str(mcef))
 with zipfile.ZipFile(jar) as z:entries={n:z.read(n) for n in z.namelist()}
 cfg=json.loads(entries['local_mc_debug.mixins.json']);cfg['client'] += ['SettingsDrawMixin','SettingsLifecycleMixin'];entries['local_mc_debug.mixins.json']=json.dumps(cfg).encode();entries['window_qa_offline.mixins.json']=json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['OfflineMcefMixin'],'injectors':{'defaultRequire':1}}).encode();entries['META-INF/neoforge.mods.toml']+=b'\n[[mixins]]\nconfig="window_qa_offline.mixins.json"\n'
 root=Path('C:/Users/ranzh/Documents/Codex/2026-10-02/task/shop-ui-20261011/media');entries['assets/muxi_horse_racing/textures/gui/umamusume_preview/approved/uma_silence_suzuka_v8_8e62cee00bb1.gif']=(root/'suzuka-v8-native-cover.gif').read_bytes();entries['assets/muxi_horse_racing/textures/gui/umamusume_preview/uma_silence_suzuka.png']=(root/'suzuka-v8-native-first.png').read_bytes()
 with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
  for name,data in entries.items():z.writestr(name,data)
 return jar
rt.compile_agent=compile_agent
orig_copy=rt.copy_inputs

def copy_inputs(home,mods,data):
 orig_copy(home,mods,data)
 if mcef in mods:
  shutil.copytree(R/'_client_test/game/mods/mcef-libraries',home/'mods/mcef-libraries');(home/'config/mcef').mkdir(exist_ok=True);(home/'config/mcef/mcef.properties').write_text('skip-download=true\nuse-cache=false\n',encoding='utf-8')
rt.copy_inputs=copy_inputs
orig_client=rt.client_arguments

def client_args(game,version,metadata,libraries,instance,name,memory,natives,unix_temp):
 packs=instance/'resourcepacks';packs.mkdir(exist_ok=True)
 for letter in ['a','b']:
  with zipfile.ZipFile(packs/('qa-user-'+letter+'.zip'),'w') as z:
   z.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':34,'description':'Private QA user pack '+letter}}))
   if letter=='a':z.writestr('assets/sodium/lang/zh_cn.json',json.dumps({'sodium.options.buttons.apply':'\u73a9\u5bb6\u81ea\u9009\u5e94\u7528'},ensure_ascii=False).encode())
 p=instance/'options.txt';p.write_text(p.read_text(encoding='utf-8')+'resourcePacks:["vanilla","mod_resources","file/qa-user-a.zip","file/qa-user-b.zip"]\nguiScale:2\n',encoding='utf-8')
 args=orig_client(game,version,metadata,libraries,instance,name,memory,natives,unix_temp)
 for key,value in [('--width','1280'),('--height','960')]:
  if key in args:args[args.index(key)+1]=value
  else:args += [key,value]
 return args
rt.client_arguments=client_args
mods=[terminal,mcef,localization]+[R/'_client_test/game/mods'/name for name in ['sodium-neoforge-0.8.13+mc1.21.1.jar','reeses-sodium-options-neoforge-2.2.4+mc1.21.1.jar','sodiumdynamiclights-neoforge-1.0.10-1.21.1.jar','sodiumextras-neoforge-1.0.8-1.21.1.jar','slc_unofficial-neoforge-3.1.1+1.21.1.jar','iris-neoforge-1.8.14-beta.1+mc1.21.1.jar','IrisSearch-1.8.1-neoforge.jar','iris_shader_folder-1.4.2-neoforge.jar','txnilib-neoforge-1.0.24-1.21.1.jar','cloth-config-15.0.140-neoforge.jar']]
args=['run','--project-root',str(T),'--instance-root',os.environ.get('MUXI_NATIVE_LAB',str(T/'build/local-mc-debug/window-locale-graceful3-20261013')),'--server-runtime',str(R/'bmc5server'),'--client-game',str(R/'_client_test/game'),'--java-home','C:/Users/ranzh/Documents/Codex/terminal-integration-task6-20261001/tools/jdk/jdk-21.0.12.1+1','--port','26133','--clients','1','--client-name','FinalUi131','--accept-eula','--unix-temp','C:/Temp','--mode','hold','--hold-seconds','900','--server-memory-mb','1536','--client-memory-mb','4096','--client-simulation-distance','5','--boot-timeout','180','--shutdown-timeout','90','--visible','--jvm-property','settings.qa.languages='+str(N/'labels.json')]
for mod in mods:args += ['--client-mod',str(mod)]
sys.exit(main.main(args))

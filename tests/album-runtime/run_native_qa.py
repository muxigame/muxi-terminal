"""Independent visible 131 QA; only own lab inputs/output, normal MC shutdown, no OS kill."""
from pathlib import Path
import argparse,ctypes,datetime,hashlib,json,os,shutil,subprocess,sys,time,uuid,zipfile,psutil
SOURCES=Path(__file__).resolve().parent
REPO=SOURCES.parents[1]
ROOT=Path(os.environ.get('ALBUM_QA_WORKDIR',str(REPO/'build/album-native-qa'))).resolve()
ROOT.mkdir(parents=True,exist_ok=True)
GAME=Path(os.environ.get('ALBUM_QA_GAME',r'C:\Users\ranzh\workspace\dev\muxigame\_client_test\game'))
JDK=Path(os.environ.get('ALBUM_QA_JDK',r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1'))
sys.path.insert(0,str(REPO));import build
def allowed(rules):
 if not rules:return True
 result=False
 for rule in rules:
  platform=rule.get('os',{})
  if platform.get('name','windows')!='windows' or platform.get('arch','x86_64') not in ('x86_64','amd64'):continue
  if any((key=='has_custom_resolution')!=value for key,value in rule.get('features',{}).items()):continue
  result=rule.get('action')=='allow'
 return result
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 sys.stdout.reconfigure(encoding='utf-8',errors='replace')
 p=argparse.ArgumentParser();p.add_argument('--prepare-only',action='store_true');p.add_argument('--lab',type=Path);p.add_argument('--other-instance',type=Path);p.add_argument('--cycles',type=int,default=8);args=p.parse_args()
 if not 1<=args.cycles<=32:raise SystemExit('Bounded 1..32 cycles required')
 session=ctypes.c_ulong();ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session))
 if os.environ.get('COMPUTERNAME')!='JBC_FCRL' or session.value!=2:raise SystemExit('131 interactive Session 2 required')
 if args.lab:lab=args.lab.resolve()
 else:
  meta=json.loads((GAME/'versions/BatterMC5Remake/BatterMC5Remake.json').read_text(encoding='utf-8'))
  libs=list(dict.fromkeys([GAME/'libraries'/x['downloads']['artifact']['path'] for x in meta['libraries'] if allowed(x.get('rules')) and x.get('downloads',{}).get('artifact')]+[GAME/'versions/BatterMC5Remake/BatterMC5Remake.jar']))
  missing=[str(x) for x in libs if not x.is_file()]
  if missing:raise SystemExit('Missing existing client libraries: '+str(missing[:5]))
  lab=ROOT/('album-runtime-'+datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]);lab.mkdir()
  for name in ('mods','config','natives'):(lab/name).mkdir()
  terminal=ROOT/'album-build/muxi-terminal-album-dev-qa.jar';shutil.copy2(terminal,lab/'mods'/terminal.name)
  for pattern in ('*mcef-neoforge-2.1.6-1.21.1.jar','*screenshot_viewer*.jar'):
   jar=next((GAME/'mods').glob(pattern));shutil.copy2(jar,lab/'mods'/jar.name)
  shutil.copytree(GAME/'mods/mcef-libraries',lab/'mods/mcef-libraries',dirs_exist_ok=True)
  shutil.copytree(GAME/'versions/BatterMC5Remake/BatterMC5Remake-natives',lab/'natives',dirs_exist_ok=True)
  (lab/'config/fml.toml').write_text('earlyWindowControl=false\nearlyWindowProvider=""\nversionCheck=false\n',encoding='utf-8')
  (lab/'config/mcef').mkdir();(lab/'config/mcef/mcef.properties').write_text('skip-download=true\nuse-cache=false\n',encoding='utf-8')
  (lab/'options.txt').write_text('lang:zh_cn\nguiScale:2\nmaxFps:30\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:2\nsimulationDistance:5\ngraphicsMode:0\n',encoding='utf-8')
  classes=lab/'qa-classes';sources=list((SOURCES/'java').rglob('*.java'))
  sources.extend(REPO/'tests/music-runtime/java/net/muxigame/terminal/qa/mixin'/name for name in ('HardwareWmiTimeoutQAMixin.java','OfflineMcefMixin.java'))
  build.compile_java(JDK/'bin/javac.exe',sources,classes,os.pathsep.join(map(str,[terminal,ROOT/'album-build/dependencies.jar'])),lab/'compile.args')
  with zipfile.ZipFile(lab/'mods/muxi-album-qa-only.jar','w',zipfile.ZIP_DEFLATED) as z:
   z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_album_qa"\nversion="1.0.0"\ndisplayName="Album private 131 QA"\n[[mixins]]\nconfig="album_runtime_qa.mixins.json"\n[[mixins]]\nconfig="album_image_qa.mixins.json"\n')
   z.writestr('album_runtime_qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['HardwareWmiTimeoutQAMixin','OfflineMcefMixin'],'injectors':{'defaultRequire':1}}))
   z.writestr('album_image_qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.albumqa.mixin','compatibilityLevel':'JAVA_21','client':['ScreenshotProbeMixin','ImageProbeMixin'],'injectors':{'defaultRequire':1}}))
   for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes).as_posix())
  subs={'auth_player_name':'AlbumQA131','auth_uuid':'91f9bc33f8644a1093cd41bd94eb05aa','auth_access_token':'0','version_name':'BatterMC5Remake','game_directory':str(lab),'assets_root':str(GAME/'assets'),'assets_index_name':meta['assetIndex']['id'],'clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release','resolution_width':'1280','resolution_height':'720','natives_directory':str(lab/'natives'),'launcher_name':'album-private-visible-qa','launcher_version':'1','classpath':os.pathsep.join(map(str,libs)),'library_directory':str(GAME/'libraries'),'classpath_separator':os.pathsep}
  def expand(items):
   out=[]
   for item in items:
    if isinstance(item,dict):
     if not allowed(item.get('rules')):continue
     values=item['value'] if isinstance(item['value'],list) else [item['value']]
    else:values=[item]
    for value in values:
     for key,replacement in subs.items():value=value.replace('${'+key+'}',replacement)
     if '${' in value:raise ValueError('Unresolved launch template '+value)
     out.append(value)
   return out
  extra=['-Dalbum.cycles='+str(args.cycles)]
  if args.other_instance:
   other=args.other_instance.resolve()
   if not other.is_dir() or other.parent.parent!=ROOT or not other.parent.name.startswith('album-runtime-') or other.name!='screenshots':raise SystemExit('Only another own private native screenshot instance permitted')
   extra.append('-Dalbum.otherInstance='+str(other))
  javaargs=['-Xms512M','-Xmx2G','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8',*extra,'-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=9','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=9',*expand(meta['arguments']['jvm']),meta['mainClass'],*expand(meta['arguments']['game'])]
  (lab/'launch.args').write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in javaargs),encoding='utf-8')
  write(lab/'inputs.json',{'session':session.value,'terminalSha256':sha(terminal),'source':json.loads((ROOT/'album-build/build.json').read_text(encoding='utf-8')),'qaOnlyWmiTimeoutMs':2000,'nativeF2Input':'owned KeyboardHandler F2 path','focusScope':'only this newly launched native GLFW window; no OS input','photosCopied':False,'productionWrites':False})
  write(ROOT/'album-runtime-latest.json',{'lab':str(lab),'prepareOnly':args.prepare_only})
 if args.prepare_only:print(json.dumps({'prepared':True,'lab':str(lab)}));return
 if lab.parent!=ROOT or not lab.name.startswith('album-runtime-'):raise SystemExit('Own lab boundary required')
 if (lab/'pid.json').exists():raise SystemExit('Do not launch same private lab twice')
 own_env=dict(os.environ);own_env.pop('MUXI_TERMINAL_GAME_CREDENTIAL',None);own_env.pop('JAVA_TOOL_OPTIONS',None)
 memory=[]
 with (lab/'boot.log').open('w',encoding='utf-8') as log:
  proc=subprocess.Popen([str(JDK/'bin/java.exe'),'@'+str(lab/'launch.args')],cwd=lab,env=own_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
  write(lab/'pid.json',{'pid':proc.pid,'session':session.value});print(json.dumps({'launched':True,'pid':proc.pid,'lab':str(lab)}),flush=True)
  deadline=time.monotonic()+600;close_requested=False;previous='';previous_memory=0;last_close_memory_cycle=-1
  while proc.poll() is None:
   if not close_requested and time.monotonic()>deadline-60:close_requested=True;write(lab/'request-normal-close.json',{'reason':'bounded deadline; only normal logout/stop requested'})
   if time.monotonic()>deadline:
    write(lab/'exit.json',{'exitCode':None,'cleanExit':False,'stillRunning':True,'forceTerminationPerformed':False});raise SystemExit('Private QA requires normal close; no kill: '+str(proc.pid))
   try:
    progress=(lab/'album-progress.json').read_text(encoding='utf-8')
    if progress!=previous:
     value=json.loads(progress)
     if value['stage']!=json.loads(previous or '{"stage":-1}').get('stage') or value.get('cycle')!=json.loads(previous or '{}').get('cycle'):print('PROGRESS='+progress,flush=True)
     previous=progress
     closed=value['stage']==20 and value.get('screen')=='' and value.get('dom',{}).get('sourceCount')==0
     if time.monotonic()-previous_memory>5 or (closed and value['cycle']!=last_close_memory_cycle):
      owned=[psutil.Process(proc.pid)]+psutil.Process(proc.pid).children(recursive=True)
      counts=[]
      for process in owned:
       try:info=process.memory_info();counts.append({'pid':process.pid,'kind':next((a[7:] for a in process.cmdline() if a.startswith('--type=')),'minecraft-or-cef-browser'),'private':getattr(info,'private',info.rss),'rss':info.rss})
       except psutil.Error:pass
      memory.append({'progress':value,'processes':counts,'privateBytes':sum(x['private'] for x in counts)});previous_memory=time.monotonic()
      if closed:last_close_memory_cycle=value['cycle']
   except (OSError,ValueError,psutil.Error):pass
   time.sleep(1)
  code=proc.wait()
 result=json.loads((lab/'album-runtime-result.json').read_text(encoding='utf-8')) if (lab/'album-runtime-result.json').exists() else {'success':False,'error':'No native result'}
 result.update({'exitCode':code,'cleanExit':code==0 and result.get('normalLogout',False),'session':session.value,'forceTerminationPerformed':False,'lab':str(lab),'screenshots':{p.name:sha(p) for p in lab.glob('*.png')}})
 write(lab/'process-memory.json',memory);write(lab/'run-summary.json',result);write(lab/'exit.json',{'exitCode':code,'cleanExit':result['cleanExit'],'forceTerminationPerformed':False})
 print(json.dumps({'success':result.get('success'),'checks':len(result.get('checks',[])),'cleanExit':result['cleanExit'],'error':result.get('error'),'lab':str(lab)},ensure_ascii=False),flush=True)
 if not result.get('success') or not result['cleanExit']:raise SystemExit(1)
if __name__=='__main__':main()

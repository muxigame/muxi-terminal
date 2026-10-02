"""131 developer music QA: real visible MC/MCEF/OpenAL, private data, no foreground input."""
from __future__ import annotations
from pathlib import Path
import argparse,ctypes,datetime,hashlib,json,os,shutil,subprocess,sys,time,uuid,zipfile
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(ROOT))
import build

def allowed(rules):
    if not rules:return True
    result=False
    for rule in rules:
        platform=rule.get('os',{})
        if platform.get('name','windows')!='windows':continue
        if platform.get('arch','x86_64') not in ('x86_64','amd64'):continue
        if any((key=='has_custom_resolution')!=value for key,value in rule.get('features',{}).items()):continue
        result=rule.get('action')=='allow'
    return result

def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workspace',type=Path,required=True)
    p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--prepare-only',action='store_true')
    p.add_argument('--with-portable',action='store_true',help='Include the installed original music mods and an offline private playlist fixture')
    args=p.parse_args()
    session=ctypes.c_ulong();ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session))
    if os.environ.get('COMPUTERNAME')!='JBC_FCRL' or session.value!=2:raise SystemExit('131 interactive Session 2 required')
    game=args.workspace/'_client_test/game'
    meta=json.loads((game/'versions/BatterMC5Remake/BatterMC5Remake.json').read_text(encoding='utf-8'))
    libs=list(dict.fromkeys([game/'libraries'/x['downloads']['artifact']['path'] for x in meta['libraries'] if allowed(x.get('rules')) and x.get('downloads',{}).get('artifact')]+[game/'versions/BatterMC5Remake/BatterMC5Remake.jar']))
    missing=[str(x) for x in libs if not x.is_file()]
    if missing:raise SystemExit('Missing installed client libraries: '+str(missing[:5]))
    compiler,runtime=build.java_tools(args.java_home)
    lab=ROOT/'build'/('music-runtime-'+datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]);lab.mkdir()
    for name in ('mods','config','natives'):(lab/name).mkdir()
    terminal=ROOT/'build/libs'/json.loads((ROOT/'build/release.json').read_text(encoding='utf-8'))['artifact']
    shutil.copy2(terminal,lab/'mods'/terminal.name)
    mcef=next((game/'mods').glob('*mcef-neoforge-2.1.6-1.21.1.jar'))
    shutil.copy2(mcef,lab/'mods'/mcef.name)
    if args.with_portable:
        for pattern in ('*net_music_list*.jar','*netmusic-*.jar','cloth-config-*-neoforge.jar'):
            installed=list((game/'mods').glob(pattern))
            if len(installed)!=1:raise SystemExit('Ambiguous installed original music dependency: '+pattern)
            shutil.copy2(installed[0],lab/'mods'/installed[0].name)
    shutil.copytree(game/'mods/mcef-libraries',lab/'mods/mcef-libraries',dirs_exist_ok=True)
    shutil.copytree(game/'versions/BatterMC5Remake/BatterMC5Remake-natives',lab/'natives',dirs_exist_ok=True)
    (lab/'config/fml.toml').write_text('earlyWindowControl=false\nearlyWindowProvider=""\nversionCheck=false\n',encoding='utf-8')
    (lab/'config/mcef').mkdir()
    (lab/'config/mcef/mcef.properties').write_text('skip-download=true\nuse-cache=false\n',encoding='utf-8')
    (lab/'options.txt').write_text('lang:zh_cn\nguiScale:2\nmaxFps:45\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:0.2\nsoundCategory_music:0.5\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:2\nsimulationDistance:5\ngraphicsMode:0\n',encoding='utf-8')
    sources=list((HERE/'java').rglob('*.java'))
    classes=lab/'qa-classes'
    build.compile_java(compiler,sources,classes,os.pathsep.join(map(str,[terminal,ROOT/'build/music-test-dependencies.jar',mcef])),lab/'compile.args')
    with zipfile.ZipFile(lab/'mods/muxi-music-qa-only.jar','w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_music_qa"\nversion="1.0.0"\ndisplayName="Music isolated QA"\n[[mixins]]\nconfig="music_runtime_qa.mixins.json"\n[[mixins]]\nconfig="music_runtime_focus.mixins.json"\n')
        z.writestr('music_runtime_qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.qa.mixin','compatibilityLevel':'JAVA_21','client':['HardwareWmiTimeoutQAMixin','OfflineMcefMixin'],'injectors':{'defaultRequire':1}}))
        z.writestr('music_runtime_focus.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.terminal.client.music.qa','compatibilityLevel':'JAVA_21','client':['FocusPreservingMixin'],'injectors':{'defaultRequire':1}}))
        for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes).as_posix())
    subs={'auth_player_name':'MusicQA131','auth_uuid':'c53d9bc3f8644a1093cd41bd94eb05ff','auth_access_token':'0','version_name':'BatterMC5Remake','game_directory':str(lab),'assets_root':str(game/'assets'),'assets_index_name':meta['assetIndex']['id'],'clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release','resolution_width':'1280','resolution_height':'720','natives_directory':str(lab/'natives'),'launcher_name':'music-private-visible-qa','launcher_version':'1','classpath':os.pathsep.join(map(str,libs)),'library_directory':str(game/'libraries'),'classpath_separator':os.pathsep}
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
    javaargs=['-Xms512M','-Xmx2G','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-Dqa.music.portable='+str(args.with_portable).lower(),'-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=9','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=9',*expand(meta['arguments']['jvm']),meta['mainClass'],*expand(meta['arguments']['game'])]
    (lab/'launch.args').write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in javaargs),encoding='utf-8')
    write(lab/'inputs.json',{'session':session.value,'terminalSha256':sha(terminal),'sourceHead':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceDirty':subprocess.check_output(['git','status','--short'],cwd=ROOT,text=True),'foregroundInput':False,'qaOnlyWmiTimeoutMs':2000,'gameMods':['minecraft','neoforge','muxi_terminal','mcef','muxi_music_qa']+(['netmusic','net_music_list','cloth_config'] if args.with_portable else []),'installedModHashes':{j.name:sha(j) for j in (lab/'mods').glob('*.jar')},'filePickerVerified':False,'productionWrites':False})
    write(ROOT/'build/music-runtime-latest.json',{'lab':str(lab),'prepareOnly':args.prepare_only})
    if args.prepare_only:print(json.dumps({'prepared':True,'lab':str(lab)}));return
    kernel=ctypes.windll.user32;foreground=int(kernel.GetForegroundWindow())
    own_env=dict(os.environ);own_env.pop('MUXI_TERMINAL_GAME_CREDENTIAL',None)
    with (lab/'boot.log').open('w',encoding='utf-8') as log:
        proc=subprocess.Popen([str(runtime),'@'+str(lab/'launch.args')],cwd=lab,env=own_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        write(lab/'pid.json',{'pid':proc.pid,'session':session.value,'foregroundWindowBefore':foreground})
        print(json.dumps({'launched':True,'pid':proc.pid,'lab':str(lab)}),flush=True)
        deadline=time.monotonic()+600;close_requested=False
        while proc.poll() is None:
            if not close_requested and time.monotonic()>deadline-60:
                close_requested=True;write(lab/'request-normal-close.json',{'reason':'bounded QA deadline; normal logout/stop requested'})
            if time.monotonic()>deadline:
                write(lab/'exit.json',{'exitCode':None,'cleanExit':False,'stillRunning':True,'forceTerminationPerformed':False});raise SystemExit('QA deadline; private process requires normal closure: '+str(proc.pid))
            time.sleep(1)
        code=proc.wait()
    result=json.loads((lab/'music-runtime-result.json').read_text(encoding='utf-8')) if (lab/'music-runtime-result.json').is_file() else {'success':False,'error':'no native result'}
    result.update({'exitCode':code,'cleanExit':code==0 and result.get('normalLogout',False),'session':session.value,'foregroundWindowBefore':foreground,'foregroundWindowAfter':int(kernel.GetForegroundWindow()),'forceTerminationPerformed':False,'lab':str(lab),'screenshots':{p.name:sha(p) for p in lab.glob('*.png')}})
    write(lab/'run-summary.json',result);write(lab/'exit.json',{'exitCode':code,'cleanExit':result['cleanExit'],'forceTerminationPerformed':False})
    print(json.dumps({'success':result.get('success'), 'checks':len(result.get('checks',[])), 'cleanExit':result['cleanExit'],'filePickerVerified':False,'lab':str(lab)},ensure_ascii=False),flush=True)
    if not result.get('success') or not result['cleanExit']:raise SystemExit(1)

if __name__=='__main__':main()

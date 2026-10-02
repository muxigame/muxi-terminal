"""Task6 fixed, bounded, isolated native QA. Session 0 may only prepare files."""
from __future__ import annotations
import argparse,ctypes,datetime,hashlib,json,os,shutil,subprocess,sys,time,uuid,zipfile,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from qa_fml_config import configure_file,patch_properties
HERE=Path(__file__).resolve().parent
OWN=HERE.parent.resolve()
ROOT=Path(r'C:\Users\ranzh\workspace\dev\muxigame')
GAME=ROOT/'_client_test/game'
PACK=Path(r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001/candidate-pack')
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001/tools/jdk/jdk-21.0.12.1+1')
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1048576),b''):h.update(chunk)
    return h.hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def allowed(rules):
    if not rules:return True
    result=False
    for rule in rules:
        platform=rule.get('os',{})
        if platform.get('name','windows')!='windows':continue
        if platform.get('arch','x86_64') not in ('amd64','x86_64'):continue
        if any((key=='has_custom_resolution')!=value for key,value in rule.get('features',{}).items()):continue
        result=rule.get('action')=='allow'
    return result
class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path=='/redirect':
            self.send_response(302);self.send_header('Location','/external');self.end_headers();return
        child=self.path=='/iframe'
        js="""(()=>{const result={actualOrigin:location.origin,actualUrl:location.href,main:window===window.top,bridge:typeof window.muxiTerminalQuery,cefQuery:typeof window.cefQuery,grant:'none'};
        const finish=()=>{window.qaMain=result;if(window!==window.top)parent.postMessage({task6:true,result},location.origin);};
        if(typeof window.muxiTerminalQuery!=='function'){finish();return;}
        let pending=3;for(const request of ['map.open','tasks.snapshot','passport.account.request'])window.muxiTerminalQuery({request,persistent:false,onSuccess:r=>{result.grant='PRIVILEGED';result[request]=r;if(!--pending)finish();},onFailure:(c,m)=>{result[request]='denied:'+c;if(c!==403)result.grant='unexpected-denial';if(!--pending)finish();}});
        })();"""
        body=('<!doctype html><meta charset="utf-8"><title>Task6 actual external boundary fixture</title><h1>Actual external '+('iframe' if child else 'main')+'</h1><script>window.addEventListener("message",e=>{if(e.origin===location.origin&&e.data?.task6)window.qaIframe=e.data.result;});</script>'+('' if child else '<iframe src="/iframe" title="Actual external subframe"></iframe>')+'<script>'+js+'</script>').encode('utf-8')
        self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def log_message(self,*args):pass
def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['preflight','ui','network','all'])
    parser.add_argument('--prepare-only',action='store_true')
    parser.add_argument('--assigned-slot',default='')
    parser.add_argument('--visible',action='store_true')
    args=parser.parse_args()
    if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL':raise SystemExit('131 only; no client launched')
    if OWN!=Path(r'C:\Users\ranzh\Documents\Codex\task6-native-map-qa-20261002').resolve():raise SystemExit('Unexpected private QA root')
    session=ctypes.c_ulong()
    if not ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session)):raise SystemExit('No session ID')
    if not args.prepare_only and (session.value==0 or args.assigned_slot!='task6-native-map' or not args.visible):raise SystemExit('Assigned interactive visible desktop required; session 0 is preparation only')
    lock=json.loads((OWN/'qa-input-lock.json').read_text(encoding='utf-8'))
    for item in lock['files']:
        path=OWN/item['path']
        if digest(path)!=item['sha256']:raise SystemExit('QA input hash differs: '+item['path'])
    home=OWN/'qa-runtime';home.mkdir(exist_ok=True)
    # Only this private launch is constrained. Never stop or alter somebody else's client.
    active=home/'active.json'
    if not args.prepare_only and active.is_file():
        old=json.loads(active.read_text(encoding='utf-8'))
        pid=old.get('pid')
        if pid:
            kernel=ctypes.windll.kernel32;kernel.OpenProcess.restype=ctypes.c_void_p
            kernel.GetExitCodeProcess.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)]
            kernel.CloseHandle.argtypes=[ctypes.c_void_p]
            handle=kernel.OpenProcess(0x1000,False,int(pid))
            if handle:
                try:
                    status=ctypes.c_ulong()
                    if not kernel.GetExitCodeProcess(handle,ctypes.byref(status)) or status.value==259:raise SystemExit('Previous task6 private PID still running')
                finally:kernel.CloseHandle(handle)
    lab=home/(args.mode+'-'+datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]);lab.mkdir()
    meta=json.loads((GAME/'versions/BatterMC5Remake/BatterMC5Remake.json').read_text(encoding='utf-8'))
    libs=list(dict.fromkeys([GAME/'libraries'/x['downloads']['artifact']['path'] for x in meta['libraries'] if allowed(x.get('rules')) and x.get('downloads',{}).get('artifact')]+[GAME/'versions/BatterMC5Remake/BatterMC5Remake.jar']))
    missing=[str(p) for p in libs if not p.is_file()]
    if missing:raise SystemExit('Missing client libraries: '+str(missing[:5]))
    natives=GAME/'versions/BatterMC5Remake/BatterMC5Remake-natives'
    qa=OWN/'inputs/task6-native-qa-only.jar'
    receipt={'owner':'task6','privateLab':str(lab),'mode':args.mode,'session':session.value,'computer':os.environ['COMPUTERNAME'],'prepareOnly':args.prepare_only,'productionWrites':False,'sourceLock':lock}
    fixture=None
    if args.mode=='preflight':
        javaargs=['-Xms32M','-Xmx128M','-Dfile.encoding=UTF-8','-Dorg.lwjgl.librarypath='+str(natives),'-Djava.library.path='+str(natives),'-cp',os.pathsep.join(map(str,[qa,*libs])),'net.muxigame.terminal.qa.TerminalRenderPreflight']
        timeout=20;result_name='render-preflight-result.json'
    else:
        if not args.prepare_only:
            previous=json.loads((home/'last-preflight.json').read_text(encoding='utf-8'))
            if not previous.get('success') or previous.get('session')!=session.value or time.time()-previous.get('finishedEpoch',0)>3600:raise SystemExit('Current desktop preflight required')
        for name in ['mods','config','natives']:(lab/name).mkdir()
        shutil.copytree(natives,lab/'natives',dirs_exist_ok=True)
        shutil.copytree(GAME/'mods/mcef-libraries',lab/'mods/mcef-libraries',dirs_exist_ok=True)
        # Read the installed release fixture; copied mods and all config edits stay in this lab.
        expected_path=PACK.parent/'expected-pack.json'
        entries=json.loads(expected_path.read_text(encoding='utf-8'))['files']
        for item in entries:
            source=PACK/item['path']
            if not source.is_file():raise SystemExit('Pack fixture missing: '+item['path'])
            if hashlib.sha1(source.read_bytes()).hexdigest()!=item['sha1']:raise SystemExit('Pack fixture changed: '+item['path'])
            if item['policy']=='Optional' and not item.get('defaultOn',False):continue
            if not source.name.endswith('.jar'):continue
            shutil.copy2(source,lab/'mods'/source.name)
        for name in ['config','defaultconfigs','resourcepacks','shaderpacks','kubejs','tacz']:
            source=PACK/name
            if source.is_dir():shutil.copytree(source,lab/name,dirs_exist_ok=True)
        # Replace only the private lab's Core/Terminal; read exact mod IDs rather than guessing names.
        for path in list((lab/'mods').glob('*.jar')):
            with zipfile.ZipFile(path) as archive:
                try:toml=archive.read('META-INF/neoforge.mods.toml').decode('utf-8')
                except KeyError:continue
            if any(('modId="'+mid+'"') in toml.replace(' ','') for mid in ['muxi_terminal','muxi_game_core']):path.unlink()
        for name in ['muxi-terminal-native-map-x-qa.jar','muxi-game-core-native-map-full-review.jar','task6-native-qa-only.jar']:
            shutil.copy2(OWN/'inputs'/name,lab/'mods'/name)
        write(lab/'config/muxi-game-core.json',{'schema':1,'features':{'identity':{'enabled':False},'login':{'enabled':False}}})
        configure_file(lab/'config/fml.toml')
        (lab/'config/mcef').mkdir(parents=True,exist_ok=True)
        patch_properties(lab/'config/mcef/mcef.properties',{'skip-download':'true','use-cache':'false','user-agent':'','download-mirror':''})
        patch_properties(lab/'config/iris.properties',{'enableShaders':'false','disableUpdateMessage':'true'})
        (lab/'options.txt').write_text('lang:zh_cn\nguiScale:2\nmaxFps:45\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:3\nsimulationDistance:5\ngraphicsMode:0\n',encoding='utf-8')
        receipt['mods']=[{'name':p.name,'sha256':digest(p)} for p in sorted((lab/'mods').glob('*.jar'))]
        subs={'auth_player_name':'10000','auth_uuid':'c53d9bc3f8644a1093cd41bd94eb05ff','auth_access_token':'0','version_name':'BatterMC5Remake','game_directory':str(lab),'assets_root':str(GAME/'assets'),'assets_index_name':meta['assetIndex']['id'],'clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release','resolution_width':'1280','resolution_height':'720','natives_directory':str(lab/'natives'),'launcher_name':'task6-native-map-visible-qa','launcher_version':'1','classpath':os.pathsep.join(map(str,libs)),'library_directory':str(GAME/'libraries'),'classpath_separator':os.pathsep}
        def expand(items):
            out=[]
            for item in items:
                if isinstance(item,dict):
                    if not allowed(item.get('rules')):continue
                    values=item['value'] if isinstance(item['value'],list) else [item['value']]
                else:values=[item]
                for value in values:
                    for key,replacement in subs.items():value=value.replace(chr(36)+'{'+key+'}',replacement)
                    if chr(36)+'{' in value:raise ValueError('Unresolved launch variable: '+value)
                    out.append(value)
            return out
        fixture_url=''
        if not args.prepare_only:
            fixture=ThreadingHTTPServer(('127.0.0.1',0),Fixture)
            threading.Thread(target=fixture.serve_forever,daemon=True).start()
            fixture_url='http://127.0.0.1:'+str(fixture.server_port)
            write(lab/'fixture-port.json',{'url':fixture_url,'owner':'task6','loopbackOnly':True})
        javaargs=['-Xms512M','-Xmx6G','-XX:ActiveProcessorCount=4','-Dfile.encoding=UTF-8','-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=9','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=9','-Dqa.mode='+args.mode,'-Dqa.ownerRoot='+str(OWN),'-Dqa.fixtureUrl='+fixture_url]+expand(meta['arguments']['jvm'])+[meta['mainClass']]+expand(meta['arguments']['game'])
        timeout=900;result_name='native-map-result.json'
    write(lab/'inputs.json',receipt)
    (lab/'launch.args').write_text('\n'.join('"'+str(a).replace('\\','/').replace('"','\\"')+'"' for a in javaargs),encoding='utf-8')
    write(home/'prepared.json',{'lab':str(lab),'mode':args.mode,'session':session.value})
    if args.prepare_only:print(json.dumps({'prepared':True,'clientStarted':False,'lab':str(lab)}));return
    own_env=dict(os.environ);own_env.pop('MUXI_TERMINAL_GAME_CREDENTIAL',None)
    timed_out=False;normal_close_requested=False;left_running=False
    with (lab/'boot.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([str(JDK/'bin/java.exe'),'@'+str(lab/'launch.args')],cwd=lab,env=own_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        write(active,{'lab':str(lab),'pid':process.pid,'mode':args.mode,'session':session.value})
        print(json.dumps({'launched':True,'pid':process.pid,'lab':str(lab)}),flush=True)
        deadline=time.monotonic()+timeout
        while process.poll() is None:
            if not normal_close_requested and time.monotonic()>deadline-60:
                normal_close_requested=True
                write(lab/'request-normal-close.json',{'owner':'task6','pid':process.pid,'lab':str(lab.resolve()),'reason':'QA approaching bounded deadline; request normal logout and Minecraft.stop, never force termination'})
                print('Requested normal closure of this private QA instance before deadline',flush=True)
            if time.monotonic()>deadline:
                timed_out=True;left_running=True
                write(lab/'normal-close-pending.json',{'pid':process.pid,'lab':str(lab),'timeout':True,'cleanExit':False,'processStillRunning':True,'forceTerminationPerformed':False})
                print('Deadline exceeded. QA failed; own process still running and needs normal closure. No process was terminated.',flush=True)
                break
            time.sleep(1)
        code=process.poll() if left_running else process.wait()
    result=json.loads((lab/result_name).read_text(encoding='utf-8')) if (lab/result_name).is_file() else {'success':False,'error':'Missing native result'}
    result.update({'exitCode':code,'timeout':timed_out,'cleanExit':code==0 and not timed_out and not left_running,'processStillRunning':left_running,'forceTerminationPerformed':False,'normalCloseRequested':normal_close_requested,'lab':str(lab),'session':session.value,'finishedEpoch':time.time(),'screenshotSha256':{p.name:digest(p) for p in lab.glob('*.png')}})
    write(lab/'run-summary.json',result);write(lab/'exit.json',{'exitCode':code,'timeout':timed_out})
    if args.mode=='preflight':write(home/'last-preflight.json',result)
    if fixture is not None:fixture.shutdown();fixture.server_close()
    print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
    if not result.get('success') or code!=0 or timed_out:raise SystemExit(1)
if __name__=='__main__':main()

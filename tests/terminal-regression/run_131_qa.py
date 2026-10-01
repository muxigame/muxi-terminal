"""Visible bounded 131 QA. Preflight first. --prepare-only never starts a window/client."""
from __future__ import annotations
import re
from pathlib import Path
import argparse,ctypes,datetime,hashlib,json,os,shutil,subprocess,sys,time,uuid,zipfile
from qa_fml_config import configure_file
HERE=Path(__file__).resolve().parent
REPO=HERE.parents[1]
ROOT=Path(r"C:\Users\ranzh\workspace\dev\muxigame")
OWN=Path(r"C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001")
BASE="bc415775dd50762262098d6a41f0a942df7ef157"
EXPECTED=None
def write(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")
def digest(path,kind="sha256"):
    h=hashlib.new(kind)
    with path.open("rb") as stream:
        for data in iter(lambda:stream.read(1048576),b""):h.update(data)
    return h.hexdigest()

def refuse_existing_qa(home):
    kernel=ctypes.windll.kernel32
    kernel.OpenProcess.restype=ctypes.c_void_p
    kernel.GetExitCodeProcess.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)]
    kernel.QueryFullProcessImageNameW.argtypes=[ctypes.c_void_p,ctypes.c_ulong,ctypes.c_wchar_p,ctypes.POINTER(ctypes.c_ulong)]
    kernel.CloseHandle.argtypes=[ctypes.c_void_p]
    for receipt in home.glob("active-*.json"):
        previous=json.loads(receipt.read_text(encoding="utf-8"));pid=previous.get("pid")
        if not pid:continue
        handle=kernel.OpenProcess(0x1000,False,int(pid))
        if not handle:continue
        try:
            code=ctypes.c_ulong();name=ctypes.create_unicode_buffer(32768);size=ctypes.c_ulong(len(name))
            if not kernel.GetExitCodeProcess(handle,ctypes.byref(code)):raise SystemExit("Cannot verify prior QA PID "+str(pid))
            if code.value==259 and kernel.QueryFullProcessImageNameW(handle,0,name,ctypes.byref(size)) and Path(name.value).name.lower() in ("java.exe","javaw.exe"):
                raise SystemExit("Prior QA Java PID "+str(pid)+" is still running; normally close that QA window before retrying. No new client started.")
        finally:kernel.CloseHandle(handle)
def command(args):return subprocess.run(list(map(str,args)),check=True,capture_output=True,text=True,timeout=20).stdout.strip()
def allowed(rules):
    if not rules:return True
    result=False
    for rule in rules:
        platform=rule.get("os",{})
        if platform.get("name","windows")!="windows":continue
        if platform.get("arch","x86_64") not in ("x86_64","amd64"):continue
        if any((key=="has_custom_resolution")!=value for key,value in rule.get("features",{}).items()):continue
        result=rule.get("action")=="allow"
    return result
def argfile(path,args):path.write_text("\n".join('"'+str(a).replace("\\","/").replace('"','\\"')+'"' for a in args),encoding="utf-8")
def config(lab,audible=False):
    (lab/"config").mkdir(exist_ok=True)
    write(lab/"config/muxi-game-core.json",{"schema":1,"features":{"identity":{"enabled":False},"login":{"enabled":False}}})
    configure_file(lab/"config/fml.toml")
    (lab/"options.txt").write_text("lang:zh_cn\nguiScale:2\nmaxFps:45\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:"+("0.25" if audible else "0.0")+"\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:3\nsimulationDistance:5\ngraphicsMode:0\n",encoding="utf-8")
    (lab/"config/mcef").mkdir(parents=True,exist_ok=True)
    (lab/"config/mcef/mcef.properties").write_text("skip-download=true\nuse-cache=false\nuser-agent=\ndownload-mirror=\n",encoding="utf-8")
def testmod(lab,classes,mode):
    container=mode=="container"
    definitions=[{"required":True,"minVersion":"0.8","package":"net.muxigame.terminal.qa.mixin","compatibilityLevel":"JAVA_21","client":["OfflineMcefMixin"] if container else ["OfflineMcefMixin","ArmProbeMixin","PoseProbeMixin","BridgeProbeMixin"],"injectors":{"defaultRequire":1}}]
    if container:definitions.append({"required":True,"minVersion":"0.8","package":"net.muxigame.terminal.smoke.mixin","compatibilityLevel":"JAVA_21","client":["AccountClientFixtureMixin","PassportRequestFixtureMixin","PassportIdentityFixtureMixin","PassportCallbackFixtureMixin"],"injectors":{"defaultRequire":1}})
    with zipfile.ZipFile(lab/"mods/terminal-qa-only.jar","w",zipfile.ZIP_DEFLATED) as archive:
        toml='modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
        for i,definition in enumerate(definitions):
            name="terminal_qa_"+str(i)+".mixins.json";archive.writestr(name,json.dumps(definition));toml+='[[mixins]]\nconfig="'+name+'"\n'
        toml+='[[mods]]\nmodId="'+("muxi_terminal_smoke" if container else "terminal_qa")+'"\nversion="1.0.0"\ndisplayName="Isolated visible terminal QA"\n'
        archive.writestr("META-INF/neoforge.mods.toml",toml)
        for path in classes.rglob("*.class"):archive.write(path,path.relative_to(classes).as_posix())
def main():
    global EXPECTED
    sys.stdout.reconfigure(encoding="utf-8",errors="replace")
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode",choices=["preflight","full","firstperson"])
    parser.add_argument("--prepare-only",action="store_true")
    parser.add_argument("--jdk",type=Path,default=Path(r"C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1"))
    parser.add_argument("--game",type=Path,default=ROOT/"_client_test/game")
    parser.add_argument("--pack",type=Path,default=OWN/"candidate-pack")
    parser.add_argument("--dependencies",type=Path,default=ROOT/"bmc5server")
    parser.add_argument("--jar",type=Path,default=REPO/"build/libs/muxi-terminal-0.2.2.jar")
    parser.add_argument("--core-jar",type=Path)
    parser.add_argument("--assigned-slot",default=os.environ.get("TERMINAL_QA_SLOT",""))
    parser.add_argument("--visible",action="store_true",default=os.environ.get("TERMINAL_QA_VISIBLE")=="1")
    args=parser.parse_args()
    if os.environ.get("COMPUTERNAME","").upper()!="JBC_FCRL":raise SystemExit("131/JBC_FCRL only; no client started")
    session=ctypes.c_ulong()
    if not ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session)):raise SystemExit("Session ID unavailable")
    # Non-session-0 guard preserved. Static preparation cannot invoke any render/client main.
    if not args.prepare_only and session.value==0:raise SystemExit("Session0 is preparation only; launch manually in the assigned 131 desktop")
    if not args.prepare_only and args.assigned_slot!="task14-production-assigned":raise SystemExit("Parent-assigned 131 slot required")
    if not args.prepare_only and not args.visible:raise SystemExit("Visible desktop launch required")
    if not args.prepare_only:refuse_existing_qa(OWN/"qa-runtime")
    lock=json.loads((OWN/"release-lock.json").read_text(encoding="utf-8"))
    if lock["productCommits"]["muxi-terminal"]!=BASE:raise SystemExit("Final product commit differs from review")
    EXPECTED=lock["artifacts"]["muxi-terminal"]["sha256"]
    terminal=args.jar.resolve()
    if digest(terminal)!=EXPECTED:raise SystemExit("Unified jar differs from the handed-off 131 build")
    head=command(["git","-C",REPO,"rev-parse","HEAD"])
    command(["git","-C",REPO,"merge-base","--is-ancestor",BASE,"HEAD"])
    changed=command(["git","-C",REPO,"diff","--name-only",BASE,"--","src"])
    dirty=command(["git","-C",REPO,"status","--porcelain","--untracked-files=all","--","src"])
    if changed or dirty:raise SystemExit("Product sources differ from reviewed base: "+changed+dirty)
    sys.path.insert(0,str(REPO));import build
    compiler,runtime=build.java_tools(args.jdk)
    meta=json.loads((args.game/"versions/BatterMC5Remake/BatterMC5Remake.json").read_text(encoding="utf-8"))
    libs=[args.game/"libraries"/x["downloads"]["artifact"]["path"] for x in meta["libraries"] if allowed(x.get("rules")) and x.get("downloads",{}).get("artifact")]
    libs=list(dict.fromkeys(libs+[args.game/"versions/BatterMC5Remake/BatterMC5Remake.jar"]))
    missing=[str(p) for p in libs if not p.is_file()]
    if missing:raise SystemExit("Missing existing client libraries: "+json.dumps(missing[:10]))
    home=OWN/"qa-runtime";home.mkdir(exist_ok=True)
    lab=home/(args.mode+"-"+datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")+"-"+uuid.uuid4().hex[:8]);lab.mkdir()
    write(home/(("prepared-" if args.prepare_only else "active-")+args.mode+".json"),{"lab":str(lab),"mode":args.mode,"prepareOnly":args.prepare_only})
    provenance={"sourceBase":BASE,"gitHead":head,"terminal":str(terminal),"terminalSha256":EXPECTED,"session":session.value,"computer":os.environ["COMPUTERNAME"],"mode":args.mode,"prepareOnly":args.prepare_only,"game":str(args.game),"pack":str(args.pack),"jdk":str(args.jdk),"productionServerOperations":False}
    write(lab/"inputs.json",provenance)
    natives=args.game/"versions/BatterMC5Remake/BatterMC5Remake-natives"
    if not natives.is_dir():raise SystemExit("Existing client natives missing")
    classes=lab/"test-classes";fixture=None;fixture_module=None
    if args.mode=="preflight":
        print("Preparing standalone context preflight; no Minecraft world/mod boot",flush=True)
        build.compile_java(compiler,list((HERE/"preflight-java").rglob("*.java")),classes,os.pathsep.join(map(str,libs)),lab/"compile.args")
        javaargs=["-Xms32M","-Xmx128M","-Dfile.encoding=UTF-8","-Dorg.lwjgl.librarypath="+str(natives),"-Djava.library.path="+str(natives),"-cp",os.pathsep.join(map(str,[classes,*libs])),"net.muxigame.terminal.qa.TerminalRenderPreflight"]
        timeout=15;result_name="render-preflight-result.json"
    else:
        if not args.prepare_only:
            receipt=home/"last-preflight.json"
            if not receipt.is_file():raise SystemExit("Run bounded desktop preflight first")
            previous=json.loads(receipt.read_text(encoding="utf-8"))
            if not previous.get("success") or previous.get("terminalSha256")!=EXPECTED or previous.get("session")!=session.value or time.time()-previous.get("finishedEpoch",0)>86400:raise SystemExit("Successful current-session preflight required")
        for directory in ["mods","natives","config"]:(lab/directory).mkdir()
        shutil.copytree(natives,lab/"natives",dirs_exist_ok=True)
        shutil.copytree(args.game/"mods/mcef-libraries",lab/"mods/mcef-libraries",dirs_exist_ok=True)
        if args.mode=="container":
            if not args.core_jar or not args.core_jar.is_file():raise SystemExit("Container requires explicit reviewed SSO Core jar")
            core=args.core_jar.resolve()
            api=command([args.jdk/"bin/javap.exe","-classpath",core,"net.muxigame.core.client.TerminalPassportApi"])
            if "request(" not in api:raise SystemExit("Supplied Core has no TerminalPassportApi.request")
            shutil.copy2(core,lab/"mods"/core.name)
            for pattern in ["*mcef-neoforge-2.1.6-1.21.1.jar","balm-neoforge*.jar","waystones-neoforge*.jar","xaerominimap-neoforge*.jar","xaeroworldmap-neoforge*.jar"]:
                matches=list((args.pack/"mods").glob(pattern))
                if len(matches)!=1:raise SystemExit("Ambiguous container dependency "+pattern)
                shutil.copy2(matches[0],lab/"mods"/matches[0].name)
            sources=list((HERE/"container-java").rglob("*.java"))+[HERE/"java/net/muxigame/terminal/qa/mixin/OfflineMcefMixin.java"]
            provenance.update({"coreJar":str(core),"coreSha256":digest(core),"syntheticSSO":True,"productionSSO":False})
            timeout=180;result_name="client-smoke-result.json"
        else:
            print("Checking final candidate 1.4.26 mod hashes; preparing private full-pack copy",flush=True)
            expected=json.loads((OWN/"expected-pack.json").read_text(encoding="utf-8"))["files"];failures=[]
            for entry in expected:
                source=args.pack/entry["path"]
                if not source.is_file() or digest(source,"sha1")!=entry["sha1"]:failures.append(entry["path"])
            if failures:raise SystemExit("Remote pack differs from release fixture: "+json.dumps(failures[:15],ensure_ascii=False))
            for entry in expected:
                if entry["policy"]=="Optional" and not entry.get("defaultOn",False) and not (args.mode=="firstperson" and "firstperson-" in entry["path"]):continue
                source=args.pack/entry["path"];shutil.copy2(source,lab/"mods"/source.name)
            for entry in json.loads((OWN/"release-lock.json").read_text(encoding="utf-8")).get("additionalFiles",[]):
                source=args.pack/entry["path"]
                if not source.is_file() or digest(source)!=entry["sha256"]:raise SystemExit("Additional release asset mismatch "+entry["path"])
            for directory in ["config","shaderpacks","resourcepacks","defaultconfigs","kubejs","tacz"]:
                source=args.pack/directory
                if source.is_dir():shutil.copytree(source,lab/directory,dirs_exist_ok=True)
            (lab/"config/iris.properties").write_text("enableShaders=true\nshaderPack=Better MC - Low\ndisableUpdateMessage=true\n",encoding="utf-8")
            sources=list((HERE/"java").rglob("*.java"))
            provenance.update({"referencePack":"1.4.26","optionalSelection":"released defaultOn; enable FirstPerson only in firstperson mode","fixtureTasks":False,"fixtureIcons":False,"samplerGlobalRouter":False,"samplerHydration":False})
            timeout=900;result_name="runtime-result.json"
        config(lab,audible=args.mode=="full");shutil.copy2(terminal,lab/"mods"/terminal.name)
        fps=lab/"config/sodiumextras-client.toml"
        if fps.exists():
            raw=fps.read_text(encoding="utf-8");fps.write_text(re.sub(r'(fpsDisplay\s*=\s*)"[^"]*"',r'\1"OFF"',raw),encoding="utf-8")
        cp=os.pathsep.join(map(str,[terminal,args.game/"libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar",args.game/"libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar",*libs,*list((args.dependencies/"libraries").rglob("*.jar")),*list((lab/"mods").glob("*.jar"))]))
        print("Compiling private test mod",flush=True)
        build.compile_java(compiler,sources,classes,cp,lab/"compile.args");testmod(lab,classes,args.mode)
        fixture_url=""
        if args.mode=="container" and not args.prepare_only:
            sys.path.insert(0,str(REPO/"tests"));import run_integration_smoke as fixture_module
            from http.server import ThreadingHTTPServer
            import threading
            fixture=ThreadingHTTPServer(("127.0.0.1",0),fixture_module.Fixture)
            threading.Thread(target=fixture.serve_forever,daemon=True).start();fixture_url="http://127.0.0.1:"+str(fixture.server_port)
        subs={"auth_player_name":"10000","auth_uuid":"c53d9bc3f8644a1093cd41bd94eb05ff","auth_access_token":"0","version_name":"BatterMC5Remake","game_directory":str(lab),"assets_root":str(args.game/"assets"),"assets_index_name":meta["assetIndex"]["id"],"clientid":"","auth_xuid":"","user_type":"legacy","version_type":"release","resolution_width":"1280","resolution_height":"720","natives_directory":str(lab/"natives"),"launcher_name":"terminal-isolated-visible-qa","launcher_version":"1","classpath":os.pathsep.join(map(str,libs)),"library_directory":str(args.game/"libraries"),"classpath_separator":os.pathsep}
        def expand(items):
            result=[]
            for item in items:
                if isinstance(item,dict):
                    if not allowed(item.get("rules")):continue
                    vals=item["value"] if isinstance(item["value"],list) else [item["value"]]
                else:vals=[item]
                for value in vals:
                    for key,replacement in subs.items():value=value.replace(chr(36)+"{"+key+"}",replacement)
                    if chr(36)+"{" in value:raise ValueError("Unresolved launch substitution "+value)
                    result.append(value)
            return result
        javaargs=["-Xms512M","-Xmx2G" if args.mode=="container" else "-Xmx6G","-XX:ActiveProcessorCount=2" if args.mode=="container" else "-XX:ActiveProcessorCount=4","-Dfile.encoding=UTF-8","-Dhttp.proxyHost=127.0.0.1","-Dhttp.proxyPort=9","-Dhttps.proxyHost=127.0.0.1","-Dhttps.proxyPort=9","-Dqa.mode="+args.mode]
        if args.mode=="container":
            agent=lab/"tcp-selector-qa-only.jar"
            with zipfile.ZipFile(agent,"w",zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("META-INF/MANIFEST.MF","Manifest-Version: 1.0\nPremain-Class: net.muxigame.terminal.smoke.TcpSelectorQaAgent\n\n")
                name="net/muxigame/terminal/smoke/TcpSelectorQaAgent.class";archive.write(classes/name,name)
            javaargs+=["-javaagent:"+str(agent),"-Dmuxi.container.fixtureUrl="+fixture_url]
        javaargs+=expand(meta["arguments"]["jvm"])+[meta["mainClass"]]+expand(meta["arguments"]["game"])
        provenance["mods"]=[{"name":p.name,"sha256":digest(p)} for p in sorted((lab/"mods").glob("*.jar"))]
    write(lab/"inputs.json",provenance);argfile(lab/"launch.args",javaargs)
    if args.prepare_only:print(json.dumps({"prepared":True,"lab":str(lab),"clientStarted":False},ensure_ascii=False));return
    print(json.dumps({"phase":"launch","lab":str(lab),"visible":True,"timeoutSeconds":timeout},ensure_ascii=False),flush=True)
    own_env=dict(os.environ);own_env.pop("MUXI_TERMINAL_GAME_CREDENTIAL",None);timed_out=False
    try:
        with (lab/"boot.log").open("w",encoding="utf-8") as log:
            process=subprocess.Popen([str(runtime),"@"+str(lab/"launch.args")],cwd=lab,env=own_env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
            provenance["privatePid"]=process.pid;write(lab/"inputs.json",provenance)
            write(home/("active-"+args.mode+".json"),{"lab":str(lab),"mode":args.mode,"pid":process.pid})
            deadline=time.monotonic()+timeout;last_progress=None;last_print=0
            while process.poll() is None:
                if time.monotonic()>deadline:
                    timed_out=True;process.terminate()
                    try:process.wait(timeout=10)
                    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
                    break
                progress=lab/"runtime-progress.json"
                if progress.is_file():
                    value=progress.read_text(encoding="utf-8")
                    if value!=last_progress and time.monotonic()-last_print>5:print(value,flush=True);last_progress=value;last_print=time.monotonic()
                elif time.monotonic()-last_print>10:print("Waiting for bounded private "+args.mode+"; boot.log="+str(lab/"boot.log"),flush=True);last_print=time.monotonic()
                time.sleep(1)
            code=process.wait()
        write(lab/"exit.json",{"exitCode":code,"timeout":timed_out})
        try:result=json.loads((lab/result_name).read_text(encoding="utf-8")) if (lab/result_name).is_file() else {"error":"No result file"}
        except (ValueError,UnicodeError) as error:result={"error":"Unreadable result file: "+str(error)}
        if fixture_module is not None:
            rows=fixture_module.REPORTS;result["fixtureReports"]=rows
            external_ok=all(any(r.get("role")==[role] and r.get("bridge",[None])[0] in ["undefined","present-no-grant","denied:403"] for r in rows) for role in ["main","iframe"]) and not any(r.get("bridge")==["PRIVILEGED"] for r in rows)
            result["externalActualJsPassed"]=external_ok
            if not external_ok:result["success"]=False
        if args.mode=="firstperson":
            for scene in ["main-right-two","main-right-other","off-left-other","off-left-two","main-left-two","main-left-other","off-right-other","off-right-two"]:
                measured=result.get("scene-"+scene,{})
                if not measured.get("firstpersonLoaded") or not measured.get("firstpersonEnabled"):
                    result["completed"]=False;result["error"]="FirstPerson not actually enabled in "+scene
        if args.mode in ["full","firstperson"]:
            for view in ["tasks","reopen-tasks","repeat-tasks","held-tasks-day","held-tasks-night","held-tasks-night-off"]:
                if "cancel" in result.get(view,{}).get("error","").lower():
                    result["completed"]=False;result["error"]="Actual task UI displayed cancelled query in "+view
        result.update({"exitCode":code,"timeout":timed_out,"lab":str(lab),"terminalSha256":EXPECTED,"session":session.value,"finishedEpoch":time.time(),"cleanExit":code==0})
        result["screenshotSha256"]={p.name:digest(p) for p in lab.glob("*.png")}
        write(lab/"run-summary.json",result);write(lab/"exit.json",{"exitCode":code,"timeout":timed_out})
        if args.mode=="preflight":write(home/"last-preflight.json",result)
        print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
        good=result.get("success",False) if args.mode in ["preflight","container"] else result.get("completed",False)
        if not good or code!=0 or timed_out:raise SystemExit(1)
    finally:
        if fixture is not None:fixture.shutdown();fixture.server_close()
if __name__=="__main__":main()

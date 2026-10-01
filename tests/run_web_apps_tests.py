"""Compile pure policy/store tests and verify persistence in separate JVM invocations."""
from pathlib import Path
import importlib.util,json,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('terminal_build',ROOT/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
workspace=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT.parent
game=workspace/'_client_test/game'
gson=next((game/'libraries/com/google/code/gson/gson').rglob('gson-*.jar'))
compiler,java=build.java_tools(None)
lab=ROOT/'build/web-app-tests';lab.mkdir(parents=True,exist_ok=True)
src=ROOT/'src/main/java/net/muxigame/terminal/client'
sources=[src/'TerminalWebPolicy.java',src/'TerminalWebApps.java',*list((ROOT/'tests/web-apps/java').rglob('*.java'))]
build.compile_java(compiler,sources,lab/'classes',str(gson),lab/'compile.args')
results=[]
for mode in ['seed','reload','policy']:
    file=lab/('policy.json' if mode=='policy' else 'profile/web-apps.json')
    if mode in ['seed','policy'] and file.exists():file.unlink()
    run=subprocess.run([str(java),'-cp',str(lab/'classes')+';'+str(gson),'net.muxigame.terminal.client.WebAppsTest',mode,str(file)],capture_output=True,encoding='utf-8',check=True)
    results.append(run.stdout.strip())
report={'success':True,'checks':results,'persistence':'seed and reload run in distinct JVMs'}
(lab/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))

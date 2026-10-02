from pathlib import Path
import sys,importlib.util,subprocess,json,tempfile
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('build',root/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
javac,java=build.java_tools(Path(sys.argv[1]) if len(sys.argv)>1 else None)
deps=Path(sys.argv[2]) if len(sys.argv)>2 else root/'build/friends-test-dependencies.jar'
out=root/'build/friends-tests';out.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(dir=out) as tmp:
    tmp=Path(tmp);sources=[root/'src/main/java/net/muxigame/terminal/client'/name for name in ['TerminalFriendsPolicy.java','TerminalFriendsTransport.java']]+[root/'tests/friends/FriendsPolicyTest.java']
    build.compile_java(javac,sources,tmp/'classes',str(deps),tmp/'args')
    result=subprocess.run([str(java),'-cp',str(tmp/'classes')+';'+str(deps),'net.muxigame.terminal.client.FriendsPolicyTest'],capture_output=True)
    print(result.stdout.decode('utf-8','replace'));print(result.stderr.decode('utf-8','replace'));result.check_returncode()
    (out/'native-result.json').write_text(json.dumps(json.loads(result.stdout),indent=2)+'\n',encoding='utf-8')

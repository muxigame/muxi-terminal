from pathlib import Path
import subprocess, tempfile, json, importlib.util
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('build',root/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
javac,java=b.java_tools(None)
output=root/'build/camera-tests';output.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(dir=output) as work:
    work=Path(work);classes=work/'classes'
    b.compile_java(javac,[root/'src/main/java/net/muxigame/terminal/client/TerminalPhotoStore.java',root/'tests/camera/PhotoStoreTest.java'],classes,'',work/'args')
    result=subprocess.run([str(java),'-cp',str(classes),'net.muxigame.terminal.client.PhotoStoreTest',str(work/'data')],capture_output=True,text=True)
    if result.returncode:print(result.stderr);result.check_returncode()
    report=json.loads(result.stdout)
    (output/'photo-store-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(result.stdout.strip())

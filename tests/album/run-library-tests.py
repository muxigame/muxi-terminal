from pathlib import Path
import importlib.util,subprocess,tempfile,json,os
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('build',root/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
javac,java=b.java_tools(Path(os.environ['ALBUM_QA_JDK']) if os.environ.get('ALBUM_QA_JDK') else None);output=root/'build/album-tests';output.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(dir=output) as temp:
    temp=Path(temp);sources=[root/'src/main/java/net/muxigame/terminal/client'/n for n in ['TerminalPhotoStore.java','TerminalPhotoImages.java','TerminalAlbumConfirmation.java']]+[root/'tests/album/AlbumLibraryTest.java']
    camera=temp/'TerminalCamera.java';camera.write_text('package net.muxigame.terminal.client; public class TerminalCamera {static String data(byte[] png){return \"data:image/png;base64,\"+java.util.Base64.getEncoder().encodeToString(png);}}',encoding='utf-8');sources.append(camera)
    b.compile_java(javac,sources,temp/'classes','',temp/'args')
    run=subprocess.run([str(java),'-cp',str(temp/'classes'),'net.muxigame.terminal.client.AlbumLibraryTest',str(temp/'synthetic-library')],capture_output=True,text=True)
    print(run.stdout);print(run.stderr);run.check_returncode()
    (output/'library-result.json').write_text(json.dumps(json.loads(run.stdout),indent=2),encoding='utf-8')

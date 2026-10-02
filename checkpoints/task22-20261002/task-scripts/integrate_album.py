from pathlib import Path
base=Path('album-worktree/src/main')
def change(path,old,new):
    p=base/path;s=p.read_text(encoding='utf-8');assert s.count(old)==1,(path,old,s.count(old));p.write_text(s.replace(old,new,1),encoding='utf-8')
j='java/net/muxigame/terminal/client/';r='resources/assets/muxi_terminal/html/terminal/'
change(j+'TerminalNativeBridge.java','            if(TerminalCameraBridge.dispatch(browser,frame,request,callback))return;',
       '            if(TerminalAlbumBridge.dispatch(browser,frame,request,callback))return;\n            if(TerminalCameraBridge.dispatch(browser,frame,request,callback))return;')
change(j+'TerminalNativeBridge.java','id.equals("camera") || id.equals("settings")','id.equals("camera") || id.equals("album") || id.equals("settings")')
change(j+'TerminalBrowserSession.java','&& !"camera".equals(app) && !"settings".equals(app)','&& !"camera".equals(app) && !"album".equals(app) && !"settings".equals(app)')
change(j+'TerminalBrowserSession.java','"camera".equals(app)?"相机":','"album".equals(app)?"相册":"camera".equals(app)?"相机":')
change(r+'app.js',"'games','camera','settings','music'].includes(id)","'games','camera','album','settings','music'].includes(id)")
change(r+'app.js',"name:id==='music'?","name:id==='album'?'相册':id==='music'?")
change(r+'app.js',"'games','camera','settings','music'].includes(route)","'games','camera','album','settings','music'].includes(route)")
change(r+'index.html','  <link rel="stylesheet" href="./camera-app.css" />','  <link rel="stylesheet" href="./camera-app.css" />\n  <link rel="stylesheet" href="./album-app.css" />')
change(r+'index.html','  <script src="./camera-app.js"></script>','  <script src="./camera-app.js"></script>\n  <script src="./album-controller.js"></script>\n  <script src="./album-app.js"></script>')
change(r+'camera-app.js','<button class="secondary" id="cameraAlbum">本地相册</button>','<button class="secondary" id="cameraAlbum">本地相册</button><button class="secondary" id="cameraOpenAlbum">打开相册 APP</button>')
change(r+'camera-app.js',"  el('cameraResume').addEventListener", "  el('cameraOpenAlbum').addEventListener('click',()=>action(async()=>{++opening;++photoEpoch;gallery=true;await camera.stop();await native('terminal.app:album');}));\n  el('cameraResume').addEventListener")

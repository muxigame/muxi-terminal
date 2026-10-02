from pathlib import Path

base=Path('camera-worktree/src/main')
def change(path,old,new):
    p=base/path
    text=p.read_text(encoding='utf-8')
    assert text.count(old)==1,(path,old,text.count(old))
    p.write_text(text.replace(old,new,1),encoding='utf-8')

j='java/net/muxigame/terminal/client/'
r='resources/assets/muxi_terminal/html/terminal/'
change(j+'TerminalNativeBridge.java','            if(request.startsWith("terminal.launch:")','            if(TerminalCameraBridge.dispatch(browser,frame,request,callback))return;\n            if(request.startsWith("terminal.launch:")')
change(j+'TerminalNativeBridge.java','id.equals("guide") || id.equals("tasks")','id.equals("guide") || id.equals("tasks") || id.equals("camera")')
change(j+'TerminalBrowserSession.java','if(!"tasks".equals(app) && !"guide".equals(app))','if(!"tasks".equals(app) && !"guide".equals(app) && !"camera".equals(app))')
change(j+'TerminalBrowserSession.java','open(Kind.BUILTIN,HOME_URL+"#/"+app,','open(Kind.BUILTIN,HOME_URL+"#/"+app,"camera".equals(app)?"相机":')
change(r+'app.js',"['tasks','guide'].includes(id)","['tasks','guide','camera'].includes(id)")
change(r+'app.js',"name:id==='tasks'?","name:id==='camera'?'相机':id==='tasks'?")
change(r+'app.js',"['home','guide','guideDetail','tasks'].includes(route)","['home','guide','guideDetail','tasks','camera'].includes(route)")
change(r+'index.html','  <link rel="stylesheet" href="./app-transition.css" />','  <link rel="stylesheet" href="./app-transition.css" />\n  <link rel="stylesheet" href="./camera-app.css" />')
change(r+'index.html','  <script src="./app-container.js"></script>','  <script src="./app-container.js"></script>\n  <script src="./camera-controller.js"></script>\n  <script src="./camera-app.js"></script>')

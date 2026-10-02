# -*- coding: utf-8 -*-
"""Real Chromium rendering at actual terminal dimensions; no native playback claim."""
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
import argparse, base64, hashlib, json, os, shutil, subprocess, threading, time, urllib.request
import websocket

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--phase', choices=['before', 'after'], default='after')
parser.add_argument('--root', type=Path)
args = parser.parse_args()
ROOT = args.root or Path(__file__).resolve().parents[1]
lab = ROOT / 'build' / ('music-layout-' + args.phase)
lab.mkdir(parents=True, exist_ok=True)
base = ROOT / 'src/main/resources/assets/muxi_terminal/html/terminal'
assets = lab / 'assets'
shutil.copytree(base, assets, dirs_exist_ok=True)

class Quiet(SimpleHTTPRequestHandler):
    def log_message(self, *args): pass

server = ThreadingHTTPServer(('127.0.0.1', 0), partial(Quiet, directory=str(assets)))
threading.Thread(target=server.serve_forever, daemon=True).start()
profile = lab / ('profile-' + str(os.getpid()))
log = (lab / 'chromium.log').open('w', encoding='utf8')
chrome = Path(r'C:/Program Files/Google/Chrome/Application/chrome.exe')
proc = subprocess.Popen([str(chrome), '--headless=new', '--no-sandbox', '--disable-gpu', '--disable-extensions', '--disable-background-networking', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0', '--remote-allow-origins=http://localhost', '--user-data-dir=' + str(profile), 'about:blank'], stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
ws = None
counter = 0
checks, errors, measurements = [], [], []
fixture = r'''window.fixture={calls:[],state:{kind:'game',title:'真实曲目示例：长标题也保持播放器可见',target:'11111111-1111-1111-1111-111111111111',status:'playing',reason:'',message:'',busy:false,volumeSource:'music',volume:.5,master:.2,muted:false,capabilities:{pause:true,play:true,stop:true,next:true,previous:true,import:true,local:true,select:true},tracks:Array.from({length:60},(_,i)=>({id:'aaaaaaaa-aaaa-aaaa-aaaa-'+String(i).padStart(12,'0'),title:i===0?'长标题测试：真实游戏曲目名称应省略显示并可悬停查看完整名称':'游戏曲目 '+String(i+1).padStart(2,'0'),source:'游戏资源 · minecraft',active:i===0})),localTracks:Array.from({length:40},(_,i)=>({id:'bbbbbbbb-bbbb-bbbb-bbbb-'+String(i).padStart(12,'0'),title:'本机音乐 '+(i+1),format:'WAV',active:false}))}};
window.muxiTerminalQuery=o=>{const f=fixture;f.calls.push(o.request);let result={};
if(o.request==='apps.list')result=[];if(o.request==='tasks.snapshot')result={supported:false,loading:false,rows:[]};
if(o.request.startsWith('resource.data:'))result='';
if(o.request.startsWith('music.')){
 if(o.request.startsWith('music.select:')){const t=f.state.tracks.find(t=>t.id===o.request.slice(13));f.state.tracks.forEach(r=>r.active=r===t);Object.assign(f.state,{kind:'game',title:t.title,status:'playing'});}
 if(o.request.startsWith('music.local:')){const t=f.state.localTracks.find(t=>t.id===o.request.slice(12));f.state.localTracks.forEach(r=>r.active=r===t);Object.assign(f.state,{kind:'local',title:t.title,status:'playing'});}
 if(o.request.startsWith('music.control:')){const p=JSON.parse(o.request.slice(14));if(p.action==='stop')f.state.status='idle';if(p.action==='play'||p.action==='resume')f.state.status='playing';if(p.action==='pause')f.state.status='paused';if(p.action==='next'||p.action==='previous'){const tracks=f.state.kind==='local'?f.state.localTracks:f.state.tracks;const i=tracks.findIndex(t=>t.active);const n=(i+(p.action==='next'?1:-1)+tracks.length)%tracks.length;tracks.forEach((t,i)=>t.active=i===n);f.state.title=tracks[n].title;}}
 if(o.request==='music.import')f.state.message='已取消选择，本机曲库未改变。';
 if(o.request.startsWith('music.remove:'))f.state.localTracks=f.state.localTracks.filter(t=>t.id!==o.request.slice(13));
 result=structuredClone(f.state);
}setTimeout(()=>o.onSuccess(JSON.stringify(result)),20);};'''

try:
    until = time.monotonic() + 20
    while not (profile / 'DevToolsActivePort').exists() and time.monotonic() < until: time.sleep(.1)
    port = int((profile / 'DevToolsActivePort').read_text().splitlines()[0])
    target = next(t for t in json.load(urllib.request.urlopen('http://127.0.0.1:%s/json' % port)) if t['type'] == 'page')
    ws = websocket.create_connection(target['webSocketDebuggerUrl'], origin='http://localhost', timeout=10)
    def cdp(method, params=None):
        global counter
        counter += 1
        ident = counter
        ws.send(json.dumps({'id':ident,'method':method,'params':params or {}}))
        while True:
            msg=json.loads(ws.recv())
            if msg.get('method')=='Runtime.exceptionThrown': errors.append(msg['params'])
            if msg.get('id') == ident:
                if 'error' in msg: raise RuntimeError(msg)
                return msg.get('result', {})
    def js(code):
        r = cdp('Runtime.evaluate', {'expression':code, 'awaitPromise':True, 'returnByValue':True})
        if 'exceptionDetails' in r: raise RuntimeError(r)
        return r.get('result',{}).get('value')
    def check(code,label):
        value=js(code)
        if value is not True: raise AssertionError((label, value))
        checks.append(label)
    def settle(): js('new Promise(r=>setTimeout(r,70))')
    def shot(name): (lab / name).write_bytes(base64.b64decode(cdp('Page.captureScreenshot', {'format':'png'})['data']))
    def size(width,height):
        cdp('Emulation.setDeviceMetricsOverride', {'width':width,'height':height,'deviceScaleFactor':1,'mobile':False})
        settle()
    def bounds():
        return js("(()=>{const ids=['music','app-content','musicTitle','music-stop','music-next','musicImport','musicListScroll'];const r={viewport:[innerWidth,innerHeight]};for(const id of ids){const e=document.getElementById(id);if(!e)continue;const b=e.getBoundingClientRect();r[id]={x:b.x,y:b.y,width:b.width,height:b.height,scrollTop:e.scrollTop,scrollHeight:e.scrollHeight,clientHeight:e.clientHeight,scrollWidth:e.scrollWidth,clientWidth:e.clientWidth};}return r})()")
    cdp('Page.enable'); cdp('Runtime.enable')
    cdp('Page.addScriptToEvaluateOnNewDocument', {'source':fixture})
    size(576,276)
    cdp('Page.navigate', {'url':'http://127.0.0.1:%s/index.html?content=1#/music' % server.server_port})
    until=time.monotonic()+10
    while not js("!!document.querySelector('[data-track]')") and time.monotonic()<until: time.sleep(.05)
    js("document.body.classList.add('content-view')"); settle()
    if args.phase == 'before':
        measurements.append(bounds()); shot('before-terminal-576x276.png')
        js("document.querySelectorAll('[data-track]')[59].scrollIntoView({block:'nearest'})"); settle()
        measurements.append(bounds()); shot('before-long-list-bottom-576x276.png')
        size(360,360);js("document.getElementById('music').scrollTop=0");settle();shot('before-narrow-360x360.png')
    else:
        for width,height in [(580,276),(576,276),(640,360),(1024,576),(420,276),(360,360),(320,276)]:
            size(width,height)
            js("document.querySelector('[data-music-library=tracks]').click()");settle()
            check("['music','app-content','app'].every(id=>{const e=document.getElementById(id);return e.scrollHeight<=e.clientHeight+1&&e.scrollWidth<=e.clientWidth+1})&&document.documentElement.scrollHeight<=innerHeight&&document.body.scrollHeight<=innerHeight",'%sx%s: body, outer shell and music page cannot scroll' % (width,height))
            check("['musicTitle','music-play','musicPause','music-stop','music-next','music-previous','musicImport'].every(id=>{const b=document.getElementById(id).getBoundingClientRect();return b.top>=0&&b.bottom<=innerHeight&&b.left>=0&&b.right<=innerWidth})",'%sx%s: player, all playback controls and import stay visible' % (width,height))
            check("document.getElementById('musicListScroll').scrollHeight>document.getElementById('musicListScroll').clientHeight&&getComputedStyle(document.getElementById('musicListScroll')).overflowY==='auto'",'%sx%s: long playlist has its own scroll container' % (width,height))
            measurements.append(bounds());shot('after-%sx%s.png' % (width,height))
        size(576,276)
        js("window.playerY=document.getElementById('music-stop').getBoundingClientRect().y;document.getElementById('musicListScroll').scrollTop=900");settle()
        check("document.getElementById('musicListScroll').scrollTop>0&&document.getElementById('music').scrollTop===0&&document.getElementById('music-stop').getBoundingClientRect().y===playerY",'scrolling a long playlist leaves player and outer page fixed')
        # Real CDP wheel input to the list; no OS foreground focus is needed.
        b=js("(()=>{const b=document.getElementById('musicListScroll').getBoundingClientRect();return {x:b.x+b.width/2,y:b.y+b.height/2}})()")
        old=js("document.getElementById('musicListScroll').scrollTop")
        cdp('Input.dispatchMouseEvent',{'type':'mouseWheel','x':b['x'],'y':b['y'],'deltaX':0,'deltaY':180});settle()
        check("document.getElementById('musicListScroll').scrollTop>%s&&document.getElementById('music').scrollTop===0&&document.getElementById('music-stop').getBoundingClientRect().y===playerY" % old,'mouse wheel scrolls only the playlist')
        js("document.getElementById('musicListScroll').scrollTop=0;document.querySelectorAll('[data-track]')[0].focus()");settle()
        for _ in range(35):
            cdp('Input.dispatchKeyEvent',{'type':'keyDown','key':'ArrowDown','code':'ArrowDown','windowsVirtualKeyCode':40})
            cdp('Input.dispatchKeyEvent',{'type':'keyUp','key':'ArrowDown','code':'ArrowDown','windowsVirtualKeyCode':40})
        settle()
        check("!!document.activeElement.dataset.track&&document.getElementById('musicListScroll').scrollTop>0&&document.getElementById('music').scrollTop===0&&document.getElementById('music-stop').getBoundingClientRect().y===playerY",'arrow keys move through long list and scroll only the list')
        check("(()=>{const a=document.activeElement.getBoundingClientRect(),b=document.getElementById('musicListScroll').getBoundingClientRect();return a.top>=b.top&&a.bottom<=b.bottom})()",'keyboard-selected row is visible within the playlist')
        js("window.focusTrack=document.activeElement.dataset.track;window.MuxiMusicApp.refresh()");settle()
        check("document.activeElement.dataset.track===focusTrack",'unchanged polling preserves distant row focus')
        cdp('Input.dispatchKeyEvent',{'type':'keyDown','key':'Enter','code':'Enter','windowsVirtualKeyCode':13})
        cdp('Input.dispatchKeyEvent',{'type':'keyUp','key':'Enter','code':'Enter','windowsVirtualKeyCode':13});settle()
        check("fixture.calls.includes('music.select:'+focusTrack)&&document.activeElement.dataset.track===focusTrack&&document.getElementById('music').scrollTop===0",'Enter plays the focused track and changed snapshots restore row focus without outer scrolling')
        shot('after-long-list-keyboard-576x276.png')
        check("(()=>{const e=document.querySelector('#musicTrackList .task-title');return e.title===fixture.state.tracks[0].title&&getComputedStyle(e).textOverflow==='ellipsis'&&e.scrollWidth>e.clientWidth})()",'long titles use ellipsis and expose full title on hover')
        check("[...document.querySelectorAll('#musicTrackList article')].every(e=>e.getBoundingClientRect().height<=52)",'playlist uses compact rows rather than tall cards')
        js("document.querySelector('[data-music-library=local]').click()");settle()
        check("document.getElementById('musicTrackList').hidden&&!document.getElementById('musicLocalList').hidden&&document.getElementById('musicLocalList').querySelectorAll('[data-local]').length===40",'local library shares the independent playlist pane')
        js("document.getElementById('musicListScroll').scrollTop=280;document.querySelector('[data-music-library=tracks]').click()");settle()
        check("document.getElementById('musicListScroll').scrollTop>0&&document.getElementById('music').scrollTop===0",'switching libraries restores native-list scroll position without moving the page')
        js("document.querySelector('[data-music-library=local]').click()");settle()
        check("document.getElementById('musicListScroll').scrollTop===280",'each library keeps its own list scroll position')
        js("document.querySelectorAll('[data-local]')[30].focus();document.activeElement.scrollIntoView({block:'nearest'});document.activeElement.click()");settle()
        check("fixture.state.kind==='local'&&fixture.state.title==='本机音乐 31'&&document.getElementById('music-stop').getBoundingClientRect().y===playerY&&document.getElementById('music').scrollTop===0",'distant local row actively plays while player stays fixed')
        js("document.getElementById('music-next').click()");settle()
        check("fixture.state.title==='本机音乐 32'",'next control still switches local playback')
        js("document.getElementById('music-stop').click()");settle()
        check("fixture.state.status==='idle'",'stop remains functional')
        js("document.getElementById('musicImport').click()");settle()
        check("fixture.calls.includes('music.import')&&document.querySelectorAll('[data-local]').length===40&&document.getElementById('music').scrollTop===0",'local import action and cancelled-selection feedback preserve library and fixed page')
        shot('after-local-list-576x276.png')
        js("document.querySelector('[data-remove]').click()");settle()
        check("fixture.state.localTracks.length===39",'local remove remains available in compact rows')
        js("fixture.state.message='这是很长的导入失败说明'.repeat(20);fixture.state.reason='原播放器没有暂停接口'.repeat(20);fixture.state.title='长曲名'.repeat(40);window.MuxiMusicApp.refresh()");settle()
        check("document.getElementById('music').scrollHeight<=document.getElementById('music').clientHeight+1&&document.getElementById('music-stop').getBoundingClientRect().bottom<=innerHeight",'long player titles and feedback cannot expand the outer page')
        # Disabled native pause remains a truthful native capability.
        js("fixture.state.kind='netmusic';fixture.state.capabilities.pause=false;window.MuxiMusicApp.refresh()");settle()
        check("document.getElementById('musicPause').disabled",'portable sources still expose no fabricated pause API')
        cdp('Input.dispatchKeyEvent',{'type':'keyDown','key':'Tab','code':'Tab','windowsVirtualKeyCode':9})
        cdp('Input.dispatchKeyEvent',{'type':'keyUp','key':'Tab','code':'Tab','windowsVirtualKeyCode':9});settle()
        check("document.getElementById('music').scrollTop===0&&document.activeElement.closest('#music')!==null",'Tab navigation keeps focus in the active app without outer scrolling')
    if errors: raise AssertionError(errors)
    report={'success':True,'phase':args.phase,'count':len(checks),'checks':checks,'measurements':measurements,'runtimeExceptions':errors,'scope':'Actual Chrome DOM, rendering, wheel and keyboard inputs; native fixtures only; no Minecraft gameplay claim','sourceSha256':hashlib.sha256((base/'music-app.js').read_bytes()).hexdigest()}
    (lab/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'success':True,'phase':args.phase,'checks':len(checks),'lab':str(lab)},ensure_ascii=True))
finally:
    if ws:
        try: cdp('Browser.close')
        except Exception: pass
        ws.close()
    try: proc.wait(timeout=10)
    except subprocess.TimeoutExpired: proc.terminate();proc.wait(timeout=10)
    server.shutdown();server.server_close();log.close()

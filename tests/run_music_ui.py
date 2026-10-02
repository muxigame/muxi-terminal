"""Actual headless Chromium DOM/render tests using task6's existing assets and native fixtures."""
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
import base64, json, subprocess, threading, time, urllib.request, websocket, os, shutil

ROOT=Path(__file__).resolve().parents[1]
lab=ROOT/'build/music-ui';lab.mkdir(parents=True,exist_ok=True)
base=ROOT/'src/main/resources/assets/muxi_terminal/html/terminal'
assets=lab/'assets';shutil.copytree(base,assets,dirs_exist_ok=True)
module=ROOT/'src/main/resources/assets/muxi_terminal/html/terminal/music-app.js'
shutil.copyfile(module,assets/'music-app.js')
settings_controls=base/'settings.css'
# Existing proposed common controls, verbatim. Exclude wallpaper and icon ownership.
shared='\n'.join(line for line in settings_controls.read_text(encoding='utf-8').splitlines() if line.startswith(('.settings-panel','.setting-row','@media')))
(assets/'shared-controls.css').write_text(shared,encoding='utf-8')
app=(assets/'app.js').read_text(encoding='utf-8')
app=app.replace("['home','guide','guideDetail','tasks']", "['home','guide','guideDetail','tasks','music']")
(assets/'app.js').write_text(app,encoding='utf-8')
index=(assets/'index.html').read_text(encoding='utf-8')
# The integrated source already contains the owner-reviewed section, scripts and settings controls.
(assets/'index.html').write_text(index,encoding='utf-8')
class Quiet(SimpleHTTPRequestHandler):
    def log_message(self,*args):pass
server=ThreadingHTTPServer(('127.0.0.1',0),partial(Quiet,directory=str(assets)))
threading.Thread(target=server.serve_forever,daemon=True).start()
profile=lab/f'profile-{os.getpid()}';log=(lab/'chromium.log').open('w',encoding='utf-8')
chrome=Path(r'C:/Program Files/Google/Chrome/Application/chrome.exe')
proc=subprocess.Popen([str(chrome),'--headless=new','--no-sandbox','--disable-gpu','--disable-extensions','--disable-background-networking','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--remote-allow-origins=http://localhost',f'--user-data-dir={profile}','about:blank'],stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
ws=None;counter=0;checks=[];errors=[]
try:
    until=time.monotonic()+20
    while not (profile/'DevToolsActivePort').exists() and time.monotonic()<until:time.sleep(.1)
    port=int((profile/'DevToolsActivePort').read_text().splitlines()[0])
    target=next(t for t in json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json')) if t['type']=='page')
    ws=websocket.create_connection(target['webSocketDebuggerUrl'],origin='http://localhost',timeout=10)
    def cdp(method,params=None):
        global counter
        counter+=1;ident=counter;ws.send(json.dumps({'id':ident,'method':method,'params':params or {}}))
        while True:
            msg=json.loads(ws.recv())
            if msg.get('method')=='Runtime.exceptionThrown':errors.append(msg['params'])
            if msg.get('id')==ident:
                if 'error' in msg:raise RuntimeError(msg)
                return msg.get('result',{})
    def js(code):
        r=cdp('Runtime.evaluate',{'expression':code,'awaitPromise':True,'returnByValue':True})
        if 'exceptionDetails' in r:raise RuntimeError(r)
        return r.get('result',{}).get('value')
    def check(code,label):
        if js(code) is not True:raise AssertionError((label,js(code)))
        checks.append(label)
    def shot(name): (lab/name).write_bytes(base64.b64decode(cdp('Page.captureScreenshot',{'format':'png'})['data']))
    cdp('Page.enable');cdp('Runtime.enable')
    cdp('Emulation.setDeviceMetricsOverride',{'width':1280,'height':720,'deviceScaleFactor':1,'mobile':False})
    fixture=r'''window.fixture={calls:[],hold:false,pending:null,fail:false,state:{kind:'background',title:'minecraft:music.overworld.forest',target:'11111111-1111-1111-1111-111111111111',status:'playing',reason:'背景音乐没有可用的上一首 / 下一首播放列表。',message:'',busy:false,volumeSource:'music',volume:.4,master:.75,muted:false,capabilities:{pause:true,play:false,stop:false,next:false,previous:false,import:true,local:true},localTracks:[{id:'22222222-2222-2222-2222-222222222222',title:'我的本地曲目',format:'WAV',active:false}]}};
window.muxiTerminalQuery=o=>{const f=fixture;f.calls.push(o.request);let r={};
if(o.request.startsWith('resource.data:'))r='';if(o.request==='apps.list')r=[];if(o.request==='tasks.snapshot')r={supported:false,loading:false,rows:[]};
if(o.request.startsWith('music.')){
 if(f.fail){queueMicrotask(()=>o.onFailure(503,'音乐接口不可用'));return;}
 if(o.request==='music.snapshot'&&f.hold){f.pending=o;return;}
 if(o.request.startsWith('music.control:')){const p=JSON.parse(o.request.slice(14));if(p.target!==f.state.target)f.state.message='音乐源已变化，请重试。';else if(p.action==='pause')f.state.status='paused';else if(p.action==='resume')f.state.status='playing';else if(p.action==='volume'){f.state.volume=p.value;f.state.muted=p.value===0;}}
 if(o.request.startsWith('music.local:'))Object.assign(f.state,{kind:'local',title:'我的本地曲目',status:'playing',target:'33333333-3333-3333-3333-333333333333',capabilities:{pause:true,stop:true,play:false,next:false,previous:false,import:true,local:true}});
 if(o.request==='music.import')f.state.message='已取消，音乐库未改变。';
 r=structuredClone(f.state);
}queueMicrotask(()=>o.onSuccess(JSON.stringify(r)));};'''
    cdp('Page.addScriptToEvaluateOnNewDocument',{'source':fixture})
    cdp('Page.navigate',{'url':f'http://127.0.0.1:{server.server_port}/index.html#/music'})
    until=time.monotonic()+10
    while not js("!!document.getElementById('musicPause')&&!document.getElementById('musicPause').disabled") and time.monotonic()<until:time.sleep(.05)
    check("document.getElementById('music').classList.contains('page-active')",'direct built-in music route initializes')
    check("document.getElementById('musicTitle').getBoundingClientRect().top<innerHeight&&document.getElementById('music').parentElement.id==='app-content'",'music renders inside the existing visible content container')
    check("document.getElementById('music-next').disabled&&document.getElementById('music-previous').disabled",'background playlist controls disabled by actual capability')
    check("fixture.calls.every(c=>!c.startsWith('music.control:'))",'opening music reads state without playback mutation')
    shot('music-background-1280.png')
    js("document.getElementById('musicPause').click();new Promise(r=>setTimeout(r,20))")
    check("fixture.state.status==='paused'&&document.getElementById('musicPause').textContent==='继续'",'APP pause uses native source and reflects confirmed status')
    js("fixture.state.status='playing';window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicPause').textContent==='暂停'",'original player state change returns through fresh snapshot')
    js("fixture.calls=[];for(let i=0;i<100;i++)document.getElementById('musicPause').click();new Promise(r=>setTimeout(r,20))")
    check("fixture.calls.filter(c=>c.startsWith('music.control:')).length===1",'100 rapid clicks serialize to one operation')
    js("Object.assign(fixture.state,{kind:'netmusic',title:'原播放器曲目',volumeSource:'records',status:'playing',target:'44444444-4444-4444-4444-444444444444',reason:'该版本没有暂停恢复接口；停止后可从头播放。',capabilities:{pause:false,stop:true,next:true,previous:true,play:false,import:true,local:true}});window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicPause').disabled&&!document.getElementById('music-next').disabled&&document.getElementById('musicVolumeLabel').textContent.includes('唱片')",'source switch changes capabilities and native volume category')
    shot('music-netmusic-1280.png')
    js("fixture.state.volume=.27;window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicVolume').value==='27'",'native options volume updates APP')
    js("let v=document.getElementById('musicVolume');v.value='0';v.dispatchEvent(new Event('input',{bubbles:true}));v.dispatchEvent(new Event('change',{bubbles:true}));new Promise(r=>setTimeout(r,20))")
    check("fixture.state.volume===0&&document.getElementById('musicMute').textContent.includes('已静音')",'APP volume control and native mute stay consistent')
    js("document.querySelector('[data-local]').focus();window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("document.activeElement.dataset.local==='22222222-2222-2222-2222-222222222222'",'polling preserves keyboard focus on local list')
    js("document.getElementById('musicImport').click();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicMessage').textContent.includes('已取消')&&document.querySelectorAll('[data-local]').length===1",'cancelled file selection preserves local list and displays result')
    js("document.querySelector('[data-local]').click();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicTitle').textContent==='我的本地曲目'&&!document.getElementById('musicPause').disabled",'opaque local track selection displays native title')
    js("document.querySelector('#music .back').focus();window.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));")
    check("document.activeElement.closest('#music')!==null&&document.activeElement.classList.contains('keyboard-selected')",'dynamic music controls use existing arrow-key focus navigation')
    shot('music-local-1280.png')
    js("fixture.hold=true;window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    js("var oldSnapshot=structuredClone(fixture.state);oldSnapshot.title='过期的旧曲目';document.getElementById('musicPause').click();new Promise(r=>setTimeout(r,20))")
    js("fixture.pending.onSuccess(JSON.stringify(oldSnapshot));fixture.hold=false;new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicTitle').textContent==='我的本地曲目'&&document.getElementById('musicPause').textContent==='继续'",'late snapshot cannot overwrite newer playback operation')
    js("fixture.state.status='idle';fixture.state.kind='idle';fixture.state.title='当前没有音乐';fixture.state.capabilities.pause=false;window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicSource').textContent.includes('已停止')&&document.getElementById('musicPause').disabled",'track end clears playback status and pause capability')
    js("Object.assign(fixture.state,{kind:'unavailable',title:'音乐桥接未接入',reason:'音乐 API 不可用',capabilities:{pause:false,stop:false,play:false,next:false,previous:false,import:true,local:false},target:'55555555-5555-5555-5555-555555555555'});window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicTitle').textContent==='音乐桥接未接入'&&document.querySelector('[data-local]').disabled",'reload/API removal invalidates local playback capabilities')
    js("fixture.state.localTracks[0].title='<img src=x onerror=window.injected=1>';window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("!window.injected&&document.querySelector('#musicLocalList .task-title').textContent.includes('<img')",'untrusted music title is escaped text')
    js("fixture.fail=true;window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    check("document.getElementById('musicMessage').textContent.includes('不可用')&&document.getElementById('musicImport').disabled",'missing API produces visible unavailable state')
    shot('music-unavailable-1280.png')
    js("show('home');document.body.classList.remove('content-view')");shot('existing-home-1280.png')
    js("show('guide')");shot('existing-guide-1280.png')
    check("getComputedStyle(document.querySelector('#guide .toolbar')).gap===getComputedStyle(document.querySelector('#music .toolbar')).gap&&getComputedStyle(document.querySelector('#music .primary')).borderRadius==='0px'",'music inherits exact existing toolbar spacing and MC button shape')
    check("getComputedStyle(document.querySelector('#musicVolume'),'::-webkit-slider-thumb').appearance==='none'",'range reuses settings task MC component instead of browser chrome')
    js("fixture.fail=false;fixture.state.localTracks[0].title='我的本地曲目';show('music');document.body.classList.add('content-view');window.MuxiMusicApp.refresh();new Promise(r=>setTimeout(r,20))")
    cdp('Emulation.setDeviceMetricsOverride',{'width':640,'height':360,'deviceScaleFactor':1,'mobile':False});shot('music-small-640.png')
    check("document.getElementById('music').scrollWidth<=document.getElementById('music').clientWidth",'small viewport has no horizontal overflow')
    js("fixture.calls=[];show('home');new Promise(r=>setTimeout(r,900))")
    check("fixture.calls.every(c=>c!=='music.snapshot')",'hidden app does not poll or mutate playback')
    report={'success':True,'count':len(checks),'checks':checks,'runtime_exceptions':errors,'scope':'actual Chromium rendering, native API fixtures; no Minecraft/MCEF GUI',
        'styles_source':str(base/'styles.css'),'shared_controls_source':str(settings_controls),'music_global_css_changes':False}
    if errors:raise AssertionError(errors)
    (lab/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False,indent=2))
finally:
    if ws:
        try:cdp('Browser.close')
        except Exception:pass
        ws.close()
    try:proc.wait(timeout=10)
    except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=10)
    server.shutdown();server.server_close();log.close()

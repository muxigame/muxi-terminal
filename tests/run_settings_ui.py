"""Headless Chromium DOM/render QA with native fixtures; never launches Minecraft or a visible browser."""
from pathlib import Path
from functools import partial
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
import base64,json,subprocess,threading,time,urllib.request,websocket,os

ROOT=Path(__file__).resolve().parents[1]
lab=ROOT/'build/settings-ui';lab.mkdir(parents=True,exist_ok=True)
assets=ROOT/'src/main/resources/assets/muxi_terminal/html/terminal'
class Quiet(SimpleHTTPRequestHandler):
    def log_message(self,*args):pass
server=ThreadingHTTPServer(('127.0.0.1',0),partial(Quiet,directory=str(assets)))
threading.Thread(target=server.serve_forever,daemon=True).start()
baseline_assets=ROOT.parent/'base-repo/src/main/resources/assets/muxi_terminal/html/terminal'
baseline_server=None
if baseline_assets.exists():
    baseline_server=ThreadingHTTPServer(('127.0.0.1',0),partial(Quiet,directory=str(baseline_assets)))
    threading.Thread(target=baseline_server.serve_forever,daemon=True).start()
chrome=Path(r'C:\Program Files\Google\Chrome\Application\chrome.exe')
profile=lab/f'profile-{os.getpid()}'
log=(lab/'chromium.log').open('w',encoding='utf-8')
proc=subprocess.Popen([str(chrome),'--headless=new','--no-sandbox','--disable-gpu','--disable-breakpad','--disable-extensions','--disable-background-networking','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--remote-allow-origins=http://localhost',f'--user-data-dir={profile}','about:blank'],stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
ws=None;checks=[];counter=0;errors=[]
try:
    portfile=profile/'DevToolsActivePort'
    until=time.monotonic()+20;port=None
    while port is None and time.monotonic()<until:
        try:port=int(portfile.read_text().splitlines()[0])
        except (OSError,IndexError,ValueError):time.sleep(.1)
    if port is None:raise RuntimeError('Headless Chromium did not expose its temporary DevTools port')
    tabs=json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json'))
    target=next(x for x in tabs if x['type']=='page')
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
        value=js(code)
        if value is not True:raise AssertionError((label,value))
        checks.append(label)
    def shot(name):
        (lab/name).write_bytes(base64.b64decode(cdp('Page.captureScreenshot',{'format':'png'})['data']))
    cdp('Page.enable');cdp('Runtime.enable');cdp('Emulation.setDeviceMetricsOverride',{'width':1280,'height':720,'deviceScaleFactor':1,'mobile':False})
    mock=r'''window.fixture={values:{volume:.37,sensitivity:.48729,fov:83,brightness:.6137,distance:12,distanceMax:16,soundEnabled:true,soundVolume:.35},calls:[],cancel:true,wallpaper:'',hold:false,pending:null};
window.muxiTerminalQuery=o=>{let r={};if(o.request==='resource.data:')r='';if(o.request.startsWith('resource.data:'))r='';if(o.request==='apps.list')r=[];if(o.request==='tasks.snapshot')r={supported:false,loading:false,rows:[]};queueMicrotask(()=>o.onSuccess(JSON.stringify(r)));};
window.muxiSettingsQuery=o=>{const f=window.fixture;f.calls.push(o.request);let r={};
 if(o.request==='settings.snapshot'){if(f.hold){f.pending=o;return;}r={...f.values};}
 if(o.request==='ui.snapshot')r={soundEnabled:f.values.soundEnabled,soundVolume:f.values.soundVolume};
 if(o.request.startsWith('settings.apply:')){Object.assign(f.values,JSON.parse(o.request.slice(15)));r={...f.values};if(f.holdApply){f.pendingApply={o,r};return;}}
 if(o.request.startsWith('ui.save:')){Object.assign(f.values,JSON.parse(o.request.slice(8)));r={soundEnabled:f.values.soundEnabled,soundVolume:f.values.soundVolume};}
 if(o.request==='wallpaper.read')r={data:f.wallpaper};
 if(o.request==='wallpaper.choose')r={cancelled:f.cancel};
 if(o.request==='wallpaper.reset'){f.wallpaper='';window.terminalSettingsWallpaper('');}
 queueMicrotask(()=>o.onSuccess(JSON.stringify(r)));
};'''
    cdp('Page.addScriptToEvaluateOnNewDocument',{'source':mock})
    url=f'http://127.0.0.1:{server.server_port}/index.html'
    cdp('Page.navigate',{'url':url+'#/settings'})
    until=time.monotonic()+10
    while not js("!!document.getElementById('settingsFields')&&document.getElementById('setting-fov').disabled===false") and time.monotonic()<until:time.sleep(.05)
    check("document.getElementById('settings').classList.contains('page-active')",'direct settings route initializes')
    check("document.getElementById('settingsApply').disabled&&fixture.calls.every(c=>!c.startsWith('settings.apply:'))",'open page writes no game values')
    check("document.getElementById('setting-distance').max==='16'",'native render-distance maximum used')
    shot('settings-game-1280.png')
    js("var v=document.getElementById('setting-fov');v.value='90';v.dispatchEvent(new Event('input',{bubbles:true}));")
    check("fixture.calls.every(c=>!c.startsWith('settings.apply:'))",'slider draft does not mutate Options')
    js("document.getElementById('settingsApply').click();new Promise(r=>setTimeout(r,10))")
    check("fixture.calls.filter(c=>c.startsWith('settings.apply:')).at(-1)==='settings.apply:{\"fov\":90}'&&fixture.values.sensitivity===.48729&&fixture.values.brightness===.6137",'submit only edited FOV; exact untouched values survive')
    js("var v=document.getElementById('setting-distance');v.value='16';v.dispatchEvent(new Event('input',{bubbles:true}));document.getElementById('settingsDiscard').click();new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('setting-distance').value==='12'&&fixture.values.distance===12",'discard preserves native distance')
    js("fixture.values.fov=97;window.dispatchEvent(new Event('focus'));new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('setting-fov').value==='97'",'vanilla options change re-read on return')
    js("fixture.values.fov=96;window.terminalSettingsRefresh();new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('setting-fov').value==='96'",'native vanilla-return hook refreshes snapshot')
    js("var v=document.getElementById('setting-fov');v.value='94';v.dispatchEvent(new Event('input',{bubbles:true}));fixture.values.brightness=.7;window.terminalSettingsRefresh();new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('setting-fov').value==='94'&&document.getElementById('setting-brightness').value==='0.7'",'vanilla return updates native values while preserving edited draft')
    js("document.getElementById('settingsDiscard').click();new Promise(r=>setTimeout(r,10))")
    js("fixture.hold=true;window.dispatchEvent(new Event('focus'));new Promise(r=>setTimeout(r,10))")
    js("var v=document.getElementById('setting-fov');v.value='95';v.dispatchEvent(new Event('input',{bubbles:true}));fixture.pending.onSuccess(JSON.stringify(fixture.values));fixture.hold=false;")
    check("document.getElementById('setting-fov').value==='95'&&!document.getElementById('settingsApply').disabled",'late snapshot cannot erase in-progress draft')
    js("document.getElementById('settingsDiscard').click();new Promise(r=>setTimeout(r,10))")
    js("document.getElementById('settingsSoundVolume').value='.2';document.getElementById('settingsSoundVolume').dispatchEvent(new Event('input',{bubbles:true}));var v=document.getElementById('setting-fov');v.value='93';v.dispatchEvent(new Event('input',{bubbles:true}));document.getElementById('settingsApply').click();new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('settingsSoundVolume').value==='0.2'&&!document.getElementById('settingsUiSave').disabled",'game submit preserves pending UI preference draft')
    js("fixture.holdApply=true;var v=document.getElementById('setting-fov');v.value='92';v.dispatchEvent(new Event('input',{bubbles:true}));document.getElementById('settingsApply').click();document.getElementById('settingsSoundVolume').value='.25';document.getElementById('settingsSoundVolume').dispatchEvent(new Event('input',{bubbles:true}));")
    check("document.getElementById('settingsApply').disabled&&document.getElementById('settingsUiSave').disabled",'editing UI draft cannot re-enable pending submit')
    js("fixture.pendingApply.o.onSuccess(JSON.stringify(fixture.pendingApply.r));fixture.holdApply=false;new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('settingsSoundVolume').value==='0.25'",'pending game response preserves new UI draft')
    js("var v=document.getElementById('setting-distance');v.value='16';v.dispatchEvent(new Event('input',{bubbles:true}));document.getElementById('settingsDiscard').click();new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('settingsSoundVolume').value==='0.25'",'discard game draft leaves UI draft intact')
    js("document.getElementById('settingsUiSave').click();new Promise(r=>setTimeout(r,10))")
    check("fixture.values.soundVolume===.25&&document.getElementById('settingsUiSave').disabled",'UI preferences save separately')
    js("document.getElementById('wallpaperChoose').click();new Promise(r=>setTimeout(r,10))")
    check("document.getElementById('settingsStatus').textContent.includes('取消')&&!document.querySelector('.shell').style.getPropertyValue('--terminal-wallpaper')",'cancel file selection preserves default')
    js("window.terminalSettingsWallpaper('file:///private/image.png')")
    check("!document.querySelector('.shell').style.getPropertyValue('--terminal-wallpaper')",'web UI refuses file paths')
    js("window.terminalSettingsWallpaper('data:image/png;base64,iVBORw0KGgo=')")
    check("document.querySelector('.shell').style.getPropertyValue('--terminal-wallpaper').includes('data:image/png')",'normalized image fills shell via background')
    js("document.getElementById('wallpaperReset').click();new Promise(r=>setTimeout(r,10))")
    check("!document.querySelector('.shell').style.getPropertyValue('--terminal-wallpaper')",'restore default clears custom wallpaper')
    js("document.getElementById('settings').scrollTop=500")
    shot('settings-wallpaper-sound-1280.png')
    # Focus and mouse use the same document-level handler, including dynamically created controls.
    js("fixture.calls=[];document.getElementById('settingsVanilla').focus();new Promise(r=>setTimeout(r,110))")
    js("document.getElementById('settingsVanilla').dispatchEvent(new PointerEvent('pointerover',{bubbles:true}));new Promise(r=>setTimeout(r,110))")
    js("document.getElementById('settingsVanilla').click();new Promise(r=>setTimeout(r,10))")
    check("fixture.calls.includes('ui.sound:hover')&&fixture.calls.includes('ui.sound:select')",'focus, hover and activation request native audio')
    js("fixture.calls=[];for(let i=0;i<20;i++)document.getElementById('settingsVanilla').dispatchEvent(new PointerEvent('pointerover',{bubbles:true}));new Promise(r=>setTimeout(r,10))")
    check("fixture.calls.filter(c=>c.startsWith('ui.sound:')).length<=1",'renderer throttle bounds hover spam')
    js("var v=document.getElementById('settingsSoundEnabled');v.checked=false;v.dispatchEvent(new Event('change',{bubbles:true}));document.getElementById('settingsUiSave').click();new Promise(r=>setTimeout(r,110))")
    js("fixture.calls=[];document.getElementById('settingsVanilla').dispatchEvent(new PointerEvent('pointerover',{bubbles:true}));document.getElementById('settingsVanilla').click();new Promise(r=>setTimeout(r,10))")
    check("fixture.calls.every(c=>!c.startsWith('ui.sound:'))",'UI sound disable respected')
    js("window.terminalSettingsUi({soundEnabled:true});fixture.calls=[];document.getElementById('settingsVanilla').dispatchEvent(new PointerEvent('pointerover',{bubbles:true}));new Promise(r=>setTimeout(r,110))")
    check("fixture.calls.includes('ui.sound:hover')",'persistent shell accepts sound preference broadcast')
    js("fixture.calls=[];var btn=document.getElementById('settingsVanilla');btn.focus();btn.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));new Promise(r=>setTimeout(r,10))")
    check("fixture.calls.filter(c=>c==='settings.vanilla').length===1",'Enter activates the focused settings button once')
    # Capture actual existing and new pages with the same candidate styles and viewport.
    js("show('home');document.body.classList.remove('content-view')")
    shot('existing-home-1280.png')
    js("show('guide')");shot('existing-guide-1280.png')
    js("show('settings');document.body.classList.add('content-view');document.getElementById('settings').scrollTop=0")
    cdp('Emulation.setDeviceMetricsOverride',{'width':640,'height':360,'deviceScaleFactor':1,'mobile':False})
    shot('settings-small-640.png')
    check("document.getElementById('settings').scrollWidth<=document.getElementById('settings').clientWidth",'small viewport has no horizontal overflow')
    check("document.querySelector('#settings .primary').classList.contains('primary')&&getComputedStyle(document.querySelector('#settings .detail-card')).borderRadius==='0px'",'existing MC panel/button components retained')
    report={'success':True,'checks':checks,'count':len(checks),'runtime_exceptions':errors,'type':'headless Chromium with native API fixture; not Minecraft/MCEF GUI'}
    if errors:raise AssertionError(errors)
    if baseline_server:
        cdp('Emulation.setDeviceMetricsOverride',{'width':1280,'height':720,'deviceScaleFactor':1,'mobile':False})
        cdp('Page.navigate',{'url':f'http://127.0.0.1:{baseline_server.server_port}/index.html#/home'})
        until=time.monotonic()+10
        while not js(f"location.port==='{baseline_server.server_port}'&&document.readyState==='complete'&&!!document.querySelector('.app-grid .app-card')&&typeof show==='function'") and time.monotonic()<until:time.sleep(.05)
        shot('baseline-home-1280.png')
        js("show('guide')");shot('baseline-guide-1280.png')
        report['baseline_screenshots']='unmodified task6 fb1a7b4 clone, same viewport/browser fixture'
    (lab/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
finally:
    if ws:
        try:cdp('Browser.close')
        except Exception:pass
        ws.close()
    try:proc.wait(timeout=10)
    except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=10)
    server.shutdown();server.server_close();log.close()
    if baseline_server:baseline_server.shutdown();baseline_server.server_close()

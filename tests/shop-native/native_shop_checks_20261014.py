from pathlib import Path
import datetime,json,os,sys,time,traceback,hashlib,shutil
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(os.environ.get('MUXI_SHOP_NATIVE_EVIDENCE',str(ROOT/'muxi-terminal/build/shop-native-evidence')))
ACTIVE=json.loads((OUT/'ACTIVE.json').read_text(encoding='utf-8'))
LAB=Path(ACTIVE['instanceRoot']);os.environ['MUXI_NATIVE_LAB']=str(LAB)
sys.path.insert(0,str(ROOT/'muxi-terminal/tests/native-window'));import control as c
results=[];shots=[]

def save():
    (OUT/'native-shop-checks.json').write_text(json.dumps({'instanceRoot':str(LAB),'nativeMinecraftMcef':True,'physicalOSInput':False,'productInputLockSha256':ACTIVE['inputLockSha256'],'checks':results,'screenshots':shots,'passedChecks':sum(x['passed'] for x in results),'failedChecks':sum(not x['passed'] for x in results),'untested':['Successful purchase/redemption with real authenticated platform balance','Real SSO or VIP account','Endless R/G and dual-game isolation, reserved for original owner','Final normal JVM/JCEF shutdown, deferred until shared instance handoff completes']},ensure_ascii=False,indent=2),encoding='utf-8')

def check(name,passed,evidence):
    row={'name':name,'passed':bool(passed),'evidence':evidence,'atUTC':datetime.datetime.now(datetime.timezone.utc).isoformat()};results.append(row);save();print(json.dumps(row,ensure_ascii=False),flush=True)

def shot(name):
    # Screenshot.grab writes asynchronously: require a fully decoded PNG.
    from native_animation_followup_20261014 import shot as capture
    record=capture(name)
    shots.append({'name':name,**record});save()


MEASURE="""(()=>{const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,right:r.right,bottom:r.bottom}};const root=document.querySelector('#games'),mount=document.querySelector('#mg-content'),cards=[...mount.querySelectorAll('.mg-product-card')],first=cards[0],nav=document.querySelector('.mg-page-nav');return {width:innerWidth,height:innerHeight,pageShop:root.classList.contains('mg-shop'),category:document.querySelector('#mg-shop-game').value,nav:rect(nav),navButtons:[...nav.querySelectorAll('button,select')].filter(e=>e.getBoundingClientRect().width).map(e=>({text:e.innerText,rect:rect(e)})),content:rect(mount),scrollTop:mount.scrollTop,scrollHeight:mount.scrollHeight,clientHeight:mount.clientHeight,cards:cards.length,first:first?{title:first.querySelector('h4')?.textContent,rect:rect(first),image:first.querySelector('img')?rect(first.querySelector('img')):null,price:first.querySelector('.mg-product-price')?.textContent,priceElements:[...first.querySelectorAll('strong')].map(e=>({text:e.textContent,rect:rect(e)})),button:first.querySelector('button')?{text:first.querySelector('button').innerText,disabled:first.querySelector('button').disabled,rect:rect(first.querySelector('button'))}:null}:null,standaloneCurrencyVisible:!!document.querySelector('#mg-currencies').getBoundingClientRect().width,platform:document.querySelector('#mg-platform').innerText,exchange:document.querySelector('#mg-exchange-value').textContent,confirmHidden:document.querySelector('#mg-confirm').hidden,notice:document.querySelector('#mg-notice').textContent}})()"""

def first_visible(m,require_image=True):
    f=m.get('first');bounds=m['content']
    return bool(f and f.get('button') and f['rect']['x']>=bounds['x']-1 and f['rect']['right']<=bounds['right']+1 and f['rect']['y']>=bounds['y']-1 and (not require_image or (f.get('image') and f['image']['y']>=bounds['y']-1 and f['image']['bottom']<=bounds['bottom']+1)) and f['button']['rect']['bottom']<=bounds['bottom']+1)

def category(name):
    c.ev("(()=>{const e=document.querySelector('#mg-shop-game');e.value="+json.dumps(name)+";e.dispatchEvent(new Event('change',{bubbles:true}));return {requested:e.value}})()")
    time.sleep(.4)

def main():
    c.alive();before=c.w('observe');assert before['screen']=='TerminalScreen'
    c.ev("(()=>{document.querySelector('#mg-content').scrollTop=0;return {reset:true}})()")
    m=c.ev(MEASURE);check('default-first-product-visible',first_visible(m) and not m['standaloneCurrencyVisible'],m);shot('shop-native-default')
    c.ev("(()=>{window.__jointGifRefs=[...document.querySelectorAll('#mg-content img')].filter(x=>x.currentSrc.startsWith('data:image/gif'));return {count:window.__jointGifRefs.length}})()")
    gif_probe="""(()=>{const imgs=[...document.querySelectorAll('#mg-content img')].filter(x=>x.currentSrc.startsWith('data:image/gif'));return {count:imgs.length,items:imgs.map((x,i)=>{const canvas=document.createElement('canvas');canvas.width=x.naturalWidth;canvas.height=x.naturalHeight;const cx=canvas.getContext('2d',{willReadFrequently:true});cx.drawImage(x,0,0);let h=2166136261;for(const b of cx.getImageData(0,0,canvas.width,canvas.height).data)h=Math.imul(h^b,16777619);return {i,title:x.closest('.mg-product-card').querySelector('h4').textContent,complete:x.complete,w:x.naturalWidth,h:x.naturalHeight,pixelHash:(h>>>0).toString(16),sameNode:window.__jointGifRefs[i]===x,srcLength:x.currentSrc.length}})}})()"""
    samples=[]
    c.ev("(()=>{document.querySelector('#mg-content').scrollTop=350;return {scrollTop:document.querySelector('#mg-content').scrollTop}})()")
    for i in range(7):
        samples.append(c.ev(gif_probe))
        if i in [0,3,6]:shot('shop-native-gif-frame-'+str(i))
        if i<6:time.sleep(2)
    stable=all(s['count']==6 and all(x['complete'] and x['w']==156 and x['h']==210 and x['sameNode'] for x in s['items']) for s in samples)
    animated=all(len({s['items'][i]['pixelHash'] for s in samples})>1 for i in range(6))
    check('six-gifs-decoded-and-persistent-through-live-refresh',stable,{'samples':samples})
    check('six-gif-poster-probe-diagnostic-only',True,{'distinctPixelFrames':[len({s['items'][i]['pixelHash'] for s in samples}) for i in range(6)],'method':'Canvas poster diagnostic only; real animation acceptance requires native_animation_followup_20261014.py framebuffer pairs'})
    # Exercise the existing image-error fallback in the private live DOM only.
    fallback=c.ev("(()=>{const x=window.__jointGifRefs[0];x.dispatchEvent(new Event('error'));const card=x.closest('.mg-product-card');return {title:card.querySelector('h4').textContent,images:[...card.querySelectorAll('img')].map(i=>({png:i.currentSrc.startsWith('data:image/png'),display:getComputedStyle(i).display,complete:i.complete,w:i.naturalWidth,h:i.naturalHeight}))}})()")
    check('existing-png-fallback-native-render',any(x['png'] and x['display']!='none' and x['complete'] for x in fallback['images']),{'fixture':'QA-only synthetic image error event; real image and PNG resource unchanged','result':fallback});shot('shop-native-png-fallback')
    c.ev("(()=>{const x=window.__jointGifRefs[0],p=x.parentElement;x.style.display='';for(const img of p.querySelectorAll('img'))if(img!==x)img.style.display='none';return {restored:true}})()")
    c.ev("(()=>{document.querySelector('#mg-content').scrollTop=0;return {reset:true}})()")
    normal=c.w('observe')['layout']['outer'];c.chrome('windowMode');time.sleep(.7)
    full=c.ev(MEASURE);state=c.w('observe');check('fullscreen-first-product-and-navigation',state['fullscreen'] and first_visible(full),{'window':state,'dom':full});shot('shop-native-fullscreen')
    c.chrome('windowMode');time.sleep(.4);restored=c.w('observe');check('fullscreen-restores-normal-bounds',not restored['fullscreen'] and restored['layout']['outer']==normal,{'before':normal,'after':restored['layout']['outer']})
    for scale in [3,4,1,2]:
        c.w('scale',value=scale);time.sleep(.8);c.ev("(()=>{document.querySelector('#mg-content').scrollTop=0;return {reset:true}})()")
        measure=c.ev(MEASURE);check('gui-scale-'+str(scale)+'-first-product-visible',first_visible(measure),measure);shot('shop-native-scale-'+str(scale))
    category('zombie-challenge');zombie=c.ev(MEASURE);check('zombie-category-switch-and-first-product',zombie['category']=='zombie-challenge' and first_visible(zombie,require_image=False),zombie);shot('shop-native-zombie')
    category('horse_racing');saved=c.ev("(()=>{const e=document.querySelector('#mg-content');e.scrollTop=350;return {scrollTop:e.scrollTop}})()")['scrollTop']
    category('zombie-challenge');category('horse_racing');actual=c.ev(MEASURE)['scrollTop'];check('category-specific-scroll-restored',saved>0 and abs(actual-saved)<=1,{'requestedAndAchieved':saved,'restored':actual})
    c.domclick('#mg-refresh');time.sleep(2.5);refreshed=c.ev(MEASURE);check('manual-refresh-retains-shop-and-scroll',refreshed['category']=='horse_racing' and abs(refreshed['scrollTop']-saved)<=1,refreshed)
    category('parkour');empty=c.ev(MEASURE);check('category-without-products-keeps-navigation',empty['category']=='parkour' and empty['cards']==0 and empty['nav']['w']>0,empty);shot('shop-native-empty-category')
    # Keep a real live entry open for >=210 seconds from the recorded first successful entry.
    opened=datetime.datetime.fromisoformat(json.loads((OUT/'native-open.json').read_text(encoding='utf-8'))['atUTC'])
    while (datetime.datetime.now(datetime.timezone.utc)-opened).total_seconds()<210:
        c.w('observe');time.sleep(5)
    entry=c.w('observe');check('entry-remains-responsive-after-210-seconds',entry['mcef'] and not entry['reloading'] and entry['state']['rendered'] and not entry['state']['error'],{'elapsedSeconds':(datetime.datetime.now(datetime.timezone.utc)-opened).total_seconds(),'window':entry})
    category('horse_racing');c.ev("(()=>{document.querySelector('#mg-content').scrollTop=0;return {reset:true}})()")
    c.domclick('[data-mg-page="lobby"]');time.sleep(.5)
    lobby=c.ev("({text:document.body.innerText.slice(0,1000),isLobby:document.querySelector('#games').classList.contains('mg-lobby')})")
    check('restore-real-lobby-for-runner-owner',lobby['isLobby'],lobby);shot('shop-native-handoff-lobby')
    c.chrome('close');time.sleep(.4);final=c.w('observe');check('terminal-closes-back-to-same-live-client',final['screen']=='NONE',final)
    (OUT/'GUI-HANDOFF.json').write_text(json.dumps({'readyForOriginalEndlessOwner':True,'instanceRoot':str(LAB),'port':ACTIVE['port'],'runId':c.alive()['runId'],'clientName':'10000','clientUuid':'bac84aa3-61d5-3a42-acb0-02ab44e17ee2','nativeWindowController':str(ROOT/'muxi-terminal/tests/native-window/control.py'),'controllerEnvironment':{'MUXI_NATIVE_LAB':str(LAB)},'qaPlayerScope':'One actual offline client; no fabricated second player','productsFrozen':True,'normalStopPending':True,'finalScreen':final['screen'],'nativeShopFailedChecks':[x['name'] for x in results if not x['passed']],'noOtherOwnerInputAuthorizedByThisScript':True,'parentMustAssignNextInputOwner':True,'runnerTraceEnabled':True,'runtimeHoldSeconds':3600},ensure_ascii=False,indent=2),encoding='utf-8')
    print('SHOP_CHECKS_COMPLETE',flush=True)

if __name__=='__main__':
    try:main()
    except Exception as e:
        check('native-controller-completion',False,{'error':str(e),'traceback':traceback.format_exc()});raise

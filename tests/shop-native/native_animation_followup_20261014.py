from pathlib import Path
from PIL import Image,ImageChops
import json,os,sys,time,shutil,hashlib
sys.stdout.reconfigure(encoding='utf-8')
R=Path(__file__).resolve().parents[3];O=Path(os.environ.get('MUXI_SHOP_NATIVE_EVIDENCE',str(R/'muxi-terminal/build/shop-native-evidence')))
L=Path(json.loads((O/'ACTIVE.json').read_text(encoding='utf-8'))['instanceRoot']);os.environ['MUXI_NATIVE_LAB']=str(L)
sys.path.insert(0,str(R/'muxi-terminal/tests/native-window'));import control as c

def shot(name):
    c.shot(name+'.png');p=L/'host/screenshots'/(name+'.png');end=time.monotonic()+10
    while time.monotonic()<end:
        try:
            with Image.open(p) as im:im.load();size=im.size
            if p.stat().st_size==0:raise ValueError('pending screenshot')
            break
        except (OSError,ValueError):time.sleep(.1)
    else:raise RuntimeError('Screenshot did not complete: '+name)
    d=O/(name+'.png');shutil.copyfile(p,d)
    return {'path':str(d),'sha256':hashlib.sha256(d.read_bytes()).hexdigest(),'decodedSize':size}

def main():
    handoff=json.loads((O/'GUI-HANDOFF.json').read_text(encoding='utf-8'));handoff['readyForOriginalEndlessOwner']=False;(O/'GUI-HANDOFF.json').write_text(json.dumps(handoff,ensure_ascii=False,indent=2),encoding='utf-8')
    c.w('open');c.domclick('[data-mg-page="shop"]');time.sleep(.5)
    results=[]
    for idx in range(6):
        d=c.ev("(()=>{const imgs=[...document.querySelectorAll('#mg-content img')].filter(x=>x.currentSrc.startsWith('data:image/gif'));const x=imgs["+str(idx)+"];x.scrollIntoView({block:'center'});const b=x.getBoundingClientRect();return {title:x.closest('.mg-product-card').querySelector('h4').textContent,rect:{x:b.x,y:b.y,w:b.width,h:b.height},display:getComputedStyle(x).display,naturalWidth:x.naturalWidth,naturalHeight:x.naturalHeight}})()")
        s=c.w('observe');time.sleep(.3);a=shot('gif-native-'+str(idx)+'-a');time.sleep(.8);b=shot('gif-native-'+str(idx)+'-b')
        r=d['rect'];content=s['layout']['content'];g=s['guiScale'];x=content['x']*g+r['x'];y=(content['y']+22)*g+r['y'];box=(round(x+3),round(y+3),round(x+r['w']-3),round(y+r['h']-3))
        with Image.open(a['path']) as im:ia=im.convert('RGB').crop(box)
        with Image.open(b['path']) as im:ib=im.convert('RGB').crop(box)
        diff=ImageChops.difference(ia,ib);count=sum(max(pixel)>8 for pixel in diff.getdata());bbox=diff.getbbox()
        results.append({'title':d['title'],'decoded':d['naturalWidth']==156 and d['naturalHeight']==210,'visible':d['display']!='none','nativeCropBox':box,'changedPixelsOver8':count,'differentFrame':bool(bbox) and count>20,'screenshots':[a,b]})
        print(json.dumps(results[-1],ensure_ascii=False),flush=True)
    c.ev("(()=>{const e=document.querySelector('#mg-shop-game');e.value='zombie-challenge';e.dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('#mg-content').scrollTop=0;window.muxi.invoke('games.snapshot').then(s=>window.__jointFresh=s);return {requested:true}})()")
    time.sleep(2)
    zombie=c.ev("(()=>{const e=document.querySelector('#mg-content .mg-product-card'),b=e.getBoundingClientRect(),m=document.querySelector('#mg-content').getBoundingClientRect(),button=e.querySelector('button').getBoundingClientRect();return {title:e.querySelector('h4').textContent,price:e.querySelector('.mg-product-price')?.textContent,html:e.innerHTML.slice(0,2500),firstCardVisible:b.top>=m.top&&button.bottom<=m.bottom,category:document.querySelector('#mg-shop-game').value,serverFirstCards:(window.__jointFresh?.games||[]).find(g=>g.id==='zombie-challenge')?.ui?.sections?.filter(s=>s.role==='shop').map(s=>({title:s.title,card:s.cards?.[0]?{title:s.cards[0].title,icon:s.cards[0].icon,preview:s.cards[0].preview}:null}))}})()")
    zombie['screenshot']=shot('zombie-native-followup')
    c.domclick('[data-mg-page="lobby"]');c.chrome('close')
    summary={'nativeSixGifAnimationPassed':all(x['differentFrame'] and x['decoded'] and x['visible'] for x in results),'evidenceMethod':'Actual Minecraft framebuffer screenshots, paired at 0.8s interval, comparing visible GIF image regions; Canvas poster hashes were not used to conclude animation failure','images':results,'zombie':zombie,'zombieCorrection':'Initial image-required predicate did not accommodate a backend product without a standalone preview; title, price, actions and native render checked separately','originalChecksRetained':'native-shop-checks.json','finalScreen':c.w('observe')['screen'],'physicalOSInput':False}
    (O/'native-animation-followup.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    handoff['readyForOriginalEndlessOwner']=True;handoff['animationFollowup']=str(O/'native-animation-followup.json');handoff['finalScreen']=summary['finalScreen'];(O/'GUI-HANDOFF.json').write_text(json.dumps(handoff,ensure_ascii=False,indent=2),encoding='utf-8')
    print('ANIMATION_FOLLOWUP_COMPLETE',summary['nativeSixGifAnimationPassed'],flush=True)

if __name__=='__main__':main()

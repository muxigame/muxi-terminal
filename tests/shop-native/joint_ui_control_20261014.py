from pathlib import Path
import datetime,json,os,sys,time
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(os.environ.get('MUXI_SHOP_NATIVE_EVIDENCE',str(ROOT/'muxi-terminal/build/shop-native-evidence')))
ACTIVE=json.loads((OUT/'ACTIVE.json').read_text(encoding='utf-8'))
LAB=Path(ACTIVE['instanceRoot']);os.environ['MUXI_NATIVE_LAB']=str(LAB)
sys.path.insert(0,str(ROOT/'muxi-terminal/tests/native-window'))
import control as c

def save(name,data):
    (OUT/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(data,ensure_ascii=False,indent=2),flush=True)

def main():
    phase=sys.argv[1]
    if phase=='open':
        c.wait(lambda s:s['mcef'] and not s['reloading'],timeout=90)
        c.w('open');time.sleep(.3);c.w('app',name='games');time.sleep(3)
        c.ev("(()=>{window.muxi.invoke('games.snapshot').then(x=>window.__jointSnapshot=x);return {requested:true}})()")
        time.sleep(2)
        result=c.ev("(()=>{const s=window.__jointSnapshot||{};return {width:innerWidth,height:innerHeight,hash:location.hash,bridge:typeof window.muxi?.invoke,snapshotKeys:Object.keys(s),gameTitles:(s.games||[]).map(g=>({id:g.id,title:g.title})),text:document.body.innerText.slice(0,3000),buttons:[...document.querySelectorAll('button')].filter(x=>x.getBoundingClientRect().width).map(x=>({text:x.innerText,id:x.id,action:x.dataset.mgAction,open:x.dataset.open,disabled:x.disabled})),selects:[...document.querySelectorAll('select')].map(x=>({id:x.id,value:x.value,options:[...x.options].map(o=>({value:o.value,text:o.textContent}))}))}})()")
        save('native-open',{'nativeMC':True,'physicalOSInput':False,'atUTC':datetime.datetime.now(datetime.timezone.utc).isoformat(),'window':c.w('observe'),'dom':result})
        c.shot('joint-ui-open.png')
    elif phase=='eval':
        save('last-eval',c.ev(Path(sys.argv[2]).read_text(encoding='utf-8')))
    elif phase=='observe':
        save('native-observe',c.w('observe'))
    elif phase=='click':
        c.domclick(sys.argv[2]);time.sleep(.7);save('last-click',c.w('observe'))
    elif phase=='shop':
        c.domclick('[data-mg-page="shop"]');time.sleep(2)
        save('native-shop',c.ev("(()=>{const r=e=>{const b=e.getBoundingClientRect();return {x:b.x,y:b.y,w:b.width,h:b.height}};return {width:innerWidth,height:innerHeight,text:document.body.innerText.slice(0,5000),nav:r(document.querySelector('.mg-page-nav')),select:document.querySelector('#mg-shop-game').value,images:[...document.querySelectorAll('#mg-content img')].map(x=>({alt:x.alt,srcPrefix:x.currentSrc.slice(0,40),srcLength:x.currentSrc.length,complete:x.complete,naturalWidth:x.naturalWidth,naturalHeight:x.naturalHeight,rect:r(x)})),buttons:[...document.querySelectorAll('#mg-content button')].map(x=>({text:x.innerText,action:x.dataset.mgAction,disabled:x.disabled,rect:r(x)}))}})()"))
        c.shot('joint-ui-shop-normal.png')
    else:raise ValueError(phase)

if __name__=='__main__':main()

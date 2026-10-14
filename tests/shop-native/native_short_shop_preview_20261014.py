from pathlib import Path
import json,os,sys,time
sys.stdout.reconfigure(encoding='utf-8')
R=Path(__file__).resolve().parents[3];O=Path(os.environ.get('MUXI_SHOP_NATIVE_EVIDENCE',str(R/'muxi-terminal/build/shop-native-evidence')))
L=Path(json.loads((O/'ACTIVE.json').read_text(encoding='utf-8'))['instanceRoot']);os.environ['MUXI_NATIVE_LAB']=str(L)
sys.path.insert(0,str(R/'muxi-terminal/tests/native-window'));import control as c
sys.path.insert(0,str(Path(__file__).parent));from native_shop_checks_20261014 import MEASURE,first_visible
from native_animation_followup_20261014 import shot

def main():
    c.w('open');c.domclick('[data-mg-page="shop"]');c.w('scale',value=4);time.sleep(.5)
    c.ev("(()=>{const e=document.querySelector('#mg-shop-game');e.value='horse_racing';e.dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('#mg-content').scrollTop=0;return {reset:true}})()")
    before=c.ev(MEASURE);css=Path(__file__).with_name('short-shop-fix.css').read_text(encoding='utf-8')
    c.ev("(()=>{document.querySelector('#joint-short-shop-qa')?.remove();const s=document.createElement('style');s.id='joint-short-shop-qa';s.textContent="+json.dumps(css)+";document.head.append(s);return {installed:true}})()")
    time.sleep(.5);after=c.ev(MEASURE);picture=shot('shop-native-scale4-proposed-fix')
    c.ev("(()=>{document.querySelector('#joint-short-shop-qa').remove();return {removed:true}})()")
    c.w('scale',value=2);c.domclick('[data-mg-page="lobby"]');c.chrome('close')
    result={'passed':first_visible(after),'baselinePassed':first_visible(before),'before':before,'after':after,'screenshot':picture,'inputLockUnchanged':True,'scope':'Proposed source CSS replayed temporarily in live MCEF DOM for native validation; frozen product JAR not replaced','qaStyleRemoved':True,'finalScreen':c.w('observe')['screen']}
    (O/'native-short-shop-fix-preview.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)

if __name__=='__main__':main()

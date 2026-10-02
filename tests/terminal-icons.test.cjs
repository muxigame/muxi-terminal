const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const code=fs.readFileSync(__dirname+'/../src/main/resources/assets/muxi_terminal/html/terminal/terminal-icons.js','utf8');
function harness(){
  let revision=1,calls=[],nextId=0,poll;const cancelled=[],callbacks=new Map();
  const document={hidden:false,documentElement:{},querySelectorAll:()=>[]};
  const window={document,addEventListener(){},MutationObserver:class{observe(){}disconnect(){}},
    muxiTerminalCancel:id=>{cancelled.push(id);callbacks.delete(id);},
    muxiTerminalQuery:options=>{const id=++nextId;calls.push(options.request);callbacks.set(id,options);queueMicrotask(()=>{if(!callbacks.has(id))return;callbacks.delete(id);if(options.request==='icons.state')options.onSuccess(JSON.stringify({revision}));else options.onSuccess(JSON.stringify({revision,src:'data:image/png;base64,'+Buffer.from(options.request).toString('base64')}));});return id;}};
  const context={window,setTimeout,clearTimeout,setInterval:cb=>{poll=cb;return 1;},clearInterval(){},queueMicrotask,Date,Map,Promise};vm.runInNewContext(code,context);
  return {api:window.TerminalIcons,calls,cancelled,callbacks,document,tick:()=>poll(),setRevision:value=>revision=value,window};
}
(async()=>{
  const h=harness(),a=h.api;
  await a.request({kind:'item',id:'minecraft:enchanted_golden_apple'}); // first response negotiates revision
  const before=h.calls.length;
  const same=Array.from({length:10},()=>a.request({kind:'item',id:'minecraft:enchanted_golden_apple'}));
  const results=await Promise.all(same);assert.ok(results.every(Boolean));assert.equal(h.calls.length-before,1);
  assert.equal(h.calls.filter(x=>x.includes('textures/item/enchanted_golden_apple')).length,0);
  const semantic=a.html({kind:'semantic',id:'experience-levels',label:'经验等级'});assert.match(semantic,/ti-experience-levels/);assert.doesNotMatch(semantic,/<img/);
  const invalid=a.html({kind:'item',id:'https://remote/icon.png',label:'无效'});assert.match(invalid,/ti-fallback/);assert.doesNotMatch(invalid,/<img/);
  const count=h.calls.length;assert.equal(await a.request({kind:'semantic',id:'experience-levels'}),null);assert.equal(h.calls.length,count);
  assert.equal(await a.request({kind:'item',id:'https://remote/icon.png'}),null);
  for(let i=0;i<120;i++)await a.request({kind:'item',id:'minecraft:test_'+i});
  assert.ok(a.stats().entries<=96);assert.ok(a.stats().bytes<=4194304);
  h.setRevision(2);a.invalidate(2);assert.equal(a.stats().entries,0);
  assert.ok(await a.request({kind:'item',id:'minecraft:enchanted_golden_apple'}));
  a.release();assert.equal(a.stats().bytes,0);
  const images=Array.from({length:120},(_,i)=>{
    const img={dataset:{terminalIconKind:'item',terminalIconId:'minecraft:bulk_'+i},isConnected:true,hidden:true,parentElement:{querySelector:()=>null},removeAttribute(){this.source='';}};
    Object.defineProperty(img,'src',{get(){return this.source;},set(value){this.source=value;queueMicrotask(()=>this.onload?.());}});return img;
  });
  const root={querySelectorAll:()=>images,contains:img=>images.includes(img)};
  await a.hydrate(root);await new Promise(resolve=>setImmediate(resolve));
  assert.ok(images.every(img=>img.src&&!img.hidden),'bulk hydration must not drop icons when the native queue is bounded');
  assert.equal(a.stats().attached,120);assert.ok(a.stats().entries<=96);assert.equal(a.stats().queued,0);
  h.document.hidden=true;const beforePoll=h.calls.length;h.tick();await new Promise(resolve=>setImmediate(resolve));
  assert.equal(h.calls.length,beforePoll+1,'held/hidden MCEF views still need resource-revision checks');
  a.release(root);assert.equal(a.stats().attached,0);assert.ok(images.every(img=>!img.src));
  a.release();assert.equal(a.stats().bytes,0);
  // Transport cancellation on disposal settles pending callers and does not retain requests.
  h.window.muxiTerminalQuery=options=>{h.callbacks.set(999,options);return 999;};
  const hung=a.request({kind:'item',id:'minecraft:stone'});a.dispose();assert.equal(await hung,null);assert.ok(h.cancelled.includes(999));
  await new Promise(resolve=>setImmediate(resolve));assert.equal(a.stats().attached,0);assert.equal(a.stats().entries,0);assert.equal(a.stats().queries,0);
  assert.equal(a.stats().active,0);assert.equal(a.stats().pending,0);
  console.log(JSON.stringify({passed:true,cases:['item-ID request without texture guessing','same-request coalescing','semantic XP without pseudo item','no remote URLs','bounded LRU','120-image backpressure without dropped icons','reload invalidation including hidden MCEF views','release','pending-query cancellation on dispose']}));
})().catch(error=>{console.error(error);process.exitCode=1;});

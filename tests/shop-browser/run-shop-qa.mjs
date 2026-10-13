// Reuse the room regression's real Chromium harness and explicitly labelled native API fixtures.
// No Minecraft/MCEF, player input, live balance or production redemption is claimed here.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const repo=process.env.SHOP_QA_REPO||path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const work=process.env.SHOP_QA_OUTPUT;
if(!work)throw Error('Set SHOP_QA_OUTPUT to a new isolated evidence directory');
const before=process.argv.includes('--before');
const cover=JSON.parse(fs.readFileSync(process.env.SHOP_QA_COVER_RECEIPT,'utf8'));
const gif='data:image/gif;base64,'+fs.readFileSync(cover.output).toString('base64');
const firstPng='data:image/png;base64,'+fs.readFileSync(path.join(path.dirname(cover.output),'suzuka-v8-native-first.png')).toString('base64');
const existing=fs.readFileSync(path.join(repo,'tests/minigames-browser/run-browser-qa.mjs'),'utf8').replace(/\r\n/g,'\n');
const boundary=existing.indexOf('\ntry{\n  let port;');
if(boundary<0)throw Error('Cannot locate the room browser harness boundary');
let harness=existing.slice(0,boundary);
if(!harness.includes('const fixture=String.raw'))throw Error('Browser fixture harness changed; inspect before adapting');
harness=harness.replace(/const repo=path.resolve\([^;]+;/,`const repo=${JSON.stringify(repo)};`)
 .replace(/const output=path.join\([^;]+;/,`const output=${JSON.stringify(work)};`);
const setup=String.raw`
(()=>{
 const f=window.__fx;
 f.state.games.find(g=>g.id==='zombie-challenge').ui.currencies=[{id:'challenge_score',label:'战斗结算分',value:'27',scope:'本局',note:'按战斗规则结算；不等同平台积分'},{id:'challenge_coins',label:'兑换币',value:'9223372036854775806',scope:'枪战累计',note:'仅用于枪战兑换和携枪费用'}];
 for(const g of f.state.games)g.ui.shop={title:g.title+'商店',sections:[{title:g.title+'兑换',text:'测试数据：按游戏分类展示',cards:Array.from({length:12},(_,i)=>({title:g.title+'商品'+i,text:'商品说明 '+i,actions:[{label:'兑换',action:'buy',value:'item:'+i,enabled:true}]}))}]};
 f.state.games.find(g=>g.id==='tower_defense').ui.shop.sections=[];
 const horse=f.state.games.find(g=>g.id==='horse_racing');
 horse.ui.shop.sections[0].cards[0]={title:'无声铃鹿',text:'浏览器 fixture；GIF 来自已批准 v8 的真实原生帧。',maidAppearance:{modelId:'10_umamusume/umamusume_silence_suzuka_approved'},preview:__BEFORE__?{kind:'resource',id:'muxi_horse_racing:textures/gui/umamusume_preview/uma_silence_suzuka.png',label:'本地外观预览'}:{kind:'preview',id:__RESOURCE__,label:'无声铃鹿 · 已批准 v8 封面',width:156,height:210},previewFallback:{kind:'resource',id:'muxi_horse_racing:textures/gui/umamusume_preview/uma_silence_suzuka.png',label:'本地外观预览'},actions:[{label:'查看兑换确认',action:'tradeQuoteBuy',value:'uma_silence_suzuka',enabled:true}]};
 const original=window.muxiTerminalQuery;
 window.muxiTerminalQuery=o=>{
  if(o.request.startsWith('icons.get:')){
   f.calls.push(o.request);const d=JSON.parse(o.request.slice(10));
   let src=d.kind==='preview'?__GIF__:__PNG__;
   if(f.missingGif&&d.kind==='preview'){queueMicrotask(()=>o.onFailure(404,'Fixture missing resource'));return;}
   const finish=()=>o.onSuccess(JSON.stringify({revision:1,src,kind:d.kind,id:d.id}));if(f.iconDelay)setTimeout(finish,f.iconDelay);else queueMicrotask(finish);return;
  }
  return original(o);
 };
})();`.replaceAll('__BEFORE__',String(before)).replaceAll('__RESOURCE__',JSON.stringify(cover.resourceId)).replaceAll('__GIF__',JSON.stringify(gif)).replaceAll('__PNG__',JSON.stringify(firstPng));
const scenario=String.raw`
try{
 let port;for(let i=0;i<200;i++){if(fs.existsSync(path.join(profile,'DevToolsActivePort'))){port=Number(fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0]);break;}if(child.exitCode!==null)throw Error(stderr);await pause(100);}
 if(!port)throw Error('Chrome startup timeout: '+stderr);
 const targets=await(await fetch('http://127.0.0.1:'+port+'/json')).json();socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
 socket.addEventListener('message',event=>{const message=JSON.parse(event.data);if(message.id&&pending.has(message.id)){const cb=pending.get(message.id);pending.delete(message.id);clearTimeout(cb.timer);message.error?cb.reject(Error(JSON.stringify(message.error))):cb.resolve(message.result);}else if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails);});
 await cdp('Page.enable');await cdp('Runtime.enable');await cdp('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
 await cdp('Page.addScriptToEvaluateOnNewDocument',{source:fixture+'\n'+SETUP});await cdp('Page.navigate',{url:'http://127.0.0.1:'+server.address().port+'/index.html#/games'});
 await eventually('!!document.querySelector("[data-mg-create]")');
 const click=s=>js('{const e=document.querySelector('+JSON.stringify(s)+');e.focus();e.click();}');
 const refresh=async()=>{await click('#mg-refresh');await pause(80);};
 const choose=async id=>{await js('{const e=document.getElementById("mg-shop-game");e.value='+JSON.stringify(id)+';e.dispatchEvent(new Event("change",{bubbles:true}));}');await pause(50);};
 await shot('lobby-'+MODE);
 await click('[data-mg-page="shop"]');await pause(200);await shot('shop-'+MODE);
 if(BEFORE){
  await check('before: all game shops are rendered in one long page','document.querySelectorAll("#mg-content .section-title").length===5');
 }else{
  await check('exact exchange coin string survives, independently of platform points','document.getElementById("mg-exchange-value").textContent==="9223372036854775806"&&document.getElementById("mg-platform").textContent.includes("9223372036854775807")');
  await check('only selected shop is mounted, without unrelated currencies','document.getElementById("mg-shop-game").value==="flight"&&document.querySelectorAll("#mg-content .mg-shop-title").length===1&&!document.getElementById("mg-content").textContent.includes("僵尸挑战商品")&&!document.getElementById("mg-currencies").textContent.includes("枪战累计")');
  await choose('horse_racing');await eventually('!!document.querySelector(".mg-preview-primary img:not([hidden])")');
  await check('real native GIF bytes decode in Chromium','document.querySelector(".mg-preview-primary img").src.startsWith("data:image/gif;base64,")&&document.querySelector(".mg-preview-primary img").naturalWidth===156');
  await shot('horse-gif-after');
  await js('window.__coverNode=document.querySelector(".mg-preview-primary img");window.__coverSrc=__coverNode.src;window.__coverRequests=__fx.calls.filter(s=>s.startsWith("icons.get:")).length;window.__srcChanges=0;window.__watch=new MutationObserver(()=>__srcChanges++);__watch.observe(__coverNode,{attributes:true,attributeFilter:["src"]});document.getElementById("mg-content").scrollTop=300');
  await choose('outbreak');await check('new category starts at its own scroll origin','document.getElementById("mg-content").scrollTop===0');
  await choose('horse_racing');await check('category scroll restores on return','document.getElementById("mg-content").scrollTop===300');
  await js('window.__coverNode=document.querySelector(".mg-preview-primary img");window.__srcChanges=0;window.__watch.disconnect();__watch.observe(__coverNode,{attributes:true,attributeFilter:["src"]});document.getElementById("mg-content").scrollTop=0');
  await eventually('!document.querySelector(".mg-preview-primary img").hidden');
  const frameHashes=[];
  for(const [index,wait] of [[0,0],[1,700],[2,11000]]){
   await pause(wait);const clip=await js('(()=>{const r=document.querySelector(".mg-preview-primary img").getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,scale:1};})()');
   const {data}=await cdp('Page.captureScreenshot',{format:'png',clip});const buffer=Buffer.from(data,'base64');fs.writeFileSync(path.join(output,'gif-live-frame-'+index+'.png'),buffer);frameHashes.push(crypto.createHash('sha256').update(buffer).digest('hex'));
   await js('__fx.state.platform.points=String(BigInt(__fx.state.platform.points)-1n)');await refresh();
  }
  await check('polling and balance updates retain actual GIF node, src and visible state','document.querySelector(".mg-preview-primary img")===__coverNode&&__coverNode.src===__coverSrc&&!__coverNode.hidden&&__srcChanges===0');
  assert.ok(new Set(frameHashes).size>1,'Actual animated preview pixels must change across time');checks.push('visible GIF changes pixels and remains decoded beyond its 11-second loop');
  await js('document.getElementById("mg-content").scrollTop=180;__fx.state.games.find(g=>g.id==="horse_racing").ui.shop.sections[0].cards[0].text+=" 更新说明"');await refresh();
  await check('changed card text preserves the same GIF node and source','document.querySelector(".mg-preview-primary img")===__coverNode&&__srcChanges===0&&document.getElementById("mg-content").textContent.includes("更新说明")');
  await check('same-category content refresh preserves scroll','document.getElementById("mg-content").scrollTop===180');
  await choose('tower_defense');await check('empty selected shop is explicit and contains no foreign goods','document.getElementById("mg-content").textContent.includes("当前游戏暂无可兑换商品")&&!document.querySelector("#mg-content [data-mg-action]")');await shot('empty-category-after');
  for(const [width,height] of [[1280,720],[640,480],[480,320],[320,320]]){
   await cdp('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});
   await check('navigation and long balance fit '+width,'(()=>{const n=document.querySelector(".mg-page-nav"),b=document.getElementById("mg-exchange-balance"),a=document.querySelector("[data-mg-page=shop]"),v=document.getElementById("mg-exchange-value"),r=b.getBoundingClientRect(),s=a.getBoundingClientRect();return n.scrollWidth<=n.clientWidth&&Math.abs(r.y-s.y)<1&&Math.abs(r.height-s.height)<1&&r.x>s.x+s.width-1&&r.width>=40&&v.textContent==="9223372036854775806"&&b.getAttribute("aria-label").includes(v.textContent);})()');
   await check('category select fits '+width,'document.getElementById("mg-shop-switcher").scrollWidth<=document.getElementById("mg-shop-switcher").clientWidth');
   await check('short-window product region remains usable '+width,'document.getElementById("mg-content").clientHeight>=90');await shot('narrow-after-'+width);
  }
  await cdp('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
  await click('#mg-back');await check('lobby keeps coin balance visible','!document.getElementById("mg-exchange-balance").hidden&&document.querySelector("[data-mg-create]")');
  const hit=await js('(()=>{const r=document.querySelector(".mg-coin-icon").getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()');
  await cdp('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,...hit});await cdp('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,...hit});
  await check('icon hit area opens coin-owning game without a redemption write','document.getElementById("mg-shop-game").value==="zombie-challenge"&&document.querySelector("[data-mg-page=shop]").getAttribute("aria-pressed")==="true"&&__fx.actions.length===0');
  await js('document.getElementById("mg-exchange-balance").focus()');await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Enter',code:'Enter',windowsVirtualKeyCode:13});await cdp('Input.dispatchKeyEvent',{type:'keyUp',key:'Enter',code:'Enter',windowsVirtualKeyCode:13});
  await check('coin control exposes keyboard focus and an exact accessible balance','document.activeElement.id==="mg-exchange-balance"&&document.activeElement.getAttribute("aria-label").includes("9223372036854775806")');
  await js('delete __fx.state.platform.points;__fx.state.platform.available=false;__fx.state.games.find(g=>g.id==="zombie-challenge").ui.currencies=[]');await refresh();
  await check('missing coin data does not invent zero or use platform points','document.getElementById("mg-exchange-value").textContent==="—"&&document.getElementById("mg-exchange-balance").title.includes("尚未同步")');
  await choose('horse_racing');await js('__fx.missingGif=true;TerminalIcons.invalidate(1)');await eventually('!!document.querySelector(".mg-preview-backup img:not([hidden])")');
  await check('missing approved GIF retains honest PNG fallback','document.querySelector(".mg-preview-primary img").hidden&&!document.querySelector(".mg-preview-backup img").hidden&&getComputedStyle(document.querySelector(".mg-preview-primary-label")).display==="none"');await shot('missing-gif-fallback-after');
  await js('__fx.missingGif=false;TerminalIcons.invalidate(2)');await eventually('!!document.querySelector(".mg-preview-primary img:not([hidden])")');
  await check('resource reload recovers approved GIF','!document.querySelector(".mg-preview-primary img").hidden');
  await click('#mg-back');await check('unmounted previews release their image references without counting other apps','!document.querySelector(".mg-card-preview")&&TerminalIcons.stats().previewAttached===0');
  await click('[data-mg-page="shop"]');await choose('horse_racing');await js('__fx.iconDelay=500;TerminalIcons.invalidate(1)');await choose('flight');await pause(650);
  await check('late GIF callback cannot resurrect a departed category','!document.querySelector(".mg-card-preview")&&TerminalIcons.stats().previewAttached===0&&document.getElementById("mg-shop-game").value==="flight"');await js('__fx.iconDelay=0');
  await choose('horse_racing');await eventually('!!document.querySelector(".mg-preview-primary img:not([hidden])")');
  await js('__fx.state.activeGame="outbreak"');await refresh();await check('another active game still blocks horse purchases','[...document.querySelectorAll("#mg-content [data-mg-action]")].every(b=>b.disabled)');await js('__fx.state.activeGame=""');await refresh();
  const writes=await js('__fx.actions.length');await js('{const b=[...document.querySelectorAll("#mg-content [data-mg-action]")].find(b=>b.textContent==="查看兑换确认");b.click();b.click();}');await pause(60);
  await check('selected horse quote routes once to the actual game/SKU contract','__fx.actions.length==='+String(writes+1)+'&&__fx.last.game==="horse_racing"&&__fx.last.action==="tradeQuoteBuy"&&__fx.last.value==="uma_silence_suzuka"');
  await check('pending quote disables navigation and duplicate submissions','document.getElementById("mg-shop-game").disabled&&document.getElementById("mg-exchange-balance").disabled&&[...document.querySelectorAll("#mg-content [data-mg-action]")].every(b=>b.disabled)');
  await js('__fx.complete(false)');await refresh();await check('matching authoritative receipt unlocks without claiming native delivery','!document.getElementById("mg-shop-game").disabled&&__fx.actions.length==='+String(writes+1));
  await js('__fx.state.games=__fx.state.games.filter(g=>g.id!=="horse_racing")');await refresh();await check('removed category falls back to a current game and releases its previews','document.getElementById("mg-shop-game").value==="flight"&&!document.querySelector(".mg-card-preview")&&TerminalIcons.stats().previewAttached===0');
  await js('__fx.state.games=[]');await refresh();await check('empty game catalog is explicit and has no stale product actions','document.getElementById("mg-shop-game").disabled&&document.getElementById("mg-content").textContent.includes("暂无可用的游戏商店")&&!document.querySelector("#mg-content [data-mg-action]")');
 }
 assert.deepEqual(errors,[],'No uncaught browser exceptions');
 const report={success:true,mode:MODE,checkCount:checks.length,checks,errors,scope:'Actual headless Chromium layout and interaction; game/balance/redemption APIs are explicit fixtures. GIF pixels come from prior approved native capture, not a new MC run.',coverResource:COVER,output};fs.writeFileSync(path.join(output,'result.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}catch(error){await shot('failure').catch(()=>{});fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({error:String(error),checks,errors,stderr},null,2));throw error;}
finally{if(socket){await cdp('Browser.close').catch(()=>{});socket.close();}await new Promise(resolve=>server.close(resolve));}
`.replaceAll('SETUP',JSON.stringify(setup)).replaceAll('BEFORE',String(before)).replaceAll('MODE',JSON.stringify(before?'before':'after')).replaceAll('COVER',JSON.stringify(cover.resourceId));
fs.mkdirSync(work,{recursive:true});
const moduleFile=path.join(work,'harness.mjs');fs.writeFileSync(moduleFile,harness+scenario);
await import(pathToFileURL(moduleFile));

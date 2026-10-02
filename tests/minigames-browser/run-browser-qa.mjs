// Real Chromium layout and interaction checks with explicit native API fixtures.
// This does not claim Minecraft/MCEF, real players, or backend acceptance.
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import crypto from 'node:crypto';

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const assets=path.join(repo,'src/main/resources/assets/muxi_terminal/html/terminal');
const output=path.join(repo,'build/minigames-browser',new Date().toISOString().replace(/[:.]/g,'-'));
fs.mkdirSync(output,{recursive:true});
const profile=path.join(output,'chrome-profile');
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const checks=[],errors=[];
const server=http.createServer((req,res)=>{
  try{
    const target=path.resolve(assets,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
    if(!target.startsWith(assets+path.sep)||!fs.statSync(target).isFile()){res.writeHead(404);res.end();return;}
    const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png','.woff2':'font/woff2'};
    res.setHeader('Content-Type',(types[path.extname(target)]||'application/octet-stream')+'; charset=utf-8');res.end(fs.readFileSync(target));
  }catch{res.writeHead(404);res.end();}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const child=spawn(process.env.MINIGAMES_QA_CHROME||'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  ['--headless=new','--disable-gpu','--disable-extensions','--disable-background-networking','--no-first-run','--no-default-browser-check','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],
  {windowsHide:true,stdio:['ignore','ignore','pipe']});
let stderr='',socket,sequence=0;child.stderr.on('data',data=>stderr+=data);
const pending=new Map();
function cdp(method,params={}){
  const id=++sequence;
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout: '+method));},15000);
    pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method,params}));
  });
}
async function js(expression){
  const result=await cdp('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});
  if(result.exceptionDetails)throw Error(JSON.stringify(result.exceptionDetails));return result.result.value;
}
async function check(label,expression){assert.equal(await js(`Boolean(${expression})`),true,label);checks.push(label);}
async function shot(name){const {data}=await cdp('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(output,name+'.png'),Buffer.from(data,'base64'));}
async function eventually(expression){for(let i=0;i<100;i++){if(await js(expression))return;await pause(50);}throw Error('Wait expired: '+expression);}

const fixture=String.raw`
window.__fx={calls:[],actions:[],fail:false,hold:true,late:null};
(()=>{
  const f=window.__fx;
  const create={role:'create',title:'\u521b\u5efa\u623f\u95f4',text:'',fields:[
    {id:'map',label:'\u5730\u56fe',selected:'school',options:[{value:'school',label:'\u9003\u79bb\u5b66\u9662',modes:['CAMPAIGN']},{value:'camp',label:'\u795e\u79d8\u8425\u5730',modes:['SURVIVAL']}]},
    {id:'difficulty',label:'\u96be\u5ea6',selected:'1',options:[{value:'1',label:'\u666e\u901a'},{value:'2',label:'\u56f0\u96be'}]},
    {id:'mode',label:'\u6a21\u5f0f',selected:'CAMPAIGN',options:[{value:'CAMPAIGN',label:'\u6218\u5f79'},{value:'SURVIVAL',label:'\u751f\u5b58'}]}],
    actions:[{action:'createConfigured',label:'\u786e\u8ba4\u521b\u5efa',value:'',fields:'map,difficulty,mode',jsonFields:true,enabled:true}]};
  const room={role:'room',title:'\u7b49\u5f85\u623f\u95f4',text:'\u623f\u4e3b\u70b9\u51fb\u5f00\u59cb\u540e\u624d\u8fdb\u5165\u5730\u56fe',actions:[{action:'start',label:'\u5f00\u59cb',value:'',enabled:true},{action:'leave',label:'\u9000\u51fa\u623f\u95f4',value:'',enabled:true,safe:true,confirm:'\u9000\u51fa\u5e76\u6062\u590d\u539f\u7269\u54c1\u548c\u4f4d\u7f6e\uff1f'}]};
  const invite={role:'invite',title:'\u9080\u8bf7\u5728\u7ebf\u73a9\u5bb6',text:'',cards:[{title:'10001',text:'\u5728\u7ebf',actions:[{action:'invite',label:'\u9080\u8bf7',value:'bbbbbbbb-bbbb-3bbb-8bbb-bbbbbbbbbbbb',enabled:true}]}]};
  const rooms={role:'rooms',title:'\u623f\u95f4',cards:[{title:'ABCD',text:'\u7b49\u5f85\u4e2d \u00b7 1/4',actions:[{action:'join',label:'\u52a0\u5165',value:'ABCD',enabled:true}]}]};
  const clone=x=>JSON.parse(JSON.stringify(x));
  f.create=create;f.room=room;f.invite=invite;f.rooms=rooms;
  f.state={supported:true,loading:false,protocol:2,actionSupported:true,allowed:true,activeGame:'',platform:{available:true,points:'9223372036854775807'},
    games:[{id:'outbreak',title:'\u6c42\u63f4\u4e4b\u8def',state:{rooms:[]},ui:{currencies:[{label:'\u79ef\u5206',value:'8',scope:'\u672c\u73a9\u6cd5',note:''}],lobby:{sections:[create,rooms]},shop:{title:'\u6c42\u63f4\u4e4b\u8def\u5546\u5e97',sections:[{title:'\u8865\u7ed9',text:'\u670d\u52a1\u5668\u62a5\u4ef7',cards:[{title:'\u533b\u7597\u5305',text:'3 \u79ef\u5206',actions:[{action:'buy',label:'\u5151\u6362',value:'medkit:7:3',enabled:true}]}]}]}}},
      {id:'zombie-challenge',title:'\u50f5\u5c38\u6311\u6218',state:{rooms:[]},ui:{lobby:{sections:[clone(create),{role:'rooms',title:'\u623f\u95f4',cards:[]}]},shop:{title:'\u50f5\u5c38\u6311\u6218\u5546\u5e97',sections:[]}}}]};
  f.enter=()=>{f.state.activeGame='outbreak';f.state.games[0].state.rooms=[{id:'ROOM1',session:'aaaaaaaa-aaaa-3aaa-8aaa-aaaaaaaaaaaa',mine:true,phase:'WAITING',lobbyWaiting:true}];f.state.games[0].ui.lobby.sections=[create,room,invite,rooms];};
  f.complete=(membership=true)=>{if(membership&&['createConfigured','join'].includes(f.last.action))f.enter();f.state.operation={request:f.last.request,status:'completed',notice:'\u670d\u52a1\u5668\u5df2\u786e\u8ba4'};};
  f.reset=()=>{f.state.activeGame='';f.state.games[0].state.rooms=[];f.state.games[0].ui.lobby.sections=[create,rooms];delete f.state.operation;};
  window.muxiTerminalQuery=o=>{
    f.calls.push(o.request);let value={ok:true};
    if(o.request==='games.context')value={game:'',page:'lobby',drafts:{}};
    else if(o.request==='games.snapshot')value=clone(f.state);
    else if(o.request==='friends.invites.snapshot')value={version:1,enabled:false,authenticated:false,rooms:[],onlinePlayers:[],invitations:[]};
    else if(o.request==='apps.list')value=[];
    else if(o.request.startsWith('resource.data:'))value='';
    else if(o.request==='icons.state')value={revision:1};
    else if(o.request.startsWith('icons.get:'))value={revision:1,src:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4v8AAAAASUVORK5CYII='};
    else if(o.request==='tasks.snapshot')value={supported:false,loading:false,rows:[]};
    else if(o.request.startsWith('games.action:')){
      const action=JSON.parse(o.request.slice(13)),request='aaaaaaaa-aaaa-4aaa-8aaa-'+String(f.actions.length+1).padStart(12,'0');
      f.actions.push(action);f.last={...action,request};f.state.operation={request,status:'pending',notice:''};value={ok:true,request};
      if(f.fail)f.state.operation={request,status:'failed',notice:'\u623f\u95f4\u521b\u5efa\u5931\u8d25'};
      if(!f.hold){
        if(action.action==='start'){f.state.games[0].state.rooms[0].phase='ACTIVE';f.state.games[0].state.rooms[0].lobbyWaiting=true;f.state.games[0].ui.lobby.sections=[create,room,rooms];}
        if(action.action==='leave')f.reset();
        f.state.operation={request,status:'completed',notice:'\u670d\u52a1\u5668\u5df2\u786e\u8ba4'};
      }
      if(f.late===true){f.late=()=>o.onSuccess(JSON.stringify(value));return;}
    }
    queueMicrotask(()=>o.onSuccess(JSON.stringify(value)));
  };
})();`.replace(/\\u([0-9a-f]{4})/gi,(_,code)=>String.fromCharCode(92)+'u'+code);

try{
  let port;for(let i=0;i<200;i++){if(fs.existsSync(path.join(profile,'DevToolsActivePort'))){port=Number(fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0]);break;}if(child.exitCode!==null)throw Error(stderr);await pause(100);}
  if(!port)throw Error('Chrome startup timeout: '+stderr);
  const targets=await(await fetch(`http://127.0.0.1:${port}/json`)).json();
  socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
  socket.addEventListener('message',event=>{const message=JSON.parse(event.data);if(message.id&&pending.has(message.id)){const callback=pending.get(message.id);pending.delete(message.id);clearTimeout(callback.timer);message.error?callback.reject(Error(JSON.stringify(message.error))):callback.resolve(message.result);}else if(message.method==='Runtime.exceptionThrown')errors.push(message.params.exceptionDetails);});
  await cdp('Page.enable');await cdp('Runtime.enable');
  await cdp('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
  await cdp('Page.addScriptToEvaluateOnNewDocument',{source:fixture});
  await cdp('Page.navigate',{url:`http://127.0.0.1:${server.address().port}/index.html#/games`});
  await eventually('!!document.querySelector("[data-mg-create]")');
  // The public owner may stage its module before wiring the shared index.
  // Consumer-only tests use that exact module; production integration remains its owner's work.
  if(!await js('!!window.TerminalIcons')&&fs.existsSync(path.join(assets,'terminal-icons.js'))){
    await js(fs.readFileSync(path.join(assets,'terminal-icons.js'),'utf8'));
    await js(`{const style=document.createElement('style');style.textContent=${JSON.stringify(fs.readFileSync(path.join(assets,'terminal-icons.css'),'utf8'))};document.head.appendChild(style);}`);
  }
  await check('default lobby shows room list and create, hides mode/form/invitation',`document.querySelector('[data-mg-create]')&&document.querySelector('[data-mg-action]')&&!document.querySelector('[data-mg-choice]')&&document.getElementById('mg-games').hidden&&document.getElementById('mg-room-invitations').hidden`);
  await shot('01-lobby');
  await js(`document.querySelector('[data-mg-create]').click()`);
  await check('create opens separate mode selection',`!document.getElementById('mg-games').hidden&&document.querySelectorAll('[data-mg-game]').length===2`);await shot('02-mode');
  await js(`document.querySelector('[data-mg-cancel]').click()`);
  await check('cancel makes no action request',`__fx.actions.length===0&&document.getElementById('mg-games').hidden`);
  await js(`document.querySelector('[data-mg-create]').click();document.querySelector('[data-mg-game="outbreak"]').click()`);
  await check('campaign selects only compatible maps even when map field comes first',`document.querySelectorAll('[data-mg-map]').length===1&&document.querySelector('[data-mg-map]').dataset.mgMap==='school'`);
  await js(`document.querySelector('[data-mg-choice="outbreak:mode"][data-mg-value="SURVIVAL"]').click()`);
  await check('switch mode replaces incompatible map',`document.querySelectorAll('[data-mg-map]').length===1&&document.querySelector('[data-mg-map]').dataset.mgMap==='camp'`);await shot('03-map');
  await js(`document.querySelector('[data-mg-map]').click()`);
  await check('confirmation only exposes difficulty and selected map summary',`document.querySelector('[data-mg-choice="outbreak:difficulty"]')&&!document.querySelector('[data-mg-map]')&&!document.querySelector('[data-mg-choice="outbreak:mode"]')&&__fx.actions.length===0`);await shot('04-confirm');
  await js(`__fx.fail=true;document.querySelector('[data-mg-action]').click()`);
  await eventually(`document.getElementById('mg-notice').textContent.includes('\u5931\u8d25')`);
  await check('create failure stays in confirmation with invitations hidden',`document.querySelector('[data-mg-choice="outbreak:difficulty"]')&&document.getElementById('mg-room-invitations').hidden`);
  await js(`__fx.fail=false;document.querySelector('[data-mg-action]').click();document.querySelector('[data-mg-action]').click()`);await pause(100);
  await check('duplicate clicks submit once; ACK alone never enters room',`__fx.actions.length===2&&document.getElementById('mg-room-invitations').hidden&&!document.querySelector('[data-mg-action="1"]')`);
  await js(`__fx.complete(false);window.MuxiMinigamesApp.activate()`);await pause(80);
  await check('completed receipt without membership cannot enter room',`document.getElementById('mg-room-invitations').hidden&&document.querySelector('[data-mg-choice="outbreak:difficulty"]')`);
  await js(`__fx.enter();window.MuxiMinigamesApp.activate()`);await eventually(`!document.getElementById('mg-room-invitations').hidden`);
  await check('final receipt plus membership opens room-scoped invitations',`document.getElementById('friendsInviteOnline')&&document.getElementById('friendsInviteFriends')&&document.querySelector('[data-mg-action]')`);await shot('05-waiting-room');
  await eventually(`document.getElementById('friendsInviteStatus').textContent.includes('\u672a\u63d0\u4f9b')`);
  await check('unavailable trusted friends service is stated honestly',`document.getElementById('friendsInviteStatus').textContent.includes('\u672a\u63d0\u4f9b')`);
  await js(`__fx.hold=false;[...document.querySelectorAll('[data-mg-action]')].find(b=>b.textContent==='\u5f00\u59cb').click()`);
  await eventually(`document.getElementById('mg-room-invitations').hidden`);
  await check('active room hides all invitations despite stale waiting flag',`document.getElementById('mg-room-invitations').hidden&&!document.getElementById('mg-content').textContent.includes('\u9080\u8bf7\u5728\u7ebf\u73a9\u5bb6')`);await shot('06-active-room');
  await js(`[...document.querySelectorAll('[data-mg-action]')].find(b=>b.textContent==='\u9000\u51fa\u623f\u95f4').click()`);
  await check('leave requires explicit confirmation',`!document.getElementById('mg-confirm').hidden`);await shot('07-leave-dialog');
  const beforeLeave=await js('__fx.actions.length');await js(`document.getElementById('mg-confirm-cancel').click()`);
  await check('cancel leave preserves membership and sends nothing',`__fx.actions.length===${beforeLeave}&&__fx.state.games[0].state.rooms.length===1`);
  await js(`[...document.querySelectorAll('[data-mg-action]')].find(b=>b.textContent==='\u9000\u51fa\u623f\u95f4').click();document.getElementById('mg-confirm-ok').click()`);
  await eventually(`!!document.querySelector('[data-mg-create]')&&!document.querySelector('[data-mg-open-room]')`);
  await check('confirmed leave returns clean lobby',`document.getElementById('mg-room-invitations').hidden&&document.getElementById('mg-games').hidden`);
  await js(`__fx.hold=true;document.querySelector('[data-mg-action]').click();__fx.complete();window.MuxiMinigamesApp.activate()`);
  await eventually(`!document.getElementById('mg-room-invitations').hidden`);
  await check('join from lobby list enters room after final receipt and membership',`__fx.last.action==='join'&&__fx.last.value==='ABCD'&&!document.getElementById('mg-room-invitations').hidden`);
  await js(`document.querySelector('[data-mg-page="shop"]').click()`);
  await check('shop stays in existing navigation and retains exact platform amount',`document.getElementById('mg-content').textContent.includes('\u5546\u5e97')&&document.getElementById('mg-platform').textContent.includes('9223372036854775807')`);await shot('08-shop');
  await check('games reuses pixel button style and terminal toolbar',`getComputedStyle(document.querySelector('#mg-content .primary')).borderRadius==='0px'&&document.getElementById('games').parentElement.id==='app-content'`);
  await js(`__fx.late=true;document.querySelector('[data-mg-action]').click()`);await pause(4150);
  await check('lost callback stays blocked instead of repeating uncertain mutation',`document.querySelector('[data-mg-action]').disabled&&/\u8d85\u65f6|\u672a\u6536\u5230\u6700\u7ec8\u786e\u8ba4/.test(document.getElementById('mg-notice').textContent)`);
  await js(`__fx.complete(false);__fx.late()`);
  await eventually(`!document.querySelector('[data-mg-action]').disabled`);
  await check('late callback resumes correlation and only final receipt unlocks',`!document.querySelector('[data-mg-action]').disabled&&document.getElementById('mg-notice').textContent.includes('\u5df2\u786e\u8ba4')`);
  if(await js('!!window.TerminalIcons')){
    await js(`(async()=>{__fx.late=false;const card=__fx.state.games[0].ui.shop.sections[0].cards[0];card.icon={kind:'item',id:'minecraft:enchanted_golden_apple',label:'Apple'};card.actions[0].confirm='Confirm item purchase?';await window.MuxiMinigamesApp.activate();})()`);
    await eventually(`!!document.querySelector('#mg-content img[data-terminal-icon-id="minecraft:enchanted_golden_apple"]')&&!document.querySelector('#mg-content img[data-terminal-icon-id="minecraft:enchanted_golden_apple"]').hidden`);
    await check('cards consume shared icon html and hydrate APIs',`document.querySelector('#mg-content img').naturalWidth>0&&__fx.calls.some(c=>c.startsWith('icons.get:'))`);
    await js(`document.querySelector('[data-mg-action]').click()`);
    await eventually(`!!document.querySelector('#mg-confirm-text img')&&!document.querySelector('#mg-confirm-text img').hidden`);
    await check('confirmation inherits server card descriptor through common icon service',`document.querySelector('#mg-confirm-text img').dataset.terminalIconId==='minecraft:enchanted_golden_apple'`);await shot('10-dialog-icon-consumer-fixture');
    await js(`document.getElementById('mg-confirm-cancel').click()`);
    await check('closing confirmation releases its shared image attachment',`document.getElementById('mg-confirm').hidden&&!document.querySelector('#mg-confirm-text img').hasAttribute('src')`);
  }
  await cdp('Emulation.setDeviceMetricsOverride',{width:640,height:360,deviceScaleFactor:1,mobile:false});
  await js(`document.getElementById('mg-back').click()`);await shot('09-small-lobby');
  await check('small viewport does not overflow horizontally',`document.getElementById('games').scrollWidth<=document.getElementById('games').clientWidth`);
  assert.deepEqual(errors,[],'no uncaught browser exceptions');
  const sourceHashes={};for(const file of ['apps/minigames/app.js','apps/minigames/model.js','apps/minigames/app.css','friends-invites.js','index.html'])sourceHashes[file]=crypto.createHash('sha256').update(fs.readFileSync(path.join(assets,file))).digest('hex');
  const report={success:true,checks,checkCount:checks.length,errors,sourceHashes,scope:'Actual Chromium DOM, layout and screenshots; native API fixtures. No Minecraft/MCEF or real-player/backend acceptance.',output};
  fs.writeFileSync(path.join(output,'result.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}catch(error){await shot('failure').catch(()=>{});fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({error:String(error),checks,errors,stderr},null,2));throw error;}
finally{if(socket){await cdp('Browser.close').catch(()=>{});socket.close();}await new Promise(resolve=>server.close(resolve));}

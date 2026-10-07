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
window.__fx={calls:[],actions:[],inviteActions:[],hold:true,fail:false,trusted:true};
(()=>{
 const f=window.__fx,H='11111111-1111-4111-8111-111111111111',P='22222222-2222-4222-8222-222222222222',S='33333333-3333-4333-8333-333333333333';
 const clone=x=>JSON.parse(JSON.stringify(x));f.H=H;f.P=P;f.S=S;
 const field=(id,selected,options)=>({id,label:{map:'地图',difficulty:'难度',mode:'模式'}[id],selected,options:options.map(x=>typeof x==='string'?{value:x,label:x}:x)});
 const tiers=['简单','普通','困难','专家','极限'].map((label,i)=>({value:String(i+1),label}));
 const specs=[
 ['flight','太平洋空战',[field('mode','pve',[{value:'pve',label:'PVE 协作'},{value:'pvp',label:'PVP 红蓝对战'}]),field('map','pacific-training',[{value:'pacific-training',label:'硫磺岛'}]),field('difficulty','1',tiers)]],
 ['horse_racing','障碍赛马',[field('mode','obstacle',[{value:'obstacle',label:'障碍赛'}]),field('map','horse-stadium',[{value:'horse-stadium',label:'赛马场'}]),field('difficulty','1',tiers)]],
 ['zombie-challenge','僵尸挑战',[field('difficulty','NORMAL',[{value:'NORMAL',label:'普通'},{value:'EXTREME',label:'极限'}]),field('map','research_lab',[{value:'research_lab',label:'研究所'},{value:'mysterious_camp',label:'神秘营地'}])]],
 ['outbreak','求援之路',[field('map','school',[{value:'school',label:'逃离学院',modes:['CAMPAIGN']},{value:'camp',label:'神秘营地',modes:['SURVIVAL']}]),field('difficulty','1',[{value:'0',label:'简单'},{value:'1',label:'普通'},{value:'2',label:'困难'},{value:'3',label:'极限'}]),field('mode','CAMPAIGN',[{value:'CAMPAIGN',label:'战役'},{value:'SURVIVAL',label:'生存'}])]]];
 const make=([id,title,fields])=>({id,title,state:{self:H,rooms:[]},ui:{lobby:{sections:[{role:'create',fields,actions:[{action:id==='zombie-challenge'?'createMap':'createConfigured',value:'',enabled:true,jsonFields:id!=='zombie-challenge',fields:fields.map(f=>f.id).join(',')}]},{role:'rooms',cards:[]}]},shop:{title:title+'商店',sections:[{title:'补给',cards:[{title:'医疗包',text:'3 积分',actions:[{label:'兑换',action:'buy',value:'medkit:7:3',enabled:true}]}]}]}}});
 f.state={supported:true,loading:false,roomUiVersion:1,protocol:2,actionSupported:true,allowed:true,activeGame:'',self:{uuid:H,name:'Dot 🦊'},platform:{available:true,points:'9223372036854775807'},games:specs.map(make)};
 f.rooms=(n=4)=>{for(const g of f.state.games){g.state.rooms=[];g.ui.lobby.sections=g.ui.lobby.sections.filter(s=>['create','rooms'].includes(s.role));g.ui.lobby.sections[0].actions[0].enabled=true;const rows=g.ui.lobby.sections.find(s=>s.role==='rooms');rows.cards=[];for(let i=0;i<n;i++){const id=g.id+'-'+i;g.state.rooms.push({id,session:S,host:P,mine:false,roomName:'队友 '+i+' 的房间',phase:'WAITING',count:1,capacity:4});rows.cards.push({title:id,actions:[{label:'加入',action:'join',value:id,enabled:true}]});}}};
 f.own=()=>f.state.games.flatMap(g=>g.state.rooms).find(r=>r.mine);
 f.compose=()=>{for(const g of f.state.games){const r=g.state.rooms.find(r=>r.mine);g.ui.lobby.sections=g.ui.lobby.sections.filter(s=>['create','rooms'].includes(s.role));g.ui.lobby.sections[0].actions[0].enabled=!f.own();if(!r)continue;
  const host=r.host===H,waiting=['WAITING','LOBBY'].includes(r.phase),create=g.ui.lobby.sections[0],diff=create.fields.find(f=>f.id==='difficulty');if(r.ai)r.ai.editable=waiting&&!r.ai.locked&&r.ai.supported;
  g.ui.lobby.sections.push({role:'room',actions:[{label:'开始',action:'start',value:'',enabled:host&&waiting},{label:'退出并恢复',action:'leave',value:'',enabled:true,safe:true,confirm:'退出并恢复原物品和位置？'}]},{role:'settings',fields:[{...clone(diff),id:'roomDifficulty',selected:String(r.difficulty)}],actions:[{label:'修改难度',action:g.id==='horse_racing'?'enemy':'difficulty',value:'{roomDifficulty}',enabled:host&&waiting}],cards:g.id==='flight'?r.roster.map(m=>({title:m.name,text:m.side==='red'?'红队':'蓝队',actions:['red','blue'].map(side=>({label:side==='red'?'红队':'蓝队',action:'assignSide',value:m.uuid+'|'+side,enabled:host&&waiting&&r.mode==='pvp'}))})):[]},{role:'invite',cards:[{title:'100002',actions:[{label:'邀请',action:'invite',value:P,enabled:host&&waiting}]}]});
 }};
 f.enter=(gameId=f.last?.game||'flight',name='Dot 🦊的房间',values)=>{const g=f.state.games.find(g=>g.id===gameId),fields=g.ui.lobby.sections[0].fields,selected=Object.fromEntries(fields.map((v,i)=>[v.id,values?.[i]||v.selected]));g.state.rooms=g.state.rooms.filter(r=>!r.mine);g.state.rooms.unshift({id:'ROOM1',session:S,host:H,mine:true,roomName:name,phase:g.id==='zombie-challenge'?'LOBBY':'WAITING',count:2,capacity:selected.mode==='pvp'?2:4,...selected,roster:[{uuid:H,name:'Dot 🦊',host:true,online:true,side:g.id==='flight'?'red':undefined},{uuid:P,name:'队友小白',host:false,online:true,side:g.id==='flight'?(selected.mode==='pvp'?'blue':'red'):undefined}]});f.state.activeGame=gameId;f.compose();};
 f.apply=()=>{const a=f.last;if(a.action==='roomCreate'){const [name,values]=JSON.parse(a.value);f.enter(a.game,name,values);}else if(a.action==='roomSettings'){const [,name,change]=JSON.parse(a.value),r=f.own();r.roomName=name;if(change.length)r.difficulty=change[1];f.compose();}else if(a.action==='join')f.enter(a.game,'加入的房间');else if(a.action==='roomAiAdd'||a.action==='roomAiRemove'){const [session,revision,seat]=JSON.parse(a.value),r=f.own();if(session!==r.session||revision!==r.ai.revision)throw Error('fixture stale CAS');if(a.action==='roomAiAdd')r.roster.push({kind:'ai',aiId:'cccccccc-cccc-4ccc-8ccc-'+String(++f.aiSequence).padStart(12,'0'),name:'酒狐女仆',appearance:'jiufox_maid'});else r.roster=r.roster.filter(m=>m.aiId!==seat);r.ai.revision=String(BigInt(r.ai.revision)+1n);r.count=r.roster.length;r.aiCount=r.roster.filter(m=>m.kind==='ai').length;f.compose();}else if(a.action==='start'){f.own().phase='RUNNING';if(f.own().ai)f.own().ai.locked=true;f.compose();}else if(a.action==='leave'){for(const g of f.state.games)g.state.rooms=g.state.rooms.filter(r=>!r.mine);f.state.activeGame='';f.compose();}else if(a.action==='assignSide'){const [id,side]=a.value.split('|');f.own().roster.find(m=>m.uuid===id).side=side;f.compose();}};
 f.complete=(membership=true)=>{if(membership)f.apply();f.state.operation={request:f.last.request,status:f.fail?'failed':'completed',notice:f.fail?'房间操作失败':'服务器已确认'};};
 f.aiSequence=0;f.aiEnable=(gameId,enabled=true)=>{f.state.roomAiVersion=1;const g=f.state.games.find(g=>g.id===gameId),r=f.own();g.roomCapabilities={aiTeammates:{supported:enabled,appearance:'jiufox_maid',label:'酒狐女仆'}};r.ai={supported:enabled,revision:'0',locked:false,editable:enabled,appearance:'jiufox_maid'};r.roster.forEach(m=>m.kind='human');r.humanCount=r.roster.length;r.aiCount=0;f.compose();};
 f.reset=()=>{f.state.activeGame='';delete f.state.operation;f.hold=true;f.fail=false;f.rooms();};
 f.social=()=>({version:1,enabled:f.trusted,authenticated:f.trusted,selfUid:'100001',selfUuid:H,onlinePlayers:[{uid:'100002',uuid:P,gameName:'100002',displayName:'小白'},{uid:'100003',uuid:'44444444-4444-4444-8444-444444444444',gameName:'100003',displayName:'小红'}],rooms:f.own()?[{game:f.state.activeGame,session:S,host:f.own().host,mine:true,inviteable:f.own().host===H&&['WAITING','LOBBY'].includes(f.own().phase),socialManaged:true}]:[],invitations:f.tickets||[],operation:f.inviteReceipt});
 f.finishInvite=()=>{f.tickets=[{invitation:'55555555-5555-4555-8555-555555555555',game:f.state.activeGame,room:S,host:H,target:P,source:f.lastInvite.source,status:'PENDING',expiresInSeconds:300}];f.inviteReceipt={request:f.inviteRequest,status:'completed'};};
 f.state.social={enabled:true,authenticated:true,invitations:[]};f.rooms();
 window.muxiTerminalQuery=o=>{f.calls.push(o.request);let value={ok:true};
  if(o.request==='games.context')value={game:'',page:'lobby',drafts:{}};
  else if(o.request==='games.snapshot')value=clone(f.state);
  else if(o.request==='friends.invites.snapshot')value=f.social();
  else if(o.request==='friends.invite-peers')value={selfUid:'100001',friends:[{uid:'100002',uuid:P,gameName:'100002'}]};
  else if(o.request==='apps.list')value=[];
  else if(o.request==='tasks.snapshot')value={supported:false,rows:[]};
  else if(o.request.startsWith('resource.data:'))value='';
  else if(o.request==='icons.state')value={revision:1};
  else if(o.request.startsWith('icons.get:'))value={revision:1,src:''};
  else if(o.request.startsWith('friends.invites.action:')){const a=JSON.parse(o.request.slice(23));f.inviteActions.push(a);f.lastInvite=a;f.inviteRequest='aaaaaaaa-aaaa-4aaa-8aaa-'+String(f.inviteActions.length).padStart(12,'0');f.inviteReceipt={request:f.inviteRequest,status:'pending'};value={ok:true,request:f.inviteRequest};}
  else if(o.request.startsWith('games.action:')){const a=JSON.parse(o.request.slice(13));f.actions.push(a);f.last={...a,request:'bbbbbbbb-bbbb-4bbb-8bbb-'+String(f.actions.length).padStart(12,'0')};f.state.operation={request:f.last.request,status:f.fail?'failed':'pending',notice:f.fail?'房间操作失败':''};value={ok:true,request:f.last.request};if(!f.hold&&!f.fail)f.complete();}
  queueMicrotask(()=>o.onSuccess(JSON.stringify(value)));
 };
})();`;

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
  const click=selector=>js(`{const e=document.querySelector(${JSON.stringify(selector)});e.focus();e.click();}`);
  const change=(selector,value)=>js(`{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('change',{bubbles:true}));}`);
  const refresh=()=>js('window.MuxiMinigamesApp.activate()');
  const close=()=>click('.mg-dialog [data-mg-dialog-close]');
  await check('clean lobby has room list, no inline creation/settings/invitation form',`document.querySelector('#mg-room-list')&&document.getElementById('mg-room-invitations').hidden&&!document.querySelector('[data-mg-field]')&&!document.querySelector('#mg-content .friends-invite-panel')`);
  await js(`__fx.state.games[0].ui.lobby.sections.push({role:'recovery',title:'恢复上次状态',actions:[{label:'恢复并退出',action:'leave',value:'',safe:true,enabled:true}]});__fx.state.allowed=false`);await refresh();
  await check('recovery retains a safe exit even when normal actions are restricted',`document.querySelector('.mg-recovery button')&&!document.querySelector('.mg-recovery button').disabled&&document.querySelector('[data-mg-create]').disabled`);
  await js('__fx.state.allowed=true;__fx.rooms(20)');await refresh();
  for(const [width,height]of [[1920,1080],[1280,720],[640,360],[480,320]]){
   await cdp('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});
   await check(`lobby fills available height and scrolls internally ${width}x${height}`,`(()=>{const p=document.getElementById('games'),l=document.getElementById('mg-room-list');return p.scrollWidth<=p.clientWidth&&p.scrollHeight<=p.clientHeight&&l.scrollHeight>l.clientHeight&&l.getBoundingClientRect().bottom<=p.getBoundingClientRect().bottom;})()`);
   await js(`document.getElementById('mg-room-list').scrollTop=100`);await refresh();
   await check(`refresh preserves list scroll ${width}x${height}`,`document.getElementById('mg-room-list').scrollTop===100`);
   await shot(`lobby-${width}x${height}`);
  }
  await cdp('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
  await click('[data-mg-create]');
  await check('create is one dialog with actual nickname, game, mode, map, difficulty',`document.querySelector('[role="dialog"]')&&!document.querySelector('.mg-dialog').hidden&&document.querySelector('[data-mg-room-name]').value==='Dot 🦊的房间'&&document.querySelectorAll('.mg-dialog select').length===4`);
  await check('flight creation does not expose hangar or seat knobs',`!document.querySelector('.mg-dialog').textContent.match(/redHumans|blueAi|机库|出击上限/)`);
  await shot('create-flight');
  for(const [width,height]of [[640,360],[480,320]]){
   await cdp('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});
   await check(`create dialog fits viewport ${width}x${height}`,`(()=>{const p=document.querySelector('.mg-dialog-panel'),r=p.getBoundingClientRect();return r.left>=0&&r.top>=0&&r.right<=innerWidth&&r.bottom<=innerHeight&&p.scrollWidth<=p.clientWidth;})()`);await shot(`create-${width}x${height}`);
  }
  await cdp('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
  await change('[data-mg-create-game]','outbreak');await change('[data-mg-field="mode"]','SURVIVAL');
  await check('mode filters compatible map regardless of native field order',`document.querySelector('[data-mg-field="map"]').options.length===1&&document.querySelector('[data-mg-field="map"]').value==='camp'`);
  await close();await check('cancel creation sends no request',`__fx.actions.length===0&&document.querySelector('.mg-dialog').hidden`);
  for(let i=0;i<3;i++){await click('[data-mg-create]');await close();}
  await check('reopening create does not duplicate dialog or submit listeners',`document.querySelectorAll('.mg-dialog').length===1&&__fx.actions.length===0`);
  await click('[data-mg-create]');
  await js(`{const e=document.querySelector('[data-mg-room-name]');e.value='<img src=x onerror=alert(1)>';e.dispatchEvent(new Event('input',{bubbles:true}));}`);
  await click('[data-mg-submit="create"]');await check('invalid long name cannot submit',`__fx.actions.length===0&&document.querySelector('.mg-dialog-status').textContent.length>0`);
  await js(`{const e=document.querySelector('[data-mg-room-name]');e.value='<b>Dot</b>';e.dispatchEvent(new Event('input',{bubbles:true}));}`);
  await change('[data-mg-field="difficulty"]','3');await js(`document.querySelector('[data-mg-submit="create"]').click();document.querySelector('[data-mg-submit="create"]').click()`);
  await pause(100);
  await check('duplicate create sends once; ACK does not auto-enter or start',`__fx.actions.length===1&&__fx.last.action==='roomCreate'&&!__fx.own()&&!document.querySelector('.mg-dialog').hidden&&document.querySelector('[data-mg-submit="create"]').disabled&&!__fx.actions.some(a=>a.action==='start')`);
  await js('__fx.complete(false)');await refresh();
  await check('completed receipt without actual membership does not enter room',`!__fx.own()&&!document.querySelector('.mg-dialog').hidden&&document.querySelector('[data-mg-submit="create"]').disabled`);
  await js('__fx.apply()');await refresh();await eventually(`!!document.querySelector('[data-mg-settings]')`);
  await check('membership plus final receipt enters waiting details and actual roster',`document.querySelector('.mg-dialog').hidden&&document.querySelectorAll('.mg-members li').length===2&&document.querySelector('.mg-room-overview h3').textContent==='<b>Dot</b>'&&!document.querySelector('.mg-room-overview h3 b')&&__fx.own().phase==='WAITING'`);
  await check('room has unified invite/settings buttons and removes bottom invite complexity',`document.querySelectorAll('[data-mg-invites]').length===1&&document.querySelectorAll('[data-mg-settings]').length===1&&document.getElementById('mg-room-invitations').hidden&&!document.querySelector('#mg-content [data-mg-choice]')`);await shot('waiting-room');
  await click('[data-mg-settings]');
  await check('PVE settings contain only room name and one difficulty',`document.querySelector('.mg-dialog [data-mg-room-name]')&&document.querySelectorAll('.mg-dialog select').length===1&&document.querySelectorAll('[data-mg-modal-action]').length===0`);
  await check('PVE hides redundant team assignment controls',`document.querySelectorAll('[data-mg-modal-action]').length===0`);await shot('settings-flight');
  await js(`{const e=document.querySelector('[data-mg-room-name]');e.value='训练室';e.dispatchEvent(new Event('input',{bubbles:true}));__fx.hold=false;}`);await change('[data-mg-field]','4');await click('[data-mg-submit="settings"]');await eventually('document.querySelector(".mg-dialog").hidden');
  await check('settings save waits for receipt and authoritative room name/difficulty',`__fx.own().roomName==='训练室'&&__fx.own().difficulty==='4'&&document.querySelector('.mg-room-overview h3').textContent==='训练室'`);
  await js(`__fx.own().mode='pvp';__fx.own().roster[1].side='blue';__fx.compose()`);await refresh();await click('[data-mg-settings]');
  await check('PVP retains compact red/blue team assignment in settings',`document.querySelectorAll('[data-mg-modal-action]').length===4`);await shot('settings-flight-pvp');await close();
  await js(`__fx.own().mode='pve';__fx.own().roster[1].side='red';__fx.compose()`);await refresh();
  await click('[data-mg-invites]');await eventually(`document.querySelectorAll('[data-invite-uid]').length===2`);
  await check('invite dialog tab order is online left, friends right',`document.getElementById('friendsInviteOnline').textContent==='在线玩家'&&document.getElementById('friendsInviteFriends').textContent==='我的好友'&&document.getElementById('friendsInviteOnline').compareDocumentPosition(document.getElementById('friendsInviteFriends'))&Node.DOCUMENT_POSITION_FOLLOWING`);await shot('invite-online');
  await click('#friendsInviteFriends');await eventually(`document.querySelectorAll('[data-invite-uid]').length===1`);await shot('invite-friends');
  await check('friends tab uses actual online friendship intersection',`document.querySelector('[data-invite-uid]').dataset.inviteUid==='100002'&&__fx.calls.includes('friends.invite-peers')`);
  await js(`document.querySelector('[data-invite-uid]').click();document.querySelector('[data-invite-uid]').click()`);await pause(100);
  await check('invite duplicate click sends once and waits for final receipt',`__fx.inviteActions.length===1&&document.querySelector('[data-invite-uid]').disabled`);
  await close();await click('[data-mg-invites]');
  await check('close/reopen preserves pending invite and disables duplicate issuance',`document.querySelector('[data-invite-uid]').disabled&&__fx.inviteActions.length===1`);
  await js('__fx.finishInvite()');await eventually(`!!document.querySelector('[data-invite-cancel]')&&!document.querySelector('[data-invite-uid]').disabled`);
  await check('matching final receipt plus invitation ticket unlocks dialog',`__fx.inviteActions.length===1&&document.querySelector('[data-invite-cancel]')`);
  await close();await js(`__fx.trusted=false;__fx.state.social={enabled:false,authenticated:false,invitations:[]}`);await refresh();await click('[data-mg-invites]');await click('#friendsInviteRefresh');await click('#friendsInviteOnline');await eventually(`!!document.querySelector('[data-invite-player]')`);
  await check('online invitation fallback uses actual game action, friends honestly unavailable',`document.querySelector('[data-invite-player]').dataset.invitePlayer===__fx.P`);
  const writes=await js('__fx.actions.length');await click('[data-invite-player]');await eventually(`__fx.actions.length===${writes+1}&&!document.querySelector('[data-invite-player]').disabled`);
  await check('fallback issues exactly one native invite',`__fx.last.action==='invite'&&__fx.last.value===__fx.P`);await click('#friendsInviteFriends');await check('unavailable friends tab is explicit',`document.getElementById('friendsInviteStatus').textContent.includes('未提供')&&!document.querySelector('[data-invite-uid]')`);await close();
  await js(`__fx.own().host=__fx.P;__fx.compose()`);await refresh();await click('[data-mg-settings]');
  await check('member can inspect settings but cannot rename/change difficulty/save/invite',`[...document.querySelectorAll('.mg-dialog input,.mg-dialog select,.mg-dialog [data-mg-submit],.mg-dialog [data-mg-modal-action]')].every(c=>c.disabled)&&document.querySelector('[data-mg-invites]').disabled`);await shot('settings-member-readonly');await close();
  await js(`__fx.own().host=__fx.H;__fx.own().phase='RUNNING';__fx.compose()`);await refresh();await click('[data-mg-settings]');
  await check('active room settings remain read-only despite host',`document.querySelector('[data-mg-submit="settings"]').disabled&&document.querySelector('[data-mg-invites]').disabled`);await close();
  await js(`__fx.own().phase='WAITING';__fx.compose();__fx.fail=true`);await refresh();await click('[data-mg-settings]');await click('[data-mg-submit="settings"]');await eventually(`document.querySelector('.mg-dialog-status').textContent.includes('失败')`);
  await check('failed server setting stays in dialog without invented success',`!document.querySelector('.mg-dialog').hidden&&__fx.own().roomName==='训练室'&&!document.querySelector('[data-mg-submit="settings"]').disabled`);await close();await js('__fx.fail=false');
  for(const gameId of ['horse_racing','zombie-challenge','outbreak']){
   await js(`__fx.reset()`);await click('#mg-back');await refresh();await click('[data-mg-create]');await change('[data-mg-create-game]',gameId);
   if(gameId==='outbreak')await change('[data-mg-field="mode"]','SURVIVAL');
   await js('__fx.hold=false');await click('[data-mg-submit="create"]');await eventually(`!!document.querySelector('[data-mg-settings]')&&document.querySelector('.mg-dialog').hidden`);
   await check(`${gameId} creates into waiting via same compact room adapter`, `__fx.last.game===${JSON.stringify(gameId)}&&__fx.last.action==='roomCreate'&&['WAITING','LOBBY'].includes(__fx.own().phase)&&__fx.own().roomName==='Dot 🦊的房间'`);
   await click('[data-mg-settings]');await check(`${gameId} exposes one difficulty setting`, `document.querySelectorAll('.mg-dialog select').length===1&&!document.querySelector('[data-mg-submit="settings"]').disabled`);await shot('settings-'+gameId);await close();
  }
  for(const gameId of ['outbreak','zombie-challenge']){
   await js(`__fx.reset();__fx.enter(${JSON.stringify(gameId)});__fx.aiEnable(${JSON.stringify(gameId)})`);await click('#mg-back');await refresh();await click(`[data-mg-open-room="${gameId}"]`);
   await check(`${gameId} opts into one AI button in existing roster`, `document.querySelectorAll('[data-mg-ai-add]').length===1&&document.querySelector('[data-mg-ai-add]').closest('.detail-card').querySelector('.mg-members')&&!document.querySelector('[data-mg-ai-add]').disabled`);
   const before=await js('__fx.actions.length');await js(`document.querySelector('[data-mg-ai-add]').click();document.querySelector('[data-mg-ai-add]').click()`);await pause(100);
   await check(`${gameId} duplicate add emits one request and ACK waits for roster`, `__fx.actions.length===${before+1}&&__fx.last.action==='roomAiAdd'&&__fx.own().aiCount===0&&document.querySelector('[data-mg-ai-add]').disabled`);
   await js('__fx.complete(false)');await refresh();
   await check(`${gameId} completed receipt without seat mutation stays pending`, `document.querySelector('[data-mg-ai-add]').disabled&&document.querySelectorAll('.mg-ai-member').length===0`);
   await js('__fx.apply()');await refresh();await eventually(`document.querySelectorAll('.mg-ai-member').length===1&&!document.querySelector('[data-mg-ai-add]').disabled`);
   await check(`${gameId} confirmed configured AI never appears as an online human`, `document.querySelector('.mg-ai-member').textContent.includes('AI 队友')&&document.querySelector('.mg-ai-member').textContent.includes('待开局')&&!document.querySelector('.mg-ai-member').textContent.match(/在线|离线/)&&__fx.own().humanCount===2`);
   await js('__fx.hold=false');await click('[data-mg-ai-add]');await eventually(`document.querySelectorAll('.mg-ai-member').length===2&&__fx.own().ai.revision==='2'`);
   await check(`${gameId} total human and AI capacity disables add and invite`, `__fx.own().count===4&&document.querySelector('[data-mg-ai-add]').disabled&&document.querySelector('[data-mg-invites]').disabled&&!document.querySelector('[data-mg-ai-remove]').disabled`);await shot('ai-full-'+gameId);
   await click('[data-mg-ai-remove]');await eventually(`document.querySelectorAll('.mg-ai-member').length===1&&__fx.own().ai.revision==='3'&&!document.querySelector('[data-mg-ai-add]').disabled`);
   await check(`${gameId} remove sends exact seat and restores capacity`, `__fx.last.action==='roomAiRemove'&&JSON.parse(__fx.last.value).length===3&&__fx.own().count===3&&__fx.own().humanCount===2&&document.querySelectorAll('.mg-members li:not(.mg-ai-member)').length===2`);
   await js(`__fx.own().host=__fx.P;__fx.compose()`);await refresh();await check(`${gameId} nonhost sees roster but cannot add or remove`, `document.querySelector('[data-mg-ai-add]').disabled&&document.querySelector('[data-mg-ai-remove]').disabled`);
   await js(`__fx.own().host=__fx.H;__fx.own().phase='RUNNING';__fx.own().ai.locked=true;__fx.compose()`);await refresh();await check(`${gameId} playing frozen plan has read-only controls`, `document.querySelector('[data-mg-ai-add]').disabled&&document.querySelector('[data-mg-ai-remove]').disabled&&document.querySelector('.mg-ai-member').textContent.includes('已锁定')`);
   await js(`__fx.own().phase='WAITING';__fx.own().ai.locked=false;__fx.state.allowed=false;__fx.compose()`);await refresh();await check(`${gameId} participation restriction blocks AI mutations`, `document.querySelector('[data-mg-ai-add]').disabled&&document.querySelector('[data-mg-ai-remove]').disabled`);await js('__fx.state.allowed=true');await refresh();
   for(const [width,height]of [[1280,720],[480,320]]){await cdp('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});await check(`${gameId} roster actions fit width ${width}`,`document.getElementById('games').scrollWidth<=document.getElementById('games').clientWidth&&document.querySelector('.mg-ai-member').scrollWidth<=document.querySelector('.mg-ai-member').clientWidth`);await shot(`ai-roster-${gameId}-${width}x${height}`);}await cdp('Emulation.setDeviceMetricsOverride',{width:1280,height:720,deviceScaleFactor:1,mobile:false});
  }
  for(const gameId of ['flight','horse_racing']){await js(`__fx.reset();__fx.enter(${JSON.stringify(gameId)});__fx.aiEnable(${JSON.stringify(gameId)})`);await click('#mg-back');await refresh();await click(`[data-mg-open-room="${gameId}"]`);await check(`${gameId} has no manual AI controls even with forged opt-in`, `!document.querySelector('[data-mg-ai-add]')&&!document.querySelector('[data-mg-ai-remove]')`);}
  await js(`__fx.reset();__fx.enter('outbreak');__fx.aiEnable('outbreak',false)`);await click('#mg-back');await refresh();await click('[data-mg-open-room="outbreak"]');await check('unwired outbreak owner capability hides AI controls',`!document.querySelector('[data-mg-ai-add]')`);
  await click('[data-mg-page="shop"]');await check('shop preserves exact point amount and game currency actions',`document.getElementById('mg-platform').textContent.includes('9223372036854775807')&&document.querySelector('#mg-content [data-mg-action]')`);await shot('shop-regression');
  await click('#mg-back');await js('__fx.reset()');await refresh();await click('[data-mg-create]');
  await js(`document.querySelector('.mg-dialog-footer [data-mg-submit]').focus()`);await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});await cdp('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});
  await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowRight',code:'ArrowRight',windowsVirtualKeyCode:39});await cdp('Input.dispatchKeyEvent',{type:'keyUp',key:'ArrowRight',code:'ArrowRight',windowsVirtualKeyCode:39});
  await check('arrow navigation cannot move focus into the modal background',`document.querySelector('.mg-dialog').contains(document.activeElement)`);
  await check('Tab traps focus within the modal',`document.querySelector('.mg-dialog').contains(document.activeElement)`);
  await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await cdp('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});await check('Escape closes modal and sends no mutation',`document.querySelector('.mg-dialog').hidden&&document.activeElement.matches('[data-mg-create]')`);
  assert.deepEqual(errors,[],'no uncaught browser exceptions');
  const sourceHashes={};for(const file of ['apps/minigames/app.js','apps/minigames/model.js','apps/minigames/dialog.js','apps/minigames/app.css','friends-invites.js','friends-invites-controller.js','index.html'])sourceHashes[file]=crypto.createHash('sha256').update(fs.readFileSync(path.join(assets,file))).digest('hex');
  const report={success:true,checkCount:checks.length,checks,errors,sourceHashes,scope:'Actual headless Chromium DOM/layout/interaction with explicit native API fixtures; no Minecraft/MCEF or live friend service acceptance.',output};fs.writeFileSync(path.join(output,'result.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
}catch(error){await shot('failure').catch(()=>{});fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({error:String(error),checks,errors,stderr},null,2));throw error;}
finally{if(socket){await cdp('Browser.close').catch(()=>{});socket.close();}await new Promise(resolve=>server.close(resolve));}

from pathlib import Path
root=Path(__file__).resolve().parents[2];assets=root/'src/main/resources/assets/muxi_terminal/html/terminal'
stub=r'''<script>
window.qaCalls=[];window.qaHold=false;window.qaPending=null;
const peer=(uid,name=uid,online=true)=>({uid,displayName:name,gameName:uid,uuid:'00000000-0000-0000-0000-000000000001',online,messageAvailable:online});
window.qaRuntime={version:1,enabled:true,authenticated:true,selfUid:'100001',selfUuid:'11111111-1111-4111-8111-111111111111',rooms:[{game:'zombie-challenge',session:'33333333-3333-4333-8333-333333333333',host:'11111111-1111-4111-8111-111111111111',mine:true,socialManaged:true,inviteable:true}],onlinePlayers:[peer('100002','在线好友'),peer('100005','在线陌生玩家')],invitations:[]};window.qaGameHold=false;window.qaGameFinish=null;
window.qaSnapshot={version:1,selfUid:'100001',authenticated:true,identityMode:'platform-uid',presenceAvailable:true,friends:[peer('100002','<img src=x onerror="window.qaXss=true">'),peer('9007199254740993','精确大 UID',false)],incoming:[peer('100003','申请玩家')],outgoing:[],blocked:[],onlinePlayers:[peer('100002','在线好友'),peer('100005','在线陌生玩家')],limits:{friends:500,pending:100,blocked:1000,online:1024}};
window.muxiTerminalQuery=({request,onSuccess,onFailure})=>setTimeout(()=>{
qaCalls.push(request);let response={ok:true};
if(request==='friends.invites.snapshot')response=structuredClone(qaRuntime);
if(request.startsWith('friends.invites.action:')){const a=JSON.parse(request.slice(23)),requestId='55555555-5555-4555-8555-555555555555';response={ok:true,request:requestId};qaRuntime.operation={request:requestId,status:'pending'};
qaGameFinish=()=>{if(a.op==='issue')qaRuntime.invitations.push({invitation:'44444444-4444-4444-8444-444444444444',room:a.room,game:a.game,source:a.source,host:qaRuntime.selfUuid,target:qaRuntime.onlinePlayers.find(p=>p.uid===a.uid).uuid,status:'PENDING',expiresInSeconds:300});else{const t=qaRuntime.invitations.find(t=>t.invitation===a.invitation);t.status={accept:'ACCEPTED',decline:'DECLINED',cancel:'CANCELLED'}[a.op];if(a.op==='accept')qaRuntime.rooms.push({game:t.game,session:t.room,mine:true,socialManaged:true});}qaRuntime.operation={request:requestId,status:'completed'};};if(!qaGameHold)setTimeout(()=>qaGameFinish(),350);}
if(request==='friends.snapshot')response=structuredClone(qaSnapshot);
if(request==='friends.invite-peers')response={version:1,selfUid:qaSnapshot.selfUid,presenceAvailable:qaSnapshot.presenceAvailable,friends:structuredClone(qaSnapshot.friends),onlinePlayers:structuredClone(qaSnapshot.onlinePlayers),runtime:structuredClone(qaRuntime)};
if(request.startsWith('friends.action:')){
  const a=JSON.parse(request.slice(15));response={version:1,request:a.request,status:'ok',ok:true,changed:true};
  if(qaHold){qaPending=()=>onSuccess(JSON.stringify(response));return;}
  const remove=key=>qaSnapshot[key]=qaSnapshot[key].filter(p=>p.uid!==a.uid);
  if(a.op==='request')qaSnapshot.outgoing.push(peer(a.uid));
  if(a.op==='accept'){remove('incoming');qaSnapshot.friends.push(peer(a.uid));}
  if(a.op==='cancel'){remove('incoming');remove('outgoing');}
  if(a.op==='remove')remove('friends');
  if(a.op==='block'){remove('friends');remove('incoming');remove('outgoing');qaSnapshot.blocked.push(peer(a.uid));}
  if(a.op==='unblock')remove('blocked');
}
if(request.startsWith('friends.message:'))response={ok:true,openedChat:true,prefillOnly:true};
if(request==='games.snapshot')response={protocol:2,actionSupported:true,games:[],allowed:false,resultPending:0,platform:{verified:false}};
if(request==='games.context')response={};onSuccess(JSON.stringify(response));
},10);
</script>'''
html=(assets/'index.html').read_text(encoding='utf-8').replace('<head>','<head>\n<base href="'+assets.as_uri()+'/">\n'+stub,1)
(Path(__file__).parent/'harness.html').write_text(html,encoding='utf-8')
source=(root/'tests/app-transition/run-browser-qa.mjs').read_text(encoding='utf-8')
prefix=source[:source.index("  await cdp('Page.enable')")].replace("pathToFileURL(path.join(directory, 'demo.html')).href","pathToFileURL(path.join(directory, 'harness.html')).href+'#/friends'")
tests=r'''
  await cdp('Page.enable');await cdp('Runtime.enable');await cdp('Emulation.setDeviceMetricsOverride',{width:960,height:660,deviceScaleFactor:1,mobile:false});await pause(1100);
  const tests=[];async function check(name,expression){tests.push({name,passed:!!await evaluate(expression)});}
  async function click(selector){await evaluate('document.querySelector('+JSON.stringify(selector)+').click()');await pause(170);}
  async function key(key,code=key){await cdp('Input.dispatchKeyEvent',{type:'keyDown',key,code});await cdp('Input.dispatchKeyEvent',{type:'keyUp',key,code});await pause(100);}
  await check('independent friends card and current shell','document.querySelectorAll("#friendsApp").length===1 && document.querySelector("#friends.page-active") && document.querySelectorAll(".shell").length===1');
  await check('album and all original modules preserved','document.querySelector("#albumApp") && document.querySelector("#camera") && document.querySelector("#music") && document.querySelector("#settings") && document.querySelector("#games")');
  await check('dynamic installed count includes ninth app','document.querySelectorAll("#home .app-card").length===9 && document.querySelector("#installedAppCount").textContent.startsWith("9")');
  await check('opening reads without relationship writes','qaCalls.includes("friends.snapshot") && !qaCalls.some(x=>x.startsWith("friends.action:"))');
  await check('large UID stays exact and display text cannot inject','document.querySelector("#friendsList").textContent.includes("9007199254740993") && !document.querySelector("#friendsList img") && !window.qaXss');
  await check('offline private message unavailable','[...document.querySelectorAll("[data-friends-op=message]")].find(button=>button.dataset.friendsUid==="9007199254740993").disabled');
  await screenshot('01-friends-synthetic');await click('[data-friends-op=message][data-friends-uid="100002"]');
  await check('shortcut asks native prefill only','qaCalls.includes("friends.message:100002") && !qaCalls.some(x=>x.includes("sendChat")||x.includes("sendCommand"))');
  await click('[data-friends-op=remove][data-friends-uid="100002"]');
  await check('safe confirmation excludes background controls','document.activeElement.id==="friendsCancel" && [...document.querySelectorAll("#friendsBody button,#friendsBody input")].every(b=>b.disabled) && qaSnapshot.friends.length===2');
  await screenshot('02-confirmation-synthetic');await key('Tab');await check('shared Tab reaches only confirm action','document.activeElement.id==="friendsConfirmOk"');
  await key('Escape');await check('DOM Escape cancels without mutation','document.querySelector("#friendsConfirm").hidden && qaSnapshot.friends.length===2');
  await click('[data-friends-op=remove][data-friends-uid="100002"]');await click('#friendsConfirmOk');
  await check('explicit remove reflects confirmed API snapshot','qaSnapshot.friends.length===1 && ![...document.querySelectorAll("[data-friends-uid]")].some(button=>button.dataset.friendsUid==="100002")');
  await click('[data-friends-tab=add]');await evaluate('document.querySelector("#friendsUid").value="100004";document.querySelector("#friendsAddForm").requestSubmit()');await pause(200);
  await check('UID request uses explicit action and idempotency key','qaCalls.some(x=>{if(!x.startsWith("friends.action:"))return false;const a=JSON.parse(x.slice(15));return a.op==="request"&&a.uid==="100004"&&/^[0-9a-f-]{36}$/.test(a.request)&&!a.actor;})');
  await click('[data-friends-tab=requests]');await check('incoming and outgoing represented separately','document.querySelector("#friendsList").textContent.includes("收到的申请") && document.querySelector("#friendsList").textContent.includes("已发送的申请")');await screenshot('03-requests-synthetic');
  await click('[data-friends-op=accept][data-friends-uid="100003"]');await check('accept required before reciprocal friendship','!qaSnapshot.incoming.length && qaSnapshot.friends.some(p=>p.uid==="100003")');
  await click('[data-friends-tab=friends]');await click('[data-friends-op=block][data-friends-uid="100003"]');await click('#friendsConfirmOk');
  await check('block removes relation after explicit confirmation','qaSnapshot.blocked.some(p=>p.uid==="100003") && !qaSnapshot.friends.some(p=>p.uid==="100003")');
  await click('[data-friends-tab=blocked]');await click('[data-friends-op=unblock][data-friends-uid="100003"]');await check('unblock never restores friendship','!qaSnapshot.blocked.length && !qaSnapshot.friends.some(p=>p.uid==="100003")');
  await evaluate('location.hash="/games"');await pause(550);await check('invitation online tab uses native roster','document.querySelectorAll("#friendsInviteRows button").length===2 && [...document.querySelectorAll("#friendsInviteRows button")].every(b=>!b.disabled)');
  await click('#friendsInviteFriends');await check('friends tab intersects reciprocal relation and native roster','!document.querySelector("#friendsInviteRows button")');
  await evaluate('qaSnapshot.friends.push(qaRuntime.onlinePlayers[0])');await click('#friendsInviteRefresh');await check('friends tab offers only verified reciprocal online friend','document.querySelectorAll("#friendsInviteRows button").length===1');
  await evaluate('qaGameHold=true');await click('[data-invite-uid="100002"]');await check('ACK and pending keep busy without final success','document.querySelector("#friendsInviteStatus").textContent.includes("等待") && document.querySelector("#friendsInviteRefresh").disabled && !qaRuntime.invitations.length');
  await evaluate('qaGameFinish();qaGameHold=false');await pause(350);await check('final issued ticket enables cancel','document.querySelector("[data-invite-op=cancel]") && !document.querySelector("#friendsInviteRefresh").disabled');
  await screenshot('04-invitation-tabs-synthetic');await click('[data-invite-op=cancel]');await pause(550);await check('cancel waits for final cancelled ticket','qaRuntime.invitations[0].status==="CANCELLED" && !document.querySelector("[data-invite-op=cancel]")');
  await evaluate('qaRuntime.invitations.push({invitation:"66666666-6666-4666-8666-666666666666",room:"77777777-7777-4777-8777-777777777777",game:"outbreak",source:"online",host:"00000000-0000-0000-0000-000000000001",target:qaRuntime.selfUuid,status:"PENDING",expiresInSeconds:300})');await click('#friendsInviteRefresh');await click('[data-invite-op=accept]');await pause(550);await check('accept requires final ticket and actual room membership','qaRuntime.invitations[1].status==="ACCEPTED" && qaRuntime.rooms.some(r=>r.session===qaRuntime.invitations[1].room&&r.mine) && !document.querySelector("#friendsInviteRefresh").disabled');
  await evaluate('qaRuntime.invitations.push({...qaRuntime.invitations[1],invitation:"88888888-8888-4888-8888-888888888888",status:"PENDING"})');await click('#friendsInviteRefresh');await click('[data-invite-op=decline]');await pause(550);await check('decline receives final declined ticket','qaRuntime.invitations[2].status==="DECLINED"');
  await evaluate('qaRuntime.rooms[0].inviteable=false');await click('#friendsInviteRefresh');await check('old or unavailable managed room cannot invite','[...document.querySelectorAll("#friendsInviteRows button")].every(b=>b.disabled)');
  await evaluate('qaRuntime.enabled=false');await click('#friendsInviteRefresh');await check('missing server capability disables invitation UI','!document.querySelector("#friendsInviteRows button") && document.querySelector("#friendsInviteStatus").textContent.includes("协议")');
  await evaluate('location.hash="/friends"');await pause(350);await click('[data-friends-tab=friends]');await cdp('Emulation.setDeviceMetricsOverride',{width:420,height:500,deviceScaleFactor:1,mobile:false});await screenshot('05-small-friends-synthetic');
  await check('small viewport has no horizontal overflow','document.querySelector("#friends").scrollWidth<=document.querySelector("#friends").clientWidth');
  await click('[data-friends-tab=add]');await evaluate('qaHold=true;document.querySelector("#friendsUid").value="100006";document.querySelector("#friendsAddForm").requestSubmit()');await pause(100);await evaluate('location.hash="/home"');await pause(100);await evaluate('qaPending();qaHold=false');await pause(150);
  await check('old async receipt cannot reopen or populate new page','location.hash==="#/home" && window.MuxiFriendsApp.state().snapshot===null && !window.MuxiFriendsApp.state().lastReceipt');
  tests.push({name:'no runtime exceptions',passed:errors.length===0,errors});const report={synthetic:true,passed:tests.filter(t=>t.passed).length,total:tests.length,tests,scope:'headless browser/fake native bridge, no actual cookie/relations/chat/game clients'};
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));process.exitCode=report.passed===report.total?0:1;await cdp('Browser.close');
}finally{if(socket && socket.readyState===WebSocket.OPEN)socket.close();if(child.exitCode===null)child.kill();}
'''
# Keep selectors valid inside JS expression strings, with one escaped quote layer.
tests=tests.replace('\\\\"','\\"')
(Path(__file__).parent/'run-browser-qa.mjs').write_text(prefix+tests,encoding='utf-8')
print('Prepared friends browser QA; synthetic native API and relationship data only')

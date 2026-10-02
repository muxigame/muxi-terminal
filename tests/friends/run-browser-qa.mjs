// No npm dependencies. Use the installed Chrome and Node's built-in CDP WebSocket.
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {fileURLToPath, pathToFileURL} from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const output = path.join(directory, 'evidence');
fs.mkdirSync(output, {recursive: true});
const chrome = process.env.TRANSITION_CHROME || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const profile = fs.mkdtempSync(path.join(output, 'chrome-profile-'));
const child = spawn(chrome, ['--headless=new', '--disable-gpu', '--no-sandbox', '--disable-extensions', '--disable-background-networking', '--disable-component-update', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], {windowsHide: true, stdio: ['ignore', 'ignore', 'pipe']});
let stderr = '';
child.stderr.on('data', chunk => { stderr += chunk; });
let socket;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
try {
  let port;
  for (let i = 0; i < 150; i++) {
    const activePort = path.join(profile, 'DevToolsActivePort');
    if (fs.existsSync(activePort)) { port = Number(fs.readFileSync(activePort, 'utf8').split('\n')[0]); break; }
    if (child.exitCode !== null) throw new Error(`Chrome exited: ${stderr}`);
    await pause(100);
  }
  if (!port) throw new Error(`Chrome did not start: ${stderr}`);
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(pathToFileURL(path.join(directory, 'harness.html')).href+'#/friends')}`, {method: 'PUT'})).json();
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, {once: true}); socket.addEventListener('error', reject, {once: true}); });
  let sequence = 0;
  const pending = new Map(), errors = [];
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const callback = pending.get(message.id); pending.delete(message.id); clearTimeout(callback.timer);
      if (message.error) callback.reject(new Error(JSON.stringify(message.error)));
      else callback.resolve(message.result);
    } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
  });
  socket.addEventListener('close', event => {
    for (const callback of pending.values()) { clearTimeout(callback.timer); callback.reject(new Error(`CDP closed (${event.code}): ${stderr}`)); }
    pending.clear();
  });
  function cdp(method, params = {}) {
    const id = ++sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}; ${stderr}`)); }, 10000);
      pending.set(id, {resolve, reject, timer}); socket.send(JSON.stringify({id, method, params}));
    });
  }
  async function evaluate(expression) {
    const result = await cdp('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
  }
  async function screenshot(name) {
    const result = await cdp('Page.captureScreenshot', {format: 'png', captureBeyondViewport: false});
    fs.writeFileSync(path.join(output, `${name}.png`), Buffer.from(result.data, 'base64'));
  }

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

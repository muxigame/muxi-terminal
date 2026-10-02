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
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(pathToFileURL(path.join(directory, 'harness.html')).href+'#/album')}`, {method: 'PUT'})).json();
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

  await cdp('Page.enable');await cdp('Runtime.enable');
  await cdp('Emulation.setDeviceMetricsOverride',{width:960,height:660,deviceScaleFactor:1,mobile:false});await pause(1000);
  const tests=[];async function check(name,expression){tests.push({name,passed:!!await evaluate(expression)});}
  async function click(selector){await evaluate('document.querySelector('+JSON.stringify(selector)+').click()');await pause(220);}
  async function key(key,code=key){await cdp('Input.dispatchKeyEvent',{type:'keyDown',key,code});await cdp('Input.dispatchKeyEvent',{type:'keyUp',key,code});await pause(150);}
  await check('independent home card and fixed shell','document.querySelectorAll("#albumApp").length===1 && document.querySelectorAll(".shell").length===1 && document.querySelector("#album.page-active")');
  await check('shared installed-app count observes album card','document.querySelector("#installedAppCount").textContent.startsWith(String(document.querySelectorAll("#home .app-card").length))');
  await check('original modules retained','document.querySelector("#camera") && document.querySelector("#settings") && document.querySelector("#music") && document.querySelector("#games")');
  await check('native lazy thumbnail grid','document.querySelectorAll("#albumGrid .album-tile").length===2 && document.querySelector("#albumGrid img").src.startsWith("data:image/png;")');
  await screenshot('01-thumbnail-grid-synthetic');
  await click('#albumGrid button');
  await check('viewer opens and thumbnail bytes released','!document.querySelector("#albumViewer").hidden && document.querySelector("#albumImage").src.startsWith("data:image/png;") && !document.querySelector("#albumGrid img").hasAttribute("src")');
  await click('#albumZoomIn');await check('zoom control','document.querySelector("#albumImage").style.width==="150%"');await click('#albumFit');
  await click('#albumDelete');
  await check('clear confirmation before any mutation','!document.querySelector("#albumConfirm").hidden && qaLive.length===2 && !qaCommands.some(x=>x.startsWith("album.recycle:"))');
  await check('safe default focus and background excluded','document.activeElement.id==="albumCancelDelete" && [...document.querySelectorAll("#albumBody button")].every(b=>b.disabled)');
  await screenshot('02-confirmation-synthetic');
  await key('Tab');await check('shared Tab enters only confirm control','document.activeElement.id==="albumConfirmDelete"');
  await key('Escape');await check('Escape cancels without navigating or deleting','location.hash==="#/album" && document.querySelector("#albumConfirm").hidden && qaLive.length===2 && document.activeElement.id==="albumDelete"');
  await click('#albumDelete');await click('#albumCancelDelete');await check('explicit cancel keeps original','qaLive.length===2 && qaTrash.length===0');
  await click('#albumDelete');await click('#albumConfirmDelete');
  await check('confirmed recycle refreshes shared list and clears viewed bytes','qaLive.length===1 && qaTrash.length===1 && document.querySelectorAll("#albumGrid button").length===1 && !document.querySelector("#albumImage").hasAttribute("src")');
  await click('#albumRecycleTab');await click('#albumGrid button');
  await check('recycle viewer supports restore and hides deletion','!document.querySelector("#albumRestore").hidden && document.querySelector("#albumDelete").hidden');
  await screenshot('03-recycle-view-synthetic');await click('#albumRestore');await click('#albumPhotosTab');
  await check('restored photo immediately visible','qaLive.length===2 && qaTrash.length===0 && document.querySelectorAll("#albumGrid button").length===2');
  await evaluate('location.hash="/camera"');await pause(700);await click('#cameraShutter');await pause(500);
  await check('camera save uses shared library','qaLive.length===3 && qaState.saved');
  await check('camera retains embedded album and independent link','document.querySelector("#cameraAlbum") && document.querySelector("#cameraOpenAlbum")');
  await evaluate('location.hash="/album"');await pause(700);
  await check('new camera photo immediately present after album switch','document.querySelectorAll("#albumGrid button").length===3');
  await cdp('Emulation.setDeviceMetricsOverride',{width:420,height:500,deviceScaleFactor:1,mobile:false});await pause(200);
  await check('compact viewport has no horizontal overflow','document.querySelector("#album").scrollWidth<=document.querySelector("#album").clientWidth');await screenshot('04-small-grid-synthetic');
  await click('#albumGrid button');await click('#albumDelete');await screenshot('05-small-confirmation-synthetic');
  await check('compact confirmation visible and scrollable','(() => {const r=document.querySelector("#albumConfirm [role=dialog]").getBoundingClientRect();return r.top>=0 && r.bottom<=innerHeight && r.right<=innerWidth;})()');
  await evaluate('location.hash="/home"');await pause(250);
  await check('route exit drops images and pending confirmation','!qaPending && !document.querySelector("#albumImage").hasAttribute("src") && [...document.querySelectorAll("#albumGrid img")].every(i=>!i.hasAttribute("src"))');
  tests.push({name:'no browser runtime exceptions',passed:errors.length===0,errors});
  const report={synthetic:true,passed:tests.filter(t=>t.passed).length,total:tests.length,tests};
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));process.exitCode=report.passed===report.total?0:1;
  await cdp('Browser.close');
}finally{if(socket && socket.readyState===WebSocket.OPEN)socket.close();if(child.exitCode===null)child.kill();}

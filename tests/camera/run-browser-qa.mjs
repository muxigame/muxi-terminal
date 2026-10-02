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
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(pathToFileURL(path.join(directory, 'harness.html')).href+'#/camera')}`, {method: 'PUT'})).json();
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

  await cdp('Page.enable'); await cdp('Runtime.enable');
  await cdp('Emulation.setDeviceMetricsOverride',{width:960,height:660,deviceScaleFactor:1,mobile:false});
  await pause(1500);const tests=[];
  async function check(name,expression){tests.push({name,passed:!!await evaluate(expression)});}
  await check('camera route inside fixed existing shell','document.querySelector("#camera.page-active") && document.querySelectorAll(".shell").length===1 && document.querySelectorAll("#cameraApp").length===1');
  await check('private native first preview enables shutter','!document.querySelector("#cameraShutter").disabled && document.querySelector("#cameraPreview").src.startsWith("data:image/png;")');
  await screenshot('01-forward-synthetic');
  await evaluate('document.querySelector("#cameraSelfie").click()');await pause(700);
  await check('selfie selected','document.querySelector("#cameraSelfie").getAttribute("aria-pressed")==="true" && qaCommands.includes("camera.mode:selfie")');
  await evaluate('document.querySelector("#cameraForward").click()');await pause(700);
  await check('forward restored','document.querySelector("#cameraForward").getAttribute("aria-pressed")==="true"');
  await evaluate('document.querySelector("#cameraShutter").click()');await pause(700);
  await check('saved filename shown safely as text','document.querySelector("#cameraStatus").textContent.includes("muxi-20261001")');
  await evaluate('document.querySelector("#cameraAlbum").click()');await pause(500);
  await check('album stops capture and drops preview pixels','!qaState.active && !document.querySelector("#cameraPreview").hasAttribute("src") && document.querySelectorAll("#cameraPhotoList button").length===1');
  await evaluate('document.querySelector("#cameraPhotoList button").click()');await pause(200);
  await check('local photo viewer','!document.querySelector("#cameraPhoto").hidden && document.querySelector("#cameraPhoto").src.startsWith("data:image/png;")');
  await screenshot('02-album-synthetic');
  await evaluate('document.querySelector("#cameraReturn").click()');await pause(700);
  await check('return releases viewed image and resumes camera','qaState.active && !document.querySelector("#cameraPhoto").hasAttribute("src")');
  await evaluate('window.dispatchEvent(new KeyboardEvent("keydown",{key:"ArrowRight",bubbles:true}))');
  await check('inherits shell keyboard selection','document.querySelector("#camera button.keyboard-selected")===document.activeElement');
  const beforeSpace=await evaluate('qaCommands.filter(x=>x==="camera.shutter").length');
  await cdp('Input.dispatchKeyEvent',{type:'keyDown',key:' ',code:'Space',windowsVirtualKeyCode:32});
  await cdp('Input.dispatchKeyEvent',{type:'keyUp',key:' ',code:'Space',windowsVirtualKeyCode:32});await pause(250);
  tests.push({name:'space shutter from focused control without duplicate click',passed:await evaluate('qaCommands.filter(x=>x==="camera.shutter").length')===beforeSpace+1});
  await cdp('Emulation.setDeviceMetricsOverride',{width:420,height:500,deviceScaleFactor:1,mobile:false});await pause(200);
  await check('small viewport has no horizontal overflow','document.querySelector("#camera").scrollWidth<=document.querySelector("#camera").clientWidth');
  await screenshot('03-small-synthetic');
  await evaluate('location.hash="/home"');await pause(500);
  await check('route exit stops preview and clears pixels','!qaState.active && !document.querySelector("#cameraPreview").hasAttribute("src")');
  tests.push({name:'no browser exceptions',passed:errors.length===0,errors});
  const report={synthetic:true,passed:tests.filter(t=>t.passed).length,total:tests.length,tests};
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
  process.exitCode=report.passed===report.total?0:1;
  await cdp('Browser.close');
} finally {if(socket && socket.readyState===WebSocket.OPEN)socket.close();if(child.exitCode===null)child.kill();}

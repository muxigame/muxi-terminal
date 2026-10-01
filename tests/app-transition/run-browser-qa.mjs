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
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(pathToFileURL(path.join(directory, 'demo.html')).href)}`, {method: 'PUT'})).json();
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
  await cdp('Emulation.setDeviceMetricsOverride', {width: 1280, height: 800, deviceScaleFactor: 1, mobile: false});
  for (let i = 0; i < 80; i++) { if (await evaluate('typeof window.demoLaunch === "function"')) break; await pause(50); }
  const baseline = await evaluate('({url: location.href, bar: document.querySelector(".statusbar").getBoundingClientRect().toJSON()})');
  await screenshot('01-home');
  await evaluate('demoLaunch("guide", "success")'); await pause(70); await screenshot('02-opening-from-icon');
  await pause(180); await screenshot('03-loading');
  await pause(500); await screenshot('04-app-content');
  await evaluate('demoTransition.close("home")');
  // Also capture the reverse animation while its promise is still running.
  await evaluate('demoLaunch("custom", "hold")'); await pause(210);
  await evaluate('void demoTransition.close("home")'); await pause(55); await screenshot('05-return-to-icon');
  await pause(150);
  await evaluate('demoLaunch("custom", "error")'); await pause(340); await screenshot('06-error-recovery');
  await evaluate('demoHome(); demoLaunch("tasks", "hold")'); await pause(2100); await screenshot('07-timeout-recovery');
  await evaluate('demoHome()');
  const tests = await evaluate('runTransitionTests()');
  // System preference changes are tested through Chrome media emulation.
  await evaluate(`window.motionHost = document.createElement('div'); motionHost.style.cssText='width:300px;height:320px'; document.body.appendChild(motionHost); window.motionTransition=MuxiAppTransition.create({host:motionHost}); window.motionRequest=motionTransition.begin({id:'motion'}); motionRequest.ready();`);
  await cdp('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'reduce'}]});
  await pause(40);
  const changed = await evaluate('motionTransition.getState().state === "ready" && motionHost.querySelector(".mt-launch-layer").hidden');
  tests.results.push({name: 'live system reduce-motion preference completes active reveal', passed: changed});
  await cdp('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'no-preference'}]}); await pause(40);
  await evaluate('motionTransition.begin({id:"closing-motion"}); window.motionClose = motionTransition.close();');
  await cdp('Emulation.setEmulatedMedia', {features: [{name: 'prefers-reduced-motion', value: 'reduce'}]}); await pause(40);
  const closed = await evaluate('motionClose.then(value => value && motionTransition.getState().state === "idle")');
  tests.results.push({name: 'live reduce-motion change resolves active return promise', passed: closed});
  await evaluate('motionTransition.destroy(); motionHost.remove();');
  await cdp('Emulation.setDeviceMetricsOverride', {width: 400, height: 850, deviceScaleFactor: 1, mobile: true});
  await evaluate('document.getElementById("scenario-reduced").click(); demoLaunch("custom","hold");'); await pause(60);
  await screenshot('08-small-screen-reduced-motion');
  const small = await evaluate('(() => {const r=document.querySelector(".mt-launch-card").getBoundingClientRect(); const h=document.getElementById("viewport").getBoundingClientRect(); return r.left>=h.left && r.right<=h.right && r.top>=h.top && r.bottom<=h.bottom;})()');
  tests.results.push({name: 'small viewport keeps transition card inside content', passed: small});
  await cdp('Emulation.setDeviceMetricsOverride', {width: 1280, height: 800, deviceScaleFactor: 1, mobile: false});
  const after = await evaluate('({url: location.href, bar: document.querySelector(".statusbar").getBoundingClientRect().toJSON()})');
  tests.results.push({name: 'all app scenarios keep outer statusbar geometry and page URL', passed: JSON.stringify(baseline) === JSON.stringify(after)});
  tests.results.push({name: 'browser scenarios produce no runtime exceptions', passed: errors.length === 0, errors});
  tests.passed = tests.results.filter(test => test.passed).length; tests.total = tests.results.length;
  fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(tests, null, 2));
  console.log(`${tests.passed}/${tests.total} checks passed. Evidence: ${output}`);
  for (const test of tests.results.filter(test => !test.passed)) console.error(JSON.stringify(test));
  process.exitCode = tests.passed === tests.total ? 0 : 1;
  await cdp('Browser.close');
} finally {
  if (socket && socket.readyState === WebSocket.OPEN) socket.close();
  if (child.exitCode === null) child.kill();
}

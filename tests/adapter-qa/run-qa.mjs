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
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(pathToFileURL(path.join(directory, 'harness.html')).href + '?baseline=1')}`, {method: 'PUT'})).json();
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
      const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}; ${stderr}`)); }, 30000);
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
  await cdp('Emulation.setDeviceMetricsOverride', {width:1280,height:800,deviceScaleFactor:1,mobile:false});
  for(let i=0;i<80;i++){if(await evaluate('typeof adapterProbe === "function"'))break;await pause(50);}
  const baseline=await evaluate('adapterProbe()');
  await screenshot('01-baseline-rapid-click-failure');
  fs.writeFileSync(path.join(output,'baseline-race.json'),JSON.stringify(baseline,null,2));
  await cdp('Page.navigate',{url:pathToFileURL(path.join(directory,'harness.html')).href});await pause(150);
  for(let i=0;i<80;i++){if(await evaluate('!location.search && typeof runAdapterTests === "function"'))break;await pause(50);}
  const initial=await evaluate('({url:location.href,bar:document.querySelector(".statusbar").getBoundingClientRect().toJSON()})');
  const tests=await evaluate('runAdapterTests()');
  await screenshot('02-fixed-home');
  await evaluate('bridge.mode("hold"); void bridge.launch("guide").catch(()=>{});');await pause(90);await screenshot('03-fixed-opening');
  await pause(200);await screenshot('04-fixed-loading');
  await evaluate('bridge.emit({loading:false,rendered:true})');await pause(240);await screenshot('05-fixed-child-view');
  await evaluate('void terminalReturnHome()');await pause(60);await screenshot('06-fixed-return');await pause(180);
  await evaluate('bridge.mode("error"); void bridge.launch("web","web").catch(()=>{});');await pause(350);await screenshot('07-fixed-error');
  await evaluate('terminalTransition.cancel("reset")');await pause(150);
  await cdp('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await pause(40);
  await evaluate('bridge.mode("hold"); void bridge.launch("guide").catch(()=>{});');await pause(90);
  const noAnimation=await evaluate('getComputedStyle(document.querySelector(".mt-launch-layer")).animationName === "none"');
  await evaluate('bridge.emit({loading:false,rendered:true})');await pause(80);
  const immediate=await evaluate('terminalTransition.getState().state === "ready" && bridge.visible');
  await screenshot('08-reduced-motion-child-view');
  const returned=await evaluate('terminalReturnHome().then(done=>done && terminalTransition.getState().state==="idle")');await pause(100);
  tests.results.push({name:'integrated system reduced-motion removes entry and return delays',passed:noAnimation&&immediate&&returned});
  const after=await evaluate('({url:location.href,bar:document.querySelector(".statusbar").getBoundingClientRect().toJSON()})');
  tests.results.push({name:'all integrated scenarios retain shell geometry and URL',passed:JSON.stringify(initial)===JSON.stringify(after)});
  tests.results.push({name:'no integrated browser runtime exceptions',passed:errors.length===0,errors});
  tests.passed=tests.results.filter(test=>test.passed).length;tests.total=tests.results.length;
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(tests,null,2));
  console.log(`Baseline latest launch matched: ${baseline.rapid.matched}; dropped controls: ${baseline.rapid.drops}`);
  console.log(`${tests.passed}/${tests.total} adapter checks passed. Evidence: ${output}`);
  for(const test of tests.results.filter(test=>!test.passed))console.error(JSON.stringify(test));
  process.exitCode=tests.passed===tests.total && !baseline.rapid.matched ? 0 : 1;
  await cdp('Browser.close');
} finally {
  if(socket && socket.readyState === WebSocket.OPEN)socket.close();
  if(child.exitCode===null)child.kill();
}

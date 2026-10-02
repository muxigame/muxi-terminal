const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const assets=path.join(__dirname,'../../src/main/resources/assets/muxi_terminal/html/terminal');
let calls=[],launches=0,status='';
function element(){return {listeners:{},children:[],addEventListener(k,fn){this.listeners[k]=fn;},append(x){this.children.push(x);},querySelector(s){return this.nodes[s]??=(element());},nodes:{}};}
const host=element(),grid=element(),location={hash:'#/music'};
const context={document:{querySelector:s=>s==='#app-content'?host:s==='#home .app-grid'?grid:null,createElement:()=>element()},location,native:async s=>{calls.push(s);return {ok:true,native:true};},setStatus:s=>status=s,navigate:()=>{},window:{terminalLaunch:()=>{launches++;}},console};
vm.runInNewContext(fs.readFileSync(path.join(assets,'camera-app.js'),'utf8'),context);
assert.equal(grid.children.length,1);assert.equal(grid.children[0].className,'app-card');assert.equal(grid.children[0].id,'cameraApp');
assert.ok(grid.children[0].innerHTML.includes('camera-icon'));assert.ok(grid.children[0].innerHTML.includes('相机'));
grid.children[0].listeners.click();assert.deepEqual(calls,['camera.open']);assert.equal(launches,0);assert.equal(location.hash,'#/music');
assert.equal(host.children.length,1);assert.ok(!host.children[0].innerHTML.includes('<img'));assert.ok(!host.children[0].innerHTML.includes('cameraPreview'));
host.children[0].nodes['#cameraNativeOpen'].listeners.click();assert.equal(calls.at(-1),'camera.open');assert.equal(location.hash,'#/music');
const source=fs.readFileSync(path.join(assets,'app.js'),'utf8');
const fn=source.slice(source.indexOf('function navigate(id,source){'),source.indexOf("document.querySelectorAll('[data-open]')"));
context.show=()=>{throw Error('camera must not show a web page');};context.contentView=true;context.window.muxiTerminalQuery=()=>{};
vm.runInNewContext(fn,context);context.navigate('camera',grid.children[0]);assert.equal(calls.at(-1),'camera.open');assert.equal(location.hash,'#/music');assert.equal(launches,0);
console.log(JSON.stringify({success:true,checks:16,scope:'native entry, shell URL preserved, no web preview/launch transition; simulated DOM'}));

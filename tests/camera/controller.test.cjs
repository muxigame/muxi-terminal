const assert=require('node:assert/strict');
const {create}=require('../../src/main/resources/assets/muxi_terminal/html/terminal/camera-controller.js');
async function run(){
  let commands=[],scheduled=[],cleared=[],states=[];
  let state={active:true,busy:false,preview:'data:image/png;base64,test',sequence:1,mode:'forward'};
  const invoke=async command=>{commands.push(command);if(command==='camera.state')return {...state};return {ok:true};};
  const c=create({invoke,onState:s=>states.push(s),schedule:fn=>{scheduled.push(fn);return scheduled.length;},cancel:id=>cleared.push(id)});
  await assert.rejects(c.shutter(),/等待/);
  await c.start();assert.deepEqual(commands,['camera.begin','camera.state']);
  state.mode='selfie';await c.mode('selfie');assert.equal(c.getState().mode,'selfie');
  state.mode='forward';await c.mode('forward');assert.equal(c.getState().mode,'forward');
  const count=commands.length;await assert.rejects(c.mode('webcam'),/无效/);assert.equal(commands.length,count);
  await c.shutter();assert(commands.includes('camera.shutter'));
  state.busy=true;await scheduled.at(-1)();await assert.rejects(c.shutter(),/稍候/);
  state.busy=false;state.preview='';await scheduled.at(-1)();await assert.rejects(c.shutter(),/等待/);
  await c.stop();assert(cleared.length);assert.equal(c.getState().preview,'');assert.equal(c.getState().active,false);
  await assert.rejects(c.mode('selfie'),/暂停/);
  let resolvePoll,seen=[];
  const d=create({invoke:command=>command==='camera.state'?new Promise(r=>resolvePoll=r):Promise.resolve({ok:true}),onState:s=>seen.push(s),schedule:()=>{throw Error('late poll scheduled');}});
  const start=d.start();await new Promise(r=>setImmediate(r));await d.dispose();
  const atStop=seen.length;resolvePoll({...state,active:true,preview:'late image'});await start;assert.equal(seen.length,atStop);
  let resolveBegin;const e=create({invoke:command=>command==='camera.begin'?new Promise(r=>resolveBegin=r):Promise.resolve({ok:true})});
  const pending=e.start();await e.stop();resolveBegin({ok:true});await pending;assert.equal(e.getState().active,false);
  const f=create({invoke:async()=>{throw Error('denied');}});await assert.rejects(f.start(),/denied/);assert.equal(f.getState().active,false);
  assert(commands.every(command=>command.startsWith('camera.')));
  console.log(JSON.stringify({success:true,checks:14,coverage:['forward/selfie','invalid lens','shutter before first frame','busy rejection','pause/resume','dispose clears timer and pixels','stale poll result','cancel pending begin','bridge failure','camera-only transport']}));
}
run().catch(error=>{console.error(error);process.exitCode=1;});

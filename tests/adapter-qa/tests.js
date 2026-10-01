window.adapterProbe=async function(){
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  bridge.mode('hold');bridge.launch('guide').catch(()=>{});bridge.launch('tasks').catch(()=>{});
  await sleep(700);
  const rapid={expected:'tasks',actual:bridge.state?.kind==='BUILTIN'?bridge.state?.launchToken:null,drops:bridge.drops,latest:terminalTransition.getState(),matched:String(terminalTransition.getState().token)===bridge.state?.launchToken};
  return {rapid};
};
window.runAdapterTests=async function(){
  const results=[];const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const assert=(value,message)=>{if(!value)throw Error(message);};
  async function until(test,ms=2500){const end=Date.now()+ms;while(Date.now()<end){if(test())return;await sleep(20);}throw Error('Timed out waiting for adapter state');}
  async function reset(){terminalTransition.cancel('test-reset');await sleep(180);bridge.reset();}
  async function test(name,run){await reset();try{await run();results.push({name,passed:true});}catch(error){results.push({name,passed:false,error:String(error.stack||error)});}}
  await test('fast alternate opens survive native generation invalidation',async()=>{
    bridge.mode('hold');for(let i=0;i<20;i++)bridge.launch(i%2?'tasks':'guide').catch(()=>{});
    await until(()=>String(terminalTransition.getState().token)===bridge.state?.launchToken);
    assert(bridge.drops===0,'current commands were discarded');
    assert(bridge.calls.filter(call=>call.command.startsWith('terminal.launch:')).length===1,'obsolete launches were dispatched');
  });
  await test('duplicate same-app clicks share transport promise',async()=>{
    bridge.mode('hold');const a=bridge.launch('web','web');const b=bridge.launch('web','web');assert(a===b,'transport duplicated');await a;
    assert(bridge.calls.filter(call=>call.command.startsWith('terminal.launch:')).length===1,'duplicate native launch');
  });
  await test('native child stays hidden until completed load and actual paint',async()=>{
    bridge.mode('hold');await bridge.launch('guide');bridge.emit({loading:false,rendered:false});await sleep(100);assert(!bridge.visible,'revealed before paint');
    bridge.emit({rendered:true});await until(()=>bridge.visible);assert(['revealing','ready'].includes(terminalTransition.getState().state),'visual state not ready');
  });
  await test('older same-token generation cannot reveal a newer loading page',async()=>{
    bridge.mode('hold');await bridge.launch('guide');const state=bridge.state;
    terminalOnViewState({...state,generation:state.generation+2,loading:true,rendered:false});
    terminalOnViewState({...state,generation:state.generation+1,loading:false,rendered:true});await sleep(240);
    assert(!bridge.visible && terminalTransition.getState().state==='loading','stale generation revealed child');
  });
  await test('failure holds recovery overlay and retry opens a fresh token',async()=>{
    bridge.mode('error');await bridge.launch('web','web');await until(()=>terminalTransition.getState().state==='error');
    assert(!bridge.visible && !$('.mt-launch-retry').hidden,'failure recovery unavailable');
    const token=terminalTransition.getState().token;bridge.mode('success');$('.mt-launch-retry').click();$('.mt-launch-retry').click();await until(()=>bridge.visible);
    assert(terminalTransition.getState().token>token,'retry reused old request');
    assert(bridge.calls.filter(call=>call.command.startsWith('terminal.launch:')).length===2,'retry double-click duplicated');
  });
  await test('return during loading closes native child and blocks late state',async()=>{
    bridge.mode('hold');await bridge.launch('guide');const old=bridge.state;assert(await terminalReturnHome(),'return rejected');await sleep(100);
    terminalOnViewState({...old,loading:false,rendered:true});await sleep(80);
    assert(!bridge.visible && terminalTransition.getState().state==='idle','late result reopened app');
  });
  await test('new open during return remains the current native child',async()=>{
    await bridge.launch('guide');await until(()=>bridge.visible);const closing=terminalReturnHome();const next=bridge.launch('tasks');
    assert(!(await closing),'superseded return succeeded');await next;await until(()=>bridge.visible);
    assert(String(terminalTransition.getState().token)===bridge.state.launchToken,'latest launch cancelled');
  });
  await test('native home immediately invalidates pending JS launch',async()=>{
    bridge.mode('hold');await bridge.launch('guide');const old=bridge.state;terminalContainerCancel();await sleep(100);terminalOnViewState({...old,loading:false,rendered:true});
    assert(terminalTransition.getState().state==='idle' && !bridge.visible,'native home revived pending launch');
  });
  await test('rejected launch offers error recovery without replacing shell',async()=>{
    const shell=$('.shell');try{await bridge.launch('invalid');}catch(error){}assert(terminalTransition.getState().state==='error','native rejection not surfaced');assert(shell===$('.shell'),'shell replaced');
  });
  await test('discarded native callback times out and retry queue recovers',async()=>{
    bridge.dropNext=true;const opening=bridge.launch('guide');try{await opening;}catch(error){}assert(terminalTransition.getState().state==='error','discarded callback never recovered');
    $('.mt-launch-retry').click();await until(()=>bridge.visible);assert(bridge.drops===1,'wrong drop count');
  });
  await reset();
  return {passed:results.filter(item=>item.passed).length,total:results.length,results};
};

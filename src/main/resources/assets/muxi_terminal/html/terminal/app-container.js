/* Trusted shell adapter. Native client owns navigation, credentials and actual frame authorization. */
(() => {
  if(contentView)return;
  let active=null;
  let returningToken=null;
  const reducedMotion=()=>!!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const homeCommand=()=>reducedMotion()?'terminal.home:reduced':'terminal.home';
  // The native bridge intentionally discards calls captured before a content
  // generation change. Serialize shell controls so a cancelled older launch
  // cannot invalidate the newest launch captured in the same renderer tick.
  let controls=Promise.resolve();
  function control(command,current){
    const operation=controls.then(()=>{
      if(current && !current())return {skipped:true};
      let timer;
      const deadline=new Promise((resolve,reject)=>{timer=setTimeout(()=>reject(new Error('终端操作未完成，请重试或返回主页。')),4000);});
      return Promise.race([native(command),deadline]).finally(()=>clearTimeout(timer));
    });
    // A deliberately discarded native callback must not strand later controls.
    controls=operation.catch(()=>{});
    return operation;
  }
  function reveal(visible){
    if(!active?.viewId)return;
    native('terminal.reveal:'+JSON.stringify({viewId:active.viewId,token:String(active.handle.token),visible,reducedMotion:reducedMotion()})).catch(()=>{});
  }
  const transition=MuxiAppTransition.create({
    host:$('#app-content'),timeoutMs:10000,
    onStateChange({state}){if(state!=='closing')reveal(state==='revealing'||state==='ready');},
    onCancel({token}){control('terminal.cancel-launch:'+token).catch(()=>{});},
    onTimeout({token}){control('terminal.cancel-launch:'+token).catch(()=>{});},
    onCloseStart({token}){
      control(homeCommand(),()=>{
        const current=transition.getState();
        if(current.token!==token || current.state!=='closing')return false;
        returningToken=token;return true;
      }).catch(error=>{if(returningToken===token)returningToken=null;setStatus(error.message);});
    },
    onRetry(){if(active)window.terminalLaunch(active.app).catch(error=>setStatus(error.message));}
  });
  window.terminalLaunch=app=>{
    const handle=transition.begin({id:app.id,name:app.name,source:app.source,icon:app.source?.querySelector('.app-icon'),navigationKey:app.kind+':'+app.id});
    if(active?.handle.token===handle.token)return active.promise;
    active={app,handle,viewId:0,generation:-1,promise:null};
    const request=active;
    request.promise=control('terminal.launch:'+JSON.stringify({kind:app.kind,id:app.id,token:String(handle.token)}),
      ()=>handle.isCurrent() && !['error','closing'].includes(transition.getState().state))
      .catch(error=>{if(handle.isCurrent())handle.fail(error.message);throw error;});
    return request.promise;
  };
  window.terminalOnViewState=state=>{
    if(!active || String(active.handle.token)!==state.launchToken || !active.handle.isCurrent())return;
    if(state.generation<active.generation)return;
    active.generation=state.generation;
    active.viewId=state.viewId;
    if(state.error)active.handle.fail(state.error);
    else if(!state.loading && state.rendered && state.viewId)active.handle.ready();
  };
  window.terminalReturnHome=()=>{
    if(!active){return native(homeCommand());}
    return transition.close('home').then(complete=>{if(complete)active=null;return complete;});
  };
  window.terminalContainerCancel=()=>{
    // The acknowledgement of an older animated home can arrive after a new
    // click; it must not cancel that new intent. Native home still cancels it.
    const completedReturn=returningToken;returningToken=null;
    if(completedReturn!==null && active?.handle.token!==completedReturn)return;
    if(transition.getState().state==='closing')return;
    transition.cancel('native-home');active=null;
  };
  window.addEventListener('pagehide',()=>{transition.destroy();active=null;});
  window.terminalTransition=transition;
})();

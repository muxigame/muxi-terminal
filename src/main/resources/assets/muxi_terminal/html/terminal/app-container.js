/* Trusted shell adapter. Native client owns navigation, credentials and actual frame authorization. */
(() => {
  if(contentView)return;
  let active=null;
  function reveal(visible){
    if(!active?.viewId)return;
    native('terminal.reveal:'+JSON.stringify({viewId:active.viewId,token:String(active.handle.token),visible})).catch(()=>{});
  }
  const transition=MuxiAppTransition.create({
    host:$('#app-content'),timeoutMs:10000,
    onStateChange({state}){reveal(state==='revealing'||state==='ready');},
    onCancel({token}){native('terminal.cancel-launch:'+token).catch(()=>{});},
    onTimeout({token}){native('terminal.cancel-launch:'+token).catch(()=>{});},
    onCloseStart(){native('terminal.home').catch(error=>setStatus(error.message));},
    onRetry(){if(active)window.terminalLaunch(active.app).catch(error=>setStatus(error.message));}
  });
  window.terminalLaunch=app=>{
    const handle=transition.begin({id:app.id,name:app.name,source:app.source,icon:app.source?.querySelector('.app-icon'),navigationKey:app.kind+':'+app.id});
    if(active?.handle.token===handle.token)return active.promise;
    active={app,handle,viewId:0,promise:null};
    const request=active;
    request.promise=native('terminal.launch:'+JSON.stringify({kind:app.kind,id:app.id,token:String(handle.token)}))
      .catch(error=>{if(handle.isCurrent())handle.fail(error.message);throw error;});
    return request.promise;
  };
  window.terminalOnViewState=state=>{
    if(!active || String(active.handle.token)!==state.launchToken || !active.handle.isCurrent())return;
    active.viewId=state.viewId;
    if(state.error)active.handle.fail(state.error);
    else if(!state.loading && state.rendered && state.viewId)active.handle.ready();
  };
  window.terminalReturnHome=()=>{
    if(!active){return native('terminal.home');}
    return transition.close('home').then(complete=>{if(complete)active=null;});
  };
  window.terminalContainerCancel=()=>{
    if(transition.getState().state==='closing')return;
    transition.cancel('native-home');active=null;
  };
  window.addEventListener('pagehide',()=>{transition.destroy();active=null;});
  window.terminalTransition=transition;
})();

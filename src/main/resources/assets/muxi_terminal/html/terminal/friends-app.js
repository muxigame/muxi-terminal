(() => {
  const host=document.querySelector('#app-content'),grid=document.querySelector('#home .app-grid');if(!host || !grid)return;
  const card=document.createElement('button');card.id='friendsApp';card.className='app-card';card.innerHTML='<span class="app-icon friends-icon"><i></i></span><span class="app-name">平台好友</span><span class="app-desc">好友申请 · 原生私信</span>';grid.append(card);
  card.addEventListener('click',()=>navigate('friends',card));
  const page=document.createElement('section');page.id='friends';page.className='page';
  page.innerHTML=`<div id="friendsBody"><div class="toolbar"><button id="friendsHome" class="back" aria-label="返回主页">‹</button><div><div class="eyebrow">APP / FRIENDS</div><h2>平台好友</h2></div><button id="friendsRefresh" class="secondary">刷新</button></div>
    <p class="friends-note">好友按平台 UID 保存，与临时小游戏队伍和领地队伍分开。</p><div class="task-tabs friends-actions"><button data-friends-tab="friends" class="task-tab task-tab-active" aria-pressed="true">我的好友</button><button data-friends-tab="requests" class="task-tab" aria-pressed="false">好友申请</button><button data-friends-tab="add" class="task-tab" aria-pressed="false">添加好友</button><button data-friends-tab="blocked" class="task-tab" aria-pressed="false">已屏蔽</button></div>
    <p id="friendsStatus" role="status" aria-live="polite">正在读取好友…</p><p id="friendsPresence" class="friends-presence"></p>
    <div class="friends-actions"><button id="friendsLogin" class="secondary">打开玩家中心登录</button><button id="friendsRetry" class="secondary" hidden>重试未确认操作</button><button id="friendsAbandon" class="secondary" hidden>放弃本次重试</button></div>
    <label id="friendsSearchLabel">筛选 UID / 昵称<input id="friendsSearch" type="search" maxlength="80" autocomplete="off"></label>
    <form id="friendsAddForm" class="detail-card" hidden><label for="friendsUid">对方平台 UID</label><input id="friendsUid" type="text" inputmode="numeric" maxlength="16" pattern="[1-9][0-9]{4,15}" autocomplete="off" placeholder="5–16 位 UID" required><p>昵称仅用于显示；对方接受申请后才成为好友。</p><div class="detail-actions"><button id="friendsRequest" type="submit" class="primary">发送好友申请</button></div></form><div id="friendsList"></div></div>
    <div id="friendsConfirm" hidden><div class="detail-card" role="dialog" aria-modal="true" aria-labelledby="friendsConfirmTitle"><h3 id="friendsConfirmTitle"></h3><p id="friendsConfirmText"></p><div class="detail-actions"><button id="friendsCancel" class="secondary">取消</button><button id="friendsConfirmOk" class="primary">确认</button></div></div></div>`;
  host.append(page);const el=id=>document.getElementById(id),active=()=>contentView && location.hash==='#/friends';
  let disposed=false,tab='friends',view={},signature='',confirmation=null,backgroundButtons=null,returnFocus=null;
  async function invoke(command){let timer;try{return await Promise.race([native(command),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('好友响应超时，结果未确认')),15000);})]);}finally{clearTimeout(timer);}}
  function run(work){work().catch(error=>{if(active() && !disposed)el('friendsStatus').textContent=controller.getState().error || error.message;});}
  function canWrite(){return !!view.snapshot && !view.busy && !view.needsRefresh && !view.retry;}
  function row(peer,buttons){const name=esc(peer.displayName),id=esc(peer.uid);return `<article class="detail-card friends-row"><div><h3>${name}</h3><p class="friends-uid">UID ${id} · <span class="${peer.online?'friends-online':'friends-offline'}">${peer.online?'在线':'离线'}</span></p></div><div class="detail-actions">${buttons.map(([op,label,enabled=true])=>`<button class="secondary" data-friends-op="${op}" data-friends-uid="${id}" ${!canWrite() || !enabled?'disabled':''}>${label}</button>`).join('')}</div></article>`;}
  function render(){
    if(disposed)return;const snapshot=view.snapshot,query=el('friendsSearch').value.trim().toLowerCase();
    el('friendsStatus').textContent=view.error || (view.busy?'正在读取 / 核实操作…':snapshot?'当前平台 UID '+snapshot.selfUid+(view.lastReceipt?' · 操作回执已确认':''):'请先登录玩家中心，再刷新好友');
    el('friendsPresence').textContent=snapshot?.presenceAvailable?'在线状态来自服务器；离线好友不能接收临时邀请。':'在线状态暂不可用，临时邀请和快捷私信已停用。';
    el('friendsRetry').hidden=el('friendsAbandon').hidden=!view.retry;el('friendsRetry').disabled=view.busy || view.needsRefresh;el('friendsAbandon').disabled=view.busy;
    el('friendsRequest').disabled=!canWrite();el('friendsRefresh').disabled=view.busy;el('friendsLogin').disabled=view.busy;
    el('friendsAddForm').hidden=tab!=='add';el('friendsSearchLabel').hidden=tab==='add';el('friendsList').hidden=tab==='add';
    for(const button of page.querySelectorAll('[data-friends-tab]')){const selected=button.dataset.friendsTab===tab;button.setAttribute('aria-pressed',String(selected));button.classList.toggle('task-tab-active',selected);}
    const next=JSON.stringify([snapshot,tab,query,canWrite()]);if(next===signature)return;signature=next;
    const filter=rows=>rows.filter(peer=>peer.uid.includes(query) || peer.displayName.toLowerCase().includes(query));
    let html='';if(snapshot){
      if(tab==='friends')html=filter(snapshot.friends).map(peer=>row(peer,[['message','私信',snapshot.presenceAvailable && peer.online && peer.messageAvailable===true],['remove','移除好友'],['block','屏蔽']])).join('');
      if(tab==='requests')html='<div class="section-title">收到的申请</div>'+filter(snapshot.incoming).map(peer=>row(peer,[['accept','接受'],['cancel','拒绝']])).join('')+'<div class="section-title">已发送的申请</div>'+filter(snapshot.outgoing).map(peer=>row(peer,[['cancel','撤回申请']])).join('');
      if(tab==='blocked')html=filter(snapshot.blocked).map(peer=>row(peer,[['unblock','解除屏蔽']])).join('');
    }
    el('friendsList').innerHTML=html || '<p class="friends-note">此列表暂无玩家。</p>';
  }
  const controller=MuxiFriendsController.create({invoke,onChange:state=>{view=state;render();}});
  function confirm(op,uid,button){
    confirmation={op,uid};returnFocus=button;backgroundButtons=[...el('friendsBody').querySelectorAll('button,input,select,textarea')].map(button=>[button,button.disabled]);backgroundButtons.forEach(([button])=>button.disabled=true);
    el('friendsConfirmTitle').textContent=op==='block'?'屏蔽这个平台 UID？':'移除这个好友？';el('friendsConfirmText').textContent='UID '+uid+(op==='block'?'：将同时移除好友和双方申请；解除屏蔽不会自动恢复好友。':'：将解除双方的好友关系，不改变临时队伍或领地。');
    el('friendsConfirm').hidden=false;page.scrollTop=0;el('friendsCancel').focus({preventScroll:true});
  }
  function cancel(){confirmation=null;el('friendsConfirm').hidden=true;backgroundButtons?.forEach(([button,disabled])=>button.disabled=disabled);backgroundButtons=null;const focus=returnFocus;returnFocus=null;if(active() && focus?.isConnected)focus.focus({preventScroll:true});}
  page.addEventListener('click',event=>{
    const select=event.target.closest('[data-friends-tab]');if(select && !confirmation){tab=select.dataset.friendsTab;render();return;}
    const button=event.target.closest('[data-friends-op]');if(!button || button.disabled)return;const {friendsOp:op,friendsUid:uid}=button.dataset;
    if(op==='remove' || op==='block')confirm(op,uid,button);else if(op==='message')run(()=>invoke('friends.message:'+uid));else run(()=>controller.mutate(op,uid));
  });
  el('friendsSearch').addEventListener('input',render);el('friendsAddForm').addEventListener('submit',event=>{event.preventDefault();run(()=>controller.mutate('request',el('friendsUid').value.trim()));});
  el('friendsRefresh').addEventListener('click',()=>run(()=>controller.refresh()));el('friendsLogin').addEventListener('click',()=>run(()=>native('terminal.external:https://mc.muxigame.com/account.html')));
  el('friendsHome').addEventListener('click',()=>run(()=>native('terminal.home')));el('friendsRetry').addEventListener('click',()=>run(()=>controller.retry()));el('friendsAbandon').addEventListener('click',()=>controller.abandon());
  el('friendsCancel').addEventListener('click',cancel);el('friendsConfirmOk').addEventListener('click',()=>{const selected=confirmation;cancel();if(selected)run(()=>controller.mutate(selected.op,selected.uid));});
  el('friendsConfirm').addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();cancel();}});
  function sync(){cancel();if(active()){show('friends');run(()=>controller.refresh());}else controller.dispose();}
  window.addEventListener('hashchange',sync);window.addEventListener('focus',()=>{if(active() && !confirmation && !view.retry)run(()=>controller.refresh());});
  const interval=setInterval(()=>{if(active() && !confirmation && !view.retry)run(()=>controller.refresh());},30000);
  window.addEventListener('pagehide',()=>{disposed=true;controller.dispose();clearInterval(interval);});
  window.MuxiFriendsApp={refresh:()=>controller.refresh(),state:()=>controller.getState()};if(active())sync();
})();

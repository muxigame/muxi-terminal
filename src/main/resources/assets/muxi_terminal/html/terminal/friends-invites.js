(() => {
  'use strict';
  const root=document.getElementById('games'),mount=document.getElementById('mg-room-invitations');if(!root||!mount)return;
  const panel=document.createElement('section');panel.className='detail-card friends-invite-panel';panel.id='friendsInvitePanel';
  panel.innerHTML='<h3>\u9080\u8bf7\u961f\u53cb</h3><div class="task-tabs detail-actions"><button id="friendsInviteOnline" class="task-tab">\u670d\u52a1\u5668\u5728\u7ebf\u73a9\u5bb6</button><button id="friendsInviteFriends" class="task-tab">\u9080\u8bf7\u597d\u53cb</button><button id="friendsInviteRefresh" class="secondary">\u5237\u65b0</button></div><p id="friendsInviteStatus" class="friends-invite-note"></p><ul id="friendsInviteRows"></ul><h4>\u672c\u623f\u95f4\u9080\u8bf7</h4><ul id="friendsInviteTickets"></ul>';
  mount.append(panel);
  const el=id=>document.getElementById(id),inApp=()=>contentView&&location.hash==='#/games';
  let source='online',controller=null,scope={visible:false,game:'',session:''},poll;
  const active=()=>inApp()&&scope.visible;
  const invoke=request=>new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('\u54cd\u5e94\u8d85\u65f6\uff0c\u8bf7\u5237\u65b0\u6838\u5b9e')),15000);native(request).then(resolve,reject).finally(()=>clearTimeout(timer));});
  function render(state){
    if(!active())return;
    const runtime=state.runtime,available=MuxiFriendsInvites.enabled(runtime);
    const room=available?runtime.rooms.find(row=>row.game===scope.game&&row.session===scope.session&&row.mine):null;
    const inviteable=room?.inviteable===true;
    for(const [id,mode]of [['friendsInviteOnline','online'],['friendsInviteFriends','friends']]){el(id).setAttribute('aria-pressed',String(source===mode));el(id).classList.toggle('task-tab-active',source===mode);el(id).disabled=state.busy;}
    el('friendsInviteRefresh').disabled=state.busy;
    el('friendsInviteStatus').textContent=state.error||(state.busy?'\u5df2\u63d0\u4ea4\uff0c\u6b63\u5728\u7b49\u5f85\u670d\u52a1\u5668\u6700\u7ec8\u786e\u8ba4\u3002':!available?'\u5f53\u524d\u8fde\u63a5\u672a\u63d0\u4f9b\u53ef\u7528\u7684\u597d\u53cb\u9080\u8bf7\u670d\u52a1\u3002\u82e5\u73a9\u6cd5\u63d0\u4f9b\u5728\u7ebf\u9080\u8bf7\uff0c\u53ef\u4f7f\u7528\u4e0a\u65b9\u623f\u95f4\u64cd\u4f5c\u3002':!inviteable?'\u4ec5\u7b49\u5f85\u4e2d\u7684\u623f\u4e3b\u53ef\u9080\u8bf7\u961f\u53cb\u3002':'\u9009\u62e9\u961f\u53cb\u53d1\u9001\u672c\u623f\u95f4\u9080\u8bf7\uff0c\u5bf9\u65b9\u63a5\u53d7\u540e\u52a0\u5165\u3002');
    const peers=available?MuxiFriendsInvites.peers(runtime,state.business,source):[];
    el('friendsInviteRows').innerHTML=peers.map(peer=>`<li><span>${esc(peer.displayName)}</span><button class="secondary" data-invite-uid="${esc(peer.uid)}" ${state.busy||!inviteable?'disabled':''}>\u9080\u8bf7</button></li>`).join('')||'<li>\u6682\u65e0\u53ef\u9080\u8bf7\u7684\u5728\u7ebf\u73a9\u5bb6\u3002</li>';
    el('friendsInviteTickets').innerHTML=(runtime?.invitations||[]).filter(ticket=>ticket.game===scope.game&&ticket.room===scope.session).map(ticket=>`<li><span>${esc(ticket.status)} \u00b7 ${esc(ticket.expiresInSeconds)} \u79d2</span>${ticket.status==='PENDING'&&ticket.host===runtime.selfUuid&&ticket.expiresInSeconds>0?`<button class="secondary" data-invite-cancel="${esc(ticket.invitation)}" ${state.busy?'disabled':''}>\u64a4\u9500</button>`:''}</li>`).join('')||'<li>\u6682\u65e0\u53d1\u51fa\u7684\u9080\u8bf7\u3002</li>';
  }
  async function refresh(){if(!controller||!active())return;await controller.refresh();if(source==='friends')await controller.friends();}
  function enter(){controller?.close();controller=null;if(active()){controller=MuxiFriendsInvites.create({invoke,onChange:render});render(controller.state);void refresh();}}
  window.MuxiRoomInvites={
    show(next){const changed=scope.visible!==next.visible||scope.game!==next.game||scope.session!==next.session;scope=next;mount.hidden=!next.visible;if(changed)enter();},
    async respond(action){
      if(!inApp())throw Error('\u8bf7\u5728\u5c0f\u6e38\u620f\u5927\u5385\u5904\u7406\u9080\u8bf7');
      const temporary=MuxiFriendsInvites.create({invoke});
      try{await temporary.refresh();return await temporary.act(action);}finally{temporary.close();}
    }
  };
  panel.addEventListener('click',event=>{
    const button=event.target.closest('button');if(!button||button.disabled||!controller)return;
    if(button.id==='friendsInviteRefresh'){void refresh();return;}
    if(button.id==='friendsInviteOnline'||button.id==='friendsInviteFriends'){source=button.id==='friendsInviteOnline'?'online':'friends';render(controller.state);if(source==='friends')void controller.friends();return;}
    let action;
    if(button.dataset.inviteUid)action={op:'issue',game:scope.game,source,room:scope.session,uid:button.dataset.inviteUid};
    else if(button.dataset.inviteCancel)action={op:'cancel',game:scope.game,invitation:button.dataset.inviteCancel};
    if(action)void controller.act(action).catch(()=>{});
  });
  window.addEventListener('hashchange',enter);
  window.addEventListener('pagehide',()=>{controller?.close();clearInterval(poll);});
  poll=setInterval(()=>{if(active()&&controller&&!controller.state.busy)void refresh();},2000);
})();

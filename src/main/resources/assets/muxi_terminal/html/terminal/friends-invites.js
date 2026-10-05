(() => {
  'use strict';
  const root=document.getElementById('games'),mount=document.getElementById('mg-room-invitations');if(!root||!mount)return;
  const panel=document.createElement('section');panel.className='detail-card friends-invite-panel';panel.id='friendsInvitePanel';
  panel.innerHTML='<div class="task-tabs detail-actions" role="tablist" aria-label="邀请来源"><button id="friendsInviteOnline" class="task-tab" role="tab">在线玩家</button><button id="friendsInviteFriends" class="task-tab" role="tab">我的好友</button><button id="friendsInviteRefresh" class="secondary">刷新</button></div><p id="friendsInviteStatus" class="friends-invite-note" role="status"></p><ul id="friendsInviteRows"></ul><div id="friendsInviteSent"><h4>已发出的邀请</h4><ul id="friendsInviteTickets"></ul></div>';
  mount.append(panel);
  const el=id=>document.getElementById(id),inApp=()=>contentView&&location.hash==='#/games';
  let source='online',controller=null,scope={visible:false,game:'',session:''},poll;
  const active=()=>inApp()&&scope.visible;
  const invoke=request=>new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('响应超时，请刷新核实')),15000);native(request).then(resolve,reject).finally(()=>clearTimeout(timer));});
  function render(state){
    window.MuxiMinigamesApp?.invitationBusy(state.busy||state.unconfirmed);
    if(!active())return;
    const runtime=state.runtime,available=MuxiFriendsInvites.enabled(runtime),current=window.MuxiMinigamesApp?.inviteContext();
    const room=available?runtime.rooms.find(row=>row.game===scope.game&&row.session===scope.session&&row.mine):null;
    const inviteable=available?room?.inviteable===true:!current?.authenticated&&current?.permission.invite===true;
    for(const [id,mode]of [['friendsInviteOnline','online'],['friendsInviteFriends','friends']]){el(id).setAttribute('aria-selected',String(source===mode));el(id).classList.toggle('task-tab-active',source===mode);el(id).disabled=state.busy;}
    el('friendsInviteRefresh').disabled=state.busy;
    el('friendsInviteStatus').textContent=state.error||(state.busy?'已提交，正在等待服务器最终确认。':!available?(source==='friends'?'当前连接未提供可用的好友邀请服务。':'当前连接使用本玩法的在线玩家邀请。'):!inviteable?'仅等待中的房主可邀请队友。':'选择队友发送本房间邀请，对方接受后加入。');
    const locked=state.busy||state.unconfirmed||!inviteable;
    if(available){
      const peers=MuxiFriendsInvites.peers(runtime,state.business,source);
      el('friendsInviteRows').innerHTML=peers.map(peer=>`<li><span>${esc(peer.displayName)}</span><button class="secondary" data-invite-uid="${esc(peer.uid)}" ${locked?'disabled':''}>邀请</button></li>`).join('')||'<li>暂无可邀请的在线玩家。</li>';
    }else{
      el('friendsInviteRows').innerHTML=source==='online'&&!current?.authenticated?(current?.targets||[]).map(peer=>`<li><span>${esc(peer.name)}</span><button class="secondary" data-invite-player="${esc(peer.uuid)}" ${locked||!peer.enabled?'disabled':''}>邀请</button></li>`).join('')||'<li>暂无可邀请的在线玩家。</li>':'<li>好友邀请暂不可用。</li>';
    }
    const tickets=(runtime?.invitations||[]).filter(ticket=>ticket.game===scope.game&&ticket.room===scope.session);
    el('friendsInviteSent').hidden=!tickets.length;
    el('friendsInviteTickets').innerHTML=tickets.map(ticket=>`<li><span>${esc(ticket.status)} · ${esc(ticket.expiresInSeconds)} 秒</span>${ticket.status==='PENDING'&&ticket.host===runtime.selfUuid&&ticket.expiresInSeconds>0?`<button class="secondary" data-invite-cancel="${esc(ticket.invitation)}" ${locked?'disabled':''}>撤销</button>`:''}</li>`).join('');
  }
  async function refresh(){if(!controller||!active())return;await controller.refresh();if(source==='friends')await controller.friends();}
  function reset(){controller?.close();controller=null;window.MuxiMinigamesApp?.invitationBusy(false);}
  window.MuxiRoomInvites={
    show(next){
      const changed=scope.game!==next.game||scope.session!==next.session,reopened=!scope.visible&&next.visible;
      scope=next;mount.hidden=!next.visible;
      if(changed)reset();
      if(active()&&!controller){source='online';controller=MuxiFriendsInvites.create({invoke,onChange:render});render(controller.state);void refresh();}
      else if(active()&&controller){render(controller.state);if(reopened&&!controller.state.busy)void refresh();}
    },
    async respond(action){if(!inApp())throw Error('请在小游戏大厅处理邀请');const temporary=MuxiFriendsInvites.create({invoke});try{await temporary.refresh();return await temporary.act(action);}finally{temporary.close();}}
  };
  panel.addEventListener('click',event=>{
    const button=event.target.closest('button');if(!button||button.disabled||!controller)return;
    if(button.id==='friendsInviteRefresh'){void refresh();return;}
    if(button.id==='friendsInviteOnline'||button.id==='friendsInviteFriends'){source=button.id==='friendsInviteOnline'?'online':'friends';render(controller.state);if(source==='friends')void controller.friends();return;}
    if(button.dataset.invitePlayer){const peer=window.MuxiMinigamesApp.inviteContext().targets.find(t=>t.uuid===button.dataset.invitePlayer);if(peer){const operation=window.MuxiMinigamesApp.invite(peer.target);controller.state.busy=true;render(controller.state);void operation.finally(()=>{controller.state.busy=false;render(controller.state);});}return;}
    let action;
    if(button.dataset.inviteUid)action={op:'issue',game:scope.game,source,room:scope.session,uid:button.dataset.inviteUid};
    else if(button.dataset.inviteCancel)action={op:'cancel',game:scope.game,invitation:button.dataset.inviteCancel};
    if(action)void controller.act(action).catch(()=>{});
  });
  window.addEventListener('hashchange',()=>{if(!inApp())reset();});
  window.addEventListener('pagehide',()=>{reset();clearInterval(poll);});
  poll=setInterval(()=>{if(active()&&controller&&!controller.state.busy)void refresh();},2000);
})();

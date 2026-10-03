(() => {
  'use strict';
  const M=window.MuxiMinigamesModel,el=id=>document.getElementById(id),root=el('games');
  if(!root)return;
  let snapshot={loading:true,games:[]},context=M.cleanContext({}),actions=new Map(),lastRenderSignature='',stopped=false,pending=null,unconfirmed=null,invitationBusy=false,confirmAction=null,lastRead=0,readSerial=0,saveTimer;
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const lobbyHint='\u4ece\u623f\u95f4\u5217\u8868\u52a0\u5165\uff0c\u6216\u521b\u5efa\u65b0\u623f\u95f4\u3002';
  const text={lobby:'\u623f\u95f4\u5927\u5385',create:'\u521b\u5efa\u623f\u95f4',mode:'\u9009\u62e9\u6a21\u5f0f',map:'\u9009\u62e9\u5730\u56fe',confirm:'\u96be\u5ea6\u4e0e\u786e\u8ba4',room:'\u6211\u7684\u623f\u95f4',cancel:'\u53d6\u6d88\u521b\u5efa',previous:'\u4e0a\u4e00\u6b65'};
  function bridge(command,onLate){
    if(typeof window.muxi?.invoke!=='function')return Promise.reject(new Error('\u7ec8\u7aef\u6865\u63a5\u8fd8\u672a\u5c31\u7eea\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5'));
    let timer,expired=false;const invoking=window.muxi.invoke(command);
    invoking.then(value=>{if(expired)onLate?.(value);},()=>{});
    return Promise.race([invoking,new Promise((_,reject)=>{timer=setTimeout(()=>{expired=true;reject(Object.assign(new Error('\u7ec8\u7aef\u54cd\u5e94\u8d85\u65f6\uff0c\u8bf7\u5237\u65b0\u6838\u5b9e\u72b6\u6001'),{unconfirmed:true}));},4000);})]).finally(()=>clearTimeout(timer));
  }
  function notice(message){el('mg-notice').textContent=message;el('mg-notice').hidden=root.classList.contains('mg-lobby')&&message===lobbyHint;}
  function remember(){clearTimeout(saveTimer);saveTimer=setTimeout(()=>bridge('games.context:'+JSON.stringify(context)).catch(()=>{}),120);}
  const game=()=> (snapshot.games||[]).find(item=>item.id===context.game);
  const owned=()=> (snapshot.games||[]).find(item=>M.ownRoom(item));
  const stage=()=>['mode','map','create','room'].includes(context.drafts['minigames:stage'])?context.drafts['minigames:stage']:'lobby';
  const setStage=value=>{context.drafts['minigames:stage']=value;};
  const busy=()=>!!(pending||unconfirmed||invitationBusy);
  const iconHtml=(icon,label='')=>icon&&typeof icon==='object'?window.TerminalIcons?.html({...icon,label:icon.label||label})||'':'';
  function previewHtml(card){
    if(!card.preview&&!card.horseSku&&!card.horsePreviewId&&!card.horseAppearance)return '';
    const label=typeof card.preview?.label==='string'&&card.preview.label.trim()?card.preview.label:'\u9a6c\u5339\u5916\u89c2\u793a\u610f\u56fe';
    const preview=card.preview?.kind==='resource'&&typeof card.preview.id==='string'&&card.preview.id.endsWith('.png')?card.preview:null;
    return `<figure class="mg-card-preview"><div class="mg-preview-media">${iconHtml(preview,label)}<span class="mg-preview-unavailable">\u5916\u89c2\u9884\u89c8\u6682\u4e0d\u53ef\u7528</span></div><figcaption>${M.esc(label)}</figcaption></figure>`;
  }
  function buttonHtml(id,button,owner,displayOwner=owner){
    const key=String(actions.size);actions.set(key,{game:id,button,owner,icon:button.icon||displayOwner.icon});
    return `<button data-mg-action="${key}" ${M.blocked(snapshot,id,button)||busy()?'disabled':''} class="${button.safe?'secondary':'primary'}">${iconHtml(button.icon,button.label)}${M.esc(button.label)}</button>`;
  }
  function fieldsHtml(id,owner,filter=()=>true){
    const selected=M.values(id,owner,context.drafts);
    return (owner.fields||[]).filter(filter).map(field=>`<div class="mg-field"><span>${M.esc(field.label)}</span><div class="detail-actions">${M.fieldOptions(field,selected).map(option=>`<button class="task-tab ${String(option.value)===selected[field.id]?'task-tab-active':''}" data-mg-choice="${M.esc(id+':'+field.id)}" data-mg-value="${M.esc(option.value)}" ${busy()?'disabled':''} aria-pressed="${String(option.value)===selected[field.id]}">${M.esc(option.label)}</button>`).join('')}</div></div>`).join('');
  }
  function sectionHtml(id,section){
    return `<section class="detail-card"><div class="detail-section"><h3>${iconHtml(section.icon,section.title)}${M.esc(section.title)}</h3><p>${M.esc(section.text)}</p></div><div class="mg-fields">${fieldsHtml(id,section)}</div><div class="detail-actions">${(section.actions||[]).map(button=>buttonHtml(id,button,section)).join('')}</div><div class="guide-list">${(section.cards||[]).map(card=>`<article class="guide-card">${previewHtml(card)}<h4>${iconHtml(card.icon,card.title)}${M.esc(card.title)}</h4><p>${M.esc(card.text)}</p><div class="detail-actions">${(card.actions||[]).map(button=>buttonHtml(id,button,section,card)).join('')}</div></article>`).join('')}</div></section>`;
  }
  function currencyHtml(item){return (item?.ui?.currencies||[]).map(currency=>`<article class="task-intro"><div><small>${M.esc(currency.label)} \u00b7 ${M.esc(currency.scope)}</small><strong>${M.esc(currency.value)}</strong><p>${M.esc(currency.note)}</p></div></article>`).join('');}
  function mapOptions(item){const owner=M.createSection(item),selected=M.values(item.id,owner||{},context.drafts);return M.fieldOptions(owner?.fields?.find(field=>field.id==='map')||{},selected);}
  function flowButtons(previous){return `<div class="detail-actions"><button class="secondary" data-mg-stage="${previous}" ${busy()?'disabled':''}>${text.previous}</button><button class="secondary" data-mg-cancel ${busy()?'disabled':''}>${text.cancel}</button></div>`;}
  function render(){
    if(stopped)return;
    const games=snapshot.games||[],selected=game(),step=stage(),room=M.ownRoom(selected);
    if(step==='room'&&!room&&!busy())setStage('lobby');
    const currentStep=stage();
    root.classList.toggle('mg-lobby',context.page==='lobby'&&currentStep==='lobby');
    el('mg-notice').hidden=root.classList.contains('mg-lobby')&&el('mg-notice').textContent===lobbyHint;
    const signature=JSON.stringify([snapshot.games,snapshot.activeGame,snapshot.platform,snapshot.social,snapshot.resultPending,snapshot.allowed,snapshot.actionSupported,snapshot.loading,snapshot.protocol,context,busy()]);
    if(signature===lastRenderSignature)return;lastRenderSignature=signature;
    el('mg-platform').hidden=context.page!=='shop';
    el('mg-platform').innerHTML=`<div><small>\u5e73\u53f0\u7d2f\u8ba1\u79ef\u5206</small><strong>${M.esc(M.platformText(snapshot.platform))}</strong></div><p>\u5404\u73a9\u6cd5\u8d27\u5e01\u4e0e\u5e73\u53f0\u79ef\u5206\u5206\u522b\u8bb0\u5f55\u3002${snapshot.resultPending>0?` ${M.esc(snapshot.resultPending)} \u6761\u7ed3\u7b97\u7b49\u5f85\u786e\u8ba4\u3002`:''}</p>`;
    for(const tab of root.querySelectorAll('[data-mg-page]')){const active=tab.dataset.mgPage===context.page;tab.setAttribute('aria-pressed',String(active));tab.classList.toggle('task-tab-active',active);}
    el('mg-stepbar').hidden=context.page!=='lobby'||['lobby','room'].includes(currentStep);
    el('mg-stepbar').innerHTML=['mode','map','create'].map((value,index)=>`<span class="task-tab ${currentStep===value?'task-tab-active':''}" aria-current="${currentStep===value?'step':'false'}">${index+1}. ${text[value==='create'?'confirm':value]}</span>`).join('');
    el('mg-games').hidden=context.page!=='lobby'||currentStep!=='mode';
    el('mg-games').innerHTML=games.map(entry=>`<button class="guide-card" data-mg-game="${M.esc(entry.id)}" ${!M.createSection(entry)||busy()||owned()?'disabled':''}><h3>${M.esc(entry.title)}</h3><p>${text.mode}</p></button>`).join('')||'<div class="guide-intro">\u7b49\u5f85\u670d\u52a1\u5668\u52a0\u8f7d\u53ef\u7528\u6a21\u5f0f\u3002</div>';
    el('mg-currencies').hidden=context.page!=='shop';
    window.TerminalIcons?.release(el('mg-currencies'));
    el('mg-currencies').innerHTML=context.page==='shop'?games.map(currencyHtml).join(''):'';
    actions.clear();let content='';
    if(context.page==='shop'){
      content=games.map(entry=>`<div class="section-title">${M.esc(entry.ui?.shop?.title||entry.title)}</div>${M.sections(entry,'shop').map(section=>sectionHtml(entry.id,section)).join('')}`).join('');
    }else if(currentStep==='lobby'){
      const own=owned();
      content=`<div class="mg-lobby-heading"><h3>${text.lobby}</h3><button class="primary" data-mg-create ${busy()||own||snapshot.loading||snapshot.actionSupported===false||snapshot.allowed===false?'disabled':''}>${text.create}</button></div>`;
      content+='<div id="mg-room-list" class="mg-room-list" role="region" aria-label="房间列表" tabindex="0">';
      if(own)content+=`<article class="task-intro"><div><strong>${M.esc(own.title)} \u00b7 ${M.esc(M.ownRoom(own).id)}</strong><span>\u4f60\u5df2\u5728\u623f\u95f4\u4e2d</span></div><button class="secondary" data-mg-open-room="${M.esc(own.id)}">\u8fd4\u56de\u623f\u95f4</button></article>`;
      const rooms=games.flatMap(entry=>M.sections(entry).filter(section=>M.role(section)==='rooms').map(section=>({entry,section})));
      const any=rooms.some(({section})=>(section.cards||[]).length);
      content+=any?rooms.filter(({section})=>(section.cards||[]).length).map(({entry,section})=>`<div class="section-title">${M.esc(entry.title)}</div>${sectionHtml(entry.id,section)}`).join(''):'<div class="task-empty">\u6682\u65e0\u53ef\u52a0\u5165\u7684\u623f\u95f4\u3002\u70b9\u51fb\u201c\u521b\u5efa\u623f\u95f4\u201d\u5f00\u59cb\u7ec4\u961f\u3002</div>';
      if(snapshot.social?.enabled&&snapshot.social?.authenticated&&!own){
        content+=(snapshot.social.invitations||[]).filter(ticket=>ticket.status==='PENDING'&&ticket.expiresInSeconds>0&&games.some(entry=>entry.id===ticket.game&&entry.state?.self===ticket.target)).map(ticket=>`<article class="task-intro"><div><strong>${M.esc(games.find(entry=>entry.id===ticket.game)?.title)} \u00b7 ${M.esc(ticket.room.slice(0,8))}</strong><span>\u6536\u5230\u623f\u95f4\u9080\u8bf7 \u00b7 ${M.esc(ticket.expiresInSeconds)} \u79d2</span></div><div class="detail-actions"><button class="primary" data-mg-invite="${M.esc(ticket.invitation)}" data-mg-invite-op="accept" ${busy()?'disabled':''}>\u63a5\u53d7\u5e76\u52a0\u5165</button><button class="secondary" data-mg-invite="${M.esc(ticket.invitation)}" data-mg-invite-op="decline" ${busy()?'disabled':''}>\u62d2\u7edd</button></div></article>`).join('');
      }
      content+='</div>';
    }else if(currentStep==='mode'){
      content=`<div class="guide-intro">\u5148\u9009\u62e9\u73a9\u6cd5\uff0c\u518d\u9009\u62e9\u5730\u56fe\u548c\u96be\u5ea6\u3002\u786e\u8ba4\u524d\u4e0d\u4f1a\u521b\u5efa\u623f\u95f4\u3002</div><button class="secondary" data-mg-cancel>${text.cancel}</button>`;
    }else if(selected&&currentStep==='map'){
      const owner=M.createSection(selected),maps=mapOptions(selected);
      content=`<section class="detail-card"><div class="detail-section"><h3>${M.esc(selected.title)} \u00b7 ${text.map}</h3></div><div class="mg-fields">${owner?fieldsHtml(selected.id,owner,field=>field.id==='mode'):''}</div><div class="guide-list mg-map-list">${maps.map(map=>`<button class="guide-card" data-mg-map="${M.esc(map.value)}" ${busy()?'disabled':''}><h3>${M.esc(map.label)}</h3><p>\u9009\u62e9\u8fd9\u5f20\u5730\u56fe</p></button>`).join('')||'<p>\u5f53\u524d\u6a21\u5f0f\u6ca1\u6709\u53ef\u7528\u5730\u56fe\uff0c\u8bf7\u5207\u6362\u6a21\u5f0f\u6216\u5237\u65b0\u3002</p>'}</div>${flowButtons('mode')}</section>`;
    }else if(selected&&currentStep==='create'){
      const owner=M.createSection(selected),values=M.values(selected.id,owner||{},context.drafts);
      const summary=(owner?.fields||[]).filter(field=>field.id!=='difficulty').map(field=>`${M.esc(field.label)}\uff1a${M.esc(M.fieldOptions(field,values).find(option=>String(option.value)===values[field.id])?.label||values[field.id])}`).join(' \u00b7 ');
      content=owner?`<section class="detail-card"><div class="detail-section"><h3>${M.esc(selected.title)} \u00b7 ${text.confirm}</h3><p>${summary}</p><p>\u521b\u5efa\u540e\u5148\u8fdb\u5165\u7b49\u5f85\u623f\u95f4\uff0c\u9080\u8bf7\u961f\u53cb\u540e\u7531\u623f\u4e3b\u5f00\u59cb\u3002</p></div><div class="mg-fields">${fieldsHtml(selected.id,owner,field=>field.id==='difficulty')}</div><div class="detail-actions">${(owner.actions||[]).filter(button=>/^create/.test(button.action)).map(button=>buttonHtml(selected.id,button,owner)).join('')}</div>${flowButtons('map')}</section>`:'<div class="guide-intro">\u5f53\u524d\u6a21\u5f0f\u672a\u63d0\u4f9b\u521b\u5efa\u9009\u9879\uff0c\u8bf7\u5237\u65b0\u3002</div>';
    }else if(selected&&room){
      const social=snapshot.social?.enabled===true&&snapshot.social?.authenticated===true;
      content=`<div class="section-title">${text.room} \u00b7 ${M.esc(selected.title)}</div>`+M.sections(selected).filter(section=>['room','recovery','result'].includes(M.role(section))||(M.role(section)==='invite'&&M.waiting(room)&&!social)).map(section=>sectionHtml(selected.id,section)).join('');
    }
    const listScroll=el('mg-room-list')?.scrollTop||0;
    window.TerminalIcons?.release(el('mg-content'));el('mg-content').innerHTML=content;
    if(el('mg-room-list'))el('mg-room-list').scrollTop=listScroll;
    void window.TerminalIcons?.hydrate(el('mg-content'));void window.TerminalIcons?.hydrate(el('mg-currencies'));
    const inviteVisible=context.page==='lobby'&&currentStep==='room'&&M.waiting(room);
    const inviteMount=el('mg-room-invitations');if(inviteMount)inviteMount.hidden=!inviteVisible;
    window.MuxiRoomInvites?.show({visible:inviteVisible,game:selected?.id||'',session:room?.session||''});remember();
  }
  function availabilityNotice(){
    if(!snapshot.supported)return '\u5f53\u524d\u670d\u52a1\u5668\u672a\u63d0\u4f9b\u5c0f\u6e38\u620f\u670d\u52a1\u3002';
    if(snapshot.loading||!Array.isArray(snapshot.games))return '\u6b63\u5728\u7b49\u5f85\u670d\u52a1\u5668\u52a0\u8f7d\u5927\u5385\u3002';
    if(snapshot.actionSupported===false||snapshot.protocol!==2)return '\u5ba2\u6237\u7aef\u4e0e\u670d\u52a1\u5668\u5c0f\u6e38\u620f\u7248\u672c\u4e0d\u5339\u914d\uff0c\u8bf7\u66f4\u65b0\u540e\u91cd\u8bd5\u3002';
    if(unconfirmed)return '\u672a\u6536\u5230\u6700\u7ec8\u786e\u8ba4\uff0c\u8bf7\u5237\u65b0\u6838\u5b9e\u623f\u95f4\u72b6\u6001\uff0c\u4e0d\u4f1a\u81ea\u52a8\u91cd\u8bd5\u3002';
    if(busy())return '\u6b63\u5728\u7b49\u5f85\u670d\u52a1\u5668\u6700\u7ec8\u786e\u8ba4\uff0c\u8bf7\u52ff\u91cd\u590d\u63d0\u4ea4\u3002';
    return snapshot.notice||(snapshot.allowed?lobbyHint:'\u5f53\u524d\u53c2\u4e0e\u53d7\u9650\uff0c\u4ecd\u53ef\u5b89\u5168\u9000\u51fa\u6216\u6062\u590d\u3002');
  }
  function finishOperation(data){
    const operation=pending||unconfirmed,result=data.operation;
    if(!operation||!result||result.request!==operation.request||!['completed','failed'].includes(result.status))return false;
    const item=(data.games||[]).find(entry=>entry.id===operation.game),room=M.ownRoom(item);
    if(result.status==='completed'&&operation.enter&&!room)return false;
    if(result.status==='completed'&&operation.leave&&room)return false;
    pending=null;unconfirmed=null;
    if(result.status==='completed'&&operation.enter){context.game=operation.game;context.page='lobby';setStage('room');}
    if(result.status==='completed'&&operation.leave){context.page='lobby';context.game='';setStage('lobby');}
    render();notice(result.notice||(result.status==='completed'?'\u64cd\u4f5c\u5df2\u786e\u8ba4':'\u64cd\u4f5c\u672a\u5b8c\u6210'));return true;
  }
  async function refresh(force=false){
    if(stopped||!root.classList.contains('page-active')||(!force&&Date.now()-lastRead<1500))return;
    lastRead=Date.now();const serial=++readSerial;
    try{await bridge('games.request');const data=await bridge('games.snapshot');if(stopped||serial!==readSerial)return;snapshot=data;if(finishOperation(data))return;render();if(!pending)notice(availabilityNotice());}
    catch(error){if(!stopped&&serial===readSerial){render();notice(error.message);}}
  }
  async function execute(target){
    if(busy())return;
    const current=M.currentAction(snapshot,target);if(!current||M.blocked(snapshot,target.game,current.button)){notice('\u670d\u52a1\u5668\u72b6\u6001\u5df2\u53d8\u5316\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5');return;}
    try{
      const value=M.actionValue(current.game,current.button,current.owner,context.drafts);if(value.length>128)throw new Error('\u64cd\u4f5c\u53c2\u6570\u8fc7\u957f');
      const operation={request:'sending',game:current.game,enter:/^create/.test(current.button.action)||current.button.action==='join',leave:current.button.action==='leave'};
      pending=operation;render();notice(availabilityNotice());
      const sent=await bridge('games.action:'+JSON.stringify({game:current.game,action:current.button.action,value}),ack=>{
        if(stopped||(pending!==operation&&unconfirmed!==operation)||!ack?.request)return;
        operation.request=ack.request;void refresh(true);
      });
      if(stopped)return;if(!sent.request)throw new Error('\u5c0f\u6e38\u620f\u63d0\u4ea4\u672a\u786e\u8ba4');pending.request=sent.request;
      const deadline=Date.now()+7000;
      while(pending&&!stopped&&Date.now()<deadline){await sleep(150);await refresh(true);}
      if(pending&&!stopped){unconfirmed=pending;pending=null;render();notice('\u672a\u6536\u5230\u6700\u7ec8\u786e\u8ba4\uff0c\u8bf7\u5237\u65b0\u6838\u5b9e\u623f\u95f4\u72b6\u6001\uff0c\u4e0d\u4f1a\u81ea\u52a8\u91cd\u8bd5\u3002');}
    }catch(error){if(!stopped){if(pending?.request==='sending'&&!error.unconfirmed){pending=null;}else if(pending){unconfirmed=pending;pending=null;}render();notice(error.message);}}
  }
  function lobby(){context.page='lobby';context.game='';setStage('lobby');render();}
  async function respond(button){
    if(busy())return;
    const ticket=(snapshot.social?.invitations||[]).find(row=>row.invitation===button.dataset.mgInvite&&row.status==='PENDING'&&row.expiresInSeconds>0);
    if(!ticket||!window.MuxiRoomInvites?.respond)return;
    invitationBusy=true;render();
    try{
      const completed=await window.MuxiRoomInvites.respond({op:button.dataset.mgInviteOp,game:ticket.game,invitation:ticket.invitation});
      if(stopped)return;
      await refresh(true);
      if(completed&&button.dataset.mgInviteOp==='accept'&&M.ownRoom((snapshot.games||[]).find(entry=>entry.id===ticket.game))){context.game=ticket.game;context.page='lobby';setStage('room');}
    }catch(error){notice(error.message);}
    finally{invitationBusy=false;render();}
  }
  root.addEventListener('click',event=>{
    const control=event.target.closest('button');if(control?.disabled)return;
    const invitation=event.target.closest('[data-mg-invite]');if(invitation){void respond(invitation);return;}
    if(event.target.closest('[data-mg-create]')){if(busy()||owned())return;context.page='lobby';context.game='';setStage('mode');render();return;}
    if(event.target.closest('[data-mg-cancel]')){if(!busy())lobby();return;}
    const open=event.target.closest('[data-mg-open-room]');if(open){context.game=open.dataset.mgOpenRoom;setStage('room');render();return;}
    const mode=event.target.closest('[data-mg-game]');if(mode){if(busy()||owned())return;context.game=mode.dataset.mgGame;setStage('map');render();return;}
    const choice=event.target.closest('[data-mg-choice]');if(choice){if(busy())return;context.drafts[choice.dataset.mgChoice]=choice.dataset.mgValue;const item=game(),owner=M.createSection(item),values=M.values(item.id,owner||{},context.drafts);for(const [key,value]of Object.entries(values))context.drafts[item.id+':'+key]=value;render();return;}
    const map=event.target.closest('[data-mg-map]');if(map){if(busy()||!mapOptions(game()).some(option=>String(option.value)===map.dataset.mgMap))return;context.drafts[context.game+':map']=map.dataset.mgMap;setStage('create');render();return;}
    const step=event.target.closest('[data-mg-stage]');if(step){if(!busy()){setStage(step.dataset.mgStage);render();}return;}
    const page=event.target.closest('[data-mg-page]');if(page){context.page=page.dataset.mgPage;render();return;}
    const button=event.target.closest('[data-mg-action]');if(!button)return;const target=actions.get(button.dataset.mgAction);if(!target)return;
    if(target.button.confirm){confirmAction=target;window.TerminalIcons?.release(el('mg-confirm-text'));el('mg-confirm-text').innerHTML=iconHtml(target.icon)+`<span>${M.esc(target.button.confirm)}</span>`;void window.TerminalIcons?.hydrate(el('mg-confirm-text'));el('mg-confirm').hidden=false;el('mg-confirm-cancel').focus();}else execute(target);
  });
  el('mg-back').addEventListener('click',lobby);
  el('mg-refresh').addEventListener('click',()=>refresh(true));
  function cancel(){el('mg-confirm').hidden=true;window.TerminalIcons?.release(el('mg-confirm-text'));confirmAction=null;}
  el('mg-confirm-cancel').addEventListener('click',cancel);
  el('mg-confirm-ok').addEventListener('click',()=>{const target=confirmAction;cancel();if(target)execute(target);});
  root.addEventListener('keydown',event=>{if(event.key==='Escape'&&!el('mg-confirm').hidden){cancel();event.stopPropagation();}});
  const interval=setInterval(()=>refresh(),2000);
  window.addEventListener('hashchange',()=>refresh(true));
  window.addEventListener('pagehide',()=>{stopped=true;readSerial++;pending=null;unconfirmed=null;clearInterval(interval);clearTimeout(saveTimer);});
  window.MuxiMinigamesApp={activate:()=>refresh(true)};
  async function initialize(){try{context=M.cleanContext(await bridge('games.context'));}catch{}context.page='lobby';setStage('lobby');await refresh(true);}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initialize,{once:true});else initialize();
})();

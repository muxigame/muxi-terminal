(() => {
  'use strict';
  const M=window.MuxiMinigamesModel,el=id=>document.getElementById(id),root=el('games');
  if(!root)return;
  let snapshot={loading:true,games:[]},context=M.cleanContext({}),actions=new Map(),lastRenderSignature='',stopped=false,pending=null,confirmAction=null,lastRead=0,readSerial=0,saveTimer;
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  function bridge(command){
    if(typeof window.muxi?.invoke!=='function')return Promise.reject(new Error('终端桥接尚未就绪，请稍后重试'));
    let timer;return Promise.race([window.muxi.invoke(command),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('终端响应超时，请刷新检查状态')),4000);})]).finally(()=>clearTimeout(timer));
  }
  function notice(message){el('mg-notice').textContent=message;}
  function remember(){clearTimeout(saveTimer);saveTimer=setTimeout(()=>bridge('games.context:'+JSON.stringify(context)).catch(()=>{}),120);}
  function game(){return (snapshot.games||[]).find(item=>item.id===context.game);}
  function stage(){return ['mode','map','create','room'].includes(context.drafts['minigames:stage'])?context.drafts['minigames:stage']:'mode';}
  function setStage(value){context.drafts['minigames:stage']=value;}
  function sections(item,page='lobby'){return item?.ui?.[page]?.sections||[];}
  function createSection(item){return sections(item).find(section=>section.role==='create'||(section.actions||[]).some(action=>/^create/.test(action.action)));}
  function ownRoom(item){return (item?.state?.rooms||[]).find(room=>room.mine);}
  function buttonHtml(id,button,owner){
    const key=String(actions.size);actions.set(key,{game:id,button,owner});
    return `<button data-mg-action="${key}" ${M.blocked(snapshot,id,button)||pending?'disabled':''} class="${button.safe?'secondary':'primary'}">${M.esc(button.label)}</button>`;
  }
  function fieldsHtml(id,owner,filter=()=>true){
    const selected=M.values(id,owner,context.drafts);
    return (owner.fields||[]).filter(filter).map(field=>`<div class="mg-field"><span>${M.esc(field.label)}</span><div class="detail-actions">${(field.options||[]).map(option=>`<button class="task-tab ${String(option.value)===selected[field.id]?'task-tab-active':''}" data-mg-choice="${M.esc(id+':'+field.id)}" data-mg-value="${M.esc(option.value)}" aria-pressed="${String(option.value)===selected[field.id]}">${M.esc(option.label)}</button>`).join('')}</div></div>`).join('');
  }
  function sectionHtml(id,section){
    return `<section class="detail-card"><div class="detail-section"><h3>${M.esc(section.title)}</h3><p>${M.esc(section.text)}</p></div><div class="mg-fields">${fieldsHtml(id,section)}</div><div class="detail-actions">${(section.actions||[]).map(button=>buttonHtml(id,button,section)).join('')}</div><div class="guide-list">${(section.cards||[]).map(card=>`<article class="guide-card"><h4>${M.esc(card.title)}</h4><p>${M.esc(card.text)}</p><div class="detail-actions">${(card.actions||[]).map(button=>buttonHtml(id,button,section)).join('')}</div></article>`).join('')}</div></section>`;
  }
  function currencyHtml(item){return (item?.ui?.currencies||[]).map(currency=>`<article class="task-intro"><div><small>${M.esc(currency.label)} · ${M.esc(currency.scope)}</small><strong>${M.esc(currency.value)}</strong><p>${M.esc(currency.note)}</p></div></article>`).join('');}
  function render(){
    if(stopped)return;const games=snapshot.games||[],item=game();
    if(snapshot.activeGame&&context.page==='lobby'&&stage()!=='mode'&&(!context.game||snapshot.activeGame===context.game)){context.game=snapshot.activeGame;setStage('room');}
    const selected=game(),step=stage();
    const signature=JSON.stringify([snapshot.games,snapshot.activeGame,snapshot.platform,snapshot.resultPending,snapshot.allowed,snapshot.actionSupported,snapshot.loading,snapshot.protocol,context,!!pending]);
    if(signature===lastRenderSignature)return;lastRenderSignature=signature;
    const last=snapshot.lastResult,lastTitle=last?games.find(entry=>entry.id===last.game)?.title||last.game:'';
    el('mg-platform').innerHTML=`<div><small>平台累计积分</small><strong>${M.esc(M.platformText(snapshot.platform))}</strong></div><p>独立于战术点与兑换币，当前不兑换商品。${snapshot.resultPending>0?` ${M.esc(snapshot.resultPending)} 条结算等待确认。`:''}${last?` 最近记录：${M.esc(lastTitle)} · ${last.win?'通关':'未通关'}。`:''}</p>`;
    for(const tab of root.querySelectorAll('[data-mg-page]')){const active=tab.dataset.mgPage===context.page;tab.setAttribute('aria-pressed',String(active));tab.classList.toggle('task-tab-active',active);}
    el('mg-stepbar').hidden=context.page!=='lobby';
    el('mg-stepbar').innerHTML=['mode','map','create','room'].map((value,index)=>`<button class="task-tab ${step===value?'task-tab-active':''}" data-mg-stage="${value}" ${value!=='mode'&&!selected?'disabled':''}>${index+1}. ${['模式','地图','创建','队伍'][index]}</button>`).join('');
    el('mg-games').hidden=context.page!=='lobby'||step!=='mode';
    el('mg-games').innerHTML=games.map(entry=>`<button class="guide-card" data-mg-game="${M.esc(entry.id)}"><h3>${M.esc(entry.title)}</h3><p>${snapshot.activeGame===entry.id?'当前队伍 · 进入房间':'选择模式与地图'}</p></button>`).join('')||'<div class="guide-intro">等待服务器返回可用模式…</div>';
    el('mg-currencies').innerHTML=(context.page==='shop'?games:[selected]).filter(Boolean).map(currencyHtml).join('');actions.clear();
    let content='';
    if(context.page==='shop'){
      content=games.map(entry=>`<div class="section-title">${M.esc(entry.ui?.shop?.title||entry.title)}</div>${sections(entry,'shop').map(section=>sectionHtml(entry.id,section)).join('')}`).join('');
    }else if(step==='mode'){
      content=games.map(entry=>sections(entry).filter(section=>['rooms','recovery','result'].includes(section.role)).map(section=>`<div class="section-title">${M.esc(entry.title)}</div>${sectionHtml(entry.id,section)}`).join('')).join('');
    }else if(selected&&step==='map'){
      const owner=createSection(selected),maps=owner?.fields?.find(field=>field.id==='map')?.options||[];
      content=`<section class="detail-card"><div class="detail-section"><h3>${M.esc(selected.title)} · 选择地图</h3><p>地图来自当前服务器。选择后确认难度并创建房间。</p></div><div class="mg-fields">${owner?fieldsHtml(selected.id,owner,field=>field.id==='mode'):''}</div><div class="guide-list">${maps.map(map=>`<button class="guide-card" data-mg-map="${M.esc(map.value)}"><h3>${M.esc(map.label)}</h3><p>选择此地图</p></button>`).join('')||'<p>当前没有可用地图，请刷新或联系管理员。</p>'}</div></section>`;
    }else if(selected&&step==='create'){
      const owner=createSection(selected);content=owner?sectionHtml(selected.id,owner):'<div class="guide-intro">当前模式未提供创建选项，请刷新。</div>';
    }else if(selected){
      content=sections(selected).filter(section=>section.role!=='create').map(section=>sectionHtml(selected.id,section)).join('');
      if(!ownRoom(selected))content='<div class="guide-intro">当前没有队伍，可在大厅加入房间，或选择地图创建房间。</div>'+content;
    }
    el('mg-content').innerHTML=content;remember();
  }
  function availabilityNotice(){
    if(!snapshot.supported)return '当前服务器未提供小游戏服务，请检查客户端与服务器版本。';
    if(snapshot.loading||!Array.isArray(snapshot.games))return '正在等待服务器返回大厅…';
    if(snapshot.actionSupported===false||snapshot.protocol!==2)return '客户端与服务器小游戏版本不匹配，请更新后再创建、加入或兑换。';
    return snapshot.notice||(snapshot.allowed?'选择模式与地图，或从房间列表加入队友。':'当前连接参与受限；仍可安全退出并恢复。');
  }
  async function refresh(force=false){
    if(stopped||!root.classList.contains('page-active')||(!force&&Date.now()-lastRead<1500))return;
    lastRead=Date.now();const serial=++readSerial;
    try{
      await bridge('games.request');const data=await bridge('games.snapshot');if(stopped||serial!==readSerial)return;
      snapshot=data;
      if(pending&&data.operation?.request===pending.request){const result=data.operation;pending=null;notice(result.notice|| (result.status==='completed'?'操作已完成':'操作未完成'));render();return;}
      render();if(!pending)notice(availabilityNotice());
    }catch(error){if(!stopped&&serial===readSerial){pending=null;render();notice(error.message);}}
  }
  async function execute(target){
    if(pending)return;
    const current=M.currentAction(snapshot,target);if(!current||M.blocked(snapshot,target.game,current.button)){notice('房间或商品状态已变化，请刷新后重试');return;}
    try{
      const value=M.actionValue(current.game,current.button,current.owner,context.drafts);if(value.length>128)throw new Error('操作参数过长');
      pending={request:'sending'};render();notice('正在提交，等待服务器确认…');
      const sent=await bridge('games.action:'+JSON.stringify({game:current.game,action:current.button.action,value}));
      if(stopped)return;if(!sent.request)throw new Error('小游戏协议不匹配，操作未确认');pending={request:sent.request};
      const deadline=Date.now()+7000;
      while(pending&&!stopped&&Date.now()<deadline){await sleep(150);await refresh(true);}
      if(pending&&!stopped){pending=null;render();notice('未收到服务器确认，请刷新检查房间或余额后再操作。');}
    }catch(error){if(!stopped){pending=null;render();notice(error.message);}}
  }
  root.addEventListener('click',event=>{
    const mode=event.target.closest('[data-mg-game]');if(mode){context.game=mode.dataset.mgGame;context.page='lobby';setStage(ownRoom(game())?'room':'map');render();return;}
    const choice=event.target.closest('[data-mg-choice]');if(choice){context.drafts[choice.dataset.mgChoice]=choice.dataset.mgValue;render();return;}
    const map=event.target.closest('[data-mg-map]');if(map){context.drafts[context.game+':map']=map.dataset.mgMap;setStage('create');render();return;}
    const step=event.target.closest('[data-mg-stage]');if(step){setStage(step.dataset.mgStage);render();return;}
    const page=event.target.closest('[data-mg-page]');if(page){context.page=page.dataset.mgPage;render();return;}
    const button=event.target.closest('[data-mg-action]');if(!button)return;const target=actions.get(button.dataset.mgAction);if(!target)return;
    if(target.button.confirm){confirmAction=target;el('mg-confirm-text').textContent=target.button.confirm;el('mg-confirm').hidden=false;el('mg-confirm-cancel').focus();}
    else execute(target);
  });
  root.addEventListener('change',event=>{const field=event.target.closest('[data-mg-field]');if(field){context.drafts[field.dataset.mgField]=field.value;remember();}});
  el('mg-back').addEventListener('click',()=>{context.page='lobby';setStage('mode');context.game='';render();});
  el('mg-refresh').addEventListener('click',()=>refresh(true));
  function cancel(){el('mg-confirm').hidden=true;confirmAction=null;}
  el('mg-confirm-cancel').addEventListener('click',cancel);
  el('mg-confirm-ok').addEventListener('click',()=>{const target=confirmAction;cancel();if(target)execute(target);});
  root.addEventListener('keydown',event=>{if(event.key==='Escape'&&!el('mg-confirm').hidden){cancel();event.stopPropagation();}});
  const interval=setInterval(()=>refresh(),2000);
  window.addEventListener('hashchange',()=>refresh(true));
  window.addEventListener('pagehide',()=>{stopped=true;readSerial++;pending=null;clearInterval(interval);clearTimeout(saveTimer);});
  window.MuxiMinigamesApp={activate:()=>refresh(true)};
  async function initialize(){try{context=M.cleanContext(await bridge('games.context'));}catch{}await refresh(true);}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initialize,{once:true});else initialize();
})();

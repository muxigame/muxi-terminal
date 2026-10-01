(() => {
  'use strict';
  const M=window.MuxiMinigamesModel,el=id=>document.getElementById(id),root=el('games');
  if(!root)return;
  let snapshot={games:[]},context=M.cleanContext({}),actions=new Map(),lastViewSignature="",busy=false,stopped=false,confirmAction=null,lastRead=0,readSerial=0,saveTimer;
  const bridge=command=>new Promise((resolve,reject)=>{
    if(typeof window.cefQuery!=='function'){reject(new Error('请在游戏终端中打开小游戏'));return;}
    window.cefQuery({request:command,onSuccess:value=>{try{resolve(JSON.parse(value));}catch{reject(new Error('服务返回无效数据'));}},onFailure:(code,message)=>reject(new Error(message||'小游戏服务不可用'))});
  });
  function notice(message){el('mg-notice').textContent=message;}
  function remember(){clearTimeout(saveTimer);saveTimer=setTimeout(()=>bridge('games.context:'+JSON.stringify(context)).catch(()=>{}),120);}
  function selectedGame(){return (snapshot.games||[]).find(game=>game.id===context.game);}
  function buttonHtml(game,button,owner){
    const key=String(actions.size);actions.set(key,{game,button,owner});
    return `<button data-mg-action="${key}" ${M.blocked(snapshot,game,button)||busy?'disabled':''} class="${button.safe?'secondary':'primary'}">${M.esc(button.label)}</button>`;
  }
  function fieldsHtml(game,owner){
    const selected=M.values(game,owner,context.drafts);
    return (owner.fields||[]).map(field=>`<label>${M.esc(field.label)}<select data-mg-field="${M.esc(game+':'+field.id)}">${(field.options||[]).map(option=>`<option value="${M.esc(option.value)}" ${String(option.value)===selected[field.id]?'selected':''}>${M.esc(option.label)}</option>`).join('')}</select></label>`).join('');
  }
  function sectionHtml(game,section){
    return `<section class="mg-section"><h3>${M.esc(section.title)}</h3><p class="mg-section-note">${M.esc(section.text)}</p>
      <div class="mg-fields">${fieldsHtml(game,section)}</div><div class="mg-actions">${(section.actions||[]).map(button=>buttonHtml(game,button,section)).join('')}</div>
      <div class="mg-cards">${(section.cards||[]).map(card=>`<article class="mg-card"><h4>${M.esc(card.title)}</h4><p>${M.esc(card.text)}</p><div class="mg-actions">${(card.actions||[]).map(button=>buttonHtml(game,button,section)).join('')}</div></article>`).join('')}</div></section>`;
  }
  function render(){
    if(stopped)return;
    const games=snapshot.games||[];if(!games.some(game=>game.id===context.game))context.game=games[0]?.id||'';
    const game=selectedGame();
    el('mg-platform').innerHTML=`<div><small>平台累计积分</small><strong>${M.esc(M.platformText(snapshot.platform))}</strong></div><p>平台积分独立累计，当前不支持商品兑换。${snapshot.resultPending>0?`有 ${M.esc(snapshot.resultPending)} 份游戏结果等待确认。`:''}</p>`;
    el('mg-games').innerHTML=games.map(item=>`<button data-mg-game="${M.esc(item.id)}" aria-pressed="${item.id===context.game}"><span>${M.esc(item.title)}</span><small>${snapshot.activeGame===item.id?'当前队伍':''}</small></button>`).join('')||'<p>尚无可用玩法</p>';
    for(const tab of root.querySelectorAll('[data-mg-page]'))tab.setAttribute('aria-pressed',String(tab.dataset.mgPage===context.page));
    el('mg-currencies').innerHTML=(game?.ui?.currencies||[]).map(currency=>`<article><small>${M.esc(currency.label)} · ${M.esc(currency.scope)}</small><strong>${M.esc(currency.value)}</strong><p>${M.esc(currency.note)}</p></article>`).join('');
    const page=game?.ui?.[context.page];
    const signature=JSON.stringify([context.game,context.page,page,context.drafts,snapshot.allowed,snapshot.activeGame,busy]);
    if(signature!==lastViewSignature){lastViewSignature=signature;actions.clear();
    el('mg-content').innerHTML=page?`<h2 class="mg-page-title">${M.esc(page.title)}</h2>${(page.sections||[]).map(section=>sectionHtml(game.id,section)).join('')}`:'<section class="mg-section"><h3>等待玩法数据</h3><p>此玩法尚未提供终端页面。</p></section>';
    }
    remember();
  }
  async function refresh(force=false){
    if(stopped||!root.classList.contains('page-active')||(!force&&Date.now()-lastRead<1500))return;
    lastRead=Date.now();const serial=++readSerial;
    try{await bridge('games.request');const data=await bridge('games.snapshot');if(stopped||serial!==readSerial)return;snapshot=data;render();notice(!data.supported?'当前服务器未提供小游戏服务':data.notice||(data.allowed?'选择玩法，创建或加入队伍；商店在 APP 内切换。':'参与许可正在检查或受限制；仍可安全退出当前队伍。'));}
    catch(error){if(!stopped)notice(error.message);}
  }
  async function execute(action){
    if(busy||M.blocked(snapshot,action.game,action.button))return;
    try{
      const value=M.actionValue(action.game,action.button,action.owner,context.drafts);if(value.length>128)throw new Error('操作参数过长');
      busy=true;render();notice('已提交操作，等待服务器确认…');
      await bridge('games.action:'+JSON.stringify({game:action.game,action:action.button.action,value}));
      setTimeout(async()=>{busy=false;await refresh(true);},500);
    }catch(error){busy=false;notice(error.message);render();}
  }
  root.addEventListener('click',event=>{
    const game=event.target.closest('[data-mg-game]');if(game){context.game=game.dataset.mgGame;render();return;}
    const page=event.target.closest('[data-mg-page]');if(page){context.page=page.dataset.mgPage;render();return;}
    const button=event.target.closest('[data-mg-action]');if(!button)return;const action=actions.get(button.dataset.mgAction);if(!action)return;
    if(action.button.confirm){confirmAction=action;el('mg-confirm-text').textContent=action.button.confirm;el('mg-confirm').hidden=false;el('mg-confirm-cancel').focus();}
    else execute(action);
  });
  root.addEventListener('change',event=>{const field=event.target.closest('[data-mg-field]');if(field){context.drafts[field.dataset.mgField]=field.value;remember();}});
  el('mg-refresh').addEventListener('click',()=>refresh(true));
  el('mg-confirm-cancel').addEventListener('click',()=>{el('mg-confirm').hidden=true;confirmAction=null;});
  el('mg-confirm-ok').addEventListener('click',()=>{el('mg-confirm').hidden=true;if(confirmAction)execute(confirmAction);confirmAction=null;});
  const interval=setInterval(()=>refresh(),2000);
  window.addEventListener('hashchange',()=>refresh(true));
  window.addEventListener('pagehide',()=>{stopped=true;clearInterval(interval);clearTimeout(saveTimer);});
  window.MuxiMinigamesApp={activate:()=>refresh(true)};
  async function initialize(){try{context=M.cleanContext(await bridge('games.context'));}catch{}await refresh(true);}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initialize,{once:true});else initialize();
})();

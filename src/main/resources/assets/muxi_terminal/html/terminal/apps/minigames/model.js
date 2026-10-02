(function(root){
  'use strict';
  const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const safeGame=value=>typeof value==='string'&&/^[a-z0-9_-]{1,32}$/.test(value);
  function cleanContext(value){
    const drafts={};for(const [key,item] of Object.entries(value?.drafts||{}))if(/^[a-zA-Z0-9_:-]{1,80}$/.test(key)&&typeof item==='string'&&item.length<=64&&Object.keys(drafts).length<24)drafts[key]=item;
    return {game:safeGame(value?.game)?value.game:'',page:value?.page==='shop'?'shop':'lobby',drafts};
  }
  function values(game,owner,drafts){
    const ordered=[...(owner.fields||[])].sort((a,b)=>Number(b.id==='mode')-Number(a.id==='mode'));
    const result={};for(const field of ordered){
      const options=fieldOptions(field,result),saved=drafts[game+':'+field.id];
      result[field.id]=options.some(option=>String(option.value)===saved)?saved:String(field.selected??options[0]?.value??'');
      if(!options.some(option=>String(option.value)===result[field.id]))result[field.id]=String(options[0]?.value??'');
    }return result;
  }
  function fieldOptions(field,selected={}){
    return (field.options||[]).filter(option=>!Array.isArray(option.modes)||!selected.mode||option.modes.includes(selected.mode));
  }
  const sections=(item,page='lobby')=>item?.ui?.[page]?.sections||[];
  const createSection=item=>sections(item).find(section=>section.role==='create'||(section.actions||[]).some(button=>/^create/.test(button.action)));
  const ownRoom=item=>(item?.state?.rooms||[]).find(room=>room.mine===true);
  function role(section){
    if(section.role)return section.role;
    const actions=[...(section.actions||[]),...(section.cards||[]).flatMap(card=>card.actions||[])];
    if(actions.some(button=>/^create/.test(button.action)))return 'create';
    if(actions.some(button=>button.action==='invite'))return 'invite';
    if(actions.some(button=>button.action==='join'))return 'rooms';
    if(actions.some(button=>['start','difficulty'].includes(button.action)))return 'room';
    return '';
  }
  function waiting(room){return !!room&&(['WAITING','LOBBY','BUILDING'].includes(room.phase)||(room.lobbyWaiting===true&&['PREPARING','COUNTDOWN'].includes(room.phase)));}
  function actionValue(game,button,owner,drafts){
    const selected=values(game,owner,drafts);
    if(button.jsonFields){const keys=String(button.fields||'').split(',');const data={};for(const key of keys){if(!(key in selected))throw new Error('请选择完整创建参数');data[key]=selected[key];}return JSON.stringify(data);}
    return String(button.value||'').replace(/\{([a-zA-Z][a-zA-Z0-9_]*)\}/g,(_,key)=>{if(!(key in selected))throw new Error('请选择完整创建参数');return selected[key];});
  }
  function blocked(snapshot,game,button){return snapshot.loading===true||snapshot.actionSupported===false||(snapshot.protocol!=null&&snapshot.protocol!==2)||button.enabled===false||(!button.safe&&(!snapshot.allowed||(snapshot.activeGame&&snapshot.activeGame!==game)));}
  function platformText(platform){return platform?.available?String(platform.points):'尚未同步';}
  function currentAction(snapshot,target){
    const game=(snapshot.games||[]).find(item=>item.id===target.game);
    for(const page of ['lobby','shop'])for(const owner of game?.ui?.[page]?.sections||[]){
      const buttons=[...(owner.actions||[]),...(owner.cards||[]).flatMap(card=>card.actions||[])];
      const button=buttons.find(item=>item.action===target.button.action&&item.value===target.button.value&&item.fields===target.button.fields);
      if(button)return {game:target.game,button,owner};
    }return null;
  }
  const api={esc,safeGame,cleanContext,values,fieldOptions,sections,createSection,ownRoom,role,waiting,actionValue,blocked,platformText,currentAction};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.MuxiMinigamesModel=api;
})(typeof window!=='undefined'?window:globalThis);

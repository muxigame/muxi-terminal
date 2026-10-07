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
  function waiting(room){return !!room&&(['WAITING','LOBBY'].includes(room.phase));}
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
  function defaultName(nickname){let name=String(nickname||'玩家').replace(/[\p{Cc}\p{Cf}§]/gu,'').trim()||'玩家';let end=Math.min(20,name.length);if(end&&/[\uD800-\uDBFF]/.test(name[end-1]))end--;return name.slice(0,end)+'的房间';}
  function roomName(value){const name=String(value||'').trim();if(!name||name.length>24||/[\p{Cc}\p{Cf}§]/u.test(name))throw Error('房间名称应为 1—24 个字，不能包含控制字符');return name;}
  function createPacket(item,name,drafts){const owner=createSection(item),selected=values(item.id,owner||{},drafts);if(!owner)throw Error('当前玩法不能创建房间');for(const field of owner.fields||[])if(!fieldOptions(field,selected).some(option=>String(option.value)===selected[field.id]))throw Error('请选择兼容的模式和地图');const packet=JSON.stringify([roomName(name),(owner.fields||[]).map(field=>selected[field.id])]);if(packet.length>128)throw Error('房间名称或选项过长');return packet;}
  function settings(item){return sections(item).filter(section=>['room','settings'].includes(role(section))).flatMap(section=>(section.fields||[]).flatMap(field=>{const action=(section.actions||[]).find(a=>['difficulty','enemy'].includes(a.action)&&a.value==='{'+field.id+'}');return action?[{field,action,owner:section}]:[];}));}
  function permissions(snapshot,item,room){const self=snapshot.self?.uuid||item?.state?.self||'';const host=!!room&&room.host===self,waitingRoom=waiting(room),allowed=snapshot.allowed!==false;return {host,waiting:waitingRoom,configure:host&&waitingRoom&&allowed,invite:host&&waitingRoom&&allowed&&(room.count??room.roster?.length??0)<(room.capacity??4)};}
  function roomProfile(item,room){const owner=createSection(item),raw={mode:room.mode,map:room.map,difficulty:String(room.difficulty??room.enemyTier??'')};const label=id=>{const field=owner?.fields?.find(f=>f.id===id),option=fieldOptions(field||{},raw).find(o=>String(o.value)===String(raw[id]));return option?.label||raw[id]|| (id==='mode'?'合作挑战':id==='map'?(room.mapTitle||room.title||'默认地图'):'默认难度');};return {mode:label('mode'),map:label('map'),difficulty:label('difficulty')};}
  const canonicalUuid=value=>typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
  const validAiRevision=value=>typeof value==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(value)&&BigInt(value)<9223372036854775807n;
  function aiControls(snapshot,item,room){
    const supported=snapshot.roomAiVersion===1&&['outbreak','zombie-challenge'].includes(item?.id)&&item?.roomCapabilities?.aiTeammates?.supported===true&&room?.ai?.supported===true;
    const editable=supported&&room.ai.editable===true&&room.ai.locked===false&&permissions(snapshot,item,room).host&&!blocked(snapshot,item.id,{enabled:true})&&validAiRevision(room.ai.revision)&&canonicalUuid(room.session);
    return {supported,editable,canAdd:editable&&(room.count??Infinity)<(room.capacity??0),canRemove:editable,revision:room?.ai?.revision};
  }
  function aiPacket(snapshot,item,room,op,seat){
    const controls=aiControls(snapshot,item,room);
    if(!controls.editable||!['aiAdd','aiRemove'].includes(op)||op==='aiAdd'&&!controls.canAdd)throw Error('AI 队友列表或房间权限已变化，请刷新后重试');
    if(op==='aiRemove'&&(!canonicalUuid(seat)||!(room.roster||[]).some(row=>row.kind==='ai'&&row.aiId===seat)))throw Error('AI 席位已变化，请刷新后重试');
    return JSON.stringify([room.session,controls.revision,...(op==='aiRemove'?[seat]:[])]);
  }
  function aiConfirmed(room,operation){
    if(!operation.ai)return true;
    if(room?.session!==operation.session||room.ai?.revision!==operation.nextAiRevision)return false;
    const ids=(room.roster||[]).filter(row=>row.kind==='ai').map(row=>row.aiId),before=operation.beforeAiIds;
    if(operation.ai==='aiAdd')return ids.length===before.length+1&&before.every(id=>ids.includes(id))&&ids.filter(id=>!before.includes(id)).length===1;
    return !ids.includes(operation.aiId)&&ids.length===before.length-1&&ids.every(id=>before.includes(id));
  }
  const api={esc,safeGame,cleanContext,values,fieldOptions,sections,createSection,ownRoom,role,waiting,actionValue,blocked,platformText,currentAction,defaultName,roomName,createPacket,settings,permissions,roomProfile,aiControls,aiPacket,aiConfirmed};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.MuxiMinigamesModel=api;
})(typeof window!=='undefined'?window:globalThis);

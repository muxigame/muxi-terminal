(function(root,factory){if(typeof module==='object' && module.exports)module.exports=factory();else root.MuxiFriendsController=factory();})(typeof globalThis==='object'?globalThis:this,()=>{
  const UID=/^[1-9][0-9]{4,15}$/;
  const ops=new Set(['request','accept','cancel','remove','block','unblock']);
  function uid(value){if(typeof value!=='string' || !UID.test(value))throw new Error('请输入 5–16 位平台 UID');return value;}
  function snapshot(value){
    if(!value || value.version!==1 || value.authenticated!==true || value.identityMode!=='platform-uid')throw new Error('好友服务版本或登录状态不匹配');uid(value.selfUid);
    for(const [key,max] of [['friends',500],['incoming',100],['outgoing',100],['blocked',1000],['onlinePlayers',1024]]){
      if(!Array.isArray(value[key]) || value[key].length>max)throw new Error('好友列表超出协议限制');const seen=new Set();
      for(const peer of value[key]){uid(peer.uid);if(peer.uid===value.selfUid || seen.has(peer.uid) || typeof peer.displayName!=='string' || [...peer.displayName].length>80 || peer.gameName!==peer.uid || typeof peer.uuid!=='string' || typeof peer.online!=='boolean')throw new Error('好友玩家身份不匹配');seen.add(peer.uid);}
    }
    if(typeof value.presenceAvailable!=='boolean' || value.incoming.length+value.outgoing.length>100)throw new Error('好友快照不匹配');return value;
  }
  function key(){
    if(typeof globalThis.crypto?.randomUUID==='function')return crypto.randomUUID();
    const bytes=new Uint8Array(16);crypto.getRandomValues(bytes);bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
    const hex=[...bytes].map(x=>x.toString(16).padStart(2,'0')).join('');return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join('-');
  }
  function create({invoke,onChange=()=>{},makeKey=key}){
    let epoch=0,closed=false;let state={snapshot:null,busy:false,error:'',lastReceipt:null,retry:null,needsRefresh:true};
    const current=e=>!closed && epoch===e;
    function update(next){state={...state,...next};onChange({...state});}
    async function refresh(){
      if(state.busy)return false;closed=false;const e=++epoch;update({busy:true,error:''});
      try{const next=snapshot(await invoke('friends.snapshot'));if(!current(e))return false;
        update({snapshot:next,busy:false,needsRefresh:false,retry:state.retry?.selfUid===next.selfUid?state.retry:null});return true;
      }catch(error){if(current(e))update({busy:false,error:error.message,needsRefresh:true});throw error;}
    }
    async function mutate(op,target,retry=null){
      target=uid(target);if(closed || state.busy)return false;
      if(!ops.has(op) || !state.snapshot || state.needsRefresh || target===state.snapshot.selfUid || (state.retry && !retry))throw new Error('请先刷新并核实未确认的操作');
      const command=retry || {op,uid:target,request:makeKey(),selfUid:state.snapshot.selfUid};
      if(command.selfUid!==state.snapshot.selfUid || command.op!==op || command.uid!==target)throw new Error('账号已变化，请重新操作');
      const e=++epoch;update({busy:true,error:'',lastReceipt:null});
      try{
        const receipt=await invoke('friends.action:'+JSON.stringify({op,uid:target,request:command.request}));
        if(!current(e))return false;
        if(receipt?.version!==1 || receipt.request!==command.request || receipt.ok!==true || receipt.status!=='ok' || typeof receipt.changed!=='boolean')throw new Error('操作回执不匹配，结果未确认');
        update({lastReceipt:receipt,retry:null});
        const next=snapshot(await invoke('friends.snapshot'));if(!current(e))return false;
        if(next.selfUid!==command.selfUid)throw new Error('账号已变化，请重新打开好友');
        update({snapshot:next,busy:false,error:'',needsRefresh:false});return true;
      }catch(error){if(current(e))update({busy:false,error:state.lastReceipt?'操作回执已确认，列表刷新失败：'+error.message:'操作结果未确认，请先刷新核实：'+error.message,retry:state.lastReceipt?null:command,needsRefresh:true});throw error;}
    }
    function retry(){if(!state.retry)throw new Error('没有待核实操作');return mutate(state.retry.op,state.retry.uid,state.retry);}
    function abandon(){update({retry:null,lastReceipt:null});}
    function dispose(){closed=true;++epoch;update({busy:false,snapshot:null,retry:null,lastReceipt:null,needsRefresh:true});}
    return {refresh,mutate,retry,abandon,dispose,getState:()=>({...state})};
  }
  function peers(value,source){
    if(!value || value.version!==1 || typeof value.presenceAvailable!=='boolean' || !Array.isArray(value.onlinePlayers) || !Array.isArray(value.friends) || !['online','friends'].includes(source))throw new Error('邀请来源快照不匹配');
    if(!value.presenceAvailable)return [];const friends=new Set(value.friends.map(peer=>uid(peer.uid)));
    return value.onlinePlayers.filter(peer=>UID.test(peer.uid) && peer.online===true && peer.gameName===peer.uid && (source==='online' || friends.has(peer.uid)));
  }
  return {create,snapshot,uid,peers,key};
});

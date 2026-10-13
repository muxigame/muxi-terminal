(()=>{if(window.__nativeShop?.invokePatched)return;const originalInvoke=window.muxi.invoke.bind(window.muxi),original=window.muxiTerminalQuery,H='11111111-1111-4111-8111-111111111111';const f=window.__nativeShop={missing:false,seq:0,invokePatched:true};
const game=id=>({id,title:id==='horse_racing'?'赛马商店':'飞行商店',state:{rooms:[]},ui:{lobby:{sections:[]},shop:{sections:[{title:'原生MCEF界面夹具',cards:Array.from({length:15},(_,i)=>({title:'测试商品 '+i,text:'只测显示，不购买、不修改游戏数据',preview:i===0&&id==='horse_racing'?{kind:'preview',id:f.missing?'muxi_horse_racing:textures/gui/umamusume_preview/approved/missing_qa.gif':'muxi_horse_racing:textures/gui/umamusume_preview/approved/uma_silence_suzuka_v8_8e62cee00bb1.gif',label:'已批准原生GIF'}:undefined,previewFallback:i===0&&id==='horse_racing'?{kind:'resource',id:'muxi_horse_racing:textures/gui/umamusume_preview/uma_silence_suzuka.png',label:'PNG回退'}:undefined,actions:[]}))}]}}});
const snapshot=()=>({supported:true,loading:false,protocol:2,roomUiVersion:1,actionSupported:true,allowed:true,activeGame:'',self:{uuid:H,name:'Private UI fixture'},platform:{available:true,points:String(++f.seq)},games:[game('horse_racing'),game('flight')]});
window.muxi.invoke=command=>{
if(command==='games.request')return Promise.resolve({ok:true});
if(command==='games.context')return Promise.resolve({game:'horse_racing',page:'shop',drafts:{}});
if(command==='games.snapshot')return Promise.resolve(snapshot());
if(command.startsWith('games.context:'))return Promise.resolve({ok:true});
if(command==='friends.invites.snapshot')return Promise.resolve({version:1,enabled:false,invitations:[]});
return originalInvoke(command);};
})();
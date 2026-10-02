const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const M=require('../src/main/resources/assets/muxi_terminal/html/terminal/apps/minigames/model.js');
const source=fs.readFileSync(path.join(__dirname,'../src/main/resources/assets/muxi_terminal/html/terminal/apps/minigames/app.js'),'utf8');
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));let passed=0;
function check(name,fn){fn();passed++;}
class Element{
  constructor(id){this.id=id;this.innerHTML='';this.textContent='';this.hidden=false;this.listeners={};this.dataset={};this.classes=new Set();this.classList={contains:v=>this.classes.has(v),toggle:(v,on)=>on?this.classes.add(v):this.classes.delete(v)};}
  addEventListener(name,fn){this.listeners[name]=fn;}setAttribute(){}focus(){}
  querySelectorAll(){return tabs;}
}
const nodes=new Map(),el=id=>{if(!nodes.has(id))nodes.set(id,new Element(id));return nodes.get(id);};
const tabs=[new Element('lobby'),new Element('shop')];tabs[0].dataset.mgPage='lobby';tabs[1].dataset.mgPage='shop';el('games').classes.add('page-active');
const create={role:'create',title:'Create',text:'',fields:[{id:'mode',selected:'SURVIVAL',options:[{value:'SURVIVAL',label:'Survival'}]},{id:'map',selected:'lab',options:[{value:'lab',label:'Research Lab'},{value:'camp',label:'Mysterious Camp'}]},{id:'difficulty',selected:'1',options:[{value:'1',label:'Normal'}]}],actions:[{label:'Create room',action:'createConfigured',value:'',enabled:true,jsonFields:true,fields:'map,difficulty,mode'}]};
const team={role:'room',title:'Team',text:'',actions:[{label:'Leave',action:'leave',value:'',enabled:true,safe:true,confirm:'Restore inventory?'}]};
const invite={role:'invite',title:'Invite teammates',text:'',cards:[{title:'Guest',text:'Online',actions:[{label:'Invite',action:'invite',value:'guest-uuid',enabled:true}]}]};
const goods={title:'Exchange',text:'',cards:[{title:'Bandage',text:'3 coins',actions:[{label:'Redeem',action:'buy',value:'bandage:7:3',enabled:true}]}]};
const server={protocol:2,supported:true,actionSupported:true,allowed:true,games:[{id:'game-a',title:'Zombie',state:{rooms:[]},ui:{currencies:[{label:'Coins',scope:'local',value:'8',note:'not platform'}],lobby:{sections:[create,{role:'rooms',title:'Rooms',cards:[]}]},shop:{title:'Zombie exchange',sections:[goods]}}},{id:'game-b',title:'Outbreak',state:{rooms:[]},ui:{lobby:{sections:[]},shop:{title:'Campaign supplies',sections:[{title:'Supplies',text:'map pickups'}]}}}],platform:{available:true,points:'9223372036854775807'}};
let requests=[],receiptDelay=0,newReceipt,timeout=false;const events={};
const win={MuxiMinigamesModel:M,addEventListener:(name,fn)=>events[name]=fn,muxi:{invoke:command=>{
  requests.push(command);
  if(timeout&&command.startsWith('games.action:'))return new Promise(()=>{});
  if(command==='games.context')return Promise.resolve({game:'',page:'lobby',drafts:{}});
  if(command==='games.snapshot'){
    if(newReceipt&&--receiptDelay<=0){server.operation=newReceipt;newReceipt=null;}
    return Promise.resolve(JSON.parse(JSON.stringify(server)));
  }
  if(command.startsWith('games.action:')){
    const action=JSON.parse(command.slice(13));const request='request-'+requests.length;receiptDelay=2;server.operation={request,status:'pending',notice:'server pending'};
    if(action.action==='createConfigured'){assert.deepEqual(JSON.parse(action.value),{map:'camp',difficulty:'1',mode:'SURVIVAL'});server.activeGame='game-a';server.games[0].state.rooms=[{mine:true,id:'room1',phase:'WAITING',lobbyWaiting:true}];server.games[0].ui.lobby.sections=[create,team,invite];}
    newReceipt={request,status:action.action==='buy'?'failed':'completed',notice:action.action==='buy'?'insufficient local coins':'server confirmed'};
    return Promise.resolve({ok:true,request});
  }
  return Promise.resolve({ok:true});
}}};
vm.runInNewContext(source,{window:win,document:{readyState:'complete',getElementById:el},setTimeout,clearTimeout,setInterval:()=>1,clearInterval:()=>{},Promise,Date,Map,JSON,Error});
function click(kind,value){const dataset={};dataset[kind]=value;const target={closest:selector=>selector===({'mgCreate':'[data-mg-create]','mgCancel':'[data-mg-cancel]','mgGame':'[data-mg-game]','mgMap':'[data-mg-map]','mgPage':'[data-mg-page]','mgAction':'[data-mg-action]','mgStage':'[data-mg-stage]'}[kind])?{dataset}:null};el('games').listeners.click({target});}
function action(label){const html=el('mg-content').innerHTML;const match=html.match(new RegExp('data-mg-action="([0-9]+)"[^>]*>'+label+'<'));assert.ok(match,'Action visible: '+label);click('mgAction',match[1]);}
(async()=>{
  await wait(30);
  check('shared terminal invoke reads both modes without cefQuery',()=>{assert.match(el('mg-games').innerHTML,/Zombie/);assert.match(el('mg-games').innerHTML,/Outbreak/);assert.ok(requests.includes('games.request'));assert.doesNotMatch(source,/window\.cefQuery|window\.muxiTerminalQuery/);});
  check('default lobby is only rooms and create entry, without wizard or invitations',()=>{assert.equal(el('mg-games').hidden,true);assert.equal(el('mg-stepbar').hidden,true);assert.equal(el('mg-room-invitations').hidden,true);assert.match(el('mg-content').innerHTML,/data-mg-create/);assert.doesNotMatch(el('mg-content').innerHTML,/data-mg-choice|Invite teammates/);});
  click('mgCreate','');check('create entry opens the separate mode selection',()=>assert.equal(el('mg-games').hidden,false));
  click('mgCancel','');check('cancel returns to clean lobby without submitting',()=>{assert.equal(el('mg-games').hidden,true);assert.ok(!requests.some(command=>command.startsWith('games.action:')));});
  click('mgCreate','');click('mgGame','game-a');check('mode proceeds to server supplied maps using existing cards',()=>{assert.match(el('mg-content').innerHTML,/Research Lab/);assert.match(el('mg-content').innerHTML,/Mysterious Camp/);assert.match(el('mg-content').innerHTML,/class="guide-card"/);});
  click('mgMap','camp');check('map proceeds to difficulty confirmation with selected map',()=>{assert.match(el('mg-content').innerHTML,/Mysterious Camp/);assert.doesNotMatch(el('mg-content').innerHTML,/data-mg-map=/);});
  action('Create room');action('Create room');await wait(30);check('transport callback alone does not open room or announce completion',()=>{assert.doesNotMatch(el('mg-notice').textContent,/server confirmed/);assert.doesNotMatch(el('mg-content').innerHTML,/Invite teammates/);assert.equal(el('mg-room-invitations').hidden,true);assert.equal(requests.filter(command=>command.includes('"action":"createConfigured"')).length,1);});await wait(400);
  check('server receipt opens room and invite controls',()=>{assert.match(el('mg-content').innerHTML,/Invite teammates/);assert.match(el('mg-notice').textContent,/server confirmed/);});
  action('Invite');await wait(400);check('invite reaches server with provider UUID',()=>assert.ok(requests.some(cmd=>cmd.includes('"action":"invite"')&&cmd.includes('guest-uuid'))));
  click('mgPage','shop');check('one internal shop contains existing providers and separate platform points',()=>{assert.match(el('mg-content').innerHTML,/Zombie exchange/);assert.match(el('mg-content').innerHTML,/Campaign supplies/);assert.match(el('mg-platform').innerHTML,/9223372036854775807/);});
  action('Redeem');await wait(400);check('purchase failure comes from server receipt',()=>assert.match(el('mg-notice').textContent,/insufficient local coins/));
  click('mgPage','lobby');action('Leave');check('safe return requires confirmation',()=>assert.equal(el('mg-confirm').hidden,false));
  team.actions[0].enabled=false;await win.MuxiMinigamesApp.activate();const before=requests.filter(c=>c.startsWith('games.action:')).length;el('mg-confirm-ok').listeners.click();await wait(20);
  check('confirmation revalidates latest room state',()=>assert.equal(requests.filter(c=>c.startsWith('games.action:')).length,before));
  server.actionSupported=false;await win.MuxiMinigamesApp.activate();check('version mismatch is explicit and not a participation-ban message',()=>{assert.equal(M.blocked(server,'game-a',{enabled:true}),true);assert.match(el('mg-notice').textContent,/版本不匹配/);});
  server.loading=true;server.actionSupported=true;await win.MuxiMinigamesApp.activate();check('loading remains distinct from denial',()=>assert.match(el('mg-notice').textContent,/等待服务器/));server.loading=false;
  server.activeGame='';el('mg-back').listeners.click();check('back returns to the clean room lobby',()=>assert.equal(el('mg-games').hidden,true));
  server.actionSupported=true;team.actions[0].enabled=true;await win.MuxiMinigamesApp.activate();click('mgPage','shop');timeout=true;action('Redeem');await wait(4150);
  check('lost native callback times out without allowing an uncertain mutation to be repeated',()=>{assert.match(el('mg-notice').textContent,/超时/);assert.match(el('mg-content').innerHTML,/data-mg-action="\d+" disabled/);});
  events.pagehide();console.log(`Minigames APP integration: ${passed} interactions passed (synthetic DOM/bridge; not MCEF GUI)`);
})().catch(error=>{events.pagehide();console.error(error);process.exitCode=1;});

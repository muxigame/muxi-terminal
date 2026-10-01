const guides = [
  {
    id:'create', name:'机械动力 Create', short:'CR', category:'工程 / 自动化',
    icon:'create:textures/item/andesite_alloy.png', colors:['#b48a4b','#392c1d'], manual:'manual.open:create', manualLabel:'打开 Ponder 教程索引',
    summary:'用齿轮、传动轴和机械结构，把自动化真正“搭”在世界里。',
    why:'Create 的核心不是机器方块堆叠，而是可视化的机械系统。它非常适合作为整合包中期的大型工程主线。',
    steps:['先从安山合金、齿轮与传动轴开始，理解动力如何传递。','做一个水车或其他稳定动力源，再观察应力容量。','先完成一个小型自动化，例如自动加工或简单物品运输，再扩建工厂。'],
    manualHint:'Create 没有 Patchouli 书，但有完整的 Ponder 动画教程系统；终端会直接打开 Ponder 索引。'
  },
  {
    id:'iceandfire', name:'冰火传说 Ice and Fire', short:'IF', category:'探索 / 战斗',
    icon:'iceandfire:textures/item/bestiary.png', colors:['#447c9c','#251d25'], manual:'manual.open:iceandfire', manualLabel:'打开怪物图鉴',
    summary:'龙、神话生物、龙骨武器与高风险探索内容。',
    why:'它把世界探索变成真正需要准备的远征。龙巢、龙穴和稀有生物都会给装备成长带来明显变化。',
    steps:['前期不要直接挑战成年龙，先准备远程手段、药水和撤退路线。','收集龙相关素材时优先确认目标生物阶段与环境优势。','获得龙骨与龙鳞后再逐步进入高阶装备路线。'],
    manualHint:'使用玩家背包中已有的《怪物图鉴》数据打开；没有图鉴时终端不会凭空解锁内容。'
  },
  {
    id:'waystones', name:'传送石碑 Waystones', short:'WS', category:'交通 / 探索',
    icon:'waystones:textures/item/warp_stone.png', colors:['#8263b4','#27223b'],
    summary:'激活石碑后建立属于自己的快速旅行网络。',
    why:'整合包世界很大，石碑会成为长期探索最重要的基础设施之一。',
    steps:['遇到石碑时优先激活并给位置起容易识别的名字。','在基地、资源区和关键维度入口附近建立自己的石碑。','终端与地图后续会继续把已激活石碑整合进统一导航。'],
    manualHint:'当前版本没有独立 Patchouli / 自带手册，主要通过物品提示与 JEI 学习。'
  },
  {
    id:'farmersdelight', name:"农夫乐事 Farmer's Delight", short:'FD', category:'生存 / 食物',
    icon:'farmersdelight:textures/item/iron_knife.png', colors:['#74944d','#2c321e'],
    summary:'更完整的烹饪、作物处理和高质量食物体系。',
    why:'它让食物从“补饥饿值”变成一条稳定的生存与探索补给线。',
    steps:['先做砧板和基础厨具，熟悉原料处理方式。','建立小型农田并保留多种作物，不要只种一种高饱食食物。','开始远征前准备成套料理，观察不同食物带来的持续收益。'],
    manualHint:'当前安装版本没有独立游戏内手册，配方入口以 JEI 与原版配方书为主。'
  },
  {
    id:'twilightforest', name:'暮色森林 The Twilight Forest', short:'TF', category:'维度 / Boss',
    icon:'twilightforest:textures/item/magic_map.png', colors:['#4b935e','#182c21'],
    summary:'一个有明确推进顺序的经典冒险维度。',
    why:'它很适合当作一条独立冒险线：探索、Boss、区域解锁和战利品都具有清晰阶段感。',
    steps:['准备稳定装备与补给后再建立暮色森林入口。','推进时留意区域限制，不要只按地图直线冲向下一个 Boss。','保留 Boss 战利品与关键掉落，它们通常与后续推进有关。'],
    manualHint:'当前版本主要用进度/成就系统提示推进顺序，没有独立 Patchouli 手册。'
  },
  {
    id:'alexsmobs', name:"Alex's Mobs", short:'AM', category:'生态 / 生物',
    icon:'alexsmobs:textures/item/animal_dictionary.png', colors:['#ba7350','#3b241b'], manual:'manual.open:alexsmobs', manualLabel:'打开动物词典',
    summary:'大量有独特行为、掉落和互动机制的新生物。',
    why:'很多生物不是单纯的“新怪”，而是拥有特殊生态、驯服方式或实用掉落。',
    steps:['第一次遇到陌生生物时先观察行为，不要默认它是敌对目标。','留意稀有掉落和特殊互动，它们常用于独特装备或工具。','探索不同群系，因为多数新增生物有明确的生态分布。'],
    manualHint:'直接调用 Alex’s Mobs 自带的 Animal Dictionary 界面。'
  },
  {
    id:'starcatcher', name:'星钓 Starcatcher', short:'SC', category:'钓鱼 / 收集',
    icon:'starcatcher:textures/item/starcatcher_guide.png', colors:['#4f8eb5','#173046'], manual:'manual.open:starcatcher', manualLabel:'打开钓鱼指南',
    summary:'围绕钓鱼小游戏、鱼种收集、鱼竿升级、鱼钩与鱼饵构建的一整套钓鱼玩法。',
    why:'Starcatcher 不只是增加几条鱼，它把钓鱼做成了长期收集线：不同环境、鱼种、装备和记录都会逐步丰富你的渔获图鉴。',
    steps:['先制作或取得基础钓竿与 Starcatcher Guide，熟悉抛竿和小游戏。','开始收集不同区域的鱼，同时尝试鱼钩、鱼饵和钓竿升级。','利用指南记录鱼种，再逐步挑战特殊天气、稀有鱼和更高级钓具。'],
    manualHint:'调用 Starcatcher 自带的 Fishing Guide GUI，不依赖 Patchouli。'
  },
  {
    id:'modulargolems', name:'模块化傀儡 Modular Golems', short:'MG', category:'随从 / 构筑',
    icon:'modulargolems:textures/item/command_wand.png', colors:['#927c62','#30271f'], manual:'manual.open:patchouli:modulargolems:golem_guide', manualLabel:'打开傀儡指南',
    summary:'制作不同材料、装备和升级组合的傀儡，让随从承担战斗、护卫与功能性工作。',
    why:'傀儡真正有趣的地方在“组装”：材料决定基础能力，装备和升级继续改变定位，可以围绕自己的基地和战斗方式做专属配置。',
    steps:['先阅读傀儡指南，理解金属傀儡、人形傀儡与材料体系。','制作基础傀儡后，用指挥杖熟悉跟随、驻守和行为控制。','再逐步添加升级、装备与不同材料，不要一开始就追求最高阶数值。'],
    manualHint:'已核实 Patchouli 书 ID：modulargolems:golem_guide。'
  },
  {
    id:'touhoumaid', name:'车万女仆 Touhou Little Maid', short:'TL', category:'随从 / 生活',
    icon:'touhou_little_maid:textures/item/hakurei_gohei.png', colors:['#bf5260','#3e2027'], manual:'manual.open:patchouli:touhou_little_maid:memorizable_gensokyo', manualLabel:'打开记忆中的幻想乡',
    summary:'召唤并培养女仆，让她们承担战斗、农务、搬运与大量生活辅助工作。',
    why:'女仆是一条非常完整的长期随从路线：从召唤、好感与任务，到装备、背包、模型和各种扩展功能，都能逐步形成自己的伙伴体系。',
    steps:['先通过手册了解祭坛、女仆召唤与基础交互。','给女仆安排清晰任务并准备合适工具、食物和装备。','熟悉好感、复活与任务系统后，再扩展背包、模型和战斗能力。'],
    manualHint:'已核实 Patchouli 书 ID：touhou_little_maid:memorizable_gensokyo。'
  },
  {
    id:'cataclysm', name:"灾变 L_Ender's Cataclysm", short:'CT', category:'Boss / 高难战斗',
    icon:'cataclysm:textures/item/monstrous_eye.png', colors:['#923b3b','#2a1115'],
    summary:'大型结构、阶段化 Boss 与高强度装备奖励组成的高难度冒险内容。',
    why:'灾变更接近整合包后期的 Boss 挑战线。敌人的机制、场地和伤害都明显高于普通探索内容，准备工作本身就是玩法的一部分。',
    steps:['先提升基础装备和生存能力，不要把灾变 Boss 当普通精英怪处理。','根据目标 Boss 准备抗性、远程方案、治疗和撤退手段。','击败 Boss 后保留关键材料，它们通常会进入下一阶段装备路线。'],
    manualHint:'已检查当前安装的 3.33 版本：没有 Patchouli 或独立游戏内手册，终端指南将承担基础说明入口。'
  }
];

const $ = s => document.querySelector(s);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const sleep = ms => new Promise(r => setTimeout(r, ms));
let contentView=location.hash!=='#/home' && !!location.hash;
if(contentView)document.body.classList.add('content-view');

async function native(command){
  const deadline=Date.now()+2500;
  while(typeof window.cefQuery!=='function' && Date.now()<deadline) await sleep(50);
  return new Promise((resolve,reject)=>{
    if(typeof window.cefQuery!=='function'){reject(new Error('终端桥接尚未就绪，请稍后重试'));return;}
    window.cefQuery({request:command,onSuccess:r=>{try{resolve(JSON.parse(r))}catch{resolve(r)}},onFailure:(c,m)=>reject(new Error(`${c}: ${m}`))});
  });
}

window.muxi={
  invoke:native,
  home:()=>native('terminal.home'),
  close:()=>native('terminal.close'),
  openManual:command=>native(command),
  openExternal:url=>native('terminal.external:'+url)
};

function show(id){
  const page=$('#'+id);
  if(!page)return;
  document.querySelectorAll('.page').forEach(p=>p.classList.remove('page-active'));
  page.classList.add('page-active');
  if(id==='tasks') refreshTasks(true);
  if(id==='games')window.MuxiMinigamesApp?.activate();
}

function navigate(id,source){
  if(typeof window.cefQuery==='function'){
    if(contentView && id==='home'){native('terminal.home').catch(error=>setStatus(error.message));return;}
    if(!contentView && ['tasks','guide','games'].includes(id)){
      if(window.terminalLaunch)window.terminalLaunch({kind:'builtin',id,name:id==='games'?'小游戏':id==='tasks'?'任务':'游戏指南',source}).catch(error=>setStatus(error.message));
      else native('terminal.app:'+id).catch(error=>setStatus(error.message));return;
    }
  }
  if(location.hash!==`#/${id}`) location.hash=`/${id}`;
  show(id);
}

document.querySelectorAll('[data-open]').forEach(el=>el.addEventListener('click',()=>navigate(el.dataset.open,el)));

const resourceCache=new Map();
async function resourceData(id){
  if(resourceCache.has(id))return resourceCache.get(id);
  const promise=native('resource.data:'+id).then(data=>{
    if(typeof data==='string' && /^data:image\//.test(data)) return data;
    resourceCache.delete(id);return null;
  }).catch(()=>{resourceCache.delete(id);return null;});
  resourceCache.set(id,promise);
  return promise;
}

async function hydrateImages(root=document){
  const nodes=[...root.querySelectorAll('img[data-resource]')];
  await Promise.all(nodes.map(async img=>{
    if(img.dataset.loaded)return;
    const data=await resourceData(img.dataset.resource);
    if(data){img.dataset.loaded='1';img.src=data;img.hidden=false;const fallback=img.parentElement?.querySelector('.mod-fallback');if(fallback)fallback.hidden=true;}
    else img.hidden=true;
  }));
}

function iconStyle(g){return `--icon1:${g.colors[0]};--icon2:${g.colors[1]}`;}
function guideIcon(g,detail=false){
  return `<span class="mod-icon" style="${iconStyle(g)}"><span class="mod-fallback">${esc(g.short)}</span><img hidden data-resource="${esc(g.icon)}" alt=""></span>`;
}

function renderGuides(filter=''){
  const q=filter.trim().toLowerCase();
  const root=$('#guideList');
  root.innerHTML=guides.filter(g=>(g.name+' '+g.category+' '+g.summary).toLowerCase().includes(q)).map(g=>`
    <button class="guide-card" data-guide="${g.id}">
      ${guideIcon(g)}
      <span><h3>${esc(g.name)}</h3><p>${esc(g.summary)}</p><span class="card-meta"><i class="pill">${esc(g.category)}</i><i class="pill recommended">入门推荐</i></span></span>
    </button>`).join('');
  root.querySelectorAll('[data-guide]').forEach(el=>el.addEventListener('click',()=>openGuide(el.dataset.guide)));
  hydrateImages(root);
}

function openGuide(id){
  const g=guides.find(x=>x.id===id);if(!g)return;
  $('#detailTitle').textContent=g.name;
  $('#detailCategory').textContent=g.category;
  $('#detailSummary').textContent=g.summary;
  $('#detailWhy').textContent=g.why;
  $('#detailSteps').innerHTML=g.steps.map((s,i)=>`<div class="step"><b>${i+1}</b><span>${esc(s)}</span></div>`).join('');
  const holder=$('#detailIcon');holder.style.cssText=iconStyle(g);holder.innerHTML=`<span class="mod-fallback">${esc(g.short)}</span><img hidden data-resource="${esc(g.icon)}" alt="">`;
  hydrateImages($('#guideDetail'));
  const manual=$('#openManual');
  manual.hidden=!g.manual;
  manual.textContent=g.manualLabel||'打开游戏内手册';
  manual.onclick=()=>g.manual&&window.muxi.openManual(g.manual).catch(error=>setStatus(error.message));
  $('#manualHint').textContent=g.manualHint||'';
  navigate('guideDetail');
}

$('#guideSearch').addEventListener('input',e=>renderGuides(e.target.value));

function itemTexture(item){
  const split=String(item).split(':');
  if(split.length!==2)return null;
  return `${split[0]}:textures/item/${split[1]}.png`;
}

let taskState=null;
async function refreshTasks(requestFirst=false){
  if(requestFirst) native('tasks.request').catch(()=>{});
  if(requestFirst) await sleep(80);
  try{
    const state=await native('tasks.snapshot');
    if(state&&typeof state==='object'){taskState=state;renderTasks(state);}
  }catch(error){
    $('#taskMeta').textContent='任务服务不可用';
    $('#taskList').innerHTML=`<div class="task-empty">${esc(error.message)}</div>`;
  }
}

function rewardHtml(reward){
  const texture=itemTexture(reward.item);
  return `<span class="reward-chip">${texture?`<img hidden data-resource="${esc(texture)}" alt="">`:''}<span>${esc(reward.name)}${reward.count>1?` ×${reward.count}`:''}</span></span>`;
}

function renderTasks(state){
  const rows=state.rows||[];
  $('#taskMeta').textContent=state.error|| (state.supported?'服务器任务已连接':'当前服务器不支持任务协议');
  $('#taskCountdown').textContent=state.loading?'正在读取任务…':`刷新倒计时 ${state.remaining||'--:--:--'}`;
  $('#rerollAllowance').textContent=state.loading?'':`今日可换 ${state.rerollsRemaining??0} 次`;
  const list=$('#taskList');
  if(state.loading){list.innerHTML='<div class="task-empty">正在读取每日任务…</div>';}
  else if(!rows.length){list.innerHTML=`<div class="task-empty">${esc(state.error||state.notice||'今天没有可显示的每日任务。')}</div>`;}
  else{
    list.innerHTML=rows.map(row=>{
      const percent=Math.max(0,Math.min(100,Math.round((row.progress/Math.max(1,row.goal))*100)));
      const classes=['task-card',row.tracked?'task-tracked':'',row.ready?'task-ready':'',row.claimed?'task-claimed':''].filter(Boolean).join(' ');
      const rewards=(row.rewards||[]).map(rewardHtml).join('')+(row.experienceLevels?`<span class="reward-chip reward-xp">经验等级 +${row.experienceLevels}</span>`:'');
      return `<article class="${classes}" data-task="${esc(row.id)}">
        <div class="task-row-head"><div class="task-title">${esc(row.title)}${row.hard?'<span class="task-hard">高难</span>':''}</div><div class="task-progress">${row.progress}/${row.goal}${esc(row.unit||'')}</div></div>
        <div class="task-progress-bg"><div class="task-progress-fill" style="width:${percent}%"></div></div>
        <p class="task-description">${esc(row.description)}</p>
        <div class="task-bottom"><div class="task-rewards">${rewards||'<span class="reward-chip">无额外奖励</span>'}</div>
        <div class="task-actions">
          <button data-action="track" class="${row.tracked?'tracked-button':''}">${row.tracked?'取消追踪':'追踪'}</button>
          <button data-action="reroll" ${row.rerollable?'':'disabled'}>换一项</button>
          <button data-action="claim" class="${row.ready?'claim-ready':''}" ${row.ready?'':'disabled'}>${row.claimed?'已领取':'领取'}</button>
        </div></div>
      </article>`;
    }).join('');
    hydrateImages(list);
  }

  const mainline=state.mainline||[];
  $('#mainlineList').innerHTML=mainline.length?mainline.map(item=>`<article class="task-card mainline-card"><div class="task-title">${esc(item.title)}</div><p class="task-description" style="margin-top:8px">${esc(item.description)}</p></article>`).join(''):'<div class="task-empty">暂无主线任务。</div>';
}

$('#taskList').addEventListener('click',async event=>{
  const button=event.target.closest('button[data-action]');if(!button)return;
  const card=button.closest('[data-task]');if(!card)return;
  const id=card.dataset.task, action=button.dataset.action;
  if(action==='reroll'&&button.dataset.confirm!=='1'){
    button.dataset.confirm='1';button.textContent='确认换一项';
    setTimeout(()=>{if(button.isConnected){button.dataset.confirm='';button.textContent='换一项';}},2500);
    return;
  }
  button.disabled=true;
  try{
    const command=action==='track'?`tasks.track:${id}`:action==='claim'?`tasks.claim:${id}`:`tasks.reroll:${id}`;
    await native(command);await sleep(120);await refreshTasks(false);
  }catch(error){setStatus(error.message);button.disabled=false;}
});

document.querySelectorAll('[data-task-tab]').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('[data-task-tab]').forEach(b=>b.classList.toggle('task-tab-active',b===button));
  const daily=button.dataset.taskTab==='daily';
  $('#dailyTasksPanel').hidden=!daily;$('#mainlineTasksPanel').hidden=daily;
}));

$('#passportApp').addEventListener('click',event=>{
  const opening=window.terminalLaunch?window.terminalLaunch({kind:'account',id:'passport',name:'木夕账户',source:event.currentTarget}):native('passport.open');
  opening.catch(error=>setStatus(error.message));
});


// Full keyboard / handheld navigation. Arrow keys move between actionable
// controls, Enter activates, Backspace/Escape go back, Delete always returns
// to the terminal home screen.
let navIndex=0;
function navItems(){
  const page=document.querySelector('.page.page-active');
  if(!page)return [];
  return [...page.querySelectorAll('button:not(:disabled),input:not(:disabled),[tabindex]:not([tabindex="-1"])')]
    .filter(el=>!el.hidden && el.offsetParent!==null);
}
function selectNav(index,scroll=true){
  const items=navItems(); if(!items.length)return;
  navIndex=(index+items.length)%items.length;
  items.forEach((el,i)=>el.classList.toggle('keyboard-selected',i===navIndex));
  const el=items[navIndex];
  if(el.focus)el.focus({preventScroll:true});
  if(scroll)el.scrollIntoView({block:'nearest',inline:'nearest'});
}
function moveNav(key){
  const items=navItems(); if(!items.length)return;
  const current=items[navIndex]||items[0], r=current.getBoundingClientRect();
  const cx=r.left+r.width/2, cy=r.top+r.height/2;
  const horizontal=key==='ArrowLeft'||key==='ArrowRight';
  const sign=(key==='ArrowRight'||key==='ArrowDown')?1:-1;
  let best=-1,bestScore=Infinity;
  items.forEach((el,i)=>{
    if(el===current)return;
    const q=el.getBoundingClientRect(), x=q.left+q.width/2, y=q.top+q.height/2;
    const primary=horizontal?(x-cx)*sign:(y-cy)*sign;
    if(primary<=2)return;
    const cross=horizontal?Math.abs(y-cy):Math.abs(x-cx);
    const score=primary+cross*2.4;
    if(score<bestScore){bestScore=score;best=i;}
  });
  selectNav(best>=0?best:navIndex+sign);
}
function backRoute(){
  const route=routeFromHash();
  if(route==='guideDetail')navigate('guide');
  else if(route!=='home')navigate('home');
}
window.addEventListener('keydown',e=>{
  if(['INPUT','TEXTAREA'].includes(document.activeElement?.tagName))return;
  if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)){
    moveNav(e.key);e.preventDefault();return;
  }
  if(e.key==='Enter'){
    const items=navItems(), el=items[navIndex];
    if(el && el.tagName!=='INPUT'){el.click();e.preventDefault();}
    return;
  }
  if(e.key==='Delete'){
    navigate('home');navIndex=0;setTimeout(()=>selectNav(0,false),0);e.preventDefault();return;
  }
  if(e.key==='Escape'||(e.key==='Backspace'&&document.activeElement?.tagName!=='INPUT')){
    backRoute();navIndex=0;setTimeout(()=>selectNav(0,false),0);e.preventDefault();
  }
});

function setStatus(message){
  const status=$('#statusText');status.textContent=message;
  clearTimeout(setStatus.timer);setStatus.timer=setTimeout(()=>status.textContent='本地模式',3500);
}

function routeFromHash(){
  const route=(location.hash||'#/home').replace(/^#\/?/,'');
  return ['home','guide','guideDetail','tasks','games'].includes(route)?route:'home';
}
window.addEventListener('hashchange',()=>show(routeFromHash()));

function tickClock(){const d=new Date();$('#clock').textContent=d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false});}
tickClock();setInterval(tickClock,15000);
setInterval(()=>{if($('#tasks').classList.contains('page-active'))refreshTasks(false);},1500);
renderGuides();
show(routeFromHash());
window.terminalShellHome=()=>{location.hash='/home';show('home');window.refreshWebApps?.();window.terminalContainerCancel?.();};
window.terminalPrepareContent=route=>{contentView=true;document.body.classList.add('content-view');location.replace('#'+route);show(route.replace(/^\//,''));};

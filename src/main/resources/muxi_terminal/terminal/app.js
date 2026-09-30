const guides = [
  {id:'create',name:'机械动力 Create',short:'CR',category:'工程 / 自动化',colors:['#b48a4b','#392c1d'],summary:'用齿轮、传动轴和机械结构，把自动化真正“搭”在世界里。',why:'Create 的核心不是机器方块堆叠，而是可视化的机械系统。它非常适合作为整合包中期的大型工程主线。',steps:['先从安山合金、齿轮与传动轴开始，理解动力如何传递。','做一个水车或其他稳定动力源，再观察应力容量。','先完成一个小型自动化，例如自动加工或简单物品运输，再扩建工厂。']},
  {id:'iceandfire',name:'冰火传说 Ice and Fire',short:'IF',category:'探索 / 战斗',colors:['#447c9c','#251d25'],summary:'龙、神话生物、龙骨武器与高风险探索内容。',why:'它把世界探索变成了真正需要准备的远征。龙巢、龙穴和稀有生物都会给装备成长带来明显变化。',steps:['前期不要直接挑战成年龙，先准备远程手段、药水和撤退路线。','收集龙相关素材时优先确认目标生物阶段与环境优势。','获得龙骨与龙鳞后再逐步进入高阶装备路线。']},
  {id:'waystones',name:'传送石碑 Waystones',short:'WS',category:'交通 / 探索',colors:['#8263b4','#27223b'],summary:'激活石碑后建立属于自己的快速旅行网络。',why:'整合包世界很大，石碑会成为长期探索最重要的基础设施之一。',steps:['遇到石碑时优先激活并给位置起容易识别的名字。','在基地、资源区和关键维度入口附近建立自己的石碑。','后续可直接配合地图与玩家终端做统一导航。']},
  {id:'farmersdelight',name:"农夫乐事 Farmer's Delight",short:'FD',category:'生存 / 食物',colors:['#74944d','#2c321e'],summary:'更完整的烹饪、作物处理和高质量食物体系。',why:'它让食物从“补饥饿值”变成一条稳定的生存与探索补给线。',steps:['先做砧板和基础厨具，熟悉原料处理方式。','建立小型农田并保留多种作物，不要只种一种高饱食食物。','开始远征前准备成套料理，观察不同食物带来的持续收益。']},
  {id:'twilightforest',name:'暮色森林 The Twilight Forest',short:'TF',category:'维度 / Boss',colors:['#4b935e','#182c21'],summary:'一个有明确推进顺序的经典冒险维度。',why:'它很适合当作一条独立冒险线：探索、Boss、区域解锁和战利品都具有清晰阶段感。',steps:['准备稳定装备与补给后再建立暮色森林入口。','推进时留意区域限制，不要只按地图直线冲向下一个 Boss。','保留 Boss 战利品与关键掉落，它们通常与后续推进有关。']},
  {id:'alexsmobs',name:"Alex's Mobs",short:'AM',category:'生态 / 生物',colors:['#ba7350','#3b241b'],summary:'大量有独特行为、掉落和互动机制的新生物。',why:'很多生物不是单纯的“新怪”，而是拥有特殊生态、驯服方式或实用掉落。',steps:['第一次遇到陌生生物时先观察行为，不要默认它是敌对目标。','留意稀有掉落和特殊互动，它们常用于独特装备或工具。','探索不同群系，因为多数新增生物有明确的生态分布。']}
];

const $ = s => document.querySelector(s);
function show(id){document.querySelectorAll('.page').forEach(p=>p.classList.remove('page-active'));$('#'+id).classList.add('page-active');}
document.querySelectorAll('[data-open]').forEach(el=>el.addEventListener('click',()=>show(el.dataset.open)));

function native(command){
  return new Promise((resolve,reject)=>{
    if(typeof window.cefQuery!=='function'){resolve({ok:false,offline:true});return;}
    window.cefQuery({request:command,onSuccess:r=>{try{resolve(JSON.parse(r))}catch{resolve(r)}},onFailure:(c,m)=>reject(new Error(`${c}: ${m}`))});
  });
}
window.muxi={invoke:native,home:()=>native('terminal.home'),close:()=>native('terminal.close'),openBook:id=>native('patchouli.open:'+id)};

function iconStyle(g){return `--icon1:${g.colors[0]};--icon2:${g.colors[1]}`}
function renderGuides(filter=''){
  const q=filter.trim().toLowerCase();
  $('#guideList').innerHTML=guides.filter(g=>(g.name+' '+g.category+' '+g.summary).toLowerCase().includes(q)).map(g=>`
    <button class="guide-card" data-guide="${g.id}">
      <span class="mod-icon" style="${iconStyle(g)}">${g.short}</span>
      <span><h3>${g.name}</h3><p>${g.summary}</p><span class="card-meta"><i class="pill">${g.category}</i><i class="pill recommended">入门推荐</i></span></span>
    </button>`).join('');
  document.querySelectorAll('[data-guide]').forEach(el=>el.addEventListener('click',()=>openGuide(el.dataset.guide)));
}
function openGuide(id){
  const g=guides.find(x=>x.id===id);if(!g)return;
  $('#detailTitle').textContent=g.name;$('#detailCategory').textContent=g.category;$('#detailSummary').textContent=g.summary;$('#detailWhy').textContent=g.why;
  $('#detailIcon').textContent=g.short;$('#detailIcon').style.cssText=iconStyle(g);
  $('#detailSteps').innerHTML=g.steps.map((s,i)=>`<div class="step"><b>${i+1}</b><span>${s}</span></div>`).join('');
  const manual=$('#openManual');manual.hidden=!g.book;manual.onclick=()=>g.book&&window.muxi.openBook(g.book);
  show('guideDetail');
}
$('#guideSearch').addEventListener('input',e=>renderGuides(e.target.value));
function tickClock(){const d=new Date();$('#clock').textContent=d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false});}
tickClock();setInterval(tickClock,15000);renderGuides();

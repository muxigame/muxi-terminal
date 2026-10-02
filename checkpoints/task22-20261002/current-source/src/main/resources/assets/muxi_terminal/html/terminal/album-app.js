/* Independent builtin route, shared photo library and existing shell keyboard navigation. */
(() => {
  const host=document.querySelector('#app-content'),grid=document.querySelector('#home .app-grid');if(!host || !grid)return;
  const card=document.createElement('button');card.id='albumApp';card.className='app-card';
  card.innerHTML='<span class="app-icon album-icon"><i></i></span><span class="app-name">相册</span><span class="app-desc">本地照片 · 查看与恢复</span>';grid.append(card);
  card.addEventListener('click',()=>window.terminalLaunch({kind:'builtin',id:'album',name:'相册',source:card}).catch(error=>setStatus(error.message)));
  const page=document.createElement('section');page.id='album';page.className='page';
  page.innerHTML=`<div id="albumBody"><div class="toolbar"><button id="albumHome" class="back" aria-label="返回首页">‹</button><div><div class="eyebrow">APP / ALBUM</div><h2>相册</h2></div><span class="album-local">仅保存在本机</span></div>
    <div class="album-actions"><button id="albumPhotosTab" class="secondary" aria-pressed="true">照片</button><button id="albumRecycleTab" class="secondary" aria-pressed="false">回收区</button><button id="albumRefresh" class="secondary">刷新</button><button id="albumCamera" class="primary">去拍照</button></div>
    <p id="albumStatus" role="status" aria-live="polite">正在读取本地照片…</p><div id="albumGrid" class="album-grid"></div>
    <div id="albumViewer" hidden><div class="album-actions"><button id="albumClosePhoto" class="secondary">返回缩略图</button><button id="albumPrevious" class="secondary">上一张</button><button id="albumNext" class="secondary">下一张</button><button id="albumFit" class="secondary">适应</button><button id="albumZoomOut" class="secondary" aria-label="缩小照片">−</button><button id="albumZoomIn" class="secondary" aria-label="放大照片">＋</button><button id="albumDelete" class="secondary">移入回收区</button><button id="albumRestore" class="primary" hidden>恢复照片</button></div>
    <p id="albumPhotoName"></p><div id="albumImageScroll"><img id="albumImage" alt="本地相册照片"></div></div>
    <p class="album-note">与相机共用本机照片，不上传。移入回收区后可以恢复。</p></div>
    <div id="albumConfirm" hidden><div class="detail-card" role="dialog" aria-modal="true" aria-labelledby="albumConfirmTitle" tabindex="-1"><h3 id="albumConfirmTitle">将这张照片移入本地回收区？</h3><p id="albumConfirmName"></p><p>原 PNG 将移到照片库内的回收区，不会永久删除。可从“回收区”恢复。</p><div class="detail-actions"><button id="albumCancelDelete" class="secondary">取消</button><button id="albumConfirmDelete" class="primary">确认移入回收区</button></div></div></div>`;
  host.append(page);
  const el=id=>document.getElementById(id);const active=()=>contentView && location.hash==='#/album';
  let disposed=false,zoom=1,visibleState={},thumbEpoch=0,observer=null,thumbQueue=[],pumping=false,disabledButtons=null,returnFocus=null;
  function message(text){el('albumStatus').textContent=text;}
  async function invoke(command){
    for(let attempt=0;attempt<10;attempt++){
      let timer;
      try{return await Promise.race([native(command),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('相册响应超时，请刷新后重试。')),4000);})]);}
      catch(error){if(!error.message.startsWith('429:') || attempt===9)throw error;await new Promise(resolve=>setTimeout(resolve,120));}
      finally{clearTimeout(timer);}
    }
  }
  function action(work){work().catch(error=>{if(active() && !disposed)message(error.message);});}
  function releaseThumbs(){++thumbEpoch;thumbQueue=[];observer?.disconnect();observer=null;el('albumGrid').querySelectorAll('img').forEach(img=>img.removeAttribute('src'));}
  function validImage(image){return typeof image==='string' && image.startsWith('data:image/png;base64,');}
  async function pump(){
    if(pumping)return;pumping=true;
    try{while(thumbQueue.length && active() && !disposed){const {img,id,epoch}=thumbQueue.shift();
      try{const image=await controller.thumb(id);if(epoch!==thumbEpoch || !img.isConnected || !validImage(image))continue;img.src=image;}
      catch{if(epoch===thumbEpoch && img.isConnected)img.alt='缩略图暂不可用';}
    }}finally{pumping=false;}
  }
  function queue(img,id){thumbQueue.push({img,id,epoch:thumbEpoch});void pump();}
  function renderGrid(state){
    releaseThumbs();el('albumGrid').replaceChildren();
    if(typeof IntersectionObserver==='function')observer=new IntersectionObserver(entries=>{for(const entry of entries){if(entry.isIntersecting){observer.unobserve(entry.target);queue(entry.target,entry.target.dataset.id);}}},{root:page,rootMargin:'100px'});
    for(const photo of state.photos){const button=document.createElement('button');button.className='album-tile secondary';button.setAttribute('aria-label','查看照片 '+photo.id);
      const img=document.createElement('img');img.alt='照片缩略图';img.dataset.id=photo.id;
      const label=document.createElement('span');const date=photo.id.slice(5,13),time=photo.id.slice(14,20);
      label.textContent=date.slice(0,4)+'-'+date.slice(4,6)+'-'+date.slice(6,8)+'\n'+time.slice(0,2)+':'+time.slice(2,4)+':'+time.slice(4,6)+' UTC';button.title=photo.id;button.append(img,label);
      button.addEventListener('click',()=>{zoom=1;action(()=>controller.open(photo.id));});el('albumGrid').append(button);
      if(observer)observer.observe(img);else queue(img,photo.id);
    }
    if(!state.photos.length)el('albumGrid').textContent=state.recycled?'回收区是空的。':'还没有照片，去相机拍一张吧。';
  }
  function applyZoom(){el('albumImage').style.width=zoom===1?'100%':(zoom*100)+'%';el('albumImage').style.maxWidth=zoom===1?'100%':'none';}
  function confirmation(open){
    if(open && !disabledButtons){
      returnFocus=el('albumDelete');disabledButtons=[...el('albumBody').querySelectorAll('button')].map(button=>[button,button.disabled]);
      disabledButtons.forEach(([button])=>button.disabled=true);page.scrollTop=0;el('albumConfirm').hidden=false;
      setTimeout(()=>el('albumCancelDelete').focus({preventScroll:true}),0);
    }else if(!open && disabledButtons){
      el('albumConfirm').hidden=true;disabledButtons.forEach(([button,disabled])=>button.disabled=disabled);disabledButtons=null;
      const focus=returnFocus;returnFocus=null;
      setTimeout(()=>{if(focus?.isConnected && active())focus.focus({preventScroll:true});},0);
    }
  }
  const controller=MuxiAlbumController.create({invoke,onChange:state=>{
    if(disposed)return;
    const previous=visibleState;visibleState=state;
    if(previous.photos!==state.photos || previous.recycled!==state.recycled)renderGrid(state);
    const selected=!!state.selected;
    if(selected && !previous.selected)releaseThumbs();
    else if(!selected && previous.selected)renderGrid(state);
    el('albumGrid').hidden=selected;el('albumViewer').hidden=!selected;
    el('albumPhotosTab').setAttribute('aria-pressed',String(!state.recycled));el('albumRecycleTab').setAttribute('aria-pressed',String(state.recycled));
    el('albumDelete').hidden=state.recycled;el('albumRestore').hidden=!state.recycled;
    el('albumPhotoName').textContent=state.selected?.id || '';
    if(validImage(state.image)){el('albumImage').src=state.image;applyZoom();}else el('albumImage').removeAttribute('src');
    if(previous.selected?.id!==state.selected?.id || !selected){zoom=1;applyZoom();el('albumImageScroll').scrollTo(0,0);}
    if(state.error)message(state.error);else if(state.busy)message('正在处理本地照片…');else message((state.recycled?'本地回收区':'本地照片')+' · '+state.photos.length+' 张（最多显示最近 200 张）');
    confirmation(!!state.confirm);
    if(!disabledButtons)for(const button of el('albumBody').querySelectorAll('button'))button.disabled=state.busy;
    const i=state.photos.findIndex(p=>p.id===state.selected?.id);
    if(!disabledButtons){el('albumPrevious').disabled=state.busy || i<=0;el('albumNext').disabled=state.busy || i<0 || i>=state.photos.length-1;}
    el('albumCancelDelete').disabled=el('albumConfirmDelete').disabled=state.busy;
    el('albumConfirmName').textContent=state.confirm?.id || '';
  }});
  el('albumHome').addEventListener('click',()=>action(()=>native('terminal.home')));
  el('albumCamera').addEventListener('click',()=>action(()=>native('terminal.app:camera')));
  el('albumPhotosTab').addEventListener('click',()=>action(()=>controller.load(false)));
  el('albumRecycleTab').addEventListener('click',()=>action(()=>controller.load(true)));
  el('albumRefresh').addEventListener('click',()=>action(()=>controller.load(visibleState.recycled)));
  el('albumClosePhoto').addEventListener('click',()=>action(()=>controller.closePhoto()));
  function move(delta){const i=visibleState.photos.findIndex(p=>p.id===visibleState.selected?.id),photo=visibleState.photos[i+delta];if(photo){zoom=1;action(()=>controller.open(photo.id));}}
  el('albumPrevious').addEventListener('click',()=>move(-1));el('albumNext').addEventListener('click',()=>move(1));
  el('albumFit').addEventListener('click',()=>{zoom=1;applyZoom();});
  el('albumZoomIn').addEventListener('click',()=>{zoom=Math.min(4,zoom+.5);applyZoom();});
  el('albumZoomOut').addEventListener('click',()=>{zoom=Math.max(1,zoom-.5);applyZoom();});
  el('albumDelete').addEventListener('click',()=>action(()=>controller.prepareDelete()));
  el('albumCancelDelete').addEventListener('click',()=>action(()=>controller.cancelDelete()));
  el('albumConfirmDelete').addEventListener('click',()=>action(()=>controller.confirmDelete()));
  el('albumRestore').addEventListener('click',()=>action(()=>controller.restore()));
  el('albumConfirm').addEventListener('keydown',event=>{if(event.key==='Escape' && !visibleState.busy){event.preventDefault();event.stopPropagation();action(()=>controller.cancelDelete());}});
  function sync(){if(active()){show('album');action(()=>controller.load(false));}else{controller.dispose();releaseThumbs();el('albumImage').removeAttribute('src');}}
  window.addEventListener('hashchange',sync);
  window.addEventListener('pagehide',()=>{controller.dispose();releaseThumbs();el('albumImage').removeAttribute('src');disposed=true;});
  window.addEventListener('focus',()=>{if(active() && !visibleState.selected && !visibleState.busy)action(()=>controller.load(visibleState.recycled));});
  if(active())sync();
})();

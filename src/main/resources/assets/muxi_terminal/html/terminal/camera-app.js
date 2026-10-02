/* Additive built-in module; retains existing MC shell, card, button and keyboard styles. */
(() => {
  const host=document.querySelector('#app-content'),grid=document.querySelector('#home .app-grid');
  if(!host || !grid)return;
  const card=document.createElement('button');card.className='app-card';card.id='cameraApp';
  card.innerHTML='<span class="app-icon camera-icon"><i></i></span><span class="app-name">相机</span><span class="app-desc">前拍 / 自拍 · 本地相册</span>';
  grid.append(card);
  card.addEventListener('click',()=>window.terminalLaunch({kind:'builtin',id:'camera',name:'相机',source:card}).catch(error=>setStatus(error.message)));
  const page=document.createElement('section');page.id='camera';page.className='page';
  page.innerHTML=`<div class="toolbar"><button class="back" id="cameraHome" aria-label="返回首页">‹</button><div><div class="eyebrow">APP / CAMERA</div><h2>相机</h2></div><span class="camera-local">仅保存在本机</span></div>
    <div class="camera-layout"><div class="camera-view"><img id="cameraPreview" alt="游戏内相机预览" hidden><span id="cameraPlaceholder">等待游戏画面…</span><span id="cameraLens">前拍</span></div>
    <div class="camera-controls"><button class="secondary" id="cameraForward" aria-pressed="true">前拍</button><button class="secondary" id="cameraSelfie" aria-pressed="false">自拍</button><button class="primary" id="cameraShutter" disabled>拍照 [空格]</button><button class="secondary" id="cameraResume">恢复预览</button><button class="secondary" id="cameraAlbum">本地相册</button><button class="secondary" id="cameraOpenAlbum">打开相册 APP</button></div>
    <p id="cameraStatus" role="status" aria-live="polite">右键打开终端大界面使用相机；预览约每秒更新一次。</p><p class="camera-note">照片保留游戏画面原始分辨率，仅存本机。自拍使用游戏正面第三人称视角，近墙时镜头会按游戏规则避障。</p>
    <div id="cameraPhotos" hidden><div class="camera-album-actions"><button class="secondary" id="cameraReturn">返回预览</button><span>最近 200 张照片</span></div><div id="cameraPhotoList"></div><img id="cameraPhoto" alt="本地照片" hidden></div></div>`;
  host.append(page);
  const el=id=>document.getElementById(id);
  const message=text=>{el('cameraStatus').textContent=text;};
  let gallery=false,disposed=false,photoEpoch=0,opening=0,lastSaved='';
  function invoke(command){
    let deadline;
    return Promise.race([native(command),new Promise((_,reject)=>{deadline=setTimeout(()=>reject(new Error('相机响应超时，请重新打开相机。')),4000);})]).finally(()=>clearTimeout(deadline));
  }
  const camera=MuxiCameraController.create({invoke,onError:error=>message(error.message),onState:state=>{
    if(disposed)return;
    el('cameraShutter').disabled=gallery || !state.active || state.busy || !state.preview;
    el('cameraForward').disabled=el('cameraSelfie').disabled=gallery || !state.active || state.busy;
    el('cameraResume').hidden=!!state.active || gallery;
    const selfie=state.mode==='selfie';el('cameraLens').textContent=selfie?'自拍':'前拍';
    el('cameraForward').setAttribute('aria-pressed',String(!selfie));el('cameraSelfie').setAttribute('aria-pressed',String(selfie));
    if(!gallery){el('cameraPreview').hidden=!state.preview;el('cameraPlaceholder').hidden=!!state.preview;
      el('cameraPlaceholder').textContent=state.active?'等待游戏画面…':'预览已暂停';
      if(state.preview)el('cameraPreview').src=state.preview;else el('cameraPreview').removeAttribute('src');}
    if(state.error)message('画面或保存失败，请重试。');
    else if(state.saved && state.saved!==lastSaved){lastSaved=state.saved;message('已保存到本地相册：'+state.saved);}
  }});
  const active=()=>contentView && location.hash==='#/camera';
  async function start(){
    const attempt=++opening;gallery=false;++photoEpoch;el('cameraPhotos').hidden=true;
    el('cameraPhoto').removeAttribute('src');el('cameraPhoto').hidden=true;
    // The native container reveals a loaded/painted view after its existing launch animation.
    for(let i=0;i<12 && attempt===opening && active() && !disposed;i++){
      try{await camera.start();if(camera.getState().active)return;}catch{}
      await new Promise(resolve=>setTimeout(resolve,250));
    }
  }
  function action(work){work().catch(error=>message(error.message));}
  el('cameraHome').addEventListener('click',()=>{++opening;action(async()=>{await camera.stop();await native('terminal.home');});});
  el('cameraForward').addEventListener('click',()=>action(()=>camera.mode('forward')));
  el('cameraSelfie').addEventListener('click',()=>action(()=>camera.mode('selfie')));
  el('cameraShutter').addEventListener('click',()=>action(()=>camera.shutter()));
  el('cameraOpenAlbum').addEventListener('click',()=>action(async()=>{++opening;++photoEpoch;gallery=true;await camera.stop();await native('terminal.app:album');}));
  el('cameraResume').addEventListener('click',()=>action(start));el('cameraReturn').addEventListener('click',()=>action(start));
  async function read(command){for(let i=0;i<8;i++){try{return await invoke(command);}catch(error){if(!error.message.startsWith('429:') || i===7)throw error;await new Promise(resolve=>setTimeout(resolve,150));}}}
  el('cameraAlbum').addEventListener('click',()=>action(async()=>{
    ++opening;gallery=true;const e=++photoEpoch;await camera.stop();el('cameraPhotos').hidden=false;
    el('cameraPreview').removeAttribute('src');el('cameraPreview').hidden=true;el('cameraPlaceholder').hidden=false;
    el('cameraPlaceholder').textContent='本地相册';el('cameraResume').hidden=true;
    const list=el('cameraPhotoList');list.replaceChildren();
    const photos=await read('camera.photos');if(e!==photoEpoch || disposed || !active())return;
    if(!photos.length){list.textContent='还没有照片，返回预览拍一张吧。';return;}
    for(const photo of photos){const button=document.createElement('button');button.className='secondary';button.textContent=photo.id;
      button.addEventListener('click',()=>action(async()=>{
        const p=++photoEpoch;const data=await read('camera.photo:'+photo.id);
        if(p!==photoEpoch || disposed || !gallery || !active())return;
        if(typeof data!=='string' || !data.startsWith('data:image/png;base64,'))throw new Error('照片格式无效。');
        el('cameraPhoto').src=data;el('cameraPhoto').hidden=false;
      }));list.append(button);}
  }));
  function sync(){if(active()) {show('camera');action(start);}else{++opening;++photoEpoch;camera.stop();}}
  window.addEventListener('hashchange',sync);
  window.addEventListener('pagehide',()=>{disposed=true;++opening;++photoEpoch;camera.dispose();el('cameraPreview').removeAttribute('src');el('cameraPhoto').removeAttribute('src');});
  document.addEventListener('visibilitychange',()=>{if(document.hidden){++opening;camera.stop();}});
  document.addEventListener('keydown',event=>{
    if(!active() || gallery || event.repeat || event.defaultPrevented || /INPUT|TEXTAREA/.test(event.target.tagName))return;
    // Enter activates the focused control; Space always uses the camera shutter.
    if(event.code==='Space'){event.preventDefault();action(()=>camera.shutter());}
  });
  if(active())sync();
})();

/* Native camera launcher. Retains the existing terminal card/icon and never navigates the shell. */
(() => {
  const host=document.querySelector('#app-content'),grid=document.querySelector('#home .app-grid');
  if(!host || !grid)return;
  const card=document.createElement('button');card.className='app-card';card.id='cameraApp';
  card.innerHTML='<span class="app-icon camera-icon"><i></i></span><span class="app-name">相机</span><span class="app-desc">前拍 / 自拍 · 本机相册</span>';
  grid.append(card);
  const open=()=>native('camera.open').catch(error=>setStatus(error.message));
  card.addEventListener('click',open);
  // Existing bookmarks may still use #/camera. They show only a native entry, never a web viewfinder.
  const page=document.createElement('section');page.id='camera';page.className='page';
  page.innerHTML='<div class="toolbar"><button class="back" data-open="home" aria-label="返回首页">←</button><div><div class="eyebrow">APP / CAMERA</div><h2>相机</h2></div></div><button class="primary" id="cameraNativeOpen">进入拍摄</button><p>拍摄时隐藏终端与游戏 HUD；关闭后返回当前终端。照片仅保存在本机，可在相册查看。</p>';
  host.append(page);
  page.querySelector('#cameraNativeOpen').addEventListener('click',open);
  page.querySelector('[data-open]').addEventListener('click',()=>navigate('home'));
})();

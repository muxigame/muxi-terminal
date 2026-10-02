/* Trusted local built-in settings app. No file input, uploads, URLs or source paths. */
(() => {
  'use strict';
  const fields=[['volume','主音量',0,1,.01,'%'],['sensitivity','鼠标灵敏度',0,1,.01,'sensitivity%'],['fov','视野 FOV',30,110,1,'°'],['brightness','亮度',0,1,.01,'%'],['distance','视距',2,32,1,' 区块']];
  let baseline={},dirty=new Map(),uiDirty=new Map(),editing=false,requestSerial=0,snapshotReady=false,gameSaving=false,uiSaving=false;
  let enabled=true,lastSound=0,wallSerial=0,wallpaperLoaded=false;
  const root=document.createElement('section');root.id='settings';root.className='page';
  root.innerHTML=`<div class="toolbar"><button class="back" data-settings-home aria-label="返回首页">←</button><div><div class="eyebrow">APP / SETTINGS</div><h2>设置</h2></div></div>
    <div class="detail-card settings-panel"><h3>游戏设置</h3><p>读取当前 Minecraft 原生设置。调整后点击应用；视距也在确认时生效。</p><div id="settingsFields"></div><div class="detail-actions"><button id="settingsApply" class="primary" disabled>应用更改</button><button id="settingsDiscard" class="secondary" disabled>取消更改</button><button id="settingsVanilla" class="secondary">完整原版设置</button></div></div>
    <div class="detail-card settings-panel"><h3>终端壁纸</h3><p>铺满 Pad 屏幕，仅保存在本机。支持 PNG / JPEG，文件不超过 12 MiB，最长边 8192，最多 1600 万像素。</p><div class="detail-actions"><button id="wallpaperChoose" class="primary">选择本机图片</button><button id="wallpaperReset" class="secondary">恢复默认壁纸</button></div></div>
    <div class="detail-card settings-panel"><h3>操作音效</h3><div class="setting-row"><label for="settingsSoundEnabled">鼠标 / 键盘操作音效</label><input id="settingsSoundEnabled" type="checkbox" checked disabled><output></output></div><div class="setting-row"><label for="settingsSoundVolume">音效音量</label><input id="settingsSoundVolume" type="range" min="0" max="1" step=".01" disabled><output id="settingsSoundOutput"></output></div><p>同时遵循 Minecraft 主音量和静音。</p><div class="detail-actions"><button id="settingsUiSave" class="primary" disabled>保存音效设置</button></div></div><p id="settingsStatus" class="settings-status" role="status" aria-live="polite"></p>`;
  document.querySelector('#app-content').append(root);
  const card=document.createElement('button');card.className='app-card';card.dataset.open='settings';
  card.innerHTML='<span class="app-icon settings-icon" aria-hidden="true"><i></i><i></i><i></i></span><span class="app-name">设置</span><span class="app-desc">游戏、壁纸与音效</span>';
  document.querySelector('.app-grid').append(card);
  card.addEventListener('click',()=>navigate('settings',card));
  root.querySelector('[data-settings-home]').addEventListener('click',()=>navigate('home'));
  const query=(request,timeout=5000)=>new Promise((resolve,reject)=>{
    if(typeof window.muxiSettingsQuery!=='function'){reject(new Error('本地设置接口暂不可用'));return;}
    let settled=false;
    const timer=timeout?setTimeout(()=>{if(!settled){settled=true;reject(new Error('设置操作超时，请重试'));}},timeout):null;
    window.muxiSettingsQuery({request,persistent:false,onSuccess:r=>{if(settled)return;settled=true;clearTimeout(timer);try{resolve(JSON.parse(r));}catch{reject(new Error('设置响应无效'));}},onFailure:(c,m)=>{if(settled)return;settled=true;clearTimeout(timer);reject(new Error(m));}});
  });
  const get=id=>document.getElementById(id);
  const status=text=>{get('settingsStatus').textContent=text;};
  const label=(value,suffix)=>suffix==='sensitivity%'?`${Math.round(value*200)}%`:suffix==='%'?`${Math.round(value*100)}%`:`${value}${suffix}`;
  const buttons=()=>{get('settingsApply').disabled=gameSaving||uiSaving||!snapshotReady||!dirty.size;get('settingsDiscard').disabled=gameSaving||uiSaving||!snapshotReady||!dirty.size;get('settingsUiSave').disabled=gameSaving||uiSaving||!snapshotReady||!uiDirty.size;};
  for(const [key,title,min,max,step,suffix] of fields){
    const row=document.createElement('div');row.className='setting-row';
    row.innerHTML=`<label for="setting-${key}">${title}</label><input id="setting-${key}" data-game-setting="${key}" type="range" min="${min}" max="${max}" step="${step}" disabled><output id="setting-${key}-value"></output>`;
    get('settingsFields').append(row);
    get('setting-'+key).addEventListener('input',e=>{
      requestSerial++;const value=Number(e.target.value);get('setting-'+key+'-value').textContent=label(value,suffix);
      if(value===baseline[key])dirty.delete(key);else dirty.set(key,value);buttons();
    });
  }
  function accept(s,preserveUi=false,preserveGame=false){
    baseline=s;if(!preserveGame)dirty.clear();else for(const [key,value] of dirty)if(value===s[key])dirty.delete(key);
    if(!preserveUi)uiDirty.clear();snapshotReady=true;enabled=!!s.soundEnabled;
    for(const [key,,,max,,suffix] of fields){const input=get('setting-'+key),value=dirty.has(key)?dirty.get(key):s[key];input.max=key==='distance'?s.distanceMax:max;input.value=value;input.disabled=false;get('setting-'+key+'-value').textContent=label(value,suffix);}
    get('settingsSoundEnabled').checked=uiDirty.has('soundEnabled')?uiDirty.get('soundEnabled'):enabled;
    get('settingsSoundEnabled').disabled=get('settingsSoundVolume').disabled=uiSaving;
    const volume=uiDirty.has('soundVolume')?uiDirty.get('soundVolume'):s.soundVolume;
    get('settingsSoundVolume').value=volume;get('settingsSoundOutput').textContent=label(volume,'%');buttons();
  }
  async function refresh(preserveDraft=false,preserveUi=preserveDraft){
    const serial=++requestSerial;
    try{const s=await query('settings.snapshot');if(serial===requestSerial&&root.classList.contains('page-active')){accept(s,preserveUi,preserveDraft);status('');}}
    catch(e){if(serial===requestSerial)status(e.message);}
  }
  window.terminalSettingsRefresh=()=>{if(editing)refresh(true);};
  window.terminalSettingsUi=s=>{enabled=!!s.soundEnabled;};
  async function syncWallpaper(){
    if(wallpaperLoaded)return;
    const serial=wallSerial;
    try{const s=await query('wallpaper.read');if(serial===wallSerial)window.terminalSettingsWallpaper(s.data);}catch{}
  }
  window.terminalSettingsWallpaper=data=>{
    wallSerial++;
    // Only normalized native PNG responses become a CSS image; never local paths or arbitrary URLs.
    if(typeof data!=='string'||(data&&!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(data)))return;
    wallpaperLoaded=true;
    const shell=document.querySelector('.shell');
    if(data)shell.style.setProperty('--terminal-wallpaper',`url("${data}")`);else shell.style.removeProperty('--terminal-wallpaper');
  };
  get('settingsApply').addEventListener('click',async()=>{
    const patch=Object.fromEntries(dirty);if(!Object.keys(patch).length)return;
    requestSerial++;gameSaving=true;buttons();
    fields.forEach(([key])=>get('setting-'+key).disabled=true);
    try{const s=await query('settings.apply:'+JSON.stringify(patch));accept(s,true);status('已应用并保存 Minecraft 设置');}
    catch(e){status(e.message);fields.forEach(([key])=>get('setting-'+key).disabled=false);}
    finally{gameSaving=false;buttons();}
  });
  get('settingsDiscard').addEventListener('click',()=>refresh(false,true));
  get('settingsVanilla').addEventListener('click',()=>query('settings.vanilla').catch(e=>status(e.message)));
  get('settingsSoundEnabled').addEventListener('change',e=>{requestSerial++;const value=e.target.checked;if(value===baseline.soundEnabled)uiDirty.delete('soundEnabled');else uiDirty.set('soundEnabled',value);buttons();});
  get('settingsSoundVolume').addEventListener('input',e=>{requestSerial++;const value=Number(e.target.value);get('settingsSoundOutput').textContent=label(value,'%');if(value===baseline.soundVolume)uiDirty.delete('soundVolume');else uiDirty.set('soundVolume',value);buttons();});
  get('settingsUiSave').addEventListener('click',async()=>{
    requestSerial++;uiSaving=true;buttons();get('settingsSoundEnabled').disabled=get('settingsSoundVolume').disabled=true;
    try{const s=await query('ui.save:'+JSON.stringify(Object.fromEntries(uiDirty)));Object.assign(baseline,s);enabled=s.soundEnabled;uiDirty.clear();buttons();status('已保存终端音效设置');}
    catch(e){status(e.message);}
    finally{uiSaving=false;get('settingsSoundEnabled').disabled=get('settingsSoundVolume').disabled=false;buttons();}
  });
  const wallpaper=async command=>{
    get('wallpaperChoose').disabled=get('wallpaperReset').disabled=true;
    try{const s=await query(command,command==='wallpaper.choose'?0:5000);status(s.cancelled?'已取消选择，壁纸保持原样':command==='wallpaper.reset'?'已恢复默认壁纸':'壁纸已保存在本机');}
    catch(e){status(e.message);}
    finally{get('wallpaperChoose').disabled=get('wallpaperReset').disabled=false;}
  };
  get('wallpaperChoose').addEventListener('click',()=>wallpaper('wallpaper.choose'));
  get('wallpaperReset').addEventListener('click',()=>wallpaper('wallpaper.reset'));
  function visible(){
    const active=root.classList.contains('page-active');
    if(active&&!editing)refresh();
    if(!active&&editing){requestSerial++;dirty.clear();uiDirty.clear();snapshotReady=false;buttons();}
    editing=active;
  }
  new MutationObserver(visible).observe(root,{attributes:true,attributeFilter:['class']});
  window.addEventListener('focus',()=>{if(editing&&!dirty.size&&!uiDirty.size)refresh();});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden&&editing&&!dirty.size&&!uiDirty.size)refresh();});
  window.addEventListener('pageshow',()=>{syncWallpaper();if(editing)refresh();});
  // Built-in content starts at HOME_URL and receives its hash after load; retry a
  // startup read that the native generation guard correctly discarded at that switch.
  window.addEventListener('hashchange',syncWallpaper);
  const sound=kind=>{const now=performance.now();if(!enabled||now-lastSound<90)return;lastSound=now;query('ui.sound:'+kind).catch(()=>{});};
  const control=e=>e.target.closest?.('button,input,[tabindex]');
  document.addEventListener('pointerover',e=>{const el=control(e);if(el&&!el.disabled&&!el.contains(e.relatedTarget))sound('hover');});
  document.addEventListener('focusin',e=>{if(control(e))sound('hover');});
  document.addEventListener('click',e=>{const el=control(e);if(el&&!el.disabled)sound('select');},true);
  document.addEventListener('change',e=>{if(e.target.matches?.('input[type=range],input[type=checkbox]'))sound('select');});
  // Tab/Shift+Tab enters each range; arrows adjust the native value's draft.
  root.addEventListener('keydown',e=>{
    if(e.key==='Enter'&&e.target.tagName==='BUTTON'){e.stopPropagation();e.preventDefault();if(!e.repeat&&!e.target.disabled)e.target.click();}
  });
  if(routeFromHash()==='settings')show('settings');
  query('ui.snapshot').then(s=>{enabled=s.soundEnabled;}).catch(()=>{});syncWallpaper();visible();
})();

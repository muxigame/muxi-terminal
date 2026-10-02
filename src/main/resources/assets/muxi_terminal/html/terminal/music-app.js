/* Built-in module: inherits task6's fixed shell, MC components, scale and navigation. */
(() => {
  'use strict';
  let root, state, inflight = false, busy = false, timer, epoch = 0;
  const active = () => root?.classList.contains('page-active') && !document.hidden;
  const el = id => root.querySelector('#' + id);
  const safe = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function request(command) {
    return new Promise((resolve, reject) => {
      const query = window.muxiTerminalQuery;
      if (typeof query !== 'function') { reject(new Error('音乐接口尚未接入，请在游戏终端中打开。')); return; }
      let deadline = setTimeout(() => reject(new Error('音乐请求超时，请重试。')), 4000);
      query({request: command, persistent:false, onSuccess: data => {
        clearTimeout(deadline);
        try { resolve(JSON.parse(data)); } catch { reject(new Error('音乐状态无法读取。')); }
      }, onFailure: (code, message) => { clearTimeout(deadline); reject(new Error(message || '音乐接口不可用。')); }});
    });
  }
  function error(message) { if (root) el('musicMessage').textContent = message; }
  function render(next) {
    state = next;
    const caps = next.capabilities || {};
    el('musicTitle').textContent = next.title || '当前没有音乐';
    const names = {background: '游戏背景音乐', netmusic: '原便携播放器', local: '个人本地音乐', other: '附近播放器', idle: '游戏音乐', unavailable: '音乐不可用'};
    const status = {playing: '播放中', paused: '已暂停', loading: '正在解码', idle: '已停止'};
    el('musicSource').textContent = (names[next.kind] || '游戏音乐') + ' · ' + (status[next.status] || '等待状态');
    el('musicReason').textContent = next.reason || '';
    el('musicMessage').textContent = next.message || '';
    el('musicPause').textContent = next.status === 'paused' ? '继续' : '暂停';
    el('musicPause').disabled = busy || !caps.pause;
    ['play', 'stop', 'previous', 'next'].forEach(action => el('music-' + action).disabled = busy || !caps[action]);
    el('musicImport').disabled = busy || !caps.import;
    el('musicVolumeLabel').textContent = (next.volumeSource === 'records' ? '唱片音量' : '音乐音量') + ' · Minecraft 原生选项';
    const slider = el('musicVolume');
    if (document.activeElement !== slider) slider.value = Math.round((Number(next.volume) || 0) * 100);
    el('musicVolumeValue').textContent = `${slider.value}%`;
    el('musicMute').textContent = next.muted ? '已静音 · 主音量 ' + Math.round((Number(next.master) || 0) * 100) + '%' : '主音量 ' + Math.round((Number(next.master) || 0) * 100) + '%';
    const tracks = next.localTracks || [], signature = JSON.stringify([tracks, caps.local, busy, next.busy]);
    const list = el('musicLocalList');
    // Preserve keyboard focus on unchanged snapshots.
    if (list.dataset.signature !== signature) {
      list.dataset.signature = signature;
      list.innerHTML = tracks.length ? tracks.map(track => `<article class="task-card"><div class="task-row-head"><strong class="task-title">${safe(track.title)}</strong><span class="pill">${safe(track.format)}${track.active ? ' · 当前' : ''}</span></div><div class="detail-actions"><button class="secondary" data-local="${safe(track.id)}" ${busy || !caps.local ? 'disabled' : ''}>播放</button><button class="secondary" data-remove="${safe(track.id)}" ${busy || next.busy ? 'disabled' : ''}>移除本地副本</button></div></article>`).join('') : '<div class="task-empty">还没有本地音乐。选择本机文件，导入个人本地曲库。</div>';
    }
  }
  async function refresh() {
    if (!active() || inflight || busy) return;
    inflight = true; const current = epoch;
    try { const next = await request('music.snapshot'); if (current === epoch && active()) render(next); }
    catch (failure) { if (current === epoch && active()) { error(failure.message); root.querySelectorAll('button:not(.back)').forEach(b => b.disabled = true); } }
    finally { inflight = false; }
  }
  async function act(command) {
    if (busy || !active()) return;
    busy = true; const current = ++epoch;
    if (state) render(state);
    try { const result = await request(command); if (current === epoch && active()) { busy = false; render(result); } }
    catch (failure) { if (current === epoch && active()) { busy = false; if (state) render(state); error(failure.message); } }
    finally { busy = false; if (current === epoch && active() && state) { el('musicImport').disabled = !state.capabilities?.import; } }
  }
  function control(action, value) {
    if (!state?.target) return;
    return act('music.control:' + JSON.stringify({action, target: state.target, ...(value === undefined ? {} : {value})}));
  }
  function mount(section) {
    if (root) return; root = section;
    root.innerHTML = `<div class="toolbar"><button class="back" data-open="home" aria-label="返回主页">‹</button><div><div class="eyebrow">APP / MUSIC</div><h2>音乐</h2></div></div>
      <article class="detail-card settings-panel"><div class="task-row-head"><div><span class="pill" id="musicSource">正在读取游戏音乐…</span><h3 id="musicTitle">当前没有音乐</h3></div></div>
      <p class="manual-hint" id="musicReason"></p><div class="detail-actions"><button id="music-previous" class="secondary" disabled>上一首</button><button id="music-play" class="secondary" disabled>从头播放</button><button id="musicPause" class="primary" disabled>暂停</button><button id="music-stop" class="secondary" disabled>停止</button><button id="music-next" class="secondary" disabled>下一首</button></div>
      <div class="detail-section"><div class="setting-row"><label for="musicVolume" id="musicVolumeLabel">音乐音量 · Minecraft 原生选项</label><input id="musicVolume" type="range" min="0" max="100" step="1" aria-label="当前音乐来源音量"><output id="musicVolumeValue">0%</output></div><p id="musicMute"></p></div>
      <p class="manual-hint" id="musicMessage" role="status" aria-live="polite"></p></article>
      <div class="section-title"><span>本地曲库</span><button id="musicImport" class="secondary" disabled>导入本机音乐</button></div>
      <div class="guide-intro"><span>仅在本机复制保存，不上传。支持 OGG Vorbis、PCM WAV；MP3 / FLAC / AAC 需现有解码器并逐曲验证。退出音乐 APP 停止本地播放。</span></div><div class="task-list" id="musicLocalList"></div>`;
    root.addEventListener('click', event => {
      const button = event.target.closest('button'); if (!button || button.disabled) return;
      if (button.dataset.open === 'home') { window.terminalReturnHome ? window.terminalReturnHome() : window.muxi?.home(); return; }
      if (button.id === 'musicImport') act('music.import');
      else if (button.id === 'musicPause') control(state.status === 'paused' ? 'resume' : 'pause');
      else if (button.id.startsWith('music-')) control(button.id.slice(6));
      else if (button.dataset.local) act('music.local:' + button.dataset.local);
      else if (button.dataset.remove) act('music.remove:' + button.dataset.remove);
    });
    el('musicVolume').addEventListener('input', () => el('musicVolumeValue').textContent = el('musicVolume').value + '%');
    el('musicVolume').addEventListener('change', () => control('volume', Number(el('musicVolume').value) / 100));
    new MutationObserver(() => { epoch++; if (active()) refresh(); }).observe(root, {attributes: true, attributeFilter: ['class']});
    timer = setInterval(refresh, 750); refresh();
  }
  window.MuxiMusicApp = {mount, refresh, destroy() { clearInterval(timer); epoch++; if (root) request('music.exit').catch(() => {}); }};
  window.addEventListener('pagehide', () => window.MuxiMusicApp.destroy());
  window.addEventListener('focus', refresh);
  window.addEventListener('visibilitychange', refresh);
  const start = () => { const section = document.getElementById('music'); if (section) mount(section); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();

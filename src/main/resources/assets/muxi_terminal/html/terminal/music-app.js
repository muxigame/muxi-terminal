/* Built-in module: inherits task6's fixed shell, MC components, scale and navigation. */
(() => {
  'use strict';
  const stylesheet = document.createElement('link');
  stylesheet.rel = 'stylesheet';
  stylesheet.href = new URL('./music-app.css', document.currentScript?.src || location.href).href;
  document.head.append(stylesheet);
  let root, state, inflight = false, busy = false, timer, epoch = 0, listFocus;
  let library = 'tracks';
  const scrollPositions = {tracks: 0, local: 0};
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
  function updateList(list, signature, html) {
    if (list.dataset.signature === signature) return;
    const focused = document.activeElement;
    if (list.contains(focused) && focused.matches('button')) {
      const key = ['track', 'local', 'remove'].find(key => focused.dataset[key]);
      listFocus = {list: list.id, key, id: focused.dataset[key], selected: focused.classList.contains('keyboard-selected')};
    }
    list.dataset.signature = signature;
    list.innerHTML = html;
    // Busy snapshots replace disabled rows. Restore the same row after completion,
    // unless the user has moved focus to another control or library in the meantime.
    if (!busy && listFocus?.list === list.id && !list.hidden) {
      const current = document.activeElement;
      if (current === document.body || list.contains(current)) {
        const button = [...list.querySelectorAll('button')].find(button => button.dataset[listFocus.key] === listFocus.id);
        if (button && !button.disabled) {
          button.classList.toggle('keyboard-selected', listFocus.selected);
          button.focus({preventScroll: true});
        }
      }
      listFocus = undefined;
    }
  }
  function selectLibrary(next) {
    if (library === next) return;
    const scroll = el('musicListScroll');
    scrollPositions[library] = scroll.scrollTop;
    library = next; listFocus = undefined;
    el('musicTrackList').hidden = next !== 'tracks';
    el('musicLocalList').hidden = next !== 'local';
    root.querySelectorAll('[data-music-library]').forEach(button => {
      const selected = button.dataset.musicLibrary === next;
      button.setAttribute('aria-selected', selected);
      button.classList.toggle('primary', selected);
      button.classList.toggle('secondary', !selected);
    });
    scroll.scrollTop = scrollPositions[next];
  }
  function render(next) {
    state = next;
    const caps = next.capabilities || {};
    el('musicTitle').textContent = next.title || '当前没有音乐';
    el('musicTitle').title = el('musicTitle').textContent;
    const names = {game: '游戏曲目', background: '游戏背景音乐', netmusic: '原便携播放器', local: '个人本地音乐', other: '附近播放器', idle: '游戏音乐', unavailable: '音乐不可用'};
    const status = {playing: '播放中', paused: '已暂停', loading: '正在解码', idle: '已停止'};
    el('musicSource').textContent = (names[next.kind] || '游戏音乐') + ' · ' + (status[next.status] || '等待状态');
    el('musicReason').textContent = next.reason || '';
    el('musicMessage').textContent = next.message || '';
    ['musicSource', 'musicReason', 'musicMessage'].forEach(id => el(id).title = el(id).textContent);
    el('music-play').textContent = ['game', 'local'].includes(next.kind) ? '从头播放' : '播放';
    el('musicPause').textContent = next.status === 'paused' ? '继续' : '暂停';
    el('musicPause').disabled = busy || !caps.pause;
    ['play', 'stop', 'previous', 'next'].forEach(action => el('music-' + action).disabled = busy || !caps[action]);
    el('musicImport').disabled = busy || !caps.import;
    el('musicVolumeLabel').textContent = (next.volumeSource === 'records' ? '唱片音量' : '音乐音量') + ' · Minecraft 原生选项';
    const slider = el('musicVolume');
    if (document.activeElement !== slider) slider.value = Math.round((Number(next.volume) || 0) * 100);
    el('musicVolumeValue').textContent = `${slider.value}%`;
    el('musicMute').textContent = next.muted ? '已静音 · 主音量 ' + Math.round((Number(next.master) || 0) * 100) + '%' : '主音量 ' + Math.round((Number(next.master) || 0) * 100) + '%';
    const available = next.tracks || [], songList = el('musicTrackList');
    el('musicTrackCount').textContent = `${available.length} 首`;
    el('musicPlaylistReason').textContent = next.playlistReason || '';
    el('musicPlaylistReason').title = el('musicPlaylistReason').textContent;
    const songSignature = JSON.stringify([available, caps.select, busy]);
    updateList(songList, songSignature, available.length ? available.map(track => `<article class="task-card music-track-row ${track.active ? 'music-current' : ''}" title="${safe(track.source)}" ${track.active ? 'aria-current="true"' : ''}><div class="music-track-info"><strong class="task-title" title="${safe(track.title)}">${safe(track.title)}</strong>${track.active ? '<span class="music-track-current">当前</span>' : ''}</div><div class="detail-actions"><button class="secondary" data-track="${safe(track.id)}" aria-label="${track.active ? '从头播放' : '播放'} ${safe(track.title)}" ${busy || !caps.select ? 'disabled' : ''}>${track.active ? '重播' : '播放'}</button></div></article>`).join('') : '<div class="task-empty">当前没有可选曲目。可导入本机音乐，或为原便携播放器装入歌单。</div>');
    const tracks = next.localTracks || [], signature = JSON.stringify([tracks, caps.local, busy, next.busy]);
    el('musicLocalCount').textContent = `${tracks.length} 首`;
    updateList(el('musicLocalList'), signature, tracks.length ? tracks.map(track => `<article class="task-card music-track-row ${track.active ? 'music-current' : ''}" title="${safe(track.format)}" ${track.active ? 'aria-current="true"' : ''}><div class="music-track-info"><strong class="task-title" title="${safe(track.title)}">${safe(track.title)}</strong>${track.active ? '<span class="music-track-current">当前</span>' : ''}</div><div class="detail-actions"><button class="secondary" data-local="${safe(track.id)}" aria-label="播放 ${safe(track.title)}" ${busy || !caps.local ? 'disabled' : ''}>播放</button><button class="secondary" data-remove="${safe(track.id)}" aria-label="移除 ${safe(track.title)} 的本机副本" title="仅移除本机副本，保留原文件" ${busy || next.busy ? 'disabled' : ''}>移除</button></div></article>`).join('') : '<div class="task-empty">还没有本机音乐。点击「导入」选择本机文件。</div>');
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
    if (root) return; root = section; root.classList.add('music-app');
    root.innerHTML = `<div class="toolbar"><button class="back" data-open="home" aria-label="返回主页">‹</button><div><div class="eyebrow">APP / MUSIC</div><h2>音乐</h2></div></div>
      <div class="music-layout"><article class="detail-card settings-panel music-player" aria-label="音乐播放器">
      <div class="music-heading"><span class="pill" id="musicSource">正在读取游戏音乐。</span><h3 id="musicTitle">当前没有音乐</h3></div>
      <div class="detail-actions music-controls"><button id="music-previous" class="secondary" disabled>上一首</button><button id="music-play" class="secondary" disabled>播放</button><button id="music-next" class="secondary" disabled>下一首</button><button id="musicPause" class="primary" disabled>暂停</button><button id="music-stop" class="secondary" disabled>停止</button></div>
      <div class="music-volume"><div class="setting-row"><label for="musicVolume" id="musicVolumeLabel">音乐音量 · Minecraft 原生选项</label><input id="musicVolume" type="range" min="0" max="100" step="1" aria-label="当前音乐来源音量"><output id="musicVolumeValue">0%</output></div><p id="musicMute"></p></div>
      <div class="music-feedback"><p class="manual-hint" id="musicReason"></p><p class="manual-hint" id="musicMessage" role="status" aria-live="polite"></p></div></article>
      <aside class="music-library" aria-label="播放列表"><div class="music-library-head"><strong class="music-library-label">播放列表</strong><button id="musicImport" class="secondary" title="导入本机音乐，仅在本机复制保存，不上传" disabled>导入</button></div>
      <div class="music-tabs" role="tablist" aria-label="曲目来源"><button id="musicTracksTab" class="primary" role="tab" aria-selected="true" aria-controls="musicTrackList" data-music-library="tracks">游戏／随身 <span id="musicTrackCount">0 首</span></button><button id="musicLocalTab" class="secondary" role="tab" aria-selected="false" aria-controls="musicLocalList" data-music-library="local">本机 <span id="musicLocalCount">0 首</span></button></div>
      <div class="music-list-scroll" id="musicListScroll"><div class="task-list" id="musicTrackList" role="tabpanel" aria-labelledby="musicTracksTab"></div><div class="task-list" id="musicLocalList" role="tabpanel" aria-labelledby="musicLocalTab" hidden></div></div>
      <div class="music-library-note"><p class="manual-hint" id="musicPlaylistReason"></p><span title="仅在本机复制保存，不上传。支持 OGG Vorbis、PCM WAV；MP3 / FLAC / AAC 按现有解码器能力验证。退出音乐 APP 停止本机播放。">本机导入不上传 · OGG / WAV</span></div></aside></div>`;
    root.addEventListener('click', event => {
      const button = event.target.closest('button'); if (!button || button.disabled) return;
      if (button.dataset.open === 'home') { window.terminalReturnHome ? window.terminalReturnHome() : window.muxi?.home(); return; }
      if (button.dataset.musicLibrary) selectLibrary(button.dataset.musicLibrary);
      else if (button.id === 'musicImport') act('music.import');
      else if (button.id === 'musicPause') control(state.status === 'paused' ? 'resume' : 'pause');
      else if (button.id.startsWith('music-')) control(button.id.slice(6));
      else if (button.dataset.track) act('music.select:' + button.dataset.track);
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

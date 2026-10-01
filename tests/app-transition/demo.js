(function () {
  'use strict';
  var names = {guide: '游戏指南', tasks: '任务', custom: '自定义应用'};
  var reduced = false;
  var transition;
  var pending = new Map();
  function home() {
    document.getElementById('demo-home').hidden = false;
    document.getElementById('demo-content').hidden = true;
  }
  function cancel(token) {
    clearTimeout(pending.get(token));
    pending.delete(token);
  }
  function setup() {
    transition = MuxiAppTransition.create({
      host: document.getElementById('viewport'), timeoutMs: 2000, reducedMotion: reduced,
      onStateChange: function (event) { document.getElementById('demo-log').textContent = event.state + (event.id ? ' / ' + event.id : ''); },
      onCancel: function (event) { cancel(event.token); if (event.reason === 'back') home(); },
      onCloseStart: function () { home(); },
      onTimeout: function (event) { cancel(event.token); },
      onRetry: function (event) { launch(event.id, 'success'); }
    });
    window.demoTransition = transition;
  }
  function launch(id, mode) {
    var source = document.querySelector('[data-demo-app="' + id + '"]');
    var request = transition.begin({id: id, name: names[id], source: source, icon: source.querySelector('.app-icon')});
    if (pending.has(request.token)) return request;
    if (mode === 'hold') return request;
    pending.set(request.token, setTimeout(function () {
      pending.delete(request.token);
      if (!request.isCurrent()) return;
      if (mode === 'error') { request.fail('连接暂时不可用，请重试或返回主页。'); return; }
      document.getElementById('content-title').textContent = names[id];
      document.getElementById('demo-home').hidden = true;
      document.getElementById('demo-content').hidden = false;
      request.ready();
    }, mode === 'error' ? 260 : 550));
    return request;
  }
  document.querySelectorAll('[data-demo-app]').forEach(function (button) { button.addEventListener('click', function () { launch(button.dataset.demoApp, 'success'); }); });
  document.getElementById('content-back').addEventListener('click', function () { transition.close('home').then(home); });
  document.getElementById('scenario-home').addEventListener('click', function () { transition.close('home').then(home); });
  document.getElementById('scenario-loading').addEventListener('click', function () { home(); launch('custom', 'hold'); });
  document.getElementById('scenario-error').addEventListener('click', function () { home(); launch('custom', 'error'); });
  document.getElementById('scenario-timeout').addEventListener('click', function () { home(); launch('tasks', 'hold'); });
  document.getElementById('scenario-reduced').addEventListener('click', function (event) {
    transition.destroy(); reduced = !reduced; home(); setup();
    event.currentTarget.textContent = '减少动效：' + (reduced ? '开' : '关');
  });
  window.demoLaunch = launch;
  window.demoHome = function () { transition.cancel('home'); home(); };
  setup();
})();

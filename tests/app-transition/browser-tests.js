/* Real-DOM lifecycle regression suite; driven by run-browser-qa.mjs. */
window.runTransitionTests = async function () {
  'use strict';
  var results = [];
  var sleep = function (ms) { return new Promise(function (resolve) { setTimeout(resolve, ms); }); };
  function assert(condition, message) { if (!condition) throw new Error(message); }
  async function test(name, run) {
    var host = document.createElement('div');
    host.style.cssText = 'position:static;width:300px;height:320px;margin:20px';
    var source = document.createElement('button');
    source.textContent = 'app'; host.appendChild(source); document.body.appendChild(host);
    var events = [], instance;
    function create(extra) {
      instance = MuxiAppTransition.create(Object.assign({host: host, timeoutMs: 500, minDurationMs: 0, reducedMotion: true, onCancel: function (event) { events.push(event); }}, extra));
      return instance;
    }
    try {
      await run({host: host, source: source, create: create, events: events, layer: function () { return host.querySelector('.mt-launch-layer'); }});
      results.push({name: name, passed: true});
    } catch (error) { results.push({name: name, passed: false, error: String(error.stack || error)}); }
    finally { if (instance) instance.destroy(); host.remove(); }
  }
  await test('built-in and custom app metadata stays plain text', function (f) {
    var t = f.create(); var r = t.begin({id: 'custom:one', name: '<img src=x onerror=alert(1)>'});
    assert(f.layer().querySelector('.mt-launch-title').textContent.includes('<img'), 'name changed');
    assert(!f.layer().querySelector('img'), 'unsafe markup inserted');
    assert(r.ready() && f.layer().hidden, 'reduced ready did not hide');
  });
  await test('duplicate opening and loading clicks share the same request', async function (f) {
    var t = f.create(); var first = t.begin({id: 'guide'});
    assert(first === t.begin({id: 'guide'}), 'opening duplicated');
    await sleep(20);
    assert(t.getState().state === 'loading', 'did not enter loading');
    assert(first === t.begin({id: 'guide'}), 'loading duplicated');
  });
  await test('new navigation inside the same app uses navigationKey', function (f) {
    var t = f.create(); var first = t.begin({id: 'web', navigationKey: 'page-a'});
    var second = t.begin({id: 'web', navigationKey: 'page-b'});
    assert(second.token > first.token && !first.isCurrent(), 'same app navigation reused stale token');
    assert(f.events[0].reason === 'superseded', 'old view not notified');
  });
  await test('stale success failure and cancel cannot touch newer navigation', function (f) {
    var t = f.create(); var first = t.begin({id: 'guide'}); var second = t.begin({id: 'custom'});
    assert(!first.ready() && !first.fail('old') && !first.cancel(), 'stale operation accepted');
    assert(t.getState().token === second.token && !f.layer().hidden, 'new overlay changed');
  });
  await test('opening captures icon coordinates within the content host', function (f) {
    var t = f.create(); t.begin({id: 'guide', source: f.source});
    assert(parseFloat(f.layer().style.getPropertyValue('--mt-origin-top')) < 20, 'origin did not follow source');
  });
  await test('fast readiness has bounded short animation then releases overlay', async function (f) {
    var t = f.create({reducedMotion: false, minDurationMs: 180}); var r = t.begin({id: 'guide', source: f.source}); r.ready();
    assert(!f.layer().hidden, 'fast opening flashed');
    assert(r === t.begin({id: 'guide'}), 'duplicate during reveal duplicated');
    await sleep(340);
    assert(f.layer().hidden && t.getState().state === 'ready', 'overlay never released');
    assert(!f.source.classList.contains('mt-launch-source'), 'pressed icon stuck');
  });
  await test('timeout produces recovery and rejects late success', async function (f) {
    var timeouts = 0; var t = f.create({timeoutMs: 250, onTimeout: function () { timeouts++; }});
    var r = t.begin({id: 'slow'}); await sleep(310);
    assert(t.getState().state === 'error' && timeouts === 1, 'timeout did not recover');
    assert(!r.ready() && !r.fail('late') && !f.layer().hidden, 'late result changed error');
    assert(f.layer().querySelector('.mt-launch-pixels').hidden, 'infinite loading remained');
    assert(f.host.getAttribute('aria-busy') !== 'true', 'busy stuck');
  });
  await test('transport failure shows usable return and retry', function (f) {
    var t = f.create({onRetry: function () {}}); var r = t.begin({id: 'web'}); r.fail('连接失败');
    assert(t.getState().state === 'error', 'error absent');
    assert(!f.layer().querySelector('.mt-launch-retry').hidden, 'retry absent');
    assert(f.layer().querySelector('.mt-launch-status').textContent === '连接失败', 'failure omitted');
  });
  await test('retry double-click is coalesced while shell starts a new request', function (f) {
    var retries = 0; var t = f.create({onRetry: function () { retries++; }}); t.begin({id: 'web'}).fail('offline');
    var button = f.layer().querySelector('.mt-launch-retry'); button.click(); button.click();
    assert(retries === 1 && button.disabled, 'duplicate retry');
    t.begin({id: 'web'}).fail('offline again'); button.click();
    assert(retries === 2, 'new request retry stayed disabled');
  });
  await test('without retry adapter failure still offers return', function (f) {
    var t = f.create(); t.begin({id: 'web'}).fail();
    assert(f.layer().querySelector('.mt-launch-retry').hidden, 'dead retry offered');
    assert(!f.layer().querySelector('.mt-launch-back').hidden, 'return absent');
  });
  await test('return during loading rejects delayed completion and restores focus', async function (f) {
    var covered = false; var t = f.create({reducedMotion: false, onCloseStart: function () { covered = true; }});
    var r = t.begin({id: 'guide', source: f.source});
    var promise = t.close('home');
    assert(covered && t.getState().state === 'closing', 'shell not notified to show home');
    assert(promise === t.close('home'), 'duplicate close was not shared');
    assert(!r.ready(), 'delayed success accepted during close');
    assert(await promise, 'close did not complete');
    assert(f.layer().hidden && document.activeElement === f.source, 'focus or overlay remained');
  });
  await test('new navigation during close cancels the old return promise', async function (f) {
    var t = f.create({reducedMotion: false}); t.begin({id: 'guide'}).ready();
    var promise = t.close(); var r = t.begin({id: 'tasks'});
    assert(!(await promise), 'superseded close completed navigation');
    await sleep(190);
    assert(t.getState().token === r.token && !f.layer().hidden, 'old closing timer hid new app');
  });
  await test('reduced motion has immediate ready and return with no animation', async function (f) {
    var t = f.create(); var r = t.begin({id: 'guide', source: f.source});
    assert(getComputedStyle(f.layer()).animationName === 'none', 'reduced overlay animates');
    assert(!f.source.classList.contains('mt-launch-source'), 'reduced source animates');
    r.ready(); assert(f.layer().hidden, 'reduced ready delayed');
    var done = t.close(); assert(f.layer().hidden && await done, 'reduced return delayed');
  });
  await test('back button returns from timeout without native navigation', async function (f) {
    var t = f.create({timeoutMs: 250}); t.begin({id: 'web'}); await sleep(300);
    f.layer().querySelector('.mt-launch-back').focus(); f.layer().querySelector('.mt-launch-back').click();
    assert(t.getState().state === 'idle' && f.layer().hidden, 'back stuck');
    assert(f.events[0].reason === 'back', 'back adapter not notified');
    assert(!f.layer().contains(document.activeElement), 'hidden overlay kept focus');
  });
  await test('native close immediate cancellation clears all visual state', async function (f) {
    var t = f.create({reducedMotion: false, timeoutMs: 250}); var r = t.begin({id: 'guide', source: f.source});
    t.cancel('terminal-close'); await sleep(310);
    assert(f.layer().hidden && t.getState().state === 'idle' && !r.isCurrent(), 'close was revived');
    assert(!f.source.classList.contains('mt-launch-source'), 'source animation stuck');
  });
  await test('destroy while closing resolves promise and detaches overlay', async function (f) {
    var t = f.create({reducedMotion: false}); t.begin({id: 'guide'}); var closing = t.close(); t.destroy(); t.destroy();
    assert(!(await closing) && !f.layer(), 'destroy retained promise or DOM');
    assert(f.host.style.position === 'static' && !f.host.classList.contains('mt-transition-host'), 'host not restored');
    var threw = false; try { t.begin({id: 'x'}); } catch (error) { threw = true; }
    assert(threw, 'destroyed instance reused');
  });
  await test('destroy preserves pre-existing host attributes and classes', function (f) {
    f.host.setAttribute('aria-busy', 'false'); f.host.classList.add('mt-transition-host');
    var t = f.create(); t.begin({id: 'x'}); t.destroy();
    assert(f.host.getAttribute('aria-busy') === 'false' && f.host.classList.contains('mt-transition-host'), 'host state lost');
  });
  await test('custom icon is visual-only and strips IDs and inline handlers', function (f) {
    var icon = document.createElement('span'); icon.innerHTML = '<button id="dup" onclick="alert(1)">X</button>';
    var t = f.create(); t.begin({id: 'custom', icon: icon});
    var clone = f.layer().querySelector('.mt-launch-icon button');
    assert(!clone.id && !clone.hasAttribute('onclick') && clone.tabIndex === -1, 'icon remained interactive');
  });
  await test('20 rapid alternating clicks preserve only the last request', async function (f) {
    var t = f.create({timeoutMs: 250}); var requests = [];
    for (var i = 0; i < 20; i++) requests.push(t.begin({id: i % 2 ? 'tasks' : 'guide'}));
    requests.slice(0, -1).forEach(function (r) { assert(!r.ready(), 'old request accepted'); });
    requests[19].ready(); await sleep(310);
    assert(t.getState().state === 'ready' && f.layer().hidden, 'old timeout changed ready');
  });
  await test('statusbar and shell stay mounted and keep their geometry', async function (f) {
    var shell = document.querySelector('.preview-device'), bar = document.querySelector('.statusbar');
    var bounds = JSON.stringify(bar.getBoundingClientRect().toJSON()); var href = location.href;
    var t = f.create(); t.begin({id: 'custom'}).ready(); await t.close();
    assert(document.querySelector('.preview-device') === shell && document.querySelector('.statusbar') === bar, 'shell replaced');
    assert(bounds === JSON.stringify(bar.getBoundingClientRect().toJSON()) && href === location.href, 'outer geometry or URL changed');
  });
  return {passed: results.filter(function (item) { return item.passed; }).length, total: results.length, results: results};
};

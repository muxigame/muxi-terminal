/* Presentation only: the shell owns navigation and app-view readiness. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.MuxiAppTransition = factory();
})(typeof window !== 'undefined' ? window : this, function () {
  'use strict';

  function create(options) {
    options = options || {};
    var host = options.host;
    if (!host || !host.ownerDocument) throw new TypeError('App transition requires an internal content host');
    var doc = host.ownerDocument;
    var win = doc.defaultView;
    var media = win.matchMedia ? win.matchMedia('(prefers-reduced-motion: reduce)') : null;
    var reduced = typeof options.reducedMotion === 'boolean' ? options.reducedMotion : !!(media && media.matches);
    var timeoutMs = bounded(options.timeoutMs, 10000, 250, 60000);
    var minDurationMs = bounded(options.minDurationMs, 180, 0, 500);
    var exitMs = 110;
    var serial = 0;
    var active = null;
    var state = 'idle';
    var disposed = false;
    var originalBusy = host.getAttribute('aria-busy');
    var originalPosition = host.style.position;
    var positioned = win.getComputedStyle(host).position === 'static';
    if (positioned) host.style.position = 'relative';
    var hadHostClass = host.classList.contains('mt-transition-host');
    host.classList.add('mt-transition-host');

    function element(tag, className, text) {
      var node = doc.createElement(tag);
      node.className = className;
      if (text) node.textContent = text;
      return node;
    }
    var layer = element('div', 'mt-launch-layer');
    layer.hidden = true;
    layer.setAttribute('role', 'region');
    layer.setAttribute('aria-label', '应用打开状态');
    var card = element('div', 'mt-launch-card');
    var icon = element('div', 'mt-launch-icon');
    icon.setAttribute('aria-hidden', 'true');
    var caption = element('div', 'mt-launch-caption', 'APP / OPEN');
    var title = element('div', 'mt-launch-title');
    var status = element('p', 'mt-launch-status');
    status.setAttribute('role', 'status');
    status.setAttribute('aria-live', 'polite');
    status.setAttribute('aria-atomic', 'true');
    var pixels = element('div', 'mt-launch-pixels');
    pixels.setAttribute('aria-hidden', 'true');
    for (var i = 0; i < 4; i++) pixels.appendChild(element('i', 'mt-launch-pixel'));
    var actions = element('div', 'mt-launch-actions');
    var retry = element('button', 'mt-launch-button mt-launch-retry', '重试');
    var back = element('button', 'mt-launch-button mt-launch-back', '返回');
    retry.type = back.type = 'button';
    actions.appendChild(retry);
    actions.appendChild(back);
    [icon, caption, title, status, pixels, actions].forEach(function (node) { card.appendChild(node); });
    layer.appendChild(card);
    host.appendChild(layer);

    function applyMotion() { layer.classList.toggle('mt-reduced-motion', reduced); }
    applyMotion();
    function notify(name, value) {
      if (typeof options[name] === 'function') options[name](value);
    }
    function setState(next) {
      state = next;
      layer.setAttribute('data-state', next);
      notify('onStateChange', {state: next, token: active && active.token, id: active && active.app.id});
    }
    function restoreBusy() {
      if (originalBusy === null) host.removeAttribute('aria-busy');
      else host.setAttribute('aria-busy', originalBusy);
    }
    function clearTimers(request) {
      request.timers.forEach(function (timer) { win.clearTimeout(timer); });
      request.timers.length = 0;
    }
    function clearSource(request) {
      if (request.source && !request.hadSourceClass) request.source.classList.remove('mt-launch-source');
    }
    function isCurrent(request) { return !disposed && active === request; }
    function later(request, callback, delay) {
      request.timers.push(win.setTimeout(function () { if (isCurrent(request)) callback(); }, delay));
    }
    function hide() {
      // Hidden layers must never retain focus over the child browser.
      if (layer.contains(doc.activeElement)) doc.activeElement.blur();
      layer.hidden = true;
      restoreBusy();
    }
    function terminate(request, reason, nextState, focusSource) {
      if (!isCurrent(request)) return false;
      clearTimers(request);
      clearSource(request);
      request.settled = true;
      active = null;
      hide();
      setState(nextState);
      if (focusSource && request.source && request.source.isConnected && !request.source.disabled) request.source.focus();
      notify('onCancel', {id: request.app.id, token: request.token, reason: reason});
      if (request.resolveClose) request.resolveClose(false);
      return true;
    }
    function setOrigin(source) {
      var bounds = host.getBoundingClientRect();
      var box = source && source.getBoundingClientRect();
      var width = bounds.width || 1, height = bounds.height || 1;
      var visible = box && box.width > 0 && box.height > 0;
      var left = visible ? box.left - bounds.left : width / 2 - 24;
      var top = visible ? box.top - bounds.top : height / 2 - 24;
      var right = visible ? box.right - bounds.left : width / 2 + 24;
      var bottom = visible ? box.bottom - bounds.top : height / 2 + 24;
      function percent(value, total) { return Math.max(0, Math.min(100, value / total * 100)) + '%'; }
      layer.style.setProperty('--mt-origin-top', percent(top, height));
      layer.style.setProperty('--mt-origin-right', percent(width - right, width));
      layer.style.setProperty('--mt-origin-bottom', percent(height - bottom, height));
      layer.style.setProperty('--mt-origin-left', percent(left, width));
    }
    function fail(request, message, reason) {
      if (!isCurrent(request) || request.settled) return false;
      clearTimers(request);
      clearSource(request);
      request.settled = true;
      request.failed = true;
      restoreBusy();
      status.textContent = message || '应用未能打开，请重试或返回。';
      caption.textContent = 'APP / RETRY';
      pixels.hidden = true;
      retry.hidden = typeof options.onRetry !== 'function';
      back.textContent = '返回主页';
      setState('error');
      if (reason === 'timeout') notify('onTimeout', {id: request.app.id, token: request.token});
      return true;
    }
    function ready(request) {
      if (!isCurrent(request) || request.settled) return false;
      request.settled = true;
      clearTimers(request);
      clearSource(request);
      function reveal() {
        restoreBusy();
        if (reduced) { hide(); setState('ready'); return; }
        setState('revealing');
        later(request, function () { hide(); setState('ready'); }, exitMs);
      }
      var remaining = reduced ? 0 : Math.max(0, minDurationMs - (Date.now() - request.startedAt));
      if (remaining) later(request, reveal, remaining);
      else reveal();
      return true;
    }
    function begin(app) {
      if (disposed) throw new Error('App transition has been destroyed');
      if (!app || app.id === undefined || app.id === null) throw new TypeError('App transition requires an app id');
      var key = String(app.navigationKey === undefined ? app.id : app.navigationKey);
      if (active && active.key === key && (state === 'opening' || state === 'loading' || state === 'revealing')) return active.handle;
      if (active) terminate(active, 'superseded', 'idle', false);
      var request = {
        app: {id: String(app.id), name: String(app.name || app.id), navigationKey: key},
        key: key, token: ++serial, timers: [], settled: false, failed: false,
        source: app.source || null, startedAt: Date.now()
      };
      var handle = {
        id: request.app.id, token: request.token,
        ready: function () { return ready(request); },
        fail: function (message) { return fail(request, message, 'failure'); },
        cancel: function (reason) { return terminate(request, reason || 'cancelled', 'idle', false); },
        isCurrent: function () { return isCurrent(request); }
      };
      request.handle = handle;
      active = request;
      title.textContent = request.app.name;
      status.textContent = '正在打开…';
      caption.textContent = 'APP / OPEN';
      pixels.hidden = false;
      retry.hidden = true;
      retry.disabled = false;
      back.textContent = '返回';
      back.hidden = false;
      icon.textContent = '';
      setOrigin(app.icon || request.source);
      // Reuse either a built-in or custom icon without copying interactive markup.
      if (app.icon && app.icon.cloneNode) {
        var clone = app.icon.cloneNode(true);
        [clone].concat(Array.prototype.slice.call(clone.querySelectorAll('*'))).forEach(function (node) {
          node.removeAttribute('id');
          Array.prototype.slice.call(node.attributes).forEach(function (attribute) {
            if (/^on/i.test(attribute.name)) node.removeAttribute(attribute.name);
          });
          if (node.matches('button, a, input, select, textarea, [tabindex]')) node.setAttribute('tabindex', '-1');
        });
        icon.appendChild(clone);
      } else icon.textContent = request.app.name.slice(0, 2);
      host.setAttribute('aria-busy', 'true');
      layer.hidden = false;
      // Restart the short entry animation for every distinct navigation.
      layer.classList.remove('mt-launch-enter');
      void layer.offsetWidth;
      layer.classList.add('mt-launch-enter');
      if (request.source) {
        request.hadSourceClass = request.source.classList.contains('mt-launch-source');
        if (!reduced) request.source.classList.add('mt-launch-source');
      }
      setState('opening');
      later(request, function () { clearSource(request); setState('loading'); }, reduced ? 0 : 160);
      later(request, function () { fail(request, '打开超时，请重试或返回主页。', 'timeout'); }, timeoutMs);
      return handle;
    }
    function close(reason) {
      if (!active || disposed) return Promise.resolve(false);
      var request = active;
      if (state === 'closing') return request.closePromise;
      clearTimers(request);
      clearSource(request);
      request.settled = true;
      request.closePromise = new Promise(function (resolve) { request.resolveClose = resolve; });
      restoreBusy();
      layer.hidden = false;
      caption.textContent = 'APP / HOME';
      status.textContent = '返回主页…';
      pixels.hidden = true;
      retry.hidden = back.hidden = true;
      function finish() {
        if (!isCurrent(request)) return;
        var resolve = request.resolveClose;
        request.resolveClose = null;
        terminate(request, reason || 'home', 'idle', true);
        resolve(true);
      }
      request.finishClose = finish;
      setState('closing');
      // The shell mounts home and hides its child view while this layer covers it.
      notify('onCloseStart', {id: request.app.id, token: request.token, reason: reason || 'home'});
      if (!isCurrent(request)) return request.closePromise;
      if (reduced) finish();
      else later(request, finish, 150);
      return request.closePromise;
    }
    function onBack() {
      close('back');
    }
    function onRetry() {
      if (!active || !active.failed || active.retryRequested) return;
      var request = active;
      // Disable before notifying; double-clicks cannot start duplicate retries.
      request.retryRequested = true;
      retry.disabled = true;
      notify('onRetry', {id: request.app.id, navigationKey: request.key, token: request.token});
    }
    function onMotionChange(event) {
      if (typeof options.reducedMotion === 'boolean') return;
      reduced = event.matches;
      applyMotion();
      if (reduced && active) {
        clearSource(active);
        if (state === 'closing') active.finishClose();
        else if (active.settled && !active.failed) { clearTimers(active); hide(); setState('ready'); }
      }
    }
    back.addEventListener('click', onBack);
    retry.addEventListener('click', onRetry);
    if (media && media.addEventListener) media.addEventListener('change', onMotionChange);
    else if (media && media.addListener) media.addListener(onMotionChange);
    return {
      begin: begin,
      close: close,
      cancel: function (reason) { return active ? terminate(active, reason || 'cancelled', 'idle', false) : false; },
      getState: function () { return {state: state, id: active && active.app.id, token: active && active.token}; },
      destroy: function () {
        if (disposed) return;
        if (active) terminate(active, 'destroyed', 'idle', false);
        disposed = true;
        back.removeEventListener('click', onBack);
        retry.removeEventListener('click', onRetry);
        if (media && media.removeEventListener) media.removeEventListener('change', onMotionChange);
        else if (media && media.removeListener) media.removeListener(onMotionChange);
        layer.remove();
        if (!hadHostClass) host.classList.remove('mt-transition-host');
        if (positioned) host.style.position = originalPosition;
        restoreBusy();
        setState('destroyed');
      }
    };
  }
  function bounded(value, fallback, min, max) {
    return typeof value === 'number' && isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
  }
  return {create: create};
});

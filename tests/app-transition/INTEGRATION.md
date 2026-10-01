# 玩家终端 APP 打开过渡：接入合同

基线：`0.2.1 / c674e73656b7e7b51f507d21a2afed5fc276ab4b`。这是独立表现层组件；唯一 shell 和子浏览视图由网页应用任务 `01a0f671-5e2f-7052-823d-cd6ee244d035` 管理。本补丁不注册导航监听，不改现有 `app.js` / `index.html`，不发送 nativeQuery / cefQuery，不处理 SSO。

## 接入位置

1. shell 页面加载 `app-transition.css` 和 `app-transition.js`。
2. `host` 传终端内部内容区的唯一 DOM 容器。状态栏、外壳、系统导航必须在该容器外。
3. 每个 shell 只创建一个实例。组件在 host 内追加一层绝对定位的过渡 UI，取消或完成后隐藏，不替换 host 或 shell。
4. 内置 APP、自定义 APP 使用同一接口。提供 `source` 点击按钮以及 `icon` 图标节点即可按图标位置展开；缺失图标时显示应用名首两个字符并从内容中心展开。

```js
const transition = MuxiAppTransition.create({
  host: document.querySelector('#app-content'),
  // 默认尊重系统 prefers-reduced-motion；仅在用户有设置时传布尔值。
  reducedMotion: userPreferences.reduceMotion,
  timeoutMs: 10000,
  onCancel({token, reason}) {
    shell.cancelPendingView(token); // 取消本次待开视图；仅操作对应 token。
  },
  onTimeout({token}) {
    shell.cancelPendingView(token); // 终止等待，错误恢复 UI 会保留。
  },
  onCloseStart({token}) {
    shell.hideChildView(token);
    shell.showHome(); // 收回覆盖层已出现，此时准备主页。
  },
  onRetry({id}) {
    openApp(shell.findApp(id)); // 必须同步调用 begin，或保持按钮禁用至 begin。
  },
  onStateChange({state, token}) {
    // Java 独立子浏览视图不会被 DOM z-index 盖住，shell 必须自行协调合成。
    if (state === 'opening' || state === 'loading' || state === 'error') {
      shell.hideChildView(token);
    }
    if (state === 'revealing' || state === 'ready') {
      shell.showPreparedChildView(token);
    }
  }
});

const pending = new Map();
function openApp(app, navigationKey) {
  const request = transition.begin({
    id: app.id, name: app.name, source: app.button, icon: app.icon,
    navigationKey: navigationKey === undefined ? app.id : navigationKey
  });
  // 同一等待期间重复点击返回相同 handle。shell 也必须用 token 去重传输。
  if (pending.has(request.token)) return pending.get(request.token);
  // shell.openView 是父级架构接口的占位名称；应替换为父级真实接口。
  const opening = Promise.resolve().then(() => shell.openView(app, request.token))
    .then(view => {
      if (!request.isCurrent()) { shell.disposeView(view); return; }
      // ready() 成功才可接收视图；超时/失败后的迟到成功返回 false。
      shell.prepareChildView(view, request.token);
      if (!request.ready()) shell.disposeView(view);
    }, error => request.fail('应用未能打开，请重试或返回主页。'))
    .finally(() => pending.delete(request.token));
  pending.set(request.token, opening);
  return opening;
}

// 持久 shell 的主页按钮、返回键调用；内置过渡“返回”按钮会自行调用 close。
function returnHome() {
  return transition.close('home').then(completed => {
    if (completed) shell.finishReturnHome();
  });
}
// 原生终端关闭、shell 卸载立即取消，避免等待动画再关闭原生窗口。
function terminalClose() { transition.cancel('terminal-close'); shell.closeTerminal(); }
function shellUnmount() { transition.destroy(); }
```

上例仅定义适配顺序，不是现有 Java 或安全导航协议。若父级的子视图合成不能在 `revealing` 时显示下层内容，改为收到 `ready` 再显示即可；展开/收回和状态栏仍在持久 shell 内运行。`ready` 的依据应是父级真实页面就绪或可信的渲染成功通知，不能只把“导航请求已接收”当成页面加载成功。

## API 与状态

| 接口 | 行为 |
| --- | --- |
| `begin({id,name,source,icon,navigationKey})` | 立即显示覆盖层，返回 `{id,token,ready,fail,cancel,isCurrent}`。相同导航在 opening/loading/revealing 时合并；不同 navigationKey 取代旧请求。 |
| `handle.ready()` | 真实内容已准备时调用。成功返回 true。过时、已失败、超时、取消的请求返回 false。 |
| `handle.fail(message)` | 停止加载，显示失败与返回按钮；有 onRetry 才显示重试。message 按纯文本展示。 |
| `handle.cancel(reason)` / `cancel(reason)` | 立即停止对应/当前 UI 与计时器，通知 onCancel。供新导航、原生关闭或卸载使用。 |
| `close(reason='home')` | 显示返回覆盖层，通知 onCloseStart，再朝原图标位置收回。返回 Promise<boolean>；重复关闭共享同一个 Promise，期间被新导航取代或销毁返回 false。 |
| `getState()` | 返回 `{state,id,token}`。 |
| `destroy()` | 取消请求，移除覆盖层和媒体查询监听，恢复原 host position / aria-busy，重复调用安全。 |

状态流：`idle → opening → loading → revealing → ready`；快速就绪可跳过 loading。失败/超时进入 `error`；返回进入 `closing → idle`；卸载进入 `destroyed`。旧回调不能改变当前状态。token 在单个实例内递增，不承担跨 shell 或安全校验用途。

图标反馈 130 ms，内容展开 180 ms，内容显露 110 ms，返回收回 150 ms。快速内容至少展示 180 ms 打开效果；减少动效时无动画且成功/返回立即完成。默认加载期限 10 秒，可配置 250–60000 ms，超时后停止像素加载动画、恢复可操作的失败 UI，迟到成功不会把失败 UI重新打开。同一失败状态的重复重试点击仅通知一次，下一次 begin 会恢复重试能力。

## 验证和交付边界

在独立副本根目录执行：

```powershell
node tests/app-transition/run-browser-qa.mjs
```

运行器使用已安装 Chrome 与 Node 内置 CDP，无需 npm 包，仅打开本地 demo。Chrome 使用临时独立用户目录、隐藏窗口和关闭扩展；受限 Windows 执行环境需要 `--no-sandbox` 才能启动测试渲染进程。该标志只作用于此本地 QA 进程，不涉及游戏 CEF 配置。

`tests/app-transition/evidence/results.json` 保存 25 项真实浏览器检查，PNG 展示主页、图标展开、加载、内容就绪、返回收回、失败、超时与窄屏减少动效。已人工查看关键截图。检查覆盖连续点击、导航令牌、过时成功/失败/取消、重试去重、加载中返回、关闭时新导航、销毁清理、焦点释放、动态系统减少动效以及外框和 URL 稳定。

本次仅交付独立新增文件和接入合同。父级尚未给出真实 shell / 子视图接口，所以未接入原生终端，也未做 Minecraft / CEF 实机动画验证。最终集成时需按父级接口补上上述适配，重点验证 Java 子浏览视图与过渡层的合成时序。没有提交、推送、发布或重启。

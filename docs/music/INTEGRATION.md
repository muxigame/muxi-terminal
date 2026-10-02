# task6 接入清单（仅 owner 接共享文件）

音乐任务 worktree：`task-21/music-app`，分支 `candidate/terminal-music`。新增 Java 包 `net.muxigame.terminal.client.music`，资源 `music-app.js` 和独立 mixin 描述。补丁只新增文件；不要将本候选整包替换已上线或 task6 的整包。

1. 在 `META-INF/neoforge.mods.toml` 的最终整合候选注册客户端 mixin：

```toml
[[mixins]]
config="muxi_terminal.music.mixins.json"
```

2. 复用 `muxiTerminalQuery`，保持现有主框架、实际 browser 身份、generation、document 和 guarded callback 校验。仅在现有可信 handler 的校验后处理 music 请求；WEB / ACCOUNT clients 继续没有 native router。建议在 onQuery 的 guarded callback 创建后、其现有 mc.execute 前插入：

```java
java.util.function.BooleanSupplier musicValid = () ->
    TerminalBrowserSession.generation() == epoch
    && TerminalBrowserSession.trusted(browser, frame)
    && document.equals(frame.getURL());
if (net.muxigame.terminal.client.music.TerminalMusicBridge.handle(
        browser, frame, TerminalBrowserSession.trusted(browser, frame),
        request, guarded, musicValid)) return true;
```

模块额外限制 exact `mod://muxi_terminal/terminal/index.html#/music` 主框架，request 最大 192 字符；不接收 URL、文件路径、源身份、背包 slot 或原生类型。保留 owner 对 persistent 请求的拒绝。不得将该桥接附到外部 client。

3. 在 `TerminalBrowserSession.openApp` 和 `TerminalNativeBridge` 的 builtin launch allowlist 添加 `music`，标题“音乐”。保留 Kind.BUILTIN 独立内容视图，`HOME_URL + "#/music"`。不要在主页 shell 内直接切换页来绕过既有动画/内容容器。

4. 在 `#app-content` 内加入 `<section id="music" class="page"></section>`；加载 `music-app.js`，放在现有 app/navigation 脚本之后。加入主页卡片 `data-open="music"`，复用 `app-card/app-icon/app-name/app-desc`；图标可复用包内唱片资源。现有 `navigate` 的 builtin 集合、`routeFromHash` 的页面集合添加 music，名称查表补“音乐”。module 只处理页内动作，继续用 owner 现有空间方向键焦点和原生返回/退出。

5. 样式接口需与设置 owner 合并：音乐使用现有 `toolbar`、`back`、`detail-card`、`task-card`、`primary/secondary`、`task-list` 等，另直接复用 task20 已实现的 `setting-row`、`settings-panel` 共用控件。不新增音乐全局 CSS。task6 将这些设置控件提取/确认成共用样式一次，或在最终合并保留当前导出的类；若统一改类名，再同步音乐 markup。轻微放大尺度、图标、字型、颜色、间距、过渡全部沿 owner 最终样式。

6. 原生生命周期必须接 `TerminalMusicService.closeLocal()`：music 内容视图开始关闭/切去别的 APP、终端关闭，以及 native 动画 close 的权限撤销处。不要依赖 pagehide（旧 view 的 router 可能已撤销）。在 `closeContent()` 与 `animateHome()` disarm 之前，如果旧 Kind.BUILTIN music 视图正在退出，调用它；MC Screen 关闭可同样调用。该方法只停止本模块的本地流，并释放本模块对单个背景声音的暂停。Level unload 和终端 Screen 离开已有自动客户端事件兜底。绝不可调用 SoundManager.stopAll/pause/resume 来代替。

7. 音量契约：设置 `volume`=MASTER，音乐在 background/local 用 MUSIC、portable/nearby 用 RECORDS。每次 snapshot 都读取 Options；每次提交仅设置来源的 `getSoundSourceOptionInstance` 并 `save`。不覆盖 MASTER、不复制 UI 音效偏好。若 task20 增加分类滑杆，直接读取相同原生 OptionInstance 即可。

端点：`music.snapshot`、`music.import`（无路径参数）、`music.local:<UUID>`、`music.remove:<UUID>`、`music.exit`、`music.control:{"action":"pause|resume|play|stop|next|previous|volume","target":"snapshot token","value":0..1}`。只有 volume 使用 value；opaque target 由原生生成，换源会失效。

工作区候选 JAR 只证明编译，并未在其中修改共享 mods.toml / shell / bridge，因此尚未激活模块，不能拿该 JAR 直接部署。由 task6 在自己的最终候选接入以上点，再安排实机测试。

# Terminal application container integration contract

Base: c674e73656b7e7b51f507d21a2afed5fc276ab4b (0.2.1). Isolated copy: `isolated-terminal/`.

- `TerminalBrowserSession.current()/getOrCreate()`: persistent trusted HOME shell, never external navigation.
- `content()`: current independent offscreen MCEFBrowser, nullable. `activeBrowser()`: input target.
- `openApp(route)`: built-in content view, only home/guide/tasks; home closes content.
- `openWebApp(url, name)`: HTTP(S) personal app in independent client with NO message router.
- `openAccountView(url)`: separate account view, exact HTTPS account origin boundary, NO message router. SSO owner uses returned browser for native one-use exchange only.
- `home()/closeContent()/back()`: clear SSO pending state, close old view, return shell; back uses content history first.
- `state()`: immutable record generation/kind/title/url/loading/error/viewId/launchToken/rendered. generation changes on navigation; viewId is stable across redirects within one opened view. rendered is set after a native onPaint following load completion. Async owners capture generation and actual browser, and check before applying a result.
- `addStateListener(Consumer<State>)/removeStateListener(...)`: main-thread state notifications for animation/loading/error integration. Notifications superseded before delivery are discarded. Compare generation and kind; loading=false/error nonempty indicates failure while shell remains usable.
- `resizeViews(width,height,barHeight)` and `TerminalScreen` compose shell texture + content below native 22 GUI-pixel navigation bar. Independent hit tests and focus; native home/back always available.
- Animation: `beginLaunch(token,kind,title)` precedes open; the pending token is carried through the asynchronous SSO request to openAccountView. `revealView(viewId,token,visible)` and `cancelLaunch(token)` reject stale tokens. `requestHome()` requests the shell close animation; `home()` is the actual native close. The adapter receives `window.terminalOnViewState(state)` only in the owned shell. New native command `terminal.launch:{kind,id,token}` drives builtin/web/account launches; credentials never enter this request.

The completed task-7 app-transition module was applied unchanged to this isolated copy. app-container.js adapts it; index.html loads it. Do NOT additionally apply the original task-7 patch on top of this patch (new files are already included). Its 25/25 module tests are upstream evidence; this container adds native animation integration checks.

Owned files: TerminalBrowserSession, TerminalScreen (composition/input only), TerminalWebPolicy, TerminalWebApps, TerminalViewClient, TerminalNativeBridge authorization/dispatch, personal-app JS/CSS/HTML. TerminalPassportNavigation contains minimal container adaptation and should be reconciled with the SSO owner's implementation; ticket formats/endpoints unchanged. TerminalHandRenderer is left to regression owner; it can overlay `content()` texture in the screen rectangle if desired. Render fixes must preserve the two texture draws in TerminalScreen.

No AGENTS.md or .agents/skills was found under the supplied repo, its workspace ancestors, or this execution workspace. No shared source writes, commit, push, release, deployment, or running-game restart.

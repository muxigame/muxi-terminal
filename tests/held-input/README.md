# 手持终端原生输入验证

直接手持玩家终端时，公共终端浏览器继续以原来的 MCEF 动态纹理绘制。无需右键、K 或打开 Minecraft Screen，未加修饰键的方向键移动页面焦点，Enter / 小键盘 Enter 确认。主手、副手共用一次输入；同时持有两个终端也只发送一次。

`TerminalHeldInput` 在真实 `KeyboardHandler.keyPress` 内处理上述六个键。聊天、其他 Screen、失焦、未持有、死亡和退出世界会释放焦点与按键。打开原有终端 Screen 前释放手持捕获，让其原有键盘处理继续工作。Enter 的 repeat 不会再次确认新页面；接管自定义方向键时清除对应游戏绑定的按下和点击队列。WASD、F、B、Tab、Ctrl+Tab 和带修饰键的快捷键仍走原有路径。

## 运行

在当前统一工作树中运行，Python 3 和 JDK 21 路径由本机提供：

```powershell
python -B tests/held-input/build_candidate.py --java-home <JDK21>
python -B tests/held-input/prepare_runtime.py --java-home <JDK21> --core <已验证核心JAR> --framework <已验证小游戏框架JAR> --zombie <已验证生存JAR>
python -B tests/held-input/launch_runtime.py --java-home <JDK21>
# 启动命令保持运行，在另一终端运行一个检查脚本：
python -B tests/held-input/run_runtime_checks.py
# 仅补测长按确认和自定义绑定边界时，重新 prepare / launch 后使用：
python -B tests/held-input/run_lease_checks.py
```

编译先归档 Terminal 的实际 HEAD，仅叠加五个明确列出的手持输入产品文件。候选包、QA 包及日志均写入忽略的 `build/`。运行通过同级 `better-mc-remake/scripts/local_mc_debug.py` 创建全新、带归属标记的客户端和服务端目录。复用本地 MCEF 库，并只在自有目录设置离线 QA 参数。终端 QA MOD 不进入产品包。

检查脚本先等网络连接、加载 Screen 关闭及手持浏览器取得焦点，随后从游戏线程调用原生键盘入口，检查真实 CEF 页面、GPU 纹理、原生手持绘制、Core 输入包装的 finally、真实网络背包与退出资源。命令、回执和停止请求都验证本轮 runId；只正常关闭自己的实例。

这些检查使用 QA 辅助的原生窗口焦点与键盘回调，`physicalOSInput=false`。不代表物理键鼠验收，也不是完整 523/525 包业务复测。原生截图中的 Live 徽标是验证动态 DOM 绘制的临时 QA 内容。

验收记录见 [2026-10-03 原生证据](../../docs/held-input/131-native-evidence-20261003.json)。

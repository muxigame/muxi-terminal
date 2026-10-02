# Task22 原生相机 QA 启动交接

产品候选和隔离 QA 已准备在 131 的专用目录。尚未启动 MC；不得重复启动或修改生产实例。

由 task14 使用其现有 `\MuxiDesktopQA` 入口在 Session 2 一次触发以下固定脚本，并回传实际 Java PID。task22 自行读取进度、验证真实图片及功能、收集结果。task14 只协调启动与焦点。此脚本不注册、修改或删除任何任务，不抢焦点。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002\NativeCameraSession2Fixed.ps1"
```

固定实例：`C:\Users\ranzh\Documents\Codex\camera-native-task22-20261002\run-20261002-055245-420455d3`。不得改为共享或生产目录。`candidate-pins.json` 固定产品与 QA 哈希；runner 检查 Session、已启动状态和独占启动凭据。

WMI 复用 task14 已验证 QA-only `HardwareWmiTimeoutQAMixin`：在 `SystemReport.putHardware` HEAD 设置 OSHI WMI 查询期限 2000 ms，读回实际值，不符即失败。必须从实际 `boot.log` 看到 `QA_HARDWARE_WMI_TIMEOUT_MS=2000`。此 Mixin 不进入产品 JAR；不重启 WMI、服务或 VM。

进度读取原样复用 task14 `shared_progress_io.py` 的 Windows read/write/delete 共享打开及有限重试。Java 写入临时文件后以 20 次有限重试替换；读写暂时不可用只记日志，不中断测试。最终结果写入失败或没有结果均不能认定成功。正常断线后由 `Minecraft.stop()` 退出；超过期限只记录仍在运行，不 kill、不另起客户端。

世界 ready 后窗口标题为 `Muxi task22 native camera QA - isolated 131`。请协调该窗口的前台时隙。`camera-progress.json` 到 `focusCoordinationReady=true` 时，task22 需要一次实际焦点离开再恢复；采样程序不会控制其他窗口或注入 OS 输入。当前投影 PID 4204 的焦点时隙优先完成。

验收涵盖真实 CEF 相机卡片、原生前拍/自拍、三种持握、初始 F1 隐藏 HUD、七张独立 PNG、同帧取景 UI、相册查看、取消/回收/恢复、关闭返回同一 Screen/浏览器/页面/查看状态、取消快门、断线恢复及正常退出。仍需 task22 检查原始 PNG 与 UI 图片，确认 YSM/光影效果和照片无 UI；静态夹具不替代实机验收。

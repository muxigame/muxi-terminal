# 相册：131 原生截图验收（2026-10-02）

相册现在读取实际 `Minecraft.gameDirectory/screenshots` 的直接子文件。旧实现只读 `screenshots/muxi-terminal/<当前玩家 UUID>`，因此原生 F2 和相机已经保存的普通截图不会出现在相册。启动器隔离实例时，以当前运行实例的 gameDirectory 为准，不搜索其他实例。

当前安装的 `screenshot_viewer 1.3.4` 提供 ESC 截图管理。实机调用其 `getVanillaScreenshotsFolder()` 验证返回同一原生实例目录。相册直接复用此目录语义，不依赖可选模组的私有浏览器、配置路径或缓存；原生相机提交 `f599caf` 已通过 `Screenshot.grab` 写入这里。

旧私有照片库仅兼容当前玩家 UUID 子目录，标为只读，支持打开和缩略图。未迁移、复制或删除旧库照片。已定位原相机运行目录的 6 个 PNG，仅核数量，未把它们冒充本轮实机显示通过。

## 行为与安全范围

- PNG、JPEG/JPG、BMP、GIF 第一帧；支持原生 F2、改名与中文文件名。按文件修改时间从新到旧排序，最多显示最新 200 张；自动刷新约 3 秒，选中大图或确认删除时不打断操作。
- 只接受目录内合法 basename，校验格式签名、64 MiB 文件上限和 16,777,216 像素解码上限；不跟随符号链接或目录 junction，不遍历世界、其他实例或其他玩家照片库。
- 缩略图 240×160，大图最长边受 1280 限制。ImageIO 内存解码/缩放，reader、输入流、Graphics2D 和 BufferedImage 均及时释放；相册不创建 NativeImage、纹理缓存、临时磁盘图或 blob URL。
- 用户二次确认后，照片同目录移动到 `screenshots/.recycle`，恢复回原名；不覆盖同名文件。旧库照片只读，不能进入此修改路径。
- 只读浏览允许窗口暂时失焦。准备删除、删除、恢复保留焦点约束；所有操作仍限于自己的可信相册 main frame、当前实例/玩家/连接/Screen 和导航代次。

## CEF 回调与退出修复

原 `onQuery` 中的 CefFrame 包装在原生回调返回后已失效；旧异步鉴权继续使用它并静默丢掉完成回调，是查询超时和 `CefQueryCallback_N::finalize` 的直接原因之一。已保留 frame 身份与文档 URL，在主线程重新取得独立 frame，用原子一次结算保护 success/failure，导航或退出时明确返回 409。独立 frame 在结算后显式 dispose。

此外，原生 `onLoadEnd` 的 State URL 没有 JS hash，不能用它与 `#/album` 比较；改为核实际可信 browser/frame 的完整路由。原生 Screen 退出会通知仍保留的 CEF 文档，停止刷新与缩略图队列，清除大图/缩略图 src；重新打开恢复加载。

## 真实验收与证据

最终候选 SHA-256：`62819ebf01b50c1332d889ae355f71c12b5106df58c1c517a1caa43a67303a01`。私有 QA 构建包含当时共享 dev 的其他 owner dirty 文件；相册提交仅包含本 owner 文件。没有生产部署、008 重启、凭据新增或照片上传。

运行目录：`C:\Users\ranzh\Documents\Codex\2026-10-02\task-10\album-runtime-20261002-083634-93208d32`。最终产品通过 44 项真实运行检查、24 次开关；正常断开私有世界并退出，退出码 0，没有脚本强杀。完整 JSON 摘要见 [131-native-evidence-20261002.json](131-native-evidence-20261002.json)。

实际操作包含：打开前的原生 F2；相册保持打开时再次走真实 KeyboardHandler F2 press/release 并等待自动刷新；真实 CEF 缩略图与大图；删除确认/取消、回收、恢复；打开原生相机、真实拍摄、返回同一相册；另一独立实例排除上一实例的 3 张原生照片。没有运行时 query mock 或合成图片替代。

已人工检查 `album-native-large.png` 和 `album-native-camera-shared.png`：大图与操作栏在终端第一屏可见，3 张实际照片的缩略图和日期完整显示。图片只保存在上述私有测试目录，不进入仓库。

退出采样 25 份：每次图片 src 清零，原生截图 NativeImage 存活数和相机 IO worker 均为 0，finalize 异常 0。Java 堆 312,548,384 → 312,662,256 字节（约 +0.11 MiB），直接缓冲区 10,617,487 → 10,340,741 字节。CEF 子进程私有内存约 211 → 228 MiB；这一增长已留逐次证据，不能据此宣称长期内存零增长。此验收限于安装的最小 MC/NeoForge + MCEF + screenshot_viewer，不等同于完整整合包/YSM/光影验收。

文件/真实解码测试 58 项，桥权限与异步取消 fixture 65 项，controller 测试通过；另有真实 NTFS junction 拒绝检查 2 项。它们只作为补充，不替代实机。中间失败的私有运行均保留，未改为通过；最终证据只采用正常完成的运行。

## 重跑

源代码与运行器见 [tests/album-runtime](../../tests/album-runtime/README.md)。QA 的 OSHI WMI 超时 2000ms 仅在 `SystemReport.putHardware` 前由 QA mixin 设置，未进入生产代码。使用已安装运行库、本地 MCEF 二进制和独立新世界；不复制用户 worlds、screenshots 或浏览缓存。

补充正常完成运行：`C:\Users\ranzh\Documents\Codex\2026-10-02\task-10\album-runtime-20261002-084132-86950f41`，通过 28 项/8 次开关，回收前后在内存实际比较 SHA-256 字节一致；退出码 0。新增 JS 堆/子进程类型采样只记录统计，不记录照片内容或完整进程参数。

# task22 相册增量 → task6

这次继续原 task22 相机任务；没有新建委托任务。独立 Git worktree：`../album-worktree`，分支 `task22-album`，使用本任务的私有 bare clone。原 `delivery/` 相机包及 `camera-worktree` 原样保留。

## 对照基线和文件边界

本增量基于 task6 **当前已集成候选**：`task-6/sso-integration/muxi-terminal`，对应 `current-integration-delivery/README.md` 描述的 0.2.2 审核源码，已经包含相机、设置、音乐、小游戏、最新 SSO 和输入。不是原 task22 独立 0.2.1 候选，也不是仓库 main 的 raw diff。`VERIFICATION.json` 记录每个修改文件的基线/结果 SHA-256，并检查 task6 是否在快照后继续改动。

使用以下增量，不重应用旧相机或旧 task6 全量补丁，不用 ZIP 中的共享文件覆盖 task6 新源码：

- `album-module-incremental.patch`：新建相册桥接/确认票据/公共图片缩放 helper、相册 JS/controller/CSS、专项测试；仅修改原 task22 的 PhotoStore、CameraBridge、camera-app。
- `album-task6-owner-wiring.patch`：交 task6 审接的四处小接线：TerminalBrowserSession、TerminalNativeBridge、app.js、index.html。扩展现有允许列表和脚本/CSS 引入，保留音乐/设置/小游戏路由与原生外层 SSO/输入/消息守卫。
- `album-combined-incremental.patch`：上述两份组合。独立模块补丁和接线补丁分别通过 clean-snapshot apply/whitespace 检查，应用后源码一致。
- `album-task22-incremental-review.zip`：所选源码、补丁、审核 jar、测试和视觉证据。源码用于审阅；共享文件仍由 task6 合并。

未修改 TerminalCamera 的渲染/持握限制、TerminalScreen、TerminalHeldScreen、TerminalHandRenderer、TerminalClient、SSO adapter、共用 CSS/键盘处理、设置/音乐/小游戏模块、资源版本或服务器实体。动态首页入口复用 task6 的已安装 APP 计数观察器，无需新增计数逻辑。相机仍有原内嵌相册，并新增打开独立相册入口；独立相册可返回相机。

## 照片库、缩略图和回收

相册和相机都调用同一 TerminalPhotoStore，目录仍是 `<gameDirectory>/screenshots/muxi-terminal/<profile UUID>/`。沿用同一现有目录列表，不新建第二套照片或索引，不复制原 PNG。拍照后台任务、相册读取和移动共用既有单任务 IO 限流器，列表请求在操作完成后读取当前文件；拍照后进入相册、回收后刷新及恢复后切回照片立即一致。

缩略图按可见区域逐个加载，最多 240×160；放大查看以最多 1280×1280 的内存预览支持适应及 1–4 倍缩放/滚动，磁盘原图分辨率不变。不保存缩略图副本。离开页面、切换列表或进入查看器会丢弃不再使用的图片 src/观察器/加载结果。继续保留每个列表最多 200 张、目录枚举最多 10000 个条目的既有界限。卡片显示 UTC 拍摄时间，完整文件 ID 保留在 tooltip/查看器中。

“移入回收区”先出现应用内明确确认框，默认聚焦“取消”。背景按钮被禁用，继续使用共有 Tab/Shift+Tab/方向键/Enter 导航；确认框内部 Escape 取消并返回操作按钮。没有新增全局键盘接管。原生确认票据有效 30 秒、一次性、绑定实际拥有的浏览器与 generation；取消、过期、旧票据、重放和换页不能执行回收。

确认后使用 **同一照片库内的文件移动**：原文件 → `.recycle/<原照片 ID>`。回收区可查看并恢复到原目录/原文件名；目标已存在时拒绝操作，绝不覆盖。没有永久删除、自动清空、OS 回收站、文件选择器、任意路径、云上传或社交接口。仅允许符合原 APP ID 格式的普通 PNG；越界 ID、库外文件、符号链接/junction 被拒绝。已开始的磁盘移动可能在退出后完成，结果仍可从库内回收区恢复；尚未开始的失效操作不执行，失效回调不会改旧页面。

独立相册命令只允许实际 owned BUILTIN `index.html#/album` 主 frame，当前终端 Screen/世界/玩家/连接身份与 generation 保持有效并聚焦。HOME、其他内置 APP、WEB/ACCOUNT、iframe、伪 URL/浏览器和已退出页面均不能使用相册文件操作。相册没有相机截图能力；原 CameraBridge 仍只授权相机页。独立相册可复用 task6 的 held Screen 输入；相机沿用 task6 的右键放大拍摄限制。

## 已通过的证据

- 完整当前集成候选离线 Java 编译，Minecraft 1.21.1 / NeoForge 21.1.250 / release 21，继续 task6 的 0.2.2 私有审核 metadata，未发布。
- 相册 controller 16 项；相册浏览器合成 UI 21 项，包括新增入口与动态计数、旧 APP 保留、懒加载、放大、确认/取消/键盘焦点、回收/恢复、相机新照片一致、窄屏和退出释放。
- 实际 PhotoStore/确认类对临时合成库 35 项：不复制、重载、两个方向不覆盖同名文件、库外标记文件不变、符号链接拒绝、票据归属/代次/过期/取消/重放。
- 生产 AlbumBridge/CameraBridge/PhotoImages 代码在模拟 CEF/MC/图像后端中 53 项：外部/iframe/其他页拒绝、相册无截图能力、图库路径限制、忙时拒绝、失焦/换页/Screen 退出时取消待执行移动、缩略尺寸及异常关闭原生图像。夹具不等于真实 MCEF/NativeImage/GPU 验收。
- 既有相机 controller 14、相机浏览器合成 UI 13、SSO adapter 84、native content transition 20 项仍通过。
- 视觉查看大/小布局；繁忙既有壁纸下状态和说明使用相同 MC 面板色保持可读。所有相册/照片像素均为明确标注的合成测试内容；壁纸为既有静态资源，不是本任务游戏截图。

所有文件移动测试仅作用于 `task-22/.../build/album-tests/` 临时生成的合成照片。未枚举/读取/移动真实用户照片、未运行真实客户端或操作线上进程。沙箱已知的 Windows JDK 路径权限问题通过自动审核的离线测试/编译执行解除，最终编译和夹具干净通过；未遭遇审批拒绝。

重跑（在 source root）：

```powershell
node tests/album/controller.test.cjs
python tests/album/run-library-tests.py
python tests/album/run-bridge-fixtures.py
python tests/album/prepare-browser-qa.py
node tests/album/run-browser-qa.mjs
node tests/camera/controller.test.cjs
python tests/camera/prepare-browser-qa.py
node tests/camera/run-browser-qa.mjs
python tests/run_navigation_adapter_tests.py
python tests/run_motion_tests.py
```

审核构建脚本复用 task6 当前的 task21 compiler-dependencies.jar，只读依赖；不在本包分发依赖或私有 YSM 文件。父级决定后续协调版本。

## 实机待验收与时隙

目前 131 GUI 通道缺失。父级需与 task14 协调唯一 GUI 时隙，再在已授权 `jbc-1@192.168.110.131` 验收。未请求新 GUI、未 SSH/启动客户端、未热替、重启或发布。不得用本审核 jar 覆盖 task6 新整合候选。

1. task6 合并接线后，真实 MCEF 首页入口/计数、held/fullscreen 输入、相册/相机互跳、共用壁纸和字号、表格/缩略图长列表键盘导航。
2. **仅用独立合成验收 profile/照片库**，验证真实 PNG 缩略/放大/颜色/尺寸、拍照后首次进入相册一致、确认默认取消、Escape/Tab/Enter、取消不移动、回收/恢复重启持久、同名恢复拒绝。不得用真实用户照片做回收测试。
3. 原生消息路由中外部 APP/ACCOUNT/iframe/其他内置页、旧 generation、关闭/失焦/连接切换与重复票据，确保无照片权限、无旧页面回调或失效移动。
4. 真实 NativeImage/STB 与工作线程在长列表、损坏/大 PNG、快速查看/返回/退出、读盘失败时资源稳定；无磁盘缩略副本、无重复原图、无上传。
5. 原相机的 YSM/光影/前拍自拍、HUD/终端隐藏、持握变体、F1/视角和 GL 状态恢复验收仍全部保留；本增量不声称这些未完成 GPU 检查通过。

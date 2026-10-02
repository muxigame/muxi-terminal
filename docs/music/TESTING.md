# 验证范围与待实机项

已通过：隔离 Java 21 目标编译、30 项原生模块 JVM 检查、14 项真实已安装类签名校验、23 项实际 headless Chromium 渲染/行为检查。MC 的 JOrbis Vorbis 和 PCM WAV 用真实 decoder 读取；网络音乐控制/网页双向回读使用 API fixture，不视为真实服务器/OpenAL 通过。

脚本：`tests/build_music_candidate.py`、`tests/run_music_native_tests.py`、`tests/run_music_ui.py`。JDK 在本沙箱内进行路径解析会 AccessDenied，编译/JVM 检查通过自动审核后以工作区外读取权限运行；输出仅在本 worktree，未修改运行中的 pack/服务器。浏览器采用后台 headless，未启动游戏或占用 task14 GUI。

结果：`build/native-tests/result.json`、`bindings-result.json`，`build/music-ui/result.json`。UI 截图同一 viewport、同一 task6 样式：existing-home / existing-guide 与 music-background / music-netmusic / music-local / music-unavailable（1280×720），另 music-small（640×360）。共用滑杆/面板样式直接取 task20 settings.css 的既有组件规则，不含其 wallpaper/icon 全局改动；未修改 owner 文件。已目视检查实际 PNG，保证内容位于固定 content 容器，MC 方形斜边按钮/滑杆一致，不用浏览器蓝色滑杆。

覆盖：原生来源 API 缺失显示、能力切源、曲终状态、快连点 100 次只提交一次、晚到 snapshot 不覆盖新操作、原生音量/静音回读、现有方向键焦点、列表焦点稳定、音频坏文件拒绝、导入失败回滚、个人持久化、原文件保留、path/iframe/custom-page/redirect 拒绝、UNC 提前拒绝、32 路并发重复 close 只关闭一次。

以下必须由父级安排 `jbc-1@192.168.110.131` 且先取得 task14 的 GUI 时隙；本任务没有 SSH 启动测试客户端，没有在 008 重测：

1. task6 完整整合候选加载四个 mixin，可信 music view 得到专用 API，缺 API / 错版 netmusic 时有提示。
2. 原便携列表/单 CD 播放后 APP 曲名与真实 OpenAL 状态一致；APP PLAY/STOP/NEXT/PREV 后原 GUI/服务器同步；不误控附近方块/其他玩家。无响应 10 秒提示可见。
3. 背景音乐只暂停该声音，战斗、脚步、环境/UI 音效持续；游戏/失焦的原生 pause/resume 后 APP 状态按实际 channel 回读，关闭 APP 不遗留冻结声音。
4. 本机 chooser 取消/成功/拒绝网络盘、损坏和不可解码 MP3/FLAC/AAC；可解码曲目连续播放，不上传、不发送数据包，不出现原路径到 renderer/API/log。
5. 本地切歌、曲末、原播放器接管、离开 APP、关闭终端、退出世界、重连/资源重载释放全部 stream/channel；一首正在导入时关闭 view 不提交过期导入；个人列表重开可读。
6. 设置 MASTER 与音乐 MUSIC/RECORDS 双向同步和静音，快连点/切源/移动背包 slot 时旧 target 拒绝且无重复流或意外叠播。
7. 实际 MCEF 中外部自定义页/iframe/URL 同形变体不能调用 music；权限随 generation 撤销。固定 shell、动画和稍大尺度最终截图与既有页对照。

生产冻结继续：不重启、不热换、不发布。当前结果是隔离候选和可审接入点，不是已上线完成。

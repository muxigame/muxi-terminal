# 音乐 APP 主动播放

音乐页显示当前资源包实际存在的音乐音频文件，以及本人背包内便携播放器已有歌单。每行可点选播放；当前来源支持时可上一首、下一首、停止或从头播放。仍使用终端既有像素组件和导航。

游戏曲目由 `GameMusicLibrary` 从当前资源管理器的 `sounds/**/music/**/*.ogg` 读取，包括原版 `sounds/music/`。曲名来自文件名；没有硬编码演示歌单、联网搜索、下载曲库或新增服务。资源包重载后曲目 ID 更新，过期选择失效。`GameMusicSound` 在 Minecraft 现有音频引擎中流式播放指定文件，避免音乐事件随机选择另一首。

便携曲目由现有 `NetMusicListAdapter` 读取。网页仅收到随机 ID、已清理曲名、来源和当前标记；原始 SongInfo、URL、背包物品信息仍在原生层。选择前重新核对背包与歌单；删除、替换、移动槽位或改动曲目会使旧 ID 失效。点选及手动上一首/下一首调用原播放器已有 `SELECT_INDEX`，避免其 `NEXT` 在单曲循环模式下重播当前曲目；不修改原歌单循环设置。原播放器没有暂停/恢复接口，页面继续禁用该能力。

`music.snapshot` 新增 `tracks`、`playlistReason` 与 `capabilities.select`。`music.select:<UUID>` 只接受当前原生列表中的 ID。共用桥接和导航无需修改。播放状态继续从实际 OpenAL 状态回读；后台音乐、附近唱片与其他人的播放器保留原有控制边界。

本地导入继续复制到本机当前玩家的个人目录，先真实解码验证再保存，原文件保留。OGG Vorbis / PCM WAV 可用，MP3 / FLAC / AAC 依现有解码器逐曲验证，不扩展为保证支持所有编码。没有上传或新增网络请求。切换来源会释放 APP 自己的流；正在播放的唱片/原播放器需要先停止，防止叠播。退出音乐页、终端关闭与离开世界均释放 APP 的游戏/本地播放；保留已有自然背景音乐系统。

## 开发验证

在既有当前分支源码目录运行，全量编译不临时改动 `mod.json`：

```powershell
python tests/build_music_candidate.py --workspace <已安装客户端工作区> --java-home <JDK21目录>
python tests/run_music_native_tests.py --workspace <已安装客户端工作区> --java-home <JDK21目录> --dependencies build/music-test-dependencies.jar
python tests/run_music_ui.py
python tests/music-runtime/run_music_runtime.py --workspace <已安装客户端工作区> --java-home <JDK21目录> --with-portable
```

浏览器测试使用本机 Chrome 与 `websocket-client`，输出 `build/music-ui/result.json` 与截图。解码/本地库测试、真实已安装播放器二进制核验分别输出 `build/native-tests/result.json` 与 `bindings-result.json`。

实机驱动仅在 131 的 Session 2 使用独立 `build/music-runtime-<UTC>-<nonce>` 数据目录。加载当前编译产物、MCEF；`--with-portable` 额外加载本机已有 NetMusic、播放列表与 Cloth Config，并在私有集成世界放入两首本机音频歌单。没有外部音乐服务或真实玩家数据。实际 MCEF 按钮经产品原生桥播放，OpenAL 状态用于断言。驱动保留失败证据，正常退出世界和 Minecraft；超时只请求正常退出并记录失败，不强杀。WMI 2000ms 限时与防抢焦点钩子只在 QA 模组中。

`build/music-runtime-latest.json` 指向最近测试目录。每次的 `inputs.json`、`music-runtime-result.json`、`run-summary.json`、`exit.json`、`boot.log` 和原始 PNG 分别记录输入 SHA、实际状态、检查结果与退出码。最终可接受的功能结果必须同时满足 `success=true` 和 `cleanExit=true`。失败启动或仅正常清理退出不算通过。

Windows 文件选择对话框尚无真实输入验证。实际文件复制、解码、保存、列表回显和本地播放已单独验证；`filePickerVerified=false` 明确保留该缺口。当前桌面工具返回 Unknown tool，且没有 node_repl；需恢复桌面输入并协调焦点时段后，由音乐开发会话补完，不转交发布代测。

测试覆盖音乐模块与原播放器依赖组合；不宣称完整发布整合包、真实远端歌单服务或所有音频编码均验收通过。未部署、未发布、未改变正式整合包和启动器版本。

# 音乐来源和真实能力

独立候选，不涉及已上线 pack1.4.26 的重启、热换、发布或个人文件上传。

已读取工程和 workspace 的约束：`muxi-terminal` 及相关仓库未找到项目 AGENTS / 专用音乐 skill；已核查上级约束及 task6 的 `CONTAINER_INTERFACE.md`。本任务仅新增音乐模块，未改 task6 的 shell、bridge、全局 CSS 或 task20 设置模块。

实际音乐 JAR 在整合包源、`_client_test/game/mods`、`runtime-fixed-v4/mods` 和 release 候选中一致：

| 模组 | SHA-256 |
| --- | --- |
| net_music_list 4.3 | caed1efa1b49f3489d2beee0a82369f8a1029df5cac9e9655c57fd2d9887c2db |
| netmusic 1.5.2-neoforge+mc1.21.1 | 731fba4b4d47256d5f44f8392b093b53c7d1c650b66111eda75b820f7f89a14b |

同时存在 biomemusic 1.21.1-4.1；其现有配置 `smartMusic=false`、`stopMusicForRecords=false`。不能假设播放唱片时背景音乐必然自动停止。

| 来源 | 曲名和状态 | 控制能力 |
| --- | --- | --- |
| MC / BiomeMusic 背景音乐 | 读取 MusicManager.currentMusic，使用实际选中音频资源的文件名；没有官方本地化元数据时不编造曲名。状态从声音线程回读 OpenAL。 | 仅针对当前音乐声音暂停/继续；MUSIC 原生分类音量；无上一首/下一首列表。 |
| 本人便携 net_music_list 播放器 | 当前 RingerSound 的 songName、实际声音状态、本人背包中的 ringer UUID 和原列表索引。支持单张 CD 与原播放列表。 | PLAY、STOP；列表有多曲时 NEXT，Previous 使用 SELECT_INDEX。没有 PAUSE/RESUME 数据包，不把 STOP/PLAY 称为暂停继续。 |
| 其他玩家、附近方块、广播或唱片 | 可识别的歌曲元数据或原声音资源名、当前状态。 | 只读播放状态；可改本机 RECORDS 分类音量。没有其他玩家或方块控制权限。 |
| 个人本地曲库 | 原生选文件，个人本机副本，opaque UUID、曲名、格式。 | 复用 MC 音频引擎播放、暂停继续、停止、上一首/下一首、MUSIC 音量；退出 APP 停止并释放流。 |

NetMusic 的现有 `getVolume()` 确实读取 RECORDS 分类，且其满音量有自己的倍率行为；本模块不修改该行为。设置 APP 的 `volume` 是 MASTER，本模块的分类音量与 MASTER 共同决定是否静音，不写第二套音量文件。

本地格式：OGG 仅 Vorbis，由 MC JOrbis 解码；WAV 仅可转换的 PCM 单/双声道；MP3、FLAC、AAC 使用已安装 NetMusic 的 file-only 解码入口并逐文件验证，不能保证所有编码变种均可读取。没有新登录、网络搜索、分发或 Windows 媒体控制。没有调用 FFmpeg 的联网/下载逻辑。

本地路径：`<gameDirectory>/config/muxi_terminal/music/<player UUID>/`，仅保存生成 ID 的副本和 title/extension manifest；不保存原路径，不向网页返回文件 URL、完整路径或音频数据。拒绝 UNC、网络映射盘、路径参数、超 256 MiB 单曲和超过 300 曲。导入失败删除未提交副本；取消/过期选择不导入。

本地开始前若存在 RECORDS 声音，明确要求先在原播放器停止；不会替用户停止他人广播。开始本地播放会停止当前背景音乐，期间拒绝新的自动背景音乐。原播放器重新开始 RECORDS 播放时，本地流退出，把控制权交回原来源。

来源替换、曲目结束、重载会更新操作 target；过期请求拒绝。原模组操作仍回读原播放器，10 秒未出现状态变化会显示未确认提示，不能仅凭数据包发出就声称成功播放。

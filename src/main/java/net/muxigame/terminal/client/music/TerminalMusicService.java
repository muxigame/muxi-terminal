package net.muxigame.terminal.client.music;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Main-thread state, existing sound engine, file-only background IO. No HTTP or OS media controls. */
@EventBusSubscriber(modid = "muxi_terminal", value = Dist.CLIENT)
public final class TerminalMusicService {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Muxi local music IO"); t.setDaemon(true); return t;
    });
    private static LocalMusicLibrary library;
    private static UUID profile;
    private static boolean busy;
    private static String message = "";
    private static GameMusicSound game;
    private static String gameId, gameTitle = "游戏音乐";
    private static volatile long session;
    private record QueueEntry(GameMusicLibrary.Track game, String local, PortableChoice portable) {}
    private static List<QueueEntry> queue = List.of();
    private static int queueIndex = -1;
    private static String queueGroup = "";
    private static NetMusicListAdapter.Portable ownedPortable;
    private static SoundInstance portableSound;
    private static int portableWait;
    private static int gameTicks;
    private static long soundResourceGeneration;
    private static GameMusicLibrary.Track gameTrack;
    private record PortableChoice(NetMusicListAdapter.Portable player, int index, String title) {}
    private static Map<String, PortableChoice> portableChoices = Map.of();
    private static Map<String, String> portableIds = Map.of();
    private static String playlistReason = "";
    private static LocalMusicSound local;
    private static String localId;
    private static int localTicks;
    private static SoundInstance paused;
    private static Object lastSource;
    private static String lastTrack = "", target = UUID.randomUUID().toString();
    private static long lastControl;
    private static String awaitingTarget;
    private static long awaitingSince;
    private record PlaybackReading(SoundInstance sound, int state) {}
    private static volatile PlaybackReading reading;
    private record Source(String kind, String title, SoundSource volume, SoundInstance sound,
                          NetMusicListAdapter.Portable portable, String reason) {}
    private TerminalMusicService() {}
    static ExecutorService io() { return IO; }

    private static Map<SoundInstance, ChannelAccess.ChannelHandle> channels() {
        var sound = Minecraft.getInstance().getSoundManager();
        if (!(sound instanceof MusicAccess.Sounds access)
            || !(access.muxiMusic$engine() instanceof MusicAccess.Engine engine)) return Map.of();
        return engine.muxiMusic$channels();
    }
    public static boolean owns(SoundInstance sound) { return sound != null && (sound == game || sound == local); }
    public static boolean hasOwnedPlayback() { return game != null || local != null || ownedPortable != null; }
    public static ChannelAccess.ChannelHandle channel(SoundInstance sound) { return channels().get(sound); }
    private static boolean carriesTerminal() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return false;
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (inventory.getItem(i).is(net.muxigame.terminal.MuxiTerminal.PLAYER_TERMINAL.get())) return true;
        return false;
    }
    private static boolean hooked() {
        return Minecraft.getInstance().getSoundManager() instanceof MusicAccess.Sounds
            && Minecraft.getInstance().getMusicManager() instanceof MusicAccess.Manager;
    }
    private static Source source() {
        Minecraft mc = Minecraft.getInstance();
        if (local != null) return new Source("local", library.tracks().stream().filter(t -> t.id().equals(localId))
            .map(LocalMusicLibrary.Track::title).findFirst().orElse("本地音乐"), SoundSource.MUSIC, local, null, "");
        if (game != null) return new Source("game", gameTitle, SoundSource.MUSIC, game, null, "携带终端时持续播放；移出个人背包或离开服务器时停止。");
        if (!hooked()) return new Source("unavailable", "音乐桥接未接入", SoundSource.MUSIC, null, null, "请先接入音乐客户端适配，再重新打开游戏。");
        List<NetMusicListAdapter.Portable> players = List.of();
        String error = "";
        try { players = NetMusicListAdapter.portable(mc); }
        catch (ClassNotFoundException ignored) { error = "未安装网络音乐播放列表；原生背景音乐仍可读取。"; }
        catch (ReflectiveOperationException | LinkageError failure) { error = "播放列表 API 与当前版本不匹配，请使用原播放器。"; }
        for (SoundInstance sound : List.copyOf(channels().keySet())) {
            if (!mc.getSoundManager().isActive(sound) || channels().get(sound).isStopped()) continue;
            if (!NetMusicListAdapter.ringer(sound)) continue;
            try {
                if (!NetMusicListAdapter.self(sound)) continue;
                UUID id = NetMusicListAdapter.ringerId(sound);
                var owned = players.stream().filter(p -> id.equals(p.id())).findFirst().orElse(null);
                return new Source("netmusic", NetMusicListAdapter.title(sound), SoundSource.RECORDS, sound, owned,
                    owned == null ? "当前音乐源不在本人便携播放器中，只显示状态和原生唱片音量。" : "该版本没有暂停恢复接口；停止后可从头播放。");
            } catch (ReflectiveOperationException failure) { error = "播放列表状态接口不可用，请使用原播放器。"; }
        }
        SoundInstance current = ((MusicAccess.Manager)mc.getMusicManager()).muxiMusic$current();
        if (current != null && mc.getSoundManager().isActive(current))
            return new Source("background", backgroundTitle(current), SoundSource.MUSIC, current, null,
                "曲名取自当前音频资源；背景音乐没有可用的上一首 / 下一首播放列表。");
        for (SoundInstance sound : List.copyOf(channels().keySet())) {
            if (!mc.getSoundManager().isActive(sound) || channels().get(sound).isStopped()) continue;
            if (sound.getSource() != SoundSource.RECORDS) continue;
            String title = sound.getLocation().toString();
            try { if (NetMusicListAdapter.ringer(sound)) title = NetMusicListAdapter.title(sound); }
            catch (ReflectiveOperationException ignored) {}
            return new Source("other", title, SoundSource.RECORDS, sound, null,
                "附近唱片、方块或其他玩家的播放器只显示状态，需在原播放器控制。");
        }
        if (players.size() == 1 && !players.getFirst().songs().isEmpty()) {
            var player = players.getFirst();
            String title = "便携播放器已停止";
            if (player.index() >= 0 && player.index() < player.songs().size()) {
                Object info = player.songs().get(player.index());
                try { title = MusicSecurity.title(info.getClass().getField("songName").get(info)); }
                catch (ReflectiveOperationException ignored) {}
            }
            return new Source("netmusic", title, SoundSource.RECORDS, null, player, "暂停恢复不可用；播放会从头开始。");
        }
        return new Source("idle", "当前没有音乐", SoundSource.MUSIC, null, null,
            players.size() > 1 ? "有多个便携播放器，请先在原播放器开始播放以确定控制对象。" : error);
    }
    private static void readPlaylists() {
        Map<String, PortableChoice> choices = new LinkedHashMap<>();
        Map<String, String> ids = new HashMap<>();
        playlistReason = "";
        try {
            for (var player : NetMusicListAdapter.portable(Minecraft.getInstance())) {
                for (int index = 0; index < player.songs().size(); index++) {
                    Object info = player.songs().get(index);
                    // Full authoritative metadata stays native; only random IDs and sanitized titles enter CEF.
                    String key = player.slot() + ":" + player.id() + ":" + index + ":" + new Gson().toJson(info);
                    String id = portableIds.getOrDefault(key, UUID.randomUUID().toString());
                    ids.put(key, id);
                    choices.put(id, new PortableChoice(player, index, NetMusicListAdapter.songTitle(info)));
                }
            }
        } catch (ClassNotFoundException ignored) {
            playlistReason = "未安装便携播放器；可直接播放游戏曲目或导入本机音乐。";
        } catch (ReflectiveOperationException | LinkageError failure) {
            choices.clear(); ids.clear(); playlistReason = "原播放器曲目列表暂不可用，请检查安装版本。";
        }
        portableChoices = choices; portableIds = ids;
    }
    private static Source observe() {
        Source source = source();
        Object key = source.sound() != null ? source.sound() : source.portable() != null ? source.portable().id() : source.kind();
        String track = source.title() + ":" + (source.portable() == null ? "" : source.portable().index());
        if (!Objects.equals(lastSource, key) || !lastTrack.equals(track)) {
            // A stopped, finished, replaced or reloaded sound invalidates all old controls.
            if (paused != source.sound() && paused != null) {
                var handle = channels().get(paused);
                if (handle != null) handle.execute(com.mojang.blaze3d.audio.Channel::unpause);
                paused = null;
            }
            lastSource = key; lastTrack = track; target = UUID.randomUUID().toString();
        }
        if (awaitingTarget != null) {
            if (!target.equals(awaitingTarget)) { awaitingTarget = null; message = "原播放器状态已更新。"; }
            else if (System.nanoTime() - awaitingSince > 10_000_000_000L) {
                awaitingTarget = null; message = "原播放器尚未确认操作，请检查原播放器、曲目可用性或连接。";
            }
        }
        return source;
    }
    private static String backgroundTitle(SoundInstance sound) {
        String path = sound.getSound() == null ? sound.getLocation().getPath() : sound.getSound().getLocation().getPath();
        return MusicSecurity.title(path.substring(path.lastIndexOf('/') + 1).replace('_', ' '));
    }
    private static void loadLibrary() {
        Minecraft mc = Minecraft.getInstance();
        UUID current = mc.player == null ? null : mc.player.getUUID();
        if (Objects.equals(profile, current)) return;
        stopSession(); session++; profile = current; library = null; busy = false;
        if (current == null) return;
        busy = true;
        long token = session;
        IO.execute(() -> {
            LocalMusicLibrary loaded = null;
            try { loaded = new LocalMusicLibrary(mc.gameDirectory.toPath().resolve("config/muxi_terminal/music").resolve(current.toString())); }
            catch (Exception failure) {}
            LocalMusicLibrary ready = loaded;
            mc.execute(() -> {
                if (token != session || !current.equals(profile)) return;
                library = ready; busy = false;
                if (ready == null) message = "本地音乐库无法读取，请检查本机目录权限；已有文件未被覆盖。";
            });
        });
    }
    public static String command(String request, BooleanSupplier valid) throws Exception {
        loadLibrary();
        if (request.equals("music.snapshot")) return snapshot().toString();
        if (request.equals("music.exit")) { closeLocal(); return snapshot().toString(); }
        if (request.equals("music.refresh")) { GameMusicLibrary.invalidate(); return snapshot().toString(); }
        if (request.startsWith("music.playlist:")) {
            String group = request.substring(15);
            if (!carriesTerminal() || busy || !group.equals("local") && GameMusicLibrary.loading()) return reply(false, "请等待曲库完成，并将终端留在个人背包。");
            List<QueueEntry> entries = new ArrayList<>();
            if (group.equals("local") && library != null) {
                for (var track : library.tracks()) entries.add(new QueueEntry(null, track.id(), null));
            } else {
                for (var track : GameMusicLibrary.tracks()) if (group.equals(track.group())) entries.add(new QueueEntry(track, null, null));
                readPlaylists();
                for (var choice : portableChoices.values()) if (group.equals(choice.player().id().toString())) entries.add(new QueueEntry(null, null, choice));
            }
            if (entries.isEmpty()) return reply(false, "当前列表没有可播放曲目。");
            stopSession(); queue = List.copyOf(entries); queueGroup = group; queueIndex = 0;
            try {
                String result = playQueue();
                if (!JsonParser.parseString(result).getAsJsonObject().get("ok").getAsBoolean()) stopSession();
                return result;
            } catch (Exception failure) { stopSession(); return reply(false, "当前列表首曲不可用，请重新选择或刷新。"); }
        }
        if (request.equals("music.import")) {
            if (busy || library == null) return reply(false, "音乐库尚未就绪或正在操作，请稍候。");
            busy = true; message = "请选择本机音乐；支持 OGG Vorbis、PCM WAV，MP3 / FLAC / AAC 需本机解码验证。";
            LocalMusicLibrary captured = library; UUID user = profile; long token = session;
            IO.execute(() -> {
                String status;
                try {
                    Class<?> tiny = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
                    String selected = (String)tiny.getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class,
                        Class.forName("org.lwjgl.PointerBuffer"), CharSequence.class, boolean.class)
                        .invoke(null, "选择本地音乐（OGG / PCM WAV；MP3 FLAC AAC 逐曲验证）", null, null, "Audio", false);
                    if (token != session || !valid.getAsBoolean()) status = "已取消过期导入。";
                    else if (selected == null) status = "已取消，音乐库未改变。";
                    else { captured.add(Path.of(selected), file -> { LocalDecoder.probe(file); if (token != session || !valid.getAsBoolean()) throw new IllegalStateException("Expired"); }); status = "已导入个人本地音乐库。"; }
                } catch (Exception | LinkageError failure) {
                    status = "导入失败：请选本机 OGG Vorbis / PCM WAV；MP3、FLAC、AAC 需可用 NetMusic 解码器。上限 256 MiB / 曲、300 曲。";
                }
                String result = status;
                Minecraft.getInstance().execute(() -> { if (token == session && Objects.equals(user, profile) && captured == library) { busy = false; message = result; } });
            });
            return reply(true, message);
        }
        if (request.startsWith("music.remove:")) {
            String id = request.substring(13);
            if (!MusicSecurity.id(id) || library == null || busy) return reply(false, "本地曲目不可用或正在操作。");
            if (id.equals(localId)) stopSession();
            busy = true; LocalMusicLibrary captured = library; UUID user = profile; long token = session;
            IO.execute(() -> {
                String status;
                try { if (token != session || !valid.getAsBoolean()) throw new IllegalStateException(); captured.remove(id); status = "已从本地库移除；原文件保留。"; }
                catch (Exception failure) { status = "移除失败，请检查本机权限。"; }
                String result = status;
                Minecraft.getInstance().execute(() -> { if (token == session && Objects.equals(user, profile) && captured == library) { busy = false; message = result; } });
            });
            return reply(true, "正在移除本地副本…");
        }
        if (request.startsWith("music.select:")) {
            String id = request.substring(13);
            if (!MusicSecurity.id(id) || !hooked() || busy || awaitingTarget != null) return reply(false, "曲目尚未就绪或正在操作。");
            var track = GameMusicLibrary.find(id);
            if (track != null) { queue = List.of(); queueIndex = -1; queueGroup = ""; return startGame(track); }
            // Re-read inventory before selecting: removed/replaced/reordered items invalidate the old ID.
            readPlaylists();
            PortableChoice choice = portableChoices.get(id);
            if (choice == null) return reply(false, "曲目列表已变化，请重新选择。");
            long now = System.nanoTime();
            if (now - lastControl < 180_000_000L) return reply(false, "操作过快，请稍候。");
            Source current = observe();
            if (current.kind().equals("other") || current.kind().equals("netmusic") && current.portable() == null)
                return reply(false, "当前音乐源需先在原播放器停止，避免叠播。");
            if (current.portable() != null && !Objects.equals(current.portable().id(), choice.player().id()))
                NetMusicListAdapter.command(current.portable(), "STOP", 0);
            stopSession(); Minecraft.getInstance().getMusicManager().stopPlaying();
            observe(); // Confirm from the stopped source after releasing APP-owned audio.
            NetMusicListAdapter.command(choice.player(), "SELECT_INDEX", choice.index());
            ownedPortable = choice.player(); portableSound = null; portableWait = 0;
            awaitingTarget = target; awaitingSince = now; lastControl = now;
            message = "已选择曲目；等待原播放器确认实际播放。";
            return reply(true, message);
        }
        if (request.startsWith("music.local:")) {
            String id = request.substring(12);
            if (!MusicSecurity.id(id) || library == null || busy || awaitingTarget != null || !hooked()) return reply(false, "本地曲目或音乐桥接尚未就绪。");
            queue = List.of(); queueIndex = -1; queueGroup = ""; return startLocal(id);
        }
        if (!request.startsWith("music.control:")) return reply(false, "未知音乐操作。");
        JsonObject input = JsonParser.parseString(request.substring(14)).getAsJsonObject();
        if (!input.keySet().stream().allMatch(Set.of("action", "target", "value")::contains)) return reply(false, "无效音乐参数。");
        Source source = observe();
        if (!input.has("target") || !target.equals(input.get("target").getAsString())) return reply(false, "音乐源已变化，请重试。");
        String action = input.get("action").getAsString();
        long now = System.nanoTime();
        if (now - lastControl < 180_000_000L) return reply(false, "操作过快，请稍候。");
        if (action.equals("stop") && hasOwnedPlayback()) {
            stopSession(); return reply(true, "已停止播放并清空本次队列。");
        }
        if ((action.equals("next") || action.equals("previous")) && !queue.isEmpty()) {
            queueIndex = Math.floorMod(queueIndex + (action.equals("next") ? 1 : -1), queue.size());
            return playQueue();
        }
        if (action.equals("volume")) {
            double value = input.get("value").getAsDouble();
            if (!Double.isFinite(value) || value < 0 || value > 1) return reply(false, "音量应在 0 到 100% 之间。");
            var mc = Minecraft.getInstance(); mc.options.getSoundSourceOptionInstance(source.volume()).set(value); mc.options.save();
        } else if (action.equals("play") && source.portable() == null && !source.kind().equals("other")) {
            if (source.kind().equals("local")) return startLocal(localId);
            var tracks = GameMusicLibrary.tracks();
            if (tracks.isEmpty() || !hooked()) return reply(false, "当前资源包没有可播放的游戏曲目。");
            var selected = GameMusicLibrary.find(gameId);
            return startGame(selected == null ? tracks.getFirst() : selected);
        } else if ((action.equals("pause") || action.equals("resume")) && (source.kind().equals("background") || source.kind().equals("local") || source.kind().equals("game"))) {
            var handle = channels().get(source.sound());
            if (handle == null || handle.isStopped()) return reply(false, "曲目尚未就绪或已结束。");
            if (action.equals("pause")) { handle.execute(com.mojang.blaze3d.audio.Channel::pause); paused = source.sound(); }
            else { handle.execute(com.mojang.blaze3d.audio.Channel::unpause); paused = null; }
        } else if (source.kind().equals("game")) {
            if (action.equals("stop")) stopSession();
            else if (action.equals("next") || action.equals("previous")) {
                var tracks = GameMusicLibrary.tracks();
                int at = -1; for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).id().equals(gameId)) at = i;
                if (at < 0 || tracks.size() < 2) return reply(false, "没有其他游戏曲目。");
                return startGame(tracks.get(Math.floorMod(at + (action.equals("next") ? 1 : -1), tracks.size())));
            } else return reply(false, "该音乐源不支持此操作。");
        } else if (source.kind().equals("local")) {
            if (action.equals("stop")) stopSession();
            else if (action.equals("next") || action.equals("previous")) {
                List<LocalMusicLibrary.Track> tracks = library.tracks();
                int at = -1; for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).id().equals(localId)) at = i;
                if (at < 0 || tracks.size() < 2) return reply(false, "没有其他本地曲目。");
                return startLocal(tracks.get(Math.floorMod(at + (action.equals("next") ? 1 : -1), tracks.size())).id());
            } else return reply(false, "该音乐源不支持此操作。");
        } else if (source.portable() != null) {
            if (!Set.of("play", "stop", "next", "previous").contains(action)) return reply(false, "原播放器没有暂停恢复接口。");
            if (source.portable().songs().isEmpty()) return reply(false, "原播放列表为空。");
            if (action.equals("previous") && source.portable().index() < 0) return reply(false, "当前曲目位置未知。");
            // Native NEXT follows the item's loop mode and may replay the same song. A manual skip selects an index.
            int selectedIndex = action.equals("next") || action.equals("previous")
                ? Math.floorMod(source.portable().index() + (action.equals("next") ? 1 : -1), source.portable().songs().size()) : 0;
            NetMusicListAdapter.command(source.portable(), switch (action) { case "play" -> "PLAY"; case "stop" -> "STOP"; default -> "SELECT_INDEX"; }, selectedIndex);
            if (!action.equals("stop")) {
                ownedPortable = source.portable(); portableSound = null; portableWait = 0;
            }
            awaitingTarget = target; awaitingSince = now;
            message = "已交给原播放器；状态以实际播放回读为准。";
        } else return reply(false, "该音乐源不支持此操作，请使用原播放器。");
        lastControl = now;
        return reply(true, action.equals("volume") ? "已同步 Minecraft 原生音量。" : source.portable() != null ? message : "操作已提交。");
    }
    private static String startGame(GameMusicLibrary.Track track) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (now - lastControl < 180_000_000L) return reply(false, "操作过快，请稍候。");
        if (!carriesTerminal()) return reply(false, "请将终端留在个人背包内。");
        if (!hasOwnedPlayback() && channels().entrySet().stream().anyMatch(e -> e.getKey().getSource() == SoundSource.RECORDS && !e.getValue().isStopped()))
            return reply(false, "当前有唱片或原播放器，请先停止，避免叠播。");
        stopOwned(); MusicContinuity.reset();
        soundResourceGeneration = GameMusicLibrary.resourceGeneration(); gameTrack = track;
        gameId = track.id(); gameTitle = track.title(); gameTicks = 0; game = new GameMusicSound(track.audio());
        mc.getMusicManager().stopPlaying(); mc.getSoundManager().play(game); lastControl = now;
        message = "播放游戏曲目；携带终端时持续播放。";
        return reply(true, message);
    }
    private static String startLocal(String id) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (now - lastControl < 180_000_000L) return reply(false, "操作过快，请稍候。");
        if (!hasOwnedPlayback() && channels().entrySet().stream().anyMatch(e -> e.getKey().getSource() == SoundSource.RECORDS && !e.getValue().isStopped()))
            return reply(false, "当前有唱片或网络播放器，请先在原播放器停止，避免叠播。");
        if (!carriesTerminal()) return reply(false, "请将终端留在个人背包内。");
        Path file = library.file(id);
        stopOwned(); MusicContinuity.reset();
        soundResourceGeneration = GameMusicLibrary.resourceGeneration();
        localId = id; localTicks = 0; local = new LocalMusicSound(file);
        mc.getMusicManager().stopPlaying(); mc.getSoundManager().play(local);
        lastControl = now;
        message = "播放个人本地曲目；携带终端时持续播放。";
        return reply(true, message);
    }
    /** Existing UI callers detach only. Playback is player/inventory-owned, not browser-owned. */
    public static void closeLocal() {}
    private static void stopSession() {
        stopOwned(); queue = List.of(); queueIndex = -1; queueGroup = "";
        if (ownedPortable != null) {
            try {
                var current = NetMusicListAdapter.portable(Minecraft.getInstance()).stream()
                    .filter(p -> p.id().equals(ownedPortable.id())).findFirst().orElse(null);
                if (current != null) NetMusicListAdapter.command(current, "STOP", 0);
            }
            catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        ownedPortable = null; portableSound = null; portableWait = 0;
    }
    private static String playQueue() throws Exception {
        var entry = queue.get(queueIndex); lastControl = 0;
        if (entry.game() != null) return startGame(entry.game());
        if (entry.local() != null) return startLocal(entry.local());
        stopOwned(); Minecraft.getInstance().getMusicManager().stopPlaying();
        NetMusicListAdapter.command(entry.portable().player(), "SELECT_INDEX", entry.portable().index());
        ownedPortable = entry.portable().player(); portableSound = null; portableWait = 0;
        return reply(true, "整单播放；等待原生播放器确认。");
    }
    private static void ended(String status) {
        stopOwned();
        while (!queue.isEmpty() && ++queueIndex < queue.size()) {
            try { JsonObject result = JsonParser.parseString(playQueue()).getAsJsonObject();
                if (result.get("ok").getAsBoolean()) return;
            } catch (Exception ignored) {}
        }
        stopSession(); message = status;
    }
    private static void stopOwned() {
        if (ownedPortable != null) {
            try {
                var current = NetMusicListAdapter.portable(Minecraft.getInstance()).stream()
                    .filter(p -> p.id().equals(ownedPortable.id())).findFirst().orElse(null);
                if (current != null) NetMusicListAdapter.command(current, "STOP", 0);
            }
            catch (ReflectiveOperationException | LinkageError ignored) {}
            if (portableSound != null) Minecraft.getInstance().getSoundManager().stop(portableSound);
            ownedPortable = null; portableSound = null; portableWait = 0;
        }
        if (game != null) { Minecraft.getInstance().getSoundManager().stop(game); game.dispose(); game = null; }
        gameId = null; gameTrack = null; gameTicks = 0;
        if (local != null) { Minecraft.getInstance().getSoundManager().stop(local); local.dispose(); local = null; }
        localId = null; localTicks = 0;
        // Release the session-owned pause when playback stops.
        if (paused != null) { var handle = channels().get(paused); if (handle != null) handle.execute(com.mojang.blaze3d.audio.Channel::unpause); paused = null; }
    }
    private static String reply(boolean ok, String status) {
        JsonObject out = snapshot(); out.addProperty("ok", ok); out.addProperty("message", status); return out.toString();
    }
    private static JsonObject snapshot() {
        Source source = observe(); Minecraft mc = Minecraft.getInstance(); JsonObject out = new JsonObject();
        var handle = channels().get(source.sound());
        if (handle != null) handle.execute(channel -> {
            if (channel instanceof MusicAccess.ChannelState actual) reading = new PlaybackReading(source.sound(), actual.muxiMusic$state());
        });
        PlaybackReading measured = reading;
        // OpenAL source states: INITIAL=0x1011, PLAYING=0x1012, PAUSED=0x1013, STOPPED=0x1014.
        String playback = source.sound() == null ? "idle" : measured != null && measured.sound() == source.sound()
            ? switch (measured.state()) { case 0x1012 -> "playing"; case 0x1013 -> "paused"; case 0x1014 -> "idle"; default -> "loading"; }
            : "loading";
        out.addProperty("kind", source.kind()); out.addProperty("title", source.title()); out.addProperty("target", target);
        out.addProperty("status", playback);
        out.addProperty("reason", source.reason()); out.addProperty("message", message); out.addProperty("busy", busy);
        out.addProperty("volumeSource", source.volume() == SoundSource.RECORDS ? "records" : "music");
        out.addProperty("volume", mc.options.getSoundSourceOptionInstance(source.volume()).get());
        out.addProperty("master", mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        out.addProperty("muted", mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0 || mc.options.getSoundSourceVolume(source.volume()) == 0);
        JsonObject cap = new JsonObject(); boolean ownLocal = source.kind().equals("local"); boolean ownPortable = source.portable() != null;
        boolean ownGame = source.kind().equals("game");
        var gameTracks = hooked() ? GameMusicLibrary.tracks() : List.<GameMusicLibrary.Track>of();
        readPlaylists();
        cap.addProperty("pause", source.sound() != null && (ownLocal || ownGame || source.kind().equals("background")));
        cap.addProperty("play", ownPortable ? source.sound() == null : hooked() && (ownLocal || !gameTracks.isEmpty()) && !source.kind().equals("other"));
        cap.addProperty("stop", ownLocal || ownGame || ownPortable);
        cap.addProperty("next", ownGame ? gameTracks.size() > 1 : ownLocal ? library.tracks().size() > 1 : ownPortable && source.portable().songs().size() > 1);
        cap.addProperty("previous", ownGame ? gameTracks.size() > 1 : ownLocal ? library.tracks().size() > 1 : ownPortable && source.portable().songs().size() > 1 && source.portable().index() >= 0);
        if (queue.size() > 1 && hasOwnedPlayback()) { cap.addProperty("next", true); cap.addProperty("previous", true); }
        cap.addProperty("import", library != null && !busy); cap.addProperty("local", library != null && !busy && awaitingTarget == null && hooked());
        cap.addProperty("select", hooked() && !busy && awaitingTarget == null);
        if (awaitingTarget != null) for (String action : List.of("play", "stop", "next", "previous")) cap.addProperty(action, false);
        out.add("capabilities", cap);
        JsonArray available = new JsonArray();
        for (var track : gameTracks) {
            JsonObject row = new JsonObject(); row.addProperty("id", track.id()); row.addProperty("title", track.title());
            row.addProperty("source", track.provider()); row.addProperty("group", track.group());
            row.addProperty("active", game != null && track.audio().equals(game.getLocation())); available.add(row);
        }
        for (var entry : portableChoices.entrySet()) {
            var choice = entry.getValue();
            JsonObject row = new JsonObject(); row.addProperty("id", entry.getKey()); row.addProperty("title", choice.title());
            row.addProperty("source", "本人便携播放器 · 槽位 " + (choice.player().slot() + 1) + " · 第 " + (choice.index() + 1) + " 首");
            row.addProperty("group", choice.player().id().toString());
            row.addProperty("active", source.sound() != null && source.portable() != null
                && Objects.equals(source.portable().id(), choice.player().id()) && source.portable().index() == choice.index()); available.add(row);
        }
        out.add("tracks", available); out.addProperty("playlistReason", GameMusicLibrary.error().isEmpty() ? playlistReason : GameMusicLibrary.error());
        out.addProperty("catalogLoading", GameMusicLibrary.loading()); out.addProperty("catalogScans", GameMusicLibrary.scans());
        out.addProperty("catalogGeneration", GameMusicLibrary.generation());
        JsonArray groups = new JsonArray(); Map<String, String> names = new LinkedHashMap<>(); Map<String, Integer> counts = new HashMap<>();
        for (var track : gameTracks) { names.put(track.group(), track.provider()); counts.merge(track.group(), 1, Integer::sum); }
        for (var choice : portableChoices.values()) {
            String id = choice.player().id().toString(); names.put(id, "个人便携播放器 · 槽位 " + (choice.player().slot() + 1)); counts.merge(id, 1, Integer::sum);
        }
        names.forEach((id, name) -> { JsonObject group = new JsonObject(); group.addProperty("id", id); group.addProperty("name", name); group.addProperty("count", counts.get(id)); groups.add(group); });
        out.add("groups", groups); out.addProperty("queueGroup", queueGroup); out.addProperty("queueIndex", queueIndex); out.addProperty("queueSize", queue.size());
        JsonArray rows = new JsonArray();
        if (library != null) for (var track : library.tracks()) {
            JsonObject row = new JsonObject(); row.addProperty("id", track.id()); row.addProperty("title", track.title());
            row.addProperty("format", track.extension().toUpperCase(Locale.ROOT)); row.addProperty("active", track.id().equals(localId)); rows.add(row);
        }
        out.add("localTracks", rows); return out;
    }
    @SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
        MusicContinuity.tick();
        if (local == null && game == null && paused == null && ownedPortable == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!carriesTerminal()) { stopSession(); return; }
        if (mc.getOverlay() != null) return;
        if (soundResourceGeneration != GameMusicLibrary.resourceGeneration()
            && (game != null && !mc.getSoundManager().isActive(game) || local != null && !mc.getSoundManager().isActive(local))) {
            try {
                lastControl = 0;
                if (game != null) startGame(gameTrack); else startLocal(localId);
            } catch (Exception failure) { stopSession(); message = "资源重载后当前曲目不可用，已释放播放资源。"; }
            return;
        }
        if (game != null) {
            gameTicks++;
            // A stopped native record can remain in BiomeMusic's concurrency cache briefly.
            // Retry only before getStream was requested; never create a second pending stream.
            if (!game.requested && !game.failed && gameTicks % 20 == 0 && channel(game) == null)
                mc.getSoundManager().play(game);
            if (game.failed) ended("游戏曲目不可用，队列已结束。");
            else if (gameTicks > 600 && !game.ready) ended("游戏曲目读取超时，已释放资源。");
            else if (game.ready && gameTicks > 5 && !mc.getSoundManager().isActive(game)) ended("整单播放已结束。");
        }
        if (local != null) {
            localTicks++;
            // A stopped native record can remain in BiomeMusic's concurrency cache briefly.
            // Retry only before getStream was requested; never create a second pending stream.
            if (!local.requested && !local.failed && localTicks % 20 == 0 && channel(local) == null)
                mc.getSoundManager().play(local);
            if (local.failed) ended("本地曲目解码失败，队列已结束。");
            else if (localTicks > 600 && !local.ready) ended("本地解码超时，已释放资源。");
            else if (local.ready && localTicks > 5 && !mc.getSoundManager().isActive(local)) ended("整单播放已结束。");
        }
        if (ownedPortable != null) {
            portableWait++;
            for (var sound : List.copyOf(channels().keySet())) {
                try {
                    if (NetMusicListAdapter.ringer(sound) && NetMusicListAdapter.self(sound)
                        && ownedPortable.id().equals(NetMusicListAdapter.ringerId(sound)) && mc.getSoundManager().isActive(sound)) {
                        if (portableSound != null && portableSound != sound && !mc.getSoundManager().isActive(portableSound) && !queue.isEmpty()) {
                            ended("整单播放已结束。"); return;
                        }
                        portableSound = sound;
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
            if (portableSound != null && !mc.getSoundManager().isActive(portableSound)) ended("整单播放已结束。");
            else if (portableSound == null && portableWait > 600) ended("原生播放器未确认播放，已停止本次队列。");
        }
    }
    @SubscribeEvent public static void onPlay(PlaySoundEvent event) {
        if (local == null && game == null || event.getSound() == null || event.getSound() == local || event.getSound() == game) return;
        if (event.getSound().getSource() == SoundSource.MUSIC) event.setSound(null);
    }

    @SubscribeEvent public static void onUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        Minecraft.getInstance().execute(() -> { stopSession(); session++; MusicContinuity.reset(); profile = null; library = null; portableChoices = Map.of(); portableIds = Map.of(); busy = false; awaitingTarget = null; reading = null; message = ""; target = UUID.randomUUID().toString(); });
    }
}

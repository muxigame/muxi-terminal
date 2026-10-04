package net.muxigame.terminal.client.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import java.lang.reflect.Field;
import java.util.*;

/** Per-stream envelopes. Never changes Options, mod configuration or foreign RECORDS audio. */
public final class MusicContinuity {
    private record Fade(float from, float to, int began, int duration) {
        float value(int now) { return MusicEnvelope.interpolate(from, to, now - began, duration); }
    }
    private static final Map<SoundInstance, Fade> fades = new IdentityHashMap<>();
    private static int clock;
    private static boolean pendingBiomeIn;
    private static long biomeFades, legacyAdjustments, smoothStops;
    private MusicContinuity() {}
    private static SoundInstance background() {
        var manager = Minecraft.getInstance().getMusicManager();
        return manager instanceof MusicAccess.Manager access ? access.muxiMusic$current() : null;
    }
    private static boolean disableLegacy(Object manager, boolean clearMusic) {
        try {
            Field fade = manager.getClass().getDeclaredField("fadeTowards");
            fade.setAccessible(true); fade.setFloat(manager, -1);
            if (clearMusic) {
                Field music = manager.getClass().getDeclaredField("playedMusic");
                music.setAccessible(true); music.set(manager, null);
            }
            return true;
        } catch (ReflectiveOperationException ignored) { return false; }
    }
    public static boolean biomeIn(MusicManager manager) {
        if (!disableLegacy(manager, false)) return false;
        pendingBiomeIn = true; biomeFades++;
        SoundInstance current = background();
        if (current != null && TerminalMusicService.channel(current) != null)
            fades.put(current, new Fade(0.0001f, 1, clock, 40));
        return true;
    }
    public static boolean biomeOut(MusicManager manager) {
        if (!disableLegacy(manager, true)) return false;
        biomeFades++; manager.stopPlaying(); return true;
    }
    public static void afterStart() {
        if (pendingBiomeIn && background() != null)
            fades.putIfAbsent(background(), new Fade(0.0001f, 1, clock, 40));
        pendingBiomeIn = false;
    }
    public static float gain(SoundInstance sound, float nativeGain) {
        if (sound.getSource() != SoundSource.MUSIC) return nativeGain;
        // APP-owned streams do not inherit background-only biome/noteblock ducking.
        if (TerminalMusicService.owns(sound))
            return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);
        if (pendingBiomeIn && sound == background() && !fades.containsKey(sound))
            fades.put(sound, new Fade(0.0001f, 1, clock, 40));
        Fade fade = fades.get(sound);
        return fade == null ? nativeGain : Math.max(0, nativeGain * fade.value(clock));
    }
    public static boolean stopBackground(SoundInstance sound) {
        if (TerminalMusicService.hasOwnedPlayback()) return false;
        if (sound == null || sound.getSource() != SoundSource.MUSIC || TerminalMusicService.owns(sound)) return false;
        var handle = TerminalMusicService.channel(sound);
        if (handle == null || handle.isStopped()) return false;
        Fade previous = fades.get(sound);
        if (previous == null || previous.to() != 0) {
            fades.put(sound, new Fade(previous == null ? 1 : previous.value(clock), 0, clock, 40));
            smoothStops++;
        }
        return true;
    }
    public static void replaceLegacyAdjustment() {
        legacyAdjustments++;
        Minecraft.getInstance().getSoundManager().updateSourceVolume(SoundSource.MUSIC, 1);
    }
    public static void tick() {
        clock++;
        var sounds = Minecraft.getInstance().getSoundManager();
        boolean refresh = false;
        for (var entry : List.copyOf(fades.entrySet())) {
            Fade fade = entry.getValue();
            var handle = TerminalMusicService.channel(entry.getKey());
            if (clock - fade.began() >= fade.duration()) {
                fades.remove(entry.getKey());
                if (fade.to() == 0) sounds.stop(entry.getKey());
                refresh = true;
            } else if (handle != null && !handle.isStopped()) refresh = true;
        }
        if (refresh) sounds.updateSourceVolume(SoundSource.MUSIC, 1);
    }
    public static void reset() {
        var sounds = Minecraft.getInstance().getSoundManager();
        for (var entry : fades.entrySet()) if (entry.getValue().to() == 0) sounds.stop(entry.getKey());
        fades.clear(); pendingBiomeIn = false;
    }
    public static com.google.gson.JsonObject diagnostics() {
        var value = new com.google.gson.JsonObject();
        value.addProperty("biomeFadeHooks", biomeFades);
        value.addProperty("legacyGainWritesReplaced", legacyAdjustments);
        value.addProperty("smoothBackgroundStops", smoothStops);
        value.addProperty("activeEnvelopes", fades.size());
        return value;
    }
}

package net.muxigame.terminal.client.music;

/** Dimensionless fade targets: independent of user category volume and never used as stop sentinels. */
public final class MusicEnvelope {
    private MusicEnvelope() {}
    public static float interpolate(float from, float to, int elapsed, int duration) {
        float progress = Math.max(0, Math.min(1, elapsed / (float)Math.max(1, duration)));
        return Math.max(0, Math.min(1, from + (to - from) * progress));
    }
}

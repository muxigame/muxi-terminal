package net.muxigame.terminal.client.music;

import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import java.util.concurrent.CompletableFuture;

/** Select one actual resource file, rather than randomly choosing from a pooled event. */
final class GameMusicSound extends AbstractTickableSoundInstance {
    volatile boolean requested;
    volatile boolean ready, failed;
    private volatile boolean closed;
    private AudioStream opened;
    GameMusicSound(ResourceLocation audio) {
        super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(audio), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        volume = 1; pitch = 1; relative = true; attenuation = Attenuation.NONE;
    }
    public void tick() {}
    public boolean canPlaySound() { return !closed; }
    public boolean canStartSilent() { return true; }
    public WeighedSoundEvents resolve(SoundManager manager) {
        sound = new Sound(location, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
        var event = new WeighedSoundEvents(location, null);
        event.addSound(sound);
        return event;
    }
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean loop) {
        requested = true;
        return buffers.getStream(sound.getPath(), false).thenApply(stream -> {
            AudioStream next = new OwnedAudioStream(stream, () -> failed = !closed);
            synchronized (this) {
                if (closed) {
                    try { next.close(); } catch (Exception ignored) {}
                    throw new java.util.concurrent.CompletionException(new IllegalStateException("Stopped"));
                }
                opened = next; ready = true; return next;
            }
        }).whenComplete((stream, error) -> { if (error != null && !closed) failed = true; });
    }
    synchronized void dispose() {
        closed = true; stop();
        if (opened != null) { try { opened.close(); } catch (Exception ignored) {} opened = null; }
    }
}

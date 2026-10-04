package net.muxigame.terminal.client.music;

import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import java.nio.file.Path;
import java.util.concurrent.*;

/** One stream in the existing MC sound engine; no OS playback device or HTML audio. */
final class LocalMusicSound extends AbstractTickableSoundInstance {
    private final Path file;
    private volatile AudioStream opened;
    private volatile boolean closed;
    volatile boolean failed;
    volatile boolean requested;
    volatile boolean ready;
    LocalMusicSound(Path file) {
        super(SoundEvents.MUSIC_MENU.value(),
            SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.file = file;
        volume = 1; pitch = 1; relative = true; attenuation = Attenuation.NONE;
    }
    public void tick() {}
    public WeighedSoundEvents resolve(SoundManager manager) {
        sound = new Sound(location, net.minecraft.util.valueproviders.ConstantFloat.of(1),
            net.minecraft.util.valueproviders.ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
        var event = new WeighedSoundEvents(location, null); event.addSound(sound); return event;
    }
    public boolean canPlaySound() { return !closed; }
    public boolean canStartSilent() { return true; }
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean loop) {
        requested = true;
        return CompletableFuture.supplyAsync(() -> {
            try {
                AudioStream next = new OwnedAudioStream(LocalDecoder.open(file), () -> failed = !closed);
                synchronized (this) {
                    if (closed) { next.close(); throw new IllegalStateException("Closed"); }
                    opened = next; ready = true; return next;
                }
            } catch (Exception error) { failed = !closed; throw new CompletionException(new IllegalStateException("本地曲目解码失败")); }
        }, TerminalMusicService.io());
    }
    synchronized void dispose() {
        closed = true; stop();
        if (opened != null) {
            try { opened.close(); } catch (Exception ignored) {}
            opened = null;
        }
    }
}

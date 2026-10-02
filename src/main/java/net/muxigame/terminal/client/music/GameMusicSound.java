package net.muxigame.terminal.client.music;

import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import java.util.concurrent.CompletableFuture;

/** Select one actual resource file, rather than randomly choosing from a pooled event. */
final class GameMusicSound extends AbstractTickableSoundInstance {
    volatile boolean ready, failed;
    GameMusicSound(ResourceLocation audio) {
        super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(audio), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        volume = 1; pitch = 1; relative = true; attenuation = Attenuation.NONE;
    }
    public void tick() {}
    public boolean canStartSilent() { return true; }
    public WeighedSoundEvents resolve(SoundManager manager) {
        sound = new Sound(location, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
        var event = new WeighedSoundEvents(location, null);
        event.addSound(sound);
        return event;
    }
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean loop) {
        return buffers.getStream(sound.getPath(), false).whenComplete((stream, error) -> {
            ready = error == null; failed = error != null;
        });
    }
}

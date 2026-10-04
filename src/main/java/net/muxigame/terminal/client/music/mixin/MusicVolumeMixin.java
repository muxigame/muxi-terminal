package net.muxigame.terminal.client.music.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.muxigame.terminal.client.music.MusicContinuity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=SoundEngine.class, priority=900)
public abstract class MusicVolumeMixin {
    @Inject(method="calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at=@At("RETURN"), cancellable=true)
    private void muxiMusic$volume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(MusicContinuity.gain(sound, cir.getReturnValue()));
    }
    @Inject(method="adjustVolume", at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void muxiMusic$biomeVolume(SoundSource source, float value, CallbackInfo ci) {
        if (source == SoundSource.MUSIC) { MusicContinuity.replaceLegacyAdjustment(); ci.cancel(); }
    }
}

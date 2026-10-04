package net.muxigame.terminal.client.music.mixin;

import net.minecraft.client.sounds.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.Music;
import net.muxigame.terminal.client.music.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lower priority sees Biome Music's added helpers; optional hooks do not require that mod. */
@Mixin(value=MusicManager.class, priority=900)
public abstract class MusicManagerContinuityMixin {
    @Inject(method="fadeIn", at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void muxiMusic$biomeIn(CallbackInfo ci) {
        if (MusicContinuity.biomeIn((MusicManager)(Object)this)) ci.cancel();
    }
    @Inject(method="fadeOut", at=@At("HEAD"), cancellable=true, require=0, remap=false)
    private void muxiMusic$biomeOut(CallbackInfo ci) {
        if (MusicContinuity.biomeOut((MusicManager)(Object)this)) ci.cancel();
    }
    @Inject(method="startPlaying", at=@At("RETURN"))
    private void muxiMusic$started(Music music, CallbackInfo ci) { MusicContinuity.afterStart(); }
    @Inject(method="tick", at=@At("HEAD"), cancellable=true)
    private void muxiMusic$exclusive(CallbackInfo ci) {
        if (TerminalMusicService.hasOwnedPlayback()) ci.cancel();
    }
    @Redirect(method={"tick", "stopPlaying()V"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/sounds/SoundManager;stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"))
    private void muxiMusic$smoothStop(SoundManager manager, SoundInstance sound) {
        if (!MusicContinuity.stopBackground(sound)) manager.stop(sound);
    }
}

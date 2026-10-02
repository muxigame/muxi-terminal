package net.muxigame.terminal.client.music.mixin;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.muxigame.terminal.client.music.MusicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MusicManager.class)
public interface MusicManagerAccessor extends MusicAccess.Manager {
    @Accessor("currentMusic") SoundInstance muxiMusic$current();
}

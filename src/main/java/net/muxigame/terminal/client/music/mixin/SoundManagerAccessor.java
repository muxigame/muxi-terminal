package net.muxigame.terminal.client.music.mixin;
import net.minecraft.client.sounds.*;
import net.muxigame.terminal.client.music.MusicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SoundManager.class)
public interface SoundManagerAccessor extends MusicAccess.Sounds {
    @Accessor("soundEngine") SoundEngine muxiMusic$engine();
}

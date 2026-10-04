package net.muxigame.terminal.musicnextqa.mixin;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Channel.class,remap=false)
public interface ChannelGainAccessor {
    @Accessor("source") int qa$source();
}

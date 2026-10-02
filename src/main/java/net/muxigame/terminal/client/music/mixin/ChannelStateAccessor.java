package net.muxigame.terminal.client.music.mixin;
import com.mojang.blaze3d.audio.Channel;
import net.muxigame.terminal.client.music.MusicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Query only on ChannelAccess's sound thread. */
@Mixin(value = Channel.class, remap = false)
public interface ChannelStateAccessor extends MusicAccess.ChannelState {
    @Invoker(value = "getState", remap = false) int muxiMusic$state();
}

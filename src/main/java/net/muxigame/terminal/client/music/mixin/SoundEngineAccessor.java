package net.muxigame.terminal.client.music.mixin;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.muxigame.terminal.client.music.MusicAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor extends MusicAccess.Engine {
    @Accessor("instanceToChannel") Map<SoundInstance, ChannelAccess.ChannelHandle> muxiMusic$channels();
}

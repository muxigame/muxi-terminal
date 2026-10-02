package net.muxigame.terminal.client.music;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import java.util.Map;

/** Implemented by this module's narrow client mixins. Never exported to a web page. */
public final class MusicAccess {
    private MusicAccess() {}
    public interface Manager { SoundInstance muxiMusic$current(); }
    public interface Sounds { SoundEngine muxiMusic$engine(); }
    public interface Engine { Map<SoundInstance, ChannelAccess.ChannelHandle> muxiMusic$channels(); }
    public interface ChannelState { int muxiMusic$state(); }
}

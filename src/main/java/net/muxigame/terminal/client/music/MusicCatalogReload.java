package net.muxigame.terminal.client.music;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@EventBusSubscriber(modid = "muxi_terminal", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MusicCatalogReload {
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> GameMusicLibrary.reloaded());
    }
}

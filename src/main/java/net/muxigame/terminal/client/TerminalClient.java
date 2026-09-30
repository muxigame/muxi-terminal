package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.muxigame.terminal.MuxiTerminal;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class TerminalClient {
    private static final TerminalHandRenderer HAND_RENDERER = new TerminalHandRenderer();
    private static boolean bootstrapped;

    private TerminalClient() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        NeoForge.EVENT_BUS.addListener(TerminalClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(TerminalClient::onLevelUnload);
        TerminalNativeBridge.installWhenReady();
    }

    public static void openHome() {
        openApp("home");
    }

    public static void openApp(String app) {
        Minecraft mc = Minecraft.getInstance();
        if (MCEF.isInitialized()) {
            var browser=TerminalBrowserSession.getOrCreate();
            TerminalBrowserSession.openApp(app);
            mc.setScreen(new TerminalScreen(browser));
            return;
        }

        if (mc.player != null)
            mc.player.displayClientMessage(Component.translatable("muxi_terminal.mcef_wait"), true);

        MCEF.scheduleForInit(success -> mc.execute(() -> {
            if (success) {
                var browser=TerminalBrowserSession.getOrCreate();
                TerminalBrowserSession.openApp(app);
                mc.setScreen(new TerminalScreen(browser));
            }
            else if (mc.player != null)
                mc.player.displayClientMessage(Component.translatable("muxi_terminal.mcef_failed"), true);
        }));
    }

    private static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean hasTerminal = mc.player.getMainHandItem().is(MuxiTerminal.PLAYER_TERMINAL.get())
            || mc.player.getOffhandItem().is(MuxiTerminal.PLAYER_TERMINAL.get());
        if (!hasTerminal) return;

        // Render once from the main-hand pass and suppress both vanilla hands.
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            HAND_RENDERER.render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getSwingProgress(), event.getEquipProgress());
        }
        event.setCanceled(true);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) TerminalBrowserSession.close();
    }
}


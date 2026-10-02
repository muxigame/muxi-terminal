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

    public static void bootstrap(net.neoforged.bus.api.IEventBus modBus) {
        if (bootstrapped) return;
        bootstrapped = true;
        TerminalHeldScreen.register(modBus);
        NeoForge.EVENT_BUS.addListener(TerminalClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(TerminalClient::onLevelUnload);
        TerminalNativeBridge.installWhenReady();
        registerMinigameApp();
    }

    private static void registerMinigameApp() {
        try {
            java.util.function.BiConsumer<String,String> launcher=(game,page)->{
                TerminalGameAppsBridge.select(game,page);
                openApp("games");
            };
            Class.forName("net.muxigame.minigames.client.TerminalGamesApi")
                .getMethod("registerAppLauncher",java.util.function.BiConsumer.class).invoke(null,launcher);
        } catch (ClassNotFoundException unavailable) {
            // The terminal remains usable without the optional minigame framework.
        } catch (ReflectiveOperationException | LinkageError failed) {
            throw new IllegalStateException("Cannot register minigame app launcher",failed);
        }
    }

    /** Right-click preserves the page already visible on the held pad. */
    public static void openTerminal() {
        Minecraft mc = Minecraft.getInstance();
        if (!MCEF.isInitialized()) { openHome(); return; }
        mc.setScreen(new TerminalScreen(TerminalBrowserSession.getOrCreate()));
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
        boolean main = mc.player.getMainHandItem().is(MuxiTerminal.PLAYER_TERMINAL.get());
        boolean off = mc.player.getOffhandItem().is(MuxiTerminal.PLAYER_TERMINAL.get());
        if (!main && !off) return;
        // The live held display must exist before the player ever opens the UI.
        if (MCEF.isInitialized() && TerminalBrowserSession.current() == null)
            TerminalBrowserSession.getOrCreate();

        // Terminal follows map-like behaviour: one hand is enough. It only expands to
        // a two-hand tablet pose when the other hand is empty.
        if ((event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && main)
            || (event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND && off)) {
            HAND_RENDERER.render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getSwingProgress(), event.getEquipProgress(),
                event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND);
            event.setCanceled(true);
        } else if (mc.player.getItemInHand(event.getHand()).isEmpty()) {
            // The terminal renderer supplies both supporting arms. Suppress the
            // vanilla empty-hand pass, especially when the terminal is offhand.
            event.setCanceled(true);
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) TerminalBrowserSession.close();
    }
}

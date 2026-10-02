package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.muxigame.terminal.MuxiTerminal;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Explicit input capture, while the same browser remains drawn on the held item. */
final class TerminalHeldScreen extends TerminalScreen {
    private static final KeyMapping INTERACT = new KeyMapping("key.muxi_terminal.held_interact",
        InputConstants.Type.KEYSYM, 75 /* GLFW_KEY_K */, "key.categories.muxi_terminal");
    private final Object connection;

    private TerminalHeldScreen(MCEFBrowser browser) {
        super(browser);
        connection = Minecraft.getInstance().getConnection();
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(INTERACT));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            while (INTERACT.consumeClick()) {
                if (mc.screen == null && holdingTerminal() && MCEF.isInitialized())
                    mc.setScreen(new TerminalHeldScreen(TerminalBrowserSession.getOrCreate()));
            }
        });
    }

    static boolean holdingTerminal() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isAlive() &&
            (player.getMainHandItem().is(MuxiTerminal.PLAYER_TERMINAL.get())
             || player.getOffhandItem().is(MuxiTerminal.PLAYER_TERMINAL.get()));
    }

    @Override protected void init() {
        super.init();
        TerminalBrowserSession.resizeViews(640, 400, 32);
        closeButton.setPosition(width - 88, 8);
        KeyMapping.releaseAll();
    }

    @Override public void tick() {
        if (!holdingTerminal() || Minecraft.getInstance().getConnection() != connection) { onClose(); return; }
        super.tick();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String help = "方向键 / Tab 导航 · Enter 确认 · Alt+← 返回 · Esc 退出 · 右键大界面";
        graphics.fill(6, height - 24, width - 6, height - 5, 0xD017120D);
        graphics.drawString(font, font.plainSubstrByWidth(help, width - 24), 12, height - 18, 0xFFFFE7BC, false);
        closeButton.render(graphics, mouseX, mouseY, partialTick);
        TerminalBrowserSession.finishContentFrame();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) { TerminalClient.openTerminal(); return true; }
        return closeButton.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return true; }
    @Override public void mouseMoved(double x, double y) { }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return true; }
}

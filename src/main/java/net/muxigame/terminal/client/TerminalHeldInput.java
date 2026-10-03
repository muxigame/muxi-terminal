package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Narrow keyboard ownership for the live pad, without opening a Minecraft Screen. */
public final class TerminalHeldInput {
    private static final TerminalKeyCapture<MCEFBrowser> CAPTURE = new TerminalKeyCapture<>(new TerminalKeyCapture.Channel<>() {
        public boolean usable(MCEFBrowser target) { return TerminalBrowserSession.owns(target); }
        public void focus(MCEFBrowser target, boolean value) { target.setFocus(value); }
        public void press(MCEFBrowser target, int key, int scan, int modifiers) { target.sendKeyPress(key, scan, modifiers); }
        public void release(MCEFBrowser target, int key, int scan, int modifiers) { target.sendKeyRelease(key, scan, modifiers); }
        public void type(MCEFBrowser target, char character, int modifiers) { target.sendKeyTyped(character, modifiers); }
    });
    private static final java.util.Set<Integer> PRESSED = new java.util.HashSet<>();
    private TerminalHeldInput() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> refresh());
        // Release before the next Screen's init establishes its own browser focus.
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> clear());
    }

    private static boolean usable() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
            && mc.level != null && mc.getConnection() != null && mc.screen == null
            && mc.isWindowActive() && TerminalHeldScreen.holdingTerminal() && MCEF.isInitialized();
    }

    private static boolean navigation(int key) {
        return key == 262 || key == 263 || key == 264 || key == 265 || key == 257 || key == 335;
    }

    private static boolean refresh() {
        if (!usable()) { clear(); return false; }
        CAPTURE.bind(TerminalBrowserSession.activeBrowser(), true);
        return true;
    }

    /** Called inside the native keyPress dispatch; true suppresses only this pad event. */
    public static boolean key(long window, int key, int scan, int action, int modifiers) {
        var mc = Minecraft.getInstance();
        if (mc.getWindow() == null || window != mc.getWindow().getWindow()) return false;
        if (!navigation(key)) return false;
        if (action == 0) {
            if (!PRESSED.remove(key)) return false;
            CAPTURE.release(key, scan, modifiers);
            return usable();
        }
        // Movement, chat, Tab/offhand, player-list and modified shortcuts keep their routes.
        if ((action != 1 && action != 2) || modifiers != 0 || !refresh()) return false;
        // A game binding may already be down when the player picks up the pad.
        // Clear only the unmodified binding for this captured physical key.
        for (var mapping : mc.options.keyMappings) {
            if (mapping.getKeyModifier() == net.neoforged.neoforge.client.settings.KeyModifier.NONE
                && mapping.matches(key, scan)) {
                mapping.setDown(false);
                while (mapping.consumeClick()) {}
            }
        }
        // Confirmation belongs to one press even if its action opens a new view.
        if (action == 2 && (key == 257 || key == 335)) return true;
        CAPTURE.press(key, scan, modifiers);
        PRESSED.add(key);
        return true;
    }

    static void clear() {
        CAPTURE.clear();
        PRESSED.clear();
    }
}

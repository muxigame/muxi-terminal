package net.muxigame.terminal.client.music;

import net.minecraft.client.Minecraft;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import java.util.function.BooleanSupplier;

/** Owner calls this before shared command dispatch, passing actual browser identity. */
public final class TerminalMusicBridge {
    private TerminalMusicBridge() {}
    public static boolean handle(CefBrowser browser, CefFrame frame, boolean ownedBrowser,
                                 String request, CefQueryCallback callback, BooleanSupplier validGeneration) {
        if (request == null || !request.startsWith("music.")) return false;
        if (!MusicSecurity.trusted(ownedBrowser, frame != null && frame.isMain(),
            frame == null ? null : frame.getURL(), browser == null ? null : browser.getURL())) {
            callback.failure(403, "音乐仅对终端内置页面开放"); return true;
        }
        if (request.length() > 192) { callback.failure(400, "音乐请求过长"); return true; }
        Minecraft.getInstance().execute(() -> {
            if (!validGeneration.getAsBoolean()) return;
            try { callback.success(TerminalMusicService.command(request, validGeneration)); }
            catch (Exception | LinkageError error) { callback.failure(503, "音乐接口不可用，请返回原播放器检查"); }
        });
        return true;
    }
}

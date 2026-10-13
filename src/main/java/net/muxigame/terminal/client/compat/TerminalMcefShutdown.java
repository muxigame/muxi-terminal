package net.muxigame.terminal.client.compat;

import com.cinemamod.mcef.MCEF;
import net.muxigame.terminal.client.TerminalBrowserSession;
import org.cef.CefApp;
import org.cef.browser.CefBrowser;

/** Graceful shutdown of this JVM's CEF app; never enumerates or kills OS processes. */
public final class TerminalMcefShutdown {
    private static final java.util.Set<CefBrowser> views=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private TerminalMcefShutdown() {}
    public static void opened(CefBrowser browser) { views.add(browser); }
    public static void closed(CefBrowser browser) { views.remove(browser); }
    public static void close() {
        if (!MCEF.isInitialized()) return;
        CefApp app=MCEF.getApp().getHandle();
        TerminalBrowserSession.close();
        // Drain browser/client callbacks while CEF still owns its message-loop context.
        // N_DoMessageLoopWork after app.dispose can race native context destruction.
        long deadline=System.nanoTime()+2_000_000_000L;
        while (!views.isEmpty() && System.nanoTime()<deadline) {
            app.N_DoMessageLoopWork();
            if (!pause()) break;
        }
        if (!views.isEmpty()) {
            System.getLogger("MuxiTerminal/McefShutdown").log(System.Logger.Level.WARNING,
                "Terminal CEF close callbacks did not drain: {0}",views.size());
            return;
        }
        MCEF.shutdown();
        // No native message-loop calls are permitted after app disposal.
        deadline=System.nanoTime()+2_000_000_000L;
        while (CefApp.getState()!=CefApp.CefAppState.TERMINATED && System.nanoTime()<deadline) {
            if (!pause()) break;
        }
        System.getLogger("MuxiTerminal/McefShutdown").log(
            CefApp.getState()==CefApp.CefAppState.TERMINATED?System.Logger.Level.INFO:System.Logger.Level.WARNING,
            "Per-JVM graceful CEF shutdown state: {0}; terminal views: {1}",CefApp.getState(),views.size());
    }
    private static boolean pause() {
        try { Thread.sleep(2); return true; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }
}

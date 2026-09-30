package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;

public final class TerminalBrowserSession {
    public static final String HOME_URL = "mod://muxi_terminal/terminal/index.html";
    private static MCEFBrowser browser;

    private TerminalBrowserSession() {}

    public static synchronized MCEFBrowser getOrCreate() {
        if (browser == null) {
            if (!MCEF.isInitialized()) throw new IllegalStateException("MCEF is not initialized");
            browser = MCEF.createBrowser(HOME_URL, false);
            browser.resize(1280, 720);
        }
        return browser;
    }

    public static synchronized MCEFBrowser current() {
        return browser;
    }

    public static synchronized void home() {
        if (browser != null) browser.loadURL(HOME_URL);
    }

    public static synchronized void close() {
        if (browser == null) return;
        browser.close();
        browser = null;
    }
}


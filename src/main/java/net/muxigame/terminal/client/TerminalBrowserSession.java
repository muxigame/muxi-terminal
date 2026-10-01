package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import java.net.URI;
import java.util.Set;

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
        TerminalPassportNavigation.clear();
        if (browser != null) browser.loadURL(HOME_URL);
    }

    public static synchronized void openApp(String app) {
        getOrCreate().loadURL(HOME_URL+"#/"+safeRoute(app));
    }

    public static synchronized boolean openExternal(String value) {
        try {
            URI uri=URI.create(value);
            if(!"https".equalsIgnoreCase(uri.getScheme())) return false;
            if(!Set.of("account.muxigame.com","mc.muxigame.com").contains(uri.getHost())) return false;
            getOrCreate().loadURL(uri.toString());
            return true;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private static String safeRoute(String route) {
        if(route==null) return "home";
        return switch(route){case "tasks","guide","home"->route;default->"home";};
    }

    public static synchronized void close() {
        TerminalPassportNavigation.clear();
        if (browser == null) return;
        browser.close();
        browser = null;
    }
}


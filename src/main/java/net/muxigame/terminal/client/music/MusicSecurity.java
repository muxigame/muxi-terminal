package net.muxigame.terminal.client.music;

/** Pure policy, independently tested. Only the owner's built-in top-level shell is trusted. */
public final class MusicSecurity {
    public static final String SHELL = "mod://muxi_terminal/terminal/index.html";
    private MusicSecurity() {}
    public static boolean trusted(boolean owned, boolean mainFrame, String frame, String browser) {
        return owned && mainFrame && shell(frame) && shell(browser)
            && frame.equals(SHELL + "#/music") && browser.equals(frame);
    }
    private static boolean shell(String url) {
        if (url == null) return false;
        int hash = url.indexOf('#');
        return (hash < 0 ? url : url.substring(0, hash)).equals(SHELL);
    }
    public static boolean id(String value) {
        return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    public static String title(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains("://") || text.startsWith("file:")) return "未命名曲目";
        if (text.contains("\\") || text.startsWith("/") || text.matches("^[A-Za-z]:.*")) text = text.replace('\\', '/').replaceFirst(".*/", "");
        text = text.replaceAll("[\\p{Cntrl}]", "").strip();
        return text.isEmpty() ? "未命名曲目" : text.substring(0, Math.min(160, text.length()));
    }
}

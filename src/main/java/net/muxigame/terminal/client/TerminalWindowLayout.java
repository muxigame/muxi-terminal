package net.muxigame.terminal.client;

/** One GUI-space layout drives the tablet, native controls and browser input mapping. */
final class TerminalWindowLayout {
    static final int FRAME = 10, TOP = 24, BUTTON = 18, VIEWPORT_MARGIN = 4;
    static final double ASPECT = 16.0 / 10.0;
    private TerminalWindowLayout() {}
    record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) {
            return px >= x && py >= y && px < right() && py < bottom();
        }
    }
    record View(Rect outer, Rect content, Rect close, Rect windowMode) {
        int browserX(double guiX, double scale) {
            return Math.min(browserWidth(scale) - 1, (int)Math.round((guiX - content.x()) * scale));
        }
        int browserY(double guiY, double scale, int toolbar) {
            int bodyHeight = Math.max(1, browserHeight(scale) - (int)Math.round(toolbar * scale));
            return Math.min(bodyHeight - 1, (int)Math.round((guiY - content.y() - toolbar) * scale));
        }
        int browserWidth(double scale) { return Math.max(1, (int)Math.round(content.width() * scale)); }
        int browserHeight(double scale) { return Math.max(1, (int)Math.round(content.height() * scale)); }
    }
    static View fit(int width, int height, boolean fullscreen) {
        int maxWidth = Math.max(1, width - VIEWPORT_MARGIN * 2);
        int maxHeight = Math.max(1, height - VIEWPORT_MARGIN * 2);
        int targetWidth = maxWidth, targetHeight = maxHeight;
        if (!fullscreen) {
            // Enlarge the original outer rectangle, including its minimum-size case, by 10%.
            int oldWidth = Math.max(180, (int)(width * 0.58f));
            int oldHeight = (int)Math.round(oldWidth / ASPECT);
            int oldLimit = Math.max(120, (int)(height * 0.54f));
            if (oldHeight > oldLimit) {
                oldHeight = oldLimit;
                oldWidth = (int)Math.round(oldHeight * ASPECT);
            }
            targetWidth = Math.min(maxWidth, (int)Math.round(oldWidth * 1.10));
            targetHeight = Math.min(maxHeight, (int)Math.round(oldHeight * 1.10));
        }
        int outerWidth = targetWidth, outerHeight = (int)Math.round(outerWidth / ASPECT);
        if (outerHeight > targetHeight) {
            outerHeight = targetHeight;
            outerWidth = (int)Math.round(outerHeight * ASPECT);
        }
        Rect outer = new Rect((width - outerWidth) / 2, (height - outerHeight) / 2, outerWidth, outerHeight);
        Rect content = new Rect(outer.x() + FRAME, outer.y() + TOP,
            Math.max(1, outer.width() - FRAME * 2), Math.max(1, outer.height() - TOP - FRAME));
        Rect close = new Rect(outer.right() - BUTTON - 2, outer.y() + 3, BUTTON, BUTTON);
        Rect windowMode = new Rect(close.x() - BUTTON - 2, close.y(), BUTTON, BUTTON);
        return new View(outer, content, close, windowMode);
    }
}

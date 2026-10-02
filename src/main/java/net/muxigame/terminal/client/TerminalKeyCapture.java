package net.muxigame.terminal.client;

import java.util.HashMap;
import java.util.Map;

/** Tracks pressed keys by actual browser identity; switching cannot release into a new page. */
final class TerminalKeyCapture<T> {
    interface Channel<T> {
        boolean usable(T target);
        void focus(T target, boolean focused);
        void press(T target, int key, int scan, int modifiers);
        void release(T target, int key, int scan, int modifiers);
        void type(T target, char character, int modifiers);
    }
    private final Channel<T> channel;
    private final Map<Integer,Integer> pressed = new HashMap<>();
    private T target;
    private boolean focused;
    TerminalKeyCapture(Channel<T> channel) { this.channel = channel; }
    void bind(T next, boolean focus) {
        if (next != target) { clear(); target = next; }
        if (target != null && channel.usable(target) && focus != focused) {
            if (!focus) releasePressed();
            channel.focus(target, focus); focused = focus;
        }
    }
    void press(int key, int scan, int modifiers) {
        if (target == null || !focused || !channel.usable(target)) return;
        channel.press(target, key, scan, modifiers); pressed.put(key, scan);
    }
    void release(int key, int scan, int modifiers) {
        if (pressed.remove(key) != null && target != null && channel.usable(target))
            channel.release(target, key, scan, modifiers);
    }
    void type(char character, int modifiers) {
        if (target != null && focused && channel.usable(target) && !Character.isISOControl(character))
            channel.type(target, character, modifiers);
    }
    void clear() {
        if (target != null && channel.usable(target)) {
            releasePressed();
            channel.focus(target, false);
        }
        pressed.clear(); target = null; focused = false;
    }
    private void releasePressed() {
        for (var key : pressed.entrySet()) channel.release(target, key.getKey(), key.getValue(), 0);
        pressed.clear();
    }
}

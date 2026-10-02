package net.muxigame.terminal.albumqa;
import java.util.*;
public final class ImageProbe {
 private static final Set<Object> images=Collections.newSetFromMap(new IdentityHashMap<>());
 public static synchronized void acquired(Object image){images.add(image);}
 public static synchronized void closed(Object image){images.remove(image);}
 public static synchronized int live(){return images.size();}
}

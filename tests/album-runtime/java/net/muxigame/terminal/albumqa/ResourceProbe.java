package net.muxigame.terminal.albumqa;

import com.google.gson.*;
import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import java.lang.management.ManagementFactory;
import java.lang.management.BufferPoolMXBean;
import java.util.*;
import org.lwjgl.opengl.GL11;

/** QA counters hold native addresses/integers only, never product images or browsers. No forced GC. */
public final class ResourceProbe {
    private static final Map<Long,Long> images=new HashMap<>();
    private static final Set<Integer> textures=new HashSet<>();
    private static long allocated,closed,liveBytes,textureAllocations,textureCloses;
    public static final java.util.concurrent.atomic.AtomicLong queriesStarted=new java.util.concurrent.atomic.AtomicLong(),queriesSettled=new java.util.concurrent.atomic.AtomicLong();
    public static synchronized void image(long pointer,long bytes){if(pointer!=0 && images.putIfAbsent(pointer,bytes)==null){allocated++;liveBytes+=bytes;}}
    public static synchronized void close(long pointer){Long bytes=images.remove(pointer);if(bytes!=null){closed++;liveBytes-=bytes;}}
    public static synchronized void texture(int id){if(id!=0 && textures.add(id))textureAllocations++;}
    public static synchronized void closeTexture(int id){if(id!=0 && textures.remove(id))textureCloses++;}
    public static synchronized JsonObject sample(String phase,int round){
        var value=new JsonObject();value.addProperty("phase",phase);value.addProperty("bridgeQueriesStarted",queriesStarted.get());value.addProperty("bridgeQueriesSettled",queriesSettled.get());value.addProperty("bridgeQueriesOutstanding",queriesStarted.get()-queriesSettled.get());value.addProperty("round",round);value.addProperty("nanoTime",System.nanoTime());
        var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();value.addProperty("heapUsed",heap.getUsed());value.addProperty("heapCommitted",heap.getCommitted());
        long collections=0;for(var gc:ManagementFactory.getGarbageCollectorMXBeans())collections+=Math.max(0,gc.getCollectionCount());value.addProperty("naturalGcCount",collections);
        value.addProperty("nativeImagesLive",images.size());value.addProperty("nativeImageBytesLive",liveBytes);value.addProperty("nativeImageAllocations",allocated);value.addProperty("nativeImageCloses",closed);
        int live=0;for(int texture:textures)if(GL11.glIsTexture(texture))live++;value.addProperty("cefTexturesOwned",textures.size());value.addProperty("cefTexturesLive",live);value.addProperty("cefTextureAllocations",textureAllocations);value.addProperty("cefTextureCloses",textureCloses);
        value.addProperty("mainColorTexture",Minecraft.getInstance().getMainRenderTarget().getColorTextureId());
        value.addProperty("cameraWorkerThreads",Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().equals("muxi-terminal-camera-io")).count());
        for(var pool:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)){value.addProperty(pool.getName()+"Buffers",pool.getCount());value.addProperty(pool.getName()+"BufferBytes",pool.getMemoryUsed());}
        try{var app=MCEF.getApp().getHandle();var field=org.cef.CefApp.class.getDeclaredField("clients_");field.setAccessible(true);synchronized(app){value.addProperty("cefClients",((Collection<?>)field.get(app)).size());}}catch(Exception e){value.addProperty("cefClientsUnavailable",e.toString());}
        if(org.lwjgl.opengl.GL.getCapabilities().GL_NVX_gpu_memory_info)value.addProperty("gpuAvailableKiB",GL11.glGetInteger(0x9049));
        return value;
    }
}

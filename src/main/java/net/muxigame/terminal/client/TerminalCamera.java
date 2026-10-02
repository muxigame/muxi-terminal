package net.muxigame.terminal.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import org.cef.callback.CefQueryCallback;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reads the already rendered world; no second world pass, entity mutation or private YSM API. */
public final class TerminalCamera {
    private static boolean installed;
    private static Session session;
    private static CameraType previous;
    private static boolean previousHideGui;
    private static final AtomicBoolean working=new AtomicBoolean();
    private static volatile State result=new State(false,"forward",false,0,"","","");
    public record State(boolean active,String mode,boolean busy,long sequence,String preview,String saved,String error){}
    private static final class Session {
        final long generation;final Object browser;
        boolean selfie,shutter;long nextFrame,sequence;
        volatile boolean closed;
        Session(){generation=TerminalBrowserSession.generation();browser=TerminalBrowserSession.content();}
    }
    private TerminalCamera(){}
    public static void begin(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof TerminalHeldScreen)throw new IllegalStateException("Right-click to open the large terminal view before using camera");
        if(mc.level==null || mc.player==null || !(mc.screen instanceof TerminalScreen) || !mc.isWindowActive())throw new IllegalStateException("Return to the focused game to use camera");
        if(!installed){
            installed=true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,TerminalCamera::beforeFrame);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,TerminalCamera::afterFrame);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,TerminalCamera::beforeGui);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,TerminalCamera::hand);
            NeoForge.EVENT_BUS.addListener(TerminalCamera::tick);
            TerminalBrowserSession.addStateListener(s->{if(session!=null && !valid(session))stop();});
        }
        if(session!=null && valid(session))return;
        stop();session=new Session();result=new State(true,"forward",false,0,"","","");
    }
    public static void stop(){
        if(session!=null)session.closed=true;
        session=null;restore();
        result=new State(false,result.mode(),false,result.sequence(),"",result.saved(),result.error());
    }
    public static void mode(boolean selfie){
        require();if(session.shutter || working.get())throw new IllegalStateException("Wait for the current photo");
        session.selfie=selfie;session.nextFrame=0;
        // Prevent a frame from the previous lens being presented as the new lens.
        result=new State(true,selfie?"selfie":"forward",false,result.sequence(),"",result.saved(),"");
    }
    public static void shutter(){require();if(working.get() || session.shutter)throw new IllegalStateException("Photo is busy");session.shutter=true;session.nextFrame=0;}
    public static State state(){var s=session;return new State(s!=null,result.mode(),working.get() || (s!=null && s.shutter),result.sequence(),result.preview(),result.saved(),result.error());}
    private static void require(){if(session==null || !valid(session))throw new IllegalStateException("Preview paused; resume camera first");}
    private static boolean valid(Session s){
        Minecraft mc=Minecraft.getInstance();
        return !s.closed && mc.level!=null && mc.player!=null && mc.screen instanceof TerminalScreen && !(mc.screen instanceof TerminalHeldScreen) && mc.isWindowActive()
            && TerminalBrowserSession.generation()==s.generation && TerminalBrowserSession.content()==s.browser
            && TerminalBrowserSession.activeBrowser()==s.browser && TerminalBrowserSession.contentVisible()
            && (TerminalBrowserSession.HOME_URL+"#/camera").equals(TerminalBrowserSession.state().url());
    }
    private static void beforeFrame(RenderFrameEvent.Pre event){
        restore();if(session==null)return;if(!valid(session)){stop();return;}
        var mc=Minecraft.getInstance();previous=mc.options.getCameraType();previousHideGui=mc.options.hideGui;
        mc.options.setCameraType(session.selfie?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        mc.options.hideGui=false; // Makes the pre-HUD hook available even after F1; restored this frame.
    }
    private static void afterFrame(RenderFrameEvent.Post event){restore();}
    private static void restore(){if(previous!=null){var mc=Minecraft.getInstance();mc.options.setCameraType(previous);mc.options.hideGui=previousHideGui;previous=null;}}
    private static void tick(ClientTickEvent.Pre event){restore();if(session!=null && !valid(session))stop();}
    private static void hand(RenderHandEvent event){if(session!=null && valid(session))event.setCanceled(true);}
    private static void beforeGui(RenderGuiEvent.Pre event){
        Session s=session;if(s==null || previous==null || !valid(s) || working.get() || System.nanoTime()<s.nextFrame)return;
        RenderSystem.assertOnRenderThread();
        boolean save=s.shutter;s.shutter=false;s.nextFrame=System.nanoTime()+1_000_000_000L;
        NativeImage image=null;
        int binding=GlStateManager._getInteger(0x8069); // GL_TEXTURE_BINDING_2D
        int[] names={0x0D05,0x0D02,0x0D03,0x0D04}; // PACK_ALIGNMENT / ROW_LENGTH / SKIP_ROWS / SKIP_PIXELS
        int[] values=new int[names.length];for(int i=0;i<names.length;i++)values[i]=GlStateManager._getInteger(names[i]);
        try{
            var target=Minecraft.getInstance().getMainRenderTarget();
            if((long)target.width*target.height>16_777_216L)throw new IllegalStateException("Camera supports frames up to 16 megapixels");
            // Own allocation before download so an OpenGL/readback failure also closes it.
            // Same bind/download/flip sequence as 1.21.1 Screenshot.takeScreenshot.
            image=new NativeImage(target.width,target.height,false);
            RenderSystem.bindTexture(target.getColorTextureId());image.downloadTexture(0,true);image.flipY();
            if(!save){
                int width=Math.min(640,image.getWidth()),height=Math.max(1,(int)((long)width*image.getHeight()/image.getWidth()));
                NativeImage small=new NativeImage(width,height,false);
                try{image.resizeSubRectTo(0,0,image.getWidth(),image.getHeight(),small);}catch(Throwable error){small.close();throw error;}
                image.close();image=small;
            }
            NativeImage owned=image;image=null;
            TerminalPhotoStore store=TerminalCameraBridge.store();String mode=s.selfie?"selfie":"forward";
            try{if(!submit(()->{
                try(owned){
                    if(s.closed)return;
                    byte[] png=owned.asByteArray();String saved=save?store.save(png):result.saved();
                    String preview;
                    if(save){
                        int w=Math.min(640,owned.getWidth()),h=Math.max(1,(int)((long)w*owned.getHeight()/owned.getWidth()));
                        try(var small=new NativeImage(w,h,false)){owned.resizeSubRectTo(0,0,owned.getWidth(),owned.getHeight(),small);preview=data(small.asByteArray());}
                    }else preview=data(png);
                    if(!s.closed)result=new State(true,mode,false,++s.sequence,preview,saved,"");
                }catch(Exception error){if(!s.closed)result=new State(true,mode,false,result.sequence(),result.preview(),result.saved(),"Photo could not be saved or read");}
            })){owned.close();}}catch(RuntimeException | Error error){owned.close();throw error;}
        }catch(Exception error){result=new State(true,s.selfie?"selfie":"forward",false,result.sequence(),result.preview(),result.saved(),"Camera frame unavailable");}
        finally{if(image!=null)image.close();GlStateManager._bindTexture(binding);for(int i=0;i<names.length;i++)GlStateManager._pixelStore(names[i],values[i]);}
    }
    @FunctionalInterface interface Read {String get() throws Exception;}
    static void io(Read read,CefQueryCallback callback){
        if(!submit(()->{try{String response=read.get();Minecraft.getInstance().execute(()->callback.success(response));}catch(Exception error){Minecraft.getInstance().execute(()->callback.failure(400,"Photo unavailable"));}}))callback.failure(429,"Camera is busy");
    }
    // One bounded daemon task at a time; the worker exits after each operation. No persistent thread or queue.
    private static boolean submit(Runnable action){
        if(!working.compareAndSet(false,true))return false;
        Thread worker=new Thread(()->{try{action.run();}finally{working.set(false);}},"muxi-terminal-camera-io");
        worker.setDaemon(true);
        try{worker.start();return true;}catch(RuntimeException | Error error){working.set(false);throw error;}
    }
    static String data(byte[] png){return "data:image/png;base64,"+Base64.getEncoder().encodeToString(png);}
}

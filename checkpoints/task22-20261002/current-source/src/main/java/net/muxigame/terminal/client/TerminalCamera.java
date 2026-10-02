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

/** Native world view. Browser objects/navigation stay alive, but are never drawn in this Screen. */
public final class TerminalCamera {
    private static boolean installed;
    private static Session session;
    private static CameraType frameCamera;
    private static boolean frameHideGui;
    private static final AtomicBoolean working=new AtomicBoolean();
    private static volatile State result=new State(false,"forward",false,0,"","","");
    public record State(boolean active,String mode,boolean busy,long sequence,String preview,String saved,String error){}
    private static final class Session {
        final long generation=TerminalBrowserSession.generation();
        final Object browser=TerminalBrowserSession.current(),content=TerminalBrowserSession.content();
        final Object level=Minecraft.getInstance().level,player=Minecraft.getInstance().player,connection=Minecraft.getInstance().getConnection();
        final CameraType cameraType=Minecraft.getInstance().options.getCameraType();
        final boolean hideGui=Minecraft.getInstance().options.hideGui;
        final TerminalScreen terminal;
        final TerminalCameraScreen screen=new TerminalCameraScreen();
        boolean selfie,shutter;float yaw,pitch;
        volatile boolean closed;
        Session(TerminalScreen terminal){this.terminal=terminal;}
    }
    private TerminalCamera(){}
    public static void begin(){
        var mc=Minecraft.getInstance();
        if(session!=null && valid(session))return;
        if(!(mc.screen instanceof TerminalScreen terminal) || mc.level==null || mc.player==null || !mc.player.isAlive() || mc.getConnection()==null || !mc.isWindowActive())
            throw new IllegalStateException("请在已进入世界的终端中打开相机");
        install();stop(false);var next=new Session(terminal);session=next;
        result=new State(true,"forward",false,0,"","","");
        try{mc.setScreen(next.screen);}catch(RuntimeException | Error failed){stop(false);mc.setScreen(terminal);throw failed;}
    }
    private static void install(){
        if(installed)return;installed=true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,TerminalCamera::beforeFrame);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,TerminalCamera::afterFrame);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,TerminalCamera::hand);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,TerminalCamera::angles);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,TerminalCamera::gui);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,TerminalCamera::layer);
        NeoForge.EVENT_BUS.addListener(TerminalCamera::tick);
        TerminalBrowserSession.addStateListener(s->{if(session!=null && !context(session))stop(true);});
    }
    public static void stop(){stop(true);}
    static void removed(TerminalCameraScreen screen){if(session!=null && session.screen==screen)stop(false);}
    private static void stop(boolean returnTerminal){
        var s=session;if(s!=null)s.closed=true;session=null;restoreFrame();
        if(s!=null){var mc=Minecraft.getInstance();mc.options.setCameraType(s.cameraType);mc.options.hideGui=s.hideGui;}
        result=new State(false,result.mode(),working.get(),result.sequence(),"",result.saved(),result.error());
        if(s!=null && returnTerminal && Minecraft.getInstance().screen==s.screen){
            // No home/openApp/loadURL calls: same Screen and same live browser DOM/history.
            Minecraft.getInstance().setScreen(context(s)?s.terminal:null);
        }
    }
    private static boolean context(Session s){
        var mc=Minecraft.getInstance();
        return mc.level!=null && mc.level==s.level && mc.player==s.player && mc.player!=null && mc.player.isAlive()
            && mc.getConnection()!=null && mc.getConnection()==s.connection
            && TerminalBrowserSession.generation()==s.generation && TerminalBrowserSession.current()==s.browser
            && TerminalBrowserSession.content()==s.content
            && (!(s.terminal instanceof TerminalHeldScreen) || TerminalHeldScreen.holdingTerminal());
    }
    private static boolean valid(Session s){return !s.closed && context(s) && Minecraft.getInstance().screen==s.screen;}
    private static Session require(){var s=session;if(s==null || !valid(s))throw new IllegalStateException("相机已关闭");return s;}
    public static void mode(boolean selfie){
        var s=require();if(s.shutter || working.get())throw new IllegalStateException("正在保存照片，请稍候");
        s.selfie=selfie;s.yaw=s.pitch=0;
        result=new State(true,selfie?"selfie":"forward",false,result.sequence(),"",result.saved(),"");
    }
    public static void shutter(){
        var s=require();if(!Minecraft.getInstance().isWindowActive())throw new IllegalStateException("请先返回游戏窗口");
        if(working.get() || s.shutter)throw new IllegalStateException("正在保存照片，请稍候");
        s.shutter=true;result=new State(true,result.mode(),true,result.sequence(),"",result.saved(),"");
    }
    static void aim(double dx,double dy){var s=require();s.yaw+=(float)dx*0.15f;s.pitch=Math.max(-70,Math.min(70,s.pitch+(float)dy*0.15f));}
    public static State state(){var s=session;return new State(s!=null,result.mode(),working.get() || (s!=null && s.shutter),result.sequence(),"",result.saved(),result.error());}
    static boolean focused(){return Minecraft.getInstance().isWindowActive();}
    private static void beforeFrame(RenderFrameEvent.Pre event){
        restoreFrame();var s=session;if(s==null)return;if(!valid(s)){stop(true);return;}
        var mc=Minecraft.getInstance();frameCamera=s.cameraType;frameHideGui=s.hideGui;
        mc.options.setCameraType(s.selfie?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        mc.options.hideGui=true; // Temporary; never saved to options.txt. Normal HUD/map layers are disabled.
    }
    private static void afterFrame(RenderFrameEvent.Post event){restoreFrame();}
    private static void restoreFrame(){
        if(frameCamera!=null){var mc=Minecraft.getInstance();mc.options.setCameraType(frameCamera);mc.options.hideGui=frameHideGui;frameCamera=null;}
    }
    private static void tick(ClientTickEvent.Pre event){
        restoreFrame();var s=session;if(s==null)return;
        if(!valid(s)){stop(true);return;}
        if(s.shutter && !focused()){
            s.shutter=false;result=new State(true,result.mode(),working.get(),result.sequence(),"",result.saved(),"窗口失焦，本次拍照已取消");
        }
    }
    private static void hand(RenderHandEvent event){if(session!=null && valid(session))event.setCanceled(true);}
    private static void gui(RenderGuiEvent.Pre event){if(session!=null && valid(session))event.setCanceled(true);}
    private static void layer(RenderGuiLayerEvent.Pre event){if(session!=null && valid(session))event.setCanceled(true);}
    private static void angles(ViewportEvent.ComputeCameraAngles event){
        var s=session;if(s!=null && valid(s) && frameCamera!=null){event.setYaw(event.getYaw()+s.yaw);event.setPitch(Math.max(-89,Math.min(89,event.getPitch()+s.pitch)));}
    }
    /** First statement of native Screen.render, after world/shader output and BEFORE camera controls. */
    static void capture(){
        var s=session;if(s==null || !valid(s) || frameCamera==null || !s.shutter || working.get() || !focused())return;
        RenderSystem.assertOnRenderThread();s.shutter=false;
        NativeImage image=null;int binding=GlStateManager._getInteger(0x8069);
        int[] names={0x0D05,0x0D02,0x0D03,0x0D04},values=new int[names.length];
        for(int i=0;i<names.length;i++)values[i]=GlStateManager._getInteger(names[i]);
        try{
            var target=Minecraft.getInstance().getMainRenderTarget();
            if(target.width<=0 || target.height<=0 || (long)target.width*target.height>16_777_216L)throw new IllegalStateException("照片支持最大 1600 万像素");
            image=new NativeImage(target.width,target.height,false);
            RenderSystem.bindTexture(target.getColorTextureId());image.downloadTexture(0,true);image.flipY();
            NativeImage owned=image;image=null;var store=TerminalCameraBridge.store();String mode=s.selfie?"selfie":"forward";
            try{if(!submit(()->{
                try(owned){
                    if(s.closed)return;
                    String saved=store.save(owned.asByteArray());
                    if(!s.closed)result=new State(true,mode,false,result.sequence()+1,"",saved,"");
                }catch(Exception failure){if(!s.closed)result=new State(true,mode,false,result.sequence(),"",result.saved(),"保存失败，请检查本机磁盘空间");}
            })){owned.close();result=new State(true,mode,false,result.sequence(),"",result.saved(),"照片保存忙，请重试");}}
            catch(RuntimeException | Error failure){owned.close();throw failure;}
        }catch(Exception failure){result=new State(true,s.selfie?"selfie":"forward",false,result.sequence(),"",result.saved(),"无法读取当前画面，请重试");}
        finally{if(image!=null)image.close();GlStateManager._bindTexture(binding);for(int i=0;i<names.length;i++)GlStateManager._pixelStore(names[i],values[i]);}
    }
    @FunctionalInterface interface Read {String get() throws Exception;}
    static void io(Read read,CefQueryCallback callback){
        if(!submit(()->{try{String response=read.get();Minecraft.getInstance().execute(()->callback.success(response));}catch(Exception error){Minecraft.getInstance().execute(()->callback.failure(400,"Photo unavailable"));}}))callback.failure(429,"Camera is busy");
    }
    private static boolean submit(Runnable action){
        if(!working.compareAndSet(false,true))return false;
        Thread worker=new Thread(()->{try{action.run();}finally{working.set(false);}},"muxi-terminal-camera-io");worker.setDaemon(true);
        try{worker.start();return true;}catch(RuntimeException | Error error){working.set(false);throw error;}
    }
    static String data(byte[] png){return "data:image/png;base64,"+Base64.getEncoder().encodeToString(png);}
}

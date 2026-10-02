package net.muxigame.terminal.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import org.cef.CefClient;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

/** Separate query namespace; attach ONLY beside the existing trusted-client bridge. */
final class TerminalSettingsBridge {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Muxi terminal local wallpaper");t.setDaemon(true);return t;});
    private static final AtomicBoolean choosing=new AtomicBoolean();
    private static long wallpaperRevision;
    static void attach(CefClient client){
        client.addMessageRouter(CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig("muxiSettingsQuery","muxiSettingsCancel"),new Handler()));
    }
    private static final class Handler extends CefMessageRouterHandlerAdapter {
        @Override public boolean onQuery(CefBrowser b,CefFrame f,long id,String request,boolean persistent,CefQueryCallback callback){
            if(!TerminalBrowserSession.trusted(b,f)){callback.failure(403,"仅可信本地终端主页面可调用设置");return true;}
            if(persistent || request==null || request.length()>2048){callback.failure(400,"设置请求无效");return true;}
            long epoch=TerminalBrowserSession.generation(),frameId=f.getIdentifier();String document=f.getURL();var mc=Minecraft.getInstance();
            // Never retain JCEF's short-lived onQuery CefFrame wrapper across threads.
            java.util.function.BooleanSupplier valid=()->currentDocument(b,frameId,document,epoch);
            CefQueryCallback reply=new CefQueryCallback(){
                private final AtomicBoolean settled=new AtomicBoolean();
                @Override public void success(String value){if(settled.compareAndSet(false,true)){if(valid.getAsBoolean())callback.success(value);else callback.failure(409,"Terminal view changed");}}
                @Override public void failure(int code,String message){if(settled.compareAndSet(false,true)){boolean current=valid.getAsBoolean();callback.failure(current?code:409,current?message:"Terminal view changed");}}
            };
            var screen=mc.screen;var connection=mc.getConnection();
            java.util.function.BooleanSupplier active=()->valid.getAsBoolean() && screen instanceof TerminalScreen && mc.screen==screen
                && connection!=null && mc.getConnection()==connection && mc.level!=null && mc.player!=null && mc.player.isAlive()
                && TerminalBrowserSession.content()==b && TerminalBrowserSession.contentVisible();
            mc.execute(()->{
                if(!valid.getAsBoolean()){reply.failure(409,"Terminal view changed");return;}
                try {
                    if(request.equals("ui.snapshot")){reply.success(TerminalSettings.ui().toString());return;}
                    if(request.startsWith("ui.sound:")){TerminalSettings.sound(request.substring(9));reply.success("{}");return;}
                    if(request.equals("wallpaper.read")){
                        long revision=wallpaperRevision;
                        IO.execute(()->{
                            String data="";
                            try{Path p=TerminalSettings.file("wallpaper.png");if(Files.exists(p))data=TerminalWallpaper.data(TerminalWallpaper.normalize(p));}catch(Exception ignored){}
                            String result=data;mc.execute(()->{
                                if(!valid.getAsBoolean()){reply.failure(409,"Terminal view changed");return;}
                                if(revision!=wallpaperRevision){reply.failure(409,"壁纸已变更");return;}
                                publishWallpaper(result);var j=new JsonObject();j.addProperty("data",result);reply.success(j.toString());
                            });
                        });return;
                    }
                    // Native state is the authority; callers cannot claim a route or permission.
                    if(!document.equals(TerminalBrowserSession.HOME_URL+"#/settings") || !active.getAsBoolean() || !mc.isWindowActive()){reply.failure(403,"请在设置应用内操作");return;}
                    if(request.equals("settings.snapshot")){reply.success(TerminalSettings.snapshot().toString());return;}
                    if(request.startsWith("settings.apply:")){reply.success(TerminalSettings.apply(JsonParser.parseString(request.substring(15)).getAsJsonObject()).toString());return;}
                    if(request.startsWith("ui.save:")){
                        String result=TerminalSettings.saveUi(JsonParser.parseString(request.substring(8)).getAsJsonObject()).toString();
                        publishScript("window.terminalSettingsUi?.("+result+")");reply.success(result);return;
                    }
                    if(request.equals("settings.vanilla")){reply.success("{}");TerminalSettings.vanilla();return;}
                    if(request.equals("wallpaper.reset")){
                        if(choosing.get()){reply.failure(409,"请先关闭图片选择器");return;}
                        Files.deleteIfExists(TerminalSettings.file("wallpaper.png"));wallpaperRevision++;publishWallpaper("");reply.success("{}");return;
                    }
                    if(request.equals("wallpaper.choose")){
                        if(!choosing.compareAndSet(false,true)){reply.failure(409,"图片选择器已打开");return;}
                        IO.execute(()->{
                            try {
                                // Reflection keeps the existing offline compile classpath; tinyfd ships with MC.
                                var type=Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
                                var method=type.getMethod("tinyfd_openFileDialog",CharSequence.class,CharSequence.class,Class.forName("org.lwjgl.PointerBuffer"),CharSequence.class,boolean.class);
                                String path=(String)method.invoke(null,"选择终端壁纸（PNG / JPEG）",null,null,"PNG / JPEG",false);
                                byte[] png=path==null?null:TerminalWallpaper.normalize(Path.of(path));
                                mc.execute(()->{
                                    try {
                                        if(!active.getAsBoolean()){reply.failure(409,"Terminal view changed");return;}
                                        var j=new JsonObject();j.addProperty("cancelled",png==null);
                                        if(png!=null){TerminalWallpaper.atomicWrite(TerminalSettings.file("wallpaper.png"),png);wallpaperRevision++;publishWallpaper(TerminalWallpaper.data(png));}
                                        reply.success(j.toString());
                                    }catch(Exception e){reply.failure(400,"壁纸保存失败，请检查本地目录权限");}
                                    finally{choosing.set(false);}
                                });
                            }catch(Exception | LinkageError error){mc.execute(()->{choosing.set(false);reply.failure(400,"图片无法读取：仅支持 PNG / JPEG，12 MiB，最长边 8192，最多 1600 万像素");});}
                        });return;
                    }
                    reply.failure(404,"未知设置命令");
                }catch(Exception e){reply.failure(400,"设置未保存：请检查输入值或本地目录权限");}
            });return true;
        }
    }
    private static boolean currentDocument(CefBrowser browser,long frameId,String document,long epoch){
        if(TerminalBrowserSession.generation()!=epoch)return false;
        CefFrame live=browser.getMainFrame();if(live==null)return false;
        try{return live.getIdentifier()==frameId && TerminalBrowserSession.trusted(browser,live) && document.equals(live.getURL());}
        finally{live.dispose();}
    }
    private static void publishWallpaper(String data){
        publishScript("window.terminalSettingsWallpaper?.("+new Gson().toJson(data)+")");
    }
    private static void publishScript(String script){
        var shell=TerminalBrowserSession.current();if(shell!=null)shell.executeJavaScript(script,TerminalBrowserSession.HOME_URL,0);
        var app=TerminalBrowserSession.content();if(app!=null && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN)app.executeJavaScript(script,TerminalBrowserSession.HOME_URL,0);
    }
}

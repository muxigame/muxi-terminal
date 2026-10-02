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
            long epoch=TerminalBrowserSession.generation();String document=f.getURL();var mc=Minecraft.getInstance();
            java.util.function.BooleanSupplier valid=()->TerminalBrowserSession.generation()==epoch && TerminalBrowserSession.trusted(b,f) && document.equals(f.getURL());
            mc.execute(()->{
                if(!valid.getAsBoolean())return;
                try {
                    if(request.equals("ui.snapshot")){callback.success(TerminalSettings.ui().toString());return;}
                    if(request.startsWith("ui.sound:")){TerminalSettings.sound(request.substring(9));callback.success("{}");return;}
                    if(request.equals("wallpaper.read")){
                        long revision=wallpaperRevision;
                        IO.execute(()->{
                            String data="";
                            try{Path p=TerminalSettings.file("wallpaper.png");if(Files.exists(p))data=TerminalWallpaper.data(TerminalWallpaper.normalize(p));}catch(Exception ignored){}
                            String result=data;mc.execute(()->{
                                if(!valid.getAsBoolean())return;
                                if(revision!=wallpaperRevision){callback.failure(409,"壁纸已变更");return;}
                                publishWallpaper(result);var j=new JsonObject();j.addProperty("data",result);callback.success(j.toString());
                            });
                        });return;
                    }
                    // Native state is the authority; callers cannot claim a route or permission.
                    if(!document.equals(TerminalBrowserSession.HOME_URL+"#/settings")){callback.failure(403,"请在设置应用内操作");return;}
                    if(request.equals("settings.snapshot")){callback.success(TerminalSettings.snapshot().toString());return;}
                    if(request.startsWith("settings.apply:")){callback.success(TerminalSettings.apply(JsonParser.parseString(request.substring(15)).getAsJsonObject()).toString());return;}
                    if(request.startsWith("ui.save:")){
                        String result=TerminalSettings.saveUi(JsonParser.parseString(request.substring(8)).getAsJsonObject()).toString();
                        publishScript("window.terminalSettingsUi?.("+result+")");callback.success(result);return;
                    }
                    if(request.equals("settings.vanilla")){callback.success("{}");TerminalSettings.vanilla();return;}
                    if(request.equals("wallpaper.reset")){
                        if(choosing.get()){callback.failure(409,"请先关闭图片选择器");return;}
                        Files.deleteIfExists(TerminalSettings.file("wallpaper.png"));wallpaperRevision++;publishWallpaper("");callback.success("{}");return;
                    }
                    if(request.equals("wallpaper.choose")){
                        if(!choosing.compareAndSet(false,true)){callback.failure(409,"图片选择器已打开");return;}
                        IO.execute(()->{
                            try {
                                // Reflection keeps the existing offline compile classpath; tinyfd ships with MC.
                                var type=Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
                                var method=type.getMethod("tinyfd_openFileDialog",CharSequence.class,CharSequence.class,Class.forName("org.lwjgl.PointerBuffer"),CharSequence.class,boolean.class);
                                String path=(String)method.invoke(null,"选择终端壁纸（PNG / JPEG）",null,null,"PNG / JPEG",false);
                                byte[] png=path==null?null:TerminalWallpaper.normalize(Path.of(path));
                                mc.execute(()->{
                                    try {
                                        if(!valid.getAsBoolean())return;
                                        var j=new JsonObject();j.addProperty("cancelled",png==null);
                                        if(png!=null){TerminalWallpaper.atomicWrite(TerminalSettings.file("wallpaper.png"),png);wallpaperRevision++;publishWallpaper(TerminalWallpaper.data(png));}
                                        callback.success(j.toString());
                                    }catch(Exception e){callback.failure(400,"壁纸保存失败，请检查本地目录权限");}
                                    finally{choosing.set(false);}
                                });
                            }catch(Exception | LinkageError error){mc.execute(()->{choosing.set(false);if(valid.getAsBoolean())callback.failure(400,"图片无法读取：仅支持 PNG / JPEG，12 MiB，最长边 8192，最多 1600 万像素");});}
                        });return;
                    }
                    callback.failure(404,"未知设置命令");
                }catch(Exception e){callback.failure(400,"设置未保存：请检查输入值或本地目录权限");}
            });return true;
        }
    }
    private static void publishWallpaper(String data){
        publishScript("window.terminalSettingsWallpaper?.("+new Gson().toJson(data)+")");
    }
    private static void publishScript(String script){
        var shell=TerminalBrowserSession.current();if(shell!=null)shell.executeJavaScript(script,TerminalBrowserSession.HOME_URL,0);
        var app=TerminalBrowserSession.content();if(app!=null && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN)app.executeJavaScript(script,TerminalBrowserSession.HOME_URL,0);
    }
}

package net.muxigame.terminal.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import java.util.function.BooleanSupplier;

/** Scoped photo-library operations; never arbitrary paths, OS trash or screenshot authority. */
public final class TerminalAlbumBridge {
    private static final Gson JSON=new Gson();
    private static final TerminalAlbumConfirmation CONFIRM=new TerminalAlbumConfirmation();
    private TerminalAlbumBridge(){}
    /** The browser is retained for the handheld terminal, so native Screen exit needs explicit pixel release. */
    static void visibility(boolean visible){
        var browser=TerminalBrowserSession.content();
        if(browser!=null && (TerminalBrowserSession.HOME_URL+"#/album").equals(browser.getURL()))
            browser.executeJavaScript("window.dispatchEvent(new CustomEvent('muxi-album-visibility',{detail:"+visible+"}))",browser.getURL(),0);
    }
    static boolean authorized(CefBrowser browser,CefFrame frame){
        var mc=Minecraft.getInstance();String url=TerminalBrowserSession.HOME_URL+"#/album";
        return mc.screen instanceof TerminalScreen && mc.level!=null && mc.player!=null
            && TerminalBrowserSession.trusted(browser,frame) && browser==TerminalBrowserSession.content()
            && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN
            && url.equals(browser.getURL()) && url.equals(frame.getURL());
    }
    public static boolean dispatch(CefBrowser browser,CefFrame frame,String request,CefQueryCallback callback){
        if(!request.startsWith("album."))return false;
        boolean mutation=request.startsWith("album.prepare-delete:") || request.equals("album.cancel-delete")
            || request.startsWith("album.recycle:") || request.startsWith("album.restore:");
        if(!authorized(browser,frame) || (mutation && !Minecraft.getInstance().isWindowActive())){callback.failure(403,"Album is available only in the owned album app");return true;}
        var mc=Minecraft.getInstance();var directory=mc.gameDirectory;var screen=mc.screen;var level=mc.level;var player=mc.player;var connection=mc.getConnection();
        long generation=TerminalBrowserSession.generation();
        BooleanSupplier valid=()->authorized(browser,frame) && mc.gameDirectory==directory && mc.screen==screen && mc.level==level && mc.player==player && mc.getConnection()==connection && (!mutation || mc.isWindowActive())
            && TerminalBrowserSession.generation()==generation && TerminalBrowserSession.content()==browser;
        try{
            var photos=TerminalCameraBridge.store();
            if(request.equals("album.photos")){io(valid,()->JSON.toJson(photos.list()),callback);return true;}
            if(request.equals("album.recycled")){io(valid,()->JSON.toJson(photos.recycled()),callback);return true;}
            if(request.startsWith("album.prepare-delete:")){
                String id=request.substring("album.prepare-delete:".length());TerminalPhotoStore.requireWritable(id);
                callback.success(JSON.toJson(CONFIRM.prepare(id,browser,generation)));return true;
            }
            if(request.equals("album.cancel-delete")){CONFIRM.cancel(browser,generation);callback.success("{\"ok\":true}");return true;}
            if(request.startsWith("album.recycle:")){
                String token=request.substring("album.recycle:".length());
                if(!token.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid confirmation");
                io(valid,()->{photos.recycle(CONFIRM.consume(token,browser,generation));return "{\"ok\":true}";},callback);return true;
            }
            if(request.startsWith("album.restore:")){
                String id=request.substring("album.restore:".length());checkId(id);
                io(valid,()->{photos.restore(id);return "{\"ok\":true}";},callback);return true;
            }
            for(String prefix:new String[]{"album.thumb:","album.photo:","album.recycled-thumb:","album.recycled-photo:"}){
                if(!request.startsWith(prefix))continue;
                String id=request.substring(prefix.length());checkId(id);
                boolean recycled=prefix.startsWith("album.recycled-"),thumb=prefix.contains("thumb");
                io(valid,()->JSON.toJson(TerminalPhotoImages.data(photos,id,recycled,thumb?240:1280,thumb?160:1280)),callback);return true;
            }
            callback.failure(404,"Unknown album command");
        }catch(Exception error){callback.failure(400,error.getMessage()==null?"Album unavailable":error.getMessage());}
        return true;
    }
    private static void checkId(String id){if(!TerminalPhotoStore.validId(id))throw new IllegalArgumentException("Invalid photo id");}
    private static void io(BooleanSupplier valid,TerminalCamera.Read action,CefQueryCallback callback){
        TerminalCamera.io(()->{if(!valid.getAsBoolean())throw new IllegalStateException("Album view changed");return action.get();},new CefQueryCallback(){
            @Override public void success(String value){if(valid.getAsBoolean())callback.success(value);else callback.failure(409,"Album view changed");}
            @Override public void failure(int code,String value){callback.failure(valid.getAsBoolean()?code:409,valid.getAsBoolean()?value:"Album view changed");}
        });
    }
}

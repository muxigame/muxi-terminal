package net.muxigame.terminal.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

/** Called ONLY after the owner's existing generation/main-frame authorization. */
public final class TerminalCameraBridge {
    private static final Gson JSON=new Gson();
    private TerminalCameraBridge(){}
    static boolean authorized(CefBrowser browser,CefFrame frame){
        return TerminalBrowserSession.trusted(browser,frame) && browser==TerminalBrowserSession.content()
            && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN
            && (TerminalBrowserSession.HOME_URL+"#/camera").equals(browser.getURL())
            && (TerminalBrowserSession.HOME_URL+"#/camera").equals(frame.getURL());
    }
    public static boolean dispatch(CefBrowser browser,CefFrame frame,String request,CefQueryCallback callback){
        if(!request.startsWith("camera."))return false;
        if(!authorized(browser,frame)){callback.failure(403,"Camera is available only in the owned camera app");return true;}
        try{
            switch(request){
                case "camera.begin" -> {TerminalCamera.begin();callback.success("{\"ok\":true}");}
                case "camera.stop" -> {TerminalCamera.stop();callback.success("{\"ok\":true}");}
                case "camera.mode:forward" -> {TerminalCamera.mode(false);callback.success("{\"ok\":true}");}
                case "camera.mode:selfie" -> {TerminalCamera.mode(true);callback.success("{\"ok\":true}");}
                case "camera.shutter" -> {TerminalCamera.shutter();callback.success("{\"ok\":true}");}
                case "camera.state" -> callback.success(JSON.toJson(TerminalCamera.state()));
                case "camera.photos" -> {var photos=store();TerminalCamera.io(()->JSON.toJson(photos.list()),callback);}
                default -> {
                    if(request.startsWith("camera.photo:")){
                        String id=request.substring(13);
                        if(!TerminalPhotoStore.validId(id))throw new IllegalArgumentException("Invalid photo id");
                        var photos=store();
                        TerminalCamera.io(()->JSON.toJson(TerminalPhotoImages.data(photos,id,false,1280,Integer.MAX_VALUE)),callback);
                    }else callback.failure(404,"Unknown camera command");
                }
            }
        }catch(Exception error){callback.failure(400,error.getMessage()==null?"Camera unavailable":error.getMessage());}
        return true;
    }
    static TerminalPhotoStore store(){
        var mc=Minecraft.getInstance();
        return new TerminalPhotoStore(mc.gameDirectory.toPath().resolve("screenshots/muxi-terminal").resolve(mc.getUser().getProfileId().toString()));
    }
}

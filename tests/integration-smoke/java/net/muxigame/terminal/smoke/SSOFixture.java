package net.muxigame.terminal.smoke;

import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.cef.CefClient;
import org.cef.browser.*;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.misc.*;
import org.cef.network.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** TEST MOD ONLY. No real credential, login proof, network auth or production configuration. */
public final class SSOFixture {
    public static final String ENTRY="https://mc.muxigame.com/api/v1/auth/terminal";
    public static final String POST=ENTRY+"/exchange",ACCOUNT="https://mc.muxigame.com/account.html";
    public static final String BODY="{\"ticket\":\"synthetic-one-use-fixture\",\"verifier\":\"synthetic-native-fixture\",\"requestId\":\"fixture\"}";
    public static volatile Consumer<String> pending;
    public static volatile int requestCalls;
    public static volatile int posts,requests;
    public static volatile String postedBody="",postedMethod="",postedOrigin="",postedAction="";
    public static volatile boolean holdEntry;
    private static LocalPlayer identity;
    private static final ThreadLocal<Deque<LocalPlayer>> saved=ThreadLocal.withInitial(LinkedList::new);
    private SSOFixture() {}
    public static void enter(){
        if(!Minecraft.getInstance().isSameThread())throw new AssertionError("SSO identity fixture must run on game thread");
        try{
            if(identity==null){
                var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
                identity=(LocalPlayer)unsafe.allocateInstance(LocalPlayer.class);
                var connection=(ClientPacketListener)unsafe.allocateInstance(ClientPacketListener.class);
                var member=LocalPlayer.class.getField("connection");member.setAccessible(true);member.set(identity,connection);
            }
            saved.get().push(Minecraft.getInstance().player);Minecraft.getInstance().player=identity;
        }catch(Exception e){throw new RuntimeException(e);}
    }
    public static void leave(){Minecraft.getInstance().player=saved.get().pop();}
    public static Runnable wrap(Runnable original){return ()->{enter();try{original.run();}finally{leave();}};}
    public static void deliver(){var callback=pending;pending=null;if(callback==null)throw new AssertionError("No synthetic ticket request");callback.accept(BODY);}
    public static void install(MCEFBrowser browser){
        try{
            CefClient client=browser.getClient();var field=CefClient.class.getDeclaredField("requestHandler_");field.setAccessible(true);
            var delegate=(CefRequestHandler)field.get(client);
            client.removeRequestHandler();client.addRequestHandler(new CefRequestHandlerAdapter(){
                @Override public boolean onBeforeBrowse(CefBrowser b,CefFrame f,CefRequest r,boolean user,boolean redirect){return delegate.onBeforeBrowse(b,f,r,user,redirect);}
                @Override public boolean onOpenURLFromTab(CefBrowser b,CefFrame f,String url,boolean gesture){return delegate.onOpenURLFromTab(b,f,url,gesture);}
                @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b,CefFrame f,CefRequest r,boolean nav,boolean download,String initiator,BoolRef disable){
                    var original=delegate.getResourceRequestHandler(b,f,r,nav,download,initiator,disable);
                    return new CefResourceRequestHandlerAdapter(){
                        @Override public boolean onBeforeResourceLoad(CefBrowser b,CefFrame f,CefRequest r){return original.onBeforeResourceLoad(b,f,r);}
                        @Override public CefCookieAccessFilter getCookieAccessFilter(CefBrowser b,CefFrame f,CefRequest r){return original.getCookieAccessFilter(b,f,r);}
                        @Override public void onProtocolExecution(CefBrowser b,CefFrame f,CefRequest r,BoolRef allow){original.onProtocolExecution(b,f,r,allow);}
                        @Override public CefResourceHandler getResourceHandler(CefBrowser b,CefFrame f,CefRequest r){return response(r);}
                    };
                }
            });
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private static CefResourceHandler response(CefRequest request){
        requests++;String url=request.getURL();
        if(POST.equals(url)){
            posts++;postedMethod=request.getMethod();postedOrigin=request.getHeaderByName("Origin");postedAction=request.getHeaderByName("X-Muxi-Terminal-Action");
            var elements=new Vector<CefPostDataElement>();if(request.getPostData()!=null)request.getPostData().getElements(elements);
            var output=new java.io.ByteArrayOutputStream();for(var element:elements){byte[] bytes=new byte[element.getBytesCount()];element.getBytes(bytes.length,bytes);output.writeBytes(bytes);}
            postedBody=output.toString(StandardCharsets.UTF_8);
        }
        String html=ENTRY.equals(url)?"<html><body>QA entry<iframe src='"+ENTRY+"?child=1'></iframe></body></html>":
            "<html><body style='background:#d4f0d1;font:28px sans-serif;padding:30px'><h1>QA account signed in</h1><p>Synthetic single-use native POST. Persistent terminal shell.</p></body></html>";
        boolean entry=ENTRY.equals(url);boolean post=POST.equals(url);
        byte[] body=html.getBytes(StandardCharsets.UTF_8);
        return new CefResourceHandlerAdapter(){
            private int offset;
            @Override public boolean processRequest(CefRequest r,CefCallback callback){
                if(entry && holdEntry){Thread worker=new Thread(()->{try{Thread.sleep(1000);}catch(InterruptedException ignored){}callback.Continue();});worker.setDaemon(true);worker.start();}
                else callback.Continue();return true;
            }
            @Override public void getResponseHeaders(CefResponse response,IntRef length,StringRef redirect){
                response.setStatus(post?302:200);response.setMimeType("text/html");length.set(body.length);
                if(post){response.setHeaderByName("Location",ACCOUNT,true);redirect.set(ACCOUNT);}
                response.setHeaderByName("Cache-Control","no-store",true);
            }
            @Override public boolean readResponse(byte[] data,int requested,IntRef read,CefCallback callback){
                int count=Math.min(Math.min(requested,data.length),body.length-offset);if(count<=0){read.set(0);return false;}
                System.arraycopy(body,offset,data,0,count);offset+=count;read.set(count);return true;
            }
        };
    }
}

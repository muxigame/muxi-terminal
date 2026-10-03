package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFClient;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import org.cef.CefClient;
import org.cef.browser.*;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.*;

/** Private CefClient for each view. External clients never receive a native message router. */
final class TerminalViewClient {
    final TerminalBrowserSession.Kind kind;
    final MCEFBrowser browser;
    private final CefClient client;
    private final CefRequestContext accountContext;
    TerminalViewClient(TerminalBrowserSession.Kind kind,String url){
        this.kind=kind;client=MCEF.getApp().getHandle().createClient();
        accountContext=kind==TerminalBrowserSession.Kind.ACCOUNT?CefRequestContext.createContext(null):null;
        MCEFClient wrapper=new MCEFClient(client);
        client.addRequestHandler(new CefRequestHandlerAdapter(){
            @Override public boolean onBeforeBrowse(CefBrowser b,CefFrame f,CefRequest request,boolean user,boolean redirect){
                String target=request.getURL();
                if(kind==TerminalBrowserSession.Kind.ACCOUNT && TerminalPassportNavigation.beforeBrowse(b,f,target))return true;
                boolean allowed=switch(kind){
                    case HOME,BUILTIN -> TerminalWebPolicy.localDocument(target);
                    case WEB -> TerminalWebPolicy.web(target) || (f!=null && !f.isMain() && "about:blank".equals(target));
                    case ACCOUNT -> TerminalWebPolicy.account(target) || TerminalPassportNavigation.ERROR.equals(target) || "about:blank".equals(target);
                };
                if(!allowed){TerminalBrowserSession.loaded(b,b.getURL(),false,"已阻止不受支持或越过账户边界的链接");return true;}
                if(f!=null && f.isMain())TerminalBrowserSession.navigation(b);
                return false;
            }
            @Override public boolean onOpenURLFromTab(CefBrowser b,CefFrame f,String target,boolean gesture){return true;}
            @Override public boolean getAuthCredentials(CefBrowser b,String origin,boolean proxy,String host,int port,String realm,String scheme,CefAuthCallback callback){callback.cancel();return true;}
            @Override public boolean onCertificateError(CefBrowser b,CefLoadHandler.ErrorCode code,String url,CefCallback callback){return false;}
            @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b,CefFrame f,CefRequest r,boolean nav,boolean download,String initiator,BoolRef disable){
                return new CefResourceRequestHandlerAdapter(){
                    @Override public boolean onBeforeResourceLoad(CefBrowser b,CefFrame f,CefRequest r){
                        String value=r.getURL();
                        if(kind==TerminalBrowserSession.Kind.ACCOUNT){
                            var headers=new java.util.HashMap<String,String>();r.getHeaderMap(headers);
                            headers.keySet().removeIf(key->key.equalsIgnoreCase("Authorization") || key.equalsIgnoreCase("Cookie"));
                            r.setHeaderMap(headers);
                            if(!TerminalPassportNavigation.permitsResource(b,value) || TerminalPassportNavigation.interactiveAuth(value))return true;
                            if(r.getPostData()!=null && !TerminalPassportNavigation.credentialOrigin(value))return true;
                            if(TerminalPassportNavigation.credentialOrigin(value)){
                                String access=TerminalPassportNavigation.freshAccess(b);
                                if(access.isEmpty())return true;
                                headers.put("Authorization","Bearer "+access);r.setHeaderMap(headers);
                            }
                        }
                        if(kind==TerminalBrowserSession.Kind.HOME || kind==TerminalBrowserSession.Kind.BUILTIN)
                            return !value.startsWith("mod://muxi_terminal/terminal/") && !value.startsWith("data:");
                        return !TerminalWebPolicy.web(value) && !value.startsWith("data:") && !value.startsWith("blob:") && !"about:blank".equals(value);
                    }
                    @Override public CefCookieAccessFilter getCookieAccessFilter(CefBrowser b,CefFrame f,CefRequest r){
                        if(kind==TerminalBrowserSession.Kind.ACCOUNT)return new CefCookieAccessFilter(){
                            @Override public boolean canSendCookie(CefBrowser b,CefFrame f,CefRequest r,CefCookie c){return false;}
                            @Override public boolean canSaveCookie(CefBrowser b,CefFrame f,CefRequest r,CefResponse response,CefCookie c){return false;}
                        };
                        if(kind!=TerminalBrowserSession.Kind.WEB)return null;
                        return new CefCookieAccessFilter(){
                            private boolean allowed(CefRequest r,CefCookie c){
                                String domain=c.domain==null?"":c.domain.toLowerCase(java.util.Locale.ROOT);
                                return !TerminalWebPolicy.accountHost(r.getURL()) && !domain.equals("muxigame.com") && !domain.endsWith(".muxigame.com");
                            }
                            @Override public boolean canSendCookie(CefBrowser b,CefFrame f,CefRequest r,CefCookie c){return allowed(r,c);}
                            @Override public boolean canSaveCookie(CefBrowser b,CefFrame f,CefRequest r,CefResponse response,CefCookie c){return allowed(r,c);}
                        };
                    }
                    @Override public void onProtocolExecution(CefBrowser b,CefFrame f,CefRequest r,BoolRef allow){allow.set(false);}
                };
            }
        });
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter(){
            @Override public boolean onBeforePopup(CefBrowser b,CefFrame f,String target,String name){
                long epoch=TerminalBrowserSession.generation();
                if(kind==TerminalBrowserSession.Kind.WEB && TerminalWebPolicy.web(target))Minecraft.getInstance().execute(()->{
                    if(TerminalBrowserSession.content()==b && TerminalBrowserSession.generation()==epoch)b.loadURL(target);
                });
                return true;
            }
        });
        client.addDownloadHandler(new CefDownloadHandlerAdapter(){
            @Override public void onBeforeDownload(CefBrowser b,CefDownloadItem item,String name,CefBeforeDownloadCallback callback){}
            @Override public void onDownloadUpdated(CefBrowser b,CefDownloadItem item,CefDownloadItemCallback callback){callback.cancel();}
        });
        client.addDialogHandler(new CefDialogHandler(){
            @Override public boolean onFileDialog(CefBrowser b,CefDialogHandler.FileDialogMode mode,String title,String path,java.util.Vector<String> accepts,CefFileDialogCallback callback){if(kind==TerminalBrowserSession.Kind.ACCOUNT && TerminalPassportNavigation.authorized(b))return false;callback.Cancel();return true;}
        });
        wrapper.addLoadHandler(new CefLoadHandlerAdapter(){
            @Override public void onLoadStart(CefBrowser b,CefFrame f,CefRequest.TransitionType type){if(f.isMain() && !(kind==TerminalBrowserSession.Kind.ACCOUNT && (f.getURL().startsWith("about:blank") || TerminalPassportNavigation.staleAccountLoad(b,f.getURL()))))TerminalBrowserSession.loaded(b,f.getURL(),true,"");}
            @Override public void onLoadEnd(CefBrowser b,CefFrame f,int status){
                if(!f.isMain() || status==ErrorCode.ERR_ABORTED.getCode() || (kind==TerminalBrowserSession.Kind.ACCOUNT && (f.getURL().startsWith("about:blank") || TerminalPassportNavigation.staleAccountLoad(b,f.getURL()))))return;
                if(kind==TerminalBrowserSession.Kind.BUILTIN && status>0 && status<400 && TerminalWebPolicy.localDocument(f.getURL()))
                    b.executeJavaScript("window.terminalPrepareContent?.("+new com.google.gson.Gson().toJson(java.net.URI.create(url).getFragment())+")",TerminalBrowserSession.HOME_URL,0);
                TerminalBrowserSession.loaded(b,f.getURL(),false,status<=0?"网页未能加载，请检查链接或网络":status>=400?"HTTP "+status:"");
            }
            @Override public void onLoadError(CefBrowser b,CefFrame f,ErrorCode code,String text,String failed){
                if(f.isMain() && code!=ErrorCode.ERR_ABORTED && !(kind==TerminalBrowserSession.Kind.ACCOUNT && TerminalPassportNavigation.staleAccountLoad(b,failed)))TerminalBrowserSession.loaded(b,failed,false,"网页加载失败："+text);
            }
        });
        if(kind==TerminalBrowserSession.Kind.HOME || kind==TerminalBrowserSession.Kind.BUILTIN){TerminalNativeBridge.attach(client);TerminalSettingsBridge.attach(client);}
        if(kind==TerminalBrowserSession.Kind.ACCOUNT)TerminalPassportNavigation.install(wrapper);
        browser=new MCEFBrowser(wrapper,kind==TerminalBrowserSession.Kind.BUILTIN?TerminalBrowserSession.HOME_URL:url,false){
            @Override protected CefRequestContext getRequestContext(){return accountContext==null?super.getRequestContext():accountContext;}
            @Override public void onPaint(CefBrowser b,boolean popup,java.awt.Rectangle[] dirty,java.nio.ByteBuffer buffer,int width,int height){
                super.onPaint(b,popup,dirty,buffer,width,height);if(!popup)TerminalBrowserSession.painted(b);
            }
        }.useBrowserControls(false);
        browser.setCloseAllowed();
    }
    void start(){browser.createImmediately();}
    void close(){browser.setFocus(false);browser.stopLoad();browser.close();client.dispose();if(accountContext!=null)accountContext.dispose();}
}

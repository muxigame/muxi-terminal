package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/** Submit a one-use payload natively from the expected browser's exact HTTPS main frame. */
final class TerminalPassportNavigation {
    private static final String ACCOUNT="https://mc.muxigame.com/account.html";
    private static final String EXCHANGE="https://mc.muxigame.com/api/v1/auth/terminal";
    private static final String POST="https://mc.muxigame.com/api/v1/auth/terminal/exchange";
    private static MCEFBrowser expected;
    private static String payload;
    private static long expires;
    private static long generation;
    private static boolean opening;
    private static Object gameConnection;
    private static long viewId;
    private TerminalPassportNavigation() {}
    static void install(com.cinemamod.mcef.MCEFClient client){
        // MCEF multiplexes handlers. CefClient.addLoadHandler silently ignores a second handler.
        client.addLoadHandler(new CefLoadHandlerAdapter(){
            @Override public void onLoadEnd(CefBrowser browser,CefFrame frame,int status){
                if(frame==null || !frame.isMain()) return;
                String loadedUrl=frame.getURL();
                // Container lifetime and native navigation run on the game thread.
                Minecraft.getInstance().execute(()->{
                String value;
                synchronized(TerminalPassportNavigation.class){
                    if(!currentView(browser)) return;
                    Minecraft mc=Minecraft.getInstance();
                    if(mc.player==null || mc.getConnection()!=gameConnection || !(mc.screen instanceof TerminalScreen)){clear();return;}
                    if(payload==null){
                        if(EXCHANGE.equals(loadedUrl)) return; // Duplicate entry completion must not disarm the pending POST.
                        MCEFBrowser current=expected;
                        boolean failed=POST.equals(loadedUrl);
                        clear();
                        if(failed) current.loadURL(ACCOUNT);
                        return;
                    }
                    if(!EXCHANGE.equals(loadedUrl) || status!=200 || System.nanoTime()>expires){
                        clear();return;
                    }
                    value=payload;payload=null;
                }
                byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
                CefPostDataElement element=CefPostDataElement.create();
                element.setToBytes(bytes.length,bytes);
                CefPostData data=CefPostData.create();data.addElement(element);
                CefRequest request=CefRequest.create();
                request.setURL(POST);request.setMethod("POST");request.setPostData(data);
                request.setHeaderMap(Map.of("Content-Type","application/json","Origin","https://mc.muxigame.com","X-Muxi-Terminal-Action","1"));
                synchronized(TerminalPassportNavigation.class){
                    Minecraft mc=Minecraft.getInstance();
                    if(!currentView(browser)
                        || mc.player==null || mc.getConnection()!=gameConnection || !(mc.screen instanceof TerminalScreen)
                        || !EXCHANGE.equals(browser.getURL()) || System.nanoTime()>expires){clear();return;}
                    browser.loadRequest(request);
                }
                });
            }
            @Override public void onLoadError(CefBrowser browser,CefFrame frame,org.cef.handler.CefLoadHandler.ErrorCode code,String message,String failedUrl){
                if(code==org.cef.handler.CefLoadHandler.ErrorCode.ERR_ABORTED) return;
                if(frame==null || !frame.isMain()) return;
                Minecraft.getInstance().execute(()->{
                synchronized(TerminalPassportNavigation.class){
                    if(!currentView(browser)) return;
                    Minecraft mc=Minecraft.getInstance();
                    if(mc.player==null || mc.getConnection()!=gameConnection || !(mc.screen instanceof TerminalScreen)){clear();return;}
                    MCEFBrowser current=expected;clear();current.loadURL(ACCOUNT);
                }
                });
            }
        });
    }
    private static boolean currentView(CefBrowser browser){
        return expected!=null && browser==expected && TerminalBrowserSession.content()==expected
            && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.ACCOUNT
            && TerminalBrowserSession.state().viewId()==viewId;
    }
    static synchronized void clear(){expected=null;payload=null;expires=0;opening=false;gameConnection=null;viewId=0;generation++;}
    static void open(){
        Minecraft mc=Minecraft.getInstance();
        MCEFBrowser browser=TerminalBrowserSession.current();
        final long requestGeneration;
        final String sourceUrl=browser==null?"":browser.getURL();
        final long viewGeneration=TerminalBrowserSession.generation();
        final Object sourceConnection=mc.getConnection();
        synchronized(TerminalPassportNavigation.class){
            if(opening) return;
            clear();opening=true;requestGeneration=generation;
        }
        try {
            Class<?> api=Class.forName("net.muxigame.core.client.TerminalPassportApi");
            Consumer<String> callback=value->mc.execute(()->{
                synchronized(TerminalPassportNavigation.class){
                    if(generation!=requestGeneration) return;
                    opening=false;
                    if(mc.player==null || mc.getConnection()!=sourceConnection || !(mc.screen instanceof TerminalScreen) || browser==null
                        || TerminalBrowserSession.current()!=browser || !sourceUrl.equals(browser.getURL())
                        || TerminalBrowserSession.generation()!=viewGeneration) return;
                }
                if(value==null || value.isEmpty()){TerminalBrowserSession.openAccountView(ACCOUNT);return;}
                MCEFBrowser account=TerminalBrowserSession.openAccountView(EXCHANGE);
                synchronized(TerminalPassportNavigation.class){
                    expected=account;payload=value;gameConnection=sourceConnection;
                    viewId=TerminalBrowserSession.state().viewId();expires=System.nanoTime()+20_000_000_000L;
                }
            });
            api.getMethod("request",Consumer.class).invoke(null,callback);
        }catch(ReflectiveOperationException | LinkageError ignored){clear();TerminalBrowserSession.openAccountView(ACCOUNT);}
    }
}

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
    private TerminalPassportNavigation() {}
    static void install(com.cinemamod.mcef.MCEFClient client){
        // MCEF multiplexes handlers. CefClient.addLoadHandler silently ignores a second handler.
        client.addLoadHandler(new CefLoadHandlerAdapter(){
            @Override public void onLoadEnd(CefBrowser browser,CefFrame frame,int status){
                String value;
                synchronized(TerminalPassportNavigation.class){
                    if(expected==null || !frame.isMain()
                        || browser.getIdentifier()!=expected.getIdentifier()) return;
                    if(Minecraft.getInstance().player==null || Minecraft.getInstance().getConnection()!=gameConnection){clear();return;}
                    if(payload==null){
                        MCEFBrowser current=expected;
                        boolean failed=POST.equals(frame.getURL());
                        clear();
                        if(failed) current.loadURL(ACCOUNT);
                        return;
                    }
                    if(!EXCHANGE.equals(frame.getURL()) || status!=200 || System.nanoTime()>expires){
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
                    if(expected==null || browser.getIdentifier()!=expected.getIdentifier()
                        || mc.player==null || mc.getConnection()!=gameConnection || !(mc.screen instanceof TerminalScreen)
                        || !EXCHANGE.equals(browser.getURL()) || System.nanoTime()>expires){clear();return;}
                    browser.loadRequest(request);
                }
            }
            @Override public void onLoadError(CefBrowser browser,CefFrame frame,org.cef.handler.CefLoadHandler.ErrorCode code,String message,String failedUrl){
                if(code==org.cef.handler.CefLoadHandler.ErrorCode.ERR_ABORTED) return;
                synchronized(TerminalPassportNavigation.class){
                    if(expected==null || !frame.isMain() || browser.getIdentifier()!=expected.getIdentifier()) return;
                    MCEFBrowser current=expected;clear();current.loadURL(ACCOUNT);
                }
            }
        });
    }
    static synchronized void clear(){expected=null;payload=null;expires=0;opening=false;gameConnection=null;generation++;}
    static void open(){
        Minecraft mc=Minecraft.getInstance();
        MCEFBrowser browser=TerminalBrowserSession.current();
        final long requestGeneration;
        final String sourceUrl=browser==null?"":browser.getURL();
        final long viewGeneration=TerminalBrowserSession.generation();
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
                    if(mc.player==null || !(mc.screen instanceof TerminalScreen) || browser==null
                        || TerminalBrowserSession.current()!=browser || !sourceUrl.equals(browser.getURL())
                        || TerminalBrowserSession.generation()!=viewGeneration) return;
                }
                if(value==null || value.isEmpty()){TerminalBrowserSession.openExternal(ACCOUNT);return;}
                MCEFBrowser account=TerminalBrowserSession.openAccountView(EXCHANGE);
                synchronized(TerminalPassportNavigation.class){
                    expected=account;payload=value;gameConnection=mc.getConnection();expires=System.nanoTime()+20_000_000_000L;
                }
            });
            api.getMethod("request",Consumer.class).invoke(null,callback);
        }catch(ReflectiveOperationException | LinkageError ignored){clear();TerminalBrowserSession.openExternal(ACCOUNT);}
    }
}

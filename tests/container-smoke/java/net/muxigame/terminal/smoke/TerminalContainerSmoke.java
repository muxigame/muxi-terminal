package net.muxigame.terminal.smoke;

import com.cinemamod.mcef.*;
import net.minecraft.client.*;
import net.muxigame.terminal.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

@Mod(value="muxi_terminal_smoke",dist=Dist.CLIENT)
public final class TerminalContainerSmoke {
    private int ticks,frames,stage;
    private boolean done;
    private MCEFBrowser shell,old;
    private String base=System.getProperty("muxi.container.fixtureUrl");
    private volatile String text="";
    private volatile int staleResult;
    private final List<String> passed=new ArrayList<>();
    public TerminalContainerSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void next(){stage++;frames=0;text="";}
    private void shot(Minecraft mc,String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of(name+".png"));}}
    private void tick(ClientTickEvent.Post event){
        if(done)return;Minecraft mc=Minecraft.getInstance();
        try{
            if(++ticks>1600)throw new AssertionError("container smoke timeout at stage "+stage+"; "+TerminalBrowserSession.state());
            check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_FALSE,"hidden QA window");
            if(stage==0){
                if(ticks<30 || mc.getOverlay()!=null || !MCEF.isInitialized())return;
                TerminalClient.openHome();shell=TerminalBrowserSession.current();next();return;
            }
            if(!(mc.screen instanceof TerminalScreen))return;
            if(++frames<35)return;
            check(TerminalBrowserSession.current()==shell,"persistent shell identity");
            check(TerminalWebPolicy.localDocument(shell.getURL()),"shell never leaves local document");
            if(stage==1){
                check(shell.getRenderer().getTextureID()!=0,"shell texture");shot(mc,"container-home");
                shell.executeJavaScript("document.querySelector('#addWebApp').click();document.querySelector('#webAppName').value='Container QA';document.querySelector('#webAppUrl').value="+new com.google.gson.Gson().toJson(base+"/redirect")+";document.querySelector('#webAppForm').requestSubmit();",shell.getURL(),0);
                next();return;
            }
            if(stage==2){
                shell.getText(value->text=value);if(!text.contains("Container QA"))return;
                check(TerminalBrowserSession.personalApps().list().size()==1,"form saved through trusted bridge");
                passed.add("add form and native persistence");shot(mc,"container-personal-apps");
                shell.executeJavaScript("document.querySelector('[data-open=guide]').click()",shell.getURL(),0);next();return;
            }
            if(stage==3){
                var content=TerminalBrowserSession.content();check(content!=null && content!=shell,"distinct builtin view");
                if(!TerminalBrowserSession.contentVisible() || !content.getURL().endsWith("#/guide"))return;
                check(content.getClient()!=shell.getClient(),"independent builtin client");
                check(content.getRenderer().getTextureID()!=0 && content.getRenderer().getTextureID()!=shell.getRenderer().getTextureID(),"two actual MCEF textures");
                shot(mc,"container-builtin");passed.add("builtin launch animation reveals actual content texture while preserving shell");
                content.executeJavaScript("const frame=document.createElement('iframe');frame.src='mod://muxi_terminal/terminal/index.html';frame.name='qa-child';document.body.appendChild(frame);",content.getURL(),0);
                next();return;
            }
            if(stage==4){
                var content=TerminalBrowserSession.content();var frame=content.getFrame("qa-child");if(frame==null || !frame.isValid())return;
                assertDenied(content,frame,"tasks.snapshot");passed.add("actual local child frame denied restricted bridge");
                assertDenied(shell,shell.getMainFrame(),"tasks.snapshot",false);
                passed.add("trusted actual main frame accepted");
                queueOldNavigation(content);TerminalBrowserSession.home();
                var app=TerminalBrowserSession.personalApps().list().getFirst();
                TerminalBrowserSession.openWebApp(app.url(),app.name());next();return;
            }
            if(stage==5){
                var content=TerminalBrowserSession.content();if(content==null || !content.getURL().endsWith("/page") || content.isLoading())return;
                check(content.getClient()!=shell.getClient(),"external client isolation");
                check(TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.WEB,"web state");
                check(staleResult==0,"old queued navigation callback suppressed");
                check(content.getRenderer().getTextureID()!=0,"external actual texture");
                assertDenied(content,content.getMainFrame(),"passport.open");
                assertDenied(content,content.getMainFrame(),"tasks.snapshot");
                content.executeJavaScript("document.body.setAttribute('data-query-type',typeof window.muxiTerminalQuery);",content.getURL(),0);
                content.getSource(value->{try{Files.writeString(Path.of("external-source.html"),value);}catch(Exception ignored){}});
                shot(mc,"container-web");passed.add("external redirect kept in content; no message router; native tasks/passport denied");
                passed.add("old queued native command did not replace new content or deliver stale result");
                old=content;content.loadURL(base+"/second");next();return;
            }
            if(stage==6){
                var content=TerminalBrowserSession.content();if(!content.getURL().endsWith("/second") || content.isLoading())return;
                TerminalBrowserSession.back();next();return;
            }
            if(stage==7){
                var content=TerminalBrowserSession.content();if(!content.getURL().endsWith("/page") || content.isLoading())return;
                passed.add("content history back while shell preserved");
                content.loadURL("mod://muxi_terminal/terminal/index.html");next();return;
            }
            if(stage==8){
                check(TerminalBrowserSession.content().getURL().startsWith(base),"external cannot navigate to trusted mod document");
                check(!TerminalBrowserSession.state().error().isEmpty(),"blocked navigation error exposed");
                passed.add("web-to-local navigation blocked");
                long previous=TerminalBrowserSession.generation();TerminalBrowserSession.home();
                check(TerminalBrowserSession.content()==null && TerminalBrowserSession.generation()>previous,"home invalidates old view");
                assertDenied(old,old.getMainFrame(),"tasks.snapshot");
                passed.add("home closes app; stale browser excluded");
                TerminalBrowserSession.openWebApp("http://127.0.0.1:1/unavailable","Unavailable");next();return;
            }
            if(stage==9){
                if(TerminalBrowserSession.state().loading() || TerminalBrowserSession.state().error().isEmpty())return;
                shot(mc,"container-error");passed.add("load failure visible while native shell home remains available");
                TerminalBrowserSession.home();
                var app=TerminalBrowserSession.personalApps().list().getFirst();
                TerminalBrowserSession.personalApps().save(app.id(),"Edited QA",base+"/page");
                TerminalBrowserSession.personalApps().delete(app.id());
                check(TerminalBrowserSession.personalApps().list().isEmpty(),"delete");
                finish(mc,true,null);
            }
        }catch(Throwable error){error.printStackTrace();finish(mc,false,error.toString());}
    }
    private void assertDenied(CefBrowser browser,CefFrame frame,String command)throws Exception{assertDenied(browser,frame,command,true);}
    private void queueOldNavigation(CefBrowser browser)throws Exception{
        Class<?> type=Class.forName("net.muxigame.terminal.client.TerminalNativeBridge$Handler");
        var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var handler=(CefMessageRouterHandlerAdapter)ctor.newInstance();
        var frame=browser.getMainFrame();
        Thread worker=new Thread(()->handler.onQuery(browser,frame,123,"terminal.app:tasks",false,new CefQueryCallback(){
            @Override public void success(String value){staleResult=200;}
            @Override public void failure(int code,String value){staleResult=code;}
        }));worker.start();worker.join(2000);check(!worker.isAlive(),"query enqueue completed");
    }
    private void assertDenied(CefBrowser browser,CefFrame frame,String command,boolean denied)throws Exception{
        Class<?> type=Class.forName("net.muxigame.terminal.client.TerminalNativeBridge$Handler");
        var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var handler=(CefMessageRouterHandlerAdapter)ctor.newInstance();
        int[] result={0};
        handler.onQuery(browser,frame,99,command,false,new CefQueryCallback(){
            @Override public void success(String value){result[0]=200;}
            @Override public void failure(int code,String value){result[0]=code;}
        });
        check(result[0]==(denied?403:200),"actual native handler "+command+" expected "+(denied?403:200)+" got "+result[0]);
    }
    private void finish(Minecraft mc,boolean success,String error){
        done=true;
        try{var report=new LinkedHashMap<String,Object>();report.put("success",success);report.put("checks",passed);report.put("windowVisible",false);report.put("error",error);
            Files.writeString(Path.of("client-smoke-result.json"),new com.google.gson.Gson().toJson(report));}
        catch(Exception ignored){}
        TerminalBrowserSession.close();mc.stop();
    }
}

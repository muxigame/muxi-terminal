package net.muxigame.terminal.smoke;

import com.cinemamod.mcef.*;
import com.google.gson.*;
import net.minecraft.client.*;
import net.muxigame.terminal.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

@Mod(value="muxi_terminal_smoke",dist=Dist.CLIENT)
public final class TerminalIntegrationSmoke {
    private int ticks,frames,stage;
    private boolean done,openShot,closeShot,closingDenied;
    private MCEFBrowser shell;
    private String webId;
    private Consumer<String> oldReply;
    private volatile String text="";
    private final String base=System.getProperty("muxi.container.fixtureUrl");
    private final List<String> passed=new ArrayList<>();
    private final List<Map<String,Object>> motionSamples=new ArrayList<>();
    public TerminalIntegrationSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::frame);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void next(){stage++;frames=0;text="";}
    private void js(String code){shell.executeJavaScript(code,shell.getURL(),0);}
    private void launch(String kind,String id){js("window.terminalLaunch({kind:"+new Gson().toJson(kind)+",id:"+new Gson().toJson(id)+",name:'Integration QA',source:document.querySelector('[data-open=guide]')}).catch(()=>{})");}
    private void shot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Path.of(name+".png"));}}
    private boolean route(String end){var view=TerminalBrowserSession.content();return view!=null && view.getURL().endsWith(end);}
    private boolean ready(){return TerminalBrowserSession.contentVisible() && TerminalBrowserSession.state().rendered() && !TerminalBrowserSession.state().loading() && !TerminalBrowserSession.contentMotion().animating();}
    private void frame(ScreenEvent.Render.Post event){
        if(done || !(event.getScreen() instanceof TerminalScreen) || TerminalBrowserSession.content()==null || !TerminalBrowserSession.contentVisible())return;
        try{
            var motion=TerminalBrowserSession.contentMotion();
            if(motion.animating() && motion.alpha()>0.02 && motion.alpha()<0.98){
                var row=new LinkedHashMap<String,Object>();row.put("phase",motion.closing()?"closing":"opening");row.put("alpha",motion.alpha());row.put("scale",motion.scale());row.put("kind",TerminalBrowserSession.state().kind());row.put("viewId",TerminalBrowserSession.state().viewId());motionSamples.add(row);
                if(motion.alpha()>0.25 && motion.alpha()<0.75){
                    if(!motion.closing() && !openShot){openShot=true;shot("integrated-native-opening-mid");}
                    if(motion.closing() && !closeShot){closeShot=true;shot("integrated-native-closing-mid");}
                }
                if(motion.closing() && !closingDenied && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN){
                    var view=TerminalBrowserSession.content();denied(view,view.getMainFrame(),"tasks.snapshot");closingDenied=true;
                    check(TerminalBrowserSession.activeBrowser()==shell,"closing content must relinquish input");
                    var top=TerminalScreen.class.getDeclaredField("top");top.setAccessible(true);
                    var y=TerminalScreen.class.getDeclaredMethod("browserY",double.class);y.setAccessible(true);
                    check((int)y.invoke(event.getScreen(),top.getInt(event.getScreen())+60.0)==(int)Math.round(60*Minecraft.getInstance().getWindow().getGuiScale()),"closing input uses shell coordinates");
                }
            }
        }catch(Throwable error){error.printStackTrace();finish(false,error.toString());}
    }
    private void tick(ClientTickEvent.Post event){
        if(done)return;Minecraft mc=Minecraft.getInstance();
        try{
            if(ticks%100==0)Files.writeString(Path.of("runtime-progress.json"),new Gson().toJson(Map.of("stage",stage,"frames",frames,"viewState",TerminalBrowserSession.state())));
            if(++ticks>2000)throw new AssertionError("integrated smoke timeout stage="+stage+" state="+TerminalBrowserSession.state()+" syntheticPosts="+SSOFixture.posts+" requests="+SSOFixture.requests+" pending="+(SSOFixture.pending!=null));
            check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE,"visible QA window");
            check(mc.player==null,"synthetic identity must be scoped to adapter tasks only");
            if(stage==0){if(ticks<30 || mc.getOverlay()!=null || !MCEF.isInitialized())return;TerminalClient.openHome();shell=TerminalBrowserSession.current();next();return;}
            if(!(mc.screen instanceof TerminalScreen))return;frames++;
            check(TerminalBrowserSession.current()==shell,"persistent shell identity");
            if(stage==1){if(frames<35 || shell.isLoading())return;shot("integrated-home");js("document.querySelector('[data-open=guide]').click();document.querySelector('[data-open=guide]').click();document.querySelector('[data-open=tasks]').click()");next();return;}
            if(stage==2){
                if(frames<20 || !ready() || !route("#/tasks"))return;
                check(openShot,"actual opening intermediate rendered frame captured");
                passed.add("rapid duplicate/alternate launch shows latest actual child with native alpha/scale animation");shot("integrated-tasks-ready");TerminalBrowserSession.requestHome();next();return;
            }
            if(stage==3){
                if(frames<12 || TerminalBrowserSession.content()!=null)return;
                check(closeShot && closingDenied,"native closing pixels retained while builtin bridge immediately denied");
                passed.add("actual native closing intermediate frame; bridge revoked before fade completion");launch("account","passport");next();return;
            }
            if(stage==4){
                if(SSOFixture.pending==null)return;check(SSOFixture.requestCalls==1,"one synthetic ticket request");SSOFixture.deliver();next();return;
            }
            if(stage==5){
                if(frames<20 || !route("account.html") || !ready())return;
                var view=TerminalBrowserSession.content();view.getText(value->text=value);if(!text.contains("QA account signed in"))return;
                check(SSOFixture.posts==1,"exactly one actual CEF POST");check(SSOFixture.postedBody.equals(SSOFixture.BODY),"payload only in native POST body");
                check(SSOFixture.postedMethod.equals("POST") && SSOFixture.postedOrigin.equals("https://mc.muxigame.com") && SSOFixture.postedAction.equals("1"),"fixed native method/origin/action");
                check(!view.getURL().contains("synthetic") && TerminalWebPolicy.localDocument(shell.getURL()),"no URL credential or shell navigation");
                denied(view,view.getMainFrame(),"passport.open");denied(view,view.getMainFrame(),"tasks.snapshot");
                passed.add("real CEF synthetic one-use native POST/redirect renders account child; shell persistent; account bridge denied");shot("integrated-sso-account");TerminalBrowserSession.requestHome();next();return;
            }
            if(stage==6){if(frames<12 || TerminalBrowserSession.content()!=null)return;launch("account","passport");next();return;}
            if(stage==7){if(SSOFixture.pending==null)return;oldReply=SSOFixture.pending;SSOFixture.pending=null;TerminalBrowserSession.requestHome();next();return;}
            if(stage==8){if(frames<12 || TerminalBrowserSession.content()!=null)return;oldReply.accept(SSOFixture.BODY);next();return;}
            if(stage==9){
                if(frames<12)return;check(TerminalBrowserSession.content()==null && SSOFixture.posts==1,"cancelled late ticket cannot open or POST");
                passed.add("cancelled actual adapter async ticket remains home and cannot POST");SSOFixture.holdEntry=true;launch("account","passport");next();return;
            }
            if(stage==10){if(SSOFixture.pending==null)return;SSOFixture.deliver();next();return;}
            if(stage==11){if(frames<4 || TerminalBrowserSession.content()==null || !TerminalBrowserSession.state().loading())return;TerminalBrowserSession.requestHome();next();return;}
            if(stage==12){
                if(frames<35)return;check(TerminalBrowserSession.content()==null && SSOFixture.posts==1,"cancelled delayed actual CEF entry cannot consume ticket");
                passed.add("return during synthetic HTTPS entry load blocks late native POST");SSOFixture.holdEntry=false;
                var app=TerminalBrowserSession.personalApps().save("","External QA",base+"/redirect");webId=app.id();launch("web",webId);next();return;
            }
            if(stage==13){
                if(frames<20 || !route("/page") || !ready())return;
                var view=TerminalBrowserSession.content();denied(view,view.getMainFrame(),"passport.open");denied(view,view.getMainFrame(),"tasks.snapshot");
                boolean child=false;for(long id:view.getFrameIdentifiers()){var frame=view.getFrame(id);if(frame!=null && frame.isValid() && !frame.isMain()){denied(view,frame,"tasks.snapshot");child=true;}}
                check(child,"actual external iframe tested");passed.add("external redirect main/iframe remain denied by actual bridge; no privileged fixture response");shot("integrated-external");
                view.loadURL(TerminalBrowserSession.HOME_URL);next();return;
            }
            if(stage==14){
                if(frames<8)return;check(TerminalBrowserSession.content().getURL().startsWith(base) && !TerminalBrowserSession.state().error().isEmpty(),"web-to-local blocked");
                js("window.terminalReturnHome();window.terminalLaunch({kind:'builtin',id:'guide',name:'Latest guide',source:document.querySelector('[data-open=guide]')}).catch(()=>{})");next();return;
            }
            if(stage==15){
                if(frames<25 || !route("#/guide") || !ready())return;
                check(SSOFixture.posts==1,"all cancellation/frame scenarios kept one POST total");passed.add("open during close preserves newest native child; blocked web-to-local target never gains bridge");
                TerminalBrowserSession.personalApps().delete(webId);finish(true,null);
            }
        }catch(Throwable error){error.printStackTrace();finish(false,error.toString());}
    }
    private static void denied(CefBrowser browser,CefFrame frame,String request)throws Exception{
        var type=Class.forName("net.muxigame.terminal.client.TerminalNativeBridge$Handler");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        var handler=(CefMessageRouterHandlerAdapter)constructor.newInstance();int[] result={0};handler.onQuery(browser,frame,123,request,false,new CefQueryCallback(){
            @Override public void success(String value){result[0]=200;}@Override public void failure(int code,String value){result[0]=code;}
        });if(result[0]!=403)throw new AssertionError("Actual bridge allowed "+request+" result="+result[0]);
    }
    private void finish(boolean success,String error){
        if(done)return;done=true;
        try{var report=new LinkedHashMap<String,Object>();report.put("success",success);report.put("checks",passed);report.put("motionSamples",motionSamples);report.put("syntheticPostCount",SSOFixture.posts);report.put("syntheticRequestCount",SSOFixture.requestCalls);report.put("windowVisible",GLFW.glfwGetWindowAttrib(Minecraft.getInstance().getWindow().getWindow(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE);report.put("gpuRenderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));report.put("productionSSO",false);report.put("error",error);report.put("stage",stage);Files.writeString(Path.of("client-smoke-result.json"),new Gson().toJson(report));}
        catch(Exception ignored){}TerminalBrowserSession.close();Minecraft.getInstance().stop();
    }
}

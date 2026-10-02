package net.muxigame.terminal.smoke;

import com.cinemamod.mcef.*;
import com.google.gson.*;
import net.minecraft.client.*;
import net.muxigame.terminal.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** QA-only hidden-client fixture. Does not change production Java/navigation. */
@Mod(value="muxi_terminal_smoke",dist=Dist.CLIENT)
public final class TerminalContainerSmoke {
    private int ticks,frames,stage;
    private boolean done;
    private MCEFBrowser shell;
    private String id;
    private long failedView;
    private volatile String text="";
    private final String base=System.getProperty("muxi.container.fixtureUrl");
    private final List<String> passed=new ArrayList<>();
    public TerminalContainerSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void next(){stage++;frames=0;text="";}
    private void js(String code){shell.executeJavaScript(code,shell.getURL(),0);}
    private void shot(Minecraft mc,String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of(name+".png"));}}
    private String app(String kind,String value,String title){return "window.terminalLaunch({kind:"+new Gson().toJson(kind)+",id:"+new Gson().toJson(value)+",name:"+new Gson().toJson(title)+",source:document.querySelector('[data-open=guide]')}).catch(()=>{})";}
    private JsonObject visual(){
        js("(()=>{let q=document.getElementById('qa-state');if(!q){q=document.createElement('pre');q.id='qa-state';q.style='font-size:1px;position:absolute;width:1px;height:1px;overflow:hidden;bottom:0';document.body.appendChild(q);}q.textContent='QA_STATE '+JSON.stringify(window.terminalTransition?.getState());})()");
        shell.getText(value->text=value);
        int marker=text.indexOf("QA_STATE ");if(marker<0)return null;
        try{return JsonParser.parseString(text.substring(marker+9).split("\\R",2)[0].trim()).getAsJsonObject();}catch(Exception e){return null;}
    }
    private boolean visualState(String expected){var visual=visual();return visual!=null && visual.has("state") && expected.equals(visual.get("state").getAsString());}
    private boolean contentRoute(String route){var content=TerminalBrowserSession.content();return content!=null && content.getURL().endsWith(route);}
    private void tick(ClientTickEvent.Post event){
        if(done)return;Minecraft mc=Minecraft.getInstance();
        try{
            if(++ticks>1800)throw new AssertionError("animation container timeout at stage "+stage+"; "+TerminalBrowserSession.state()+"; "+text);
            check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_FALSE,"hidden QA window");
            if(stage==0){if(ticks<30 || mc.getOverlay()!=null || !MCEF.isInitialized())return;TerminalClient.openHome();shell=TerminalBrowserSession.current();next();return;}
            if(!(mc.screen instanceof TerminalScreen))return;
            frames++;
            check(TerminalBrowserSession.current()==shell,"persistent shell identity");
            if(stage==1 && !TerminalWebPolicy.localDocument(shell.getURL()))return;
            check(TerminalWebPolicy.localDocument(shell.getURL()),"shell never navigates externally");
            if(stage==1){
                if(frames<35 || shell.isLoading())return;
                shot(mc,"animation-native-home");
                js(app("builtin","guide","Guide")+";"+app("builtin","guide","Guide")+";"+app("builtin","tasks","Tasks"));next();return;
            }
            if(stage==2){
                if(frames<25 || !TerminalBrowserSession.contentVisible() || !contentRoute("#/tasks"))return;
                var content=TerminalBrowserSession.content();check(content!=shell,"separate app browser");
                check(content.getRenderer().getTextureID()!=0 && content.getRenderer().getTextureID()!=shell.getRenderer().getTextureID(),"independent actual textures");
                if(!visualState("ready"))return;
                shot(mc,"animation-native-rapid-latest");passed.add("actual CEF rapid duplicate/alternate clicks reveal latest tasks view and preserve shell");
                TerminalBrowserSession.requestHome();next();return;
            }
            if(stage==3){
                if(frames==2)shot(mc,"animation-native-return-frame");
                if(frames<10 || TerminalBrowserSession.content()!=null || !visualState("idle"))return;
                passed.add("native home uses return animation and closes child while retaining shell");
                var saved=TerminalBrowserSession.personalApps().save("","Unavailable","http://127.0.0.1:1/unavailable");id=saved.id();
                js(app("web",id,"Unavailable"));next();return;
            }
            if(stage==4){
                var state=TerminalBrowserSession.state();if(frames<20 || state.loading() || state.error().isEmpty() || !visualState("error"))return;
                check(!TerminalBrowserSession.contentVisible(),"failure child remains hidden below recovery overlay");failedView=state.viewId();
                shot(mc,"animation-native-error");passed.add("actual CEF load failure hides child and exposes animation recovery UI");
                TerminalBrowserSession.personalApps().save(id,"Recovered",base+"/redirect");js("document.querySelector('.mt-launch-retry').click();document.querySelector('.mt-launch-retry').click();");next();return;
            }
            if(stage==5){
                var state=TerminalBrowserSession.state();if(frames<25 || !TerminalBrowserSession.contentVisible() || !state.url().endsWith("/page") || !visualState("ready"))return;
                check(state.viewId()==failedView+1,"retry double-click launches one fresh native view");
                shot(mc,"animation-native-retry-content");passed.add("recovery retry double-click produces one fresh native child and rendered content");
                TerminalBrowserSession.requestHome();next();return;
            }
            if(stage==6){
                if(frames<12 || TerminalBrowserSession.content()!=null || !visualState("idle"))return;
                TerminalBrowserSession.personalApps().save(id,"Slow",base+"/slow");js(app("web",id,"Slow"));next();return;
            }
            if(stage==7){
                if(frames<6 || TerminalBrowserSession.content()==null || !TerminalBrowserSession.state().loading())return;
                shot(mc,"animation-native-loading");TerminalBrowserSession.requestHome();next();return;
            }
            if(stage==8){
                if(frames<75 || TerminalBrowserSession.content()!=null || !visualState("idle"))return;
                passed.add("return during real pending HTTP load stays home after delayed server response");
                js(app("builtin","guide","Guide"));next();return;
            }
            if(stage==9){
                if(frames<25 || !TerminalBrowserSession.contentVisible() || !visualState("ready"))return;
                js("window.terminalReturnHome();"+app("builtin","tasks","Tasks"));next();return;
            }
            if(stage==10){
                if(frames<25 || !TerminalBrowserSession.contentVisible() || !contentRoute("#/tasks") || !visualState("ready"))return;
                shot(mc,"animation-native-open-during-return");passed.add("new native app intent during return supersedes older home acknowledgement");
                TerminalBrowserSession.home();next();return;
            }
            if(stage==11){
                if(frames<15 || !visualState("idle"))return;
                passed.add("direct native home invalidates adapter launch state");
                TerminalBrowserSession.personalApps().delete(id);
                check(TerminalBrowserSession.current()==shell,"final persistent shell");finish(mc,true,null);
            }
        }catch(Throwable error){error.printStackTrace();finish(mc,false,error.toString());}
    }
    private void finish(Minecraft mc,boolean success,String error){
        done=true;
        try{var report=new LinkedHashMap<String,Object>();report.put("success",success);report.put("checks",passed);report.put("windowVisible",false);report.put("error",error);report.put("stage",stage);
            Files.writeString(Path.of("client-smoke-result.json"),new Gson().toJson(report));}
        catch(Exception ignored){}
        TerminalBrowserSession.close();mc.stop();
    }
}

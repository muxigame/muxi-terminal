package net.muxigame.terminal.qa;

import com.cinemamod.mcef.MCEF;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.muxigame.terminal.MuxiTerminal;
import net.muxigame.terminal.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import org.cef.browser.CefBrowser;
import java.nio.file.*;
import java.util.*;

/** Isolated QA only: real released pack, real CEF/native widgets, real OpenGL world and real PNGs. */
@Mod(value="camera_native_qa",dist=Dist.CLIENT)
public final class NativeCameraQA {
    private Minecraft mc;
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject report=new JsonObject();
    private int ticks,stage,age,checks,shot,domSeenAt;
    private boolean starting,finished,requested,disconnected,focusLost,f1ShotRequested,f1ShotSaved,inTick;
    private String captureName="",lastSaved="",domAction="";
    private volatile JsonObject dom;
    private TerminalScreen returned;
    private Object shell,content;
    private long generation;
    private CameraType originalCamera;
    private boolean originalHud;
    private String shellUrl;
    private final JsonArray photos=new JsonArray();
    private final JsonArray renderChecks=new JsonArray();
    private long sceneReadySince;
    private int nativePlayerPre,nativePlayerPost;
    private final long start=System.nanoTime();
    private static final String[] MODES={"forward","selfie","forward","selfie","forward","selfie"};
    public NativeCameraQA(){
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::frame);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,(RenderPlayerEvent.Pre e)->{if(Minecraft.getInstance().screen instanceof TerminalCameraScreen && e.getEntity()==Minecraft.getInstance().player)nativePlayerPre++;});
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post e)->{if(Minecraft.getInstance().screen instanceof TerminalCameraScreen && e.getEntity()==Minecraft.getInstance().player)nativePlayerPost++;});
    }
    private void check(boolean value,String text){checks++;if(!value)throw new IllegalStateException(text);}
    private void next(){stage++;age=0;requested=false;dom=null;domAction="";domSeenAt=0;writeProgress();}
    private void writeProgress(){
        try{var p=new JsonObject();p.addProperty("stage",stage);p.addProperty("age",age);p.addProperty("shot",shot);
            p.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());p.addProperty("windowActive",mc.isWindowActive());
            p.addProperty("focusCoordinationReady",stage==9 && f1ShotSaved);p.addProperty("observedFocusLoss",focusLost);
            atomic("camera-progress.json",p);}catch(Exception e){System.out.println("QA_PROGRESS_WRITE_UNAVAILABLE="+e);}
    }
    // Same bounded Windows replacement retry as task14's verified progress-sharing repair.
    private void atomic(String name,JsonObject value)throws Exception{
        var target=Path.of(name);var temp=Path.of(name+".tmp");
        Files.writeString(temp,gson.toJson(value));
        for(int attempt=0;;attempt++){
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);return;}
            catch(FileSystemException busy){if(attempt>=19)throw busy;Thread.sleep(10);}
        }
    }
    private void capture(String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of(name));}}
    private void frame(RenderFrameEvent.Post event){
        if(finished || captureName.isEmpty())return;
        try{
            if(captureName.startsWith("shot-")){
                check(mc.screen instanceof TerminalCameraScreen,"native Screen expected during photo/UI render");
                check(mc.options.hideGui,"ordinary HUD must be suppressed during native render");
                check(mc.options.getCameraType()==("selfie".equals(TerminalCamera.state().mode())?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON),"actual render lens differs");
                var camera=mc.gameRenderer.getMainCamera();var info=new JsonObject();info.addProperty("frame",captureName);info.addProperty("detached",camera.isDetached());info.addProperty("cameraEntityIsPlayer",camera.getEntity()==mc.player);info.addProperty("playerInvisible",mc.player.isInvisible());info.addProperty("playerY",mc.player.getY());info.addProperty("cameraY",camera.getPosition().y);info.addProperty("renderedSections",mc.levelRenderer.countRenderedSections());info.addProperty("nativePlayerPre",nativePlayerPre);info.addProperty("nativePlayerPost",nativePlayerPost);renderChecks.add(info);
            }else check(mc.screen instanceof TerminalScreen,"terminal Screen expected for return/album screenshot");
            capture(captureName);captureName="";
        }catch(Exception e){fail(e);}
    }
    private void saveContext(){
        returned=(TerminalScreen)mc.screen;shell=TerminalBrowserSession.current();content=TerminalBrowserSession.content();generation=TerminalBrowserSession.generation();
        shellUrl=TerminalBrowserSession.current().getURL();originalCamera=mc.options.getCameraType();originalHud=mc.options.hideGui;
    }
    private void sameContext(){
        check(mc.screen==returned,"close must return exact preceding terminal Screen");
        check(TerminalBrowserSession.generation()==generation && TerminalBrowserSession.current()==shell && TerminalBrowserSession.content()==content,"terminal browser/navigation identity changed");
        check(TerminalBrowserSession.current().getURL().equals(shellUrl),"terminal page URL changed");
        check(mc.options.getCameraType()==originalCamera && mc.options.hideGui==originalHud,"HUD/camera options did not restore");
        check(!TerminalCamera.state().active(),"native camera session retained after close");
    }
    private Button button(int index){return (Button)mc.screen.children().stream().filter(c->c instanceof Button).toList().get(index);}
    private void tick(ClientTickEvent.Post event){
        if(finished || inTick)return;
        if(mc==null){mc=Minecraft.getInstance();if(mc==null)return;}
        inTick=true;
        try{
            ticks++;age++;if(System.nanoTime()-start>900_000_000_000L)throw new IllegalStateException("Bounded native camera QA deadline, stage="+stage);
            if(ticks%20==0)writeProgress();
            if(!MCEF.isInitialized())return;
            if(!starting){
                if(mc.getOverlay()!=null || mc.screen==null)return;starting=true;
                mc.options.renderDistance().set(3);mc.options.bobView().set(false);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.createWorldOpenFlows().createFreshLevel("task22-native-camera",new LevelSettings("Isolated task22 native camera",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(987654321L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            if(mc.player==null && ticks%40==0 && (mc.screen instanceof ConfirmScreen || mc.screen instanceof BackupConfirmScreen)){
                for(var c:mc.screen.children())if(c instanceof Button b && b.active){b.onPress();break;}
            }
            if(stage==11){
                if(!disconnected || age<50)return;capture("title-after-disconnect.png");
                check(!TerminalCamera.state().active(),"camera persisted after disconnect");
                check(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive() && t.getName().equals("muxi-terminal-camera-io")),"camera IO worker remained alive after operations/exit");
                check(mc.options.getCameraType()==originalCamera && mc.options.hideGui==originalHud,"disconnect did not restore options");
                report.addProperty("completed",true);report.addProperty("checks",checks);report.add("photos",photos);report.add("renderChecks",renderChecks);
                report.addProperty("realFocusLossObserved",focusLost);report.addProperty("syntheticImages",false);report.addProperty("productionChanged",false);
                report.addProperty("windowFocusChangedBySampler",false);report.addProperty("inputPath","actual CEF DOM card click and native MC button/key handlers; no OS input injection");
                atomic("camera-result.json",report);finished=true;mc.stop();return;
            }
            if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
            if(stage==0){
                // Full pack first-login sync/protection can finish well after a LocalPlayer exists.
                // Wait for the actual scene; never reposition a player or modify server entities.
                if(sceneReadySince==0)sceneReadySince=System.nanoTime();
                if(System.nanoTime()-sceneReadySince<40_000_000_000L || !mc.level.hasChunkAt(mc.player.blockPosition()) || mc.levelRenderer.countRenderedSections()==0 || mc.player.isInvisible())return;
                report.addProperty("sceneReadyBeforeCamera",true);report.addProperty("sceneRenderedSections",mc.levelRenderer.countRenderedSections());report.addProperty("qaPlayerRepositioned",false);report.addProperty("serverEntitiesModifiedByCamera",false);
                mc.getWindow().setTitle("Muxi task22 native camera QA - isolated 131");
                mc.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MuxiTerminal.PLAYER_TERMINAL.get()));
                mc.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                TerminalClient.openHome();next();return;
            }
            if(stage==1){
                if(!(mc.screen instanceof TerminalScreen) || age<100 || !mc.isWindowActive())return;
                report.addProperty("gpuRenderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
                JsonArray mods=new JsonArray();for(var mod:ModList.get().getMods())mods.add(mod.getModId()+":"+mod.getVersion());report.add("mods",mods);
                try{var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");report.addProperty("shaderPackInUse",(Boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null)));}
                catch(ReflectiveOperationException unavailable){report.addProperty("shaderPackInUseProbe",unavailable.toString());}
                saveContext();capture("terminal-before-native-camera.png");
                TerminalBrowserSession.current().executeJavaScript("document.getElementById('cameraApp').click()",TerminalBrowserSession.HOME_URL,0);next();return;
            }
            if(stage==2){
                if(!(mc.screen instanceof TerminalCameraScreen)){if(age>200)throw new IllegalStateException("Actual terminal card did not enter native camera");return;}
                check(TerminalBrowserSession.current()==shell && TerminalBrowserSession.content()==content && TerminalBrowserSession.generation()==generation,"camera entry navigated/closed terminal browser");
                check(TerminalBrowserSession.current().getURL().equals(shellUrl),"camera entry changed page URL");next();return;
            }
            if(stage==3){
                if(age<20 || !mc.isWindowActive())return;
                if(shot>=2){mc.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHIELD));mc.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(MuxiTerminal.PLAYER_TERMINAL.get()));}
                if(shot>=4){mc.player.setMainArm(HumanoidArm.LEFT);mc.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MuxiTerminal.PLAYER_TERMINAL.get()));mc.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));}
                if(!TerminalCamera.state().mode().equals(MODES[shot])){
                    if(shot%2==0)button(1).onPress();else mc.screen.keyPressed(70,0,0);
                }
                check(TerminalCamera.state().mode().equals(MODES[shot]),"native button/F did not switch lens");
                lastSaved=TerminalCamera.state().saved();captureName=String.format("shot-%02d-%s-ui.png",shot,MODES[shot]);
                if(shot%2==0)button(0).onPress();else mc.screen.keyPressed(32,0,0);
                next();return;
            }
            if(stage==4){
                var state=TerminalCamera.state();if(state.busy() || state.saved().equals(lastSaved)){if(age>200)throw new IllegalStateException("Actual shutter produced no PNG: "+state.error());return;}
                check(captureName.isEmpty(),"native shutter render/UI frame not sampled");
                var source=mc.gameDirectory.toPath().resolve("screenshots/muxi-terminal").resolve(mc.getUser().getProfileId().toString()).resolve(state.saved());
                check(Files.isRegularFile(source),"native PNG missing in shared photo library");
                String name=String.format("shot-%02d-%s-photo.png",shot,MODES[shot]);Files.copy(source,Path.of(name));
                JsonObject photo=new JsonObject();photo.addProperty("id",state.saved());photo.addProperty("mode",MODES[shot]);photo.addProperty("artifact",name);
                photo.addProperty("holding",shot<2?"main-empty":shot<4?"off-shield":"main-shield-left-arm");photo.addProperty("bytes",Files.size(source));photos.add(photo);
                shot++;if(shot<MODES.length){stage=3;age=0;return;}
                mc.screen.keyPressed(256,0,0);sameContext();captureName="terminal-after-close.png";next();return;
            }
            if(stage==5){
                if(!captureName.isEmpty())return;
                if(!requested){TerminalBrowserSession.current().executeJavaScript("document.getElementById('albumApp').click()",TerminalBrowserSession.current().getURL(),0);requested=true;return;}
                var b=TerminalBrowserSession.content();if(b==null || !b.getURL().endsWith("#/album") || TerminalBrowserSession.state().loading() || age<100)return;
                if(domAction.isEmpty()){
                    String ids=gson.toJson(photos);runDOM(b,"const photos="+ids+";const rows=await q('album.photos');if(!photos.every(p=>rows.some(r=>r.id===p.id)))throw Error('Actual camera photos missing from shared album');const id=photos[0].id;const tile=[...document.querySelectorAll('.album-tile')].find(b=>b.title===id);if(!tile)throw Error('Actual album tile absent');tile.click();await waitFor(()=>!document.getElementById('albumViewer').hidden&&document.getElementById('albumImage').src.startsWith('data:image/png;base64,')&&document.getElementById('albumImage').complete);return {rows,viewer:true,photoName:document.getElementById('albumPhotoName').textContent,thumbnailLoaded:[...document.querySelectorAll('.album-tile img')].some(i=>i.complete&&i.naturalWidth>0)};","album-open");return;
                }
                readDOM(b);if(dom==null)return;if(domSeenAt==0){domSeenAt=ticks;return;}if(ticks-domSeenAt<12)return;
                check(dom.get("ok").getAsBoolean(),"real CEF album viewer failed: "+dom);report.add("albumOpen",dom);captureName="album-real-photo-viewer.png";next();return;
            }
            if(stage==6){
                if(!captureName.isEmpty())return;
                saveContext();TerminalBrowserSession.content().executeJavaScript("document.getElementById('albumCamera').click()",TerminalBrowserSession.content().getURL(),0);next();return;
            }
            if(stage==7){
                if(!(mc.screen instanceof TerminalCameraScreen)){if(age>160)throw new IllegalStateException("Album 'go take photo' did not enter native Screen");return;}
                if(age<40)return;button(2).onPress();sameContext();captureName="album-same-viewer-after-close.png";next();return;
            }
            if(stage==8){
                if(!captureName.isEmpty())return;
                var b=TerminalBrowserSession.content();
                if(domAction.isEmpty()){
                    runDOM(b,"if(document.getElementById('albumViewer').hidden||!document.getElementById('albumImage').complete)throw Error('Album viewer state lost on return');const photo="+gson.toJson(photos.get(0).getAsJsonObject().get("id"))+";document.getElementById('albumDelete').click();await waitFor(()=>!document.getElementById('albumConfirm').hidden);document.getElementById('albumCancelDelete').click();await waitFor(()=>document.getElementById('albumConfirm').hidden);if(!(await q('album.photos')).some(p=>p.id===photo))throw Error('Cancel removed photo');document.getElementById('albumDelete').click();await waitFor(()=>!document.getElementById('albumConfirm').hidden);document.getElementById('albumConfirmDelete').click();await waitFor(()=>document.getElementById('albumConfirm').hidden);if((await q('album.photos')).some(p=>p.id===photo)||!(await q('album.recycled')).some(p=>p.id===photo))throw Error('Confirm did not recycle QA photo');await q('album.restore:'+photo);return {cancelPreserved:true,confirmedRecycle:true,restore:(await q('album.photos')).some(p=>p.id===photo)};","album-recycle-restore");return;
                }
                readDOM(b);if(dom==null)return;check(dom.get("ok").getAsBoolean(),"actual album recycle/restore failed: "+dom);report.add("albumRecycleRestore",dom);
                mc.options.hideGui=true;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);saveContext();TerminalCamera.begin();next();return;
            }
            if(stage==9){
                if(!f1ShotRequested){
                    if(age<20 || !mc.isWindowActive())return;
                    captureName="shot-06-forward-ui.png";button(0).onPress();f1ShotRequested=true;return;
                }
                if(!f1ShotSaved){
                    var state=TerminalCamera.state();if(state.busy() || state.saved().isEmpty())return;
                    var source=mc.gameDirectory.toPath().resolve("screenshots/muxi-terminal").resolve(mc.getUser().getProfileId().toString()).resolve(state.saved());
                    check(Files.isRegularFile(source),"native capture did not save with F1 initially hiding HUD");
                    Files.copy(source,Path.of("shot-06-forward-photo.png"));var photo=new JsonObject();photo.addProperty("id",state.saved());photo.addProperty("mode","forward");photo.addProperty("artifact","shot-06-forward-photo.png");photo.addProperty("holding","F1-hidden-HUD-entry");photo.addProperty("bytes",Files.size(source));photos.add(photo);
                    f1ShotSaved=true;lastSaved=state.saved();writeProgress();return;
                }
                // Physical focus loss/recovery is coordinated by the desktop owner; this sampler never steals another window.
                if(!mc.isWindowActive() && !focusLost){
                    focusLost=true;mc.screen.keyPressed(32,0,0);
                    check(!TerminalCamera.state().busy() && TerminalCamera.state().saved().equals(lastSaved),"physical focus loss allowed a shutter");
                }
                if(!focusLost || !mc.isWindowActive() || age<40)return;
                check(TerminalCamera.state().active(),"focus loss closed native mode");
                check(!TerminalCamera.state().busy(),"unexpected shutter on focus regain");
                mc.screen.keyPressed(256,0,0);sameContext();check(mc.options.hideGui,"F1 hidden HUD state was not preserved");next();return;
            }
            if(stage==10){
                TerminalCamera.begin();TerminalCamera.shutter();mc.screen.keyPressed(256,0,0);sameContext();
                check(!TerminalCamera.state().busy(),"cancel before world readback leaked shutter");
                TerminalCamera.begin();originalCamera=mc.options.getCameraType();originalHud=mc.options.hideGui;
                mc.tell(()->{try{if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());disconnected=true;}catch(Throwable e){fail(e);}});next();return;
            }
        }catch(Throwable e){fail(e);}finally{inTick=false;}
    }
    private void runDOM(CefBrowser b,String action,String name){
        domAction=name;dom=null;
        String js="(async()=>{document.documentElement.removeAttribute('data-camera-qa');const wait=ms=>new Promise(r=>setTimeout(r,ms));const waitFor=async f=>{for(let i=0;i<100;i++){if(f())return;await wait(100);}throw Error('UI wait deadline');};const once=request=>new Promise((resolve,reject)=>muxiTerminalQuery({request,persistent:false,onSuccess:s=>{try{resolve(JSON.parse(s));}catch(e){reject(e);}},onFailure:(c,m)=>reject(Error(c+':'+m))}));const q=async request=>{for(let i=0;i<30;i++){try{return await once(request);}catch(e){if(!String(e).startsWith('Error: 429:')||i===29)throw e;await wait(80);}}};let result;try{result={ok:true,value:await(async()=>{"+action+"})()};}catch(e){result={ok:false,error:String(e)};}result.action="+gson.toJson(name)+";document.documentElement.setAttribute('data-camera-qa',btoa(unescape(encodeURIComponent(JSON.stringify(result)))));})()";
        b.executeJavaScript(js,b.getURL(),0);
    }
    private void readDOM(CefBrowser b){
        if(age%5!=0)return;String action=domAction;
        b.getSource(source->{if(!action.equals(domAction))return;var m=java.util.regex.Pattern.compile("data-camera-qa=\\\"([^\\\"]+)\\\"").matcher(source);
            if(m.find())try{var result=JsonParser.parseString(new String(Base64.getDecoder().decode(m.group(1)),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();if(action.equals(result.get("action").getAsString()))dom=result;}catch(Exception ignored){}
        });
    }
    private void fail(Throwable e){
        if(finished)return;finished=true;e.printStackTrace();
        try{TerminalCamera.stop();report.addProperty("completed",false);report.addProperty("stage",stage);report.addProperty("age",age);report.addProperty("error",e.toString());report.addProperty("checks",checks);report.add("photos",photos);report.add("renderChecks",renderChecks);atomic("camera-result.json",report);}catch(Exception writeFailure){writeFailure.printStackTrace();}
        mc.stop();
    }
}

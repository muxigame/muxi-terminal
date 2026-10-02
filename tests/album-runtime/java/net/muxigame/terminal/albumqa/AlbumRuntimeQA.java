package net.muxigame.terminal.albumqa;

import com.cinemamod.mcef.MCEF;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.muxigame.terminal.MuxiTerminal;
import net.muxigame.terminal.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.management.*;

/** Real native KeyboardHandler F2, actual product CEF routes and pixels; own private world/files only. */
@Mod(value="muxi_album_qa",dist=Dist.CLIENT)
public final class AlbumRuntimeQA {
    private int ticks,stage,frames,cycles,domAt;
    private boolean starting,requested,finished,closing;
    private volatile JsonObject dom;
    private String historical,newF2,camera;
    private byte[] originalF2Digest;
    private Path screenshots;
    private Set<String> before;
    private final Gson json=new Gson();
    private final JsonArray checks=new JsonArray(),memory=new JsonArray(),domSamples=new JsonArray();
    private final JsonObject report=new JsonObject();
    private final int cycleLimit=Integer.getInteger("album.cycles",8);
    public AlbumRuntimeQA(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
    private void write() throws Exception {
        report.add("checks",checks);report.add("memorySamples",memory);report.add("domSamples",domSamples);
        report.addProperty("stage",stage);Files.writeString(Path.of("album-runtime-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private Set<String> names() throws Exception {if(!Files.isDirectory(screenshots))return Set.of();try(var paths=Files.list(screenshots)){return new HashSet<>(paths.filter(Files::isRegularFile).map(p->p.getFileName().toString()).toList());}}
    private String added() throws Exception {var now=names();now.removeAll(before);return now.stream().filter(n->n.endsWith(".png")).findFirst().orElse(null);}
    private void f2(){var mc=Minecraft.getInstance();mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),GLFW.GLFW_KEY_F2,0,GLFW.GLFW_PRESS,0);mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),GLFW.GLFW_KEY_F2,0,GLFW.GLFW_RELEASE,0);}
    private void shot(String name) throws Exception {try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(Path.of(name+".png"));}}
    private void js(String code){var b=TerminalBrowserSession.content();if(b!=null)b.executeJavaScript(code,b.getURL(),0);}
    private void click(String selector){js("document.querySelector("+json.toJson(selector)+")?.click()");}
    private void openPhoto(String name){js("[...document.querySelectorAll('#albumGrid button')].find(b=>b.title==="+json.toJson(name)+")?.click()");}
    private void readDom(){
        if(TerminalBrowserSession.content()==null)return;
        js("(()=>{const q=s=>document.querySelector(s),v={stage:"+stage+",cycle:"+cycles+",url:location.href,status:q('#albumStatus')?.textContent,jsHeap:performance.memory?.usedJSHeapSize||0,busy:q('#albumRefresh')?.disabled,names:[...document.querySelectorAll('#albumGrid button')].map(b=>b.title),thumbs:[...document.querySelectorAll('#albumGrid img')].filter(i=>i.hasAttribute('src')&&i.complete&&i.naturalWidth>0).length,sourceCount:[...document.querySelectorAll('#album img')].filter(i=>i.hasAttribute('src')).length,viewer:!!q('#albumViewer')&&!q('#albumViewer').hidden,image:!!q('#albumImage')?.hasAttribute('src')&&q('#albumImage').complete&&q('#albumImage').naturalWidth>0,imageWidth:q('#albumImage')?.naturalWidth,imageHeight:q('#albumImage')?.naturalHeight,selected:q('#albumPhotoName')?.textContent,confirm:!!q('#albumConfirm')&&!q('#albumConfirm').hidden,error:window.__albumQaError||''};document.documentElement.setAttribute('data-album-qa',btoa(unescape(encodeURIComponent(JSON.stringify(v)))));})()");
        domAt=ticks+3;
    }
    private boolean ready(){return dom!=null&&dom.get("stage").getAsInt()==stage&&dom.get("cycle").getAsInt()==cycles;}
    private boolean listed(String name){return ready()&&dom.getAsJsonArray("names").asList().stream().anyMatch(v->v.getAsString().equals(name));}
    private void next(){stage++;frames=0;requested=false;dom=null;domAt=0;}
    private void sample() {
        System.gc();JsonObject m=new JsonObject();m.addProperty("cycle",cycles);m.addProperty("heapUsed",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        long bytes=0;for(var pool:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))bytes+=pool.getMemoryUsed();m.addProperty("directBufferBytes",bytes);
        m.addProperty("cameraWorkers",Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().equals("muxi-terminal-camera-io")).count());
        m.addProperty("nativeScreenshotImages",ImageProbe.live());memory.add(m);
    }
    private void closeNormally(){
        if(closing)return;closing=true;stage=99;frames=0;Minecraft mc=Minecraft.getInstance();
        mc.tell(()->{try{TerminalCamera.stop();if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}catch(Throwable e){report.addProperty("logoutError",e.toString());}});
    }
    private void tick(ClientTickEvent.Post event){
        if(finished)return;var mc=Minecraft.getInstance();
        try{
            ticks++;
            if(ticks%40==0){JsonObject p=new JsonObject();p.addProperty("stage",stage);p.addProperty("cycle",cycles);p.addProperty("ticks",ticks);p.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());p.addProperty("focused",mc.isWindowActive());if(dom!=null)p.add("dom",dom.deepCopy());Files.writeString(Path.of("album-progress.json"),p.toString());}
            if(stage==99){if(mc.level!=null||mc.getSingleplayerServer()!=null||++frames<40)return;report.addProperty("normalLogout",true);write();finished=true;mc.stop();return;}
            if(Files.exists(Path.of("request-normal-close.json"))||ticks>10000)throw new IllegalStateException("QA deadline at stage "+stage);
            if(!MCEF.isInitialized()||mc.getOverlay()!=null)return;
            if(!starting){
                if(!(mc.screen instanceof TitleScreen))return;starting=true;mc.options.renderDistance().set(2);mc.options.graphicsMode().set(GraphicsStatus.FAST);
                screenshots=mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR);report.addProperty("gameDirectory",mc.gameDirectory.getAbsolutePath());report.addProperty("screenshotsDirectory",screenshots.toString());
                mc.createWorldOpenFlows().createFreshLevel("album-private-qa",new LevelSettings("Album private QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,false,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(92345678L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            if(mc.player==null||mc.level==null||mc.screen instanceof ReceivingLevelScreen)return;
            if(domAt!=0&&ticks>=domAt){domAt=0;var b=TerminalBrowserSession.content();if(b!=null)b.getSource(source->{var match=java.util.regex.Pattern.compile("data-album-qa=\"([^\"]+)\"").matcher(source);if(match.find())dom=JsonParser.parseString(new String(Base64.getDecoder().decode(match.group(1)),StandardCharsets.UTF_8)).getAsJsonObject();});}
            frames++;if(frames>900)throw new IllegalStateException("Stage timeout "+stage+" DOM "+dom);
            if(stage==0){
                if(!requested){GLFW.glfwSetWindowTitle(mc.getWindow().getWindow(),"Album Owner QA 131 - private task10");GLFW.glfwFocusWindow(mc.getWindow().getWindow());requested=true;return;}
                if(!mc.isWindowActive())return;
                check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE,"actual isolated 131 window visible and focused");
                report.addProperty("renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
                var vanilla=(java.io.File)Class.forName("io.github.lgatodu47.screenshot_viewer.ScreenshotViewerUtils").getMethod("getVanillaScreenshotsFolder").invoke(null);
                check(vanilla.toPath().toAbsolutePath().normalize().equals(screenshots.toAbsolutePath().normalize()),"installed ESC screenshot manager default equals this native instance screenshots");
                mc.setScreen(null);mc.player.getInventory().setItem(0,new ItemStack(MuxiTerminal.PLAYER_TERMINAL.get()));before=names();check(before.isEmpty(),"fresh independent gameDirectory starts with own empty native screenshot directory");f2();next();return;
            }
            if(stage==1){historical=added();if(historical==null||Files.size(screenshots.resolve(historical))<=100)return;check(true,"native F2 creates actual game screenshot before album opens");report.addProperty("historicalNativeF2",historical);TerminalClient.openApp("album");next();return;}
            if(stage>=2&&stage!=10&&stage!=14&&frames%10==0)readDom();
            if(ready() && (dom.get("status").getAsString().contains("finalize") || !dom.get("error").getAsString().isEmpty()))throw new IllegalStateException("CEF error "+dom);
            if(stage==2){if(!listed(historical)||dom.get("thumbs").getAsInt()<1||frames<35)return;check(true,"historical actual F2 visible with decoded real CEF thumbnail");
                String other=System.getProperty("album.otherInstance");if(other!=null){Path previous=Path.of(other);Set<String> foreign;
                    try(var files=Files.list(previous)){foreign=new HashSet<>(files.filter(Files::isRegularFile).map(p->p.getFileName().toString()).toList());}
                    check(!foreign.isEmpty()&&!previous.toAbsolutePath().normalize().equals(screenshots.toAbsolutePath().normalize())&&names().stream().noneMatch(foreign::contains)&&dom.getAsJsonArray("names").asList().stream().noneMatch(v->foreign.contains(v.getAsString())),"second actual Minecraft instance excludes previous instance native F2 and camera photos");report.addProperty("otherInstanceDirectory",other);report.addProperty("foreignNativePhotoCount",foreign.size());}
                domSamples.add(dom.deepCopy());shot("album-native-history");before=names();f2();next();return;}
            if(stage==3){newF2=added();if(newF2==null||newF2.equals(historical)||!listed(newF2))return;check(true,"F2 while album stays open appears through product automatic refresh");report.addProperty("newNativeF2",newF2);domSamples.add(dom.deepCopy());shot("album-native-new-f2");openPhoto(newF2);next();return;}
            if(stage==4){if(!ready()||!dom.get("image").getAsBoolean()||!newF2.equals(dom.get("selected").getAsString()))return;check(dom.get("imageWidth").getAsInt()<=1280&&dom.get("imageHeight").getAsInt()<=1280,"native F2 opens real bounded CEF large image");domSamples.add(dom.deepCopy());shot("album-native-large");originalF2Digest=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(screenshots.resolve(newF2)));click("#albumDelete");next();return;}
            if(stage==5){if(!ready()||!dom.get("confirm").getAsBoolean())return;check(Files.exists(screenshots.resolve(newF2)),"prepare-delete dialog leaves native F2 untouched");click("#albumCancelDelete");next();return;}
            if(stage==6){if(!ready()||dom.get("confirm").getAsBoolean())return;check(Files.exists(screenshots.resolve(newF2)),"cancel-delete leaves native F2 untouched");click("#albumDelete");next();return;}
            if(stage==7){if(!ready()||!dom.get("confirm").getAsBoolean())return;click("#albumConfirmDelete");next();return;}
            if(stage==8){if(!ready()||dom.get("busy").getAsBoolean()||Files.exists(screenshots.resolve(newF2))||!Files.exists(screenshots.resolve(".recycle").resolve(newF2)))return;check(true,"confirmed native F2 moved to recoverable current-instance recycle with no copy");click("#albumRecycleTab");next();return;}
            if(stage==9){if(!listed(newF2))return;openPhoto(newF2);next();return;}
            if(stage==10){if(frames%10==0)readDom();if(!ready()||!dom.get("image").getAsBoolean())return;shot("album-native-recycle");click("#albumRestore");next();return;}
            if(stage==11){if(!ready()||dom.get("busy").getAsBoolean()||!Files.exists(screenshots.resolve(newF2))||Files.exists(screenshots.resolve(".recycle").resolve(newF2)))return;check(java.security.MessageDigest.isEqual(originalF2Digest,java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(screenshots.resolve(newF2)))),"restore preserves original native F2 filename and SHA-256 bytes");click("#albumPhotosTab");next();return;}
            if(stage==12){if(!listed(newF2))return;click("#albumCamera");next();return;}
            if(stage==13){if(!(mc.screen instanceof TerminalCameraScreen)||frames<20)return;check(true,"album camera button opens actual native camera screen");before=names();mc.screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);next();return;}
            if(stage==14){camera=added();if(camera==null||TerminalCamera.state().busy())return;check(camera.contains("_camera_")&&Files.size(screenshots.resolve(camera))>100,"actual native camera output shares current native F2 directory");report.addProperty("nativeCameraFile",camera);mc.screen.onClose();next();return;}
            if(stage==15){if(!listed(camera)||dom.get("thumbs").getAsInt()<1)return;check(true,"camera return shows native camera output and both F2 photos in same CEF album");domSamples.add(dom.deepCopy());shot("album-native-camera-shared");openPhoto(camera);next();return;}
            if(stage==16){if(!ready()||!dom.get("image").getAsBoolean())return;check(true,"actual camera output opens in CEF album");mc.screen.onClose();next();return;}
            if(stage==17){if(!ready()||dom.get("sourceCount").getAsInt()!=0||frames<25)return;check(mc.screen==null,"native terminal exit clears retained CEF thumbnail and large-image sources");check(ImageProbe.live()==0,"native F2/camera screenshot image allocations all closed");sample();TerminalClient.openTerminal();next();return;}
            if(stage==18){if(!listed(camera)||dom.get("thumbs").getAsInt()<1)return;check(true,"reopening retained terminal resumes album and thumbnails");cycles=0;stage=19;frames=0;requested=false;dom=null;return;}
            if(stage==19){
                if(!ready())return;
                if(!requested){if(dom.get("thumbs").getAsInt()<1)return;openPhoto(newF2);requested=true;dom=null;return;}
                if(!dom.get("image").getAsBoolean()){if(frames%40==0&&!dom.get("viewer").getAsBoolean()&&!dom.get("busy").getAsBoolean())openPhoto(newF2);return;}
                mc.screen.onClose();stage=20;frames=0;requested=false;dom=null;return;
            }
            if(stage==20){
                if(!ready()||dom.get("sourceCount").getAsInt()!=0||frames<60)return;
                sample();check(ImageProbe.live()==0,"cycle "+cycles+" has no native screenshot image leak");
                if(++cycles<cycleLimit){TerminalClient.openTerminal();stage=19;frames=0;requested=false;dom=null;return;}
                report.addProperty("reopenCycles",cycles);check(memory.getAsJsonArray().size()==cycleLimit+1,"all real close-state resource samples collected");
                report.addProperty("success",true);report.addProperty("nativeF2Input","actual KeyboardHandler.keyPress F2 press/release on owned GLFW window");report.addProperty("privacy","only own newly generated native game screenshots; no user photos copied or uploaded");write();closeNormally();
            }
        }catch(Throwable failure){failure.printStackTrace();report.addProperty("success",false);report.addProperty("error",failure.toString());try{write();}catch(Exception ignored){}closeNormally();}
    }
}

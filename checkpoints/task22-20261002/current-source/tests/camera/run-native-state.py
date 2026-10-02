"""Production camera lifecycle against deterministic MC/GL fixtures. NOT real render acceptance."""
from pathlib import Path
import importlib.util,subprocess,tempfile,json
root=Path(__file__).resolve().parents[2];out=root/'build/native-camera-tests';out.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('build',root/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
javac,java=build.java_tools(None)
dependency=Path('C:/Users/Administrator/Documents/Codex/2026-10-01/task-21/music-app/build/compiler-dependencies.jar')
files={
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.client.gui.screens.Screen;public class Minecraft {
static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}
public Screen screen;public Object level=new Object(),connection=new Object();public Player player=new Player();public Options options=new Options();public boolean focus=true;
public boolean isWindowActive(){return focus;}public Object getConnection(){return connection;}public void execute(Runnable r){r.run();}
public void setScreen(Screen next){var old=screen;screen=next;if(old!=null)old.removed();}public Target target=new Target();public Target getMainRenderTarget(){return target;}
public static class Target{public int width=800,height=600;public int getColorTextureId(){return 88;}}
public static class Player{public boolean alive=true;public boolean isAlive(){return alive;}}
public static class Options{public boolean hideGui;public CameraType camera=CameraType.THIRD_PERSON_BACK;public CameraType getCameraType(){return camera;}public void setCameraType(CameraType c){camera=c;}}
}''',
'net/minecraft/client/gui/screens/Screen.java':'''package net.minecraft.client.gui.screens;public class Screen{public void removed(){}}''',
'net/muxigame/terminal/client/TerminalScreen.java':'''package net.muxigame.terminal.client;public class TerminalScreen extends net.minecraft.client.gui.screens.Screen{}''',
'net/muxigame/terminal/client/TerminalHeldScreen.java':'''package net.muxigame.terminal.client;public class TerminalHeldScreen extends TerminalScreen{public static boolean holding=true;public static boolean holdingTerminal(){return holding;}}''',
'net/muxigame/terminal/client/TerminalCameraScreen.java':'''package net.muxigame.terminal.client;public class TerminalCameraScreen extends net.minecraft.client.gui.screens.Screen{public void removed(){TerminalCamera.removed(this);}}''',
'net/muxigame/terminal/client/TerminalBrowserSession.java':'''package net.muxigame.terminal.client;public class TerminalBrowserSession{public static long gen=7;public static Object browser=new Object(),content=new Object();public static long generation(){return gen;}public static Object current(){return browser;}public static Object content(){return content;}public static void addStateListener(java.util.function.Consumer<Object> l){}}''',
'net/muxigame/terminal/client/TerminalCameraBridge.java':'''package net.muxigame.terminal.client;public class TerminalCameraBridge {static Store store=new Store();public static Store store(){return store;}public static class Store{public int saves;public String save(byte[] bytes){saves++;return "photo-"+saves;}}}''',
'net/neoforged/neoforge/common/NeoForge.java':'''package net.neoforged.neoforge.common;public class NeoForge {public static Bus EVENT_BUS=new Bus();public static class Bus{public <T>void addListener(java.util.function.Consumer<T> c){}public <T>void addListener(net.neoforged.bus.api.EventPriority p,java.util.function.Consumer<T> c){}}}''',
'com/mojang/blaze3d/platform/GlStateManager.java':'''package com.mojang.blaze3d.platform;public class GlStateManager{public static int binding=3;public static java.util.Map<Integer,Integer> pack=new java.util.HashMap<>();static{for(int n:new int[]{0x0D05,0x0D02,0x0D03,0x0D04})pack.put(n,n+1);}public static int _getInteger(int n){return n==0x8069?binding:pack.get(n);}public static void _bindTexture(int b){binding=b;}public static void _pixelStore(int n,int v){pack.put(n,v);}}''',
'com/mojang/blaze3d/systems/RenderSystem.java':'''package com.mojang.blaze3d.systems;public class RenderSystem{public static void assertOnRenderThread(){}public static void bindTexture(int id){com.mojang.blaze3d.platform.GlStateManager.binding=id;}}''',
'com/mojang/blaze3d/platform/NativeImage.java':'''package com.mojang.blaze3d.platform;public class NativeImage implements AutoCloseable {public static volatile int live,downloads;public static boolean failDownload,failEncode;private boolean closed;public NativeImage(int w,int h,boolean b){live++;}public void downloadTexture(int l,boolean b){downloads++;GlStateManager.pack.replaceAll((k,v)->0);if(failDownload)throw new IllegalStateException("download");}public void flipY(){}public byte[] asByteArray(){if(failEncode)throw new IllegalStateException("encode");return new byte[]{1};}public void close(){if(!closed){closed=true;live--;}}}''',
'net/muxigame/terminal/client/NativeCameraStateTest.java':'''package net.muxigame.terminal.client;
import net.minecraft.client.*;import net.minecraft.client.gui.screens.Screen;import com.mojang.blaze3d.platform.*;import net.neoforged.neoforge.client.event.*;
public class NativeCameraStateTest {
static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}static void call(String n,Class<?> t)throws Exception{var m=TerminalCamera.class.getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(null,(Object)null);}
static void pre()throws Exception{call("beforeFrame",RenderFrameEvent.Pre.class);}static void post()throws Exception{call("afterFrame",RenderFrameEvent.Post.class);}static void tick()throws Exception{call("tick",ClientTickEvent.Pre.class);}
static void waitIO()throws Exception{for(int i=0;i<200&&TerminalCamera.state().busy();i++)Thread.sleep(5);check(!TerminalCamera.state().busy(),"bounded worker completed");}
public static void main(String[] args)throws Exception {
var mc=Minecraft.getInstance();var terminal=new TerminalScreen();mc.screen=terminal;var camera=mc.options.camera;long gen=TerminalBrowserSession.gen;Object browser=TerminalBrowserSession.browser,content=TerminalBrowserSession.content,player=mc.player,level=mc.level;
TerminalCamera.begin();check(mc.screen instanceof TerminalCameraScreen,"native screen entered");check(TerminalCamera.state().active(),"session active");check(TerminalBrowserSession.gen==gen && TerminalBrowserSession.browser==browser && TerminalBrowserSession.content==content,"browser navigation untouched");
var hud=new net.muxigame.terminal.client.camera.mixin.NativeCameraHudMixin(){};var method=net.muxigame.terminal.client.camera.mixin.NativeCameraHudMixin.class.getDeclaredMethod("muxi$nativeCamera",net.minecraft.client.gui.GuiGraphics.class,net.minecraft.client.DeltaTracker.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);method.setAccessible(true);var callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("test",true);method.invoke(hud,null,null,callback);check(callback.isCancelled(),"standalone saving indicator canceled in native Screen");check(mc.options.camera==camera && !mc.options.hideGui,"saving indicator filter does not change options");
pre();check(mc.options.hideGui && mc.options.camera==CameraType.FIRST_PERSON,"forward world without HUD");TerminalCamera.capture();check(NativeImage.downloads==0,"live native preview does not read back/encode frames");post();check(!mc.options.hideGui && mc.options.camera==camera,"frame settings restored");
TerminalCamera.mode(true);pre();check(mc.options.camera==CameraType.THIRD_PERSON_FRONT,"selfie standard game camera");post();check(mc.options.camera==camera,"selfie perspective restored");TerminalCamera.mode(false);
TerminalCamera.stop();mc.options.hideGui=true;TerminalCamera.begin();pre();post();check(mc.options.hideGui,"existing F1 hidden state preserved");TerminalCamera.stop();mc.options.hideGui=false;TerminalCamera.begin();
pre();TerminalCamera.shutter();TerminalCamera.capture();post();waitIO();check(TerminalCameraBridge.store.saves==1 && TerminalCamera.state().sequence()==1,"one shutter saved once");check(NativeImage.live==0,"native image closed");check(GlStateManager.binding==3 && GlStateManager.pack.entrySet().stream().allMatch(e->e.getValue()==e.getKey()+1),"texture binding and pixel-pack params restored");
pre();NativeImage.failDownload=true;TerminalCamera.shutter();TerminalCamera.capture();post();check(NativeImage.live==0 && !TerminalCamera.state().error().isEmpty(),"failed readback closed allocation");check(GlStateManager.binding==3 && GlStateManager.pack.entrySet().stream().allMatch(e->e.getValue()==e.getKey()+1),"failed readback restores GL state");NativeImage.failDownload=false;
pre();NativeImage.failEncode=true;TerminalCamera.shutter();TerminalCamera.capture();post();waitIO();check(NativeImage.live==0 && TerminalCameraBridge.store.saves==1,"failed PNG encode closed image, no file");NativeImage.failEncode=false;
mc.target.width=10000;mc.target.height=10000;pre();TerminalCamera.shutter();TerminalCamera.capture();post();check(NativeImage.live==0 && TerminalCameraBridge.store.saves==1,"oversized frames fail before allocation/save");mc.target.width=800;mc.target.height=600;
TerminalCamera.shutter();mc.focus=false;tick();check(!TerminalCamera.state().busy(),"queued shutter canceled on focus loss");boolean denied=false;try{TerminalCamera.shutter();}catch(IllegalStateException e){denied=true;}check(denied,"unfocused shutter denied");check(TerminalCamera.state().active() && mc.screen instanceof TerminalCameraScreen,"native mode stays available while unfocused");mc.focus=true;
pre();TerminalCamera.stop();check(mc.screen==terminal,"close returns exact saved terminal screen");check(mc.options.camera==camera && !mc.options.hideGui,"close mid-frame restores options");check(TerminalBrowserSession.gen==gen && TerminalBrowserSession.content==content,"same page after close");check(mc.player==player && mc.level==level,"no player/level replacement");
callback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("test",true);method.invoke(hud,null,null,callback);check(!callback.isCancelled(),"normal terminal saving indicator restored after camera close");
TerminalCamera.begin();TerminalCamera.shutter();TerminalCamera.stop();check(TerminalCameraBridge.store.saves==1 && !TerminalCamera.state().active(),"cancel before render creates no PNG");
TerminalCamera.begin();pre();var replacement=new Screen();mc.setScreen(replacement);check(!TerminalCamera.state().active() && mc.screen==replacement,"external screen replacement releases mode without overriding it");check(mc.options.camera==camera && !mc.options.hideGui,"replacement restores options");
mc.screen=terminal;TerminalCamera.begin();TerminalBrowserSession.gen++;tick();check(mc.screen==null && !TerminalCamera.state().active(),"invalid browser context safely exits without old terminal");
mc.screen=terminal;TerminalCamera.begin();pre();mc.connection=null;tick();check(mc.screen==null && !TerminalCamera.state().active() && !mc.options.hideGui && mc.options.camera==camera,"disconnect releases session/options without stale terminal");
mc.connection=new Object();var held=new TerminalHeldScreen();mc.screen=held;TerminalCamera.begin();TerminalCamera.stop();check(mc.screen==held,"held interaction returns exact held terminal screen");TerminalCamera.begin();TerminalHeldScreen.holding=false;tick();check(mc.screen==null && !TerminalCamera.state().active(),"removed held terminal invalidates return context");
check(NativeImage.live==0,"no remaining native image allocation");System.out.println("{\\"success\\":true,\\"checks\\":"+checks+",\\"scope\\":\\"production lifecycle and readback logic; deterministic MC/GL fixtures, not a real game render\\"}");
}}
'''
}
with tempfile.TemporaryDirectory(dir=out) as temporary:
    temp=Path(temporary);sources=[]
    for name,source in files.items():
        p=temp/'stubs'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8');sources.append(p)
    sources.extend(root/'src/main/java/net/muxigame/terminal/client'/name for name in ['TerminalCamera.java','camera/mixin/NativeCameraHudMixin.java'])
    build.compile_java(javac,sources,temp/'classes',str(dependency),temp/'compile.args')
    run=subprocess.run([str(java),'-cp',str(temp/'classes')+';'+str(dependency),'net.muxigame.terminal.client.NativeCameraStateTest'],capture_output=True,text=True)
    print(run.stdout);print(run.stderr);run.check_returncode();report=json.loads(run.stdout)
    (out/'native-state-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')

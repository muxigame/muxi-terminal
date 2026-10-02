"""Exercise real settings/bridge/codec code with MC/CEF API fixtures. No game or real options file."""
from pathlib import Path
import importlib.util,json,subprocess,sys,shutil,zipfile

ROOT=Path(__file__).resolve().parents[1]
workspace=Path(sys.argv[1]) if len(sys.argv)>1 else Path(r'C:\Users\Administrator\WorkSpace\muxigame')
spec=importlib.util.spec_from_file_location('build',ROOT/'build.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
compiler,java=build.java_tools(None)
lab=ROOT/'build/settings-tests';lab.mkdir(parents=True,exist_ok=True)
gson_source=next((workspace/'_client_test/game/libraries/com/google/code/gson/gson').rglob('gson-*.jar'))
gson=lab/'gson.jar';shutil.copyfile(gson_source,gson)
gson_classes=lab/'gson-classes'
with zipfile.ZipFile(gson) as z:
    for name in z.namelist():
        if name.endswith('.class'):
            p=gson_classes/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(name))
FILES={
'net/minecraft/client/OptionInstance.java':'''package net.minecraft.client; public class OptionInstance<T>{private T v;public int sets;private Object range;public OptionInstance(T v){this.v=v;}public OptionInstance(T v,Object r){this.v=v;range=r;}public T get(){return v;}public void set(T n){sets++;v=n;}public Object values(){return range;}public record IntRange(int minInclusive,int maxInclusive){}}''',
'net/minecraft/client/Options.java':'''package net.minecraft.client; public class Options { public int saves; public OptionInstance<Double> volume=new OptionInstance<>(.37),sensitivity=new OptionInstance<>(.48729),gamma=new OptionInstance<>(.6137);public OptionInstance<Integer> fov=new OptionInstance<>(83),distance=new OptionInstance<>(12,new OptionInstance.IntRange(2,16));public OptionInstance<Double> sensitivity(){return sensitivity;}public OptionInstance<Double> gamma(){return gamma;}public OptionInstance<Integer> fov(){return fov;}public OptionInstance<Integer> renderDistance(){return distance;}public OptionInstance<Double> getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource s){return volume;}public float getSoundSourceVolume(net.minecraft.sounds.SoundSource s){return volume.get().floatValue();}public void save(){saves++;}}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client; public class Minecraft {static final Minecraft MC=new Minecraft();public Options options=new Options();public java.io.File gameDirectory;public Object screen;public final java.util.Queue<Runnable> queue=new java.util.concurrent.ConcurrentLinkedQueue<>();public static Minecraft getInstance(){return MC;}public void execute(Runnable r){queue.add(r);}public void flush(){for(Runnable r;(r=queue.poll())!=null;)r.run();}public void setScreen(Object s){screen=s;}public final SoundManager sounds=new SoundManager();public SoundManager getSoundManager(){return sounds;}public static class SoundManager {public int plays;public void play(Object s){plays++;}}}''',
'net/minecraft/client/gui/screens/options/OptionsScreen.java':'''package net.minecraft.client.gui.screens.options;public class OptionsScreen {public final Object parent;public final net.minecraft.client.Options options;public OptionsScreen(Object p,net.minecraft.client.Options o){parent=p;options=o;}public void onClose(){net.minecraft.client.Minecraft.getInstance().setScreen(parent);}}''',
'net/minecraft/client/resources/sounds/SimpleSoundInstance.java':'''package net.minecraft.client.resources.sounds;public class SimpleSoundInstance {public static Object forUI(Object event,float pitch,float volume){return new Object();}}''',
'net/minecraft/sounds/SoundSource.java':'package net.minecraft.sounds;public enum SoundSource{MASTER}',
'net/minecraft/sounds/SoundEvents.java':'package net.minecraft.sounds;public class SoundEvents {public static final Event UI_BUTTON_CLICK=new Event();public static class Event {public Object value(){return this;}}}',
'org/cef/CefClient.java':'''package org.cef;public class CefClient{public org.cef.browser.CefMessageRouter router;public void addMessageRouter(org.cef.browser.CefMessageRouter r){router=r;}}''',
'org/cef/browser/CefBrowser.java':'''package org.cef.browser;public class CefBrowser{public final java.util.List<String> scripts=new java.util.ArrayList<>();public void executeJavaScript(String s,String u,int n){scripts.add(s);}}''',
'org/cef/browser/CefFrame.java':'''package org.cef.browser;public class CefFrame{public String url;public boolean main=true,valid=true;public String getURL(){return url;}}''',
'org/cef/callback/CefQueryCallback.java':'''package org.cef.callback;public interface CefQueryCallback{void success(String r);void failure(int c,String m);}''',
'org/cef/handler/CefMessageRouterHandlerAdapter.java':'''package org.cef.handler;public class CefMessageRouterHandlerAdapter{public boolean onQuery(org.cef.browser.CefBrowser b,org.cef.browser.CefFrame f,long q,String r,boolean p,org.cef.callback.CefQueryCallback c){return false;}}''',
'org/cef/browser/CefMessageRouter.java':'''package org.cef.browser;public class CefMessageRouter{public org.cef.handler.CefMessageRouterHandlerAdapter handler;public record CefMessageRouterConfig(String query,String cancel){}public static CefMessageRouter create(CefMessageRouterConfig c,org.cef.handler.CefMessageRouterHandlerAdapter h){var r=new CefMessageRouter();r.handler=h;return r;}}''',
'net/muxigame/terminal/client/TerminalBrowserSession.java':'''package net.muxigame.terminal.client;public class TerminalBrowserSession {public static final String HOME_URL="mod://muxi_terminal/terminal/index.html";public enum Kind{HOME,BUILTIN,WEB,ACCOUNT}public record State(Kind kind){}public static long epoch;public static org.cef.browser.CefBrowser shell=new org.cef.browser.CefBrowser(),app=new org.cef.browser.CefBrowser();public static boolean trusted(org.cef.browser.CefBrowser b,org.cef.browser.CefFrame f){return (b==shell||b==app)&&f!=null&&f.valid&&f.main&&TerminalWebPolicy.localDocument(f.url);}public static long generation(){return epoch;}public static org.cef.browser.CefBrowser current(){return shell;}public static org.cef.browser.CefBrowser content(){return app;}public static State state(){return new State(Kind.BUILTIN);}}''',
'org/lwjgl/PointerBuffer.java':'package org.lwjgl;public class PointerBuffer{}',
'org/lwjgl/util/tinyfd/TinyFileDialogs.java':'''package org.lwjgl.util.tinyfd;public class TinyFileDialogs {public static volatile String selected;public static volatile boolean entered;public static volatile java.util.concurrent.CountDownLatch gate;public static String tinyfd_openFileDialog(CharSequence t,CharSequence p,org.lwjgl.PointerBuffer f,CharSequence d,boolean m)throws Exception{entered=true;var g=gate;if(g!=null)g.await();return selected;}}''',
}
for name,content in FILES.items():
    p=lab/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
src=ROOT/'src/main/java/net/muxigame/terminal/client'
sources=[src/name for name in ['TerminalSettings.java','TerminalSettingsBridge.java','TerminalWallpaper.java','TerminalWebPolicy.java']]
sources+=list((lab/'src').rglob('*.java'))+list((ROOT/'tests/settings/java').rglob('*.java'))
build.compile_java(compiler,sources,lab/'classes',str(gson_classes),lab/'compile.args')
results=[]
for mode in ['service','reload','bridge','codec','wallpaper-seed','wallpaper-reload']:
    run=subprocess.run([str(java),'-Djava.awt.headless=true','-cp',str(lab/'classes')+';'+str(gson),'net.muxigame.terminal.client.SettingsTest',mode,str(lab/'fake-game')],capture_output=True,encoding='utf-8',timeout=60)
    if run.returncode:raise RuntimeError(run.stdout+run.stderr)
    results.append(json.loads(run.stdout))
report={'success':True,'test_type':'real module code; MC Options/CEF/file-picker API fixtures; no Minecraft launched','results':results}
(lab/'result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))

"""Run production album/camera bridge and image helper against synthetic native/CEF fixtures."""
from pathlib import Path
import importlib.util,subprocess,tempfile,json,os
root=Path(__file__).resolve().parents[2];out=root/'build/album-tests';out.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('build',root/'build.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
javac,java=b.java_tools(Path(os.environ['ALBUM_QA_JDK']) if os.environ.get('ALBUM_QA_JDK') else None)
dependency=root/'build/music-test-dependencies.jar'
files={
'org/cef/browser/CefBrowser.java':'''package org.cef.browser; public class CefBrowser { public String url="";public String getURL(){return url;}public void executeJavaScript(String code,String url,int line){} }''',
'org/cef/browser/CefFrame.java':'''package org.cef.browser; public class CefFrame {public String url="";public boolean valid=true,main=true;public String getURL(){return url;}}''',
'org/cef/callback/CefQueryCallback.java':'''package org.cef.callback;public interface CefQueryCallback {void success(String response);void failure(int code,String message);}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client; public class Minecraft {
public static final Minecraft INSTANCE=new Minecraft();public Object screen=new net.muxigame.terminal.client.TerminalScreen(),level=new Object(),player=new Object(),connection=new Object();public boolean focused=true;public java.io.File gameDirectory;
public static Minecraft getInstance(){return INSTANCE;}public boolean isWindowActive(){return focused;}public Object getConnection(){return connection;}public User getUser(){return new User();}
public static class User {public java.util.UUID getProfileId(){return new java.util.UUID(0,1);}}}''',
'net/muxigame/terminal/client/TerminalScreen.java':'''package net.muxigame.terminal.client;public class TerminalScreen {}''',
'net/muxigame/terminal/client/TerminalBrowserSession.java':'''package net.muxigame.terminal.client;import org.cef.browser.*;public class TerminalBrowserSession {
public static final String HOME_URL="mod://muxi_terminal/terminal/index.html";public enum Kind {HOME,BUILTIN,WEB,ACCOUNT}public record State(Kind kind,String url){}
public static long gen=1;public static CefBrowser content=new CefBrowser(),shell=new CefBrowser();public static Kind kind=Kind.BUILTIN;public static String nativeUrl=null;public static boolean isShell(CefBrowser b){return b==shell;}public static long generation(){return gen;}public static CefBrowser content(){return content;}public static State state(){return new State(kind,nativeUrl==null?content.url:nativeUrl);}
public static boolean trusted(CefBrowser b,CefFrame f){return b==content && kind==Kind.BUILTIN && f!=null && f.valid && f.main && b.url.equals(f.url);}}''',
'net/muxigame/terminal/client/TerminalCamera.java':'''package net.muxigame.terminal.client;import org.cef.callback.CefQueryCallback;public class TerminalCamera {
@FunctionalInterface interface Read {String get() throws Exception;}public static boolean defer,busy;public static java.util.List<Runnable> queued=new java.util.ArrayList<>();public static int begins;
static void io(Read action,CefQueryCallback callback){if(busy){callback.failure(429,"busy");return;}Runnable r=()->{try{callback.success(action.get());}catch(Exception e){callback.failure(400,e.getMessage());}};if(defer)queued.add(r);else r.run();}
public static void flush(){var q=java.util.List.copyOf(queued);queued.clear();q.forEach(Runnable::run);}public static void begin(){begins++;}public static void stop(){}public static void mode(boolean v){}public static void shutter(){}public static Object state(){return null;}
static String data(byte[] data){return "data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(data);}}''',
'com/mojang/blaze3d/platform/NativeImage.java':'''package com.mojang.blaze3d.platform;public class NativeImage implements AutoCloseable {
public static int live,lastWidth,lastHeight;public static boolean failResize;int w,h;boolean closed;public NativeImage(int w,int h,boolean unused){this.w=w;this.h=h;live++;lastWidth=w;lastHeight=h;}
public static NativeImage read(byte[] png){var b=java.nio.ByteBuffer.wrap(png,16,8);return new NativeImage(b.getInt(),b.getInt(),false);}public int getWidth(){return w;}public int getHeight(){return h;}
public void resizeSubRectTo(int x,int y,int w,int h,NativeImage target){if(failResize)throw new IllegalStateException("synthetic resize failure");}public byte[] asByteArray(){return new byte[]{1,2,3};}public void close(){if(!closed){closed=true;live--;}}}''',
'net/muxigame/terminal/client/AlbumBridgeTest.java':'''package net.muxigame.terminal.client;
import org.cef.browser.*;import org.cef.callback.*;import net.minecraft.client.Minecraft;import java.nio.file.*;import com.google.gson.JsonParser;import com.mojang.blaze3d.platform.NativeImage;
public class AlbumBridgeTest {
static int checks;static void check(boolean b,String text){checks++;if(!b)throw new AssertionError(text);}static class Reply implements CefQueryCallback {String value;int code;public void success(String s){value=s;}public void failure(int c,String s){code=c;value=s;}}
static Reply album(CefBrowser b,CefFrame f,String cmd){Reply r=new Reply();check(TerminalAlbumBridge.dispatch(b,f,cmd,r),"command recognized");return r;}
static String token(CefBrowser b,CefFrame f,String id){return JsonParser.parseString(album(b,f,"album.prepare-delete:"+id).value).getAsJsonObject().get("token").getAsString();}
public static void main(String[] args) throws Exception {
var mc=Minecraft.getInstance();mc.gameDirectory=Path.of(args[0]).toFile();var browser=TerminalBrowserSession.content;var frame=new CefFrame();browser.url=TerminalBrowserSession.HOME_URL+"#/album";frame.url=browser.url;
var store=TerminalCameraBridge.store();var image=new java.awt.image.BufferedImage(800,600,java.awt.image.BufferedImage.TYPE_INT_RGB);var encoded=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",encoded);image.flush();byte[] png=encoded.toByteArray();String id=store.save(png);
check(album(browser,frame,"album.photos").value.contains(id),"shared camera photo visible");TerminalBrowserSession.nativeUrl=TerminalBrowserSession.HOME_URL;check(album(browser,frame,"album.photos").value.contains(id),"CEF hash route accepted when native onLoadEnd state has document-only URL");TerminalBrowserSession.nativeUrl=null;
Reply camera=new Reply();TerminalCameraBridge.dispatch(browser,frame,"camera.begin",camera);check(camera.code==403 && TerminalCamera.begins==0,"album cannot use screenshot authority");
check(album(TerminalBrowserSession.shell,frame,"album.photos").code==403,"shell denied");
for(var kind:new TerminalBrowserSession.Kind[]{TerminalBrowserSession.Kind.WEB,TerminalBrowserSession.Kind.ACCOUNT}){TerminalBrowserSession.kind=kind;check(album(browser,frame,"album.photos").code==403,"external kind denied");}TerminalBrowserSession.kind=TerminalBrowserSession.Kind.BUILTIN;
frame.main=false;check(album(browser,frame,"album.photos").code==403,"iframe denied");frame.main=true;frame.valid=false;check(album(browser,frame,"album.photos").code==403,"closed frame denied");frame.valid=true;
String route=browser.url;browser.url=TerminalBrowserSession.HOME_URL+"#/camera";frame.url=browser.url;check(album(browser,frame,"album.photos").code==403,"other builtin denied");browser.url=route;frame.url=route;
check(album(browser,frame,"album.thumb:"+id).value.startsWith("\\\"data:image/png;base64,"),"thumbnail data only");check(NativeImage.live==0,"thumbnail native images released");
Files.write(store.directory().resolve("corrupt.png"),new byte[]{(byte)137,80,78,71,13,10,26,10});check(album(browser,frame,"album.photo:corrupt.png").code==400 && NativeImage.live==0,"invalid image rejected without native allocations");Files.delete(store.directory().resolve("corrupt.png"));
check(album(browser,frame,"album.restore:../outside").code==400,"path traversal denied before IO");
check(album(browser,frame,"album.recycle:"+id).code==400 && store.list().size()==1,"unconfirmed direct id denied");
String cancelled=token(browser,frame,id);album(browser,frame,"album.cancel-delete");check(album(browser,frame,"album.recycle:"+cancelled).code==400 && store.list().size()==1,"cancel does not move photo");
String accepted=token(browser,frame,id);check(album(browser,frame,"album.recycle:"+accepted).code==0 && store.list().isEmpty(),"one-use confirmed library move");check(album(browser,frame,"album.recycle:"+accepted).code==400,"replay denied");
check(album(browser,frame,"album.recycled").value.contains(id),"trash metadata visible");album(browser,frame,"album.restore:"+id);check(store.list().size()==1 && store.recycled().isEmpty(),"restored in original library");
String busyTicket=token(browser,frame,id);TerminalCamera.busy=true;check(album(browser,frame,"album.recycle:"+busyTicket).code==429,"bounded worker busy rejection");TerminalCamera.busy=false;check(album(browser,frame,"album.recycle:"+busyTicket).code==0,"busy rejection preserves confirmation");album(browser,frame,"album.restore:"+id);
String pending=token(browser,frame,id);TerminalCamera.defer=true;Reply delayed=album(browser,frame,"album.recycle:"+pending);TerminalBrowserSession.gen++;TerminalCamera.flush();check(delayed.code==409 && store.list().size()==1,"navigation cancels pending mutation and explicitly settles callback");
String hidden=token(browser,frame,id);Reply closed=album(browser,frame,"album.recycle:"+hidden);Object original=mc.screen;mc.screen=null;TerminalCamera.flush();check(closed.code==409 && store.list().size()==1,"screen exit cancels pending mutation");mc.screen=original;
mc.focused=false;check(album(browser,frame,"album.photo:"+id).code==0,"owned read-only image survives window focus change");check(album(browser,frame,"album.prepare-delete:"+id).code==403,"new mutation remains focus guarded");mc.focused=true;
String unfocused=token(browser,frame,id);Reply blur=album(browser,frame,"album.recycle:"+unfocused);mc.focused=false;TerminalCamera.flush();check(blur.code==409 && store.list().size()==1,"focus loss cancels pending mutation");mc.focused=true;
String replaced=token(browser,frame,id);Reply replacedFrame=album(browser,frame,"album.recycle:"+replaced);frame.url="https://outside.example/";TerminalCamera.flush();check(replacedFrame.code==409 && store.list().size()==1,"actual frame replacement cancels mutation without generation update");frame.url=route;
String destroyed=token(browser,frame,id);Reply invalidFrame=album(browser,frame,"album.recycle:"+destroyed);frame.valid=false;TerminalCamera.flush();check(invalidFrame.code==409 && store.list().size()==1,"actual invalid frame cancels mutation without generation update");frame.valid=true;
TerminalCamera.defer=false;check(NativeImage.live==0,"no remaining native fixture image");
System.out.println("{\\\"success\\\":true,\\\"checks\\\":"+checks+",\\\"fixture\\\":\\\"production bridge logic, simulated CEF/MC/image backend, synthetic temporary files\\\"}");
}}'''
}
with tempfile.TemporaryDirectory(dir=out) as temporary:
    temp=Path(temporary);sources=[]
    for name,text in files.items():
        p=temp/'stubs'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');sources.append(p)
    sources.extend(root/'src/main/java/net/muxigame/terminal/client'/n for n in ['TerminalAlbumBridge.java','TerminalAlbumConfirmation.java','TerminalPhotoImages.java','TerminalPhotoStore.java','TerminalCameraBridge.java'])
    b.compile_java(javac,sources,temp/'classes',str(dependency),temp/'args')
    run=subprocess.run([str(java),'-cp',str(temp/'classes')+';'+str(dependency),'net.muxigame.terminal.client.AlbumBridgeTest',str(temp/'synthetic-game')],capture_output=True,text=True)
    print(run.stdout);print(run.stderr);run.check_returncode();report=json.loads(run.stdout)
    (out/'bridge-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')

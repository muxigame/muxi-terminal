package net.muxigame.terminal.client;

import com.google.gson.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class SettingsTest {
    static int checks;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    static void rejects(Runnable r){try{r.run();throw new AssertionError("accepted invalid patch");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Path game=Path.of(args[1]);Files.createDirectories(game);Minecraft.getInstance().gameDirectory=game.toFile();
        switch(args[0]){case "service"->service();case "reload"->reload();case "bridge"->bridge(game);case "codec"->codec(game);case "wallpaper-seed"->wallpaperSeed(game);case "wallpaper-reload"->wallpaperReload();}
        System.out.println("{\"mode\":\""+args[0]+"\",\"checks\":"+checks+"}");
    }
    static void service()throws Exception{
        var o=Minecraft.getInstance().options;
        check(TerminalSettings.snapshot().get("sensitivity").getAsDouble()==.48729,"exact native read");
        check(TerminalSettings.snapshot().get("distanceMax").getAsInt()==16,"native hardware range");
        TerminalSettings.apply(json("{}"));check(o.saves==0,"empty submit does not save");
        TerminalSettings.apply(json("{\"fov\":83}"));check(o.fov.sets==0&&o.saves==0,"unchanged value does not apply");
        TerminalSettings.apply(json("{\"fov\":90}"));check(o.fov.get()==90&&o.fov.sets==1&&o.saves==1,"single submit callback and save");
        check(o.sensitivity.get()==.48729&&o.gamma.get()==.6137&&o.volume.get()==.37,"untouched native values preserved");
        o.gamma.set(.82314);check(TerminalSettings.snapshot().get("brightness").getAsDouble()==.82314,"original options change visible");
        for(String p:new String[]{"{\"volume\":2}","{\"fov\":80.5}","{\"fov\":\"90\"}","{\"sensitivity\":null}","{\"distance\":17}","{\"distance\":1}","{\"path\":1}","{\"volume\":0.8,\"fov\":999}"})rejects(()->TerminalSettings.apply(json(p)));
        check(o.volume.get()==.37&&o.saves==1,"whole patch validates before any write");
        TerminalSettings.apply(json("{\"distance\":16}"));check(o.distance.get()==16&&o.distance.sets==1,"distance commits once");
        TerminalSettings.apply(json("{\"distance\":16}"));check(o.distance.sets==1&&o.saves==2,"no repeat rebuild on unchanged submit");
        TerminalSettings.saveUi(json("{\"soundEnabled\":false,\"soundVolume\":0.27}"));
        check(!TerminalSettings.ui().get("soundEnabled").getAsBoolean(),"UI preference applied");
        rejects(()->{try{TerminalSettings.saveUi(json("{\"soundEnabled\":1}"));}catch(java.io.IOException e){throw new RuntimeException(e);}});
        TerminalSettings.sound("select");check(Minecraft.getInstance().sounds.plays==0,"toggle off is silent");
        TerminalSettings.vanilla();check(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen,"original screen entry");
        ((net.minecraft.client.gui.screens.options.OptionsScreen)Minecraft.getInstance().screen).onClose();
        check(TerminalBrowserSession.app.scripts.getLast().contains("terminalSettingsRefresh"),"vanilla return explicitly refreshes native values");
    }
    static void reload()throws Exception{
        check(!TerminalSettings.ui().get("soundEnabled").getAsBoolean()&&TerminalSettings.ui().get("soundVolume").getAsDouble()==.27,"prefs survive new JVM");
        TerminalSettings.saveUi(json("{\"soundEnabled\":true}"));var mc=Minecraft.getInstance();
        // Warm the sound classes before measuring the short throttle window.
        TerminalSettings.sound("hover");Thread.sleep(100);mc.sounds.plays=0;
        long started=System.nanoTime();TerminalSettings.sound("hover");TerminalSettings.sound("select");long elapsed=System.nanoTime()-started;
        check(mc.sounds.plays>=1 && mc.sounds.plays<=2 && (elapsed>=90_000_000L || mc.sounds.plays==1),"native 90ms throttle within measured window");
        int audible=mc.sounds.plays;Thread.sleep(100);mc.options.volume.set(0.0);TerminalSettings.sound("select");check(mc.sounds.plays==audible,"MC master mute wins");
    }
    static final class Reply implements CefQueryCallback{volatile String success;volatile int code;public void success(String s){success=s;}public void failure(int c,String m){code=c;}}
    static org.cef.CefClient client;static CefFrame frame;
    static Reply call(CefBrowser b,CefFrame f,String cmd,boolean persistent){var r=new Reply();org.cef.browser.CefBrowser.live=f;client.router.handler.onQuery(b,f,1,cmd,persistent,r);Minecraft.getInstance().flush();return r;}
    static Reply call(String cmd){return call(TerminalBrowserSession.app,frame,cmd,false);}
    static void await(Reply r)throws Exception{long until=System.nanoTime()+15_000_000_000L;while(r.success==null&&r.code==0&&System.nanoTime()<until){Thread.sleep(5);Minecraft.getInstance().flush();}check(r.success!=null||r.code!=0,"async completion");}
    static void bridge(Path game)throws Exception{
        Files.deleteIfExists(TerminalSettings.file("wallpaper.png"));
        client=new org.cef.CefClient();TerminalSettingsBridge.attach(client);frame=new CefFrame();frame.url=TerminalBrowserSession.HOME_URL+"#/settings";org.cef.browser.CefBrowser.live=frame;
        check(call("settings.snapshot").success!=null,"owned settings accepted");
        var detached=new CefFrame();detached.url=frame.url;var frameReply=new Reply();
        client.router.handler.onQuery(TerminalBrowserSession.app,detached,2,"settings.snapshot",false,frameReply);detached.dispose();Minecraft.getInstance().flush();
        check(frameReply.success!=null,"disposed callback wrapper does not invalidate independent current main frame");
        Minecraft.getInstance().focused=false;check(call("settings.apply:{\"fov\":99}").code==403,"unfocused page cannot change options");Minecraft.getInstance().focused=true;
        check(call(new CefBrowser(),frame,"settings.snapshot",false).code==403,"foreign browser rejected");
        frame.main=false;check(call("wallpaper.choose").code==403,"subframe rejected");frame.main=true;
        frame.url="https://example.com/";check(call("wallpaper.choose").code==403,"external page rejected");
        frame.url=TerminalBrowserSession.HOME_URL;check(call("settings.apply:{}").code==403,"outside settings cannot mutate");
        check(call("ui.snapshot").success!=null,"shell UI preference read accepted");frame.url=TerminalBrowserSession.HOME_URL+"#/settings";org.cef.browser.CefBrowser.live=frame;
        check(call("ui.save:{\"soundEnabled\":true}").success!=null&&TerminalBrowserSession.shell.scripts.getLast().contains("terminalSettingsUi"),"UI preferences broadcast to persistent shell");
        check(call(TerminalBrowserSession.app,frame,"settings.snapshot",true).code==400,"persistent requests rejected");
        check(call("x".repeat(2049)).code==400,"bounded bridge requests");
        var stale=new Reply();client.router.handler.onQuery(TerminalBrowserSession.app,frame,1,"settings.apply:{\"fov\":99}",false,stale);TerminalBrowserSession.epoch++;Minecraft.getInstance().flush();
        check(stale.code==409&&stale.success==null&&Minecraft.getInstance().options.fov.get()==83,"stale queued mutation discarded");
        TinyFileDialogs.selected=null;var cancelled=call("wallpaper.choose");await(cancelled);
        check(json(cancelled.success).get("cancelled").getAsBoolean()&&!Files.exists(TerminalSettings.file("wallpaper.png")),"cancel preserves default");
        Path pic=game.resolve("test-source-secret-name.png");ImageIO.write(new BufferedImage(100,50,BufferedImage.TYPE_INT_RGB),"png",pic.toFile());
        TinyFileDialogs.selected=pic.toString();var picked=call("wallpaper.choose");await(picked);
        check(Files.exists(TerminalSettings.file("wallpaper.png")),"local normalized persistence; native reply="+picked.success+" code="+picked.code);
        check(!picked.success.contains("secret")&&!picked.success.contains(game.toString()),"selection response has no path");
        var read=call("wallpaper.read");await(read);check(json(read.success).get("data").getAsString().startsWith("data:image/png;base64,"),"restart read gives image only");
        byte[] original=Files.readAllBytes(TerminalSettings.file("wallpaper.png"));
        TinyFileDialogs.selected=game.resolve("missing.png").toString();var bad=call("wallpaper.choose");await(bad);check(bad.code==400&&java.util.Arrays.equals(original,Files.readAllBytes(TerminalSettings.file("wallpaper.png"))),"invalid selection preserves wallpaper");
        TinyFileDialogs.selected=pic.toString();TinyFileDialogs.gate=new java.util.concurrent.CountDownLatch(1);TinyFileDialogs.entered=false;
        var pending=call("wallpaper.choose");long until=System.nanoTime()+1_000_000_000L;while(!TinyFileDialogs.entered&&System.nanoTime()<until)Thread.sleep(5);
        check(call("wallpaper.reset").code==409,"reset serialized against chooser");
        check(call("wallpaper.choose").code==409,"no duplicate chooser");
        TerminalBrowserSession.epoch++;TinyFileDialogs.gate.countDown();TinyFileDialogs.gate=null;
        for(int i=0;i<20;i++){Thread.sleep(10);Minecraft.getInstance().flush();}
        check(pending.code==409&&pending.success==null&&java.util.Arrays.equals(original,Files.readAllBytes(TerminalSettings.file("wallpaper.png"))),"closed generation cannot commit chooser result");
        var staleRead=new Reply();var mc=Minecraft.getInstance();
        client.router.handler.onQuery(TerminalBrowserSession.app,frame,1,"wallpaper.read",false,staleRead);
        mc.queue.remove().run();long deadline=System.nanoTime()+15_000_000_000L;
        while(mc.queue.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(5);
        Runnable delivery=mc.queue.remove();
        check(call("wallpaper.reset").success!=null&&!Files.exists(TerminalSettings.file("wallpaper.png")),"default restores and clears persisted file");
        delivery.run();check(staleRead.code==409,"read started before reset cannot resurrect old wallpaper");
        check(TerminalBrowserSession.shell.scripts.size()>=2,"wallpaper broadcasts to full shell");
    }
    static void codec(Path game)throws Exception{
        Path p=game.resolve("codec-image");
        for(String format:new String[]{"png","jpeg"}){ImageIO.write(new BufferedImage(32,24,BufferedImage.TYPE_INT_RGB),format,p.toFile());check(TerminalWallpaper.normalize(p).length>0,"valid "+format);}
        ImageIO.write(new BufferedImage(3000,100,BufferedImage.TYPE_INT_RGB),"png",p.toFile());var decoded=ImageIO.read(new java.io.ByteArrayInputStream(TerminalWallpaper.normalize(p)));check(decoded.getWidth()==2048,"output edge bounded");
        ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"gif",p.toFile());rejectImage(p,"gif rejected");
        Files.writeString(p,"<svg xmlns='http://www.w3.org/2000/svg'/>");rejectImage(p,"svg rejected");
        Files.writeString(p,"not an image");rejectImage(p,"corrupt rejected");
        Files.write(p,new byte[TerminalWallpaper.MAX_BYTES+1]);rejectImage(p,"file bytes bounded");
        ImageIO.write(new BufferedImage(8193,1,BufferedImage.TYPE_INT_RGB),"png",p.toFile());rejectImage(p,"oversized header rejected");
        ImageIO.write(new BufferedImage(5000,4000,BufferedImage.TYPE_BYTE_GRAY),"png",p.toFile());rejectImage(p,"pixel bomb rejected before decode");
        Path saved=game.resolve("codec-store/wallpaper.png");TerminalWallpaper.atomicWrite(saved,new byte[]{1,2,3});TerminalWallpaper.atomicWrite(saved,new byte[]{4});check(java.util.Arrays.equals(Files.readAllBytes(saved),new byte[]{4}),"atomic replacement");
        try(var files=Files.list(saved.getParent())){check(files.count()==1,"no temporary-file leak");}
    }
    static void rejectImage(Path p,String why)throws Exception{try{TerminalWallpaper.normalize(p);throw new AssertionError(why);}catch(java.io.IOException e){checks++;}}
    static void wallpaperSeed(Path game)throws Exception{
        Path p=game.resolve("persist-source.png");ImageIO.write(new BufferedImage(32,24,BufferedImage.TYPE_INT_RGB),"png",p.toFile());
        TerminalWallpaper.atomicWrite(TerminalSettings.file("wallpaper.png"),TerminalWallpaper.normalize(p));Files.delete(p);
        check(Files.exists(TerminalSettings.file("wallpaper.png")),"wallpaper independent of original source");
    }
    static void wallpaperReload()throws Exception{
        client=new org.cef.CefClient();TerminalSettingsBridge.attach(client);frame=new CefFrame();frame.url=TerminalBrowserSession.HOME_URL;
        var read=call("wallpaper.read");await(read);
        check(json(read.success).get("data").getAsString().startsWith("data:image/png;base64,"),"wallpaper survives fresh JVM with source removed");
    }
}

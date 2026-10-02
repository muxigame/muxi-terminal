package net.muxigame.terminal.musicqa;

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
import net.muxigame.terminal.client.music.TerminalMusicService;
import net.muxigame.terminal.client.music.LocalMusicLibrary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import javax.sound.sampled.*;
import java.io.*;

/** Isolated QA driver: actual MCEF page clicks, product bridge and OpenAL readback. */
@Mod(value="muxi_music_qa",dist=Dist.CLIENT)
public final class MusicRuntimeQA {
    private int ticks, stage, frames;
    private boolean starting, requested, finished, closing;
    private volatile JsonObject dom;
    private int domAt;
    private String firstId, firstTitle, localFirst;
    private JsonObject initial;
    private volatile String fixtureError;
    private volatile String fixturePlayMode;
    private final JsonArray checks = new JsonArray(), samples = new JsonArray();
    private final JsonObject report = new JsonObject();
    public MusicRuntimeQA() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private JsonObject snapshot() throws Exception {
        return JsonParser.parseString(TerminalMusicService.command("music.snapshot",()->true)).getAsJsonObject();
    }
    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks.add(label);
    }
    private void shot(String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of(name + ".png"));
        }
    }
    private void click(String selector) {
        var browser = TerminalBrowserSession.content();
        browser.executeJavaScript("document.querySelector("+new Gson().toJson(selector)+")?.click()",browser.getURL(),0);
    }
    private void scroll(String selector) {
        var browser=TerminalBrowserSession.content();
        browser.executeJavaScript("document.querySelector("+new Gson().toJson(selector)+")?.scrollIntoView({block:'start'})",browser.getURL(),0);
    }
    private void portableFixture() {
        Minecraft mc=Minecraft.getInstance();UUID user=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            try {
                String prefix="com.gly091020.netMusicListNeoforge.";
                Class<?> registry=Class.forName(prefix+"NetMusicList"),list=Class.forName(prefix+"item.NetMusicListItem"),player=Class.forName(prefix+"item.NetMusicPlayerItem");
                Class<?> info=Class.forName("com.github.tartaricacid.netmusic.item.ItemMusicCD$SongInfo");
                ItemStack playlist=new ItemStack((net.minecraft.world.item.Item)((java.util.function.Supplier<?>)registry.getField("MUSIC_LIST_ITEM").get(null)).get());
                for(int n=0;n<2;n++) {
                    Object song=info.getConstructor(String.class,String.class,int.class,boolean.class).newInstance(Path.of("本机音乐验收"+n+".wav").toAbsolutePath().toUri().toString(),"便携歌单本机曲目 "+n,12,false);
                    playlist=(ItemStack)list.getMethod("setSongInfo",info,ItemStack.class).invoke(null,song,playlist);
                }
                ItemStack portable=new ItemStack((net.minecraft.world.item.Item)((java.util.function.Supplier<?>)registry.getField("MUSIC_PLAYER_ITEM").get(null)).get());
                fixturePlayMode=list.getMethod("getPlayMode",ItemStack.class).invoke(null,playlist).toString();
                player.getMethod("getOrCreateRingerId",ItemStack.class).invoke(null,portable);
                Object container=player.getMethod("getContainer",ItemStack.class).invoke(null,portable);
                container.getClass().getMethod("setItem",int.class,ItemStack.class).invoke(container,0,playlist);
                container.getClass().getMethod("setChanged").invoke(container);
                var actual=mc.getSingleplayerServer().getPlayerList().getPlayer(user);
                actual.getInventory().setItem(2,portable);actual.inventoryMenu.broadcastChanges();
            }catch(Throwable e){fixtureError=e.toString();}
        });
    }
    private void readDom() {
        var browser = TerminalBrowserSession.content();
        if (browser == null) return;
        String marker="data-music-qa";
        browser.executeJavaScript("(()=>{const v={stage:"+stage+",url:location.href,hidden:document.hidden,binding:typeof window.muxiTerminalQuery,active:document.querySelector('#music')?.classList.contains('page-active'),tracks:[...document.querySelectorAll('[data-track]')].map(b=>({id:b.dataset.track,disabled:b.disabled,title:b.closest('article').querySelector('strong').textContent})),local:[...document.querySelectorAll('[data-local]')].map(b=>({id:b.dataset.local,disabled:b.disabled})),title:document.querySelector('#musicTitle')?.textContent,status:document.querySelector('#musicSource')?.textContent,message:document.querySelector('#musicMessage')?.textContent};document.documentElement.setAttribute('"+marker+"',btoa(unescape(encodeURIComponent(JSON.stringify(v)))));})()",browser.getURL(),0);
        domAt=ticks+4;
    }
    private void next() { stage++; frames=0; requested=false; dom=null; }
    private void record(JsonObject state) {
        JsonObject value=state.deepCopy();value.addProperty("qaStage",stage);samples.add(value);
        if (dom!=null) report.add("dom-"+stage,dom.deepCopy());
    }
    private void write() throws Exception {
        report.add("checks",checks);report.add("snapshots",samples);
        Files.writeString(Path.of("music-runtime-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private void closeNormally() {
        if(closing)return;closing=true;Minecraft mc=Minecraft.getInstance();
        mc.tell(()->{
            try {TerminalMusicService.closeLocal(); if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}
            catch(Throwable e){report.addProperty("logoutError",e.toString());}
            stage=99;frames=0;
        });
    }
    private void tick(ClientTickEvent.Post event) {
        if(finished)return;Minecraft mc=Minecraft.getInstance();
        try {
            ticks++;
            if(fixtureError!=null)throw new IllegalStateException(fixtureError);
            if(ticks%100==0) {
                JsonObject p=new JsonObject();p.addProperty("stage",stage);p.addProperty("ticks",ticks);p.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());
                Files.writeString(Path.of("music-progress.json"),p.toString());
            }
            if(stage==99) {
                if(mc.level!=null||mc.getSingleplayerServer()!=null||++frames<40)return;
                shot("music-normal-logout");report.addProperty("normalLogout",true);write();finished=true;mc.stop();return;
            }
            if(Files.exists(Path.of("request-normal-close.json"))||ticks>12000)throw new IllegalStateException("QA deadline at stage "+stage);
            if(!MCEF.isInitialized()||mc.getOverlay()!=null)return;
            if(!starting) {
                if(!(mc.screen instanceof TitleScreen))return;
                starting=true;mc.options.renderDistance().set(2);mc.options.graphicsMode().set(GraphicsStatus.FAST);
                mc.createWorldOpenFlows().createFreshLevel("music-private-qa",new LevelSettings("Music private QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,false,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(92345678L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            if(mc.player==null||mc.level==null||mc.screen instanceof ReceivingLevelScreen)return;
            if(domAt!=0&&ticks>=domAt) {
                domAt=0;var browser=TerminalBrowserSession.content();
                if(browser!=null)browser.getSource(source->{var match=java.util.regex.Pattern.compile("data-music-qa=\"([^\"]+)\"").matcher(source);if(match.find())dom=JsonParser.parseString(new String(Base64.getDecoder().decode(match.group(1)),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();});
            }
            frames++;
            if(frames>900)throw new IllegalStateException("Stage timeout "+stage+" state "+snapshot()+" DOM "+dom);
            if(stage==0) {
                mc.setScreen(null);mc.player.getInventory().setItem(0,new ItemStack(MuxiTerminal.PLAYER_TERMINAL.get()));
                check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE,"actual QA window is visible in Session 2");
                report.addProperty("renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
                report.addProperty("filePickerVerified",false);report.addProperty("localImportScope","Actual product file copy/decode/store API; file-selection dialog needs coordinated desktop input");
                TerminalClient.openApp("music");next();return;
            }
            var state=snapshot();
            if(stage==1) {
                if(state.get("busy").getAsBoolean()||TerminalBrowserSession.content()==null||!TerminalBrowserSession.contentVisible()||!TerminalBrowserSession.state().rendered())return;
                if(frames%20==0)readDom();
                if(dom==null||dom.get("stage").getAsInt()!=stage)return;
                if(dom.getAsJsonArray("tracks").size()!=state.getAsJsonArray("tracks").size())return;
                check(dom.get("active").getAsBoolean(),"actual MCEF music page is active");
                check(state.getAsJsonArray("tracks").size()>2&&dom.getAsJsonArray("tracks").size()==state.getAsJsonArray("tracks").size(),"real resource catalog equals visible selectable MCEF list");
                firstId=state.getAsJsonArray("tracks").get(0).getAsJsonObject().get("id").getAsString();firstTitle=state.getAsJsonArray("tracks").get(0).getAsJsonObject().get("title").getAsString();
                initial=state.deepCopy();record(state);shot("music-real-list");click("[data-track='"+firstId+"']");next();return;
            }
            if(stage>=2&&stage<=6) {
                if(frames==1)scroll(stage==2?"#musicTrackList":"#musicTitle");
                String expected=stage==5?"paused":"playing";
                if(!state.get("kind").getAsString().equals("game")||!state.get("status").getAsString().equals(expected)||frames<35)return;
                switch(stage) {
                    case 2 -> {check(state.get("title").getAsString().equals(firstTitle),"listed track click actively plays exact game file with OpenAL PLAYING");record(state);shot("music-real-playing-list");click("#music-next");}
                    case 3 -> {check(!state.get("title").getAsString().equals(firstTitle),"next switches to a different actual game track");record(state);shot("music-real-next");click("#music-previous");}
                    case 4 -> {check(state.get("title").getAsString().equals(firstTitle),"previous returns to the selected game track");record(state);click("#musicPause");}
                    case 5 -> {check(true,"game pause reports actual OpenAL PAUSED");record(state);shot("music-real-paused");click("#musicPause");}
                    case 6 -> {check(true,"game resume reports actual OpenAL PLAYING");record(state);click("#music-stop");}
                }
                next();return;
            }
            if(stage==7) {
                if(state.get("kind").getAsString().equals("game")||frames<35)return;
                check(true,"stop clears actual APP game playback");record(state);shot("music-real-stopped");
                // Use the exact product library and decoder for real local file import, without replacing the bridge.
                var field=TerminalMusicService.class.getDeclaredField("library");field.setAccessible(true);var library=(LocalMusicLibrary)field.get(null);
                for(int n=0;n<2;n++) {
                    Path original=Path.of("本机音乐验收"+n+".wav");byte[] pcm=new byte[44100*2*12];
                    for(int i=0;i<pcm.length/2;i++){short v=(short)(Math.sin(i*2*Math.PI*(220+n*110)/44100)*500);pcm[i*2]=(byte)v;pcm[i*2+1]=(byte)(v>>8);}
                    try(var in=new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(44100,16,1,true,false),pcm.length/2)){AudioSystem.write(in,AudioFileFormat.Type.WAVE,original.toFile());}
                    var probe=Class.forName("net.muxigame.terminal.client.music.LocalDecoder").getDeclaredMethod("probe",Path.class);probe.setAccessible(true);
                    library.add(original,file->probe.invoke(null,file));check(Files.exists(original),"real local import preserves source WAV "+n);
                }
                next();return;
            }
            if(stage==8) {
                if(frames%20==0)readDom();
                if(dom==null||dom.get("stage").getAsInt()!=stage||dom.getAsJsonArray("local").size()!=2)return;
                if(!requested){scroll("#musicLocalList");requested=true;return;}
                if(frames<40)return;
                check(state.getAsJsonArray("localTracks").size()==2,"actual decoded local imports appear in native and MCEF lists");
                localFirst=state.getAsJsonArray("localTracks").get(0).getAsJsonObject().get("id").getAsString();record(state);shot("music-real-local-list");click("[data-local='"+localFirst+"']");next();return;
            }
            if(stage==9||stage==10) {
                if(frames==1)scroll("#musicTitle");
                if(!state.get("kind").getAsString().equals("local")||!state.get("status").getAsString().equals("playing")||frames<35)return;
                if(stage==9){check(state.getAsJsonArray("localTracks").get(0).getAsJsonObject().get("active").getAsBoolean(),"local row click plays decoded WAV with actual OpenAL PLAYING");record(state);shot("music-real-local-playing");click("#music-next");}
                else{check(state.getAsJsonArray("localTracks").get(1).getAsJsonObject().get("active").getAsBoolean(),"next switches to second actual local WAV");record(state);shot("music-real-local-next");click("#music-stop");}
                next();return;
            }
            if(stage==11) {
                if(state.get("kind").getAsString().equals("local")||frames<35)return;
                check(true,"stop clears actual local playback");record(state);click("[data-local='"+localFirst+"']");next();return;
            }
            if(stage==12) {
                if(!state.get("kind").getAsString().equals("local")||!state.get("status").getAsString().equals("playing")||frames<35)return;
                click("#music .back");next();return;
            }
            if(stage==13) {
                if(state.get("kind").getAsString().equals("local")||frames<35)return;
                check(TerminalBrowserSession.content()==null,"return Home closes music content and releases owned playback");record(state);shot("music-real-exit-home");
                if(Boolean.getBoolean("qa.music.portable")){portableFixture();TerminalClient.openApp("music");next();return;}
                report.addProperty("success",true);write();closeNormally();
            }
            if(stage==14) {
                var rows=state.getAsJsonArray("tracks");JsonObject selected=null;int portableCount=0;
                for(var row:rows)if(row.getAsJsonObject().get("source").getAsString().startsWith("本人")){portableCount++;if(selected==null)selected=row.getAsJsonObject();}
                if(portableCount!=2||frames<60)return;
                if(frames%20==0)readDom();if(dom==null||dom.get("stage").getAsInt()!=stage)return;
                check(rows.size()==dom.getAsJsonArray("tracks").size(),"original portable inventory playlist is included in the actual MCEF selectable list");
                check(!state.toString().contains("file:")&&!state.toString().contains(mc.gameDirectory.toString()),"portable playlist snapshot never exports native local URL or source path");
                record(state);firstId=selected.get("id").getAsString();firstTitle=selected.get("title").getAsString();click("[data-track='"+firstId+"']");next();return;
            }
            if(stage>=15&&stage<=17||stage==19) {
                if(frames==1)scroll(stage==15?"[data-track='"+firstId+"']":"#musicTitle");
                if(!state.get("kind").getAsString().equals("netmusic")||!state.get("status").getAsString().equals("playing")||frames<35)return;
                if(stage==15){check(state.get("title").getAsString().equals(firstTitle),"portable row click uses real original SELECT_INDEX and reaches actual OpenAL PLAYING");check(!state.getAsJsonObject("capabilities").get("pause").getAsBoolean(),"original portable player has no fabricated pause capability");record(state);shot("music-real-portable-list");click("#music-next");}
                if(stage==16){check(!state.get("title").getAsString().equals(firstTitle),"next switches original portable playlist to second track");record(state);shot("music-real-portable-next");click("#music-previous");}
                if(stage==17){check(state.get("title").getAsString().equals(firstTitle),"previous selects original portable playlist first track");record(state);click("#music-stop");}
                if(stage==19){check(state.get("title").getAsString().equals(firstTitle),"original portable track restarts after STOP and PLAY");record(state);click("#music-stop");}
                next();return;
            }
            if(stage==18||stage==20) {
                if(state.get("kind").getAsString().equals("netmusic")&&state.get("status").getAsString().equals("playing")||frames<40)return;
                check(true,"STOP releases original portable audio stream");record(state);shot("music-real-portable-stopped");
                if(stage==18){click("#music-play");next();return;}
                Class<?> playerType=Class.forName("com.gly091020.netMusicListNeoforge.item.NetMusicPlayerItem");
                Object container=playerType.getMethod("getContainer",ItemStack.class).invoke(null,mc.player.getInventory().getItem(2));
                ItemStack playlist=(ItemStack)container.getClass().getMethod("getItem",int.class).invoke(container,0);
                String mode=Class.forName("com.gly091020.netMusicListNeoforge.item.NetMusicListItem").getMethod("getPlayMode",ItemStack.class).invoke(null,playlist).toString();
                check("LOOP".equals(fixturePlayMode)&&fixturePlayMode.equals(mode),"manual previous/next works in LOOP mode and preserves original item loop setting");
                report.addProperty("success",true);write();closeNormally();
            }
        } catch(Throwable error) {
            error.printStackTrace();report.addProperty("success",false);report.addProperty("error",error.toString());report.addProperty("failedStage",stage);
            try{if(dom!=null)report.add("failedDom",dom.deepCopy());report.add("failedSnapshot",snapshot());shot("music-failed-stage");write();}catch(Exception ignored){}closeNormally();
        }
    }
}

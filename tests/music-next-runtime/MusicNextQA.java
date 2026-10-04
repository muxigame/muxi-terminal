package net.muxigame.terminal.musicnextqa.driver;

import net.muxigame.terminal.client.music.*;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.*;
import net.muxigame.terminal.client.TerminalBrowserSession;
import net.muxigame.terminal.client.TerminalScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.sound.sampled.*;
import org.lwjgl.openal.AL10;

/** Private native fixture. Uses actual product service, MC channels and local files. */
@Mod(value="music_next_qa",dist=Dist.CLIENT)
public final class MusicNextQA {
    private long last;
    private int ticks;
    private volatile JsonArray audio = new JsonArray();
    public MusicNextQA() {
        try {
            Path root = Path.of("").toAbsolutePath();
            Path staged = root.resolve("mcef-libraries"), destination = root.resolve("mods/mcef-libraries");
            if (Files.isDirectory(staged)) {
                try(var paths=Files.walk(staged)) {
                    for(Path source:paths.toList()) {
                        Path target=destination.resolve(staged.relativize(source));
                        if(Files.isDirectory(source))Files.createDirectories(target);
                        else Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.createDirectories(root.resolve("config/mcef"));
            Files.writeString(root.resolve("config/mcef/mcef.properties"),"skip-download=true\nuse-cache=false\n");
        } catch (IOException failure) { throw new RuntimeException(failure); }
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private JsonObject snapshot() throws Exception {
        JsonObject out = JsonParser.parseString(TerminalMusicService.command("music.snapshot", () -> true)).getAsJsonObject();
        out.add("continuity",MusicContinuity.diagnostics());
        out.add("audio",audio.deepCopy()); out.addProperty("tick",ticks);
        Minecraft mc=Minecraft.getInstance();
        out.addProperty("actualMusicGain",mc.options.getSoundSourceVolume(SoundSource.MUSIC));
        out.addProperty("selectedSlot",mc.player==null?-1:mc.player.getInventory().selected);
        out.addProperty("screen",mc.screen==null?"NONE":mc.screen.getClass().getSimpleName());
        out.addProperty("reloading",mc.getOverlay()!=null);
        return out;
    }
    private void readAudio() {
        Minecraft mc=Minecraft.getInstance();
        if (!(mc.getSoundManager() instanceof MusicAccess.Sounds access) || !(access.muxiMusic$engine() instanceof MusicAccess.Engine engine)) return;
        JsonArray result = new JsonArray();
        for(var entry : List.copyOf(engine.muxiMusic$channels().entrySet())) {
            if(entry.getKey().getSource()!=SoundSource.MUSIC && entry.getKey().getSource()!=SoundSource.RECORDS)continue;
            SoundInstance sound=entry.getKey();
            entry.getValue().execute(channel->{
                JsonObject row=new JsonObject();row.addProperty("own",TerminalMusicService.owns(sound));
                row.addProperty("source",sound.getSource().getName());row.addProperty("event",sound.getLocation().toString());
                int id=((net.muxigame.terminal.musicnextqa.mixin.ChannelGainAccessor)channel).qa$source();
                row.addProperty("gain",AL10.alGetSourcef(id,AL10.AL_GAIN));row.addProperty("state",AL10.alGetSourcei(id,AL10.AL_SOURCE_STATE));
                row.addProperty("baseVolume",sound.getVolume());
                synchronized(result){result.add(row);audio=result.deepCopy();}
            });
        }
        if(result.isEmpty())audio=new JsonArray();
    }
    private JsonObject run(JsonObject request) throws Exception {
        Minecraft mc=Minecraft.getInstance();String type=request.get("type").getAsString();
        switch(type) {
            case "api" -> { return JsonParser.parseString(TerminalMusicService.command(request.get("command").getAsString(),()->true)).getAsJsonObject(); }
            case "setup" -> { mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(.2); mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(.5); mc.options.save(); }
            case "background" -> {
                mc.getMusicManager().stopPlaying();MusicContinuity.reset();
                mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(request.get("volume").getAsDouble());
                Music music = mc.getSituationalMusic();
                Class.forName("com.biomemusic.MusicChoice").getField("lastChoice").set(null,music);
                mc.getMusicManager().startPlaying(music);
            }
            case "legacy-gain" -> {
                var engine=((MusicAccess.Sounds)mc.getSoundManager()).muxiMusic$engine();
                engine.getClass().getMethod("adjustVolume",SoundSource.class,float.class).invoke(engine,SoundSource.MUSIC,mc.options.getSoundSourceVolume(SoundSource.MUSIC));
            }
            case "refresh-gain" -> mc.getSoundManager().updateSourceVolume(SoundSource.MUSIC,1);
            case "background-stop" -> mc.getMusicManager().stopPlaying();
            case "biome-stop" -> {
                var method=mc.getMusicManager().getClass().getDeclaredMethod("fadeOut");method.setAccessible(true);method.invoke(mc.getMusicManager());
            }
            case "detach" -> { TerminalMusicService.closeLocal();mc.setScreen(null); }
            case "select-slot" -> mc.player.getInventory().selected=request.get("slot").getAsInt();
            case "import-fixture" -> {
                LocalMusicLibrary library=(LocalMusicLibrary)field("library");
                for(int n=0;n<2;n++) {
                    Path file=mc.gameDirectory.toPath().resolve("fixture-"+n+".wav");
                    int seconds=request.has("seconds")?request.get("seconds").getAsInt():3;
                    int samples=22050*seconds;byte[] data=new byte[samples*2];
                    for(int i=0;i<samples;i++){short value=(short)(Math.sin(i*2*Math.PI*(220+n*110)/22050)*1600);data[i*2]=(byte)value;data[i*2+1]=(byte)(value>>8);}
                    AudioFormat format=new AudioFormat(22050,16,1,true,false);
                    try(var stream=new AudioInputStream(new ByteArrayInputStream(data),format,samples)){AudioSystem.write(stream,AudioFileFormat.Type.WAVE,file.toFile());}
                    library.add(file,LocalDecoder::probe);
                }
            }
            case "reload" -> mc.reloadResourcePacks();
            case "disconnect" -> { if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen()); }
            case "reconnect" -> {
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:"+Integer.getInteger("qa.local.port")),
                    new net.minecraft.client.multiplayer.ServerData("Private music QA","127.0.0.1",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
            }
            case "ui-open" -> {
                net.muxigame.terminal.client.TerminalClient.openApp("music");
            }
            case "ui-js" -> {
                var browser=TerminalBrowserSession.content();browser.executeJavaScript(request.get("script").getAsString(),browser.getURL(),0);
            }
            case "ui-source" -> {
                var browser=TerminalBrowserSession.content();String id=request.get("id").getAsString();
                browser.getSource(source->{try{Files.writeString(mc.gameDirectory.toPath().resolve("music-ui-"+id+".html"),source);}catch(IOException failure){throw new RuntimeException(failure);}});
            }
            case "observe" -> {}
            default -> throw new IllegalArgumentException(type);
        }
        return snapshot();
    }
    private static Object field(String name) throws Exception {var f=TerminalMusicService.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private void tick(ClientTickEvent.Post event) {
        ticks++;if(ticks%2==0)readAudio();
        Path file=Minecraft.getInstance().gameDirectory.toPath().resolve("music-qa-request.json");
        if(!Files.exists(file))return;
        try {
            JsonObject request=JsonParser.parseString(Files.readString(file)).getAsJsonObject();long id=request.get("id").getAsLong();
            if(id<=last)return;last=id;
            JsonObject result=new JsonObject();result.addProperty("id",id);
            try{result.add("state",run(request));result.addProperty("ok",true);}
            catch(Throwable error){result.addProperty("ok",false);result.addProperty("error",error.toString());error.printStackTrace();}
            Files.writeString(file.resolveSibling("music-qa-result-"+id+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception transientRead){}
    }
}

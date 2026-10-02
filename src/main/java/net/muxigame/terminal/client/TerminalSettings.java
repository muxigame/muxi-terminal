package net.muxigame.terminal.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.*;

/** Runs on Minecraft's main thread. Options remains the only authority for game values. */
public final class TerminalSettings {
    private static final Set<String> GAME=Set.of("volume","sensitivity","fov","brightness","distance");
    private static boolean loaded,soundEnabled=true;
    private static double soundVolume=.35;
    private static long lastSound;
    private TerminalSettings() {}
    static Path file(String name){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/muxi-terminal/settings").resolve(name);}
    static void load(){
        if(loaded)return;loaded=true;
        try {
            Path p=file("ui.json");if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>4096)return;
            var j=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            boolean enabled=j.get("soundEnabled").getAsBoolean();double volume=number(j,"soundVolume",0,1,false);
            soundEnabled=enabled;soundVolume=volume;
        }catch(Exception ignored){}
    }
    static JsonObject ui(){load();var j=new JsonObject();j.addProperty("soundEnabled",soundEnabled);j.addProperty("soundVolume",soundVolume);return j;}
    static JsonObject snapshot(){
        var o=Minecraft.getInstance().options;var j=ui();
        j.addProperty("volume",o.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        j.addProperty("sensitivity",o.sensitivity().get());j.addProperty("fov",o.fov().get());
        j.addProperty("brightness",o.gamma().get());j.addProperty("distance",o.renderDistance().get());
        // Mirror the current native validator (including device-dependent render-distance maximum).
        int max=32;
        if(o.renderDistance().values() instanceof net.minecraft.client.OptionInstance.IntRange r)max=r.maxInclusive();
        j.addProperty("distanceMax",max);return j;
    }
    static double number(JsonObject j,String key,double min,double max,boolean integer){
        var v=j.get(key);
        if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("设置值必须是数字");
        double n=v.getAsDouble();
        if(!Double.isFinite(n) || n<min || n>max || (integer && n!=Math.rint(n)))throw new IllegalArgumentException("设置值超出原版范围");
        return n;
    }
    static JsonObject apply(JsonObject patch){
        var mc=Minecraft.getInstance();var o=mc.options;
        for(String key:patch.keySet()){
            if(!GAME.contains(key))throw new IllegalArgumentException("未知游戏设置");
            switch(key){
                case "volume","sensitivity","brightness"->number(patch,key,0,1,false);
                case "fov"->number(patch,key,30,110,true);
                case "distance"->number(patch,key,2,snapshot().get("distanceMax").getAsInt(),true);
            }
        }
        boolean changed=false;
        for(String key:patch.keySet()){
            double n=patch.get(key).getAsDouble();
            switch(key){
                case "volume"->{var v=o.getSoundSourceOptionInstance(SoundSource.MASTER);if(v.get()!=n){v.set(n);changed=true;}}
                case "sensitivity"->{if(o.sensitivity().get()!=n){o.sensitivity().set(n);changed=true;}}
                case "brightness"->{if(o.gamma().get()!=n){o.gamma().set(n);changed=true;}}
                case "fov"->{if(o.fov().get()!=n){o.fov().set((int)n);changed=true;}}
                case "distance"->{if(o.renderDistance().get()!=n){o.renderDistance().set((int)n);changed=true;}}
            }
        }
        // OptionInstance callbacks apply native effects once, after explicit submit.
        if(changed)o.save();return snapshot();
    }
    static JsonObject saveUi(JsonObject patch) throws java.io.IOException {
        load();boolean enabled=soundEnabled;double volume=soundVolume;
        for(String k:patch.keySet())if(!Set.of("soundEnabled","soundVolume").contains(k))throw new IllegalArgumentException("未知终端设置");
        if(patch.has("soundEnabled")){
            var v=patch.get("soundEnabled");if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("音效开关无效");enabled=v.getAsBoolean();
        }
        if(patch.has("soundVolume"))volume=number(patch,"soundVolume",0,1,false);
        var j=new JsonObject();j.addProperty("soundEnabled",enabled);j.addProperty("soundVolume",volume);
        TerminalWallpaper.atomicWrite(file("ui.json"),j.toString().getBytes(StandardCharsets.UTF_8));
        soundEnabled=enabled;soundVolume=volume;return ui();
    }
    static void sound(String kind){
        load();if(!Set.of("hover","select").contains(kind))throw new IllegalArgumentException("未知音效");
        long now=System.nanoTime();var mc=Minecraft.getInstance();
        if(!soundEnabled || soundVolume<=0 || mc.options.getSoundSourceVolume(SoundSource.MASTER)<=0 || now-lastSound<90_000_000)return;
        lastSound=now;
        // Built-in licensed asset; forUI uses MASTER, which applies Minecraft's own mute/volume.
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(),kind.equals("hover")?1.3f:1f,(float)(soundVolume*(kind.equals("hover")?.18:.45))));
    }
    static void vanilla(){
        var mc=Minecraft.getInstance();var owner=TerminalBrowserSession.content();long epoch=TerminalBrowserSession.generation();
        mc.setScreen(new OptionsScreen(mc.screen,mc.options){
            @Override public void onClose(){
                super.onClose();
                if(owner!=null && owner==TerminalBrowserSession.content() && epoch==TerminalBrowserSession.generation())
                    owner.executeJavaScript("window.terminalSettingsRefresh?.()",TerminalBrowserSession.HOME_URL,0);
            }
        });
    }
}

package net.muxigame.terminal.client.music;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.sounds.AudioStream;
import java.util.*;

/** Inspects real installed binary method/field signatures without initializing or booting MC. */
public final class MusicBindingTest {
    private static final List<String> checks = new ArrayList<>();
    private static Class<?> type(String name) throws Exception { return Class.forName(name, false, MusicBindingTest.class.getClassLoader()); }
    private static void check(boolean ok,String label) { if(!ok)throw new AssertionError(label);checks.add(label); }
    public static void main(String[] args) throws Exception {
        String p="com.gly091020.netMusicListNeoforge.";
        Class<?> player=type(p+"item.NetMusicPlayerItem"),list=type(p+"item.NetMusicListItem");
        check(player.getMethod("getRingerId",ItemStack.class).getReturnType()==UUID.class,"real portable ringer getter matches");
        check(player.getMethod("getContainer",ItemStack.class).getReturnType().getMethod("getItem",int.class).getReturnType()==ItemStack.class,"real native inventory container access matches");
        check(list.getMethod("getSongInfoList",ItemStack.class).getReturnType()==List.class,"real existing playlist access matches");
        check(list.getMethod("getSongIndex",ItemStack.class).getReturnType()==Integer.class,"real current playlist index getter matches");
        Class<?> info=type("com.github.tartaricacid.netmusic.item.ItemMusicCD$SongInfo");
        check(info.getField("songName").getType()==String.class,"real song name metadata matches");
        check(type("com.github.tartaricacid.netmusic.item.ItemMusicCD").getMethod("getSongInfo",ItemStack.class).getReturnType()==info,"real single CD portable source getter matches");
        Class<?> action=type(p+"packet.MusicPlayerActionPacket$Action"),mode=type(p+"util.PlayMode"),packet=type(p+"packet.MusicPlayerActionPacket");
        check(packet.getConstructor(action,int.class,int.class,mode,info)!=null,"real existing server action packet constructor matches");
        var actions=Arrays.stream(action.getEnumConstants()).map(Object::toString).toList();
        check(actions.containsAll(List.of("PLAY","STOP","NEXT","SELECT_INDEX"))&&!actions.contains("PAUSE")&&!actions.contains("RESUME"),"real packet supports play stop next select; no fabricated pause/resume");
        Class<?> ringer=type(p+"sounds.RingerSound");
        check(ringer.getMethod("isSelf").getReturnType()==boolean.class&&ringer.getMethod("getRingerId").getReturnType()==UUID.class&&ringer.getMethod("getInfo").getReturnType()==info,"real playing source ownership and metadata access matches");
        Class<?> decoder=type("com.github.tartaricacid.netmusic.client.audio.NetMusicAudioStream");
        check(AudioStream.class.isAssignableFrom(decoder)&&decoder.getConstructor(java.net.URL.class)!=null,"real optional local decoder constructor matches");
        check(type("net.minecraft.client.sounds.MusicManager").getDeclaredField("currentMusic")!=null,"MC current music accessor field verified");
        check(type("net.minecraft.client.sounds.SoundManager").getDeclaredField("soundEngine")!=null,"MC sound engine accessor field verified");
        check(type("net.minecraft.client.sounds.SoundEngine").getDeclaredField("instanceToChannel")!=null,"MC sound channel accessor field verified");
        check(type("com.mojang.blaze3d.audio.Channel").getDeclaredMethod("getState").getReturnType()==int.class,"MC actual OpenAL state invoker verified");
        System.out.println(new com.google.gson.Gson().toJson(Map.of("success",true,"count",checks.size(),"checks",checks,"scope","real installed class signatures; no static MC initialization or gameplay")));
    }
}

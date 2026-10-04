package net.muxigame.terminal.musicnextqa.driver;
import com.google.gson.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Assisted native item fixture in this private loopback server only. */
@Mod(value="music_next_qa",dist=Dist.DEDICATED_SERVER)
public final class MusicNextServerQA {
    private long last;
    public MusicNextServerQA(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void tick(ServerTickEvent.Post event) {
        Path home=Path.of(System.getProperty("qa.local.root")).resolve("server");Path file=home.resolve("music-qa-request.json");
        if(!Files.exists(file))return;
        try {
            JsonObject request=JsonParser.parseString(Files.readString(file)).getAsJsonObject();long id=request.get("id").getAsLong();if(id<=last)return;last=id;
            JsonObject result=new JsonObject();result.addProperty("id",id);
            try {
                var player=event.getServer().getPlayerList().getPlayerByName(request.get("player").getAsString());
                String prefix="com.gly091020.netMusicListNeoforge.";
                Class<?> registry=Class.forName(prefix+"NetMusicList"),list=Class.forName(prefix+"item.NetMusicListItem"),portable=Class.forName(prefix+"item.NetMusicPlayerItem");
                Class<?> info=Class.forName("com.github.tartaricacid.netmusic.item.ItemMusicCD$SongInfo");
                ItemStack playlist=new ItemStack((net.minecraft.world.item.Item)((java.util.function.Supplier<?>)registry.getField("MUSIC_LIST_ITEM").get(null)).get());
                int index=0;
                for(var url:request.getAsJsonArray("files")) {
                    Object song=info.getConstructor(String.class,String.class,int.class,boolean.class).newInstance(url.getAsString(),"Portable fixture "+index++,3,false);
                    playlist=(ItemStack)list.getMethod("setSongInfo",info,ItemStack.class).invoke(null,song,playlist);
                }
                ItemStack stack=new ItemStack((net.minecraft.world.item.Item)((java.util.function.Supplier<?>)registry.getField("MUSIC_PLAYER_ITEM").get(null)).get());
                portable.getMethod("getOrCreateRingerId",ItemStack.class).invoke(null,stack);
                Object container=portable.getMethod("getContainer",ItemStack.class).invoke(null,stack);
                container.getClass().getMethod("setItem",int.class,ItemStack.class).invoke(container,0,playlist);container.getClass().getMethod("setChanged").invoke(container);
                player.getInventory().setItem(2,stack);player.inventoryMenu.broadcastChanges();
                result.addProperty("ok",true);result.addProperty("nativeItemFixture",true);
            }catch(Throwable failure){result.addProperty("ok",false);result.addProperty("error",failure.toString());failure.printStackTrace();}
            Files.writeString(home.resolve("music-qa-result-"+id+".json"),result.toString());
        }catch(Exception transientRead){}
    }
}

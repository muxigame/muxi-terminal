package net.muxigame.terminal.minigamesqa;

import com.google.gson.*;
import java.nio.file.*;
import net.muxigame.minigames.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Observes actual connected players on the server thread; never creates players or grants trust. */
@Mod(value="minigames_native_server_qa",dist=Dist.DEDICATED_SERVER)
public final class MinigamesDedicatedServerQA {
    private final Path coordinator=Path.of(System.getProperty("qa.minigames.coordinator"));
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private int last=-1;
    public MinigamesDedicatedServerQA(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::tick);}
    private void write(String name,JsonObject row)throws Exception{
        Path path=coordinator.resolve(name),temp=path.resolveSibling(name+".tmp");
        Files.writeString(temp,gson.toJson(row));Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private void started(ServerStartedEvent event){
        try{JsonObject row=new JsonObject();row.addProperty("dedicated",true);row.addProperty("pid",ProcessHandle.current().pid());row.addProperty("fakePlayers",false);write("server-ready.json",row);}catch(Exception error){throw new IllegalStateException(error);}
    }
    private void tick(ServerTickEvent.Post event){
        Path command=coordinator.resolve("command-server.json");if(!Files.isRegularFile(command))return;
        try{
            JsonObject next=JsonParser.parseString(Files.readString(command)).getAsJsonObject();int id=next.get("id").getAsInt();if(id<=last)return;last=id;
            if(!next.get("type").getAsString().equals("observe"))throw new IllegalArgumentException("Observation only");
            MinecraftServer server=event.getServer();JsonArray players=new JsonArray();
            for(var p:server.getPlayerList().getPlayers()){
                JsonObject player=new JsonObject();player.addProperty("name",p.getGameProfile().getName());player.addProperty("uuid",p.getUUID().toString());
                player.addProperty("dimension",p.level().dimension().location().toString());player.addProperty("x",p.getX());player.addProperty("y",p.getY());player.addProperty("z",p.getZ());
                player.addProperty("inventory",p.getInventory().save(new net.minecraft.nbt.ListTag()).toString());player.addProperty("returnsPending",PlayerReturns.pending(p));
                player.addProperty("connected",p.connection.isAcceptingMessages());player.addProperty("transport",p.connection.getConnection().getRemoteAddress().toString());
                player.addProperty("resultPending",GamePlatform.pendingCount(p));
                player.addProperty("serverThread",server.isSameThread());player.addProperty("playerServerMatches",p.server==server);
                player.addProperty("currentPlayerMatches",server.getPlayerList().getPlayer(p.getUUID())==p);
                player.addProperty("profileBanned",server.getPlayerList().getBans().isBanned(p.getGameProfile()));
                player.addProperty("ipBanned",server.getPlayerList().getIpBans().isBanned(p.connection.getRemoteAddress()));
                player.addProperty("whitelistAllows",server.getPlayerList().isWhiteListed(p.getGameProfile()));
                String lastResult=GamePlatform.last(p);if(!lastResult.isEmpty())player.add("lastActualResult",JsonParser.parseString(lastResult));
                try{player.addProperty("admittedUid",TrustedAccounts.uid(p));}catch(IllegalArgumentException unknown){player.addProperty("admittedUid",-1);}
                try{player.addProperty("socialUid",TrustedAccounts.socialUid(p));}catch(IllegalArgumentException unknown){player.addProperty("socialUid",-1);}
                player.add("games",GameRuntime.get(server).snapshot(p,""));players.add(player);
            }
            JsonObject row=new JsonObject();row.add("players",players);row.addProperty("ok",true);row.addProperty("fakePlayers",false);row.addProperty("actualDedicatedServer",true);write("result-server-"+id+".json",row);
        }catch(Exception error){error.printStackTrace();}
    }
}

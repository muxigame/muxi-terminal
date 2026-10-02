package net.muxigame.terminal.minigamesqa;

import com.cinemamod.mcef.MCEF;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.muxigame.terminal.client.TerminalClient;
import net.muxigame.terminal.client.TerminalBrowserSession;
import net.muxigame.minigames.GameRuntime;
import net.muxigame.minigames.PlayerReturns;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** QA only: two real clients, genuine LAN connections, no fake ServerPlayers or trust grants. */
@Mod(value="minigames_native_qa",dist=Dist.CLIENT)
public final class MinigamesNativeQA {
    private final String role=System.getProperty("qa.minigames.role");
    private final Path coordinator=Path.of(System.getProperty("qa.minigames.coordinator"));
    private final int port=Integer.getInteger("qa.minigames.port");
    private final boolean dedicated=Boolean.getBoolean("qa.minigames.dedicated");
    private boolean joinRequested,passportRequested;
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private boolean starting,ready,inTick,published,closing;
    private int ticks,lastCommand=-1,delay,stopAt,worldReadyTicks;
    private JsonObject command;
    private long generation;
    private volatile JsonObject sampled;
    public MinigamesNativeQA(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void write(Path path,JsonObject data)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,gson.toJson(data));
        Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private JsonObject status(Minecraft mc){
        JsonObject row=new JsonObject();row.addProperty("role",role);row.addProperty("ticks",ticks);row.addProperty("pid",ProcessHandle.current().pid());
        row.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());row.addProperty("focused",mc.isWindowActive());
        row.addProperty("ready",ready);row.addProperty("command",lastCommand);
        row.addProperty("realNetworkPlayer",mc.getConnection()!=null&&mc.player!=null);
        row.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));
        if(mc.player!=null){row.addProperty("name",mc.player.getGameProfile().getName());row.addProperty("uuid",mc.player.getUUID().toString());}
        return row;
    }
    private void closeNormally(Minecraft mc) {
        if(closing)return;closing=true;
        int id=lastCommand;
        // Disconnect renders nested ticks; run it after this event callback returns.
        mc.tell(()->{
            try {
                TerminalBrowserSession.close();if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());
                JsonObject row=status(mc);row.addProperty("ok",true);row.addProperty("normalDisconnect",true);
                write(coordinator.resolve("result-"+role+"-"+id+".json"),row);
                command=null;stopAt=ticks+40;
            } catch(Throwable error) {
                error.printStackTrace();
                try {JsonObject row=status(mc);row.addProperty("ok",false);row.addProperty("error",error.toString());write(coordinator.resolve("normal-close-failed-"+role+".json"),row);}catch(Exception ignored){}
            }
        });
    }
    private void tick(ClientTickEvent.Post event){
        if(inTick)return;inTick=true;Minecraft mc=Minecraft.getInstance();
        try {
            ticks++;
            if(stopAt>0){if(ticks>=stopAt)mc.stop();return;}
            if(closing)return;
            if(ticks%40==0)write(coordinator.resolve("status-"+role+".json"),status(mc));
            if(command==null&&Files.isRegularFile(coordinator.resolve("command-"+role+".json"))){
                JsonObject next=JsonParser.parseString(Files.readString(coordinator.resolve("command-"+role+".json"))).getAsJsonObject();
                if(next.get("id").getAsInt()>lastCommand){
                    command=next;lastCommand=next.get("id").getAsInt();sampled=null;delay=0;
                    if(!next.get("type").getAsString().equals("stop")){
                        GLFW.glfwShowWindow(mc.getWindow().getWindow());GLFW.glfwRestoreWindow(mc.getWindow().getWindow());GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                    }
                }
            }
            if(command!=null&&command.get("type").getAsString().equals("stop")){
                closeNormally(mc);return;
            }
            if(command!=null&&command.get("type").getAsString().equals("reconnect")){
                if(!dedicated||!ready)throw new IllegalStateException("Reconnect requires an admitted dedicated connection");
                int id=lastCommand;command=null;starting=true;ready=false;
                Files.deleteIfExists(coordinator.resolve("mint-join-"+role+".json"));
                Files.deleteIfExists(coordinator.resolve("join-minted-"+role+".json"));
                Files.deleteIfExists(coordinator.resolve("ready-"+role+".json"));
                mc.tell(()->{
                    try {
                        TerminalBrowserSession.close();if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());
                        joinRequested=false;passportRequested=false;worldReadyTicks=0;starting=false;
                        JsonObject row=status(mc);row.addProperty("ok",true);row.addProperty("normalDisconnect",true);
                        write(coordinator.resolve("result-"+role+"-"+id+".json"),row);
                    } catch(Exception error){throw new RuntimeException(error);}
                });return;
            }
            if(!MCEF.isInitialized())return;
            if(!starting&&mc.getOverlay()==null&&mc.screen instanceof TitleScreen){
                mc.options.renderDistance().set(3);mc.options.bobView().set(false);mc.options.setCameraType(CameraType.FIRST_PERSON);
                if(role.equals("host")&&!dedicated) {
                    starting=true;
                    mc.createWorldOpenFlows().createFreshLevel("minigames-private-qa",new LevelSettings("Minigames private lifecycle QA",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(987654321L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                } else if(Files.isRegularFile(coordinator.resolve("server-ready.json"))) {
                    if(dedicated){
                        if(!joinRequested){JsonObject request=new JsonObject();request.addProperty("uid",mc.getUser().getName());write(coordinator.resolve("mint-join-"+role+".json"),request);joinRequested=true;}
                        if(!Files.isRegularFile(coordinator.resolve("join-minted-"+role+".json")))return;
                    }
                    starting=true;
                    ConnectScreen.startConnecting(mc.screen,mc,new ServerAddress("127.0.0.1",port),new ServerData("Private minigames QA","127.0.0.1:"+port,ServerData.Type.LAN),false,null);
                }
                return;
            }
            if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
            if(!ready){
                if(mc.screen!=null){worldReadyTicks=0;return;}
                if(++worldReadyTicks<40)return;
            }
            if(role.equals("host")&&!dedicated&&!published){
                var server=mc.getSingleplayerServer();if(server==null)return;
                if(!server.getWorldData().getLevelName().equals("Minigames private lifecycle QA"))throw new IllegalStateException("Refusing to publish a non-QA world");
                // Ephemeral offline LAN test world only; this does not grant account/SSO trust.
                server.setUsesAuthentication(false);
                if(!server.publishServer(GameType.SURVIVAL,true,port))throw new IllegalStateException("LAN publish failed");
                JsonObject row=new JsonObject();row.addProperty("port",port);row.addProperty("isolatedWorld",true);row.addProperty("fakePlayers",false);
                row.addProperty("accountAuthentication","private-offline-LAN; not trusted account acceptance");
                write(coordinator.resolve("server-ready.json"),row);published=true;
            }
            if(dedicated&&!passportRequested){passportRequested=true;net.muxigame.core.client.TerminalPassportApi.request(value->{});}
            if(!ready){ready=true;write(coordinator.resolve("ready-"+role+".json"),status(mc));}
            if(command==null)return;
            String type=command.get("type").getAsString();
            if(type.equals("observe")){
                if(!role.equals("host"))throw new IllegalArgumentException("Only genuine integrated host observes its server");
                int id=lastCommand;command=null;
                mc.getSingleplayerServer().execute(()->{
                    try {
                        JsonObject row=new JsonObject();JsonArray players=new JsonArray();var server=mc.getSingleplayerServer();
                        for(var p:server.getPlayerList().getPlayers()){
                            JsonObject player=new JsonObject();player.addProperty("name",p.getGameProfile().getName());player.addProperty("uuid",p.getUUID().toString());
                            player.addProperty("dimension",p.level().dimension().location().toString());player.addProperty("x",p.getX());player.addProperty("y",p.getY());player.addProperty("z",p.getZ());
                            player.addProperty("inventory",p.getInventory().save(new net.minecraft.nbt.ListTag()).toString());player.addProperty("returnsPending",PlayerReturns.pending(p));
                            player.addProperty("connected",p.connection.isAcceptingMessages());player.addProperty("transport",p.connection.getConnection().getRemoteAddress().toString());
                            player.add("games",GameRuntime.get(server).snapshot(p,""));players.add(player);
                        }
                        row.add("players",players);row.addProperty("ok",true);row.addProperty("fakePlayers",false);write(coordinator.resolve("result-host-"+id+".json"),row);
                    }catch(Exception e){e.printStackTrace();}
                });return;
            }
            if(!mc.isWindowActive()){if(++delay>200)throw new IllegalStateException("Real window focus not obtained");return;}
            if(type.equals("open")){
                if(delay++==0){TerminalClient.openApp("games");return;}
                var state=TerminalBrowserSession.state();
                if(TerminalBrowserSession.content()==null||!state.rendered()||state.loading()||!TerminalBrowserSession.contentVisible())return;
                if(delay<60)return;
                JsonObject row=status(mc);row.addProperty("ok",true);row.addProperty("url",TerminalBrowserSession.content().getURL());write(coordinator.resolve("result-"+role+"-"+lastCommand+".json"),row);command=null;return;
            }
            var browser=TerminalBrowserSession.content();if(browser==null)throw new IllegalStateException("No real MCEF app");
            if(delay++==0){
                generation=TerminalBrowserSession.generation();
                String js="(async()=>{const q=request=>window.muxi.invoke(request);const wait=ms=>new Promise(r=>setTimeout(r,ms));let result;try{result={ok:true,value:await(async()=>{"+command.get("body").getAsString()+"})()};}catch(e){result={ok:false,error:String(e)};}result.url=location.href;result.text=document.body.innerText;result.generation="+generation+";document.documentElement.setAttribute('data-minigames-qa-"+lastCommand+"',btoa(unescape(encodeURIComponent(JSON.stringify(result)))));})()";
                browser.executeJavaScript(js,browser.getURL(),0);
            }
            if(sampled==null&&delay%5==0){
                int id=lastCommand;long epoch=generation;
                browser.getSource(source->{if(lastCommand!=id||TerminalBrowserSession.generation()!=epoch)return;var matcher=java.util.regex.Pattern.compile("data-minigames-qa-"+id+"=\\\"([^\\\"]+)\\\"").matcher(source);if(matcher.find())sampled=JsonParser.parseString(new String(Base64.getDecoder().decode(matcher.group(1)),StandardCharsets.UTF_8)).getAsJsonObject();});
            }
            if(sampled!=null){
                sampled.addProperty("realMcef",true);sampled.addProperty("realWindowFocus",mc.isWindowActive());
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of("command-"+lastCommand+".png"));}
                write(coordinator.resolve("result-"+role+"-"+lastCommand+".json"),sampled);command=null;
            }
        }catch(Throwable failure){
            failure.printStackTrace();try{JsonObject row=status(mc);row.addProperty("ok",false);row.addProperty("error",failure.toString());write(coordinator.resolve("fatal-"+role+".json"),row);}catch(Exception ignored){}
            closeNormally(mc);
        }finally{inTick=false;}
    }
}

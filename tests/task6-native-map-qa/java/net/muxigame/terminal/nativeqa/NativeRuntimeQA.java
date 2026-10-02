package net.muxigame.terminal.nativeqa;

import com.cinemamod.mcef.MCEF;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.muxigame.core.feature.dimensions.*;
import net.muxigame.core.feature.waystones.WaystoneMapNetwork;
import net.muxigame.core.feature.waystones.network.*;
import net.muxigame.core.client.waystones.*;
import net.muxigame.terminal.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.cef.browser.CefBrowser;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Only installed in an owned, offline QA lab. No product bridge or permission is replaced. */
@Mod(value="task6_native_qa",dist=Dist.CLIENT)
public final class NativeRuntimeQA {
    public static final ConcurrentMap<UUID,JsonObject> RECEIPTS=new ConcurrentHashMap<>();
    public static final ConcurrentMap<String,JsonObject> DRAWN=new ConcurrentHashMap<>();
    public static volatile int executed,completeEvents;
    public static volatile CompletableFuture<Either<Void,WaystoneTeleportError>> delayed;
    public static volatile boolean delayNext,prepareSeen;
    private final JsonObject report=new JsonObject(),checks=new JsonObject();
    private int ticks,stage,age;
    private boolean starting,done,entered;
    private volatile JsonObject dom;
    private volatile Throwable serverFailure;
    private CompletableFuture<Void> serverJob;
    private TerminalScreen terminal;
    private CefBrowser browser;
    private long generation;
    private String browserUrl;
    private UUID request;
    private Waystone source,target,cross,unactivated,share;
    private WorldPortals.Frame sourceGate;
    private JsonObject before;
    private final Path facility=Path.of("config/muxi-game-core/teleport-network.json");
    private static final String LONG_NAME="\u7f51\u7edc\u77f3\u7891\u539f\u59cb\u5b8c\u6574\u540d\u79f0\u00b7\u6e05\u6668\u4e16\u754c\u00b7ABCDEFGH\u00b7\u672b\u5c3e\u5fc5\u987b\u53ef\u89c1";

    public NativeRuntimeQA(){
        NeoForge.EVENT_BUS.addListener(this::tick);
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Prepare.class,event->{
            if(!delayNext)return;
            delayNext=false;prepareSeen=true;
            event.addPreparationTask(value->{delayed=new CompletableFuture<>();return delayed;});
        });
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Complete.class,event->completeEvents++);
    }
    public static void receipt(UUID req,UUID target,String status,String message){
        JsonObject result=new JsonObject();result.addProperty("request",req.toString());result.addProperty("target",target.toString());result.addProperty("status",status);result.addProperty("message",message);RECEIPTS.put(req,result);
    }
    public static void draw(xaero.map.mods.gui.Waypoint waypoint,boolean focused){
        JsonObject value=new JsonObject();value.addProperty("name",waypoint.getName());value.addProperty("focused",focused);
        var dimension=NativeStoneRenderer.viewDimension();value.addProperty("dimension",dimension==null?"":dimension.location().toString());
        value.addProperty("badge",WaystoneMapClient.portalLinked(dimension,new BlockPos(waypoint.getX(),waypoint.getY(),waypoint.getZ())));
        DRAWN.put(waypoint.getX()+"/"+waypoint.getY()+"/"+waypoint.getZ(),value);
    }
    private static Minecraft mc(){return Minecraft.getInstance();}
    private ServerPlayer actor(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private void server(Runnable action){
        serverJob=CompletableFuture.runAsync(()->{try{action.run();}catch(Throwable error){serverFailure=error;throw error;}},mc().getSingleplayerServer());
    }
    private boolean awaitServer(){if(serverFailure!=null)throw new IllegalStateException("Native server fixture failed",serverFailure);return serverJob==null||serverJob.isDone();}
    private void check(String name,boolean value){checks.addProperty(name,value);if(!value)throw new IllegalStateException("Native check failed: "+name);}
    private void next(){stage++;age=0;entered=false;dom=null;}
    private void capture(String tag)throws Exception{try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(Path.of(tag+".png"));}}
    private boolean map(){return mc().screen instanceof xaero.map.gui.GuiMap;}
    private void escape(){mc().screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);}
    private void openTerminal(){browser=TerminalBrowserSession.getOrCreate();terminal=new TerminalScreen((com.cinemamod.mcef.MCEFBrowser)browser);mc().setScreen(terminal);}
    private void script(CefBrowser target,String javascript){target.executeJavaScript(javascript,target.getURL(),0);}
    private void sample(CefBrowser target,String expression){
        int expected=stage;
        script(target,"(()=>{try{document.documentElement.setAttribute('data-task6-native',btoa(unescape(encodeURIComponent(JSON.stringify("+expression+")))));}catch(e){document.documentElement.setAttribute('data-task6-native',btoa(JSON.stringify({error:String(e)})));}})()");
        target.getSource(html->{
            if(stage!=expected)return;
            Matcher found=Pattern.compile("data-task6-native=\"([^\"]+)\"").matcher(html);
            if(found.find())try{dom=JsonParser.parseString(new String(Base64.getDecoder().decode(found.group(1)),StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception ignored){}
        });
    }
    private JsonObject position(ServerPlayer p){
        JsonObject value=new JsonObject();value.addProperty("dimension",p.serverLevel().dimension().location().toString());value.addProperty("x",p.getX());value.addProperty("y",p.getY());value.addProperty("z",p.getZ());value.addProperty("xp",p.totalExperience);value.addProperty("level",p.experienceLevel);value.addProperty("executed",executed);value.addProperty("completeEvents",completeEvents);return value;
    }
    private boolean unchanged(JsonObject old,JsonObject now){return old.get("dimension").equals(now.get("dimension"))&&old.get("x").equals(now.get("x"))&&old.get("y").equals(now.get("y"))&&old.get("z").equals(now.get("z"))&&old.get("xp").equals(now.get("xp"));}
    private void setupMove(ServerLevel level,double x,double z){ServerPlayer p=actor();p.teleportTo(level,x,64,z,Set.of(),0,0);p.setGameMode(GameType.SURVIVAL);p.giveExperiencePoints(Math.max(0,5000-p.totalExperience));}
    private Waystone stone(ServerLevel level,BlockPos pos,String name,boolean activated){
        floor(level,pos.getX(),pos.getZ());Waystone value=WaystonesAPI.placeWaystone(level,pos,new WaystoneStyle(ResourceLocation.fromNamespaceAndPath("waystones","waystone"))).orElseThrow();
        ((MutableWaystone)value).setName(Component.literal(name));if(activated)WaystonesAPI.activateWaystone(actor(),value);return value;
    }
    private void floor(ServerLevel level,int x,int z){for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){level.setBlock(new BlockPos(x+dx,63,z+dz),Blocks.STONE.defaultBlockState(),3);for(int dy=64;dy<=68;dy++){BlockPos pos=new BlockPos(x+dx,dy,z+dz);if(!(level.getBlockState(pos).getBlock() instanceof net.blay09.mods.waystones.block.WaystoneBlockBase))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}}}
    private WorldPortals.Frame gate(ServerLevel level,BlockPos origin,int destination){
        WorldPortals.Frame frame=new WorldPortals.Frame(origin,Direction.Axis.X,destination);
        for(int x=0;x<4;x++)for(int y=0;y<5;y++)level.setBlock(frame.at(x,y),(x==0||x==3||y==0||y==4)?Blocks.STONE.defaultBlockState():WorldPortals.block(destination).defaultBlockState().setValue(NetherPortalBlock.AXIS,Direction.Axis.X),3);
        if(!WorldPortals.valid(level,frame,true))throw new IllegalStateException("Real physical gate not valid");return frame;
    }
    private void registerSource(){
        try{Files.createDirectories(facility.getParent());JsonObject root=new JsonObject();root.addProperty("version",1);JsonArray list=new JsonArray();JsonObject entry=new JsonObject();entry.addProperty("dimension",source.getDimension().location().toString());JsonArray xyz=new JsonArray();BlockPos pos=sourceGate.at(1,1);xyz.add(pos.getX());xyz.add(pos.getY());xyz.add(pos.getZ());entry.add("portalPos",xyz);entry.addProperty("waystoneUid",source.getWaystoneUid().toString());list.add(entry);root.add("facilities",list);Files.writeString(facility,new Gson().toJson(root));}catch(Exception e){throw new RuntimeException(e);}
    }
    private void fixture(){
        Path gameDir=mc().gameDirectory.toPath().toAbsolutePath().normalize(),root=Path.of(System.getProperty("qa.ownerRoot")).toAbsolutePath().normalize();
        if(!gameDir.startsWith(root.resolve("qa-runtime"))||!gameDir.getFileName().toString().startsWith(System.getProperty("qa.mode")+"-"))throw new IllegalStateException("Refuse writes outside owned isolated world");
        ServerLevel home=mc().getSingleplayerServer().getLevel(Level.OVERWORLD),survival=mc().getSingleplayerServer().getLevel(WorldDimensions.OVERWORLD);
        if(survival==null)throw new IllegalStateException("Installed Core survival dimension missing");
        floor(home,100,100);setupMove(home,-1.5,0.5);
        source=stone(home,new BlockPos(0,64,0),"Source stone",true);
        target=stone(home,new BlockPos(20,64,0),LONG_NAME,true);
        unactivated=stone(home,new BlockPos(40,64,0),"Unactivated target",false);
        for(int i=0;i<8;i++)stone(home,new BlockPos(24+(i%4)*3,64,4+(i/4)*3),"Dense stone "+i+LONG_NAME,true);
        cross=stone(survival,new BlockPos(0,64,0),"Cross dimension stone",true);
        // Optional native placement is outside the UI/warp scenarios below.
        floor(home,50,0);share=WaystonesAPI.placeSharestone(home,new BlockPos(50,64,0),DyeColor.WHITE).orElse(null);
        report.addProperty("nativeSharestonePlacementAvailable",share!=null);
        report.addProperty("nativeSharestoneWarpCovered",false);
        if(share!=null)((MutableWaystone)share).setName(Component.literal("Native sharestone"));
        sourceGate=gate(home,new BlockPos(2,63,0),1);gate(home,new BlockPos(22,63,0),1);
        try{Files.deleteIfExists(facility);}catch(Exception error){throw new RuntimeException(error);}
        check("fixtureActualStones",NetworkPortalFacilities.actualStone(home,source)&&NetworkPortalFacilities.actualStone(home,target));
        check("playerGateBadgeOnly",NetworkPortalFacilities.portalBadge(actor().server,target,NetworkFacilityConfig.empty())&&!NetworkPortalFacilities.currentDimensionGateway(actor(),NetworkFacilityConfig.empty()));
    }
    private void send(Waystone value){request=UUID.randomUUID();PacketDistributor.sendToServer(new WaystoneMapNetwork.Warp(request,value.getWaystoneUid()));}
    private JsonObject finalReceipt(){JsonObject value=RECEIPTS.get(request);return value!=null&&!"pending".equals(value.get("status").getAsString())?value:null;}
    private void tick(ClientTickEvent.Post event){
        if(done)return;
        try{
            if(stage!=99 && Files.isRegularFile(Path.of("request-normal-close.json"))){
                JsonObject command=JsonParser.parseString(Files.readString(Path.of("request-normal-close.json"))).getAsJsonObject();
                if(command.get("pid").getAsLong()==ProcessHandle.current().pid()
                    && Path.of(command.get("lab").getAsString()).toAbsolutePath().normalize().equals(mc().gameDirectory.toPath().toAbsolutePath().normalize())){
                    report.addProperty("error","Bounded QA normal-close request");report.addProperty("failedStage",stage);report.addProperty("normalCloseRequested",true);
                    normalLogout();
                }
            }
            ticks++;age++;
            if(stage!=99 && (ticks>17000||(stage!=0&&age>1800)))throw new IllegalStateException("Native QA timeout stage "+stage);
            if(ticks%40==0){JsonObject progress=new JsonObject();progress.addProperty("stage",stage);progress.addProperty("age",age);progress.addProperty("screen",mc().screen==null?"":mc().screen.getClass().getName());progress.addProperty("executed",executed);Files.writeString(Path.of("runtime-progress.json"),new Gson().toJson(progress));}
            if(stage==99){if(age<80)return;capture("logout-title");report.addProperty("logoutScreen",mc().screen==null?"":mc().screen.getClass().getName());report.addProperty("success",!report.has("error"));finish();return;}
            if(!MCEF.isInitialized())return;
            if(!starting){
                if(mc().getOverlay()!=null||mc().screen==null)return;
                starting=true;mc().options.renderDistance().set(3);
                mc().createWorldOpenFlows().createFreshLevel("task6-native-map-qa",new LevelSettings("Task6 isolated native QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,false,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261002L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc().screen);return;
            }
            if(mc().player==null||mc().level==null||mc().getOverlay()!=null||mc().screen instanceof ReceivingLevelScreen)return;
            if(!awaitServer())return;
            switch(stage){
            case 0->{
                if(!entered){entered=true;report.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));report.addProperty("windowVisible",GLFW.glfwGetWindowAttrib(mc().getWindow().getWindow(),GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE);report.addProperty("modCount",ModList.get().size());server(this::fixture);return;}
                server(()->{setupMove(actor().server.getLevel(Level.OVERWORLD),100.5,100.5);check("outsideEntrance",NetworkPortalFacilities.source(actor(),NetworkPortalFacilities.config())==null);});next();
            }
            case 1->{if(age<60)return;if(!entered){entered=true;mc().setScreen(null);mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),GLFW.GLFW_KEY_M,0,GLFW.GLFW_PRESS,0);mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),GLFW.GLFW_KEY_M,0,GLFW.GLFW_RELEASE,0);return;}if(!map())return;check("ordinaryMOutsideEntrance",true);capture("01-ordinary-M-no-entrance");escape();server(()->setupMove(actor().server.getLevel(Level.OVERWORLD),-1.5,0.5));next();}
            case 2->{if(age<60)return;GLFW.glfwFocusWindow(mc().getWindow().getWindow());openTerminal();next();}
            case 3->{
                if(age%20==0)sample(browser,"({card:!!document.getElementById('nativeMapApp'),binding:typeof window.muxiTerminalQuery,url:location.href,loading:document.readyState})");
                if(dom==null||!dom.has("card")||!dom.get("card").getAsBoolean())return;
                check("realCefHomeAppCard",true);check("realCefOwnedBinding",dom.get("binding").getAsString().equals("function"));capture("02-home-X-app-card");
                Button x=terminal.children().stream().filter(v->v instanceof Button b&&b.getMessage().getString().equals("X")).map(v->(Button)v).findFirst().orElseThrow();
                check("XPixel18Button",x.getWidth()==18&&x.getHeight()==18);x.onPress();check("XRealCloseAction",mc().screen==null);next();
            }
            case 4->{if(age<20)return;if(!entered){entered=true;capture("03-X-closed");openTerminal();return;}if(age<60)return;generation=TerminalBrowserSession.generation();browserUrl=browser.getURL();script(browser,"document.getElementById('nativeMapApp').click()");next();}
            case 5->{
                if(!map())return;if(age<60)return;check("appOpensInstalledGuiMap",true);check("liveNativeNetworkChannel",WaystoneMapClient.supported());
                if(!WaystoneMapClient.nearSource())return;
                check("actualServerSnapshotNearSource",true);report.add("actualNativeDraws",new Gson().toJsonTree(DRAWN));capture("04-APP-native-map-stones");escape();check("homeReturnSameScreen",mc().screen==terminal);check("homeReturnSameGeneration",TerminalBrowserSession.generation()==generation);check("homeReturnSameBrowserUrl",browser.getURL().equals(browserUrl));next();
            }
            case 6->{if(age<40)return;TerminalBrowserSession.openApp("guide");next();}
            case 7->{
                if(TerminalBrowserSession.state().loading()||!TerminalBrowserSession.state().rendered())return;
                if(age<60)return;browser=TerminalBrowserSession.content();
                if(!entered){entered=true;
                    String child="if(typeof window.muxiTerminalQuery!=='function')parent.qaOwnedIframe={grant:'none',code:'undefined'};else window.muxiTerminalQuery({request:'map.open',persistent:false,onSuccess:r=>parent.qaOwnedIframe={grant:'PRIVILEGED',response:r},onFailure:(code,message)=>parent.qaOwnedIframe={grant:code===403?'none':'unexpected',code,message}})";
                    script(browser,"(()=>{let f=document.createElement('iframe');f.id='task6-owned-frame';f.style.display='none';f.onload=()=>f.contentWindow.eval("+new Gson().toJson(child)+");f.src='mod://muxi_terminal/terminal/index.html#task6-owned-frame';document.body.append(f);})()");return;
                }
                if(age%20==0)sample(browser,"({iframe:window.qaOwnedIframe})");
                if(dom==null||!dom.has("iframe"))return;
                report.add("ownedActualSubframe",dom.getAsJsonObject("iframe").deepCopy());check("ownedLocalSubframeNoNativeGrant",dom.getAsJsonObject("iframe").get("grant").getAsString().equals("none"));
                generation=TerminalBrowserSession.generation();browserUrl=browser.getURL();capture("05-native-guide-before-map");
                script(browser,"window.muxiTerminalQuery({request:'map.open',persistent:false,onSuccess:r=>document.documentElement.setAttribute('data-task6-open',r),onFailure:(c,m)=>document.documentElement.setAttribute('data-task6-open','failure:'+c+':'+m)})");next();
            }
            case 8->{if(!map())return;if(age<60)return;capture("06-guide-native-map");escape();check("guideReturnSameScreen",mc().screen==terminal);check("guideReturnSameBrowser",TerminalBrowserSession.content()==browser);check("guideReturnSameUrl",browser.getURL().equals(browserUrl));check("guideReturnSameGeneration",TerminalBrowserSession.generation()==generation);next();}
            case 9->{if(age<30)return;script(browser,"window.muxiTerminalQuery({request:'map.open',persistent:false,onSuccess:()=>{},onFailure:()=>{}})");next();}
            case 10->{if(!map())return;if(age<30)return;TerminalBrowserSession.home();escape();check("staleNavigationDoesNotRestoreOldScreen",mc().screen==null);capture("07-stale-return-no-terminal");openTerminal();TerminalBrowserSession.openWebApp(System.getProperty("qa.fixtureUrl")+"/redirect","Task6 external fixture");next();}
            case 11->{
                browser=TerminalBrowserSession.content();if(browser==null||TerminalBrowserSession.state().loading())return;
                if(age%20==0)sample(browser,"({main:window.qaMain,iframe:window.qaIframe,url:location.href})");
                if(dom==null||!dom.has("main")||!dom.has("iframe"))return;
                report.add("externalActualJavascript",dom.deepCopy());check("externalRedirectActualUrl",dom.get("url").getAsString().endsWith("/external"));
                check("externalMainNoNativeGrant",dom.getAsJsonObject("main").get("grant").getAsString().equals("none"));check("externalIframeNoNativeGrant",dom.getAsJsonObject("iframe").get("grant").getAsString().equals("none"));capture("08-external-redirect-iframe");TerminalBrowserSession.home();mc().setScreen(null);next();
            }
            case 12->{
                if(System.getProperty("qa.mode").equals("ui")){report.addProperty("scopeUiOnly",true);stage=31;age=0;return;}
                if(age<30)return;server(()->{before=position(actor());});next();
            }
            case 13->{if(age<30)return;send(unactivated);next();}
            case 14->{if(finalReceipt()==null)return;report.add("unactivatedReceipt",finalReceipt().deepCopy());check("realUnactivatedRejected",finalReceipt().get("status").getAsString().equals("failed"));server(()->check("unactivatedNoMoveNoFee",unchanged(before,position(actor()))));next();}
            case 15->{if(age<30)return;send(cross);next();}
            case 16->{if(finalReceipt()==null)return;report.add("unregisteredCrossReceipt",finalReceipt().deepCopy());check("unregisteredGateCannotCross",finalReceipt().get("status").getAsString().equals("failed"));server(()->{check("unregisteredCrossNoMoveNoFee",unchanged(before,position(actor())));registerSource();check("realCurrentDimRegisteredGate",NetworkPortalFacilities.currentDimensionGateway(actor(),NetworkPortalFacilities.config()));});next();}
            case 17->{if(age<30)return;send(target);next();}
            case 18->{
                if(finalReceipt()==null)return;report.add("sameDimensionReceipt",finalReceipt().deepCopy());check("realNativeSameDimCompleted",finalReceipt().get("status").getAsString().equals("completed"));
                server(()->{check("realNativeSameDimArrived",actor().serverLevel().dimension().equals(target.getDimension())&&actor().blockPosition().distSqr(target.getPos())<=256);report.add("sameDimensionAfter",position(actor()));check("sameDimOriginalNativeEvent",completeEvents>0);setupMove(actor().server.getLevel(Level.OVERWORLD),-1.5,0.5);});next();
            }
            case 19->{if(age<60)return;send(cross);next();}
            case 20->{
                if(finalReceipt()==null)return;report.add("registeredCrossReceipt",finalReceipt().deepCopy());check("realNativeCrossDimCompleted",finalReceipt().get("status").getAsString().equals("completed"));
                server(()->{check("realNativeCrossDimArrived",actor().serverLevel().dimension().equals(cross.getDimension())&&actor().blockPosition().distSqr(cross.getPos())<=256);report.add("crossDimensionAfter",position(actor()));setupMove(actor().server.getLevel(Level.OVERWORLD),-1.5,0.5);});next();
            }
            case 21->{if(age<60)return;server(()->{before=position(actor());prepareSeen=false;delayed=null;delayNext=true;});next();}
            case 22->{if(age<20)return;send(cross);next();}
            case 23->{
                if(!prepareSeen||delayed==null)return;check("actualWaystonesPrepareObserved",true);server(()->{actor().serverLevel().setBlock(sourceGate.at(0,0),Blocks.AIR.defaultBlockState(),3);check("actualGateBrokenDuringPrepare",!WorldPortals.valid(actor().serverLevel(),sourceGate,true));delayed.complete(Either.left(null));});next();
            }
            case 24->{if(finalReceipt()==null)return;report.add("asyncBrokenGateReceipt",finalReceipt().deepCopy());check("actualAsyncBrokenGateRejected",finalReceipt().get("status").getAsString().equals("failed"));server(()->{check("actualAsyncNoMoveNoFee",unchanged(before,position(actor())));sourceGate=gate(actor().serverLevel(),new BlockPos(2,63,0),1);});next();}
            case 25->{if(age<60)return;server(()->before=position(actor()));next();}
            case 26->{if(age<20)return;PacketDistributor.sendToServer(new WaystoneMapNetwork.Teleport(cross.getDimension().location(),cross.getPos()));next();}
            case 27->{if(age<100)return;server(()->{check("legacyRealPacketDelivered",executed>0);});next();}
            case 28->{if(age<20)return;server(()->{setupMove(actor().server.getLevel(Level.OVERWORLD),-1.5,0.5);try{Files.delete(facility);}catch(Exception e){throw new RuntimeException(e);}before=position(actor());});next();}
            case 29->{if(age<30)return;request=null;int old=executed;report.addProperty("legacyBeforeExecute",old);PacketDistributor.sendToServer(new WaystoneMapNetwork.Teleport(cross.getDimension().location(),cross.getPos()));next();}
            case 30->{if(age<80)return;server(()->{check("legacyGuardActualNoMoveNoFee",unchanged(before,position(actor())));check("legacyGuardActualPacketHandled",executed>report.get("legacyBeforeExecute").getAsInt());});next();}
            case 31->{
                report.addProperty("observedDrawCount",DRAWN.size());report.add("actualNativeDrawsFinal",new Gson().toJsonTree(DRAWN));
                check("actualNativeStoneFullNameDrawn",DRAWN.values().stream().anyMatch(v->v.get("name").getAsString().equals(LONG_NAME)));
                check("actualNativePortalBadgeDrawn",DRAWN.values().stream().anyMatch(v->v.get("badge").getAsBoolean()));
                report.addProperty("executedPacketCount",executed);report.addProperty("nativeCompleteEventCount",completeEvents);capture("09-before-normal-logout");normalLogout();
            }
            default->throw new IllegalStateException("Unexpected stage "+stage);
            }
        }catch(Throwable error){
            report.addProperty("error",error.toString());report.addProperty("failedStage",stage);report.addProperty("success",false);report.add("checks",checks);
            error.printStackTrace();
            try{capture("failure-stage-"+stage);Files.writeString(Path.of("native-map-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));java.io.StringWriter stack=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(stack));Files.writeString(Path.of("native-qa-failure-stack.txt"),stack.toString());}catch(Exception evidenceError){evidenceError.printStackTrace();}
            normalLogout();
        }
    }
    private void normalLogout(){
        stage=99;age=0;
        // tell queues even on the render thread; execute may run synchronously.
        // Enter closing state before any nested rendering or client events.
        mc().tell(()->mc().disconnect(new TitleScreen()));
    }
    private void finish()throws Exception{
        report.add("checks",checks);report.addProperty("testWorld","task6-native-map-qa");report.addProperty("instrumentation","Observe actual renderer/network; offline MCEF checksum only; test-only Waystones preparation delay; isolated world setup. No product bridge mock, no force teleport.");
        Files.writeString(Path.of("native-map-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));done=true;mc().stop();
    }
}

package net.muxigame.terminal.qa;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.muxigame.terminal.MuxiTerminal;
import net.muxigame.terminal.client.TerminalBrowserSession;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.util.*;

/** Private guns BEFORE any terminal, then the exact same guns AFTER holding it. */
final class GunDiagnosticRuntime {
 private static final String[] GUNS={"tacz:glock_17","tacz:ak47","ra1k:fn57","ra1k:mp155","ra1k:nl545","ra1k:rsh12","ra1k:vssk","ra1k:vulkan"};
 private final PhoenixVisibleQA before=new PhoenixVisibleQA("before-terminal",GUNS),after=new PhoenixVisibleQA("after-terminal",GUNS);
 private boolean starting,staging,finished;private volatile boolean staged;private int ticks,phase,frames;
 private static final JsonObject poses=new JsonObject();
 GunDiagnosticRuntime(){NeoForge.EVENT_BUS.addListener(this::tick);}
 private void tick(ClientTickEvent.Post event){
  if(finished)return;var mc=Minecraft.getInstance();
  try{
   if(++ticks>12000)throw new IllegalStateException("Gun diagnostics exceeded tick bound phase "+phase);
   if(ticks%100==0)Files.writeString(Path.of("runtime-progress.json"),"{\"phase\":"+phase+",\"ticks\":"+ticks+"}");
   if(!starting){if(mc.getOverlay()!=null||mc.screen==null)return;starting=true;mc.options.renderDistance().set(3);mc.options.bobView().set(false);mc.options.setCameraType(CameraType.FIRST_PERSON);
    mc.createWorldOpenFlows().createFreshLevel("gun-isolation-qa",new LevelSettings("Private gun before-after terminal diagnosis",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(987654321L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
   }
   if(phase==4){if(++frames<100)return;finish(null);mc.stop();return;}
   if(mc.player==null&&ticks%40==0&&(mc.screen instanceof ConfirmScreen||mc.screen instanceof BackupConfirmScreen))for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.active){b.onPress();break;}
   if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
   if(!staging){staging=true;mc.setScreen(null);var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var level=p.serverLevel();
    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=99;y<=105;y++)level.setBlock(new BlockPos(x,y,z),(y==99?Blocks.SMOOTH_STONE:Blocks.AIR).defaultBlockState(),3);
    level.setDayTime(6000);p.teleportTo(0.5,100,0.5);p.setYRot(180);p.setXRot(0);p.getInventory().clearContent();p.inventoryMenu.broadcastChanges();staged=true;
   });return;}
   if(!staged)return;mc.player.setYRot(180);mc.player.setXRot(0);
   if(phase==0){if(before.tick()){phase=1;frames=0;}return;}
   if(phase==1){
    if(frames++==0){var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);p.getInventory().clearContent();p.getInventory().selected=0;p.getInventory().setItem(0,new ItemStack(MuxiTerminal.PLAYER_TERMINAL.get()));p.inventoryMenu.broadcastChanges();});}
    if(frames<100)return;if(!mc.player.getMainHandItem().is(MuxiTerminal.PLAYER_TERMINAL.get()))throw new IllegalStateException("Terminal control did not sync");
    try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of("terminal-control.png"));}pose("terminal-control");TerminalBrowserSession.close();phase=2;frames=0;return;
   }
   if(phase==2){if(after.tick()){phase=3;frames=0;}return;}
   if(phase==3){TerminalBrowserSession.close();mc.level.disconnect();mc.disconnect(new TitleScreen());phase=4;frames=0;}
  }catch(Throwable e){e.printStackTrace();try{finish(e);}catch(Exception ignored){}mc.stop();}
 }
 private void finish(Throwable failure)throws Exception{finished=true;var report=new JsonObject();report.addProperty("completed",failure==null);report.addProperty("diagnosticOnly",true);report.addProperty("visualAccepted",false);report.addProperty("mode",System.getProperty("qa.mode"));report.addProperty("phase",phase);if(failure!=null)report.addProperty("error",failure.toString());var mods=new JsonObject();for(var mod:ModList.get().getMods())if(Set.of("yes_steve_model","firstperson","tacz","skinlayers3d","notenoughanimations","playeranimator","sway","audio_improvements","sound_physics_remastered","muxi_terminal").contains(mod.getModId()))mods.addProperty(mod.getModId(),mod.getVersion().toString());report.add("renderModVersions",mods);report.add("modelPoses",poses);Files.writeString(Path.of("runtime-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
 static void pose(String label)throws Exception{var mc=Minecraft.getInstance();if(mc.player==null)return;var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);var model=renderer.getModel();var row=new JsonObject();row.addProperty("renderer",renderer.getClass().getName());row.addProperty("model",model.getClass().getName());row.add("rightArm",part(model.rightArm));row.add("leftArm",part(model.leftArm));row.add("rightSleeve",part(model.rightSleeve));row.add("leftSleeve",part(model.leftSleeve));poses.add(label,row);Files.writeString(Path.of("gun-model-poses.json"),new GsonBuilder().setPrettyPrinting().create().toJson(poses));}
 private static JsonObject part(ModelPart p){var r=new JsonObject();r.addProperty("x",p.x);r.addProperty("y",p.y);r.addProperty("z",p.z);r.addProperty("xRot",p.xRot);r.addProperty("yRot",p.yRot);r.addProperty("zRot",p.zRot);r.addProperty("visible",p.visible);return r;}
}

package net.muxigame.terminal.nativeqa.mixin;
import com.google.gson.*;
import net.blay09.mods.waystones.api.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.waystones.network.NetworkPortalFacilities;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.nio.file.*;
import java.util.function.*;
import java.util.concurrent.CompletableFuture;
/** Read-only private QA; preserves lifecycle, guard, ticket and future results. */
@Mixin(targets="net.muxigame.core.feature.waystones.network.NetworkTargetPreparation",remap=false)
public abstract class TargetPreparationObservationMixin {
 @Inject(method="await(Ljava/lang/Object;Lnet/minecraft/server/MinecraftServer;Ljava/util/function/Supplier;Ljava/util/function/BooleanSupplier;Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",at=@At("HEAD"))
 private static void qa$before(Object key,MinecraftServer server,Supplier<String> invalid,BooleanSupplier ready,Runnable wake,CallbackInfoReturnable<CompletableFuture<String>> cir){
  if(key instanceof WaystoneTeleportContext ctx)record("native-full-before-wake",ctx,server,null);
 }
 @Inject(method="finish",at=@At("HEAD"))
 private static void qa$finish(@Coerce Object wait,String reason,CallbackInfo ci){
  try{
   var key=wait.getClass().getDeclaredField("key");key.setAccessible(true);
   var server=wait.getClass().getDeclaredField("server");server.setAccessible(true);
   if(key.get(wait) instanceof WaystoneTeleportContext ctx)record("native-preparation-finish",ctx,(MinecraftServer)server.get(wait),reason);
  }catch(Exception error){throw new RuntimeException(error);}
 }
 private static void record(String phase,WaystoneTeleportContext ctx,MinecraftServer server,String reason){
  try{
   var json=new JsonObject();json.addProperty("phase",phase);json.addProperty("thread",Thread.currentThread().getName());json.addProperty("reason",reason);
   var stone=ctx.getTargetWaystone();var level=server.getLevel(stone.getDimension());
   json.addProperty("dimension",stone.getDimension().location().toString());json.addProperty("expectedUid",stone.getWaystoneUid().toString());
   if(level!=null){
    var idle=ServerLevel.class.getDeclaredField("emptyTime");idle.setAccessible(true);json.addProperty("emptyTime",idle.getInt(level));
    var fresh=Level.class.getDeclaredField("freshBlockEntities");fresh.setAccessible(true);json.addProperty("freshBlockEntities",((java.util.Collection<?>)fresh.get(level)).size());
    json.addProperty("chunkLoaded",level.hasChunkAt(stone.getPos()));json.addProperty("physicalExactUid",NetworkPortalFacilities.actualStone(level,stone));
    if(level.hasChunkAt(stone.getPos())){var local=WaystonesAPI.getWaystoneAt(level,stone.getPos()).orElse(null);json.addProperty("localValid",local!=null&&local.isValid());json.addProperty("localUid",local==null?"":local.getWaystoneUid().toString());}
   }
   Files.writeString(Path.of(System.getProperty("qa.ownerRoot"),"native-target-preparation-observed.jsonl"),new Gson().toJson(json)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  }catch(Exception error){throw new RuntimeException(error);}
 }
}

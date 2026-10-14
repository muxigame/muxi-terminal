package net.muxigame.terminal.nativeqa.mixin;
import com.google.gson.*;
import net.blay09.mods.waystones.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.feature.waystones.network.NetworkTeleportGuard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.file.*;
import java.util.*;
/** QA-only observation: never changes a guard result or loads an unloaded chunk. */
@Mixin(value=NetworkTeleportGuard.class,remap=false)
public abstract class GuardDiagnosticMixin {
 @Inject(method="denial(Lnet/blay09/mods/waystones/api/WaystoneTeleportContext;Z)Ljava/lang/String;",at=@At("RETURN"),require=1)
 private static synchronized void qa$reason(WaystoneTeleportContext context,boolean prepared,CallbackInfoReturnable<String> cir){
  if(cir.getReturnValue()==null || !(context.getEntity() instanceof ServerPlayer p))return;
  try{
   var json=new JsonObject();json.addProperty("reason",cir.getReturnValue());json.addProperty("prepared",prepared);
   json.addProperty("thread",Thread.currentThread().getName());json.addProperty("sourceDimension",p.serverLevel().dimension().location().toString());
   var original=context.getTargetWaystone();var stone=WaystonesAPI.getWaystone(p.server,original.getWaystoneUid()).orElse(null);
   json.addProperty("contextUid",original.getWaystoneUid().toString());json.addProperty("contextValid",original.isValid());
   json.addProperty("registryPresent",stone!=null);
   if(stone!=null){
    json.addProperty("registryValid",stone.isValid());json.addProperty("targetDimension",stone.getDimension().location().toString());json.addProperty("targetPosition",stone.getPos().toShortString());
    var level=p.server.getLevel(stone.getDimension());json.addProperty("levelPresent",level!=null);
    if(level!=null){boolean loaded=level.hasChunkAt(stone.getPos());json.addProperty("chunkLoaded",loaded);
     if(loaded){json.addProperty("block",level.getBlockState(stone.getPos()).toString());var be=level.getBlockEntity(stone.getPos());json.addProperty("blockEntity",be==null?"null":be.getClass().getName());
      var local=WaystonesAPI.getWaystoneAt(level,stone.getPos()).orElse(null);json.addProperty("localPresent",local!=null);if(local!=null){json.addProperty("localUid",local.getWaystoneUid().toString());json.addProperty("localValid",local.isValid());}}
    }
   }
   var stack=new JsonArray();for(var f:Thread.currentThread().getStackTrace())if(f.getClassName().contains("Waystone")||f.getClassName().contains("NetworkTeleport"))stack.add(f.toString());json.add("stack",stack);
   Path root=Path.of(System.getProperty("qa.ownerRoot")).toAbsolutePath().normalize();if(!Path.of("").toAbsolutePath().normalize().equals(root))throw new IllegalStateException("Own lab required");
   Files.writeString(root.resolve("guard-diagnostic.jsonl"),new Gson().toJson(json)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  }catch(Exception error){throw new IllegalStateException("QA guard observation failed",error);}
 }
}

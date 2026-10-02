package net.muxigame.terminal.nativeqa.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.feature.waystones.WaystoneMapNetwork;
import net.muxigame.terminal.nativeqa.NativeRuntimeQA;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;
@Mixin(value=WaystoneMapNetwork.class,remap=false)
public abstract class NetworkProbeMixin {
 @Inject(method="execute",at=@At("HEAD"),require=1)
 private static void qa$packet(ServerPlayer player,UUID request,UUID target,CallbackInfo info){NativeRuntimeQA.executed++;}
 @Inject(method="finish",at=@At("HEAD"),require=1)
 private static void qa$receipt(ServerPlayer player,@Coerce Object state,UUID request,UUID target,String status,String message,CallbackInfo info){NativeRuntimeQA.receipt(request,target,status,message);}
}

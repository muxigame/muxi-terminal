package net.muxigame.localdebug.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Minecraft.class) public abstract class SettingsLifecycleMixin {
 @Inject(method="reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"))
 void reload(CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> ci){net.muxigame.localdebug.SettingsProbe.reloadRequests.incrementAndGet();ci.getReturnValue().whenComplete((v,e)->{if(e==null)net.muxigame.localdebug.SettingsProbe.reloadCompleted.incrementAndGet();});}
 @Inject(method="close",at=@At("HEAD")) void closing(CallbackInfo ci){System.out.println("NATIVE_QA_CLOSE_HEAD_CEF "+org.cef.CefApp.getState()+" initialized="+com.cinemamod.mcef.MCEF.isInitialized());}
 @Inject(method="close",at=@At("RETURN")) void closed(CallbackInfo ci){System.out.println("NATIVE_QA_CLOSE_RETURN_CEF "+org.cef.CefApp.getState()+" initialized="+com.cinemamod.mcef.MCEF.isInitialized());}
}

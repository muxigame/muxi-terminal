package net.muxigame.terminal.albumqa.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.muxigame.terminal.client.TerminalCameraScreen;
import net.muxigame.terminal.albumqa.YsmRenderProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observes installed YSM 2.6.5 player drawing; never changes renderer behavior. */
@Mixin(targets="com.elfmcys.yesstevemodel.Oo00o00OOooooO0OooooOooO",remap=false)
public abstract class YsmRenderQAMixin {
 @Inject(method="oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/world/entity/player/Player;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"))
 private void qa$entered(Player player,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){if(selfie(player))YsmRenderProbe.selfieCalls++;}
 @Inject(method="oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/world/entity/player/Player;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lcom/elfmcys/yesstevemodel/Oo00o00OOooooO0OooooOooO;oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/O0OOoooOOoOo0O00O0oOoo0O;Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",shift=At.Shift.AFTER))
 private void qa$drawn(Player player,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){if(selfie(player))YsmRenderProbe.selfieRendered++;}
 private static boolean selfie(Player player){var mc=Minecraft.getInstance();return mc.screen instanceof TerminalCameraScreen && player==mc.player;}
}

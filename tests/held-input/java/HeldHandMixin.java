package net.muxigame.terminal.heldqa.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.muxigame.terminal.heldqa.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.muxigame.terminal.client.TerminalHandRenderer",remap=false)
public abstract class HeldHandMixin {
 @Inject(method="render",at=@At("HEAD"))private void hand(PoseStack stack,MultiBufferSource buffers,int light,float swing,float equip,boolean main,CallbackInfo ci){if(HeldFiles.enabled()){if(main)HeldProbe.mainDraws++;else HeldProbe.offDraws++;}}
 @Inject(method="renderArm",at=@At("HEAD"))private void arm(PoseStack stack,MultiBufferSource buffers,int light,float side,float equip,float sway,float bob,boolean two,CallbackInfo ci){if(HeldFiles.enabled()){if(two)HeldProbe.twoArmDraws++;else HeldProbe.oneArmDraws++;}}
}

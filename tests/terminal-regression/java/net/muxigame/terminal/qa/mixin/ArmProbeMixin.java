package net.muxigame.terminal.qa.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.muxigame.terminal.qa.TerminalRuntimeQA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PlayerRenderer.class,remap=false)
public abstract class ArmProbeMixin {
 @Inject(method="renderRightHand",at=@At("RETURN"))
 private void qa$right(PoseStack stack,MultiBufferSource buffers,int light,AbstractClientPlayer player,CallbackInfo ci){TerminalRuntimeQA.arm(stack,((PlayerRenderer)(Object)this).getModel().rightArm,light);}
 @Inject(method="renderLeftHand",at=@At("RETURN"))
 private void qa$left(PoseStack stack,MultiBufferSource buffers,int light,AbstractClientPlayer player,CallbackInfo ci){TerminalRuntimeQA.arm(stack,((PlayerRenderer)(Object)this).getModel().leftArm,light);}
}

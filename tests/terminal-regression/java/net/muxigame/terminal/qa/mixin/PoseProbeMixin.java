package net.muxigame.terminal.qa.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.muxigame.terminal.qa.TerminalRuntimeQA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.muxigame.terminal.client.TerminalHandRenderer",remap=false)
public abstract class PoseProbeMixin {
 @Inject(method="render",at=@At("HEAD"))
 private void qa$before(PoseStack stack,MultiBufferSource buffers,int light,float swing,float equip,boolean main,CallbackInfo ci){TerminalRuntimeQA.before(stack);}
 @Inject(method="render",at=@At("RETURN"))
 private void qa$after(PoseStack stack,MultiBufferSource buffers,int light,float swing,float equip,boolean main,CallbackInfo ci){TerminalRuntimeQA.after(stack);}
}

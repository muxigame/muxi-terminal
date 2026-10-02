package net.muxigame.terminal.albumqa.mixin;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=Screenshot.class,remap=false)
public abstract class ScreenshotProbeMixin {
 @Inject(method="takeScreenshot",at=@At("RETURN"))
 private static void album$record(com.mojang.blaze3d.pipeline.RenderTarget target,CallbackInfoReturnable<NativeImage> result){net.muxigame.terminal.albumqa.ImageProbe.acquired(result.getReturnValue());}
}

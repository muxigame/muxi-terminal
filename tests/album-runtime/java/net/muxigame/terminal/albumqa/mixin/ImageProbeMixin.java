package net.muxigame.terminal.albumqa.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=NativeImage.class,remap=false)
public abstract class ImageProbeMixin {
 @Inject(method="close",at=@At("HEAD"))
 private void album$close(CallbackInfo info){net.muxigame.terminal.albumqa.ImageProbe.closed(this);}
}

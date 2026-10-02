package net.muxigame.terminal.albumqa.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import net.muxigame.terminal.albumqa.ResourceProbe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=NativeImage.class,remap=false)
public abstract class ImageResourceQAMixin {
    @Shadow private long pixels;
    @Shadow @Final private long size;
    @Inject(method={"<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V","<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZJ)V"},at=@At("RETURN"))
    private void qa$allocate(CallbackInfo ci){ResourceProbe.image(pixels,size);}
    @Inject(method="close",at=@At("HEAD"))
    private void qa$close(CallbackInfo ci){ResourceProbe.close(pixels);}
}

package net.muxigame.terminal.heldqa.mixin;
import com.cinemamod.mcef.MCEFRenderer;
import net.muxigame.terminal.heldqa.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MCEFRenderer.class,remap=false)
public abstract class HeldRendererMixin {
 @Shadow public abstract int getTextureID();
 @Inject(method="initialize",at=@At("RETURN"))private void texture(CallbackInfo ci){if(HeldFiles.enabled())HeldProbe.textures.add(getTextureID());}
}

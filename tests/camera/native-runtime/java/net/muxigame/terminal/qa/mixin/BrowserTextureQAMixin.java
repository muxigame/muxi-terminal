package net.muxigame.terminal.qa.mixin;
import com.cinemamod.mcef.MCEFRenderer;
import net.muxigame.terminal.qa.ResourceProbe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MCEFRenderer.class,remap=false)
public abstract class BrowserTextureQAMixin {
    @Shadow public abstract int getTextureID();
    @Inject(method="initialize",at=@At("RETURN"))
    private void qa$texture(CallbackInfo ci){ResourceProbe.texture(getTextureID());}
    @Inject(method="cleanup",at=@At("HEAD"))
    private void qa$close(CallbackInfo ci){ResourceProbe.closeTexture(getTextureID());}
}

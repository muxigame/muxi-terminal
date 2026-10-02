package net.muxigame.terminal.nativeqa.mixin;
import net.minecraft.client.gui.GuiGraphics;
import net.muxigame.core.client.waystones.NativeStoneRenderer;
import net.muxigame.terminal.nativeqa.NativeRuntimeQA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.mods.gui.Waypoint;
@Mixin(value=NativeStoneRenderer.class,remap=false)
public abstract class RendererProbeMixin {
 @Inject(method="draw",at=@At("HEAD"),require=1)
 private static void qa$nativeDraw(Waypoint waypoint,boolean focused,float scale,double x,double y,GuiGraphics graphics,CallbackInfo info){NativeRuntimeQA.draw(waypoint,focused);}
}

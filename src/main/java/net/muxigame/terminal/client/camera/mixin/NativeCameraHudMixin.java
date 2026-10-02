package net.muxigame.terminal.client.camera.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.muxigame.terminal.client.TerminalCameraScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GameRenderer also invokes this outside the cancellable HUD layer. Never changes the autosave option. */
@Mixin(value=Gui.class,remap=false)
public abstract class NativeCameraHudMixin {
    @Inject(method="renderSavingIndicator(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",at=@At("HEAD"),cancellable=true)
    private void muxi$nativeCamera(GuiGraphics graphics,DeltaTracker delta,CallbackInfo callback){
        if(Minecraft.getInstance().screen instanceof TerminalCameraScreen)callback.cancel();
    }
}

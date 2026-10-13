package net.muxigame.terminal.client.compat.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Post-apply global-kill guard plus graceful shutdown before vanilla destroys GL. */
@Mixin(value=Minecraft.class,priority=1)
public abstract class MinecraftMcefShutdownGuardMixin {
    @Inject(method="close",at=@At("HEAD"))
    private void terminal$closeCef(CallbackInfo ci) {
        net.muxigame.terminal.client.compat.TerminalMcefShutdown.close();
    }
}

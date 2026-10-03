package net.muxigame.terminal.client.input.mixin;

import net.minecraft.client.KeyboardHandler;
import net.muxigame.terminal.client.TerminalHeldInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class HeldTerminalKeyboardMixin {
    @Inject(method="keyPress", at=@At("HEAD"), cancellable=true)
    private void muxi$heldTerminal(long window, int key, int scan, int action, int modifiers, CallbackInfo ci) {
        if (TerminalHeldInput.key(window, key, scan, action, modifiers)) ci.cancel();
    }
}

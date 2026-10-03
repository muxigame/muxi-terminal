package net.muxigame.terminal.heldqa.mixin;
import net.minecraft.client.Minecraft;
import net.muxigame.terminal.heldqa.*;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.muxigame.terminal.client.TerminalNativeBridge$Handler",remap=false)
public abstract class HeldBridgeMixin {
 @Inject(method="dispatch",at=@At("HEAD"),cancellable=true)private void probe(Minecraft mc,CefBrowser browser,CefFrame frame,String request,CefQueryCallback reply,CallbackInfo ci){if(HeldFiles.enabled()&&request.startsWith("heldqa:")){HeldProbe.dom(request.substring(7));reply.success("{}");ci.cancel();}}
}

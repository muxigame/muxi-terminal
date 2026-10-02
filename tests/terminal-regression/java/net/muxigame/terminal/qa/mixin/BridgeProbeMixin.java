package net.muxigame.terminal.qa.mixin;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.muxigame.terminal.client.TerminalNativeBridge$Handler",remap=false)
public abstract class BridgeProbeMixin {
 @Inject(method="onQuery",at=@At("HEAD"))
 private void qa$query(CefBrowser browser,CefFrame frame,long id,String request,boolean persistent,CefQueryCallback callback,CallbackInfoReturnable<Boolean> ci){
  if(request.startsWith("tasks.")||request.startsWith("resource.data:"))System.out.println("QA_BRIDGE received "+request+" id="+id+" frame="+frame.getURL());
 }
}

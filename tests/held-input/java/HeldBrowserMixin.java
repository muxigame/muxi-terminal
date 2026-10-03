package net.muxigame.terminal.heldqa.mixin;
import com.cinemamod.mcef.MCEFBrowser;
import net.muxigame.terminal.heldqa.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MCEFBrowser.class,remap=false)
public abstract class HeldBrowserMixin {
 @Inject(method="sendKeyPress",at=@At("HEAD"))private void press(int key,long scan,int mods,CallbackInfo ci){HeldProbe.key(this,key,1);}
 @Inject(method="sendKeyRelease",at=@At("HEAD"))private void release(int key,long scan,int mods,CallbackInfo ci){HeldProbe.key(this,key,0);}
 @Inject(method="onPaint",at=@At("HEAD"))private void paint(org.cef.browser.CefBrowser browser,boolean popup,java.awt.Rectangle[] dirty,java.nio.ByteBuffer buffer,int width,int height,CallbackInfo ci){if(HeldFiles.enabled()&&!popup)HeldProbe.paints++;}
}

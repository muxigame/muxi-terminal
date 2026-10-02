package net.muxigame.terminal.qa.mixin;

import net.minecraft.client.Minecraft;
import net.muxigame.terminal.client.TerminalAlbumBridge;
import net.muxigame.terminal.client.TerminalBrowserSession;
import org.cef.browser.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** QA-only observation. Does not retain callbacks, grant authority, or alter return values. */
@Mixin(value=TerminalAlbumBridge.class,remap=false)
public abstract class AlbumAuthorityQAMixin {
    @Inject(method="authorized",at=@At("RETURN"),require=1)
    private static void qa$observe(CefBrowser browser,CefFrame frame,CallbackInfoReturnable<Boolean> result){
        if(Boolean.TRUE.equals(result.getReturnValue()))return;
        var mc=Minecraft.getInstance();
        System.out.println("QA_ALBUM_AUTHORITY_FALSE thread="+Thread.currentThread().getName()+" epoch="+TerminalBrowserSession.generation()+" kind="+TerminalBrowserSession.state().kind()+" contentOwner="+(browser==TerminalBrowserSession.content())+" windowActive="+mc.isWindowActive()+" screen="+(mc.screen==null?"null":mc.screen.getClass().getSimpleName())+" levelPresent="+(mc.level!=null)+" playerPresent="+(mc.player!=null)+" browserUrl="+(browser==null?"null":browser.getURL())+" frameValid="+(frame!=null && frame.isValid())+" frameMain="+(frame!=null && frame.isMain())+" frameUrl="+(frame==null?"null":frame.getURL()));
    }
}

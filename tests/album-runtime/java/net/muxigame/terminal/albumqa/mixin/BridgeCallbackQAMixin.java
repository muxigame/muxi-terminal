package net.muxigame.terminal.albumqa.mixin;
import org.cef.callback.CefQueryCallback;
import net.muxigame.terminal.albumqa.ResourceProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Counts completion while passing all callbacks/values to the real native bridge unchanged. */
@Mixin(targets="net.muxigame.terminal.client.TerminalNativeBridge$Handler",remap=false)
public abstract class BridgeCallbackQAMixin {
 @ModifyVariable(method="onQuery",at=@At("HEAD"),argsOnly=true)
 private CefQueryCallback qa$callback(CefQueryCallback callback){
  ResourceProbe.queriesStarted.incrementAndGet();return new CefQueryCallback(){
   private final java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();
   private void settle(){if(done.compareAndSet(false,true))ResourceProbe.queriesSettled.incrementAndGet();}
   public void success(String value){try{callback.success(value);}finally{settle();}}
   public void failure(int code,String value){try{callback.failure(code,value);}finally{settle();}}
  };
 }
}

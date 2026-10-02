package net.muxigame.terminal.qa.mixin;
import net.minecraft.SystemReport;
import oshi.SystemInfo;
import oshi.util.GlobalConfig;
import oshi.util.platform.windows.WmiQueryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** QA-only hardware diagnostics deadline, based on the existing task14 bounded hardware query. */
@Mixin(value=SystemReport.class,remap=false)
public abstract class HardwareWmiTimeoutQAMixin {
 @Inject(method="putHardware(Loshi/SystemInfo;)V",at=@At("HEAD"),require=1)
 private void qa$boundedHardware(SystemInfo info,CallbackInfo ci){
  GlobalConfig.set(GlobalConfig.OSHI_UTIL_WMI_TIMEOUT,2000);
  int actual=WmiQueryHandler.createInstance().getWmiTimeout();
  if(actual!=2000)throw new IllegalStateException("QA hardware deadline not applied: "+actual);
  System.out.println("TASK6_QA_HARDWARE_WMI_TIMEOUT_MS="+actual);
 }
}

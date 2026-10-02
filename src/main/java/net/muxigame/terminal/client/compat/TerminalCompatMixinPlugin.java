package net.muxigame.terminal.client.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Runs after injection passes; avoids loading Minecraft/MCEF during transformer setup. */
public final class TerminalCompatMixinPlugin implements IMixinConfigPlugin {
    private static final String MARKER="net.muxigame.terminal.client.compat.mixin.MinecraftMcefShutdownGuardMixin";
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){return true;}
    @Override public void acceptTargets(Set<String> myTargets,Set<String> otherTargets){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo info){}
    @Override public void postApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo info){
        if(!MARKER.equals(mixinClassName))return;
        int count=McefShutdownGuard.patch(targetClass);
        String status=count==1?"disabled:1":"not-present";
        System.setProperty("muxi_terminal.mcef_shutdown_guard",status);
        System.getLogger("MuxiTerminal/McefShutdownGuard").log(System.Logger.Level.INFO,
            "MCEF global Windows helper-kill hook: {0}; normal per-JVM shutdown remains unchanged",status);
    }
}

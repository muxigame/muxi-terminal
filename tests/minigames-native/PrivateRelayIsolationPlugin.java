package net.muxigame.terminal.qa.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** QA-only isolation of e4mc's public relay; not a candidate LAN compatibility fix. */
public final class PrivateRelayIsolationPlugin implements IMixinConfigPlugin {
    public void onLoad(String packageName) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) { return true; }
    public void acceptTargets(Set<String> ours, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("PrivateRelayIsolationMixin")) return;
        int guarded = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.endsWith("startTcpServerListenerInject")
                    || !method.desc.equals("(Ljava/net/InetAddress;ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")) continue;
            if (!sourceTagged(method.visibleAnnotations) && !sourceTagged(method.invisibleAnnotations)) continue;
            method.instructions.clear();
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.maxStack = 0;
            guarded++;
        }
        if (guarded != 1) throw new IllegalStateException("Private relay isolation expected one source-tagged e4mc hook, found " + guarded);
        System.err.println("PRIVATE_QA_E4MC_PUBLIC_RELAY_ISOLATED hooks=" + guarded + " candidateLanAcceptance=false");
    }
    private static boolean sourceTagged(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1).equals("link.e4mc.mixin.ServerConnectionListenerMixin")) return true;
            }
        }
        return false;
    }
}

package net.muxigame.terminal.client.compat;

import java.util.HashSet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Suppresses only MCEF's merged Windows process-name kill. No runtime process commands. */
public final class McefShutdownGuard {
    private static final String SOURCE="com.cinemamod.mcef.mixins.CefWindowsShutdownMixin";
    private static final String MERGED="Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String CALLBACK="(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private McefShutdownGuard(){}

    public static int patch(ClassNode target){
        if(!"net/minecraft/client/Minecraft".equals(target.name))
            throw new IllegalArgumentException("MCEF close guard target is not Minecraft");
        int changed=0;
        for(MethodNode method:target.methods){
            if(!fromMcef(method))continue;
            var constants=new HashSet<String>();boolean processStart=false;
            for(AbstractInsnNode instruction:method.instructions){
                if(instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String s)constants.add(s);
                if(instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/ProcessBuilder")
                        && call.name.equals("start") && call.desc.equals("()Ljava/lang/Process;"))processStart=true;
            }
            // An upstream handler without process-name killing needs no modification.
            if(!constants.contains("taskkill") || !constants.contains("/IM"))continue;
            if(!CALLBACK.equals(method.desc) || (method.access&Opcodes.ACC_STATIC)!=0 || !processStart
                    || !constants.contains("/F") || !constants.contains("jcef_helper.exe") || !constants.contains("tasklist"))
                throw new IllegalStateException("Unsupported MCEF process-name shutdown hook: "+method.name+method.desc);
            if(++changed>1)throw new IllegalStateException("Multiple unsafe MCEF close handlers; refusing ambiguous transform");
            method.instructions.clear();method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
            method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
            method.maxStack=0;method.maxLocals=2;
        }
        return changed;
    }

    private static boolean fromMcef(MethodNode method){
        if(method.visibleAnnotations==null)return false;
        for(AnnotationNode annotation:method.visibleAnnotations){
            if(!MERGED.equals(annotation.desc) || annotation.values==null)continue;
            for(int i=0;i+1<annotation.values.size();i+=2)
                if("mixin".equals(annotation.values.get(i)) && SOURCE.equals(annotation.values.get(i+1)))return true;
        }
        return false;
    }
}

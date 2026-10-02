package net.muxigame.terminal.compatqa;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import net.muxigame.terminal.client.compat.McefShutdownGuard;

/** Actual installed MCEF handler bytecode fixture; no unsafe handler is ever executed. */
public final class ShutdownGuardTests {
 private static int checks;
 private static final String OWNER="com.cinemamod.mcef.mixins.CefWindowsShutdownMixin";
 private static final String DESC="(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 private static ClassNode fixture(byte[] mcef,String owner,boolean annotate){
  var source=new ClassNode();new ClassReader(mcef).accept(source,0);
  var method=source.methods.stream().filter(m->m.name.equals("close")&&m.desc.equals(DESC)).findFirst().orElseThrow();
  var target=new ClassNode();target.version=Opcodes.V21;target.access=Opcodes.ACC_PUBLIC;target.name="net/minecraft/client/Minecraft";target.superName="java/lang/Object";
  method.name="handler$variedRuntimeName$close";method.visibleAnnotations=new ArrayList<>();method.invisibleAnnotations=null;
  if(annotate){var merged=new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");merged.values=new ArrayList<>(List.of("mixin",owner,"priority",1000));method.visibleAnnotations.add(merged);}
  target.methods.add(method);
  var ordinary=new MethodNode(Opcodes.ACC_PUBLIC,"unrelated","()I",null,null);ordinary.instructions.add(new IntInsnNode(Opcodes.BIPUSH,42));ordinary.instructions.add(new InsnNode(Opcodes.IRETURN));ordinary.maxStack=1;ordinary.maxLocals=1;target.methods.add(ordinary);
  return target;
 }
 private static String instructions(MethodNode m){var values=new ArrayList<String>();for(var i:m.instructions){values.add(i.getClass().getSimpleName()+":"+i.getOpcode()+(i instanceof LdcInsnNode l?":"+l.cst:""));}return values.toString();}
 private static boolean contains(ClassNode target,String value){for(var m:target.methods)for(var i:m.instructions)if(i instanceof LdcInsnNode l&&value.equals(l.cst))return true;return false;}
 public static void main(String[] args)throws Exception{
  byte[] source;try(var jar=new JarFile(args[0]);var in=jar.getInputStream(jar.getJarEntry("com/cinemamod/mcef/mixins/CefWindowsShutdownMixin.class"))){source=in.readAllBytes();}
  var target=fixture(source,OWNER,true);var hook=target.methods.getFirst();var ordinary=target.methods.getLast();String ordinaryBefore=instructions(ordinary);var annotations=hook.visibleAnnotations;
  check(contains(target,"taskkill")&&contains(target,"/IM"),"fixture comes from actual installed unsafe commands");
  check(McefShutdownGuard.patch(target)==1,"actual handler selected through source metadata despite renamed name");
  check(hook.instructions.size()==1&&hook.instructions.getFirst().getOpcode()==Opcodes.RETURN,"actual process-command body fully absent");
  check(hook.tryCatchBlocks.isEmpty()&&(hook.localVariables==null||hook.localVariables.isEmpty())&&hook.visibleLocalVariableAnnotations==null&&hook.invisibleLocalVariableAnnotations==null,"obsolete exception/local/annotation regions cleared");
  check(hook.maxStack==0&&hook.maxLocals==2,"valid callback instance layout");
  check(hook.visibleAnnotations==annotations,"merged source metadata preserved");
  check(instructions(ordinary).equals(ordinaryBefore),"other Minecraft method remains unchanged");
  check(McefShutdownGuard.patch(target)==0,"second transform is idempotent");
  var foreign=fixture(source,"example.other.CloseMixin",true);String foreignBefore=instructions(foreign.methods.getFirst());
  check(McefShutdownGuard.patch(foreign)==0&&instructions(foreign.methods.getFirst()).equals(foreignBefore),"same dangerous constants from unrelated owner untouched");
  var unmarked=fixture(source,OWNER,false);check(McefShutdownGuard.patch(unmarked)==0&&contains(unmarked,"taskkill"),"unmarked methods never rewritten");
  var incompatible=fixture(source,OWNER,true);incompatible.methods.getFirst().desc="()V";
  boolean refused=false;try{McefShutdownGuard.patch(incompatible);}catch(IllegalStateException expected){refused=true;}check(refused,"unexpected dangerous source signature refuses silent success");
  var duplicate=fixture(source,OWNER,true);duplicate.methods.add(fixture(source,OWNER,true).methods.getFirst());refused=false;
  try{McefShutdownGuard.patch(duplicate);}catch(IllegalStateException expected){refused=true;}check(refused,"ambiguous multiple hooks rejected");
  // Verify the transformed real body can be defined and invoked without MCEF/process calls.
  target.name="net/muxigame/terminal/compatqa/RuntimeCloseFixture";
  var ctor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));ctor.instructions.add(new InsnNode(Opcodes.RETURN));ctor.maxStack=1;ctor.maxLocals=1;target.methods.add(ctor);
  var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);target.accept(writer);byte[] compiled=writer.toByteArray();
  class FixtureLoader extends ClassLoader{Class<?> loadFixture(byte[] data){return defineClass(null,data,0,data.length);}}
  var klass=new FixtureLoader().loadFixture(compiled);Object instance=klass.getConstructor().newInstance();
  klass.getMethod(hook.name,Class.forName("org.spongepowered.asm.mixin.injection.callback.CallbackInfo")).invoke(instance,new Object[]{null});
  check(((Integer)klass.getMethod("unrelated").invoke(instance))==42,"JVM verifies transformed real handler and unrelated method");
  System.out.println("MCEF shutdown guard fixture checks: "+checks+" PASS (no Minecraft run; no process commands executed)");
 }
}

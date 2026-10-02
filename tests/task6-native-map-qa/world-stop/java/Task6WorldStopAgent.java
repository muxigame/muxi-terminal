import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.io.*;
/** Read only the task6 test driver; request vanilla halt(false) only for this owned world. */
public final class Task6WorldStopAgent {
 public static void agentmain(String mode,Instrumentation inst)throws Exception {
  Path lab=Path.of("C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002/qa-runtime/all-20261002-052451-d188b089").toAbsolutePath().normalize();
  if(ProcessHandle.current().pid()!=34736||!Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(lab))throw new IllegalStateException("Wrong private PID or directory");
  Class<?> qa=null,neo=null,hooks=null,resource=null;
  for(Class<?> type:inst.getAllLoadedClasses()){
   if(type.getName().equals("net.muxigame.terminal.nativeqa.NativeRuntimeQA"))qa=type;
   if(type.getName().equals("net.neoforged.neoforge.common.NeoForge"))neo=type;
   if(type.getName().equals("net.neoforged.neoforge.server.ServerLifecycleHooks"))hooks=type;
   if(type.getName().equals("net.minecraft.world.level.storage.LevelResource"))resource=type;
  }
  if(qa==null||neo==null||hooks==null||resource==null)throw new IllegalStateException("Native classes not loaded");
  Module agent=Task6WorldStopAgent.class.getModule();
  inst.redefineModule(qa.getModule(),Set.of(),Map.of(),Map.of(qa.getPackageName(),Set.of(agent)),Set.of(),Map.of());
  Object bus=neo.getField("EVENT_BUS").get(null);Class<?> busClass=bus.getClass();
  inst.redefineModule(busClass.getModule(),Set.of(),Map.of(),Map.of("net.neoforged.bus",Set.of(agent)),Set.of(),Map.of());
  Field listeners=busClass.getDeclaredField("listeners");listeners.setAccessible(true);Map<?,?> map=(Map<?,?>)listeners.get(bus);
  Object own=null;
  for(Object key:map.keySet()){
   if(!key.getClass().getName().startsWith("net.muxigame.terminal.nativeqa.NativeRuntimeQA"))continue;
   if(qa.isInstance(key)){own=key;break;}
   for(Field field:key.getClass().getDeclaredFields()){
    if(Modifier.isStatic(field.getModifiers()))continue;field.setAccessible(true);Object value=field.get(key);if(qa.isInstance(value)){own=value;break;}
   }
   if(own!=null)break;
  }
  if(own==null)throw new IllegalStateException("Own registered QA instance not found");
  Field report=qa.getDeclaredField("report");report.setAccessible(true);Files.writeString(lab.resolve("qa-failure-snapshot.json"),report.get(own).toString());
  Field failure=qa.getDeclaredField("serverFailure");failure.setAccessible(true);Throwable error=(Throwable)failure.get(own);
  if(error!=null){StringWriter text=new StringWriter();error.printStackTrace(new PrintWriter(text));Files.writeString(lab.resolve("qa-server-failure-stack.txt"),text.toString());}
  if(!mode.equals("halt"))return;
  Object server=hooks.getMethod("getCurrentServer").invoke(null);if(server==null)throw new IllegalStateException("No owned server");
  Object rootResource=resource.getField("ROOT").get(null);Path world=(Path)server.getClass().getMethod("getWorldPath",resource).invoke(server,rootResource);
  if(!world.toAbsolutePath().normalize().startsWith(lab.resolve("saves")))throw new IllegalStateException("Server world is not in the owned private lab");
  Files.writeString(lab.resolve("owned-server-normal-halt-request.json"),"{\"pid\":34736,\"method\":\"MinecraftServer.halt(false), vanilla server stop and save\",\"forceOsTermination\":false,\"featureAcceptance\":false,\"utc\":\""+Instant.now()+"\"}");
  server.getClass().getMethod("halt",boolean.class).invoke(server,false);
 }
}

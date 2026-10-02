import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Explicit failed-bootstrap cleanup only. Never installed in a product or exposed to CEF. */
public final class Task6BootstrapCloseAgent {
 public static void agentmain(String arguments,Instrumentation instrumentation)throws Exception {
  String[] values=arguments.split("\\|",-1);
  if(values.length!=3)throw new IllegalArgumentException("Expected fixed lab, PID and process start time");
  Path expected=Path.of("C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002/qa-runtime/all-20261002-050954-5de742e6").toAbsolutePath().normalize();
  Path lab=Path.of(values[0]).toAbsolutePath().normalize();
  long pid=Long.parseLong(values[1]),started=Long.parseLong(values[2]);
  if(!lab.equals(expected)||pid!=17272||ProcessHandle.current().pid()!=pid)throw new IllegalStateException("Wrong private process or lab");
  if(!Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(lab))throw new IllegalStateException("Wrong game directory");
  if(!System.getProperty("qa.ownerRoot","").replace('\\','/').equals("C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002"))throw new IllegalStateException("Wrong owner root");
  if(Math.abs(ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli()-started)>2000)throw new IllegalStateException("PID reused");
  boolean bootWmi=false,gameRunning=false;
  for(var entry:Thread.getAllStackTraces().entrySet()){
   if(entry.getKey().getName().equals("Render thread"))gameRunning=true;
   if(entry.getKey().getName().equals("main")){
    boolean query=false,preload=false;
    for(var frame:entry.getValue()){
     if(frame.getClassName().startsWith("com.sun.jna.platform.win32.COM.Wbemcli"))query=true;
     if(frame.getClassName().equals("net.minecraft.CrashReport")&&frame.getMethodName().equals("preload"))preload=true;
    }
    bootWmi=query&&preload;
   }
  }
  if(!bootWmi||gameRunning)throw new IllegalStateException("Not the diagnosed pre-window WMI bootstrap stall; no shutdown requested");
  Files.writeString(lab.resolve("bootstrap-close-request.json"),"{\"pid\":17272,\"reason\":\"verified pre-window OSHI WMI stall\",\"method\":\"Runtime.exit(2), standard JVM shutdown hooks\",\"minecraftNormalExitAccepted\":false,\"forceOsTermination\":false,\"utc\":\""+Instant.now()+"\"}");
  Runtime.getRuntime().addShutdownHook(new Thread(()->{
   try{Files.writeString(lab.resolve("bootstrap-jvm-shutdown-hook.json"),"{\"pid\":17272,\"ownShutdownHookRan\":true,\"minecraftNormalExitAccepted\":false,\"utc\":\""+Instant.now()+"\"}");}catch(Exception error){error.printStackTrace();}
  },"task6-failed-bootstrap-receipt"));
  new Thread(()->Runtime.getRuntime().exit(2),"task6-own-bootstrap-standard-shutdown").start();
 }
}

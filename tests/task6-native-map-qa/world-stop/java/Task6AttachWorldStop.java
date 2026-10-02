import com.sun.tools.attach.VirtualMachine;
import java.nio.file.*;
public final class Task6AttachWorldStop {
 public static void main(String[] args)throws Exception {
  if(args.length!=2||!args[1].equals("halt")&&!args[1].equals("snapshot"))throw new IllegalArgumentException("Expected fixed agent and snapshot/halt");
  Path file=Path.of(args[0]).toAbsolutePath().normalize();
  if(!file.equals(Path.of("C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002/world-stop/task6-world-stop.jar").toAbsolutePath().normalize()))throw new IllegalArgumentException("Wrong agent");
  VirtualMachine vm=VirtualMachine.attach("34736");try{vm.loadAgent(file.toString(),args[1]);}finally{vm.detach();}
 }
}

import com.sun.tools.attach.VirtualMachine;
import java.nio.file.*;
public final class Task6AttachBootstrapClose {
 public static void main(String[] args)throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("Exact fixed private agent path required");
  Path agent=Path.of(args[0]).toAbsolutePath().normalize();
  if(!agent.equals(Path.of("C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002/bootstrap-close/task6-bootstrap-close.jar").toAbsolutePath().normalize()))throw new IllegalArgumentException("Wrong own agent");
  VirtualMachine vm=VirtualMachine.attach("17272");
  try{vm.loadAgent(agent.toString(),"C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002/qa-runtime/all-20261002-050954-5de742e6|17272|1790917828045");}
  finally{vm.detach();}
 }
}

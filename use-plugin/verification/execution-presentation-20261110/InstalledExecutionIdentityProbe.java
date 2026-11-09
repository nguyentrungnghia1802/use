import java.nio.file.*;
import java.util.*;
public class InstalledExecutionIdentityProbe {
 public static void main(String[] args)throws Exception {
  Path install=Path.of(args[0]).toRealPath();var receipt=new TreeMap<String,Object>();
  for(String name:List.of("org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ExecutionGroups","org.tzi.use.plugins.jacamo.ui.use4goal.state.IStarInstancesPanel","org.jacamo.bridge.contract.JasonAgentInventory","org.jacamo.bridge.adapter.JasonStepObservation","jacamo.infra.JaCaMoLauncher","org.jacamo.checkpoint.runtime.ExecutionBarrier")){
   var type=Class.forName(name,false,InstalledExecutionIdentityProbe.class.getClassLoader());
   var jar=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
   if(!jar.startsWith(install))throw new AssertionError("Non-installed class: "+name+" "+jar);
   String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
   receipt.put(name,Map.of("jar",jar.toString(),"sha256",hash));System.out.println("LOADED "+name+" "+jar+" SHA256="+hash);
  }
  Files.writeString(Path.of(args[1]),new String(org.jacamo.bridge.contract.CanonicalJson.encode(receipt),java.nio.charset.StandardCharsets.UTF_8));
  System.out.println("INSTALLED_EXECUTION_IDENTITY_PASS");
 }
}

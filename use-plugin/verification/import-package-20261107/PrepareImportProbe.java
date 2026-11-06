import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;

/** Exercises the shipped preparation/import path; does not start agent reasoning. */
public class PrepareImportProbe {
 public static void main(String[] args) {
  try { run(args); System.out.println("PACKAGED_PREPARE_IMPORT_PASS"); System.exit(0); }
  catch(Throwable failure){ failure.printStackTrace(); System.exit(1); }
 }
 static void run(String[] args)throws Exception {
  Path install=Path.of(args[0]).toRealPath(), models=Path.of(args[1]).toRealPath();
  for(String name:List.of("org.jacamo.bridge.contract.JasonAgentInventory",
    "org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources")) {
   var type=Class.forName(name);
   Path jar=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
   if(!jar.startsWith(install))throw new AssertionError("Non-installed production class: "+name);
   System.out.println("LOADED "+name+" "+jar+" SHA256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
  }
  Session host=new Session();DefaultJaCaMoFacade facade=new DefaultJaCaMoFacade(install,host);
  facade.verifiedCheckpointMode(true);
  try {
   facade.prepareOwnedProducer(Path.of(args[2]).toRealPath(),
    new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",models.resolve("auction-mapping.xml"),
     models.resolve("auction.ocl"),models.resolve("auction.csl"),models.resolve("auction.istar"),false),
    install.resolve("bridge/runtime-classpath.txt"),false);
   if(host.system()==null)throw new AssertionError("Authoritative MSystem was not imported");
   if(facade.ownedProducerStatus().pid()<=0)throw new AssertionError("Owned producer missing");
   System.out.println("IMPORTED model="+host.system().model().name()+" producerPID="+facade.ownedProducerStatus().pid());
  } finally {facade.stopOwnedProducer();facade.close();}
 }
}

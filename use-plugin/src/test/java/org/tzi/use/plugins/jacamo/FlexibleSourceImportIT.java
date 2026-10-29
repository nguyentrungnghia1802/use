package org.tzi.use.plugins.jacamo;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.tzi.use.main.Session;
import org.jacamo.bridge.contract.CanonicalJson;
import static org.junit.jupiter.api.Assertions.*;
class FlexibleSourceImportIT {
 @Test @Timeout(240) void preparesActualExternalAuctionWithAuthoredMappingAndStopsOwnedChild()throws Exception {
  Path checkout=Path.of("..").toAbsolutePath().normalize(),project=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm"),inputs=checkout.resolve("use-plugin/examples/auction/authored-v1");
  byte[] before=Files.readAllBytes(project);var session=new Session();long pid;
  try(var facade=new DefaultJaCaMoFacade(checkout,session)) {
   var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",inputs.resolve("auction-mapping.xml"),inputs.resolve("auction_constraints_final_fixed.ocl"),inputs.resolve("auction_csl.csl"),inputs.resolve("auction_istar_final.istar"),false);
   facade.prepareOwnedProducer(project,selection,null,false);pid=facade.ownedProducerStatus().pid();
   assertEquals("MODEL_READY",facade.workflowStatus().state());assertNotEquals(ProcessHandle.current().pid(),pid);assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive());
   assertSame(session.system().model(),facade.semanticModels().nativeModel());assertArrayEquals(before,Files.readAllBytes(project));
   var program=facade.semanticProgramEvidence();assertFalse(program.isEmpty());facade.stopOwnedProducer();assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
   Path evidence=checkout.resolve("use-plugin/verification/flexible-source-import");Files.createDirectories(evidence);
   Files.write(evidence.resolve("program.json"),CanonicalJson.encode(program));
   Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("status","PASS","project",project.toString(),"producerPid",pid,"usePid",ProcessHandle.current().pid(),"stage","MODEL_READY","sourceUnchanged",true,"stoppedChild",true,"strictSourceChecksums",false)));
  }
 }
}

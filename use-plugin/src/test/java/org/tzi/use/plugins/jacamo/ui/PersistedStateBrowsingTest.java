package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import java.time.Instant;import java.nio.file.Path;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.*;import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import static org.junit.jupiter.api.Assertions.*;
class PersistedStateBrowsingTest {
 @TempDir Path directory;
 RuntimeVerificationResult result(int n,String commands){var now=Instant.EPOCH;String hash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(commands.getBytes(java.nio.charset.StandardCharsets.UTF_8));return new RuntimeVerificationResult("run",1,"revision",n,"event"+n,"cartago",n,now,now,now,"cut"+n,"contracts",hash,List.of(),0,"INCOMPLETE","CURRENT_OBSERVED","");}
 @Test void realAcceptedStateCommandsSurviveTailEvictionAndReopeningWithoutASecondNativeRuntime(){
  var journal=new RuntimeEventJournal(directory,2,1024*1024);RuntimeVerificationResult latest=null;
  for(int n=1;n<=12;n++){String commands="!set item.p := "+n+"\n";latest=result(n,commands);assertTrue(journal.append("ENGINE_RESULT",Map.of("acceptedStateCommands",commands),latest));}
  var finalResult=latest;var facade=new JaCaMoFacade(){public String status(){return "history";}public RuntimeVerificationResult runtimeVerificationResult(){return finalResult;}public List<RuntimeVerificationResult> runtimeVerificationHistory(){return journal.history();}public RuntimeHistoryPage runtimeHistoryTail(){return journal.tailPage(128);}public RuntimeHistoryPage runtimeHistoryPage(long offset,int limit){return journal.page(offset,limit);}};
  assertEquals(2,journal.history().size());var cuts=new ObservedConformancePresenter(facade).capture();assertEquals(12,cuts.size());assertEquals("!set item.p := 1\n",cuts.getFirst().state());assertEquals("!set item.p := 12\n",cuts.getLast().state());assertEquals(cuts,new ObservedConformancePresenter(facade).capture());assertFalse(journal.hasGap());
 }
 @Test void mismatchedStateCommandsAreRejectedAndDoNotManufactureAHistoricalImage(){
  var journal=new RuntimeEventJournal(directory,2,1024*1024);assertFalse(journal.append("ENGINE_RESULT",Map.of("acceptedStateCommands","tampered"),result(1,"actual")));assertTrue(journal.hasGap());assertTrue(journal.diagnostic().contains("HISTORY_ACCEPTED_STATE_HASH_MISMATCH"));
 }
}

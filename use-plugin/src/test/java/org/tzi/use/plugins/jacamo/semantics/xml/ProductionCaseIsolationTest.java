package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProductionCaseIsolationTest {
 @Test void allProductionMappingAndProducerSourcesHaveNoCaseConstantsOrLegacyClasses()throws Exception {
  Path root=Path.of("..").toAbsolutePath().normalize();
  for(String tree:List.of("use-plugin/src/main/java","jacamo-bridge-jacamo/src/main/java","use-jacamo-semantic-engine/src/main/java"))try(var files=Files.walk(root.resolve(tree))){
   for(Path path:files.filter(f->f.toString().endsWith(".java")).toList()){
    String source=Files.readString(path);
    for(String word:List.of("auction","currentWinner","P_bid_t2","sch_a1","houseBuilding","HelloWorld","CounterTeam"))assertFalse(source.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT)),path+" has case-specific production data: "+word);
   }
  }
  String launcher=Files.readString(root.resolve("use-plugin/tools/jacamo-bridge.ps1"));assertFalse(launcher.toLowerCase(Locale.ROOT).contains("auction"));
  for(String module:List.of("use-plugin","jacamo-bridge-jacamo"))try(var files=Files.walk(root.resolve(module+"/target/classes"))){
   assertFalse(files.anyMatch(f->f.getFileName().toString().startsWith("Auction")),"Removed case bytecode must not survive incremental packaging");
  }
 }
 @Test void retiredProfileAndRecordingCannotFallbackToNativeOrDeclarative(){
  assertThrows(IllegalArgumentException.class,()->org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile.configured("AUCTION_OBSERVED_V1"));
  var error=assertThrows(IllegalArgumentException.class,()->new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest().decode(org.jacamo.bridge.contract.CanonicalJson.encode(Map.of("schemaVersion","1.1.0"))));
  assertTrue(error.getMessage().contains("RETIRED"));
 }
}

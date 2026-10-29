package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;
class CurrentAuctionBundleTest {
 @Test void cleanFiveInputBundleBuildsRealModelsAndNativeContractsWithoutReferenceInputs()throws Exception {
  Path root=Path.of("examples/auction"),reference=Path.of("src/test/resources/auction-reference/authored-v1");
  try(var entries=Files.list(root)){assertEquals(Set.of("README.md","auction.jcm","build.gradle","src","auction.csl","auction.istar","auction-mapping.xml","auction.ocl"),new HashSet<>(entries.map(p->p.getFileName().toString()).toList()));}
  for(var pair:Map.of("auction.csl","auction_csl.csl","auction.istar","auction_istar_final.istar","auction.ocl","auction_constraints_final_fixed.ocl","auction-mapping.xml","auction-mapping.xml").entrySet())assertArrayEquals(Files.readAllBytes(reference.resolve(pair.getValue())),Files.readAllBytes(root.resolve(pair.getKey())),"Relocation must not change semantics");
  var source=GenericMappingEngineTest.official(root.resolve("auction.jcm"));
  var program=new GenericMappingEngine().map(source,MappingInputs.read(root.resolve("auction.jcm"),root.resolve("auction-mapping.xml"),root.resolve("auction.ocl"),root.resolve("auction.csl"),root.resolve("auction.istar"),false));
  var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
  assertNotNull(built.nativeResult().state().system());assertTrue(program.goalModel().findElement("O_bid_accepted").isPresent());assertTrue(program.cslModel().findEntity("Auction").isPresent());assertFalse(built.xmlPreparation().authored().clauses().isEmpty());
 }
}

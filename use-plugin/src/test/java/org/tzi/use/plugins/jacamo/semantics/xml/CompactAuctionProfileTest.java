package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.*;
import static org.junit.jupiter.api.Assertions.*;
class CompactAuctionProfileTest {
 @Test void compactProfileKeepsRealOriginalModelsAndExplicitObservationClosure()throws Exception {
  Path root=Path.of("examples/auction"),inputs=root.resolve("compact-v1");
  var frozen=MappingInputs.read(root.resolve("auction.jcm"),inputs.resolve("auction-mapping.xml"),inputs.resolve("auction.ocl"),inputs.resolve("auction.csl"),inputs.resolve("auction.istar"),false);
  var loader=new AuthoredModelLoader();var csl=loader.csl(frozen);var goals=loader.goals(frozen);assertEquals(1,csl.model().entities().size());assertEquals(3,goals.model().getActors().getFirst().elements().size());
  assertTrue(frozen.specification().root().child("runtime").child("datasets").children("beliefViews").isEmpty());
  assertEquals(2,frozen.specification().root().child("requirements").children("capability").size());
  assertEquals(6,csl.model().entities().getFirst().attributes().size());
  new CslScopeModelAdapter().compile(csl.model(),XmlNativePreparation.observerSchema(frozen.specification()));
  MappingInputs.fromFrozen(frozen.xml().bytes(),frozen.ocl().bytes(),frozen.sources(),"portable");
 }
}

package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;
class CurrentAuctionBundleTest {
 @Test void absentExtraOclKeepsAllEmbeddedContractsAndFreezesReplayWithoutEditingFiles()throws Exception {
  Path root=Path.of("examples/auction"),xml=root.resolve("auction-compact-mapping.xml");byte[] original=Files.readAllBytes(xml);
  var inputs=MappingInputs.read(root.resolve("auction.jcm"),xml,null,root.resolve("auction.csl"),root.resolve("auction.istar"),false);
  assertEquals(0,inputs.ocl().bytes().length);assertArrayEquals(original,Files.readAllBytes(xml));
  var restored=MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),inputs.sources(),"optional-ocl-replay");
  assertEquals(inputs.xml().sha256(),restored.xml().sha256());
  var source=GenericMappingEngineTest.official(root.resolve("auction.jcm"));
  var program=new GenericMappingEngine().map(source,restored);
  var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
  assertEquals(7,org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ModelNativeContracts.read(program.compactPlan().mappingInputs().orElseThrow()).clauses().size());
  assertNotNull(built.nativeResult().state().system().model().getClassInvariant("auctionGroup::AuctioneerPopulation"));
 }

 @Test void authoringRevisionGetsNewIdentityWithoutMutatingFrozenInputs(@org.junit.jupiter.api.io.TempDir Path temporary)throws Exception {
   for(String file:List.of("auction.csl","auction.istar","auction.ocl","auction-compact-mapping.xml"))Files.copy(Path.of("examples/auction").resolve(file),temporary.resolve(file));
   Path project=Path.of("examples/auction/auction.jcm"),xml=temporary.resolve("auction-compact-mapping.xml"),csl=temporary.resolve("auction.csl");
   var inputs=MappingInputs.read(project,xml,temporary.resolve("auction.ocl"),csl,temporary.resolve("auction.istar"),false);
   var source=GenericMappingEngineTest.official(project);
   var program=new GenericMappingEngine().map(source,inputs);
   program.assertUnchanged();
   Files.writeString(csl,Files.readString(csl)+"\n// Deliberate authoring revision.\n");
   program.assertUnchanged();
   var revised=MappingInputs.read(project,xml,temporary.resolve("auction.ocl"),csl,temporary.resolve("auction.istar"),false);
   assertNotEquals(inputs.require(MappingInputs.AUTHORED_CSL).sha256(),revised.require(MappingInputs.AUTHORED_CSL).sha256());
   assertNotEquals(program.hash(),new GenericMappingEngine().map(source,revised).hash());
   var frozen=MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),inputs.sources(),"immutable-replay");
   assertEquals(inputs.xml().sha256(),frozen.xml().sha256());
   assertArrayEquals(inputs.require(MappingInputs.AUTHORED_CSL).bytes(),frozen.require(MappingInputs.AUTHORED_CSL).bytes());
 }

 @Test void selectedExternalAuctionImportsTheEditedMrvModelsWithDeadlineDisabled()throws Exception {
  Path project=Path.of("D:/_CODE_BANK/Project_/vnu-sme-lab/jacamo/examples/auction/auction.jcm"),root=Path.of("examples/auction");
  assertTrue(Files.isRegularFile(project),"Actual user-selected external Auction must exist for this receipt");
  var source=GenericMappingEngineTest.official(project);
  var inputs=MappingInputs.read(project,root.resolve("auction-compact-mapping.xml"),root.resolve("auction.ocl"),root.resolve("auction.csl"),root.resolve("auction.istar"),false);
  var program=new GenericMappingEngine().map(source,inputs);
  var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
  assertEquals(7,org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ModelNativeContracts.read(program.compactPlan().mappingInputs().orElseThrow()).clauses().size());
  assertNull(built.nativeResult().state().system().model().getClassInvariant("AuctionObligation::WithinDeadline"));
  assertNotNull(built.nativeResult().state().system().model().getClassInvariant("auctionGroup::ParticipantPopulation"));
  var frozen=MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),inputs.sources(),"edited-mrv");assertEquals(inputs.xml().sha256(),frozen.xml().sha256());
 }

 @Test void falseBundleUsesTheSameAdmittedModelsAndRetainsItsSourceFault()throws Exception {
  Path root=Path.of("examples/auction-false");
  var source=GenericMappingEngineTest.official(root.resolve("auction.jcm"));
  var program=new GenericMappingEngine().map(source,MappingInputs.read(root.resolve("auction.jcm"),root.resolve("auction-compact-mapping.xml"),root.resolve("auction.ocl"),root.resolve("auction.csl"),root.resolve("auction.istar"),false));
  var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
  assertEquals(8,org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ModelNativeContracts.read(program.compactPlan().mappingInputs().orElseThrow()).clauses().size());
  var faulty=Files.readString(root.resolve("src/env/auction_env/AuctionArtifact.java"));
  var stopBody=faulty.substring(faulty.indexOf("public void stop()"),faulty.indexOf("public void bid("));
  assertFalse(stopBody.contains("updateValue(false)"),"The intentional missing stop transition stays unchanged");
  for(String file:List.of("auction.csl","auction.ocl"))assertArrayEquals(Files.readAllBytes(Path.of("examples/auction").resolve(file)),Files.readAllBytes(root.resolve(file)));
 }
 @Test void cleanFiveInputBundleBuildsRealModelsAndNativeContractsWithoutReferenceInputs()throws Exception {
  Path root=Path.of("examples/auction"),reference=Path.of("src/test/resources/auction-reference/authored-v1");
  // Native USE persists diagram layouts beside inputs. They are runtime output,
  // not another authored model or a reason to delete a user's saved layout.
  try(var entries=Files.list(root)){assertEquals(Set.of("README.md","auction.jcm","build.gradle","src","auction.csl","auction.istar","auction-compact-mapping.xml","auction.ocl"),new HashSet<>(entries.map(p->p.getFileName().toString()).filter(n->!n.endsWith(".mrv")).toList()));}
  var contracts=org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ModelNativeContracts.blocks(Files.readAllBytes(root.resolve("auction.csl")));assertEquals(1,contracts.size());
  assertTrue(org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ModelNativeContracts.blocks(Files.readAllBytes(root.resolve("auction.istar"))).getFirst().contains("SchemeOrder"));
  var source=GenericMappingEngineTest.official(root.resolve("auction.jcm"));
  var program=new GenericMappingEngine().map(source,MappingInputs.read(root.resolve("auction.jcm"),root.resolve("auction-compact-mapping.xml"),root.resolve("auction.ocl"),root.resolve("auction.csl"),root.resolve("auction.istar"),false));
  var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
  assertEquals(Set.of("auctioneer","participant"),org.tzi.use.plugins.jacamo.semantics.mapping.CanonicalUseBindingAdapter.enactedRoles(built.nativeResult().state().system().model().getClass("auction_capabilities")));
  var layout=org.tzi.use.plugins.jacamo.ui.use4goal.csl.CslLayoutBuilder.build(program.cslModel(),Map.of("auction_capabilities",Set.of("auctioneer","participant")));
  assertEquals(2,layout.edges.stream().filter(e->e.kind()==org.tzi.use.plugins.jacamo.ui.use4goal.csl.CslEdgeKind.ENACTMENT).count());
  var embedded=org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ModelNativeContracts.read(program.compactPlan().mappingInputs().orElseThrow()).clauses();assertEquals(7,embedded.size());
  assertEquals(5,embedded.stream().filter(c->c.kind()!=org.tzi.use.plugins.jacamo.semantics.mapping.ocl.AuthoredOclSpecificationLoader.Kind.INVARIANT).count());
  assertTrue(new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.AuthoredOclSpecificationLoader().parseOptional(Files.readAllBytes(root.resolve("auction.ocl")),"optional").clauses().isEmpty());
  var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  var diagram=factory.newDocumentBuilder().parse(Path.of("Core/auction/auction_csl.drawio").toFile());
  var canonical=new org.tzi.use.semantic.engine.frontend.CslFrontend().compile(diagram.getDocumentElement().getAttribute("cslSource"),"drawio-cslSource").requireModel();
  assertEquals(canonical.findEntity("Auction"),program.cslModel().findEntity("Auction"),"Artifact semantics from the diagram remain unchanged");
  assertEquals(Set.of("AuctionOrg","auctionGroup","auction_capabilities"),program.cslModel().orgContexts().stream().map(c->c.name()).collect(java.util.stream.Collectors.toSet()));
  assertEquals(Set.of("auctioneer","participant"),program.cslModel().roles().stream().map(c->c.name()).collect(java.util.stream.Collectors.toSet()));
  assertTrue(program.cslModel().generalizations().isEmpty(),"User selected Agent/OrgCtx, not inherited capability Role");
  var capability=program.goalModel().findActor("auction_capabilities").orElseThrow();
  assertInstanceOf(org.vnu.sme.goal.dsl.istar.mm.Role.class,capability,"The supplied Draw.io node explicitly declares elementType=role");
  assertEquals(7,capability.elements().stream().filter(e->e instanceof org.vnu.sme.goal.dsl.istar.mm.Task).count());
  assertEquals(2,capability.elements().stream().filter(e->e instanceof org.vnu.sme.goal.dsl.istar.mm.Resource).count());
  assertEquals(5,capability.neededBys().size());
  assertTrue(program.goalModel().findElement("do_auction").isPresent());
  assertTrue(program.goalModel().findElement("createScheme").isPresent());
  assertTrue(program.goalModel().findElement("commitMission").isPresent());
  assertTrue(program.goalModel().findElement("SchemeBoard").isPresent());
  assertEquals(Set.of("D_start","D_bid","D_decide"),program.goalModel().getDependencies().stream().map(d->d.dependum().id()).collect(java.util.stream.Collectors.toSet()));
  assertEquals(2,capability.associations().size());
  assertEquals(Set.of("A_start","A_decide","P_bid"),program.compactPlan().specification().goalRealizations().stream().map(r->r.target()).collect(java.util.stream.Collectors.toSet()));
  assertEquals(Set.of("auction_capabilities"),program.compactPlan().specification().actorRuntimes().stream().filter(r->"true".equals(r.attributes().get("physicalActorView"))).map(r->r.actor()).collect(java.util.stream.Collectors.toSet()));
  assertTrue(program.goalModel().findActor("Auctioneer").orElseThrow().elements().stream().noneMatch(e->e instanceof org.vnu.sme.goal.dsl.istar.mm.Task));
  assertNotNull(built.nativeResult().state().system());assertTrue(program.goalModel().findElement("O_bid_accepted").isEmpty());assertTrue(program.goalModel().findElement("O_bid_attempt").isEmpty());assertTrue(program.cslModel().findEntity("Auction").isPresent());
  assertEquals(Files.readString(root.resolve("auction.csl")),diagram.getDocumentElement().getAttribute("cslSource"));
  var intentional=factory.newDocumentBuilder().parse(Path.of("Core/auction/auction_istar_final_fixed.drawio").toFile());
  var original=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(intentional.getDocumentElement().getAttribute("istarSource"),"Core",org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
  assertEquals(org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(original),org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(program.goalModel()),"Only the explicitly disabled observer contract differs from the Core graph");
  assertTrue(Files.readString(root.resolve("auction.istar")).contains("depend Auction.O_bid -> goal D_bid -> Participant.P_bid"));
  assertFalse(Files.readString(root.resolve("auction-compact-mapping.xml")).contains("<acceptedFailure"));
  assertTrue(Files.readString(root.resolve("auction.istar")).contains("context AuctionObligation"));
  assertFalse(Files.readString(root.resolve("auction-compact-mapping.xml")).contains("<oclClause name=\"WithinDeadline\""));
  assertTrue(embedded.stream().noneMatch(c->c.name().equals("WithinDeadline")));
  for(String model:List.of("auction.csl"))assertArrayEquals(Files.readAllBytes(root.resolve(model)),Files.readAllBytes(Path.of("examples/auction-false").resolve(model)));
 }
}

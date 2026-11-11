package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;

/** Focused component/native-writer regressions. Not a live MAS or workflow acceptance. */
class CompactRepairRegressionTest {
    @TempDir Path temp;
    static class ClassProbe {
        String actualField;
        static String staticField;
        String ordinaryMethod(){return "not_a_property";}
    }
    @Test void stringConstantsMethodsAndIntrinsicNamesAreNotFabricatedProperties() throws Exception {
        var type=ClassProbe.class;var resource=type.getResource("/"+type.getName().replace('.','/')+".class");
        byte[] bytes;try(var input=resource.openStream()){bytes=input.readAllBytes();}
        String hash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(bytes);
        var proof=new SourceEvidence(EvidenceAuthority.OFFICIAL_CARTAGO_API,resource.toExternalForm(),hash,type.getName(),"probe",0,0,"","",0,Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS"));
        var field=new SourceEvidence(EvidenceAuthority.OFFICIAL_CARTAGO_API,resource.toExternalForm(),hash,type.getName(),"probe",0,0,"","",0,Fidelity.EXACT,CapabilityStatus.COMPLETE,
                List.of("DECLARED_CARTAGO_ARTIFACT_FIELD","OWNER:"+type.getName(),"FIELD_SIGNATURE:[\""+type.getName()+"\",\"actualField\",\"java.lang.String\"]"));
        var metadata=new SemanticMetadata("probe","JACAMO_ARTIFACT_DECLARATION","fixture",EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(proof,field),List.of());
        var base=JacamoSemanticSnapshot.empty();
        var snapshot=new JacamoSemanticSnapshot(base.contractVersion(),base.project(),List.of(),List.of(),
                List.of(new JcmSemanticContract.ArtifactDeclarationSemantic(metadata,"probe","workspace",type.getName(),List.of())),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var catalog=SourceSymbolCatalog.fromSnapshot(snapshot);
        assertEquals("java.lang.String",catalog.resolve(SourceReference.parse("artifact:"+type.getName()+"/field:actualField")).signature());
        for(String name:List.of("not_a_property","name","id","workspace"))
            assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("artifact:"+type.getName()+"/property:"+name)));
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("artifact:"+type.getName()+"/op:ordinaryMethod")));
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("artifact:"+type.getName()+"/field:staticField")));
    }
    @Test void rejectsUnsafeSourcePathsAndHiddenOrDuplicateXmlConfiguration() {
        for(String path:List.of("../secret.asl","src/../secret.asl","https://host/worker.asl","C:/worker.asl","/worker.asl"))
            assertThrows(IllegalArgumentException.class,()->SourceReference.parse("asl:"+path+"/goal:deliver"));
        for(String child:List.of("<csl/><csl/>","<runtime><requirement id='r' provider='custom.v1' script='hidden'/></runtime>",
                "<runtime><actorRuntime actor='Courier'/><actorRuntime actor='Courier'/></runtime>","<csl>hidden code</csl>"))
            assertThrows(IllegalArgumentException.class,()->CompactMappingReader.read("<mapping xmlns='urn:use:jacamo-mapping:3' version='3.0'>"+child+"</mapping>","invalid.xml"));
        assertThrows(IllegalArgumentException.class,()->CompactMappingReader.read("<!DOCTYPE mapping SYSTEM 'file:///not-allowed'><mapping xmlns='urn:use:jacamo-mapping:3' version='3.0'/>","external.xml"));
    }
    @Test void rejectsMissingSourcesWrongArgumentsAndAmbiguousPaths() {
        var catalog=new SourceSymbolCatalog();
        catalog.register(SourceSymbol.agent("courier"));
        catalog.registerPlan("src/one/worker.asl","+!deliver(Item)","deliver",3);
        catalog.registerPlan("src/two/worker.asl","+!deliver(Item)","deliver",3);
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("agent:imaginary")));
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("asl:src/one/worker.asl/plan:deliver(Item,Other)/step:move[0..1]")));
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("asl:worker.asl/plan:deliver/step:move[0..1]")));
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("asl:src/one/worker.asl/plan:deliver/step:move[0..4]")));
        assertEquals(SourceReference.Kind.ASL_PLAN_STEP,catalog.resolve(SourceReference.parse("asl:src/one/worker.asl/plan:deliver/step:move[0..3]")).kind());
    }
    @Test void resolvesExactOperationOverloadsAndRejectsAmbiguity() {
        var catalog=new SourceSymbolCatalog();catalog.register(SourceSymbol.artifact("env.Parcel",null));
        catalog.register(SourceSymbol.operation("env.Parcel","move","move(java.lang.String)"));
        catalog.register(SourceSymbol.operation("env.Parcel","move","move(int)"));
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("artifact:env.Parcel/op:move")));
        assertThrows(IllegalArgumentException.class,()->catalog.resolve(SourceReference.parse("artifact:env.Parcel/op:move(boolean)")));
        assertEquals("move(int)",catalog.resolve(SourceReference.parse("artifact:env.Parcel/op:move(int)")).signature());
    }
    @Test void conversionsDoNotInventFalseTruncateOrStringifyObjects() {
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue("maybe",CslBinding.TypeConversion.TO_BOOLEAN));
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue(1.5,CslBinding.TypeConversion.TO_INTEGER));
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue(Long.MAX_VALUE,CslBinding.TypeConversion.TO_INTEGER));
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue(new Object(),CslBinding.TypeConversion.TO_STRING));
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue(new Object(),CslBinding.TypeConversion.DIRECT));
        Object disguised=new Object(){@Override public String toString(){return "42";}};
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue(disguised,CslBinding.TypeConversion.TO_INTEGER));
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue(disguised,CslBinding.TypeConversion.TO_REAL));
        assertSame(UndefinedValue.instance,RuntimeInstanceLinker.convertValue(Double.NaN,CslBinding.TypeConversion.TO_REAL));
        assertEquals(BooleanValue.FALSE,RuntimeInstanceLinker.convertValue("false",CslBinding.TypeConversion.TO_BOOLEAN));
        assertEquals("UNKNOWN",new PhysicalCutFact.StepReceipt("deliver","move","Move",0,null).status());
    }
    @Test void physicalIdentityDoesNotCollideAfterSanitizingAndRequiresEpoch() {
        var one=new PhysicalCutFact.ArtifactInstance("parcel","env.Parcel","/a-b","run","life",Map.of(),Map.of());
        var two=new PhysicalCutFact.ArtifactInstance("parcel","env.Parcel","/a_b","run","life",Map.of(),Map.of());
        assertNotEquals(RuntimeInstanceLinker.artifactObjectName("Parcel",one),RuntimeInstanceLinker.artifactObjectName("Parcel",two));
        assertThrows(IllegalArgumentException.class,()->RuntimeInstanceLinker.artifactObjectName("Parcel",new PhysicalCutFact.ArtifactInstance("parcel","env.Parcel","/a",Map.of(),Map.of())));
    }
    @Test void rollbackRestoresDeletedIdentitiesBothEndpointsLinksAndAttributeChanges() throws Exception {
        var system=system();var cls=system.model().getClass("Parcel");
        var a=system.state().createObject(cls,"a");var b=system.state().createObject(cls,"b");
        a.state(system.state()).setAttributeValue(cls.attribute("status",true),new StringValue("ready"));
        var association=system.model().getAssociation("Route");var link=system.state().createLink(association,List.of(a,b),List.of());
        var transaction=new RuntimeInstanceLinker.ProjectionTransaction("dispose",List.of(),List.of(),List.of("a","b"));
        var applied=AuthoritativeMSystemProjector.project(system,transaction);
        assertEquals(0,system.state().numObjects());applied.rollback(system);
        assertSame(a,system.state().objectByName("a"));assertSame(b,system.state().objectByName("b"));
        assertEquals(new StringValue("ready"),a.state(system.state()).attributeValue("status"));
        assertTrue(system.state().allLinks().contains(link));applied.rollback(system);
    }
    @Test void rejectsWrongRollbackOwnerAndStaleTransaction() throws Exception {
        var system=system();var one=new RuntimeInstanceLinker.ObjectMutation("Parcel","p",Map.of("status",new StringValue("first")),true);
        var applied=AuthoritativeMSystemProjector.project(system,new RuntimeInstanceLinker.ProjectionTransaction("one",List.of(one),List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->applied.rollback(system()));
        var object=system.state().objectByName("p");object.state(system.state()).setAttributeValue(object.cls().attribute("status",true),new StringValue("later"));
        assertThrows(IllegalStateException.class,()->applied.rollback(system));
        assertEquals(new StringValue("later"),object.state(system.state()).attributeValue("status"));
    }
    @Test void projectionFailureRestoresEarlierMutationAndRejectsWrongNativeType() throws Exception {
        var system=system();var object=system.state().createObject(system.model().getClass("Parcel"),"p");
        object.state(system.state()).setAttributeValue(object.cls().attribute("status",true),new StringValue("before"));
        var mutations=List.of(new RuntimeInstanceLinker.ObjectMutation("Parcel","p",Map.of("status",new StringValue("changed")),false),
                new RuntimeInstanceLinker.ObjectMutation("Parcel","newParcel",Map.of("status",BooleanValue.TRUE),true));
        assertThrows(IllegalArgumentException.class,()->AuthoritativeMSystemProjector.project(system,new RuntimeInstanceLinker.ProjectionTransaction("bad",mutations,List.of(),List.of())));
        assertSame(object,system.state().objectByName("p"));assertNull(system.state().objectByName("newParcel"));
        assertEquals(new StringValue("before"),object.state(system.state()).attributeValue("status"));
    }
    @Test void nativeWriterKeepsSameNameArtifactsSeparateAndDoesNotDisposeFromTransportCompleteness() throws Exception {
        var f=fixture();var engine=f.engine();
        var a=artifact("/main/one","life-1","one");var b=artifact("/main/two","life-2","two");
        engine.applyMappingCut(cut(f,List.of(a,b),Completeness.COMPLETE),"run",1,List.of());
        var first=engine.objectForSemanticId(a.id().canonical());var second=engine.objectForSemanticId(b.id().canonical());
        assertNotNull(first);assertNotNull(second);assertNotSame(first,second);
        assertEquals(new StringValue("one"),first.state(engine.system().state()).attributeValue("status"));
        assertEquals(new StringValue("two"),second.state(engine.system().state()).attributeValue("status"));
        engine.applyMappingCut(cut(f,List.of(),Completeness.COMPLETE),"run",1,List.of());
        assertSame(first,engine.objectForSemanticId(a.id().canonical()));assertSame(second,engine.objectForSemanticId(b.id().canonical()));
    }
    @Test void incompleteNativeScopeIntegrationCannotBecomeModelReadyOrReleaseAuthority() throws Exception {
        var fixture=fixture();
        var error=assertThrows(IllegalArgumentException.class,()->new org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline()
                .buildCanonicalProgram(fixture.source(),org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.AUTO,fixture.program()));
        assertTrue(error.getMessage().contains("COMPACT_SOURCE_QUALIFIED_SCOPE_AND_TASK_INTEGRATION_REQUIRED"),error.toString());
        var inventoryError=assertThrows(IllegalArgumentException.class,()->org.tzi.use.plugins.jacamo.semantics.runtime.RequirementInventory.from(fixture.program(),fixture.engine().system().model()));
        assertTrue(inventoryError.getMessage().contains("COMPACT_EXPLICIT_INTENTIONAL_REQUIREMENT_SELECTION_REQUIRED"));
    }
    @Test void nativeWriterInvalidatesPartialValuesAndRollsBackValuesAliasesAndMetadataOnBadCut() throws Exception {
        var f=fixture();var engine=f.engine();var a=artifact("/main/one","life-1","one");
        engine.applyMappingCut(cut(f,List.of(a),Completeness.COMPLETE),"run",1,List.of());
        var original=engine.objectForSemanticId(a.id().canonical());var before=engine.savepoint();
        var bad=artifact("/main/one","life-1",true);var added=artifact("/main/new","life-3","valid");
        assertThrows(NativeRuntimeProtocolException.class,()->engine.applyMappingCut(cut(f,List.of(added,bad),Completeness.COMPLETE),"run",1,List.of()));
        assertSame(original,engine.objectForSemanticId(a.id().canonical()));assertNull(engine.objectForSemanticId(added.id().canonical()));
        assertEquals(new StringValue("one"),original.state(engine.system().state()).attributeValue("status"));
        assertEquals(before.bindings().toMap(),engine.savepoint().bindings().toMap());
        assertEquals(before.tombstones(),engine.savepoint().tombstones());
        engine.applyMappingCut(cut(f,List.of(a),Completeness.PARTIAL),"run",1,List.of());
        assertTrue(original.state(engine.system().state()).attributeValue("status").isUndefined());
        assertEquals("true",engine.metadata(original,"propertyAbsent:status"));
    }
    @Test void unrelatedRepeatedPropertyNamesDoNotRejectTheCutAndSelectedAmbiguityStaysUnknown() throws Exception {
        var f=fixture();var item=artifact("/main/one","life-1","one");var original=cut(f,List.of(item),Completeness.COMPLETE);
        var facts=new ArrayList<>(original.facts());
        for(String slot:List.of("one","two"))facts.add(new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot","unmapped",slot,"snapshot"),RuntimeFactKind.PROPERTY,
                Map.of("artifactSemanticId","unmapped","name","items","values",List.of(slot),"valueTypes",List.of("java.lang.String")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        var accepted=new RuntimeSnapshot(original.snapshotId(),original.modelRevision(),original.captureStartedAt(),original.captureEndedAt(),original.startWatermarks(),original.endWatermarks(),1,facts,original.sourceCompleteness(),original.stateFingerprint(),original.captureMetadata());
        f.engine().applyMappingCut(accepted,"run",1,List.of());var object=f.engine().objectForSemanticId(item.id().canonical());
        assertEquals(new StringValue("one"),object.state(f.engine().system().state()).attributeValue("status"));
        facts.add(new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot",item.property().id().scope(),"another-slot","snapshot"),
                RuntimeFactKind.PROPERTY,item.property().values(),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        var ambiguous=new RuntimeSnapshot(original.snapshotId(),original.modelRevision(),original.captureStartedAt(),original.captureEndedAt(),original.startWatermarks(),original.endWatermarks(),1,facts,original.sourceCompleteness(),original.stateFingerprint(),original.captureMetadata());
        f.engine().applyMappingCut(ambiguous,"run",1,List.of());
        assertTrue(object.state(f.engine().system().state()).attributeValue("status").isUndefined());
        assertEquals("COMPACT_PROPERTY_AMBIGUOUS",f.engine().metadata(object,"compact:quality:status"));
    }
    @Test void exactDisposalRemovesOnlyItsPhysicalIncarnationAndStaleEpochCannotModifyTheCut() throws Exception {
        var fixture=fixture();var engine=fixture.engine();var a=artifact("/main/one","life-1","one");var b=artifact("/main/two","life-2","two");
        engine.applyMappingCut(cut(fixture,List.of(a,b),Completeness.COMPLETE),"run",1,List.of());
        var first=engine.objectForSemanticId(a.id().canonical());var second=engine.objectForSemanticId(b.id().canonical());
        var stale=disposal("old-run",a.id());
        assertThrows(NativeRuntimeProtocolException.class,()->engine.applyMappingCut(cut(fixture,List.of(b),Completeness.COMPLETE),"run",1,List.of(stale),stale));
        assertSame(first,engine.objectForSemanticId(a.id().canonical()));assertSame(second,engine.objectForSemanticId(b.id().canonical()));
        var current=disposal("run",a.id());
        engine.applyMappingCut(cut(fixture,List.of(b),Completeness.COMPLETE),"run",1,List.of(current),current);
        assertNull(engine.objectForSemanticId(a.id().canonical()));assertSame(second,engine.objectForSemanticId(b.id().canonical()));
    }
    private static RuntimeEvent disposal(String run,BridgeEntityId artifact) {
        return new RuntimeEvent("disposal",run,1,"revision","cartago","cartago",1,Instant.EPOCH,RuntimeEventKind.DISPOSED,RuntimeFactKind.ARTIFACT,
                ProjectionStatus.MATERIALIZED_FAITHFULLY,artifact,null,"","",Map.of(),Map.of("normalizedEventKind","DELETE_CARTAGO_ARTIFACT"),
                new SourceWatermark("cartago",1),Completeness.COMPLETE,List.of());
    }
    private record Fixture(SemanticProgram program,NativeRuntimeMutationEngine engine,ModelSnapshot source) { }
    @Test void exactAgentDeclarationDoesNotConflictWithItsSameNamedSourceProgram() throws Exception {
        var fixture=fixture();var catalog=SourceSymbolCatalog.fromSnapshot(fixture.source().semanticContract());
        assertNull(catalog.resolve(SourceReference.parse("agent:courier")).owner());
        assertEquals("src/agt/courier.asl",catalog.resolve(SourceReference.parse("agent:src/agt/courier.asl")).owner());
        assertEquals("agent-declaration",fixture.program().bindings().find(SemanticBindingIndex.Kind.ACTOR,"Courier").orElseThrow().sourceId());
    }
    private Fixture fixture() throws Exception {
        var jcm=temp.resolve("delivery.jcm");Files.writeString(jcm,"mas delivery {}");
        var csl=temp.resolve("delivery.csl");Files.writeString(csl,"csl v4.0 Delivery { entity Parcel { attribute status : String required mutable; } }");
        var istar=temp.resolve("delivery.istar");Files.writeString(istar,"istar DeliveryGoals { role Courier { goal Delivered : Achieve } }");
        var xml=temp.resolve("mapping.xml");Files.writeString(xml,"""
          <mapping id="delivery" xmlns="urn:use:jacamo-mapping:3" version="3.0">
            <csl><map source="artifact:env.Parcel" target="Parcel"/><map source="artifact:env.Parcel/property:status" target="Parcel.status"/></csl>
            <istar><map source="agent:courier" target="Courier"/></istar>
          </mapping>
          """);
        var inputs=MappingInputs.read(jcm,xml,null,csl,istar);
        var env=new CartagoSemanticContract.EnvironmentSemantic(meta("environment","CARTAGO_ENVIRONMENT"),"environment","environment","1","local",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                List.of(new CartagoSemanticContract.LiveObservablePropertySemantic(meta("property","CARTAGO_PROPERTY"),"artifact-declaration","slot","status",List.of(),List.of())),List.of(),List.of(),List.of(),List.of(),List.of());
        var semantic=new JacamoSemanticSnapshot(JacamoSemanticSnapshot.CURRENT_VERSION,
                new JcmSemanticContract.ProjectSemantic(meta("project","JACAMO_PROJECT"),"delivery",inputs.projectDigest()),
                List.of(new JcmSemanticContract.AgentDeclarationSemantic(meta("agent-declaration","JACAMO_AGENT_DECLARATION"),"courier","src/agt/courier.asl",Map.of(),List.of(),"","","",1)),List.of(),
                List.of(new JcmSemanticContract.ArtifactDeclarationSemantic(meta("artifact-declaration","JACAMO_ARTIFACT_DECLARATION"),"parcel","workspace","env.Parcel",List.of())),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                List.of(new JasonSemanticContract.AgentProgramSemantic(meta("program","JASON_AGENT_PROGRAM"),"agent-declaration","project:/src/agt/courier.asl","a".repeat(64),
                    new JasonSemanticContract.PlanLibrarySemantic(meta("library","JASON_PLAN_LIBRARY"),List.of()),List.of(),List.of(),List.of(),List.of())),
                List.of(env),List.of(),List.of(),List.of());
        var source=new ModelSnapshot("revision",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of(),semantic);
        var program=new GenericMappingEngine().map(source,inputs);
        var binding=new Use75CslModelBackend().compile(program.cslModel(),M0ExtensionSpec.observedPlayers());
        new CanonicalUseBindingAdapter().install(program,binding,new CodeGroundedTraceCollector());
        var system=new MSystem(binding.model());
        return new Fixture(program,new NativeRuntimeMutationEngine(system,new NativeObjectBindings(),new CodeGroundedRuntimeRuleRegistry(),program),source);
    }
    private static SemanticMetadata meta(String id,String kind) {return new SemanticMetadata(id,kind,"fixture",EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(),List.of());}
    private record Observed(RuntimeFact fact,RuntimeFact workspace,RuntimeFact property) {BridgeEntityId id(){return fact.id();}}
    private static Observed artifact(String path,String life,Object value) {
        String workspace="cartago:workspace:environment:"+path;String identity="cartago:artifact:environment:"+path+":"+life;
        var ws=new RuntimeFact(new BridgeEntityId("cartago","environment","workspace","environment",path,"workspace-"+life),RuntimeFactKind.WORKSPACE,
                Map.of("semanticId",workspace,"environmentId","environment","fullName",path,"uuid","workspace-"+life),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var fact=new RuntimeFact(new BridgeEntityId("cartago","environment","artifact",path,"parcel",life),RuntimeFactKind.ARTIFACT,
                Map.of("semanticId",identity,"name","parcel","uuid",life,"workspaceSemanticId",workspace,"artifactTypeJavaClassName","env.Parcel"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var property=new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot",identity,"slot","snapshot"),RuntimeFactKind.PROPERTY,
                Map.of("artifactSemanticId",identity,"name","status","values",List.of(value),"valueTypes",List.of("java.lang.String")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        return new Observed(fact,ws,property);
    }
    private static RuntimeSnapshot cut(Fixture f,List<Observed> observations,Completeness quality) {
        var facts=new ArrayList<RuntimeFact>();for(var item:observations){facts.add(item.workspace());facts.add(item.fact());facts.add(item.property());}
        var marks=Map.of("cartago",new SourceWatermark("cartago",1));
        return new RuntimeSnapshot("cut", "revision",Instant.EPOCH,Instant.EPOCH,marks,marks,1,facts,Map.of("cartago",quality),"component-fixture",Map.of());
    }
    private static MSystem system() {
        var errors=new StringWriter();var model=USECompiler.compileSpecification(new ByteArrayInputStream("""
          model Delivery
          class Parcel
          attributes status : String
          end
          association Route between
            Parcel[*] role sourceParcel
            Parcel[*] role targetParcel
          end
          """.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"delivery.use",new PrintWriter(errors),new ModelFactory());
        assertNotNull(model,errors.toString());return new MSystem(model);
    }
}

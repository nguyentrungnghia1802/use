package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Jason object handles and CArtAgO facts. A reference is not a creation or Task success certificate. */
class XmlArtifactReferenceTest {
    @TempDir Path temp;
    @Test void typedSourceReferencesResolveOnlyCurrentPhysicalObjectsWithoutNameFallback()throws Exception {
        String type=NeutralFieldArtifact.class.getName();
        Path jcm=Files.writeString(temp.resolve("demo.jcm"),"mas inventory { agent worker : worker.asl workspace main { artifact stock: "+type+" } }\n");
        Path asl=Files.writeString(temp.resolve("worker.asl"),"+!inspect(A) <- .print(\"reference observed\").\n");
        Path ocl=Files.writeString(temp.resolve("case.ocl"),"context X inv Valid: true\n");
        String xml=MappingXmlReaderTest.XML.replace("1".repeat(64),GenericMappingEngineTest.hash(jcm)).replace("2".repeat(64),GenericMappingEngineTest.hash(ocl))
            .replace("</sources>","<file id=\"behavior\" path=\"worker.asl\" kind=\"ASL\" sha256=\""+GenericMappingEngineTest.hash(asl)+"\"/></sources>")
            .replace("<runtime/>","<runtime><datasets><artifactReferences id=\"references\" requestRef=\"steps\"/></datasets></runtime>")
            .replace("<requirements/>","<requirements><capability id=\"steps\" provider=\"jason.plan-boundaries.v1\" required=\"true\" sourceRef=\"behavior\"><plan trigger=\"inspect(A)\"><step task=\"InspectHandle\" first=\"0\" after=\"1\" variables=\"A\" artifactVariable=\"A\" artifactClass=\""+type+"\"/></plan></capability></requirements>");
        Path mapping=Files.writeString(temp.resolve("mapping.xml"),xml);var inputs=MappingInputs.read(jcm,mapping,ocl);var requests=CapabilityRequirements.compile(inputs);
        var source=GenericMappingEngineTest.official(jcm);var program=source.semanticContract().jasonPrograms().getFirst();
        var environment=CartagoEnvironment.getInstance();environment.init();var creator=new CartagoBasicContext("reference"+UUID.randomUUID().toString().replace("-",""));
        var artifact=creator.makeArtifact(creator.getJoinedWspId("main"),"referenceStock"+UUID.randomUUID().toString().replace("-",""),type);
        var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"run",source);
        var agent=new Agent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg();agent.setASLSrc(asl.toUri().toString());try(var input=Files.newInputStream(asl)){agent.parseAS(input,asl.toUri().toString());}
        // No external action or environment session is needed to observe an already-ground handle.
        var arch=new jaca.CAgentArch(){@Override public String getAgName(){return "worker";}@Override public Collection<Literal> perceive(){return List.of();}};
        var ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),arch);
        var identity=new BridgeEntityId("jason","agent","runtime-agent","inventory","worker","incarnation");var frames=new ArrayList<VerificationObservation>();
        var context=new FrameworkObservationProviders.Context(environment,source,temp,"run",1,frames::add,reason->fail(reason));
        try(var snapshots=new SnapshotCoordinator(List.of(cartago),128);var session=FrameworkObservationProviders.agent(context,ts,identity,program).open(requests)) {
            assertTrue(session.requiredUnavailable().isEmpty(),session.capabilities().toString());
            ts.getC().addAchvGoal(ASSyntax.createLiteral("inspect",arch.getJavaLib().objectToTerm(artifact)),Intention.EmptyInt);for(int i=0;i<25;i++)ts.reasoningCycle();
            assertEquals(2,frames.size());assertEquals(Completeness.COMPLETE,frames.getLast().quality(),frames.getLast().diagnostic());
            var physical=snapshots.capture(source.modelRevision(),3).snapshot();var facts=new LinkedHashMap<String,RuntimeFact>();physical.facts().forEach(f->facts.put(f.id().canonical(),f));
            facts.put(identity.canonical(),new RuntimeFact(identity,RuntimeFactKind.AGENT,Map.of("name","worker"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
            long sequence=0;for(var frame:frames){var event=frame.event("run",1,source.modelRevision(),++sequence,java.time.Instant.EPOCH,"","",true);facts.put(event.entityId().canonical(),new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of()));}
            var marks=new LinkedHashMap<>(physical.endWatermarks());marks.put("jason",new SourceWatermark("jason",1));marks.put(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,sequence));
            var quality=new LinkedHashMap<>(physical.sourceCompleteness());quality.put("jason",Completeness.COMPLETE);quality.put(VerificationObservation.SOURCE_ID,Completeness.COMPLETE);
            var cut=new RuntimeSnapshot("references",source.modelRevision(),java.time.Instant.EPOCH,java.time.Instant.EPOCH,marks,marks,1,new ArrayList<>(facts.values()),quality,"reference-state");
            var sources=new RuntimeFactSources(inputs.specification(),source,requests);var selected=sources.capture(cut,"run",1).get("references");
            assertEquals(Completeness.PARTIAL,selected.populationQuality());assertEquals(1,selected.rows().size());var row=selected.rows().getFirst();
            var actual=physical.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().incarnation().equals(artifact.getId().toString())).findFirst().orElseThrow();
            assertEquals(actual.id().canonical(),row.identity());assertEquals(Completeness.COMPLETE,row.quality());assertTrue(row.aliases().contains(actual.values().get("semanticId")));
            assertEquals("InspectHandle",row.values().get("task"));assertEquals("SUCCEEDED",row.values().get("phase"));assertEquals(identity.canonical(),row.values().get("agent"));
            assertEquals(actual.id().canonical(),row.proofs().get("physical-identity"));
            var absent=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),marks,marks,1,cut.facts().stream().filter(f->!f.id().equals(actual.id())).toList(),quality,cut.stateFingerprint());
            assertTrue(sources.capture(absent,"run",1).get("references").rows().isEmpty(),"A past source handle cannot create a world object absent from the actual cut");
            var oldValues=new LinkedHashMap<>(actual.values());oldValues.put("uuid",UUID.randomUUID().toString());
            var replacement=new RuntimeFact(new BridgeEntityId("cartago","environment","artifact",actual.id().scope(),actual.id().localId(),(String)oldValues.get("uuid")),actual.kind(),oldValues,actual.relations(),actual.projectionStatus(),actual.completeness(),actual.evidence());
            var changed=new ArrayList<>(cut.facts());changed.replaceAll(f->f.id().equals(actual.id())?replacement:f);
            var recreated=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),marks,marks,1,changed,quality,cut.stateFingerprint());
            assertTrue(sources.capture(recreated,"run",1).get("references").rows().isEmpty(),"Same display name must not redirect a typed old UUID");
        }finally {
            agent.stopAg();environment.getController("/main").removeArtifact(artifact.getName());
            for(var id:environment.getController("/main").getCurrentAgents())if(id.getAgentName().equals(creator.getName()))environment.resolveWSP("/main").getWorkspace().quitAgent(id);
            environment.shutdown();
        }
    }
}

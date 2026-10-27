package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import cartago.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact source Jason plans with fixture-only action dispatch to actual CArtAgO.
 * No changed source/oracle, full MAS, external TCP or Task acceptance is claimed. */
class AuctionDeclarativeArtifactTest {
    @ParameterizedTest(name="authored={0}") @ValueSource(booleans={false,true}) @Timeout(60)
    void exactSourceHandlesArgumentsAndCreationReceiptsDriveXmlArtifactState(boolean authored)throws Exception {
        try(var f=new AuctionOrgFixture()) {
            var live=f.live;f.organizationBoard();var group=f.group();
            var root=AuctionDeclarativeSpecificationTest.CASE;var selected=root.resolve("authored-v1");
            var inputs=authored?MappingInputs.read(root.resolve("auction.jcm"),selected.resolve("auction-mapping.xml"),selected.resolve("auction_constraints_final_fixed.ocl"),selected.resolve("auction_csl.csl"),selected.resolve("auction_istar_final.istar")):AuctionDeclarativeSpecificationTest.inputs(root);
            var program=new GenericMappingEngine().map(live.source,inputs);
            var requests=program.xmlPlan().requirements();var discovery=requests.stream().filter(r->r.id().equals("workspace-operations")).findFirst().orElseThrow();
            var field=requests.stream().filter(r->r.id().equals("candidate-field")).findFirst().orElseThrow();
            var steps=requests.stream().filter(r->r.id().equals("execution-steps")).findFirst().orElseThrow();
            var context=new FrameworkObservationProviders.Context(live.environment,live.source,AuctionDeclarativeSpecificationTest.CASE.toRealPath(),live.run.id(),live.run.generation(),ignored->{},reason->fail(reason));
            var frames=new ArrayList<VerificationObservation>();int[] consumed={0};
            try(var history=new CartagoOperationHistory(discovery,context);var fields=new ArtifactFieldObservation(live.environment,live.source,field,AuctionDeclarativeSpecificationTest.CASE.toRealPath());
                var driver=new SourceDriver(f,steps,frames);
                var capture=authored?new AuctionDeclarativeObserverTest.Capture(f,context,requests,history,frames):null) {
                // The authored containment rule requires a qualified physical
                // Scheme -> responsible Group receipt, not a workspace guess.
                java.util.concurrent.Callable<RuntimeSnapshot> snapshot=()->capture==null?observed(f,frames,driver.identity,discovery,history,field,fields):capture.cut(frames,driver.identity,discovery,history,field,fields);
                var first=f.scheme("xmlOne","delivery(city,date(15,12,2015))");var second=f.scheme("xmlTwo","service_two");
                if(authored){live.actor.doAction(group,new Op("addScheme",first.getName()),5000);live.actor.doAction(group,new Op("addScheme",second.getName()),5000);}
                driver.start(first,"xmlOne","delivery(city,date(15,12,2015))");driver.start(second,"xmlTwo","service_two");
                assertEquals(8,frames.stream().filter(o->o.kind()==VerificationObservation.Kind.JASON_STEP).count());assertTrue(frames.stream().allMatch(o->o.quality()==Completeness.COMPLETE),frames.toString());
                while(consumed[0]<live.events.size())history.observe(live.events.get(consumed[0]++));
                var built=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                try(var projector=new NativeRuntimeProjector(built,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                    var cut=snapshot.call();
                    var futureArguments=copy(cut,cut.facts().stream().map(fact->{if(fact.kind()!=RuntimeFactKind.SCHEME_BOARD)return fact;
                        var values=new LinkedHashMap<>(fact.values());values.put("schemeArgumentObservationVersion","999");return new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());}).toList());
                    var sources=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources(program.xmlPlan().inputs().specification(),live.source,requests);
                    assertTrue(sources.capture(futureArguments,live.run.id(),live.run.generation()).get("source-step-artifacts").rows().isEmpty(),"An unsupported structured argument observation version cannot supply a binding");
                    var referencedSchemes=sources.capture(cut,live.run.id(),live.run.generation()).get("source-step-schemes").rows().stream().filter(row->row.values().get("task").equals("A_start_t1")).toList();
                    assertEquals(2,referencedSchemes.size());
                    for(var row:referencedSchemes){assertEquals(row.values().get("scheme"),row.identity());assertEquals(row.identity(),row.proofs().get("physical-identity"));assertNotEquals(row.identity(),row.values().get("domainIdentity"));assertEquals(Set.of(row.identity()),row.aliases(),"A referenced board cannot acquire aliases of the domain Artifact");}
                    projector.applySnapshot(cut,live.run.id(),live.run.generation());
                    var system=projector.system();var auctions=system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).toList();
                    assertEquals(2,auctions.size(),projector.trace().toString());
                    for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.ARTIFACT&&"auction_env.AuctionArtifact".equals(fact.values().get("artifactTypeJavaClassName"))) {
                        var object=projector.mutations().objectForSemanticId(fact.id().canonical());assertNotNull(object);
                        assertSame(object,projector.mutations().objectForSemanticId((String)fact.values().get("semanticId")),"Only the verified current physical Artifact may own its callback alias");
                    }
                    assertEquals(Set.of("xmlOne","xmlTwo"),auctions.stream().map(o->o.state(system.state()).attributeValue("id").toString()).map(s->s.substring(1,s.length()-1)).collect(java.util.stream.Collectors.toSet()));
                    assertTrue(auctions.stream().allMatch(o->o.state(system.state()).attributeValue("running").toString().equals("true")));
                    assertEquals(2,system.state().allLinks().stream().filter(l->l.association().name().equals("CslEntityScopes")).count());
                    var artifact=live.artifacts.stream().filter(a->a.getName().equals("xmlOne")).findFirst().orElseThrow();
                    live.actor.doAction(artifact,new Op("bid",9.0),5000);live.actor.doAction(artifact,new Op("bid",12.0),5000);while(consumed[0]<live.events.size())history.observe(live.events.get(consumed[0]++));
                    projector.applySnapshot(snapshot.call(),live.run.id(),live.run.generation());
                    var one=auctions.stream().filter(o->o.state(system.state()).attributeValue("id").toString().equals("'xmlOne'")).findFirst().orElseThrow();
                    assertEquals("9.0",one.state(system.state()).attributeValue("best_bid").toString());
                    assertEquals("'"+live.actor.getName()+"'",one.state(system.state()).attributeValue("currentWinner").toString());
                    live.actor.doAction(artifact,new Op("stop"),5000);while(consumed[0]<live.events.size())history.observe(live.events.get(consumed[0]++));
                    projector.applySnapshot(snapshot.call(),live.run.id(),live.run.generation());
                    assertSame(system,projector.system());assertEquals("false",one.state(system.state()).attributeValue("running").toString());
                    assertEquals(one.state(system.state()).attributeValue("currentWinner"),one.state(system.state()).attributeValue("winner"));
                    var known=snapshot.call();
                    var unavailable=copy(known,known.facts().stream().map(fact->{if(fact.kind()!=RuntimeFactKind.ARTIFACT||!"auction_env.AuctionArtifact".equals(fact.values().get("artifactTypeJavaClassName")))return fact;
                        var values=new LinkedHashMap<>(fact.values());values.remove(CapabilityObservation.KEY);return new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());}).toList());
                    projector.applySnapshot(unavailable,live.run.id(),live.run.generation());
                    for(String attribute:List.of("running","task","best_bid","winner","currentWinner"))assertTrue(one.state(system.state()).attributeValue(attribute).isUndefined(),"Missing coherent field evidence must invalidate "+attribute);
                    assertEquals("'xmlOne'",one.state(system.state()).attributeValue("id").toString());
                    projector.applySnapshot(snapshot.call(),live.run.id(),live.run.generation());
                    String previousName=one.name(),previousLifetime=projector.mutations().metadata(one,"xml:m0:lifetime");
                    int from=live.events.size();live.dispose(artifact);for(var event:List.copyOf(live.events.subList(from,live.events.size())))projector.apply(event);
                    assertEquals(1,system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count(),"Actual disposal, not missing enumeration, removes the old incarnation");
                    driver.start(first,"xmlOne","delivery(city,date(15,12,2015))");while(consumed[0]<live.events.size())history.observe(live.events.get(consumed[0]++));
                    projector.applySnapshot(snapshot.call(),live.run.id(),live.run.generation());
                    var recreated=system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")&&o.state(system.state()).attributeValue("id").toString().equals("'xmlOne'")).findFirst().orElseThrow();
                    assertNotEquals(previousName,recreated.name());assertNotEquals(previousLifetime,projector.mutations().metadata(recreated,"xml:m0:lifetime"));
                }
            }
        }
    }
    static RuntimeSnapshot copy(RuntimeSnapshot cut,List<RuntimeFact> facts){return new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),facts,cut.sourceCompleteness(),cut.stateFingerprint(),cut.captureMetadata());}
    static RuntimeSnapshot observed(AuctionOrgFixture f,List<VerificationObservation> frames,BridgeEntityId agent,ObservationCapabilityRequest discovery,CartagoOperationHistory history,ObservationCapabilityRequest field,ArtifactFieldObservation fields)throws Exception {
        var live=f.live;var cut=f.cut();var scope=new CapabilityObservation.Scope(live.run.id(),live.run.generation(),live.source.modelRevision(),"cartago",cut.endWatermarks().get("cartago").sequence());var facts=new LinkedHashMap<String,RuntimeFact>();
        for(var fact:cut.facts()) {
            var values=new LinkedHashMap<>(fact.values());var receipts=new LinkedHashMap<String,Object>();
            if(fact.kind()==RuntimeFactKind.ARTIFACT) {
                var id=live.environment.getController(fact.id().scope()).getArtifactInfo(fact.id().localId()).getId();
                if(id.getArtifactType().equals("cartago.WorkspaceArtifact"))receipts.put(discovery.id(),CapabilityObservation.of(discovery,scope,Completeness.COMPLETE,"",history.capture(id)).payload());
                if(id.getArtifactType().equals("auction_env.AuctionArtifact"))receipts.put(field.id(),CapabilityObservation.of(field,scope,Completeness.COMPLETE,"",fields.capture(id,"SNAPSHOT","")).payload());
            }
            if(!receipts.isEmpty())values.put(CapabilityObservation.KEY,receipts);
            facts.put(fact.id().canonical(),new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence()));
        }
        facts.put(agent.canonical(),new RuntimeFact(agent,RuntimeFactKind.AGENT,Map.of("name",agent.localId(),"semanticId",agent.canonical(),"normalizedEventKind","UPSERT_JASON_AGENT_STATE"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        long sequence=0;for(var frame:frames){var event=frame.event(live.run.id(),live.run.generation(),live.source.modelRevision(),++sequence,java.time.Instant.EPOCH,"", "",true);facts.put(event.entityId().canonical(),new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of()));}
        var marks=new LinkedHashMap<>(cut.endWatermarks());marks.put("jason",new SourceWatermark("jason",1));marks.put(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,sequence));
        var coverage=new LinkedHashMap<>(cut.sourceCompleteness());coverage.put("jason",Completeness.COMPLETE);coverage.put(VerificationObservation.SOURCE_ID,Completeness.PARTIAL);
        return new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),marks,marks,1,new ArrayList<>(facts.values()),coverage,cut.stateFingerprint());
    }
    static final class SourceDriver implements AutoCloseable {
        final AuctionOrgFixture fixture;final cartago.util.agent.CartagoBasicContext actor;final Agent agent=new Agent();final BridgeEntityId identity;final TransitionSystem ts;final JasonStepCapability observer;final jaca.CAgentArch arch;final AutoCloseable registration;int action;
        java.util.function.Consumer<VerificationObservation> emission;
        SourceDriver(AuctionOrgFixture f,ObservationCapabilityRequest request,List<VerificationObservation> frames)throws Exception {
            this(f,request,frames,f.live.actor,"bob");
        }
        SourceDriver(AuctionOrgFixture f,ObservationCapabilityRequest request,List<VerificationObservation> frames,cartago.util.agent.CartagoBasicContext actor,String declaration)throws Exception {
            fixture=f;this.actor=actor;emission=frames::add;var live=f.live;identity=new BridgeEntityId("jason","agent","runtime-agent",live.source.semanticContract().project().name(),actor.getName(),UUID.randomUUID().toString());
            registration=CslRuntimeTestRegistry.bind(live.source.semanticContract(),actor.getName(),declaration,identity);
            agent.setConsiderToAddMIForThisAgent(false);agent.initAg();var path=AuctionDeclarativeSpecificationTest.CASE.resolve("src/agt/auction_capabilities.asl").toRealPath();agent.setASLSrc(path.toUri().toString());try(var input=Files.newInputStream(path)){agent.parseAS(input,path.toUri().toString());}
            arch=new jaca.CAgentArch(){@Override public String getAgName(){return actor.getName();}@Override public Collection<Literal> perceive(){return List.of();}@Override public void act(ActionExec a){dispatch(a);}};
            ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),arch);
            var source=live.source.semanticContract().jasonPrograms().stream().filter(p->p.declarationId().equals(declaration)).findFirst().orElseThrow();
            observer=new JasonStepCapability(request,ts,identity,live.run.id(),live.run.generation(),live.source.modelRevision(),source,AuctionDeclarativeSpecificationTest.CASE.toRealPath(),frame->emission.accept(frame),reason->fail(reason));
        }
        void start(ArtifactId scheme,String id,String service)throws Exception {
            var schemeTerm=ASSyntax.createAtom(scheme.getName());
            for(var entry:Map.of("Id",id,"Service",service).entrySet()) {
                var belief=ASSyntax.createLiteral("goalArgument",schemeTerm,ASSyntax.createAtom("auction"),ASSyntax.createString(entry.getKey()),ASSyntax.createString(entry.getValue()));belief.addAnnot(ASSyntax.createLiteral("artifact_id",arch.getJavaLib().objectToTerm(scheme)));agent.addBel(belief);
            }
            goal("start",scheme);
        }
        void arguments(ArtifactId scheme,String id)throws Exception {
            var belief=ASSyntax.createLiteral("goalArgument",ASSyntax.createAtom(scheme.getName()),ASSyntax.createAtom("auction"),ASSyntax.createString("Id"),ASSyntax.createString(id));belief.addAnnot(ASSyntax.createLiteral("artifact_id",arch.getJavaLib().objectToTerm(scheme)));agent.addBel(belief);
        }
        void goal(String name,ArtifactId scheme)throws Exception {
            var goal=ASSyntax.createLiteral(name);goal.addAnnot(ASSyntax.createLiteral("scheme",ASSyntax.createAtom(scheme.getName())));ts.getC().addAchvGoal(goal,Intention.EmptyInt);
            for(int cycle=0;cycle<80;cycle++)ts.reasoningCycle();
        }
        void until(java.util.function.BooleanSupplier condition)throws Exception {
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while(!condition.getAsBoolean()&&System.nanoTime()<deadline){ts.reasoningCycle();Thread.sleep(2);}
            assertTrue(condition.getAsBoolean(),"Actual source callback timed out");
        }
        void dispatch(ActionExec execution) {
            String correlation=UUID.randomUUID().toString();observer.action(execution,RuntimeEventKind.STARTED,correlation);
            try {
                var term=execution.getActionTerm();var live=fixture.live;
                switch(term.getFunctor()) {
                    case "makeArtifact" -> {
                        String name=((StringTerm)term.getTerm(0)).getString();var artifact=live.auction(name);assertTrue(execution.getIntention().peek().getUnif().unifies(arch.getJavaLib().objectToTerm(artifact),term.getTerm(3)));
                    }
                    case "focus" -> {
                        var artifact=(ArtifactId)arch.getJavaLib().termToObject(term.getTerm(0));assertNotNull(artifact,term.toString());actor.doAction(new Op("focus",artifact),artifact.getWorkspaceId(),5000);
                        // Fixture percept from an actual successful focus; unused source query arguments are explicit fixture placeholders.
                        agent.addBel(ASSyntax.createLiteral("focusing",arch.getJavaLib().objectToTerm(artifact),ASSyntax.createString(artifact.getName()),ASSyntax.createString(artifact.getArtifactType()),ASSyntax.createString(artifact.getWorkspaceId().getFullName()),ASSyntax.createAtom("fixture"),ASSyntax.createAtom("fixture")));
                    }
                    case "lookupArtifact" -> {
                        var result=new OpFeedbackParam<ArtifactId>();actor.doAction(new Op("lookupArtifact",arch.getJavaLib().termToObject(term.getTerm(0)),result),actor.getJoinedWspId("main"),5000);
                        assertTrue(execution.getIntention().peek().getUnif().unifies(arch.getJavaLib().objectToTerm(result.get()),term.getTerm(1)));
                    }
                    case "start" -> {
                        var annotation=term.getAnnot("artifact_id");var artifact=(ArtifactId)arch.getJavaLib().termToObject(annotation.getTerm(0));actor.doAction(artifact,new Op("start",arch.getJavaLib().termToObject(term.getTerm(0))),5000);
                    }
                    case "bid","stop" -> {
                        var artifact=(ArtifactId)arch.getJavaLib().termToObject(term.getAnnot("artifact_id").getTerm(0));
                        actor.doAction(artifact,term.getFunctor().equals("stop")?new Op("stop"):new Op("bid",((NumberTerm)term.getTerm(0)).solve()),5000);
                    }
                    default -> throw new IllegalStateException("Unexpected fixture action: "+term);
                }
                execution.setResult(true);observer.action(execution,RuntimeEventKind.SUCCEEDED,correlation);ts.getC().addFeedbackAction(execution);
            }catch(Exception error){throw new AssertionError("Actual source action failed",error);}
        }
        @Override public void close()throws Exception{try{observer.close();agent.stopAg();}finally{registration.close();}}
    }
}

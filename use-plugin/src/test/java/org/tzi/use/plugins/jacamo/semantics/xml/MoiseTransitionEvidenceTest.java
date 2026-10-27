package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.MoiseSchemeCutDecoder;
/** Actual CArtAgO source callbacks, public SchemeBoard state; internal transition timestamps remain unavailable. */
class MoiseTransitionEvidenceTest {
    @Test @Timeout(35) void genericProviderRetainsPublicTransitionCutsAndSeparatesAdministrationFromDoneEvidence()throws Exception {
        Path root=Path.of("examples/csl-counter").toRealPath();var source=GenericMappingEngineTest.official(root.resolve("counter.jcm"));
        var request=new ObservationCapabilityRequest("scheme",MoiseSchemeCapability.PROVIDER,true,Map.of("organization","counter_service","scheme","work"));
        var environment=CartagoEnvironment.getInstance();var config=jason.util.Config.get();String web=config.getProperty(jason.util.Config.START_WEB_OI);config.setProperty(jason.util.Config.START_WEB_OI,"false");environment.init();
        var actor=new CartagoBasicContext("transition"+UUID.randomUUID().toString().replace("-",""));var artifacts=new ArrayList<ArtifactId>();
        var frames=new ConcurrentLinkedQueue<Map<String,Object>>();var workspace=environment.resolveWSP("/main").getWorkspace();
        try(var provider=new MoiseSchemeCapability(request,environment,source,root,"run",1)) {
            var logger=(ICartagoLogger)java.lang.reflect.Proxy.newProxyInstance(ICartagoLogger.class.getClassLoader(),new Class<?>[]{ICartagoLogger.class},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->"TransitionEvidenceLogger";};
                if(Set.of("artifactCreated","newPercept").contains(method.getName())&&args[1] instanceof ArtifactId id&&id.getArtifactType().equals("ora4mas.nopl.SchemeBoard"))frames.add(provider.capture(id,method.getName().equals("artifactCreated")?"ARTIFACT_CREATED":"PROPERTY_PUBLICATION",(Long)args[0],"",null,""));
                if(Set.of("opStarted","opCompleted","opFailed").contains(method.getName())&&args[1] instanceof OpId operation&&args[2] instanceof ArtifactId id&&args[3] instanceof Op op&&op.getName().equals("admCommand")&&op.getParamValues().length==1&&op.getParamValues()[0] instanceof String command)
                    frames.add(provider.capture(id,method.getName().equals("opStarted")?"OP_STARTED":method.getName().equals("opCompleted")?"OP_SUCCEEDED":"OP_FAILED",(Long)args[0],operation.toString(),operation.getAgentBodyId(),command));
                return null;
            });
            workspace.registerLogger(logger);
            try {
                var feedback=new OpFeedbackParam<ArtifactId>();actor.doAction(new Op("makeArtifact","transitionSource","ora4mas.nopl.SchemeBoard",new Object[]{root.resolve("src/org/organization.xml").toString(),"work"},feedback),actor.getJoinedWspId("main"),5000);artifacts.add(feedback.get());
                var baseline=provider.capture(feedback.get(),"SNAPSHOT",0,"",null,"");assertEquals("COMPLETE",baseline.get("status"),baseline.toString());assertEquals("COMPLETE",baseline.get("historyQuality"),"baseline="+baseline+"; callbacks="+frames);
                assertTrue(baseline.containsKey("transitions"),"Frozen membership must come from a retained source transition cut, not the later consumer snapshot");
                var done=baseline.get("done");actor.doAction(feedback.get(),new Op("admCommand","goalSatisfied(first)"),5000);
                var after=provider.capture(feedback.get(),"SNAPSHOT",0,"",null,"");assertEquals("COMPLETE",after.get("status"),after.toString());
                var transitions=((List<?>)after.get("transitions")).stream().map(CanonicalJson::object).toList();
                assertTrue(transitions.stream().anyMatch(t->t.get("goal").equals("first")&&t.get("state").equals("SATISFIED")));
                assertEquals(done,after.get("done"),"Administrative satisfaction does not create an actual goalAchieved/done witness");
                assertTrue(transitions.stream().allMatch(t->t.get("timeAuthority").equals("PUBLICATION_ONLY_INTERNAL_TRANSITION_TIME_UNAVAILABLE")));
                var frozen=transitions.stream().filter(t->t.get("goal").equals("first")&&t.get("state").equals("SATISFIED")).findFirst().orElseThrow();
                assertTrue(frozen.containsKey("committed")&&frozen.containsKey("achieved")&&frozen.containsKey("done"));assertFalse(after.containsKey("adminBidOverride"));
                assertEquals(frozen,((List<?>)provider.capture(feedback.get(),"SNAPSHOT",0,"",null,"").get("transitions")).stream().map(CanonicalJson::object).filter(t->t.get("id").equals(frozen.get("id"))).findFirst().orElseThrow());
                var board=(ora4mas.nopl.SchemeBoard)workspace.getArtifactDescriptor(feedback.get().getName()).getArtifact();
                var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"run",source);var moise=new MoiseBoardSnapshotSource(board.getOEId(),"run",List::of,()->List.of(board),source.semanticContract(),root);
                try(var snapshots=new SnapshotCoordinator(List.of(cartago,moise),256)) {
                    var cut=snapshots.capture(source.modelRevision(),3).snapshot();
                    var physical=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().incarnation().equals(feedback.get().getId().toString())).findFirst().orElseThrow();
                    var physicalWorkspace=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&Objects.equals(f.values().get("semanticId"),physical.values().get("workspaceSemanticId"))).findFirst().orElseThrow();
                    var publicBoard=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD&&f.id().incarnation().endsWith(":"+feedback.get().getId())).findFirst().orElseThrow();
                    var receiptScope=new CapabilityObservation.Scope("run",1,source.modelRevision(),"cartago",cut.endWatermarks().get("cartago").sequence());
                    var raw=provider.capture(feedback.get(),"SNAPSHOT",0,"",null,"");var receipt=CapabilityObservation.of(request,receiptScope,Completeness.COMPLETE,"",raw);var decoder=new MoiseSchemeCutDecoder(source,request);
                    var decoded=decoder.decode(receipt.payload(),physical,physicalWorkspace,publicBoard,cut,"run",1);assertEquals(Completeness.COMPLETE,decoded.quality(),decoded.diagnostic());
                    var legacy=new LinkedHashMap<String,Object>(raw);legacy.put("version","MOISE_SCHEME_CUT/2.1");legacy.remove("commitmentBindings");legacy.remove("publicationClockProof");
                    legacy.put("transitions",((List<?>)legacy.get("transitions")).stream().map(CanonicalJson::object).map(t->{var old=new LinkedHashMap<>(t);old.remove("commitmentBindings");return old;}).toList());
                    legacy.remove("cutHash");legacy.put("cutHash",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(legacy));
                    var historical=decoder.decode(CapabilityObservation.of(request,receiptScope,Completeness.COMPLETE,"",legacy).payload(),physical,physicalWorkspace,publicBoard,cut,"run",1);
                    assertEquals(Completeness.COMPLETE,historical.quality(),historical.diagnostic());assertEquals(false,CanonicalJson.object(historical.values().get("commitmentBindings")).get("complete"));
                    assertEquals(false,CanonicalJson.object(historical.values().get("confirmation")).get("complete"),"Legacy data cannot retroactively acquire source clock authority");
                    var hybrid=new LinkedHashMap<>(legacy);hybrid.put("publicationClockProof",raw.get("publicationClockProof"));hybrid.remove("cutHash");hybrid.put("cutHash",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(hybrid));
                    assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,receiptScope,Completeness.COMPLETE,"",hybrid).payload(),physical,physicalWorkspace,publicBoard,cut,"run",1).quality(),"Mixed-version field inventories remain rejected");
                    assertTrue(((List<?>)historical.values().get("transitions")).stream().map(CanonicalJson::object).allMatch(t->Boolean.FALSE.equals(CanonicalJson.object(t.get("commitmentBindings")).get("complete"))),"Old captures remain readable but cannot gain historical player identity from current names");
                    var badVersion=new LinkedHashMap<String,Object>(raw);badVersion.put("version",MoiseSchemeCapability.VERSION+"-unknown");badVersion.remove("cutHash");badVersion.put("cutHash",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(badVersion));
                    assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,receiptScope,Completeness.COMPLETE,"",badVersion).payload(),physical,physicalWorkspace,publicBoard,cut,"run",1).quality());
                    assertEquals(true,decoded.values().get("continuous"));assertEquals("UNSUPPORTED",decoded.values().get("internalTransitionTimes"));
                    var effects=((List<?>)decoded.values().get("administrativeEffects")).stream().map(CanonicalJson::object).toList();assertEquals(1,effects.size());
                    assertEquals("goalSatisfied(first)",effects.getFirst().get("command"));assertEquals("OP_SUCCEEDED",effects.getFirst().get("outcome"));assertFalse(((String)effects.getFirst().get("invocation")).isBlank());
                    assertEquals("SATISFIED",CanonicalJson.object(effects.getFirst().get("afterGoals")).get("first"));assertEquals(List.of(),decoded.values().get("done"));
                    assertTrue(((List<?>)decoded.values().get("transitions")).stream().map(CanonicalJson::object).allMatch(t->((Number)t.get("sequence")).longValue()>=((Number)raw.get("birthSequence")).longValue()),"Initialization publications are trace, not the post-creation history prefix");
                    for(String key:List.of("sourceOsSha256","moiseArtifactSha256","cutHash")) {
                        var bad=new LinkedHashMap<>(raw);bad.put(key,"0".repeat(64));
                        assertEquals(Completeness.UNAVAILABLE,decoder.decode(CapabilityObservation.of(request,receiptScope,Completeness.COMPLETE,"",bad).payload(),physical,physicalWorkspace,publicBoard,cut,"run",1).quality());
                    }
                    assertEquals(Completeness.UNAVAILABLE,decoder.decode(receipt.payload(),physical,physicalWorkspace,publicBoard,cut,"run",2).quality());
                    var specification=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>","<runtime><datasets><schemeCuts id=\"lifecycle\" requestRef=\"scheme\"/></datasets></runtime>"));
                    var sources=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources(specification,source,List.of(request));
                    var values=new LinkedHashMap<>(physical.values());values.put(CapabilityObservation.KEY,Map.of(request.id(),receipt.payload()));
                    var qualified=new RuntimeFact(physical.id(),physical.kind(),values,physical.relations(),physical.projectionStatus(),physical.completeness(),physical.evidence());
                    var qualifiedCut=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,cut.facts().stream().map(f->f.id().equals(physical.id())?qualified:f).toList(),cut.sourceCompleteness(),cut.stateFingerprint());
                    var rows=sources.capture(qualifiedCut,"run",1).get("lifecycle").rows();assertEquals(1,rows.size());assertEquals(Completeness.COMPLETE,rows.getFirst().quality());assertEquals(publicBoard.id().canonical(),rows.getFirst().identity());
                    assertEquals(Set.of(publicBoard.id().canonical(),physical.id().canonical()),rows.getFirst().aliases(),"Only two verified identities of the same current physical board are aliases");
                    String observerRuntime="""
                      <runtime><tables><table id="recovery" dataset="acceptedScheme"><expand path="administrativeEffects" as="effect"/>
                       <where path="effect.command" type="String" equals="goalSatisfied(first)"/><where path="effect.outcome" type="String" equals="OP_SUCCEEDED"/>
                       <where path="effect.afterGoals.first" type="String" equals="SATISFIED"/>
                       <column name="uuid" path="schemeIdentity" required="true"/><column name="invocation" path="effect.invocation" required="true"/><column name="done" path="done" required="true"/>
                      </table></tables></runtime>
                      """;
                    var recovery=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",observerRuntime))).execute(Map.of("acceptedScheme",new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables.Dataset(rows,Completeness.PARTIAL))).get("recovery");
                    assertEquals(1,recovery.rows().size());assertEquals(Completeness.COMPLETE,recovery.rows().getFirst().quality());assertEquals(List.of(),recovery.rows().getFirst().values().get("done"));
                    assertEquals(effects.getFirst().get("invocation"),recovery.rows().getFirst().values().get("invocation"));
                    assertFalse(rows.getFirst().proofs().get("capability:scheme").isBlank());assertEquals("UNAVAILABLE",rows.getFirst().values().get("administrativeAbsenceCoverage"));
                    var missing=sources.capture(cut,"run",1).get("lifecycle");assertEquals(Completeness.UNAVAILABLE,missing.rows().getFirst().quality());assertFalse(missing.rows().getFirst().values().containsKey("continuous"));
                    assertEquals(Set.of(publicBoard.id().canonical()),missing.rows().getFirst().aliases(),"A missing provider cannot grant a physical crosswalk");
                    var unclassified=new LinkedHashMap<>(publicBoard.values());unclassified.remove("schemeSpecSemanticId");
                    var partialBoard=new RuntimeFact(publicBoard.id(),publicBoard.kind(),unclassified,publicBoard.relations(),ProjectionStatus.EVIDENCE_ONLY,Completeness.PARTIAL,publicBoard.evidence());
                    var partialCut=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,qualifiedCut.facts().stream().map(f->f.id().equals(publicBoard.id())?partialBoard:f).toList(),cut.sourceCompleteness(),cut.stateFingerprint());
                    assertNotEquals(Completeness.COMPLETE,sources.capture(partialCut,"run",1).get("lifecycle").populationQuality(),"An unclassified partial board cannot certify an empty requested scheme population");
                }
                var malformed=new OpFeedbackParam<ArtifactId>();actor.doAction(new Op("makeArtifact","unsupported-name","ora4mas.nopl.SchemeBoard",new Object[]{root.resolve("src/org/organization.xml").toString(),"work"},malformed),actor.getJoinedWspId("main"),5000);artifacts.add(malformed.get());
                assertEquals("INCOMPLETE",provider.capture(malformed.get(),"SNAPSHOT",0,"",null,"").get("status"),"Unsupported official unquoted term identity stays unavailable; the adapter must not guess its spelling");
            }finally{workspace.unregisterLogger(logger);}
        }finally {
            for(var id:artifacts){var board=(ora4mas.nopl.SchemeBoard)workspace.getArtifactDescriptor(id.getName()).getArtifact();board.getNormativeEngine().stop();environment.getController("/main").removeArtifact(id.getName());}
            CslRuntimeTestRegistry.closeCartagoContext(actor);environment.shutdown();if(web==null)config.remove(jason.util.Config.START_WEB_OI);else config.setProperty(jason.util.Config.START_WEB_OI,web);
        }
    }
}

package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(45)
class AuctionArtifactBindingTest {
    @Test void twoActualAuctionAndOriginalSchemeBoardInstancesBindOnlyAfterCreationAndFocus()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            var resolver=new AuctionArtifactBindingResolver(live.source,live.selectors);
            assertTrue(resolver.resolve(live.run,live.cut(),live.events).bindings().isEmpty());
            live.scheme("sch_a1","a1","flight 'one' ticket");live.scheme("sch_a2","a2","flight two");
            assertEquals(2,resolver.resolve(live.run,live.cut(),live.events).diagnostics().size());
            var first=live.auction("a1");var second=live.auction("a2");
            var result=resolver.resolve(live.run,live.cut(),live.events);
            assertEquals(List.of(),result.diagnostics());assertEquals(2,result.bindings().size());
            var a=result.bindings().stream().filter(b->b.auctionId().equals("a1")).findFirst().orElseThrow();
            var b=result.bindings().stream().filter(v->v.auctionId().equals("a2")).findFirst().orElseThrow();
            assertEquals(first.getId().toString(),a.artifactUuid());assertEquals(second.getId().toString(),b.artifactUuid());
            assertNotEquals(a.schemeUuid(),b.schemeUuid());assertNotEquals(a.episode(),b.episode());
            assertEquals("flight 'one' ticket",a.service());assertEquals(live.workspace.getUUID().toString(),a.workspaceUuid());
            assertEquals(live.workspace.getUUID().toString(),a.organizationWorkspaceUuid());
            assertTrue(live.source.semanticContract().artifactDeclarations().isEmpty());
            assertThrows(UnsupportedOperationException.class,()->result.bindings().clear());
        }
    }
    @Test void missingWitnessForeignRunGenerationRevisionOrFutureEventCannotGrantInstanceBinding()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            live.scheme("sch_a1","a1","service");live.auction("a1");var cut=live.cut();
            var resolver=new AuctionArtifactBindingResolver(live.source,live.selectors);
            assertEquals(1,resolver.resolve(live.run,cut,live.events).bindings().size());
            assertEquals(0,resolver.resolve(live.run,cut,List.of()).bindings().size());
            for(int change=0;change<5;change++) {
                final int mode=change;
                var altered=live.events.stream().map(e->event(e,mode)).toList();
                assertEquals(0,resolver.resolve(live.run,cut,altered).bindings().size(),"Foreign/failed/future evidence must not bind: "+mode);
            }
            var wrong=new AuctionArtifactBindingResolver.Run(live.run.id(),live.run.generation(),"different-revision");
            assertTrue(assertThrows(IllegalArgumentException.class,()->resolver.resolve(wrong,cut,live.events)).getMessage().contains("MIXED_REVISION"));
        }
    }
    @Test void recreatedArtifactCannotUseOldCreationWitnessOrReuseOldEpisode()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            live.scheme("sch_a1","a1","service");var first=live.auction("a1");
            var resolver=new AuctionArtifactBindingResolver(live.source,live.selectors);
            var old=resolver.resolve(live.run,live.cut(),live.events).bindings().getFirst();var oldEvents=List.copyOf(live.events);
            live.dispose(first);var replacement=live.auction("a1");var cut=live.cut();
            assertEquals(0,resolver.resolve(live.run,cut,oldEvents).bindings().size());
            var fresh=resolver.resolve(live.run,cut,live.events).bindings().getFirst();
            assertEquals(replacement.getId().toString(),fresh.artifactUuid());assertNotEquals(old.artifactIdentity(),fresh.artifactIdentity());
            assertNotEquals(old.episode(),fresh.episode());assertEquals(old.schemeUuid(),fresh.schemeUuid());
        }
    }
    @Test void sameArtifactForTwoIndependentSchemesIsQuarantinedInsteadOfSharingSelf()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            live.scheme("sch_a1","a1","one");live.scheme("sch_duplicate","a1","other");live.auction("a1");
            var result=new AuctionArtifactBindingResolver(live.source,live.selectors).resolve(live.run,live.cut(),live.events);
            assertTrue(result.bindings().isEmpty());assertEquals(2,result.diagnostics().size());
            assertTrue(result.diagnostics().stream().allMatch(d->d.code().equals("ARTIFACT_SHARED_BY_AMBIGUOUS_SCHEMES")));
        }
    }
    @Test void wrongProviderHashClassUuidUnknownArgumentMissingFocusAndWrongCallerRemainUnavailable()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            live.scheme("sch_a1","a1","service");live.auction("a1");var cut=live.cut();
            var resolver=new AuctionArtifactBindingResolver(live.source,live.selectors);
            for(String field:List.of("provider","resource","classHash","artifactUuid","javaClass")) {
                var changed=transform(cut,f->{
                    if(f.kind()!=RuntimeFactKind.ARTIFACT||!"auction_env.AuctionArtifact".equals(f.values().get("artifactTypeJavaClassName")))return f;
                    var values=new LinkedHashMap<>(f.values());var proof=new LinkedHashMap<>(CanonicalJson.object(values.get("artifactProviderObservation")));
                    proof.put(field,"wrong");values.put("artifactProviderObservation",proof);return fact(f,values);
                });
                assertTrue(resolver.resolve(live.run,changed,live.events).bindings().isEmpty(),field);
            }
            var noFocus=transform(cut,f->{if(!"SET_CARTAGO_FOCUS".equals(f.values().get("normalizedEventKind")))return f;
                var values=new LinkedHashMap<>(f.values());values.put("focused",false);return fact(f,values);});
            assertTrue(resolver.resolve(live.run,noFocus,live.events).bindings().isEmpty());
            var wrongCaller=live.events.stream().map(e->{var data=new LinkedHashMap<>(e.after());data.put("performerSemanticId","other-player");return withData(e,data);}).toList();
            assertTrue(resolver.resolve(live.run,cut,wrongCaller).bindings().isEmpty());
            var atom=transform(cut,f->{if(f.kind()!=RuntimeFactKind.SCHEME_BOARD)return f;
                var data=new LinkedHashMap<>(f.values());var root=new LinkedHashMap<>(CanonicalJson.object(data.get("schemeArgumentTerms")));
                var goal=new LinkedHashMap<>(CanonicalJson.object(root.get("auction")));var id=new LinkedHashMap<>(CanonicalJson.object(goal.get("Id")));
                id.put("key",Map.of("kind","LITERAL","functor","a1"));goal.put("Id",id);root.put("auction",goal);data.put("schemeArgumentTerms",root);return fact(f,data);});
            var result=resolver.resolve(live.run,atom,live.events);assertTrue(result.bindings().isEmpty());
            assertTrue(result.diagnostics().getFirst().code().contains("ARGUMENT_NOT_STRING_OR_CANONICAL_TERM"));
        }
    }
    @Test void officialJacaConversionAndMoiseReparsingKeepTypedAtomIdentityAndStructuredService()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            String service="flight_ticket(paris,athens,date(15,12,2015))";
            live.scheme("sch_a1","a1",service,true);live.auction("a1");var cut=live.cut();
            var scheme=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD).findFirst().orElseThrow();
            var terms=CanonicalJson.object(CanonicalJson.object(scheme.values().get("schemeArgumentTerms")).get("auction"));
            assertEquals("LITERAL",CanonicalJson.object(CanonicalJson.object(terms.get("Id")).get("key")).get("kind"));
            assertEquals("LITERAL",CanonicalJson.object(CanonicalJson.object(terms.get("Service")).get("key")).get("kind"));
            var result=new AuctionArtifactBindingResolver(live.source,live.selectors).resolve(live.run,cut,live.events);
            assertEquals(List.of(),result.diagnostics());assertEquals(1,result.bindings().size());
            assertEquals("a1",result.bindings().getFirst().auctionId());assertEquals(service,result.bindings().getFirst().service());
            assertEquals("JACA_3_1_ATOM_SYNTAX",result.bindings().getFirst().idConversion());
            assertEquals("JACA_3_1_GROUND_STRUCTURE_SYNTAX",result.bindings().getFirst().serviceConversion());
        }
    }
    @Test void missingTypedArgumentObjectIsIncompleteAndOriginalMoiseRejectsUnsupportedStringEscape()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            live.scheme("sch_a1","a1","service");live.auction("a1");
            var cut=transform(live.cut(),f->{if(f.kind()!=RuntimeFactKind.SCHEME_BOARD)return f;var data=new LinkedHashMap<>(f.values());data.remove("schemeArgumentTerms");return fact(f,data);});
            var result=new AuctionArtifactBindingResolver(live.source,live.selectors).resolve(live.run,cut,live.events);
            assertTrue(result.bindings().isEmpty());assertEquals(AuctionArtifactBindingResolver.Availability.INCOMPLETE,result.diagnostics().getFirst().availability());
            assertThrows(cartago.util.agent.ActionFailedException.class,()->live.scheme("unsupported_escape","uncreated","flight \\ ticket"));
        }
    }
    @Test void sameNameInTwoRealWorkspacesIsAmbiguousAndObservedLookupCanReplaceMissingCreationPrefix()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            live.scheme("sch_a1","a1","service");var first=live.auction("a1");
            var resolver=new AuctionArtifactBindingResolver(live.source,live.selectors);
            var output=new cartago.OpFeedbackParam<cartago.ArtifactId>();
            live.actor.doAction(new cartago.Op("lookupArtifact","a1",output),live.workspace,5000);assertEquals(first,output.get());
            var lookups=live.events.stream().filter(e->"lookupArtifact".equals(e.after().get("operation"))).toList();
            assertEquals(1,resolver.resolve(live.run,live.cut(),lookups).bindings().size());
            var second=live.auction("a1",live.otherWorkspace());assertNotEquals(first.getWorkspaceId(),second.getWorkspaceId());
            var result=resolver.resolve(live.run,live.cut(),live.events);assertTrue(result.bindings().isEmpty());
            assertEquals("ARTIFACT_BINDING_AMBIGUOUS",result.diagnostics().getFirst().code());
        }
    }
    @Test void missingCorrelationAndWrongOperationTargetAreNotCreationAuthorityAndConsumerNeedsNoFrameworkLoader()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            live.scheme("sch_a1","a1","service");live.auction("a1");var cut=live.cut();
            var previous=Thread.currentThread().getContextClassLoader();
            final AuctionArtifactBindingResolver resolver;
            try {
                Thread.currentThread().setContextClassLoader(new ClassLoader(null){});
                resolver=new AuctionArtifactBindingResolver(live.source,live.selectors);
                assertEquals(1,resolver.resolve(live.run,cut,live.events).bindings().size());
            } finally {Thread.currentThread().setContextClassLoader(previous);}
            var uncorrelated=live.events.stream().map(e->new RuntimeEvent(e.eventId(),e.sessionId(),e.generation(),e.modelRevision(),e.subsystem(),e.sourceId(),e.sourceSequence(),e.observedAt(),e.kind(),e.factKind(),e.projectionStatus(),e.entityId(),e.relationId(),"",e.causationId(),e.before(),e.after(),e.watermark(),e.completeness(),e.evidence())).toList();
            assertTrue(resolver.resolve(live.run,cut,uncorrelated).bindings().isEmpty());
            var wrongTarget=live.events.stream().map(e->{var data=new LinkedHashMap<>(e.after());data.put("artifactSemanticId","unrelated-operation-target");return withData(e,data);}).toList();
            assertTrue(resolver.resolve(live.run,cut,wrongTarget).bindings().isEmpty());
        }
    }
    private static RuntimeEvent event(RuntimeEvent e,int mode) {
        long sequence=mode==4?Long.MAX_VALUE:e.sourceSequence();
        return new RuntimeEvent(e.eventId(),mode==0?"other-run":e.sessionId(),e.generation()+(mode==1?1:0),mode==2?"other-revision":e.modelRevision(),
                e.subsystem(),e.sourceId(),sequence,e.observedAt(),mode==3?RuntimeEventKind.FAILED:e.kind(),e.factKind(),e.projectionStatus(),e.entityId(),e.relationId(),
                e.correlationId(),e.causationId(),e.before(),e.after(),new SourceWatermark(e.sourceId(),sequence),e.completeness(),e.evidence());
    }
    private static RuntimeEvent withData(RuntimeEvent e,Map<String,Object> after){return new RuntimeEvent(e.eventId(),e.sessionId(),e.generation(),e.modelRevision(),e.subsystem(),e.sourceId(),e.sourceSequence(),e.observedAt(),e.kind(),e.factKind(),e.projectionStatus(),e.entityId(),e.relationId(),e.correlationId(),e.causationId(),e.before(),after,e.watermark(),e.completeness(),e.evidence());}
    private static RuntimeFact fact(RuntimeFact f,Map<String,Object> values){return new RuntimeFact(f.id(),f.kind(),values,f.relations(),f.projectionStatus(),f.completeness(),f.evidence());}
    private static RuntimeSnapshot transform(RuntimeSnapshot cut,java.util.function.UnaryOperator<RuntimeFact> change){return new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),cut.facts().stream().map(change).toList(),cut.sourceCompleteness(),cut.stateFingerprint());}
}

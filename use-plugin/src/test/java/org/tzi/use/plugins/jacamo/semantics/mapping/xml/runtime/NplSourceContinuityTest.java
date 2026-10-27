package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;

/** Actual pinned NPL callbacks; only the rollback-owned metadata port is a unit fixture. */
class NplSourceContinuityTest {
    @Test @Timeout(30) void mappingUnavailabilityDoesNotInventASourceGapAndActualGapsRemainUnknown()throws Exception {
        var environment=CartagoEnvironment.getInstance();environment.init();
        var actor=new CartagoBasicContext("continuity"+UUID.randomUUID().toString().replace("-",""));
        var events=new CopyOnWriteArrayList<RuntimeEvent>();ArtifactId board=null;
        try(var norms=new NplBoardSnapshotSource("continuity-run",1,"continuity-source",()->NeutralNormBoard.instance==null?List.of():List.of(NeutralNormBoard.instance));
            var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"continuity-run",null,norms::artifactCreated);
            var coordinator=new SnapshotCoordinator(List.of(cartago,norms),1024,events::add)) {
            board=actor.makeArtifact(actor.getJoinedWspId("main"),"duties",NeutralNormBoard.class.getName());
            actor.doAction(board,new Op("load","scope deliveries { norm duty: start -> obligation(alice,true,missing,`now`+`1 seconds`). }"),5000);
            actor.doAction(board,new Op("addFact","start"),5000);
            await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED));
            var active=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).findFirst().orElseThrow();
            var initial=row(active,Completeness.COMPLETE);
            String hash=(String)NplRuntimeProfile.evidence().get("artifactSha256");
            var old=new NplObserverHistory("raw","norms",hash);var oldMetadata=new HashMap<String,String>();var oldPort=port(oldMetadata);
            assertEquals(SemanticVerdict.PASS,old.observe(oldPort,null,initial,"continuity-run",1));
            assertEquals(SemanticVerdict.INCONCLUSIVE,old.observe(oldPort,null,null,"continuity-run",1));
            assertEquals(SemanticVerdict.INCONCLUSIVE,old.observe(oldPort,null,initial,"continuity-run",1),"Retain the legacy consumer-prefix policy for historical profiles");
            assertEquals("true",oldMetadata.get("xml:observer:lifecycle:gap"));

            var source=new NplObserverHistory("raw","norms",hash,"NPL_REPORTED_SOURCE_CONTINUOUS_V1");
            var metadata=new HashMap<String,String>();var nativePort=port(metadata);
            assertEquals(SemanticVerdict.PASS,source.observe(nativePort,null,initial,"continuity-run",1));
            assertEquals(SemanticVerdict.INCONCLUSIVE,source.observe(nativePort,null,row(active,Completeness.PARTIAL),"continuity-run",1));
            assertEquals(SemanticVerdict.INCONCLUSIVE,source.observe(nativePort,null,null,"continuity-run",1));
            assertEquals(SemanticVerdict.PASS,source.observe(nativePort,null,initial,"continuity-run",1),"A qualified source creation subscription survives temporary mapping unavailability");
            assertNotEquals("true",metadata.get("xml:observer:lifecycle:gap"));
            await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED));
            var terminal=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED).findFirst().orElseThrow();
            assertEquals(SemanticVerdict.PASS,source.observe(nativePort,null,row(terminal,Completeness.COMPLETE),"continuity-run",1));
            assertEquals("UNFULFILLED",metadata.get("xml:observer:lifecycle:state"));
            var restored=new HashMap<String,String>();
            assertEquals(SemanticVerdict.PASS,source.observe(port(restored),null,row(terminal,Completeness.COMPLETE),"continuity-run",1),"A terminal snapshot needs complete source creation/terminal annotations, not a fabricated consumer ACTIVE event");
            assertEquals("SOURCE_REPORTED_TERMINAL_PREFIX_RESTORED",restored.get("xml:observer:lifecycle:diagnostic"));

            // Negative proof mutation: attachment after creation cannot claim the
            // original empty-baseline subscription. No synthetic success follows.
            var lateRaw=new LinkedHashMap<String,Object>(active.after());
            var proof=new LinkedHashMap<String,Object>(CanonicalJson.object(lateRaw.get("boardObservationProof")));
            proof.put("emptyBaselineAfterListener",false);proof.put("scopeQuality","LATE_ATTACHMENT");lateRaw.put("boardObservationProof",proof);
            var late=new RuntimeTables.Row(active.entityId().canonical(),Map.of("raw",lateRaw),Completeness.COMPLETE,Map.of("capability:norms","negative-proof"),Set.of(active.entityId().canonical()));
            var gapMetadata=new HashMap<String,String>();var gapPort=port(gapMetadata);
            assertEquals(SemanticVerdict.INCONCLUSIVE,source.observe(gapPort,null,late,"continuity-run",1));
            assertEquals(SemanticVerdict.INCONCLUSIVE,source.observe(gapPort,null,initial,"continuity-run",1),"Actual source continuity gaps stay sticky within the same observer epoch");
        } finally {
            if(board!=null){NeutralNormBoard.instance.getNormativeEngine().stop();environment.getController("/main").removeArtifact(board.getName());}
            NeutralNormBoard.instance=null;CslRuntimeTestRegistry.closeCartagoContext(actor);environment.shutdown();
        }
    }
    private static RuntimeTables.Row row(RuntimeEvent event,Completeness quality){String id=event.entityId().canonical();return new RuntimeTables.Row(id,Map.of("raw",event.after()),quality,Map.of("capability:norms","actual-source-cut"),Set.of(id));}
    private static Port port(Map<String,String> metadata){return (Port)Proxy.newProxyInstance(Port.class.getClassLoader(),new Class<?>[]{Port.class},(proxy,method,args)->{
        if(method.getName().equals("metadata")){if(args.length==2)return metadata.getOrDefault((String)args[1],"");metadata.put((String)args[1],(String)args[2]);return null;}
        throw new AssertionError("History must use the native metadata port only: "+method);
    });}
    private static void await(java.util.function.BooleanSupplier condition)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);assertTrue(condition.getAsBoolean(),"Actual NPL callback timed out");}
}

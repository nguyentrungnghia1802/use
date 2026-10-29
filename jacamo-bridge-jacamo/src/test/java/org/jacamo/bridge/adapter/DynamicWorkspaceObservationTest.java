package org.jacamo.bridge.adapter;

import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

class DynamicWorkspaceObservationTest {
    /** No initial property callback: a read used to mark this birth as published. */
    public static class EmptyArtifact extends Artifact {public void init(){}}
    @Test void onlySuccessfulTypedWorkspaceFeedbackNotifiesTheRunOwnedObserver()throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var action=new jason.asSemantics.ActionExec(jason.asSyntax.ASSyntax.parseStructure("createWorkspace(\"new\")"),new jason.asSemantics.Intention());
        try(var lease=BridgeRuntimeRegistry.attachWorkspaceObserver(calls::incrementAndGet)) {
            BridgeRuntimeRegistry.observeWorkspaceFeedback(action);assertEquals(0,calls.get());
            action.setResult(true);BridgeRuntimeRegistry.observeWorkspaceFeedback(action);assertEquals(1,calls.get());
            var unrelated=new jason.asSemantics.ActionExec(jason.asSyntax.ASSyntax.parseStructure("printMsg(\"createWorkspace\")"),new jason.asSemantics.Intention());unrelated.setResult(true);
            BridgeRuntimeRegistry.observeWorkspaceFeedback(unrelated);assertEquals(1,calls.get());
        }
        BridgeRuntimeRegistry.observeWorkspaceFeedback(action);assertEquals(1,calls.get(),"Run disposal revokes the hook");
    }
    @Test @Timeout(25) void dispatchHookObservesAWorkspaceCreatedThroughTheFrameworkApiWithoutLoggerCallbacks()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var events=new CopyOnWriteArrayList<RuntimeEvent>();
        try(var source=new CartagoSnapshotSource(env,List.of(),"direct-workspace");var lease=BridgeRuntimeRegistry.attachWorkspaceObserver(source::observeWorkspaceTopology)) {
            source.attach(events::add);env.getRootWSP().getWorkspace().createWorkspace("direct");
            var action=new jason.asSemantics.ActionExec(jason.asSyntax.ASSyntax.parseStructure("joinWorkspace(\"/main/direct\",W)"),new jason.asSemantics.Intention());
            BridgeRuntimeRegistry.observeWorkspaceDispatch(action);
            var actor=new CartagoBasicContext("dispatchObserver");var joined=new OpFeedbackParam<WorkspaceId>();actor.doAction(new Op("joinWorkspace","/main/direct",joined),actor.getJoinedWspId("main"),5000);
            var created=actor.makeArtifact(joined.get(),"domain",FieldProbeArtifact.class.getName());
            assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.CREATED&&created.getId().toString().equals(e.after().get("uuid"))));
        }finally{env.shutdown();}
    }
    @Test @Timeout(25) void controllerReadDoesNotAnnounceAnArtifactBeforeItsCreationCallback()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var events=new CopyOnWriteArrayList<RuntimeEvent>();
        var reference=new java.util.concurrent.atomic.AtomicReference<CartagoSnapshotSource>();
        try(var source=new CartagoSnapshotSource(env,List.of(),"initialization-race",null,id->{
            if(!id.getName().equals("domain"))return;
            // Deterministic interleaving of a controller query with an init-time
            // publication, while the actual creation callback is still active.
            reference.get().capture();reference.get().newPercept(0,id,null,new ArtifactObsProperty[0],null,null);
        })) {
            reference.set(source);source.attach(events::add);var actor=new CartagoBasicContext("initializationObserver");
            var created=actor.makeArtifact(actor.getJoinedWspId("main"),"domain",EmptyArtifact.class.getName());
            var actual=events.stream().filter(e->e.entityId()!=null&&e.entityId().incarnation().equals(created.getId().toString())).toList();
            assertFalse(actual.isEmpty());assertEquals(RuntimeEventKind.CREATED,actual.getFirst().kind(),"A read is not a published birth or admitted snapshot");
        } finally {env.shutdown();}
    }
    @Test @Timeout(25) void realOrgBoardCallbacksCannotPrecedeTheirCreationBoundary()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var events=new CopyOnWriteArrayList<RuntimeEvent>();
        try(var source=new CartagoSnapshotSource(env,List.of(),"org-initialization")) {
            source.attach(events::add);var actor=new CartagoBasicContext("orgObserver");var main=actor.getJoinedWspId("main");
            var os=java.nio.file.Path.of("../../jacamo/doc/tutorials/hello-world/code/helloworld/src/org/o1.xml").toAbsolutePath().normalize();
            var created=actor.makeArtifact(main,"org", "ora4mas.nopl.OrgBoard",new Object[]{os.toString()});
            var actual=events.stream().filter(e->e.entityId()!=null&&e.entityId().incarnation().equals(created.getId().toString())).toList();
            assertFalse(actual.isEmpty());assertEquals(RuntimeEventKind.CREATED,actual.getFirst().kind(),"Initial internal callbacks must not refer to an unknown artifact incarnation");
        } finally {env.shutdown();}
    }
    @Test @Timeout(25) void newWorkspaceIsObservedBeforeTheNextAgentActionWithoutAPoll()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();
        var events=new CopyOnWriteArrayList<RuntimeEvent>();
        try(var source=new CartagoSnapshotSource(env,List.of(),"dynamic-workspace")) {
            source.attach(events::add);var actor=new CartagoBasicContext("workspaceObserver");var main=actor.getJoinedWspId("main");
            actor.doAction(new Op("createWorkspace","later"),main,5000);
            var joined=new OpFeedbackParam<WorkspaceId>();actor.doAction(new Op("joinWorkspace","/main/later",joined),main,5000);var child=joined.get();
            var created=actor.makeArtifact(child,"domain",FieldProbeArtifact.class.getName());
            actor.doAction(created,new Op("bump"),5000);
            assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.CREATED&&created.getId().toString().equals(e.after().get("uuid"))),"A source poll must not be needed to observe creation in a new workspace");
            assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"bump".equals(e.after().get("operation"))),"Keep actual operation history from creation, without backfill");
            long before=source.watermark().sequence();source.close();actor.doAction(created,new Op("bump"),5000);
            assertEquals(before,source.watermark().sequence(),"Dispose removes dynamically owned loggers too");
        } finally {env.shutdown();}
    }
}

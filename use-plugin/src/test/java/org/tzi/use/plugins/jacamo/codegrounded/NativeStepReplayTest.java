package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;

class NativeStepReplayTest {
    @TempDir Path root;

    @Test void resetNextNextPreviousNextReconstructsExactStateAndNeverMutatesOriginal() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            original.applySnapshot(snapshot("initial",0));
            original.coordinator().loadProfileSource("before.ocl",RuntimeVerificationCoordinatorTest.PROFILE); original.apply(delta("B",1,"B"));
            original.apply(delta("A",2,"A"));
            Path recording=root.resolve("navigate");var replay=new NativeRuntimeReplay();
            replay.exportBundle(original,pipeline.export().useText(),recording);
            var originalResult=original.coordinator().latest();
            var originalCommands=new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(original.system()).commands();
            var expected=new java.util.ArrayList<RuntimeVerificationResult>();
            var commands=new java.util.ArrayList<String>();
            try(var bundle=replay.openBundle(recording);var forward=bundle.atStep(0)) {
                for(var step:bundle.steps()) {
                    forward.forward(step.endOrdinal()); expected.add(forward.projector().coordinator().latest());
                    commands.add(new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(forward.projector().system()).commands());
                }
            }
            var session=new org.tzi.use.main.Session();session.setSystem(original.system());
            try(var controller=new NativeReplayStepController(session)) {
                controller.open(recording); assertSame(session.system(),controller.system());
                assertNotSame(original.system().model(),session.system().model());
                for(int iteration=0;iteration<5;iteration++) {
                    controller.reset();check(controller,commands,expected);
                    controller.next();check(controller,commands,expected);
                    var system=controller.system();controller.next();check(controller,commands,expected);
                    assertSame(system,controller.system(),"Next must keep its retained native context");
                    controller.previous();check(controller,commands,expected);
                    assertNotSame(system,controller.system(),"Previous must reconstruct from baseline");
                    controller.next();check(controller,commands,expected);
                    controller.next();check(controller,commands,expected);
                    var last=controller.verificationSnapshot();controller.next();assertEquals(last,controller.verificationSnapshot());
                }
                controller.reset();controller.previous();assertEquals(0,controller.status().step());
                var system=controller.system();var before=controller.verificationSnapshot();
                assertThrows(IllegalStateException.class,session::reset);
                assertThrows(IllegalStateException.class,system::undoLastStatement);
                assertThrows(IllegalStateException.class,system::redoStatement);
                assertThrows(IllegalStateException.class,()->org.tzi.use.api.UseSystemApi.create(system,false).createObject("Agent","manual"));
                assertThrows(IllegalStateException.class,()->system.setClassInvariantFlags(original.system().model().classInvariants().iterator().next(),false,null));
                assertThrows(IllegalStateException.class,()->system.execute(new org.tzi.use.uml.sys.soil.MNewObjectStatement(system.model().getClass("Agent"),"manual")));
                assertEquals(before,controller.verificationSnapshot());
                assertEquals(originalCommands,new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(original.system()).commands());
                assertEquals(originalResult,original.coordinator().latest());
            }
            assertFalse(session.hasSystem()); assertTrue(replay.replay(recording).complete());
        }
    }
    private static void check(NativeReplayStepController controller,java.util.List<String> commands,java.util.List<RuntimeVerificationResult> expected) {
        int step=controller.status().step();var actual=controller.verificationSnapshot().result();
        assertEquals(expected.get(step).semanticEvidence(),actual.semanticEvidence());
        assertEquals(expected.get(step).resultHash(),actual.resultHash());
        assertEquals(commands.get(step),new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(controller.system()).commands());
        assertTrue(controller.system().state().allLinks().stream().allMatch(link->controller.system().state().allObjects().containsAll(link.linkedObjects())));
        assertEquals(controller.system().state().numObjects(),controller.system().state().allObjects().stream()
                .map(object->object.state(controller.system().state()).attributeValue("semanticId")).distinct().count());
    }

    @Test void invalidReopenOrFailedReconstructionPreservesTheSelectedValidSystem() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            original.applySnapshot(snapshot("initial",0));Path recording=root.resolve("valid");
            new NativeRuntimeReplay().exportBundle(original,pipeline.export().useText(),recording);
            var session=new org.tzi.use.main.Session();session.setSystem(original.system());
            try(var controller=new NativeReplayStepController(session)) {
                controller.open(recording);controller.next();var selected=session.system();var before=controller.verificationSnapshot();
                Path missing=root.resolve("missing");Files.createDirectories(missing);
                assertThrows(IllegalArgumentException.class,()->controller.open(missing));
                assertSame(selected,session.system());assertEquals(before,controller.verificationSnapshot());assertFalse(controller.busy());
                // A negative IO control targets only the replay-owned private copy, not the recording.
                var selectionField=controller.getClass().getDeclaredField("selected");selectionField.setAccessible(true);
                Object selection=((java.util.concurrent.atomic.AtomicReference<?>)selectionField.get(controller)).get();
                var bundleMethod=selection.getClass().getDeclaredMethod("bundle");bundleMethod.setAccessible(true);
                Object bundle=bundleMethod.invoke(selection);var rootField=bundle.getClass().getDeclaredField("root");rootField.setAccessible(true);
                Path baseline=((Path)rootField.get(bundle)).resolve("baseline.cmd");String commands=Files.readString(baseline);
                Files.writeString(baseline,"!not_valid_soil\n");
                assertThrows(RuntimeException.class,controller::previous);
                assertSame(selected,session.system());assertEquals(before,controller.verificationSnapshot());assertFalse(controller.busy());
                Files.writeString(baseline,commands);controller.previous();assertEquals(0,controller.status().step());
            }
        }
    }

    @Test void edtActivationFailureKeepsOriginalSessionAndReportsTheRealCause() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            Path recording=root.resolve("activation");new NativeRuntimeReplay().exportBundle(original,pipeline.export().useText(),recording);
            var session=new org.tzi.use.main.Session();session.setSystem(original.system());
            org.tzi.use.main.ChangeListener listener=event->{
                if(session.system().isReadOnly())throw new IllegalStateException("GUI_ACTIVATION_REJECTED");
            };
            session.addChangeListener(listener);
            try(var controller=new NativeReplayStepController(session)) {
                var error=assertThrows(IllegalStateException.class,()->controller.open(recording));
                assertEquals("GUI_ACTIVATION_REJECTED",error.getMessage());
                assertSame(original.system(),session.system());assertFalse(controller.active());assertFalse(controller.busy());
                session.removeChangeListener(listener);
                controller.open(recording);assertNotSame(original.system(),session.system());
            } finally {session.removeChangeListener(listener);}
        }
    }

    @Test void overlappingNavigationAndCloseDuringOpenCannotActivateAStaleWorker() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            Path recording=root.resolve("concurrent");new NativeRuntimeReplay().exportBundle(original,pipeline.export().useText(),recording);
            var session=new org.tzi.use.main.Session();session.setSystem(original.system());
            var controller=new NativeReplayStepController(session);
            var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
            javax.swing.SwingUtilities.invokeLater(()->{held.countDown();try{release.await(10,java.util.concurrent.TimeUnit.SECONDS);}
                catch(InterruptedException error){Thread.currentThread().interrupt();}});
            assertTrue(held.await(5,java.util.concurrent.TimeUnit.SECONDS));
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var opening=pool.submit(()->controller.open(recording));
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while(!controller.busy()&&System.nanoTime()<deadline)Thread.sleep(10);
                assertTrue(controller.busy());assertThrows(IllegalStateException.class,controller::next);
                var closing=pool.submit(controller::close);
                // close revokes the operation token before its EDT cleanup can run.
                var closedField=controller.getClass().getDeclaredField("closed");closedField.setAccessible(true);
                while(!((java.util.concurrent.atomic.AtomicBoolean)closedField.get(controller)).get()&&System.nanoTime()<deadline)Thread.sleep(10);
                release.countDown();closing.get(20,java.util.concurrent.TimeUnit.SECONDS);
                assertThrows(java.util.concurrent.ExecutionException.class,()->opening.get(20,java.util.concurrent.TimeUnit.SECONDS));
                assertSame(original.system(),session.system());assertFalse(controller.active());assertFalse(controller.busy());
            } finally {release.countDown();controller.close();}
        }
    }

    @Test void retainedCursorSharesBatchValidationAndPreservesNonStepProfileAndNoopVersions() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try (var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            original.applySnapshot(snapshot("initial",0));
            original.coordinator().loadProfileSource("before.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            original.apply(delta("B",1,"B"));
            original.apply(delta("noop",2,"B"));
            assertFalse(original.apply(delta("noop",2,"B")));
            original.coordinator().manualVerify();
            original.apply(delta("A",3,"A"));
            original.applySnapshot(snapshot("same-state-resync",3),SESSION,2);
            original.coordinator().manualVerify();
            var replay=new NativeRuntimeReplay(); Path recording=root.resolve("recording");
            replay.exportBundle(original,pipeline.export().useText(),recording);
            var before=original.coordinator().latest();
            try (var bundle=replay.openBundle(recording)) {
                assertTrue(bundle.report().complete(),bundle.report().toString());
                assertEquals(4,bundle.steps().size()); // baseline, atomic snapshot, B, A
                assertEquals(0,bundle.steps().getFirst().endOrdinal());
                assertEquals(2,bundle.steps().get(1).endOrdinal()); // PROFILE follows the schema-discovering snapshot
                assertTrue(bundle.steps().get(2).recordedStateVersion()>2); // Step is NOT stateVersion
                for (var step:bundle.steps()) try (var cursor=bundle.atStep(step.stepIndex())) {
                    assertEquals(step.endOrdinal(),cursor.endOrdinal());
                    assertEquals(step.stateHash(),cursor.projector().coordinator().latest().stateHash());
                    assertEquals(step.recordedStateVersion(),cursor.projector().coordinator().lastObservation().stateVersion());
                    assertNotSame(original.system(),cursor.projector().system());
                    assertNotSame(original.system().model(),cursor.projector().system().model());
                }
                try (var cursor=bundle.atStep(0)) {
                    cursor.forward(bundle.steps().getLast().endOrdinal());
                    assertEquals(before.stateHash(),cursor.projector().coordinator().latest().stateHash());
                    assertEquals(before.resultHash(),cursor.projector().coordinator().latest().resultHash());
                }
            }
            assertEquals(before,original.coordinator().latest());
            assertTrue(replay.replay(recording).complete());
        }
    }

    @Test void baselineOnlyAndStaleMissingOrCorruptRecordingsFailClosed() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try (var original=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            var replay=new NativeRuntimeReplay(); Path recording=root.resolve("baseline");
            replay.exportBundle(original,pipeline.export().useText(),recording);
            try (var bundle=replay.openBundle(recording); var cursor=bundle.atStep(0)) {
                assertEquals(1,bundle.steps().size()); assertEquals(0,cursor.endOrdinal());
            }
            original.coordinator().coverageGap("DISCONNECTED_OBSERVATION");
            Path stale=root.resolve("stale"); replay.exportBundle(original,pipeline.export().useText(),stale);
            try (var bundle=replay.openBundle(stale)) {
                assertFalse(bundle.report().complete());
                assertThrows(IllegalArgumentException.class,()->bundle.atStep(0));
            }
            Files.delete(recording.resolve("baseline.cmd"));
            assertThrows(IllegalArgumentException.class,()->replay.openBundle(recording));
            Files.writeString(stale.resolve("runtime.jsonl"),"{}\n",java.nio.file.StandardOpenOption.APPEND);
            assertThrows(IllegalArgumentException.class,()->replay.openBundle(stale));
        }
    }
}

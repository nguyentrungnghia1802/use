package org.tzi.use.plugins.jacamo.ui;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerificationEnvelope;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter;
import static org.junit.jupiter.api.Assertions.*;

class ObservedConformanceWorkflowTest {
    @Test void auctionGuiChoiceIsCapturedByThePreparationWorker() throws Exception {
        var called=new java.util.concurrent.CountDownLatch(1);var selected=new java.util.concurrent.atomic.AtomicReference<Boolean>();
        var source=new java.util.concurrent.atomic.AtomicReference<java.nio.file.Path>();
        var facade=new JaCaMoFacade(){public String status(){return "Auction GUI";}
            public ImportSelection importSelection(){return new ImportSelection("NATIVE_CURRENT",null);}
            public boolean ownedProducerSupported(){return true;}
            public ProjectSummary prepareOwnedProducer(java.nio.file.Path jcm,ImportSelection inputs,java.nio.file.Path cp,boolean showGui){
                assertFalse(SwingUtilities.isEventDispatchThread());assertFalse(inputs.strictSourceChecksums());source.set(jcm);selected.set(showGui);called.countDown();return null;}};
        var external=java.nio.file.Path.of("..").toAbsolutePath().normalize().getParent().resolve("jacamo/examples/auction/auction.jcm");
        var inputs=java.nio.file.Path.of("src/test/resources/auction-reference").toAbsolutePath();
        SwingUtilities.invokeAndWait(()->{
            var panel=new JaCaMoWorkbenchPanel(facade,message->fail(message),true);
            assertTrue(find(panel,"show-producer-gui",JCheckBox.class).isSelected());
            assertFalse(find(panel,"strict-source-checksums",JCheckBox.class).isSelected());
            for(var field:Map.of("selected-jcm",external,"authored-csl",inputs.resolve("authored-v1/auction_csl.csl"),"authored-istar",inputs.resolve("authored-v1/auction_istar_final.istar"),"mapping-specification",inputs.resolve("source-confirmed-v1/auction-mapping.xml"),"authored-ocl",inputs.resolve("source-confirmed-v1/auction_confirmed.ocl")).entrySet())
                find(panel,field.getKey(),JTextField.class).setText(field.getValue().toString());
            find(panel,"prepare-producer",JButton.class).doClick();
        });
        assertTrue(called.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(Boolean.TRUE,selected.get());assertEquals(external,source.get());
    }
    @Test void asyncCompletionReplacesItsPendingRowButDoesNotDropAnExecutionOnlyBoundary() {
        var now=Instant.EPOCH;
        var pending=new RuntimeVerificationResult("run",1,"revision",1,"entry","cartago",1,now,now,now,"c1","contracts","same-state",List.of(),0,"EVALUATION_PENDING","PENDING_EVALUATION","",null);
        var completed=new RuntimeVerificationResult("run",1,"revision",1,"entry","cartago",1,now,now,now,"c1","contracts","same-state",List.of(),0,"COMPLETE","CURRENT_OBSERVED","",null);
        var operation=new RuntimeVerificationResult("run",1,"revision",2,"no-change-operation","cartago",2,now,now,now,"c2","contracts","same-state",List.of(),0,"COMPLETE","CURRENT_OBSERVED","",null);
        var metadata=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.Metadata("snapshot",2,now,1,
                org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType.OPERATION_POST,java.util.Set.of("invocation"),
                org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.SynchronizationState.LIVE,Map.of(),Map.of(),
                org.jacamo.bridge.contract.Completeness.COMPLETE,Map.of(),Map.of());
        var snapshot=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot(2,operation,null,metadata,null);
        var facade=new JaCaMoFacade(){public String status(){return "execution";}
            public List<RuntimeVerificationResult> runtimeVerificationHistory(){return List.of(pending,completed,operation);}
            public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot verificationSnapshot(){return snapshot;}};
        var cuts=new ObservedConformancePresenter(facade).capture();
        assertEquals(2,cuts.size());assertEquals("CURRENT_OBSERVED",cuts.getFirst().freshness());
        assertNotEquals(cuts.getFirst().id(),cuts.getLast().id());
        assertTrue(cuts.getLast().trace().startsWith("Execution-only boundary / OPERATION_POST"));
        assertFalse(cuts.getFirst().failed());assertFalse(cuts.getLast().failed());
    }
    @Test void reusedPrepareActionCapturesAllFiveInputsOnceAndLeavesTheEdtResponsive() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var selected=new java.util.concurrent.atomic.AtomicReference<JaCaMoFacade.ImportSelection>();
        var calls=new java.util.concurrent.atomic.AtomicInteger();var errors=new java.util.concurrent.CopyOnWriteArrayList<String>();
        var facade=new JaCaMoFacade(){public String status(){return "prepare";}
            public ImportSelection importSelection(){return new ImportSelection("NATIVE_CURRENT",null);}
            public boolean ownedProducerSupported(){return true;}
            public ProjectSummary prepareOwnedProducer(java.nio.file.Path jcm,ImportSelection input,java.nio.file.Path classpath){
                assertFalse(SwingUtilities.isEventDispatchThread());assertTrue(jcm.endsWith("case.jcm"));
                calls.incrementAndGet();selected.set(input);entered.countDown();
                try{assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}return null;}};
        var panel=new java.util.concurrent.atomic.AtomicReference<JaCaMoWorkbenchPanel>();
        SwingUtilities.invokeAndWait(()->{
            var view=new JaCaMoWorkbenchPanel(facade,errors::add,true);panel.set(view);
            for(var field:Map.of("selected-jcm","case.jcm","authored-csl","case.csl","authored-istar","case.istar","mapping-specification","case.xml","authored-ocl","case.ocl").entrySet())
                find(view,field.getKey(),JTextField.class).setText(java.nio.file.Path.of(field.getValue()).toAbsolutePath().toString());
            find(view,"prepare-producer",JButton.class).doClick();
        });
        assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
        try {SwingUtilities.invokeAndWait(()->assertFalse(find(panel.get(),"prepare-producer",JButton.class).isEnabled()));}
        finally{release.countDown();}
        assertEquals(1,calls.get());assertTrue(errors.isEmpty(),errors.toString());
        var input=selected.get();assertEquals("JACAMO_DECLARATIVE_XML",input.profile());
        assertTrue(input.specification().endsWith("case.xml"));assertTrue(input.ocl().endsWith("case.ocl"));
        assertTrue(input.csl().endsWith("case.csl"));assertTrue(input.istar().endsWith("case.istar"));
    }
    @Test void mainWorkflowSelectsFiveAuthoredInputsAndKeepsCompatibilityControlsAvailableWithoutBpmn() throws Exception {
        var facade=new JaCaMoFacade(){public String status(){return "main workflow";}
            public ImportSelection importSelection(){return new ImportSelection("NATIVE_CURRENT",null);}
            public boolean ownedProducerSupported(){return true;}};
        SwingUtilities.invokeAndWait(()->{
            var panel=new JaCaMoWorkbenchPanel(facade,message->fail(message),true);
            assertEquals("JACAMO_DECLARATIVE_XML",find(panel,"engine-profile",JComboBox.class).getSelectedItem());
            assertEquals("AUTHORED",find(panel,"model-input-mode",JComboBox.class).getSelectedItem());
            assertNotNull(find(panel,"selected-jcm",JTextField.class));
            assertNotNull(find(panel,"use4goal-conformance-inputs",JPanel.class));
            for(String name:List.of("authored-csl","authored-istar","mapping-specification","authored-ocl"))assertNotNull(find(panel,name,JTextField.class));
            assertNotNull(find(panel,"prepare-producer",JButton.class));
            assertNotNull(find(panel,"runtime-classpath",JTextField.class));
            assertFalse(find(panel,"advanced-controls",JPanel.class).isVisible());
            find(panel,"show-advanced",JToggleButton.class).doClick();
            assertTrue(find(panel,"advanced-controls",JPanel.class).isVisible());
        });
    }
    @Test void folderSelectionIsAtomicRevalidatedAndCapturesAbsentOptionalOcl()throws Exception {
        var root=java.nio.file.Files.createTempDirectory("model-folder-");
        try {
            for(String extension:List.of("csl","istar","xml"))java.nio.file.Files.writeString(root.resolve("neutral."+extension),"");
            var entered=new java.util.concurrent.CountDownLatch(1);
            var selection=new java.util.concurrent.atomic.AtomicReference<JaCaMoFacade.ImportSelection>();
            var errors=new java.util.concurrent.CopyOnWriteArrayList<String>();
            var facade=new JaCaMoFacade(){public String status(){return "folder";}
                public ImportSelection importSelection(){return new ImportSelection("NATIVE_CURRENT",null);}
                public ProjectSummary prepareOwnedProducer(java.nio.file.Path project,ImportSelection input,java.nio.file.Path cp){
                    assertFalse(SwingUtilities.isEventDispatchThread());selection.set(input);entered.countDown();return null;}};
            var panel=new java.util.concurrent.atomic.AtomicReference<JaCaMoWorkbenchPanel>();
            SwingUtilities.invokeAndWait(()->{
                var view=new JaCaMoWorkbenchPanel(facade,errors::add,true);panel.set(view);view.selectCaseFolder(root);
                find(view,"selected-jcm",JTextField.class).setText("separate/project.jcm");
                assertFalse(find(view,"authored-csl",JTextField.class).isEditable());
                assertEquals("",find(view,"authored-ocl",JTextField.class).getText());
                var rows=find(view,"use4goal-conformance-inputs",JPanel.class).getComponent(0);
                assertSame(((Container)rows).getComponent(0),find(view,"selected-jcm",JTextField.class).getParent());
                assertSame(((Container)rows).getComponent(1),find(view,"case-folder",JTextField.class).getParent());
                assertNotNull(find(view,"choose-case-folder",JButton.class));
            });
            var duplicate=java.nio.file.Files.writeString(root.resolve("duplicate.xml"),"");
            SwingUtilities.invokeAndWait(()->find(panel.get(),"prepare-producer",JButton.class).doClick());
            assertEquals(1,errors.size());assertTrue(errors.getFirst().contains("DUPLICATE_TYPE"));assertNull(selection.get());
            java.nio.file.Files.delete(duplicate);
            SwingUtilities.invokeAndWait(()->find(panel.get(),"prepare-producer",JButton.class).doClick());
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            assertNull(selection.get().ocl());assertEquals(root.resolve("neutral.xml"),selection.get().specification());
            assertEquals(root.resolve("neutral.csl"),selection.get().csl());
        }finally{try(var files=java.nio.file.Files.list(root)){for(var file:files.toList())java.nio.file.Files.delete(file);}java.nio.file.Files.delete(root);}
    }
    @Test void runtimeClasspathLoadsAutomaticallyAndStillAllowsExplicitOverride()throws Exception {
        String property="use.jacamo.runtime.classpath-file",previous=System.getProperty(property);
        try {
            System.clearProperty(property);
            SwingUtilities.invokeAndWait(()->{
                var view=new JaCaMoWorkbenchPanel(new JaCaMoFacade(){public String status(){return "classpath";}},message->fail(message),true);
                assertEquals(java.nio.file.Path.of("D:/_CODE_BANK/Project_/08_Thesis/use/jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt"),java.nio.file.Path.of(find(view,"runtime-classpath",JTextField.class).getText()));
                assertNotNull(find(view,"choose-runtime-classpath",JButton.class));
                find(view,"runtime-classpath",JTextField.class).setText("manual.txt");
                assertEquals("manual.txt",find(view,"runtime-classpath",JTextField.class).getText());
            });
            System.setProperty(property,"explicit.txt");
            SwingUtilities.invokeAndWait(()->assertEquals("explicit.txt",find(new JaCaMoWorkbenchPanel(()->"configured",message->fail(message),true),"runtime-classpath",JTextField.class).getText()));
        }finally{if(previous==null)System.clearProperty(property);else System.setProperty(property,previous);}
    }
    @Test void historicalNavigationBorrowsTheMatchingRetainedImageInsteadOfTheCurrentLiveState() {
        var now=Instant.EPOCH;
        var first=new RuntimeVerificationResult("run",1,"revision",1,"one","cartago",1,now,now,now,"c1","contracts","state1",List.of(),0,"COMPLETE","CURRENT_OBSERVED","",null);
        var second=new RuntimeVerificationResult("run",1,"revision",2,"two","cartago",2,now,now,now,"c2","contracts","state2",List.of(),0,"COMPLETE","CURRENT_OBSERVED","",null);
        var one=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot(1,first,null,null,
                new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.StateImage(Map.of(),List.of(),"accepted image ONE"));
        var two=new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot(2,second,null,null,
                new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.StateImage(Map.of(),List.of(),"accepted image TWO"));
        var facade=new JaCaMoFacade(){public String status(){return "images";}
            public List<RuntimeVerificationResult> runtimeVerificationHistory(){return List.of(first,second);}
            public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot verificationSnapshot(){return two;}
            public List<org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot> retainedVerificationSnapshots(){return List.of(one,two);}};
        var cuts=new ObservedConformancePresenter(facade).capture();
        assertEquals(2,cuts.size());assertTrue(cuts.getFirst().state().contains("accepted image ONE"));
        assertFalse(cuts.getFirst().state().contains("accepted image TWO"));assertTrue(cuts.getLast().state().contains("accepted image TWO"));
    }
    @Test void requirementsPreviewShowsDeclaredReadsAndCapabilitiesWithoutClaimingLiveCompleteness() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view=new ObservationRequirementsPanel();
            view.refresh(Map.of("readSetHash","hash", "reads",List.of(Map.of("contract","Post",
                    "field","Meter::reading","preState",true,"sources",List.of(Map.of("source","cartago","provider","example.fields")),
                    "status","DECLARED_NOT_OBSERVED")),"capabilities",List.of(Map.of("id","fields","provider","example.fields","status","REQUESTED_NOT_OBSERVED"))));
            var table=find(view,"observation-read-set",JTable.class);
            assertEquals(2,table.getRowCount());
            assertEquals("Meter::reading @pre",table.getValueAt(0,1));
            assertTrue(table.getValueAt(0,3).toString().contains("example.fields"));
            assertEquals("DECLARED_NOT_OBSERVED",table.getValueAt(0,4));
            assertFalse(table.isCellEditable(0,0));
        });
    }
    @Test void extractedWorkflowRetainsUpstreamLayoutWithPinnedSourceAndExplicitBackendPatches() throws Exception {
        var json = org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/main/resources/META-INF/use4goal-workflow-provenance.json"))));
        assertEquals("GPL-3.0", json.get("license"));
        for(Object raw:(List<?>)json.get("files")) {
            var file=org.jacamo.bridge.contract.CanonicalJson.object(raw);
            var pinned=java.nio.file.Files.readAllBytes(java.nio.file.Path.of((String)file.get("sourceFixture")));
            var migrated=java.nio.file.Files.readAllBytes(java.nio.file.Path.of((String)file.get("targetPath")));
            var sha=java.security.MessageDigest.getInstance("SHA-256");
            assertEquals(file.get("sourceSha256"),java.util.HexFormat.of().formatHex(sha.digest(pinned)));
            assertEquals(file.get("targetSha256"),java.util.HexFormat.of().formatHex(sha.digest(migrated)));
        }
        var entry = org.jacamo.bridge.contract.CanonicalJson.object(((List<?>) json.get("files")).getFirst());
        byte[] originalBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of((String)entry.get("sourceFixture")));
        byte[] targetBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of((String)entry.get("targetPath")));
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        assertEquals(entry.get("sourceSha256"), java.util.HexFormat.of().formatHex(digest.digest(originalBytes)));
        assertEquals(entry.get("targetSha256"), java.util.HexFormat.of().formatHex(digest.digest(targetBytes)));
        String original = new String(originalBytes, java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        String selected = original.substring(original.indexOf("    private JPanel buildConformanceAolPanel()"),
                original.indexOf("    private Component buildContent()")).stripTrailing()
                .replace("buildConformanceAolPanel()", "buildConformancePanel()")
                .replace("leftAolPanel.add(aolView, BorderLayout.CENTER);", "leftAolPanel.add(new JScrollPane(stateSnapshotText), BorderLayout.CENTER);")
                .replace("Counterexample Trace & State Navigation", "Observed Prefix & State Navigation")
                .replace("State Snapshot View", "Accepted USE State (read-only)")
                .replace("Conformance Result", "Observed prefix").replace("Risk Assessment", "Coverage / freshness");
        String target = new String(targetBytes, java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(target.contains(selected), "Reuse the actual upstream layout, not a newly drawn lookalike");
        assertFalse(target.contains("import org.vnu.sme.goal.verify"));
        assertFalse(target.contains("CslSystemStateCompiler"));
        assertTrue(((List<?>)entry.get("patches")).contains("REMOVE_BPMN_SOLVER_AND_SECOND_RUNTIME"));
    }

    @Test void reusedNavigationDisplaysActualCutQualityAndDoesNotReevaluateHistory() throws Exception {
        var first = new ObservedConformancePanel.Cut("1", "create", "p = 1", List.of(
                new ObservedConformancePanel.Evaluation("g", "TRUE", "TRUE", "KNOWN", "cut1")), "COMPLETE", "CURRENT_OBSERVED", false);
        var second = new ObservedConformancePanel.Cut("2", "execution-only", "p = 1", List.of(
                new ObservedConformancePanel.Evaluation("g", "TRUE", "UNKNOWN", "GAP", "missing boundary")), "PARTIAL", "STALE", false);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ObservedConformancePanel();
            panel.setCuts(List.of(first, second));
            assertEquals("2", panel.selectedCut().id());
            assertEquals("UNKNOWN", find(panel, "observed-goals", JTable.class).getValueAt(0, 2));
            assertFalse(find(panel, "observed-failure", JButton.class).isEnabled(), "GAP must not become a failing checkpoint");
            find(panel, "observed-previous", JButton.class).doClick();
            assertEquals("1", panel.selectedCut().id());
            assertEquals("TRUE", find(panel, "observed-goals", JTable.class).getValueAt(0, 2));
            panel.setCuts(List.of(first, second));
            assertEquals("1", panel.selectedCut().id(), "Refreshing live evidence must preserve a historical selection");
            panel.setCuts(List.of());
            assertNull(panel.selectedCut());
            assertEquals(0, find(panel, "observed-goals", JTable.class).getRowCount());
        });
        assertThrows(IllegalStateException.class, () -> new ObservedConformancePanel().setCuts(List.of(first)));
    }

    @Test void presenterUsesFacadeEvidenceOnlyAndPreservesPlayerContextAndGapOverlay() {
        var occurrence = Map.<String,Object>of("sourceElement", "task", "leafStatus", "FULFILLED", "aggregateStatus", "FULFILLED",
                "quality", "KNOWN", "player", "alice", "role", "r");
        var core = Map.<String,Object>of("qualityOverlay", "HISTORY_GAP", "contexts", List.of(Map.of("context", "room",
                "occurrences", Map.of("alice-task", occurrence))));
        var evidence = new SemanticVerificationEnvelope("1.0", "JACAMO_DECLARATIVE_XML", "config", "cut", List.of(),
                Map.of(), Map.of(), List.of(), Map.of("originalEngine", core));
        var now = Instant.EPOCH;
        var result = new RuntimeVerificationResult("run", 1, "revision", 1, "event", "source", 1, now, now, now,
                "checkpoint", "constraints", "state", List.of(), 0, "PARTIAL", "STALE", "missing", evidence);
        var facade = new JaCaMoFacade() {
            public String status() { return "test"; }
            public List<RuntimeVerificationResult> runtimeVerificationHistory() { return List.of(result); }
            public RuntimeVerificationResult runtimeVerificationResult() { return result; }
        };
        var cuts = new ObservedConformancePresenter(facade).capture();
        assertEquals(1, cuts.size());
        var row = cuts.getFirst().evaluations().getFirst();
        assertEquals("FULFILLED", row.raw());
        assertEquals("UNKNOWN", row.status());
        assertEquals("HISTORY_GAP", row.quality());
        assertTrue(row.evidence().contains("alice"));
        assertTrue(row.evidence().contains("room"));
        assertFalse(cuts.getFirst().failed());
        assertTrue(cuts.getFirst().state().contains("not retained"), "Never display the current live state as a historical image");
    }

    private static <T> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) { var found = find(nested, name, type); if (found != null) return found; }
        }
        return null;
    }
}

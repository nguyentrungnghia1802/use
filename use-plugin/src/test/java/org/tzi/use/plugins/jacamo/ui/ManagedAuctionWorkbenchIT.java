package org.tzi.use.plugins.jacamo.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.impl.PluginRuntime;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.ManagedProducerTestSupport;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage;

/** Separate official producer JVM + real USE GUI/Session + unmodified maintained Auction profile. */
class ManagedAuctionWorkbenchIT {
    private String oldHeight, oldWidth, oldProjection;
    @org.junit.jupiter.api.BeforeEach void configureUseDiagramDefaults() {
        oldHeight=System.getProperty("use.gui.view.classdiagram.class.minheight");
        oldWidth=System.getProperty("use.gui.view.classdiagram.class.minwidth");
        oldProjection=System.getProperty(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.PROPERTY);
        // Both modes enforce the current domain projection boundary.
        System.setProperty(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.PROPERTY,"FULL");
        System.setProperty("use.gui.view.classdiagram.class.minheight","40");
        System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
    }
    @org.junit.jupiter.api.AfterEach void restoreUseDiagramDefaults() {
        restoreProperty("use.gui.view.classdiagram.class.minheight",oldHeight);
        restoreProperty("use.gui.view.classdiagram.class.minwidth",oldWidth);
        restoreProperty(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.PROPERTY,oldProjection);
    }
    private static void restoreProperty(String key,String value) {
        if(value==null) System.clearProperty(key); else System.setProperty(key,value);
    }
    @Test void managedOriginalAuctionKeepsCurrentUiRelationsAndRecordedReanalysisOnTheSameSession() throws Exception {
        assertFalse(java.awt.GraphicsEnvironment.isHeadless());
        Path evidence=Path.of("target/workbench-acceptance", "auction-"+System.currentTimeMillis()).toAbsolutePath();
        Path original=Path.of("../../jacamo/examples/auction/auction.jcm");
        Path profile=Path.of("src/test/resources/jacamo/ocl/auction/auction_demo.ocl").toAbsolutePath();
        String profileHash=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Files.readAllBytes(profile));
        var errors=new java.util.concurrent.CopyOnWriteArrayList<String>();
        try(var producer=new ManagedProducerTestSupport(original,evidence)) {
            producer.configureWorkflow(); Session session=new Session();
            var recording=new org.tzi.use.plugins.jacamo.HelloWorldSemanticInventoryIT.RecordingTransportFactory(evidence);
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),org.tzi.use.plugins.jacamo.SemanticAuthority.BRIDGE,
                    ()->producer.configuration,recording,org.tzi.use.plugins.jacamo.PipelineMode.CODE_GROUNDED_NATIVE,session)) {
                producer.clearWorkflowProperties(); facade.configureBridge(producer.configuration);
                MainWindow.setJavaFxCall(true);
                MainWindow window=onEdt(()->MainWindow.create(session,PluginRuntime.getInstance()));
                JFrame workbench=onEdt(()->new JFrame("Managed Auction Workbench acceptance"));
                JaCaMoWorkbenchPanel panel=onEdt(()->new JaCaMoWorkbenchPanel(facade,errors::add));
                try {
                    onEdt(()->{workbench.setContentPane(panel);workbench.setSize(1500,1000);workbench.setVisible(true);panel.importProject(producer.jcm);return null;});
                    await(()->facade.workflowStatus().state().equals("MODEL_READY"),60,errors);
                    // Facade readiness precedes SwingWorker.done() and the UI busy-guard release.
                    // A real user cannot Load OCL until this control is enabled; do not race that boundary.
                    awaitEnabled(panel,"load-profile",30,errors);
                    var system=session.system(); assertNotNull(system); assertFalse(Files.exists(producer.control.resolve("started.json")));
                    var baseline=facade.verificationSnapshot();
                    onEdt(()->{panel.loadVerificationProfile(profile);return null;});
                    await(()->facade.workflowStatus().state().equals("OCL_READY"),30,errors);
                    awaitEnabled(panel,"start-runtime",30,errors);
                    var installed=facade.verificationSnapshot(); assertSame(system,session.system());
                    assertEquals(profileHash,installed.profile().profile().sourceHash());
                    assertTrue(installed.currentVersion()>baseline.currentVersion());
                    onEdt(()->{
                        // USE asks about hiding >100 instances. Choose No through the normal dialog,
                        // so this acceptance actually verifies every displayed object/link.
                        var confirmation=new javax.swing.Timer(100,event->{
                            for(var dialog:java.awt.Window.getWindows())
                                if(dialog instanceof javax.swing.JDialog prompt && prompt.isShowing()
                                        && "Large system state".equals(prompt.getTitle())) {
                                    findOptionPane(prompt).setValue(javax.swing.JOptionPane.NO_OPTION);
                                    ((javax.swing.Timer)event.getSource()).stop();
                                }
                        });
                        confirmation.start();
                        try { menuItem(window.getJMenuBar(),"Object diagram").doClick(); }
                        finally { confirmation.stop(); }
                        return null;
                    });
                    var diagram=onEdt(()->window.getObjectDiagrams().getFirst());
                    assertSame(system,diagram.system());
                    onEdt(()->{component(panel,"start-runtime",javax.swing.JButton.class).doClick();return null;});
                    await(()->facade.workflowStatus().state().equals("LIVE"),30,errors);
                    assertTrue(Files.exists(producer.control.resolve("started.json")));
                    await(()->system.model().getClass("AuctionArtifact")!=null && system.model().getClass("AuctionArtifact").attribute("running",false)!=null,30,errors);
                    Path observedProfile=evidence.resolve("auction-observed.ocl");
                    Files.writeString(observedProfile,Files.readString(profile)+"\n"+Files.readString(Path.of("src/test/resources/jacamo/ocl/auction/auction_runtime.ocl")));
                    awaitEnabled(panel,"load-profile",30,errors);
                    onEdt(()->{panel.loadVerificationProfile(observedProfile);return null;});
                    await(()->facade.verificationSnapshot().profile().profile().sourceFile().equals(observedProfile.toString()),30,errors);
                    // Loading a runtime-only class/profile through a real GUI can
                    // finish after the short running=true interval. Do not make
                    // live completeness depend on winning that scheduling race.
                    // AuctionPauseResumeIT proves current HARD FAIL/control; this
                    // test proves UI/relations and retrospective typed state cuts.
                    await(()->facade.goalView().schemes().stream().filter(s->s.runtime()).count()==2
                            && facade.goalView().schemes().stream().filter(s->s.runtime())
                                .flatMap(s->s.goals().stream()).allMatch(g->g.state().equals("SATISFIED")),60,errors);
                    facade.runFullVerification();
                    List<RuntimeHistoryPage.Entry> recorded=allPages(facade);
                    var transition=outcomes(recorded);
                    assertFalse(transition.isEmpty(),"Current runtime profile was not evaluated: "+errors);
                    assertEquals(VerificationOutcome.PASS,transition.getLast());
                    assertSame(system,session.system());
                    assertTrue(facade.verificationSnapshot().currentVersion()>installed.currentVersion());
                    // The source deliberately allows failed/dropped bids. Membership/role
                    // does not imply focus or mission commitment by every participant.
                    // Compare every exact endpoint to an authoritative cut, not a count
                    // inferred from five agents and two auctions.
                    facade.resyncRuntime();
                    var relationEvidence=onEdt(()->assertAuthoritativeRelations(system,facade.verificationSnapshot(),recording.lastRuntimeSnapshot()));
                    Files.write(evidence.resolve("authoritative-relations.json"),CanonicalJson.encode(relationEvidence));
                    var liveRelations=onEdt(()->environmentRelations(system));
                    assertTrue((long)liveRelations.get("focusLinks")>0,liveRelations.toString());
                    assertEquals(10L,liveRelations.get("joinLinks"),liveRelations.toString());
                    assertEquals(5L,liveRelations.get("roleLinks"),liveRelations.toString());
                    assertTrue((long)liveRelations.get("commitmentLinks")>0,liveRelations.toString());
                    assertEquals(2L,liveRelations.get("workspaceObjects"),"Implicit and runtime root must share one object");
                    Files.write(evidence.resolve("relations-before-resync.json"),CanonicalJson.encode(liveRelations));
                    onEdt(()->{
                        assertTrue(system.state().allLinks().stream().allMatch(diagram.getDiagram().getVisibleData()::containsLink));
                        return null;
                    });
                    facade.resyncRuntime();
                    var resyncedRelations=onEdt(()->environmentRelations(system));
                    assertEquals(liveRelations,resyncedRelations,"Resync must retain the already observed live relations");
                    Files.write(evidence.resolve("relations-after-resync.json"),CanonicalJson.encode(resyncedRelations));
                    var domainInventory=onEdt(()->{
                        var model=system.model(); var state=system.state();
                        assertTrue(model.classes().stream().allMatch(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy::allowsClass));
                        for(String name:org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy.BASE_CLASSES) assertNotNull(model.getClass(name));
                        assertNull(model.getClass("Agent").attribute("role",true));
                        var roleLinks=state.allLinks().stream().filter(l->l.association().getAnnotation(
                            org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ROLE_ASSOCIATION)!=null).toList();
                        assertEquals(5,roleLinks.size(),"Official post-execution GroupBoard players must project as five direct links");
                        assertTrue(roleLinks.stream().allMatch(l->l instanceof org.tzi.use.uml.sys.MLinkObject));
                        assertTrue(state.checkStructure(new java.io.PrintWriter(new java.io.StringWriter()),true));
                        return Map.of("classes",model.classes().stream().map(c->c.name()).sorted().toList(),
                            "objects",state.allObjects().stream().map(o->o.name()+":"+o.cls().name()).sorted().toList(),
                            "links",state.allLinks().stream().map(l->l.association().name()+":"+l.linkedObjects().stream().map(o->o.name()).toList()).sorted().toList(),
                            "roleAssociations",model.associations().stream().filter(a->a.getAnnotation(
                                org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ROLE_ASSOCIATION)!=null)
                                .map(a->Map.of("name",a.name(),"ends",a.associationEnds().stream()
                                    .map(e->Map.of("class",e.cls().name(),"multiplicity",e.multiplicity().toString())).toList())).toList(),
                            "directRoleLinks",roleLinks.size(),"forbiddenClasses",0,"roleLinkObjects",roleLinks.size());
                    });
                    facade.exportNativeUse(evidence.resolve("auction.use")); facade.exportNativeSoil(evidence.resolve("auction.cmd"));
                    onEdt(()->{ Files.write(evidence.resolve("selective-projection.json"),org.jacamo.bridge.contract.CanonicalJson.encode(
                            org.tzi.use.plugins.jacamo.codegrounded.ProjectionExposureEvidence.assertAndReport(facade))); return null; });
                    recorded=allPages(facade);
                    onEdt(()->{
                        panel.refreshRuntime(); JTable current=component(panel,"verification-table",JTable.class);
                        assertEquals(facade.verificationSnapshot().result().outcomes().size(),current.getRowCount());
                        assertSame(system,diagram.system());
                        assertTrue(system.state().allObjects().stream().allMatch(diagram.getDiagram().getVisibleData().fObjectToNodeMap::containsKey));
                        assertTrue(system.state().allLinks().stream().allMatch(diagram.getDiagram().getVisibleData()::containsLink));
                        component(panel,"workbench-tabs",javax.swing.JTabbedPane.class).setSelectedIndex(4);
                        var image=new java.awt.image.BufferedImage(workbench.getWidth(),workbench.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics=image.createGraphics();workbench.printAll(graphics);graphics.dispose();
                        javax.imageio.ImageIO.write(image,"png",evidence.resolve("workbench.png").toFile());return null;
                    });
                    assertTrue(producer.sourceUnchanged()); assertTrue(errors.isEmpty(),errors.toString());
                    Path bundle=evidence.resolve("recorded-replay"); facade.exportRuntimeReplay(bundle);
                    var propertyEvidence=runningPropertyEvidence(bundle);
                    assertTrue(propertyEvidence.stream().anyMatch(item->((Map<?,?>)item.get("property")).get("values").equals(List.of("true"))));
                    assertTrue(propertyEvidence.stream().anyMatch(item->((Map<?,?>)item.get("property")).get("values").equals(List.of("false"))));
                    var replay=facade.replayRuntime(bundle); assertTrue(replay.complete(),replay.diagnostics().toString());
                    var before=facade.verificationSnapshot();
                    // Stop the producer before an explicit late-load/re-analysis check; preserve the recorded observation evidence.
                    producer.close();
                    facade.disconnectRuntime();
                    facade.loadVerificationProfile(observedProfile); var late=facade.verificationSnapshot();
                    var analysis=facade.reanalyzeRuntime(bundle,evidence.resolve("reanalysis"));
                    assertTrue(analysis.complete(),analysis.diagnostics().toString()); assertEquals("REPLAY_REANALYSIS",analysis.origin());
                    var retrospective=reanalysisOutcomes(analysis.output());
                    assertTrue(retrospective.contains(VerificationOutcome.FAIL),"Recorded typed running=true cut must fail the retrospective condition: "+retrospective);
                    assertEquals(VerificationOutcome.PASS,retrospective.getLast());
                    assertEquals(late,facade.verificationSnapshot()); assertSame(system,session.system());
                    var report=new java.util.LinkedHashMap<String,Object>();
                    report.put("status","PASS");report.put("scope","OBSERVED_SUPPORTED_PROJECTION_ONLY");report.put("profileHash",profileHash);
                    report.put("domainInventory",domainInventory);
                    report.put("liveRelationsBeforeResync",liveRelations); report.put("relationResyncParity",true);
                    report.put("originalSourceUnchanged",true); report.put("baseline",installed.result().toMap());
                    report.put("workflow",Map.of("bootstrapAt",facade.workflowStatus().bootstrapAt(),"startedAt",Files.readString(producer.control.resolve("started.json"))));
                    report.put("transitions",transition.stream().map(Enum::name).toList()); report.put("current",before.result().toMap());
                    report.put("retrospectiveTransitions",retrospective.stream().map(Enum::name).toList());
                    report.put("retrospectiveOrigin","REPLAY_REANALYSIS; NEW_PROFILE_RESULTS_NOT_LIVE");
                    report.put("transitionRecords",transitionRecords(recorded));report.put("runningPropertyEvidence",propertyEvidence);
                    report.put("lateLoadedVersion",late.profile().loadedVersion());
                    report.put("liveUnchangedByReanalysis",true);report.put("sameActiveSystem",true);
                    report.put("journalEntries",recorded.size());report.put("recordedReplay",replay.complete());report.put("reanalysis",analysis.complete());
                    Files.write(evidence.resolve("gui-acceptance.json"),CanonicalJson.encode(report));
                    org.tzi.use.plugins.jacamo.codegrounded.StepReplayProof.run(facade,session,window,bundle,evidence);
                    report.put("stepReplayComplete",true);
                    assertTrue(producer.sourceUnchanged());
                    Files.write(evidence.resolve("gui-acceptance.json"),CanonicalJson.encode(report));
                } catch(Exception | AssertionError failure) {
                    try {
                        var current=facade.verificationSnapshot();
                        Files.write(evidence.resolve("failure-state.json"),CanonicalJson.encode(Map.of(
                                "workflow",facade.workflowStatus().toString(),"authority",facade.authorityStatus().toString(),
                                "result",current.result()==null ? Map.of() : current.result().toMap(),
                                "relations",onEdt(()->environmentRelations(session.system())),
                                "classes",session.system().model().classes().stream().map(c->c.name()).sorted().toList())));
                        facade.exportRuntimeReplay(evidence.resolve("failure-replay"));
                    } catch(Exception diagnostics) { failure.addSuppressed(diagnostics); }
                    throw failure;
                } finally {
                    onEdt(()->{window.getObjectDiagrams().forEach(view->view.detachModel());workbench.dispose();window.dispose();MainWindow.setJavaFxCall(false);return null;});
                }
            }
        }
    }

    private static Map<String,Object> assertAuthoritativeRelations(org.tzi.use.uml.sys.MSystem system,
            org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot cut,
            org.jacamo.bridge.contract.RuntimeSnapshot source) {
        assertNotNull(source);assertNotNull(cut.image());
        var aliases=new java.util.HashMap<String,String>();
        cut.image().objects().values().forEach(object->object.exactIdentities().forEach(identity->{
            String previous=aliases.putIfAbsent(identity,object.name());
            assertTrue(previous==null || previous.equals(object.name()),"Ambiguous native identity: "+identity);
        }));
        java.util.function.Function<String,String> exact=identity->{
            String name=aliases.get(identity);assertNotNull(name,"Missing authoritative endpoint: "+identity);return name;
        };
        var domainArtifacts=source.facts().stream().map(f->f.values())
                .filter(v->"UPSERT_CARTAGO_ARTIFACT".equals(v.get("normalizedEventKind"))
                        && "auction_env.AuctionArtifact".equals(v.get("artifactTypeJavaClassName")))
                .map(v->(String)v.get("semanticId")).collect(java.util.stream.Collectors.toSet());
        assertEquals(2,domainArtifacts.size());domainArtifacts.forEach(exact::apply);
        var expected=new java.util.TreeSet<String>();
        String joins=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("memberOf","Agent","Workspace");
        String focuses=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("focuses","Agent","Artifact");
        String commitments=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("committedTo","Agent","Mission");
        for(var fact:source.facts()) {
            var value=fact.values();String kind=(String)value.get("normalizedEventKind");
            if("UPSERT_CARTAGO_AGENT_IDENTITY".equals(kind) && value.get("agentDeclarationSemanticId") instanceof String agent)
                expected.add(joins+":"+List.of(exact.apply(agent),exact.apply((String)value.get("workspaceSemanticId"))));
            if("SET_CARTAGO_FOCUS".equals(kind) && Boolean.TRUE.equals(value.get("focused"))
                    && domainArtifacts.contains(value.get("artifactSemanticId")))
                expected.add(focuses+":"+List.of(exact.apply((String)value.get("agentSemanticId")),exact.apply((String)value.get("artifactSemanticId"))));
            if("UPSERT_MOISE_GROUP".equals(kind)) for(Object entry:(List<?>)value.get("players")) {
                var player=CanonicalJson.object(entry);
                var association=org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.roleAssociation(system.model(),
                        (String)value.get("organisationSpecSemanticId"),(String)value.get("groupSpecSemanticId"),(String)player.get("roleSemanticId"));
                assertNotNull(association);
                expected.add(association.name()+":"+List.of(exact.apply((String)player.get("agentSemanticId")),exact.apply((String)value.get("semanticId"))));
            }
            if("UPSERT_MOISE_SCHEME".equals(kind)) for(Object entry:(List<?>)value.get("commitments")) {
                var player=CanonicalJson.object(entry);
                String mission=org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.functionalObjectId("mission",
                        (String)value.get("semanticId"),(String)player.get("missionSemanticId"));
                expected.add(commitments+":"+List.of(exact.apply((String)player.get("agentSemanticId")),exact.apply(mission)));
            }
        }
        var observed=environmentRelations(system);
        assertEquals(expected,new java.util.TreeSet<>((List<?>)observed.get("relations")),
                "Every exposed relation must equal the official cut; no missing or invented endpoints");
        return Map.of("snapshot",source.snapshotId(),"expectedRelations",List.copyOf(expected),"native",observed,"exactParity",true);
    }
    private static Map<String,Object> environmentRelations(org.tzi.use.uml.sys.MSystem system) {
        var links=system.state().allLinks();
        var joins=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("memberOf","Agent","Workspace");
        var focuses=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("focuses","Agent","Artifact");
        var commitments=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("committedTo","Agent","Mission");
        return Map.of("joinLinks",links.stream().filter(l->l.association().name().equals(joins)).count(),
                "focusLinks",links.stream().filter(l->l.association().name().equals(focuses)).count(),
                "roleLinks",links.stream().filter(l->l.association().getAnnotation(
                        org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ROLE_ASSOCIATION)!=null).count(),
                "commitmentLinks",links.stream().filter(l->l.association().name().equals(commitments)).count(),
                "workspaceObjects",(long)system.state().objectsOfClass(system.model().getClass("Workspace")).size(),
                "relations",links.stream().filter(l->l.association().name().equals(joins) || l.association().name().equals(focuses)
                        || l.association().name().equals(commitments) || l.association().getAnnotation(
                        org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ROLE_ASSOCIATION)!=null)
                        .map(l->l.association().name()+":"+l.linkedObjects().stream().map(o->o.name()).toList()).sorted().toList());
    }
    private static List<RuntimeHistoryPage.Entry> allPages(DefaultJaCaMoFacade facade) {
        var records=new java.util.ArrayList<RuntimeHistoryPage.Entry>();long offset=0,limit=facade.runtimeHistoryTail().persistedEntries();
        while(offset<limit){var page=facade.runtimeHistoryPage(offset,128);records.addAll(page.entries());if(page.nextOrdinal()<=offset)break;offset=page.nextOrdinal();}
        return records;
    }
    private static List<VerificationOutcome> outcomes(List<RuntimeHistoryPage.Entry> entries) {
        var values=new java.util.ArrayList<VerificationOutcome>();
        entries.forEach(entry->entry.result().outcomes().stream().filter(value->value.constraintId().equals("EXTERNAL:AuctionArtifact::DEMO_RUNTIME_NoRunningAuction"))
                .forEach(value->{if(values.isEmpty()||values.getLast()!=value.outcome())values.add(value.outcome());}));
        return values;
    }
    private static List<Map<String,Object>> transitionRecords(List<RuntimeHistoryPage.Entry> entries) {
        var records=new java.util.ArrayList<Map<String,Object>>(); VerificationOutcome previous=null;
        for(var entry:entries) for(var outcome:entry.result().outcomes())
            if(outcome.constraintId().equals("EXTERNAL:AuctionArtifact::DEMO_RUNTIME_NoRunningAuction")&&outcome.outcome()!=previous) {
                records.add(Map.of("ordinal",entry.ordinal(),"interval",entry.intervalId(),"outcome",outcome.outcome().name(),"result",entry.result().toMap()));
                previous=outcome.outcome();
            }
        return records;
    }
    private static List<VerificationOutcome> reanalysisOutcomes(Path output) throws Exception {
        var result=new java.util.ArrayList<VerificationOutcome>();
        try(var lines=Files.lines(output.resolve("reanalysis.jsonl"))) {
            for(String line:(Iterable<String>)lines::iterator) {
                var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var observation=CanonicalJson.object(entry.get("result"));
                for(Object item:(List<?>)observation.get("outcomes")) {
                    var outcome=CanonicalJson.object(item);
                    if("EXTERNAL:AuctionArtifact::DEMO_RUNTIME_NoRunningAuction".equals(outcome.get("constraintId"))) {
                        var value=VerificationOutcome.valueOf((String)outcome.get("outcome"));
                        if(result.isEmpty() || result.getLast()!=value)result.add(value);
                    }
                }
            }
        }
        return result;
    }
    private static List<Map<String,Object>> runningPropertyEvidence(Path bundle) throws Exception {
        var records=new java.util.ArrayList<Map<String,Object>>();
        try(var lines=Files.lines(bundle.resolve("runtime.jsonl"))) {
            for(String line:(Iterable<String>)lines::iterator) {
                var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(!java.util.Set.of("EVENT","SNAPSHOT","OPERATION_PRE","OPERATION_POST").contains(entry.get("kind"))) continue;
                var properties=new java.util.ArrayList<Map<String,Object>>(); collectRunningProperties(entry.get("payload"),properties);
                var result=CanonicalJson.object(entry.get("result"));
                for(var property:properties) records.add(Map.of("ordinal",entry.get("ordinal"),"entryKind",entry.get("kind"),"stateVersion",result.get("stateVersion"),
                        "eventId",result.get("eventId"),"sourceSequence",result.get("sourceSequence"),"property",property));
            }
        } return List.copyOf(records);
    }
    private static void collectRunningProperties(Object value,List<Map<String,Object>> properties) {
        if(value instanceof Map<?,?> map) {
            if("running".equals(map.get("name"))&&List.of("java.lang.Boolean").equals(map.get("valueTypes")))
                properties.add(CanonicalJson.object(value));
            map.values().forEach(child->collectRunningProperties(child,properties));
        } else if(value instanceof List<?> list) list.forEach(child->collectRunningProperties(child,properties));
    }
    private static void await(java.util.function.BooleanSupplier ready,int seconds,List<String> errors) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(!ready.getAsBoolean()&&errors.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(50);
        assertTrue(ready.getAsBoolean(),"GUI workflow timeout: "+errors);
    }
    private static void awaitEnabled(JaCaMoWorkbenchPanel panel,String name,int seconds,List<String> errors) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(!onEdt(()->component(panel,name,javax.swing.JButton.class).isEnabled())
                &&errors.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(50);
        assertTrue(onEdt(()->component(panel,name,javax.swing.JButton.class).isEnabled()),"GUI control not ready: "+name+" "+errors);
    }
    private static <T>T onEdt(java.util.concurrent.Callable<T> action)throws Exception{
        var task=new FutureTask<>(action);SwingUtilities.invokeLater(task);return task.get(30,TimeUnit.SECONDS);
    }
    private static javax.swing.JOptionPane findOptionPane(Container root) {
        for(Component child:root.getComponents()) {
            if(child instanceof javax.swing.JOptionPane pane) return pane;
            if(child instanceof Container nested) { var pane=findOptionPane(nested); if(pane!=null)return pane; }
        } return null;
    }
    private static <T extends Component>T component(Container root,String name,Class<T> type){
        for(Component child:root.getComponents()){if(name.equals(child.getName())&&type.isInstance(child))return type.cast(child);
            if(child instanceof Container container){T found=find(container,name,type);if(found!=null)return found;}}
        throw new AssertionError("Component missing: "+name);
    }
    private static <T extends Component>T find(Container root,String name,Class<T> type){
        for(Component child:root.getComponents()){if(name.equals(child.getName())&&type.isInstance(child))return type.cast(child);
            if(child instanceof Container container){T found=find(container,name,type);if(found!=null)return found;}}return null;
    }
    private static javax.swing.JMenuItem menuItem(javax.swing.JMenuBar bar,String text){
        for(int index=0;index<bar.getMenuCount();index++){var item=menuItem(bar.getMenu(index),text);if(item!=null)return item;}
        throw new AssertionError("Menu missing: "+text);
    }
    private static javax.swing.JMenuItem menuItem(javax.swing.JMenu menu,String text){
        if(menu==null)return null;
        for(Component item:menu.getMenuComponents()){
            if(item instanceof javax.swing.JMenu submenu){var found=menuItem(submenu,text);if(found!=null)return found;}
            else if(item instanceof javax.swing.JMenuItem action&&text.equals(action.getText()))return action;
        }return null;
    }
}

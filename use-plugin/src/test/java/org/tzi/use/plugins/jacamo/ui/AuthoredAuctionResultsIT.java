package org.tzi.use.plugins.jacamo.ui;
import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
import javax.swing.*;import org.junit.jupiter.api.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.main.Session;import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthoredAuctionResultsIT {
 @Test @Timeout(420) void fullAuthoredAuctionKeepsFixedModelRowsAndReportsRealConstraintsEvenAfterWinner()throws Exception {
  Path checkout=Path.of("..").toAbsolutePath().normalize(),project=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm"),inputs=checkout.resolve("use-plugin/examples/auction");
  Path evidence=Files.createTempDirectory(Files.createDirectories(checkout.resolve("use-plugin/verification/live-results-ui")),"auction-");
  byte[] source=Files.readAllBytes(project);var session=new Session();long pid=0;
  try(var facade=new DefaultJaCaMoFacade(checkout,session)){
   try{
    var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",inputs.resolve("auction-mapping.xml"),inputs.resolve("auction.ocl"),inputs.resolve("auction.csl"),inputs.resolve("auction.istar"),false);
    facade.prepareOwnedProducer(project,selection,null,false);pid=facade.ownedProducerStatus().pid();var system=session.system();assertNotEquals(ProcessHandle.current().pid(),pid);
    var definitions=new LiveGoalSummary(facade.semanticModels().goals());int expected=definitions.rows(List.of()).size();assertTrue(expected>0);
    var view=new java.util.concurrent.atomic.AtomicReference<ObservedConformancePanel>();SwingUtilities.invokeAndWait(()->view.set(ObservedConformancePanel.liveResults(facade.semanticModels().goals())));
    var presenter=new ObservedConformancePresenter(facade);facade.startRuntime();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);boolean finished=false;int samples=0;
    while(System.nanoTime()<deadline){assertSame(system,session.system());assertEquals(org.tzi.use.plugins.jacamo.bridge.BridgeClientState.LIVE,facade.authorityStatus().readiness(),facade.status());assertFalse(coordinator(facade).journal().hasGap(),coordinator(facade).journal().diagnostic());var cuts=presenter.captureCurrent();SwingUtilities.invokeAndWait(()->{view.get().setCuts(cuts);assertEquals(expected,DetachedAuthoredWorkflowTest.find(view.get(),"live-goal-status",JTable.class).getRowCount());});samples++;if(samples%10==0)diagnostics(facade,evidence);
     finished=systemRead(facade,()->{var state=system.state();var auctions=state.allObjects().stream().filter(o->o.cls().name().equals("Auction")).toList();return auctions.size()==2&&auctions.stream().allMatch(o->o.state(state).attributeValue("running").toString().equals("false")&&!o.state(state).attributeValue("winner").isUndefined()&&!o.state(state).attributeValue("winner").toString().equals("'no_winner'"));});if(finished)break;Thread.sleep(700);
    }assertTrue(finished,"Two actual source auctions must finish");
    // The observable winner precedes the Jason terminal marker. Wait for the
    // actual two certified stop receipts; console/domain completion is not a
    // verification barrier and must not create a premature final UI receipt.
    long stopDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
    while(!stopReceiptsComplete(facade)&&System.nanoTime()<stopDeadline)Thread.sleep(200);
    assertTrue(stopReceiptsComplete(facade),"Both source stop terminal receipts must be processed before freezing final verification");
    var coordinator=coordinator(facade);var result=completedObserverCut(facade,coordinator);assertNotNull(result.semantic());
    coordinator.read(()->{
     var contexts=system.state().allObjects().stream().filter(o->o.cls().name().equals("auction_capabilities")).toList();
     assertEquals(5,contexts.size(),"Each source agent has its own singleton context, not one shared capability player");
     var represented=new HashSet<String>();
     for(var context:contexts){var links=system.state().allLinks().stream().filter(l->l.association().name().equals("CslAgentContexts")&&l.linkedObjects().getFirst()==context).toList();assertEquals(1,links.size());var agent=links.getFirst().linkedObjects().get(1);assertEquals("AtomicAgent",agent.cls().name());assertTrue(represented.add(agent.name()),"No two capability contexts represent the same agent");}
     return null;
    });
    var presentation=CanonicalJson.object(result.semantic().observation().get("presentationState"));
    assertEquals("DECLARATIVE_PRESENTATION/1.2",presentation.get("version"));
    var objectQuality=CanonicalJson.object(presentation.get("objectQuality"));
    assertEquals(CanonicalJson.object(presentation.get("objects")).keySet(),objectQuality.keySet());
    assertTrue(objectQuality.values().stream().allMatch(Boolean.TRUE::equals),"The final accepted source cut has qualified values for every selected presentation object");
    var visualLinks=((List<?>)presentation.get("links")).stream().map(CanonicalJson::object).filter(l->"enacts".equals(l.get("association"))).toList();
    assertEquals(5,visualLinks.size(),"One exact actual Role enactment for each of the five agents, never all-to-all");
    coordinator.read(()->{for(var link:visualLinks){var ends=(List<?>)link.get("participants");var nativeLinks=system.state().allLinks();
      assertTrue(nativeLinks.stream().anyMatch(l->l.association().name().equals("CslAgentContexts")&&l.linkedObjects().getFirst().name().equals(ends.getFirst())&&l.linkedObjects().get(1).name().equals(link.get("via"))));
      assertTrue(nativeLinks.stream().anyMatch(l->l.association().name().equals("Plays")&&l.linkedObjects().getFirst().name().equals(link.get("via"))&&l.linkedObjects().get(1).name().equals(ends.get(1))));
    }return null;});
    var aolState=org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.aol(facade.semanticModels().csl(),result.semantic().observation(),"!new Unrelated('not_a_state_image')");
    assertEquals(5,aolState.associationLinks().stream().filter(l->l.relationName().equals("enacts")).count(),"Browsing must use frozen presentation links even without JSON image");
    // Offscreen original diagrams need the same official USE defaults as the desktop.
    var defaults=new Properties();try(var stream=getClass().getResourceAsStream("/etc/use.properties")){defaults.load(stream);}
    var introduced=new HashSet<String>();for(String key:defaults.stringPropertyNames())if(System.getProperty(key)==null){introduced.add(key);System.setProperty(key,defaults.getProperty(key));}
    try {SwingUtilities.invokeAndWait(()->{try{
      var cslView=org.tzi.use.plugins.jacamo.ui.use4goal.csl.CslView.embedded(facade.semanticModels().csl(),system.model());
      LiveGoalSummaryTest.renderPanel(cslView,"enactment-csl.png");cslView.detachModel();
      var views=new org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews(facade.semanticModels().csl(),facade.semanticModels().goals(),Map.of());
      views.select(new org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut("visual","trace","{}",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"key","known","state",false,true,false,result.semantic().observation()));
      LiveGoalSummaryTest.renderPanel(views.aolPanel,"enactment-aol.png");views.dispose();
    }catch(Exception e){throw new RuntimeException(e);}});}finally{introduced.forEach(System::clearProperty);}
    var visualCore=CanonicalJson.object(result.semantic().observation().get("originalEngine"));
    for(Object raw:(List<?>)visualCore.get("contexts")) {
     var expanded=org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.expand(facade.semanticModels().goals(),CanonicalJson.object(raw),org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1,Map.of("D_bid",org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier.ANY,"D_capability_start",org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier.SAME_PLAYER,"D_capability_bid",org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier.SAME_PLAYER,"D_capability_decide",org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier.SAME_PLAYER));
     var agents=expanded.trace().values().stream().filter(t->t.actor().equals("auction_capabilities")&&t.kind().equals("SOURCE_ELEMENT")).map(t->t.player()).collect(java.util.stream.Collectors.toSet());
     var rolePlayers=expanded.trace().values().stream().filter(t->Set.of("Auctioneer","Participant").contains(t.actor())&&t.kind().equals("SOURCE_ELEMENT")).map(t->t.player()).collect(java.util.stream.Collectors.toSet());
     assertTrue(agents.containsAll(rolePlayers),"Every admitted role player has its own capability Agent");
     assertTrue(agents.size()<=5,"A scheme may admit fewer than all five agents: role enactment is not mission commitment");
     for(var dependency:expanded.model().getDependencies())if(dependency.dependum().id().startsWith("D_capability_"))assertEquals(expanded.trace().get(dependency.dependerElmt()).player(),expanded.trace().get(dependency.dependeeElmt()).player(),"Live capability delegation must never cross physical players");
    }
    Files.write(evidence.resolve("result.json"),CanonicalJson.encode(result.toMap()));
    var objects=coordinator.read(()->system.state().allObjects().stream().filter(o->Set.of("Auction","AuctionMonitor","AuctionObligation").contains(o.cls().name())).map(o->{var fields=new TreeMap<String,String>();o.state(system.state()).attributeValueMap().forEach((a,v)->fields.put(a.name(),v.toString()));return Map.of("class",o.cls().name(),"object",o.name(),"fields",fields);}).toList());Files.write(evidence.resolve("domain-and-observer-state.json"),CanonicalJson.encode(objects));
    var browsable=presenter.capture();assertTrue(browsable.size()>2);
    var populationHistory=browsable.stream().flatMap(c->c.evaluations().stream()).filter(r->r.element().equals("NATIVE:auctionGroup::AuctioneerPopulation")).toList();
    assertTrue(populationHistory.stream().anyMatch(r->r.quality().equals("CONSTRAINT_AWAITING_QUALIFIED_ACTIVATION")),"The real initialization state must wait for qualified roster readiness, not be coerced into PASS");
    assertTrue(populationHistory.stream().noneMatch(r->r.status().equals("FAIL")),"This unchanged source keeps its one auctioneer after readiness; startup/unassessed cuts cannot create violations");
    assertTrue(result.semantic().constraints().stream().anyMatch(r->r.id().equals("NATIVE:auctionGroup::AuctioneerPopulation")&&r.kind().equals("INVARIANT")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS),"The operational population rule must genuinely run and PASS, not remain permanently skipped");
    assertTrue(result.semantic().constraints().stream().filter(r->r.id().equals("NATIVE:auctionGroup::AuctioneerPopulation")&&r.kind().equals("INVARIANT_BODY")).allMatch(r->!r.witness().getOrDefault("invariantActivation","").isBlank()),"Operational applicability must carry its latched source proof");
    Files.write(evidence.resolve("population-activation.json"),CanonicalJson.encode(Map.of("waitingObserved",true,"startupFailures",populationHistory.stream().filter(r->r.status().equals("FAIL")).count(),"finalBodies",result.semantic().constraints().stream().filter(r->r.id().equals("NATIVE:auctionGroup::AuctioneerPopulation")).map(r->Map.of("kind",r.kind(),"verdict",r.verdict().name(),"diagnostic",r.diagnostic(),"witness",r.witness())).toList())));

    // Pin result.json and UI summary to the SAME completed native cut. A new
    // live pending boundary must not be substituted for this verified receipt.
    var pinned=browsable.stream().filter(c->c.trace().contains("Result hash: "+result.resultHash())).findFirst().orElseThrow();
    var rows=definitions.rows(pinned.evaluations());assertEquals(expected,rows.size());
    var executionChoices=ExecutionScopeViews.choices(List.of(pinned));
    assertEquals(2,executionChoices.size(),"The two source schemes must retain distinct execution contexts");
    var boundEntities=new HashSet<String>();var scopedReceipts=new ArrayList<Object>();
    for(var choice:executionChoices.entrySet()){
     var context=ExecutionScopeViews.contexts(pinned).stream().filter(c->choice.getKey().equals(c.get("context"))).findFirst().orElseThrow();
     var entities=CanonicalJson.object(CanonicalJson.object(context.get("scopeView")).get("entities"));
     assertEquals(1,entities.size(),"This source binds exactly one physical Auction to each execution");
     var scopeChecks=((List<?>)context.get("executionConstraints")).stream().map(CanonicalJson::object).toList();
     var monitorBodies=scopeChecks.stream().filter(r->"INVARIANT_BODY".equals(r.get("kind"))&&"AuctionMonitor".equals(r.get("contextClass"))).toList();
     assertEquals(1,monitorBodies.stream().map(r->r.get("contextObject")).distinct().count(),"Only the exact scheme's observer may enter scoped OCL");
     assertTrue(scopeChecks.stream().filter(r->"INVARIANT".equals(r.get("kind"))).allMatch(r->"PASS".equals(r.get("verdict"))),"Every required native scoped invariant must actually evaluate PASS");
     var aol=org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.aol(facade.semanticModels().csl(),ExecutionScopeViews.project(pinned,choice.getKey()).visualEvidence(),"");
     assertEquals(1,aol.objects().values().stream().filter(o->o.kind()==org.tzi.use.plugins.jacamo.ui.use4goal.state.AcceptedAolState.Kind.ENTITY).count());
     var links=aol.associationLinks().stream().filter(l->l.relationName().equals("participates (execution)")).toList();
     var participation=(List<?>)CanonicalJson.object(context.get("scopeView")).get("participation");
     assertFalse(participation.isEmpty());assertEquals(participation.size(),links.size(),"Each execution link must have an exact cast/role/interval witness");
     SwingUtilities.invokeAndWait(()->{
      var sceneDefaults=new Properties();try(var input=getClass().getResourceAsStream("/etc/use.properties")){sceneDefaults.load(input);}catch(Exception failure){throw new RuntimeException(failure);}
      var addedSceneProperties=new HashSet<String>();for(String name:sceneDefaults.stringPropertyNames())if(System.getProperty(name)==null){System.setProperty(name,sceneDefaults.getProperty(name));addedSceneProperties.add(name);}
      try{var diagram=new org.tzi.use.plugins.jacamo.ui.use4goal.aol.view.AolDiagram(new java.io.PrintWriter(java.io.Writer.nullWriter()));
       try{diagram.setState(aol);LiveGoalSummaryTest.renderPanel(new JPanel(new java.awt.BorderLayout()){{add(new JScrollPane(diagram));}},"scoped-aol-"+choice.getValue().replaceAll("[^a-zA-Z0-9]","_")+".png");}finally{diagram.stopLayoutThread();}
      }finally{addedSceneProperties.forEach(System::clearProperty);}
     });

     assertTrue(boundEntities.add(entities.keySet().iterator().next()),"Separate schemes cannot borrow the same Entity binding");
     var scoped=ExecutionScopeViews.project(pinned,choice.getKey());
     var scopedRoots=scoped.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")&&r.element().equals("O_root")).toList();
     assertEquals(1,scopedRoots.size());assertEquals(choice.getKey(),scopedRoots.getFirst().context());
     assertEquals("FULFILLED",scopedRoots.getFirst().status(),"Each source execution must certify its own root independently");
     var localTimeline=BusinessTimeline.summarize(browsable.stream().map(c->ExecutionScopeViews.project(c,choice.getKey())).toList());
     assertFalse(localTimeline.isEmpty());assertTrue(localTimeline.size()<=30);
     SwingUtilities.invokeAndWait(()->{view.get().setCuts(browsable);view.get().selectExecution(choice.getKey());
      assertEquals(choice.getKey(),ExecutionScopeViews.selected(view.get().selectedCut()));
      assertTrue(view.get().selectedCut().evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).allMatch(r->r.context().equals(choice.getKey())));
      LiveGoalSummaryTest.renderPanel(view.get(),"execution-"+choice.getValue().replaceAll("[^a-zA-Z0-9]","_")+".png");
     });
     scopedReceipts.add(Map.of("context",choice.getKey(),"label",choice.getValue(),"entities",entities,"root",scopedRoots.getFirst().status(),"milestones",localTimeline.size(),"boundaries",browsable.size(),"scopedChecks",scopeChecks,"participation",participation));
    }
    SwingUtilities.invokeAndWait(()->view.get().selectExecution(""));
    Files.write(evidence.resolve("execution-scopes.json"),CanonicalJson.encode(scopedReceipts));

    assertTrue(pinned.selectedRequirements(),"Selected-requirement policy must come from the production runtime result");
    var declaredIds=new HashSet<String>();definitions.rows(List.of()).forEach(r->declaredIds.add(r.id()));
    assertTrue(pinned.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).allMatch(r->declaredIds.contains(r.element())),"Resources cannot become hidden Goal/Task UNKNOWN requirements");
    for(String clause:List.of("ObservedBidEvidence","ConfirmedSchemeOrder"))assertTrue(result.semantic().constraints().stream().anyMatch(c->c.id().endsWith("::"+clause)&&c.kind().equals("INVARIANT")&&Set.of("PASS","FAIL").contains(c.verdict().name())),"Actual source observer contract must execute, not stay permanently SKIPPED: "+clause);
    var finalVerdict=new java.util.concurrent.atomic.AtomicReference<String>();SwingUtilities.invokeAndWait(()->{view.get().setCuts(List.of(pinned));finalVerdict.set(DetachedAuthoredWorkflowTest.find(view.get(),"observed-verdict",JLabel.class).getText());});
    Files.write(evidence.resolve("selected-requirements.json"),CanonicalJson.encode(Map.of("verdict",finalVerdict.get(),"requirements",pinned.requirements(),"overallCoverage",pinned.coverage(),"selectedRequirements",pinned.selectedRequirements(),"goalStatuses",rows.stream().map(r->Map.of("id",r.id(),"status",r.status())).toList())));

    assertEquals("FULFILLED",rows.stream().filter(r->r.id().equals("O_bid")).findFirst().orElseThrow().status(),"Declared ANY must certify the organization using a real successful participant, not require all participants");
    var core=CanonicalJson.object(result.semantic().observation().get("originalEngine"));
    var originalRoots=((List<?>)core.get("contexts")).stream().map(CanonicalJson::object).flatMap(c->CanonicalJson.object(c.getOrDefault("occurrences",Map.of())).values().stream()).map(CanonicalJson::object).filter(o->"O_root".equals(o.get("sourceElement"))&&"SOURCE_ELEMENT".equals(o.get("expansionKind"))).toList();
    assertFalse(originalRoots.isEmpty());var rootStatus=rows.stream().filter(r->r.id().equals("O_root")).findFirst().orElseThrow().status();
    if(originalRoots.stream().allMatch(o->"FULFILLED".equals(o.get("aggregateStatus"))))assertEquals("FULFILLED",rootStatus,"All certified branches of the real source root must qualify");
    else assertNotEquals("FULFILLED",rootStatus,"Winner cannot fabricate an original root completion when a required stop checkpoint is missing");
    var taskDiagnostics=facade.runtimeVerificationHistory().stream().filter(r->r.semantic()!=null).flatMap(r->r.semantic().constraints().stream()).filter(c->c.kind().startsWith("TASK_")).map(c->Map.of("id",c.id(),"kind",c.kind(),"verdict",c.verdict().name(),"diagnostic",c.diagnostic(),"invocation",c.invocation(),"witness",c.witness())).distinct().toList();
    Files.write(evidence.resolve("task-checkpoint-diagnostics.json"),CanonicalJson.encode(taskDiagnostics));
    var milestones=BusinessTimeline.summarize(browsable);
    assertTrue(milestones.size()<=30,"This bounded 2-auction/4-participant source must not generate hundreds of domain milestones: "+milestones.size());
    assertTrue(milestones.size()*4<browsable.size(),"Full audit must remain available independently of the small business projection");
    assertFalse(pinned.executionMilestones());
    assertTrue(milestones.stream().allMatch(c->!c.businessKey().isBlank()&&!c.businessState().isBlank()),"DOMAIN milestones must have admitted selected CSL projections");
    assertTrue(milestones.stream().noneMatch(c->c.businessState().contains("!new ")||c.businessState().contains("!insert ")),"Default state preview must not be raw infrastructure SOIL");
    var terminalRows=pinned.evaluations().stream().filter(c->c.kind().equals("GOAL_TASK")&&c.status().equals("FAILED")).toList();
    assertTrue(terminalRows.stream().allMatch(c->c.quality().equals("KNOWN_HISTORY")&&c.evidence().contains("CERTIFIED_TERMINAL_EXECUTION/1.0")&&!c.originalStatus().equals("FULFILLED")),"FAILED must reference a terminal receipt, never raw history or a success certificate");
    assertTrue(terminalRows.isEmpty(),"Source deliberate decline is an explicitly accepted attempt; unexpected failures must be investigated, not auto-violated");
    assertTrue(result.semantic().constraints().stream().noneMatch(c->c.kind().equals("EXECUTION_OUTCOME")&&c.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL));
    assertEquals("FULFILLED",rows.stream().filter(r->r.id().equals("O_bid_accepted")).findFirst().orElseThrow().status(),"Actual Artifact acceptance is required independently of attempt completion");
    assertEquals("FULFILLED",rootStatus,"Both completed source auctions must have certified original-engine roots");
    boolean nativeViolation=result.semantic().constraints().stream().anyMatch(c->Set.of("INVARIANT","PRE","POST","INVOCATION_CONFORMANCE").contains(c.kind())&&c.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL);
    assertEquals(nativeViolation?"VIOLATION":"COMPLETE_OBSERVED",finalVerdict.get(),"No failures may be hidden; only selected original root plus mandatory native contracts define completion");
    Files.write(evidence.resolve("terminal-outcomes.json"),CanonicalJson.encode(Map.of("failedOccurrences",terminalRows.size(),"rows",terminalRows.stream().map(c->Map.of("element",c.element(),"status",c.status(),"original",c.originalStatus(),"evidence",c.evidence())).toList(),"note","Declared attempt semantics v3. Actual simulated .fail is recorded separately, never fabricated as accepted Artifact bid. Deterministic XmlTaskCheckpointsTest covers both accepted and unrelated failures.")));

    var delays=facade.runtimeVerificationHistory().stream().filter(r->r.semantic()!=null).mapToLong(r->Math.max(0,java.time.Duration.between(r.observedAt(),r.verifiedAt()).toMillis())).sorted().toArray();
    Files.write(evidence.resolve("essential-states.json"),CanonicalJson.encode(Map.of("milestones",milestones.size(),"auditBoundaries",browsable.size(),"completionDelayMaxMs",delays.length==0?0:delays[delays.length-1],"completionDelayLatestVerifiedMs",Math.max(0,java.time.Duration.between(result.observedAt(),result.verifiedAt()).toMillis()),"pinnedResultHash",result.resultHash(),"presentationMode","DOMAIN","optionalBeliefsAcquired",false,"latestVerifiedEventSource",result.sourceId(),"latestVerifiedEventId",result.eventId())));

    assertTrue(result.semantic().constraints().stream().anyMatch(c->c.kind().startsWith("INVARIANT")&&Set.of("PASS","FAIL","SKIPPED").contains(c.verdict().name())),"Native authored requirements must actually be classified");
    Files.write(evidence.resolve("goal-summary.json"),CanonicalJson.encode(rows.stream().map(r->Map.of("id",r.id(),"label",r.label(),"status",r.status(),"detail",r.detail())).toList()));
    var checks=result.semantic().constraints().stream().filter(c->c.contextClass().equals("AuctionObligation")||c.contextClass().equals("AuctionMonitor")).map(c->Map.of("id",c.id(),"object",c.contextObject(),"expression",c.expression(),"verdict",c.verdict().name(),"diagnostic",c.diagnostic(),"witness",c.witness())).toList();Files.write(evidence.resolve("observer-checks.json"),CanonicalJson.encode(checks));
    assertTrue(browsable.stream().anyMatch(c->c.evaluations().stream().anyMatch(r->r.originalStatus().equals("FULFILLED"))),"Original engine history must be visible, separately from current marker availability");
    var historicalProofs=browsable.stream().flatMap(c->c.evaluations().stream()).filter(r->r.kind().equals("GOAL_TASK")&&r.status().equals("FULFILLED")&&Set.of("KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(r.quality())).toList();
    assertFalse(historicalProofs.isEmpty(),"Real completed Auction steps must retain certified execution after the current marker disappears");
    assertTrue(historicalProofs.stream().allMatch(r->r.evidence().contains("CERTIFIED_EXECUTION/1.0")),"Held fulfillment must reference actual persisted runtime certificates");
    Files.write(evidence.resolve("certified-execution.json"),CanonicalJson.encode(Map.of("policy","CERTIFIED_EXECUTION/1.0","qualifiedHistoricalRows",historicalProofs.size(),"samples",historicalProofs.stream().limit(12).map(r->Map.of("element",r.element(),"quality",r.quality(),"status",r.status(),"evidence",r.evidence())).toList())));
    assertTrue(facade.runtimeHistoryPage(0,128).entries().stream().anyMatch(e->!e.stateCommands().isBlank()),"Actual frozen state commands must be persisted for browsing");
    SwingUtilities.invokeAndWait(()->{view.get().setCuts(browsable);var previous=DetachedAuthoredWorkflowTest.find(view.get(),"observed-previous",JButton.class);assertNotNull(previous);assertTrue(previous.isEnabled());previous.doClick();assertNotNull(view.get().selectedCut());assertEquals(expected,DetachedAuthoredWorkflowTest.find(view.get(),"live-goal-status",JTable.class).getRowCount());});
    facade.exportRuntimeReplay(evidence.resolve("replay"));
    facade.stopOwnedProducer();assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));assertSame(system,session.system());assertArrayEquals(source,Files.readAllBytes(project));
    var replay=facade.replayRuntime(evidence.resolve("replay"));assertTrue(replay.complete(),replay.toString());Files.write(evidence.resolve("replay-receipt.json"),CanonicalJson.encode(Map.of("status","PASS","entries",replay.checkedEntries())));
    Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("status","PASS","pid",pid,"sameMSystem",true,"fixedGoalTaskRows",expected,"samples",samples,"winnerObservedForTwoArtifacts",true,"coverage",result.coverage(),"domainFailures",result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL),"scope","Audit, not assertion that full normative profile is satisfied")));
   }finally{diagnostics(facade,evidence);
    if(!Files.exists(evidence.resolve("receipt.json"))&&coordinatorOrNull(facade)!=null)facade.exportRuntimeReplay(evidence.resolve("diagnostic-replay"));
    var owned=facade.ownedProducerStatus();
    Path preparedRoot=checkout.resolve("use-plugin/target/owned-runtime");
    if(Files.isDirectory(preparedRoot))try(var dirs=Files.list(preparedRoot)){for(var dir:dirs.toList()){var manifest=dir.resolve("prepared.json");if(!Files.isRegularFile(manifest)||!Files.readString(manifest).contains(owned.runId()))continue;for(String name:List.of("producer.stdout.log","producer.stderr.log"))if(Files.isRegularFile(dir.resolve(name)))Files.copy(dir.resolve(name),evidence.resolve(name),StandardCopyOption.REPLACE_EXISTING);}}
    facade.stopOwnedProducer();if(pid!=0)assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));System.out.println("AUTHORED_AUCTION_RESULTS_EVIDENCE="+evidence);}
  }
 }
 private static boolean stopReceiptsComplete(DefaultJaCaMoFacade facade){
  var history=facade.runtimeVerificationHistory();
  for(int i=history.size()-1;i>=0;i--){var r=history.get(i);if(r.semantic()==null||!(r.semantic().observation().get("originalEngine") instanceof Map<?,?> value))continue;var core=CanonicalJson.object(value);if(!"PINNED_NATIVE_CUT".equals(core.get("evaluated")))continue;
   var contexts=((List<?>)core.getOrDefault("contexts",List.of())).stream().map(CanonicalJson::object).toList();
   if(contexts.size()!=2)continue;boolean certified=true;
   for(var context:contexts) {
    var rows=CanonicalJson.object(context.getOrDefault("occurrences",Map.of())).values().stream().map(CanonicalJson::object).filter(o->"SOURCE_ELEMENT".equals(o.get("expansionKind"))).toList();
    var owners=rows.stream().filter(o->"A_decide".equals(o.get("sourceElement"))).toList();
    if(owners.size()!=1){certified=false;break;}
    // Tasks now belong to the Agent capability. Only the same physical player
    // holding the Auctioneer responsibility can supply its stop certificate.
    var stops=rows.stream().filter(o->"A_decide_t2".equals(o.get("sourceElement"))&&Objects.equals(o.get("player"),owners.getFirst().get("player"))).toList();
    if(stops.size()!=1||!"FULFILLED".equals(stops.getFirst().get("aggregateStatus"))||!Set.of("KNOWN","KNOWN_HISTORY").contains(stops.getFirst().get("executionQuality")))certified=false;
   }
   if(certified)return true;
  }return false;
 }
 private static void diagnostics(DefaultJaCaMoFacade facade,Path evidence)throws Exception{
  var c=coordinatorOrNull(facade);if(c==null)return;var latest=facade.runtimeVerificationResult();if(latest!=null)Files.write(evidence.resolve("last-result.json"),CanonicalJson.encode(latest.toMap()));
  var state=c.read(()->c.system().state().allObjects().stream().filter(o->Set.of("Auction","AuctionMonitor","AuctionObligation").contains(o.cls().name())).map(o->{var fields=new TreeMap<String,String>();o.state(c.system().state()).attributeValueMap().forEach((a,v)->fields.put(a.name(),v.toString()));return Map.of("class",o.cls().name(),"object",o.name(),"fields",fields);}).toList());Files.write(evidence.resolve("last-state.json"),CanonicalJson.encode(state));
  Files.write(evidence.resolve("transport.json"),CanonicalJson.encode(Map.of("status",facade.status(),"authority",facade.authorityStatus().toString(),"journalGap",c.journal().hasGap(),"journalDiagnostic",c.journal().diagnostic(),"journalBytes",c.journal().persistedBytes(),"journalQuota",c.journal().maxBytes(),"performance",c.performanceMetrics())));
 }
 private static RuntimeVerificationCoordinator coordinator(DefaultJaCaMoFacade facade)throws Exception {return Objects.requireNonNull(coordinatorOrNull(facade),"An active coordinator is required");}
 private static RuntimeVerificationCoordinator coordinatorOrNull(DefaultJaCaMoFacade facade)throws Exception {var field=DefaultJaCaMoFacade.class.getDeclaredField("nativeWorkspace");field.setAccessible(true);var workspace=field.get(facade);if(workspace==null)return null;var projector=workspace.getClass().getDeclaredField("runtimeProjector");projector.setAccessible(true);var active=(NativeRuntimeProjector)projector.get(workspace);return active==null?null:active.coordinator();}
 private static <T>T systemRead(DefaultJaCaMoFacade facade,java.util.concurrent.Callable<T> read)throws Exception{return coordinator(facade).read(read);}

 private static RuntimeVerificationResult completedObserverCut(DefaultJaCaMoFacade facade,RuntimeVerificationCoordinator coordinator)throws Exception {
  var result=coordinator.awaitEvaluation(coordinator.manualVerify(),60000);
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
  // Manual snapshot may be partial while already admitted source events are still
  // being verified. Wait for a completed current cut, never specifically for PASS.
  while(System.nanoTime()<deadline){
   var snapshot=facade.verificationSnapshot();
   if(result!=null&&result.semantic()!=null&&snapshot.currentVersion()==result.stateVersion()
      &&observerEvaluated(result,"ObservedBidEvidence")&&observerEvaluated(result,"ConfirmedSchemeOrder"))return result;
   Thread.sleep(200);result=facade.runtimeVerificationResult();
  }
  throw new AssertionError("No completed current cut evaluated both source observer contracts within deadline");
 }
 private static boolean observerEvaluated(RuntimeVerificationResult result,String name){return result.semantic().constraints().stream().anyMatch(c->c.id().endsWith("::"+name)&&c.kind().equals("INVARIANT")&&Set.of("PASS","FAIL").contains(c.verdict().name()));}
}

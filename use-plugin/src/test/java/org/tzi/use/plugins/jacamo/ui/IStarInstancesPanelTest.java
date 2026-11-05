package org.tzi.use.plugins.jacamo.ui;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.IStarInstancesPanel;
import org.tzi.use.plugins.jacamo.ui.use4goal.istar.IStarView;
import static org.junit.jupiter.api.Assertions.*;

class IStarInstancesPanelTest {
    private final Set<String> introduced=new HashSet<>();
    @org.junit.jupiter.api.BeforeEach void useBundledDiagramDefaults()throws Exception {
        var properties=new Properties();try(var input=getClass().getResourceAsStream("/etc/use.properties")){properties.load(input);}
        for(String key:properties.stringPropertyNames())if(System.getProperty(key)==null){introduced.add(key);System.setProperty(key,properties.getProperty(key));}
    }
    @org.junit.jupiter.api.AfterEach void restoreDefaults(){introduced.forEach(System::clearProperty);}

    static Cut recorded()throws Exception {
        byte[] bytes;try(var input=new java.util.zip.GZIPInputStream(IStarInstancesPanelTest.class.getResourceAsStream("/semantics/checkpoint-display-user-result-20261105.json.gz"))){bytes=input.readAllBytes();}
        var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(CanonicalJson.decode(bytes)));
        var snapshot=new VerificationSnapshot(result.stateVersion(),result,null);
        var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "frozen result";}public VerificationSnapshot verificationSnapshot(){return snapshot;}});
        return presenter.captureCurrent().getFirst();
    }
    static <T>List<T> components(Container root,Class<T> type){var found=new ArrayList<T>();for(var c:root.getComponents()){if(type.isInstance(c))found.add(type.cast(c));if(c instanceof Container child)found.addAll(components(child,type));}return found;}

    @Test void actualTwoInstancesReuseAuthoredGraphAndIgnoreHistoricalNavigation()throws Exception {
        var cut=recorded();
        var goals=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(Files.readString(Path.of("examples/auction/auction.istar")),"current-authored",org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        String before=org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(goals);
        SwingUtilities.invokeAndWait(()->{
            var instances=new IStarInstancesPanel(goals);instances.setActive(true);instances.update(List.of(cut));
            assertEquals(2,components(instances,IStarView.class).size());
            var labels=components(instances,JLabel.class).stream().map(JLabel::getText).toList();assertTrue(labels.contains("Current admitted instances: 2"));
            var selector=DetachedAuthoredWorkflowTest.find(instances,"istar-instance-selector",JComboBox.class);
            assertEquals(3,selector.getItemCount());assertEquals(ExecutionScopeViews.ALL,selector.getSelectedItem());
            assertNull(VerifiedCheckpointControlsTest.find(instances,"observed-state-selector"));
            assertNull(VerifiedCheckpointControlsTest.find(instances,"iscn-context-selector"));
            var views=components(instances,IStarView.class);instances.update(List.of(cut));assertEquals(views,components(instances,IStarView.class));
            var originalOrder=java.util.stream.IntStream.range(0,selector.getItemCount()).mapToObj(selector::getItemAt).toList();
            var evidence=new TreeMap<String,Object>(cut.visualEvidence());var core=new TreeMap<String,Object>(ExecutionScopeViews.object(evidence.get("originalEngine")));
            var reversed=new ArrayList<>(ExecutionScopeViews.contexts(cut));Collections.reverse(reversed);core.put("contexts",reversed);evidence.put("originalEngine",core);
            var update=new Cut(cut.id()+":latest",cut.trace(),cut.state(),cut.evaluations(),cut.coverage(),cut.freshness(),cut.failed(),cut.businessKey(),cut.requirements(),cut.businessState(),cut.executionMilestones(),cut.selectedRequirements(),cut.businessPopulationEmpty(),evidence);
            instances.update(List.of(cut,update));assertEquals(originalOrder,java.util.stream.IntStream.range(0,selector.getItemCount()).mapToObj(selector::getItemAt).toList(),"Execution labels must not swap when the native context iteration order changes");
            selector.setSelectedIndex(1);assertEquals(1,components(instances,IStarView.class).size());selector.setSelectedItem(ExecutionScopeViews.ALL);assertEquals(2,components(instances,IStarView.class).size());
            var history=ObservedConformancePanel.liveResults(goals);history.setCuts(List.of(new Cut("older","older","older",List.of(),"COMPLETE","CURRENT_OBSERVED",false),cut));
            DetachedAuthoredWorkflowTest.find(history,"observed-previous",JButton.class).doClick();
            assertEquals(update.id(),instances.getClientProperty("accepted-cut"));assertEquals(2,components(instances,IStarView.class).size());
            instances.setActive(false);instances.setActive(true);assertEquals(2,components(instances,IStarView.class).size());
            instances.dispose();assertNull(instances.getClientProperty("accepted-cut"));assertTrue(components(instances,IStarView.class).isEmpty());
        });
        assertEquals(before,org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(goals));
    }

    @Test void anotherAuthoredCaseUsesItsOwnEntitiesWithoutAuctionNamesOrCounts()throws Exception {
        var goals=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Shipping { role Worker { goal Done : Achieve } }").model();
        var cut=ExecutionScopeViewsTest.cut("shipping","packed","sent",List.of(ExecutionScopeViewsTest.goal("one","PENDING"),ExecutionScopeViewsTest.goal("two","FULFILLED")));
        SwingUtilities.invokeAndWait(()->{
            var panel=new IStarInstancesPanel(goals);panel.setActive(true);panel.update(List.of(cut));assertEquals(2,components(panel,IStarView.class).size());
            var selector=DetachedAuthoredWorkflowTest.find(panel,"istar-instance-selector",JComboBox.class);selector.setSelectedItem("two");assertEquals(1,components(panel,IStarView.class).size());
            panel.update(List.of());assertTrue(components(panel,IStarView.class).isEmpty());
        });
    }
}

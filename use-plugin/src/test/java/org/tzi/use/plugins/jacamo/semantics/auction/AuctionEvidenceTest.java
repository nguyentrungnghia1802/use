package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import javax.swing.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.SemanticEvidencePanel;
import static org.junit.jupiter.api.Assertions.*;

class AuctionEvidenceTest {
    @Test void taskInvocationAndItsSourceActionNamespaceStaySeparateInTheSameReadOnlyPanel()throws Exception {
        try(var component=new AuctionTaskOclBindingsTest.Component("P_bid_t1")) {
            component.fixture.org.live.actor.doAction(component.fixture.schemes.getFirst(),new cartago.Op("goalAchieved","start"),5000);component.fixture.resync();
            var terminal=component.frame("SUCCEEDED");AuctionTaskRuntimeTest.apply(component,component.frame("ENABLED"));AuctionTaskRuntimeTest.apply(component,terminal);
            var result=AuctionTaskRuntimeTest.settled(component);
            SwingUtilities.invokeAndWait(()->{
                var panel=new SemanticEvidencePanel();panel.refresh(result);var tables=new ArrayList<JTable>();collect(panel,tables);
                var original=tables.stream().filter(t->t.getName().equals("semantic-original-history")).findFirst().orElseThrow();
                int player=column(original,"Player"),scheme=column(original,"Scheme");boolean exact=false,other=false;
                for(int row=0;row<original.getRowCount();row++)if(original.getValueAt(row,2).equals(component.task)&&original.getValueAt(row,player).equals(component.actor.player())) {
                    if(original.getValueAt(row,scheme).equals(component.scope.scheme())){assertEquals("FULFILLED",original.getValueAt(row,4));exact=true;}
                    else {assertNotEquals("FULFILLED",original.getValueAt(row,4));other=true;}
                }assertTrue(exact);assertTrue(other);
                var details=tables.stream().filter(t->t.getName().equals("semantic-case-evidence")).findFirst().orElseThrow();
                boolean found=false;for(int row=0;row<details.getRowCount();row++)if(details.getValueAt(row,0).equals("TASK_POST")) {
                    assertEquals(terminal.data().get("goalInvocation"),details.getValueAt(row,6));assertEquals(terminal.subject(),details.getValueAt(row,7));
                    assertTrue(details.getValueAt(row,8).toString().contains("JASON_ACTIONEXEC_IDENTITY_NOT_CARTAGO_OPID"));found=true;
                }assertTrue(found);
            });
        }
    }
    @Test void actualCaseEvidenceShowsScopePlayerOriginalCallsAndSourceTraceWithoutCreditingOtherPlayers()throws Exception {
        try(var fixture=new AuctionCastFixture(2)) {
            AuctionNativeOperationTest.invoke(fixture,0,fixture.org.live.actor,new cartago.Op("start","evidence service"),false);
            var result=fixture.projector.coordinator().awaitEvaluation(fixture.projector.coordinator().latest(),15000);
            SwingUtilities.invokeAndWait(()->{
                var panel=new SemanticEvidencePanel();panel.refresh(result);var tables=new ArrayList<JTable>();collect(panel,tables);
                var original=tables.stream().filter(t->t.getName().equals("semantic-original-history")).findFirst().orElseThrow();
                int scheme=column(original,"Scheme"),artifact=column(original,"Artifact"),player=column(original,"Player"),calls=column(original,"Original API calls"),source=column(original,"Source rule / cell");
                var scopes=new HashSet<Object>();var players=new HashSet<Object>();
                for(int row=0;row<original.getRowCount();row++) {
                    scopes.add(original.getValueAt(row,scheme));players.add(original.getValueAt(row,player));
                    assertFalse(Objects.toString(original.getValueAt(row,artifact),"").isBlank());
                    assertFalse(Objects.toString(original.getValueAt(row,calls),"").isBlank());
                    assertFalse(Objects.toString(original.getValueAt(row,source),"").isBlank());
                    assertNotEquals("FULFILLED",original.getValueAt(row,4),"No Jason steps occurred; an Artifact start cannot credit a player's Task");
                }
                assertEquals(2,scopes.size());assertTrue(players.size()>=3,players.toString());
                var details=tables.stream().filter(t->t.getName().equals("semantic-case-evidence")).findFirst().orElseThrow();
                assertTrue(details.getRowCount()>0);assertFalse(details.isCellEditable(0,0));
            });
        }
    }
    private static int column(JTable table,String name){for(int i=0;i<table.getColumnCount();i++)if(table.getColumnName(i).equals(name))return i;throw new AssertionError("Missing evidence column "+name);}
    private static void collect(java.awt.Container parent,List<JTable> tables){for(var child:parent.getComponents()){if(child instanceof JTable table)tables.add(table);if(child instanceof java.awt.Container container)collect(container,tables);}}
}

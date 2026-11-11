package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import cartago.*;
import jason.asSyntax.ASSyntax;
import ora4mas.nopl.JasonTermWrapper;
import org.junit.jupiter.api.Test;

class MoiseGoalObservationTest {
    private Object term(String source) throws Exception{return new JasonTermWrapper(ASSyntax.parseTerm(source));}
    private ArtifactInfo info(List<ArtifactObsProperty> properties) throws Exception {
        return new ArtifactInfo(null,new ArtifactId("s1",UUID.randomUUID(),"ora4mas.nopl.SchemeBoard",new WorkspaceId("/main/org"),null),
                List.of(),properties,List.of(),null);
    }
    @Test void publicObservablePreservesStatesAgentsAndArgumentsWithExactSchemeContext() throws Exception {
        var properties=new ArrayList<ArtifactObsProperty>();long id=0;
        for(String state:List.of("waiting","enabled","satisfied")) properties.add(new ArtifactObsProperty("goalState",++id,"goalState",
                term("s1"),term("g"+id),term("[bob,alice]"),term(id==3?"[alice]":"[]"),term(state)));
        properties.add(new ArtifactObsProperty("goalArgument",++id,"goalArgument",term("s1"),term("g1"),term("\"amount\""),term("23")));
        var info=info(properties);var cut=MoiseGoalObservation.decode(info,"s1");assertSame(info.getId(),cut.artifact());
        assertEquals(List.of("WAITING","ENABLED","SATISFIED"),cut.goals().stream().map(MoiseGoalObservation.Goal::state).toList());
        assertEquals(List.of("alice","bob"),cut.goals().getFirst().committed());assertEquals(List.of("alice"),cut.goals().getLast().achieved());
        assertEquals("23",cut.arguments().get("g1").get("amount"));
        assertThrows(IllegalStateException.class,()->MoiseGoalObservation.decode(info,"other-instance"));
    }
    @Test void unavailableStateAndMalformedOrDuplicatedEvidenceFailClosed() throws Exception {
        var unknown=new ArtifactObsProperty("goalState",1,"goalState",term("s1"),term("g"),term("[]"),term("[]"),term("impossible"));
        assertThrows(IllegalStateException.class,()->MoiseGoalObservation.decode(info(List.of(unknown)),"s1"));
        var valid=new ArtifactObsProperty("goalState",2,"goalState",term("s1"),term("g"),term("[]"),term("[]"),term("waiting"));
        assertThrows(IllegalStateException.class,()->MoiseGoalObservation.decode(info(List.of(valid,valid)),"s1"));
    }
    @Test void pinnedMoiseGoalArgumentsUseDirectJasonTermsNotStringGuesses() throws Exception {
        var argument=new ArtifactObsProperty("goalArgument",1,"goalArgument",ASSyntax.createAtom("s1"),
                ASSyntax.createAtom("root"),ASSyntax.createString("service"),ASSyntax.parseTerm("flight(athens,paris)"));
        assertEquals("flight(athens,paris)",MoiseGoalObservation.decode(info(List.of(argument)),"s1").arguments().get("root").get("service"));
        var guessed=new ArtifactObsProperty("goalArgument",2,"goalArgument","s1","root","service","flight(athens,paris)");
        assertThrows(IllegalStateException.class,()->MoiseGoalObservation.decode(info(List.of(guessed)),"s1"));
    }
}

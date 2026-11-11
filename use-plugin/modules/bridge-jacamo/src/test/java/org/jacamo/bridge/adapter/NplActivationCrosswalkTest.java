package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import jason.asSyntax.*;
import jason.asSemantics.Unifier;
import npl.*;
import org.junit.jupiter.api.Test;

class NplActivationCrosswalkTest {
    @Test void copiedActualPublicTerminalAnnotationsSupplyEpochsAndMissingAnnotationsCannotUseReceiptTime()throws Exception {
        for(String state:List.of("FULFILLED","UNFULFILLED","INACTIVE")) {
            var aim=ASSyntax.createLiteral("done",ASSyntax.createAtom("sch_a1"),ASSyntax.createAtom("bid"),ASSyntax.createAtom("alice"));
            var literal=ASSyntax.createLiteral("obligation",ASSyntax.createAtom("alice"),Literal.LTrue,aim,new TimeTerm(10000,"milliseconds"));
            var value=new NormInstance(literal,new Unifier(),new Norm("actual_goal_norm",literal,Literal.LTrue));value.setActive();var crosswalk=new NplActivationCrosswalk("terminal-run",4);
            var active=crosswalk.observe("board",value.copy(),"LISTENER_CREATED","ACTIVE");
            Thread.sleep(2);switch(state){case "FULFILLED"->value.setFulfilled();case "UNFULFILLED"->value.setUnfulfilled();case "INACTIVE"->value.setInactive();default->throw new AssertionError();}
            var terminal=crosswalk.observe("board",value.copy(),"LISTENER_TERMINAL",state);
            var proof=org.jacamo.bridge.contract.CanonicalJson.object(terminal.get("sourceLifecycle"));
            assertEquals("NPL_TIME_ANNOTATIONS/1.0",proof.get("version"));assertEquals("COMPLETE",proof.get("status"));
            var actual=(TimeTerm)((Structure)value.getAnnot(state.toLowerCase(java.util.Locale.ROOT))).getTerm(0);
            assertEquals(Long.toString(NplActivationCrosswalk.epoch(actual)),proof.get("terminalEpochMillis"));
            assertEquals(active.get("createdEpochMillis"),proof.get("createdEpochMillis"));assertEquals(active.get("activationKey"),terminal.get("activationKey"));
            assertEquals(true,org.jacamo.bridge.contract.CanonicalJson.object(proof.get("aimTerm")).get("supported"));
            value.delAnnot(value.getAnnot(state.toLowerCase(java.util.Locale.ROOT)));
            var missing=org.jacamo.bridge.contract.CanonicalJson.object(crosswalk.observe("board",value.copy(),"SNAPSHOT",state).get("sourceLifecycle"));
            assertEquals("INCOMPLETE",missing.get("status"));assertEquals("",missing.get("terminalEpochMillis"));
        }
    }
    @Test void actualTenSecondAndOneHourAnnotationsRemainAbsoluteAndNeverUsesItsOwnActivation() {
        long firstDeadline=0;
        for(long duration:List.of(10000L,3600000L,10000L)) {
            long before=System.currentTimeMillis();
            var literal=ASSyntax.createLiteral("obligation",ASSyntax.createAtom("alice"),Literal.LTrue,ASSyntax.createAtom("done"),new TimeTerm(duration,"milliseconds"));
            var value=new NormInstance(literal,new Unifier(),new Norm("n",literal,Literal.LTrue));value.setActive();long after=System.currentTimeMillis();
            var payload=new NplActivationCrosswalk("clock-run",2).observe("board",value.copy(),"LISTENER_CREATED","ACTIVE");
            long deadline=Long.parseLong((String)payload.get("deadline")),created=Long.parseLong((String)payload.get("createdEpochMillis"));
            assertTrue(created>=before&&created<=after);assertTrue(deadline>=before+duration&&deadline<=after+duration);
            assertEquals(NplSourceClockObservation.clockIdentity("clock-run",2),((Map<?,?>)payload.get("timeBasis")).get("clockIdentity"));
            if(duration==10000L){if(firstDeadline!=0)assertTrue(deadline>=firstDeadline);firstDeadline=deadline;}
        }
        assertThrows(IllegalArgumentException.class,()->NplActivationCrosswalk.epoch(new TimeTerm(0,"never")));
        var value=instance("obligation");value.setTerm(3,new TimeTerm(0,"never"));var payload=new NplActivationCrosswalk().observe("board",value,"SNAPSHOT","ACTIVE");
        assertEquals("",payload.get("deadline"));assertEquals("NPL_TIME_NOT_EXACTLY_REPRESENTABLE",payload.get("deadlineTimeDiagnostic"));
    }
    static NormInstance instance(String modality) {
        var literal=ASSyntax.createLiteral(modality,ASSyntax.createAtom("alice"),Literal.LTrue,ASSyntax.createAtom("done"),new TimeTerm(new Date(System.currentTimeMillis()+10000)));var norm=new Norm("n",literal,Literal.LTrue);var value=new NormInstance(literal,new Unifier(),norm);value.setActive();return value;
    }
    @Test void copiedTerminalInstancesMatchPublicCreatedCrosswalkAndDuplicateCreationIsAmbiguous() {
        var value=instance("obligation");var registry=new NplActivationCrosswalk();String uuid=UUID.randomUUID().toString();var created=registry.observe(uuid,value.copy(),"LISTENER_CREATED","ACTIVE");assertEquals("BOUND_PUBLIC_CREATED_ANNOTATION",created.get("activationIdentityStatus"));
        var terminal=value.copy();terminal.setFulfilled();var fulfilled=registry.observe(uuid,terminal,"LISTENER_TERMINAL","FULFILLED");assertEquals(created.get("activationKey"),fulfilled.get("activationKey"));assertEquals("CREATED_EVENT_OBSERVED",fulfilled.get("history"));assertEquals("ABSOLUTE_NPL_TIME",fulfilled.get("deadlineKind"));
        var ambiguous=registry.observe(uuid,value.copy(),"LISTENER_CREATED","ACTIVE");assertEquals("AMBIGUOUS_NORM_INSTANCE",ambiguous.get("activationIdentityStatus"));assertNotEquals(created.get("activationKey"),new NplActivationCrosswalk().observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE").get("activationKey"));
    }
    @Test void permissionIsAnObservedModalityAndMissingCreationOrStateDeadlineIsExplicit() {
        var value=instance("permission");var registry=new NplActivationCrosswalk();var observed=registry.observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE");assertEquals("PERMISSION",observed.get("modality"));assertEquals("ACTIVATION_HISTORY_UNPROVEN",observed.get("history"));
        value.setTerm(3,ASSyntax.createAtom("finished"));observed=registry.observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE");assertEquals("STATE",observed.get("deadlineKind"));assertEquals("",observed.get("deadline"));value.delAnnot(value.getAnnot("created"));assertEquals("ACTIVATION_ORIGIN_UNAVAILABLE",registry.observe(UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE").get("activationIdentityStatus"));
    }
}

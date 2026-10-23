package org.jacamo.bridge.adapter;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.runtime.Settings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Official 3.3.2 semantics, with no bridge or observer installed. Not a HouseBuilding live acceptance substitute. */
class JasonWaitSemanticsTest {
    @Test void eventWaitDoesNotTreatAnAlreadyPresentBeliefAsANewEvent()throws Exception {
        var ctx=context("{+formationStatus(ok)[artifact_id(g)]}");
        assertEquals(true,new jason.stdlib.wait().execute(ctx.ts,new Unifier(),new Term[]{ASSyntax.parseTerm(ctx.argument)}));
        assertTrue(ctx.ts.getC().getPendingIntentions().containsValue(ctx.intention));
        assertTrue(ctx.ts.getC().getEvents().isEmpty());assertTrue(ctx.ts.getAg().believes(ASSyntax.parseLiteral("formationStatus(ok)[artifact_id(g)]"),new Unifier()));
        ctx.ts.getC().dropIntention(ctx.intention);
    }

    @Test void logicalConditionWaitChecksAlreadyTrueBeliefBeforeRegisteringAWait()throws Exception {
        var ctx=context("formationStatus(ok)[artifact_id(g)]");
        assertEquals(true,new jason.stdlib.wait().execute(ctx.ts,new Unifier(),new Term[]{ASSyntax.parseTerm(ctx.argument)}));
        assertTrue(ctx.ts.getC().getPendingIntentions().isEmpty());assertTrue(ctx.ts.getC().hasRunningIntention(ctx.intention));
        assertEquals("finished",ctx.intention.peek().getCurrentStep().toString());
    }

    private record Context(TransitionSystem ts,Intention intention,String argument) { }
    private static final class SelectedCircumstance extends Circumstance {
        void select(Intention intention){SI=intention;}
    }
    private static Context context(String argument)throws Exception {
        var agent=new Agent();agent.initAg();agent.getBB().add(ASSyntax.parseLiteral("formationStatus(ok)[artifact_id(g)]"));
        var c=new SelectedCircumstance();var ts=new TransitionSystem(agent,c,new Settings(),new jason.architecture.AgArch());
        var plan=ASSyntax.parsePlan("+!probe <- .wait("+argument+"); finished.");var intention=new Intention();
        intention.push(new IntendedMeans(new Option(plan,new Unifier()),plan.getTrigger()));c.select(intention);
        return new Context(ts,intention,argument);
    }
}

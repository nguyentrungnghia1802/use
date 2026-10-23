package org.jacamo.bridge.adapter;

import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.ASSyntax;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JasonIntentionViewsTest {
    @Test void derivedStateMatchesOfficialJasonForEveryPlaceAndSuspensionFlag() {
        var i=new Intention();
        for(var place:Circumstance.IntentionPlace.values())for(boolean suspended:List.of(false,true)) {
            i.setPlace(place);i.setSuspended(suspended);
            assertEquals(i.getStateBasedOnPlace(),new JasonIntentionViews.View(i,place).state(),place+":"+suspended);
        }
    }

    @Test void allContainersAreObservedWithoutChangingObjectsOrRemovingQueuedWork()throws Exception {
        var c=new Circumstance();Intention eventInt=intention(),pendingEventInt=intention(),actionInt=intention(),waitingInt=intention(),runningInt=intention();
        var event=new Event(ASSyntax.parseTrigger("+!event"),eventInt);
        c.addEvent(event);c.getPendingEvents().put("pending",new Event(ASSyntax.parseTrigger("+!pending"),pendingEventInt));
        c.getPendingActions().put(actionInt.getId(),new ActionExec(ASSyntax.parseLiteral("act"),actionInt));
        c.addPendingIntention("waiting",waitingInt);c.addRunningIntention(runningInt);
        var expected=Map.of(eventInt,Circumstance.IntentionPlace.EventQueue,pendingEventInt,Circumstance.IntentionPlace.PendingEvents,
                actionInt,Circumstance.IntentionPlace.PendingActions,waitingInt,Circumstance.IntentionPlace.PendingIntentions,
                runningInt,Circumstance.IntentionPlace.RunningIntentions);
        expected.keySet().forEach(i->i.setPlace(Circumstance.IntentionPlace.None));
        var views=JasonIntentionViews.capture(c);
        assertEquals(expected.size(),views.size());views.forEach(v->{assertEquals(expected.get(v.intention()),v.place());assertEquals(Circumstance.IntentionPlace.None,v.intention().getPlace());});
        assertEquals(List.of(event),List.copyOf(c.getEvents()));assertEquals(1,c.getPendingEvents().size());
        assertEquals(1,c.getPendingActions().size());assertSame(waitingInt,c.getPendingIntentions().get("waiting"));assertTrue(c.hasRunningIntention(runningInt));
        assertEquals(views,JasonIntentionViews.capture(c));assertThrows(UnsupportedOperationException.class,views::clear);
    }

    @Test void selectedAliasesAreReportedOnceWithOfficialSelectedEventPriority()throws Exception {
        var c=new SelectedCircumstance();var i=intention();c.addRunningIntention(i);
        c.select(new Event(ASSyntax.parseTrigger("+!work"),i),i);i.setPlace(Circumstance.IntentionPlace.None);
        var views=JasonIntentionViews.capture(c);assertEquals(1,views.size());
        assertSame(i,views.getFirst().intention());assertEquals(Circumstance.IntentionPlace.SelectedEvent,views.getFirst().place());
        assertEquals(Circumstance.IntentionPlace.None,i.getPlace());assertTrue(c.hasRunningIntention(i));
    }

    @Test void atomicRunningIntentionIsIncludedWithoutBeingRemovedOrRetagged()throws Exception {
        var plan=ASSyntax.parsePlan("@p[atomic] +!work <- act.");var i=new Intention();
        i.push(new IntendedMeans(new Option(plan,new Unifier()),plan.getTrigger()));assertTrue(i.isAtomic());
        var c=new Circumstance();c.addRunningIntention(i);i.setPlace(Circumstance.IntentionPlace.None);
        var views=JasonIntentionViews.capture(c);assertEquals(1,views.size());assertSame(i,views.getFirst().intention());
        assertEquals(Circumstance.IntentionPlace.RunningIntentions,views.getFirst().place());assertTrue(c.hasRunningIntention(i));
        assertEquals(Circumstance.IntentionPlace.None,i.getPlace());
    }

    private static final class SelectedCircumstance extends Circumstance {
        void select(Event event,Intention intention){SE=event;SI=intention;}
    }
    private static Intention intention()throws Exception {
        var plan=ASSyntax.parsePlan("+!work <- act.");var i=new Intention();
        i.push(new IntendedMeans(new Option(plan,new Unifier()),plan.getTrigger()));return i;
    }
}

package org.jacamo.bridge.adapter;

import java.util.*;
import jason.asSemantics.Circumstance;
import jason.asSemantics.Circumstance.IntentionPlace;
import jason.asSemantics.Intention;

/** Read-only container views. Jason 3.3.2 getAllIntentions writes live place metadata. */
final class JasonIntentionViews {
    private JasonIntentionViews() { }

    record View(Intention intention, IntentionPlace place) {
        // Pinned adapter for Intention.getStateBasedOnPlace, parity-tested for
        // every official place/suspension combination. No interpreter object is
        // cloned or created: Intention constructors also advance source IDs.
        Intention.State state() {
            return switch(place) {
                case PendingActions -> Intention.State.waiting;
                case PendingEvents -> Intention.State.suspended;
                case PendingIntentions -> intention.isSuspended() ? Intention.State.suspended : Intention.State.waiting;
                case SelectedEvent, SelectedIntention, EventQueue, RunningIntentions -> Intention.State.running;
                case None -> Intention.State.undefined;
            };
        }
    }

    static List<View> capture(Circumstance c) {
        if(c==null)return List.of();
        var rows=new LinkedHashMap<Intention,View>();
        var selected=c.getSelectedEvent();
        add(rows,selected==null?null:selected.getIntention(),IntentionPlace.SelectedEvent);
        add(rows,c.getSelectedIntention(),IntentionPlace.SelectedIntention);
        var events=c.getEventsPlusAtomic();
        while(events.hasNext())add(rows,events.next().getIntention(),IntentionPlace.EventQueue);
        c.getPendingEvents().values().forEach(e->add(rows,e.getIntention(),IntentionPlace.PendingEvents));
        c.getPendingActions().values().forEach(a->add(rows,a.getIntention(),IntentionPlace.PendingActions));
        c.getPendingIntentions().values().forEach(i->add(rows,i,IntentionPlace.PendingIntentions));
        var running=c.getRunningIntentionsPlusAtomic();
        while(running.hasNext())add(rows,running.next(),IntentionPlace.RunningIntentions);
        return List.copyOf(rows.values());
    }

    private static void add(Map<Intention,View> rows,Intention intention,IntentionPlace place) {
        if(intention!=null)rows.putIfAbsent(intention,new View(intention,place));
    }
}

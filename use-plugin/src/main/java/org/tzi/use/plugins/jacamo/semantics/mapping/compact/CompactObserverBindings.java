package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;

/** Versioned framework reductions; argument strings are admitted source symbols.
 * No OCL bodies, table language, application recipes or desired verdicts. */
public final class CompactObserverBindings {
    public enum Kind { SELF, WORKSPACE, FIRST_SUCCESS, FIRST_STATE, COMMITTED_AT_FIRST_STATE, DONE_PLAYERS, ACTION_PLAYERS, ADMIN_SATISFIED, NOW, NPL_GOAL, NPL_ACTIVATED, NPL_DEADLINE, NPL_ENDED, NPL_UNFULFILLED, NPL_MONITOR }
    public record Binding(Kind kind,List<String> arguments) {public Binding{arguments=List.copyOf(arguments);}}
    public static Binding parse(CompactMappingSpecification.ObserverView view,CompactMappingSpecification.ObserverView.ObserverField field) {
        var parts=field.origin().split(":",-1);Kind kind;int count;String type;
        switch(parts[0]) {
            case "SELF_REFERENCE/1.0"->{kind=Kind.SELF;count=1;type=view.name();}
            case "WORKSPACE_PATH/1.0"->{kind=Kind.WORKSPACE;count=1;type="String";}
            case "NPL_RELATIVE_NOW/1.0"->{kind=Kind.NOW;count=1;type="Integer";}
            case "NPL_GOAL/1.0"->{kind=Kind.NPL_GOAL;count=1;type="String";}
            case "NPL_ACTIVATED_AT/1.0"->{kind=Kind.NPL_ACTIVATED;count=1;type="Integer";}
            case "NPL_DEADLINE_AT/1.0"->{kind=Kind.NPL_DEADLINE;count=1;type="Integer";}
            case "NPL_ENDED_AT/1.0"->{kind=Kind.NPL_ENDED;count=1;type="Integer";}
            case "NPL_REPORTED_UNFULFILLED/1.0"->{kind=Kind.NPL_UNFULFILLED;count=1;type="Boolean";}
            case "NPL_SCHEME_VIEW_REFERENCE/1.0"->{kind=Kind.NPL_MONITOR;count=2;type=field.type();}
            case "CARTAGO_FIRST_SUCCESS_SEQUENCE/1.0"->{kind=Kind.FIRST_SUCCESS;count=2;type="Integer";}
            case "MOISE_FIRST_STATE_SEQUENCE/1.0"->{kind=Kind.FIRST_STATE;count=3;type="Integer";}
            case "MOISE_FIRST_STATE_COMMITTED_PLAYERS/1.0"->{kind=Kind.COMMITTED_AT_FIRST_STATE;count=3;type="Set(String)";}
            case "MOISE_DONE_PLAYERS/1.0"->{kind=Kind.DONE_PLAYERS;count=2;type="Set(String)";}
            case "JASON_SUCCESSFUL_ACTION_PLAYERS/1.0"->{kind=Kind.ACTION_PLAYERS;count=3;type="Set(String)";}
            case "MOISE_ADMIN_GOAL_SATISFIED/1.0"->{kind=Kind.ADMIN_SATISFIED;count=2;type="Boolean";}
            default->throw field.location().error("COMPACT_OBSERVER_ORIGIN_UNSUPPORTED:"+field.origin());
        }
        if(parts.length!=count||!type.equals(field.type()))throw field.location().error("COMPACT_OBSERVER_ORIGIN_ARITY_OR_TYPE");
        for(int i=1;i<parts.length;i++)if(!parts[i].matches(kind==Kind.NPL_MONITOR?"[A-Za-z_][A-Za-z0-9_.-]*":"[A-Za-z_][A-Za-z0-9_]*"))throw field.location().error("COMPACT_OBSERVER_SOURCE_SYMBOL_INVALID");
        if(kind.name().startsWith("NPL_")&&!view.population().equals("NPL_GOAL_OBLIGATIONS/1.0"))throw field.location().error("COMPACT_NPL_ACTIVATION_POPULATION_REQUIRED");
        var source=SourceReference.parse(view.source());
        if(kind==Kind.FIRST_SUCCESS&&!(source instanceof SourceReference.ArtifactRef)||Set.of(Kind.FIRST_STATE,Kind.COMMITTED_AT_FIRST_STATE,Kind.DONE_PLAYERS,Kind.ADMIN_SATISFIED,Kind.ACTION_PLAYERS).contains(kind)&&!(source instanceof SourceReference.MoiseSchemeRef))
            throw field.location().error("COMPACT_OBSERVER_SOURCE_FAMILY_MISMATCH");
        if(Set.of(Kind.FIRST_STATE,Kind.COMMITTED_AT_FIRST_STATE).contains(kind)&&!Set.of("WAITING","ENABLED","SATISFIED").contains(parts[2]))throw field.location().error("COMPACT_OBSERVER_GOAL_STATE_UNSUPPORTED");
        return new Binding(kind,Arrays.asList(parts).subList(1,parts.length));
    }
    private CompactObserverBindings(){ }
}

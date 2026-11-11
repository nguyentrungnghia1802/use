package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;
import jason.asSyntax.*;
import ora4mas.nopl.JasonTermWrapper;
import ora4mas.nopl.SchemeBoard;

/** Decode SchemeBoard's public goalState/goalArgument observable contract, not agent beliefs. */
final class MoiseGoalObservation {
    record Goal(String id,String state,List<String> committed,List<String> achieved) { }
    record Cut(ArtifactId artifact,List<Goal> goals,Map<String,Map<String,String>> arguments,
               Map<String,Map<String,Map<String,Object>>> structuredArguments) {
        Cut {
            goals=List.copyOf(goals);
            var raw=new TreeMap<String,Map<String,String>>();arguments.forEach((k,v)->raw.put(k,Map.copyOf(v)));arguments=Map.copyOf(raw);
            var typed=new TreeMap<String,Map<String,Map<String,Object>>>();structuredArguments.forEach((k,v)->typed.put(k,Map.copyOf(v)));structuredArguments=Map.copyOf(typed);
        }
    }
    static Cut capture(SchemeBoard board) throws Exception {
        return decode(MoiseBoardArtifactObservation.capture(board),board.getArtId());
    }
    static Cut decode(ArtifactInfo info,String scheme) throws Exception {
        var goals=new TreeMap<String,Goal>();var arguments=new TreeMap<String,Map<String,String>>();
        var structured=new TreeMap<String,Map<String,Map<String,Object>>>();
        for(var property:info.getObsProperties()) {
            if(property.getName().equals("goalState")) {
                if(property.getValues().length!=5 || !term(property.getValue(0)).equals(ASSyntax.createAtom(scheme)))
                    throw new IllegalStateException("MOISE_GOAL_STATE_CONTEXT_INVALID");
                Term goal=term(property.getValue(1));Term state=term(property.getValue(4));
                if(!(goal instanceof Literal literal) || literal.getArity()!=0 || !(state instanceof Atom atom)
                        || !Set.of("waiting","enabled","satisfied").contains(atom.getFunctor()))throw new IllegalStateException("MOISE_GOAL_STATE_UNSUPPORTED");
                var value=new Goal(literal.getFunctor(),atom.getFunctor().toUpperCase(Locale.ROOT),agents(term(property.getValue(2))),agents(term(property.getValue(3))));
                if(goals.putIfAbsent(value.id(),value)!=null)throw new IllegalStateException("MOISE_GOAL_STATE_DUPLICATED");
            } else if(property.getName().equals("goalArgument")) {
                if(property.getValues().length!=4 || !term(property.getValue(0)).equals(ASSyntax.createAtom(scheme)))
                    throw new IllegalStateException("MOISE_GOAL_ARGUMENT_CONTEXT_INVALID");
                var goal=term(property.getValue(1));var name=term(property.getValue(2));
                if(!(goal instanceof Atom atom) || !(name instanceof StringTerm string))throw new IllegalStateException("MOISE_GOAL_ARGUMENT_INVALID");
                Term actual=term(property.getValue(3));String value=actual.toString();
                if(arguments.computeIfAbsent(atom.getFunctor(),unused->new TreeMap<>()).putIfAbsent(string.getString(),value)!=null)
                    throw new IllegalStateException("MOISE_GOAL_ARGUMENT_DUPLICATED");
                structured.computeIfAbsent(atom.getFunctor(),unused->new TreeMap<>()).put(string.getString(),JasonStructuredTerms.term(actual));
            }
        }
        return new Cut(info.getId(),List.copyOf(goals.values()),arguments,structured);
    }
    private static Term term(Object value) throws Exception {
        // Moise 1.1 updateGoalArgsObsProp publishes Jason Terms directly;
        // updateGoalStateObsProp uses OrgArt.getTermsAsProlog wrappers.
        if(value instanceof Term term)return term.clone();
        if(!(value instanceof JasonTermWrapper wrapper))throw new IllegalStateException("MOISE_OBSERVABLE_TERM_TYPE_UNSUPPORTED");
        return ASSyntax.parseTerm(wrapper.getAsPrologStr());
    }
    private static List<String> agents(Term term) {
        if(!(term instanceof ListTerm list) || !list.isGround() || list.getTail()!=null)throw new IllegalStateException("MOISE_GOAL_AGENT_LIST_UNSUPPORTED");
        var result=new TreeSet<String>();for(Term agent:list) {
            if(!(agent instanceof Atom atom) || !(agent instanceof Literal literal) || literal.getArity()!=0)
                throw new IllegalStateException("MOISE_GOAL_AGENT_TERM_UNSUPPORTED");
            result.add(atom.getFunctor());
        }
        return List.copyOf(result);
    }
}

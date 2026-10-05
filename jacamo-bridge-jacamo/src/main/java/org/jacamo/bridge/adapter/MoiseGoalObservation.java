package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;
import jason.asSyntax.*;
import ora4mas.nopl.JasonTermWrapper;
import ora4mas.nopl.SchemeBoard;

/** Decode SchemeBoard's public goalState/goalArgument observable contract, not agent beliefs. */
final class MoiseGoalObservation {
    record Goal(String id,String state,List<String> committed,List<String> achieved) { }
    record Cut(ArtifactId artifact,List<Goal> goals,Map<String,Map<String,String>> arguments) { }
    static Cut capture(SchemeBoard board) throws Exception {
        var environment=CartagoEnvironment.getInstance();var matches=new ArrayList<ArtifactInfo>();
        collect(environment,environment.getRootWSP(),board,matches);
        if(matches.size()!=1)throw new IllegalStateException("MOISE_BOARD_ARTIFACT_IDENTITY_UNAVAILABLE");
        var info=matches.getFirst();return decode(info,board.getArtId());
    }
    private static void collect(CartagoEnvironment environment,WorkspaceDescriptor workspace,SchemeBoard board,List<ArtifactInfo> matches) throws Exception {
        if(workspace==null || workspace.getWorkspace()==null)return;
        var current=workspace.getWorkspace();
        for(String name:current.getArtifactList()) {
            var descriptor=current.getArtifactDescriptor(name);
            if(descriptor==null || descriptor.getArtifact()!=board)continue;
            var info=environment.getController(workspace.getId().getFullName()).getArtifactInfo(name);
            if(info==null || !info.getId().getName().equals(name) || !info.getId().getWorkspaceId().equals(workspace.getId())
                    || current.getArtifactDescriptor(name)!=descriptor)throw new IllegalStateException("MOISE_BOARD_ARTIFACT_REPLACED");
            matches.add(info);
        }
        for(var child:current.getChildWSPs())collect(environment,child,board,matches);
    }
    static Cut decode(ArtifactInfo info,String scheme) throws Exception {
        var goals=new TreeMap<String,Goal>();var arguments=new TreeMap<String,Map<String,String>>();
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
                String value=term(property.getValue(3)).toString();
                if(arguments.computeIfAbsent(atom.getFunctor(),unused->new TreeMap<>()).putIfAbsent(string.getString(),value)!=null)
                    throw new IllegalStateException("MOISE_GOAL_ARGUMENT_DUPLICATED");
            }
        }
        return new Cut(info.getId(),List.copyOf(goals.values()),Map.copyOf(arguments));
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

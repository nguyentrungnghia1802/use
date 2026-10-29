package org.jacamo.bridge.adapter;
import java.nio.file.Path;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;
/** Historical site selection and marker spelling over shared verified AST preparation. */
public final class AuctionStagingInstrumentation extends JasonPlanInstrumentation {
    public static final String RECIPE="AUCTION_PLAN_BODY_MARKERS/1.0";
    public static final String SOURCE_SHA="31679b13210114b211dad862ad86af352cc9003ac9527ea7268e3a1d0f9cfe1b";
    private static final Map<String,List<Boundary>> BOUNDARIES=Map.of(
        "start",List.of(new Boundary("A_start_t1",0,5),new Boundary("A_start_t2",5,6)),
        "decide",List.of(new Boundary("A_decide_t1",0,2),new Boundary("A_decide_t2",2,3)),
        "bid",List.of(new Boundary("P_bid_t1",0,3),new Boundary("P_bid_t2",3,4)));
    private AuctionStagingInstrumentation(){ }
    public static Prepared prepare(Agent agent,AgentProgramSemantic source,Path root,Reporter reporter)throws Exception {
        var plans=new ArrayList<PlanSelection>();
        for(var plan:source.planLibrary().plans())if(plan.metadata().evidence().stream().anyMatch(e->e.sourceDigest().equals(SOURCE_SHA))) {
            var boundaries=BOUNDARIES.get(ASSyntax.parseLiteral(plan.trigger().literal()).getFunctor());
            if(boundaries!=null)plans.add(new PlanSelection(plan.metadata().semanticId(),boundaries));
        }
        if(plans.size()!=3)throw new IllegalArgumentException("AUCTION_STAGING_EXACT_CASE_SITES_REQUIRED");
        return JasonPlanInstrumentation.prepare(agent,source,root,reporter,new Specification(RECIPE,SOURCE_SHA,plans),AuctionStagingInstrumentation::marker);
    }
    private static PlanBody marker(Site site,String phase,Reporter reporter) {
        var literal=new InternalActionLiteral(Marker.class.getName());literal.addTerm(ASSyntax.createString(site.task()));literal.addTerm(ASSyntax.createString(phase));literal.setIA(new Marker(site,phase,reporter));
        return new PlanBodyImpl(PlanBody.BodyType.internalAction,literal);
    }
    private static final class Marker extends DefaultInternalAction {
        private final Site site;private final String phase;private final Reporter reporter;
        Marker(Site site,String phase,Reporter reporter){this.site=site;this.phase=phase;this.reporter=reporter;}
        @Override public Object execute(TransitionSystem ts,Unifier unifier,Term[] args) {
            try {reporter.marker(ts,site,phase);}catch(Exception missing){try{reporter.unavailable("STEP_MARKER_CAPTURE_FAILED:"+missing.getClass().getSimpleName());}catch(RuntimeException retired){/* Retired transport cannot grant a Boolean result. */}}
            return Boolean.TRUE; // Observation never changes the original deed's outcome.
        }
    }
}

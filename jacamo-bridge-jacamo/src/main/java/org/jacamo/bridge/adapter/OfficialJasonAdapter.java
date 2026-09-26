package org.jacamo.bridge.adapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import jason.asSemantics.Agent;
import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.InternalAction;
import jason.asSyntax.Literal;
import jason.asSyntax.Plan;
import jason.asSyntax.PlanBody;
import jason.asSyntax.Structure;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelFact;

/** Official Jason parser/AST adapter. Source text is provenance, never the accepted semantic parser. */
public final class OfficialJasonAdapter {
    /** Reserved label format emitted by Jason PlanLibrary#getUniqueLabel(). */
    private static final Pattern GENERATED_LABEL = Pattern.compile("p__\\d+");
    public List<ModelFact> parse(Path projectRoot, Path source, String projectKey, String declarationId) throws Exception {
        var evidence = AdapterEvidence.file("jason-parser", projectRoot, source, "Agent.parseAS official AST");
        var parent = new BridgeEntityId("jacamo", "agent", "declaration", projectKey, declarationId, "model");
        // Parsing is an observation operation; it must not start Jason's web
        // mind-inspector service or any other listening endpoint.
        jason.util.Config.get().put(jason.util.Config.START_WEB_MI, "false");
        var agent = new SafeParsingAgent(); agent.setConsiderToAddMIForThisAgent(false); agent.initAg();
        try {
            agent.parseAS(source.toFile()); var result = new ArrayList<ModelFact>(); int ordinal = 0;
            for (Literal belief : agent.getInitialBels()) result.add(fact("belief", canonicalAst(belief.toString()), belief.getSrcInfo() == null ? 0 : belief.getSrcInfo().getSrcLine(), ordinal++, projectKey, declarationId, evidence, parent));
            for (Literal goal : agent.getInitialGoals()) result.add(fact("goal", canonicalAst(goal.toString()), goal.getSrcInfo() == null ? 0 : goal.getSrcInfo().getSrcLine(), ordinal++, projectKey, declarationId, evidence, parent));
            for (Plan plan : agent.getPL().getPlans()) {
                int planOrdinal=ordinal++;
                String labelFunctor = plan.getLabel() == null ? "" : plan.getLabel().getFunctor();
                boolean generatedLabel = GENERATED_LABEL.matcher(labelFunctor).matches();
                Map<String,String> attributes = Map.of("label", generatedLabel ? "" : labelFunctor,
                        "labelKind", generatedLabel ? "JASON_GENERATED" : "SOURCE_DECLARED",
                        "atomic", Boolean.toString(plan.isAtomic()), "breakpoint", Boolean.toString(plan.hasBreakpoint()),
                        "allUnifiers", Boolean.toString(plan.isAllUnifs()), "trigger", canonicalAst(String.valueOf(plan.getTrigger())),
                        "context", canonicalAst(String.valueOf(plan.getContext())), "bodyAst", canonicalAst(String.valueOf(plan.getBody())),
                        "sourceLine", Integer.toString(plan.getSrcInfo() == null ? 0 : plan.getSrcInfo().getSrcLine()));
                var id = new BridgeEntityId("jason", "agent", "plan", projectKey, declarationId + ":" + planOrdinal, "model");
                var triggerId=new BridgeEntityId("jason","agent","event",projectKey,declarationId+":"+planOrdinal+":trigger","model");
                var trigger=plan.getTrigger();
                result.add(new ModelFact("event",triggerId,Map.of("operator",String.valueOf(trigger.getOperator()),"type",String.valueOf(trigger.getType()),"literal",canonicalAst(String.valueOf(trigger.getLiteral()))),Map.of(),CapabilityStatus.COMPLETE,List.of(evidence)));
                var actionIds=new ArrayList<BridgeEntityId>(); int actionOrdinal=0;
                for(PlanBody body=plan.getBody();body!=null;body=body.getBodyNext()){
                    if(body.getBodyTerm()==null)continue;
                    boolean internal=body.getBodyType()==PlanBody.BodyType.internalAction;
                    if(!internal&&body.getBodyType()!=PlanBody.BodyType.action)continue;
                    var actionId=new BridgeEntityId("jason","agent","action",projectKey,declarationId+":"+planOrdinal+":action:"+actionOrdinal++,"model");
                    var term=body.getBodyTerm();String actionName=term instanceof Structure structure?structure.getFunctor():canonicalAst(term.toString());int arity=term instanceof Structure structure?structure.getArity():0;
                    result.add(new ModelFact("action",actionId,Map.of("name",actionName,"arity",Integer.toString(arity),"kind",internal?"internal":"external","ast",canonicalAst(term.toString())),Map.of(),CapabilityStatus.COMPLETE,List.of(evidence)));actionIds.add(actionId);
                }
                result.add(new ModelFact("plan", id, attributes, Map.of("agent",List.of(parent),"triggeringEvent",List.of(triggerId),"actions",actionIds), CapabilityStatus.COMPLETE, List.of(evidence)));
            }
            return List.copyOf(result);
        } finally { agent.stopAg(); }
    }
    private ModelFact fact(String kind,String ast,int line,int ordinal,String projectKey,String declarationId,org.jacamo.bridge.contract.Evidence evidence,BridgeEntityId parent) {
        var id=new BridgeEntityId("jason","agent",kind,projectKey,declarationId+":"+ordinal,"model");
        return new ModelFact(kind,id,Map.of("ast",ast,"sourceLine",Integer.toString(line)),Map.of("agent",List.of(parent)),CapabilityStatus.COMPLETE,List.of(evidence));
    }
    /**
     * Jason assigns process-global numeric names to anonymous variables.  They
     * are deliberately semantically unobservable, so the neutral contract
     * prints them as the Jason source-level anonymous token.  Quoted content is
     * left byte-for-byte intact.
     */
    static String canonicalAst(String rendered) {
        var out = new StringBuilder(rendered.length()); boolean quoted=false; char quote=0; boolean escaped=false;
        for(int i=0;i<rendered.length();){
            char c=rendered.charAt(i);
            if(quoted){out.append(c); if(escaped)escaped=false; else if(c=='\\')escaped=true; else if(c==quote)quoted=false; i++; continue;}
            if(c=='\''||c=='\"'){quoted=true;quote=c;out.append(c);i++;continue;}
            if(c=='_'&&i+1<rendered.length()&&Character.isDigit(rendered.charAt(i+1))
                    &&(i==0||!Character.isJavaIdentifierPart(rendered.charAt(i-1)))){
                int end=i+2; while(end<rendered.length()&&Character.isDigit(rendered.charAt(end)))end++;
                if(end==rendered.length()||!Character.isJavaIdentifierPart(rendered.charAt(end))){out.append('_');i=end;continue;}
            }
            out.append(c);i++;
        }
        return out.toString();
    }
    /** Parsing untrusted source never reflectively instantiates internal actions. */
    private static final class SafeParsingAgent extends Agent {
        private final InternalAction inert = new DefaultInternalAction();
        @Override public InternalAction getIA(String name) { return inert; }
    }
}

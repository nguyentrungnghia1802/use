package org.jacamo.bridge.adapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jason.asSemantics.Agent;
import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.InternalAction;
import jason.asSyntax.Literal;
import jason.asSyntax.Plan;
import jason.asSyntax.PlanBody;
import jason.asSyntax.Rule;
import jason.asSyntax.Structure;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelFact;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.ActionSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentGoalSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.BeliefRuleSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.BeliefSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.PlanBodyElementSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.PlanLibrarySemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.PlanSemantic;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.TriggerSemantic;

/** Official Jason 3.3.2 parser/object adapter. Source text is provenance, never semantic authority. */
public final class OfficialJasonAdapter {
    private static final Pattern GENERATED_LABEL=Pattern.compile("p__\\d+");
    private static final Set<String> AUDITED_BODY_TYPES=Set.of("none","action","internalAction","achieve","test",
            "addBel","addBelNewFocus","addBelBegin","addBelEnd","delBel","delBelNewFocus","delAddBel",
            "achieveNF","constraint");

    public record Result(List<ModelFact> facts, AgentProgramSemantic program) {
        public Result { facts=List.copyOf(facts); }
    }

    /** Compatibility view retained for the explicit LEGACY_V2 pipeline. */
    public List<ModelFact> parse(Path projectRoot,Path source,String projectKey,String declarationId)throws Exception{
        return adapt(projectRoot,source,projectKey,declarationId).facts();
    }

    public Result adapt(Path projectRoot,Path source,String projectKey,String declarationId)throws Exception{
        verifyAuditedBodyTypeSet();
        var evidence=AdapterEvidence.file("jason-parser",projectRoot,source,"Agent.parseAS official Jason AST");
        var parent=new BridgeEntityId("jacamo","agent","declaration",projectKey,declarationId,"model");
        jason.util.Config.get().put(jason.util.Config.START_WEB_MI,"false");
        var agent=new SafeParsingAgent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg();
        try{
            agent.parseAS(source.toFile());
            var facts=new ArrayList<ModelFact>();int legacyOrdinal=0;
            String programId="jason:agent-program:"+projectKey+":"+declarationId;
            String libraryId=programId+":plan-library";
            var typedActions=new ArrayList<ActionSemantic>();
            var typedBeliefs=new ArrayList<BeliefSemantic>();
            var typedGoals=new ArrayList<AgentGoalSemantic>();
            var typedRules=new ArrayList<BeliefRuleSemantic>();
            int beliefOrdinal=0;int ruleOrdinal=0;
            for(Literal belief:agent.getInitialBels()){
                String literal=canonicalAst(belief.toString());
                if(belief instanceof Rule rule){
                    String ruleId=programId+":belief-rule:"+ruleOrdinal+":"+AdapterEvidence.digest(
                            (canonicalAst(rule.getHead().toString())+"|"+canonicalAst(rule.getBody().toString()))
                                    .getBytes(StandardCharsets.UTF_8));
                    var metadata=SemanticEvidence.metadata(ruleId,"JASON_BELIEF_RULE","jason.asSyntax.Rule",
                            EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,
                            startLine(rule),endLine(rule),List.of());
                    typedRules.add(new BeliefRuleSemantic(metadata,ruleOrdinal++,canonicalAst(rule.getHead().toString()),
                            canonicalAst(rule.getBody().toString())));
                    facts.add(fact("belief-rule",literal,startLine(rule),legacyOrdinal++,projectKey,declarationId,evidence,parent));
                }else{
                    String beliefId=programId+":belief:"+beliefOrdinal+":"+AdapterEvidence.digest(
                            literal.getBytes(StandardCharsets.UTF_8));
                    var metadata=SemanticEvidence.metadata(beliefId,"JASON_BELIEF","jason.asSyntax.Literal",
                            EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,
                            startLine(belief),endLine(belief),List.of());
                    typedBeliefs.add(new BeliefSemantic(metadata,beliefOrdinal++,literal));
                    facts.add(fact("belief",literal,startLine(belief),legacyOrdinal++,projectKey,declarationId,evidence,parent));
                }
            }
            int goalOrdinal=0;
            for(Literal goal:agent.getInitialGoals()){
                String literal=canonicalAst(goal.toString());
                String goalId=programId+":goal:"+goalOrdinal+":"+AdapterEvidence.digest(
                        literal.getBytes(StandardCharsets.UTF_8));
                var metadata=SemanticEvidence.metadata(goalId,"JASON_AGENT_GOAL","jason.asSyntax.Literal",
                        EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,
                        startLine(goal),endLine(goal),List.of());
                typedGoals.add(new AgentGoalSemantic(metadata,goalOrdinal++,literal,"ACHIEVE"));
                facts.add(fact("goal",literal,startLine(goal),legacyOrdinal++,projectKey,declarationId,evidence,parent));
            }
            var typedPlans=new ArrayList<PlanSemantic>();int planOrdinal=0;
            for(Plan plan:agent.getPL().getPlans()){
                int ordinal=planOrdinal++;
                var bodyDrafts=body(plan);
                String triggerOperator=plan.getTrigger().getOperator().name();
                String triggerType=plan.getTrigger().getType().name();
                String triggerLiteral=canonicalAst(String.valueOf(plan.getTrigger().getLiteral()));
                String context=canonicalAst(String.valueOf(plan.getContext()));
                String digest=AdapterEvidence.digest((triggerOperator+"|"+triggerType+"|"+triggerLiteral+"|"+context+"|"
                        +bodyDrafts.stream().map(d->d.type()+":"+d.term()).collect(Collectors.joining("|")))
                        .getBytes(StandardCharsets.UTF_8));
                String planId=libraryId+":plan:"+ordinal+":"+digest;
                String triggerId=planId+":trigger";
                int planLine=plan.getSrcInfo()==null?0:plan.getSrcInfo().getSrcLine();
                var triggerMetadata=SemanticEvidence.metadata(triggerId,"JASON_TRIGGER","jason.asSyntax.Trigger",
                        EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,
                        planLine,planLine,List.of());
                var typedTrigger=new TriggerSemantic(triggerMetadata,triggerOperator,triggerType,triggerLiteral);
                var typedBody=new ArrayList<PlanBodyElementSemantic>();
                for(int i=0;i<bodyDrafts.size();i++){
                    BodyDraft draft=bodyDrafts.get(i);String bodyId=bodyId(planId,i,draft);
                    String next=i+1<bodyDrafts.size()?bodyId(planId,i+1,bodyDrafts.get(i+1)):"";
                    var metadata=SemanticEvidence.metadata(bodyId,"JASON_PLAN_BODY_ELEMENT","jason.asSyntax.PlanBody",
                            EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,
                            draft.line(),draft.line(),List.of());
                    typedBody.add(new PlanBodyElementSemantic(metadata,i,draft.type(),draft.term(),next));
                    if(draft.type().equals("action")||draft.type().equals("internalAction")){
                        var term=draft.source().getBodyTerm();
                        String functor=term instanceof Structure structure?structure.getFunctor():"";
                        int arity=term instanceof Structure structure?structure.getArity():0;
                        String actionId=bodyId+":action";
                        var actionMetadata=SemanticEvidence.metadata(actionId,
                                draft.type().equals("action")?"JASON_EXTERNAL_ACTION":"JASON_INTERNAL_ACTION",
                                "jason.asSyntax.PlanBody",EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,
                                CapabilityStatus.COMPLETE,evidence,draft.line(),draft.line(),List.of());
                        typedActions.add(new ActionSemantic(actionMetadata,bodyId,draft.term(),functor,arity,
                                draft.type().equals("action")?"EXTERNAL":"INTERNAL"));
                    }
                }
                String labelFunctor=plan.getLabel()==null?"":plan.getLabel().getFunctor();
                String sourceLabel=GENERATED_LABEL.matcher(labelFunctor).matches()?"":labelFunctor;
                var planMetadata=SemanticEvidence.metadata(planId,"JASON_PLAN","jason.asSyntax.Plan",
                        EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,
                        planLine,planLine,List.of());
                typedPlans.add(new PlanSemantic(planMetadata,ordinal,sourceLabel,context,typedTrigger,typedBody));

                // Compatibility facts remain available only to LEGACY_V2. The complete typed body above is authoritative.
                int oldOrdinal=legacyOrdinal++;
                var planBridgeId=new BridgeEntityId("jason","agent","plan",projectKey,declarationId+":"+oldOrdinal,"model");
                var triggerBridgeId=new BridgeEntityId("jason","agent","event",projectKey,declarationId+":"+oldOrdinal+":trigger","model");
                facts.add(new ModelFact("event",triggerBridgeId,Map.of("operator",String.valueOf(plan.getTrigger().getOperator()),
                        "type",String.valueOf(plan.getTrigger().getType()),"literal",triggerLiteral),Map.of(),
                        CapabilityStatus.COMPLETE,List.of(evidence)));
                var actionIds=new ArrayList<BridgeEntityId>();var bodyIds=new ArrayList<BridgeEntityId>();int actionOrdinal=0;
                for(int i=0;i<bodyDrafts.size();i++){
                    BodyDraft draft=bodyDrafts.get(i);
                    var bodyBridgeId=new BridgeEntityId("jason","agent","plan-body-element",projectKey,
                            declarationId+":"+oldOrdinal+":body:"+i,"model");bodyIds.add(bodyBridgeId);
                    facts.add(new ModelFact("plan-body-element",bodyBridgeId,Map.of("bodyType",draft.type(),"term",draft.term(),
                            "ordinal",Integer.toString(i)),Map.of(),CapabilityStatus.COMPLETE,List.of(evidence)));
                    if(!draft.type().equals("action")&&!draft.type().equals("internalAction"))continue;
                    PlanBody original=draft.source();var term=original.getBodyTerm();
                    var actionId=new BridgeEntityId("jason","agent","action",projectKey,
                            declarationId+":"+oldOrdinal+":action:"+actionOrdinal++,"model");
                    String actionName=term instanceof Structure structure?structure.getFunctor():draft.term();
                    int arity=term instanceof Structure structure?structure.getArity():0;
                    facts.add(new ModelFact("action",actionId,Map.of("name",actionName,"arity",Integer.toString(arity),
                            "kind",draft.type().equals("internalAction")?"internal":"external","ast",draft.term()),
                            Map.of(),CapabilityStatus.COMPLETE,List.of(evidence)));actionIds.add(actionId);
                }
                facts.add(new ModelFact("plan",planBridgeId,Map.of("label",sourceLabel,
                        "labelKind",sourceLabel.isBlank()?"JASON_GENERATED":"SOURCE_DECLARED",
                        "atomic",Boolean.toString(plan.isAtomic()),"breakpoint",Boolean.toString(plan.hasBreakpoint()),
                        "allUnifiers",Boolean.toString(plan.isAllUnifs()),"trigger",canonicalAst(String.valueOf(plan.getTrigger())),
                        "context",context,"bodyAst",canonicalAst(String.valueOf(plan.getBody())),
                        "sourceLine",Integer.toString(planLine)),Map.of("agent",List.of(parent),
                        "triggeringEvent",List.of(triggerBridgeId),"actions",actionIds,"bodyElements",bodyIds),
                        CapabilityStatus.COMPLETE,List.of(evidence)));
            }
            var libraryMetadata=SemanticEvidence.metadata(libraryId,"JASON_PLAN_LIBRARY","jason.pl.PlanLibrary",
                    EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,1,1,List.of());
            var library=new PlanLibrarySemantic(libraryMetadata,typedPlans);
            var programMetadata=SemanticEvidence.metadata(programId,"JASON_AGENT_PROGRAM","jason.asSemantics.Agent",
                    EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,evidence,1,1,List.of());
            var program=new AgentProgramSemantic(programMetadata,declarationId,evidence.sourceUri(),evidence.sourceDigest(),library,
                    typedActions,typedBeliefs,typedGoals,typedRules);
            return new Result(facts,program);
        }finally{agent.stopAg();}
    }

    private List<BodyDraft> body(Plan plan){
        var result=new ArrayList<BodyDraft>();Set<PlanBody> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(PlanBody node=plan.getBody();node!=null;node=node.getBodyNext()){
            if(!seen.add(node))throw new IllegalStateException("JASON_PLAN_BODY_CYCLE");
            String type=node.getBodyType().name();
            if(!AUDITED_BODY_TYPES.contains(type))throw new IllegalStateException("JASON_BODY_TYPE_UNSUPPORTED:"+type);
            String term=node.getBodyTerm()==null?"":canonicalAst(node.getBodyTerm().toString());
            result.add(new BodyDraft(node,type,term,line(node)));
        }
        return List.copyOf(result);
    }

    private String bodyId(String planId,int ordinal,BodyDraft draft){return planId+":body:"+ordinal+":"+AdapterEvidence.digest(
            (draft.type()+"|"+draft.term()).getBytes(StandardCharsets.UTF_8));}
    private void verifyAuditedBodyTypeSet(){
        Set<String> actual=Arrays.stream(PlanBody.BodyType.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
        if(!actual.equals(AUDITED_BODY_TYPES))throw new IllegalStateException("JASON_BODY_TYPE_API_DRIFT:"+actual);
    }
    private int line(jason.asSyntax.Term term){return startLine(term);}
    private int startLine(jason.asSyntax.Term term){return term.getSrcInfo()==null?0:term.getSrcInfo().getBeginSrcLine();}
    private int endLine(jason.asSyntax.Term term){return term.getSrcInfo()==null?startLine(term):term.getSrcInfo().getEndSrcLine();}
    private ModelFact fact(String kind,String ast,int line,int ordinal,String projectKey,String declarationId,
                           org.jacamo.bridge.contract.Evidence evidence,BridgeEntityId parent){
        var id=new BridgeEntityId("jason","agent",kind,projectKey,declarationId+":"+ordinal,"model");
        return new ModelFact(kind,id,Map.of("ast",ast,"sourceLine",Integer.toString(line)),Map.of("agent",List.of(parent)),CapabilityStatus.COMPLETE,List.of(evidence));
    }

    static String canonicalAst(String rendered){
        var out=new StringBuilder(rendered.length());boolean quoted=false;char quote=0;boolean escaped=false;
        for(int i=0;i<rendered.length();){char c=rendered.charAt(i);if(quoted){out.append(c);if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c==quote)quoted=false;i++;continue;}
            if(c=='\''||c=='\"'){quoted=true;quote=c;out.append(c);i++;continue;}
            if(c=='_'&&i+1<rendered.length()&&Character.isDigit(rendered.charAt(i+1))&&(i==0||!Character.isJavaIdentifierPart(rendered.charAt(i-1)))){int end=i+2;while(end<rendered.length()&&Character.isDigit(rendered.charAt(end)))end++;if(end==rendered.length()||!Character.isJavaIdentifierPart(rendered.charAt(end))){out.append('_');i=end;continue;}}
            out.append(c);i++;}return out.toString();
    }
    private record BodyDraft(PlanBody source,String type,String term,int line){ }
    private static final class SafeParsingAgent extends Agent{
        private final InternalAction inert=new DefaultInternalAction();
        @Override public InternalAction getIA(String name){return inert;}
    }
}

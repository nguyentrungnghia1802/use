package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;

/** High-level CSL rules refer to, and do not replace, the audited native source contract catalog. */
public final class MappingRuleCatalog {
    public static final String VERSION="CSL_MAPPING/1.0";
    public enum Disposition { DIRECT, ABSTRACTED, PARTIAL, TRACE_ONLY, UNSUPPORTED }
    public record Rule(String id,String sourceConcept,String target,Disposition disposition,String loss,String inverse,List<String> nativeRules) {
        public Rule{nativeRules=List.copyOf(nativeRules);}
    }
    private final List<Rule> rules;
    public MappingRuleCatalog() {
        rules=List.of(
            rule("M01","Organization","CSL namespace",Disposition.PARTIAL,"FS/NS are separate observations; specification is not deployment","structural skeleton + complement","J05 M01 M02 M03 M04"),
            rule("M02","Group","CSL OrgContext",Disposition.PARTIAL,"declaration nesting creates no population; contextual bounds require policy","structural model + complement","J06 M05 M16 M22 M23"),
            rule("M03","Role","CSL Role + occurrence",Disposition.PARTIAL,"multi-parent/scoped inheritance excluded; declaration is not enactment","single parent + complement","M06 M24 M29 M30"),
            rule("M04","Link","structural policy",Disposition.PARTIAL,"scope/direction/subgroup extension not generic association","original policy required","M07 M08 M09 M25 M26 M27 M28"),
            rule("M05","Norm","normative observer",Disposition.TRACE_ONLY,"lifecycle/deadlines not ordinary invariants","original normative source required","M14 M41 M42 M43"),
            rule("M06","Scheme","functional instance scope",Disposition.PARTIAL,"allocation/lifecycle not Entity or automatic OrgContext","definition + complement","J07 M10 M33"),
            rule("M07","Mission","allocation definition/observation",Disposition.PARTIAL,"commitment not goal satisfaction","membership/bounds + complement","M11 M17 M34 M36 M37 M38"),
            rule("M08","OGoal","explicit iStar correspondence",Disposition.ABSTRACTED,"source performance/achievement/maintenance not temporal-name casting","definition + complement","M12 M35 M39"),
            rule("M09","OPlan","outcome + execution descriptor",Disposition.PARTIAL,"sequence/parallel order cannot become plain AND","ordered operator tree + complement","M13 M40"),
            rule("M10","Agent","atomic player",Disposition.PARTIAL,"BDI state not CSL Entity; declaration not live instance","declaration only","J02 A01 C12 X09"),
            rule("M11","Belief","mental observation",Disposition.TRACE_ONLY,"stored literal not entailment/world truth","selected initial syntax + complement","A08 A10 A21 X02"),
            rule("M12","AGoal","goal-attempt observation",Disposition.ABSTRACTED,"current achievement/test goal not satisfied requirement","declaration + complement","A09 A22 X07"),
            rule("M13","Plan","execution AST / approved Task",Disposition.ABSTRACTED,"unification/control/selection not encoded by Task","original ASL required","A02 A03 A05 A16 A17 A19 A20"),
            rule("M14","Event","transient observation",Disposition.TRACE_ONLY,"queue/order/occurrence not persistent world fact","recorded evidence only","A04 A14 A18 X03"),
            rule("M15","Action","invocation / approved Task",Disposition.PARTIAL,"internal/external effects require explicit contract","interface + source complement","A06 A07 A12 X01"),
            rule("M16","Environment","topology/authority observer",Disposition.TRACE_ONLY,"not passive Entity; external world incomplete","deployment skeleton","C01 C13"),
            rule("M17","Workspace","workspace observation",Disposition.PARTIAL,"join/focus/context are different correspondences","deployment structure","J03 C02 C19 X05"),
            rule("M18","Artifact","registered CSL Entity view",Disposition.ABSTRACTED,"Java/guards/await/internal state not domain attributes","interface + original Java","J04 C03 C04 C10 C14 C15 X06 X08"),
            rule("M19","Operation","signature + transition contract",Disposition.PARTIAL,"postcondition does not synthesize effects or prove Java","interface + original source","C05 C06 C07 C16 C18"),
            rule("M20","Property","unique typed scalar Attribute",Disposition.PARTIAL,"fullId/type/value supplements needed; duplicate names not collapsed","scalar interface + complement","C08 C09 C17"),
            rule("M21","Signal","transient evidence",Disposition.TRACE_ONLY,"no durable truth without an explicit accumulator","recorded trace only","C11"),
            rule("E01","NormType","source normative modality",Disposition.PARTIAL,"NPL also supports prohibition","literal + provenance","M14"),
            rule("E02","GoalType","source goal type",Disposition.PARTIAL,"MOISE performance/default differs from supplied Ecore","literal + complement","M12"),
            rule("E03","OPlanOperator","ordered execution operator",Disposition.PARTIAL,"AND/OR does not preserve scheduler","ordered operator + complement","M13"),
            rule("E04","LinkType","policy type",Disposition.PARTIAL,"custom MOISE types outside Ecore enum","original string + provenance","M08"),
            rule("E05","LinkScope","policy scope",Disposition.PARTIAL,"direction/subgroup extension remain separate","scope + flags","M07"),
            rule("E06","ActionKind","execution kind",Disposition.PARTIAL,"two literals do not cover all Jason BodyType","source AST + complement","A06 A07"),
            rule("E07","AgentGoalType","achievement/test distinction",Disposition.PARTIAL,"test query is not ACHIEVE satisfaction","source syntax + complement","A09"));
        var nativeCatalog=new CodeGroundedRuleCatalog();for(var rule:rules)for(String id:rule.nativeRules())nativeCatalog.require(id);
        if(rules.stream().map(Rule::sourceConcept).distinct().count()!=rules.size())throw new IllegalStateException("SEMANTIC_RULE_CONCEPT_COLLISION");
    }
    private static Rule rule(String id,String concept,String target,Disposition disposition,String loss,String inverse,String nativeIds){return new Rule(id,concept,target,disposition,loss,inverse,List.of(nativeIds.split(" ")));}
    public List<Rule> rules(){return rules;}
    public Rule require(String concept){return rules.stream().filter(r->r.sourceConcept().equals(concept)).findFirst().orElseThrow(()->new IllegalArgumentException("SEMANTIC_SOURCE_CONCEPT_UNSUPPORTED:"+concept));}
    public String concept(String sourceKind) {
        return switch(sourceKind) {
            case "JACAMO_ORGANIZATION_DEPLOYMENT","MOISE_OS","MOISE_SS_CONTAINER","MOISE_FS_CONTAINER","MOISE_NS_CONTAINER"->"Organization";
            case "JACAMO_GROUP_DEPLOYMENT","MOISE_GROUP","MOISE_GROUP_ROLE_CARDINALITY","MOISE_SUBGROUP_CARDINALITY"->"Group";
            case "MOISE_ROLE"->"Role";
            case "MOISE_LINK","MOISE_ROLE_RELATION","MOISE_COMPATIBILITY"->"Link";
            case "MOISE_NORM"->"Norm";
            case "MOISE_SCHEME","JACAMO_SCHEME_DEPLOYMENT"->"Scheme";
            case "MOISE_MISSION","MOISE_SCHEME_MISSION_CARDINALITY"->"Mission";
            case "MOISE_GOAL"->"OGoal";case "MOISE_OPLAN"->"OPlan";
            case "JACAMO_AGENT_DECLARATION","JASON_AGENT_PROGRAM","CARTAGO_AGENT_IDENTITY"->"Agent";
            case "JASON_BELIEF","JASON_DOMAIN_BELIEF","JASON_BELIEF_RULE"->"Belief";
            case "JASON_AGENT_GOAL"->"AGoal";
            case "JASON_PLAN","JASON_PLAN_LIBRARY","JASON_PLAN_BODY_ELEMENT"->"Plan";
            case "JASON_TRIGGER"->"Event";
            case "JASON_EXTERNAL_ACTION","JASON_INTERNAL_ACTION"->"Action";
            case "CARTAGO_ENVIRONMENT"->"Environment";
            case "JACAMO_WORKSPACE_DECLARATION","CARTAGO_WORKSPACE"->"Workspace";
            case "JACAMO_ARTIFACT_DECLARATION","CARTAGO_ARTIFACT_TYPE","CARTAGO_PLATFORM_ARTIFACT_TYPE","CARTAGO_ARTIFACT","CARTAGO_ARTIFACT_INFO"->"Artifact";
            case "CARTAGO_OPERATION_DESCRIPTOR","CARTAGO_BACKING_JAVA_OPERATION","CARTAGO_GUARD"->"Operation";
            case "CARTAGO_OBSERVABLE_PROPERTY_SNAPSHOT"->"Property";
            default->"Supplement";
        };
    }
}

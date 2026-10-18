package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.sys.MObject;

/** Conservative AST-derived source admission; missing populations cannot become vacuous PASS. */
public final class ConstraintCoverageResolver {
    public String unavailablePopulations(Expression expression,Map<String,Completeness> sources,boolean lost) {
        if(lost)return "COVERAGE_LOST";
        var visitor=ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);
        for(var cls:visitor.populationTypes().stream().sorted(Comparator.comparing(org.tzi.use.uml.mm.MClassifier::name)).toList())
            if(!populationComplete(cls,sources)) {
                if(cls.getAnnotation("FunctionalObservation")!=null)return "FUNCTIONAL_GLOBAL_POPULATION_UNPROVEN";
                if(cls.getAnnotation("MentalObservation")!=null)return "MENTAL_GLOBAL_POPULATION_UNPROVEN";
                if(cls.getAnnotation("NormativeObservation")!=null)return "NPL_GLOBAL_POPULATION_UNPROVEN";
                return "REQUIRED_GLOBAL_POPULATION_UNPROVEN:"+cls.name();
            }
        return "";
    }
    public String unavailable(Expression expression,NativeRuntimeMutationEngine engine,Map<String,Completeness> sources,boolean lost) {
        if(lost)return "COVERAGE_LOST";
        var visitor=ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);
        var coverage=visitor.getCoverageData();var classes=new HashSet<>(coverage.getCompleteClassCoverage().keySet());
        if(engine.canonicalMappingConfigured())for(var association:coverage.getAssociationCoverage().keySet())if(association.getAnnotation("OriginalCslAssociation")!=null) {
            String source=engine.system().model().getAnnotationValue("CanonicalTupleAuthority","sourceId");
            if(source.isBlank()||sources.get(source)!=Completeness.COMPLETE)return "AUTHORITATIVE_ASSOCIATION_TUPLE_POPULATION_UNPROVEN:"+association.name();
        }
        coverage.getAttributeCoverage().keySet().forEach(a->classes.add(a.owner()));
        coverage.getAssociationCoverage().keySet().forEach(a->classes.addAll(a.associatedClasses()));
        String population=unavailablePopulations(expression,sources,lost);if(!population.isBlank())return population;
        for(var cls:classes.stream().sorted(Comparator.comparing(org.tzi.use.uml.mm.MClassifier::name)).toList()) {
            if(cls.getAnnotation("FunctionalObservation")!=null && visitor.populationTypes().contains(cls)
                    && !organizationComplete(sources))return "FUNCTIONAL_GLOBAL_POPULATION_UNPROVEN";
            if(cls.getAnnotation("MentalObservation")!=null && visitor.populationTypes().contains(cls) && sources.get("jason")!=Completeness.COMPLETE)return "MENTAL_GLOBAL_POPULATION_UNPROVEN";
            if(cls.getAnnotation("NormativeObservation")!=null && visitor.populationTypes().contains(cls) && sources.get("npl")!=Completeness.COMPLETE)return "NPL_GLOBAL_POPULATION_UNPROVEN";
            boolean domain=cls.name().equals("DomainEntity") || cls.allParents().stream().anyMatch(p->p.name().equals("DomainEntity"));
            boolean organization=Set.of("RoleOccurrence","OrgContext").contains(cls.name()) || cls.allParents().stream().anyMatch(p->Set.of("RoleOccurrence","OrgContext").contains(p.name()));
            if(domain && sources.get("cartago")!=Completeness.COMPLETE)return "REQUIRED_DOMAIN_OBSERVATION_INCOMPLETE:cartago";
            if(organization && !organizationComplete(sources))return "REQUIRED_ORGANIZATIONAL_POPULATION_UNPROVEN";
        }
        return "";
    }
    public String unavailableSelf(Expression expression,NativeRuntimeMutationEngine engine,MObject self,Map<String,Completeness> sources,boolean lost) {
        return unavailableSelf(expression,engine,self,sources,lost,Set.of());
    }
    public String unavailableSelf(Expression expression,NativeRuntimeMutationEngine engine,MObject self,Map<String,Completeness> sources,boolean lost,Set<String> freshBoards) {
        if(self.cls().getAnnotation("NormativeObservation")!=null && !freshBoards.contains(engine.metadata(self,"semanticId")))return "EXACT_NPL_OBSERVATION_UNAVAILABLE";
        String owner=engine.metadata(self,"mentalAgent");
        if(!owner.isBlank()) {
            if(!freshBoards.contains(owner))return "EXACT_AGENT_CYCLE_CUT_UNAVAILABLE";
            var mental=engine.objectForSemanticId(org.tzi.use.plugins.jacamo.semantics.observer.MentalObservationMapper.identity(owner));
            if(mental==null || !org.tzi.use.uml.ocl.value.BooleanValue.TRUE.equals(mental.state(engine.system().state()).attributeValue("complete")))return "MENTAL_BB_CUT_INCOMPLETE";
        }
        String scheme=engine.metadata(self,"functionalScheme");
        if(!scheme.isBlank() && !freshBoards.contains(scheme))return "EXACT_FUNCTIONAL_BOARD_CUT_UNAVAILABLE";
        String reason=unavailable(expression,engine,sources,lost);if(!reason.isBlank())return reason;
        var visitor=ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);
        for(var read:visitor.attributeReads()) {
            var attribute=read.attribute();String annotation=attribute.getAnnotation(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE)!=null?org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE:"CslAttribute";
            if(attribute.getAnnotation(annotation)==null)continue;
            boolean canonical=engine.canonicalMappingConfigured()&&annotation.equals(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE);
            boolean optional="true".equals(attribute.getAnnotationValue(annotation,"optional"));
            if(optional&&!canonical)continue;
            var candidates=read.directSelf() && self.cls().isSubClassifierOf(attribute.owner())?List.of(self):engine.system().state().allObjects().stream().filter(o->o.cls().isSubClassifierOf(attribute.owner())).toList();
            for(var object:candidates) {
                if(engine.metadata(object,"propertySlot:"+attribute.name()).isBlank()) {
                    if(canonical&&optional&&"true".equals(engine.metadata(object,"propertyAbsent:"+attribute.name())))continue;
                    return "REQUIRED_PROPERTY_OBSERVATION_UNAVAILABLE:"+attribute.owner().name()+"."+attribute.name()+":"+object.name();
                }
                if(canonical&&!org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.valueHash(object.state(engine.system().state()).attributeValue(attribute)).equals(engine.metadata(object,"observedValueHash:"+attribute.name())))
                    return "NATIVE_VALUE_DIFFERS_FROM_ACCEPTED_OBSERVATION:"+object.name()+"."+attribute.name();
            }
        }
        return "";
    }
    public boolean populationComplete(org.tzi.use.uml.mm.MClassifier cls,Map<String,Completeness> sources) {
        if(kind(cls,"DomainEntity") || kind(cls,"WorkspaceObservation"))return sources.get("cartago")==Completeness.COMPLETE;
        // AtomicAgent unifies Jason players and independently observed CArtAgO players.
        if(kind(cls,"AtomicAgent"))return sources.get("jason")==Completeness.COMPLETE && sources.get("cartago")==Completeness.COMPLETE;
        if(cls.getAnnotation("MentalObservation")!=null)return sources.get("jason")==Completeness.COMPLETE;
        if(cls.getAnnotation("NormativeObservation")!=null)return sources.get("npl")==Completeness.COMPLETE;
        if(cls.name().equals("RuntimePlayer"))return sources.get("jason")==Completeness.COMPLETE && sources.get("cartago")==Completeness.COMPLETE && organizationComplete(sources);
        if(kind(cls,"RoleOccurrence") || kind(cls,"OrgContext") || cls.getAnnotation("FunctionalObservation")!=null)return organizationComplete(sources);
        return false; // Unknown source authority cannot certify an empty or complete population.
    }
    private static boolean kind(org.tzi.use.uml.mm.MClassifier cls,String name) {
        return cls.name().equals(name) || cls.allParents().stream().anyMatch(p->p.name().equals(name));
    }
    private static boolean organizationComplete(Map<String,Completeness> sources) {
        var selected=sources.entrySet().stream().filter(e->e.getKey().startsWith("moise:")).toList();
        return !selected.isEmpty() && selected.stream().allMatch(e->e.getValue()==Completeness.COMPLETE);
    }
}

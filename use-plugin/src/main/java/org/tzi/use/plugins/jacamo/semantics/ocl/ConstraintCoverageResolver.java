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
        return unavailablePopulations(expression,null,sources,lost);
    }
    public String unavailablePopulations(Expression expression,NativeRuntimeMutationEngine engine,Map<String,Completeness> sources,boolean lost) {
        if(lost)return "COVERAGE_LOST";
        var visitor=ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);
        for(var cls:visitor.populationTypes().stream().sorted(Comparator.comparing(org.tzi.use.uml.mm.MClassifier::name)).toList())
            if(!populationComplete(cls,sources,engine)) {
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
        String population=unavailablePopulations(expression,engine,sources,lost);if(!population.isBlank())return population;
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
        if(engine.xmlCase()) {
            if(xmlObserver(self)) {if(!xmlObserverOwned(engine,self))return "XML_EXACT_OBSERVER_BINDING_UNAVAILABLE";}
            else if(!engine.canonicalProgramHash().equals(engine.metadata(self,"xml:m0:program"))||!"COMPLETE".equals(engine.metadata(self,"xml:m0:quality")))return "XML_EXACT_M0_BINDING_UNAVAILABLE";
        }
        if(legacyAuction(engine)) {
            if(kind(self.cls(),"OrgContext")) {
                if(!engine.metadata(self,"auctionRule:R01").isBlank()&&!"COMPLETE_OBSERVED_ORGANIZATION".equals(engine.metadata(self,"auctionOrganizationQuality")))return "AUCTION_ORGANIZATION_SCOPE_UNAVAILABLE";
                if(engine.metadata(self,"auctionRule:R01").isBlank()&&engine.metadata(self,"auctionRule:R02").isBlank())return "AUCTION_CONTEXT_MAPPING_PROOF_UNAVAILABLE";
            }
            if(kind(self.cls(),"RoleOccurrence")&&!"true".equals(engine.metadata(self,"auctionLiveEnactment")))return "AUCTION_EXACT_ROLE_ENACTMENT_UNAVAILABLE";
            if(kind(self.cls(),"DomainEntity")&&!"RESOLVED".equals(engine.metadata(self,"auctionExistence")))return "AUCTION_EXACT_ARTIFACT_BINDING_UNAVAILABLE";
        }
        if(engine.canonicalMappingConfigured()&&"INCOMPLETE".equals(engine.metadata(self,"rolePopulationQuality")))return "CANONICAL_ROLE_POPULATION_BINDING_INCOMPLETE";
        if(legacyAuction(engine)&&"UNRESOLVED".equals(engine.metadata(self,"auctionExistence")))return "AUCTION_EXACT_ARTIFACT_BINDING_UNAVAILABLE";
        if(legacyAuction(engine)&&"false".equals(engine.metadata(self,"auctionLiveEnactment")))return "AUCTION_EXACT_ROLE_ENACTMENT_UNAVAILABLE";
        if(legacyAuction(engine)&&"INCOMPLETE".equals(engine.metadata(self,"auctionPopulationQuality")))return "AUCTION_ROLE_POPULATION_BINDING_INCOMPLETE";
        if(self.cls().getAnnotation("NormativeObservation")!=null && !freshBoards.contains(engine.metadata(self,"semanticId")))return "EXACT_NPL_OBSERVATION_UNAVAILABLE";
        String owner=engine.metadata(self,"mentalAgent");
        if(!owner.isBlank()) {
            if(!freshBoards.contains(owner))return "EXACT_AGENT_CYCLE_CUT_UNAVAILABLE";
            var mental=engine.objectForSemanticId(org.tzi.use.plugins.jacamo.semantics.observer.MentalObservationMapper.identity(owner));
            if(mental==null || !org.tzi.use.uml.ocl.value.BooleanValue.TRUE.equals(mental.state(engine.system().state()).attributeValue("complete")))return "MENTAL_BB_CUT_INCOMPLETE";
        }
        String scheme=engine.metadata(self,"functionalScheme");
        if(!scheme.isBlank() && !freshBoards.contains(scheme))return "EXACT_FUNCTIONAL_BOARD_CUT_UNAVAILABLE";
        // A complete per-object cut is independent of a partial enumeration.
        // Explicit allInstances and authoritative relation populations still
        // require their own global completeness. Other profiles keep the
        // previous conservative admission policy.
        String reason=(legacyAuction(engine)||engine.xmlCase())?unavailablePopulations(expression,engine,sources,lost):unavailable(expression,engine,sources,lost);if(!reason.isBlank())return reason;
        var visitor=ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);
        if(legacyAuction(engine)||engine.xmlCase())for(var association:visitor.getCoverageData().getAssociationCoverage().keySet())if(association.getAnnotation("OriginalCslAssociation")!=null) {
            String source=engine.system().model().getAnnotationValue("CanonicalTupleAuthority","sourceId");if(source.isBlank()||sources.get(source)!=Completeness.COMPLETE)return "AUTHORITATIVE_ASSOCIATION_TUPLE_POPULATION_UNPROVEN:"+association.name();
        }
        if(engine.xmlCase())for(var navigation:visitor.navigationReads()) {
            String reference=xmlReferenceUnavailable(navigation,engine,self);if(!reference.isBlank())return reference;
        }
        for(var read:visitor.attributeReads()) {
            var candidates=receivers(read,engine,self);if(candidates==null)return "AUCTION_ATTRIBUTE_RECEIVER_UNAVAILABLE:"+read.attribute().owner().name()+"."+read.attribute().name();
            if(engine.xmlCase()&&read.attribute().getAnnotation("DeclarativeObserverView")!=null) {
                for(var object:candidates) {
                    if(!xmlObserverOwned(engine,object))return "XML_EXACT_OBSERVER_BINDING_UNAVAILABLE";
                    String quality=engine.metadata(object,"xml:observer:quality:"+read.attribute().name());
                    if(!Set.of("COMPLETE","HISTORICAL_COMPLETE").contains(quality))return (quality.equals("ERROR")?"ERROR:":quality.startsWith("UNSUPPORTED")?"UNSUPPORTED:":"")+"XML_OBSERVER_FIELD_UNAVAILABLE:"+read.attribute().name()+":"+quality;
                    if(!org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.valueHash(object.state(engine.system().state()).attributeValue(read.attribute())).equals(engine.metadata(object,"xml:observer:value-hash:"+read.attribute().name())))return "ERROR:XML_OBSERVER_VALUE_DIFFERS_FROM_ACCEPTED_OBSERVATION:"+object.name()+":"+read.attribute().name();
                }
            }
            if(legacyAuction(engine)&&read.attribute().getAnnotation(org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionObserverUseBinding.ANNOTATION)!=null) {
                var attribute=read.attribute();
                for(var object:candidates) {
                    String quality=engine.metadata(object,"auctionObserverQuality:"+attribute.name());
                    if(!quality.equals("COMPLETE"))return (quality.startsWith("UNSUPPORTED:")?"UNSUPPORTED:":quality.startsWith("ERROR:")?"ERROR:":"")+"AUCTION_OBSERVER_FIELD_UNAVAILABLE:"+attribute.name()+":"+(quality.isBlank()?"NO_ACCEPTED_OBSERVATION":quality);
                    if(!org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.valueHash(object.state(engine.system().state()).attributeValue(attribute)).equals(engine.metadata(object,"auctionObserverHash:"+attribute.name())))return "ERROR:AUCTION_OBSERVER_VALUE_DIFFERS_FROM_ACCEPTED_OBSERVATION:"+attribute.name();
                }
            }
            var attribute=read.attribute();String annotation=attribute.getAnnotation(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE)!=null?org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE:"CslAttribute";
            if(attribute.getAnnotation(annotation)==null)continue;
            boolean canonical=engine.canonicalMappingConfigured()&&annotation.equals(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE);
            boolean optional="true".equals(attribute.getAnnotationValue(annotation,"optional"));
            if(optional&&!canonical)continue;
            for(var object:candidates) {
                boolean caseObservation=canonical&&!"true".equals(engine.metadata(object,"propertyAbsent:"+attribute.name()))
                        &&(legacyAuction(engine)&&!engine.metadata(object,"auctionObserved:"+attribute.name()).isBlank()
                            ||engine.xmlCase()&&engine.canonicalProgramHash().equals(engine.metadata(object,"xml:m0:program"))&&"COMPLETE".equals(engine.metadata(object,"xml:m0:quality"))&&!engine.metadata(object,"observedValueHash:"+attribute.name()).isBlank());
                if(!caseObservation&&engine.metadata(object,"propertySlot:"+attribute.name()).isBlank()) {
                    if(canonical&&optional&&"true".equals(engine.metadata(object,"propertyAbsent:"+attribute.name())))continue;
                    return "REQUIRED_PROPERTY_OBSERVATION_UNAVAILABLE:"+attribute.owner().name()+"."+attribute.name()+":"+object.name();
                }
                if(canonical&&!org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.valueHash(object.state(engine.system().state()).attributeValue(attribute)).equals(engine.metadata(object,"observedValueHash:"+attribute.name())))
                    return (legacyAuction(engine)?"ERROR:":"")+"NATIVE_VALUE_DIFFERS_FROM_ACCEPTED_OBSERVATION:"+object.name()+"."+attribute.name();
            }
        }
        return "";
    }
    private List<MObject> receivers(ExternalOclConstraintService.DependencyVisitor.AttributeRead read,NativeRuntimeMutationEngine engine,MObject self) {
        if(read.directSelf()&&self.cls().isSubClassifierOf(read.attribute().owner()))return List.of(self);
        if(legacyAuction(engine)||engine.xmlCase())try {
            var variables=new org.tzi.use.uml.ocl.value.VarBindings(engine.system().state());variables.push("self",self.value());
            var value=new org.tzi.use.uml.ocl.expr.Evaluator().eval(read.receiver(),engine.system().state(),variables);
            if(value instanceof org.tzi.use.uml.ocl.value.ObjectValue object&&object.value().cls().isSubClassifierOf(read.attribute().owner()))return List.of(object.value());
            return null; // Unbound iterator/undefined navigation is not an empty observed population.
        }catch(RuntimeException unavailable){return null;}
        return engine.system().state().allObjects().stream().filter(o->o.cls().isSubClassifierOf(read.attribute().owner())).toList();
    }
    private String xmlReferenceUnavailable(org.tzi.use.uml.ocl.expr.ExpNavigation navigation,NativeRuntimeMutationEngine engine,MObject self) {
        var association=navigation.getDestination().association();
        if(!"EXTERNAL_OBSERVER_REFERENCE".equals(association.getAnnotationValue(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.HELPER,"kind")))return "";
        String field=org.tzi.use.semantic.engine.use75.Use75CslModelBackend.decode(association.getAnnotationValue("DeclarativeObserverView","field64"));
        // Inverse collections require separate population authority, not a forward-reference certificate.
        if(!field.equals(navigation.getDestination().nameAsRolename()))return "UNSUPPORTED:XML_OBSERVER_INVERSE_REFERENCE_COVERAGE";
        try {
            var variables=new org.tzi.use.uml.ocl.value.VarBindings(engine.system().state());variables.push("self",self.value());
            var source=new org.tzi.use.uml.ocl.expr.Evaluator().eval(navigation.getObjectExpression(),engine.system().state(),variables);
            if(!(source instanceof org.tzi.use.uml.ocl.value.ObjectValue object)||!xmlObserverOwned(engine,object.value()))return "XML_OBSERVER_REFERENCE_RECEIVER_UNAVAILABLE:"+field;
            String quality=engine.metadata(object.value(),"xml:observer:quality:"+field);
            if(!Set.of("COMPLETE","HISTORICAL_COMPLETE").contains(quality))return (quality.equals("ERROR")?"ERROR:":"")+"XML_OBSERVER_REFERENCE_UNAVAILABLE:"+field+":"+quality;
            String expected=engine.metadata(object.value(),"xml:observer:reference-target:"+field);
            var targets=object.value().getNavigableObjects(engine.system().state(),navigation.getSource(),navigation.getDestination(),List.of());
            if(expected.isBlank()||targets.size()!=1||!targets.getFirst().name().equals(expected)||!xmlObserverOwned(engine,targets.getFirst()))return "ERROR:XML_OBSERVER_REFERENCE_DIFFERS_FROM_ACCEPTED_OBSERVATION:"+object.value().name()+":"+field;
            return "";
        }catch(RuntimeException unavailable){return "XML_OBSERVER_REFERENCE_RECEIVER_UNAVAILABLE:"+field;}
    }
    /** Captured on the native writer; worker evidence never consults live metadata. */
    public Map<String,String> witness(Expression expression,NativeRuntimeMutationEngine engine,MObject self,Map<String,Completeness> sources,String unavailable) {
        var visitor=ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);var fields=new TreeMap<String,Object>();
        for(var read:visitor.attributeReads()) {
            var objects=receivers(read,engine,self);String field=read.attribute().owner().name()+"."+read.attribute().name();
            fields.put(field,objects==null?Map.of("quality","RECEIVER_UNAVAILABLE"):objects.stream().map(o->fieldWitness(engine,o,read.attribute().name())).toList());
        }
        return Map.of("observationQuality",unavailable.isBlank()?"COMPLETE_FOR_EXPRESSION":"UNAVAILABLE","coverageReason",unavailable,"sourceCompleteness",json(sources),"requiredFields",json(List.copyOf(fields.keySet())),"fieldWitnesses",json(fields),"requiredPopulations",json(visitor.populationTypes().stream().map(c->c.name()).sorted().toList()),"scopeProof",engine.metadata(self,"auctionGroupAdmissionCut"));
    }
    private static String json(Object value){return new String(org.jacamo.bridge.contract.CanonicalJson.encode(value),java.nio.charset.StandardCharsets.UTF_8);}
    private Map<String,String> fieldWitness(NativeRuntimeMutationEngine engine,MObject object,String field) {
        if(engine.xmlCase()&&xmlObserver(object))return Map.of("object",object.name(),"identity",engine.metadata(object,"xml:observer:subject"),"observerQuality",engine.metadata(object,"xml:observer:quality:"+field),"observerValueHash",engine.metadata(object,"xml:observer:value-hash:"+field),"sourceCut",engine.metadata(object,"xml:observer:cut"),"mappingPlanHash",engine.canonicalProgram().xmlPlan().hash(),"project",engine.metadata(object,"xml:observer:project"));
        var witness=new TreeMap<String,String>();witness.put("object",object.name());witness.put("identity",engine.metadata(object,"semanticId"));witness.put("observerQuality",engine.metadata(object,"auctionObserverQuality:"+field));witness.put("acceptedValueHash",engine.metadata(object,"observedValueHash:"+field));witness.put("observerValueHash",engine.metadata(object,"auctionObserverHash:"+field));witness.put("sourceCut",engine.metadata(object,"auctionObserved:"+field));
        witness.put("scheme",engine.metadata(object,"auctionMonitorScheme"));witness.put("artifact",engine.metadata(object,"auctionMonitorArtifact"));witness.put("observerCut",engine.metadata(object,"auctionMonitorCut"));witness.put("activation",engine.metadata(object,"auctionObligationActivation"));
        String source=engine.metadata(object,"auctionObligationSource");witness.put("normSourceHash",source.isBlank()?"":ExternalOclConstraintService.sha256(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));return Map.copyOf(witness);
    }
    public static org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict unavailableVerdict(String reason) {
        while(reason.startsWith("PRE_STATE_"))reason=reason.substring("PRE_STATE_".length());
        return reason.startsWith("UNSUPPORTED:")?org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.UNSUPPORTED:reason.startsWith("ERROR:")?org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.ERROR:org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED;
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
    public boolean populationComplete(org.tzi.use.uml.mm.MClassifier cls,Map<String,Completeness> sources,NativeRuntimeMutationEngine engine) {
        if(!populationComplete(cls,sources))return false;
        if(engine!=null&&engine.xmlCase()&&(kind(cls,"DomainEntity")||kind(cls,"OrgContext")||kind(cls,"RoleOccurrence"))) {
            var objects=engine.system().state().allObjects().stream().filter(o->o.cls().isSubClassifierOf(cls)).toList();
            return !objects.isEmpty()&&objects.stream().allMatch(o->engine.canonicalProgramHash().equals(engine.metadata(o,"xml:m0:program"))&&"COMPLETE".equals(engine.metadata(o,"xml:m0:quality"))&&"COMPLETE".equals(engine.metadata(o,"xml:m0:population")));
        }
        if(engine==null||!legacyAuction(engine)||!kind(cls,"DomainEntity"))return true;
        var witnesses=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("WorkspaceObservation")).toList();
        return !witnesses.isEmpty()&&witnesses.stream().allMatch(o->"COMPLETE".equals(engine.metadata(o,"auctionDomainPopulationQuality")));
    }
    private static boolean legacyAuction(NativeRuntimeMutationEngine engine){return engine!=null&&engine.auctionCase()&&!engine.xmlCase();}
    private static boolean xmlObserver(MObject object){return object.cls().getAnnotation("DeclarativeObserverView")!=null&&object.cls().getAnnotationValue(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.HELPER,"kind").equals("EXTERNAL_OBSERVER_VIEW");}
    private static boolean xmlObserverOwned(NativeRuntimeMutationEngine engine,MObject object) {
        return xmlObserver(object)&&engine.canonicalProgramHash().equals(engine.metadata(object,"xml:observer:program"))
            &&engine.canonicalProgram().xmlPlan().inputs().xml().sha256().equals(object.cls().getAnnotationValue("DeclarativeObserverView","mappingHash"))
            &&!engine.metadata(object,"xml:observer:epoch").isBlank()&&!engine.metadata(object,"xml:observer:key").isBlank()&&!engine.metadata(object,"xml:observer:cut").isBlank();
    }
    private static boolean kind(org.tzi.use.uml.mm.MClassifier cls,String name) {
        return cls.name().equals(name) || cls.allParents().stream().anyMatch(p->p.name().equals(name));
    }
    private static boolean organizationComplete(Map<String,Completeness> sources) {
        var selected=sources.entrySet().stream().filter(e->e.getKey().startsWith("moise:")).toList();
        return !selected.isEmpty() && selected.stream().allMatch(e->e.getValue()==Completeness.COMPLETE);
    }
}

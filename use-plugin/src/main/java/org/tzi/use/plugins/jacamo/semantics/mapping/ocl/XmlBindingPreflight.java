package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.use75.NativeOclBackend;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;

/** Compiler-AST read inventory joined to explicit XML writes. This is a
 * declaration diagnostic, never a claim of observed availability or completeness. */
public final class XmlBindingPreflight {
    private XmlBindingPreflight() { }
    public static Map<String,Object> inspect(SemanticProgram program,MModel model,
            AuthoredOclSpecificationLoader.Prepared authored,JaCaMoOclContractMapping.Result intentional) {
        var declarations=new TreeMap<String,List<Map<String,String>>>();
        var root=program.xmlPlan().inputs().specification().root();
        for(var rule:root.child("runtime").children("rule"))for(var write:rule.children())
            if(Set.of("upsertInstance","assignSnapshot").contains(write.name()))for(var assign:write.children("assign")) {
                MClass type=model.getClass(write.attr("targetType"));var attribute=type.attribute(assign.attr("target"),true);
                String field=attribute.owner().name()+"::"+attribute.name();
                declarations.computeIfAbsent(field,k->new ArrayList<>()).add(Map.of("rule",rule.attr("id"),"input",rule.attr("inputRef"),"sourceField",assign.attr("sourceField"),"converter",assign.attr("converter")));
            }
        var expressions=new TreeMap<String,Expression>();
        for(var section:root.child("runtime").children("beliefBindings"))for(var node:section.children("bind")) {
            var attribute=model.getClass(node.attr("roleType")).attribute(node.attr("attribute"),true);
            declarations.computeIfAbsent(attribute.owner().name()+"::"+attribute.name(),k->new ArrayList<>()).add(Map.of("rule",node.attr("id"),"input",node.attr("datasetRef"),"sourceField","beliefJson","converter","EXACT_STORED_LITERAL_JSON"));
        }
        for(var clause:authored.clauses())expressions.put("OCL:"+clause.specification().name(),
                clause.invariant()==null?clause.expression():clause.invariant().expandedExpression());
        // CSL-authored invariants are production requirements too. Their native
        // forall expansion includes the context population, even without an
        // explicit allInstances call in the authored body.
        for(var invariant:authored.cslInvariants())expressions.put("CSL:"+invariant.qualifiedName(),invariant.expandedExpression());
        try(var backend=new NativeOclBackend(model,program.hash(),program.profile().engine().name())) {
            for(var contract:intentional.contracts())expressions.putIfAbsent(contract.id(),backend.compile(contract.specification()).expression());
        }
        var reads=new ArrayList<Map<String,Object>>();
        for(var entry:expressions.entrySet()) {
            var visitor=ExternalOclConstraintService.dependencyVisitor();entry.getValue().processWithVisitor(visitor);
            var seen=new TreeSet<String>();
            for(var read:visitor.attributeReads()) {
                var attribute=read.attribute();String field=attribute.owner().name()+"::"+attribute.name();if(!seen.add(field+":"+read.preState()))continue;
                var writes=declarations.getOrDefault(field,List.of());
                boolean observer="EXTERNAL_OBSERVER_VIEW".equals(attribute.owner().getAnnotationValue(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.HELPER,"kind"));
                var origins=new ArrayList<Map<String,Object>>();
                for(var write:writes)origins.addAll(org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.SelectedObservationPlanner.origins(program.xmlPlan().inputs().specification(),write.get("input")));
                reads.add(Map.of("contract",entry.getKey(),"field",field,"directSelf",read.directSelf(),"preState",read.preState(),"mappingRules",writes,"sources",origins.stream().distinct().toList(),
                        "status",writes.isEmpty()?(observer?"OBSERVER_REQUIRES_QUALIFIED_PROJECTION":"INCOMPLETE_NO_DECLARED_WRITE"):"DECLARED_NOT_OBSERVED"));
            }
            for(var type:visitor.populationTypes().stream().sorted(Comparator.comparing(MClassifier::name)).toList())reads.add(Map.of("contract",entry.getKey(),"population",type.name(),"status","REQUIRES_COMPLETE_POPULATION"));
            for(var scoped:visitor.scopedNavigationReads()){var navigation=scoped.expression();reads.add(Map.of("contract",entry.getKey(),
                    "association",navigation.getDestination().association().name(),"fromType",navigation.getSource().cls().name(),
                    "toType",navigation.getDestination().cls().name(),"fromRole",navigation.getSource().nameAsRolename(),
                    "toRole",navigation.getDestination().nameAsRolename(),"preState",scoped.preState(),"status","REQUIRES_COMPLETE_LINK_POPULATION"));}
        }
        var readSet=ObservationReadSet.compile(expressions);
        var scope=org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.SelectedObservationPlanner.compile(program.xmlPlan().inputs().specification(),readSet.hash(),program.xmlPlan().requirements());
        return org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.object(Map.of("version","XML_BINDING_PREFLIGHT/1.0","reads",List.copyOf(reads),"readSet",readSet.evidence(),"readSetHash",readSet.hash(),"captureScope",scope.evidence(),"captureScopeHash",scope.hash(),"capabilities",program.xmlPlan().requirements().stream().map(r->Map.of("id",r.id(),"provider",r.provider(),"required",r.required(),"status","REQUESTED_NOT_OBSERVED")).toList()));
    }
}

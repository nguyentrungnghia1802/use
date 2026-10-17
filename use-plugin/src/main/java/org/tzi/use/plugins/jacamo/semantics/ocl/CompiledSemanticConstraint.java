package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.tzi.use.uml.ocl.expr.Expression;

/** Native AST plus immutable dependency/version metadata. AST evaluation always uses pinned caller states. */
public record CompiledSemanticConstraint(String id,String kind,String context,String body,String modelRevision,String profileHash,
        Expression expression,Set<String> populationTypes,Set<String> attributes,Set<String> associations) {
    public CompiledSemanticConstraint {Objects.requireNonNull(expression);populationTypes=Set.copyOf(populationTypes);attributes=Set.copyOf(attributes);associations=Set.copyOf(associations);}
    public static CompiledSemanticConstraint of(String id,String kind,String context,String body,String revision,String profile,Expression expression) {
        var visitor=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);var data=visitor.getCoverageData();
        return new CompiledSemanticConstraint(id,kind,context,body,revision,profile,expression,visitor.populationTypes().stream().map(org.tzi.use.uml.mm.MClassifier::name).collect(java.util.stream.Collectors.toSet()),data.getAttributeCoverage().keySet().stream().map(a->a.owner().name()+"."+a.name()).collect(java.util.stream.Collectors.toSet()),data.getAssociationCoverage().keySet().stream().map(org.tzi.use.uml.mm.MAssociation::name).collect(java.util.stream.Collectors.toSet()));
    }
}

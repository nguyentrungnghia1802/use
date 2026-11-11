package org.jacamo.bridge.adapter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;

/** Provenance only: projection and verification demand remain in USE. */
final class JasonBeliefEvidence {
    private JasonBeliefEvidence() { }

    static boolean projectSource(String uri) {
        // AdapterEvidence emits digest-validated project: URIs; library/external evidence has jar:/file: URIs.
        return uri != null && uri.startsWith("project:/") && !uri.substring(9).contains("../")
                && !uri.substring(9).startsWith("external/");
    }

    static boolean authoritativeElsewhere(Literal literal) {
        return literal.getAnnot("artifact_id") != null || literal.getAnnot("percept_type") != null
                || literal.hasSource(ASSyntax.createAtom("percept"));
    }

    static boolean hidden(Literal literal) {
        return literal.hasAnnot(ASSyntax.createAtom("hide_in_mind_inspector"));
    }

    static Set<String> authoredPredicates(AgentProgramSemantic program) {
        var result = new TreeSet<String>();
        program.beliefs().stream().filter(b -> b.metadata().sourceKind().equals("JASON_DOMAIN_BELIEF"))
                .forEach(b -> addPredicate(result, b.literal()));
        for (var plan : program.planLibrary().plans()) {
            if (plan.metadata().evidence().stream().noneMatch(e -> projectSource(e.sourceUri()))) continue;
            for (var body : plan.body()) if (Set.of("addBel", "addBelNewFocus", "addBelBegin", "addBelEnd", "delAddBel").contains(body.bodyType()))
                addPredicate(result, body.term());
        }
        return Set.copyOf(result);
    }

    private static void addPredicate(Set<String> result, String text) {
        try { result.add(ASSyntax.parseLiteral(text).getPredicateIndicator().toString()); }
        catch (Exception unsupported) { /* No exact literal evidence: never infer authorship. */ }
    }

    static Map<String, Object> provenance(Literal literal, Set<String> authored) {
        var result = new LinkedHashMap<String, Object>();
        result.put("predicateIndicator", literal.getPredicateIndicator().toString());
        result.put("domainAuthored", authored.contains(literal.getPredicateIndicator().toString()) && !hidden(literal));
        result.put("authoritativeElsewhere", authoritativeElsewhere(literal));
        result.put("hidden", hidden(literal));
        return Map.copyOf(result);
    }
}

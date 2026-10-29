package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.mapping.SourceModelNormalizer;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import static org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionMappingSpecification.Node;
import static org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionSelectorResult.Row;

/** Executes the pinned fragment selectors. Official parsers remain the source model authority. */
public final class AuctionSourceSelector {
    private static final int MAX_ROWS = 4096, MAX_REGEX = 8192, MAX_WORK = 2_000_000;

    public AuctionSelectorResult execute(AuctionMappingSpecification spec, AuctionCaseBundle bundle, ModelSnapshot source) {
        return execute(spec,bundle,source,null);
    }
    public AuctionSelectorResult execute(AuctionMappingSpecification spec,AuctionCaseBundle bundle,ModelSnapshot source,AuctionJcmDerivation derivation) {
        if(derivation!=null)derivation.requireOwnership(bundle,source);
        if (source.semanticContract().project() == null) throw spec.root().location().error("OFFICIAL_SOURCE_REQUIRED");
        if (!spec.sourceHash().equals(bundle.require("case-rules").sha256())) throw spec.root().location().error("SPECIFICATION_BUNDLE_MISMATCH");
        new AuctionMappingValidator().validate(spec, bundle);
        var selected = new LinkedHashMap<String, List<Row>>(); int count = 0;
        for (Node selector : spec.selectors()) {
            var rows = select(selector, bundle.require(selector.attr("sourceRef")).bytes(), spec.namespaces());
            count += rows.size();
            if (count > 16384) throw selector.location().error("TOTAL_SELECTOR_ROW_CAPACITY");
            selected.put(selector.attr("id"), rows);
        }
        var result = new AuctionSelectorResult(bundle.hash(), spec.sourceHash(), new SourceModelNormalizer().normalize(source).sourceHash(), selected,
                List.of("CONFIGURED_PLAYERS_ARE_NOT_ENACTMENTS", "REQUESTS_ARE_NOT_ARTIFACT_INSTANCES", "RAW_JAVA_FRAGMENT_REQUIRES_R01_BYTECODE_PROOF"));
        verifyOfficial(spec, bundle, source, result,derivation);
        requireConditions(spec, result);
        return result;
    }

    /** Compatibility adapter; the bounded selector implementation is shared with XML v2. */
    public List<Row> select(Node selector,byte[] bytes,Map<String,String> namespaces) {
        return new org.tzi.use.plugins.jacamo.semantics.mapping.xml.SourceSelectors().select(neutral(selector),bytes,namespaces)
            .stream().map(r->new Row(r.selectorId(),r.sourceId(),r.sourceDigest(),r.values(),r.line(),r.column(),r.locator(),r.matched())).toList();
    }
    private static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node neutral(Node node) {
        var at=node.location();
        return new org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node(node.name(),node.attributes(),node.text(),
            node.children().stream().map(AuctionSourceSelector::neutral).toList(),
            new org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Location(at.source(),at.line(),at.column(),at.ruleId()));
    }

    private static void requireConditions(AuctionMappingSpecification spec, AuctionSelectorResult result) {
        for (Node node : spec.nodes()) switch (node.name()) {
            case "require", "initializerDependency" -> {
                var rows = result.require(node.attr("selectorRef"));
                if (rows.isEmpty() || rows.stream().anyMatch(row -> !node.attr("equals").equals(row.values().get(node.attr("field"))))) throw node.location().error("REQUIRE_VALUE_MISMATCH");
            }
            case "requireSet" -> {
                if (!values(result, node.attr("selectorRef"), node.attr("field"), node).equals(Set.copyOf(List.of(node.attr("values").split("\\s+"))))) throw node.location().error("REQUIRE_SET_MISMATCH");
            }
            case "requireSameSet" -> {
                if (!values(result, node.attr("leftSelectorRef"), node.attr("leftField"), node).equals(values(result, node.attr("rightSelectorRef"), node.attr("rightField"), node))) throw node.location().error("REQUIRE_SAME_SET_MISMATCH");
            }
            default -> { }
        }
        for (Node rule : spec.modelRules()) for (Node population : rule.children("populationCase")) {
            var matching = result.require(rule.attr("selectRef")).stream().filter(row -> population.attr("sourceRole").equals(row.values().get("id"))).toList();
            if (matching.size() != 1 || !population.attr("expectedMin").equals(matching.getFirst().values().get("min")) || !population.attr("expectedMax").equals(matching.getFirst().values().get("max"))) throw population.location().error("POPULATION_BOUND_MISMATCH");
        }
    }
    private static Set<String> values(AuctionSelectorResult result, String selector, String field, Node at) {
        var values = new HashSet<String>();
        for (Row row : result.require(selector)) { String value = row.values().get(field); if (value == null) throw at.location().error("CAPTURE_FIELD_REQUIRED:" + field); values.add(value); }
        return values;
    }

    private static void verifyOfficial(AuctionMappingSpecification spec, AuctionCaseBundle bundle, ModelSnapshot snapshot, AuctionSelectorResult result,AuctionJcmDerivation derivation) {
        var source = snapshot.semanticContract(); var agentNames = new HashSet<String>(); source.agentDeclarations().forEach(agent -> agentNames.add(agent.name()));
        for (Node file : spec.root().child("sources").children("file")) {
            String digest = bundle.require(file.attr("id")).sha256();
            switch (file.attr("purpose")) {
                case "application-configuration" -> { if (!source.project().sourceDigest().equals(digest)
                        && (derivation==null||!source.project().sourceDigest().equals(derivation.derivedHash()))) throw file.location().error("OFFICIAL_JCM_DIGEST_MISMATCH"); }
                case "agent-behavior" -> { if (source.jasonPrograms().stream().noneMatch(program -> program.sourceDigest().equals(digest))) throw file.location().error("OFFICIAL_ASL_DIGEST_MISMATCH"); }
                case "organizational-specification" -> { if (source.moiseOrganizations().stream().noneMatch(org -> org.metadata().evidence().stream().anyMatch(e -> e.sourceDigest().equals(digest)))) throw file.location().error("OFFICIAL_OS_DIGEST_MISMATCH"); }
                default -> { }
            }
        }
        for (Node selector : spec.selectors()) {
            var rows = result.require(selector.attr("id"));
            if (!rows.isEmpty() && rows.getFirst().values().containsKey("program")) {
                if (!values(result, selector.attr("id"), "agent", selector).equals(agentNames)) throw selector.location().error("OFFICIAL_AGENT_DECLARATIONS_MISMATCH");
            }
            for (Row row : rows) {
                var value = row.values();
                if (value.containsKey("agent") && !agentNames.contains(value.get("agent"))) throw selector.location().error("OFFICIAL_AGENT_UNRESOLVED");
                if (value.containsKey("instance") && value.containsKey("spec")) {
                    boolean found = row.matched().startsWith("organisation")
                            ? source.organizationDeployments().stream().anyMatch(org -> org.name().equals(value.get("instance")) && organizationSourceMatches(org,value.get("spec"),bundle,source))
                            : source.groupDeployments().stream().anyMatch(group -> group.name().equals(value.get("instance")) && group.type().equals(value.get("spec")));
                    if (!found) throw selector.location().error("OFFICIAL_DEPLOYMENT_MISMATCH");
                }
                if (value.containsKey("min") && value.containsKey("max")) {
                    boolean found = source.moiseOrganizations().stream().anyMatch(org -> {
                        var roles = new HashMap<String, String>(); org.structuralSpecification().roles().forEach(role -> roles.put(role.metadata().semanticId(), role.roleId()));
                        return org.structuralSpecification().groupRoleCardinalities().stream().anyMatch(card -> value.get("id").equals(roles.get(card.roleId())) && Integer.toString(card.min()).equals(value.get("min")) && Integer.toString(card.max()).equals(value.get("max")));
                    });
                    if (!found) throw selector.location().error("OFFICIAL_ROLE_BOUND_MISMATCH");
                }
                if (value.containsKey("className")) {
                    String exact = row.matched().replaceAll("\\s+", "");
                    if (source.jasonPrograms().stream().flatMap(program -> program.actions().stream()).noneMatch(action -> action.functor().equals("makeArtifact") && action.arity() == 4 && action.term().equals(exact))) throw selector.location().error("OFFICIAL_ARTIFACT_CREATION_ACTION_MISMATCH");
                }
                if (value.containsKey("operator")) {
                    if (source.moiseOrganizations().stream().flatMap(org -> org.functionalSpecification().schemes().stream()).flatMap(scheme -> scheme.plans().stream()).noneMatch(plan -> plan.operator().equals(value.get("operator")))) throw selector.location().error("OFFICIAL_PLAN_OPERATOR_MISMATCH");
                }
            }
        }
    }

    static boolean organizationSourceMatches(org.jacamo.bridge.contract.semantic.JcmSemanticContract.OrganizationDeploymentSemantic deployment,String declared,AuctionCaseBundle bundle,
            org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot source) {
        if(deployment.source().equals(declared))return true;
        // The official Moise platform resolves its source parameter at start:
        // auction-os.xml becomes file:src/org/auction-os.xml. Retain the actual
        // DTO and accept only the exact frozen bundle path + exact OS evidence,
        // never a basename/suffix match or an arbitrary external file URI.
        var input=bundle.inputs().get("src-os");if(input==null||!declared.equals(java.nio.file.Path.of(input.path()).getFileName().toString())||!deployment.source().equals("file:"+input.path()))return false;
        String hash=bundle.require("src-os").sha256(),uri="project:/"+input.path();
        return deployment.metadata().evidence().stream().anyMatch(e->e.sourceDigest().equals(source.project().sourceDigest()))
                &&source.moiseOrganizations().stream().anyMatch(o->o.metadata().evidence().stream().anyMatch(e->e.sourceUri().equals(uri)&&e.sourceDigest().equals(hash)
                    &&e.authority()==org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_MOISE_API&&e.fidelity()==org.jacamo.bridge.contract.semantic.Fidelity.EXACT&&e.capabilityStatus()==org.jacamo.bridge.contract.CapabilityStatus.COMPLETE));
    }

}

package org.tzi.use.plugins.jacamo.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.tzi.use.plugins.jacamo.materialization.*;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;

/** Generic order projection. No domain class names or ordering from link iteration. */
public final class OrderProjectionPlanner {
    public List<OrderProjectionSpec> specifications(JsonNode mapping) {
        if (!"TARGET_ONLY_INDEPENDENT_RANKS".equals(mapping.path("orderProjection").path("kind").asText()))
            throw new MappingException("ORDER_CONTRACT_MISSING", "Explicit independent rank contract required");
        Map<String, JsonNode> rules = new HashMap<>();
        mapping.path("referenceMappings").forEach(r -> rules.put(r.path("id").asText(), r));
        List<OrderProjectionSpec> result = new ArrayList<>();
        for (JsonNode rule : mapping.path("referenceMappings")) {
            if (!rule.path("sourceOrdered").asBoolean() || rule.path("sourceMultiplicity").path("upper").asInt() == 1) continue;
            boolean reverse = rule.path("target").has("aliasOf");
            JsonNode canonical = reverse ? rules.get(rule.path("target").path("aliasOf").asText()) : rule;
            if (canonical == null) throw new MappingException("ORDER_ALIAS_MISSING", rule.toString());
            JsonNode target = canonical.path("target");
            result.add(new OrderProjectionSpec(rule.path("source").asText(), rule.path("id").asText(),
                    rule.path("sourceOwner").asText(), rule.path("sourceTarget").asText(), target.path("name").asText(),
                    reverse, target.path(reverse ? "firstEnd" : "secondEnd").path("role").asText()));
        }
        return result.stream().sorted(Comparator.comparing(OrderProjectionSpec::sourceIdentity)).toList();
    }

    /** Authoritative list, including an explicit empty list. Object identities are resolved before this boundary. */
    public record SourceOrder(String sourceIdentity, String ownerObject, List<String> targets) {
        public SourceOrder { targets = List.copyOf(targets); }
    }

    public InstancePlan project(TransformationPlan structure, InstancePlan membership, List<SourceOrder> sourceOrders) {
        Map<String, ObjectPlan> objects = new TreeMap<>();
        membership.objects().forEach(o -> objects.put(o.name(), o));
        Map<String, SourceOrder> supplied = new HashMap<>();
        for (var order : sourceOrders) {
            if (supplied.put(key(order.sourceIdentity(), order.ownerObject()), order) != null)
                throw new MappingException("ORDER_DUPLICATE_SOURCE", order.toString());
        }
        List<ObjectPlan> outputObjects = new ArrayList<>(membership.objects());
        List<LinkPlan> outputLinks = new ArrayList<>(membership.links());
        Set<String> consumed = new HashSet<>();
        for (var spec : structure.orderProjections()) for (var owner : membership.objects()) {
            if (!isA(owner.className(), spec.owner(), structure, new HashSet<>())) continue;
            String key = key(spec.sourceIdentity(), owner.name());
            SourceOrder order = supplied.get(key);
            if (order == null) throw new MappingException("ORDER_SOURCE_MISSING", key);
            consumed.add(key);
            Set<String> expected = new TreeSet<>();
            for (var link : membership.links()) if (link.association().equals(spec.association()) &&
                    (spec.reverse() ? link.targetObject() : link.sourceObject()).equals(owner.name()))
                expected.add(spec.reverse() ? link.sourceObject() : link.targetObject());
            if (new HashSet<>(order.targets()).size() != order.targets().size() || !expected.equals(new TreeSet<>(order.targets())))
                throw new MappingException("ORDER_MEMBERSHIP_MISMATCH", key);
            for (int rank = 0; rank < order.targets().size(); rank++) {
                ObjectPlan target = objects.get(order.targets().get(rank));
                if (target == null || !isA(target.className(), spec.target(), structure, new HashSet<>()))
                    throw new MappingException("ORDER_TARGET_MISMATCH", key);
                String identity = key(spec.sourceIdentity(), key(owner.semanticId(), target.semanticId()));
                String name = rowName(identity);
                if (objects.containsKey(name)) throw new MappingException("ORDER_NAME_COLLISION", name);
                outputObjects.add(new ObjectPlan(name, spec.entryClass(), identity, Map.of("rank", new AttributeValue.IntegerNumber(rank))));
                outputLinks.add(new LinkPlan(spec.ownerAssociation(), owner.name(), name, false, owner.semanticId(), identity, spec.ruleId()));
                outputLinks.add(new LinkPlan(spec.targetAssociation(), name, target.name(), false, identity, target.semanticId(), spec.ruleId()));
            }
        }
        if (!consumed.equals(supplied.keySet())) throw new MappingException("ORDER_SOURCE_UNKNOWN", supplied.keySet().toString());
        return new InstancePlan(outputObjects, outputLinks, membership.diagnostics());
    }

    private boolean isA(String actual, String required, TransformationPlan plan, Set<String> visited) {
        if (actual.equals(required)) return true;
        if (!visited.add(actual)) return false;
        return plan.classes().stream().filter(c -> c.name().equals(actual)).flatMap(c -> c.superclasses().stream())
                .anyMatch(c -> isA(c, required, plan, visited));
    }
    private String key(String a, String b) { return a.length() + ":" + a + b.length() + ":" + b; }
    public static String rowName(String identity) {
        try { return "order_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}

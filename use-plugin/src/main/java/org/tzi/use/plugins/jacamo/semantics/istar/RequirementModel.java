package org.tzi.use.plugins.jacamo.semantics.istar;

import java.util.*;
import static org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.required;
import org.tzi.use.plugins.jacamo.semantics.profile.SemanticsProfile;

/** Authored intentional model and exact occurrence bindings; never inferred from active Jason goals. */
public record RequirementModel(List<Node> nodes) {
    public enum Temporal { NONE, ACHIEVE, MAINTAIN, SUSTAIN }
    public enum Kind { GOAL, TASK }
    public enum Refinement { NONE, AND, OR }
    public record Binding(String actorId, String playerIdentity, String roleTypeId, String contextIdentity,
            String selfIdentity, String selfTypeId, String episodeId) {
        public Binding {
            required(actorId); required(playerIdentity); required(roleTypeId); required(contextIdentity);
            required(selfIdentity); required(selfTypeId); required(episodeId);
        }
    }
    public record Node(String id, String requirementId, Kind kind, Temporal temporal, Binding binding,
            String condition, String activation, Refinement refinement, List<String> children,
            String dependency, String contractId) {
        public Node {
            required(id); required(requirementId); Objects.requireNonNull(kind); Objects.requireNonNull(temporal);
            Objects.requireNonNull(binding); Objects.requireNonNull(refinement);
            condition = Objects.requireNonNullElse(condition, "");
            activation = Objects.requireNonNullElse(activation, "true");
            children = List.copyOf(children); dependency = Objects.requireNonNullElse(dependency, "");
            contractId = Objects.requireNonNullElse(contractId, "");
            int sources = (condition.isBlank() ? 0 : 1) + (children.isEmpty() ? 0 : 1) + (dependency.isBlank() ? 0 : 1);
            if (kind == Kind.GOAL && sources != 1)
                throw new IllegalArgumentException("GOAL_REQUIRES_ONE_PROPAGATION_SOURCE:" + id);
            if (children.isEmpty() != (refinement == Refinement.NONE))
                throw new IllegalArgumentException("REFINEMENT_CHILDREN_REQUIRED:" + id);
            if (new HashSet<>(children).size() != children.size())
                throw new IllegalArgumentException("DUPLICATE_REFINEMENT_CHILD:" + id);
            if (kind == Kind.TASK && (contractId.isBlank() || sources != 0))
                throw new IllegalArgumentException("TASK_INVOCATION_CONTRACT_REQUIRED:" + id);
        }
    }
    public RequirementModel {
        nodes = List.copyOf(nodes);
        var index = new HashMap<String, Node>();
        for (var node : nodes) if (index.put(node.id(), node) != null)
            throw new IllegalArgumentException("REQUIREMENT_OCCURRENCE_DUPLICATED:" + node.id());
        for (var node : nodes) visit(node, index, new HashSet<>(), new HashSet<>());
    }
    private static void visit(Node node, Map<String, Node> index, Set<String> path, Set<String> done) {
        if (done.contains(node.id())) return;
        if (!path.add(node.id())) throw new IllegalArgumentException("INTENTIONAL_CYCLE_UNSUPPORTED:" + node.id());
        var dependencies = new ArrayList<>(node.children());
        if (!node.dependency().isBlank()) dependencies.add(node.dependency());
        for (String id : dependencies) {
            var target = index.get(id);
            if (target == null) throw new IllegalArgumentException("REQUIREMENT_BINDING_UNRESOLVED:" + id);
            visit(target, index, path, done);
        }
        path.remove(node.id()); done.add(node.id());
    }
    public void admit(SemanticsProfile profile) {
        for (var node : nodes) {
            profile.require("istar." + (node.kind() == Kind.TASK ? "task" : node.temporal().name().toLowerCase(Locale.ROOT)));
            if (node.refinement() != Refinement.NONE)
                profile.require("istar." + node.refinement().name().toLowerCase(Locale.ROOT));
            if (!node.dependency().isBlank()) profile.require("istar.dependency");
        }
    }
}

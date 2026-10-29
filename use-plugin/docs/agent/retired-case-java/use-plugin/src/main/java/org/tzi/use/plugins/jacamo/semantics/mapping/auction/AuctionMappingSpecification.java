package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;

/** Located immutable syntax, not a validation receipt or an engine capability. */
public final class AuctionMappingSpecification {
    public record Location(String source, int line, int column, String ruleId) {
        public IllegalArgumentException error(String reason) {
            return new IllegalArgumentException("MAPPING_XML:" + source + ":" + line + ":" + column + ":" + ruleId + ":" + reason);
        }
    }
    public record Node(String name, Map<String, String> attributes, String text, List<Node> children, Location location) {
        public Node { attributes = Map.copyOf(attributes); children = List.copyOf(children); }
        public String attr(String name) {
            var value = attributes.get(name);
            if (value == null) throw location.error("ATTRIBUTE_REQUIRED:" + name);
            return value;
        }
        public String optional(String name) { return attributes.getOrDefault(name, ""); }
        public List<Node> children(String kind) { return children.stream().filter(n -> n.name().equals(kind)).toList(); }
        public Node child(String kind) {
            var matching = children(kind);
            if (matching.size() != 1) throw location.error("EXACTLY_ONE_CHILD_REQUIRED:" + kind);
            return matching.getFirst();
        }
    }

    private final String sourceHash;
    private final Node root;
    private final Map<String, String> namespaces;
    private final Map<String, Node> ids;
    private final List<Node> nodes;

    AuctionMappingSpecification(String sourceHash, Node root, Map<String, String> namespaces) {
        this.sourceHash = sourceHash; this.root = Objects.requireNonNull(root);
        this.namespaces = Map.copyOf(namespaces);
        var index = new LinkedHashMap<String, Node>(); var all = new ArrayList<Node>();
        collect(root, index, all);
        this.ids = Collections.unmodifiableMap(index); this.nodes = List.copyOf(all);
    }
    public String sourceHash() { return sourceHash; }
    public Map<String, String> namespaces() { return namespaces; }
    public Node root() { return root; }
    public Map<String, Node> byId() { return ids; }
    public List<Node> nodes() { return nodes; }
    public Node requireId(String id) {
        var value = ids.get(id);
        if (value == null) throw root.location().error("UNRESOLVED_ID:" + id);
        return value;
    }
    public List<Node> selectors() { return root.child("selectors").children("select"); }
    public List<Node> modelRules() { return root.child("modelRules").children("rule"); }
    public List<Node> runtimeRules() { return root.child("runtimeMapping").child("rules").children("rule"); }
    public List<Node> views() { return root.child("observerSupport").child("views").children("view"); }
    private static void collect(Node node, Map<String, Node> ids, List<Node> all) {
        all.add(node);
        if (node.attributes().containsKey("id")) {
            String id = node.attr("id");
            if (!id.matches("[A-Za-z_][A-Za-z0-9_.-]*") || ids.putIfAbsent(id, node) != null)
                throw node.location().error("INVALID_OR_DUPLICATE_ID:" + id);
        }
        node.children().forEach(child -> collect(child, ids, all));
    }
}

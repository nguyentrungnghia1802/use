package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.util.*;
import java.util.regex.*;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.SourceSelectors.Row;

/** M1-only interpreter. It emits source and calls the original compiler; it never creates live objects. */
public final class CslRuleExecutor {
    public record Trace(String ruleId, Row row, String target, String drawioCell) { }
    public record Omission(String id, String concept, String handledBy, String reason, String disposition, List<Node> traces) {
        public Omission { traces = List.copyOf(traces); }
    }
    public record Result(CslModel model, String generatedSource, String fingerprint, List<Trace> traces, List<Omission> omissions) {
        public Result { traces = List.copyOf(traces); omissions = List.copyOf(omissions); }
    }
    private static final Pattern TEMPLATE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final class Classifier {
        final String kind, name, owner; final List<String> attributes = new ArrayList<>(); final Set<String> attributeNames = new HashSet<>();
        Classifier(String kind, String name, String owner) { this.kind = kind; this.name = name; this.owner = owner; }
    }

    public CslModel generate(MappingInputs inputs) {return prepare(inputs).model();}
    public Result prepare(MappingInputs inputs) {
        return execute(inputs.specification().root().child("modelRules"),inputs.specification().root().child("omittedConcepts").children("omit"),SourceSelections.execute(inputs));
    }
    /** Low-level M1 instructions after dialect-specific selection/assertion admission.
     * prepare() executes the v2 selection gate; the legacy caller retains its own gate. */
    public Result execute(Node modelRules,List<Node> omitted,Map<String,List<Row>> selected) {
        var classifiers = new LinkedHashMap<String, Classifier>(); var invariants = new ArrayList<String>(); var traces = new ArrayList<Trace>();
        for (Node rule : modelRules.children("rule")) for (Row row : SourceSelections.require(selected,rule.attr("selectRef"),rule)) for (Node operation : rule.children()) {
            switch (operation.name()) {
                case "createClassifier" -> {
                    String name = identifier(render(operation.attr("name"), row, operation), operation);
                    String owner = render(operation.optional("owner"), row, operation);
                    if (!owner.isEmpty() && (!classifiers.containsKey(owner) || !classifiers.get(owner).kind.equals("OrgCtx"))) throw operation.location().error("M1_OWNER_UNRESOLVED");
                    if (classifiers.putIfAbsent(name, new Classifier(operation.attr("kind"), name, owner)) != null) throw operation.location().error("M1_CLASSIFIER_DUPLICATE:" + name);
                    traces.add(trace(rule, operation, row, name));
                }
                case "createAttribute" -> {
                    String name = identifier(render(operation.attr("name"), row, operation), operation);
                    String owner = render(operation.attr("owner"), row, operation), defaultLiteral = "";
                    if (operation.attributes().containsKey("defaultFrom")) {
                        if (!operation.attr("defaultEncoding").equals("ocl-string")) throw operation.location().error("M1_DEFAULT_ENCODING_UNSUPPORTED");
                        String value = row.values().get(operation.attr("defaultFrom"));
                        if (value == null) throw operation.location().error("M1_DEFAULT_CAPTURE_REQUIRED");
                        defaultLiteral = "'" + value.replace("'", "''") + "'";
                    }
                    attribute(classifiers, owner, name, operation.attr("type"), operation.attr("optional"), operation.attr("mutable"), defaultLiteral, operation);
                    traces.add(trace(rule, operation, row, owner + "." + name));
                }
                case "mapObservable" -> {
                    String sourceName = row.values().get(operation.attr("matchOn").equals("sourceName") ? "name" : operation.attr("matchOn"));
                    var matching = rule.children("observableCase").stream().filter(c -> c.attr("sourceName").equals(sourceName)).toList();
                    if (matching.size() != 1) throw operation.location().error("M1_OBSERVABLE_UNMATCHED_OR_AMBIGUOUS");
                    Node entry = matching.getFirst();
                    if (!entry.attr("initialExpression").equals(row.values().get("initial"))) throw entry.location().error("M1_INITIAL_EXPRESSION_MISMATCH");
                    if (entry.attributes().containsKey("defaultPolicy") && !entry.attr("defaultPolicy").equals("omit-from-M1")) throw entry.location().error("M1_DEFAULT_POLICY_UNSUPPORTED");
                    String owner = render(operation.attr("owner"),row,operation), name = identifier(render(entry.attr("targetName"),row,entry), entry);
                    attribute(classifiers, owner, name, entry.attr("type"), entry.attr("optional"), entry.attr("mutable"), entry.optional("defaultLiteral"), entry);
                    traces.add(trace(rule, entry, row, owner + "." + name));
                }
                case "createPopulationInvariant" -> {
                    var matching = rule.children("populationCase").stream().filter(c -> c.attr("sourceRole").equals(row.values().get("id"))).toList();
                    if (matching.size() != 1) throw operation.location().error("M1_POPULATION_CASE_UNMATCHED_OR_AMBIGUOUS");
                    Node entry = matching.getFirst();
                    if (!entry.attr("expectedMin").equals(row.values().get("min")) || !entry.attr("expectedMax").equals(row.values().get("max"))) throw entry.location().error("POPULATION_BOUND_MISMATCH");
                    String context = identifier(entry.attr("context"), entry), name = identifier(entry.attr("name"), entry);
                    if (!classifiers.containsKey(context)) throw entry.location().error("M1_INVARIANT_CONTEXT_UNRESOLVED");
                    invariants.add("    context " + context + " inv " + name + ": " + render(entry.attr("oclTemplate"), row, entry) + ";\n");
                    traces.add(new Trace(rule.attr("id"), row, context + "::" + name, "cslSource:" + name));
                }
                case "require", "requireSet", "requireSameSet", "trace", "note", "observableCase", "populationCase" -> { /* conditions executed by selector gate; cases consumed by operations above */ }
                default -> throw operation.location().error("M1_OPERATION_UNSUPPORTED:" + operation.name());
            }
        }
        String modelName = identifier(modelRules.attr("targetModel"), modelRules);
        var generated = new StringBuilder("csl " + modelRules.attr("targetVersion") + " " + modelName + " {\n");
        for (Classifier classifier : classifiers.values()) if (classifier.owner.isEmpty()) emit(classifier, classifiers, generated, 1);
        invariants.forEach(generated::append); generated.append("}\n");
        CslModel model = new CslFrontend().compile(generated.toString(), "generated-from:" + modelRules.location().source()).requireModel();
        Node closed = modelRules.children("closedTarget").isEmpty()?null:modelRules.child("closedTarget");
        checkCount(closed, "entities", model.entities().size()); checkCount(closed, "roles", model.roles().size()); checkCount(closed, "orgContexts", model.orgContexts().size());
        checkCount(closed, "attributes", classifiers.values().stream().mapToInt(c -> c.attributes.size()).sum());
        checkCount(closed, "declarationContainments", classifiers.values().stream().filter(c -> !c.owner.isEmpty()).count());
        checkCount(closed, "invariants", model.invariants().size()); checkCount(closed, "associations", model.relations().size());
        checkCount(closed, "associationEnds", model.relations().stream().mapToInt(r -> r.endpoints().size()).sum());
        checkCount(closed, "generalizations", model.generalizations().size()); checkCount(closed, "compatibilities", model.compatibilities().size());
        String fingerprint = CslModelFingerprint.of(model);
        var omissions = omitted.stream().map(node -> new Omission(node.attr("id"), node.attr("concept"), node.optional("handledBy"), node.attr("reason"), "INTENTIONAL_M1_OMISSION_NOT_RUNTIME_VERDICT", node.children("trace"))).toList();
        return new Result(model, generated.toString(), fingerprint, traces, omissions);
    }

    private static void attribute(Map<String, Classifier> classifiers, String owner, String name, String type, String optional, String mutable, String defaultLiteral, Node at) {
        if(!Set.of("String","Integer","Boolean","Real").contains(type))throw at.location().error("M1_ATTRIBUTE_TYPE_UNSUPPORTED:"+type);
        var classifier = classifiers.get(owner);
        if (classifier == null) throw at.location().error("M1_ATTRIBUTE_OWNER_UNRESOLVED:" + owner);
        if (!classifier.attributeNames.add(name)) throw at.location().error("M1_ATTRIBUTE_DUPLICATE:" + owner + "." + name);
        classifier.attributes.add("attribute " + name + " : " + type + (optional.equals("false") ? " required" : " optional") + (mutable.equals("true") ? " mutable" : "") + (defaultLiteral.isEmpty() ? "" : " default " + defaultLiteral) + ";");
    }
    private static Trace trace(Node rule, Node operation, Row row, String target) { return new Trace(rule.attr("id"), row, target, render(operation.optional("drawioCell"), row, operation)); }
    private static String render(String template, Row row, Node at) {
        var matcher = TEMPLATE.matcher(template); var output = new StringBuilder();
        while (matcher.find()) {
            String value = row.values().get(matcher.group(1));
            if (value == null) throw at.location().error("M1_CAPTURE_REQUIRED:" + matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output); if (output.indexOf("${") >= 0) throw at.location().error("M1_TEMPLATE_UNSUPPORTED"); return output.toString();
    }
    private static String identifier(String value, Node at) { if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) throw at.location().error("M1_IDENTIFIER_INVALID"); return value; }
    private static void checkCount(Node closed, String key, long actual) { if (closed!=null && closed.attributes().containsKey(key) && Long.parseLong(closed.attr(key)) != actual) throw closed.location().error("M1_CLOSED_TARGET_COUNT_MISMATCH:" + key); }
    private static void emit(Classifier classifier, Map<String, Classifier> all, StringBuilder output, int depth) {
        String indent = "    ".repeat(depth), keyword = switch (classifier.kind) { case "OrgCtx" -> "orgContext"; case "Role" -> "role"; case "Entity" -> "entity"; default -> throw new IllegalArgumentException("M1_CLASSIFIER_KIND_UNSUPPORTED"); };
        if (classifier.kind.equals("Role") && classifier.attributes.isEmpty()) { output.append(indent).append(keyword).append(' ').append(classifier.name).append(";\n"); return; }
        output.append(indent).append(keyword).append(' ').append(classifier.name).append(" {\n");
        classifier.attributes.forEach(attribute -> output.append(indent).append("    ").append(attribute).append('\n'));
        for (Classifier child : all.values()) if (child.owner.equals(classifier.name)) emit(child, all, output, depth + 1);
        output.append(indent).append("}\n");
    }
}

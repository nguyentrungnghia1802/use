package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.vnu.sme.goal.dsl.istar.parser.IStarLexer;
import org.vnu.sme.goal.dsl.istar.parser.IStarParser;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import static org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionMappingSpecification.*;

/** Structural/binding validation only. Historical XML assertions never grant runtime support. */
public final class AuctionMappingValidator {
    private record Shape(Set<String> required, Set<String> allowed) { }
    private static final Map<String, Shape> VOCABULARY = vocabulary();
    private static final Set<String> OPERATIONS = Set.of("createClassifier", "createAttribute", "mapObservable", "createPopulationInvariant", "upsertInstance", "assignSnapshot", "reconcileRemoval");
    private static final Set<String> BOOLEANS = words("nativeRuntimeAdapterAvailable use4GoalNativeRuleFormat physicalWorkspaceOwnership optional mutable verifyInitialExpression replacementAllowed atomic required associationInCSL sourceClassExists createsCSLClassifiers createsCSLAssociations modifiesInputOCL modifiesInputIStar successEvidenceRequired emittedIntoCSL inventCoordinatorRole currentOCLUsesAtPre emitHistoricalCSLAttributes adminSatisfiedFlagDefinesActualAchievement emitCSLGoalFlags compareDiagramLayout");
    private static final Set<String> COUNTS = words("expectedCount orgContexts roles entities attributes declarationContainments invariants associations associationEnds generalizations compatibilities durationMs checksPassed artifactInstancesBeforeCreation expectedMin expectedMax");
    private static final Map<String, Set<String>> REFERENCES = Map.ofEntries(
            Map.entry("rootRef", Set.of("root")), Map.entry("sourceRef", Set.of("file", "archiveEntry")),
            Map.entry("selectRef", Set.of("select")), Map.entry("selectorRef", Set.of("select")),
            Map.entry("leftSelectorRef", Set.of("select")), Map.entry("rightSelectorRef", Set.of("select")),
            Map.entry("sourceSelectorRef", Set.of("select")), Map.entry("sourceSelectorRefs", Set.of("select")),
            Map.entry("inputRef", Set.of("input")), Map.entry("observerViewRef", Set.of("view")),
            Map.entry("converter", Set.of("converter")), Map.entry("runtimeConverter", Set.of("converter")));

    public void validateStructure(AuctionMappingSpecification spec) {
        Node root = spec.root();
        if (!root.name().equals("auctionJacamoToCslRules")) throw root.location().error("ROOT_UNSUPPORTED");
        shape(root, "#document");
        for (String section : List.of("profile", "sources", "formatContract", "selectors", "modelRules", "runtimeMapping", "omittedConcepts", "observerSupport", "verificationBindings", "validation", "limitations")) root.child(section);
        expect(root, "formatVersion", "1.0"); expect(root, "targetMetamodel", "CSL-core-v4.0");
        expect(root.child("sources"), "checksumAlgorithm", "SHA-256");
        expect(root.child("profile"), "cslVersion", "v4.0"); expect(root.child("profile"), "xmlDialect", "custom-declarative");
        var target = root.child("profile").child("closedTarget");
        root.child("profile").child("scopeAlias");
        for (String child : List.of("commonRequirements", "adapterInputs", "rules", "conversions")) root.child("runtimeMapping").child(child);
        for (String child : List.of("identityBindings", "views", "evidenceClassification")) root.child("observerSupport").child(child);
        if (target.attributes().isEmpty()) throw target.location().error("CLOSED_TARGET_REQUIRED");
        var format = root.child("formatContract");
        var selectors = format.child("selectorSemantics");
        expect(selectors, "xpath", "XPath-1.0"); expect(selectors, "regex", "Python-re");
        expect(selectors, "regexFlags", "MULTILINE"); expect(selectors, "regexPreprocess", "strip-C-and-line-comments-preserve-quoted-strings");
        expect(selectors, "iteration", "document-order"); expect(selectors, "countFailure", "reject-model-transformation");
        expect(selectors, "captures", "named-regex-groups-or-explicit-XPath-captures");
        var execution = format.child("modelExecution");
        expect(execution, "ruleOrder", "document-order"); expect(execution, "mode", "foreach-selected-row");
        expect(execution, "interpolation", "${captureName}"); expect(execution, "onDuplicateTarget", "reject"); expect(execution, "onFailedCondition", "reject");
        expect(execution, "defaultStringEncoding", "OCL-single-quoted-double-apostrophe"); expect(execution, "unmatchedObservable", "reject");
        format.child("traceSemantics");
        var runtimeSemantics = format.child("runtimeSemantics");
        expect(runtimeSemantics, "inputVocabulary", "normalized-adapter-contract-not-existing-JaCaMo-API");
        expect(runtimeSemantics, "onMissingRequiredState", "incomplete-do-not-claim-full-conformance");
        expect(runtimeSemantics, "onUnresolvedBinding", "quarantine-do-not-guess"); expect(runtimeSemantics, "onStaleOrMixedRevision", "reject-snapshot");
        var declared = new HashSet<String>();
        for (Node operation : format.children("operation")) {
            if (!OPERATIONS.contains(operation.attr("name")) || !declared.add(operation.attr("name"))) throw operation.location().error("OPERATION_UNSUPPORTED_OR_DUPLICATE");
        }
        if (!declared.equals(OPERATIONS)) throw format.location().error("OPERATION_VOCABULARY_INCOMPLETE");
        for (Node node : spec.nodes()) {
            if (Set.of("view", "input").contains(node.name())) {
                var names = new HashSet<String>();
                for (Node field : node.children("field")) if (!names.add(field.attr("name"))) throw field.location().error("DUPLICATE_FIELD");
            }
            for (var reference : REFERENCES.entrySet()) if (node.attributes().containsKey(reference.getKey())) {
                if (node.attr(reference.getKey()).isBlank()) throw node.location().error("EMPTY_REFERENCE:" + reference.getKey());
                for (String id : words(node.attr(reference.getKey()))) {
                    var referred = spec.byId().get(id);
                    if (referred == null || !reference.getValue().contains(referred.name())) throw node.location().error("UNRESOLVED_REFERENCE:" + reference.getKey() + ":" + id);
                }
            }
            for (var attribute : node.attributes().entrySet()) {
                if (BOOLEANS.contains(attribute.getKey()) && !Set.of("true", "false").contains(attribute.getValue())) throw node.location().error("BOOLEAN_REQUIRED:" + attribute.getKey());
                if (COUNTS.contains(attribute.getKey()) && !attribute.getValue().matches("[0-9]{1,9}")) throw node.location().error("NONNEGATIVE_INTEGER_REQUIRED:" + attribute.getKey());
                if (Set.of("sha256", "archiveSha256").contains(attribute.getKey()) && !attribute.getValue().matches("[0-9a-f]{64}")) throw node.location().error("SHA256_REQUIRED");
            }
            if (node.name().equals("select")) {
                if (node.attr("syntax").equals("regex")) node.child("pattern");
                else if (node.attr("syntax").equals("xpath")) node.attr("expression");
                else throw node.location().error("SELECTOR_SYNTAX_UNSUPPORTED");
                var captures = new HashSet<String>();
                for (Node capture : node.children("capture")) if (!captures.add(capture.attr("name"))) throw capture.location().error("DUPLICATE_CAPTURE");
            }
            if (node.name().equals("createClassifier") && !Set.of("OrgCtx", "Role", "Entity").contains(node.attr("kind"))) throw node.location().error("CLASSIFIER_KIND_UNSUPPORTED");
        }
        for (Node rule : spec.modelRules()) { expect(rule, "phase", "M1"); rule.attr("selectRef"); }
        for (Node rule : spec.runtimeRules()) { expect(rule, "phase", "M0"); rule.attr("inputRef"); }
    }

    public void validate(AuctionMappingSpecification spec, AuctionCaseBundle bundle) {
        validateStructure(spec);
        var roots = new HashSet<String>();
        for (Node root : spec.root().child("sources").children("root")) {
            roots.add(root.attr("id"));
            if (root.attributes().containsKey("gitHead") && !root.attr("gitHead").equals(bundle.runtimeRequirements().get("engineUpstreamCommit")))
                throw root.location().error("ENGINE_UPSTREAM_PIN_MISMATCH");
        }
        if (!roots.equals(bundle.rootAliases().keySet())) throw spec.root().location().error("ROOT_ALIAS_SET_MISMATCH");
        String requiredOmissions = bundle.runtimeRequirements().get("mappingRequiredOmissionIds");
        var omissionIds = new HashSet<String>();
        spec.root().child("omittedConcepts").children("omit").forEach(node -> omissionIds.add(node.attr("id")));
        if (requiredOmissions == null || !omissionIds.equals(words(requiredOmissions))) throw spec.root().location().error("OMISSION_INVENTORY_MISMATCH");
        for (Node file : spec.root().child("sources").children("file")) {
            try { bundle.requireLocation(file.attr("id"), file.attr("rootRef"), file.attr("path"), file.attr("sha256")); }
            catch (IllegalArgumentException error) { throw file.location().error(error.getMessage()); }
        }
        for (Node entry : spec.root().child("sources").children("archiveEntry")) {
            var input = bundle.inputs().get(entry.attr("id"));
            if (input == null || !input.sha256().equals(entry.attr("sha256")) || !input.archiveSha256().equals(entry.attr("archiveSha256")) || !input.archiveEntry().equals(entry.attr("entry")))
                throw entry.location().error("ARCHIVE_PIN_MISMATCH");
        }
        Set<String> cslCells = bundle.inputs().containsKey("target-drawio") ? diagramIds(bundle.require("target-drawio").bytes(), spec.root()) : Set.of();
        Set<String> iStarCells = bundle.inputs().containsKey("input-istar") ? diagramIds(bundle.require("input-istar").bytes(), spec.root()) : Set.of();
        Set<String> tasks = bundle.inputs().containsKey("target-istar") ? iStarTasks(bundle.require("target-istar").bytes(), spec.root()) : Set.of();
        // Reuse the located specification parser. Expression syntax uses the
        // host parser; model typing/evaluation still happens only at O01/native USE.
        var clauses = new HashSet<String>();
        new AuctionOclSpecificationLoader().load(bundle).clauses().forEach(c -> clauses.add(c.name()));
        var csl = bundle.inputs().containsKey("target-csl") ? new CslFrontend().compile(new String(bundle.require("target-csl").bytes(), StandardCharsets.UTF_8), "target-csl").requireModel() : null;
        Set<String> viewNames = new HashSet<>(); spec.views().forEach(v -> viewNames.add(v.attr("name")));
        for (Node node : spec.nodes()) {
            if (!cslCells.isEmpty() && node.attributes().containsKey("drawioCell") && !node.attr("drawioCell").contains("${")) member(cslCells, node.attr("drawioCell"), node, "CSL_CELL");
            for (String attribute : List.of("istarCell", "istarCells")) if (!iStarCells.isEmpty() && node.attributes().containsKey(attribute)) for (String id : words(node.attr(attribute))) member(iStarCells, id, node, "ISTAR_CELL");
            if (!tasks.isEmpty() && node.attributes().containsKey("istarTask")) member(tasks, node.attr("istarTask"), node, "ISTAR_TASK");
            if (!tasks.isEmpty() && node.name().equals("taskBinding")) member(tasks, node.attr("istarCell"), node, "ISTAR_TASK");
            if (node.attributes().containsKey("clauses")) for (String name : words(node.attr("clauses"))) member(clauses, name, node, "OCL_CLAUSE");
            if (node.name().equals("externalInvariant")) member(clauses, node.attr("name"), node, "OCL_INVARIANT");
            if (csl != null && node.attributes().containsKey("cslRole") && csl.findRole(node.attr("cslRole")).isEmpty()) throw node.location().error("CSL_ROLE_UNRESOLVED");
            if (csl != null && node.attributes().containsKey("cslContext") && csl.findOrgContext(node.attr("cslContext")).isEmpty()) throw node.location().error("CSL_CONTEXT_UNRESOLVED");
            if (csl != null && node.attributes().containsKey("cslStateOwner") && csl.findEntity(node.attr("cslStateOwner")).isEmpty()) throw node.location().error("CSL_ENTITY_UNRESOLVED");
            if (node.name().equals("field") && !Set.of("String", "Boolean", "Integer", "Real", "JavaBinary64", "JasonAtom", "Set(String)").contains(node.attr("type")) && !viewNames.contains(node.attr("type")))
                throw node.location().error("FIELD_TYPE_UNSUPPORTED");
        }
    }

    private static void shape(Node node, String parent) {
        Shape shape = VOCABULARY.get(parent + "/" + node.name());
        if (shape == null || !shape.allowed().containsAll(node.attributes().keySet()) || !node.attributes().keySet().containsAll(shape.required())) throw node.location().error("ELEMENT_OR_ATTRIBUTE_SHAPE_UNSUPPORTED:" + parent + "/" + node.name());
        node.children().forEach(child -> shape(child, node.name()));
    }
    private static Map<String, Shape> vocabulary() {
        try (var stream = AuctionMappingValidator.class.getResourceAsStream("xml-vocabulary-1.0.txt")) {
            if (stream == null) throw new IllegalStateException("MAPPING_XML_VOCABULARY_MISSING");
            var result = new HashMap<String, Shape>();
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank() || line.startsWith("# ")) continue;
                var fields = line.split("\\|", -1);
                if (fields.length != 3 || result.put(fields[0], new Shape(words(fields[1]), words(fields[2]))) != null) throw new IllegalStateException("MAPPING_XML_VOCABULARY_INVALID");
            }
            return Map.copyOf(result);
        } catch (IOException error) { throw new IllegalStateException("MAPPING_XML_VOCABULARY_UNREADABLE", error); }
    }
    private static void expect(Node node, String key, String value) { if (!node.attr(key).equals(value)) throw node.location().error("UNSUPPORTED_VALUE:" + key); }
    private static void member(Set<String> values, String value, Node node, String kind) { if (!values.contains(value)) throw node.location().error(kind + "_UNRESOLVED:" + value); }
    private static Set<String> words(String text) { return text.isBlank() ? Set.of() : Set.copyOf(Arrays.asList(text.strip().split("\\s+"))); }
    private static Set<String> diagramIds(byte[] bytes, Node at) {
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance(); factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            var cells = document.getElementsByTagName("mxCell"); var result = new HashSet<String>();
            for (int i = 0; i < cells.getLength(); i++) result.add(((org.w3c.dom.Element)cells.item(i)).getAttribute("id"));
            return result;
        } catch (Exception error) { throw at.location().error("DRAWIO_INVALID:" + error.getMessage()); }
    }
    private static Set<String> iStarTasks(byte[] bytes, Node at) {
        var errors = new ArrayList<String>();
        var listener = new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line, int column, String message, RecognitionException cause) { errors.add(line + ":" + column + ":" + message); }
        };
        var lexer = new IStarLexer(CharStreams.fromString(new String(bytes, StandardCharsets.UTF_8)));
        lexer.removeErrorListeners(); lexer.addErrorListener(listener);
        var parser = new IStarParser(new CommonTokenStream(lexer)); parser.removeErrorListeners(); parser.addErrorListener(listener);
        var tree = parser.model();
        if (!errors.isEmpty()) throw at.location().error("ISTAR_SYNTAX_INVALID:" + errors);
        var result = new HashSet<String>(); collectTasks(tree, result); return result;
    }
    private static void collectTasks(ParseTree tree, Set<String> tasks) {
        if (tree instanceof IStarParser.BodyTaskContext task) tasks.add(task.IDENT().getText());
        for (int i = 0; i < tree.getChildCount(); i++) collectTasks(tree.getChild(i), tasks);
    }
}

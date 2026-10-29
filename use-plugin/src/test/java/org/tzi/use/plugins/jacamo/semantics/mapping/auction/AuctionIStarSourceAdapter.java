package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.frontend.IStarCapabilityAdmission;
import org.vnu.sme.goal.dsl.istar.ast.*;
import org.vnu.sme.goal.dsl.istar.parser.*;
import org.w3c.dom.Element;

/** Preserves discarded documentation outside the original engine; does not bypass semantic validation. */
public final class AuctionIStarSourceAdapter {
    public record Description(String actor, String element, String rawLiteral, String text, IStarCapabilityAdmission.SourceSpan span) { }
    public record Prepared(String originalHash, String executableHash, String executableSource, String structureHash,
                           List<Description> descriptions, Map<String, String> actorAliases) {
        public Prepared { descriptions = List.copyOf(descriptions); actorAliases = Map.copyOf(actorAliases); }
    }


    public Prepared prepare(String original, String sourceName) {
        if (original.getBytes(StandardCharsets.UTF_8).length > 1_048_576) throw invalid("SOURCE_CAPACITY");
        var admission = new IStarCapabilityAdmission().scan(original, sourceName);
        if (admission.diagnostics().stream().anyMatch(d -> d.stage() == org.tzi.use.semantic.engine.api.EngineDiagnostic.Stage.PARSE)) throw invalid("SOURCE_SYNTAX:" + admission.diagnostics());
        var descriptions = new ArrayList<Description>(); collectDescriptions(admission.tree(), admission, descriptions);
        var executable = new StringBuilder(original);
        // ANTLR spans use code points. Convert against the original before preserving UTF-16 offsets/newlines.
        for (int i = descriptions.size() - 1; i >= 0; i--) {
            var span = descriptions.get(i).span();
            int from = original.offsetByCodePoints(0, span.startOffset()), through = original.offsetByCodePoints(0, span.endOffset());
            for (int p = from; p < through; p++) if (executable.charAt(p) != '\n' && executable.charAt(p) != '\r') executable.setCharAt(p, ' ');
        }
        String view = executable.toString();
        var after = new IStarCapabilityAdmission().scan(view, sourceName + ":preserved-metadata-view");
        if (!after.diagnostics().isEmpty()) throw invalid("SOURCE_UNSUPPORTED:" + after.diagnostics());
        var beforeAst = IStarBuildingVisitor.build(admission.tree()); var afterAst = IStarBuildingVisitor.build(after.tree());
        if (!beforeAst.equals(afterAst)) throw invalid("SOURCE_AST_CHANGED");
        return new Prepared(hash(original), hash(view), view, SemanticJson.hash(SemanticJson.tree(afterAst)), descriptions, Map.of());
    }

    private static void collectDescriptions(ParseTree node, IStarCapabilityAdmission.Scan admission, List<Description> output) {
        if (node instanceof IStarParser.DescPropertyContext context) {
            IStarParser.ActorDefContext actor = null;
            for (ParseTree parent = context.getParent(); parent != null; parent = parent.getParent()) if (parent instanceof IStarParser.ActorDefContext value) { actor = value; break; }
            String element = switch (context.getParent()) {
                case IStarParser.BodyGoalContext value -> value.IDENT().getText();
                case IStarParser.BodyTaskContext value -> value.IDENT().getText();
                case IStarParser.BodyResourceContext value -> value.IDENT().getText();
                case IStarParser.BodyQualityContext value -> value.IDENT().getText();
                default -> throw invalid("DESCRIPTION_OWNER_UNSUPPORTED");
            };
            if (actor == null) throw invalid("DESCRIPTION_ACTOR_REQUIRED");
            var span = admission.findings().stream().map(IStarCapabilityAdmission.Finding::span)
                    .filter(s -> s.startOffset() == context.getStart().getStartIndex() && s.endOffset() == context.getStop().getStopIndex() + 1).findFirst().orElseThrow(() -> invalid("DESCRIPTION_SPAN_REQUIRED"));
            String literal = context.stringLit().getText();
            Object text;
            try { text = CanonicalJson.decode(literal.getBytes(StandardCharsets.UTF_8)); }
            catch (org.jacamo.bridge.contract.ContractException error) { throw invalid("DESCRIPTION_ESCAPE_UNSUPPORTED"); }
            if (!(text instanceof String value)) throw invalid("DESCRIPTION_STRING_REQUIRED");
            output.add(new Description(actor.IDENT().getText(), element, literal, value, span));
        }
        for (int i = 0; i < node.getChildCount(); i++) collectDescriptions(node.getChild(i), admission, output);
    }

    /** Exact diagram metadata/AST correspondence, not geometry or name similarity. */
    public Map<String, String> validateDiagram(Prepared prepared, byte[] diagram) {
        var scan = new IStarCapabilityAdmission().scan(prepared.executableSource(), "diagram-binding");
        if (!scan.diagnostics().isEmpty()) throw invalid("DIAGRAM_SOURCE_UNSUPPORTED");
        var ast = IStarBuildingVisitor.build(scan.tree());
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(diagram));
            var actors = new HashSet<String>(); ast.actors().forEach(a -> actors.add(a.id()));
            var pages = document.getElementsByTagName("diagram"); var matching = new ArrayList<Element>();
            for (int i = 0; i < pages.getLength(); i++) {
                var page = (Element) pages.item(i); var boundaries = new HashSet<String>(); var nodes = page.getElementsByTagName("mxCell");
                for (int j = 0; j < nodes.getLength(); j++) { var cell = (Element) nodes.item(j); if (cell.getAttribute("elementType").equals("actorBoundary")) boundaries.add(cell.getAttribute("actor")); }
                if (boundaries.equals(actors)) matching.add(page);
            }
            if (matching.size() != 1) throw invalid("DIAGRAM_ACTOR_PAGE_AMBIGUOUS");
            var cells = new LinkedHashMap<String, Element>(); var pageCells = matching.getFirst().getElementsByTagName("mxCell");
            for (int i = 0; i < pageCells.getLength(); i++) { var cell = (Element) pageCells.item(i); if (cells.putIfAbsent(cell.getAttribute("id"), cell) != null) throw invalid("DIAGRAM_CELL_ID_DUPLICATE"); }
            var aliases = new LinkedHashMap<String, String>(); var boundaries = new HashMap<String, String>();
            for (var actor : ast.actors()) {
                var circle = cells.values().stream().filter(c -> Set.of("actor", "agent", "role").contains(c.getAttribute("elementType")) && plain(c.getAttribute("value")).equals(actor.id())).toList();
                if (circle.size() != 1) throw invalid("DIAGRAM_ACTOR_ALIAS_AMBIGUOUS:" + actor.id());
                String type = circle.getFirst().getAttribute("elementType");
                if (actor.kind().equals("role") ? !type.equals("role") : !Set.of("actor", "agent").contains(type)) throw invalid("DIAGRAM_ACTOR_KIND_MISMATCH");
                aliases.put(circle.getFirst().getAttribute("id"), actor.id());
                var owner = cells.values().stream().filter(c -> c.getAttribute("elementType").equals("actorBoundary") && c.getAttribute("actor").equals(actor.id())).toList();
                if (owner.size() != 1) throw invalid("DIAGRAM_BOUNDARY_AMBIGUOUS"); boundaries.put(actor.id(), owner.getFirst().getAttribute("id"));
            }
            var expectedElements = new HashSet<String>(); var expectedEdges = new HashSet<String>();
            for (var actor : ast.actors()) for (var body : actor.body()) {
                String id; String kind; List<RelCS> relations;
                switch (body) {
                    case ElementBodyCS.GoalCS value -> { id = value.id(); kind = "goal"; relations = value.rels(); }
                    case ElementBodyCS.TaskCS value -> { id = value.id(); kind = "task"; relations = value.rels(); }
                    case ElementBodyCS.ResourceCS value -> { id = value.id(); kind = "resource"; relations = value.rels(); }
                    case ElementBodyCS.QualityCS value -> { id = value.id(); kind = "quality"; relations = value.rels(); }
                    case ElementBodyCS.ParticipatesCS value -> { expectedEdges.add("participates-in:" + value.actor() + "->" + value.target()); continue; }
                    case ElementBodyCS.IsACS value -> { expectedEdges.add("is-a:" + value.actor() + "->" + value.target()); continue; }
                }
                expectedElements.add(id); Element cell = cells.get(id);
                if (cell == null || !cell.getAttribute("elementType").equals(kind) || !cell.getAttribute("owner").equals(actor.id()) || !cell.getAttribute("parent").equals(boundaries.get(actor.id()))) throw invalid("DIAGRAM_ELEMENT_OWNER_OR_KIND_MISMATCH:" + id);
                if (body instanceof ElementBodyCS.GoalCS value && !cell.getAttribute("goalType").equals(value.goalType().toUpperCase(Locale.ROOT))) throw invalid("DIAGRAM_GOAL_TYPE_MISMATCH:" + id);
                if (body instanceof ElementBodyCS.TaskCS value) for (var condition : value.constraints()) {
                    String property = condition.kind() == IStarOclConstraintCS.Kind.PRE ? "precondition" : "postcondition";
                    if (!cell.getAttribute(property).equals(condition.oclBody())) throw invalid("DIAGRAM_TASK_CONTRACT_MISMATCH:" + id);
                }
                for (RelCS relation : relations) expectedEdges.add(switch (relation) {
                    case RelCS.RefineCS value -> "refinement:" + (value.or() ? "OR:" : "AND:") + id + "->" + value.target();
                    case RelCS.NeededByCS value -> "neededBy:" + id + "->" + value.target();
                    default -> throw invalid("DIAGRAM_RELATION_UNSUPPORTED");
                });
            }
            var actualElements = new HashSet<String>(); var actualEdges = new HashSet<String>();
            for (var cell : cells.values()) {
                if (Set.of("goal", "task", "resource", "quality").contains(cell.getAttribute("elementType"))) actualElements.add(cell.getAttribute("id"));
                String relation = cell.getAttribute("relation");
                if (Set.of("refinement", "neededBy").contains(relation)) {
                    String key = relation + ":" + (relation.equals("refinement") ? cell.getAttribute("refinementKind") + ":" : "") + cell.getAttribute("source") + "->" + cell.getAttribute("target");
                    if (!actualEdges.add(key)) throw invalid("DIAGRAM_DUPLICATE_RELATION");
                }
                if (!cell.getAttribute("associationKind").isEmpty()) actualEdges.add(cell.getAttribute("associationKind") + ":" + aliases.get(cell.getAttribute("source")) + "->" + aliases.get(cell.getAttribute("target")));
            }
            if (!expectedElements.equals(actualElements) || !expectedEdges.equals(actualEdges)) throw invalid("DIAGRAM_TOPOLOGY_MISMATCH");
            var expectedDependencies = new HashSet<String>(); var actualDependencies = new HashSet<String>();
            for (var dependency : ast.dependencies()) {
                expectedDependencies.add(dependency.dependum()); var cell = cells.get(dependency.dependum());
                if (cell == null || !cell.getAttribute("elementType").equals("dependum") || !cell.getAttribute("dependumType").equals(dependency.dependumKind()) || !cell.getAttribute("depender").equals(dependency.depender()) || !cell.getAttribute("dependee").equals(dependency.dependee())) throw invalid("DIAGRAM_DEPENDENCY_MISMATCH");
                String source = dependency.dependerElmt(), target = dependency.dependeeElmt();
                if (source == null || target == null) throw invalid("DIAGRAM_DEPENDENCY_OPENING_UNSUPPORTED");
                var legs = cells.values().stream().filter(c -> c.getAttribute("relation").equals("dependencyLeg") && c.getAttribute("dependencyId").equals(cell.getAttribute("dependencyId"))).toList();
                if (legs.size() != 2 || legs.stream().noneMatch(l -> l.getAttribute("source").equals(source) && l.getAttribute("target").equals(dependency.dependum())) || legs.stream().noneMatch(l -> l.getAttribute("source").equals(dependency.dependum()) && l.getAttribute("target").equals(target))) throw invalid("DIAGRAM_DEPENDENCY_OPENING_MISMATCH");
            }
            cells.values().stream().filter(c -> c.getAttribute("elementType").equals("dependum")).forEach(c -> actualDependencies.add(c.getAttribute("id")));
            if (!expectedDependencies.equals(actualDependencies)) throw invalid("DIAGRAM_DEPENDENCY_INVENTORY_MISMATCH");
            for (var description : prepared.descriptions()) if (!plain(cells.get(description.element()).getAttribute("value")).equals(normalize(description.text()))) throw invalid("DIAGRAM_DESCRIPTION_MISMATCH:" + description.element());
            return Map.copyOf(aliases);
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("ISTAR_DIAGRAM_INVALID", error); }
    }
    private static String plain(String html) {
        var text = new StringBuilder();
        try { new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override public void handleText(char[] data, int position) { text.append(data); }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) { if (tag == HTML.Tag.BR) text.append(" | "); }
            @Override public void handleEndTag(HTML.Tag tag, int position) { if (tag == HTML.Tag.P || tag == HTML.Tag.DIV) text.append(" | "); }
        }, true); } catch (IOException error) { throw new IllegalArgumentException("ISTAR_DIAGRAM_LABEL_INVALID", error); }
        return normalize(text.toString());
    }
    private static String normalize(String text) { return text.replace('\u00a0',' ').replaceAll("\\s*\\|\\s*", " | ").replaceAll("\\s+", " ").strip().replaceAll(" \\|$", ""); }
    private static String hash(String source) { return ExternalOclConstraintService.sha256(source.getBytes(StandardCharsets.UTF_8)); }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException("ISTAR_" + reason); }
}

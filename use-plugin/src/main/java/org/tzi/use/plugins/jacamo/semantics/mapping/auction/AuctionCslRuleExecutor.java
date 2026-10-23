package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import static org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionMappingSpecification.Node;
import static org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionSelectorResult.Row;

/** M1-only interpreter. It emits source and calls the original compiler; it never creates live objects. */
public final class AuctionCslRuleExecutor {
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

    public Result generate(AuctionMappingSpecification spec, AuctionCaseBundle bundle, AuctionSelectorResult selected) {
        if (!selected.bundleHash().equals(bundle.hash()) || !selected.specificationHash().equals(spec.sourceHash())
                || !spec.sourceHash().equals(bundle.require("case-rules").sha256())) throw spec.root().location().error("SELECTOR_OWNERSHIP_MISMATCH");
        var originalRules=spec.root().child("modelRules");
        if(!originalRules.attr("targetModel").equals(spec.root().child("profile").attr("modelName")))throw originalRules.location().error("M1_MODEL_HEADER_MISMATCH");
        var children=new ArrayList<>(originalRules.children());children.add(spec.root().child("profile").child("closedTarget"));
        var rules=new Node(originalRules.name(),originalRules.attributes(),originalRules.text(),children,originalRules.location());
        var rows=new LinkedHashMap<String,List<org.tzi.use.plugins.jacamo.semantics.mapping.xml.SourceSelectors.Row>>();
        selected.selections().forEach((id,values)->rows.put(id,values.stream().map(r->new org.tzi.use.plugins.jacamo.semantics.mapping.xml.SourceSelectors.Row(r.selectorId(),r.sourceId(),r.sourceDigest(),r.values(),r.line(),r.column(),r.locator(),r.matched())).toList()));
        var generated=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.CslRuleExecutor().execute(neutral(rules),spec.root().child("omittedConcepts").children("omit").stream().map(AuctionCslRuleExecutor::neutral).toList(),rows);
        var model=generated.model();String fingerprint=generated.fingerprint();
        var traces=generated.traces().stream().map(t->{var r=t.row();return new Trace(t.ruleId(),new Row(r.selectorId(),r.sourceId(),r.sourceDigest(),r.values(),r.line(),r.column(),r.locator(),r.matched()),t.target(),t.drawioCell());}).toList();
        var frontend = new CslFrontend();
        CslModel oracle = frontend.compile(new String(bundle.require("target-csl").bytes(), StandardCharsets.UTF_8), "canonical-csl-oracle").requireModel();
        CslModel diagram = frontend.compile(embeddedCsl(bundle.require("target-drawio").bytes(), spec.root()), "drawio-csl-oracle").requireModel();
        if (!fingerprint.equals(CslModelFingerprint.of(oracle)) || !fingerprint.equals(CslModelFingerprint.of(diagram))) throw spec.root().location().error("M1_TARGET_ORACLE_MISMATCH");
        var omissions = spec.root().child("omittedConcepts").children("omit").stream().map(node -> new Omission(node.attr("id"), node.attr("concept"), node.attr("handledBy"), node.attr("reason"), "INTENTIONAL_M1_OMISSION_NOT_RUNTIME_VERDICT", node.children("trace"))).toList();
        return new Result(model, generated.generatedSource(), fingerprint, traces, omissions);
    }

    private static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node neutral(Node node) {
        var at=node.location();return new org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node(node.name(),node.attributes(),node.text(),node.children().stream().map(AuctionCslRuleExecutor::neutral).toList(),new org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Location(at.source(),at.line(),at.column(),at.ruleId()));
    }
    private static String embeddedCsl(byte[] bytes, Node at) {
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            var nodes = document.getElementsByTagName("*"); var sources = new ArrayList<String>();
            for (int i = 0; i < nodes.getLength(); i++) { var node = (org.w3c.dom.Element) nodes.item(i); if (node.hasAttribute("cslSource")) sources.add(node.getAttribute("cslSource")); }
            if (sources.size() != 1) throw at.location().error("DRAWIO_CSL_SOURCE_NOT_UNIQUE"); return sources.getFirst();
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw at.location().error("DRAWIO_CSL_SOURCE_INVALID:" + error.getMessage()); }
    }
}

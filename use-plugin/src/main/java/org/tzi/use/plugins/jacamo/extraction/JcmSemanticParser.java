package org.tzi.use.plugins.jacamo.extraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.JcmLexer;
import org.tzi.use.plugins.jacamo.project.ProjectEdgeKind;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;

final class JcmSemanticParser {
    private static final Set<String> KEYS = Set.of("beliefs", "goals", "debug", "verbose", "ag-class",
            "ag-arch", "ag-bb-class", "instances", "join", "focus", "roles", "responsible-for",
            "players", "owner", "asl-path", "org-path", "java-path", "class-path", "platform");

    void parse(ExtractionContext context) {
        for (var source : context.graph.sources()) {
            if (source.kind() == SourceKind.JCM) parseFile(context, source.path(),
                    source.path().equals(context.graph.entry()));
        }
    }

    private void parseFile(ExtractionContext context, Path path, boolean entry) {
        try {
            List<JcmLexer.Token> tokens = JcmLexer.scan(context.read(path));
            if (tokens.size() < 3 || !tokens.getFirst().text().equals("mas")) return;
            String masName = tokens.get(1).text();
            if (entry) {
                context.projectName = masName;
                context.projectProvenance.add(context.provenance(path, tokens.get(1).line(),
                        tokens.get(1).column(), "jcm-parser", masName));
            }
            int open = indexOf(tokens, "{", 2);
            if (open < 0) return;
            int depth = 1;
            for (int i = open + 1; i < tokens.size() && depth > 0; i++) {
                String text = tokens.get(i).text();
                if (text.equals("{")) { depth++; continue; }
                if (text.equals("}")) { depth--; continue; }
                if (depth != 1) continue;
                if (text.equals("agent") && i + 1 < tokens.size()) {
                    String name = tokens.get(i + 1).text();
                    ElementDraft agent = context.element(MetamodelKind.Agent, name, List.of("MAS"), path,
                            tokens.get(i + 1).line(), tokens.get(i + 1).column(), "jcm-parser", name);
                    agent.attributes.put("name", new AttributeValue.Text(name));
                    String sourceName = name + ".asl";
                    int cursor = i + 2;
                    if (cursor + 1 < tokens.size() && tokens.get(cursor).text().equals(":")) {
                        sourceName = tokens.get(cursor + 1).text(); cursor += 2;
                    }
                    agent.attributes.put("source", new AttributeValue.Text(sourceName));
                    if (cursor < tokens.size() && tokens.get(cursor).text().equals("{")) {
                        int end = matchingBrace(tokens, cursor);
                        parseAgentConfig(agent, tokens.subList(cursor + 1, end < 0 ? cursor + 1 : end));
                        if (end >= 0) i = end;
                    }
                    for (ElementDraft expanded : expandAgentInstances(context, agent, path)) {
                        materializeInitialMentalState(context, expanded, path);
                    }
                } else if (text.equals("workspace") && i + 1 < tokens.size()) {
                    String name = tokens.get(i + 1).text();
                    ElementDraft workspace = context.element(MetamodelKind.Workspace, name, List.of("MAS"), path,
                            tokens.get(i + 1).line(), tokens.get(i + 1).column(), "jcm-parser", name);
                    if (context.environment == null) context.environment = context.element(MetamodelKind.Environment,
                            "environment", List.of("project"), path, tokens.get(i + 1).line(), tokens.get(i + 1).column(), "jcm-parser", name);
                    context.environment.references.add(new SemanticReference("workspaces", name, workspace.id));
                    workspace.attributes.put("name", new AttributeValue.Text(name));
                    int brace = indexOf(tokens, "{", i + 2);
                    int end = brace < 0 ? -1 : matchingBrace(tokens, brace);
                    if (brace >= 0 && end >= 0) {
                        parseWorkspace(context, workspace, tokens.subList(brace + 1, end), path, name);
                        i = end;
                    }
                } else if (text.equals("organisation") && i + 1 < tokens.size()) {
                    String name = tokens.get(i + 1).text();
                    ElementDraft organisation = context.element(MetamodelKind.Organization, name,
                            List.of("MAS"), path, tokens.get(i + 1).line(), tokens.get(i + 1).column(), "jcm-parser", name);
                    organisation.attributes.put("id", new AttributeValue.Text(name));
                    int cursor = i + 2;
                    String sourceName = name + ".xml";
                    if (cursor + 1 < tokens.size() && tokens.get(cursor).text().equals(":")) {
                        sourceName = tokens.get(cursor + 1).text(); cursor += 2;
                    }
                    organisation.sourceFacts.put("source", new AttributeValue.Text(sourceName));
                    int brace = cursor < tokens.size() && tokens.get(cursor).text().equals("{") ? cursor : -1;
                    int end = brace < 0 ? -1 : matchingBrace(tokens, brace);
                    if (brace >= 0 && end >= 0) {
                        parseOrganisationConfig(context, organisation, tokens.subList(brace + 1, end), path, name);
                        i = end;
                    }
                } else if (entry && (text.endsWith("-path") || text.equals("platform"))) {
                    String value = valueAfterColon(tokens, i);
                    if (!value.isBlank()) context.projectFacts.put(text, new AttributeValue.Text(value));
                }
            }
        } catch (IOException exception) {
            context.diagnostic("JCM_PARSE_IO", Severity.ERROR, Phase.PARSING, null, null,
                    "Cannot parse discovered JCM file", path + ": " + exception.getMessage(), "Check the file encoding");
        }
    }

    private void parseAgentConfig(ElementDraft agent, List<JcmLexer.Token> body) {
        int beliefIndex = 0, goalIndex = 0;
        for (int i = 0; i + 1 < body.size(); i++) {
            String key = body.get(i).text();
            if (!KEYS.contains(key) || !body.get(i + 1).text().equals(":")) continue;
            int end = nextKey(body, i + 2);
            String value = join(body.subList(i + 2, end));
            if (!value.isBlank()) agent.sourceFacts.put(key, new AttributeValue.Text(value));
            if (key.equals("beliefs") || key.equals("goals")) {
                for (ConfigTerm term : configTerms(body.subList(i + 2, end))) {
                    String prefix = key.equals("beliefs") ? "initialBelief" : "initialGoal";
                    int ordinal = key.equals("beliefs") ? beliefIndex++ : goalIndex++;
                    agent.sourceFacts.put(prefix + "@" + String.format(java.util.Locale.ROOT, "%09d", term.line())
                                    + "@" + String.format(java.util.Locale.ROOT, "%09d", ordinal),
                            new AttributeValue.Text(term.expression()));
                }
            } else if (key.equals("focus")) {
                for (String item : commaValues(body.subList(i + 2, end))) {
                    agent.references.add(new SemanticReference("artifacts", item, null));
                }
            } else if (key.equals("roles")) {
                for (int p = i + 2; p < end; p++) {
                    if (body.get(p).text().equals("in") && p > i + 2) {
                        agent.references.add(new SemanticReference("roles", (p + 1 < end ? body.get(p + 1).text() + "." : "") + body.get(p - 1).text(), null));
                    }
                }
            } else if (key.equals("join")) {
                for (String item : commaValues(body.subList(i + 2, end))) {
                    agent.references.add(new SemanticReference("workspaces", item, null));
                }
            }
            i = end - 1;
        }
    }

    private List<ElementDraft> expandAgentInstances(ExtractionContext context, ElementDraft declared, Path path) {
        int count = 1;
        if (declared.sourceFacts.get("instances") instanceof AttributeValue.Text instances) {
            try { count = Integer.parseInt(instances.value().trim()); }
            catch (NumberFormatException invalid) {
                context.diagnostic("JCM_AGENT_INSTANCES_INVALID", Severity.ERROR, Phase.PARSING,
                        declared.provenance.getFirst().span(), declared.id.value(),
                        "Agent instances must be a positive integer", instances.value(),
                        "Use a positive decimal instances value");
            }
        }
        if (count < 1) {
            context.diagnostic("JCM_AGENT_INSTANCES_INVALID", Severity.ERROR, Phase.PARSING,
                    declared.provenance.getFirst().span(), declared.id.value(),
                    "Agent instances must be positive", Integer.toString(count),
                    "Use instances: 1 or greater");
            return List.of(declared);
        }
        if (count == 1) return List.of(declared);
        context.elements.remove(declared);
        List<ElementDraft> expanded = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String name = declared.name + index;
            var span = declared.provenance.getFirst().span();
            ElementDraft instance = context.element(MetamodelKind.Agent, name, List.of("MAS"), path,
                    span.startLine(), span.startColumn(), "jcm-parser",
                    declared.provenance.getFirst().originalSpelling());
            instance.attributes.putAll(declared.attributes);
            instance.attributes.put("name", new AttributeValue.Text(name));
            instance.sourceFacts.putAll(declared.sourceFacts);
            instance.sourceFacts.put("declaredAgentName", new AttributeValue.Text(declared.name));
            instance.sourceFacts.put("instanceIndex", new AttributeValue.IntegerNumber(index));
            instance.references.addAll(declared.references);
            expanded.add(instance);
        }
        return expanded;
    }

    private void materializeInitialMentalState(ExtractionContext context, ElementDraft agent, Path path) {
        agent.sourceFacts.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("initialBelief@")
                        || entry.getKey().startsWith("initialGoal@"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String expression = ((AttributeValue.Text) entry.getValue()).value();
                    String[] key = entry.getKey().split("@");
                    int line = Integer.parseInt(key[1]);
                    boolean belief = entry.getKey().startsWith("initialBelief@");
                    MetamodelKind kind = belief ? MetamodelKind.Belief : MetamodelKind.AGoal;
                    String local = expression;
                    ElementDraft element = context.elementWithLocalId(kind, functor(expression), local,
                            List.of("MAS", agent.name, belief ? "belief" : "goal"), path,
                            line, 1, "jcm-parser", expression);
                    element.attributes.put("literal", new AttributeValue.Text(expression));
                    element.sourceFacts.put("isInitial", new AttributeValue.Bool(true));
                    element.sourceFacts.put("declarationKind", new AttributeValue.Text("JCM_INITIAL"));
                    if (!belief) element.attributes.put("type",
                            new AttributeValue.EnumLiteral("AgentGoalType", "ACHIEVEMENT"));
                    agent.references.add(new SemanticReference(belief ? "beliefs" : "goals",
                            expression, element.id));
                });
    }

    private List<ConfigTerm> configTerms(List<JcmLexer.Token> tokens) {
        List<ConfigTerm> terms = new ArrayList<>();
        for (int cursor = 0; cursor < tokens.size();) {
            while (cursor < tokens.size() && tokens.get(cursor).text().equals(",")) cursor++;
            if (cursor >= tokens.size()) break;
            int start = cursor;
            if (cursor + 1 < tokens.size() && tokens.get(cursor + 1).text().equals("(")) {
                int depth = 0;
                cursor++;
                while (cursor < tokens.size()) {
                    String token = tokens.get(cursor).text();
                    if (token.equals("(")) depth++;
                    else if (token.equals(")") && --depth == 0) { cursor++; break; }
                    cursor++;
                }
            } else cursor++;
            while (cursor < tokens.size() && tokens.get(cursor).text().equals("[")) {
                int depth = 0;
                while (cursor < tokens.size()) {
                    String token = tokens.get(cursor).text();
                    if (token.equals("[")) depth++;
                    else if (token.equals("]") && --depth == 0) { cursor++; break; }
                    cursor++;
                }
            }
            List<JcmLexer.Token> expression = tokens.subList(start, cursor);
            if (!expression.isEmpty()) terms.add(new ConfigTerm(joinSource(expression),
                    expression.getFirst().line(), expression.getFirst().column()));
        }
        return terms;
    }

    private String joinSource(List<JcmLexer.Token> tokens) {
        StringBuilder result = new StringBuilder();
        for (JcmLexer.Token token : tokens) result.append(token.sourceText());
        return result.toString();
    }

    private String functor(String expression) {
        int open = expression.indexOf('(');
        int annotation = expression.indexOf('[');
        int end = open < 0 ? expression.length() : open;
        if (annotation >= 0) end = Math.min(end, annotation);
        return expression.substring(0, end).trim();
    }

    private record ConfigTerm(String expression, int line, int column) { }

    private void parseWorkspace(ExtractionContext context, ElementDraft workspace,
                                List<JcmLexer.Token> body, Path path, String workspaceName) {
        for (int i = 0; i + 3 < body.size(); i++) {
            if (!body.get(i).text().equals("artifact") || !body.get(i + 2).text().equals(":")) continue;
            String name = body.get(i + 1).text();
            String type = body.get(i + 3).text();
            ElementDraft artifact = context.element(MetamodelKind.Artifact, name,
                    List.of("MAS", workspaceName), path, body.get(i + 1).line(), body.get(i + 1).column(), "jcm-parser", name);
            artifact.attributes.put("name", new AttributeValue.Text(name));
            artifact.attributes.put("type", new AttributeValue.Text(type));
            int cursor = i + 4;
            if (cursor < body.size() && body.get(cursor).text().equals("(")) {
                int close = indexOf(body, ")", cursor + 1);
                if (close >= 0) artifact.sourceFacts.put("parameters",
                        new AttributeValue.Text(join(body.subList(cursor + 1, close))));
            }
            workspace.references.add(new SemanticReference("artifacts", name, artifact.id));
        }
    }

    private void parseOrganisationConfig(ExtractionContext context, ElementDraft organisation,
                                         List<JcmLexer.Token> body, Path path, String organisationName) {
        for (int i = 0; i + 1 < body.size(); i++) {
            String keyword = body.get(i).text();
            if (!keyword.equals("group") && !keyword.equals("scheme")) continue;
            String name = body.get(i + 1).text();
            MetamodelKind kind = keyword.equals("group") ? MetamodelKind.Group : MetamodelKind.Scheme;
            ElementDraft instance = context.element(kind, name, List.of("MAS", organisationName), path,
                    body.get(i + 1).line(), body.get(i + 1).column(), "jcm-parser", name);
            instance.attributes.put("id", new AttributeValue.Text(name));
            instance.sourceFacts.put("declarationKind", new AttributeValue.Text("JCM_INSTANCE"));
            organisation.references.add(new SemanticReference(keyword.equals("group") ? "groups" : "schemes", name, instance.id));
            if (i + 3 < body.size() && body.get(i + 2).text().equals(":")) {
                instance.sourceFacts.put("specificationType", new AttributeValue.Text(body.get(i + 3).text()));
            }
            int brace = i + 4 < body.size() && body.get(i + 4).text().equals("{") ? i + 4 : -1;
            if (keyword.equals("group") && brace >= 0) {
                int end = matchingBrace(body, brace);
                if (end >= 0) {
                    List<JcmLexer.Token> config = body.subList(brace + 1, end);
                    for (int p = 0; p + 1 < config.size(); p++) {
                        if (config.get(p).text().equals("players") && config.get(p + 1).text().equals(":")) {
                            int stop = nextKey(config, p + 2);
                            instance.sourceFacts.put("players", new AttributeValue.Text(join(config.subList(p + 2, stop))));
                        }
                    }
                    i = end;
                }
            }
        }
    }

    private int nextKey(List<JcmLexer.Token> tokens, int start) {
        for (int i = start; i + 1 < tokens.size(); i++) {
            if (KEYS.contains(tokens.get(i).text()) && tokens.get(i + 1).text().equals(":")) return i;
        }
        return tokens.size();
    }

    private List<String> commaValues(List<JcmLexer.Token> tokens) {
        List<String> values = new ArrayList<>();
        for (JcmLexer.Token token : tokens) if (!token.text().equals(",")) values.add(token.text());
        return values;
    }

    private String valueAfterColon(List<JcmLexer.Token> tokens, int key) {
        if (key + 1 >= tokens.size() || !tokens.get(key + 1).text().equals(":")) return "";
        int end = key + 2;
        while (end < tokens.size() && !tokens.get(end).text().equals("}")
                && !(KEYS.contains(tokens.get(end).text()) && end + 1 < tokens.size()
                && tokens.get(end + 1).text().equals(":"))) end++;
        return join(tokens.subList(key + 2, end));
    }

    private String join(List<JcmLexer.Token> tokens) {
        return tokens.stream().map(JcmLexer.Token::text).reduce((a, b) -> a + " " + b).orElse("");
    }

    private int indexOf(List<JcmLexer.Token> tokens, String text, int start) {
        for (int i = start; i < tokens.size(); i++) if (tokens.get(i).text().equals(text)) return i;
        return -1;
    }

    private int matchingBrace(List<JcmLexer.Token> tokens, int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            if (tokens.get(i).text().equals("{")) depth++;
            else if (tokens.get(i).text().equals("}") && --depth == 0) return i;
        }
        return -1;
    }
}

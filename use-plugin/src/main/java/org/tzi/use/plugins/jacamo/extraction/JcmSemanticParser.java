package org.tzi.use.plugins.jacamo.extraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            List<JcmLexer.Token> tokens = JcmLexer.scan(Files.readString(path));
            if (tokens.size() < 3 || !tokens.getFirst().text().equals("mas")) return;
            String masName = tokens.get(1).text();
            ElementDraft mas = null;
            if (entry) {
                mas = context.element(MetamodelKind.MAS, masName, List.of("project"), path,
                        tokens.get(1).line(), tokens.get(1).column(), "jcm-parser", masName);
                mas.attributes.put("Name", new AttributeValue.Text(masName));
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
                            tokens.get(i).line(), tokens.get(i).column(), "jcm-parser", name);
                    if (mas != null) mas.references.add(new SemanticReference("agent", name, agent.id));
                    agent.attributes.put("Name", new AttributeValue.Text(name));
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
                } else if (text.equals("workspace") && i + 1 < tokens.size()) {
                    String name = tokens.get(i + 1).text();
                    ElementDraft workspace = context.element(MetamodelKind.Workspace, name, List.of("MAS"), path,
                            tokens.get(i).line(), tokens.get(i).column(), "jcm-parser", name);
                    if (mas != null) mas.references.add(new SemanticReference("workspace", name, workspace.id));
                    workspace.attributes.put("Name", new AttributeValue.Text(name));
                    int brace = indexOf(tokens, "{", i + 2);
                    int end = brace < 0 ? -1 : matchingBrace(tokens, brace);
                    if (brace >= 0 && end >= 0) {
                        parseWorkspace(context, workspace, tokens.subList(brace + 1, end), path, name);
                        i = end;
                    }
                } else if (text.equals("organisation") && i + 1 < tokens.size()) {
                    String name = tokens.get(i + 1).text();
                    ElementDraft organisation = context.element(MetamodelKind.Organisation, name,
                            List.of("MAS"), path, tokens.get(i).line(), tokens.get(i).column(), "jcm-parser", name);
                    if (mas != null) mas.references.add(new SemanticReference("organisation", name, organisation.id));
                    organisation.attributes.put("id", new AttributeValue.Text(name));
                    int cursor = i + 2;
                    String sourceName = name + ".xml";
                    if (cursor + 1 < tokens.size() && tokens.get(cursor).text().equals(":")) {
                        sourceName = tokens.get(cursor + 1).text(); cursor += 2;
                    }
                    organisation.attributes.put("source", new AttributeValue.Text(sourceName));
                    int brace = cursor < tokens.size() && tokens.get(cursor).text().equals("{") ? cursor : -1;
                    int end = brace < 0 ? -1 : matchingBrace(tokens, brace);
                    if (brace >= 0 && end >= 0) {
                        parseOrganisationConfig(context, organisation, tokens.subList(brace + 1, end), path, name);
                        i = end;
                    }
                } else if (mas != null && (text.endsWith("-path") || text.equals("platform"))) {
                    String value = valueAfterColon(tokens, i);
                    if (!value.isBlank()) mas.attributes.put(text, new AttributeValue.Text(value));
                }
            }
        } catch (IOException exception) {
            context.diagnostic("JCM_PARSE_IO", Severity.ERROR, Phase.PARSING, null, null,
                    "Cannot parse discovered JCM file", path + ": " + exception.getMessage(), "Check the file encoding");
        }
    }

    private void parseAgentConfig(ElementDraft agent, List<JcmLexer.Token> body) {
        for (int i = 0; i + 1 < body.size(); i++) {
            String key = body.get(i).text();
            if (!KEYS.contains(key) || !body.get(i + 1).text().equals(":")) continue;
            int end = nextKey(body, i + 2);
            String value = join(body.subList(i + 2, end));
            if (!value.isBlank()) agent.attributes.put(key, new AttributeValue.Text(value));
            if (key.equals("focus")) {
                for (String item : commaValues(body.subList(i + 2, end))) {
                    agent.references.add(new SemanticReference("artifact", item, null));
                }
            } else if (key.equals("roles")) {
                for (int p = i + 2; p < end; p++) {
                    if (body.get(p).text().equals("in") && p > i + 2) {
                        agent.references.add(new SemanticReference("role", body.get(p - 1).text(), null));
                    }
                }
            } else if (key.equals("join")) {
                for (String item : commaValues(body.subList(i + 2, end))) {
                    agent.references.add(new SemanticReference("joinWorkspace", item, null));
                }
            }
            i = end - 1;
        }
    }

    private void parseWorkspace(ExtractionContext context, ElementDraft workspace,
                                List<JcmLexer.Token> body, Path path, String workspaceName) {
        for (int i = 0; i + 3 < body.size(); i++) {
            if (!body.get(i).text().equals("artifact") || !body.get(i + 2).text().equals(":")) continue;
            String name = body.get(i + 1).text();
            String type = body.get(i + 3).text();
            ElementDraft artifact = context.element(MetamodelKind.Artifact, name,
                    List.of("MAS", workspaceName), path, body.get(i).line(), body.get(i).column(), "jcm-parser", name);
            artifact.attributes.put("Name", new AttributeValue.Text(name));
            artifact.attributes.put("className", new AttributeValue.Text(type));
            int cursor = i + 4;
            if (cursor < body.size() && body.get(cursor).text().equals("(")) {
                int close = indexOf(body, ")", cursor + 1);
                if (close >= 0) artifact.attributes.put("parameters",
                        new AttributeValue.Text(join(body.subList(cursor + 1, close))));
            }
            workspace.references.add(new SemanticReference("artifact", name, null));
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
                    body.get(i).line(), body.get(i).column(), "jcm-parser", name);
            instance.attributes.put(keyword.equals("group") ? "Name" : "instanceName", new AttributeValue.Text(name));
            if (i + 3 < body.size() && body.get(i + 2).text().equals(":")) {
                instance.attributes.put("specificationType", new AttributeValue.Text(body.get(i + 3).text()));
            }
            int brace = i + 4 < body.size() && body.get(i + 4).text().equals("{") ? i + 4 : -1;
            if (keyword.equals("group") && brace >= 0) {
                int end = matchingBrace(body, brace);
                if (end >= 0) {
                    List<JcmLexer.Token> config = body.subList(brace + 1, end);
                    for (int p = 0; p + 1 < config.size(); p++) {
                        if (config.get(p).text().equals("players") && config.get(p + 1).text().equals(":")) {
                            int stop = nextKey(config, p + 2);
                            List<String> assignments = config.subList(p + 2, stop).stream().map(JcmLexer.Token::text)
                                    .filter(value -> !value.equals(",")).toList();
                            for (int q = 0; q + 1 < assignments.size(); q += 2) {
                                String agentName = assignments.get(q), roleName = assignments.get(q + 1);
                                instance.references.add(new SemanticReference("RefRole", roleName, null));
                                organisation.references.add(new SemanticReference("deploysAgent", agentName, null));
                            }
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

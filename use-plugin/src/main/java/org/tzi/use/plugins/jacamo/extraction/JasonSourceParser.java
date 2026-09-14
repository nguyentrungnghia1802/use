package org.tzi.use.plugins.jacamo.extraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.project.SourceSpan;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;

/** Static parser for the documented AgentSpeak subset used by the importer. */
final class JasonSourceParser {
    void parse(ExtractionContext context) {
        List<ElementDraft> agents = context.elements.stream().filter(element -> element.kind == MetamodelKind.Agent).toList();
        for (ElementDraft agent : agents) {
            String sourceName = ((AttributeValue.Text) agent.attributes.get("source")).value();
            List<Path> matches = context.graph.sources().stream().filter(source -> source.kind() == SourceKind.ASL
                    && source.path().getFileName().toString().equals(Path.of(sourceName).getFileName().toString()))
                    .map(source -> source.path()).toList();
            if (matches.size() == 1) parseAgent(context, agent, matches.getFirst());
            else if (matches.size() > 1) context.diagnostic("JASON_SOURCE_AMBIGUOUS", Severity.ERROR, Phase.PARSING,
                    agent.provenance.getFirst().span(), agent.id.value(), "Agent source is ambiguous",
                    matches.toString(), "Use unique source file names or paths");
        }
    }

    private void parseAgent(ExtractionContext context, ElementDraft agent, Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                String raw = stripComment(lines.get(lineIndex)).trim();
                if (raw.isEmpty() || raw.startsWith("{")) continue;
                int line = lineIndex + 1;
                if (!raw.endsWith(".")) {
                    diagnostic(context, path, line, "JASON_SYNTAX", Severity.ERROR,
                            "AgentSpeak statement must end with '.'", raw, "Terminate the statement with '.'");
                    continue;
                }
                String statement = raw.substring(0, raw.length() - 1).trim();
                if (statement.startsWith("+!")) parsePlan(context, agent, path, line, statement);
                else if (statement.startsWith("!")) addGoal(context, agent, path, line, statement.substring(1), true);
                else if (statement.contains(":-")) addRule(context, agent, path, line, statement);
                else if (isPredicate(statement)) addBelief(context, agent, path, line, statement);
                else diagnostic(context, path, line, "JASON_UNSUPPORTED_STATEMENT", Severity.WARNING,
                            "AgentSpeak statement is outside the supported static subset", statement,
                            "Keep it as source evidence or extend the parser explicitly");
            }
        } catch (IOException exception) {
            context.diagnostic("JASON_IO", Severity.ERROR, Phase.PARSING, agent.provenance.getFirst().span(),
                    agent.id.value(), "Cannot read AgentSpeak source", path + ": " + exception.getMessage(),
                    "Check file encoding and permissions");
        }
    }

    private void addBelief(ExtractionContext context, ElementDraft agent, Path path, int line, String expression) {
        String name = functor(expression);
        ElementDraft belief = context.element(MetamodelKind.Belief, name,
                List.of("MAS", agent.name), path, line, 1, "jason-parser", expression);
        belief.attributes.put("Name", new AttributeValue.Text(name));
        belief.attributes.put("Expression", new AttributeValue.Text(expression));
        belief.attributes.put("isInitial", new AttributeValue.Bool(true));
        agent.references.add(new SemanticReference("belief", name, belief.id));
    }

    private void addRule(ExtractionContext context, ElementDraft agent, Path path, int line, String statement) {
        String[] parts = statement.split(":-", 2);
        if (parts[0].isBlank() || parts[1].isBlank()) {
            diagnostic(context, path, line, "JASON_RULE_INVALID", Severity.ERROR, "Invalid rule",
                    statement, "Provide both rule head and expression"); return;
        }
        String name = functor(parts[0].trim());
        ElementDraft rule = context.element(MetamodelKind.Rule, name,
                List.of("MAS", agent.name), path, line, 1, "jason-parser", statement);
        rule.attributes.put("Expression", new AttributeValue.Text(parts[1].trim()));
        agent.references.add(new SemanticReference("rule", name, rule.id));
    }

    private void addGoal(ExtractionContext context, ElementDraft agent, Path path, int line,
                         String expression, boolean initial) {
        String name = functor(expression);
        ElementDraft goal = context.element(MetamodelKind.Goal, name,
                List.of("MAS", agent.name, "goal"), path, line, 1, "jason-parser", expression);
        goal.attributes.put("Name", new AttributeValue.Text(name));
        goal.attributes.put("Expression", new AttributeValue.Text(expression));
        goal.attributes.put("isInitial", new AttributeValue.Bool(initial));
        agent.references.add(new SemanticReference("hasGoal", name, goal.id));
    }

    private void parsePlan(ExtractionContext context, ElementDraft agent, Path path, int line, String statement) {
        int arrow = statement.indexOf("<-");
        if (arrow < 0) {
            diagnostic(context, path, line, "JASON_PLAN_INVALID", Severity.ERROR, "Plan body is missing",
                    statement, "Add <- followed by a plan body"); return;
        }
        String head = statement.substring(2, arrow).trim();
        String bodyText = statement.substring(arrow + 2).trim();
        String triggerExpression;
        String contextExpression = null;
        int colon = head.indexOf(':');
        if (colon >= 0) {
            triggerExpression = head.substring(0, colon).trim();
            contextExpression = head.substring(colon + 1).trim();
        } else triggerExpression = head;
        if (triggerExpression.isBlank() || bodyText.isBlank() || (colon >= 0 && contextExpression.isBlank())) {
            diagnostic(context, path, line, "JASON_PLAN_INVALID", Severity.ERROR, "Plan trigger, context or body is empty",
                    statement, "Provide a trigger and body; remove ':' or add a context"); return;
        }
        if (contextExpression != null && contextExpression.contains("|")) {
            diagnostic(context, path, line, "JASON_UNSUPPORTED_EXPRESSION", Severity.WARNING,
                    "Disjunction is preserved but not structurally interpreted", contextExpression,
                    "Resolve it explicitly before constraint translation");
        }
        String planName = "plan@" + line;
        ElementDraft plan = context.element(MetamodelKind.Plan, planName,
                List.of("MAS", agent.name), path, line, 1, "jason-parser", statement);
        plan.attributes.put("Name", new AttributeValue.Text(planName));
        agent.references.add(new SemanticReference("plan", planName, plan.id));
        String triggerName = functor(triggerExpression);
        ElementDraft trigger = context.element(MetamodelKind.TriggeringEvent, triggerName,
                List.of("MAS", agent.name, planName), path, line, 1, "jason-parser", triggerExpression);
        trigger.attributes.put("Expression", new AttributeValue.Text(triggerExpression));
        trigger.references.add(new SemanticReference("triggersPlan", planName, plan.id));
        if (contextExpression != null) {
            ElementDraft condition = context.element(MetamodelKind.Context, "context@" + line,
                    List.of("MAS", agent.name, planName), path, line, colon + 1, "jason-parser", contextExpression);
            condition.attributes.put("Expression", new AttributeValue.Text(contextExpression));
            plan.references.add(new SemanticReference("hasContext", condition.name, condition.id));
        }
        ElementDraft body = context.element(MetamodelKind.Body, "body@" + line,
                List.of("MAS", agent.name, planName), path, line, arrow + 3, "jason-parser", bodyText);
        body.attributes.put("Name", new AttributeValue.Text(body.name));
        plan.references.add(new SemanticReference("hasBody", body.name, body.id));
        List<String> terms = splitTopLevel(bodyText, ';');
        ElementDraft previous = null;
        for (int index = 0; index < terms.size(); index++) {
            String term = terms.get(index).trim();
            if (term.isEmpty()) continue;
            MetamodelKind kind = term.startsWith(".send") ? MetamodelKind.Message
                    : term.startsWith(".") ? MetamodelKind.InternalAction
                    : term.startsWith("+") || term.startsWith("-") ? MetamodelKind.MentalNotes
                    : MetamodelKind.ExternalAction;
            String name = functor(term.replaceFirst("^[+-.]", ""));
            ElementDraft action = context.element(kind, name,
                    List.of("MAS", agent.name, planName, "action" + index), path, line, 1,
                    "jason-parser", term);
            action.attributes.put("Name", new AttributeValue.Text(name));
            action.attributes.put("Expression", new AttributeValue.Text(term));
            if (kind == MetamodelKind.Message) {
                action.attributes.put("content", new AttributeValue.Text(term));
                action.attributes.put("isBroadcast", new AttributeValue.Bool(term.startsWith(".broadcast")));
            }
            if (kind == MetamodelKind.ExternalAction) {
                action.references.add(new SemanticReference("operation", name, null));
            }
            body.references.add(new SemanticReference("bodyterm", name, action.id));
            plan.references.add(new SemanticReference("hasAction", name, action.id));
            if (previous != null) previous.references.add(new SemanticReference("nextAction", name, action.id));
            else body.references.add(new SemanticReference("firstAction", name, action.id));
            previous = action;
        }
    }

    private void diagnostic(ExtractionContext context, Path path, int line, String code, Severity severity,
                            String message, String evidence, String remediation) {
        context.diagnostic(code, severity, Phase.PARSING, new SourceSpan(path, line, 1, line,
                Math.max(1, evidence.length())), null, message, evidence, remediation);
    }

    private boolean isPredicate(String statement) {
        return statement.matches("[a-z][A-Za-z0-9_]*(\\s*\\(.*\\))?");
    }

    private String functor(String expression) {
        String trimmed = expression.trim();
        int open = trimmed.indexOf('(');
        return (open < 0 ? trimmed : trimmed.substring(0, open)).trim();
    }

    private String stripComment(String line) {
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }

    private List<String> splitTopLevel(String text, char separator) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(' || ch == '[') depth++;
            else if (ch == ')' || ch == ']') depth--;
            else if (ch == separator && depth == 0) { result.add(text.substring(start, i)); start = i + 1; }
        }
        result.add(text.substring(start));
        return result;
    }
}

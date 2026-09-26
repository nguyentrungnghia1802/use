package org.tzi.use.plugins.jacamo.extraction;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jason.asSemantics.Agent;
import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.InternalAction;
import jason.asSyntax.Literal;
import jason.asSyntax.Plan;
import jason.asSyntax.PlanBody;
import jason.asSyntax.Rule;
import jason.asSyntax.SourceInfo;
import jason.asSyntax.Structure;
import jason.asSyntax.Term;
import jason.asSyntax.parser.ParseException;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.project.SourceSpan;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;

/**
 * Static AgentSpeak extraction backed by Jason's pinned parser. Directives are masked before
 * parsing and internal-action lookup is inert, so importing source cannot execute includes or
 * instantiate project/plugin classes.
 */
final class JasonSourceParser {
    private static final Pattern DIRECTIVE = Pattern.compile(
            "(?m)^[\\t ]*\\{[\\t ]*([a-zA-Z_][a-zA-Z0-9_.-]*)[\\t ]*\\(([^\\r\\n]*)\\)[\\t ]*}[\\t ]*(?=\\r?$)");
    private static final Pattern ANONYMOUS_VARIABLE = Pattern.compile("(?<![A-Za-z0-9_])_[0-9]+(?=$|[^A-Za-z0-9_])");

    void parse(ExtractionContext context) {
        Map<Path, ParsedSource> parsed = new HashMap<>();
        List<ElementDraft> agents = context.elements.stream()
                .filter(element -> element.kind == MetamodelKind.Agent).toList();
        for (ElementDraft agent : agents) {
            String sourceName = ((AttributeValue.Text) agent.attributes.get("source")).value();
            List<Path> matches = sourceMatches(context, sourceName);
            if (matches.size() == 1) {
                Path source = matches.getFirst();
                ParsedSource syntax = parsed.computeIfAbsent(source, path -> parseSource(context, path));
                if (syntax != null) extractAgent(context, agent, source, syntax, true);
            } else if (matches.size() > 1) {
                context.diagnostic("JASON_SOURCE_AMBIGUOUS", Severity.ERROR, Phase.PARSING,
                        agent.provenance.getFirst().span(), agent.id.value(), "Agent source is ambiguous",
                        matches.toString(), "Use one exact source path within the declared asl-path roots");
            }
        }
    }

    private List<Path> sourceMatches(ExtractionContext context, String sourceName) {
        String portable = sourceName.replace('\\', '/');
        Path suffix;
        try { suffix = Path.of(portable).normalize(); }
        catch (RuntimeException invalid) { return List.of(); }
        boolean qualified = suffix.getNameCount() > 1;
        return context.graph.sources().stream().filter(source -> source.kind() == SourceKind.ASL)
                .map(source -> source.path())
                .filter(path -> qualified ? path.endsWith(suffix)
                        : path.getFileName().toString().equals(suffix.getFileName().toString()))
                .sorted().toList();
    }

    private ParsedSource parseSource(ExtractionContext context, Path path) {
        try {
            String original = context.read(path);
            SanitizedSource sanitized = sanitizeDirectives(original);
            for (Directive directive : sanitized.directives()) {
                Severity severity = directive.argument().contains("$") ? Severity.WARNING : Severity.INFO;
                context.diagnostic("JASON_DIRECTIVE_NOT_EXECUTED", severity, Phase.PARSING,
                        new SourceSpan(path, directive.line(), 1, directive.line(),
                                Math.max(1, directive.source().length())), null,
                        "AgentSpeak directive was retained as source evidence and was not executed",
                        directive.source(), "Use statically discovered local includes; external templates remain explicit source-only semantics");
            }
            StaticParsingAgent parserAgent = new StaticParsingAgent();
            parserAgent.setConsiderToAddMIForThisAgent(false);
            parserAgent.initAg();
            try {
                parserAgent.parseAS(new StringReader(sanitized.text()), path.toString());
                return new ParsedSource(original, List.copyOf(parserAgent.getInitialBels()),
                        List.copyOf(parserAgent.getInitialGoals()), List.copyOf(parserAgent.getPL().getPlans()));
            } finally {
                parserAgent.stopAg();
            }
        } catch (ParseException error) {
            int line = 1, column = 1;
            if (error.currentToken != null && error.currentToken.next != null) {
                line = Math.max(1, error.currentToken.next.beginLine);
                column = Math.max(1, error.currentToken.next.beginColumn);
            }
            context.diagnostic("JASON_SYNTAX", Severity.ERROR, Phase.PARSING,
                    new SourceSpan(path, line, column, line, column), null,
                    "Jason rejected the AgentSpeak source", error.getMessage(),
                    "Correct the source syntax; the importer does not recover by guessing statements");
        } catch (IOException error) {
            context.diagnostic("JASON_IO", Severity.ERROR, Phase.PARSING, null, null,
                    "Cannot read AgentSpeak source", path + ": " + error.getMessage(),
                    "Check file encoding and permissions");
        } catch (Exception error) {
            context.diagnostic("JASON_PARSE_FAILURE", Severity.ERROR, Phase.PARSING, null, null,
                    "Cannot parse AgentSpeak source without execution", path + ": " + error.getMessage(),
                    "Use syntax supported by the pinned Jason parser and report deterministic parser failures");
        }
        return null;
    }

    private void extractAgent(ExtractionContext context, ElementDraft agent, Path path,
                              ParsedSource source, boolean primarySource) {
        for (Literal literal : source.beliefs()) {
            if (literal instanceof Rule rule) addRule(context, agent, path, source.text(), rule);
            else addBelief(context, agent, path, source.text(), literal);
        }
        for (Literal goal : source.goals()) addGoal(context, agent, path, source.text(), goal);
        for (Plan plan : source.plans()) addPlan(context, agent, path, source.text(), plan, primarySource);
    }

    private void addBelief(ExtractionContext context, ElementDraft agent, Path path,
                           String source, Literal literal) {
        int line = sourceLine(literal.getSrcInfo());
        String expression = normalize(literal.toString());
        String spelling = sourceStatement(source, line, expression);
        ElementDraft existing = existingMentalState(context, agent, MetamodelKind.Belief, expression);
        if (existing != null) {
            context.addProvenance(existing, path, line, sourceColumn(source, line, literal.getFunctor()),
                    "jason-parser", spelling);
            existing.sourceFacts.put("alsoDeclaredInASL", new AttributeValue.Bool(true));
            if (agent.references.stream().noneMatch(reference -> reference.feature().equals("beliefs")
                    && existing.id.equals(reference.targetId())))
                agent.references.add(new SemanticReference("beliefs", expression, existing.id));
            return;
        }
        String localId = uniqueLocalId(context, MetamodelKind.Belief,
                List.of("MAS", agent.name), expression, line);
        ElementDraft belief = context.elementWithLocalId(MetamodelKind.Belief, literal.getFunctor(), localId,
                List.of("MAS", agent.name), path, line, sourceColumn(source, line, literal.getFunctor()),
                "jason-parser", spelling);
        belief.attributes.put("literal", new AttributeValue.Text(expression));
        belief.sourceFacts.put("isInitial", new AttributeValue.Bool(true));
        agent.references.add(new SemanticReference("beliefs", expression, belief.id));
    }

    private void addRule(ExtractionContext context, ElementDraft agent, Path path,
                         String source, Rule rule) {
        int line = sourceLine(rule.getSrcInfo());
        String statement = normalize(rule.toString());
        String spelling = sourceStatement(source, line, statement);
        String key = "rule@" + relativeSource(context, path) + ":" + String.format(Locale.ROOT, "%09d", line);
        agent.sourceFacts.put(key, new AttributeValue.Text(statement));
        context.addProvenance(agent, path, line, sourceColumn(source, line, rule.getFunctor()),
                "jason-parser", spelling);
        diagnostic(context, path, line, "JASON_RULE_SOURCE_ONLY", Severity.WARNING,
                "V2 has no Rule EClass; rule retained as source fact", statement,
                "Do not translate rule semantics without an explicit supported contract");
    }

    private void addGoal(ExtractionContext context, ElementDraft agent, Path path,
                         String source, Literal literal) {
        int line = sourceLine(literal.getSrcInfo());
        String expression = normalize(literal.toString());
        String spelling = sourceStatement(source, line, "!" + expression);
        ElementDraft existing = existingMentalState(context, agent, MetamodelKind.AGoal, expression);
        if (existing != null) {
            context.addProvenance(existing, path, line, sourceColumn(source, line, literal.getFunctor()),
                    "jason-parser", spelling);
            existing.sourceFacts.put("alsoDeclaredInASL", new AttributeValue.Bool(true));
            if (agent.references.stream().noneMatch(reference -> reference.feature().equals("goals")
                    && existing.id.equals(reference.targetId())))
                agent.references.add(new SemanticReference("goals", expression, existing.id));
            return;
        }
        String localId = uniqueLocalId(context, MetamodelKind.AGoal,
                List.of("MAS", agent.name, "goal"), expression, line);
        ElementDraft goal = context.elementWithLocalId(MetamodelKind.AGoal, literal.getFunctor(), localId,
                List.of("MAS", agent.name, "goal"), path, line,
                sourceColumn(source, line, literal.getFunctor()), "jason-parser", spelling);
        goal.attributes.put("literal", new AttributeValue.Text(expression));
        goal.sourceFacts.put("isInitial", new AttributeValue.Bool(true));
        goal.attributes.put("type", new AttributeValue.EnumLiteral("AgentGoalType", "ACHIEVEMENT"));
        agent.references.add(new SemanticReference("goals", expression, goal.id));
    }

    private void addPlan(ExtractionContext context, ElementDraft agent, Path path, String source,
                         Plan syntax, boolean primarySource) {
        int line = sourceLine(syntax.getTrigger().getLiteral().getSrcInfo());
        String statement = sourceStatement(source, line, normalize(syntax.toASString()));
        String planName = primarySource ? "plan@" + line
                : "plan@" + relativeSource(context, path) + ":" + line;
        planName = uniqueName(context, MetamodelKind.Plan, List.of("MAS", agent.name), planName);
        ElementDraft plan = context.element(MetamodelKind.Plan, planName,
                List.of("MAS", agent.name), path, line, sourceColumn(source, line, triggerMarker(syntax)),
                "jason-parser", statement);
        plan.sourceFacts.put("body", new AttributeValue.Text(renderBody(syntax.getBody())));
        agent.references.add(new SemanticReference("plans", planName, plan.id));

        String triggerExpression = normalize(syntax.getTrigger().getLiteral().toString());
        ElementDraft trigger = context.elementWithLocalId(MetamodelKind.Event,
                syntax.getTrigger().getLiteral().getFunctor(), triggerExpression,
                List.of("MAS", agent.name, planName), path, line,
                sourceColumn(source, line, syntax.getTrigger().getLiteral().getFunctor()),
                "jason-parser", triggerExpression);
        trigger.attributes.put("literal", new AttributeValue.Text(triggerExpression));
        trigger.attributes.put("operator", new AttributeValue.Text(syntax.getTrigger().getOperator().toString()));
        trigger.attributes.put("type", new AttributeValue.Text(syntax.getTrigger().getType().toString()));
        plan.references.add(new SemanticReference("triggeringEvent", triggerExpression, trigger.id));

        if (syntax.getContext() != null) {
            String expression = normalize(syntax.getContext().toString());
            plan.attributes.put("context", new AttributeValue.Text(expression));
            if (expression.contains("|")) diagnostic(context, path, line,
                    "JASON_UNSUPPORTED_EXPRESSION", Severity.WARNING,
                    "Disjunction is preserved but not structurally interpreted", expression,
                    "Resolve it explicitly before constraint translation");
        }

        BodyCursor cursor = new BodyCursor();
        List<FocusBinding> focus = new ArrayList<>();
        collectFocusBindings(syntax.getContext(), focus);
        walkBody(context, agent, plan, path, source, syntax.getBody(), "", focus,
                new LinkedHashMap<>(), cursor);
    }

    private void walkBody(ExtractionContext context, ElementDraft agent, ElementDraft plan,
                          Path path, String source, PlanBody body, String prefix,
                          List<FocusBinding> inheritedFocus,
                          Map<String, DynamicArtifactBinding> inheritedArtifacts,
                          BodyCursor cursor) {
        int sibling = 0;
        for (PlanBody item = body; item != null && !item.isEmptyBody(); item = item.getBodyNext(), sibling++) {
            String bodyPath = prefix.isEmpty() ? Integer.toString(sibling) : prefix + "." + sibling;
            Term term = item.getBodyTerm();
            List<FocusBinding> focus = new ArrayList<>(inheritedFocus);
            collectFocusBindings(term, focus);
            Map<String, DynamicArtifactBinding> artifacts = new LinkedHashMap<>(inheritedArtifacts);
            collectDynamicArtifactBinding(term, artifacts);

            if (item.getBodyType() == PlanBody.BodyType.action
                    || item.getBodyType() == PlanBody.BodyType.internalAction) {
                addAction(context, agent, plan, path, source, item, bodyPath, focus, artifacts, cursor.next());
            } else {
                String expression = bodyTermExpression(item);
                plan.sourceFacts.put("bodyTerm@" + bodyPath, new AttributeValue.Text(expression));
                diagnostic(context, path, sourceLine(term == null ? null : term.getSrcInfo()),
                        "JASON_BODY_TERM_SOURCE_ONLY", Severity.WARNING,
                        "V2 Action does not represent this body term", expression,
                        "Retain body position and source; do not invent an Action");
            }

            List<PlanBody> nested = new ArrayList<>();
            collectNestedBodies(term, nested);
            for (int nestedIndex = 0; nestedIndex < nested.size(); nestedIndex++) {
                walkBody(context, agent, plan, path, source, nested.get(nestedIndex),
                        bodyPath + ".n" + nestedIndex, focus, artifacts, cursor);
            }
            inheritedFocus = focus;
            inheritedArtifacts = artifacts;
        }
    }

    private void addAction(ExtractionContext context, ElementDraft agent, ElementDraft plan,
                           Path path, String source, PlanBody body, String bodyPath,
                           List<FocusBinding> focus, Map<String, DynamicArtifactBinding> artifacts,
                           int ordinal) {
        Term term = body.getBodyTerm();
        String expression = normalize(term.toString());
        String name = term instanceof Literal literal ? literal.getFunctor()
                : term instanceof Structure structure ? structure.getFunctor() : expression;
        if (name.startsWith(".")) name = name.substring(1);
        int line = sourceLine(term.getSrcInfo());
        ElementDraft action = context.element(MetamodelKind.Action, name,
                List.of("MAS", agent.name, plan.name, "action" + ordinal), path, line,
                sourceColumn(source, line, name), "jason-parser", expression);
        boolean internal = body.getBodyType() == PlanBody.BodyType.internalAction;
        action.attributes.put("name", new AttributeValue.Text(name));
        action.attributes.put("kind", new AttributeValue.EnumLiteral("ActionKind",
                internal ? "INTERNAL" : "EXTERNAL"));
        int arity = term instanceof Structure structure ? structure.getArity() : 0;
        action.attributes.put("arity", new AttributeValue.IntegerNumber(arity));
        action.sourceFacts.put("Expression", new AttributeValue.Text(expression));
        action.sourceFacts.put("bodyPosition", new AttributeValue.IntegerNumber(ordinal));
        action.sourceFacts.put("bodyPath", new AttributeValue.Text(bodyPath));
        if (!internal) {
            attachReceiverEvidence(action, term, focus, artifacts);
            action.references.add(new SemanticReference("operation", name, null));
        }
        plan.references.add(new SemanticReference("actions", name, action.id));
    }

    private void attachReceiverEvidence(ElementDraft action, Term term,
                                        List<FocusBinding> focus,
                                        Map<String, DynamicArtifactBinding> artifacts) {
        if (!(term instanceof Literal literal)) return;
        Literal artifactId = literal.getAnnot("artifact_id");
        if (artifactId == null || artifactId.getArity() != 1) return;
        String id = normalize(artifactId.getTerm(0).toString());
        Set<String> exact = new LinkedHashSet<>();
        Set<String> types = new LinkedHashSet<>();
        for (FocusBinding binding : focus) if (binding.artifactId().equals(id)) {
            if (binding.qualifiedArtifact() != null) exact.add(binding.qualifiedArtifact());
            if (binding.artifactType() != null) types.add(binding.artifactType());
        }
        DynamicArtifactBinding dynamic = artifacts.get(id);
        if (dynamic != null && dynamic.artifactType() != null) types.add(dynamic.artifactType());
        if (exact.size() == 1) {
            action.sourceFacts.put("receiverArtifact", new AttributeValue.Text(exact.iterator().next()));
            action.sourceFacts.put("receiverEvidence", new AttributeValue.Text("artifact_id/focused variable identity"));
        } else {
            if (types.size() == 1) action.sourceFacts.put("receiverArtifactType",
                    new AttributeValue.Text(types.iterator().next()));
            action.sourceFacts.put("receiverResolution", new AttributeValue.Text("RUNTIME_DEPENDENT"));
            action.sourceFacts.put("receiverArtifactIdTerm", new AttributeValue.Text(id));
        }
    }

    private void collectFocusBindings(Term term, List<FocusBinding> result) {
        if (term == null || term instanceof PlanBody) return;
        if (term instanceof Literal literal && literal.getFunctor().equals("focused") && literal.getArity() == 3) {
            String artifactId = normalize(literal.getTerm(2).toString());
            String workspace = groundAtom(literal.getTerm(0));
            String artifact = groundAtom(literal.getTerm(1));
            String type = annotationString(literal.getTerm(1), "artifact_type");
            result.add(new FocusBinding(artifactId,
                    workspace == null || artifact == null ? null : workspace + "." + artifact, type));
        }
        if (term instanceof jason.asSyntax.ListTerm list) {
            if (list.getAsList() != null)
                for (Term child : list.getAsList()) collectFocusBindings(child, result);
        } else if (term instanceof Structure structure && structure.getTerms() != null) {
            for (Term child : structure.getTerms()) collectFocusBindings(child, result);
        }
    }

    private void collectDynamicArtifactBinding(Term term, Map<String, DynamicArtifactBinding> result) {
        if (!(term instanceof Literal literal) || !literal.getFunctor().equals("makeArtifact")
                || literal.getArity() < 4) return;
        String id = normalize(literal.getTerm(3).toString());
        String type = stringValue(literal.getTerm(1));
        String name = literal.getTerm(0).isVar() ? null : normalize(literal.getTerm(0).toString());
        result.put(id, new DynamicArtifactBinding(name, type));
    }

    private void collectNestedBodies(Term term, List<PlanBody> result) {
        if (term == null) return;
        if (term instanceof PlanBody nested) {
            result.add(nested);
            return;
        }
        if (term instanceof jason.asSyntax.ListTerm list) {
            if (list.getAsList() != null)
                for (Term child : list.getAsList()) collectNestedBodies(child, result);
        } else if (term instanceof Structure structure && structure.getTerms() != null) {
            for (Term child : structure.getTerms()) collectNestedBodies(child, result);
        }
    }

    private String annotationString(Term term, String annotation) {
        if (!(term instanceof Literal literal)) return null;
        Literal value = literal.getAnnot(annotation);
        return value == null || value.getArity() != 1 ? null : stringValue(value.getTerm(0));
    }

    private String stringValue(Term term) {
        if (!term.isString()) return null;
        String value = term.toString();
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private String groundAtom(Term term) {
        if (!(term instanceof Literal literal) || term.isVar() || literal.getArity() != 0
                || literal.hasAnnot()) return null;
        return literal.getFunctor();
    }

    private String uniqueLocalId(ExtractionContext context, MetamodelKind kind, List<String> owner,
                                 String preferred, int line) {
        String candidate = preferred;
        int suffix = 0;
        while (containsIdentity(context, kind, owner, candidate)) {
            candidate = preferred + "@" + line + (suffix++ == 0 ? "" : ":" + suffix);
        }
        return candidate;
    }

    private ElementDraft existingMentalState(ExtractionContext context, ElementDraft agent,
                                             MetamodelKind kind, String literal) {
        AttributeValue.Text value = new AttributeValue.Text(literal);
        return context.elements.stream().filter(element -> element.kind == kind)
                .filter(element -> element.id.ownerPath().size() >= 2
                        && element.id.ownerPath().get(0).equals("MAS")
                        && element.id.ownerPath().get(1).equals(agent.name))
                .filter(element -> value.equals(element.attributes.get("literal")))
                .findFirst().orElse(null);
    }

    private String uniqueName(ExtractionContext context, MetamodelKind kind, List<String> owner,
                              String preferred) {
        String candidate = preferred;
        int suffix = 2;
        while (containsIdentity(context, kind, owner, candidate)) candidate = preferred + "#" + suffix++;
        return candidate;
    }

    private boolean containsIdentity(ExtractionContext context, MetamodelKind kind,
                                     List<String> owner, String localId) {
        return context.elements.stream().anyMatch(element -> element.kind == kind
                && element.id.ownerPath().equals(owner) && element.id.localId().equals(localId));
    }

    private String triggerMarker(Plan plan) {
        return plan.getTrigger().getOperator() + plan.getTrigger().getType().toString()
                + plan.getTrigger().getLiteral().getFunctor();
    }

    private String renderBody(PlanBody body) {
        List<String> terms = new ArrayList<>();
        for (PlanBody item = body; item != null && !item.isEmptyBody(); item = item.getBodyNext())
            terms.add(bodyTermExpression(item));
        return String.join("; ", terms);
    }

    private String bodyTermExpression(PlanBody body) {
        String prefix = switch (body.getBodyType()) {
            case achieve -> "!";
            case achieveNF -> "!!";
            case test -> "?";
            case addBel, addBelBegin, addBelEnd, addBelNewFocus -> "+";
            case delBel, delBelNewFocus -> "-";
            case delAddBel -> "-+";
            default -> "";
        };
        return prefix + normalize(body.getBodyTerm().toString());
    }

    private String relativeSource(ExtractionContext context, Path path) {
        return context.graph.root().path().relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private int sourceLine(SourceInfo source) {
        return source == null ? 1 : Math.max(1, source.getBeginSrcLine());
    }

    private int sourceColumn(String source, int line, String marker) {
        String text = source.lines().skip(Math.max(0, line - 1L)).findFirst().orElse("");
        int found = marker == null || marker.isBlank() ? -1 : text.indexOf(marker);
        if (found >= 0) return found + 1;
        int first = 0;
        while (first < text.length() && Character.isWhitespace(text.charAt(first))) first++;
        return first + 1;
    }

    private String sourceStatement(String source, int line, String fallback) {
        int offset = lineOffset(source, line);
        while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) offset++;
        if (offset >= source.length()) return fallback;
        int start = offset;
        int parentheses = 0, brackets = 0, braces = 0;
        boolean quoted = false, escaped = false, lineComment = false, blockComment = false;
        for (int index = offset; index < source.length(); index++) {
            char ch = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (ch == '\n' || ch == '\r') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') { blockComment = false; index++; }
                continue;
            }
            if (!quoted && ch == '/' && next == '/') { lineComment = true; index++; continue; }
            if (!quoted && ch == '/' && next == '*') { blockComment = true; index++; continue; }
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
                continue;
            }
            if (ch == '"') { quoted = true; continue; }
            if (ch == '(') parentheses++;
            else if (ch == ')') parentheses--;
            else if (ch == '[') brackets++;
            else if (ch == ']') brackets--;
            else if (ch == '{') braces++;
            else if (ch == '}') braces--;
            else if (ch == '.' && parentheses == 0 && brackets == 0 && braces == 0
                    && (next == '\0' || Character.isWhitespace(next) || next == '/')) {
                return source.substring(start, index + 1).stripTrailing();
            }
        }
        return fallback;
    }

    private int lineOffset(String source, int line) {
        int current = 1;
        for (int index = 0; index < source.length(); index++) {
            if (current == line) return index;
            if (source.charAt(index) == '\n') current++;
        }
        return current == line ? source.length() : 0;
    }

    private String normalize(String expression) {
        return ANONYMOUS_VARIABLE.matcher(expression).replaceAll("_");
    }

    private SanitizedSource sanitizeDirectives(String source) {
        Matcher matcher = DIRECTIVE.matcher(source);
        StringBuilder masked = new StringBuilder(source);
        List<Directive> directives = new ArrayList<>();
        while (matcher.find()) {
            int line = 1;
            for (int index = 0; index < matcher.start(); index++) if (source.charAt(index) == '\n') line++;
            directives.add(new Directive(matcher.group(), matcher.group(2), line));
            for (int index = matcher.start(); index < matcher.end(); index++) {
                char ch = masked.charAt(index);
                if (ch != '\r' && ch != '\n') masked.setCharAt(index, ' ');
            }
        }
        return new SanitizedSource(masked.toString(), List.copyOf(directives));
    }

    private void diagnostic(ExtractionContext context, Path path, int line, String code,
                            Severity severity, String message, String evidence, String remediation) {
        context.diagnostic(code, severity, Phase.PARSING,
                new SourceSpan(path, Math.max(1, line), 1, Math.max(1, line), Math.max(1, evidence.length())),
                null, message, evidence, remediation);
    }

    private record ParsedSource(String text, List<Literal> beliefs,
                                List<Literal> goals, List<Plan> plans) { }
    private record Directive(String source, String argument, int line) { }
    private record SanitizedSource(String text, List<Directive> directives) { }
    private record FocusBinding(String artifactId, String qualifiedArtifact, String artifactType) { }
    private record DynamicArtifactBinding(String artifactName, String artifactType) { }
    private static final class BodyCursor {
        private int value;
        int next() { return value++; }
    }

    /** Never reflectively loads or instantiates an internal action while parsing untrusted source. */
    private static final class StaticParsingAgent extends Agent {
        private final InternalAction inert = new DefaultInternalAction();
        @Override public InternalAction getIA(String iaName) { return inert; }
    }
}

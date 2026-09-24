package org.tzi.use.plugins.jacamo.verification;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tzi.use.plugins.jacamo.constraint.ConstraintSpec;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.project.SourceSpan;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MPrePostCondition;

/** Immutable registry joining source provenance to the constraints compiled by USE. */
public final class ConstraintRegistry {
    private static final Pattern PROFILE_INVARIANT = Pattern.compile(
            "(?ms)^\\s*context\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+inv\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(.*?)(?=^\\s*context\\s+|\\z)");
    private static final Pattern PROFILE_OPERATION_CONTEXT = Pattern.compile(
            "(?m)^[ \\t]*context[ \\t]+([A-Za-z_][A-Za-z0-9_]*)::([A-Za-z_][A-Za-z0-9_]*)"
                    + "[ \\t]*\\([^\\r\\n]*\\)[ \\t]*$");
    private static final Pattern PROFILE_CONTEXT_START = Pattern.compile("(?m)^[ \\t]*context[ \\t]+");
    private static final Pattern PROFILE_OPERATION_CLAUSE = Pattern.compile(
            "(?ms)^[ \\t]*(pre|post)[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*:[ \\t]*(.*?)"
                    + "(?=^[ \\t]*(?:pre|post)[ \\t]+[A-Za-z_][A-Za-z0-9_]*[ \\t]*:|\\z)");
    private final List<ConstraintDescriptor> descriptors;
    private final Map<String, ConstraintDescriptor> compiled;
    private final Map<String, String> fingerprints;

    private ConstraintRegistry(List<ConstraintDescriptor> descriptors, Map<String, ConstraintDescriptor> compiled,
                               Map<String, String> fingerprints) {
        this.descriptors = descriptors.stream().sorted(Comparator.comparing(ConstraintDescriptor::id)).toList();
        this.compiled = Map.copyOf(compiled);
        this.fingerprints = java.util.Collections.unmodifiableMap(new TreeMap<>(fingerprints));
    }

    public static RegisteredProfile profile(ConstraintOrigin origin, OclProfileLoader.LoadedProfile profile) {
        if (origin == ConstraintOrigin.TRANSLATED) throw new IllegalArgumentException("profile origin cannot be TRANSLATED");
        return new RegisteredProfile(origin, profile);
    }

    public static ConstraintRegistry load(MModel model, OclGenerator.GeneratedOcl generated,
                                          List<RegisteredProfile> profiles) {
        Map<String, ConstraintDescriptor> sourceDescriptors = new LinkedHashMap<>();
        OclGenerator renderer = new OclGenerator();
        for (ConstraintSpec spec : generated.emitted()) {
            ConstraintKind kind = switch (spec.kind()) {
                case INVARIANT -> ConstraintKind.INV;
                case PRE -> ConstraintKind.PRE;
                case POST -> ConstraintKind.POST;
            };
            Path path = spec.provenance().span().path();
            ConstraintDescriptor descriptor = new ConstraintDescriptor(spec.id(), spec.name(), spec.contextClass(),
                    spec.operationName(), kind, ConstraintOrigin.TRANSLATED, path, spec.provenance().span(),
                    spec.dependencies(), true, renderer.render(spec.expression()));
            sourceDescriptors.put(key(descriptor), descriptor);
        }
        for (RegisteredProfile registration : profiles) {
            String content = registration.profile().content();
            Matcher matcher = PROFILE_INVARIANT.matcher(content);
            while (matcher.find()) {
                int line = 1 + (int) content.substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                Path path = registration.profile().origin();
                String context = matcher.group(1);
                String name = matcher.group(2);
                String expression = stripTrailingProfileComments(matcher.group(3));
                String id = authoredId(registration.origin(), path, context, null, ConstraintKind.INV, name);
                SourceSpan span = new SourceSpan(path, line, 1, line, Math.max(1, matcher.group().length()));
                ConstraintDescriptor descriptor = new ConstraintDescriptor(id, name, context, null,
                        ConstraintKind.INV, registration.origin(), path, span, List.of(), true, expression);
                sourceDescriptors.put(key(descriptor), descriptor);
            }
            Matcher contextMatcher = PROFILE_OPERATION_CONTEXT.matcher(content);
            while (contextMatcher.find()) {
                int nextContext = content.length();
                Matcher next = PROFILE_CONTEXT_START.matcher(content);
                if (next.find(contextMatcher.end())) nextContext = next.start();
                Matcher clause = PROFILE_OPERATION_CLAUSE.matcher(content.substring(contextMatcher.end(), nextContext));
                while (clause.find()) {
                    int start = contextMatcher.end() + clause.start();
                    int line = 1 + (int) content.substring(0, start).chars().filter(c -> c == '\n').count();
                    Path path = registration.profile().origin();
                    ConstraintKind kind = "pre".equals(clause.group(1)) ? ConstraintKind.PRE : ConstraintKind.POST;
                    String name = clause.group(2);
                    String id = authoredId(registration.origin(), path, contextMatcher.group(1),
                            contextMatcher.group(2), kind, name);
                    SourceSpan span = new SourceSpan(path, line, 1, line, Math.max(1, clause.group().length()));
                    ConstraintDescriptor descriptor = new ConstraintDescriptor(id, name,
                            contextMatcher.group(1), contextMatcher.group(2), kind,
                            registration.origin(), path, span, List.of(), true,
                            stripTrailingProfileComments(clause.group(3)));
                    sourceDescriptors.put(key(descriptor), descriptor);
                }
            }
        }

        List<ConstraintDescriptor> all = new ArrayList<>();
        Map<String, ConstraintDescriptor> compiled = new LinkedHashMap<>();
        for (MClassInvariant invariant : model.classInvariants()) {
            String key = invariantKey(invariant);
            ConstraintDescriptor descriptor = sourceDescriptors.get(key);
            if (descriptor == null) {
                var order = generated.orderProjections().stream().filter(p -> p.owner().equals(invariant.cls().name())
                        && ("order_" + p.ruleId()).equals(invariant.name())).findFirst();
                if (order.isPresent()) {
                    var proof = order.get();
                    Path mapping = Path.of(org.tzi.use.plugins.jacamo.mapping.ActiveBaseline.RESOURCE_ROOT,
                            org.tzi.use.plugins.jacamo.mapping.ActiveBaseline.MAPPING);
                    descriptor = new ConstraintDescriptor("CORE:ORDER:" + proof.sourceIdentity(), invariant.name(),
                            invariant.cls().name(), null, ConstraintKind.INV, ConstraintOrigin.CORE, mapping,
                            null, List.of(), invariant.isActive(), invariant.bodyExpression().toString());
                }
            }
            if (descriptor == null) descriptor = fallback(invariant);
            all.add(descriptor);
            compiled.put(key, descriptor);
        }
        for (MPrePostCondition condition : model.prePostConditions()) {
            String key = conditionKey(condition);
            ConstraintDescriptor descriptor = sourceDescriptors.get(key);
            if (descriptor == null) descriptor = fallback(condition);
            all.add(descriptor);
            compiled.put(key, descriptor);
        }
        Map<String, String> fingerprints = new TreeMap<>();
        fingerprints.put("useModelSha256", sha256(generated.useModel()));
        fingerprints.put("translatedManifestSha256", sha256(generated.provenanceManifest()));
        for (RegisteredProfile profile : profiles)
            fingerprints.put(profile.origin().name().toLowerCase() + ":" + portable(profile.profile().origin()),
                    profile.profile().sha256());
        return new ConstraintRegistry(all, compiled, fingerprints);
    }

    public List<ConstraintDescriptor> descriptors() { return descriptors; }
    public Map<String, String> fingerprints() { return fingerprints; }
    public ConstraintDescriptor byId(String id) {
        return descriptors.stream().filter(descriptor -> descriptor.id().equals(id)).findFirst().orElse(null);
    }
    ConstraintDescriptor descriptor(MClassInvariant invariant) { return compiled.get(invariantKey(invariant)); }
    ConstraintDescriptor descriptor(MPrePostCondition condition) { return compiled.get(conditionKey(condition)); }

    private static ConstraintDescriptor fallback(MClassInvariant invariant) {
        Path path = Path.of("compiled-model");
        return new ConstraintDescriptor("COMPILED:" + invariant.qualifiedName(), invariant.name(),
                invariant.cls().name(), null, ConstraintKind.INV, ConstraintOrigin.USER, path,
                new SourceSpan(path, 1, 1, 1, 1), List.of(), invariant.isActive(), invariant.bodyExpression().toString());
    }

    private static ConstraintDescriptor fallback(MPrePostCondition condition) {
        Path path = Path.of("compiled-model");
        ConstraintKind kind = condition.isPre() ? ConstraintKind.PRE : ConstraintKind.POST;
        return new ConstraintDescriptor("COMPILED:" + condition, condition.name(), condition.cls().name(),
                condition.operation().name(), kind, ConstraintOrigin.USER, path,
                new SourceSpan(path, 1, 1, 1, 1), List.of(), true, condition.expression().toString());
    }

    private static String key(ConstraintDescriptor descriptor) {
        return descriptor.kind() == ConstraintKind.INV
                ? "I|" + descriptor.context() + "|" + descriptor.name()
                : "P|" + descriptor.context() + "|" + descriptor.operation() + "|" + descriptor.kind() + "|" + descriptor.name();
    }
    private static String invariantKey(MClassInvariant invariant) {
        return "I|" + invariant.cls().name() + "|" + invariant.name();
    }
    private static String conditionKey(MPrePostCondition condition) {
        return "P|" + condition.cls().name() + "|" + condition.operation().name() + "|"
                + (condition.isPre() ? ConstraintKind.PRE : ConstraintKind.POST) + "|" + condition.name();
    }

    private static String stripTrailingProfileComments(String expression) {
        return expression.replaceFirst("(?ms)\\s*(?:^\\s*--[^\\r\\n]*(?:\\R|\\z))+\\s*\\z", "").strip();
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String authoredId(ConstraintOrigin origin, Path path, String context, String operation,
                                     ConstraintKind kind, String name) {
        return origin + ":" + portable(path) + ":" + context + ":"
                + (operation == null ? "" : operation + ":") + kind + ":" + name;
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public record RegisteredProfile(ConstraintOrigin origin, OclProfileLoader.LoadedProfile profile) { }
}

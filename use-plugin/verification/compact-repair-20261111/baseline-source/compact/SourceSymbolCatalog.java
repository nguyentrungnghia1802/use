package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.semantic.CartagoSemanticContract;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
import org.jacamo.bridge.contract.semantic.SourceEvidence;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * Catalogue indexing source symbols from parsed JaCaMo contracts.
 * Resolves SourceReferences with disambiguation, owner validation, and boundary verification.
 */
public final class SourceSymbolCatalog {

    private final Map<String, SourceSymbol> symbolsByQualified = new LinkedHashMap<>();
    private final Map<String, List<SourceSymbol>> symbolsBySimple = new HashMap<>();
    private final Map<String, Set<String>> artifactMembers = new HashMap<>(); // className -> memberNames
    private final Map<String, Set<String>> orgMembers = new HashMap<>(); // org -> Set of "kind:name"
    private final Map<String, List<PlanInfo>> plansByPath = new HashMap<>(); // aslPath -> List<PlanInfo>

    public record PlanInfo(String trigger, String label, int stepCount) {}

    public SourceSymbolCatalog() {}

    public static SourceSymbolCatalog fromSnapshot(JacamoSemanticSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SourceSymbolCatalog catalog = new SourceSymbolCatalog();

        Map<String, String> artifactSemanticIdToClass = new LinkedHashMap<>();
        Set<String> indexedArtifactClasses = new LinkedHashSet<>();

        // Index Agent Declarations
        if (snapshot.agentDeclarations() != null) {
            for (var agent : snapshot.agentDeclarations()) {
                catalog.register(SourceSymbol.agent(agent.name()));
            }
        }

        // Index JCM artifact declarations
        if (snapshot.artifactDeclarations() != null) {
            for (var artDecl : snapshot.artifactDeclarations()) {
                if (artDecl.javaClass() != null && !artDecl.javaClass().isBlank()) {
                    String digest = artDecl.metadata() != null ? artDecl.metadata().semanticId() : null;
                    catalog.register(SourceSymbol.artifact(artDecl.javaClass(), digest));
                    catalog.registerIntrinsicArtifactProperties(artDecl.javaClass());
                    if (artDecl.metadata() != null && artDecl.metadata().semanticId() != null) {
                        artifactSemanticIdToClass.put(artDecl.metadata().semanticId(), artDecl.javaClass());
                    }
                    if (artDecl.metadata() != null && artDecl.metadata().evidence() != null) {
                        for (SourceEvidence ev : artDecl.metadata().evidence()) {
                            catalog.indexArtifactClassEvidence(artDecl.javaClass(), ev, indexedArtifactClasses);
                        }
                    }
                }
            }
        }

        // Index CArtAgO Environments
        if (snapshot.cartagoEnvironments() != null) {
            for (var env : snapshot.cartagoEnvironments()) {
                Map<String, String> typeIdToClass = new LinkedHashMap<>();
                if (env.artifactTypes() != null) {
                    for (var at : env.artifactTypes()) {
                        String semId = at.metadata() != null ? at.metadata().semanticId() : null;
                        catalog.register(SourceSymbol.artifact(at.javaClassName(), semId));
                        catalog.registerIntrinsicArtifactProperties(at.javaClassName());
                        if (semId != null) {
                            typeIdToClass.put(semId, at.javaClassName());
                            artifactSemanticIdToClass.put(semId, at.javaClassName());
                        }
                    }
                }
                if (env.artifacts() != null) {
                    for (var art : env.artifacts()) {
                        String cls = typeIdToClass.get(art.artifactTypeSemanticId());
                        if (cls != null && art.metadata() != null) {
                            artifactSemanticIdToClass.put(art.metadata().semanticId(), cls);
                        }
                        if (art.name() != null && !art.name().isBlank()) {
                            catalog.register(SourceSymbol.artifact(art.name(), null));
                        }
                    }
                }
                if (env.operations() != null) {
                    for (var op : env.operations()) {
                        String ownerClass = artifactSemanticIdToClass.getOrDefault(op.artifactSemanticId(), op.artifactSemanticId());
                        catalog.register(SourceSymbol.operation(ownerClass, op.name(), op.name() + "/" + op.arity()));
                    }
                }
                if (env.backingOperations() != null) {
                    for (var bop : env.backingOperations()) {
                        if (bop.declaringClass() != null && !bop.declaringClass().isBlank()) {
                            catalog.register(SourceSymbol.operation(bop.declaringClass(), bop.methodName(),
                                    bop.methodName() + "(" + String.join(",", bop.parameterTypes()) + ")"));
                        }
                    }
                }
                if (env.liveProperties() != null) {
                    for (var prop : env.liveProperties()) {
                        String ownerClass = artifactSemanticIdToClass.getOrDefault(prop.artifactSemanticId(), prop.artifactSemanticId());
                        catalog.register(SourceSymbol.property(ownerClass, prop.name()));
                    }
                }
                if (env.propertySnapshots() != null) {
                    for (var prop : env.propertySnapshots()) {
                        String ownerClass = artifactSemanticIdToClass.getOrDefault(prop.artifactSemanticId(), prop.artifactSemanticId());
                        catalog.register(SourceSymbol.property(ownerClass, prop.name()));
                    }
                }
            }
        }

        // Index Moise Organizations
        if (snapshot.moiseOrganizations() != null) {
            for (var org : snapshot.moiseOrganizations()) {
                String orgName = org.name();
                if (org.structuralSpecification() != null) {
                    if (org.structuralSpecification().roles() != null) {
                        for (var role : org.structuralSpecification().roles()) {
                            catalog.register(SourceSymbol.moiseRole(orgName, role.roleId()));
                        }
                    }
                    if (org.structuralSpecification().groups() != null) {
                        for (var group : org.structuralSpecification().groups()) {
                            catalog.register(SourceSymbol.moiseGroup(orgName, group.groupId()));
                        }
                    }
                }
                if (org.functionalSpecification() != null && org.functionalSpecification().schemes() != null) {
                    for (var scheme : org.functionalSpecification().schemes()) {
                        catalog.register(SourceSymbol.moiseScheme(orgName, scheme.schemeId()));
                        if (scheme.goals() != null) {
                            for (var goal : scheme.goals()) {
                                catalog.register(SourceSymbol.moiseGoal(orgName, goal.goalId()));
                            }
                        }
                    }
                }
            }
        }

        // Index Jason Programs
        if (snapshot.jasonPrograms() != null) {
            for (var prog : snapshot.jasonPrograms()) {
                String path = normalizePath(prog.sourceUri());
                if (prog.declarationId() != null && !prog.declarationId().isBlank()) {
                    catalog.register(SourceSymbol.agent(prog.declarationId()));
                }
                String base = basenameWithoutExt(path);
                if (!base.isBlank()) {
                    catalog.register(SourceSymbol.agent(base));
                }
                if (prog.goals() != null) {
                    for (var g : prog.goals()) {
                        catalog.register(SourceSymbol.aslGoal(path, g.literal()));
                        catalog.register(SourceSymbol.aslGoal(path, extractFunctor(g.literal())));
                    }
                }
                if (prog.planLibrary() != null && prog.planLibrary().plans() != null) {
                    for (var plan : prog.planLibrary().plans()) {
                        String triggerStr = plan.trigger() != null ? plan.trigger().literal() : "";
                        int bodySize = plan.body() != null ? plan.body().size() : 0;
                        catalog.registerPlan(path, triggerStr, plan.label(), bodySize);
                        if (triggerStr.startsWith("+!") || triggerStr.startsWith("-!") || triggerStr.startsWith("!")) {
                            catalog.register(SourceSymbol.aslGoal(path, triggerStr));
                            catalog.register(SourceSymbol.aslGoal(path, extractFunctor(triggerStr)));
                        }
                    }
                }
                if (prog.actions() != null) {
                    for (var action : prog.actions()) {
                        if (action.metadata() != null && action.metadata().evidence() != null) {
                            for (SourceEvidence ev : action.metadata().evidence()) {
                                if (ev.sourceJavaFqcn() != null && !ev.sourceJavaFqcn().isBlank()) {
                                    catalog.indexArtifactClassEvidence(ev.sourceJavaFqcn(), ev, indexedArtifactClasses);
                                }
                            }
                        }
                    }
                }
            }
        }

        return catalog;
    }

    private void registerIntrinsicArtifactProperties(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) return;
        register(SourceSymbol.property(fqcn, "id", "String"));
        register(SourceSymbol.property(fqcn, "name", "String"));
        register(SourceSymbol.property(fqcn, "workspace", "String"));
    }

    private void indexArtifactClassEvidence(String fqcn, SourceEvidence ev, Set<String> indexedArtifactClasses) {
        if (fqcn == null || fqcn.isBlank()) return;
        register(SourceSymbol.artifact(fqcn, ev.sourceDigest()));
        registerIntrinsicArtifactProperties(fqcn);
        if (ev.sourceUri() != null && (ev.sourceUri().startsWith("file:") || ev.sourceUri().startsWith("jar:file:"))) {
            if (indexedArtifactClasses.add(fqcn + "@" + ev.sourceUri())) {
                inspectClassBytecode(fqcn, ev.sourceUri());
            }
        }
    }

    private void inspectClassBytecode(String fqcn, String uriStr) {
        try {
            URL url = URI.create(uriStr).toURL();
            byte[] bytes;
            try (InputStream is = url.openStream()) {
                bytes = is.readAllBytes();
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0xCAFEBABE) return;
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int cpCount = in.readUnsignedShort();
            String[] utf8Pool = new String[cpCount];
            int[] stringIndices = new int[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8Pool[i] = in.readUTF();
                    case 3, 4 -> in.readInt();
                    case 5, 6 -> { in.readLong(); i++; }
                    case 7, 8, 16, 19, 20 -> {
                        int idx = in.readUnsignedShort();
                        if (tag == 8) stringIndices[i] = idx;
                    }
                    case 9, 10, 11, 12, 17, 18 -> { in.readUnsignedShort(); in.readUnsignedShort(); }
                    case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                    default -> { return; }
                }
            }
            in.readUnsignedShort(); // access_flags
            in.readUnsignedShort(); // this_class
            in.readUnsignedShort(); // super_class
            int ifaces = in.readUnsignedShort();
            for (int i = 0; i < ifaces; i++) in.readUnsignedShort();

            // Fields
            int fieldsCount = in.readUnsignedShort();
            for (int i = 0; i < fieldsCount; i++) {
                in.readUnsignedShort(); // access
                int nameIdx = in.readUnsignedShort();
                in.readUnsignedShort(); // desc
                int attrsCount = in.readUnsignedShort();
                for (int a = 0; a < attrsCount; a++) {
                    in.readUnsignedShort();
                    int len = in.readInt();
                    in.skipBytes(len);
                }
                if (nameIdx > 0 && nameIdx < cpCount && utf8Pool[nameIdx] != null) {
                    register(SourceSymbol.field(fqcn, utf8Pool[nameIdx]));
                }
            }

            // Methods
            int methodsCount = in.readUnsignedShort();
            for (int i = 0; i < methodsCount; i++) {
                in.readUnsignedShort(); // access
                int nameIdx = in.readUnsignedShort();
                in.readUnsignedShort(); // desc
                int attrsCount = in.readUnsignedShort();
                for (int a = 0; a < attrsCount; a++) {
                    in.readUnsignedShort();
                    int len = in.readInt();
                    in.skipBytes(len);
                }
                if (nameIdx > 0 && nameIdx < cpCount && utf8Pool[nameIdx] != null) {
                    String mName = utf8Pool[nameIdx];
                    if (!mName.startsWith("<")) {
                        register(SourceSymbol.operation(fqcn, mName, mName));
                    }
                }
            }

            // String constants in constant pool (used in defineObsProperty / getObsProperty)
            for (int i = 1; i < cpCount; i++) {
                int sIdx = stringIndices[i];
                if (sIdx > 0 && sIdx < cpCount && utf8Pool[sIdx] != null) {
                    String literal = utf8Pool[sIdx];
                    if (literal.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        register(SourceSymbol.property(fqcn, literal));
                    }
                }
            }
        } catch (Exception ignored) {
            // Non-fatal if URI is synthetic in unit tests
        }
    }

    public void register(SourceSymbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        String qKey = symbol.kind() + ":" + symbol.qualifiedName();
        symbolsByQualified.put(qKey, symbol);
        symbolsBySimple.computeIfAbsent(symbol.simpleName(), k -> new ArrayList<>()).add(symbol);

        if (symbol.owner() != null) {
            if (symbol.kind() == SourceReference.Kind.ARTIFACT_PROPERTY
                    || symbol.kind() == SourceReference.Kind.ARTIFACT_FIELD
                    || symbol.kind() == SourceReference.Kind.ARTIFACT_OPERATION) {
                artifactMembers.computeIfAbsent(symbol.owner(), k -> new HashSet<>()).add(symbol.kind() + ":" + symbol.simpleName());
            } else if (symbol.kind() == SourceReference.Kind.MOISE_GROUP
                    || symbol.kind() == SourceReference.Kind.MOISE_ROLE
                    || symbol.kind() == SourceReference.Kind.MOISE_SCHEME
                    || symbol.kind() == SourceReference.Kind.MOISE_GOAL) {
                orgMembers.computeIfAbsent(symbol.owner(), k -> new HashSet<>()).add(symbol.kind() + ":" + symbol.simpleName());
            }
        }
    }

    public void registerPlan(String aslPath, String trigger, String label, int stepCount) {
        String normPath = normalizePath(aslPath);
        plansByPath.computeIfAbsent(normPath, k -> new ArrayList<>()).add(new PlanInfo(trigger, label, stepCount));
    }

    public List<SourceSymbol> allSymbols() {
        return List.copyOf(symbolsByQualified.values());
    }

    public SourceSymbol resolve(SourceReference ref) {
        Objects.requireNonNull(ref, "ref");
        switch (ref) {
            case SourceReference.ArtifactRef a -> {
                SourceSymbol s = findArtifact(a.className());
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_SYMBOL_NOT_FOUND: artifact '" + a.className() + "'");
                }
                return s;
            }
            case SourceReference.ArtifactPropertyRef p -> {
                SourceSymbol art = findArtifactOrThrow(p.className());
                SourceSymbol s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_PROPERTY + ":" + p.className() + "/" + p.propertyName());
                if (s == null && art != null) {
                    s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_PROPERTY + ":" + art.qualifiedName() + "/" + p.propertyName());
                }
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_PROPERTY_NOT_FOUND: '" + p.className() + "/" + p.propertyName() + "'");
                }
                return s;
            }
            case SourceReference.ArtifactFieldRef f -> {
                SourceSymbol art = findArtifactOrThrow(f.className());
                SourceSymbol s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_FIELD + ":" + f.className() + "/" + f.fieldName());
                if (s == null && art != null) {
                    s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_FIELD + ":" + art.qualifiedName() + "/" + f.fieldName());
                }
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_FIELD_NOT_FOUND: '" + f.className() + "/" + f.fieldName() + "'");
                }
                return s;
            }
            case SourceReference.ArtifactOpRef o -> {
                SourceSymbol art = findArtifactOrThrow(o.className());
                SourceSymbol s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_OPERATION + ":" + o.className() + "/" + o.operationName());
                if (s == null && art != null) {
                    s = symbolsByQualified.get(SourceReference.Kind.ARTIFACT_OPERATION + ":" + art.qualifiedName() + "/" + o.operationName());
                }
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_OPERATION_NOT_FOUND: '" + o.className() + "/" + o.operationName() + "'");
                }
                return s;
            }
            case SourceReference.MoiseGroupRef g -> {
                checkOrgExists(g.organization());
                String qKey = SourceReference.Kind.MOISE_GROUP + ":" + g.organization() + "/" + g.groupName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_GROUP_NOT_FOUND: '" + g.organization() + "/" + g.groupName() + "'");
                }
                return s;
            }
            case SourceReference.MoiseRoleRef r -> {
                checkOrgExists(r.organization());
                String qKey = SourceReference.Kind.MOISE_ROLE + ":" + r.organization() + "/" + r.roleName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_ROLE_NOT_FOUND: '" + r.organization() + "/" + r.roleName() + "'");
                }
                return s;
            }
            case SourceReference.MoiseSchemeRef sc -> {
                checkOrgExists(sc.organization());
                String qKey = SourceReference.Kind.MOISE_SCHEME + ":" + sc.organization() + "/" + sc.schemeName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_SCHEME_NOT_FOUND: '" + sc.organization() + "/" + sc.schemeName() + "'");
                }
                return s;
            }
            case SourceReference.MoiseGoalRef mg -> {
                checkOrgExists(mg.organization());
                String qKey = SourceReference.Kind.MOISE_GOAL + ":" + mg.organization() + "/" + mg.goalName();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    throw new IllegalArgumentException("SOURCE_MOISE_GOAL_NOT_FOUND: '" + mg.organization() + "/" + mg.goalName() + "'");
                }
                return s;
            }
            case SourceReference.AgentRef ag -> {
                String qKey = SourceReference.Kind.AGENT + ":" + ag.agentOrProgram();
                SourceSymbol s = symbolsByQualified.get(qKey);
                if (s == null) {
                    List<SourceSymbol> list = symbolsBySimple.get(ag.agentOrProgram());
                    if (list != null && !list.isEmpty()) {
                        List<SourceSymbol> agentSymbols = list.stream().filter(x -> x.kind() == SourceReference.Kind.AGENT).toList();
                        if (agentSymbols.size() == 1) return agentSymbols.get(0);
                        if (agentSymbols.size() > 1) {
                            throw new IllegalArgumentException("AMBIGUOUS_AGENT_SYMBOL: '" + ag.agentOrProgram() + "' matches multiple candidates");
                        }
                    }
                    throw new IllegalArgumentException("SOURCE_AGENT_NOT_FOUND: '" + ag.agentOrProgram() + "'");
                }
                return s;
            }
            case SourceReference.AslGoalRef g -> {
                String norm = normalizePath(g.aslPath());
                SourceSymbol s = symbolsByQualified.get(SourceReference.Kind.ASL_GOAL + ":" + norm + "/" + g.goalTrigger());
                if (s != null) return s;
                String functor = extractFunctor(g.goalTrigger());
                s = symbolsByQualified.get(SourceReference.Kind.ASL_GOAL + ":" + norm + "/" + functor);
                if (s != null) return s;

                List<PlanInfo> plans = findPlansByPath(norm);
                if (plans != null && !plans.isEmpty()) {
                    List<PlanInfo> matching = matchPlans(plans, g.goalTrigger());
                    if (!matching.isEmpty()) {
                        return SourceSymbol.aslGoal(norm, g.goalTrigger());
                    }
                }
                throw new IllegalArgumentException("SOURCE_ASL_GOAL_NOT_FOUND: '" + g.raw() + "'");
            }
            case SourceReference.AslPlanStepRef ps -> {
                String norm = normalizePath(ps.aslPath());
                List<PlanInfo> plans = findPlansByPath(norm);
                if (plans == null || plans.isEmpty()) {
                    throw new IllegalArgumentException("SOURCE_ASL_PLAN_NOT_FOUND: no plans registered for '" + ps.aslPath() + "'");
                }
                List<PlanInfo> matching = matchPlans(plans, ps.planTrigger());
                if (matching.isEmpty()) {
                    throw new IllegalArgumentException("SOURCE_ASL_PLAN_NOT_FOUND: '" + ps.planTrigger() + "' in " + ps.aslPath());
                }
                if (matching.size() > 1) {
                    throw new IllegalArgumentException("AMBIGUOUS_PLAN_TRIGGER: '" + ps.planTrigger() + "' matches multiple plans in " + ps.aslPath());
                }
                PlanInfo p = matching.get(0);
                if (ps.firstIndex() < 0 || ps.afterIndex() <= ps.firstIndex() || ps.afterIndex() > p.stepCount()) {
                    throw new IllegalArgumentException("PLAN_STEP_OUT_OF_BOUNDS: [" + ps.firstIndex() + ".." + ps.afterIndex() + "] exceeds plan step count " + p.stepCount());
                }
                return SourceSymbol.aslPlanStep(norm, ps.planTrigger(), ps.stepName(), ps.firstIndex(), ps.afterIndex());
            }
        }
    }

    private List<PlanInfo> findPlansByPath(String normPath) {
        List<PlanInfo> direct = plansByPath.get(normPath);
        if (direct != null) return direct;
        for (var entry : plansByPath.entrySet()) {
            if (entry.getKey().endsWith("/" + normPath) || normPath.endsWith("/" + entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static List<PlanInfo> matchPlans(List<PlanInfo> plans, String requestedTrigger) {
        String reqStripped = stripTriggerPrefix(requestedTrigger);
        List<PlanInfo> exact = plans.stream()
                .filter(p -> (p.label() != null && p.label().equals(requestedTrigger))
                        || stripTriggerPrefix(p.trigger()).equals(reqStripped))
                .toList();
        if (!exact.isEmpty()) return exact;

        String reqFunctor = extractFunctor(requestedTrigger);
        return plans.stream()
                .filter(p -> extractFunctor(p.trigger()).equals(reqFunctor)
                        || (p.label() != null && extractFunctor(p.label()).equals(reqFunctor)))
                .toList();
    }

    private static String stripTriggerPrefix(String trigger) {
        if (trigger == null) return "";
        String s = trigger.trim();
        while (!s.isEmpty() && ("+ - ! ?".indexOf(s.charAt(0)) >= 0)) {
            s = s.substring(1).trim();
        }
        return s;
    }

    private static String extractFunctor(String trigger) {
        String s = stripTriggerPrefix(trigger);
        int paren = s.indexOf('(');
        return paren >= 0 ? s.substring(0, paren).trim() : s;
    }

    private static String basenameWithoutExt(String path) {
        if (path == null || path.isBlank()) return "";
        String norm = normalizePath(path);
        int slash = norm.lastIndexOf('/');
        String name = slash >= 0 ? norm.substring(slash + 1) : norm;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private SourceSymbol findArtifact(String name) {
        String qKey = SourceReference.Kind.ARTIFACT + ":" + name;
        SourceSymbol s = symbolsByQualified.get(qKey);
        if (s != null) return s;

        List<SourceSymbol> matches = symbolsByQualified.values().stream()
                .filter(x -> x.kind() == SourceReference.Kind.ARTIFACT
                        && (x.qualifiedName().equals(name) || x.qualifiedName().endsWith("." + name)))
                .toList();
        if (matches.size() == 1) return matches.get(0);
        if (matches.size() > 1) {
            throw new IllegalArgumentException("AMBIGUOUS_ARTIFACT_NAME: '" + name + "' matches multiple artifact classes");
        }
        return null;
    }

    private SourceSymbol findArtifactOrThrow(String className) {
        SourceSymbol s = findArtifact(className);
        if (s == null) {
            throw new IllegalArgumentException("SOURCE_ARTIFACT_NOT_FOUND: '" + className + "'");
        }
        return s;
    }

    private void checkOrgExists(String orgName) {
        if (!orgMembers.containsKey(orgName)) {
            boolean found = symbolsByQualified.values().stream().anyMatch(x -> Objects.equals(x.owner(), orgName));
            if (!found) {
                throw new IllegalArgumentException("SOURCE_ORGANIZATION_NOT_FOUND: '" + orgName + "'");
            }
        }
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        String norm = path.replace('\\', '/').trim();
        if (norm.startsWith("project:/")) {
            norm = norm.substring("project:/".length());
        }
        return norm.replaceAll("^\\./", "");
    }
}

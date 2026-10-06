package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MElementAnnotation;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.value.*;

/** Shared semantic identities and explicit relation decisions. Names are presentation only. */
public final class DomainProjection {
    public static final String ANNOTATION = "DomainProjection";
    public enum RelationDisposition {
        PRESERVE_AS_ASSOCIATION, PRESERVE_AS_LINK, FLATTEN, CONVERT_TO_ATTRIBUTE,
        CONVERT_TO_OCL, DEFER_TO_GOAL_MODEL, IGNORE_NOT_NEEDED,
        PRESERVE_AS_CLASS, PRESERVE_AS_ASSOCIATION_CLASS, CONVERT_TO_OPERATION, UNSUPPORTED
    }
    private DomainProjection() { }

    public static String kind(MClass cls) { return cls.getAnnotationValue(ANNOTATION, "kind"); }
    public static String hash(String id) { return MoiseUseSymbols.hash(id); }
    public static String symbol(String label) { return MoiseUseSymbols.label(label); }
    public static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    public static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
    public static String programId(String uri, String digest) { return "agent-program:" + canonicalPath(uri) + ":" + digest; }
    public static String basename(String uri) {
        String path = canonicalPath(uri).replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (!name.endsWith(".asl")) throw new IllegalArgumentException("AGENT_PROGRAM_ASL_SOURCE_REQUIRED:" + uri);
        return name.substring(0, name.length() - 4);
    }
    public static String canonicalPath(String source) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("SOURCE_PATH_REQUIRED");
        try {
            URI uri = URI.create(source);
            if (uri.getScheme() != null && uri.getScheme().length() > 1 && !"file".equalsIgnoreCase(uri.getScheme()))
                return uri.normalize().toString();
            if ("file".equalsIgnoreCase(uri.getScheme()))
                return (uri.isOpaque() ? Path.of(uri.getSchemeSpecificPart()) : Path.of(uri)).toAbsolutePath().normalize().toString();
        } catch (IllegalArgumentException ignored) { /* Windows paths are not URI syntax. */ }
        return Path.of(source).toAbsolutePath().normalize().toString();
    }
    public static String artifactId(String fqcn) { return "artifact-class:" + fqcn; }
    public static String simpleName(String fqcn) { return fqcn.substring(fqcn.lastIndexOf('.') + 1); }

    public static void installBases(UseModelApi api, CodeGroundedTraceCollector trace, SemanticMetadata owner) throws UseApiException {
        for (String name : NativeProjectionPolicy.BASE_CLASSES) {
            var existing = api.getModel().getClass(name);
            if (existing != null) {
                if (!kind(existing).equals(NativeProjectionPolicy.BASE_KINDS.get(name)))
                    throw new IllegalArgumentException("PROJECTION_BASE_IDENTITY_COLLISION:" + name);
                continue;
            }
            var cls = api.createClass(name, false);
            annotate(cls, NativeProjectionPolicy.BASE_KINDS.get(name), "projection-base:" + name, "");
            cls.addAnnotation(new MElementAnnotation("ProjectionPolicy", Map.of("version", NativeProjectionPolicy.VERSION)));
            if (!Set.of("Belief", "AgentGoal").contains(name)) domainAttribute(api,name, "name", "String");
            for (String field : switch (name) {
                case "Agent", "Workspace", "Artifact" -> List.<String>of();
                case "Belief", "AgentGoal" -> List.of("literal");
                case "Scheme" -> List.of("arguments");
                case "OrganizationalGoal" -> List.of("id", "description", "goalType", "ttf", "arguments", "decompositionOperator");
                case "Mission" -> List.of("id");
                default -> List.<String>of();
            }) domainAttribute(api,name, field, "String");
            if (name.equals("OrganizationalGoal")) {
                var runtimeState=api.createAttribute(name,"runtimeState","String");
                runtimeState.addAnnotation(new MElementAnnotation("ProjectionAttribute",Map.of("category","VERIFICATION_STATE","provenance","official-scheme-observation")));
                domainAttribute(api,name, "orderInParent", "Integer");
                domainAttribute(api,name, "minAgentsToSatisfy", "Integer");
            }
            if (name.equals("Mission")) {
                domainAttribute(api,name, "min", "Integer"); domainAttribute(api,name, "max", "Integer");
            }
            trace(trace, "J01", owner, "MClass", "class:" + name, RelationDisposition.PRESERVE_AS_CLASS,
                    "POLICY=" + NativeProjectionPolicy.VERSION, "SEMANTIC_KIND=" + kind(cls));
        }
        baseAssociation(api, "hasBelief", "Agent", "beliefOwner", "1", "Belief", "beliefs", "*");
        baseAssociation(api, "hasGoal", "Agent", "goalOwner", "1", "AgentGoal", "goals", "*");
        baseAssociation(api, "memberOf", "Agent", "members", "*", "Workspace", "joinedWorkspaces", "*");
        baseAssociation(api, "locatedIn", "Workspace", "workspace", "1", "Artifact", "artifacts", "*");
        baseAssociation(api, "focuses", "Agent", "observers", "*", "Artifact", "focusedArtifacts", "*");
        baseAssociation(api, "containsGroup", "Organization", "organization", "1", "Group", "groups", "*");
        baseAssociation(api, "containsScheme", "Organization", "schemeOrganization", "0..1", "Scheme", "schemes", "*");
        baseAssociation(api, "responsibleFor", "Group", "responsibleGroups", "*", "Scheme", "responsibleSchemes", "*");
        baseAssociation(api, "schemeGoals", "Scheme", "goalScheme", "0..1", "OrganizationalGoal", "schemeGoals", "*");
        baseAssociation(api, "schemeMissions", "Scheme", "missionScheme", "0..1", "Mission", "missions", "*");
        baseAssociation(api, "missionGoals", "Mission", "referencingMissions", "*", "OrganizationalGoal", "missionGoals", "*");
        baseAssociation(api, "committedTo", "Agent", "committedAgents", "*", "Mission", "commitments", "*");
        baseAssociation(api, "goalCommitment", "Agent", "goalCommittedAgents", "*", "OrganizationalGoal", "committedOrganizationalGoals", "*");
        baseAssociation(api, "goalAchievement", "Agent", "goalAchievedAgents", "*", "OrganizationalGoal", "achievedOrganizationalGoals", "*");
        baseAssociation(api, "subGoals", "OrganizationalGoal", "parentGoal", "0..1", "OrganizationalGoal", "subGoals", "*");
        for(var relation:api.getModel().associations()) if(relation.getAnnotation("DomainRelation")!=null)
            trace(trace,"J01",owner,"MAssociation","association:"+relation.name(),RelationDisposition.PRESERVE_AS_ASSOCIATION,"POLICY="+NativeProjectionPolicy.VERSION);
    }
    /** Exposure follows the producer's semantic category; application property names are not a blacklist. */
    public static org.tzi.use.uml.mm.MAttribute domainAttribute(UseModelApi api,String owner,String name,String type) throws UseApiException {
        var attribute=api.createAttribute(owner,name,type);
        attribute.addAnnotation(new MElementAnnotation("ProjectionAttribute",Map.of("category","DOMAIN","provenance","semantic-contract")));
        return attribute;
    }
    private static void baseAssociation(UseModelApi api, String relation, String first, String firstRole, String firstBounds,
            String second, String secondRole, String secondBounds) throws UseApiException {
        String name = relation(relation, first, second);
        if (api.getModel().getAssociation(name) == null) association(api, name, first, firstRole, firstBounds, second, secondRole, secondBounds);
    }
    public static String occurrenceId(String kind, String owner, String source) {
        return kind + ":" + NativeUseModelBuilder.canonicalJson(List.of(owner, source));
    }
    /** Exact official Java signature only; unavailable/erased types never become OclAny/String guesses. */
    public static org.tzi.use.uml.mm.MOperation operation(UseModelApi api,String owner,
            org.jacamo.bridge.contract.semantic.CartagoSemanticContract.OperationDescriptorSemantic descriptor,
            org.jacamo.bridge.contract.semantic.CartagoSemanticContract.BackingJavaOperationSemantic backing) throws UseApiException {
        if(backing==null || descriptor.internal() || descriptor.dynamic() || backing.varArgs()
                || descriptor.metadata().capabilityStatus()!=org.jacamo.bridge.contract.CapabilityStatus.COMPLETE
                || descriptor.metadata().evidenceAuthority()!=org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_CARTAGO_API
                || backing.metadata().evidenceAuthority()!=org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_CARTAGO_API
                || descriptor.metadata().fidelity()!=org.jacamo.bridge.contract.semantic.Fidelity.EXACT
                || backing.metadata().fidelity()!=org.jacamo.bridge.contract.semantic.Fidelity.EXACT
                || backing.metadata().capabilityStatus()!=org.jacamo.bridge.contract.CapabilityStatus.COMPLETE
                || !backing.operationDescriptorId().equals(descriptor.metadata().semanticId())
                || !backing.methodName().equals(descriptor.name()) || backing.parameterTypes().size()!=descriptor.arity())
            throw new IllegalArgumentException("CARTAGO_OPERATION_SIGNATURE_UNRESOLVED:"+descriptor.metadata().semanticId());
        try {
            Class<?> type=Class.forName(backing.declaringClass(),false,Thread.currentThread().getContextClassLoader());
            String artifactType=decode(api.getModel().getClass(owner).getAnnotationValue(ANNOTATION,"javaClass64"));
            if(!type.isAssignableFrom(Class.forName(artifactType,false,Thread.currentThread().getContextClassLoader())))
                throw new IllegalArgumentException("CARTAGO_OPERATION_OWNER_MISMATCH:"+descriptor.metadata().semanticId());
            var matches=java.util.Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals(backing.methodName())
                && java.util.Arrays.stream(m.getParameterTypes()).map(Class::getName).toList().equals(backing.parameterTypes())
                && m.getReturnType().getName().equals(backing.returnType()) && m.isAnnotationPresent(cartago.OPERATION.class)).toList();
            if(matches.size()!=1) throw new IllegalArgumentException("CARTAGO_OPERATION_REFLECTION_MISMATCH:"+descriptor.metadata().semanticId());
        } catch(ClassNotFoundException|LinkageError unavailable) { /* The official adapter has already copied the actual Method signature. */ }
        String[][] parameters=new String[backing.parameterTypes().size()][2];
        for(int i=0;i<parameters.length;i++) { parameters[i][0]="p"+i; parameters[i][1]=operationType(backing.parameterTypes().get(i)); }
        String result=backing.returnType().equals("void") ? null : operationType(backing.returnType());
        String signature=NativeUseModelBuilder.canonicalJson(List.of(backing.declaringClass(),backing.methodName(),backing.parameterTypes(),backing.returnType()));
        String name=symbol(descriptor.name())+"_"+hash(signature);
        var cls=api.getModel().getClass(owner); var operation=cls.operation(name,false);
        if(operation==null) operation=api.createOperation(owner,name,parameters,result);
        if(!operation.getAnnotationValue("CartagoOperation","signature64").isEmpty()
                && !operation.getAnnotationValue("CartagoOperation","signature64").equals(encode(signature)))
            throw new IllegalArgumentException("CARTAGO_OPERATION_IDENTITY_COLLISION:"+signature);
        operation.addAnnotation(new MElementAnnotation("CartagoOperation",Map.of("signature64",encode(signature),"javaName64",encode(descriptor.name()),"policy",NativeProjectionPolicy.VERSION)));
        operation.addAnnotation(new MElementAnnotation("OperationSource_"+hash(descriptor.metadata().semanticId()),Map.of(
            "descriptorId64",encode(descriptor.metadata().semanticId()),"backingId64",encode(backing.metadata().semanticId()))));
        return operation;
    }
    public static void propertyTrace(org.tzi.use.uml.mm.MAttribute attribute,String sourceId) {
        attribute.addAnnotation(new MElementAnnotation("ProjectionAttribute",Map.of("category","VERIFICATION_STATE","provenance","application-observable")));
        attribute.addAnnotation(new MElementAnnotation("PropertySource_"+hash(sourceId),Map.of("sourceId64",encode(sourceId))));
    }
    private static String operationType(String javaType) {
        return switch(javaType) {
            case "boolean","java.lang.Boolean" -> "Boolean";
            case "byte","short","int","java.lang.Byte","java.lang.Short","java.lang.Integer" -> "Integer";
            case "float","double","java.lang.Float","java.lang.Double" -> "Real";
            case "java.lang.String","char","java.lang.Character" -> "String";
            default -> throw new IllegalArgumentException("CARTAGO_OPERATION_TYPE_UNSUPPORTED:"+javaType);
        };
    }

    public static void annotate(MClass cls, String kind, String id, String javaClass) {
        cls.addAnnotation(new MElementAnnotation(ANNOTATION, new java.util.TreeMap<>(Map.of(
                "kind", kind, "sourceId64", encode(id), "javaClass64", encode(javaClass)))));
    }
    public static String classFor(MModel model, String kind, String sourceId) {
        return model.classes().stream().filter(c -> kind(c).equals(kind)
                && c.getAnnotationValue(ANNOTATION, "sourceId64").equals(encode(sourceId)))
                .map(MClass::name).findFirst().orElseThrow(() -> new IllegalArgumentException("DOMAIN_TYPE_UNRESOLVED:" + sourceId));
    }
    public static String artifactClass(MModel model, String fqcn) {
        return model.classes().stream().filter(c -> kind(c).equals("artifact")
                && c.getAnnotationValue(ANNOTATION, "javaClass64").equals(encode(fqcn)))
                .map(MClass::name).findFirst().orElseThrow(() -> new IllegalArgumentException("ARTIFACT_TYPE_UNRESOLVED:" + fqcn));
    }
    /** Framework bookkeeping artifacts are transport evidence, not public domain state. */
    public static boolean domainArtifact(String fqcn) {
        return domainArtifact(fqcn, artifactOrigin(fqcn));
    }
    public static boolean domainArtifact(String fqcn, String origin) {
        // ORA4MAS state already has authoritative Organization/Group/Scheme projection.
        return "APPLICATION".equals(origin) && !fqcn.startsWith("ora4mas.nopl.");
    }
    public static String artifactOrigin(String fqcn) {
        try {
            var type=Class.forName(fqcn,false,Thread.currentThread().getContextClassLoader());
            var provider=type.getProtectionDomain().getCodeSource();
            var platform=cartago.Artifact.class.getProtectionDomain().getCodeSource();
            if(provider==null || platform==null || provider.getLocation()==null || platform.getLocation()==null
                    || !cartago.Artifact.class.isAssignableFrom(type)) return "UNAVAILABLE";
            return provider.getLocation().equals(platform.getLocation()) ? "PLATFORM" : "APPLICATION";
        } catch(ClassNotFoundException|LinkageError unavailable) { return "UNAVAILABLE"; }
    }
    public static String ensureArtifactClass(UseModelApi api, String fqcn) throws UseApiException {
        return ensureArtifactClass(api, fqcn, artifactOrigin(fqcn));
    }
    public static String ensureArtifactClass(UseModelApi api, String fqcn, String origin) throws UseApiException {
        try { return artifactClass(api.getModel(), fqcn); }
        catch (IllegalArgumentException missing) { /* An authoritative runtime type can extend this same model. */ }
        if (!fqcn.matches("[A-Za-z_$][A-Za-z0-9_$.]*") || !domainArtifact(fqcn,origin))
            throw new IllegalArgumentException("ARTIFACT_DOMAIN_TYPE_REQUIRED:" + fqcn);
        String id = artifactId(fqcn), name = symbol(simpleName(fqcn));
        if (api.getModel().getClass(name) != null) name += "_" + hash(id);
        var cls = api.createClass(name, false); annotate(cls, "artifact", id, fqcn);
        cls.addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation("ArtifactProvider",Map.of("origin",origin)));
        if (api.getModel().getClass("Artifact") == null) throw new IllegalArgumentException("ARTIFACT_BASE_REQUIRED");
        api.createGeneralization(name, "Artifact");
        return name;
    }
    public static String propertyName(String name) {
        String symbol = symbol(name);
        if (!symbol.equals(name) || name.equals("name"))
            symbol = "obs_" + symbol + "_" + hash(name);
        return symbol;
    }
    public static MoiseSemanticContract.OrganizationSemantic organization(JacamoSemanticSnapshot source,
            org.jacamo.bridge.contract.semantic.JcmSemanticContract.OrganizationDeploymentSemantic deployment) {
        String requested = deployment.source();
        String relative = requested.replace('\\', '/');
        if (relative.startsWith("file:")) relative = URI.create(relative).getSchemeSpecificPart();
        List<String> paths = List.of(canonicalPath(requested), URI.create("project:/" + relative).normalize().toString(),
                URI.create("project:/src/org/" + relative).normalize().toString());
        // The official adapter's project:/ provenance is canonical and does not depend on the consumer's cwd.
        var matches = source.moiseOrganizations().stream().filter(o -> paths.contains(canonicalPath(o.sourceUri()))
                || o.metadata().evidence().stream().anyMatch(e -> paths.contains(e.sourceUri()))).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("JCM_ORGANISATION_SOURCE_UNRESOLVED:" + requested);
        return matches.get(0);
    }
    public static void trace(CodeGroundedTraceCollector trace, String rule, SemanticMetadata source,
            String targetKind, String target, RelationDisposition disposition, String... diagnostics) {
        var details = new java.util.ArrayList<String>(); details.add("DISPOSITION=" + disposition);
        details.addAll(List.of(diagnostics));
        trace.add(new CodeGroundedRuleCatalog().require(rule), TracePhase.MODEL_DECLARATION,
                source, targetKind, target, details);
    }
    public static String relation(String kind, String firstClass, String secondClass) {
        switch (kind) {
            case "memberOf" -> { kind = "joins"; firstClass = "Agent"; secondClass = "Workspace"; }
            case "locatedIn" -> { kind = "contains"; firstClass = "Workspace"; secondClass = "Artifact"; }
            case "focuses" -> { firstClass = "Agent"; secondClass = "Artifact"; }
            case "containsGroup" -> { firstClass = "Organization"; secondClass = "Group"; }
            case "containsScheme" -> { firstClass = "Organization"; secondClass = "Scheme"; }
            default -> { }
        }
        return kind + "_" + hash(firstClass + "|" + secondClass);
    }
    public static void association(UseModelApi api, String name, String first, String firstRole,
            String firstMultiplicity, String second, String secondRole, String secondMultiplicity) throws UseApiException {
        if (api.getModel().getClass(first) == null || api.getModel().getClass(second) == null)
            throw new IllegalArgumentException("DOMAIN_ASSOCIATION_ENDPOINT_UNRESOLVED:" + name);
        api.createAssociation(name, new String[] {first, second}, new String[] {firstRole, secondRole},
                new String[] {firstMultiplicity, secondMultiplicity}, new int[] {0, 0},
                new boolean[] {false, false}, new String[0][][]);
        api.getModel().getAssociation(name).addAnnotation(new MElementAnnotation("DomainRelation",Map.of(
            "firstKind",kind(api.getModel().getClass(first)),"secondKind",kind(api.getModel().getClass(second)),
            "disposition","PRESERVE_AS_ASSOCIATION")));
    }
    public static String propertyType(List<String> types) {
        if (types.size() != 1) throw new IllegalArgumentException("OBS_PROPERTY_NON_SCALAR:" + types);
        return switch (types.get(0)) {
            case "java.lang.Boolean", "boolean" -> "Boolean";
            // CArtAgO may update an Integer-valued property with a Double later. A stable
            // numeric classifier must accept both observations without changing compiled OCL.
            case "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "byte", "short", "int" -> "Real";
            case "java.lang.Float", "java.lang.Double", "float", "double" -> "Real";
            case "java.lang.String", "java.lang.Character", "char", "jason.asSyntax.Atom" -> "String";
            default -> throw new IllegalArgumentException("OBS_PROPERTY_TYPE_UNSUPPORTED:" + types.get(0));
        };
    }
    public static Value propertyValue(String type, String text) {
        return switch (type) {
            case "Boolean" -> {
                if (!List.of("true", "false").contains(text)) throw new IllegalArgumentException("OBS_PROPERTY_BOOLEAN_INVALID:" + text);
                yield BooleanValue.get(Boolean.parseBoolean(text));
            }
            case "Integer" -> IntegerValue.valueOf(Integer.parseInt(text));
            case "Real" -> {
                double number = Double.parseDouble(text);
                if (!Double.isFinite(number)) throw new IllegalArgumentException("OBS_PROPERTY_REAL_NONFINITE:" + text);
                yield new RealValue(number);
            }
            case "String" -> new StringValue(text);
            default -> throw new IllegalArgumentException("OBS_PROPERTY_TYPE_UNSUPPORTED:" + type);
        };
    }
}

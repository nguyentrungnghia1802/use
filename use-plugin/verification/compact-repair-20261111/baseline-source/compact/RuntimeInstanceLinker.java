package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.tzi.use.uml.ocl.value.*;

import java.util.*;

/**
 * Links physical cut observations to target model instances and synthesizes state mutations.
 * Supports dynamic creation, property/field value projection, role/actor linking, and reversible rollback.
 */
public final class RuntimeInstanceLinker {

    public record ObjectMutation(
            String className,
            String objectName,
            Map<String, Value> attributes,
            boolean isNew) {}

    public record LinkMutation(
            String associationName,
            String sourceObject,
            String targetObject) {}

    public record ProjectionTransaction(
            String cutId,
            List<ObjectMutation> objectMutations,
            List<LinkMutation> linkMutations,
            List<String> disposedObjects) {}

    public static ProjectionTransaction link(CompiledObservationPlan plan, PhysicalCutFact cut) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cut, "cut");

        List<ObjectMutation> objectMutations = new ArrayList<>();
        List<LinkMutation> linkMutations = new ArrayList<>();
        List<String> disposedObjects = new ArrayList<>(cut.disposedObjects());

        // 1. Link Artifacts and Attributes
        // Group CSL bindings by target Entity / Group / Role
        Map<String, List<CslBinding>> bindingsByEntity = new LinkedHashMap<>();
        for (CslBinding binding : plan.cslBindings()) {
            String entityName = binding.targetRef().qualifier();
            bindingsByEntity.computeIfAbsent(entityName, k -> new ArrayList<>()).add(binding);
        }

        Map<String, String> moiseGroupObjects = new LinkedHashMap<>();
        for (var entry : bindingsByEntity.entrySet()) {
            String entityName = entry.getKey();
            List<CslBinding> bindings = entry.getValue();

            // Find structural mapping (entity, group, or role)
            CslBinding entityBinding = bindings.stream()
                    .filter(b -> b.kind() == CslBinding.BindingKind.ENTITY_MAPPING
                            || b.kind() == CslBinding.BindingKind.GROUP_MAPPING
                            || b.kind() == CslBinding.BindingKind.ROLE_MAPPING)
                    .findFirst()
                    .orElse(null);

            if (entityBinding != null && entityBinding.sourceRef() instanceof SourceReference.ArtifactRef ar) {
                List<PhysicalCutFact.ArtifactInstance> matchingArtifacts = cut.findArtifactsByClass(ar.className());
                for (PhysicalCutFact.ArtifactInstance art : matchingArtifacts) {
                    String runPart = (art.runId() == null || art.runId().isBlank())
                            ? ""
                            : sanitizeId(art.runId()) + "_";
                    String wsPart = (art.workspace() == null || art.workspace().isBlank())
                            ? ""
                            : sanitizeId(art.workspace()) + "_";
                    String incPart = (art.incarnation() == null || art.incarnation().isBlank())
                            ? ""
                            : "_" + sanitizeId(art.incarnation());
                    String objName = entityName.toLowerCase(Locale.ROOT) + "_" + runPart + wsPart + sanitizeId(art.name()) + incPart;
                    Map<String, Value> attrs = new LinkedHashMap<>();

                    // Read attributes mapped from properties and fields
                    for (CslBinding b : bindings) {
                        if (b.kind() == CslBinding.BindingKind.ATTRIBUTE_MAPPING) {
                            String attrName = b.targetRef().member();
                            Value val = UndefinedValue.instance;

                            if (b.sourceRef() instanceof SourceReference.ArtifactPropertyRef pr) {
                                Object raw = art.properties().get(pr.propertyName());
                                if (raw == null) {
                                    if ("name".equals(pr.propertyName()) || "id".equals(pr.propertyName())) {
                                        raw = art.name();
                                    } else if ("workspace".equals(pr.propertyName())) {
                                        raw = art.workspace();
                                    }
                                }
                                val = convertValue(raw, b.conversion());
                            } else if (b.sourceRef() instanceof SourceReference.ArtifactFieldRef fr) {
                                Object raw = art.fields().get(fr.fieldName());
                                val = convertValue(raw, b.conversion());
                            }
                            attrs.put(attrName, val);
                        }
                    }

                    objectMutations.add(new ObjectMutation(entityName, objName, attrs, true));
                }
            } else if (entityBinding != null && entityBinding.sourceRef() instanceof SourceReference.MoiseGroupRef gr) {
                for (PhysicalCutFact.MoiseInstance mi : cut.moiseInstances()) {
                    if (mi.orgName().equals(gr.organization()) && mi.groupName() != null && !mi.groupName().isBlank()) {
                        String objName = entityName.toLowerCase(Locale.ROOT) + "_" + sanitizeId(mi.orgName()) + "_" + sanitizeId(mi.groupName());
                        objectMutations.add(new ObjectMutation(entityName, objName, Map.of(), true));
                        moiseGroupObjects.put(mi.orgName() + ":" + mi.groupName(), objName);
                    }
                }
            } else if (entityBinding != null && entityBinding.sourceRef() instanceof SourceReference.MoiseRoleRef rr) {
                for (PhysicalCutFact.MoiseInstance mi : cut.moiseInstances()) {
                    if (mi.orgName().equals(rr.organization())) {
                        for (var rp : mi.rolePlayers().entrySet()) {
                            if (rp.getKey().equals(rr.roleName())) {
                                String grpPart = (mi.groupName() == null || mi.groupName().isBlank()) ? sanitizeId(mi.orgName()) : sanitizeId(mi.groupName());
                                String objName = entityName.toLowerCase(Locale.ROOT) + "_" + grpPart + "_" + sanitizeId(rp.getValue());
                                objectMutations.add(new ObjectMutation(entityName, objName, Map.of(), true));
                                String groupObj = moiseGroupObjects.get(mi.orgName() + ":" + mi.groupName());
                                if (groupObj != null && plan.cslModel().isPresent()) {
                                    var csl = plan.cslModel().get();
                                    csl.links().stream()
                                            .filter(l -> (l.source().equals(entityName) || l.target().equals(entityName)))
                                            .findFirst()
                                            .ifPresent(l -> linkMutations.add(new LinkMutation(l.name(), objName, groupObj)));
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Link Actors and Intentional Occurrences
        for (IStarBinding ib : plan.istarBindings()) {
            if (ib.kind() == IStarBinding.BindingKind.ACTOR_MAPPING) {
                String actorName = ib.targetRef().qualifier();
                if (ib.sourceRef() instanceof SourceReference.AgentRef agRef) {
                    String expected = agRef.agentOrProgram();
                    String expectedBase = expected.endsWith(".asl") ? expected.substring(0, expected.length() - 4) : expected;
                    for (PhysicalCutFact.AgentInstance ag : cut.agents()) {
                        String agSourceBase = ag.aslSource().endsWith(".asl")
                                ? ag.aslSource().substring(0, ag.aslSource().length() - 4)
                                : ag.aslSource();
                        boolean matches = ag.name().equals(expected)
                                || (!agSourceBase.isBlank() && agSourceBase.equals(expectedBase))
                                || ag.name().equals(expectedBase)
                                || ag.name().startsWith(expectedBase);
                        if (matches) {
                            String objName = "actor_" + sanitizeId(ag.name());
                            objectMutations.add(new ObjectMutation(actorName, objName, Map.of(), true));
                        }
                    }
                } else if (ib.sourceRef() instanceof SourceReference.MoiseRoleRef rr) {
                    Set<String> matchedAgents = new LinkedHashSet<>();
                    for (PhysicalCutFact.MoiseInstance mi : cut.moiseInstances()) {
                        if (mi.orgName().equals(rr.organization())) {
                            for (var rp : mi.rolePlayers().entrySet()) {
                                if (rp.getKey().equals(rr.roleName())) {
                                    matchedAgents.add(rp.getValue());
                                }
                            }
                        }
                    }
                    for (PhysicalCutFact.AgentInstance ag : cut.agents()) {
                        if (ag.roles().contains(rr.roleName())) {
                            matchedAgents.add(ag.name());
                        }
                    }
                    for (String agentName : matchedAgents) {
                        String objName = "actor_" + sanitizeId(actorName) + "_" + sanitizeId(agentName);
                        objectMutations.add(new ObjectMutation(actorName, objName, Map.of(), true));
                    }
                }
            }
        }

        return new ProjectionTransaction(cut.cutId(), objectMutations, linkMutations, disposedObjects);
    }

    private static String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) return "unnamed";
        return raw.trim().replaceAll("[^A-Za-z0-9_]", "_");
    }

    public static Value convertValue(Object raw, CslBinding.TypeConversion conversion) {
        if (raw == null) return UndefinedValue.instance;

        return switch (conversion) {
            case TO_STRING -> new StringValue(String.valueOf(raw));
            case TO_INTEGER -> {
                if (raw instanceof Integer i) {
                    yield IntegerValue.valueOf(i);
                }
                if (raw instanceof Short sh) {
                    yield IntegerValue.valueOf(sh.intValue());
                }
                if (raw instanceof Byte by) {
                    yield IntegerValue.valueOf(by.intValue());
                }
                try {
                    java.math.BigDecimal bd = (raw instanceof java.math.BigDecimal d)
                            ? d
                            : new java.math.BigDecimal(String.valueOf(raw).trim());
                    yield IntegerValue.valueOf(bd.intValueExact());
                } catch (NumberFormatException | ArithmeticException e) {
                    yield UndefinedValue.instance;
                }
            }
            case TO_REAL -> {
                if (raw instanceof Number n) {
                    double d = n.doubleValue();
                    yield Double.isFinite(d) ? new RealValue(d) : UndefinedValue.instance;
                }
                try {
                    double d = Double.parseDouble(String.valueOf(raw).trim());
                    yield Double.isFinite(d) ? new RealValue(d) : UndefinedValue.instance;
                } catch (NumberFormatException e) {
                    yield UndefinedValue.instance;
                }
            }
            case TO_BOOLEAN -> {
                if (raw instanceof Boolean b) {
                    yield BooleanValue.get(b);
                }
                if (raw instanceof String s) {
                    String trimmed = s.trim();
                    if ("true".equalsIgnoreCase(trimmed)) yield BooleanValue.TRUE;
                    if ("false".equalsIgnoreCase(trimmed)) yield BooleanValue.FALSE;
                }
                yield UndefinedValue.instance;
            }
            case DIRECT, NONE -> {
                if (raw instanceof String s) yield new StringValue(s);
                if (raw instanceof Integer i) yield IntegerValue.valueOf(i);
                if (raw instanceof Short sh) yield IntegerValue.valueOf(sh.intValue());
                if (raw instanceof Byte by) yield IntegerValue.valueOf(by.intValue());
                if (raw instanceof Long l) {
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        yield IntegerValue.valueOf(l.intValue());
                    }
                    yield UndefinedValue.instance;
                }
                if (raw instanceof Double d) yield Double.isFinite(d) ? new RealValue(d) : UndefinedValue.instance;
                if (raw instanceof Float f) yield Float.isFinite(f) ? new RealValue(f.doubleValue()) : UndefinedValue.instance;
                if (raw instanceof Boolean b) yield BooleanValue.get(b);
                yield new StringValue(String.valueOf(raw));
            }
        };
    }

    private RuntimeInstanceLinker() {}
}

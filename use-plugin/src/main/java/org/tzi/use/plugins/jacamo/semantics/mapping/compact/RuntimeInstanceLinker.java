package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.tzi.use.uml.ocl.value.*;

import java.util.*;

/**
 * Links physical cut observations to target model instances and synthesizes state mutations.
 * Component-level artifact/scalar projection only. Physical agents, role intervals,
 * scopes and disposal require the native runtime adapter; no Actor MClass is invented.
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
            List<ObjectMutation> objectMutations,List<LinkMutation> linkMutations,List<String> disposedObjects) {
        public ProjectionTransaction {objectMutations=List.copyOf(objectMutations);linkMutations=List.copyOf(linkMutations);disposedObjects=List.copyOf(disposedObjects);}
    }

    public static ProjectionTransaction link(CompiledObservationPlan plan,PhysicalCutFact cut) {
        Objects.requireNonNull(plan);Objects.requireNonNull(cut);
        if(!cut.disposedObjects().isEmpty())throw new IllegalArgumentException("COMPACT_DISPOSAL_REQUIRES_NATIVE_SOURCE_INVENTORY");
        if(plan.cslBindings().stream().anyMatch(b -> b.kind()==CslBinding.BindingKind.GROUP_MAPPING || b.kind()==CslBinding.BindingKind.ROLE_MAPPING))
            throw new IllegalArgumentException("COMPACT_TYPED_ROLE_CONTEXT_PROJECTION_REQUIRED");
        var objects=new ArrayList<ObjectMutation>();
        for(var binding:plan.cslBindings())if(binding.sourceRef() instanceof SourceReference.ArtifactRef) {
            String target=binding.targetRef().qualifier();String sourceClass=binding.sourceSymbol().qualifiedName();
            for(var artifact:cut.artifacts())if(sourceClass.equals(artifact.className())) {
                var values=new LinkedHashMap<String,Value>();
                for(var member:plan.cslBindings())if(member.isAttribute() && member.targetRef().qualifier().equals(target)) {
                    Object raw=switch(member.sourceRef()) {
                        case SourceReference.ArtifactPropertyRef property -> artifact.properties().get(property.propertyName());
                        case SourceReference.ArtifactFieldRef field -> artifact.fields().get(field.fieldName());
                        default -> null;
                    };
                    values.put(member.targetRef().member(),convertValue(raw,member.conversion()));
                }
                objects.add(new ObjectMutation(target,artifactObjectName(target,artifact),Map.copyOf(values),true));
            }
        }
        return new ProjectionTransaction(cut.cutId(),List.copyOf(objects),List.of(),List.of());
    }

    public static String artifactObjectName(String target, PhysicalCutFact.ArtifactInstance artifact) {
        if(artifact.runId().isBlank() || artifact.workspace()==null || artifact.workspace().isBlank() || artifact.incarnation().isBlank())
            throw new IllegalArgumentException("COMPACT_QUALIFIED_ARTIFACT_IDENTITY_REQUIRED");
        return "compact_" + org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(
                List.of(target,artifact.className(),artifact.runId(),artifact.workspace(),artifact.name(),artifact.incarnation()));
    }

    public static Value convertValue(Object raw, CslBinding.TypeConversion conversion) {
        if (raw == null) return UndefinedValue.instance;
        if((conversion==CslBinding.TypeConversion.TO_INTEGER||conversion==CslBinding.TypeConversion.TO_REAL)
                &&!(raw instanceof String)&&!Set.of(Byte.class,Short.class,Integer.class,Long.class,Float.class,Double.class,java.math.BigInteger.class,java.math.BigDecimal.class).contains(raw.getClass()))
            return UndefinedValue.instance;

        return switch (conversion) {
            case TO_STRING -> raw instanceof String text ? new StringValue(text) : UndefinedValue.instance;
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
                yield UndefinedValue.instance;
            }
        };
    }

    private RuntimeInstanceLinker() {}
}

package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceIndex;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.ocl.value.EnumValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/** Materializes the first vertical slice into one exact {@link MSystem}. */
public final class NativeUseStateBuilder {
    public Result build(JacamoSpecificationModel source, NativeUseModelBuilder.Result schema) {
        CodeGroundedTraceCollector trace = new CodeGroundedTraceCollector();
        schema.trace().records().forEach(trace::add);
        return build(source, schema, trace);
    }

    public Result build(JacamoSpecificationModel source, NativeUseModelBuilder.Result schema,
                        CodeGroundedTraceCollector trace) {
        MSystem system = new MSystem(schema.model());
        UseSystemApi api = UseSystemApi.create(system, false);
        CodeGroundedRuleCatalog catalog = new CodeGroundedRuleCatalog();
        Map<String,MObject> semanticObjects = new LinkedHashMap<>();
        Set<String> objectNames = new HashSet<>();
        try {
            for (var program : source.programs()) {
                MObject programObject = object(api, semanticObjects, objectNames, "AgentProgram",
                        program.metadata(), program.declarationId());
                text(api, programObject, "declarationId", program.declarationId());
                text(api, programObject, "sourceUri", program.sourceUri());
                text(api, programObject, "sourceDigest", program.sourceDigest());
                trace.add(catalog.require("A01"), TracePhase.INSTANCE_MATERIALIZATION, program.metadata(),
                        "MObject", programObject.name(), List.of());

                var library = java.util.Objects.requireNonNull(program.planLibrary(), "A02_PLAN_LIBRARY_REQUIRED");
                MObject libraryObject = object(api, semanticObjects, objectNames, "PlanLibrary", library.metadata(),
                        program.declarationId());
                trace.add(catalog.require("A02"), TracePhase.INSTANCE_MATERIALIZATION, library.metadata(),
                        "MObject", libraryObject.name(), List.of());
                link(api, schema, "A16AgentProgramPlanLibrary", programObject, libraryObject);
                trace.add(catalog.require("A16"), TracePhase.INSTANCE_MATERIALIZATION, program.metadata(),
                        "MLink", linkIdentity("A16", program.metadata().semanticId(), library.metadata().semanticId()), List.of());

                List<JasonSemanticContract.PlanSemantic> plans = library.plans();
                for (int planIndex = 0; planIndex < plans.size(); planIndex++) {
                    var plan = plans.get(planIndex);
                    if (plan.ordinal() != planIndex)
                        throw new IllegalArgumentException("A17_PLAN_ORDINAL_NONCONTIGUOUS: " + plan.metadata().semanticId());
                    MObject planObject = object(api, semanticObjects, objectNames, "Plan", plan.metadata(),
                            display(plan.label(), "plan_" + planIndex));
                    integer(api, planObject, "ordinal", plan.ordinal());
                    text(api, planObject, "label", plan.label());
                    text(api, planObject, "jasonContext", plan.context());
                    trace.add(catalog.require("A03"), TracePhase.INSTANCE_MATERIALIZATION, plan.metadata(),
                            "MObject", planObject.name(), List.of());
                    link(api, schema, "A17PlanLibraryPlan", libraryObject, planObject);
                    trace.add(catalog.require("A17"), TracePhase.INSTANCE_MATERIALIZATION, plan.metadata(),
                            "MLink", linkIdentity("A17", library.metadata().semanticId(), plan.metadata().semanticId()), List.of());
                    orderEntry(api, schema, semanticObjects, objectNames, trace, catalog, "A17", "A17PlanOrderEntry",
                            "A17OrderOwner", "A17OrderMember", libraryObject, planObject, library.metadata(),
                            plan.metadata(), planIndex);

                    var trigger = java.util.Objects.requireNonNull(plan.trigger(), "A04_TRIGGER_REQUIRED");
                    requireLiteral(NativeUseModelBuilder.TRIGGER_OPERATORS, trigger.operator(),
                            "JASON_TRIGGER_OPERATOR_UNSUPPORTED");
                    requireLiteral(NativeUseModelBuilder.TRIGGER_TYPES, trigger.type(),
                            "JASON_TRIGGER_TYPE_UNSUPPORTED");
                    MObject triggerObject = object(api, semanticObjects, objectNames, "Trigger", trigger.metadata(),
                            "trigger_" + planIndex);
                    enumeration(api, schema, triggerObject, "operator", "TriggerOperator", trigger.operator());
                    enumeration(api, schema, triggerObject, "triggerType", "TriggerType", trigger.type());
                    text(api, triggerObject, "literal", trigger.literal());
                    link(api, schema, "A18PlanTrigger", planObject, triggerObject);
                    trace.add(catalog.require("A04"), TracePhase.INSTANCE_MATERIALIZATION, trigger.metadata(),
                            "MObject", triggerObject.name(), List.of());
                    trace.add(catalog.require("A18"), TracePhase.INSTANCE_MATERIALIZATION, trigger.metadata(),
                            "MLink", linkIdentity("A18", plan.metadata().semanticId(), trigger.metadata().semanticId()), List.of());

                    List<MObject> bodyObjects = new ArrayList<>();
                    for (int bodyIndex = 0; bodyIndex < plan.body().size(); bodyIndex++) {
                        var body = plan.body().get(bodyIndex);
                        if (body.ordinal() != bodyIndex)
                            throw new IllegalArgumentException("A19_BODY_ORDINAL_NONCONTIGUOUS: " + body.metadata().semanticId());
                        requireLiteral(NativeUseModelBuilder.BODY_TYPES, body.bodyType(), "JASON_BODY_TYPE_UNSUPPORTED");
                        MObject bodyObject = object(api, semanticObjects, objectNames, "PlanBodyElement", body.metadata(),
                                body.bodyType() + "_" + bodyIndex);
                        integer(api, bodyObject, "ordinal", body.ordinal());
                        enumeration(api, schema, bodyObject, "bodyType", "PlanBodyType", body.bodyType());
                        text(api, bodyObject, "term", body.term());
                        link(api, schema, "A19PlanBodyElement", planObject, bodyObject);
                        orderEntry(api, schema, semanticObjects, objectNames, trace, catalog, "A19",
                                "A19BodyOrderEntry", "A19OrderOwner", "A19OrderMember", planObject, bodyObject,
                                plan.metadata(), body.metadata(), bodyIndex);
                        trace.add(catalog.require("A05"), TracePhase.INSTANCE_MATERIALIZATION, body.metadata(),
                                "MObject", bodyObject.name(), List.of());
                        trace.add(catalog.require("A19"), TracePhase.INSTANCE_MATERIALIZATION, body.metadata(),
                                "MLink", linkIdentity("A19", plan.metadata().semanticId(), body.metadata().semanticId()), List.of());
                        bodyObjects.add(bodyObject);
                    }
                    for (int bodyIndex = 0; bodyIndex < plan.body().size(); bodyIndex++) {
                        var body = plan.body().get(bodyIndex);
                        String expectedNext = bodyIndex + 1 < plan.body().size()
                                ? plan.body().get(bodyIndex + 1).metadata().semanticId() : "";
                        if (!expectedNext.equals(body.nextSemanticId()))
                            throw new IllegalArgumentException("A20_NEXT_ORDER_MISMATCH: " + body.metadata().semanticId());
                        if (bodyIndex + 1 < bodyObjects.size()) {
                            link(api, schema, "A20PlanBodyNext", bodyObjects.get(bodyIndex), bodyObjects.get(bodyIndex + 1));
                            trace.add(catalog.require("A20"), TracePhase.INSTANCE_MATERIALIZATION, body.metadata(),
                                    "MLink", linkIdentity("A20", body.metadata().semanticId(), expectedNext), List.of());
                        }
                    }
                }
            }
            StringWriter validation = new StringWriter();
            PrintWriter output = new PrintWriter(validation, true);
            boolean structureValid = system.state().checkStructure(output);
            boolean invariantsValid = system.state().check(output, false, true, true, List.of());
            if (!structureValid || !invariantsValid)
                throw new IllegalStateException("NATIVE_USE_STATE_INVALID: " + validation);
            return new Result(system, semanticObjects, trace.index(), true, true, validation.toString());
        } catch (UseApiException error) {
            throw new IllegalStateException("NATIVE_USE_STATE_BUILD_FAILED: " + error.getMessage(), error);
        }
    }

    private static void orderEntry(UseSystemApi api, NativeUseModelBuilder.Result schema,
                                   Map<String,MObject> semanticObjects, Set<String> names,
                                   CodeGroundedTraceCollector trace, CodeGroundedRuleCatalog catalog,
                                   String ruleId, String className, String ownerAssociation,
                                   String memberAssociation, MObject owner, MObject member,
                                   SemanticMetadata ownerMetadata, SemanticMetadata memberMetadata, int rank)
            throws UseApiException {
        String semanticId = "infrastructure:" + ruleId + ":" + ownerMetadata.semanticId() + ":" + rank;
        SemanticMetadata infrastructure = new SemanticMetadata(semanticId, ruleId + "_ORDER_ENTRY",
                NativeUseStateBuilder.class.getName(), memberMetadata.evidenceAuthority(), memberMetadata.fidelity(),
                memberMetadata.capabilityStatus(), memberMetadata.evidence(), List.of());
        MObject entry = object(api, semanticObjects, names, className, infrastructure, ruleId.toLowerCase() + "_" + rank);
        integer(api, entry, "rank", rank);
        link(api, schema, ownerAssociation, owner, entry);
        link(api, schema, memberAssociation, member, entry);
        trace.add(catalog.require(ruleId), TracePhase.INSTANCE_MATERIALIZATION, memberMetadata,
                "ORDER_ENTRY", entry.name(), List.of());
    }

    private static MObject object(UseSystemApi api, Map<String,MObject> semanticObjects, Set<String> names,
                                  String className, SemanticMetadata metadata, String human) throws UseApiException {
        if (semanticObjects.containsKey(metadata.semanticId()))
            throw new IllegalArgumentException("DUPLICATE_SEMANTIC_IDENTITY: " + metadata.semanticId());
        MClass cls = api.getSystem().model().getClass(className);
        if (cls == null) throw new IllegalStateException("NATIVE_USE_CLASS_MISSING: " + className);
        String objectName = objectName(className, human, metadata.semanticId());
        if (!names.add(objectName)) throw new IllegalStateException("NATIVE_USE_OBJECT_NAME_COLLISION: " + objectName);
        MObject object = api.createObjectEx(cls, objectName);
        text(api, object, "semanticId", metadata.semanticId());
        semanticObjects.put(metadata.semanticId(), object);
        return object;
    }

    private static void text(UseSystemApi api, MObject object, String name, String value) throws UseApiException {
        api.setAttributeValueEx(object, attribute(object, name), new StringValue(value == null ? "" : value));
    }

    private static void integer(UseSystemApi api, MObject object, String name, int value) throws UseApiException {
        api.setAttributeValueEx(object, attribute(object, name), IntegerValue.valueOf(value));
    }

    private static void enumeration(UseSystemApi api, NativeUseModelBuilder.Result schema, MObject object,
                                    String attribute, String enumeration, String literal) throws UseApiException {
        api.setAttributeValueEx(object, attribute(object, attribute),
                new EnumValue(schema.model().enumType(enumeration), literal));
    }

    private static MAttribute attribute(MObject object, String name) {
        MAttribute attribute = object.cls().attribute(name, true);
        if (attribute == null) throw new IllegalStateException("NATIVE_USE_ATTRIBUTE_MISSING: "
                + object.cls().name() + "." + name);
        return attribute;
    }

    private static void link(UseSystemApi api, NativeUseModelBuilder.Result schema, String association,
                             MObject first, MObject second) throws UseApiException {
        MAssociation value = schema.model().getAssociation(association);
        if (value == null) throw new IllegalStateException("NATIVE_USE_ASSOCIATION_MISSING: " + association);
        api.createLinkEx(value, new MObject[] {first, second});
    }

    private static void requireLiteral(List<String> allowed, String value, String diagnostic) {
        if (!allowed.contains(value)) throw new IllegalArgumentException(diagnostic + ": " + value);
    }

    private static String display(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String linkIdentity(String rule, String first, String second) { return "link:" + rule + ":" + first + "->" + second; }

    public static String objectName(String kind, String human, String semanticId) {
        String safe = (human == null ? kind : human).replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isBlank()) safe = kind;
        if (safe.length() > 32) safe = safe.substring(0, 32);
        if (Character.isDigit(safe.charAt(0))) safe = "_" + safe;
        return kind.toLowerCase() + "_" + safe + "_" + hash8(semanticId);
    }

    private static String hash8(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Result(MSystem system, Map<String,MObject> semanticObjectIndex,
                         CodeGroundedTraceIndex trace, boolean structureValid, boolean invariantsValid,
                         String validationOutput) {
        public Result { semanticObjectIndex = Collections.unmodifiableMap(new LinkedHashMap<>(semanticObjectIndex)); }
    }
}

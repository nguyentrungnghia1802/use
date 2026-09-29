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
import org.jacamo.bridge.contract.semantic.CrossSemanticContract;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
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
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
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
            var semantic = source.snapshot();
            for (var agent : semantic.agentDeclarations()) {
                MObject agentObject = object(api, semanticObjects, objectNames, "Agent", agent.metadata(), agent.name());
                text(api, agentObject, "name", agent.name());
                text(api, agentObject, "sourceUri", agent.sourceUri());
                text(api, agentObject, "options", NativeUseModelBuilder.canonicalJson(agent.options()));
                text(api, agentObject, "architectureClasses",
                        NativeUseModelBuilder.canonicalJson(agent.architectureClasses()));
                text(api, agentObject, "agentClass", agent.agentClass());
                text(api, agentObject, "beliefBaseClass", agent.beliefBaseClass());
                text(api, agentObject, "host", agent.host());
                integer(api, agentObject, "instances", agent.instances());
                trace.add(catalog.require("J02"), TracePhase.INSTANCE_MATERIALIZATION, agent.metadata(),
                        "MObject", agentObject.name(), List.of());
            }
            for (var workspace : semantic.workspaceDeclarations()) {
                MObject workspaceObject = object(api, semanticObjects, objectNames, "WorkspaceDeclaration", workspace.metadata(),
                        workspace.name());
                text(api, workspaceObject, "name", workspace.name());
                text(api, workspaceObject, "host", workspace.host());
                bool(api, workspaceObject, "debug", workspace.debug());
                trace.add(catalog.require("J03"), TracePhase.INSTANCE_MATERIALIZATION, workspace.metadata(),
                        "MObject", workspaceObject.name(), List.of("JCM_DECLARATION_NOT_RUNTIME_WORKSPACE_ID"));
            }
            for (var artifact : semantic.artifactDeclarations()) {
                MObject artifactObject = object(api, semanticObjects, objectNames, "ArtifactDeclaration",
                        artifact.metadata(), artifact.name());
                text(api, artifactObject, "name", artifact.name());
                text(api, artifactObject, "workspace", artifact.workspace());
                text(api, artifactObject, "javaClass", artifact.javaClass());
                text(api, artifactObject, "parameters",
                        NativeUseModelBuilder.canonicalJson(artifact.parameters()));
                trace.add(catalog.require("J04"), TracePhase.INSTANCE_MATERIALIZATION, artifact.metadata(),
                        "MObject", artifactObject.name(), List.of("DECLARATION_NOT_LIVE_CARTAGO_ARTIFACT"));
            }
            for (var organization : semantic.organizationDeployments()) {
                MObject organizationObject = object(api, semanticObjects, objectNames, "OrganizationDeployment",
                        organization.metadata(), organization.name());
                text(api, organizationObject, "name", organization.name());
                text(api, organizationObject, "source", organization.source());
                text(api, organizationObject, "institution", organization.institution());
                text(api, organizationObject, "debug", organization.debug());
                trace.add(catalog.require("J05"), TracePhase.INSTANCE_MATERIALIZATION, organization.metadata(),
                        "MObject", organizationObject.name(), List.of("DEPLOYMENT_NOT_MOISE_OS"));
            }
            for (var group : semantic.groupDeployments()) {
                MObject groupObject = object(api, semanticObjects, objectNames, "GroupDeployment", group.metadata(),
                        group.name());
                text(api, groupObject, "organization", group.organization());
                text(api, groupObject, "name", group.name());
                text(api, groupObject, "type", group.type());
                text(api, groupObject, "responsibleFor",
                        NativeUseModelBuilder.canonicalJson(group.responsibleFor()));
                trace.add(catalog.require("J06"), TracePhase.INSTANCE_MATERIALIZATION, group.metadata(),
                        "MObject", groupObject.name(), List.of("DEPLOYMENT_NOT_MOISE_GROUP"));
            }
            for (var scheme : semantic.schemeDeployments()) {
                MObject schemeObject = object(api, semanticObjects, objectNames, "SchemeDeployment", scheme.metadata(),
                        scheme.name());
                text(api, schemeObject, "organization", scheme.organization());
                text(api, schemeObject, "name", scheme.name());
                text(api, schemeObject, "type", scheme.type());
                trace.add(catalog.require("J07"), TracePhase.INSTANCE_MATERIALIZATION, scheme.metadata(),
                        "MObject", schemeObject.name(), List.of("DEPLOYMENT_NOT_MOISE_SCHEME"));
            }
            for (var institution : semantic.institutionDeployments()) {
                MObject institutionObject = object(api, semanticObjects, objectNames, "InstitutionDeployment",
                        institution.metadata(), institution.name());
                text(api, institutionObject, "name", institution.name());
                text(api, institutionObject, "workspaces",
                        NativeUseModelBuilder.canonicalJson(institution.workspaces()));
                text(api, institutionObject, "opaqueParameters",
                        NativeUseModelBuilder.canonicalJson(institution.opaqueParameters()));
                trace.add(catalog.require("J08"), TracePhase.INSTANCE_MATERIALIZATION, institution.metadata(),
                        "MObject", institutionObject.name(), List.of("OPAQUE_FIELDS_RETAINED_AS_CANONICAL_DATA"));
            }
            for (var tuple : semantic.rawRoleTuples()) {
                trace.add(catalog.require("J09"), TracePhase.INSTANCE_MATERIALIZATION, tuple.metadata(),
                        "RAW_ROLE_TUPLE", tuple.metadata().semanticId(), List.of("UNRESOLVED_UNTIL_X04"));
            }
            for (var tuple : semantic.rawFocusTuples()) {
                trace.add(catalog.require("J10"), TracePhase.INSTANCE_MATERIALIZATION, tuple.metadata(),
                        "RAW_FOCUS_TUPLE", tuple.metadata().semanticId(), List.of("UNRESOLVED_UNTIL_X06"));
            }
            for (var provenance : semantic.importProvenance()) {
                trace.add(catalog.require("J11"), TracePhase.INSTANCE_MATERIALIZATION, provenance.metadata(),
                        "IMPORT_PROVENANCE", provenance.metadata().semanticId(),
                        provenance.status() == org.jacamo.bridge.contract.CapabilityStatus.COMPLETE
                                ? List.of() : List.of(provenance.status().name()));
            }
            for (var organization : semantic.moiseOrganizations())
                materializeMoise(api, schema, semanticObjects, objectNames, trace, catalog, organization);
            for (var environment : semantic.cartagoEnvironments()) {
                MObject environmentObject = object(api, semanticObjects, objectNames, "Environment",
                        environment.metadata(), environment.environmentId());
                text(api, environmentObject, "name", environment.name());
                text(api, environmentObject, "environmentId", environment.environmentId());
                text(api, environmentObject, "version", environment.version());
                text(api, environmentObject, "defaultInfrastructureLayer", environment.defaultInfrastructureLayer());
                trace.add(catalog.require("C01"), TracePhase.INSTANCE_MATERIALIZATION, environment.metadata(),
                        "MObject", environmentObject.name(), List.of());

                for (var workspace : environment.workspaces()) {
                    MObject workspaceObject = object(api, semanticObjects, objectNames, "Workspace",
                            workspace.metadata(), workspace.fullName());
                    text(api, workspaceObject, "fullName", workspace.fullName());
                    text(api, workspaceObject, "name", workspace.name());
                    text(api, workspaceObject, "uuid", workspace.uuid());
                    text(api, workspaceObject, "parentSemanticId", workspace.parentSemanticId());
                    text(api, workspaceObject, "environmentSemanticId", workspace.environmentSemanticId());
                    bool(api, workspaceObject, "local", workspace.local());
                    text(api, workspaceObject, "protocol", workspace.protocol());
                    text(api, workspaceObject, "remotePath", workspace.remotePath());
                    text(api, workspaceObject, "address", workspace.address());
                    trace.add(catalog.require("C02"), TracePhase.INSTANCE_MATERIALIZATION, workspace.metadata(),
                            "MObject", workspaceObject.name(), List.of());
                }
                for (var artifactType : environment.artifactTypes()) {
                    MObject typeObject = object(api, semanticObjects, objectNames, "ArtifactType",
                            artifactType.metadata(), artifactType.javaClassName());
                    text(api, typeObject, "javaClassName", artifactType.javaClassName());
                    text(api, typeObject, "classLoaderIdentity", artifactType.classLoaderIdentity());
                    trace.add(catalog.require("C03"), TracePhase.INSTANCE_MATERIALIZATION, artifactType.metadata(),
                            "MObject", typeObject.name(), artifactType.classLoaderIdentity().isBlank()
                                    ? List.of("ARTIFACT_TYPE_CLASSLOADER_UNAVAILABLE") : List.of());
                }
                for (var artifact : environment.artifacts()) {
                    String artifactClass = schema.nativeArtifactTypeClassNames()
                            .getOrDefault(artifact.artifactTypeSemanticId(), "Artifact");
                    MObject artifactObject = object(api, semanticObjects, objectNames, artifactClass,
                            artifact.metadata(), artifact.name());
                    text(api, artifactObject, "name", artifact.name());
                    text(api, artifactObject, "uuid", artifact.uuid());
                    text(api, artifactObject, "artifactTypeSemanticId", artifact.artifactTypeSemanticId());
                    text(api, artifactObject, "workspaceSemanticId", artifact.workspaceSemanticId());
                    text(api, artifactObject, "creatorAgentSemanticId", artifact.creatorAgentSemanticId());
                    trace.add(catalog.require("C04"), TracePhase.INSTANCE_MATERIALIZATION, artifact.metadata(),
                            "MObject", artifactObject.name(), List.of());
                }
                for (var operation : environment.operations()) {
                    MObject operationObject = object(api, semanticObjects, objectNames, "Operation",
                            operation.metadata(), operation.name());
                    text(api, operationObject, "artifactSemanticId", operation.artifactSemanticId());
                    text(api, operationObject, "keyId", operation.keyId());
                    text(api, operationObject, "name", operation.name());
                    integer(api, operationObject, "arity", operation.arity());
                    bool(api, operationObject, "dynamic", operation.dynamic());
                    bool(api, operationObject, "linkOperation", operation.linkOperation());
                    bool(api, operationObject, "ui", operation.ui());
                    bool(api, operationObject, "internal", operation.internal());
                    trace.add(catalog.require("C05"), TracePhase.INSTANCE_MATERIALIZATION, operation.metadata(),
                            "MObject", operationObject.name(), List.of());
                }
                for (var backing : environment.backingOperations()) {
                    MObject backingObject = object(api, semanticObjects, objectNames, "BackingJavaOperation",
                            backing.metadata(), backing.methodName());
                    text(api, backingObject, "operationDescriptorId", backing.operationDescriptorId());
                    text(api, backingObject, "declaringClass", backing.declaringClass());
                    text(api, backingObject, "methodName", backing.methodName());
                    text(api, backingObject, "parameterTypes", NativeUseModelBuilder.canonicalJson(backing.parameterTypes()));
                    text(api, backingObject, "returnType", backing.returnType());
                    bool(api, backingObject, "varArgs", backing.varArgs());
                    text(api, backingObject, "classLoaderIdentity", backing.classLoaderIdentity());
                    trace.add(catalog.require("C06"), TracePhase.INSTANCE_MATERIALIZATION, backing.metadata(),
                            "MObject", backingObject.name(), schema.nativeOperationDescriptorIds()
                                    .contains(backing.operationDescriptorId())
                                            ? List.of("NATIVE_MOPERATION_PROJECTED")
                                            : List.of("NATIVE_MOPERATION_NOT_PROJECTED"));
                }
                for (var guard : environment.guards()) {
                    MObject guardObject = object(api, semanticObjects, objectNames, "Guard", guard.metadata(), guard.name());
                    text(api, guardObject, "operationDescriptorId", guard.operationDescriptorId());
                    text(api, guardObject, "name", guard.name());
                    integer(api, guardObject, "arity", guard.arity());
                    text(api, guardObject, "implementationClass", guard.implementationClass());
                    trace.add(catalog.require("C07"), TracePhase.INSTANCE_MATERIALIZATION, guard.metadata(),
                            "MObject", guardObject.name(), List.of());
                }
                if (!environment.liveProperties().isEmpty())
                    throw new IllegalArgumentException("C08_LIVE_PROPERTY_NOT_EXPOSED_BY_AUDITED_API");
                trace.add(catalog.require("C08"), TracePhase.INSTANCE_MATERIALIZATION, environment.metadata(),
                        "UNAVAILABLE", "C08:" + environment.metadata().semanticId(),
                        List.of("C08_UNAVAILABLE_NO_LIVE_OBSPROPERTY_API"));
                for (var property : environment.propertySnapshots()) {
                    MObject propertyObject = object(api, semanticObjects, objectNames, "ObservablePropertySnapshot",
                            property.metadata(), property.name());
                    text(api, propertyObject, "artifactSemanticId", property.artifactSemanticId());
                    text(api, propertyObject, "propertyId", property.propertyId());
                    text(api, propertyObject, "name", property.name());
                    text(api, propertyObject, "values", NativeUseModelBuilder.canonicalJson(property.values()));
                    text(api, propertyObject, "valueTypes", NativeUseModelBuilder.canonicalJson(property.valueTypes()));
                    text(api, propertyObject, "annotations", NativeUseModelBuilder.canonicalJson(property.annotations()));
                    trace.add(catalog.require("C09"), TracePhase.INSTANCE_MATERIALIZATION, property.metadata(),
                            "MObject", propertyObject.name(), List.of());
                }
                for (var info : environment.artifactInfos()) {
                    MObject infoObject = object(api, semanticObjects, objectNames, "ArtifactInfo",
                            info.metadata(), info.artifactSemanticId());
                    text(api, infoObject, "artifactSemanticId", info.artifactSemanticId());
                    text(api, infoObject, "creatorAgentSemanticId", info.creatorAgentSemanticId());
                    text(api, infoObject, "operationSemanticIds", NativeUseModelBuilder.canonicalJson(info.operationSemanticIds()));
                    text(api, infoObject, "observablePropertySemanticIds",
                            NativeUseModelBuilder.canonicalJson(info.observablePropertySemanticIds()));
                    text(api, infoObject, "linkedArtifactSemanticIds",
                            NativeUseModelBuilder.canonicalJson(info.linkedArtifactSemanticIds()));
                    trace.add(catalog.require("C10"), TracePhase.INSTANCE_MATERIALIZATION, info.metadata(),
                            "MObject", infoObject.name(), List.of());
                }
                for (var signal : environment.signals()) {
                    MObject signalObject = object(api, semanticObjects, objectNames, "Signal", signal.metadata(), signal.name());
                    text(api, signalObject, "artifactSemanticId", signal.artifactSemanticId());
                    text(api, signalObject, "name", signal.name());
                    text(api, signalObject, "values", NativeUseModelBuilder.canonicalJson(signal.values()));
                    trace.add(catalog.require("C11"), TracePhase.INSTANCE_MATERIALIZATION, signal.metadata(),
                            "MObject", signalObject.name(), List.of());
                }
                for (var agent : environment.agents()) {
                    MObject agentObject = object(api, semanticObjects, objectNames, "CartagoAgentIdentity",
                            agent.metadata(), agent.globalId());
                    text(api, agentObject, "globalId", agent.globalId());
                    integer(api, agentObject, "localId", agent.localId());
                    text(api, agentObject, "name", agent.name());
                    text(api, agentObject, "role", agent.role());
                    text(api, agentObject, "workspaceSemanticId", agent.workspaceSemanticId());
                    trace.add(catalog.require("C12"), TracePhase.INSTANCE_MATERIALIZATION, agent.metadata(),
                            "MObject", agentObject.name(), List.of());
                }
                for (var workspace : environment.workspaces()) {
                    MObject workspaceObject = required(semanticObjects, workspace.metadata().semanticId(), "C13_WORKSPACE_OBJECT");
                    link(api, schema, "C13EnvironmentWorkspace", environmentObject, workspaceObject);
                    trace.add(catalog.require("C13"), TracePhase.INSTANCE_MATERIALIZATION, workspace.metadata(),
                            "MLink", linkIdentity("C13", environment.metadata().semanticId(), workspace.metadata().semanticId()), List.of());
                }
                for (var artifact : environment.artifacts()) {
                    MObject artifactObject = required(semanticObjects, artifact.metadata().semanticId(), "C14_ARTIFACT_OBJECT");
                    MObject workspaceObject = required(semanticObjects, artifact.workspaceSemanticId(), "C14_WORKSPACE_REFERENCE");
                    link(api, schema, "C14WorkspaceArtifact", workspaceObject, artifactObject);
                    MObject typeObject = required(semanticObjects, artifact.artifactTypeSemanticId(), "C15_TYPE_REFERENCE");
                    link(api, schema, "C15ArtifactType", artifactObject, typeObject);
                    trace.add(catalog.require("C14"), TracePhase.INSTANCE_MATERIALIZATION, artifact.metadata(),
                            "MLink", linkIdentity("C14", artifact.workspaceSemanticId(), artifact.metadata().semanticId()), List.of());
                    trace.add(catalog.require("C15"), TracePhase.INSTANCE_MATERIALIZATION, artifact.metadata(),
                            "MLink", linkIdentity("C15", artifact.metadata().semanticId(), artifact.artifactTypeSemanticId()), List.of());
                }
                for (var operation : environment.operations()) {
                    MObject operationObject = required(semanticObjects, operation.metadata().semanticId(), "C16_OPERATION_OBJECT");
                    MObject artifactObject = required(semanticObjects, operation.artifactSemanticId(), "C16_ARTIFACT_REFERENCE");
                    link(api, schema, "C16ArtifactOperation", artifactObject, operationObject);
                    trace.add(catalog.require("C16"), TracePhase.INSTANCE_MATERIALIZATION, operation.metadata(),
                            "MLink", linkIdentity("C16", operation.artifactSemanticId(), operation.metadata().semanticId()), List.of());
                }
                for (var property : environment.propertySnapshots()) {
                    MObject propertyObject = required(semanticObjects, property.metadata().semanticId(), "C17_PROPERTY_OBJECT");
                    MObject artifactObject = required(semanticObjects, property.artifactSemanticId(), "C17_ARTIFACT_REFERENCE");
                    link(api, schema, "C17ArtifactObservableProperty", artifactObject, propertyObject);
                    trace.add(catalog.require("C17"), TracePhase.INSTANCE_MATERIALIZATION, property.metadata(),
                            "MLink", linkIdentity("C17", property.artifactSemanticId(), property.metadata().semanticId()), List.of());
                }
                for (var guard : environment.guards()) {
                    MObject guardObject = required(semanticObjects, guard.metadata().semanticId(), "C18_GUARD_OBJECT");
                    MObject operationObject = required(semanticObjects, guard.operationDescriptorId(), "C18_OPERATION_REFERENCE");
                    link(api, schema, "C18OperationGuard", operationObject, guardObject);
                    trace.add(catalog.require("C18"), TracePhase.INSTANCE_MATERIALIZATION, guard.metadata(),
                            "MLink", linkIdentity("C18", guard.operationDescriptorId(), guard.metadata().semanticId()), List.of());
                }
                for (var agent : environment.agents()) {
                    MObject agentObject = required(semanticObjects, agent.metadata().semanticId(), "C19_AGENT_OBJECT");
                    MObject workspaceObject = required(semanticObjects, agent.workspaceSemanticId(), "C19_WORKSPACE_REFERENCE");
                    link(api, schema, "C19WorkspaceAgent", workspaceObject, agentObject);
                    trace.add(catalog.require("C19"), TracePhase.INSTANCE_MATERIALIZATION, agent.metadata(),
                            "MLink", linkIdentity("C19", agent.workspaceSemanticId(), agent.metadata().semanticId()), List.of());
                }
                for (var focus : environment.focuses()) {
                    MObject agentObject = required(semanticObjects, focus.agentSemanticId(), "C20_AGENT_REFERENCE");
                    MObject artifactObject = required(semanticObjects, focus.artifactSemanticId(), "C20_ARTIFACT_REFERENCE");
                    if (focus.focused()) link(api, schema, "C20AgentArtifactFocus", agentObject, artifactObject);
                    trace.add(catalog.require("C20"), TracePhase.INSTANCE_MATERIALIZATION, focus.metadata(),
                            focus.focused() ? "MLink" : "FOCUS_EVENT", linkIdentity("C20", focus.agentSemanticId(),
                                    focus.artifactSemanticId()), List.of(focus.focused() ? "FOCUS" : "UNFOCUS"));
                }
            }
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
                provenance(trace, catalog, program.metadata(), "program:" + program.metadata().semanticId());

                for (int beliefIndex = 0; beliefIndex < program.beliefs().size(); beliefIndex++) {
                    var belief = program.beliefs().get(beliefIndex);
                    if (belief.ordinal() != beliefIndex)
                        throw new IllegalArgumentException("A21_BELIEF_ORDINAL_NONCONTIGUOUS: " + belief.metadata().semanticId());
                    MObject beliefObject = object(api, semanticObjects, objectNames, "Belief", belief.metadata(),
                            "belief_" + beliefIndex);
                    integer(api, beliefObject, "ordinal", belief.ordinal());
                    text(api, beliefObject, "literal", belief.literal());
                    link(api, schema, "A21ProgramBelief", programObject, beliefObject);
                    trace.add(catalog.require("A08"), TracePhase.INSTANCE_MATERIALIZATION, belief.metadata(),
                            "MObject", beliefObject.name(), List.of());
                    trace.add(catalog.require("A21"), TracePhase.INSTANCE_MATERIALIZATION, belief.metadata(),
                            "MLink", linkIdentity("A21", program.metadata().semanticId(), belief.metadata().semanticId()), List.of());
                    provenance(trace, catalog, belief.metadata(), "belief:" + belief.metadata().semanticId());
                }

                for (int goalIndex = 0; goalIndex < program.goals().size(); goalIndex++) {
                    var goal = program.goals().get(goalIndex);
                    if (goal.ordinal() != goalIndex)
                        throw new IllegalArgumentException("A22_GOAL_ORDINAL_NONCONTIGUOUS: " + goal.metadata().semanticId());
                    MObject goalObject = object(api, semanticObjects, objectNames, "AgentGoal", goal.metadata(),
                            "goal_" + goalIndex);
                    integer(api, goalObject, "ordinal", goal.ordinal());
                    text(api, goalObject, "literal", goal.literal());
                    text(api, goalObject, "goalKind", goal.goalKind());
                    link(api, schema, "A22ProgramGoal", programObject, goalObject);
                    trace.add(catalog.require("A09"), TracePhase.INSTANCE_MATERIALIZATION, goal.metadata(),
                            "MObject", goalObject.name(), List.of());
                    trace.add(catalog.require("A22"), TracePhase.INSTANCE_MATERIALIZATION, goal.metadata(),
                            "MLink", linkIdentity("A22", program.metadata().semanticId(), goal.metadata().semanticId()), List.of());
                    provenance(trace, catalog, goal.metadata(), "goal:" + goal.metadata().semanticId());
                }

                for (int ruleIndex = 0; ruleIndex < program.beliefRules().size(); ruleIndex++) {
                    var beliefRule = program.beliefRules().get(ruleIndex);
                    if (beliefRule.ordinal() != ruleIndex)
                        throw new IllegalArgumentException("A10_RULE_ORDINAL_NONCONTIGUOUS: " + beliefRule.metadata().semanticId());
                    MObject ruleObject = object(api, semanticObjects, objectNames, "BeliefRule", beliefRule.metadata(),
                            "rule_" + ruleIndex);
                    integer(api, ruleObject, "ordinal", beliefRule.ordinal());
                    text(api, ruleObject, "head", beliefRule.head());
                    text(api, ruleObject, "body", beliefRule.body());
                    trace.add(catalog.require("A10"), TracePhase.INSTANCE_MATERIALIZATION, beliefRule.metadata(),
                            "MObject", ruleObject.name(), List.of());
                    provenance(trace, catalog, beliefRule.metadata(), "rule:" + beliefRule.metadata().semanticId());
                }

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

                for (var action : program.actions()) {
                    MObject bodyObject = semanticObjects.get(action.planBodySemanticId());
                    if (bodyObject == null)
                        throw new IllegalArgumentException("ACTION_BODY_SEMANTIC_ID_UNRESOLVED: "
                                + action.planBodySemanticId());
                    requireLiteral(NativeUseModelBuilder.ACTION_KINDS, action.kind(), "JASON_ACTION_KIND_UNSUPPORTED");
                    MObject actionObject = object(api, semanticObjects, objectNames, "Action", action.metadata(),
                            action.kind().toLowerCase() + "_" + action.functor());
                    text(api, actionObject, "planBodySemanticId", action.planBodySemanticId());
                    text(api, actionObject, "term", action.term());
                    text(api, actionObject, "functor", action.functor());
                    integer(api, actionObject, "arity", action.arity());
                    enumeration(api, schema, actionObject, "kind", "ActionKind", action.kind());
                    trace.add(catalog.require(action.kind().equals("EXTERNAL") ? "A06" : "A07"),
                            TracePhase.INSTANCE_MATERIALIZATION, action.metadata(), "MObject", actionObject.name(), List.of());
                    provenance(trace, catalog, action.metadata(), "action:" + action.metadata().semanticId());
                }
            }
            applyExactBindings(api, schema, semanticObjects, objectNames, trace, catalog, semantic.exactBindings());
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

    private static void applyExactBindings(UseSystemApi api, NativeUseModelBuilder.Result schema,
                                           Map<String, MObject> semanticObjects, Set<String> objectNames,
                                           CodeGroundedTraceCollector trace, CodeGroundedRuleCatalog catalog,
                                           List<CrossSemanticContract.ExactBindingSemantic> bindings)
            throws UseApiException {
        for (var binding : bindings) {
            if (binding.sourceIds().size() != 1 || binding.targetIds().size() != 1)
                throw new IllegalArgumentException("CROSS_BINDING_CARDINALITY_UNSUPPORTED: "
                        + binding.metadata().semanticId());
            MObject evidence = object(api, semanticObjects, objectNames, "ExactBindingEvidence", binding.metadata(),
                    binding.ruleId());
            text(api, evidence, "ruleId", binding.ruleId());
            text(api, evidence, "sourceIds", NativeUseModelBuilder.canonicalJson(binding.sourceIds()));
            text(api, evidence, "targetIds", NativeUseModelBuilder.canonicalJson(binding.targetIds()));
            text(api, evidence, "bindingContext", NativeUseModelBuilder.canonicalJson(binding.context()));
            trace.add(catalog.require(binding.ruleId()), TracePhase.INSTANCE_MATERIALIZATION, binding.metadata(),
                    "MObject", evidence.name(), List.of("EXACT_EVIDENCE", "CONTEXT_RETAINED"));

            Endpoint endpoint = endpoint(binding.ruleId());
            MObject source = required(semanticObjects, binding.sourceIds().get(0),
                    "CROSS_SOURCE_OBJECT_MISSING_" + binding.ruleId());
            MObject target = required(semanticObjects, binding.targetIds().get(0),
                    "CROSS_TARGET_OBJECT_MISSING_" + binding.ruleId());
            if (!endpoint.sourceClass().equals(source.cls().name()))
                throw new IllegalArgumentException("CROSS_SOURCE_CLASS_MISMATCH_" + binding.ruleId()
                        + ": " + source.cls().name());
            if (!endpoint.targetClass().equals(target.cls().name()))
                throw new IllegalArgumentException("CROSS_TARGET_CLASS_MISMATCH_" + binding.ruleId()
                        + ": " + target.cls().name());
            link(api, schema, endpoint.association(), source, target);
            trace.add(catalog.require(binding.ruleId()), TracePhase.INSTANCE_MATERIALIZATION, binding.metadata(),
                    "MLink", linkIdentity(binding.ruleId(), binding.sourceIds().get(0), binding.targetIds().get(0)),
                    List.of("EXACT_EVIDENCE", "CONTEXT_RETAINED"));
        }
    }

    private static Endpoint endpoint(String ruleId) {
        return switch (ruleId) {
            case "X01" -> new Endpoint("X01ActionOperation", "Action", "Operation");
            // X02 deliberately targets the exact C09 snapshot because C08 live properties are unavailable.
            case "X02" -> new Endpoint("X02BeliefProperty", "Belief", "ObservablePropertySnapshot");
            case "X03" -> new Endpoint("X03TriggerSignal", "Trigger", "Signal");
            case "X04" -> new Endpoint("X04AgentRole", "Agent", "Role");
            case "X05" -> new Endpoint("X05AgentWorkspace", "Agent", "Workspace");
            case "X06" -> new Endpoint("X06AgentArtifactFocus", "Agent", "Artifact");
            case "X07" -> new Endpoint("X07AgentGoalOrganizationalGoal", "AgentGoal", "OrganizationalGoal");
            case "X08" -> new Endpoint("X08DeclarationArtifact", "ArtifactDeclaration", "Artifact");
            case "X09" -> new Endpoint("X09AgentIdentity", "Agent", "CartagoAgentIdentity");
            default -> throw new IllegalArgumentException("CROSS_RULE_ID_UNSUPPORTED: " + ruleId);
        };
    }

    private record Endpoint(String association, String sourceClass, String targetClass) { }

    private static void materializeMoise(UseSystemApi api, NativeUseModelBuilder.Result schema,
                                         Map<String, MObject> semanticObjects, Set<String> objectNames,
                                         CodeGroundedTraceCollector trace, CodeGroundedRuleCatalog catalog,
                                         MoiseSemanticContract.OrganizationSemantic organization)
            throws UseApiException {
        MObject organizationObject = object(api, semanticObjects, objectNames, "Organization",
                organization.metadata(), organization.name());
        text(api, organizationObject, "name", organization.name());
        text(api, organizationObject, "sourceUri", organization.sourceUri());
        trace.add(catalog.require("M01"), TracePhase.INSTANCE_MATERIALIZATION, organization.metadata(),
                "MObject", organizationObject.name(), List.of());

        var structural = organization.structuralSpecification();
        MObject structuralObject = object(api, semanticObjects, objectNames, "StructuralSpecification",
                structural.metadata(), structural.specificationId());
        text(api, structuralObject, "organizationSemanticId", structural.organizationSemanticId());
        text(api, structuralObject, "specificationId", structural.specificationId());
        text(api, structuralObject, "rootGroupSemanticId", structural.rootGroupSemanticId());
        link(api, schema, "M18OrganizationSS", organizationObject, structuralObject);
        trace.add(catalog.require("M02"), TracePhase.INSTANCE_MATERIALIZATION, structural.metadata(),
                "MObject", structuralObject.name(), List.of());
        trace.add(catalog.require("M18"), TracePhase.INSTANCE_MATERIALIZATION, structural.metadata(),
                "MLink", linkIdentity("M18", organization.metadata().semanticId(), structural.metadata().semanticId()), List.of());

        var functional = organization.functionalSpecification();
        MObject functionalObject = object(api, semanticObjects, objectNames, "FunctionalSpecification",
                functional.metadata(), functional.specificationId());
        text(api, functionalObject, "organizationSemanticId", functional.organizationSemanticId());
        text(api, functionalObject, "specificationId", functional.specificationId());
        link(api, schema, "M19OrganizationFS", organizationObject, functionalObject);
        trace.add(catalog.require("M03"), TracePhase.INSTANCE_MATERIALIZATION, functional.metadata(),
                "MObject", functionalObject.name(), List.of());
        trace.add(catalog.require("M19"), TracePhase.INSTANCE_MATERIALIZATION, functional.metadata(),
                "MLink", linkIdentity("M19", organization.metadata().semanticId(), functional.metadata().semanticId()), List.of());

        var normative = organization.normativeSpecification();
        MObject normativeObject = object(api, semanticObjects, objectNames, "NormativeSpecification",
                normative.metadata(), normative.specificationId());
        text(api, normativeObject, "organizationSemanticId", normative.organizationSemanticId());
        text(api, normativeObject, "specificationId", normative.specificationId());
        link(api, schema, "M20OrganizationNS", organizationObject, normativeObject);
        trace.add(catalog.require("M04"), TracePhase.INSTANCE_MATERIALIZATION, normative.metadata(),
                "MObject", normativeObject.name(), List.of());
        trace.add(catalog.require("M20"), TracePhase.INSTANCE_MATERIALIZATION, normative.metadata(),
                "MLink", linkIdentity("M20", organization.metadata().semanticId(), normative.metadata().semanticId()), List.of());

        Map<String, MoiseSemanticContract.RoleRelationSemantic> relations = new LinkedHashMap<>();
        Map<String, MObject> groupObjects = new LinkedHashMap<>();
        Map<String, MObject> roleObjects = new LinkedHashMap<>();
        for (var role : structural.roles()) {
            MObject roleObject = object(api, semanticObjects, objectNames, "Role", role.metadata(), role.roleId());
            roleObjects.put(role.roleId(), roleObject);
            text(api, roleObject, "roleId", role.roleId());
            bool(api, roleObject, "isAbstract", role.abstractRole());
            trace.add(catalog.require("M06"), TracePhase.INSTANCE_MATERIALIZATION, role.metadata(),
                    "MObject", roleObject.name(), List.of());
        }
        for (var role : structural.roles()) {
            MObject roleObject = roleObjects.get(role.roleId());
            for (String superRoleId : role.superRoleSemanticIds()) {
                MObject superRoleObject = roleObjects.get(superRoleId);
                if (superRoleObject == null) {
                    throw new IllegalArgumentException("M24_SUPER_ROLE_OBJECT: " + superRoleId);
                }
                link(api, schema, "M24RoleSuperRole", roleObject, superRoleObject);
                trace.add(catalog.require("M24"), TracePhase.INSTANCE_MATERIALIZATION, role.metadata(),
                        "MLink", linkIdentity("M24", role.roleId(), superRoleId), List.of());
            }
        }
        for (var group : structural.groups()) {
            MObject groupObject = object(api, semanticObjects, objectNames, "Group", group.metadata(), group.groupId());
            groupObjects.put(group.groupId(), groupObject);
            text(api, groupObject, "groupId", group.groupId());
            text(api, groupObject, "parentGroupSemanticId", group.parentGroupSemanticId());
            trace.add(catalog.require("M05"), TracePhase.INSTANCE_MATERIALIZATION, group.metadata(),
                    "MObject", groupObject.name(), List.of());
            if (!group.parentGroupSemanticId().isBlank()) {
                link(api, schema, "M23GroupSubgroup", required(semanticObjects, group.parentGroupSemanticId(),
                        "M23_PARENT_GROUP_OBJECT"), groupObject);
                trace.add(catalog.require("M23"), TracePhase.INSTANCE_MATERIALIZATION, group.metadata(),
                        "MLink", linkIdentity("M23", group.parentGroupSemanticId(), group.groupId()), List.of());
            }
        }
        if (!structural.rootGroupSemanticId().isBlank()) {
            link(api, schema, "M22SSGroup", structuralObject,
                    required(semanticObjects, structural.rootGroupSemanticId(), "M22_ROOT_GROUP_OBJECT"));
            trace.add(catalog.require("M22"), TracePhase.INSTANCE_MATERIALIZATION, structural.metadata(),
                    "MLink", linkIdentity("M22", structural.metadata().semanticId(), structural.rootGroupSemanticId()), List.of());
        }
        for (var role : structural.roles()) {
            link(api, schema, "M21SSRole", structuralObject, roleObjects.get(role.roleId()));
            trace.add(catalog.require("M21"), TracePhase.INSTANCE_MATERIALIZATION, role.metadata(),
                    "MLink", linkIdentity("M21", structural.metadata().semanticId(), role.roleId()), List.of());
        }
        for (var relation : structural.roleRelations()) {
            relations.put(relation.relationId(), relation);
            MObject relationObject = object(api, semanticObjects, objectNames, "RoleRelation", relation.metadata(),
                    relation.relationId());
            text(api, relationObject, "relationId", relation.relationId());
            text(api, relationObject, "relationKind", relation.relationKind());
            text(api, relationObject, "groupSemanticId", relation.groupSemanticId());
            text(api, relationObject, "sourceRoleSemanticId", relation.sourceRoleSemanticId());
            text(api, relationObject, "targetRoleSemanticId", relation.targetRoleSemanticId());
            text(api, relationObject, "scope", relation.scope());
            bool(api, relationObject, "extendsToSubGroups", relation.extendsToSubGroups());
            bool(api, relationObject, "bidirectional", relation.bidirectional());
            trace.add(catalog.require("M07"), TracePhase.INSTANCE_MATERIALIZATION, relation.metadata(),
                    "MObject", relationObject.name(), List.of());
        }
        for (var link : structural.links()) {
            MObject linkObject = object(api, semanticObjects, objectNames, "Link", link.metadata(), link.linkType());
            text(api, linkObject, "roleRelationSemanticId", link.roleRelationSemanticId());
            text(api, linkObject, "linkType", link.linkType());
            MoiseSemanticContract.RoleRelationSemantic relation = requiredRelation(relations,
                    link.roleRelationSemanticId(), "M08_ROLE_RELATION");
            link(api, schema, "M07RoleRelationLink", required(semanticObjects, relation.relationId(),
                    "M07_ROLE_RELATION_OBJECT"), linkObject);
            linkRoleEndpoint(api, schema, semanticObjects, "M25LinkSource", linkObject,
                    relation.sourceRoleSemanticId(), "M25_LINK_SOURCE");
            linkRoleEndpoint(api, schema, semanticObjects, "M26LinkTarget", linkObject,
                    relation.targetRoleSemanticId(), "M26_LINK_TARGET");
            trace.add(catalog.require("M08"), TracePhase.INSTANCE_MATERIALIZATION, link.metadata(),
                    "MObject", linkObject.name(), List.of());
            trace.add(catalog.require("M25"), TracePhase.INSTANCE_MATERIALIZATION, link.metadata(),
                    "MLink", linkIdentity("M25", link.metadata().semanticId(), relation.sourceRoleSemanticId()), List.of());
            trace.add(catalog.require("M26"), TracePhase.INSTANCE_MATERIALIZATION, link.metadata(),
                    "MLink", linkIdentity("M26", link.metadata().semanticId(), relation.targetRoleSemanticId()), List.of());
        }
        for (var compatibility : structural.compatibilities()) {
            MObject compatibilityObject = object(api, semanticObjects, objectNames, "Compatibility",
                    compatibility.metadata(), compatibility.roleRelationSemanticId());
            text(api, compatibilityObject, "roleRelationSemanticId", compatibility.roleRelationSemanticId());
            MoiseSemanticContract.RoleRelationSemantic relation = requiredRelation(relations,
                    compatibility.roleRelationSemanticId(), "M09_ROLE_RELATION");
            link(api, schema, "M07RoleRelationCompatibility", required(semanticObjects, relation.relationId(),
                    "M07_ROLE_RELATION_OBJECT"), compatibilityObject);
            linkRoleEndpoint(api, schema, semanticObjects, "M27CompatibilitySource", compatibilityObject,
                    relation.sourceRoleSemanticId(), "M27_COMPATIBILITY_SOURCE");
            linkRoleEndpoint(api, schema, semanticObjects, "M28CompatibilityTarget", compatibilityObject,
                    relation.targetRoleSemanticId(), "M28_COMPATIBILITY_TARGET");
            trace.add(catalog.require("M09"), TracePhase.INSTANCE_MATERIALIZATION, compatibility.metadata(),
                    "MObject", compatibilityObject.name(), List.of());
            trace.add(catalog.require("M27"), TracePhase.INSTANCE_MATERIALIZATION, compatibility.metadata(),
                    "MLink", linkIdentity("M27", compatibility.metadata().semanticId(), relation.sourceRoleSemanticId()), List.of());
            trace.add(catalog.require("M28"), TracePhase.INSTANCE_MATERIALIZATION, compatibility.metadata(),
                    "MLink", linkIdentity("M28", compatibility.metadata().semanticId(), relation.targetRoleSemanticId()), List.of());
        }
        for (var cardinality : structural.groupRoleCardinalities()) {
            MObject cardinalityObject = object(api, semanticObjects, objectNames, "GroupRoleCardinality",
                    cardinality.metadata(), cardinality.groupId());
            text(api, cardinalityObject, "groupSemanticId", cardinality.groupId());
            text(api, cardinalityObject, "roleSemanticId", cardinality.roleId());
            integer(api, cardinalityObject, "minCardinality", cardinality.min());
            integer(api, cardinalityObject, "maxCardinality", cardinality.max());
            link(api, schema, "M29CardinalityOwner", cardinalityObject,
                    required(semanticObjects, cardinality.groupId(), "M29_GROUP_OBJECT"));
            link(api, schema, "M30CardinalityMember", cardinalityObject,
                    required(semanticObjects, cardinality.roleId(), "M30_ROLE_OBJECT"));
            trace.add(catalog.require("M15"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MObject", cardinalityObject.name(), List.of());
            trace.add(catalog.require("M29"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MLink", linkIdentity("M29", cardinality.metadata().semanticId(), cardinality.groupId()), List.of());
            trace.add(catalog.require("M30"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MLink", linkIdentity("M30", cardinality.metadata().semanticId(), cardinality.roleId()), List.of());
        }
        for (var cardinality : structural.subGroupCardinalities()) {
            MObject cardinalityObject = object(api, semanticObjects, objectNames, "SubGroupCardinality",
                    cardinality.metadata(), cardinality.parentGroupId());
            text(api, cardinalityObject, "parentGroupSemanticId", cardinality.parentGroupId());
            text(api, cardinalityObject, "subGroupSemanticId", cardinality.subGroupId());
            integer(api, cardinalityObject, "minCardinality", cardinality.min());
            integer(api, cardinalityObject, "maxCardinality", cardinality.max());
            link(api, schema, "M31SubgroupCardinalityOwner", cardinalityObject,
                    required(semanticObjects, cardinality.parentGroupId(), "M31_PARENT_GROUP_OBJECT"));
            link(api, schema, "M32SubgroupCardinalityMember", cardinalityObject,
                    required(semanticObjects, cardinality.subGroupId(), "M32_CHILD_GROUP_OBJECT"));
            trace.add(catalog.require("M16"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MObject", cardinalityObject.name(), List.of());
            trace.add(catalog.require("M31"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MLink", linkIdentity("M31", cardinality.metadata().semanticId(), cardinality.parentGroupId()), List.of());
            trace.add(catalog.require("M32"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MLink", linkIdentity("M32", cardinality.metadata().semanticId(), cardinality.subGroupId()), List.of());
        }

        Map<String, MObject> schemeObjects = new LinkedHashMap<>();
        for (var scheme : functional.schemes()) {
            MObject schemeObject = object(api, semanticObjects, objectNames, "Scheme", scheme.metadata(), scheme.schemeId());
            schemeObjects.put(scheme.schemeId(), schemeObject);
            text(api, schemeObject, "schemeId", scheme.schemeId());
            text(api, schemeObject, "functionalSpecificationSemanticId", scheme.functionalSpecificationSemanticId());
            text(api, schemeObject, "rootGoalSemanticId", scheme.rootGoalSemanticId());
            link(api, schema, "M33FSScheme", functionalObject, schemeObject);
            trace.add(catalog.require("M10"), TracePhase.INSTANCE_MATERIALIZATION, scheme.metadata(),
                    "MObject", schemeObject.name(), List.of());
            trace.add(catalog.require("M33"), TracePhase.INSTANCE_MATERIALIZATION, scheme.metadata(),
                    "MLink", linkIdentity("M33", functional.metadata().semanticId(), scheme.metadata().semanticId()), List.of());
            for (var mission : scheme.missions()) {
                MObject missionObject = object(api, semanticObjects, objectNames, "Mission", mission.metadata(), mission.missionId());
                text(api, missionObject, "missionId", mission.missionId());
                text(api, missionObject, "schemeSemanticId", mission.schemeSemanticId());
                text(api, missionObject, "goalSemanticIds", NativeUseModelBuilder.canonicalJson(mission.goalSemanticIds()));
                link(api, schema, "M34SchemeMission", schemeObject, missionObject);
                trace.add(catalog.require("M11"), TracePhase.INSTANCE_MATERIALIZATION, mission.metadata(),
                        "MObject", missionObject.name(), List.of());
                trace.add(catalog.require("M34"), TracePhase.INSTANCE_MATERIALIZATION, mission.metadata(),
                        "MLink", linkIdentity("M34", scheme.schemeId(), mission.missionId()), List.of());
            }
            for (var goal : scheme.goals()) {
                MObject goalObject = object(api, semanticObjects, objectNames, "OrganizationalGoal", goal.metadata(), goal.goalId());
                text(api, goalObject, "goalId", goal.goalId());
                text(api, goalObject, "schemeSemanticId", goal.schemeSemanticId());
                enumeration(api, schema, goalObject, "goalType", "MoiseGoalType", goal.goalType());
                text(api, goalObject, "description", goal.description());
                text(api, goalObject, "arguments", goal.arguments());
                integer(api, goalObject, "minAgentsToSatisfy", goal.minAgentsToSatisfy());
                text(api, goalObject, "ttf", goal.ttf());
                text(api, goalObject, "location", goal.location());
                text(api, goalObject, "dependencySemanticIds", NativeUseModelBuilder.canonicalJson(goal.dependencySemanticIds()));
                text(api, goalObject, "planSemanticId", goal.planSemanticId());
                text(api, goalObject, "inPlanSemanticId", goal.inPlanSemanticId());
                trace.add(catalog.require("M12"), TracePhase.INSTANCE_MATERIALIZATION, goal.metadata(),
                        "MObject", goalObject.name(), List.of());
            }
            for (var mission : scheme.missions()) {
                MObject missionObject = required(semanticObjects, mission.missionId(), "M38_MISSION_OBJECT");
                for (String goalId : mission.goalSemanticIds()) {
                    link(api, schema, "M38MissionGoal", missionObject,
                            required(semanticObjects, goalId, "M38_GOAL_OBJECT"));
                    trace.add(catalog.require("M38"), TracePhase.INSTANCE_MATERIALIZATION, mission.metadata(),
                            "MLink", linkIdentity("M38", mission.missionId(), goalId), List.of());
                }
            }
            for (var plan : scheme.plans()) {
                MObject planObject = object(api, semanticObjects, objectNames, "OrganizationalPlan", plan.metadata(), plan.planId());
                text(api, planObject, "planId", plan.planId());
                text(api, planObject, "schemeSemanticId", plan.schemeSemanticId());
                text(api, planObject, "targetGoalSemanticId", plan.targetGoalSemanticId());
                enumeration(api, schema, planObject, "planOperator", "MoisePlanOperator", plan.operator());
                real(api, planObject, "successRate", plan.successRate());
                link(api, schema, "M39GoalPlan", required(semanticObjects, plan.targetGoalSemanticId(),
                        "M39_TARGET_GOAL_OBJECT"), planObject);
                trace.add(catalog.require("M13"), TracePhase.INSTANCE_MATERIALIZATION, plan.metadata(),
                        "MObject", planObject.name(), List.of());
                trace.add(catalog.require("M39"), TracePhase.INSTANCE_MATERIALIZATION, plan.metadata(),
                        "MLink", linkIdentity("M39", plan.targetGoalSemanticId(), plan.planId()), List.of());
                for (String subGoalId : plan.orderedSubGoalSemanticIds()) {
                    link(api, schema, "M40PlanSubGoals", planObject,
                            required(semanticObjects, subGoalId, "M40_SUBGOAL_OBJECT"));
                    trace.add(catalog.require("M40"), TracePhase.INSTANCE_MATERIALIZATION, plan.metadata(),
                            "MLink", linkIdentity("M40", plan.planId(), subGoalId), List.of());
                }
            }
            if (!scheme.rootGoalSemanticId().isBlank()) {
                link(api, schema, "M35SchemeRootGoal", schemeObject,
                        required(semanticObjects, scheme.rootGoalSemanticId(), "M35_ROOT_GOAL_OBJECT"));
                trace.add(catalog.require("M35"), TracePhase.INSTANCE_MATERIALIZATION, scheme.metadata(),
                        "MLink", linkIdentity("M35", scheme.schemeId(), scheme.rootGoalSemanticId()), List.of());
            }
        }
        for (var cardinality : functional.schemeMissionCardinalities()) {
            MObject cardinalityObject = object(api, semanticObjects, objectNames, "SchemeMissionCardinality",
                    cardinality.metadata(), cardinality.schemeId());
            text(api, cardinalityObject, "schemeSemanticId", cardinality.schemeId());
            text(api, cardinalityObject, "missionSemanticId", cardinality.missionId());
            integer(api, cardinalityObject, "minCardinality", cardinality.min());
            integer(api, cardinalityObject, "maxCardinality", cardinality.max());
            link(api, schema, "M36SchemeCardinality", cardinalityObject,
                    required(semanticObjects, cardinality.schemeId(), "M36_SCHEME_OBJECT"));
            link(api, schema, "M37MissionCardinality", cardinalityObject,
                    required(semanticObjects, cardinality.missionId(), "M37_MISSION_OBJECT"));
            trace.add(catalog.require("M17"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MObject", cardinalityObject.name(), List.of());
            trace.add(catalog.require("M36"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MLink", linkIdentity("M36", cardinality.metadata().semanticId(), cardinality.schemeId()), List.of());
            trace.add(catalog.require("M37"), TracePhase.INSTANCE_MATERIALIZATION, cardinality.metadata(),
                    "MLink", linkIdentity("M37", cardinality.metadata().semanticId(), cardinality.missionId()), List.of());
        }
        for (var norm : normative.norms()) {
            MObject normObject = object(api, semanticObjects, objectNames, "Norm", norm.metadata(), norm.normId());
            text(api, normObject, "normId", norm.normId());
            text(api, normObject, "normativeSpecificationSemanticId", norm.normativeSpecificationSemanticId());
            text(api, normObject, "roleSemanticId", norm.roleSemanticId());
            text(api, normObject, "missionSemanticId", norm.missionSemanticId());
            enumeration(api, schema, normObject, "normType", "MoiseNormType", norm.operationType());
            text(api, normObject, "condition", norm.condition());
            text(api, normObject, "timeConstraint", norm.timeConstraint());
            link(api, schema, "M41NSNorm", normativeObject, normObject);
            trace.add(catalog.require("M14"), TracePhase.INSTANCE_MATERIALIZATION, norm.metadata(),
                    "MObject", normObject.name(), List.of("NORM_RETAINED_AS_DATA_NO_OCL"));
            trace.add(catalog.require("M41"), TracePhase.INSTANCE_MATERIALIZATION, norm.metadata(),
                    "MLink", linkIdentity("M41", normative.metadata().semanticId(), norm.normId()), List.of());
            if (!norm.roleSemanticId().isBlank()) {
                link(api, schema, "M42NormRole", normObject,
                        required(semanticObjects, norm.roleSemanticId(), "M42_ROLE_OBJECT"));
                trace.add(catalog.require("M42"), TracePhase.INSTANCE_MATERIALIZATION, norm.metadata(),
                        "MLink", linkIdentity("M42", norm.normId(), norm.roleSemanticId()), List.of());
            }
            if (!norm.missionSemanticId().isBlank()) {
                link(api, schema, "M43NormMission", normObject,
                        required(semanticObjects, norm.missionSemanticId(), "M43_MISSION_OBJECT"));
                trace.add(catalog.require("M43"), TracePhase.INSTANCE_MATERIALIZATION, norm.metadata(),
                        "MLink", linkIdentity("M43", norm.normId(), norm.missionSemanticId()), List.of());
            }
        }
    }

    private static void linkRoleEndpoint(UseSystemApi api, NativeUseModelBuilder.Result schema,
                                         Map<String, MObject> semanticObjects, String association,
                                         MObject relationObject, String roleId, String diagnostic)
            throws UseApiException {
        if (roleId == null || roleId.isBlank()) throw new IllegalArgumentException(diagnostic + "_UNAVAILABLE");
        link(api, schema, association, relationObject, required(semanticObjects, roleId, diagnostic));
    }

    private static MoiseSemanticContract.RoleRelationSemantic requiredRelation(
            Map<String, MoiseSemanticContract.RoleRelationSemantic> relations, String relationId, String diagnostic) {
        var relation = relations.get(relationId);
        if (relation == null) throw new IllegalArgumentException(diagnostic + ": " + relationId);
        return relation;
    }

    private static void real(UseSystemApi api, MObject object, String name, double value) throws UseApiException {
        api.setAttributeValueEx(object, attribute(object, name), new RealValue(value));
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

    private static MObject required(Map<String, MObject> semanticObjects, String semanticId, String diagnostic) {
        MObject object = semanticObjects.get(semanticId);
        if (object == null) throw new IllegalArgumentException(diagnostic + ": " + semanticId);
        return object;
    }

    private static void bool(UseSystemApi api, MObject object, String name, boolean value) throws UseApiException {
        api.setAttributeValueEx(object, attribute(object, name), BooleanValue.get(value));
    }

    private static void provenance(CodeGroundedTraceCollector trace, CodeGroundedRuleCatalog catalog,
                                   SemanticMetadata metadata, String targetIdentity) {
        trace.add(catalog.require("A11"), TracePhase.INSTANCE_MATERIALIZATION, metadata,
                "PROVENANCE", targetIdentity, List.of());
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

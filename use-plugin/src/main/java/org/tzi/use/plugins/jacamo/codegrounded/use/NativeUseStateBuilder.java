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
import org.tzi.use.uml.ocl.value.BooleanValue;
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
                    MObject artifactObject = object(api, semanticObjects, objectNames, "Artifact",
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
                            "MObject", backingObject.name(), List.of("NATIVE_MOPERATION_NOT_PROJECTED"));
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

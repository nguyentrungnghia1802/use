package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.csl.ScalarValueCodec;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;

/**
 * Runtime state mapping for compact semantic mappings (v3).
 * Connects live/replay RuntimeSnapshot cuts to PhysicalCutFact, RuntimeInstanceLinker,
 * AuthoritativeMSystemProjector/Port, and NativeVerificationCoordinator.
 */
public final class CompactRuntimeStateMapping implements CaseRuntimeMapping {

    private final SemanticProgram program;
    private final CompiledObservationPlan compactPlan;
    private final MModel model;
    private final Map<String, String> canonicalToObjectName = new LinkedHashMap<>();
    private final Map<String, PhysicalCutFact> cutsByRevision = new LinkedHashMap<>();
    private final Map<String, NativeVerificationCoordinator.CutVerificationReport> reportsByRevision = new LinkedHashMap<>();
    private final Map<String, List<OccurrenceProjection.Scope>> scopesByRevision = new LinkedHashMap<>();
    private final Map<String, List<TaskDemandProjection.Demand>> demandsByRevision = new LinkedHashMap<>();
    private PhysicalCutFact latestCut;
    private NativeVerificationCoordinator.CutVerificationReport latestReport;

    public CompactRuntimeStateMapping(SemanticProgram program, MModel model) {
        this.program = Objects.requireNonNull(program, "program");
        this.compactPlan = Objects.requireNonNull(program.compactPlan(), "compactPlan");
        this.model = Objects.requireNonNull(model, "model");
    }

    public CompiledObservationPlan compactPlan() {
        return compactPlan;
    }

    public Optional<PhysicalCutFact> cutForRevision(String revision) {
        return Optional.ofNullable(cutsByRevision.getOrDefault(revision, latestCut));
    }

    public Optional<NativeVerificationCoordinator.CutVerificationReport> reportForRevision(String revision) {
        return Optional.ofNullable(reportsByRevision.getOrDefault(revision, latestReport));
    }

    @Override
    public void validateObservation(VerificationObservation observation, String run, long generation, String revision) {
        program.assertUnchanged();
        if (run == null || run.isBlank() || generation < 0) {
            throw new IllegalArgumentException("COMPACT_OBSERVATION_EPOCH_MISMATCH");
        }
    }

    @Override
    public List<CaseRuntimeMapping.Outcome> applyCut(
            Port port,
            String run,
            long generation,
            RuntimeSnapshot cut,
            List<RuntimeEvent> prefix,
            RuntimeEvent event) throws UseApiException {
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(cut, "cut");
        program.assertUnchanged();

        String runId = (run != null && !run.isBlank()) ? run
                : (cut.runId() != null && !cut.runId().isBlank() ? cut.runId() : "default");
        String epoch = runId + ":" + generation;

        PhysicalCutFact physicalCut = extractPhysicalCutFact(cut, event, runId);
        cutsByRevision.put(cut.modelRevision(), physicalCut);
        latestCut = physicalCut;

        RuntimeInstanceLinker.LinkingResult linked = RuntimeInstanceLinker.link(compactPlan, physicalCut);
        var outcomes = new ArrayList<CaseRuntimeMapping.Outcome>();

        // 1. Apply disposed objects
        for (String disposedName : linked.disposedObjects()) {
            MObject existing = port.find(disposedName);
            if (existing != null) {
                port.deleteObject(existing);
                outcomes.add(new CaseRuntimeMapping.Outcome(
                        false, "COMPACT-M0-DISPOSE", disposedName, existing,
                        "DISPOSED:COMPACT_RUNTIME_LINKER"));
            }
        }

        // 2. Apply object mutations from RuntimeInstanceLinker
        for (var mutation : linked.objectMutations()) {
            MClass cls = model.getClass(mutation.targetClass());
            if (cls == null) continue;
            MObject obj = port.ensureObject(mutation.instanceName(), cls);
            port.metadata(obj, "semanticId", mutation.instanceName());
            port.metadata(obj, "xml:m0:program", program.hash());
            port.metadata(obj, "xml:m0:epoch", epoch);
            port.metadata(obj, "xml:m0:rule", "compact:" + mutation.targetClass());
            for (var attrEntry : mutation.attributes().entrySet()) {
                MAttribute attr = cls.attribute(attrEntry.getKey(), true);
                if (attr != null) {
                    port.set(obj, attr.name(), coerceToAttributeType(attr.type(), attrEntry.getValue()));
                }
            }
            outcomes.add(new CaseRuntimeMapping.Outcome(
                    true, "COMPACT-M0-UPSERT", mutation.instanceName(), obj,
                    "MAPPED:COMPACT_RUNTIME_LINKER"));
        }

        // 3. Apply link mutations from RuntimeInstanceLinker
        for (var link : linked.linkMutations()) {
            MAssociation assoc = model.getAssociation(link.associationName());
            if (assoc == null) continue;
            List<MObject> participants = new ArrayList<>();
            for (String endName : link.participantNames()) {
                MObject p = port.find(endName);
                if (p != null) participants.add(p);
            }
            if (participants.size() == assoc.associationEnds().size() && !port.hasLink(assoc, participants)) {
                port.createLink(assoc, participants);
            }
        }

        // 4. Project OrgContext instances (workspace/group/agent contexts) and Observer views
        projectOrgContextsAndObservers(port, cut, physicalCut, epoch, outcomes);

        // 5. Build OccurrenceProjection.Scope and TaskDemandProjection.Demand for RuntimeEngineSessionOwner
        buildOccurrencesAndDemands(port, cut, physicalCut, runId, generation);

        // 6. Run NativeVerificationCoordinator on the authoritative MSystem
        var system = port.readState().system();
        var report = NativeVerificationCoordinator.evaluate(compactPlan, system, physicalCut);
        reportsByRevision.put(cut.modelRevision(), report);
        latestReport = report;

        return List.copyOf(outcomes);
    }

    @Override
    public boolean ownsRemoval(Port port, MObject object) {
        return program.hash().equals(port.metadata(object, "xml:m0:program"));
    }

    @Override
    public String rawInputRuleId() {
        return "COMPACT-RAW-INPUT";
    }

    @Override
    public String observationRejectionCode() {
        return "COMPACT_OBSERVATION_REJECTED";
    }

    @Override
    public String mappingRejectionCode() {
        return "COMPACT_M0_MAPPING_REJECTED";
    }

    public List<OccurrenceProjection.Scope> occurrences(Port port, String run, long generation, String revision) {
        return scopesByRevision.getOrDefault(revision, List.of());
    }

    public List<TaskDemandProjection.Demand> taskDemands(Port port, String run, long generation, String revision) {
        return demandsByRevision.getOrDefault(revision, List.of());
    }

    private PhysicalCutFact extractPhysicalCutFact(RuntimeSnapshot cut, RuntimeEvent event, String runId) {
        Map<String, String> workspaceNamesBySemanticId = new LinkedHashMap<>();
        for (RuntimeFact fact : cut.facts()) {
            if (fact.kind() == RuntimeFactKind.WORKSPACE && "cartago".equals(fact.id().authority())) {
                String semId = Objects.toString(fact.values().get("semanticId"), "");
                String name = Objects.toString(fact.values().get("name"), fact.id().localId());
                if (!semId.isBlank()) {
                    workspaceNamesBySemanticId.put(semId, semId);
                    workspaceNamesBySemanticId.put(semId + ":name", name);
                }
            }
        }

        // Collect properties per artifact semanticId
        Map<String, Map<String, Object>> propsByArtifactSemanticId = new LinkedHashMap<>();
        for (RuntimeFact fact : cut.facts()) {
            if (fact.kind() == RuntimeFactKind.PROPERTY && "cartago".equals(fact.id().authority())) {
                String artSemId = Objects.toString(fact.values().get("artifactSemanticId"), "");
                String propName = Objects.toString(fact.values().get("name"), "");
                if (artSemId.isBlank() || propName.isBlank()) continue;
                Object val = extractPropertyScalar(fact.values());
                if (val != null) {
                    propsByArtifactSemanticId.computeIfAbsent(artSemId, k -> new LinkedHashMap<>()).put(propName, val);
                }
            }
        }

        // Collect artifacts
        List<PhysicalCutFact.ArtifactInstance> artifacts = new ArrayList<>();
        for (RuntimeFact fact : cut.facts()) {
            if (fact.kind() == RuntimeFactKind.ARTIFACT && "cartago".equals(fact.id().authority())) {
                String javaClass = Objects.toString(fact.values().get("artifactTypeJavaClassName"), "");
                if (javaClass.startsWith("ora4mas.nopl.") || javaClass.startsWith("cartago.")) continue;
                String name = Objects.toString(fact.values().get("name"), fact.id().localId());
                String wspSemId = Objects.toString(fact.values().get("workspaceSemanticId"), fact.id().scope());
                String artSemId = Objects.toString(fact.values().get("semanticId"), fact.id().canonical());
                String incarnation = fact.id().incarnation();

                Map<String, Object> props = new LinkedHashMap<>(propsByArtifactSemanticId.getOrDefault(artSemId, Map.of()));
                Map<String, Object> fields = new LinkedHashMap<>();

                if (fact.values().get(CapabilityObservation.KEY) instanceof Map<?, ?> receipts) {
                    for (Object rawReceipt : receipts.values()) {
                        if (rawReceipt instanceof Map<?, ?> rMap) {
                            Object dataObj = rMap.get("data") instanceof Map<?, ?> d ? d : rMap;
                            if (dataObj instanceof Map<?, ?> dMap) {
                                if (dMap.get("fieldName") instanceof String fn && dMap.containsKey("fieldValue")) {
                                    fields.put(fn, dMap.get("fieldValue"));
                                }
                                if (dMap.get("fields") instanceof Map<?, ?> fm) {
                                    fm.forEach((k, v) -> fields.put(String.valueOf(k), v));
                                }
                                if (dMap.get("properties") instanceof Map<?, ?> pm) {
                                    pm.forEach((k, v) -> props.putIfAbsent(String.valueOf(k), v));
                                }
                            }
                        }
                    }
                }

                var artInst = new PhysicalCutFact.ArtifactInstance(
                        name, javaClass, wspSemId, props, fields, runId, incarnation);
                artifacts.add(artInst);
                String targetClass = compactPlan.cslBindings().stream()
                        .filter(b -> b.kind() == CompiledObservationPlan.CslBinding.BindingKind.ENTITY_MAPPING
                                && b.sourceRef() instanceof CompactMappingSpecification.SourceReference.ArtifactRef ar
                                && ar.className().equals(javaClass))
                        .map(b -> b.targetRef().qualifier())
                        .findFirst()
                        .orElse("");
                if (!targetClass.isBlank()) {
                    canonicalToObjectName.put(
                            fact.id().canonical(),
                            RuntimeInstanceLinker.sanitizeIdentifier(targetClass + "_" + runId + "_" + wspSemId + "_" + name + "_" + incarnation));
                }
            }
        }

        // Collect agents and role enactments
        Map<String, String> declIdToAsl = new LinkedHashMap<>();
        for (var decl : program.source().nodes()) {
            if ("JACAMO_AGENT_DECLARATION".equals(decl.sourceKind())) {
                String srcUri = String.valueOf(decl.features().getOrDefault("source",
                        decl.features().getOrDefault("aslSource", new NormalizedSourceModel.Feature("", true, ""))).value());
                declIdToAsl.put(decl.id(), srcUri);
            }
        }
        Map<String, Set<String>> rolesByAgentName = new LinkedHashMap<>();
        for (RuntimeFact fact : cut.facts()) {
            if (fact.kind() == RuntimeFactKind.GROUP_BOARD && "moise".equals(fact.id().authority())) {
                if (fact.values().get("players") instanceof List<?> players) {
                    for (Object pObj : players) {
                        if (pObj instanceof Map<?, ?> pMap) {
                            String ag = Objects.toString(pMap.get("agentName"), Objects.toString(pMap.get("agent"), ""));
                            String role = Objects.toString(pMap.get("roleId"), Objects.toString(pMap.get("role"), ""));
                            if (!ag.isBlank() && !role.isBlank()) {
                                rolesByAgentName.computeIfAbsent(ag, k -> new LinkedHashSet<>()).add(role);
                            }
                        }
                    }
                }
            }
        }

        List<PhysicalCutFact.AgentInstance> agents = new ArrayList<>();
        for (RuntimeFact fact : cut.facts()) {
            if (fact.kind() == RuntimeFactKind.AGENT && "jason".equals(fact.id().authority())) {
                String name = Objects.toString(fact.values().get("name"), fact.id().localId());
                String declId = Objects.toString(fact.values().get("agentDeclarationSemanticId"), "");
                String asl = declIdToAsl.getOrDefault(declId, "");
                Set<String> enacted = rolesByAgentName.getOrDefault(name, Set.of());
                agents.add(new PhysicalCutFact.AgentInstance(name, Set.of(), Set.of(), enacted, asl));
            }
        }

        // Collect step receipts
        List<PhysicalCutFact.StepReceipt> stepReceipts = new ArrayList<>();
        for (RuntimeFact fact : cut.facts()) {
            if (fact.values().containsKey("verificationObservation")) {
                Object rawObs = fact.values().get("verificationObservation");
                if (rawObs instanceof Map<?, ?> obsMap && "STEP".equals(obsMap.get("kind"))
                        && obsMap.get("data") instanceof Map<?, ?> data) {
                    String task = Objects.toString(data.get("task"), "");
                    String phase = Objects.toString(data.get("phase"), "UNKNOWN");
                    String agent = Objects.toString(data.get("agent"), "");
                    String scheme = Objects.toString(data.get("scheme"), "");
                    String invocation = Objects.toString(data.get("invocation"), "");
                    Map<String, Object> bound = new LinkedHashMap<>();
                    if (data.get("bindings") instanceof Map<?, ?> bMap) {
                        bMap.forEach((k, v) -> bound.put(String.valueOf(k), v));
                    }
                    for (var window : compactPlan.stepWindows()) {
                        String simpleTask = window.istarTaskId().contains(".")
                                ? window.istarTaskId().substring(window.istarTaskId().lastIndexOf('.') + 1)
                                : window.istarTaskId();
                        if (simpleTask.equals(task) || window.windowId().equals(task)) {
                            stepReceipts.add(new PhysicalCutFact.StepReceipt(
                                    window.windowId(), phase, bound, agent, fact.id().scope(), scheme, invocation));
                        }
                    }
                }
            }
        }

        // Collect disposed objects
        List<String> disposed = new ArrayList<>();
        if (event != null && (event.kind() == RuntimeEventKind.DISPOSED || event.kind() == RuntimeEventKind.REMOVED)
                && event.entityId() != null) {
            String mappedName = canonicalToObjectName.remove(event.entityId().canonical());
            if (mappedName != null) {
                disposed.add(mappedName);
            }
        }

        return new PhysicalCutFact(runId, cut.generation(), artifacts, agents, List.of(), stepReceipts, disposed);
    }

    private void projectOrgContextsAndObservers(
            Port port,
            RuntimeSnapshot cut,
            PhysicalCutFact physicalCut,
            String epoch,
            List<CaseRuntimeMapping.Outcome> outcomes) throws UseApiException {
        // Project workspace-backed OrgContexts (e.g. GreetingRun, BuildingRun)
        for (var oc : program.cslModel().orgContexts()) {
            MClass cls = model.getClass(oc.name());
            if (cls == null) continue;
            if (oc.attributes().stream().anyMatch(a -> "workspace".equals(a.name()))) {
                for (RuntimeFact fact : cut.facts()) {
                    if (fact.kind() == RuntimeFactKind.WORKSPACE && "cartago".equals(fact.id().authority())) {
                        String semId = Objects.toString(fact.values().get("semanticId"), fact.id().canonical());
                        String instName = RuntimeInstanceLinker.sanitizeIdentifier(oc.name() + "_" + fact.id().canonical());
                        MObject obj = port.ensureObject(instName, cls);
                        port.metadata(obj, "semanticId", fact.id().canonical());
                        port.metadata(obj, "xml:m0:program", program.hash());
                        port.metadata(obj, "xml:m0:epoch", epoch);
                        if (cls.attribute("workspace", true) != null) {
                            port.set(obj, "workspace", new StringValue(semId));
                        }
                        if (cls.attribute("name", true) != null) {
                            port.set(obj, "name", new StringValue(Objects.toString(fact.values().get("name"), fact.id().localId())));
                        }
                        outcomes.add(new CaseRuntimeMapping.Outcome(
                                true, "COMPACT-M0-CONTEXT", instName, obj, "MAPPED:COMPACT_CONTEXT_PROJECTOR"));
                    }
                }
            }
        }

        // Project BuildProgress observer instances if BuildProgress exists on MModel
        MClass buildProgressCls = model.getClass("BuildProgress");
        if (buildProgressCls != null) {
            projectBuildProgress(port, cut, buildProgressCls, epoch);
        }
    }

    private void projectBuildProgress(Port port, RuntimeSnapshot cut, MClass buildProgressCls, String epoch) throws UseApiException {
        Map<String, String> wspParent = new LinkedHashMap<>();
        for (RuntimeFact fact : cut.facts()) {
            if (fact.kind() == RuntimeFactKind.WORKSPACE && "cartago".equals(fact.id().authority())) {
                String semId = Objects.toString(fact.values().get("semanticId"), "");
                String parent = Objects.toString(fact.values().get("parentSemanticId"), "");
                if (!semId.isBlank() && !parent.isBlank()) {
                    wspParent.put(semId, parent);
                }
            }
        }
        List<String> ops = List.of(
                "prepareSite", "layFloors", "buildWalls", "buildRoof",
                "fitWindows", "fitDoors", "installPlumbing", "installElectricalSystem",
                "paintExterior", "paintInterior");
        for (RuntimeFact artifact : cut.facts()) {
            if (artifact.kind() == RuntimeFactKind.ARTIFACT && "cartago".equals(artifact.id().authority())
                    && "simulator.House".equals(artifact.values().get("artifactTypeJavaClassName"))) {
                String wsp = Objects.toString(artifact.values().get("workspaceSemanticId"), "");
                String runWsp = wspParent.getOrDefault(wsp, wsp);
                String instName = RuntimeInstanceLinker.sanitizeIdentifier("BuildProgress_" + artifact.id().canonical());
                MObject obj = port.ensureObject(instName, buildProgressCls);
                port.metadata(obj, "semanticId", artifact.id().canonical());
                port.metadata(obj, "xml:m0:program", program.hash());
                port.metadata(obj, "xml:m0:epoch", epoch);
                if (buildProgressCls.attribute("workspace", true) != null) {
                    port.set(obj, "workspace", new StringValue(runWsp));
                }
                Map<String, Integer> seqByOp = extractOperationSequences(artifact);
                for (String op : ops) {
                    String field = op + "At";
                    if (buildProgressCls.attribute(field, true) != null) {
                        int seq = seqByOp.getOrDefault(op, -1);
                        port.set(obj, field, IntegerValue.valueOf(seq));
                    }
                }
            }
        }
    }

    private static Map<String, Integer> extractOperationSequences(RuntimeFact artifact) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (!(artifact.values().get(CapabilityObservation.KEY) instanceof Map<?, ?> receipts)) {
            return result;
        }
        for (Object rawReceipt : receipts.values()) {
            if (!(rawReceipt instanceof Map<?, ?> rMap)) continue;
            Object dataObj = rMap.get("data") instanceof Map<?, ?> d ? d : rMap;
            if (!(dataObj instanceof Map<?, ?> dMap) || !(dMap.get("events") instanceof List<?> events)) continue;
            for (Object evObj : events) {
                if (!(evObj instanceof Map<?, ?> ev)) continue;
                if (!"SUCCEEDED".equals(ev.get("outcome"))) continue;
                String op = Objects.toString(ev.get("operation"), "");
                Object seqObj = ev.get("sourceSequence");
                if (!op.isBlank() && seqObj instanceof Number num) {
                    result.putIfAbsent(op, num.intValue());
                }
            }
        }
        return result;
    }

    private void buildOccurrencesAndDemands(
            Port port,
            RuntimeSnapshot cut,
            PhysicalCutFact physicalCut,
            String runId,
            long generation) {
        List<OccurrenceProjection.Scope> scopes = new ArrayList<>();
        String primaryActor = program.goalBindings().actors().keySet().stream().findFirst().orElse("");
        if (!primaryActor.isEmpty()) {
            // Find context or entity objects to anchor scopes
            String selfDecl = program.goalBindings().elements().values().stream()
                    .map(GoalRuntimeBindingPlan.ElementTemplate::selfDeclaration)
                    .findFirst()
                    .orElse("");
            MClass selfCls = model.getClass(selfDecl);
            if (selfCls != null) {
                for (MObject obj : port.objectsOfClass(selfCls)) {
                    String semId = port.metadata(obj, "semanticId");
                    String anchorId = semId == null || semId.isBlank() ? obj.name() : semId;
                    Map<String, String> selfs = new LinkedHashMap<>();
                    for (var elem : program.goalBindings().elements().values()) {
                        if (!elem.derived()) {
                            selfs.put(elem.elementId(), anchorId);
                        }
                    }
                    String scopeId = "compact-scope:" + obj.name();
                    var member = new OccurrenceProjection.Member(
                            primaryActor,
                            anchorId + ":" + primaryActor,
                            anchorId,
                            "",
                            anchorId + ":interval",
                            "CONTEXT_VIEWPOINT");
                    scopes.add(new OccurrenceProjection.Scope(
                            scopeId,
                            "compact-observed-scope",
                            anchorId,
                            anchorId,
                            Map.of("uuid", anchorId),
                            true,
                            List.of(member),
                            selfs,
                            "",
                            Map.of("programHash", program.hash(), "cut", cut.snapshotId())));
                }
            }
        }
        scopesByRevision.put(cut.modelRevision(), List.copyOf(scopes));
        demandsByRevision.put(cut.modelRevision(), List.of());
    }

    private static Object extractPropertyScalar(Map<String, Object> values) {
        if (!(values.get("values") instanceof List<?> raw) || raw.isEmpty()) return null;
        Object first = raw.getFirst();
        if (values.get("valueTypes") instanceof List<?> types && !types.isEmpty() && types.getFirst() instanceof String javaType) {
            try {
                String targetType = switch (javaType) {
                    case "java.lang.Integer", "int", "java.lang.Long", "long", "java.lang.Short", "short" -> "Integer";
                    case "java.lang.Double", "double", "java.lang.Float", "float" -> "Real";
                    case "java.lang.Boolean", "boolean" -> "Boolean";
                    default -> "String";
                };
                Value decoded = ScalarValueCodec.decode(targetType, List.of(first), List.of(javaType));
                return switch (decoded) {
                    case IntegerValue iv -> iv.value();
                    case RealValue rv -> rv.value();
                    case BooleanValue bv -> bv.value();
                    case StringValue sv -> sv.value();
                    default -> first;
                };
            } catch (Exception ignored) {
                return first;
            }
        }
        return first;
    }

    private static Value coerceToAttributeType(org.tzi.use.uml.ocl.type.Type attrType, Value val) {
        if (val == null || val.isUndefined()) return UndefinedValue.instance;
        if (attrType.isTypeOfReal() && val instanceof IntegerValue iv) {
            return new RealValue(iv.value());
        }
        return val;
    }
}

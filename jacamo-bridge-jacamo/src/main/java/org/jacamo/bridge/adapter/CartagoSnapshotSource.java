package org.jacamo.bridge.adapter;

import cartago.AgentId;
import cartago.ArtifactId;
import cartago.ArtifactObsProperty;
import cartago.CartagoEnvironment;
import cartago.ICartagoController;
import cartago.ICartagoLogger;
import cartago.IEventFilter;
import cartago.Op;
import cartago.OpId;
import cartago.Tuple;
import cartago.WorkspaceDescriptor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Read-only official CArtAgO controller/logger adapter with UUID identity revalidation. */
public final class CartagoSnapshotSource implements SnapshotSource, ICartagoLogger {
    public static final class UnstableCutException extends IllegalStateException {
        private UnstableCutException(List<String> reasons){super("CARTAGO_BOUNDARY_CUT_NOT_STABLE:"+reasons);}
    }
    private final CartagoEnvironment environment;
    private final Set<String> configuredWorkspaces;
    private final Set<String> registeredWorkspaces = new LinkedHashSet<>();
    private final Map<String,cartago.Workspace> loggerOwners=new LinkedHashMap<>();
    private final Object registrationLock=new Object();
    // Callback registration does not take captureLock: capture may be reading
    // the artifact whose operation is invoking the logger. Adopt on next read.
    private final Map<String,cartago.Workspace> pendingLoggerOwners=new LinkedHashMap<>();
    private final Set<BridgeEntityId> knownArtifacts = new LinkedHashSet<>();
    private final Set<BridgeEntityId> pendingCreations = new LinkedHashSet<>();
    private final String sessionId;
    private final long generation;
    private final Consumer<ArtifactId> creationObserver;
    private final AtomicLong sequence = new AtomicLong();
    // Official controller reads can acquire workspace/artifact locks. Logger
    // callbacks must never wait for a monitor held across those reads.
    private final Object captureLock = new Object();
    private volatile Consumer<RuntimeEvent> observer;
    private volatile Completeness completeness = Completeness.UNAVAILABLE;
    private volatile Completeness topologyCompleteness = Completeness.UNAVAILABLE;
    private volatile Map<String,String> workspaceDeclarationAliases = Map.of();
    private volatile ProductionObservationSession capabilities;
    private volatile Set<RuntimeFactKind> observationKinds=Set.of(RuntimeFactKind.values());
    private volatile org.jacamo.bridge.contract.ObservationCaptureScope observationScope;
    private volatile boolean closed;
    private volatile boolean awaitingObservationPlan;
    void deferObservation(){synchronized(captureLock){if(observer!=null||observationScope!=null)throw new IllegalStateException("CARTAGO_OBSERVATION_ALREADY_ACTIVE");awaitingObservationPlan=true;}}
    void observationsReady(){synchronized(captureLock){awaitingObservationPlan=false;}}
    public void observationScope(org.jacamo.bridge.contract.ObservationCaptureScope scope){
        synchronized(captureLock){if(observationScope!=null)throw new IllegalStateException("CARTAGO_SCOPE_ALREADY_FROZEN");observationScope=Objects.requireNonNull(scope);}
    }
    private boolean selectedArtifact(ArtifactId artifact){var scope=observationScope;return !closed&&(scope==null||scope.includesArtifactClass(artifact.getArtifactType()));}
    private boolean selectedProperty(ArtifactObsProperty property){var scope=observationScope;return !awaitingObservationPlan&&(scope==null||scope.includesProperty(property.getName()));}
    // Board publication boundaries feed the existing official board source;
    // never infer domain goal truth from these framework identities.
    private boolean selectedBoard(ArtifactId artifact){return selectedArtifact(artifact)&&Set.of(ora4mas.nopl.GroupBoard.class.getName(),ora4mas.nopl.SchemeBoard.class.getName(),ora4mas.nopl.OrgBoard.class.getName()).contains(artifact.getArtifactType());}
    private boolean selectedOperation(ArtifactId artifact,Op operation){return !closed&&!awaitingObservationPlan&&(observationScope==null||selectedArtifact(artifact)&&(selectedBoard(artifact)||capabilities!=null&&capabilities.observesOperationBoundary(artifact,operation)));}
    public void observationKinds(Set<RuntimeFactKind> kinds){if(kinds.isEmpty())throw new IllegalArgumentException("CARTAGO_CAPTURE_KINDS_REQUIRED");observationKinds=Set.copyOf(kinds);}
    public void capabilities(ProductionObservationSession session){if(capabilities!=null)throw new IllegalStateException("CARTAGO_CAPABILITIES_ALREADY_INSTALLED");capabilities=Objects.requireNonNull(session);}

    public CartagoSnapshotSource(CartagoEnvironment environment, List<String> workspaces, String sessionId) {
        this(environment,workspaces,sessionId,null);
    }
    public CartagoSnapshotSource(CartagoEnvironment environment,List<String> workspaces,String sessionId,org.jacamo.bridge.contract.ModelSnapshot source) {
        this(environment,workspaces,sessionId,source,null);
    }
    public CartagoSnapshotSource(CartagoEnvironment environment,List<String> workspaces,String sessionId,org.jacamo.bridge.contract.ModelSnapshot source,Consumer<ArtifactId> creationObserver) {
        this.environment = java.util.Objects.requireNonNull(environment);
        this.configuredWorkspaces = new LinkedHashSet<>(workspaces == null ? List.of() : workspaces);
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
        this.creationObserver=creationObserver;
    }

    @Override public String sourceId() { return "cartago"; }

    @Override public void attach(Consumer<RuntimeEvent> observer) throws Exception {
        synchronized(captureLock) {
            if (closed||this.observer != null) throw new IllegalStateException("CARTAGO_CLOSED_OR_ALREADY_ATTACHED");
            this.observer = java.util.Objects.requireNonNull(observer);
            refreshRegistrations();
        }
    }

    @Override public SourceWatermark watermark() { return new SourceWatermark(sourceId(), sequence.get()); }

    @Override public String topologyFingerprint() {
        synchronized(captureLock) { return readTopologyFingerprint(); }
    }

    private String readTopologyFingerprint() {
        refreshRegistrations();
        var ids = new TreeSet<String>();
        var scope=observationScope;if(scope!=null)ids.add("scope:"+scope.hash());
        boolean complete = true;
        for (String workspace : registeredWorkspaces) {
            try {
                ICartagoController controller = environment.getController(workspace);
                for (AgentId id : controller.getCurrentAgents()) ids.add("a:" + id.getGlobalId());
                for (ArtifactId id : controller.getCurrentArtifacts()) if(selectedArtifact(id))ids.add("r:" + id.getId());
            } catch (Exception error) { complete = false; }
        }
        topologyCompleteness = registeredWorkspaces.isEmpty() ? Completeness.UNAVAILABLE
                : complete&&!awaitingObservationPlan ? Completeness.COMPLETE : Completeness.PARTIAL;
        // Topology failure can lower authority; a successful identity-only read
        // cannot promote a previously incomplete property/state capture.
        if(topologyCompleteness!=Completeness.COMPLETE)completeness=topologyCompleteness;
        return AdapterEvidence.digest(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
    }

    @Override public List<RuntimeFact> capture() {
        synchronized(captureLock) { return readSnapshot(); }
    }

    /** Controller reads never run while holding the logger publication monitor.
     * Only a verified stable local interval is enqueued before the callback. */
    public void publishObservation(Runnable callback) throws Exception {
        publishObservation(()->null,callback);
    }
    public void publishObservation(java.util.concurrent.Callable<Void> refreshOtherSources,Runnable callback) throws Exception {
        synchronized(captureLock) {
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
            var rejected=new ArrayList<String>();
            for(int attempt=0;attempt<16&&System.nanoTime()<deadline;attempt++) {
                long before=sequence.get();var started=Instant.now();String topology=readTopologyFingerprint();
                boolean topologyReady=topologyCompleteness==Completeness.COMPLETE;
                var facts=readSnapshot(observationKinds.contains(RuntimeFactKind.OPERATION));
                var capturedCompleteness=completeness();
                // Public board evidence must describe the same stable CArtAgO interval,
                // not the poll which preceded this controller read. Never hold the logger
                // monitor across another framework/controller call.
                refreshOtherSources.call();String afterTopology=readTopologyFingerprint();
                synchronized(this) {
                    if(sequence.get()!=before||!topology.equals(afterTopology)||!topologyReady||topologyCompleteness!=Completeness.COMPLETE||capturedCompleteness!=Completeness.COMPLETE) {
                        rejected.add("attempt="+(attempt+1)+",sequence="+before+"->"+sequence.get()+",topologyChanged="+!topology.equals(afterTopology)+",completeness="+capturedCompleteness+",topologyCompleteness="+topologyCompleteness);
                        continue;
                    }
                    var neutral=capabilities;
                    if(neutral==null||observer==null)throw new IllegalStateException("CARTAGO_OBSERVATION_OWNER_UNAVAILABLE");
                    long next=before+1;
                    var committed=neutral.commitStableCapture(facts.stream().filter(f->observationKinds.contains(f.kind())||f.kind()==RuntimeFactKind.OPERATION).toList(),before,next);var ended=Instant.now();var mark=new SourceWatermark(sourceId(),next);
                    var metadata=new LinkedHashMap<String,Object>(Map.of("cutPolicy","LOCAL_SOURCE_STABLE_INTERVAL","topologyStart",topology,"topologyEnd",afterTopology,"globalAtomicity","UNSUPPORTED","capturedFactKinds",observationKinds.stream().map(Enum::name).sorted().toList(),"supplementalFactKinds",observationKinds.contains(RuntimeFactKind.OPERATION)?List.of():List.of("OPERATION")));
                    var scope=observationScope;if(scope!=null){metadata.put("captureScope",scope.evidence());metadata.put("captureScopeHash",scope.hash());}
                    var cut=new org.jacamo.bridge.contract.RuntimeSnapshot("cartago-cut:"+sessionId+":"+next,System.getProperty("jacamo.bridge.modelRevision"),started,ended,Map.of(sourceId(),mark),Map.of(sourceId(),mark),attempt+1,committed,Map.of(sourceId(),Completeness.COMPLETE),AdapterEvidence.digest(org.jacamo.bridge.contract.CanonicalJson.encode(committed.stream().map(f->List.of(f.id().canonical(),f.values())).toList())),metadata);
                    var values=org.jacamo.bridge.contract.CartagoSourceCut.payload(cut);
                    emit(RuntimeEventKind.CHANGED,new BridgeEntityId("cartago","environment","source-cut",environment.getId().toString(),"capture",sessionId),RuntimeFactKind.RUNTIME_EVENT,values);
                    callback.run();return;
                }
            }
            throw new UnstableCutException(rejected);
        }
    }

    private List<RuntimeFact> readSnapshot() {
        return readSnapshot(true);
    }
    private List<RuntimeFact> readSnapshot(boolean includeOperations) {
        refreshRegistrations();
        var facts = new ArrayList<RuntimeFact>();
        boolean complete = !registeredWorkspaces.isEmpty();
        try {
            var neutral=capabilities;
            var snapshot = new OfficialCartagoAdapter().capture(environment,(artifact,operation)->
                    !awaitingObservationPlan&&((observationScope==null||observationKinds.contains(RuntimeFactKind.OPERATION))&&includeOperations
                    ||neutral!=null&&neutral.observesOperation(artifact,operation)),this::selectedArtifact,(artifact,property)->selectedProperty(property));
            String environmentSemanticId = snapshot.metadata().semanticId();
            String environmentId = snapshot.environmentId();
            var artifactTypes = snapshot.artifactTypes().stream().collect(java.util.stream.Collectors.toMap(
                    value -> value.metadata().semanticId(), value -> value));
            var workspaceNames = snapshot.workspaces().stream().collect(java.util.stream.Collectors.toMap(
                    value -> value.metadata().semanticId(), value -> value.fullName()));
            var declaredWorkspaceIds = new LinkedHashMap<String,String>();

            for (var workspace : snapshot.workspaces()) {
                var values = values("UPSERT_CARTAGO_WORKSPACE", workspace.metadata().semanticId());
                values.put("fullName", workspace.fullName());
                values.put("name", workspace.name());
                values.put("uuid", workspace.uuid());
                values.put("parentSemanticId", workspace.parentSemanticId());
                values.put("environmentSemanticId", workspace.environmentSemanticId());
                values.put("local", workspace.local());
                values.put("protocol", workspace.protocol());
                values.put("remotePath", workspace.remotePath());
                values.put("address", workspace.address());
                values.put("environmentName", snapshot.name());
                values.put("environmentId", environmentId);
                values.put("environmentVersion", snapshot.version());
                values.put("defaultInfrastructureLayer", snapshot.defaultInfrastructureLayer());
                var declarations=BridgeRuntimeRegistry.projectDeclarations();
                if(declarations.isPresent()) for(var declaration:declarations.get().workspaceDeclarations()) {
                    // JaCaMo.platform.Cartago.start creates each JCM workspace as an
                    // exact child of the actual root. resolveWSP accepts full paths,
                    // so a bare declaration name cannot prove runtime identity.
                    var declarationId=BridgeEntityId.parse(declaration.metadata().semanticId());
                    var descriptor=declarationId.authority().equals("cartago")
                            ? java.util.Optional.of(environment.getRootWSP())
                            : environment.getRootWSP().getWorkspace().getChildWSP(declaration.name());
                    if(descriptor.isPresent()) {
                        var resolved=descriptor.get().getId();
                        if(resolved.getUUID().toString().equals(workspace.uuid()) && resolved.getFullName().equals(workspace.fullName())) {
                            if(values.putIfAbsent("workspaceDeclarationSemanticId",declaration.metadata().semanticId())!=null)
                                throw new IllegalStateException("CARTAGO_WORKSPACE_DECLARATION_AMBIGUOUS");
                            declaredWorkspaceIds.put(declaration.metadata().semanticId(),workspace.metadata().semanticId());
                        }
                    }
                }
                facts.add(faithful(new BridgeEntityId("cartago", "environment", "workspace", environmentId,
                        workspace.fullName(), workspace.uuid()), RuntimeFactKind.WORKSPACE, values));
            }
            for (var agent : snapshot.agents()) {
                String workspaceFullName = requiredWorkspaceName(workspaceNames, agent.workspaceSemanticId());
                var values = values("UPSERT_CARTAGO_AGENT_IDENTITY", agent.metadata().semanticId());
                values.put("globalId", agent.globalId());
                values.put("localId", agent.localId());
                values.put("name", agent.name());
                values.put("role", agent.role());
                values.put("workspaceSemanticId", agent.workspaceSemanticId());
                BridgeRuntimeRegistry.agentDeclarationId(agent.name()).ifPresent(id -> values.put("agentDeclarationSemanticId",id));
                BridgeRuntimeRegistry.agentIdentity(agent.name()).ifPresent(id -> values.put("agentRuntimeSemanticId",id.canonical()));
                facts.add(faithful(agentId(workspaceFullName, agent.globalId(), agent.localId()),
                        RuntimeFactKind.AGENT, values));
            }
            for (var artifact : snapshot.artifacts()) {
                String workspaceFullName = requiredWorkspaceName(workspaceNames, artifact.workspaceSemanticId());
                var type = artifactTypes.get(artifact.artifactTypeSemanticId());
                if (type == null) throw new IllegalStateException("CARTAGO_ARTIFACT_TYPE_MISSING: "
                        + artifact.artifactTypeSemanticId());
                var values = values("UPSERT_CARTAGO_ARTIFACT", artifact.metadata().semanticId());
                values.put("name", artifact.name());
                values.put("uuid", artifact.uuid());
                values.put("artifactTypeSemanticId", artifact.artifactTypeSemanticId());
                values.put("artifactTypeJavaClassName", type.javaClassName());
                values.put("artifactTypeOrigin", OfficialCartagoAdapter.typeOrigin(type.javaClassName()));
                values.put("artifactTypeClassLoaderIdentity", type.classLoaderIdentity());
                values.put("workspaceSemanticId", artifact.workspaceSemanticId());
                values.put("creatorAgentSemanticId", artifact.creatorAgentSemanticId());
                var exactInfo=environment.getController(workspaceFullName).getArtifactInfo(artifact.name());
                values.put("artifactProviderObservation",awaitingObservationPlan?Map.of("version","1.0.0","status","UNAVAILABLE","diagnostic","AWAITING_PLAN"):exactInfo!=null&&artifact.uuid().equals(exactInfo.getId().getId().toString())
                        ?CartagoArtifactProviderObservation.capture(environment,exactInfo.getId())
                        :Map.of("version","1.0.0","status","UNAVAILABLE","diagnostic","ARTIFACT_REPLACED"));
                if(exactInfo!=null&&artifact.uuid().equals(exactInfo.getId().getId().toString()))captureCapabilities(values,exactInfo.getId(),"SNAPSHOT",0,null,null);
                var declarations=BridgeRuntimeRegistry.projectDeclarations();
                if(declarations.isPresent()) for(var declaration:declarations.get().artifactDeclarations()) {
                        // OfficialCartagoAdapter has already revalidated this UUID
                        // against the controller's ArtifactInfo. Reuse that cut,
                        // including its exact officially resolved workspace identity.
                        var workspaceDeclarations=declarations.get().workspaceDeclarations().stream()
                                .filter(w->w.name().equals(declaration.workspace()))
                                .filter(w->BridgeEntityId.parse(w.metadata().semanticId()).authority().equals("jacamo")).toList();
                        if(workspaceDeclarations.size()>1)throw new IllegalStateException("CARTAGO_ARTIFACT_WORKSPACE_DECLARATION_AMBIGUOUS");
                        String workspaceDeclaration=workspaceDeclarations.isEmpty()?"":workspaceDeclarations.getFirst().metadata().semanticId();
                        if(artifact.workspaceSemanticId().equals(declaredWorkspaceIds.get(workspaceDeclaration))
                                && declaration.name().equals(artifact.name()) && declaration.javaClass().equals(type.javaClassName())) {
                            if(values.putIfAbsent("artifactDeclarationSemanticId",declaration.metadata().semanticId())!=null)
                                throw new IllegalStateException("CARTAGO_ARTIFACT_DECLARATION_AMBIGUOUS");
                        }
                }
                facts.add(faithful(artifactId(workspaceFullName, artifact.name(), artifact.uuid()),
                        RuntimeFactKind.ARTIFACT, values));
                // A controller query can see an artifact while its creation
                // callback is still running. Reading it is not a published birth;
                // only subscription baseline and CREATED admit later callbacks.
            }
            for (var property : snapshot.propertySnapshots()) {
                var values = values("UPSERT_CARTAGO_PROPERTY_SNAPSHOT", property.metadata().semanticId());
                values.put("artifactSemanticId", property.artifactSemanticId());
                values.put("propertyId", property.propertyId());
                values.put("name", property.name());
                values.put("values", property.values());
                values.put("valueTypes", property.valueTypes());
                values.put("annotations", property.annotations());
                facts.add(faithful(new BridgeEntityId("cartago", "environment", "observable-property-snapshot",
                        property.artifactSemanticId(), property.propertyId(), "snapshot"),
                        RuntimeFactKind.PROPERTY, values));
            }
            for (var operation : snapshot.operations()) {
                var values=values("UPSERT_CARTAGO_OPERATION",operation.metadata().semanticId());
                values.put("operationDescriptor",org.jacamo.bridge.contract.semantic.SemanticContractCodec.operationToTree(operation));
                var backings=snapshot.backingOperations().stream().filter(b->b.operationDescriptorId().equals(operation.metadata().semanticId())).toList();
                if(backings.size()==1) values.put("backingOperation",org.jacamo.bridge.contract.semantic.SemanticContractCodec.backingOperationToTree(backings.getFirst()));
                facts.add(faithful(new BridgeEntityId("cartago","environment","operation-descriptor",operation.artifactSemanticId(),operation.keyId(),"snapshot"),RuntimeFactKind.OPERATION,values));
            }
            for(var focus:snapshot.focuses()) {
                if(observationScope!=null&&!observationKinds.contains(RuntimeFactKind.RELATION_STATE))continue;
                var payload=new LinkedHashMap<>(focusPayload(focus.agentSemanticId(),focus.artifactSemanticId(),true));
                var artifact=snapshot.artifacts().stream().filter(a->a.metadata().semanticId().equals(focus.artifactSemanticId())).findFirst().orElseThrow();
                String fqcn=artifactTypes.get(artifact.artifactTypeSemanticId()).javaClassName();
                payload.put("artifactTypeJavaClassName",fqcn); payload.put("artifactTypeOrigin",OfficialCartagoAdapter.typeOrigin(fqcn));
                facts.add(faithful(focusId(focus.agentSemanticId(),focus.artifactSemanticId()),RuntimeFactKind.RELATION_STATE,payload));
            }
        } catch (Exception error) {
            java.util.logging.Logger.getLogger(CartagoSnapshotSource.class.getName()).log(java.util.logging.Level.FINE,"BRIDGE_SNAPSHOT_READ_FAILED",error);
            complete = false;
        }
        completeness = registeredWorkspaces.isEmpty() ? Completeness.UNAVAILABLE
                : complete&&!awaitingObservationPlan ? Completeness.COMPLETE : Completeness.PARTIAL;
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }

    private RuntimeFact faithful(BridgeEntityId id, RuntimeFactKind kind, Map<String, Object> values) {
        return new RuntimeFact(id, kind, values, List.of(), ProjectionStatus.MATERIALIZED_FAITHFULLY,
                Completeness.COMPLETE, List.of());
    }

    private LinkedHashMap<String, Object> values(String normalizedEventKind, String semanticId) {
        var values = new LinkedHashMap<String, Object>();
        values.put("normalizedEventKind", normalizedEventKind);
        values.put("semanticId", semanticId);
        return values;
    }

    private static String requiredWorkspaceName(Map<String, String> workspaceNames, String semanticId) {
        String value = workspaceNames.get(semanticId);
        if (value == null || value.isBlank())
            throw new IllegalStateException("CARTAGO_WORKSPACE_IDENTITY_MISSING: " + semanticId);
        return value;
    }

    @Override public Map<String,Object> captureMetadata() {
        var metadata=new LinkedHashMap<String,Object>(Map.of("operationLifecycleCoverage","PARTIAL","suspendResumeObservation","UNSUPPORTED_CARTAGO_3_1_AWAIT_NO_LOGGER_HOOKS",
                "invocationBoundary","ICARTAGOLOGGER_OBSERVED_PREFIX","globalAtomicity","UNSUPPORTED"));
        var scope=observationScope;if(scope!=null){metadata.put("captureScope",scope.evidence());metadata.put("captureScopeHash",scope.hash());metadata.put("inventoryAcquisition","OFFICIAL_CONTROLLER_ENUMERATION_THEN_SELECTED_ARTIFACT_INFO_AND_VALUES");}
        if(awaitingObservationPlan)metadata.put("observationPolicy","AWAITING_PLAN_METADATA_ONLY_NOT_COMPLETE_POPULATION");
        return Map.copyOf(metadata);
    }
    @Override public synchronized Completeness completeness() {
        if(closed)return Completeness.UNAVAILABLE;
        if(awaitingObservationPlan)return Completeness.PARTIAL;
        return pendingCreations.isEmpty() ? completeness : Completeness.PARTIAL;
    }

    @Override public void close() throws Exception {
        synchronized(captureLock) {closed=true;closeLoggers();}
    }

    private void closeLoggers() throws Exception {
        observer = null;
        synchronized(registrationLock){adoptWorkspaceLoggers();}
        Exception failure = null;
        for (String workspace : List.copyOf(registeredWorkspaces)) {
            try {var owned=loggerOwners.get(workspace);if(owned!=null)owned.unregisterLogger(this);else environment.unregisterLogger(workspace,this);}
            catch (Exception error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        registeredWorkspaces.clear();
        loggerOwners.clear();
        if (failure != null) throw failure;
    }

    private void refreshRegistrations() {
        synchronized(registrationLock){refreshRegistrationsLocked();}
    }
    private void adoptWorkspaceLoggers() {
        pendingLoggerOwners.forEach((name,owner)->{var old=loggerOwners.put(name,owner);if(old!=null&&old!=owner)old.unregisterLogger(this);registeredWorkspaces.add(name);});
        pendingLoggerOwners.clear();
    }
    private void refreshRegistrationsLocked() {
        if(closed)throw new IllegalStateException("CARTAGO_SOURCE_CLOSED");
        adoptWorkspaceLoggers();
        // A path can disappear or be reused for a different workspace UUID.
        // Unregister from the actual owner, not a name resolved after retirement.
        for(var entry:List.copyOf(loggerOwners.entrySet())) {
            cartago.Workspace current=null;try{current=environment.resolveWSP(entry.getKey()).getWorkspace();}catch(Exception absent){/* no current path owner */}
            if(current!=entry.getValue()) {
                entry.getValue().unregisterLogger(this);loggerOwners.remove(entry.getKey());registeredWorkspaces.remove(entry.getKey());
                synchronized(this) {for(var id:List.copyOf(knownArtifacts))if(id.scope().equals(entry.getKey())) {
                    knownArtifacts.remove(id);pendingCreations.remove(id);
                }pendingCreations.removeIf(id->id.scope().equals(entry.getKey()));}
            }
        }
        var aliases=new LinkedHashMap<String,String>();
        BridgeRuntimeRegistry.projectDeclarations().ifPresent(project -> {
            var root=environment.getRootWSP();
            if(root==null || root.getWorkspace()==null) return;
            for(var declaration:project.workspaceDeclarations()) {
                var id=BridgeEntityId.parse(declaration.metadata().semanticId());
                var descriptor=id.authority().equals("cartago") ? java.util.Optional.of(root)
                        : root.getWorkspace().getChildWSP(declaration.name());
                descriptor.ifPresent(value -> aliases.put(workspaceSemanticId(value.getId()),declaration.metadata().semanticId()));
            }
        });
        workspaceDeclarationAliases=Map.copyOf(aliases);
        var discovered = new LinkedHashSet<>(configuredWorkspaces);
        try { collect(environment.getRootWSP(), discovered); }
        catch (RuntimeException ignored) { /* readiness remains explicit in completeness */ }
        for (String workspace : discovered) {
            if (workspace == null || workspace.isBlank() || registeredWorkspaces.contains(workspace)) continue;
            try {
                var owner=environment.resolveWSP(workspace).getWorkspace();if(owner==null)continue;owner.registerLogger(this);loggerOwners.put(workspace,owner);
                registeredWorkspaces.add(workspace);
                for (ArtifactId artifact : environment.getController(workspace).getCurrentArtifacts())
                    synchronized(this) { if (!pendingCreations.contains(artifactId(artifact))) knownArtifacts.add(artifactId(artifact)); }
            } catch (Exception ignored) { /* built-in Cartago may not have created it yet */ }
        }
    }

    private void collect(WorkspaceDescriptor descriptor, Set<String> names) {
        if (descriptor == null) return;
        if (descriptor.getId() != null) names.add(descriptor.getId().getFullName());
        if (descriptor.getWorkspace() != null)
            for (WorkspaceDescriptor child : descriptor.getWorkspace().getChildWSPs()) collect(child, names);
    }

    private String safe(Object value) { return value == null ? "" : value.toString(); }
    private BridgeEntityId artifactId(ArtifactId id) {
        return artifactId(id.getWorkspaceId().getFullName(), id.getName(), id.getId().toString());
    }
    static BridgeEntityId artifactId(String workspaceFullName, String name, String uuid) {
        return new BridgeEntityId("cartago", "environment", "artifact", workspaceFullName, name, uuid);
    }
    private BridgeEntityId agentId(AgentId id) {
        return agentId(id.getWorkspaceId().getFullName(), id.getGlobalId(), id.getLocalId());
    }
    static BridgeEntityId agentId(String workspaceFullName, String globalId, int localId) {
        return new BridgeEntityId("cartago", "environment", "agent", workspaceFullName, globalId,
                Integer.toString(localId));
    }

    private synchronized void event(RuntimeEventKind kind, ArtifactId artifact, AgentId agent, Map<String,Object> values) {
        if(artifact!=null&&!selectedArtifact(artifact))return;
        BridgeEntityId id = artifact != null ? artifactId(artifact) : agentId(agent);
        emit(kind,id,artifact != null ? RuntimeFactKind.ARTIFACT : RuntimeFactKind.AGENT,values,artifact);
    }
    private synchronized void emit(RuntimeEventKind kind,BridgeEntityId id,RuntimeFactKind factKind,Map<String,Object> values) {
        emit(kind,id,factKind,values,null);
    }
    private synchronized void emit(RuntimeEventKind kind,BridgeEntityId id,RuntimeFactKind factKind,Map<String,Object> values,ArtifactId artifact) {
        Consumer<RuntimeEvent> sink=observer;
        if(sink==null||awaitingObservationPlan) return;
        var scope=observationScope;if(scope!=null&&!org.jacamo.bridge.contract.CartagoSourceCut.KIND.equals(values.get("normalizedEventKind"))){values=new LinkedHashMap<>(values);values.put("producerCaptureScopeHash",scope.hash());}
        long next = sequence.incrementAndGet();
        String correlation = values.get("opId") instanceof String value ? value : "";
        if(!correlation.isBlank()) {
            values=new LinkedHashMap<>(values);values.put("invocationBoundary",Map.of("version","1.0","authority","ICARTAGOLOGGER_OBSERVED_PREFIX","sourceId",sourceId(),"artifactUuid",id.incarnation(),"previousWatermark",next-1,"globalAtomicity","UNSUPPORTED"));
        }
        var event=new RuntimeEvent(sessionId + ":cartago:" + next, sessionId, generation,
                System.getProperty("jacamo.bridge.modelRevision", "unnegotiated"), "cartago", sourceId(), next,
                Instant.now(), kind, factKind,
                values.containsKey("normalizedEventKind")&&!org.jacamo.bridge.contract.CartagoSourceCut.KIND.equals(values.get("normalizedEventKind")) ? ProjectionStatus.MATERIALIZED_FAITHFULLY : ProjectionStatus.EVIDENCE_ONLY,
                id, null, correlation, "", Map.of(), values,
                new SourceWatermark(sourceId(), next), Completeness.COMPLETE, List.of());

        var neutral=capabilities;
        if(neutral!=null)try {
            neutral.observe(event);
            var enriched=new LinkedHashMap<>(event.after());neutral.history(enriched,kind==RuntimeEventKind.DISPOSED?null:artifact,next);
            event=new RuntimeEvent(event.eventId(),event.sessionId(),event.generation(),event.modelRevision(),event.subsystem(),event.sourceId(),event.sourceSequence(),event.observedAt(),event.kind(),event.factKind(),event.projectionStatus(),event.entityId(),event.relationId(),event.correlationId(),event.causationId(),event.before(),enriched,event.watermark(),event.completeness(),event.evidence());
        }catch(RuntimeException failure){
            // Logger exceptions make CArtAgO remove the listener and may alter the domain action.
            // Keep the allocated sequence, publish the observation failure, and require resync.
            java.util.logging.Logger.getLogger(CartagoSnapshotSource.class.getName()).log(java.util.logging.Level.WARNING,"NEUTRAL_CARTAGO_CALLBACK_FAILED:"+next,failure);
            event=new RuntimeEvent(event.eventId(),event.sessionId(),event.generation(),event.modelRevision(),event.subsystem(),event.sourceId(),event.sourceSequence(),event.observedAt(),RuntimeEventKind.GAP,null,ProjectionStatus.EVIDENCE_ONLY,event.entityId(),null,event.correlationId(),event.causationId(),Map.of(),Map.of("diagnostic","NEUTRAL_CARTAGO_CALLBACK_FAILED:"+failure.getMessage()),event.watermark(),Completeness.PARTIAL,List.of());
        }
        sink.accept(event);


    }




    private String workspaceSemanticId(cartago.WorkspaceId id) {
        return "cartago:workspace:"+environment.getId()+":"+id.getFullName()+":"+id.getUUID();
    }
    private Map<String,Object> workspacePayload(cartago.WorkspaceId id) {
        String semanticId=workspaceSemanticId(id);
        var payload=values("UPSERT_CARTAGO_WORKSPACE",semanticId);
        payload.put("fullName",id.getFullName()); payload.put("name",id.getName()); payload.put("uuid",id.getUUID().toString());
        payload.put("environmentId",environment.getId().toString()); payload.put("environmentSemanticId","cartago:environment:"+environment.getId());
        String declaration=workspaceDeclarationAliases.get(semanticId);
        if(declaration!=null) payload.put("workspaceDeclarationSemanticId",declaration);
        return payload;
    }
    private Map<String,Object> agentPayload(AgentId agent,String kind) {
        var payload=values(kind,OfficialCartagoAdapter.agentId(environment.getId().toString(),agent));
        payload.put("globalId",agent.getGlobalId()); payload.put("localId",agent.getLocalId());
        payload.put("name",agent.getAgentName()); payload.put("role",safe(agent.getAgentRole()));
        payload.put("workspaceSemanticId",workspaceSemanticId(agent.getWorkspaceId()));
        BridgeRuntimeRegistry.agentDeclarationId(agent.getAgentName()).ifPresent(id->payload.put("agentDeclarationSemanticId",id));
        BridgeRuntimeRegistry.agentIdentity(agent.getAgentName()).ifPresent(id->payload.put("agentRuntimeSemanticId",id.canonical()));
        return payload;
    }
    private static BridgeEntityId focusId(String agent,String artifact) {
        return new BridgeEntityId("cartago","environment","focus",artifact,agent,"relation");
    }
    private static Map<String,Object> focusPayload(String agent,String artifact,boolean focused) {
        return Map.of("normalizedEventKind","SET_CARTAGO_FOCUS","agentSemanticId",agent,"artifactSemanticId",artifact,"focused",focused);
    }

    @Override public void opRequested(long time, AgentId agent, ArtifactId artifact, Op op) { if(!selectedOperation(artifact,op))return;event(RuntimeEventKind.STARTED, artifact, agent,
            Map.of("phase", "requested", "operation", op.getName(), "arity", op.getParamValues().length)); }
    @Override public void opStarted(long time, OpId id, ArtifactId artifact, Op op) { if(!selectedOperation(artifact,op))return;event(RuntimeEventKind.STARTED, artifact, null,
            operationObservation(time,artifact,id,op,"started")); }
    @Override public void opSuspended(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.CHANGED, artifact, null,
            Map.of("phase", "suspended", "opId", id.toString())); }
    @Override public void opResumed(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.CHANGED, artifact, null,
            Map.of("phase", "resumed", "opId", id.toString())); }
    @Override public void opCompleted(long time, OpId id, ArtifactId artifact, Op op) { observeCreatedWorkspace(artifact,op);if(!selectedOperation(artifact,op))return;event(RuntimeEventKind.SUCCEEDED, artifact, null,
            operationObservation(time,artifact,id,op,"completed")); }
    private void observeCreatedWorkspace(ArtifactId artifact,Op op) {
        if(closed||artifact==null||!"cartago.WorkspaceArtifact".equals(artifact.getArtifactType())
                ||!Set.of("createWorkspace","createWorkspaceWithTopology").contains(op.getName())
                ||op.getParamValues().length==0||!(op.getParamValues()[0] instanceof String name))return;
        try {
            String path=artifact.getWorkspaceId().getFullName()+"/"+name;
            var owner=environment.resolveWSP(path).getWorkspace();
            if(owner==null)throw new IllegalStateException("LOCAL_WORKSPACE_OWNER_UNAVAILABLE");
            synchronized(registrationLock) {
                if(closed)return;
                if(loggerOwners.get(path)==owner||pendingLoggerOwners.get(path)==owner)return;
                // opCompleted is delivered before the creating agent's success
                // feedback. Subscribe before it can join/create domain artifacts.
                owner.registerLogger(this);pendingLoggerOwners.put(path,owner);
            }
        }catch(Exception unavailable){gap("CARTAGO_DYNAMIC_WORKSPACE_LOGGER_UNAVAILABLE:"+unavailable.getClass().getSimpleName());}
    }
    /** Jason/JaCa callbacks may complete workspace management outside an
     * observable WorkspaceArtifact operation. Attach before that agent's TS
     * consumes success feedback, without taking captureLock or reading state. */
    void observeWorkspaceTopology() {
        if(closed)return;
        try {
            var names=new LinkedHashSet<String>();collect(environment.getRootWSP(),names);
            if(names.size()>4096)throw new IllegalStateException("WORKSPACE_OBSERVATION_BOUND");
            for(String name:names) {
                var owner=environment.resolveWSP(name).getWorkspace();if(owner==null)continue;
                synchronized(registrationLock) {
                    if(closed)return;
                    if(loggerOwners.get(name)==owner||pendingLoggerOwners.get(name)==owner)continue;
                    owner.registerLogger(this);pendingLoggerOwners.put(name,owner);
                }
            }
        }catch(Exception unavailable){gap("CARTAGO_DYNAMIC_WORKSPACE_LOGGER_UNAVAILABLE:"+unavailable.getClass().getSimpleName());}
    }
    @Override public void opFailed(long time, OpId id, ArtifactId artifact, Op op, String message, Tuple descriptor) { if(!selectedOperation(artifact,op))return;event(RuntimeEventKind.FAILED,
            artifact, null, failedOperationPayload(time,artifact,id,op,message,descriptor)); }
    private Map<String,Object> operationObservation(long time,ArtifactId artifact,OpId id,Op op,String phase) {
        var values=new LinkedHashMap<>(operationPayload(artifact,id,op,phase));
        captureCapabilities(values,artifact,phase.equals("started")?"OP_STARTED":phase.equals("completed")?"OP_SUCCEEDED":"OP_FAILED",time,id,op);

        return values;
    }
    private Map<String,Object> failedOperationPayload(long time,ArtifactId artifact,OpId id,Op op,String message,Tuple descriptor) {
        var values=new java.util.LinkedHashMap<>(operationObservation(time,artifact,id,op,"failed"));values.put("message",safe(message));values.put("descriptor",safe(descriptor));return values;
    }
    private Map<String,Object> operationPayload(ArtifactId artifact,OpId id,Op op,String phase) {
        var payload=new java.util.LinkedHashMap<String,Object>();
        payload.put("operationLifecycleCoverage","PARTIAL");payload.put("suspendResumeObservation","UNSUPPORTED_CARTAGO_3_1_AWAIT_NO_LOGGER_HOOKS");
        payload.put("phase",phase);payload.put("operation",op.getName());payload.put("opId",id.toString());
        payload.put("artifactSemanticId",OfficialCartagoAdapter.artifactId(environment.getId().toString(),artifact));
        AgentId performer=id.getAgentBodyId();
        if(performer!=null && artifact.equals(id.getArtifactId())) {
            payload.put("performerSemanticId",OfficialCartagoAdapter.agentId(environment.getId().toString(),performer));
            payload.put("performerName",performer.getAgentName());
            payload.put("performerAuthority","CARTAGO_OPID_AGENT_BODY_ID");
        } else payload.put("performerDiagnostic","EXACT_OPERATION_PERFORMER_UNAVAILABLE");
        payload.put("argumentValues",Arrays.stream(op.getParamValues()).map(v->v==null?"":v.toString()).toList());
        payload.put("argumentTypes",Arrays.stream(op.getParamValues()).map(v->v==null?"NULL":v.getClass().getName()).toList());
        payload.put("argumentObservationVersion",CartagoArgumentObservation.VERSION);
        payload.put("typedArgumentValues",Arrays.stream(op.getParamValues()).map(v->CartagoArgumentObservation.value(v,environment.getId().toString())).toList());

        try {
            var info=environment.getController(artifact.getWorkspaceId().getFullName()).getArtifactInfo(artifact.getName());
            if(info==null || !artifact.equals(info.getId())) throw new IllegalStateException("ARTIFACT_INCARNATION_CHANGED");
            var matches=info.getOperations().stream().filter(d->!d.isDynamic() && !d.isInternalOp()
                    && d.getOp() instanceof cartago.ArtifactOpMethod && d.getOp().getName().equals(op.getName())
                    && d.getOp().getNumParameters()==op.getParamValues().length).toList();
            if(matches.size()!=1) throw new IllegalStateException("OPERATION_DESCRIPTOR_NOT_UNIQUE");
            var descriptor=matches.getFirst();var method=((cartago.ArtifactOpMethod)descriptor.getOp()).getMethod();
            if(method.isVarArgs()) throw new IllegalStateException("OPERATION_VARARGS_UNSUPPORTED");
            payload.put("operationDescriptorId",OfficialCartagoAdapter.operationId((String)payload.get("artifactSemanticId"),descriptor.getKeyId()));
            payload.put("operationSignature",List.of(method.getDeclaringClass().getName(),method.getName(),
                    Arrays.stream(method.getParameterTypes()).map(Class::getName).toList(),method.getReturnType().getName()));
        } catch(Exception unavailable) {payload.put("operationEvidenceDiagnostic",unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());}
        return payload;
    }


    /** Neutral providers are driven exclusively by the frozen observation plan. */
    private void captureCapabilities(Map<String,Object> payload,ArtifactId artifact,String origin,long time,OpId invocation,Op operation) {
        if(awaitingObservationPlan)return;
        var neutral=capabilities;
        if(neutral!=null){neutral.capture(payload,artifact,origin,time,invocation,operation,sequence.get());if(origin.equals("SNAPSHOT"))neutral.history(payload,artifact,sequence.get());}
    }
    @Override public void newPercept(long time, ArtifactId artifact, Tuple signal, ArtifactObsProperty[] added,
                                     ArtifactObsProperty[] removed, ArtifactObsProperty[] changed) {
        if(awaitingObservationPlan)return;
        if(!selectedArtifact(artifact))return;
        if(added!=null)added=Arrays.stream(added).filter(this::selectedProperty).toArray(ArtifactObsProperty[]::new);
        if(removed!=null)removed=Arrays.stream(removed).filter(this::selectedProperty).toArray(ArtifactObsProperty[]::new);
        if(changed!=null)changed=Arrays.stream(changed).filter(this::selectedProperty).toArray(ArtifactObsProperty[]::new);
        if(observationScope!=null&&(added==null||added.length==0)&&(removed==null||removed.length==0)&&(changed==null||changed.length==0)
                &&!selectedBoard(artifact)&&(capabilities==null||!capabilities.observesPublicationBoundary(artifact)))return;
        synchronized (this) {
            if (!knownArtifacts.contains(artifactId(artifact))) {
                // Official init emits property callbacks BEFORE artifactCreated. Do not
                // expose half-initialized state: creation's revalidated controller snapshot
                // commits the final initial properties at the real lifecycle boundary.
                pendingCreations.add(artifactId(artifact));
                if (pendingCreations.size() > 8192) { pendingCreations.clear(); gap("CARTAGO_CREATION_BUFFER_OVERFLOW"); }
                return;
            }
        }
        if ((added == null || added.length == 0) && (removed == null || removed.length == 0)
                && (changed == null || changed.length == 0)) {
            var payload=new LinkedHashMap<String,Object>();payload.put("signal",safe(signal));captureCapabilities(payload,artifact,"PROPERTY_PUBLICATION",time,null,null);
            event(RuntimeEventKind.CHANGED, artifact, null, payload); return;
        }
        String parent = OfficialCartagoAdapter.artifactId(String.valueOf(environment.getId()), artifact);
        List<Map<String, Object>> properties = new ArrayList<>();
        if (added != null) for (ArtifactObsProperty property : added) properties.add(propertyPayload(parent, property));
        if (changed != null) for (ArtifactObsProperty property : changed) properties.add(propertyPayload(parent, property));
        List<String> removedIds = removed == null ? List.of() : Arrays.stream(removed)
                .map(property -> OfficialCartagoAdapter.propertyId(parent, property.getFullId())).toList();
        var payload = values("APPLY_CARTAGO_PROPERTY_DELTA", parent);
        payload.put("properties", properties); payload.put("removedPropertySemanticIds", removedIds);
        payload.put("signal", safe(signal));
        captureCapabilities(payload,artifact,"PROPERTY_PUBLICATION",time,null,null);

        event(RuntimeEventKind.CHANGED, artifact, null, payload);
    }
    static Map<String, Object> propertyPayload(String artifact, ArtifactObsProperty property) {
        return Map.of("semanticId", OfficialCartagoAdapter.propertyId(artifact, property.getFullId()),
                "artifactSemanticId", artifact, "propertyId", property.getFullId(), "name", property.getName(),
                "values", Arrays.stream(property.getValues()).map(value -> value == null ? "" : value.toString()).toList(),
                "valueTypes", Arrays.stream(property.getValues()).map(value -> value == null ? "NULL" : value.getClass().getName()).toList(),
                "annotations", property.getAnnots() == null ? List.of() : property.getAnnots().stream().map(Object::toString).toList());
    }
    @Override public void artifactCreated(long time, ArtifactId artifact, AgentId creator) {
        try {observeArtifactCreated(time,artifact,creator);}
        catch(RuntimeException unavailable){gap("CARTAGO_CREATION_OBSERVATION_FAILED:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());}
    }
    private void observeArtifactCreated(long time,ArtifactId artifact,AgentId creator) {
        if(closed)return;
        // Pinned Workspace.makeArtifact invokes this after init and before
        // returning the new ID. Subscription is passive and must not fail the
        // domain action when unavailable.
        if(creationObserver!=null)try{creationObserver.accept(artifact);}catch(RuntimeException unavailable){gap("NPL_CREATION_SUBSCRIPTION_UNAVAILABLE:"+unavailable.getClass().getSimpleName());}
        if(!selectedArtifact(artifact))return;
        String environmentId = String.valueOf(environment.getId());
        var payload = values("UPSERT_CARTAGO_ARTIFACT", OfficialCartagoAdapter.artifactId(environmentId, artifact));
        payload.put("name", artifact.getName()); payload.put("uuid", artifact.getId().toString());
        payload.put("workspaceSemanticId", "cartago:workspace:" + environmentId + ":"
                + artifact.getWorkspaceId().getFullName() + ":" + artifact.getWorkspaceId().getUUID());
        payload.put("artifactTypeSemanticId", OfficialCartagoAdapter.artifactTypeId(environmentId, artifact.getArtifactType()));
        payload.put("artifactTypeJavaClassName", artifact.getArtifactType());
        payload.put("artifactTypeOrigin", OfficialCartagoAdapter.typeOrigin(artifact.getArtifactType()));
        String loader = "";
        try {
            Class<?> type = Class.forName(artifact.getArtifactType(), false, Thread.currentThread().getContextClassLoader());
            loader = type.getProtectionDomain().getCodeSource() == null ? String.valueOf(type.getClassLoader())
                    : String.valueOf(type.getProtectionDomain().getCodeSource().getLocation());
        } catch (ClassNotFoundException ignored) { /* Class-loader identity is unavailable, not inferred. */ }
        payload.put("artifactTypeClassLoaderIdentity", loader);
        payload.put("creatorAgentSemanticId", creator == null ? "" : OfficialCartagoAdapter.agentId(environmentId, creator));
        payload.put("artifactProviderObservation",awaitingObservationPlan?Map.of("version","1.0.0","status","UNAVAILABLE","diagnostic","AWAITING_PLAN"):CartagoArtifactProviderObservation.capture(environment,artifact));
        captureCapabilities(payload,artifact,"ARTIFACT_CREATED",time,null,null);


        try {
            var info = environment.getController(artifact.getWorkspaceId().getFullName()).getArtifactInfo(artifact.getName());
            if (info == null || !artifact.equals(info.getId())) throw new IllegalStateException("ARTIFACT_ID_REVALIDATION_FAILED");
            // Artifact creation and its initial C09 properties form one observed transaction.
            // Omitting initial properties could turn an invariant into a false vacuous PASS.
            payload.put("properties", info.getObsProperties().stream().filter(this::selectedProperty).map(property ->
                    propertyPayload((String) payload.get("semanticId"), property)).toList());
        } catch (Exception error) {
            gap("CARTAGO_CREATION_SNAPSHOT_UNAVAILABLE:" + error.getMessage()); return;
        }
        synchronized (this) {
            // Publish the birth under the same monitor as admitting callbacks.
            // Another environment worker can publish immediately after init;
            // marking it known before enqueueing CREATED let CHANGED overtake it.
            event(RuntimeEventKind.CREATED, artifact, creator, payload);
            knownArtifacts.add(artifactId(artifact));
            pendingCreations.remove(artifactId(artifact));
        }
    }
    private synchronized void gap(String diagnostic) {
        Consumer<RuntimeEvent> sink = observer; if (sink == null) return;
        long next = sequence.incrementAndGet();
        sink.accept(new RuntimeEvent(sessionId + ":cartago:gap:" + next, sessionId, generation,
                System.getProperty("jacamo.bridge.modelRevision", "unnegotiated"), "cartago", sourceId(), next,
                Instant.now(), RuntimeEventKind.GAP, null, null, null, null, "", "", Map.of(),
                Map.of("diagnostic", diagnostic), new SourceWatermark(sourceId(), next), Completeness.PARTIAL, List.of()));
    }
    @Override public synchronized void artifactDisposed(long time, ArtifactId artifact, AgentId disposer) {
        if(!selectedArtifact(artifact))return;


        event(RuntimeEventKind.DISPOSED, artifact, disposer, values("DELETE_CARTAGO_ARTIFACT",
                OfficialCartagoAdapter.artifactId(String.valueOf(environment.getId()), artifact)));
        synchronized (this) { knownArtifacts.remove(artifactId(artifact)); pendingCreations.remove(artifactId(artifact)); }
    }
    @Override public void artifactFocussed(long time, AgentId agent, ArtifactId artifact, IEventFilter filter) { focusEvent(agent,artifact,true); }
    @Override public void artifactNoMoreFocussed(long time, AgentId agent, ArtifactId artifact) { focusEvent(agent,artifact,false); }
    private void focusEvent(AgentId agent,ArtifactId artifact,boolean focused) {
        if(!selectedArtifact(artifact)||observationScope!=null&&!observationKinds.contains(RuntimeFactKind.RELATION_STATE))return;
        String agentId=OfficialCartagoAdapter.agentId(environment.getId().toString(),agent);
        String artifactId=OfficialCartagoAdapter.artifactId(environment.getId().toString(),artifact);
        var payload=new LinkedHashMap<>(focusPayload(agentId,artifactId,focused));
        payload.put("artifactTypeJavaClassName",artifact.getArtifactType());
        payload.put("artifactTypeOrigin",OfficialCartagoAdapter.typeOrigin(artifact.getArtifactType()));
        emit(focused ? RuntimeEventKind.FOCUSED : RuntimeEventKind.UNFOCUSED,focusId(agentId,artifactId),RuntimeFactKind.RELATION_STATE,payload);
    }
    @Override public void artifactsLinked(long time, AgentId agent, ArtifactId source, ArtifactId target) { event(RuntimeEventKind.CHANGED,
            source, agent, Map.of("linkedArtifact", target.getId().toString())); }
    @Override public synchronized void agentJoined(long time, AgentId agent) {
        var workspace=agent.getWorkspaceId();
        emit(RuntimeEventKind.CREATED,new BridgeEntityId("cartago","environment","workspace",environment.getId().toString(),
                workspace.getFullName(),workspace.getUUID().toString()),RuntimeFactKind.WORKSPACE,workspacePayload(workspace));
        event(RuntimeEventKind.JOINED,null,agent,agentPayload(agent,"UPSERT_CARTAGO_AGENT_IDENTITY"));
    }
    @Override public void agentQuit(long time, AgentId agent) {
        event(RuntimeEventKind.QUIT,null,agent,agentPayload(agent,"DELETE_CARTAGO_AGENT_IDENTITY"));
    }
}

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
    private final CartagoEnvironment environment;
    private final Set<String> configuredWorkspaces;
    private final Set<String> registeredWorkspaces = new LinkedHashSet<>();
    private final Set<BridgeEntityId> knownArtifacts = new LinkedHashSet<>();
    private final Set<BridgeEntityId> pendingCreations = new LinkedHashSet<>();
    private final String sessionId;
    private final long generation;
    private final AtomicLong sequence = new AtomicLong();
    // Official controller reads can acquire workspace/artifact locks. Logger
    // callbacks must never wait for a monitor held across those reads.
    private final Object captureLock = new Object();
    private volatile Consumer<RuntimeEvent> observer;
    private volatile Completeness completeness = Completeness.UNAVAILABLE;
    private volatile Map<String,String> workspaceDeclarationAliases = Map.of();

    public CartagoSnapshotSource(CartagoEnvironment environment, List<String> workspaces, String sessionId) {
        this.environment = java.util.Objects.requireNonNull(environment);
        this.configuredWorkspaces = new LinkedHashSet<>(workspaces == null ? List.of() : workspaces);
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
    }

    @Override public String sourceId() { return "cartago"; }

    @Override public void attach(Consumer<RuntimeEvent> observer) throws Exception {
        synchronized(captureLock) {
            if (this.observer != null) throw new IllegalStateException("CARTAGO_ALREADY_ATTACHED");
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
        boolean complete = true;
        for (String workspace : registeredWorkspaces) {
            try {
                ICartagoController controller = environment.getController(workspace);
                for (AgentId id : controller.getCurrentAgents()) ids.add("a:" + id.getGlobalId());
                for (ArtifactId id : controller.getCurrentArtifacts()) ids.add("r:" + id.getId());
            } catch (Exception error) { complete = false; }
        }
        completeness = registeredWorkspaces.isEmpty() ? Completeness.UNAVAILABLE
                : complete ? Completeness.COMPLETE : Completeness.PARTIAL;
        return AdapterEvidence.digest(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
    }

    @Override public List<RuntimeFact> capture() {
        synchronized(captureLock) { return readSnapshot(); }
    }

    private List<RuntimeFact> readSnapshot() {
        refreshRegistrations();
        var facts = new ArrayList<RuntimeFact>();
        boolean complete = !registeredWorkspaces.isEmpty();
        try {
            var snapshot = new OfficialCartagoAdapter().capture(environment);
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
                            declaredWorkspaceIds.put(declaration.name(),workspace.metadata().semanticId());
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
                var declarations=BridgeRuntimeRegistry.projectDeclarations();
                if(declarations.isPresent()) for(var declaration:declarations.get().artifactDeclarations()) {
                        // OfficialCartagoAdapter has already revalidated this UUID
                        // against the controller's ArtifactInfo. Reuse that cut,
                        // including its exact officially resolved workspace identity.
                        if(artifact.workspaceSemanticId().equals(declaredWorkspaceIds.get(declaration.workspace()))
                                && declaration.name().equals(artifact.name()) && declaration.javaClass().equals(type.javaClassName())) {
                            if(values.putIfAbsent("artifactDeclarationSemanticId",declaration.metadata().semanticId())!=null)
                                throw new IllegalStateException("CARTAGO_ARTIFACT_DECLARATION_AMBIGUOUS");
                        }
                }
                facts.add(faithful(artifactId(workspaceFullName, artifact.name(), artifact.uuid()),
                        RuntimeFactKind.ARTIFACT, values));
                BridgeEntityId id = artifactId(workspaceFullName, artifact.name(), artifact.uuid());
                synchronized(this) { if (!pendingCreations.contains(id)) knownArtifacts.add(id); }
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
                var payload=new LinkedHashMap<>(focusPayload(focus.agentSemanticId(),focus.artifactSemanticId(),true));
                var artifact=snapshot.artifacts().stream().filter(a->a.metadata().semanticId().equals(focus.artifactSemanticId())).findFirst().orElseThrow();
                String fqcn=artifactTypes.get(artifact.artifactTypeSemanticId()).javaClassName();
                payload.put("artifactTypeJavaClassName",fqcn); payload.put("artifactTypeOrigin",OfficialCartagoAdapter.typeOrigin(fqcn));
                facts.add(faithful(focusId(focus.agentSemanticId(),focus.artifactSemanticId()),RuntimeFactKind.RELATION_STATE,payload));
            }
        } catch (Exception error) {
            complete = false;
        }
        completeness = registeredWorkspaces.isEmpty() ? Completeness.UNAVAILABLE
                : complete ? Completeness.COMPLETE : Completeness.PARTIAL;
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

    @Override public synchronized Completeness completeness() {
        return pendingCreations.isEmpty() ? completeness : Completeness.PARTIAL;
    }

    @Override public void close() throws Exception {
        synchronized(captureLock) { closeLoggers(); }
    }

    private void closeLoggers() throws Exception {
        if (observer == null) return;
        observer = null;
        Exception failure = null;
        for (String workspace : List.copyOf(registeredWorkspaces)) {
            try { environment.unregisterLogger(workspace, this); }
            catch (Exception error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        registeredWorkspaces.clear();
        synchronized(this) { knownArtifacts.clear(); pendingCreations.clear(); }
        if (failure != null) throw failure;
    }

    private void refreshRegistrations() {
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
                environment.registerLogger(workspace, this);
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
        BridgeEntityId id = artifact != null ? artifactId(artifact) : agentId(agent);
        emit(kind,id,artifact != null ? RuntimeFactKind.ARTIFACT : RuntimeFactKind.AGENT,values);
    }
    private synchronized void emit(RuntimeEventKind kind,BridgeEntityId id,RuntimeFactKind factKind,Map<String,Object> values) {
        Consumer<RuntimeEvent> sink=observer;
        if(sink==null) return;
        long next = sequence.incrementAndGet();
        String correlation = values.get("opId") instanceof String value ? value : "";
        sink.accept(new RuntimeEvent(sessionId + ":cartago:" + next, sessionId, generation,
                System.getProperty("jacamo.bridge.modelRevision", "unnegotiated"), "cartago", sourceId(), next,
                Instant.now(), kind, factKind,
                values.containsKey("normalizedEventKind") ? ProjectionStatus.MATERIALIZED_FAITHFULLY : ProjectionStatus.EVIDENCE_ONLY,
                id, null, correlation, "", Map.of(), values,
                new SourceWatermark(sourceId(), next), Completeness.COMPLETE, List.of()));
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

    @Override public void opRequested(long time, AgentId agent, ArtifactId artifact, Op op) { event(RuntimeEventKind.STARTED, artifact, agent,
            Map.of("phase", "requested", "operation", op.getName(), "arity", op.getParamValues().length)); }
    @Override public void opStarted(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.STARTED, artifact, null,
            operationPayload(artifact,id,op,"started")); }
    @Override public void opSuspended(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.CHANGED, artifact, null,
            Map.of("phase", "suspended", "opId", id.toString())); }
    @Override public void opResumed(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.CHANGED, artifact, null,
            Map.of("phase", "resumed", "opId", id.toString())); }
    @Override public void opCompleted(long time, OpId id, ArtifactId artifact, Op op) { event(RuntimeEventKind.SUCCEEDED, artifact, null,
            operationPayload(artifact,id,op,"completed")); }
    @Override public void opFailed(long time, OpId id, ArtifactId artifact, Op op, String message, Tuple descriptor) { event(RuntimeEventKind.FAILED,
            artifact, null, failedOperationPayload(artifact,id,op,message,descriptor)); }
    private Map<String,Object> failedOperationPayload(ArtifactId artifact,OpId id,Op op,String message,Tuple descriptor) {
        var values=new java.util.LinkedHashMap<>(operationPayload(artifact,id,op,"failed"));values.put("message",safe(message));values.put("descriptor",safe(descriptor));return values;
    }
    private Map<String,Object> operationPayload(ArtifactId artifact,OpId id,Op op,String phase) {
        var payload=new java.util.LinkedHashMap<String,Object>();
        payload.put("phase",phase);payload.put("operation",op.getName());payload.put("opId",id.toString());
        payload.put("artifactSemanticId",OfficialCartagoAdapter.artifactId(environment.getId().toString(),artifact));
        payload.put("argumentValues",Arrays.stream(op.getParamValues()).map(v->v==null?"":v.toString()).toList());
        payload.put("argumentTypes",Arrays.stream(op.getParamValues()).map(v->v==null?"NULL":v.getClass().getName()).toList());
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
    @Override public void newPercept(long time, ArtifactId artifact, Tuple signal, ArtifactObsProperty[] added,
                                     ArtifactObsProperty[] removed, ArtifactObsProperty[] changed) {
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
            event(RuntimeEventKind.CHANGED, artifact, null, Map.of("signal", safe(signal))); return;
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
        try {
            var info = environment.getController(artifact.getWorkspaceId().getFullName()).getArtifactInfo(artifact.getName());
            if (info == null || !artifact.equals(info.getId())) throw new IllegalStateException("ARTIFACT_ID_REVALIDATION_FAILED");
            // Artifact creation and its initial C09 properties form one observed transaction.
            // Omitting initial properties could turn an invariant into a false vacuous PASS.
            payload.put("properties", info.getObsProperties().stream().map(property ->
                    propertyPayload((String) payload.get("semanticId"), property)).toList());
        } catch (Exception error) {
            gap("CARTAGO_CREATION_SNAPSHOT_UNAVAILABLE:" + error.getMessage()); return;
        }
        synchronized (this) { knownArtifacts.add(artifactId(artifact)); pendingCreations.remove(artifactId(artifact)); }
        event(RuntimeEventKind.CREATED, artifact, creator, payload);
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
        event(RuntimeEventKind.DISPOSED, artifact, disposer, values("DELETE_CARTAGO_ARTIFACT",
                OfficialCartagoAdapter.artifactId(String.valueOf(environment.getId()), artifact)));
        synchronized (this) { knownArtifacts.remove(artifactId(artifact)); pendingCreations.remove(artifactId(artifact)); }
    }
    @Override public void artifactFocussed(long time, AgentId agent, ArtifactId artifact, IEventFilter filter) { focusEvent(agent,artifact,true); }
    @Override public void artifactNoMoreFocussed(long time, AgentId agent, ArtifactId artifact) { focusEvent(agent,artifact,false); }
    private void focusEvent(AgentId agent,ArtifactId artifact,boolean focused) {
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

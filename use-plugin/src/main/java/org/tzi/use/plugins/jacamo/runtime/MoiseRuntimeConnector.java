package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import moise.oe.OE;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

/** Moise 1.1 connector using authoritative OE snapshots and explicit controlled polling. */
public final class MoiseRuntimeConnector implements RuntimeConnector {
    private final String id;
    private final OE organisation;
    private final MoiseRuntimeBinding binding;
    private final CopyOnWriteArrayList<Consumer<RuntimeEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile ConnectorState state = ConnectorState.DISCONNECTED;
    private OrganisationState observed;

    public MoiseRuntimeConnector(String id, OE organisation, MoiseRuntimeBinding binding) {
        if (id == null || id.isBlank() || organisation == null || binding == null)
            throw new IllegalArgumentException("MOISE_CONNECTOR_INVALID");
        this.id = id;
        this.organisation = organisation;
        this.binding = binding;
    }

    @Override public String connectorId() { return id; }
    @Override public Set<ConnectorCapability> capabilities() {
        return Set.of(ConnectorCapability.FULL_SNAPSHOT, ConnectorCapability.EVENT_SUBSCRIPTION,
                ConnectorCapability.RECONNECT, ConnectorCapability.ORGANISATION_STATE);
    }
    @Override public ConnectorState state() { return state; }

    @Override public synchronized void connect(URI endpoint) {
        if (endpoint == null || !"jacamo".equalsIgnoreCase(endpoint.getScheme()))
            throw new IllegalArgumentException("MOISE_ENDPOINT_INVALID");
        if (state == ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_ALREADY_CONNECTED");
        observed = null;
        state = ConnectorState.CONNECTED;
    }

    @Override public synchronized RuntimeSnapshot fullSnapshot() {
        requireConnected();
        OrganisationState current = capture();
        List<RuntimeEvent> events = initialEvents(current);
        observed = current;
        String fingerprint = sha256(canonical(current));
        long last = events.isEmpty() ? sequence.get() : events.getLast().sequence();
        return new RuntimeSnapshot("moise-" + last, Instant.now(), last, events, fingerprint);
    }

    @Override public RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
        requireConnected();
        if (listener == null) throw new IllegalArgumentException("RUNTIME_LISTENER_REQUIRED");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Polls the public Moise OE state once and publishes exact state deltas. */
    public synchronized List<RuntimeEvent> pollChanges() {
        requireConnected();
        OrganisationState current = capture();
        if (observed == null) {
            observed = current;
            return List.of();
        }
        List<RuntimeEvent> changes = diff(observed, current);
        observed = current;
        changes.forEach(this::emit);
        return List.copyOf(changes);
    }

    public List<String> discoveredGroups() {
        requireConnected();
        return capture().groups().keySet().stream().sorted().toList();
    }

    public List<String> discoveredSchemes() {
        requireConnected();
        return capture().schemes().keySet().stream().sorted().toList();
    }

    /** Derived sets are evidence only; no automatic state mutation or OCL translation. */
    public MoiseNormativeSnapshot normativeSnapshot() {
        requireConnected();
        return MoiseNormativeSnapshot.capture(organisation, binding.organisation());
    }

    /** Moise OE 1.1 exposes derived permissions/obligations, not NPL lifecycle states. */
    public List<String> capabilityGaps() {
        return List.of(
                "MOISE_NO_PUBLIC_EVENT_LISTENER: role, mission and goal changes require controlled polling",
                "MOISE_NO_NPL_NORM_LIFECYCLE: activation, fulfilment, violation and expiration are not exposed by OE");
    }

    @Override public synchronized void disconnect() {
        listeners.clear();
        observed = null;
        state = ConnectorState.DISCONNECTED;
    }

    private OrganisationState capture() {
        synchronized (organisation) {
            Map<String, GroupState> groups = new LinkedHashMap<>();
            organisation.getGroups().stream().sorted().forEach(group -> groups.put(group.getId(),
                    new GroupState(group.getId(), group.getGrSpec().getId(), group.isWellFormed())));
            Map<String, SchemeState> schemes = new LinkedHashMap<>();
            Map<GoalKey, String> goals = new LinkedHashMap<>();
            organisation.getSchemes().stream().sorted().forEach(scheme -> {
                schemes.put(scheme.getId(), new SchemeState(scheme.getId(), scheme.getSpec().getId(),
                        scheme.isWellFormed()));
                scheme.getGoals().stream().sorted().forEach(goal -> goals.put(
                        new GoalKey(scheme.getId(), goal.getSpec().getId()), goal.getState().name()));
            });
            Set<RoleKey> roles = new LinkedHashSet<>();
            Set<MissionKey> missions = new LinkedHashSet<>();
            organisation.getAgents().stream().sorted().forEach(agent -> {
                agent.getRoles().stream().sorted().forEach(role -> roles.add(new RoleKey(agent.getId(),
                        role.getRole().getId(), role.getGroup().getId())));
                agent.getMissions().stream().sorted().forEach(mission -> missions.add(new MissionKey(agent.getId(),
                        mission.getMission().getId(), mission.getScheme().getId())));
            });
            return new OrganisationState(Map.copyOf(groups), Map.copyOf(schemes), Set.copyOf(roles),
                    Set.copyOf(missions), Map.copyOf(goals));
        }
    }

    private List<RuntimeEvent> initialEvents(OrganisationState current) {
        List<RuntimeEvent> result = new ArrayList<>();
        result.add(event(binding.organisationRuntimeId(), binding.organisationSemanticId(),
                RuntimeEventKind.ORGANISATION_DISCOVERED, Map.of("organisation", binding.organisation())));
        current.groups().values().stream().sorted().forEach(group -> addGroup(result, group, RuntimeEventKind.GROUP_CREATED));
        current.schemes().values().stream().sorted().forEach(scheme -> addScheme(result, scheme, RuntimeEventKind.SCHEME_CREATED));
        current.roles().stream().sorted().forEach(role -> addRole(result, role, RuntimeEventKind.ROLE_ADOPTED));
        current.missions().stream().sorted().forEach(mission -> addMission(result, mission, RuntimeEventKind.MISSION_COMMITTED));
        current.goals().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                addGoal(result, entry.getKey(), entry.getValue()));
        return result;
    }

    private List<RuntimeEvent> diff(OrganisationState before, OrganisationState after) {
        List<RuntimeEvent> result = new ArrayList<>();
        difference(after.groups().keySet(), before.groups().keySet()).stream().sorted()
                .forEach(key -> addGroup(result, after.groups().get(key), RuntimeEventKind.GROUP_CREATED));
        difference(before.groups().keySet(), after.groups().keySet()).stream().sorted()
                .forEach(key -> addGroup(result, before.groups().get(key), RuntimeEventKind.GROUP_DISPOSED));
        difference(after.schemes().keySet(), before.schemes().keySet()).stream().sorted()
                .forEach(key -> addScheme(result, after.schemes().get(key), RuntimeEventKind.SCHEME_CREATED));
        difference(before.schemes().keySet(), after.schemes().keySet()).stream().sorted()
                .forEach(key -> addScheme(result, before.schemes().get(key), RuntimeEventKind.SCHEME_DISPOSED));
        difference(after.roles(), before.roles()).stream().sorted()
                .forEach(role -> addRole(result, role, RuntimeEventKind.ROLE_ADOPTED));
        difference(before.roles(), after.roles()).stream().sorted()
                .forEach(role -> addRole(result, role, RuntimeEventKind.ROLE_REMOVED));
        difference(after.missions(), before.missions()).stream().sorted()
                .forEach(mission -> addMission(result, mission, RuntimeEventKind.MISSION_COMMITTED));
        difference(before.missions(), after.missions()).stream().sorted()
                .forEach(mission -> addMission(result, mission, RuntimeEventKind.MISSION_REMOVED));
        after.goals().entrySet().stream().filter(entry -> !entry.getValue().equals(before.goals().get(entry.getKey())))
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> addGoal(result, entry.getKey(), entry.getValue()));
        return result;
    }

    private void addGroup(List<RuntimeEvent> target, GroupState group, RuntimeEventKind kind) {
        String semantic = binding.groups().get(group.id());
        target.add(event(binding.groupRuntimeId(group.id()), semantic, kind,
                Map.of("group", group.id(), "specification", group.specification(), "wellFormed", group.wellFormed())));
    }

    private void addScheme(List<RuntimeEvent> target, SchemeState scheme, RuntimeEventKind kind) {
        String semantic = binding.schemes().get(scheme.id());
        target.add(event(binding.schemeRuntimeId(scheme.id()), semantic, kind,
                Map.of("scheme", scheme.id(), "specification", scheme.specification(), "wellFormed", scheme.wellFormed())));
    }

    private void addRole(List<RuntimeEvent> target, RoleKey role, RuntimeEventKind kind) {
        String semantic = binding.agents().get(role.agent());
        target.add(event(binding.agentRuntimeId(role.agent()), semantic, kind,
                Map.of("agent", role.agent(), "role", role.role(), "group", role.group())));
    }

    private void addMission(List<RuntimeEvent> target, MissionKey mission, RuntimeEventKind kind) {
        String semantic = binding.agents().get(mission.agent());
        target.add(event(binding.agentRuntimeId(mission.agent()), semantic, kind,
                Map.of("agent", mission.agent(), "mission", mission.mission(), "scheme", mission.scheme())));
    }

    private void addGoal(List<RuntimeEvent> target, GoalKey goal, String state) {
        String semantic = binding.schemes().get(goal.scheme());
        target.add(event(binding.schemeRuntimeId(goal.scheme()), semantic, RuntimeEventKind.SCHEME_STATE_CHANGED,
                Map.of("scheme", goal.scheme(), "goal", goal.goal(), "state", state)));
    }

    private RuntimeEvent event(String runtimeId, String semanticId, RuntimeEventKind kind, Map<String, Object> payload) {
        long next = sequence.incrementAndGet();
        Map<String, Object> evidence = new LinkedHashMap<>(payload);
        String context = switch (kind) {
            case ROLE_ADOPTED, ROLE_REMOVED -> RuntimeIdentity.key("role-player", binding.organisation(),
                    (String)payload.get("group"), (String)payload.get("agent"), (String)payload.get("role"));
            case MISSION_COMMITTED, MISSION_REMOVED -> RuntimeIdentity.key("mission-player", binding.organisation(),
                    (String)payload.get("scheme"), (String)payload.get("agent"), (String)payload.get("mission"));
            case SCHEME_STATE_CHANGED -> RuntimeIdentity.key("goal-instance", binding.organisation(),
                    (String)payload.get("scheme"), (String)payload.get("goal"));
            default -> runtimeId;
        };
        evidence.put("runtimeFactKey", context);
        return RuntimeEvent.create(id + "-" + next, Instant.now(), next, Dimension.ORGANISATION,
                kind, runtimeId, semanticId, evidence, null);
    }

    private <T> Set<T> difference(Set<T> left, Set<T> right) {
        Set<T> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private void emit(RuntimeEvent event) { listeners.forEach(listener -> listener.accept(event)); }
    private String canonical(OrganisationState value) {
        return "groups=" + value.groups().values().stream().sorted().toList()
                + "|schemes=" + value.schemes().values().stream().sorted().toList()
                + "|roles=" + value.roles().stream().sorted().toList()
                + "|missions=" + value.missions().stream().sorted().toList()
                + "|goals=" + value.goals().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
    }
    private void requireConnected() {
        if (state != ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_NOT_CONNECTED");
    }
    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception exception) { throw new IllegalStateException("SHA256_UNAVAILABLE", exception); }
    }

    private record OrganisationState(Map<String, GroupState> groups, Map<String, SchemeState> schemes,
                                     Set<RoleKey> roles, Set<MissionKey> missions, Map<GoalKey, String> goals) { }
    private record GroupState(String id, String specification, boolean wellFormed)
            implements Comparable<GroupState> {
        @Override public int compareTo(GroupState other) { return id.compareTo(other.id); }
    }
    private record SchemeState(String id, String specification, boolean wellFormed)
            implements Comparable<SchemeState> {
        @Override public int compareTo(SchemeState other) { return id.compareTo(other.id); }
    }
    private record RoleKey(String agent, String role, String group) implements Comparable<RoleKey> {
        @Override public int compareTo(RoleKey other) { return toString().compareTo(other.toString()); }
    }
    private record MissionKey(String agent, String mission, String scheme) implements Comparable<MissionKey> {
        @Override public int compareTo(MissionKey other) { return toString().compareTo(other.toString()); }
    }
    private record GoalKey(String scheme, String goal) implements Comparable<GoalKey> {
        @Override public int compareTo(GoalKey other) { return toString().compareTo(other.toString()); }
    }
}

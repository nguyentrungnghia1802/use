package org.jacamo.bridge.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import ora4mas.nopl.GroupBoard;
import ora4mas.nopl.SchemeBoard;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Exact official board snapshots. Polling completeness is explicitly PARTIAL for intermediate events. */
public final class MoiseBoardSnapshotSource implements SnapshotSource {
    private final String sourceId;
    private final String organisation;
    private final Set<String> excludedOrganisations;
    private final String sessionId;
    private final Supplier<? extends Collection<? extends GroupBoard>> groups;
    private final Supplier<? extends Collection<? extends SchemeBoard>> schemes;
    private final org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot project;
    private final java.nio.file.Path projectRoot;
    private final AtomicLong sequence = new AtomicLong();
    private Consumer<RuntimeEvent> observer;
    private final Map<String,RuntimeFact> publishedFacts=new java.util.LinkedHashMap<>();

    /** Captures one organization declared statically by the JCM project. */
    public MoiseBoardSnapshotSource(String organisation, String sessionId,
                                    Supplier<? extends Collection<? extends GroupBoard>> groups,
                                    Supplier<? extends Collection<? extends SchemeBoard>> schemes) {
        this(organisation,sessionId,groups,schemes,null,null);
    }
    public MoiseBoardSnapshotSource(String organisation, String sessionId,
                                    Supplier<? extends Collection<? extends GroupBoard>> groups,
                                    Supplier<? extends Collection<? extends SchemeBoard>> schemes,
                                    org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot project,java.nio.file.Path projectRoot) {
        if (organisation == null || organisation.isBlank()) throw new IllegalArgumentException("organisation");
        this.sourceId = "moise:" + organisation;
        this.organisation = organisation;
        this.excludedOrganisations = Set.of();
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.groups = java.util.Objects.requireNonNull(groups);
        this.schemes = java.util.Objects.requireNonNull(schemes);
        this.project=project; this.projectRoot=projectRoot;
    }

    /** Captures organizations created dynamically at runtime, excluding every statically declared organization. */
    public MoiseBoardSnapshotSource(Set<String> excludedOrganisations, String sessionId,
                                    Supplier<? extends Collection<? extends GroupBoard>> groups,
                                    Supplier<? extends Collection<? extends SchemeBoard>> schemes) {
        this(excludedOrganisations,sessionId,groups,schemes,null,null);
    }
    public MoiseBoardSnapshotSource(Set<String> excludedOrganisations, String sessionId,
                                    Supplier<? extends Collection<? extends GroupBoard>> groups,
                                    Supplier<? extends Collection<? extends SchemeBoard>> schemes,
                                    org.jacamo.bridge.contract.semantic.JacamoSemanticSnapshot project,java.nio.file.Path projectRoot) {
        this.sourceId = "moise:dynamic";
        this.organisation = null;
        this.excludedOrganisations = Set.copyOf(excludedOrganisations);
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.groups = java.util.Objects.requireNonNull(groups);
        this.schemes = java.util.Objects.requireNonNull(schemes);
        this.project=project; this.projectRoot=projectRoot;
    }

    @Override public String sourceId() { return sourceId; }

    @Override public synchronized void attach(Consumer<RuntimeEvent> observer) {
        if(this.observer!=null) throw new IllegalStateException("MOISE_ALREADY_ATTACHED");
        this.observer=java.util.Objects.requireNonNull(observer);
    }

    @Override public SourceWatermark watermark() { return new SourceWatermark(sourceId(), sequence.get()); }

    @Override public String topologyFingerprint() {
        var values = new TreeSet<String>();
        groups.get().stream().filter(java.util.Objects::nonNull)
                .filter(board -> acceptsOrganisation(board.getOEId()))
                .forEach(board -> values.add("g:" + board.getOEId() + ":" + board.getArtId()));
        schemes.get().stream().filter(java.util.Objects::nonNull)
                .filter(board -> acceptsOrganisation(board.getOEId()))
                .forEach(board -> values.add("s:" + board.getOEId() + ":" + board.getArtId()));
        return AdapterEvidence.digest(String.join("\n", values).getBytes());
    }

    @Override public synchronized List<RuntimeFact> capture() {
        var facts = new ArrayList<RuntimeFact>();
        var observedGroups=List.copyOf(groups.get());
        for (GroupBoard board : observedGroups) {
            if (board == null) throw new IllegalStateException("MOISE_GROUP_BOARD_INVALID");
            String scope = board.getOEId();
            if (!acceptsOrganisation(scope)) continue;
            if (board.getGrpState() == null || board.getSpec() == null)
                throw new IllegalStateException("MOISE_GROUP_BOARD_INVALID");
            var state = board.getGrpState().clone();
            if (!board.getArtId().equals(state.getId()))
                throw new IllegalStateException("MOISE_BOARD_IDENTITY_MISMATCH");
            var boardId = id(scope, "group-board", state.getId(), board.getArtId());
            if(project==null) facts.add(new RuntimeFact(boardId, RuntimeFactKind.GROUP_BOARD,
                Map.of("spec",board.getSpec().getId(),"wellFormed",board.isWellFormed(),"diagnostic","MOISE_PROJECT_BINDING_UNAVAILABLE"),
                List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.COMPLETE,List.of()));
            else try {
                var definition=new OfficialMoiseAdapter().capture(board.getSpec().getSS().getOS(),projectRoot,project.project().name()).organization();
                var values=new java.util.LinkedHashMap<String,Object>();
                values.put("normalizedEventKind","UPSERT_MOISE_GROUP"); values.put("runtimeIdentity",boardId.canonical());
                values.put("organisationDefinition",org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationToTree(definition));
                values.put("organisationSpecSemanticId",definition.metadata().semanticId());
                values.put("groupSpecSemanticId",OfficialMoiseAdapter.groupId(definition.metadata().semanticId(),board.getSpec()));
                values.put("organisationName",scope); values.put("name",state.getId());
                var deployments=project.organizationDeployments().stream().filter(d -> d.name().equals(scope)).toList();
                if(deployments.size()>1) throw new IllegalStateException("MOISE_ORGANISATION_DECLARATION_AMBIGUOUS");
                String owner=deployments.isEmpty() ? id(scope,"organisation",scope,scope).canonical() : deployments.get(0).metadata().semanticId();
                values.put("organisationSemanticId",owner);
                var groupDeclarations=project.groupDeployments().stream().filter(g -> g.organization().equals(scope) && g.name().equals(state.getId())).toList();
                if(groupDeclarations.size()>1 || (!groupDeclarations.isEmpty() && !groupDeclarations.get(0).type().equals(board.getSpec().getId())))
                    throw new IllegalStateException("MOISE_GROUP_DECLARATION_CONFLICT");
                values.put("semanticId",groupDeclarations.isEmpty() ? boardId.canonical() : groupDeclarations.get(0).metadata().semanticId());
                var players=new ArrayList<Map<String,Object>>();
                for(var player:state.getPlayers()) {
                    String agent=BridgeRuntimeRegistry.projectedAgentId(player.getAg()).orElseThrow(
                        () -> new IllegalStateException("MOISE_PLAYER_AGENT_BINDING_UNAVAILABLE:"+player.getAg()));
                    var roles=definition.structuralSpecification().roles().stream().filter(r -> r.roleId().equals(player.getTarget())).toList();
                    if(roles.size()!=1) throw new IllegalStateException("MOISE_PLAYER_ROLE_UNRESOLVED:"+player.getTarget());
                    players.add(Map.of("agentSemanticId",agent,"roleSemanticId",roles.get(0).metadata().semanticId()));
                }
                players.sort(Comparator.comparing(Object::toString)); values.put("players",players);
                var parentBoards=observedGroups.stream().filter(p->p!=board && scope.equals(p.getOEId()) && state.getParentGroup().equals(p.getArtId())
                    && p.getGrpState()!=null && p.getGrpState().clone().getSubgroup(state.getId())!=null).toList();
                if(parentBoards.size()>1 || parentBoards.isEmpty() && !"root".equals(state.getParentGroup()))
                    throw new IllegalStateException("MOISE_PARENT_BOARD_UNRESOLVED:"+state.getParentGroup());
                var parentIdentity=id(scope,"group-parent",state.getId(),board.getArtId());
                var parentValues=new java.util.LinkedHashMap<String,Object>();
                parentValues.put("normalizedEventKind","UPSERT_MOISE_GROUP_PARENT"); parentValues.put("runtimeIdentity",parentIdentity.canonical());
                parentValues.put("semanticId",values.get("semanticId")); parentValues.put("groupSpecSemanticId",values.get("groupSpecSemanticId"));
                parentValues.put("organisationSemanticId",owner); parentValues.put("organisationSpecSemanticId",definition.metadata().semanticId());
                parentValues.put("parentSemanticId",parentBoards.isEmpty() ? "" : groupSemanticId(parentBoards.getFirst()));
                parentValues.put("parentSpecSemanticId",parentBoards.isEmpty() ? "" : OfficialMoiseAdapter.groupId(definition.metadata().semanticId(),parentBoards.getFirst().getSpec()));
                facts.add(new RuntimeFact(boardId,RuntimeFactKind.GROUP_BOARD,values,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
                facts.add(new RuntimeFact(parentIdentity,RuntimeFactKind.RELATION_STATE,parentValues,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
            } catch(Exception unavailable) {
                facts.add(new RuntimeFact(boardId,RuntimeFactKind.GROUP_BOARD,Map.of("spec",board.getSpec().getId(),
                    "diagnostic",unavailable.getClass().getSimpleName()+":"+unavailable.getMessage()),List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.PARTIAL,List.of()));
            }
            state.getPlayers().forEach(player -> facts.add(new RuntimeFact(
                    id(scope, "role-player", player.getAg() + "/" + player.getTarget() + "/" + state.getId(),
                            board.getArtId()), RuntimeFactKind.ROLE_PLAYER,
                    Map.of("agent", player.getAg(), "role", player.getTarget(), "group", state.getId()),
                    List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
        }
        for (SchemeBoard board : List.copyOf(schemes.get())) {
            if (board == null) throw new IllegalStateException("MOISE_SCHEME_BOARD_INVALID");
            String scope = board.getOEId();
            if (!acceptsOrganisation(scope)) continue;
            if (board.getSchState() == null || board.getSpec() == null)
                throw new IllegalStateException("MOISE_SCHEME_BOARD_INVALID");
            var state = board.getSchState().clone();
            if (!board.getArtId().equals(state.getId()))
                throw new IllegalStateException("MOISE_BOARD_IDENTITY_MISMATCH");
            var boardId=id(scope,"scheme-board",state.getId(),board.getArtId());
            try {
                if(project==null) throw new IllegalStateException("MOISE_PROJECT_BINDING_UNAVAILABLE");
                var definition=new OfficialMoiseAdapter().capture(board.getSpec().getFS().getOS(),projectRoot,project.project().name()).organization();
                var spec=definition.functionalSpecification().schemes().stream().filter(s->s.schemeId().equals(board.getSpec().getId())).toList();
                if(spec.size()!=1) throw new IllegalStateException("MOISE_SCHEME_SPEC_UNRESOLVED");
                var observed=MoiseGoalObservation.capture(board);
                boardId=id(scope,"scheme-board",state.getId(),observed.artifact().getId().toString());
                var values=new java.util.LinkedHashMap<String,Object>();
                values.put("normalizedEventKind","UPSERT_MOISE_SCHEME"); values.put("runtimeIdentity",boardId.canonical());
                values.put("organisationDefinition",org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationToTree(definition));
                values.put("organisationSpecSemanticId",definition.metadata().semanticId()); values.put("schemeSpecSemanticId",spec.getFirst().metadata().semanticId());
                values.put("organisationName",scope); values.put("name",state.getId());
                var owners=project.organizationDeployments().stream().filter(d->d.name().equals(scope)).toList();
                if(owners.size()>1) throw new IllegalStateException("MOISE_ORGANISATION_DECLARATION_AMBIGUOUS");
                values.put("organisationSemanticId",owners.isEmpty() ? id(scope,"organisation",scope,scope).canonical() : owners.getFirst().metadata().semanticId());
                var declarations=project.schemeDeployments().stream().filter(s->s.organization().equals(scope) && s.name().equals(state.getId())).toList();
                if(declarations.size()>1 || (!declarations.isEmpty() && !declarations.getFirst().type().equals(board.getSpec().getId())))
                    throw new IllegalStateException("MOISE_SCHEME_DECLARATION_CONFLICT");
                values.put("semanticId",declarations.isEmpty() ? boardId.canonical() : declarations.getFirst().metadata().semanticId());
                var responsibility=new ArrayList<String>();
                for(String groupName:state.getIdsGroupsResponsibleFor()) {
                    var exact=groups.get().stream().filter(g->g.getOEId().equals(scope) && g.getArtId().equals(groupName)).toList();
                    if(exact.size()!=1) throw new IllegalStateException("MOISE_RESPONSIBLE_GROUP_UNRESOLVED:"+groupName);
                    var groupDeclarations=project.groupDeployments().stream().filter(g->g.organization().equals(scope) && g.name().equals(groupName)).toList();
                    if(groupDeclarations.size()>1) throw new IllegalStateException("MOISE_GROUP_DECLARATION_CONFLICT");
                    responsibility.add(groupDeclarations.isEmpty() ? id(scope,"group-board",groupName,exact.getFirst().getArtId()).canonical() : groupDeclarations.getFirst().metadata().semanticId());
                }
                values.put("responsibleGroupSemanticIds",responsibility.stream().sorted().toList());
                var commitments=new ArrayList<Map<String,Object>>();
                for(var player:state.getPlayers()) {
                    String agent=BridgeRuntimeRegistry.projectedAgentId(player.getAg()).orElseThrow(()->new IllegalStateException("MOISE_COMMITMENT_AGENT_UNRESOLVED:"+player.getAg()));
                    var missions=spec.getFirst().missions().stream().filter(m->m.missionId().equals(player.getTarget())).toList();
                    if(missions.size()!=1) throw new IllegalStateException("MOISE_COMMITMENT_MISSION_UNRESOLVED:"+player.getTarget());
                    commitments.add(Map.of("agentSemanticId",agent,"missionSemanticId",missions.getFirst().metadata().semanticId()));
                }
                values.put("commitments",commitments);
                var goalStates=new ArrayList<Map<String,Object>>();
                values.put("goalObservationVersion","1.0.0");
                values.put("schemeArguments",observed.arguments());
                values.put("boardArtifactIdentity",observed.artifact().getId().toString());
                for(var goal:board.getSpec().getGoals()) {
                    var goals=spec.getFirst().goals().stream().filter(g->g.goalId().equals(goal.getId())).toList();
                    if(goals.size()!=1) throw new IllegalStateException("MOISE_GOAL_STATE_UNRESOLVED:"+goal.getId());
                    var exact=observed.goals().stream().filter(g->g.id().equals(goal.getId())).toList();
                    if(exact.size()!=1)throw new IllegalStateException("MOISE_GOAL_OBSERVATION_UNAVAILABLE:"+goal.getId());
                    java.util.function.Function<String,String> agentId=name->BridgeRuntimeRegistry.projectedAgentId(name)
                            .orElseThrow(()->new IllegalStateException("MOISE_GOAL_AGENT_UNRESOLVED:"+name));
                    goalStates.add(Map.of("goalSemanticId",goals.getFirst().metadata().semanticId(),"state",exact.getFirst().state(),
                            "committedAgentSemanticIds",exact.getFirst().committed().stream().map(agentId).toList(),
                            "achievedAgentSemanticIds",exact.getFirst().achieved().stream().map(agentId).toList(),
                            "arguments",observed.arguments().getOrDefault(goal.getId(),Map.of())));
                }
                values.put("goalStates",goalStates);
                facts.add(new RuntimeFact(boardId,RuntimeFactKind.SCHEME_BOARD,values,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
            } catch(Exception unavailable) {
                facts.add(new RuntimeFact(boardId,RuntimeFactKind.SCHEME_BOARD,Map.of("spec",board.getSpec().getId(),"diagnostic",unavailable.getClass().getSimpleName()+":"+unavailable.getMessage()),
                    List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.PARTIAL,List.of()));
            }
            state.getPlayers().forEach(player -> facts.add(new RuntimeFact(
                    id(scope, "mission-commitment", player.getAg() + "/" + player.getTarget() + "/" + state.getId(),
                            board.getArtId()), RuntimeFactKind.MISSION_COMMITMENT,
                    Map.of("agent", player.getAg(), "mission", player.getTarget(), "scheme", state.getId()),
                    List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
            board.getSpec().getGoals().forEach(goal -> facts.add(new RuntimeFact(
                    id(scope, "organisational-goal-state", state.getId() + "/" + goal.getId(), board.getArtId()),
                    RuntimeFactKind.ORGANISATIONAL_GOAL_STATE,
                    Map.of("scheme", state.getId(), "goal", goal.getId(),
                            "state", state.isSatisfied(goal) ? "SATISFIED" : "NOT_SATISFIED"),
                    List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
        }
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }

    boolean acceptsOrganisation(String value) {
        if (value == null || value.isBlank()) return false;
        return organisation == null ? !excludedOrganisations.contains(value) : organisation.equals(value);
    }

    private BridgeEntityId id(String scope, String kind, String local, String incarnation) {
        return new BridgeEntityId("moise", "organisation", kind, scope, local, sessionId + ":" + incarnation);
    }

    @Override public Completeness completeness() { return Completeness.PARTIAL; }
    private String groupSemanticId(GroupBoard board) {
        var declarations=project.groupDeployments().stream().filter(g->g.organization().equals(board.getOEId()) && g.name().equals(board.getArtId())).toList();
        if(declarations.size()>1 || !declarations.isEmpty() && !declarations.getFirst().type().equals(board.getSpec().getId()))
            throw new IllegalStateException("MOISE_PARENT_GROUP_DECLARATION_CONFLICT");
        return declarations.isEmpty() ? id(board.getOEId(),"group-board",board.getArtId(),board.getArtId()).canonical() : declarations.getFirst().metadata().semanticId();
    }
    @Override public synchronized void poll() {
        if(observer==null) return;
        List<RuntimeFact> facts;
        try { facts=capture(); }
        catch(RuntimeException failure) {
            long next=sequence.incrementAndGet();
            observer.accept(new RuntimeEvent(sessionId+":"+sourceId+":"+next,sessionId,
                    Long.parseLong(System.getProperty("jacamo.bridge.generation","0")),System.getProperty("jacamo.bridge.modelRevision","unnegotiated"),
                    "moise",sourceId,next,java.time.Instant.now(),org.jacamo.bridge.contract.RuntimeEventKind.GAP,null,null,null,null,"","",Map.of(),
                    Map.of("diagnostic","MOISE_OBSERVATION_UNAVAILABLE:"+failure.getMessage()),new SourceWatermark(sourceId,next),Completeness.PARTIAL,List.of()));
            return;
        }
        // Complete supported board cuts update the existing native group/scheme rules.
        // Global polling fidelity stays PARTIAL; absence is never a deletion proof.
        for(var fact:facts.stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD || f.kind()==RuntimeFactKind.SCHEME_BOARD
                || "UPSERT_MOISE_GROUP_PARENT".equals(f.values().get("normalizedEventKind")))
                .sorted(Comparator.comparingInt((RuntimeFact f)->f.kind()==RuntimeFactKind.GROUP_BOARD ? 0 : f.kind()==RuntimeFactKind.SCHEME_BOARD ? 1 : 2)
                        .thenComparing(f->f.id().canonical())).toList()) {
            var previous=publishedFacts.get(fact.id().canonical());
            if(fact.equals(previous)) continue;
            long next=sequence.incrementAndGet();
            observer.accept(new RuntimeEvent(sessionId+":"+sourceId+":"+next,sessionId,
                    Long.parseLong(System.getProperty("jacamo.bridge.generation","0")),System.getProperty("jacamo.bridge.modelRevision","unnegotiated"),
                    "moise",sourceId,next,java.time.Instant.now(),previous==null ? org.jacamo.bridge.contract.RuntimeEventKind.CREATED
                            : org.jacamo.bridge.contract.RuntimeEventKind.CHANGED,fact.kind(),fact.projectionStatus(),
                    fact.id(),null,"","",Map.of(),fact.values(),new SourceWatermark(sourceId,next),fact.completeness(),fact.evidence()));
            publishedFacts.put(fact.id().canonical(),fact);
        }
    }
    @Override public synchronized void close() { observer=null; publishedFacts.clear(); }
}

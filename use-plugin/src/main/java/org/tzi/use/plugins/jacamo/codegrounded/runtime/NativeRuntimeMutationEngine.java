package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.BridgeRelationId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStateBuilder;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * The native-only mutation boundary.  It owns no parallel model: every accepted mutation is
 * applied through UseSystemApi to the MSystem supplied by the native pipeline.
 */
public final class NativeRuntimeMutationEngine {
    public enum Status { MATERIALIZED, EVIDENCE_ONLY, REJECTED }

    public record ApplyResult(Status status, String ruleId, String targetSemanticId,
                              String targetUseId, String diagnostic) {
        public ApplyResult {
            Objects.requireNonNull(status, "status");
            ruleId = ruleId == null ? "" : ruleId;
            targetSemanticId = targetSemanticId == null ? "" : targetSemanticId;
            targetUseId = targetUseId == null ? "" : targetUseId;
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
        public static ApplyResult evidence(String ruleId, String identity, String diagnostic) {
            return new ApplyResult(Status.EVIDENCE_ONLY, ruleId, "evidence:" + identity, "evidence", diagnostic);
        }
        public static ApplyResult rejected(String ruleId, String diagnostic) {
            return new ApplyResult(Status.REJECTED, ruleId, "", "", diagnostic);
        }
    }

    public record OclGate(boolean structureValid, boolean invariantsValid, String diagnostic) {
        public boolean passed() { return structureValid && invariantsValid; }
    }

    private record BoundTarget(String semanticId, MObject object) { }
    // Ephemeral rollback/baseline images retain exact USE object/link identity, not a history state.
    private record LinkImage(String association, List<String> objectNames, MLink original) { }
    private record ObjectImage(String className, Map<String, Value> attributes, MObject original) { }
    public record StateImage(Map<String, ObjectImage> objects, List<LinkImage> links,
                             Map<String, String> semanticNames, java.util.Set<String> tombstones, Map<String, PropertyBinding> properties,
                             java.util.Set<String> ignoredArtifacts, java.util.Set<String> observedRoleViolations,
                             Map<String, Map<String,Object>> jasonCuts) { }

    private record PropertyBinding(String owner, String name) { }
    private final Map<String, PropertyBinding> properties = new LinkedHashMap<>();
    private final java.util.Set<String> ignoredArtifacts = new java.util.HashSet<>();
    private final Map<String,Map<String,Object>> jasonCuts = new LinkedHashMap<>();
    private final MSystem system;
    private final UseSystemApi api;
    private final Map<String, MObject> semanticObjectIndex;
    private final CodeGroundedRuntimeRuleRegistry registry;
    private final StateImage baseline;
    private final java.util.Set<String> tombstones = new java.util.HashSet<>();

    public NativeRuntimeMutationEngine(MSystem system, Map<String, MObject> semanticObjectIndex,
                                       CodeGroundedRuntimeRuleRegistry registry) {
        this.system = Objects.requireNonNull(system, "system");
        this.api = UseSystemApi.create(system, false);
        this.semanticObjectIndex = new LinkedHashMap<>(semanticObjectIndex);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.baseline = capture();
    }

    public MSystem system() { return system; }
    public record TargetBinding(String sourceIdentity,String targetIdentity,String kind,List<String> context) { }
    /** Read-only binding inventory of the very same active native model and state. */
    public synchronized List<TargetBinding> targetBindings() {
        var result=new ArrayList<TargetBinding>();
        for(var cls:system.model().classes()) {
            String source=DomainProjection.decode(cls.getAnnotationValue(DomainProjection.ANNOTATION,"sourceId64"));
            if(source.isBlank()) throw new NativeRuntimeProtocolException("NATIVE_CLASS_SOURCE_BINDING_MISSING:"+cls.name());
            var context=List.of("POLICY="+org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy.VERSION,"SEMANTIC_KIND="+DomainProjection.kind(cls));
            result.add(new TargetBinding(source,"class:"+cls.name(),cls instanceof org.tzi.use.uml.mm.MAssociationClass ? "MAssociationClass" : "MClass",context));
            for(var attribute:cls.attributes()) {
                var anchors=attribute.getAllAnnotations().entrySet().stream().filter(e->e.getKey().startsWith("PropertySource_"))
                    .map(e->DomainProjection.decode(e.getValue().getAnnotationValue("sourceId64"))).sorted().toList();
                for(String anchor:anchors.isEmpty() ? List.of(source) : anchors)
                    result.add(new TargetBinding(anchor,"attribute:"+cls.name()+"."+attribute.name(),"MAttribute",context));
            }
            for(var operation:cls.operations()) for(var entry:operation.getAllAnnotations().entrySet()) if(entry.getKey().startsWith("OperationSource_"))
                result.add(new TargetBinding(DomainProjection.decode(entry.getValue().getAnnotationValue("descriptorId64")),
                    "operation:"+cls.name()+"."+operation.name(),"MOperation",List.of("SIGNATURE="+DomainProjection.decode(operation.getAnnotationValue("CartagoOperation","signature64")))));
        }
        for(var association:system.model().associations()) {
            String source=association.getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null
                ? DomainProjection.decode(association.getAnnotationValue(MoiseDomainProjection.ROLE_ASSOCIATION,"identity64"))
                : association.getAnnotation("SubgroupContext")!=null ? DomainProjection.decode(association.getAnnotationValue("SubgroupContext","sourceId64"))
                : NativeUseModelBuilder.canonicalJson(List.of("projection-relation",association.associationEnds().stream().map(e->DomainProjection.decode(e.cls().getAnnotationValue(DomainProjection.ANNOTATION,"sourceId64"))).toList(),association.name()));
            result.add(new TargetBinding(source,"association:"+association.name(),"MAssociation",List.of("ENDPOINTS="+association.associationEnds().stream().map(e->e.cls().name()+"["+e.multiplicity()+"]").toList())));
        }
        for(var object:system.state().allObjects()) {
            String source=attribute(object,"semanticId");
            var aliases=semanticObjectIndex.entrySet().stream().filter(e->e.getValue()==object).map(Map.Entry::getKey).sorted().toList();
            if(aliases.isEmpty()) throw new NativeRuntimeProtocolException("NATIVE_OBJECT_SOURCE_BINDING_MISSING:"+object.name());
            var context=List.of("SOURCE_BINDINGS="+NativeUseModelBuilder.canonicalJson(aliases));
            result.add(new TargetBinding(source,object.name(),object instanceof org.tzi.use.uml.sys.MLinkObject ? "MLinkObject" : "MObject",context));
            for(var attribute:object.cls().allAttributes()) result.add(new TargetBinding(source,"value:"+object.name()+"."+attribute.name(),"MValue",context));
        }
        for(var link:system.state().allLinks()) {
            var identities=link.linkedObjects().stream().map(o->attribute(o,"semanticId")).toList();
            String source=link instanceof MObject object ? attribute(object,"semanticId")
                : "relation-binding:"+NativeUseModelBuilder.canonicalJson(List.of(link.association().name(),identities));
            result.add(new TargetBinding(source,"link:"+link.association().name()+":"+link.linkedObjects().stream().map(MObject::name).toList(),"MLink",List.of("ORDERED_ENDPOINT_IDENTITIES="+NativeUseModelBuilder.canonicalJson(identities))));
        }
        return List.copyOf(result);
    }
    public CodeGroundedRuntimeRuleRegistry registry() { return registry; }
    public synchronized MObject objectForSemanticId(String identity) { return semanticObjectIndex.get(identity); }
    public synchronized Map<String,List<String>> objectIdentityAliases() {
        var aliases=new java.util.TreeMap<String,List<String>>();
        for(var object:system.state().allObjects()) aliases.put(object.name(),semanticObjectIndex.entrySet().stream()
                .filter(e->e.getValue()==object).map(Map.Entry::getKey).sorted().toList());
        return Map.copyOf(aliases);
    }
    public synchronized StateImage savepoint() { return capture(); }
    /** Exact before/after native feature diff, never inferred from event or case-study names. */
    public synchronized java.util.Set<String> changedFeatures(StateImage before) {
        var after=capture();var changes=new java.util.TreeSet<String>();
        var names=new java.util.TreeSet<>(before.objects().keySet());names.addAll(after.objects().keySet());
        for(String name:names) {
            var old=before.objects().get(name);var current=after.objects().get(name);
            if(old==null || current==null || !old.className().equals(current.className())) {
                for(var image:java.util.stream.Stream.of(old,current).filter(java.util.Objects::nonNull).toList()) {
                    var cls=system.model().getClass(image.className());changes.add("class:"+cls.name());
                    cls.allParents().forEach(parent->changes.add("class:"+parent.name()));
                }
                continue;
            }
            var attributes=new java.util.TreeSet<>(old.attributes().keySet());attributes.addAll(current.attributes().keySet());
            for(String key:attributes) if(!java.util.Objects.equals(old.attributes().get(key),current.attributes().get(key))) {
                var attribute=system.model().getClass(current.className()).attribute(key,true);
                changes.add("attribute:"+attribute.owner().name()+"."+key);
            }
        }
        var oldLinks=new java.util.HashSet<>(before.links());var newLinks=new java.util.HashSet<>(after.links());
        oldLinks.stream().filter(l->!newLinks.contains(l)).forEach(l->changes.add("association:"+l.association()));
        newLinks.stream().filter(l->!oldLinks.contains(l)).forEach(l->changes.add("association:"+l.association()));
        return java.util.Set.copyOf(changes);
    }
    public synchronized void rollback(StateImage image) { restore(image); }

    /** Restores the static native state in the same MSystem before an authoritative resync. */
    public synchronized void resetToBaseline() {
        restore(baseline);
    }

    /**
     * Bootstrap CArtAgO records are observed runtime instances, not permanent declarations.
     * Reconcile them against a COMPLETE authoritative snapshot after applying its upserts.
     * An unavailable/partial source cannot prove absence and must not cause deletions.
     */
    public synchronized void reconcileAuthoritativeCartago(RuntimeSnapshot snapshot) {
        if (snapshot.sourceCompleteness().get("cartago") != Completeness.COMPLETE) return;
        java.util.Set<String> retained = new java.util.HashSet<>();
        for (var fact : snapshot.facts()) {
            String kind = textOrNull(fact.values(), "normalizedEventKind");
            if (kind == null || !java.util.Set.of("UPSERT_CARTAGO_WORKSPACE", "UPSERT_CARTAGO_AGENT_IDENTITY",
                    "UPSERT_CARTAGO_ARTIFACT", "UPSERT_CARTAGO_PROPERTY_SNAPSHOT", "SET_CARTAGO_FOCUS").contains(kind)) continue;
            if (fact.completeness() != Completeness.COMPLETE
                    || fact.projectionStatus() != ProjectionStatus.MATERIALIZED_FAITHFULLY) return;
            if(!kind.equals("SET_CARTAGO_FOCUS")) retained.add(required(fact.values(), "semanticId"));
            if (kind.equals("UPSERT_CARTAGO_WORKSPACE")) retained.add(required(fact.values(), "environmentSemanticId"));
            if (kind.equals("UPSERT_CARTAGO_ARTIFACT")) retained.add(required(fact.values(), "artifactTypeSemanticId"));
        }
        try {
            List<String> obsolete = semanticObjectIndex.entrySet().stream()
                .filter(e -> java.util.Set.of("artifact", "workspace").contains(DomainProjection.kind(e.getValue().cls()))
                    && !e.getValue().state(system.state()).attributeValue("uuid").isUndefined()
                    && semanticObjectIndex.entrySet().stream().noneMatch(alias->alias.getValue()==e.getValue() && retained.contains(alias.getKey())))
                .map(Map.Entry::getKey).toList();
            for (String id : obsolete) if (semanticObjectIndex.containsKey(id)) delete(id);
            for (String id : new ArrayList<>(properties.keySet())) if (!retained.contains(id)) removeProperty(id);
            reconcileCartagoRelations(snapshot);
        } catch (UseApiException error) { throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_AUTHORITATIVE_RECONCILIATION_FAILED", error); }
    }
    public synchronized ApplyResult apply(BridgeEntityId runtimeIdentity, BridgeRelationId binding,
                                           RuntimeFactKind factKind, ProjectionStatus projectionStatus,
                                           Completeness completeness, Map<String, Object> payload) {
        return apply(runtimeIdentity, binding, factKind, projectionStatus, completeness, payload, true);
    }

    public synchronized ApplyResult apply(BridgeEntityId runtimeIdentity, BridgeRelationId binding,
            RuntimeFactKind factKind, ProjectionStatus projectionStatus, Completeness completeness,
            Map<String, Object> payload, boolean validateStructure) {
        String identity = runtimeIdentity == null ? "<missing>" : runtimeIdentity.canonical();
        String normalized = textOrNull(payload, "normalizedEventKind");
        CodeGroundedRuntimeRuleRegistry.Rule rule = normalized == null ? null : registry.select(factKind, normalized);
        if (rule == null) rule = registry.evidenceRule(factKind);
        if (rule != null && rule.action() == CodeGroundedRuntimeRuleRegistry.Action.EVIDENCE_ONLY)
            return ApplyResult.evidence(rule.id(), identity, "RUNTIME_FACT_EVIDENCE_ONLY");
        if (projectionStatus != ProjectionStatus.MATERIALIZED_FAITHFULLY
                || completeness != Completeness.COMPLETE)
            return ApplyResult.evidence("R-NATIVE-EVIDENCE", identity, "NON_AUTHORITATIVE_RUNTIME_FACT");
        if (normalized == null)
            return ApplyResult.evidence("R-NATIVE-EVIDENCE", identity, "NO_NATIVE_MUTATION_KIND");
        if (rule == null)
            return ApplyResult.rejected("R-NATIVE-RULE-MISSING", "NATIVE_RUNTIME_RULE_MISSING:" + factKind + ":" + normalized);

        StateImage before = capture();
        try {
            validatePayloadIdentity(rule.action(), runtimeIdentity, payload);
            BoundTarget target;
            switch (rule.action()) {
                case UPSERT_CARTAGO_WORKSPACE -> target = upsertWorkspace(payload);
                case UPSERT_CARTAGO_AGENT_IDENTITY -> target = upsertCartagoAgent(payload);
                case DELETE_CARTAGO_AGENT_IDENTITY -> target = quitCartagoAgent(payload);
                case SET_CARTAGO_FOCUS -> target = cartagoFocus(payload);
                case UPSERT_CARTAGO_ARTIFACT -> target = upsertArtifact(payload);
                case UPSERT_CARTAGO_PROPERTY_SNAPSHOT -> target = upsertPropertySnapshot(payload);
                case UPSERT_MOISE_GROUP -> target = upsertMoiseGroup(payload);
                case UPSERT_MOISE_SCHEME -> target = upsertMoiseScheme(payload);
                case UPSERT_MOISE_GROUP_PARENT -> target = upsertMoiseGroupParent(payload);
                case UPSERT_JASON_AGENT_STATE -> target = upsertJasonAgent(payload);
                case UPSERT_CARTAGO_OPERATION -> target = upsertOperation(payload);
                case INSERT_MOISE_ROLE_LINK, DELETE_MOISE_ROLE_LINK -> target = moiseRoleLink(payload,rule.action()==CodeGroundedRuntimeRuleRegistry.Action.INSERT_MOISE_ROLE_LINK);
                case APPLY_CARTAGO_PROPERTY_DELTA, DELETE_CARTAGO_ARTIFACT -> {
                    String semanticId = required(payload, "semanticId");
                    if (ignoredArtifacts.contains(semanticId)) throw new EvidenceOnly("FRAMEWORK_BOOKKEEPING_ARTIFACT");
                    target = new BoundTarget(semanticId, requiredObject(semanticId));
                }
                default -> target = resolveTarget(runtimeIdentity, binding);
            }
            if (!rule.targetClasses().contains("*") && !rule.targetClasses().contains(target.object().cls().name()) && !rule.targetClasses().contains(DomainProjection.kind(target.object().cls())))
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_TARGET_CLASS_MISMATCH:" + target.object().cls().name());
            switch (rule.action()) {
                case ATTRIBUTE_SET -> setAttribute(target.object(), payload, false);
                case ATTRIBUTE_UNSET -> setAttribute(target.object(), payload, true);
                case LINK_INSERT -> link(payload, true);
                case LINK_DELETE -> link(payload, false);
                case APPLY_CARTAGO_PROPERTY_DELTA -> propertyDelta(target.semanticId(), payload);
                case DELETE_CARTAGO_ARTIFACT -> deleteArtifact(target.semanticId());
                case UPSERT_CARTAGO_WORKSPACE, UPSERT_CARTAGO_AGENT_IDENTITY,
                        UPSERT_CARTAGO_ARTIFACT, UPSERT_CARTAGO_PROPERTY_SNAPSHOT,
                        UPSERT_MOISE_GROUP, INSERT_MOISE_ROLE_LINK, DELETE_MOISE_ROLE_LINK,
                        UPSERT_MOISE_SCHEME, UPSERT_JASON_AGENT_STATE, UPSERT_CARTAGO_OPERATION, UPSERT_MOISE_GROUP_PARENT -> { }
                case SET_CARTAGO_FOCUS, DELETE_CARTAGO_AGENT_IDENTITY -> { }
                case EVIDENCE_ONLY -> throw new IllegalStateException("handled above");
            }
            if (validateStructure && !structureTransitionValid(before,observedMoiseRelation(rule.id()))) {
                restore(before);
                return ApplyResult.rejected(rule.id(), "NATIVE_RUNTIME_STRUCTURE_INVALID");
            }
            return new ApplyResult(Status.MATERIALIZED, rule.id(), target.semanticId(), target.object().name(),
                    observedMoiseRelation(rule.id()) ? "NATIVE_CONTEXTUAL_ROLE_LINKS_APPLIED:"+target.object().name() : "NATIVE_RUNTIME_MUTATION_APPLIED");
        } catch (EvidenceOnly deferred) {
            var policyIgnored=java.util.Set.copyOf(ignoredArtifacts);
            restore(before);
            ignoredArtifacts.addAll(policyIgnored);
            return ApplyResult.evidence(rule.id(), identity, deferred.getMessage());
        } catch (RuntimeException | UseApiException error) {
            try { restore(before); }
            catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            return ApplyResult.rejected(rule.id(), diagnostic(error));
        }
    }

    public synchronized boolean structureValid() {
        return system.state().checkStructure(new java.io.PrintWriter(new java.io.StringWriter()));
    }
    /** Keep faithfully observed role-cardinality failures visible while accepting unrelated state updates. */
    public synchronized boolean structureTransitionValid(StateImage before,boolean observedRoleMutation) {
        if(structureValid()) return true;
        boolean observedViolation=false;
        for(var association:system.model().associations()) {
            if(system.state().checkStructure(association,new java.io.PrintWriter(new java.io.StringWriter()),true)) continue;
            if(association.getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)==null
                || (!observedRoleMutation && !before.observedRoleViolations().contains(association.name()))) return false;
            if(!observedRoleMutation) {
                var previous=before.links().stream().filter(l->l.association().equals(association.name()))
                    .map(LinkImage::objectNames).collect(java.util.stream.Collectors.toSet());
                var current=system.state().allLinks().stream().filter(l->l.association()==association)
                    .map(l->l.linkedObjects().stream().map(MObject::name).toList()).collect(java.util.stream.Collectors.toSet());
                if(!previous.equals(current)) return false;
            }
            observedViolation=true;
        }
        return observedViolation;
    }
    public static boolean observedMoiseRelation(String rule) {
        return java.util.Set.of("R-MOISE-GROUP-UPSERT","R-MOISE-ROLE-LINK-INSERT","R-MOISE-ROLE-LINK-DELETE").contains(rule);
    }

    private BoundTarget upsertMoiseGroup(Map<String,Object> payload) throws UseApiException {
        var definition=org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationFromTree(requiredValue(payload,"organisationDefinition"));
        MoiseDomainProjection.validate(definition);
        String osId=required(payload,"organisationSpecSemanticId"), groupId=required(payload,"groupSpecSemanticId");
        if(!definition.metadata().semanticId().equals(osId)) throw new NativeRuntimeProtocolException("MOISE_OS_IDENTITY_CONFLICT");
        var groups=definition.structuralSpecification().groups().stream().filter(g -> g.metadata().semanticId().equals(groupId)).toList();
        if(groups.size()!=1) throw new NativeRuntimeProtocolException("MOISE_RUNTIME_GROUP_SPEC_UNRESOLVED");
        Object raw=requiredValue(payload,"players"); if(!(raw instanceof List<?> players)) throw new NativeRuntimeProtocolException("MOISE_PLAYERS_REQUIRED");
        var assignments=new ArrayList<Map.Entry<MObject,String>>(); var tuples=new java.util.HashSet<String>();
        for(Object item:players) {
            if(!(item instanceof Map<?,?> fields)) throw new NativeRuntimeProtocolException("MOISE_PLAYER_TUPLE_REQUIRED");
            Map<String,Object> player=new LinkedHashMap<>(); fields.forEach((k,v) -> player.put(k.toString(),v));
            String role=required(player,"roleSemanticId"), agentId=player.containsKey("agentSemanticId")
                ? required(player,"agentSemanticId") : required(player,"agentDeclarationSemanticId");
            if(!groups.get(0).roleSemanticIds().contains(role)) throw new NativeRuntimeProtocolException("PLAYER_ROLE_NOT_IN_GROUP:"+role);
            var agent=requiredObject(agentId);
            if(!DomainProjection.kind(agent.cls()).equals("agent-program")) throw new NativeRuntimeProtocolException("MOISE_PLAYER_AGENT_TYPE_REQUIRED");
            if(!tuples.add(agentId+"\u0000"+role)) throw new NativeRuntimeProtocolException("MOISE_PLAYER_TUPLE_DUPLICATE");
            assignments.add(Map.entry(agent,role));
        }
        boolean known=system.model().classes().stream().anyMatch(c -> DomainProjection.kind(c).equals("organisation")
            && c.getAnnotationValue(DomainProjection.ANNOTATION,"sourceId64").equals(DomainProjection.encode(osId)));
        if(!known) {
            MoiseDomainProjection.install(new UseModelApi(system.model()),List.of(definition),new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector());
            system.state().initializeStoredModelExtensions();
        }
        String osClass=DomainProjection.classFor(system.model(),"organisation",osId), groupClass=DomainProjection.classFor(system.model(),"group",groupId);
        for(var card:definition.structuralSpecification().groupRoleCardinalities()) if(card.groupId().equals(groupId)) {
            var relation=MoiseDomainProjection.roleAssociation(system.model(),osId,groupId,card.roleId());
            if(!Integer.toString(card.min()).equals(relation.getAnnotationValue(MoiseDomainProjection.ANNOTATION,"min"))
                    || !Integer.toString(card.max()).equals(relation.getAnnotationValue(MoiseDomainProjection.ANNOTATION,"max")))
                throw new NativeRuntimeProtocolException("MOISE_RUNTIME_SCHEMA_CHANGED");
        }
        String ownerId=required(payload,"organisationSemanticId"), id=required(payload,"semanticId");
        var owner=upsertObject(ownerId,osClass,required(payload,"organisationName")); setText(owner,"name",required(payload,"organisationName"));
        var group=upsertObject(id,groupClass,required(payload,"name")); setText(group,"name",required(payload,"name"));
        ensureLink(DomainProjection.relation("containsGroup",osClass,groupClass),owner,group);
        for(var link:new ArrayList<>(system.state().allLinks())) if(link.association().getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null
                && link.linkedObjects().contains(group)) deleteLink(link);
        for(var assignment:assignments) ensureLink(MoiseDomainProjection.roleAssociation(system.model(),osId,groupId,assignment.getValue()).name(),assignment.getKey(),group);
        return new BoundTarget(id,group);
    }
    private BoundTarget moiseRoleLink(Map<String,Object> payload,boolean insert) throws UseApiException {
        String groupId=required(payload,"groupSemanticId"), spec=required(payload,"groupSpecSemanticId"), os=required(payload,"organisationSpecSemanticId");
        var group=requiredObject(groupId); var agent=requiredObject(payload.containsKey("agentSemanticId")
            ? required(payload,"agentSemanticId") : required(payload,"agentDeclarationSemanticId"));
        if(!group.cls().name().equals(DomainProjection.classFor(system.model(),"group",spec)) || !DomainProjection.kind(agent.cls()).equals("agent-program"))
            throw new NativeRuntimeProtocolException("MOISE_ROLE_LINK_ENDPOINT_TYPE_MISMATCH");
        var association=MoiseDomainProjection.roleAssociation(system.model(),os,spec,required(payload,"roleSemanticId"));
        MObject[] pair={agent,group}; boolean present=system.state().hasLinkBetweenObjects(association,pair);
        if(insert && !present) MoiseDomainProjection.linkObject(api,semanticObjectIndex,association,agent,group);
        else if(!insert && present) for(var link:new ArrayList<>(system.state().allLinks()))
            if(link.association()==association && link.linkedObjects().equals(List.of(pair))) deleteLink(link);
        return new BoundTarget(groupId,group);
    }
    private BoundTarget upsertMoiseGroupParent(Map<String,Object> payload) throws UseApiException {
        String childId=required(payload,"semanticId"); var child=requiredObject(childId);
        if(!child.cls().name().equals(DomainProjection.classFor(system.model(),"group",required(payload,"groupSpecSemanticId"))))
            throw new NativeRuntimeProtocolException("MOISE_SUBGROUP_CHILD_TYPE_MISMATCH");
        String parentId=java.util.Objects.toString(requiredValue(payload,"parentSemanticId"));
        MObject parent=parentId.isEmpty() ? null : requiredObject(parentId);
        var owner=requiredObject(required(payload,"organisationSemanticId"));
        String ownership=DomainProjection.relation("containsGroup","Organization","Group");
        if(!system.state().hasLinkBetweenObjects(system.model().getAssociation(ownership),new MObject[]{owner,child}))
            throw new NativeRuntimeProtocolException("MOISE_SUBGROUP_ORGANISATION_MISMATCH");
        String association=null;
        if(parent!=null) {
            if(parent==child || !DomainProjection.kind(parent.cls()).equals("group")
                    || !parent.cls().name().equals(DomainProjection.classFor(system.model(),"group",required(payload,"parentSpecSemanticId"))))
                throw new NativeRuntimeProtocolException("MOISE_SUBGROUP_PARENT_TYPE_MISMATCH");
            if(!system.state().hasLinkBetweenObjects(system.model().getAssociation(ownership),new MObject[]{owner,parent}))
                throw new NativeRuntimeProtocolException("MOISE_SUBGROUP_ORGANISATION_MISMATCH");
            association=DomainProjection.relation("containsSubgroup",parent.cls().name(),child.cls().name());
            if(system.model().getAssociation(association)==null) throw new NativeRuntimeProtocolException("MOISE_SUBGROUP_CONTEXT_UNRESOLVED");
        }
        for(var existing:new ArrayList<>(system.state().allLinks()))
            if(existing.association().getAnnotation("SubgroupContext")!=null
                    && existing.linkedObjects().get(1)==child) deleteLink(existing);
        if(parent!=null) ensureLink(association,parent,child);
        return new BoundTarget(childId,child);
    }

    private BoundTarget resolveTarget(BridgeEntityId runtimeIdentity, BridgeRelationId binding) {
        if (runtimeIdentity == null || binding == null)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_REQUIRED");
        if (!"runtime-model-binding".equals(binding.relationKind()))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_KIND_REQUIRED");
        if (binding.endpoints().size() != 2 || !binding.endpoints().contains(runtimeIdentity))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BINDING_ENDPOINT_INVALID");
        BridgeEntityId staticIdentity = binding.endpoints().stream()
                .filter(value -> !value.equals(runtimeIdentity)).findFirst().orElseThrow();
        MObject target = semanticObjectIndex.get(staticIdentity.canonical());
        var property = properties.get(staticIdentity.canonical());
        if (target == null && property != null) target = semanticObjectIndex.get(property.owner());
        if (target == null)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_TRACE_TARGET_REQUIRED:" + staticIdentity.canonical());
        return new BoundTarget(staticIdentity.canonical(), target);
    }

    private BoundTarget upsertWorkspace(Map<String, Object> payload) throws UseApiException {
        requireStableIdentity(payload, "fullName", "uuid", "environmentSemanticId");
        String semanticId=required(payload,"semanticId");
        MObject workspace;
        String declaration=textOrNull(payload,"workspaceDeclarationSemanticId");
        if(declaration!=null) {
            workspace=requiredObject(declaration);
            if(!workspace.cls().name().equals("Workspace") || !attribute(workspace,"name").equals(required(payload,"name")))
                throw new NativeRuntimeProtocolException("WORKSPACE_DECLARATION_BINDING_MISMATCH");
            var uuid=workspace.state(system.state()).attributeValue("uuid");
            if(!uuid.isUndefined() && !uuid.equals(new StringValue(required(payload,"uuid")))) throw new NativeRuntimeProtocolException("WORKSPACE_DECLARATION_INCARNATION_REDIRECT");
            semanticObjectIndex.put(semanticId,workspace);
        } else workspace=upsertObject(semanticId,"Workspace",required(payload,"name"));
        for(String field:List.of("name","fullName","uuid","environmentSemanticId")) setText(workspace,field,required(payload,field));
        return new BoundTarget(semanticId,workspace);
    }

    private void installOrganisation(org.jacamo.bridge.contract.semantic.MoiseSemanticContract.OrganizationSemantic definition) throws UseApiException {
        String id=definition.metadata().semanticId();
        boolean known=system.model().classes().stream().anyMatch(c->DomainProjection.kind(c).equals("organisation")
            && c.getAnnotationValue(DomainProjection.ANNOTATION,"sourceId64").equals(DomainProjection.encode(id)));
        if(!known) {
            MoiseDomainProjection.install(new UseModelApi(system.model()),List.of(definition),new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector());
            system.state().initializeStoredModelExtensions();
        }
    }
    private BoundTarget upsertMoiseScheme(Map<String,Object> payload) throws UseApiException {
        var definition=org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationFromTree(requiredValue(payload,"organisationDefinition"));
        MoiseDomainProjection.validate(definition);
        String os=required(payload,"organisationSpecSemanticId"),spec=required(payload,"schemeSpecSemanticId"),id=required(payload,"semanticId");
        if(!os.equals(definition.metadata().semanticId())) throw new NativeRuntimeProtocolException("MOISE_OS_IDENTITY_CONFLICT");
        var matches=definition.functionalSpecification().schemes().stream().filter(s->s.metadata().semanticId().equals(spec)).toList();
        if(matches.size()!=1) throw new NativeRuntimeProtocolException("MOISE_RUNTIME_SCHEME_SPEC_UNRESOLVED");
        var source=matches.getFirst();
        var groups=stringList(payload,"responsibleGroupSemanticIds").stream().map(this::requiredObject).toList();
        for(var group:groups) if(!DomainProjection.kind(group.cls()).equals("group")) throw new NativeRuntimeProtocolException("MOISE_RESPONSIBLE_GROUP_TYPE_REQUIRED");
        var commitments=typedRows(payload,"commitments");
        for(var commitment:commitments) {
            var agent=requiredObject(required(commitment,"agentSemanticId"));
            if(!DomainProjection.kind(agent.cls()).equals("agent-program") || source.missions().stream().noneMatch(m->m.metadata().semanticId().equals(required(commitment,"missionSemanticId"))))
                throw new NativeRuntimeProtocolException("MOISE_COMMITMENT_BINDING_UNRESOLVED");
        }
        var goalStates=typedRows(payload,"goalStates");
        for(var goal:goalStates) if(source.goals().stream().noneMatch(g->g.metadata().semanticId().equals(required(goal,"goalSemanticId")))
                || !java.util.Set.of("SATISFIED","NOT_SATISFIED","WAITING","ENABLED").contains(required(goal,"state")))
            throw new NativeRuntimeProtocolException("MOISE_GOAL_STATE_BINDING_UNRESOLVED");
        boolean goalObservation="1.0.0".equals(payload.get("goalObservationVersion"));
        if(payload.containsKey("goalObservationVersion") && !goalObservation)throw new NativeRuntimeProtocolException("MOISE_GOAL_OBSERVATION_VERSION_UNSUPPORTED");
        if(goalObservation)for(var goal:goalStates)for(String relation:List.of("committedAgentSemanticIds","achievedAgentSemanticIds"))
            for(String agent:stringList(goal,relation))if(!DomainProjection.kind(requiredObject(agent).cls()).equals("agent-program"))
                throw new NativeRuntimeProtocolException("MOISE_GOAL_AGENT_BINDING_UNRESOLVED");
        installOrganisation(definition);
        var owner=upsertObject(required(payload,"organisationSemanticId"),DomainProjection.classFor(system.model(),"organisation",os),required(payload,"organisationName"));
        setText(owner,"name",required(payload,"organisationName"));
        var scheme=upsertObject(id,DomainProjection.classFor(system.model(),"scheme",spec),required(payload,"name")); setText(scheme,"name",required(payload,"name"));
        setText(scheme,"specSemanticId",spec);setText(scheme,"runtimeIdentity",required(payload,"runtimeIdentity"));setText(scheme,"sourceLayer","RUNTIME");
        setText(scheme,"goalStateEvidence",goalObservation?"OFFICIAL_SCHEME_BOARD_OBSERVABLE_V1":"SATISFACTION_ONLY");
        if(goalObservation)setText(scheme,"arguments",NativeUseModelBuilder.canonicalJson(requiredValue(payload,"schemeArguments")));
        ensureLink(DomainProjection.relation("containsScheme","Organization","Scheme"),owner,scheme);
        MoiseDomainProjection.materializeFunctional(api,semanticObjectIndex,new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector(),definition,source,scheme);
        var responsible=system.model().getAssociation(DomainProjection.relation("responsibleFor","Group","Scheme"));
        for(var link:new ArrayList<>(system.state().allLinks())) if(link.association()==responsible && link.linkedObjects().contains(scheme)) deleteLink(link);
        for(var group:groups) ensureLink(responsible.name(),group,scheme);
        var missionObjects=source.missions().stream().map(m->requiredObject(MoiseDomainProjection.functionalObjectId("mission",id,m.metadata().semanticId()))).toList();
        var committed=system.model().getAssociation(DomainProjection.relation("committedTo","Agent","Mission"));
        for(var link:new ArrayList<>(system.state().allLinks())) if(link.association()==committed && link.linkedObjects().stream().anyMatch(missionObjects::contains)) deleteLink(link);
        for(var commitment:commitments) ensureLink(committed.name(),requiredObject(required(commitment,"agentSemanticId")),
            requiredObject(MoiseDomainProjection.functionalObjectId("mission",id,required(commitment,"missionSemanticId"))));
        for(var goal:goalStates) {
            var object=requiredObject(MoiseDomainProjection.functionalObjectId("organisational-goal",id,required(goal,"goalSemanticId")));
            setText(object,"runtimeState",required(goal,"state"));setText(object,"stateEvidence",goalObservation?"OFFICIAL_SCHEME_BOARD_OBSERVABLE_V1":"SATISFACTION_ONLY");
            if(goalObservation) {
                setText(object,"arguments",NativeUseModelBuilder.canonicalJson(requiredValue(goal,"arguments")));
                for(var pair:Map.of("goalCommitment","committedAgentSemanticIds","goalAchievement","achievedAgentSemanticIds").entrySet()) {
                    var association=system.model().getAssociation(DomainProjection.relation(pair.getKey(),"Agent","OrganizationalGoal"));
                    for(var link:new ArrayList<>(system.state().allLinks()))if(link.association()==association && link.linkedObjects().contains(object))deleteLink(link);
                    for(String agent:stringList(goal,pair.getValue()))ensureLink(association.name(),requiredObject(agent),object);
                }
            }
        }
        return new BoundTarget(id,scheme);
    }
    private static List<Map<String,Object>> typedRows(Map<String,Object> payload,String key) {
        Object raw=requiredValue(payload,key);
        if(!(raw instanceof List<?> list)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ROWS_REQUIRED:"+key);
        return list.stream().map(org.jacamo.bridge.contract.CanonicalJson::object).toList();
    }
    private BoundTarget upsertOperation(Map<String,Object> payload) throws UseApiException {
        var descriptor=org.jacamo.bridge.contract.semantic.SemanticContractCodec.operationFromTree(requiredValue(payload,"operationDescriptor"));
        if(ignoredArtifacts.contains(descriptor.artifactSemanticId())) throw new EvidenceOnly("FRAMEWORK_BOOKKEEPING_OPERATION");
        var artifact=requiredObject(descriptor.artifactSemanticId());
        if(!DomainProjection.kind(artifact.cls()).equals("artifact")) throw new EvidenceOnly("FRAMEWORK_BOOKKEEPING_OPERATION");
        Object raw=payload.get("backingOperation");
        var backing=raw==null ? null : org.jacamo.bridge.contract.semantic.SemanticContractCodec.backingOperationFromTree(raw);
        try { DomainProjection.operation(new UseModelApi(system.model()),artifact.cls().name(),descriptor,backing); }
        catch(IllegalArgumentException unsupported) { throw new EvidenceOnly(unsupported.getMessage()); }
        return new BoundTarget(descriptor.metadata().semanticId(),artifact);
    }
    private BoundTarget upsertJasonAgent(Map<String,Object> payload) throws UseApiException {
        String declaration=required(payload,"agentDeclarationSemanticId"),runtime=required(payload,"semanticId"),name=required(payload,"name");
        var bindings=system.model().classes().stream().flatMap(c->c.getAllAnnotations().values().stream()
            .filter(a->a.getName().startsWith("AgentDeclaration_") && a.getAnnotationValue("id64").equals(DomainProjection.encode(declaration))).map(a->Map.entry(c,a))).toList();
        if(bindings.size()!=1) throw new EvidenceOnly("JASON_AGENT_PROGRAM_BINDING_UNAVAILABLE:"+declaration);
        var binding=bindings.getFirst(); var metadata=binding.getValue();
        if(!BridgeEntityId.parse(runtime).scope().equals(DomainProjection.decode(metadata.getAnnotationValue("project64"))))
            throw new NativeRuntimeProtocolException("JASON_AGENT_PROJECT_BINDING_MISMATCH");
        var beliefs=typedRows(payload,"beliefs"); var goals=typedRows(payload,"goals");
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(var literal:java.util.stream.Stream.concat(beliefs.stream(),goals.stream()).toList()) {
            String id=required(literal,"semanticId"); required(literal,"literal");
            if(!id.equals(DomainProjection.occurrenceId(beliefs.contains(literal) ? "runtime-belief" : "runtime-goal",runtime,required(literal,"sourceIdentity"))) || !ids.add(id))
                throw new NativeRuntimeProtocolException("JASON_LITERAL_IDENTITY_MISMATCH");
        }
        MObject agent;
        if(Integer.parseInt(metadata.getAnnotationValue("instances"))==1) {
            if(!name.equals(DomainProjection.decode(metadata.getAnnotationValue("name64")))) throw new NativeRuntimeProtocolException("JASON_AGENT_DECLARATION_BINDING_MISMATCH");
            agent=requiredObject(declaration);
        } else {
            agent=upsertObject(runtime,binding.getKey().name(),name); setText(agent,"name",name);
            setText(agent,"sourceUri",DomainProjection.decode(metadata.getAnnotationValue("sourceUri64"))); setText(agent,"host",DomainProjection.decode(metadata.getAnnotationValue("host64")));
        }
        semanticObjectIndex.put(runtime,agent);
        // A declaration may bind successive runtime incarnations to the same native agent.
        // Only its latest completed cut is current; older cuts remain in the journal.
        MObject observedAgent=agent;
        jasonCuts.keySet().removeIf(id->!id.equals(runtime) && semanticObjectIndex.get(id)==observedAgent);
        jasonCuts.put(runtime,Map.copyOf(payload));
        literalState(agent,exposedBeliefs(beliefs),"Belief","hasBelief"); literalState(agent,goals,"AgentGoal","hasGoal");
        return new BoundTarget(runtime,agent);
    }
    private List<Map<String,Object>> exposedBeliefs(List<Map<String,Object>> beliefs) {
        if(!NativeProjectionPolicy.verificationUses(system.model(),"Belief")) return List.of();
        for(var belief:beliefs) if(!(belief.get("domainAuthored") instanceof Boolean)
                || !(belief.get("authoritativeElsewhere") instanceof Boolean)
                || !(belief.get("predicateIndicator") instanceof String indicator) || indicator.isBlank())
            throw new NativeRuntimeProtocolException("JASON_BELIEF_PROVENANCE_REQUIRED");
        return beliefs.stream().filter(b->NativeProjectionPolicy.exposesBelief(system.model(),b)).toList();
    }

    /** Re-select from current authoritative cuts when a valid OCL profile is installed/replaced. */
    public synchronized void refreshBeliefProjection() {
        var before=capture();
        try {
            java.util.Set<MObject> observed=new java.util.HashSet<>();
            for(var cut:jasonCuts.entrySet()) {
                var agent=semanticObjectIndex.get(cut.getKey()); if(agent==null) continue;
                literalState(agent,exposedBeliefs(typedRows(cut.getValue(),"beliefs")),"Belief","hasBelief"); observed.add(agent);
            }
            var seeds=new LinkedHashMap<MObject,List<Map<String,Object>>>();
            for(var annotation:system.model().getClass("Belief").getAllAnnotations().values()) {
                if(!annotation.getName().startsWith("InitialBelief_")) continue;
                var agent=semanticObjectIndex.get(DomainProjection.decode(annotation.getAnnotationValue("agent64")));
                if(agent==null || observed.contains(agent)) continue;
                seeds.computeIfAbsent(agent,k->new ArrayList<>()).add(Map.of(
                    "semanticId",DomainProjection.decode(annotation.getAnnotationValue("id64")),
                    "literal",DomainProjection.decode(annotation.getAnnotationValue("literal64")),"sourceLayer","INITIAL"));
            }
            for(var seed:seeds.entrySet()) literalState(seed.getKey(),NativeProjectionPolicy.verificationUses(system.model(),"Belief")
                    ? seed.getValue() : List.of(),"Belief","hasBelief");
        } catch(RuntimeException|UseApiException error) {
            restore(before); throw new NativeRuntimeProtocolException("BELIEF_SELECTION_REFRESH_FAILED",error);
        }
    }
    private void literalState(MObject agent,List<Map<String,Object>> values,String type,String relation) throws UseApiException {
        var association=system.model().getAssociation(DomainProjection.relation(relation,"Agent",type));
        java.util.Set<String> retained=values.stream().map(v->required(v,"semanticId")).collect(java.util.stream.Collectors.toSet());
        for(var link:new ArrayList<>(system.state().allLinks())) if(link.association()==association && link.linkedObjects().getFirst()==agent) {
            var object=link.linkedObjects().get(1);
            if(!retained.contains(attribute(object,"semanticId"))) {
                String id=attribute(object,"semanticId"); delete(id);
                tombstones.remove(id); // Literal observations may leave and re-enter a completed cycle cut.
            }
        }
        for(var literal:values) {
            var object=upsertObject(required(literal,"semanticId"),type,(type.equals("Belief") ? "belief_" : "goal_")+DomainProjection.hash(required(literal,"semanticId")));
            setText(object,"literal",required(literal,"literal")); setText(object,"sourceLayer",literal.getOrDefault("sourceLayer","RUNTIME").toString()); ensureLink(association.name(),agent,object);
        }
    }

    private BoundTarget upsertCartagoAgent(Map<String,Object> payload) throws UseApiException {
        String id=required(payload,"semanticId"); MObject object=semanticObjectIndex.get(id);
        String declaration=textOrNull(payload,"agentDeclarationSemanticId");
        if(declaration!=null) {
            MObject expected;
            var matches=system.model().classes().stream().flatMap(c->c.getAllAnnotations().values().stream()
                .filter(a->a.getName().startsWith("AgentDeclaration_") && a.getAnnotationValue("id64").equals(DomainProjection.encode(declaration)))
                .map(a->Map.entry(c,a))).toList();
            if(matches.size()==1 && Integer.parseInt(matches.getFirst().getValue().getAnnotationValue("instances"))>1) {
                var binding=matches.getFirst(); var metadata=binding.getValue();
                var runtime=BridgeEntityId.parse(required(payload,"agentRuntimeSemanticId"));
                String name=required(payload,"name");
                if(!runtime.authority().equals("jason") || !runtime.dimension().equals("agent") || !runtime.kind().equals("runtime-agent")
                    || !runtime.localId().equals(name) || !runtime.scope().equals(DomainProjection.decode(metadata.getAnnotationValue("project64"))))
                    throw new NativeRuntimeProtocolException("AGENT_RUNTIME_INSTANCE_BINDING_MISMATCH");
                expected=upsertObject(runtime.canonical(),binding.getKey().name(),name);
                setText(expected,"name",name);
                setText(expected,"sourceUri",DomainProjection.decode(metadata.getAnnotationValue("sourceUri64")));
                setText(expected,"host",DomainProjection.decode(metadata.getAnnotationValue("host64")));
            } else {
                expected=requiredObject(declaration);
                if(!attribute(expected,"name").equals(required(payload,"name")))
                    throw new NativeRuntimeProtocolException("AGENT_DECLARATION_NAME_BINDING_MISMATCH");
            }
            if(object!=null && object!=expected) throw new NativeRuntimeProtocolException("AGENT_RUNTIME_INSTANCE_REDIRECT");
            object=expected;
            if(!DomainProjection.kind(object.cls()).equals("agent-program")) throw new NativeRuntimeProtocolException("AGENT_DECLARATION_TYPE_REQUIRED");
            semanticObjectIndex.put(id,object);
        }
        if(object==null) throw new EvidenceOnly("AGENT_RUNTIME_DECLARATION_BINDING_UNAVAILABLE:"+id);
        var workspace=requiredObject(required(payload,"workspaceSemanticId"));
        ensureLink(DomainProjection.relation("memberOf","Agent","Workspace"),object,workspace);
        return new BoundTarget(id,object);
    }

    private BoundTarget cartagoFocus(Map<String,Object> payload) throws UseApiException {
        String artifactId=required(payload,"artifactSemanticId"),agentId=required(payload,"agentSemanticId");
        String origin=textOrNull(payload,"artifactTypeOrigin");
        if(origin!=null && !DomainProjection.domainArtifact(required(payload,"artifactTypeJavaClassName"),origin)) {
            ignoredArtifacts.add(artifactId); throw new EvidenceOnly("PLATFORM_OR_UNRESOLVED_FOCUS_TRACE_ONLY:"+origin);
        }
        if(ignoredArtifacts.contains(artifactId)) throw new EvidenceOnly("FRAMEWORK_BOOKKEEPING_FOCUS");
        var artifact=requiredObject(artifactId); var agent=semanticObjectIndex.get(agentId);
        if(agent==null) throw new EvidenceOnly("CARTAGO_FOCUS_AGENT_BINDING_UNAVAILABLE:"+agentId);
        if(!DomainProjection.kind(artifact.cls()).equals("artifact") || !DomainProjection.kind(agent.cls()).equals("agent-program"))
            throw new NativeRuntimeProtocolException("CARTAGO_FOCUS_ENDPOINT_TYPE_MISMATCH");
        if(!(payload.get("focused") instanceof Boolean focused)) throw new NativeRuntimeProtocolException("CARTAGO_FOCUS_STATE_REQUIRED");
        String association=DomainProjection.relation("focuses","Agent","Artifact");
        if(focused) ensureLink(association,agent,artifact);
        else for(var link:new ArrayList<>(system.state().allLinks()))
            if(link.association().name().equals(association) && link.linkedObjects().equals(List.of(agent,artifact))) deleteLink(link);
        return new BoundTarget(artifactId,artifact);
    }

    private BoundTarget quitCartagoAgent(Map<String,Object> payload) throws UseApiException {
        String id=required(payload,"semanticId"); var agent=semanticObjectIndex.get(id);
        if(agent==null) throw new EvidenceOnly("CARTAGO_QUIT_AGENT_BINDING_UNAVAILABLE:"+id);
        var workspace=requiredObject(required(payload,"workspaceSemanticId"));
        for(var link:new ArrayList<>(system.state().allLinks())) {
            if(link.association().name().equals(DomainProjection.relation("memberOf","Agent","Workspace"))
                    && link.linkedObjects().equals(List.of(agent,workspace))) deleteLink(link);
            else if(link.association().name().equals(DomainProjection.relation("focuses","Agent","Artifact"))
                    && link.linkedObjects().getFirst()==agent
                    && attribute(link.linkedObjects().get(1),"workspaceSemanticId").equals(required(payload,"workspaceSemanticId"))) deleteLink(link);
        }
        semanticObjectIndex.remove(id);
        return new BoundTarget(id,agent);
    }

    private void reconcileCartagoRelations(RuntimeSnapshot snapshot) throws UseApiException {
        String joins=DomainProjection.relation("memberOf","Agent","Workspace"),focuses=DomainProjection.relation("focuses","Agent","Artifact");
        var expected=new java.util.HashSet<List<String>>();
        var agents=new java.util.HashSet<String>();
        for(var fact:snapshot.facts()) {
            String kind=textOrNull(fact.values(),"normalizedEventKind");
            MObject first,second; String association;
            if("UPSERT_CARTAGO_AGENT_IDENTITY".equals(kind)) {
                agents.add(required(fact.values(),"semanticId"));
                first=semanticObjectIndex.get(required(fact.values(),"semanticId"));
                second=semanticObjectIndex.get(required(fact.values(),"workspaceSemanticId")); association=joins;
            } else if("SET_CARTAGO_FOCUS".equals(kind) && Boolean.TRUE.equals(fact.values().get("focused"))) {
                first=semanticObjectIndex.get(required(fact.values(),"agentSemanticId"));
                second=semanticObjectIndex.get(required(fact.values(),"artifactSemanticId")); association=focuses;
            } else continue;
            if(first!=null && second!=null) expected.add(List.of(association,first.name(),second.name()));
        }
        for(var link:new ArrayList<>(system.state().allLinks()))
            if(java.util.Set.of(joins,focuses).contains(link.association().name())
                    && !expected.contains(List.of(link.association().name(),link.linkedObjects().get(0).name(),link.linkedObjects().get(1).name()))) deleteLink(link);
        semanticObjectIndex.keySet().removeIf(id->id.startsWith("cartago:agent:") && !agents.contains(id));
    }

    private BoundTarget upsertArtifact(Map<String,Object> payload) throws UseApiException {
        requireStableIdentity(payload,"name","uuid","workspaceSemanticId");
        String id=required(payload,"semanticId"),fqcn=required(payload,"artifactTypeJavaClassName");
        String origin=textOrNull(payload,"artifactTypeOrigin");
        if(origin==null) {
            // Exported native classifiers retain their exact verified provider proof.
            // Packaged replay can use it without loading producer-side JaCaMo classes.
            try { origin=system.model().getClass(DomainProjection.artifactClass(system.model(),fqcn))
                    .getAnnotationValue("ArtifactProvider","origin"); }
            catch(IllegalArgumentException missing) { origin=null; }
            if(!"APPLICATION".equals(origin)) origin=DomainProjection.artifactOrigin(fqcn);
        }
        if(!DomainProjection.domainArtifact(fqcn,origin)) { ignoredArtifacts.add(id); throw new EvidenceOnly("PLATFORM_OR_UNRESOLVED_ARTIFACT_TRACE_ONLY:"+origin); }
        var workspace=requiredObject(required(payload,"workspaceSemanticId"));
        String declaration=textOrNull(payload,"artifactDeclarationSemanticId");
        if(declaration!=null) {
            var proofs=system.model().getClass("Artifact").getAllAnnotations().values().stream()
                .filter(a->a.getName().startsWith("ArtifactDeclaration_") && a.getAnnotationValue("id64").equals(DomainProjection.encode(declaration))).toList();
            if(proofs.size()!=1) throw new NativeRuntimeProtocolException("ARTIFACT_DECLARATION_PROOF_REQUIRED");
            var proof=proofs.getFirst();
            if(!fqcn.equals(DomainProjection.decode(proof.getAnnotationValue("javaClass64")))
                    || !required(payload,"name").equals(DomainProjection.decode(proof.getAnnotationValue("name64")))
                    || requiredObject(DomainProjection.decode(proof.getAnnotationValue("workspace64")))!=workspace)
                throw new NativeRuntimeProtocolException("ARTIFACT_DECLARATION_BINDING_MISMATCH");
        }
        int associationCount = system.model().associations().size();
        String type=DomainProjection.ensureArtifactClass(new UseModelApi(system.model()),fqcn,origin);
        if (system.model().associations().size() != associationCount) {
            system.state().initializeStoredModelExtensions();
        }
        MObject artifact;
        if(declaration!=null && semanticObjectIndex.containsKey(declaration)) {
            artifact=requiredObject(declaration);
            if(!artifact.cls().name().equals(type) || !attribute(artifact,"name").equals(required(payload,"name")))
                throw new NativeRuntimeProtocolException("ARTIFACT_DECLARATION_BINDING_MISMATCH");
            var uuid=artifact.state(system.state()).attributeValue("uuid");
            if(!uuid.isUndefined() && !uuid.equals(new StringValue(required(payload,"uuid")))) throw new NativeRuntimeProtocolException("ARTIFACT_DECLARATION_INCARNATION_REDIRECT");
            semanticObjectIndex.put(id,artifact);
        } else artifact=upsertObject(id,type,required(payload,"name"));
        if(declaration!=null) semanticObjectIndex.put(declaration,artifact);
        for(String field:List.of("name","uuid","workspaceSemanticId","artifactTypeSemanticId")) setText(artifact,field,required(payload,field));
        setText(artifact,"creatorAgentSemanticId",requiredTextAllowEmpty(payload,"creatorAgentSemanticId"));
        ensureLink(DomainProjection.relation("locatedIn","Workspace",type),workspace,artifact);
        if(payload.containsKey("properties")) propertyDelta(id,Map.of("properties",payload.get("properties"),"removedPropertySemanticIds",List.of()));
        return new BoundTarget(id,artifact);
    }

    private BoundTarget upsertPropertySnapshot(Map<String,Object> payload) throws UseApiException {
        String owner=required(payload,"artifactSemanticId");
        if(ignoredArtifacts.contains(owner)) throw new EvidenceOnly("FRAMEWORK_BOOKKEEPING_PROPERTY");
        MObject artifact=requiredObject(owner);
        List<String> values=stringList(payload,"values"),types=stringList(payload,"valueTypes");
        if(values.size()!=types.size()) throw new NativeRuntimeProtocolException("C09_VALUE_TYPE_ARITY_MISMATCH");
        String type=DomainProjection.propertyType(types),id=required(payload,"semanticId"),name=DomainProjection.propertyName(required(payload,"name"));
        Value value=DomainProjection.propertyValue(type,values.getFirst()); // Validate before extending the same native schema.
        var previous=properties.get(id);
        if(previous!=null && (!previous.owner().equals(owner)||!previous.name().equals(name)))
            throw new NativeRuntimeProtocolException("C09_PROPERTY_OWNER_MISMATCH:"+id);
        if(properties.entrySet().stream().anyMatch(e->!e.getKey().equals(id)&&e.getValue().equals(new PropertyBinding(owner,name))))
            throw new NativeRuntimeProtocolException("C09_PROPERTY_NAME_AMBIGUOUS:"+name);
        MAttribute attribute=artifact.cls().attribute(name,true);
        if(attribute!=null && !attribute.type().toString().equals(type)) throw new NativeRuntimeProtocolException("C09_PROPERTY_TYPE_CHANGED:"+name);
        if(attribute==null) {
            attribute=new UseModelApi(system.model()).createAttribute(artifact.cls().name(),name,type);
            system.state().initializeStoredModelExtensions();
        }
        DomainProjection.propertyTrace(attribute,id);
        api.setAttributeValueEx(artifact,attribute,value); properties.put(id,new PropertyBinding(owner,name));
        return new BoundTarget(id,artifact);
    }
    private void removeProperty(String id) throws UseApiException {
        var property=properties.remove(id);
        if(property==null) throw new NativeRuntimeProtocolException("C09_PROPERTY_UNRESOLVED:"+id);
        MObject owner=semanticObjectIndex.get(property.owner());
        if(owner!=null) api.setAttributeValueEx(owner,requiredAttribute(owner,property.name()),UndefinedValue.instance);
    }
    private void propertyDelta(String owner,Map<String,Object> payload) throws UseApiException {
        for(String id:stringList(payload,"removedPropertySemanticIds")) {
            if(!properties.containsKey(id) || !properties.get(id).owner().equals(owner)) throw new NativeRuntimeProtocolException("C09_PROPERTY_OWNER_MISMATCH:"+id);
            removeProperty(id);
        }
        Object raw=requiredValue(payload,"properties");
        if(!(raw instanceof List<?> list)) throw new NativeRuntimeProtocolException("C09_TYPED_PROPERTIES_REQUIRED");
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(Object item:list) {
            Map<String,Object> property=org.jacamo.bridge.contract.CanonicalJson.object(item);
            if(!owner.equals(required(property,"artifactSemanticId"))||!ids.add(required(property,"semanticId")))
                throw new NativeRuntimeProtocolException("C09_PROPERTY_IDENTITY_MISMATCH");
            upsertPropertySnapshot(property);
        }
    }
    private static final class EvidenceOnly extends RuntimeException { EvidenceOnly(String message) { super(message); } }
    private void requireStableIdentity(Map<String, Object> payload, String... attributes) {
        MObject existing = semanticObjectIndex.get(required(payload, "semanticId"));
        if (existing == null) return;
        for (String attribute : attributes)
            if (!new StringValue(required(payload, attribute)).equals(existing.state(system.state()).attributeValue(attribute)))
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INCARNATION_REDIRECT:" + attribute);
    }

    private String attribute(MObject object, String name) {
        Value value = object.state(system.state()).attributeValue(name);
        if (!(value instanceof StringValue text)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_IDENTITY_UNDEFINED:" + name);
        return text.value();
    }

    private void validatePayloadIdentity(CodeGroundedRuntimeRuleRegistry.Action action, BridgeEntityId id,
            Map<String, Object> payload) {
        if(action==CodeGroundedRuntimeRuleRegistry.Action.UPSERT_JASON_AGENT_STATE) {
            if(id==null || !id.authority().equals("jason") || !id.dimension().equals("agent") || !id.kind().equals("runtime-agent")
                    || !id.canonical().equals(required(payload,"semanticId")) || !id.localId().equals(required(payload,"name")))
                throw new NativeRuntimeProtocolException("JASON_RUNTIME_TYPED_IDENTITY_MISMATCH");
            return;
        }
        if(java.util.Set.of(CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_GROUP,
                CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_SCHEME,
                CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_GROUP_PARENT,
                CodeGroundedRuntimeRuleRegistry.Action.INSERT_MOISE_ROLE_LINK,
                CodeGroundedRuntimeRuleRegistry.Action.DELETE_MOISE_ROLE_LINK).contains(action)) {
            String expectedKind=action==CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_GROUP ? "group-board"
                : action==CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_SCHEME ? "scheme-board"
                : action==CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_GROUP_PARENT ? "group-parent" : "role-player";
            if(id==null || !id.authority().equals("moise") || !id.dimension().equals("organisation")
                    || !id.kind().equals(expectedKind) || !id.canonical().equals(required(payload,"runtimeIdentity")))
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_TYPED_IDENTITY_MISMATCH:"+expectedKind);
            if(java.util.Set.of(CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_GROUP,CodeGroundedRuntimeRuleRegistry.Action.UPSERT_MOISE_SCHEME).contains(action) && (!id.scope().equals(required(payload,"organisationName"))
                    || !id.localId().equals(required(payload,"name"))))
                throw new NativeRuntimeProtocolException("MOISE_RUNTIME_GROUP_CONTEXT_MISMATCH");
            return;
        }
        String kind, scope, local, incarnation;
        switch (action) {
            case UPSERT_CARTAGO_WORKSPACE -> {
                kind = "workspace"; scope = required(payload, "environmentId");
                local = required(payload, "fullName"); incarnation = required(payload, "uuid");
            }
            case UPSERT_CARTAGO_AGENT_IDENTITY, DELETE_CARTAGO_AGENT_IDENTITY -> {
                kind="agent"; scope=attribute(requiredObject(required(payload,"workspaceSemanticId")),"fullName");
                local=required(payload,"globalId"); incarnation=Long.toString(requiredInteger(payload,"localId"));
            }
            case UPSERT_CARTAGO_ARTIFACT -> {
                kind = "artifact"; scope = attribute(requiredObject(required(payload, "workspaceSemanticId")), "fullName");
                local = required(payload, "name"); incarnation = required(payload, "uuid");
            }
            case APPLY_CARTAGO_PROPERTY_DELTA, DELETE_CARTAGO_ARTIFACT -> {
                String semanticId = required(payload, "semanticId");
                if (ignoredArtifacts.contains(semanticId)) return;
                var artifact = requiredObject(semanticId);
                kind = "artifact"; scope = attribute(requiredObject(attribute(artifact, "workspaceSemanticId")), "fullName");
                local = attribute(artifact, "name"); incarnation = attribute(artifact, "uuid");
            }
            case UPSERT_CARTAGO_PROPERTY_SNAPSHOT -> {
                kind = "observable-property-snapshot"; scope = required(payload, "artifactSemanticId");
                local = required(payload, "propertyId"); incarnation = "snapshot";
            }
            case UPSERT_CARTAGO_OPERATION -> {
                var descriptor=org.jacamo.bridge.contract.semantic.SemanticContractCodec.operationFromTree(requiredValue(payload,"operationDescriptor"));
                kind="operation-descriptor"; scope=descriptor.artifactSemanticId(); local=descriptor.keyId(); incarnation="snapshot";
                if(!descriptor.metadata().semanticId().equals(required(payload,"semanticId"))
                        || !required(payload,"semanticId").equals("cartago:operation:"+scope+":"+local))
                    throw new NativeRuntimeProtocolException("CARTAGO_OPERATION_TYPED_IDENTITY_MISMATCH");
            }
            case SET_CARTAGO_FOCUS -> {
                kind="focus"; scope=required(payload,"artifactSemanticId");
                local=required(payload,"agentSemanticId"); incarnation="relation";
            }
            default -> { return; } // Generic mutations require their explicit runtime-model-binding instead.
        }
        if (id == null || !id.equals(new BridgeEntityId("cartago", "environment", kind, scope, local, incarnation)))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_TYPED_IDENTITY_MISMATCH:" + kind);
    }

    private void deleteArtifact(String id) throws UseApiException { delete(id); }
    private void delete(String id) throws UseApiException {
        MObject object=requiredObject(id); api.deleteObjectEx(object);
        List<String> aliases=semanticObjectIndex.entrySet().stream().filter(e->e.getValue()==object).map(Map.Entry::getKey).toList();
        aliases.forEach(semanticObjectIndex::remove); aliases.forEach(jasonCuts::remove); tombstones.addAll(aliases);
        properties.entrySet().removeIf(e->aliases.contains(e.getValue().owner()));
    }
    private static List<String> stringList(Map<String, Object> payload, String key) {
        Object raw = requiredValue(payload, key);
        if (!(raw instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String)))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_STRING_LIST_REQUIRED:" + key);
        return list.stream().map(String.class::cast).toList();
    }

    private MObject upsertObject(String semanticId, String className, String human) throws UseApiException {
        if (tombstones.contains(semanticId)) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_DISPOSED_INCARNATION:" + semanticId);
        MObject existing = semanticObjectIndex.get(semanticId);
        if (existing != null) {
            if (!className.equals(existing.cls().name()))
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_OBJECT_CLASS_MISMATCH:" + semanticId);
            return existing;
        }
        var cls = system.model().getClass(className);
        if (cls == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_CLASS_MISSING:" + className);
        String objectName = NativeUseStateBuilder.objectName(className, human, semanticId);
        MObject byName = system.state().objectByName(objectName);
        if (byName != null) objectName += "_" + DomainProjection.hash(semanticId);
        MObject object = api.createObjectEx(cls, objectName);
        setText(object, "semanticId", semanticId);
        semanticObjectIndex.put(semanticId, object);
        return object;
    }

    private MObject requiredObject(String semanticId) {
        MObject object = semanticObjectIndex.get(semanticId);
        if (object == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_OBJECT_UNRESOLVED:" + semanticId);
        return object;
    }

    private void ensureLink(String associationName, MObject first, MObject second) throws UseApiException {
        MAssociation association = system.model().getAssociation(associationName);
        if (association == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ASSOCIATION_MISSING:" + associationName);
        MObject[] connected = {first, second};
        if(association instanceof org.tzi.use.uml.mm.MAssociationClass role)
            MoiseDomainProjection.linkObject(api,semanticObjectIndex,role,first,second);
        else if (!system.state().hasLinkBetweenObjects(association, connected)) api.createLinkEx(association, connected);
    }
    private void deleteLink(MLink link) throws UseApiException {
        api.deleteLinkEx(link);
        if(link instanceof MObject object) semanticObjectIndex.entrySet().removeIf(e->e.getValue()==object);
    }

    private void setText(MObject object, String name, String value) throws UseApiException {
        api.setAttributeValueEx(object, requiredAttribute(object, name), new StringValue(value));
    }

    private void setInteger(MObject object, String name, int value) throws UseApiException {
        api.setAttributeValueEx(object, requiredAttribute(object, name), IntegerValue.valueOf(value));
    }

    private void setBoolean(MObject object, String name, boolean value) throws UseApiException {
        api.setAttributeValueEx(object, requiredAttribute(object, name), BooleanValue.get(value));
    }

    private MAttribute requiredAttribute(MObject object, String name) {
        MAttribute attribute = object.cls().attribute(name, true);
        if (attribute == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ATTRIBUTE_MISSING:" + name);
        return attribute;
    }

    private void setAttribute(MObject object, Map<String, Object> payload, boolean unset) throws UseApiException {
        String name = required(payload, "attribute");
        MAttribute attribute = object.cls().attribute(name, true);
        if (attribute == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ATTRIBUTE_MISSING:" + name);
        Value value = unset ? UndefinedValue.instance : value(payload);
        if (!value.isUndefined() && !value.type().conformsTo(attribute.type()))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ATTRIBUTE_TYPE_MISMATCH:" + name);
        api.setAttributeValueEx(object, attribute, value);
    }

    private void link(Map<String, Object> payload, boolean insert) throws UseApiException {
        String associationName = required(payload, "association");
        Object raw = payload.get("participantSemanticIds");
        if (!(raw instanceof List<?> ids) || ids.size() < 2)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PARTICIPANT_IDS_REQUIRED");
        List<MObject> objects = new ArrayList<>();
        for (Object id : ids) {
            if (!(id instanceof String semanticId) || semanticId.isBlank())
                throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PARTICIPANT_ID_INVALID");
            MObject object = semanticObjectIndex.get(semanticId);
            if (object == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PARTICIPANT_UNRESOLVED:" + semanticId);
            objects.add(object);
        }
        MAssociation association = system.model().getAssociation(associationName);
        if (association == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ASSOCIATION_MISSING:" + associationName);
        MObject[] connected = objects.toArray(MObject[]::new);
        boolean present = system.state().hasLinkBetweenObjects(association, connected);
        if (insert && !present) {
            if(association instanceof org.tzi.use.uml.mm.MAssociationClass role) {
                if(connected.length!=2 || role.getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)==null)
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_ASSOCIATION_CLASS_CONTEXT_REQUIRED");
                MoiseDomainProjection.linkObject(api,semanticObjectIndex,role,connected[0],connected[1]);
            } else api.createLinkEx(association, connected);
        }
        if (!insert && present) for(var existing:new ArrayList<>(system.state().allLinks()))
            if(existing.association()==association && existing.linkedObjects().equals(objects)) deleteLink(existing);
    }

    private StateImage capture() {
        Map<String, ObjectImage> attributes = new LinkedHashMap<>();
        system.state().allObjects().stream().sorted(Comparator.comparing(MObject::name)).forEach(object -> {
            Map<String, Value> values = new LinkedHashMap<>();
            object.cls().allAttributes().forEach(attribute ->
                    values.put(attribute.name(), java.util.Objects.requireNonNullElse(object.state(system.state()).attributeValue(attribute), UndefinedValue.instance)));
            attributes.put(object.name(), new ObjectImage(object.cls().name(), Map.copyOf(values), object));
        });
        List<LinkImage> links = system.state().allLinks().stream()
                .sorted(Comparator.comparing((MLink link) -> link.association().name())
                        .thenComparing(link -> link.linkedObjects().stream().map(MObject::name).toList().toString()))
                .map(link -> new LinkImage(link.association().name(),
                        link.linkedObjects().stream().map(MObject::name).toList(), link)).toList();
        Map<String, String> names = new LinkedHashMap<>();
        semanticObjectIndex.forEach((id, object) -> names.put(id, object.name()));
        var roleViolations=system.model().associations().stream().filter(a->a.getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null
            && !system.state().checkStructure(a,new java.io.PrintWriter(new java.io.StringWriter()),true))
            .map(MAssociation::name).collect(java.util.stream.Collectors.toSet());
        return new StateImage(Map.copyOf(attributes), links, Map.copyOf(names), java.util.Set.copyOf(tombstones), Map.copyOf(properties),
            java.util.Set.copyOf(ignoredArtifacts),java.util.Set.copyOf(roleViolations),Map.copyOf(jasonCuts));
    }

    private void restore(StateImage image) {
        try {
            for (MLink link : new ArrayList<>(system.state().allLinks())) api.deleteLinkEx(link);
            for (MObject object : new ArrayList<>(system.state().allObjects()))
                if (!image.objects().containsKey(object.name())) api.deleteObjectEx(object);
            for (var objectEntry : image.objects().entrySet()) {
                MObject object = system.state().objectByName(objectEntry.getKey());
                if (object != objectEntry.getValue().original()) {
                    if (object != null) api.deleteObjectEx(object);
                    object = objectEntry.getValue().original();
                    system.state().restoreObject(new org.tzi.use.uml.sys.MObjectState(object));
                }
                for (var attribute : object.cls().allAttributes()) api.setAttributeValueEx(object, attribute, UndefinedValue.instance);
                for (var attributeEntry : objectEntry.getValue().attributes().entrySet())
                    api.setAttributeValueEx(object, object.cls().attribute(attributeEntry.getKey(), true), attributeEntry.getValue());
            }
            semanticObjectIndex.clear();
            image.semanticNames().forEach((id, name) -> semanticObjectIndex.put(id, system.state().objectByName(name)));
            tombstones.clear(); tombstones.addAll(image.tombstones());
            properties.clear(); properties.putAll(image.properties());
            ignoredArtifacts.clear(); ignoredArtifacts.addAll(image.ignoredArtifacts());
            jasonCuts.clear(); jasonCuts.putAll(image.jasonCuts());
            for (LinkImage link : image.links()) {
                MAssociation association = system.model().getAssociation(link.association());
                if (association == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_ASSOCIATION_MISSING:" + link.association());
                MObject[] objects = link.objectNames().stream().map(name -> system.state().objectByName(name)
                        ).toArray(MObject[]::new);
                if (java.util.Arrays.stream(objects).anyMatch(Objects::isNull))
                    throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_LINK_OBJECT_MISSING:" + link.association());
                if (!system.state().hasLinkBetweenObjects(association, objects)) system.state().insertLink(link.original());
            }
        } catch (UseApiException | org.tzi.use.uml.sys.MSystemException error) {
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BASELINE_RESTORE_FAILED", error);
        }
    }

    private Value value(Map<String, Object> payload) {
        String type = required(payload, "valueType");
        Object raw = payload.get("value");
        if (raw == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_VALUE_REQUIRED");
        return switch (type) {
            case "STRING" -> stringValue(raw);
            case "BOOLEAN" -> booleanValue(raw);
            case "INTEGER" -> integer(raw);
            case "REAL" -> real(raw);
            case "UNDEFINED" -> UndefinedValue.instance;
            default -> throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_VALUE_TYPE_UNSUPPORTED:" + type);
        };
    }

    private Value stringValue(Object raw) {
        if (!(raw instanceof String value))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_STRING_VALUE_REQUIRED");
        return new StringValue(value);
    }

    private Value booleanValue(Object raw) {
        if (!(raw instanceof Boolean value))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BOOLEAN_VALUE_REQUIRED");
        return BooleanValue.get(value);
    }

    private Value integer(Object raw) {
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue())
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INTEGER_VALUE_INVALID");
        long value = number.longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INTEGER_VALUE_OVERFLOW");
        return IntegerValue.valueOf((int) value);
    }

    private Value real(Object raw) {
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_REAL_VALUE_INVALID");
        return new RealValue(number.doubleValue());
    }

    private static String required(Map<String, Object> payload, String name) {
        String value = textOrNull(payload, name);
        if (value == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PAYLOAD_REQUIRED:" + name);
        return value;
    }

    private static String requiredTextAllowEmpty(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (!(value instanceof String text))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PAYLOAD_REQUIRED:" + name);
        return text;
    }

    private static Object requiredValue(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (value == null) throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_PAYLOAD_REQUIRED:" + name);
        return value;
    }

    private static int requiredInteger(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue())
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_INTEGER_VALUE_INVALID:" + name);
        return number.intValue();
    }

    private static boolean requiredBoolean(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        if (!(value instanceof Boolean booleanValue))
            throw new NativeRuntimeProtocolException("NATIVE_RUNTIME_BOOLEAN_VALUE_INVALID:" + name);
        return booleanValue;
    }

    private static String textOrNull(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static String diagnostic(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ":" + message);
    }
}

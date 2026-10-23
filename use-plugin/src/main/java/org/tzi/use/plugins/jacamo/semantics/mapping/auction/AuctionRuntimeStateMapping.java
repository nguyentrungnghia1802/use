package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SemanticContractCodec;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.sys.MObject;

/** Adapts actual admitted source observations to the XML's normalized M0 input
 * vocabulary. No state, agent, default, bid or scheme is inferred from a name.
 * The caller owns admission, transaction, raw evidence and rollback. */
public final class AuctionRuntimeStateMapping implements CaseRuntimeMapping {
    public record Projection(List<AuctionRuntimeRules.Outcome> outcomes,List<String> diagnostics) {
        public Projection {outcomes=List.copyOf(outcomes);diagnostics=List.copyOf(diagnostics);}
    }
    private final SemanticProgram program;
    private final ModelSnapshot source;
    private final AuctionRuntimeRules rules;
    private final AuctionArtifactBindingResolver resolver;
    private final AuctionStepObservationDecoder steps;
    private final AuctionSchemeObservationDecoder schemes;
    private final AuctionMonitorProjection monitors;
    private final AuctionLifecycleMapping lifecycle;
    private final AuctionObligationDecoder obligations;
    private final AuctionObligationProjection obligationProjection;
    private final String organizationName,organizationSpec,osName,entityClass,classHash,provider,resource;
    private final Map<String,String> roleNames;
    public AuctionRuntimeStateMapping(SemanticProgram program,MModel model) {
        this.program=Objects.requireNonNull(program);if(program.casePlan()==null)throw new IllegalArgumentException("AUCTION_PROGRAM_REQUIRED");
        var official=program.complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow();
        source=ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(official.bytes())));
        program.requireSourceHash(new SourceModelNormalizer().normalize(source).sourceHash());
        var selected=program.casePlan().selectors();organizationName=selected.require("sel-org").getFirst().values().get("instance");
        osName=selected.require("sel-os-root").getFirst().values().get("id");
        var os=source.semanticContract().moiseOrganizations().stream().filter(o->o.name().equals(osName)).findFirst().orElseThrow();organizationSpec=os.metadata().semanticId();
        var names=new LinkedHashMap<String,String>();os.structuralSpecification().roles().forEach(r->names.put(r.metadata().semanticId(),r.roleId()));roleNames=Map.copyOf(names);
        entityClass=selected.require("sel-create").getFirst().values().get("className");
        var proof=SourceArtifactTypeReferences.fromActions(source).stream().filter(r->r.javaClass().equals(entityClass)).findFirst().orElseThrow().classProof();
        classHash=proof.sourceDigest();resource=proof.sourceUri();provider=proof.diagnostics().stream().filter(s->s.startsWith("PROVIDER:")).findFirst().orElseThrow().substring(9);
        resolver=new AuctionArtifactBindingResolver(source,selected);rules=new AuctionRuntimeRules(program.casePlan().specification(),model);steps=new AuctionStepObservationDecoder(program,source);
        schemes=new AuctionSchemeObservationDecoder(program);monitors=new AuctionMonitorProjection(program);lifecycle=new AuctionLifecycleMapping(rules);
        obligations=new AuctionObligationDecoder(program);obligationProjection=new AuctionObligationProjection(program,model);
    }
    public void validateObservation(VerificationObservation observation,String run,long generation,String revision) {
        if(observation.kind()==VerificationObservation.Kind.JASON_STEP)steps.decode(observation,run,generation,revision);
        if(observation.kind()==VerificationObservation.Kind.CLOCK)AuctionSourceClock.decode(observation,run,generation,revision,program.casePlan().bundle().runtimeRequirements().get("sha256:npl-0.6.1.jar"));
        if(observation.kind()==VerificationObservation.Kind.GOAL_TRANSITION)schemes.decode(observation,run,generation,revision);
        if(observation.kind()==VerificationObservation.Kind.NPL_LIFECYCLE)obligations.decode(observation,run,generation);
    }
    @Override public String rawInputRuleId(){return "AUCTION-RAW-INPUT";}
    @Override public String observationRejectionCode(){return "AUCTION_OBSERVER_PROOF_REJECTED";}
    @Override public String mappingRejectionCode(){return "AUCTION_M0_MAPPING_REJECTED";}
    @Override public boolean ownsRemoval(Port port,MObject object){return !port.metadata(object,"auctionRuleKey").isBlank();}
    @Override public List<CaseRuntimeMapping.Outcome> applyCut(Port port,String run,long generation,RuntimeSnapshot cut,List<RuntimeEvent> prefix,RuntimeEvent event)throws UseApiException {
        var projected=apply(port,new AuctionArtifactBindingResolver.Run(run,generation,cut.modelRevision()),cut,prefix,event);
        var results=new ArrayList<CaseRuntimeMapping.Outcome>();
        for(var result:projected.outcomes()) {
            boolean mapped=result.status().equals("MAPPED");
            results.add(new CaseRuntimeMapping.Outcome(mapped,"AUCTION-XML-"+result.rule(),result.nativeKey(),result.object(),
                    mapped?"XML_M0:"+result.rule()+":"+cut.snapshotId():result.status()+":"+result.diagnostic()));
        }
        for(String diagnostic:projected.diagnostics())results.add(new CaseRuntimeMapping.Outcome(false,"AUCTION-M0-QUALIFICATION",cut.snapshotId(),null,diagnostic));
        return List.copyOf(results);
    }
    public Projection apply(Port port,AuctionArtifactBindingResolver.Run run,RuntimeSnapshot cut,List<RuntimeEvent> prefix)throws UseApiException {
        return apply(port,run,cut,prefix,null);
    }
    public Projection apply(Port port,AuctionArtifactBindingResolver.Run run,RuntimeSnapshot cut,List<RuntimeEvent> prefix,RuntimeEvent event)throws UseApiException {
        program.assertUnchanged();if(!source.modelRevision().equals(run.modelRevision())||!source.modelRevision().equals(cut.modelRevision()))throw new IllegalArgumentException("AUCTION_M0_REVISION_MISMATCH");
        var outcomes=new ArrayList<AuctionRuntimeRules.Outcome>();var diagnostics=new ArrayList<String>();
        var previousEnactments=new HashMap<String,Boolean>();
        for(var old:port.objects())if(!port.metadata(old,"auctionRule:R03").isBlank())previousEnactments.put(port.metadata(old,"semanticId"),"true".equals(port.metadata(old,"auctionLiveEnactment")));
        // R06 owns retirement. Until that gate, retained objects must not
        // certify an enactment or population which is absent from this cut.
        for(var old:List.copyOf(port.objects())) {
            if(old.cls().name().equals("AuctionOrg"))port.metadata(old,"auctionOrganizationQuality","UNAVAILABLE");
            if(old.cls().name().equals("AuctionMonitor")&&!"ARCHIVED".equals(port.metadata(old,"auctionMonitorLifecycle"))) {port.metadata(old,"auctionMonitorLive","false");port.metadata(old,"auctionMonitorLifecycle","UNAVAILABLE");for(var field:old.cls().allAttributes())AuctionMonitorProjection.write(port,old,field.name(),UndefinedValue.instance,"SCHEME_CURRENT_BINDING_UNAVAILABLE");}
            if(!port.metadata(old,"auctionRule:R02").isBlank())port.metadata(old,"auctionPopulationQuality","INCOMPLETE");
            if(!port.metadata(old,"auctionRule:R03").isBlank())port.metadata(old,"auctionLiveEnactment","false");
        }
        var artifacts=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&faithful(f)).toList();
        var workspaces=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&faithful(f)).toList();
        var organizationBoards=cut.facts().stream().filter(f->Set.of(RuntimeFactKind.GROUP_BOARD,RuntimeFactKind.SCHEME_BOARD).contains(f.kind())&&faithful(f)
                &&organizationName.equals(f.values().get("organisationName"))&&organizationSpec.equals(f.values().get("organisationSpecSemanticId"))).toList();
        var scopes=new LinkedHashMap<String,RuntimeFact>();
        for(var board:organizationBoards) {
            var boardArtifacts=artifacts.stream().filter(a->Objects.equals(board.values().get("boardArtifactIdentity"),a.values().get("uuid"))).toList();
            if(boardArtifacts.size()!=1){diagnostics.add("ORGANIZATION_BOARD_ARTIFACT_UNAVAILABLE:"+board.id().canonical());continue;}
            var workspace=workspaces.stream().filter(w->Objects.equals(w.values().get("semanticId"),boardArtifacts.getFirst().values().get("workspaceSemanticId"))
                    &&organizationName.equals(w.values().get("name"))).toList();
            if(workspace.size()!=1){diagnostics.add("ACTUAL_ORGANIZATION_WORKSPACE_UNAVAILABLE:"+board.id().canonical());continue;}
            verifyOrganization(board);scopes.put(board.id().canonical(),workspace.getFirst());
            var input=Map.<String,Object>of("runId",runKey(run),"organizationUid",text(workspace.getFirst().values(),"uuid"),"instanceName",organizationName,"osId",osName);
            var outcome=rules.execute(port,input("in-org",input,cut,Map.of()));outcomes.add(outcome);
            if(outcome.object()!=null) {
                port.metadata(outcome.object(),"organizationWorkspace",text(workspace.getFirst().values(),"semanticId"));
                port.metadata(outcome.object(),"organizationWorkspaceUuid",text(workspace.getFirst().values(),"uuid"));
                port.metadata(outcome.object(),"organizationDeclaration",text(board.values(),"organisationSemanticId"));
                port.metadata(outcome.object(),"auctionOrganizationQuality","COMPLETE_OBSERVED_ORGANIZATION");
            }
        }
        for(var board:organizationBoards)if(board.kind()==RuntimeFactKind.GROUP_BOARD&&scopes.containsKey(board.id().canonical())) {
            var workspace=scopes.get(board.id().canonical());var definition=SemanticContractCodec.organizationFromTree(board.values().get("organisationDefinition"));
            var group=definition.structuralSpecification().groups().stream().filter(g->g.metadata().semanticId().equals(board.values().get("groupSpecSemanticId"))).findFirst().orElseThrow(()->new IllegalArgumentException("AUCTION_GROUP_DEFINITION_UNAVAILABLE"));
            var groupInput=Map.<String,Object>of("runId",runKey(run),"organizationUid",text(workspace.values(),"uuid"),"groupUid",board.id().canonical(),"instanceName",text(board.values(),"name"),"specId",group.groupId());
            var result=rules.execute(port,input("in-group",groupInput,cut,Map.of()));outcomes.add(result);if(result.object()==null)continue;
            var context=result.object();port.alias(board.id().canonical(),context);port.alias(text(board.values(),"semanticId"),context);
            port.metadata(context,"groupSpecSemanticId",group.metadata().semanticId());port.metadata(context,"boardArtifactIdentity",text(board.values(),"boardArtifactIdentity"));port.metadata(context,"organisationName",organizationName);
            port.metadata(context,"groupDeclarationIdentity",text(board.values(),"semanticId"));port.metadata(context,"groupInstanceName",text(board.values(),"name"));
            port.metadata(context,"auctionGroupAdmissionCut",cut.snapshotId()+":"+cut.stateFingerprint());
            port.metadata(context,"lastGroupCut",SemanticJson.hash(board.values()));
            if(!(board.values().get("players") instanceof List<?> players))throw new IllegalArgumentException("AUCTION_GROUP_PLAYERS_REQUIRED");
            var seen=new HashSet<String>();boolean complete=true;
            for(Object raw:players) {
                var player=CanonicalJson.object(raw);String roleId=text(player,"roleSemanticId"),role=roleNames.get(roleId),actorId=text(player,"agentSemanticId");
                if(role==null||!group.roleSemanticIds().contains(roleId))throw new IllegalArgumentException("AUCTION_ROLE_NOT_IN_GROUP_DEFINITION");
                if(!seen.add(SemanticJson.hash(List.of(actorId,roleId))))throw new IllegalArgumentException("AUCTION_DUPLICATE_ENACTMENT");
                MObject actual=port.find(actorId);if(!AuctionMonitorProjection.currentPlayer(port,cut,actual)){complete=false;diagnostics.add("ACTUAL_PLAYER_CURRENT_CUT_UNAVAILABLE:"+actorId);continue;}
                var fields=Map.<String,Object>of("runId",runKey(run),"groupUid",board.id().canonical(),"roleName",role,"agentUid",port.metadata(actual,"semanticId"),"agentName",port.metadata(actual,"name"));
                var occurrence=rules.execute(port,input("in-role",fields,cut,Map.of()));outcomes.add(occurrence);
                if(occurrence.object()!=null){
                    var roleObject=occurrence.object();String identity=port.metadata(roleObject,"semanticId");
                    if(!Boolean.TRUE.equals(previousEnactments.get(identity))||port.metadata(roleObject,"auctionObservedRoleInterval").isBlank()) {
                        port.metadata(roleObject,"auctionObservedRoleInterval","observed-role-interval:"+SemanticJson.hash(List.of(runKey(run),identity,cut.snapshotId())));
                        port.metadata(roleObject,"auctionRoleIntervalAuthority","CONSUMER_OBSERVED_MEMBERSHIP_NOT_FRAMEWORK_ENACTMENT_UID");
                        port.metadata(roleObject,"auctionRoleIntervalObserverFloor",Long.toString(cut.endWatermarks().getOrDefault(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,0)).sequence()));
                    }
                    port.metadata(roleObject,"roleSourceId",roleId);port.metadata(roleObject,"auctionRoleSourceActor",actorId);port.metadata(roleObject,"roleTypeId",role);port.metadata(roleObject,"auctionLiveEnactment","true");
                }
                if(!occurrence.status().equals("MAPPED"))complete=false;
            }
            port.metadata(context,"auctionObservedRoleTuples",new String(CanonicalJson.encode(seen.stream().sorted().toList()),java.nio.charset.StandardCharsets.UTF_8));
            port.metadata(context,"auctionPositivePlayerCutComplete",Boolean.toString(complete));
            if(port.objects().stream().anyMatch(o->port.metadata(context,"semanticId").equals(port.metadata(o,"roleContext"))&&"false".equals(port.metadata(o,"auctionLiveEnactment"))))complete=false;
            port.metadata(context,"auctionPopulationQuality",complete?"COMPLETE_OBSERVED_BOARD":"INCOMPLETE");
        }
        var resolved=resolver.resolve(run,cut,prefix);resolved.diagnostics().forEach(d->diagnostics.add(d.availability()+":"+d.schemeIdentity()+":"+d.code()));
        var resolvedObjects=new HashSet<MObject>();var resolvedArtifacts=new HashSet<String>();
        for(var binding:resolved.bindings()) {
            if(!scopes.containsKey(binding.schemeIdentity())){diagnostics.add("AUCTION_ORGANIZATION_SCOPE_UNAVAILABLE:"+binding.schemeIdentity());continue;}
            String orgUuid=text(scopes.get(binding.schemeIdentity()).values(),"uuid");
            if(!orgUuid.equals(binding.organizationWorkspaceUuid()))throw new IllegalArgumentException("AUCTION_CROSSWALK_ORGANIZATION_MISMATCH");
            var fields=new LinkedHashMap<String,Object>();fields.put("runId",runKey(run));fields.put("organizationUid",orgUuid);fields.put("workspaceUid",binding.workspaceUuid());fields.put("artifactUid",binding.artifactUuid());
            fields.put("artifactName",binding.auctionId());fields.put("auctionId",binding.auctionId());fields.put("className",entityClass);fields.put("schemeUid",binding.schemeIdentity());fields.put("schemeArtifactBindingVerified",true);
            var identity=rules.execute(port,input("in-artifact",fields,cut,Map.of(AuctionRuntimeRules.CROSSWALK,binding.cutHash())));outcomes.add(identity);if(identity.object()==null)continue;
            var object=identity.object();port.alias(binding.artifactIdentity(),object);
            var artifact=artifacts.stream().filter(a->binding.artifactUuid().equals(a.values().get("uuid"))).findFirst().orElseThrow();port.alias(artifact.id().canonical(),object);
            port.metadata(object,"name",binding.auctionId());port.metadata(object,"uuid",binding.artifactUuid());port.metadata(object,"workspaceSemanticId",binding.workspaceIdentity());
            port.metadata(object,"schemeIdentity",binding.schemeIdentity());port.metadata(object,"schemeEpisode",binding.episode());port.metadata(object,"artifactSemanticId",binding.artifactIdentity());
            port.metadata(object,"authority","cartago");port.metadata(object,"incarnation",binding.artifactUuid());
            port.metadata(object,"auctionExistence","RESOLVED");resolvedObjects.add(object);resolvedArtifacts.add(artifact.id().canonical());
            var monitor=monitors.apply(port,binding,cut);if(monitor!=null)outcomes.add(monitor);
            var values=fieldValues(artifact,binding);
            if(values==null) {
                invalidate(port,object);diagnostics.add("INCOMPLETE:AUCTION_COHERENT_FIELD_CUT_UNAVAILABLE:"+binding.artifactIdentity());continue;
            }
            var state=new LinkedHashMap<String,Object>();state.put("runId",runKey(run));state.put("workspaceUid",binding.workspaceUuid());state.put("artifactUid",binding.artifactUuid());
            state.put("sourceRevision",cut.endWatermarks().getOrDefault("cartago",new SourceWatermark("cartago",0)).sequence());state.put("sourceCutAccepted",true);state.put("internalFieldRead",true);state.putAll(values);
            var result=rules.execute(port,input("in-state",state,cut,Map.of(AuctionRuntimeRules.FIELD_CUT,SemanticJson.hash(artifact.values().get("auctionStateObservation")))));outcomes.add(result);
            if(!result.status().equals("MAPPED"))invalidate(port,object);
            else port.metadata(object,"auctionFullState","COMPLETE");
        }
        outcomes.addAll(lifecycle.apply(port,run,cut,event));
        outcomes.addAll(obligationProjection.apply(port,run,cut,resolved.bindings()));
        var unresolved=new TreeSet<String>();
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.ARTIFACT&&entityClass.equals(fact.values().get("artifactTypeJavaClassName"))&&!resolvedArtifacts.contains(fact.id().canonical()))unresolved.add(fact.id().canonical());
        for(var old:List.copyOf(port.objects()))if(!port.metadata(old,"auctionRule:R04").isBlank()&&!resolvedObjects.contains(old)) {
            invalidate(port,old);port.metadata(old,"auctionExistence","UNRESOLVED");unresolved.add(port.metadata(old,"artifactSemanticId"));
        }
        // A complete raw Artifact enumeration is not a complete mapped CSL
        // population when a physical candidate lacks its exact crosswalk.
        // Keep this qualification in the existing native metadata/savepoint.
        for(var object:port.objects())if(object.cls().name().equals("WorkspaceObservation")) {
            port.metadata(object,"auctionDomainPopulationQuality",unresolved.isEmpty()?"COMPLETE":"INCOMPLETE");
            port.metadata(object,"auctionUnresolvedArtifacts",new String(CanonicalJson.encode(unresolved),java.nio.charset.StandardCharsets.UTF_8));
        }
        return new Projection(outcomes,diagnostics);
    }
    private Map<String,Object> fieldValues(RuntimeFact artifact,AuctionArtifactBindingResolver.Binding binding) {
        try {
            var state=CanonicalJson.object(artifact.values().get("auctionStateObservation"));
            if(!"1.0.0".equals(state.get("version"))||!"COMPLETE".equals(state.get("status"))||!"LOCKED_ARTIFACT".equals(state.get("coherence"))||!binding.artifactIdentity().equals(state.get("artifactIdentity"))||!classHash.equals(state.get("classHash"))||!(state.get("fieldCutId") instanceof String id)||!id.matches("[0-9a-f]{64}"))return null;
            var proof=CanonicalJson.object(state.get("probeEvidence"));
            if(!provider.equals(proof.get("provider"))||!resource.equals(proof.get("resource"))||!binding.artifactUuid().equals(proof.get("artifactUuid"))||!binding.workspaceUuid().equals(proof.get("workspaceUuid"))
                    ||!"auction_env.AuctionArtifact.currentWinner:java.lang.String".equals(proof.get("field"))||!"CARTAGO31_PRIVATE_REENTRANT_TRYLOCK_READ_V1".equals(proof.get("lockPolicy"))||!"FIXED_MEMBERS_TRY_SET_ACCESSIBLE_APPROVED".equals(proof.get("accessOutcome")))return null;
            var properties=CanonicalJson.object(state.get("properties"));var fields=CanonicalJson.object(state.get("fields"));
            if(!properties.keySet().equals(Set.of("running","task","best_bid","winner"))||!fields.keySet().equals(Set.of("currentWinner")))return null;
            var candidate=scalar(fields.get("currentWinner"),"STRING","java.lang.String");
            String expected=SemanticJson.hash(Map.of("artifact",binding.artifactIdentity(),"properties",properties,"candidate",candidate,"origin",text(proof,"origin"),"invocation",Objects.toString(proof.get("invocation"),"")));
            if(!expected.equals(id))return null;
            var result=new LinkedHashMap<String,Object>();result.put("running",scalar(properties.get("running"),"BOOLEAN","java.lang.Boolean"));result.put("task",scalar(properties.get("task"),"STRING","java.lang.String"));
            result.put("best_bid",properties.get("best_bid"));result.put("winner",properties.get("winner"));result.put("currentWinner",scalar(fields.get("currentWinner"),"STRING","java.lang.String"));return result;
        }catch(IllegalArgumentException missing){return null;}
    }
    private static Object scalar(Object raw,String kind,String type){var data=CanonicalJson.object(raw);if(!kind.equals(data.get("kind"))||!type.equals(data.get("javaType"))||data.get("value")==null)throw new IllegalArgumentException("AUCTION_TYPED_SCALAR_REQUIRED");return data.get("value");}
    private static void invalidate(Port port,MObject object)throws UseApiException {
        for(var attribute:object.cls().allAttributes())if(!attribute.name().equals("id")&&!attribute.isDerived()) {port.value(object,attribute.name(),UndefinedValue.instance);port.metadata(object,"propertyAbsent:"+attribute.name(),"true");port.metadata(object,"observedValueHash:"+attribute.name(),"");port.metadata(object,"auctionObserved:"+attribute.name(),"");}
        port.metadata(object,"auctionFullState","INCOMPLETE");port.metadata(object,"auctionRule:R05","INCOMPLETE:COHERENT_FIELD_CUT_UNAVAILABLE");
    }
    private void verifyOrganization(RuntimeFact board) {
        var definition=SemanticContractCodec.organizationFromTree(board.values().get("organisationDefinition"));MoiseDomainProjection.validate(definition);
        var frozen=source.semanticContract().moiseOrganizations().stream().filter(o->o.metadata().semanticId().equals(organizationSpec)).findFirst().orElseThrow();
        // OfficialMoiseAdapter.capture uses a public live-graph digest when the
        // board's OS has no original URI. Raw-file provenance and live-graph
        // provenance are distinct; compare every semantic field and metadata
        // identity, while retaining both evidence records in the admitted cut.
        if(!SemanticJson.hash(definitionShape(SemanticContractCodec.organizationToTree(frozen))).equals(SemanticJson.hash(definitionShape(SemanticContractCodec.organizationToTree(definition)))))throw new IllegalArgumentException("AUCTION_RUNTIME_ORGANIZATION_DEFINITION_CHANGED");
        String rawHash=program.casePlan().bundle().require("src-os").sha256();
        if(definition.metadata().evidence().isEmpty())throw new IllegalArgumentException("AUCTION_RUNTIME_OS_PROVENANCE_UNAVAILABLE");
        for(var evidence:definition.metadata().evidence())if(evidence.authority()!=org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_MOISE_API
                ||evidence.fidelity()!=org.jacamo.bridge.contract.semantic.Fidelity.EXACT||evidence.capabilityStatus()!=CapabilityStatus.COMPLETE
                ||!organizationSpec.equals(evidence.sourceSemanticId())||!definition.metadata().sourceJavaFqcn().equals(evidence.sourceJavaFqcn())
                ||!(rawHash.equals(evidence.sourceDigest())||evidence.sourceUri().equals("moise-live:/"+source.semanticContract().project().name()+"/"+osName)&&evidence.sourceDigest().matches("[0-9a-f]{64}")))throw new IllegalArgumentException("AUCTION_RUNTIME_OS_PROVENANCE_UNAVAILABLE");
    }
    static Object definitionShape(Object raw) {
        if(raw instanceof List<?> list)return list.stream().map(AuctionRuntimeStateMapping::definitionShape).toList();
        if(raw instanceof Map<?,?> rawMap) {
            var map=CanonicalJson.object(rawMap);var result=new TreeMap<String,Object>();
            boolean metadata=map.containsKey("semanticId")&&map.containsKey("sourceKind"),organization=map.containsKey("structuralSpecification")&&map.containsKey("functionalSpecification")&&map.containsKey("normativeSpecification");
            map.forEach((key,value)->{if(!(metadata&&key.equals("evidence"))&&!(organization&&key.equals("sourceUri")))result.put(key,definitionShape(value));});return result;
        }
        return raw;
    }
    private static AuctionRuntimeRules.Input input(String kind,Map<String,Object> values,RuntimeSnapshot cut,Map<String,String> proofs){return new AuctionRuntimeRules.Input(kind,values,cut.snapshotId()+":"+cut.stateFingerprint(),proofs);}
    public static String runKey(AuctionArtifactBindingResolver.Run run){return run.id()+":"+run.generation();}
    private static boolean faithful(RuntimeFact f){return f.completeness()==Completeness.COMPLETE&&f.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
    private static String text(Map<String,Object> data,String key){if(!(data.get(key) instanceof String value)||value.isBlank())throw new IllegalArgumentException("AUCTION_RUNTIME_TEXT_REQUIRED:"+key);return value;}
}

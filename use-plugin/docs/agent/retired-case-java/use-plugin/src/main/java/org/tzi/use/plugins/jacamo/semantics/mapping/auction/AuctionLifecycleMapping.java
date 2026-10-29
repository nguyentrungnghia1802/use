package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;

/** Source-qualified R06 admission; no physical source operation is performed. */
final class AuctionLifecycleMapping {
    private final AuctionRuntimeRules rules;
    AuctionLifecycleMapping(AuctionRuntimeRules rules){this.rules=rules;}
    List<AuctionRuntimeRules.Outcome> apply(Port port,AuctionArtifactBindingResolver.Run run,RuntimeSnapshot cut,RuntimeEvent event)throws UseApiException {
        var results=new ArrayList<AuctionRuntimeRules.Outcome>();boolean complete=cut.sourceCompleteness().get("cartago")==Completeness.COMPLETE
                &&cut.facts().stream().filter(f->f.id().authority().equals("cartago")).allMatch(f->f.completeness()==Completeness.COMPLETE&&f.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY);
        var physical=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT).map(f->Objects.toString(f.values().get("semanticId"),"")).collect(java.util.stream.Collectors.toSet());
        var artifactUuids=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT).map(f->Objects.toString(f.values().get("uuid"),"")).collect(java.util.stream.Collectors.toSet());
        var workspaceUuids=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE).map(f->Objects.toString(f.values().get("uuid"),"")).collect(java.util.stream.Collectors.toSet());
        for(var object:List.copyOf(port.objects()))if(object.cls().name().equals("Auction")&&!port.metadata(object,"auctionRule:R04").isBlank()) {
            String id=port.metadata(object,"artifactSemanticId");boolean disposed=event!=null&&event.entityId()!=null&&event.kind()==RuntimeEventKind.DISPOSED&&event.completeness()==Completeness.COMPLETE&&event.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY
                    &&event.sessionId().equals(run.id())&&event.generation()==run.generation()&&event.modelRevision().equals(run.modelRevision())&&event.sourceId().equals("cartago")&&"DELETE_CARTAGO_ARTIFACT".equals(event.after().get("normalizedEventKind"))&&id.equals(event.after().get("semanticId"))&&event.entityId().incarnation().equals(port.metadata(object,"uuid"));
            if(!disposed&&(!complete||physical.contains(id)))continue;
            String revision="accepted-removal:"+cut.snapshotId()+":"+cut.stateFingerprint(),key=port.metadata(object,"auctionRuleKey");
            var input=new AuctionRuntimeRules.Input("in-removal",Map.of("runId",AuctionRuntimeStateMapping.runKey(run),"objectKind","auctionArtifact","nativeKey",key,"sourceRevision",revision),revision,Map.of(AuctionRuntimeRules.REMOVAL,revision,AuctionRuntimeRules.LIFETIME,port.metadata(object,"auctionLifetimeToken")));
            var removed=rules.execute(port,input);results.add(removed);if(!removed.status().equals("MAPPED"))continue;
            for(var monitor:port.objects())if(monitor.cls().name().equals("AuctionMonitor")&&id.equals(port.metadata(monitor,"auctionMonitorArtifact"))) {
                AuctionMonitorProjection.archive(port,monitor,revision);
            }
        }
        for(var monitor:port.objects())if(monitor.cls().name().equals("AuctionMonitor")&&(run.id()+":"+run.generation()).equals(port.metadata(monitor,"auctionMonitorRun"))&&!"ARCHIVED".equals(port.metadata(monitor,"auctionMonitorLifecycle"))) {
            String uuid=port.metadata(monitor,"auctionMonitorBoardUuid");if(uuid.isBlank()||(!disposed(run,event,uuid)&&(!complete||artifactUuids.contains(uuid))))continue;
            String revision="accepted-scheme-retirement:"+cut.snapshotId()+":"+cut.stateFingerprint();AuctionMonitorProjection.archive(port,monitor,revision);
            results.add(new AuctionRuntimeRules.Outcome("OBSERVER_SCHEME_ARCHIVE","MAPPED",port.metadata(monitor,"semanticId"),monitor,"PHYSICAL_SCHEME_RETIRED_BUSINESS_ARTIFACT_INDEPENDENT"));
        }
        for(var group:List.copyOf(port.objects()))if(group.cls().name().equals("auctionGroup")&&!port.metadata(group,"auctionRule:R02").isBlank()) {
            String uuid=port.metadata(group,"boardArtifactIdentity");boolean disposed=disposed(run,event,uuid);
            if(!disposed&&(!complete||artifactUuids.contains(uuid)))continue;
            String context=port.metadata(group,"semanticId"),revision="accepted-group-retirement:"+cut.snapshotId()+":"+cut.stateFingerprint();
            for(var role:List.copyOf(port.objects()))if(!port.metadata(role,"auctionRule:R03").isBlank()&&context.equals(port.metadata(role,"roleContext")))results.add(remove(port,run,role,"roleEnactment",revision));
            results.add(remove(port,run,group,"group",revision));
        }
        for(var organization:List.copyOf(port.objects()))if(organization.cls().name().equals("AuctionOrg")&&!port.metadata(organization,"auctionRule:R01").isBlank()) {
            String uuid=port.metadata(organization,"organizationWorkspaceUuid");if(!complete||workspaceUuids.contains(uuid))continue;
            String revision="accepted-organization-workspace-retirement:"+cut.snapshotId()+":"+cut.stateFingerprint();
            for(var object:port.objects())if(object.cls().name().equals("Auction")&&!port.metadata(object,"auctionInput:R04").isBlank()) {
                var origin=CanonicalJson.object(CanonicalJson.decode(port.metadata(object,"auctionInput:R04").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(uuid.equals(origin.get("organizationUid")))port.metadata(object,"auctionScopeQuality","ORGANIZATION_RETIRED_PHYSICAL_ARTIFACT_INDEPENDENT");
            }
            // Scope associations are ordinary 0..1 projection links. Deleting
            // this view detaches them; no source/domain disposal is requested.
            results.add(remove(port,run,organization,"organization",revision));
        }
        // Absence within a positive, complete observed GroupBoard player list
        // proves withdrawal of that tuple; absence of a board in MOISE polling
        // is never used as a disposal claim.
        var positiveGroups=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD&&f.completeness()==Completeness.COMPLETE&&f.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY).map(f->port.find(f.id().canonical())).filter(Objects::nonNull).filter(g->(cut.snapshotId()+":"+cut.stateFingerprint()).equals(port.metadata(g,"auctionGroupAdmissionCut"))).distinct().toList();
        for(var group:positiveGroups) {
            String context=port.metadata(group,"semanticId"),stored=port.metadata(group,"auctionObservedRoleTuples");
            if(stored.isBlank())continue;
            var observed=(List<?>)CanonicalJson.decode(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for(var role:List.copyOf(port.objects()))if(!port.metadata(role,"auctionRule:R03").isBlank()&&context.equals(port.metadata(role,"roleContext"))&&"false".equals(port.metadata(role,"auctionLiveEnactment"))) {
                String actor=port.metadata(role,"auctionRoleSourceActor"),roleId=port.metadata(role,"roleSourceId");
                // Missing current player evidence does not withdraw a tuple
                // which the positive GroupBoard still explicitly contains.
                if(actor.isBlank()||roleId.isBlank()||observed.contains(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(actor,roleId))))continue;
                String revision="accepted-role-withdrawal:"+cut.snapshotId()+":"+port.metadata(group,"lastGroupCut"),key=port.metadata(role,"auctionRuleKey");
                results.add(rules.execute(port,new AuctionRuntimeRules.Input("in-removal",Map.of("runId",AuctionRuntimeStateMapping.runKey(run),"objectKind","roleEnactment","nativeKey",key,"sourceRevision",revision),revision,Map.of(AuctionRuntimeRules.REMOVAL,revision,AuctionRuntimeRules.LIFETIME,port.metadata(role,"auctionLifetimeToken")))));
            }
            if("true".equals(port.metadata(group,"auctionPositivePlayerCutComplete"))&&port.objects().stream().noneMatch(role->context.equals(port.metadata(role,"roleContext"))&&"false".equals(port.metadata(role,"auctionLiveEnactment"))))port.metadata(group,"auctionPopulationQuality","COMPLETE_OBSERVED_BOARD");
        }
        return List.copyOf(results);
    }
    private AuctionRuntimeRules.Outcome remove(Port port,AuctionArtifactBindingResolver.Run run,org.tzi.use.uml.sys.MObject object,String kind,String revision)throws UseApiException {
        return rules.execute(port,new AuctionRuntimeRules.Input("in-removal",Map.of("runId",AuctionRuntimeStateMapping.runKey(run),"objectKind",kind,"nativeKey",port.metadata(object,"auctionRuleKey"),"sourceRevision",revision),revision,Map.of(AuctionRuntimeRules.REMOVAL,revision,AuctionRuntimeRules.LIFETIME,port.metadata(object,"auctionLifetimeToken"))));
    }
    private static boolean disposed(AuctionArtifactBindingResolver.Run run,RuntimeEvent event,String uuid) {
        return event!=null&&event.entityId()!=null&&event.kind()==RuntimeEventKind.DISPOSED&&event.factKind()==RuntimeFactKind.ARTIFACT&&event.completeness()==Completeness.COMPLETE&&event.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY&&event.sessionId().equals(run.id())&&event.generation()==run.generation()&&event.modelRevision().equals(run.modelRevision())&&event.sourceId().equals("cartago")&&event.entityId().authority().equals("cartago")&&event.entityId().incarnation().equals(uuid)&&"DELETE_CARTAGO_ARTIFACT".equals(event.after().get("normalizedEventKind"));
    }
}

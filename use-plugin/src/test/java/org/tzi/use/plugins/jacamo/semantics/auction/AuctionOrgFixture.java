package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import cartago.*;
import ora4mas.nopl.GroupBoard;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real original board/artifact instances with fixture-only agent identity
 * registration. This is not a complete Jason MAS or the external E01 gate. */
final class AuctionOrgFixture implements AutoCloseable {
    final AuctionLiveFixture live;
    final WorkspaceId organization;
    final AutoCloseable registration;
    final List<GroupBoard> groups=new ArrayList<>();
    final MoiseBoardSnapshotSource moise;
    AuctionOrgFixture()throws Exception {this(false);}
    AuctionOrgFixture(boolean normative)throws Exception {
        live=new AuctionLiveFixture(true,normative);
        // CArtAgO keeps workspace descriptors after environment.shutdown().
        // Preserve the source organization name in an independently owned tree.
        organization=live.otherWorkspace("aorg",live.otherWorkspace());registration=CslRuntimeTestRegistry.bind(live.source.semanticContract(),live.actor.getName(),"bob");
        moise=new MoiseBoardSnapshotSource("aorg",live.run.id(),()->List.copyOf(groups),()->List.copyOf(live.boards),live.source.semanticContract(),Path.of("examples/auction").toRealPath());
    }
    ArtifactId group()throws Exception {
        return group("agrp");
    }
    ArtifactId group(String name)throws Exception {
        var output=new OpFeedbackParam<ArtifactId>();
        live.actor.doAction(new Op("makeArtifact",name,"ora4mas.nopl.GroupBoard",new Object[]{Path.of("examples/auction/src/org/auction-os.xml").toRealPath().toString(),"auctionGroup"},output),organization,5000);
        var id=output.get();assertNotNull(id);live.artifacts.add(id);
        var board=(GroupBoard)live.environment.resolveWSP(organization.getFullName()).getWorkspace().getArtifactDescriptor(id.getName()).getArtifact();groups.add(board);
        assertEquals("aorg",board.getOEId());live.actor.doAction(id,new Op("adoptRole","auctioneer"),5000);return id;
    }
    ArtifactId organizationBoard()throws Exception {
        var output=new OpFeedbackParam<ArtifactId>();
        live.actor.doAction(new Op("makeArtifact","aorg","ora4mas.nopl.OrgBoard",new Object[]{Path.of("examples/auction/src/org/auction-os.xml").toRealPath().toString()},output),organization,5000);
        var id=output.get();assertNotNull(id);live.artifacts.add(id);return id;
    }
    ArtifactId scheme(String auction,String service)throws Exception {
        var id=live.scheme("sch_"+auction,auction,service,true,organization);assertEquals("aorg",live.boards.getLast().getOEId());return id;
    }
    RuntimeSnapshot cut() {
        var facts=new ArrayList<>(moise.capture());facts.addAll(live.cartago.capture());
        for(var f:facts)if(Set.of(RuntimeFactKind.SCHEME_BOARD,RuntimeFactKind.GROUP_BOARD).contains(f.kind()))assertEquals(ProjectionStatus.MATERIALIZED_FAITHFULLY,f.projectionStatus(),f.values().toString());
        var marks=new TreeMap<String,SourceWatermark>();marks.put(moise.sourceId(),moise.watermark());marks.put(live.cartago.sourceId(),live.cartago.watermark());var quality=new TreeMap<String,Completeness>();quality.put(moise.sourceId(),Completeness.PARTIAL);quality.put(live.cartago.sourceId(),Completeness.COMPLETE);
        if(live.npl!=null){facts.addAll(live.npl.capture());marks.put(live.npl.sourceId(),live.npl.watermark());quality.put(live.npl.sourceId(),live.npl.completeness());}var now=Instant.now();
        return new RuntimeSnapshot("auction-org-cut-"+(++live.cuts),live.source.modelRevision(),now,now,marks,marks,1,facts,quality,"actual-auction-org-cut-"+live.cuts);
    }
    @Override public void close()throws Exception {try{moise.close();for(var group:groups)group.getNormativeEngine().stop();registration.close();}finally{live.close();}}
}

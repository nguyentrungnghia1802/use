package org.tzi.use.plugins.jacamo.semantics.mapping.auction;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter;
/** Compatibility observer policy, sharing neutral declaration-scope preparation. */
public final class AuctionUseModelAdapter extends CslScopeModelAdapter {
    public Use75ModelBinding compile(CslModel source,AuctionMappingSpecification specification) {
        var observers=new AuctionObserverUseBinding(specification);
        return new Use75CslModelBackend().compile(source,M0ExtensionSpec.observedPlayers(),(api,binding)->{
            prepare(api,binding);observers.install(api,binding);
        });
    }
}

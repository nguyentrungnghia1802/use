package org.tzi.use.plugins.jacamo.runtime;

import cartago.Artifact;
import cartago.OPERATION;

public class TestAuctionArtifact extends Artifact {
    public void init() { defineObsProperty("open", true); }

    @OPERATION public void closeAuction() { getObsProperty("open").updateValue(false); }

    @OPERATION public void openAuction() { getObsProperty("open").updateValue(true); }

    @OPERATION public void removeOpen() { removeObsProperty("open"); }

    @OPERATION public void placeBid(String item, int amount) {
        if (amount <= 0) failed("amount must be positive");
        signal("bid", item, amount);
    }
}

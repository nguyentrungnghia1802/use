package auction;

import cartago.Artifact;
import cartago.GUARD;
import cartago.OPERATION;

public class AuctionArtifact extends Artifact {
    public void init() { defineObsProperty("open", true); }

    @OPERATION(guard="canBid")
    public void placeBid(String item, int amount) {
        if (amount <= 0) failed("amount must be positive");
        signal("bid", item, amount);
    }

    @GUARD
    public boolean canBid(String item, int amount) { return amount >= 0; }

    @OPERATION
    public void closeAuction() { getObsProperty("open").updateValue(false); }

    @OPERATION
    public void removeOpen() { removeObsProperty("open"); }
}

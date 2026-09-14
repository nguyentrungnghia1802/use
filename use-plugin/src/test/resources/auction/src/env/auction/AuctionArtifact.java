package auction;

public class AuctionArtifact extends Artifact {
    void init() { defineObsProperty("open", true); }

    @OPERATION(guard="canBid")
    void placeBid(String item, int amount) {
        signal("bid", item, amount);
        await("open");
    }

    @GUARD
    boolean canBid(int amount) { return amount > 0; }

    @INTERNAL_OPERATION
    void closeAuction() { }
}

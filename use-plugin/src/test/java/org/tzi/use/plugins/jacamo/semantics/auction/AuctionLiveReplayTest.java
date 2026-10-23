package org.tzi.use.plugins.jacamo.semantics.auction;

import org.junit.jupiter.api.Test;

/** The same real source/TCP gate, followed by self-contained offline replay.
 * No fixture actions or expected result are fed to the replay engine. */
class AuctionLiveReplayTest {
    @Test void actualExternalAuctionTasksClockAndOriginalMarkingsReplayExactly()throws Exception {
        AuctionTaskLiveTcpTest.run("V02",true);
    }
}

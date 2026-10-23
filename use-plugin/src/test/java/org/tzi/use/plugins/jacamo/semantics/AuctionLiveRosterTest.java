package org.tzi.use.plugins.jacamo.semantics;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Runtime presence and observed mission participation are separate facts. */
class AuctionLiveRosterTest {
    private static final Set<String> AGENTS=Set.of("bob","alice","maria","francois","giacomo");

    @Test void canonicalMissionMinimumDoesNotInventTasksForUncommittedRolePlayers() {
        assertDoesNotThrow(()->InstalledAuctionMain.validateTaskPlayers(AGENTS,Set.of("bob","alice","maria","francois"),false));
        assertDoesNotThrow(()->InstalledAuctionMain.validateTaskPlayers(AGENTS,Set.of("bob","alice"),false));
    }
    @Test void explicitlyCommittedControlledRosterStillRequiresEveryPlayer() {
        assertDoesNotThrow(()->InstalledAuctionMain.validateTaskPlayers(AGENTS,AGENTS,true));
        assertThrows(AssertionError.class,()->InstalledAuctionMain.validateTaskPlayers(AGENTS,Set.of("bob","alice","maria","francois"),true));
    }
    @Test void canonicalGateRejectsForeignIdentityAndAbsentParticipantEvidence() {
        assertThrows(AssertionError.class,()->InstalledAuctionMain.validateTaskPlayers(AGENTS,Set.of("bob","foreign-run-player"),false));
        assertThrows(AssertionError.class,()->InstalledAuctionMain.validateTaskPlayers(AGENTS,Set.of("bob"),false));
    }
    @Test void canonicalRuntimeStillRequiresAllFiveActualAgents() {
        assertThrows(AssertionError.class,()->InstalledAuctionMain.validateTaskPlayers(Set.of("bob","alice"),Set.of("bob","alice"),false));
    }
}

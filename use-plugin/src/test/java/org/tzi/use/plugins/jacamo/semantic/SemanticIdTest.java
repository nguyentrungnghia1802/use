package org.tzi.use.plugins.jacamo.semantic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticIdTest {
    @Test
    void producesSixPartCanonicalIdentityDeterministically() {
        SemanticId id = SemanticId.of("auction", Dimension.AGENT, "Agent", List.of("MAS"), "auctioneer");
        assertEquals("jacamo:auction:agent:Agent:MAS:auctioneer", id.value());
        assertEquals(id, SemanticId.of("auction", Dimension.AGENT, "Agent", List.of("MAS"), "auctioneer"));
    }

    @Test
    void structuralCharactersCannotMergeDistinctIdentities() {
        SemanticId nested = SemanticId.of("a:b", Dimension.ENVIRONMENT, "Artifact",
                List.of("MAS", "ws"), "auction/1");
        assertEquals("jacamo:a%3Ab:environment:Artifact:MAS/ws:auction%2F1", nested.value());
        assertNotEquals(nested, SemanticId.of("a", Dimension.ENVIRONMENT, "Artifact",
                List.of("b", "MAS", "ws"), "auction/1"));
        assertNotEquals(SemanticId.of("p", Dimension.AGENT, "Agent", List.of("a/b"), "c"),
                SemanticId.of("p", Dimension.AGENT, "Agent", List.of("a", "b"), "c"));
    }

    @Test
    void differentOwnersDoNotCollideButDuplicateIdentityIsRejected() {
        SemanticIdRegistry registry = new SemanticIdRegistry();
        SemanticId first = SemanticId.of("p", Dimension.AGENT, "Belief", List.of("MAS", "a"), "ready");
        SemanticId second = SemanticId.of("p", Dimension.AGENT, "Belief", List.of("MAS", "b"), "ready");
        registry.register(first);
        registry.register(second);
        assertThrows(IllegalArgumentException.class, () -> registry.register(first));
    }

    @Test
    void rejectsMissingIdentitySegments() {
        assertThrows(IllegalArgumentException.class,
                () -> SemanticId.of(" ", Dimension.AGENT, "Agent", List.of("MAS"), "a"));
        assertThrows(IllegalArgumentException.class,
                () -> SemanticId.of("p", Dimension.AGENT, "Agent", List.of(""), "a"));
        assertThrows(IllegalArgumentException.class,
                () -> SemanticId.of("p", Dimension.AGENT, "Agent", List.of("MAS"), ""));
    }
}

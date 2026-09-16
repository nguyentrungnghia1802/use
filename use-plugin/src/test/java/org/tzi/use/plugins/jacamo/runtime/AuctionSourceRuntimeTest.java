package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cartago.ArtifactId;
import cartago.CartagoEnvironment;
import cartago.Op;
import cartago.util.agent.ActionFailedException;
import cartago.util.agent.CartagoBasicContext;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuctionSourceRuntimeTest {
    @TempDir Path compiledClasses;

    @Test
    void compilesAndExecutesTheSameAuctionSourcesImportedByTheEvidencePipeline() throws Exception {
        Path project = Path.of("src/test/resources/auction").toAbsolutePath().normalize();
        CartagoEnvironment environment = CartagoEnvironment.getInstance();
        environment.init();
        CartagoBasicContext context = new CartagoBasicContext("auction-source-test");
        ArtifactId artifact = null;
        try (AuctionSourceRuntime runtime = AuctionSourceRuntime.load(project, compiledClasses, environment)) {
            assertTrue(runtime.jasonAgent().getBB().contains(
                    jason.asSyntax.ASSyntax.parseLiteral("auction_open")) != null);
            ArtifactId created = context.makeArtifact(context.getJoinedWspId("main"), "auction-source-runtime",
                    runtime.artifactClassName());
            artifact = created;

            context.doAction(created, new Op("placeBid", "item1", 10));
            assertThrows(ActionFailedException.class,
                    () -> context.doAction(created, new Op("placeBid", "item1", 0)));
            context.doAction(created, new Op("closeAuction"));
            var open = environment.getController("/main").getArtifactInfo("auction-source-runtime")
                    .getObsProperties().stream().filter(property -> property.getName().equals("open"))
                    .findFirst().orElseThrow();
            assertEquals(false, open.getValue());
        } finally {
            if (artifact != null) environment.getController("/main").removeArtifact("auction-source-runtime");
        }
    }
}

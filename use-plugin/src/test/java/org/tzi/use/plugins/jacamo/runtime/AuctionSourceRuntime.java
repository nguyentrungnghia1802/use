package org.tzi.use.plugins.jacamo.runtime;

import cartago.CartagoEnvironment;
import cartago.URLArtifactFactory;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import jason.asSemantics.Agent;

/** Loads the checked-in Auction sources into the real in-process JaCaMo components. */
final class AuctionSourceRuntime implements AutoCloseable {
    private static final String FACTORY = "phase14-auction-source";
    private static final String ARTIFACT_CLASS = "auction.AuctionArtifact";
    private final CartagoEnvironment environment;
    private final URLArtifactFactory artifactFactory;
    private final Agent jasonAgent;

    private AuctionSourceRuntime(CartagoEnvironment environment, URLArtifactFactory artifactFactory,
                                 Agent jasonAgent) {
        this.environment = environment;
        this.artifactFactory = artifactFactory;
        this.jasonAgent = jasonAgent;
    }

    static AuctionSourceRuntime load(Path project, Path compiledClasses, CartagoEnvironment environment)
            throws Exception {
        Path source = project.resolve("src/env/auction/AuctionArtifact.java");
        Files.createDirectories(compiledClasses);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("AUCTION_SOURCE_JDK_COMPILER_MISSING");
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(null, diagnostics, diagnostics,
                "-classpath", System.getProperty("java.class.path"),
                "-d", compiledClasses.toString(), source.toString());
        if (result != 0) throw new IllegalStateException("AUCTION_SOURCE_COMPILE_FAILED: "
                + diagnostics.toString(StandardCharsets.UTF_8));

        Agent agent = new Agent();
        agent.setConsiderToAddMIForThisAgent(false);
        agent.initAg(project.resolve("src/agt/auctioneer.asl").toString());

        URLArtifactFactory factory = new URLArtifactFactory(FACTORY, compiledClasses.toUri().toURL());
        environment.addArtifactFactory("/main", factory);
        return new AuctionSourceRuntime(environment, factory, agent);
    }

    String artifactClassName() { return ARTIFACT_CLASS; }
    Agent jasonAgent() { return jasonAgent; }

    @Override public void close() throws Exception {
        environment.removeArtifactFactory("/main", FACTORY);
        artifactFactory.cloader.close();
    }
}

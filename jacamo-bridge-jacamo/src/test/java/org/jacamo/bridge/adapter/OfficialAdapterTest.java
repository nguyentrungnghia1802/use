package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialAdapterTest {
    @TempDir Path temporary;
    private static Path hello() { return Path.of("..", "use-plugin", "src", "test", "resources", "canonical-cases", "hello-world", "helloworld.jcm").toAbsolutePath().normalize(); }
    private static Path original(String name) { return Path.of("..", "..", "JaCaMo", "examples", name, name + ".jcm").toAbsolutePath().normalize(); }

    @Test void canonicalHelloLoadsThroughOfficialMergedProjectJasonAndMoiseObjects() throws Exception {
        Path jcm=hello(); assertTrue(Files.isRegularFile(jcm));
        assertEquals("c81d15c9aa80c6e75ee8ead017f8daaddb1038ec9cfbc80c6a3057bde10b4101",AdapterEvidence.digest(Files.readAllBytes(jcm)));
        var project=new OfficialProjectLoader().load(jcm); var adapter=new OfficialProjectAdapter();
        var first=adapter.adapt(project,jcm); var second=adapter.adapt(new OfficialProjectLoader().load(jcm),jcm);
        assertEquals(first.agentDeclarations(),second.agentDeclarations());
        assertEquals(first.organisationFacts(),second.organisationFacts());
        assertEquals(first.modelRevision(),second.modelRevision());
        assertFalse(first.agentDeclarations().isEmpty());
        assertTrue(first.agentDeclarations().stream().anyMatch(f->f.factKind().equals("plan")&&f.attributes().containsKey("bodyAst")),
                () -> "facts=" + first.agentDeclarations() + " unresolved=" + first.unresolvedFacts());
        assertFalse(first.workspaces().isEmpty()); assertFalse(first.configuredArtifacts().isEmpty());
        assertTrue(first.organisationFacts().stream().anyMatch(f->f.factKind().equals("role")));
        assertTrue(first.organisationFacts().stream().anyMatch(f->f.factKind().equals("norm")));
        assertFalse(first.groupRoleCardinalities().isEmpty());
        assertTrue(first.crossDimensionalRelations().stream().anyMatch(f->f.factKind().equals("focus")));
        assertTrue(first.sources().stream().allMatch(f->f.evidence().stream().allMatch(e->e.sourceUri().startsWith("project:/"))));
    }

    @Test void jasonAnonymousVariableCanonicalizationDoesNotRewriteQuotedOrNamedTerms() {
        assertEquals("p(_,_,\"_17\",'_18',_named,x_19)",
                OfficialJasonAdapter.canonicalAst("p(_17,_204,\"_17\",'_18',_named,x_19)"));
    }

    @Test void pinnedOfficialApiSignaturesExistAndNoCorePatchIsRequired() throws Exception {
        assertTrue(jacamo.project.JaCaMoProject.class.getProtectionDomain().getCodeSource().getLocation().toString().contains("jacamo-1.3.1.jar"));
        assertNotNull(jacamo.platform.Platform.class.getMethod("setJcmProject",jacamo.project.JaCaMoProject.class));
        assertNotNull(jason.asSemantics.Agent.class.getMethod("parseAS",java.io.File.class));
        assertNotNull(cartago.ICartagoController.class.getMethod("getArtifactInfo",String.class));
        assertNotNull(moise.os.OS.class.getMethod("loadOSFromURI",String.class));
        assertNotNull(npl.NPLInterpreter.class.getMethod("addListener",npl.NormativeListener.class));
        assertTrue(BridgeAgArch.class.getSuperclass().equals(jason.architecture.AgArch.class));
    }

    @Test void platformLifecycleCreatesNewSessionAndStopsCleanly() throws Exception {
        var project=new OfficialProjectLoader().load(hello()); String firstSession;
        var platform=new JaCaMoBridgePlatform(); platform.setJcmProject(project); platform.init(new String[0]); platform.start();
        firstSession=platform.sessionId(); assertFalse(platform.modelSnapshot().agentDeclarations().isEmpty()); platform.stop();
        assertEquals(AdapterReadiness.STOPPED,platform.readiness().state("bridge").readiness());
        platform.setJcmProject(new OfficialProjectLoader().load(hello())); platform.init(new String[0]); platform.start();
        assertNotEquals(firstSession,platform.sessionId()); platform.stop();
    }

    @Test void platformHostsAuthenticatedLoopbackTransportWhenExplicitlyConfigured() throws Exception {
        int port;
        try (var reservation = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            port = reservation.getLocalPort();
        }
        Path secret = Files.writeString(temporary.resolve("bridge-secret.hex"), "ab".repeat(32));
        var platform = new JaCaMoBridgePlatform();
        platform.setJcmProject(new OfficialProjectLoader().load(hello()));
        platform.init(new String[] {"port=" + port, "secretFile=" + secret,
                "distributionSha256=" + "1".repeat(64)});
        platform.start();
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),
                    platform.serverPort()), 1000);
            assertTrue(socket.isConnected());
            assertEquals(AdapterReadiness.READY, platform.readiness().state("transport").readiness());
        } finally {
            platform.stop();
        }
        assertEquals(AdapterReadiness.STOPPED, platform.readiness().state("bridge").readiness());
    }

    @Test void originalAuctionAndHouseUseTheSameOfficialProjectAdapter() throws Exception {
        var adapter = new OfficialProjectAdapter();
        var auction = adapter.adapt(new OfficialProjectLoader().load(original("auction")), original("auction"));
        assertFalse(auction.organisationFacts().isEmpty());
        assertFalse(auction.groupRoleCardinalities().isEmpty());
        assertTrue(auction.organisationFacts().stream().anyMatch(f -> f.factKind().equals("norm")));

        var house = adapter.adapt(new OfficialProjectLoader().load(original("house-building")), original("house-building"));
        int configuredInstances = house.agentDeclarations().stream().filter(f -> f.factKind().equals("agent-declaration"))
                .mapToInt(f -> Integer.parseInt(f.attributes().get("instances"))).sum();
        assertEquals(22, configuredInstances, "official JCM instance policy");
        assertTrue(house.organisationFacts().isEmpty(), "the original JCM does not statically declare its runtime-created organisation");

        Path houseRoot = original("house-building").getParent();
        var houseOs = new OfficialMoiseAdapter().load(houseRoot, houseRoot.resolve("src/org/house-os.xml"), "house_building");
        assertEquals(13, houseOs.facts().stream().filter(f -> f.factKind().equals("organisational-goal")).count());
        assertFalse(houseOs.groupRoleCardinalities().isEmpty());
    }
}

package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CompactMappingReaderTest {

    @Test
    void parsesValidCompactMappingWithAllSections() {
        String xml = """
                <mapping id="auction" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:auction_env.AuctionArtifact" target="Auction"/>
                    <map source="artifact:auction_env.AuctionArtifact/property:winner" target="Auction.winner"/>
                    <map source="artifact:auction_env.AuctionArtifact/field:currentWinner" target="Auction.currentWinner"/>
                  </csl>
                  <istar>
                    <map source="asl:src/agt/auction_capabilities.asl/goal:do_auction" target="auction_capabilities.do_auction"/>
                    <map source="asl:src/agt/auction_capabilities.asl/plan:do_auction/step:bid[1..2]" target="auction_capabilities.bid"/>
                  </istar>
                  <runtime>
                    <actor-runtime actor="auctioneer" activation-policy="EXPLICIT_ENACTMENT" episode-policy="STATIC_LIFETIME"/>
                    <milestones kind="DOMAIN"/>
                    <requirement id="task_boundaries" provider="jason" required="true">
                      <param name="anchor" value="step_bounds"/>
                    </requirement>
                  </runtime>
                </mapping>
                """;

        CompactMappingSpecification spec = CompactMappingReader.read(xml, "auction.xml");
        assertNotNull(spec);
        assertEquals("auction", spec.id());
        assertNotNull(spec.sha256());
        assertEquals(3, spec.cslMappings().size());
        assertEquals(2, spec.istarMappings().size());
        assertEquals(1, spec.actorRuntimes().size());
        assertEquals(1, spec.requirements().size());
        assertEquals("DOMAIN", spec.milestones().kind());

        assertEquals("artifact:auction_env.AuctionArtifact", spec.cslMappings().get(0).source());
        assertEquals("Auction", spec.cslMappings().get(0).target());

        assertEquals("auctioneer", spec.actorRuntimes().get(0).actor());
        assertEquals("task_boundaries", spec.requirements().get(0).id());
        assertEquals("step_bounds", spec.requirements().get(0).parameters().get("anchor"));
    }

    @Test
    void parsesMiniatureNonAuctionFixture() {
        // Non-Auction miniature fixture: Robot vacuum cleaner
        String xml = """
                <mapping id="vacuum" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:devices.VacuumArtifact" target="VacuumCleaner"/>
                    <map source="artifact:devices.VacuumArtifact/property:battery" target="VacuumCleaner.batteryLevel"/>
                    <map source="moise:cleaning_org/group:cleaning_team" target="CleaningTeam"/>
                  </csl>
                  <istar>
                    <map source="moise:cleaning_org/role:cleaner" target="CleanerActor"/>
                    <map source="moise:cleaning_org/goal:clean_room" target="CleanerActor.clean_room"/>
                    <map source="agent:cleaner_bot" target="CleanerActor"/>
                  </istar>
                  <runtime>
                    <actor-runtime actor="CleanerActor" activation-policy="ROLE_ADOPTION" episode-policy="DYNAMIC_EPISODE"/>
                  </runtime>
                </mapping>
                """;

        CompactMappingSpecification spec = CompactMappingReader.read(xml, "vacuum.xml");
        assertNotNull(spec);
        assertEquals("vacuum", spec.id());
        assertEquals(3, spec.cslMappings().size());
        assertEquals(3, spec.istarMappings().size());
        assertEquals(1, spec.actorRuntimes().size());
        assertEquals("CleanerActor", spec.actorRuntimes().get(0).actor());
        assertEquals("ROLE_ADOPTION", spec.actorRuntimes().get(0).activationPolicy());
    }

    @Test
    void rejectsForbiddenLegacyElements() {
        String xmlWithTable = """
                <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:A" target="B"/>
                  </csl>
                  <tables>
                    <table name="legacy"/>
                  </tables>
                </mapping>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CompactMappingReader.read(xmlWithTable, "forbidden.xml"));
        assertTrue(ex.getMessage().contains("FORBIDDEN_PROCEDURAL_ELEMENT"), "Message: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("tables"), "Message: " + ex.getMessage());
    }

    @Test
    void rejectsForbiddenDatasetOrJoin() {
        String xmlWithJoin = """
                <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <dataset name="ds1"/>
                  </csl>
                </mapping>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CompactMappingReader.read(xmlWithJoin, "forbidden_ds.xml"));
        assertTrue(ex.getMessage().contains("FORBIDDEN_PROCEDURAL_ELEMENT"), "Message: " + ex.getMessage());
    }

    @Test
    void rejectsUnknownOrInvalidNamespace() {
        String badNs = """
                <mapping xmlns="urn:use:jacamo-mapping:2" version="3.0">
                  <csl>
                    <map source="artifact:A" target="B"/>
                  </csl>
                </mapping>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CompactMappingReader.read(badNs, "bad_ns.xml"));
        assertTrue(ex.getMessage().contains("INVALID_NAMESPACE"), "Message: " + ex.getMessage());
    }

    @Test
    void rejectsUnsupportedVersion() {
        String badVer = """
                <mapping xmlns="urn:use:jacamo-mapping:3" version="2.0">
                  <csl>
                    <map source="artifact:A" target="B"/>
                  </csl>
                </mapping>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CompactMappingReader.read(badVer, "bad_ver.xml"));
        assertTrue(ex.getMessage().contains("UNSUPPORTED_VERSION"), "Message: " + ex.getMessage());
    }

    @Test
    void rejectsDoctypeAndExternalEntitiesSecurely() {
        String xxe = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE mapping [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="&xxe;" target="B"/>
                  </csl>
                </mapping>
                """;

        assertThrows(IllegalArgumentException.class, () -> CompactMappingReader.read(xxe, "xxe.xml"));
    }

    @Test
    void rejectsDuplicateMappingsInSameSection() {
        String dupXml = """
                <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="artifact:A" target="B"/>
                    <map source="artifact:A" target="B"/>
                  </csl>
                </mapping>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CompactMappingReader.read(dupXml, "duplicate.xml"));
        assertTrue(ex.getMessage().contains("DUPLICATE_MAPPING"), "Message: " + ex.getMessage());
    }

    @Test
    void rejectsInvalidSourceSyntax() {
        String badSource = """
                <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <csl>
                    <map source="not_a_valid_scheme:foo" target="B"/>
                  </csl>
                </mapping>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CompactMappingReader.read(badSource, "bad_source.xml"));
        assertTrue(ex.getMessage().contains("INVALID_SOURCE_REFERENCE"), "Message: " + ex.getMessage());
    }

    @Test
    void rejectsInvalidStepRangeSyntax() {
        String badStep = """
                <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0">
                  <istar>
                    <map source="asl:file.asl/plan:p/step:s[5..2]" target="Actor.task"/>
                  </istar>
                </mapping>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CompactMappingReader.read(badStep, "bad_step.xml"));
        assertTrue(ex.getMessage().contains("STEP_RANGE_INVALID_BOUNDS"), "Message: " + ex.getMessage());
    }

    @Test
    void readsFromFilePath() throws IOException {
        Path tempFile = Files.createTempFile("test-compact-mapping", ".xml");
        try {
            String xml = """
                    <mapping id="temp-test" xmlns="urn:use:jacamo-mapping:3" version="3.0">
                      <csl>
                        <map source="artifact:A" target="B"/>
                      </csl>
                    </mapping>
                    """;
            Files.writeString(tempFile, xml);
            CompactMappingSpecification spec = CompactMappingReader.read(tempFile);
            assertEquals("temp-test", spec.id());
            assertEquals(1, spec.cslMappings().size());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}


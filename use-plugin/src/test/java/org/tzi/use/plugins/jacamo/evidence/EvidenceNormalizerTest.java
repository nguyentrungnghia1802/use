package org.tzi.use.plugins.jacamo.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EvidenceNormalizerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void normalizesOnlyTheExactProjectPathWithoutChangingJsonEscapeSemantics() throws Exception {
        Path project = Path.of("C:\\work\\auction");
        String source = "{\"path\":\"C:\\\\work\\\\auction\\\\src\\\\auction.jcm\","
                + "\"explanation\":\"checking structure...\\r\\nchecked structure\","
                + "\"literal\":\"keep\\\\this\","
                + "\"prefixCollision\":\"C:\\\\work\\\\auctioneer\","
                + "\"prose\":\"C:\\\\work\\\\auction\\\\src then C:\\\\other\\\\file\","
                + "\"case:C:\\\\work\\\\auction\\\\verification\\\\auction.ocl\":\"hash\"}\r\n";
        JsonNode before = json.readTree(source);

        String normalized = EvidenceNormalizer.normalizeJson(source, project, "<auction>");
        JsonNode after = json.readTree(normalized);

        assertEquals("<auction>/src/auction.jcm", after.path("path").asText());
        assertEquals(before.path("explanation").asText(), after.path("explanation").asText());
        assertEquals(before.path("literal").asText(), after.path("literal").asText());
        assertEquals(before.path("prefixCollision").asText(), after.path("prefixCollision").asText());
        assertEquals("<auction>/src then C:\\other\\file", after.path("prose").asText());
        assertEquals("hash", after.path("case:<auction>/verification/auction.ocl").asText());
        assertTrue(normalized.contains("\\r\\n"));
        assertFalse(normalized.contains("/r/n"));
        assertFalse(normalized.contains("\r\n"));
    }

    @Test
    void makesAPlainTextProjectPathPortableWithoutTouchingOtherBackslashes() {
        Path project = Path.of("C:\\work\\auction");
        String source = "PROFILE|C:\\work\\auction\\verification\\auction.ocl|hash\r\n"
                + "EXPLANATION|keep\\r\\n and keep\\literal\r\n";

        String normalized = EvidenceNormalizer.normalizeText(source, project, "<auction>");

        assertEquals("PROFILE|<auction>/verification/auction.ocl|hash\n"
                + "EXPLANATION|keep\\r\\n and keep\\literal\n", normalized);
    }
}

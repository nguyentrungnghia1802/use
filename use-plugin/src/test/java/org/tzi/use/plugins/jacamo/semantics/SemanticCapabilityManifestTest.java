package org.tzi.use.plugins.jacamo.semantics;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
class SemanticCapabilityManifestTest {
    @Test void packagedCapabilitiesMatchActualVersionedProfileAndStateTheirUnsupportedBoundary()throws Exception {
        try(var input=getClass().getResourceAsStream("/org/tzi/use/plugins/jacamo/semantics/capabilities.json")) {
            assertNotNull(input);var manifest=CanonicalJson.object(CanonicalJson.decode(input.readAllBytes()));assertEquals("NATIVE_CURRENT",manifest.get("defaultProjection"));var profiles=CanonicalJson.object(manifest.get("profiles"));
            for(var entry:Map.of("CSL_OBSERVER_V1",SemanticsProfile.v1(),"CSL_OBSERVER_V2",SemanticsProfile.v2()).entrySet()){var item=CanonicalJson.object(profiles.get(entry.getKey()));assertEquals(entry.getValue().version(),item.get("version"));assertEquals(entry.getValue().capabilities(),new HashSet<>((List<?>)item.get("capabilities")));}
            assertEquals("UNSUPPORTED",CanonicalJson.object(manifest.get("boundaries")).get("globalMasAtomicity"));assertTrue(((List<?>)manifest.get("unsupported")).contains("UNBOUNDED_MODEL_CHECKING"));
        }
    }
}

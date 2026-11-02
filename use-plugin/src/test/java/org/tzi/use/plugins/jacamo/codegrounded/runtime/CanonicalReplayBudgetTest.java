package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalReplayBudgetTest {
    @Test void losslessCompressedSourceHasExplicitDecodedBudgetWithoutRaisingAssetLimits() {
        String version="3.5.0";
        assertTrue(CanonicalReplaySupport.declarativeVersion(version));
        var policy=Map.<String,Object>of("schemaVersion",version,"runtimeByteLimit",2147483648L);
        assertEquals(2147483648L,CanonicalReplaySupport.fileLimit(policy,"runtime.jsonl"));
        assertEquals(67108864L,CanonicalReplaySupport.fileLimit(policy,"program.json"));
        for(Object invalid:List.of(32767,8589934593L,new BigDecimal("536870912.5"),"536870912"))
            assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version,"runtimeByteLimit",invalid),"runtime.jsonl"));
        assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version),"runtime.jsonl"));
    }
    @Test void legacyLimitsCannotBeRaisedByAddingBudgetMetadata() {
        for(String version:List.of("2.0.0","3.0.0","3.1.0","3.3.0")) {
            assertEquals(version.equals("3.1.0")||version.equals("3.3.0")?268435456L:67108864L,CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version),"runtime.jsonl"));
            assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version,"runtimeByteLimit",402653184L),"runtime.jsonl"));
        }
    }
    @Test void extendedCaseBudgetIsExplicitIntegralBoundedAndRuntimeOnly() {
        for(String version:List.of("3.2.0","3.4.0")) {
            var valid=Map.<String,Object>of("schemaVersion",version,"runtimeByteLimit",402653184L);
            assertEquals(402653184L,CanonicalReplaySupport.fileLimit(valid,"runtime.jsonl"));
            for(String asset:List.of("model.use","program.json","baseline.cmd","bindings.json","constraints.ocl"))assertEquals(67108864L,CanonicalReplaySupport.fileLimit(valid,asset));
            assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version),"runtime.jsonl"));
            for(Object invalid:List.of(-1,0,268435456L,1073741825L,new BigDecimal("402653184.1"),new BigDecimal("1E30"),"402653184"))
                assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version,"runtimeByteLimit",invalid),"runtime.jsonl"),invalid.toString());
            assertEquals(1073741824L,CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version,"runtimeByteLimit",1073741824L),"runtime.jsonl"));
        }
    }
}

package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalReplayBudgetTest {
    @Test void legacyLimitsCannotBeRaisedByAddingBudgetMetadata() {
        for(String version:List.of("2.0.0","3.0.0","3.1.0")) {
            assertEquals(version.equals("3.1.0")?268435456L:67108864L,CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version),"runtime.jsonl"));
            assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion",version,"runtimeByteLimit",402653184L),"runtime.jsonl"));
        }
    }
    @Test void extendedCaseBudgetIsExplicitIntegralBoundedAndRuntimeOnly() {
        var valid=Map.<String,Object>of("schemaVersion","3.2.0","runtimeByteLimit",402653184L);
        assertEquals(402653184L,CanonicalReplaySupport.fileLimit(valid,"runtime.jsonl"));
        for(String asset:List.of("model.use","program.json","baseline.cmd","bindings.json","constraints.ocl"))assertEquals(67108864L,CanonicalReplaySupport.fileLimit(valid,asset));
        assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion","3.2.0"),"runtime.jsonl"));
        for(Object invalid:List.of(-1,0,268435456L,1073741825L,new BigDecimal("402653184.1"),new BigDecimal("1E30"),"402653184"))
            assertThrows(IllegalArgumentException.class,()->CanonicalReplaySupport.fileLimit(Map.of("schemaVersion","3.2.0","runtimeByteLimit",invalid),"runtime.jsonl"),invalid.toString());
        assertEquals(1073741824L,CanonicalReplaySupport.fileLimit(Map.of("schemaVersion","3.2.0","runtimeByteLimit",1073741824L),"runtime.jsonl"));
    }
}

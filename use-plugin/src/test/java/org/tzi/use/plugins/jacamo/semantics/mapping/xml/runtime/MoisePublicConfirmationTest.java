package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the additional authority gate, after pinned scheme-cut validation. */
class MoisePublicConfirmationTest {
    private Map<String,Object> baseline(){return Map.of("kind","CREATION_BASELINE","origin","ARTIFACT_CREATED","publicationEpochMillis","");}
    private Map<String,Object> change(String origin,String epoch){return Map.of("kind","STATE_CHANGE","origin",origin,"publicationEpochMillis",epoch);}
    @Test void sourceClockAuthorityRequiresActualPinnedCallbackBytecode() {
        var proof=new HashMap<String,Object>(Map.of("version","CARTAGO_PUBLICATION_CLOCK/1.0","workspaceClassSha256","0578556e6977dd23d04de4b5f55a293be9ba7da0fa14d16254ae804b10af4798","status","COMPLETE","unit","EPOCH_MILLISECONDS","authority","WORKSPACE_NEW_PERCEPT_LOGGER_SYSTEM_CURRENT_TIME_MILLIS"));
        assertTrue(MoisePublicConfirmation.clockQualified(proof));
        assertFalse(MoisePublicConfirmation.clockQualified(null),"Older receipts cannot retroactively gain clock authority");
        for(String field:proof.keySet()) {var changed=new HashMap<>(proof);changed.put(field,"UNSUPPORTED");assertFalse(MoisePublicConfirmation.clockQualified(changed),field);}
    }
    @Test void completeSourcePublicationPrefixIsExplicitlyDifferentFromInternalTruthTime() {
        var valid=List.of(baseline(),change("PROPERTY_PUBLICATION","1000"),change("PROPERTY_PUBLICATION","1000"),change("PROPERTY_PUBLICATION","1001"));
        assertTrue(MoisePublicConfirmation.complete(true,valid));
        assertEquals("MOISE_PUBLIC_CONFIRMATION/1.0",MoisePublicConfirmation.VERSION);
        assertFalse(MoisePublicConfirmation.complete(false,valid),"Late attach or source gap cannot grant confirmation authority");
        assertFalse(MoisePublicConfirmation.complete(true,List.of()),"No creation prefix");
        assertFalse(MoisePublicConfirmation.complete(true,List.of(baseline(),change("SNAPSHOT","1000"))),"Poll time is not confirmation");
        assertFalse(MoisePublicConfirmation.complete(true,List.of(baseline(),change("OP_SUCCEEDED","1000"))),"Return time is not confirmation");
        assertFalse(MoisePublicConfirmation.complete(true,List.of(baseline(),change("PROPERTY_PUBLICATION",""))),"Missing source timestamp");
        assertFalse(MoisePublicConfirmation.complete(true,List.of(baseline(),change("PROPERTY_PUBLICATION","1001"),change("PROPERTY_PUBLICATION","1000"))),"Clock regression is unknown, not a violated order property");
    }
}

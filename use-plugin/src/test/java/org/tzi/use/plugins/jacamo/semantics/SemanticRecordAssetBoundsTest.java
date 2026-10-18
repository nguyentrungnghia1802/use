package org.tzi.use.plugins.jacamo.semantics;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticRecordAssetBoundsTest {
    @Test void trustedBase64AssetRoundTripsWithoutChangingUntrustedWireStringLimit() {
        byte[] source=new byte[CanonicalJson.DEFAULT_MAX_STRING];Arrays.fill(source,(byte)'x');
        var asset=TraceComplement.Asset.of("official-model","OFFICIAL_MODEL_SNAPSHOT_JSON",source);
        assertTrue(asset.contentBase64().length()>CanonicalJson.DEFAULT_MAX_STRING);
        var tree=CanonicalJson.object(SemanticJson.tree(asset));assertEquals(asset.sha256(),tree.get("sha256"));assertArrayEquals(source,Base64.getDecoder().decode((String)tree.get("contentBase64")));
        byte[] trusted=CanonicalJson.encode(tree,CanonicalJson.DEFAULT_MAX_BYTES);
        assertThrows(ContractException.class,()->CanonicalJson.encode(tree),"Default wire encoder must retain its original guard");
        assertThrows(ContractException.class,()->CanonicalJson.decode(trusted),"Default wire decoder must retain its original guard");
        assertEquals(tree,CanonicalJson.decode(trusted,CanonicalJson.DEFAULT_MAX_BYTES,CanonicalJson.DEFAULT_MAX_DEPTH,CanonicalJson.DEFAULT_MAX_BYTES));
        assertEquals(SemanticJson.hash(tree),SemanticJson.hash(CanonicalJson.object(SemanticJson.tree(asset))));
    }
    @Test void trustedRecordsStillEnforceTotalBytesAndDepthBudgets() {
        assertThrows(ContractException.class,()->SemanticJson.tree(Map.of("tooLarge","x".repeat(CanonicalJson.DEFAULT_MAX_BYTES))));
        assertThrows(ContractException.class,()->CanonicalJson.encode("x",0));assertThrows(ContractException.class,()->CanonicalJson.encode("x",CanonicalJson.DEFAULT_MAX_BYTES+1));
        Object tree="leaf";for(int i=0;i<CanonicalJson.DEFAULT_MAX_DEPTH+2;i++)tree=List.of(tree);Object deep=tree;
        assertThrows(RuntimeException.class,()->SemanticJson.tree(deep));
    }
}

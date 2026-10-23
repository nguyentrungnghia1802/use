package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HouseBuildingWaitFixtureTest {
    @TempDir Path temp;
    @Test void onlyDeclaredWaitChangesInOwnedStageAndReferenceBytesStayUntouched()throws Exception {
        var reference=Path.of("../../jacamo/examples/house-building/src/agt/giacomo.asl");var bytes=Files.readAllBytes(reference);
        var target=temp.resolve("src/agt/giacomo.asl");Files.createDirectories(target.getParent());Files.write(target,bytes);
        var proof=HouseBuildingWaitFixture.apply(temp,temp);
        assertEquals(HouseBuildingWaitFixture.PROFILE,proof.get("profile"));assertArrayEquals(bytes,Files.readAllBytes(reference));
        assertEquals(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replace(proof.get("original").toString(),proof.get("replacement").toString()),Files.readString(target));
        assertTrue(Files.isRegularFile(temp.resolve("fixture-manifest.json")));
    }
    @Test void changedReferenceCannotBeSilentlyPatched()throws Exception {
        var target=temp.resolve("src/agt/giacomo.asl");Files.createDirectories(target.getParent());Files.writeString(target,"changed");
        var failure=assertThrows(IllegalStateException.class,()->HouseBuildingWaitFixture.apply(temp,temp));
        assertEquals("FIXTURE_SOURCE_HASH",failure.getMessage());assertEquals("changed",Files.readString(target));
        assertFalse(Files.exists(temp.resolve("fixture-manifest.json")));
    }
}

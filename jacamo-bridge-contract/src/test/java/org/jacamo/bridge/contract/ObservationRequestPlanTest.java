package org.jacamo.bridge.contract;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ObservationRequestPlanTest {
    @TempDir Path root;
    @Test void selectiveHandoffPinsReadClosureAndRejectsScopeTamperingAndVersionDowngrade() throws Exception {
        var scope=new ObservationCaptureScope(false,Set.of("example.Meter"),false,Set.of("reading"),"b".repeat(64));
        var file=root.resolve("selective.json");
        ObservationRequestPlan.publish(file,"session",1,"revision","a".repeat(64),List.of(),
                Map.of("jason",List.of("active")),Set.of(RuntimeFactKind.ARTIFACT,RuntimeFactKind.PROPERTY),scope);
        assertEquals(scope,ObservationRequestPlan.read(file,"session",1,"revision").captureScope());
        assertTrue(scope.includesArtifactClass("example.Meter"));assertFalse(scope.includesArtifactClass("unrelated.Noise"));
        assertTrue(scope.includesProperty("reading"));assertFalse(scope.includesProperty("secret"));
        assertFalse(new ObservationCaptureScope(false,Set.of(),false,Set.of(),"b".repeat(64)).includesProperty("reading"));
        var value=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(file))));
        var tampered=new TreeMap<>(CanonicalJson.object(value.get("captureScope")));tampered.put("allProperties",true);tampered.put("propertyNames",List.of());
        value.put("captureScope",tampered);Files.write(file,CanonicalJson.encode(value));
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.read(file,"session",1,"revision"));
        value.put("version","OBSERVATION_REQUEST_PLAN/1.1");Files.write(file,CanonicalJson.encode(value));
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.read(file,"session",1,"revision"));
        assertThrows(IllegalArgumentException.class,()->new ObservationCaptureScope(true,Set.of("hidden"),true,Set.of(),"b".repeat(64)));
    }
    @Test void handoffIsBoundedVersionedAndOwnedByTheAcceptedSourceEpoch() throws Exception {
        var request=new ObservationCapabilityRequest("field","cartago.artifact-field.v1",true,Map.of("class","example.Counter"));
        var file=root.resolve("requests.json");
        ObservationRequestPlan.publish(file,"session",3,"revision","a".repeat(64),List.of(request));
        assertEquals(List.of(request),ObservationRequestPlan.read(file,"session",3,"revision").requests());
        ObservationRequestPlan.publish(file,"session",3,"revision","a".repeat(64),List.of(request),Map.of("jason",List.of("active")),Set.of(RuntimeFactKind.ARTIFACT,RuntimeFactKind.PROPERTY));
        assertEquals(Set.of(RuntimeFactKind.ARTIFACT,RuntimeFactKind.PROPERTY),ObservationRequestPlan.read(file,"session",3,"revision").cartagoCaptureKinds());
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.read(file,"other",3,"revision"));
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.read(file,"session",4,"revision"));
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.read(file,"session",3,"changed"));
        var value=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(file))));value.put("version","unknown");
        Files.write(file,CanonicalJson.encode(value));
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.read(file,"session",3,"revision"));
    }
    @Test void duplicateUnknownShapeAndOversizedPlansFailClosed() throws Exception {
        var file=root.resolve("requests.json");var request=new ObservationCapabilityRequest("x","example.provider",true,Map.of());
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.publish(file,"s",1,"r","a".repeat(64),List.of(request,request)));
        Files.write(file,new byte[524289]);
        assertThrows(IllegalArgumentException.class,()->ObservationRequestPlan.read(file,"s",1,"r"));
    }
}

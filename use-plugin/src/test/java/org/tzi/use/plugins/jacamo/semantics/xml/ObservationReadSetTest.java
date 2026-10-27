package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ObservationReadSet;
import org.tzi.use.semantic.engine.use75.NativeOclBackend;
import org.tzi.use.semantic.engine.use75.OclContractSpec;
import static org.junit.jupiter.api.Assertions.*;

class ObservationReadSetTest {
    @Test void compilerAstClosesNavigationPopulationAndAttributeReadsWithoutCaseVocabulary() throws Exception {
        var api = new UseModelApi("Inventory");
        var item = api.createClass("Parcel", false);
        var depot = api.createClass("Depot", false);
        api.createAttribute("Parcel", "weight", "Integer");
        api.createAssociation("Storage", "Depot", "depot", "1", 0, "Parcel", "parcels", "*", 0);
        try (var backend = new NativeOclBackend(api.getModel(), "program", "profile")) {
            var expression = backend.compile(new OclContractSpec("Capacity", OclContractSpec.Kind.GOAL, depot, null,
                    "self.parcels->forAll(p | p.weight >= 0) and Parcel.allInstances()->size() > 0")).expression();
            var reads = ObservationReadSet.compile(Map.of("Capacity", expression));
            assertEquals(List.of("Parcel::weight"), reads.fields());
            assertEquals(List.of("Parcel"), reads.populations());
            assertEquals(1, reads.navigation().size());
            assertEquals("Storage", reads.navigation().getFirst().get("association"));
            assertEquals("Depot", reads.navigation().getFirst().get("fromType"));
            assertEquals("Parcel", reads.navigation().getFirst().get("toType"));
            assertEquals(reads.hash(), ObservationReadSet.compile(Map.of("Capacity", expression)).hash());
            assertNotEquals(reads.hash(), ObservationReadSet.compile(Map.of()).hash());
            assertTrue(ObservationReadSet.compile(Map.of()).fields().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> reads.navigation().clear());
        }
    }

    @Test void preStateAndTupleFieldReadsAreRetainedAndNativeCompilationRemainsBooleanGated() throws Exception {
        var api = new UseModelApi("State");
        var counter = api.createClass("Meter", false);
        api.createAttribute("Meter", "reading", "Integer");
        try (var backend = new NativeOclBackend(api.getModel(), "p", "profile")) {
            var expression = backend.compile(new OclContractSpec("Unchanged", OclContractSpec.Kind.TRANSITION,
                    counter, null, "Tuple { before : Integer = self.reading@pre }.before <= self.reading")).expression();
            var reads = ObservationReadSet.compile(Map.of("Unchanged", expression));
            assertEquals(List.of("Meter::reading"), reads.fields());
            assertTrue(reads.attributes().stream().anyMatch(r -> Boolean.TRUE.equals(r.get("preState"))));
            assertThrows(IllegalArgumentException.class, () -> backend.compile(new OclContractSpec("Bad",
                    OclContractSpec.Kind.GOAL, counter, null, "self.reading")));
        }
    }
}

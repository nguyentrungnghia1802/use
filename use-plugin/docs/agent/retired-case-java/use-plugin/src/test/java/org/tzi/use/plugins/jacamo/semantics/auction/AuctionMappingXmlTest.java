package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionMappingXmlTest {
    private static final AuctionCaseBundle BUNDLE = AuctionCaseBundle.read(Path.of("examples/auction"));
    private static final String XML = new String(BUNDLE.require("case-rules").bytes(), StandardCharsets.UTF_8);
    private final AuctionMappingXmlLoader loader = new AuctionMappingXmlLoader();

    @Test void loadsAllSectionsAsImmutableLocatedSpecificationNotHistoricalValidationVerdict() {
        var spec = loader.load(BUNDLE);
        assertEquals(18, spec.selectors().size()); assertEquals(8, spec.modelRules().size());
        assertEquals(6, spec.runtimeRules().size()); assertEquals(3, spec.views().size());
        assertEquals(12, spec.root().child("omittedConcepts").children("omit").size());
        assertEquals(3, spec.root().child("verificationBindings").children("operationBinding").size());
        assertTrue(spec.requireId("M01").location().line() > 100);
        assertEquals("passed", spec.root().child("validation").attr("status"));
        assertThrows(UnsupportedOperationException.class, () -> spec.selectors().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.requireId("M01").attributes().clear());
    }

    @Test void rejectsMalformedUnknownNamespaceVersionElementsAttributesAndOperations() {
        for (var changed : List.of(Map.entry(XML.substring(0, XML.length() - 30), "SYNTAX"),
                Map.entry(XML.replace("urn:auction:jacamo-to-csl:1", "urn:unsupported"), "NAMESPACE_UNSUPPORTED"),
                Map.entry(XML.replace("formatVersion=\"1.0\"", "formatVersion=\"99\""), "UNSUPPORTED_VALUE:formatVersion"),
                Map.entry(XML.replace("<createClassifier ", "<executeScript "), "ELEMENT_OR_ATTRIBUTE_SHAPE_UNSUPPORTED"),
                Map.entry(XML.replace("name=\"createClassifier\"", "name=\"executeScript\""), "OPERATION_UNSUPPORTED_OR_DUPLICATE"),
                Map.entry(XML.replace("<profile ", "<profile ignoreErrors=\"true\" "), "ELEMENT_OR_ATTRIBUTE_SHAPE_UNSUPPORTED"),
                Map.entry(XML.replace("expectedCount=\"5\"", "expectedCount=\"-1\""), "NONNEGATIVE_INTEGER_REQUIRED"),
                Map.entry(XML.replace("mutable=\"true\"", "mutable=\"truthy\""), "BOOLEAN_REQUIRED"))) {
            invalid(changed.getKey(), changed.getValue());
        }
    }

    @Test void rejectsDuplicateMissingIdsAndReferencesWithSourcePosition() {
        for (String changed : List.of(XML.replace("id=\"sel-group-config\"", "id=\"sel-org\""),
                XML.replace("selectRef=\"sel-org\"", "selectRef=\"missing-selector\""),
                XML.replace("observerViewRef=\"view-monitor\"", "observerViewRef=\"missing-view\""),
                XML.replace("sourceRef=\"src-jcm\"", "sourceRef=\"missing-source\""),
                XML.replace("id=\"M01\" ", ""))) {
            var error = assertThrows(IllegalArgumentException.class, () -> parse(changed));
            assertTrue(error.getMessage().contains("changed.xml:"), error.getMessage());
        }
    }

    @Test void rejectsEmptyReferencesDuplicateFieldsAndUnknownConverters() {
        invalid(XML.replace("sourceRef=\"src-jcm\"", "sourceRef=\"\""), "EMPTY_REFERENCE");
        invalid(XML.replace("converter=\"java-string\"", "converter=\"guess-string\""), "UNRESOLVED_REFERENCE");
        invalid(XML.replace("<field name=\"bidEnabledAt\"", "<field name=\"now\""), "DUPLICATE_FIELD");
        var spec = parse(XML.replace("gitHead=\"6f3e73801239fd8bfb94ca8d04ba531b903c0ac9\"", "gitHead=\"" + "0".repeat(40) + "\""));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new AuctionMappingValidator().validate(spec, BUNDLE)).getMessage().contains("ENGINE_UPSTREAM_PIN_MISMATCH"));
    }

    @Test void rejectsExternalEntitiesDoctypeOversizeDepthAndNodeFloods() {
        String withDoctype = XML.replace("<auctionJacamoToCslRules ",
                "<!DOCTYPE auctionJacamoToCslRules [<!ENTITY leak SYSTEM 'file:///not-an-allowed-input'>]><auctionJacamoToCslRules ");
        invalid(withDoctype, "SYNTAX");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> loader.parse(new byte[1_048_577], "oversize.xml")).getMessage().contains("BYTE_CAPACITY"));
        String root = "<auctionJacamoToCslRules xmlns='urn:auction:jacamo-to-csl:1'>";
        invalid(root + "<note>".repeat(70) + "x" + "</note>".repeat(70) + "</auctionJacamoToCslRules>", "STRUCTURAL_CAPACITY");
        invalid(root + "<note/>".repeat(4097) + "</auctionJacamoToCslRules>", "STRUCTURAL_CAPACITY");
    }

    @Test void resolvesSourcePinsAndExternalCellsTasksViewsAndClausesAgainstFrozenAssets() {
        var validator = new AuctionMappingValidator();
        for (var changed : List.of(Map.entry(XML.replace("istarTask=\"A_start_t2\"", "istarTask=\"P_bid\""), "ISTAR_TASK_UNRESOLVED"),
                Map.entry(XML.replace("drawioCell=\"attr_id\"", "drawioCell=\"missing-cell\""), "CSL_CELL_UNRESOLVED"),
                Map.entry(XML.replace("clauses=\"Idle Opened\"", "clauses=\"MissingClause Opened\""), "OCL_CLAUSE_UNRESOLVED"),
                Map.entry(XML.replace("path=\"auction.jcm\"", "path=\"elsewhere/auction.jcm\""), "SOURCE_LOCATION_OR_CHECKSUM_MISMATCH"),
                Map.entry(XML.replace("c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb", "0".repeat(64)), "SOURCE_LOCATION_OR_CHECKSUM_MISMATCH"),
                Map.entry(XML.replace("entry=\"asl/org-obedient.asl\"", "entry=\"asl/other.asl\""), "ARCHIVE_PIN_MISMATCH"))) {
            var error = assertThrows(IllegalArgumentException.class, () -> validator.validate(parse(changed.getKey()), BUNDLE));
            assertTrue(error.getMessage().contains(changed.getValue()), error.getMessage());
        }
    }

    private AuctionMappingSpecification parse(String xml) { return loader.parse(xml.getBytes(StandardCharsets.UTF_8), "changed.xml"); }
    private void invalid(String xml, String reason) {
        var error = assertThrows(IllegalArgumentException.class, () -> parse(xml));
        assertTrue(error.getMessage().contains(reason), error.getMessage());
    }
}

package org.tzi.use.plugins.jacamo.ui;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CaseStudyFilesTest {
    @TempDir Path root;

    private void required() throws Exception {
        for (String type : List.of("csl", "istar", "xml")) {
            Files.writeString(root.resolve("arbitrary." + type), "");
        }
    }

    @Test void readsArbitraryNamesCaseInsensitivelyAndAllowsAbsentOcl() throws Exception {
        required();
        Files.move(root.resolve("arbitrary.istar"), root.resolve("another.ISTAR"));
        Path mrv = root.resolve("test.mrv");
        Files.writeString(mrv, """
                # Comment line
                csl = arbitrary.csl
                istar: another.ISTAR
                xml = arbitrary.xml
                """);
        var input = CaseStudyFiles.read(mrv);
        assertEquals(mrv.toAbsolutePath().normalize(), input.mrvFile());
        assertEquals("another.ISTAR", input.istar().getFileName().toString());
        assertNull(input.ocl());

        Files.writeString(root.resolve("extra.OCL"), "");
        Files.writeString(mrv, """
                csl = arbitrary.csl
                istar = another.ISTAR
                xml = arbitrary.xml
                ocl = extra.OCL
                """);
        assertEquals(root.resolve("extra.OCL").toAbsolutePath().normalize(), CaseStudyFiles.read(mrv).ocl());
    }

    @Test void rejectsDirectoryInput() {
        assertThrows(IllegalArgumentException.class, () -> CaseStudyFiles.read(root));
    }

    @Test void rejectsEveryMissingRequiredType() throws Exception {
        required();
        for (String type : List.of("csl", "istar", "xml")) {
            var lines = new ArrayList<>(List.of("csl = arbitrary.csl", "istar = arbitrary.istar", "xml = arbitrary.xml"));
            lines.removeIf(l -> l.startsWith(type));
            Path mrv = root.resolve("missing-" + type + ".mrv");
            Files.write(mrv, lines);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> CaseStudyFiles.read(mrv)).getMessage().contains("MISSING_REQUIRED: ." + type));
        }
    }

    @Test void rejectsDuplicatesForAllFourTypesIncludingOptionalOcl() throws Exception {
        required();
        Files.writeString(root.resolve("arbitrary.ocl"), "");
        for (String type : List.of("csl", "istar", "xml", "ocl")) {
            Path mrv = root.resolve("duplicate-" + type + ".mrv");
            Files.writeString(mrv, """
                    csl = arbitrary.csl
                    istar = arbitrary.istar
                    xml = arbitrary.xml
                    ocl = arbitrary.ocl
                    """ + type + " = arbitrary." + type + "\n");
            assertTrue(assertThrows(IllegalArgumentException.class, () -> CaseStudyFiles.read(mrv)).getMessage().contains("DUPLICATE_TYPE: ." + type));
        }
        assertThrows(IllegalArgumentException.class, () -> CaseStudyFiles.read(root.resolve("missing.mrv")));
    }
}

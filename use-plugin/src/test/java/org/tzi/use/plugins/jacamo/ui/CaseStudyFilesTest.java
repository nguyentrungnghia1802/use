package org.tzi.use.plugins.jacamo.ui;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CaseStudyFilesTest {
    @TempDir Path root;
    private void required()throws Exception {for(String type:List.of("csl","istar","xml"))Files.writeString(root.resolve("arbitrary."+type),"");}
    @Test void readsArbitraryNamesCaseInsensitivelyWithoutRecursingAndAllowsAbsentOcl()throws Exception {
        required();Files.move(root.resolve("arbitrary.istar"),root.resolve("another.ISTAR"));
        var nested=Files.createDirectory(root.resolve("nested"));Files.writeString(nested.resolve("duplicate.csl"),"");
        Files.writeString(root.resolve("readme.md"),"");
        var input=CaseStudyFiles.read(root);assertEquals(root.toAbsolutePath(),input.folder());
        assertEquals("another.ISTAR",input.istar().getFileName().toString());assertNull(input.ocl());
        Files.writeString(root.resolve("extra.OCL"),"");assertEquals(root.resolve("extra.OCL"),CaseStudyFiles.read(root).ocl());
    }
    @Test void rejectsEveryMissingRequiredType()throws Exception {
        required();for(String type:List.of("csl","istar","xml")) {
            var file=root.resolve("arbitrary."+type);Files.delete(file);
            assertTrue(assertThrows(IllegalArgumentException.class,()->CaseStudyFiles.read(root)).getMessage().contains("MISSING_REQUIRED: ."+type));
            Files.writeString(file,"");
        }
    }
    @Test void rejectsDuplicatesForAllFourTypesIncludingOptionalOcl()throws Exception {
        required();Files.writeString(root.resolve("arbitrary.ocl"),"");
        for(String type:List.of("csl","istar","xml","ocl")) {
            var duplicate=Files.writeString(root.resolve("another."+type.toUpperCase(Locale.ROOT)),"");
            assertTrue(assertThrows(IllegalArgumentException.class,()->CaseStudyFiles.read(root)).getMessage().contains("DUPLICATE_TYPE: ."+type));
            Files.delete(duplicate);
        }
        assertThrows(IllegalArgumentException.class,()->CaseStudyFiles.read(root.resolve("missing")));
    }
}

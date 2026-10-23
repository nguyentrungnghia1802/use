package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs;
import static org.junit.jupiter.api.Assertions.*;

class MappingInputsTest {
    @TempDir Path root;
    private Path spec()throws Exception {
        Files.writeString(root.resolve("demo.jcm"),"mas demo {}\n");
        Files.writeString(root.resolve("demo.ocl"),"context Counter inv Nonnegative: self.p >= 0\n");
        String xml=MappingXmlReaderTest.XML.replace("1".repeat(64),hash("demo.jcm")).replace("2".repeat(64),hash("demo.ocl"));
        return Files.writeString(root.resolve("mapping.xml"),xml);
    }
    private String hash(String name)throws Exception{return TraceComplement.Asset.of("check","bytes",Files.readAllBytes(root.resolve(name))).sha256();}
    private MappingInputs read(Path spec){return MappingInputs.read(root.resolve("demo.jcm"),spec,root.resolve("demo.ocl"));}
    @Test void requiresOnlyProjectXmlAndAuthoredOclAndFreezesBytes()throws Exception {
        Path xml=spec();var inputs=read(xml);assertEquals(1,inputs.sources().size());
        assertTrue(Files.list(root).noneMatch(p->p.toString().endsWith(".csl")||p.toString().endsWith(".istar")));
        byte[] frozen=inputs.require("project").bytes();Files.writeString(root.resolve("demo.jcm"),"changed");
        assertArrayEquals(frozen,inputs.require("project").bytes());assertThrows(IllegalArgumentException.class,()->read(xml));
        frozen[0]=0;assertNotEquals(0,inputs.require("project").bytes()[0]);
    }
    @Test void checksumPathAndOclChangesRejectWithoutFallback()throws Exception {
        Path xml=spec();String original=Files.readString(xml);
        Files.writeString(xml,original.replace("demo.jcm","../demo.jcm"));assertThrows(IllegalArgumentException.class,()->read(xml));
        Files.writeString(xml,original);Files.writeString(root.resolve("demo.ocl"),"context Counter inv Arbitrary: false");
        assertThrows(IllegalArgumentException.class,()->read(xml));
    }
}

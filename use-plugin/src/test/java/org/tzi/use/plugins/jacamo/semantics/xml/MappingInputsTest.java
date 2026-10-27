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
    private String bootstrap(Path selected) {
        return "    platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port=12345\", \"secretFile="+root.resolve("secret.hex").toString().replace('\\','/')+"\", \"jcmFile="+selected.toString().replace('\\','/')+"\", \"distributionSha256="+"a".repeat(64)+"\")\r\n";
    }
    @Test void exactLauncherInsertionFreezesCanonicalAndDerivedBytesForPortableReplay()throws Exception {
        Path xml=spec(),project=root.resolve("demo.jcm");String canonical=Files.readString(project),expected=hash("demo.jcm");int closing=canonical.lastIndexOf('}');
        Files.writeString(project,canonical.substring(0,closing)+bootstrap(project)+canonical.substring(closing));
        var inputs=read(xml);assertEquals(expected,inputs.require("project").sha256());assertEquals(hash("demo.jcm"),inputs.projectDigest());
        assertEquals("BRIDGE_PLATFORM_INSERTION/1.0",inputs.projectDerivation().get("recipe"));assertEquals(2,inputs.sources().size());
        Files.delete(project);var restored=MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),inputs.sources(),"portable");
        assertEquals(inputs.projectDigest(),restored.projectDigest());assertEquals(inputs.projectDerivation(),restored.projectDerivation());
        var tampered=new java.util.LinkedHashMap<>(inputs.sources());var derived=inputs.require("project:bridge-derived");
        tampered.put(derived.id(),TraceComplement.Asset.of(derived.id(),derived.format(),new String(derived.bytes(),StandardCharsets.UTF_8).replace("mas demo","mas other").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),tampered,"portable"));
    }
    @Test void changedBusinessSourceArbitraryPlatformDuplicateWrongPathAndInvalidPortCannotNormalize()throws Exception {
        Path xml=spec(),project=root.resolve("demo.jcm");String canonical=Files.readString(project);int closing=canonical.lastIndexOf('}');String prefix=canonical.substring(0,closing),suffix=canonical.substring(closing),line=bootstrap(project);
        for(String invalid:new String[]{prefix.replace("demo","other")+line+suffix,prefix+line+line+suffix,
                prefix+line.replace("port=12345","port=0")+suffix,prefix+bootstrap(root.resolve("other.jcm"))+suffix,
                prefix+line.replace("\"port=12345\", ","\"arbitrary=true\", ")+suffix,
                prefix+line+"\n"+suffix}) {
            Files.writeString(project,invalid);assertThrows(IllegalArgumentException.class,()->read(xml),invalid);
        }
    }
}

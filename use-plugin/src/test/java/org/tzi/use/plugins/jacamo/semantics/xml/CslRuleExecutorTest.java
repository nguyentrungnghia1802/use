package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import static org.junit.jupiter.api.Assertions.*;
class CslRuleExecutorTest {
    @TempDir Path temp;
    static final Path CASE=Path.of("examples/auction");
    static MappingInputs auction(Path root){return MappingInputs.read(root.resolve("auction.jcm"),root.resolve("auction-mapping.xml"),root.resolve("auction_constraints_final.ocl"));}
    @Test void differentCaseUsesSameInterpreterWithNoJavaCaseClasses()throws Exception {
        Files.writeString(temp.resolve("demo.jcm"),"mas inventory {}\n");
        Files.writeString(temp.resolve("demo.ocl"),"context inventory inv Valid: true\n");
        String xml=MappingXmlReaderTest.XML.replace("1".repeat(64),hash(temp.resolve("demo.jcm"))).replace("2".repeat(64),hash(temp.resolve("demo.ocl")))
            .replace("<selectors/>","<selectors><select id=\"model-name\" sourceRef=\"project\" syntax=\"regex\" expectedCount=\"1\"><pattern>mas\\s+(?P&lt;name&gt;\\w+)</pattern></select></selectors>")
            .replace("<modelRules targetVersion=\"v4.0\" targetModel=\"Sample\"/>","<modelRules targetVersion=\"v4.0\" targetModel=\"Inventory\"><rule id=\"entity\" selectRef=\"model-name\"><createClassifier name=\"${name}\" kind=\"Entity\"/></rule></modelRules>");
        var file=Files.writeString(temp.resolve("mapping.xml"),xml);
        var inputs=MappingInputs.read(temp.resolve("demo.jcm"),file,temp.resolve("demo.ocl"));
        var result=new CslRuleExecutor().prepare(inputs);
        assertTrue(result.model().findEntity("inventory").isPresent());assertEquals(1,result.traces().size());
        assertEquals(hash(temp.resolve("demo.jcm")),result.traces().getFirst().row().sourceDigest());
        Files.writeString(file,xml.replace("expectedCount=\"1\"","expectedCount=\"2\""));
        assertThrows(IllegalArgumentException.class,()->new CslRuleExecutor().generate(MappingInputs.read(temp.resolve("demo.jcm"),file,temp.resolve("demo.ocl"))));
    }
    private static String hash(Path p)throws Exception{return org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement.Asset.of("check","bytes",Files.readAllBytes(p)).sha256();}
    @Test void generatesRealCslWithoutOracleOrManifestAndMatchesUnchangedOracle()throws Exception {
        var original=auction(CASE);
        for(var file:original.specification().root().child("sources").children("file")) {
            Path target=temp.resolve(file.attr("path"));Files.createDirectories(target.getParent());Files.copy(CASE.resolve(file.attr("path")),target);
        }
        for(String file:new String[]{"auction-mapping.xml","auction_constraints_final.ocl"})Files.copy(CASE.resolve(file),temp.resolve(file));
        var model=new CslRuleExecutor().generate(auction(temp));
        var oracle=new CslFrontend().compile(Files.readString(CASE.resolve("auction_csl.csl")),"test-only-oracle").requireModel();
        assertEquals(CslModelFingerprint.of(oracle),CslModelFingerprint.of(model));
        Files.writeString(temp.resolve("auction_csl.csl"),"invalid");Files.writeString(temp.resolve("auction_istar_final.istar"),"invalid");
        assertEquals(CslModelFingerprint.of(model),CslModelFingerprint.of(new CslRuleExecutor().generate(auction(temp))));
    }
    @Test void mappingMutationChangesGeneratedModelWithoutTouchingOracle()throws Exception {
        var inputs=auction(CASE);var model=new CslRuleExecutor().generate(inputs);
        for(var f:inputs.specification().root().child("sources").children("file")){Path t=temp.resolve(f.attr("path"));Files.createDirectories(t.getParent());Files.copy(CASE.resolve(f.attr("path")),t);}
        Files.copy(CASE.resolve("auction_constraints_final.ocl"),temp.resolve("auction_constraints_final.ocl"));
        Files.writeString(temp.resolve("auction-mapping.xml"),Files.readString(CASE.resolve("auction-mapping.xml")).replace("name=\"Auction\" owner=\"AuctionOrg\"","name=\"Sale\" owner=\"AuctionOrg\"").replace("owner=\"Auction\"","owner=\"Sale\""));
        var changed=new CslRuleExecutor().generate(auction(temp));assertTrue(changed.findEntity("Sale").isPresent());
        assertNotEquals(CslModelFingerprint.of(model),CslModelFingerprint.of(changed));
    }
}

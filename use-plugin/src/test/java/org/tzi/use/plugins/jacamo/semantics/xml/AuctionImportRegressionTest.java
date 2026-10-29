package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;

/** The user-selected external Auction source, never a replacement toy case. */
class AuctionImportRegressionTest {
    @TempDir Path temporary;
    private final Path checkout=Path.of("..").toAbsolutePath().normalize();
    private final Path caseRoot=checkout.resolve("use-plugin/examples/auction");
    private final Path external=checkout.getParent().resolve("jacamo/examples/auction/auction.jcm");
    private MappingInputs read(Path project) {
        return MappingInputs.read(project,caseRoot.resolve("source-confirmed-v1/auction-mapping.xml"),
                caseRoot.resolve("source-confirmed-v1/auction_confirmed.ocl"),
                caseRoot.resolve("authored-v1/auction_csl.csl"),caseRoot.resolve("authored-v1/references/auction_istar_success_only_v2.istar"));
    }
    @Test void importsExternalAuctionAndKeepsExactRuntimeBytesAndPortableProof() throws Exception {
        byte[] original=Files.readAllBytes(external);
        var inputs=read(external);
        assertEquals(TraceComplement.Asset.of("actual","JCM",original).sha256(),inputs.projectDigest());
        assertArrayEquals(original,inputs.require("src-jcm").bytes(),"The selected revision must be frozen byte-for-byte");
        assertEquals(BridgeJcmDerivation.lineLayoutDigest(Files.readAllBytes(caseRoot.resolve("auction.jcm"))),BridgeJcmDerivation.lineLayoutDigest(original));
        var restored=MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),inputs.sources(),"portable-auction");
        assertEquals(inputs.projectDigest(),restored.projectDigest());
        assertArrayEquals(original,Files.readAllBytes(external),"Import must never rewrite the user's source");
    }
    @Test void formattingProofDoesNotAcceptChangedAuctionSemanticsOrFrozenTampering() throws Exception {
        for(String relative:List.of("auction.jcm","src/org/auction-os.xml","src/agt/auction_capabilities.asl","src/env/auction_env/AuctionArtifact.java","build.gradle")) {
            Path target=temporary.resolve(relative);Files.createDirectories(target.getParent());Files.copy(external.getParent().resolve(relative),target);
        }
        Path project=temporary.resolve("auction.jcm");
        Files.writeString(project,Files.readString(project).replace("mas auction", "mas changed_auction"));
        assertThrows(IllegalArgumentException.class,()->read(project));
        var inputs=read(external);var assets=new LinkedHashMap<>(inputs.sources());
        if(assets.containsKey("src-jcm:bridge-derived")) {
            var proof=assets.get("src-jcm:bridge-derived");
            assets.put(proof.id(),TraceComplement.Asset.of(proof.id(),proof.format(),new String(proof.bytes(),java.nio.charset.StandardCharsets.UTF_8).replace("mas auction","mas changed_auction").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThrows(IllegalArgumentException.class,()->MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),assets,"tampered-auction"));
        }
    }
    @Test void layoutAdmissionIsExplicitAndKeepsQuotedLiteralContentsExact() throws Exception {
        String xml=Files.readString(caseRoot.resolve("source-confirmed-v1/auction-mapping.xml"));
        Path strict=Files.writeString(temporary.resolve("strict.xml"),xml.replaceAll(" layoutPolicy=\"[^\"]+\" layoutSha256=\"[^\"]+\"",""));
        assertThrows(IllegalArgumentException.class,()->MappingInputs.read(external,strict,caseRoot.resolve("source-confirmed-v1/auction_confirmed.ocl"),caseRoot.resolve("authored-v1/auction_csl.csl"),caseRoot.resolve("authored-v1/references/auction_istar_success_only_v2.istar")));
        String source=Files.readString(external);
        assertNotEquals(BridgeJcmDerivation.lineLayoutDigest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                BridgeJcmDerivation.lineLayoutDigest(source.replace("\"a1\"","\"a1\n\n\"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path unsupported=Files.writeString(temporary.resolve("unsupported.xml"),xml.replace("JCM_LINE_LAYOUT/1.0","ARBITRARY_REWRITING"));
        assertThrows(IllegalArgumentException.class,()->MappingInputs.read(external,unsupported,caseRoot.resolve("source-confirmed-v1/auction_confirmed.ocl"),caseRoot.resolve("authored-v1/auction_csl.csl"),caseRoot.resolve("authored-v1/references/auction_istar_success_only_v2.istar")));
    }
}

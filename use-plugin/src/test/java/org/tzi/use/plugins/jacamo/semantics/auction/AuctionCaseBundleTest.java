package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionCaseBundle;
import static org.junit.jupiter.api.Assertions.*;

class AuctionCaseBundleTest {
    private static final Path CASE = Path.of("examples/auction");
    @TempDir Path temporary;

    @Test void readsPinnedSourcesAndTargetsFromPortableDirectoryOnly() throws Exception {
        Path installed = copyCase("installed");
        var bundle = AuctionCaseBundle.read(installed);
        assertEquals("auction", bundle.caseId());
        assertTrue(bundle.assets().size() >= 20);
        assertEquals("c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb", bundle.require("src-jcm").sha256());
        assertEquals("9b63b41bd1f080b5bfb1942d58aa26ebbe01b507347a1635ff09112339a113c9", bundle.require("ref-org-obedient").sha256());
        assertEquals(bundle.hash(), AuctionCaseBundle.read(installed).hash());
        assertEquals("7.5.0", bundle.runtimeRequirements().get("hostUseVersion"));
        bundle.requireLocation("src-jcm", "root-auction", "auction.jcm", bundle.require("src-jcm").sha256());
        assertThrows(IllegalArgumentException.class, () -> bundle.requireLocation("src-jcm", "root-use4goal", "auction.jcm", bundle.require("src-jcm").sha256()));
        assertThrows(UnsupportedOperationException.class, () -> bundle.assets().clear());
        assertThrows(IllegalArgumentException.class, () -> bundle.require("unknown"));
    }

    @Test void freezesRawBytesAndRecordsDerivedSourcesWithoutOverwritingAuthority() throws Exception {
        Path installed = copyCase("frozen");
        var bundle = AuctionCaseBundle.read(installed);
        byte[] original = bundle.require("src-asl").bytes();
        byte[] returned = bundle.require("src-asl").bytes(); returned[0] ^= 1;
        assertArrayEquals(original, bundle.require("src-asl").bytes());
        byte[] derived = (new String(original, java.nio.charset.StandardCharsets.UTF_8) + "\n// derived staging marker\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var receipt = bundle.derive("src-asl", "TEST_STAGING_RECIPE/1", derived);
        assertEquals(bundle.hash(), receipt.bundleHash());
        assertEquals(bundle.require("src-asl").sha256(), receipt.sourceHash());
        assertNotEquals(receipt.sourceHash(), receipt.derived().sha256());
        Files.writeString(installed.resolve("src/agt/auction_capabilities.asl"), "changed after import");
        assertArrayEquals(original, bundle.require("src-asl").bytes());
        assertThrows(IllegalArgumentException.class, () -> AuctionCaseBundle.read(installed));
    }

    @Test void missingOrModifiedInputsDoNotFallBackToDesktopOrReferenceRepositories() throws Exception {
        Path missing = copyCase("missing");
        Files.delete(missing.resolve("auction_csl.drawio"));
        assertThrows(IllegalArgumentException.class, () -> AuctionCaseBundle.read(missing));
        Path changed = copyCase("changed");
        Files.writeString(changed.resolve("auction.jcm"), "mas auction { organisation aorg_2 : auction-os.xml {} }");
        assertThrows(IllegalArgumentException.class, () -> AuctionCaseBundle.read(changed));
        Path newline = copyCase("newline");
        Files.writeString(newline.resolve("auction.jcm"), Files.readString(newline.resolve("auction.jcm")).replace("\r\n", "\n"));
        assertThrows(IllegalArgumentException.class, () -> AuctionCaseBundle.read(newline));
    }

    @Test void rejectsUnknownVersionFieldsDuplicateIdsAndEscapingPaths() throws Exception {
        for (String variant : List.of("version", "field", "duplicate", "path", "root")) {
            Path installed = copyCase(variant);
            var manifest = new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(installed.resolve("case-manifest.json")))));
            var assets = new ArrayList<Object>((List<?>) manifest.get("assets"));
            switch (variant) {
                case "version" -> manifest.put("schemaVersion", "99.0.0");
                case "field" -> manifest.put("ignoreChecksums", true);
                case "duplicate" -> { assets.add(assets.getFirst()); manifest.put("assets", assets); }
                case "path" -> {
                    var asset = new LinkedHashMap<>(CanonicalJson.object(assets.getFirst())); asset.put("path", "../outside.jcm");
                    assets.set(0, asset); manifest.put("assets", assets);
                }
                case "root" -> manifest.put("rootAliases", Map.of("root-auction", "C:/outside"));
            }
            Files.write(installed.resolve("case-manifest.json"), CanonicalJson.encode(manifest));
            assertThrows(IllegalArgumentException.class, () -> AuctionCaseBundle.read(installed), variant);
        }
    }

    @Test void verifiesActualFrameworkBinaryAndPinnedArchiveEntry() throws Exception {
        var bundle = AuctionCaseBundle.read(CASE);
        Path jar = Path.of(ora4mas.nopl.SchemeBoard.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        byte[] bytes = Files.readAllBytes(jar);
        bundle.verifyRuntimeArtifact(jar.getFileName().toString(), bytes);
        bundle.verifyArchive("ref-org-obedient", bytes);
        bytes[0] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> bundle.verifyRuntimeArtifact(jar.getFileName().toString(), bytes));
        assertThrows(IllegalArgumentException.class, () -> bundle.verifyArchive("ref-org-obedient", bytes));
        assertThrows(IllegalArgumentException.class, () -> bundle.verifyArchive("src-jcm", new byte[0]));
    }

    @Test void wrongArchiveEntryOrRuntimeFingerprintIsRejectedByBinaryVerification() throws Exception {
        byte[] actualJar = Files.readAllBytes(Path.of(ora4mas.nopl.SchemeBoard.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        for (String variant : List.of("archive-entry", "framework-hash")) {
            Path installed = copyCase(variant);
            var manifest = new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(installed.resolve("case-manifest.json")))));
            if (variant.equals("archive-entry")) {
                var assets = new ArrayList<Object>((List<?>) manifest.get("assets"));
                for (int i = 0; i < assets.size(); i++) {
                    var asset = new LinkedHashMap<>(CanonicalJson.object(assets.get(i)));
                    if (!asset.get("id").equals("ref-org-obedient")) continue;
                    asset.put("archiveEntry", "asl/not-the-reviewed-source.asl"); assets.set(i, asset);
                }
                manifest.put("assets", assets);
            } else {
                var runtime = new LinkedHashMap<>(CanonicalJson.object(manifest.get("runtimeRequirements")));
                runtime.put("sha256:moise-1.1.jar", "0".repeat(64)); manifest.put("runtimeRequirements", runtime);
            }
            Files.write(installed.resolve("case-manifest.json"), CanonicalJson.encode(manifest));
            var bundle = AuctionCaseBundle.read(installed);
            if (variant.equals("archive-entry")) assertThrows(IllegalArgumentException.class, () -> bundle.verifyArchive("ref-org-obedient", actualJar));
            else assertThrows(IllegalArgumentException.class, () -> bundle.verifyRuntimeArtifact("moise-1.1.jar", actualJar));
        }
    }

    private Path copyCase(String name) throws Exception {
        Path target = temporary.resolve(name);
        try (var files = Files.walk(CASE)) {
            for (Path file : files.toList()) {
                Path destination = target.resolve(CASE.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(destination);
                else Files.copy(file, destination);
            }
        }
        return target;
    }
}

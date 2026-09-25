package org.tzi.use.plugins.jacamo.evidence;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Pins generated evidence to a repository commit that contains its source inputs. */
public final class EvidenceSourceCommit {
    public static final List<String> ACTIVE_V2_INPUTS = List.of(
            "use-plugin/src/test/resources/auction/auction.jcm",
            "use-plugin/src/test/resources/auction/src/agt/auctioneer.asl",
            "use-plugin/src/test/resources/auction/src/env/auction/AuctionArtifact.java",
            "use-plugin/src/test/resources/auction/src/org/auction.xml",
            "use-plugin/src/test/resources/auction/verification/auction.ocl",
            "use-plugin/Core/Metamodel/version-2/jacamo_v2_complete.ecore",
            "use-plugin/Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json",
            "use-plugin/Core/Mapping/version-2/jacamo-use-mapping-v2.json",
            "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl",
            "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2-manifest.json",
            "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v2.json",
            "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json",
            "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping-v2.schema.json",
            "use-plugin/release/v2-freeze-manifest.json");

    private EvidenceSourceCommit() { }

    public static String verify(Path repository, List<String> relativePaths) throws Exception {
        Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(repository.toAbsolutePath().normalize().toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.waitFor() != 0 || !output.matches("[0-9a-f]{40}"))
            throw new IllegalStateException("PHASE14_SOURCE_COMMIT_UNAVAILABLE: " + output);
        Path root = repository.toAbsolutePath().normalize();
        for (String relative : relativePaths) {
            Path workingFile = root.resolve(relative).normalize();
            if (!workingFile.startsWith(root))
                throw new IllegalArgumentException("EVIDENCE_SOURCE_PATH_ESCAPE: " + relative);
            Process committed = new ProcessBuilder("git", "show", output + ":" + relative)
                    .directory(root.toFile()).start();
            byte[] committedBytes = committed.getInputStream().readAllBytes();
            String error = new String(committed.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (committed.waitFor() != 0 || !Files.isRegularFile(workingFile))
                throw new IllegalStateException("EVIDENCE_SOURCE_NOT_IN_COMMIT: " + relative + " " + error);
            byte[] workingBytes = Files.readAllBytes(workingFile);
            if (!Arrays.equals(normalize(committedBytes), normalize(workingBytes)))
                throw new IllegalStateException("EVIDENCE_SOURCE_DIFFERS_FROM_COMMIT: " + relative);
        }
        return output;
    }

    private static byte[] normalize(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}

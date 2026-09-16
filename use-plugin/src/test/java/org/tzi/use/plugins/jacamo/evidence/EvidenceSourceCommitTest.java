package org.tzi.use.plugins.jacamo.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceSourceCommitTest {
    @TempDir Path temporary;

    @Test
    void refusesToPinACommitWhenAnEvidenceInputDiffersFromThatCommit() throws Exception {
        run("init");
        Path fixture = temporary.resolve("auction.jcm");
        Files.writeString(fixture, "mas auction {}\n");
        run("add", "auction.jcm");
        run("-c", "user.name=Evidence Test", "-c", "user.email=evidence@example.invalid",
                "commit", "-m", "fixture");
        String committed = EvidenceSourceCommit.verify(temporary, List.of("auction.jcm"));
        assertEquals(run("rev-parse", "HEAD"), committed);

        Files.writeString(fixture, "mas auction {}\n// changed without commit\n");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EvidenceSourceCommit.verify(temporary, List.of("auction.jcm")));
        assertTrue(failure.getMessage().contains("EVIDENCE_SOURCE_DIFFERS_FROM_COMMIT"));
    }

    private String run(String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).directory(temporary.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.waitFor() != 0) throw new AssertionError(output);
        return output;
    }
}

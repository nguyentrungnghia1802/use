package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Creates a real directory redirect without turning path-security tests into skipped gates on Windows. */
public final class PathLinkSupport {
    private PathLinkSupport() { }

    public static Path createDirectoryLink(Path link, Path target) throws Exception {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException symlinkFailure) {
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
                throw symlinkFailure;
            }
            Process process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                    link.toString(), target.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0 || !Files.isDirectory(link)) {
                fail("cannot create Windows junction for path-security gate (exit=" + exit + "): " + output
                        + "; original symbolic-link failure: " + symlinkFailure.getMessage());
            }
            return link;
        }
    }
}

package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.parser.shell.ShellCommandCompiler;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.plugins.jacamo.trace.TraceStore;

/** Immutable historical artifact replay; no retained V1 production parser. */
class PreMigrationBaselineTest {
    @Test void auctionHistoricalStaticBaseline() throws Exception { replay("auction"); }
    @Test void counterHistoricalStaticBaseline() throws Exception { replay("counter-team"); }
    private void replay(String fixture) throws Exception {
        Path input = Path.of("docs/project/v2-migration/historical-static", fixture);
        var manifest = new ObjectMapper().readTree(Files.readString(input.resolve("manifest.json")));
        for (String name : List.of("model.use", "initial-state.cmd", "trace.json"))
            assertEquals(manifest.path("sha256").path(name).asText(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input.resolve(name)))), name);
        var errors = new StringWriter();
        var model = USECompiler.compileSpecification(new ByteArrayInputStream(Files.readAllBytes(input.resolve("model.use"))),
                "historical.use", input.resolve("model.use").toUri(), new PrintWriter(errors), new ModelFactory());
        assertNotNull(model, errors.toString());
        var system = new MSystem(model);
        for (String line : Files.readAllLines(input.resolve("initial-state.cmd"))) {
            if (line.isBlank()) continue;
            var statement = ShellCommandCompiler.compileShellCommand(model, system.state(), system.getVariableEnvironment(),
                    line.substring(1), "historical.cmd", new PrintWriter(errors), false);
            assertNotNull(statement, errors.toString()); system.execute(statement);
        }
        assertTrue(system.state().checkStructure(new PrintWriter(errors)), errors.toString());
        assertTrue(system.state().check(new PrintWriter(errors), false, true, true, List.of()), errors.toString());
        assertFalse(new TraceStore().read(input.resolve("trace.json")).records().isEmpty());
    }
}

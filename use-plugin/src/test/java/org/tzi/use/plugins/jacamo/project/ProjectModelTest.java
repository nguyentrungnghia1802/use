package org.tzi.use.plugins.jacamo.project;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;

class ProjectModelTest {
    @TempDir Path temporary;

    @Test
    void projectRootNormalizesPathAndRejectsBlankIdentity() {
        ProjectRoot root = new ProjectRoot(temporary.resolve("sub/.."), "demo");
        assertEquals(temporary.toAbsolutePath().normalize(), root.path());
        assertEquals("demo", root.projectId());
        assertThrows(IllegalArgumentException.class, () -> new ProjectRoot(temporary, " "));
    }

    @Test
    void sourceFileHashesExactBytesAndMissingFileFails() throws Exception {
        Path file = temporary.resolve("demo.jcm");
        Files.writeString(file, "abc");
        SourceFile source = SourceFile.read(file, SourceKind.JCM);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", source.sha256());
        assertEquals(3, source.byteLength());
        assertEquals(SourceKind.JCM, source.kind());
        assertThrows(java.io.IOException.class, () -> SourceFile.read(temporary.resolve("missing.jcm"), SourceKind.JCM));
    }

    @Test
    void sourceSpanAndDiagnosticKeepActionableLocation() {
        SourceSpan span = new SourceSpan(temporary.resolve("demo.jcm"), 2, 3, 2, 9);
        Diagnostic diagnostic = new Diagnostic("JCM001", Severity.ERROR, Phase.PROJECT_DISCOVERY,
                span, null, null, "Source missing", "agent bob: bob.asl", "Add bob.asl under src/agt");
        assertEquals(2, diagnostic.sourceLocation().startLine());
        assertEquals("JCM001", diagnostic.code());
        assertEquals("Add bob.asl under src/agt", diagnostic.remediation());
        assertThrows(IllegalArgumentException.class,
                () -> new SourceSpan(temporary.resolve("demo.jcm"), 2, 9, 2, 3));
    }
}

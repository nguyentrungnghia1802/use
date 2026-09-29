package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MMPrintVisitor;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/** Serializes a finalized native model with USE's official printer and verifies a structural recompile. */
public final class NativeUseExporter {
    public Result export(MModel model) {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer, true);
        model.processWithVisitor(new MMPrintVisitor(writer));
        writer.flush();
        String text = buffer.toString().replace("\r\n", "\n");
        StringWriter diagnostics = new StringWriter();
        MModel recompiled = USECompiler.compileSpecification(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                "code-grounded-export.use", URI.create("memory:/code-grounded-export.use"),
                new PrintWriter(diagnostics, true), new ModelFactory());
        if (recompiled == null)
            throw new IllegalStateException("NATIVE_USE_EXPORT_RECOMPILE_FAILED: " + diagnostics + "\n" + numbered(text));
        List<String> original = NativeUseStructure.signature(model);
        List<String> roundTrip = NativeUseStructure.signature(recompiled);
        if (!original.equals(roundTrip))
            throw new IllegalStateException("NATIVE_USE_EXPORT_STRUCTURE_MISMATCH: original=" + original
                    + " roundTrip=" + roundTrip + " onlyOriginal=" + difference(original, roundTrip)
                    + " onlyRoundTrip=" + difference(roundTrip, original));
        return new Result(text, recompiled, NativeUseStructure.sha256(model),
                NativeUseStructure.sha256(recompiled), diagnostics.toString());
    }

    public Result export(MModel model, Path destination) {
        Result result = export(model);
        Path output = destination.toAbsolutePath().normalize();
        if (!output.getFileName().toString().toLowerCase().endsWith(".use"))
            throw new IllegalArgumentException("NATIVE_USE_EXPORT_EXTENSION_REQUIRED");
        try {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Files.writeString(output, result.useText(), StandardCharsets.UTF_8);
            return result;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("NATIVE_USE_EXPORT_IO_FAILED: " + output, error);
        }
    }

    public record Result(String useText, MModel recompiledModel, String originalStructuralHash,
                         String recompiledStructuralHash, String compilerDiagnostics) { }

    private static String numbered(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++)
            result.append(index + 1).append(": ").append(lines[index]).append('\n');
        return result.toString();
    }

    private static List<String> difference(List<String> left, List<String> right) {
        return left.stream().filter(entry -> !right.contains(entry)).toList();
    }
}

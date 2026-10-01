package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.tzi.use.parser.shell.ShellCommandCompiler;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemException;
import org.tzi.use.uml.sys.soil.MAttributeAssignmentStatement;
import org.tzi.use.uml.sys.soil.MLinkInsertionStatement;
import org.tzi.use.uml.sys.soil.MNewObjectStatement;
import org.tzi.use.uml.sys.soil.MStatement;
import org.tzi.use.util.StringUtil;

/**
 * Exports and replays the state of the active native USE system as SOIL commands.
 *
 * <p>The exporter deliberately consumes only the supplied {@link MSystem}.  It
 * does not inspect semantic snapshots, mapping V2, text backends, trace rows,
 * or evidence stores.  Commands are rendered by USE's own SOIL statements so
 * that the output and replay use the same concrete syntax implementation.</p>
 */
public final class NativeUseSoilExporter {
    public Result export(MSystem system) {
        Objects.requireNonNull(system, "system");
        List<String> commands = new ArrayList<>();
        List<MObject> objects = system.state().allObjects().stream()
                .sorted(Comparator.comparing(MObject::name))
                .toList();
        for (MObject object : objects) {
            requireObjectName(object);
            commands.add(new MNewObjectStatement(object.cls(), object.name()).getShellCommand());
        }

        int attributeAssignments = 0;
        for (MObject object : objects) {
            List<MAttribute> attributes = object.cls().allAttributes().stream()
                    .sorted(Comparator.comparing(MAttribute::name)).toList();
            for (MAttribute attribute : attributes) {
                var value = object.state(system.state()).attributeValue(attribute);
                if (value.isUndefined()) continue;
                commands.add(attributeCommand(object, attribute, value));
                attributeAssignments++;
            }
        }

        List<MLink> links = new ArrayList<>(system.state().allLinks());
        links.sort(Comparator.comparing((MLink link) -> link.association().name())
                .thenComparing(NativeUseSoilExporter::orderedParticipantNames));
        for (MLink link : links) {
            if (link.isVirtual())
                throw new IllegalStateException("NATIVE_SOIL_VIRTUAL_LINK_UNSUPPORTED: "
                        + link.association().name());
            for (MObject object : link.linkedObjects()) requireObjectName(object);
            commands.add(new MLinkInsertionStatement(link.association(), link.linkedObjectsAsArray(),
                    link.getQualifier()).getShellCommand());
        }
        String text = commands.isEmpty() ? "" : String.join("\n", commands) + "\n";
        return new Result(text, objects.size(), attributeAssignments, links.size());
    }

    public Result export(MSystem system, Path destination) {
        Result result = export(system);
        Path output = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        if (output.getFileName() == null || !output.getFileName().toString().toLowerCase().endsWith(".cmd"))
            throw new IllegalArgumentException("NATIVE_SOIL_EXPORT_EXTENSION_REQUIRED");
        try {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Files.writeString(output, result.commands(), StandardCharsets.UTF_8);
            return result;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("NATIVE_SOIL_EXPORT_IO_FAILED: " + output, error);
        }
    }

    /**
     * Replays an exported command stream against a freshly constructed native
     * system.  The returned system is the replay state; no caller-owned system
     * is mutated.
     */
    public MSystem replay(org.tzi.use.uml.mm.MModel model, String commands) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(commands, "commands");
        MSystem replay = new MSystem(model);
        StringWriter diagnostics = new StringWriter();
        PrintWriter errors = new PrintWriter(diagnostics, true);
        int lineNumber = 0;
        for (String rawLine : commands.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) continue;
            if (!line.startsWith("!"))
                throw new IllegalArgumentException("NATIVE_SOIL_COMMAND_PREFIX_REQUIRED:" + lineNumber);
            String soil = line.substring(1).trim();
            MStatement statement = ShellCommandCompiler.compileShellCommand(model, replay.state(),
                    replay.getVariableEnvironment(), soil, "native-export.cmd:" + lineNumber, errors, false);
            if (statement == null)
                throw new IllegalStateException("NATIVE_SOIL_REPLAY_COMPILE_FAILED:" + lineNumber + ": " + diagnostics);
            try {
                replay.execute(statement, true, false, false);
            } catch (MSystemException error) {
                throw new IllegalStateException("NATIVE_SOIL_REPLAY_EXECUTION_FAILED:" + lineNumber + ": "
                        + error.getMessage(), error);
            }
        }
        return replay;
    }

    private static String orderedParticipantNames(MLink link) {
        return link.linkedObjects().stream().map(MObject::name).toList().toString();
    }

    private static String attributeCommand(MObject object, MAttribute attribute, Value value) {
        // USE's legacy StringValue printer does not escape Windows backslashes.
        // Keep the official SOIL printer for every other value, but render this
        // literal with the parser's own escaping rules so paths and apostrophes
        // round-trip on Windows as well.
        if (value instanceof StringValue string)
            return "!" + object.name() + "." + attribute.name() + " := '"
                    + StringUtil.escapeString(string.value(), '\'') + "'";
        return new MAttributeAssignmentStatement(object, attribute, value).getShellCommand();
    }

    private static void requireObjectName(MObject object) {
        if (object.name() == null || !object.name().matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalStateException("NATIVE_SOIL_OBJECT_NAME_UNSUPPORTED: " + object.name());
    }

    public record Result(String commands, int objectCount, int attributeAssignmentCount, int linkCount) { }
}

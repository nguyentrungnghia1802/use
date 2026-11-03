package org.tzi.use.plugins.jacamo.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Exact selection of manually authored system specifications defined in a .mrv file. */
record CaseStudyFiles(Path mrvFile, Path csl, Path istar, Path mapping, Path ocl) {
    public Path folder() { return mrvFile; }

    static CaseStudyFiles read(Path path) {
        Path file = java.util.Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("EXPECTED_MRV_FILE: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("MRV_FILE_NOT_FOUND: " + file);
        }
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".mrv")) {
            throw new IllegalArgumentException("NOT_A_MRV_FILE: " + file);
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalArgumentException("MRV_FILE_UNREADABLE: " + file, error);
        }

        Map<String, Path> resolved = new LinkedHashMap<>();
        Path baseDir = file.getParent() != null ? file.getParent() : Path.of(".");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }

            String key;
            String val;
            int colon = line.indexOf(':');
            int eq = line.indexOf('=');
            int sep = -1;
            if (colon >= 0 && eq >= 0) sep = Math.min(colon, eq);
            else if (colon >= 0) sep = colon;
            else if (eq >= 0) sep = eq;

            if (sep >= 0) {
                key = line.substring(0, sep).strip().toLowerCase(Locale.ROOT);
                val = line.substring(sep + 1).strip();
            } else {
                val = line;
                int dot = val.lastIndexOf('.');
                key = dot >= 0 ? val.substring(dot + 1).strip().toLowerCase(Locale.ROOT) : "";
            }

            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1).strip();
            } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1).strip();
            }

            String targetKey = switch (key) {
                case "csl" -> "csl";
                case "istar" -> "istar";
                case "xml", "mapping" -> "xml";
                case "ocl" -> "ocl";
                default -> throw new IllegalArgumentException("MRV_UNKNOWN_ENTRY: " + key + " (line " + (i + 1) + " in " + file.getFileName() + ")");
            };

            if (resolved.containsKey(targetKey)) {
                throw new IllegalArgumentException("CASE_FOLDER_DUPLICATE_TYPE: ." + targetKey + " (line " + (i + 1) + " in " + file.getFileName() + ")");
            }

            Path target = Path.of(val);
            if (!target.isAbsolute()) {
                target = baseDir.resolve(target);
            }
            target = target.normalize();

            if (Files.isSymbolicLink(target)) {
                throw new IllegalArgumentException("CASE_FILE_SYMLINK_UNSUPPORTED: " + target.getFileName());
            }
            if (!Files.isRegularFile(target)) {
                throw new IllegalArgumentException("MRV_TARGET_NOT_FOUND: " + target + " (line " + (i + 1) + " in " + file.getFileName() + ")");
            }

            resolved.put(targetKey, target);
        }

        for (String req : List.of("csl", "istar", "xml")) {
            if (!resolved.containsKey(req)) {
                throw new IllegalArgumentException("CASE_FOLDER_MISSING_REQUIRED: ." + req);
            }
        }

        return new CaseStudyFiles(file, resolved.get("csl"), resolved.get("istar"), resolved.get("xml"), resolved.get("ocl"));
    }
}

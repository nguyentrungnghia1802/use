package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ContractPayloads;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.sys.MObject;

/** Standalone offline replay; never supplies facts back to a live Bridge or Session. */
public final class NativeRuntimeReplay {
    public record ReplayReport(boolean complete, int checkedEntries, String finalStateHash, String finalResultHash,
                               List<String> diagnostics) { public ReplayReport { diagnostics = List.copyOf(diagnostics); } }
    private static final List<String> FILES = List.of("model.use", "baseline.cmd", "constraints.ocl", "runtime.jsonl");
    public void exportBundle(NativeRuntimeProjector projector, String coreUseText, Path directory) {
        projector.coordinator().read(() -> {
            var coordinator = projector.coordinator();
            if (coordinator.journal().hasGap()) throw new IllegalStateException("REPLAY_EXPORT_INCOMPLETE_JOURNAL:" + coordinator.journal().diagnostic());
            Path output = directory.toAbsolutePath().normalize();
            if (Files.exists(output)) try (var contents = Files.list(output)) {
                if (contents.findAny().isPresent()) throw new IllegalArgumentException("REPLAY_EXPORT_DIRECTORY_NOT_EMPTY");
            }
            Files.createDirectories(output);
            Files.writeString(output.resolve("model.use"), coreUseText, StandardCharsets.UTF_8);
            Files.copy(coordinator.checkpoints().baseline().commands(), output.resolve("baseline.cmd"));
            var profile = coordinator.constraints().profile();
            Files.writeString(output.resolve("constraints.ocl"), profile == null ? "" : profile.source(), StandardCharsets.UTF_8);
            Files.copy(coordinator.journal().path(), output.resolve("runtime.jsonl"));
            Map<String, Object> hashes = new LinkedHashMap<>();
            for (String file : FILES) hashes.put(file, ExternalOclConstraintService.sha256(Files.readAllBytes(output.resolve(file))));
            Map<String, Object> manifest = Map.of("schemaVersion", "1.0.0", "files", hashes,
                    "entries", coordinator.journal().persistedEntries(), "finalStateHash", coordinator.lastObservation().stateHash(),
                    "finalResultHash", coordinator.lastObservation().resultHash(), "baselineStateHash", coordinator.checkpoints().baseline().stateHash(),
                    "scope", "OBSERVED_SUPPORTED_PROJECTION_ONLY");
            Files.write(output.resolve("manifest.json"), CanonicalJson.encode(manifest));
            return null;
        });
    }
    public ReplayReport replay(Path directory) {
        int checked = 0; String stateHash = "", resultHash = "";
        NativeRuntimeProjector projector = null;
        try {
            Path root = directory.toAbsolutePath().normalize();
            Map<String, Object> manifest = CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(root.resolve("manifest.json"))));
            if (!"1.0.0".equals(manifest.get("schemaVersion"))) throw new IllegalArgumentException("REPLAY_SCHEMA_UNSUPPORTED");
            Map<String, Object> hashes = CanonicalJson.object(manifest.get("files"));
            for (String file : FILES) {
                Path path = root.resolve(file);
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 64L * 1024 * 1024)
                    throw new IllegalArgumentException("REPLAY_FILE_MISSING_OR_UNSAFE:" + file);
                if (!ExternalOclConstraintService.sha256(Files.readAllBytes(path)).equals(hashes.get(file)))
                    throw new IllegalArgumentException("REPLAY_FILE_HASH_MISMATCH:" + file);
            }
            StringWriter diagnostics = new StringWriter();
            var model = USECompiler.compileSpecification(new ByteArrayInputStream(Files.readAllBytes(root.resolve("model.use"))),
                    "model.use", root.resolve("model.use").toUri(), new PrintWriter(diagnostics, true), new ModelFactory());
            if (model == null || !diagnostics.toString().isBlank()) throw new IllegalArgumentException("REPLAY_MODEL_COMPILE_REJECTED:" + diagnostics);
            var system = new NativeUseSoilExporter().replay(model, Files.readString(root.resolve("baseline.cmd")));
            Map<String, MObject> index = new LinkedHashMap<>();
            for (MObject object : system.state().allObjects()) {
                var attribute = object.cls().attribute("semanticId", true);
                if (attribute != null && object.state(system.state()).attributeValue(attribute) instanceof StringValue identity
                        && index.putIfAbsent(identity.value(), object) != null)
                    throw new IllegalArgumentException("REPLAY_DUPLICATE_SEMANTIC_ID");
            }
            String chain = "", lastProfile = "";
            boolean incompleteCoverage = false;
            try (var lines = Files.lines(root.resolve("runtime.jsonl"), StandardCharsets.UTF_8)) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> entry = new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(iterator.next().getBytes(StandardCharsets.UTF_8))));
                    Object recordedHash = entry.remove("entryHash");
                    if (((Number) entry.get("ordinal")).longValue() != checked || !chain.equals(entry.get("previousHash"))
                            || !ExternalOclConstraintService.sha256(CanonicalJson.encode(entry)).equals(recordedHash))
                        throw new IllegalArgumentException("REPLAY_CHAIN_CORRUPT:" + checked);
                    chain = (String) recordedHash;
                    String kind = (String) entry.get("kind");
                    if (kind.equals("GAP")) throw new IllegalArgumentException("REPLAY_GAP:" + entry.get("diagnostic"));
                    Map<String, Object> expected = CanonicalJson.object(entry.get("result"));
                    if ("STALE".equals(expected.get("freshness"))) incompleteCoverage = true;
                    Map<String, Object> payload = CanonicalJson.object(entry.get("payload"));
                    if (projector == null) {
                        if (!kind.equals("BASELINE") || ((Number) expected.get("stateVersion")).longValue() != 0)
                            throw new IllegalArgumentException("REPLAY_BASELINE_REQUIRED");
                        projector = new NativeRuntimeProjector(system, index, (String) expected.get("sessionId"),
                                ((Number) expected.get("generation")).longValue(), (String) expected.get("modelRevision"), new CodeGroundedRuntimeRuleRegistry());
                        if (!projector.coordinator().latest().stateHash().equals(manifest.get("baselineStateHash")))
                            throw new IllegalArgumentException("REPLAY_BASELINE_HASH_MISMATCH");
                    } else switch (kind) {
                        case "SNAPSHOT" -> projector.applySnapshot(ContractPayloads.runtime(payload), (String) expected.get("sessionId"),
                                ((Number) expected.get("generation")).longValue());
                        case "EVENT" -> projector.apply(ContractPayloads.event(payload));
                        case "PROFILE" -> {
                            lastProfile = (String) payload.get("source");
                            Map<String, Boolean> enabled = new LinkedHashMap<>();
                            if (payload.containsKey("enabled")) CanonicalJson.object(payload.get("enabled"))
                                    .forEach((id, value) -> enabled.put(id, (Boolean) value));
                            projector.coordinator().loadProfileSource((String) payload.get("sourceFile"), lastProfile, enabled);
                        }
                        case "MANUAL" -> projector.coordinator().manualVerify();
                        case "COVERAGE" -> projector.coordinator().coverageGap(payload.containsKey("event")
                                ? ContractPayloads.event(CanonicalJson.object(payload.get("event"))) : null, (String) payload.get("diagnostic"));
                        case "REJECTED" -> {
                            boolean rejected = false;
                            try { projector.apply(ContractPayloads.event(payload)); } catch (RuntimeException error) { rejected = true; }
                            if (!rejected) throw new IllegalArgumentException("REPLAY_EXPECTED_REJECTION");
                        }
                        default -> throw new IllegalArgumentException("REPLAY_ENTRY_UNSUPPORTED:" + kind);
                    }
                    var actual = projector.coordinator().lastObservation();
                    stateHash = actual.stateHash(); resultHash = actual.resultHash();
                    if (!stateHash.equals(expected.get("stateHash")) || !resultHash.equals(expected.get("resultHash"))
                            || actual.stateVersion() != ((Number) expected.get("stateVersion")).longValue())
                        throw new IllegalArgumentException("REPLAY_STATE_OR_RESULT_MISMATCH:" + checked);
                    checked++;
                }
            }
            if (checked != ((Number) manifest.get("entries")).intValue() || !stateHash.equals(manifest.get("finalStateHash"))
                    || !resultHash.equals(manifest.get("finalResultHash")) || !lastProfile.equals(Files.readString(root.resolve("constraints.ocl"))))
                throw new IllegalArgumentException("REPLAY_TRUNCATED_OR_PROFILE_MISMATCH");
            return new ReplayReport(!incompleteCoverage, checked, stateHash, resultHash,
                    List.of(incompleteCoverage ? "REPLAY_PARTIAL_COVERAGE_GAP" : "OBSERVED_SUPPORTED_PROJECTION_ONLY"));
        } catch (Exception error) {
            return new ReplayReport(false, checked, stateHash, resultHash, List.of(error.getClass().getSimpleName() + ":" + error.getMessage()));
        } finally {
            if (projector != null) projector.close();
        }
    }
}

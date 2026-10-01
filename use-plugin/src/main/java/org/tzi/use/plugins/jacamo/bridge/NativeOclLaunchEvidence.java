package org.tzi.use.plugins.jacamo.bridge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;

/** Read-only evidence for any external profile; semantic work stays in the production facade. */
final class NativeOclLaunchEvidence {
    record ProfileEvidence(Path path, String sha256, RuntimeVerificationResult baseline) { }

    private NativeOclLaunchEvidence() { }

    static ProfileEvidence load(JaCaMoFacade facade, Session session, Path file, Path output) throws Exception {
        Path path = file.toAbsolutePath().normalize();
        String hash = ExternalOclConstraintService.sha256(Files.readAllBytes(path));
        var system = session.system();
        facade.loadVerificationProfile(path);
        if (session.system() != system) throw new IllegalStateException("EXTERNAL_OCL_SESSION_REPLACED");
        var baseline = facade.runtimeVerificationHistory().stream()
                .filter(result -> result.sourceId().equals("ocl") && result.eventId().equals("profile:" + hash))
                .findFirst().orElseThrow(() -> new IllegalStateException("EXTERNAL_OCL_BASELINE_NOT_RECORDED"));
        var registered = facade.constraints().stream().filter(constraint -> constraint.id().startsWith("EXTERNAL:"))
                .map(constraint -> {
                    if (!path.equals(constraint.sourcePath())) throw new IllegalStateException("EXTERNAL_OCL_WRONG_SOURCE");
                    return Map.of("constraintId", constraint.id(), "context", constraint.context(),
                            "sourceFile", constraint.sourcePath().toString(), "enabled", constraint.enabled());
                }).toList();
        if (registered.isEmpty()) throw new IllegalStateException("EXTERNAL_OCL_PROFILE_NOT_ATTACHED");
        Files.write(output.resolve("ocl-baseline.json"), CanonicalJson.encode(baseline.toMap()));
        Files.write(output.resolve("ocl-registry.json"), CanonicalJson.encode(Map.of("sourceFile", path.toString(),
                "sourceSha256", hash, "modelRevision", baseline.modelRevision(), "constraints", registered)));
        // Inspection uses the same EDT writer boundary, not an unsynchronized MSystemState read.
        FutureTask<Map<String, Object>> inspection = new FutureTask<>(() -> {
            Map<String, Object> counts = new LinkedHashMap<>();
            List<Map<String, Object>> schema = new ArrayList<>();
            for (var cls : system.model().classes().stream().sorted(java.util.Comparator.comparing(
                    org.tzi.use.uml.mm.MClass::name)).toList()) {
                counts.put(cls.name(), system.state().objectsOfClass(cls).size());
                schema.add(Map.of("class", cls.name(), "attributes", cls.allAttributes().stream()
                        .map(attribute -> Map.of("name", attribute.name(), "type", attribute.type().toString())).toList()));
            }
            return Map.of("classObjectCounts", counts, "schema", schema,
                    "associations", system.model().associations().stream().map(association -> association.name()).sorted().toList(),
                    "stateVersion", facade.runtimeVerificationResult().stateVersion());
        });
        if (SwingUtilities.isEventDispatchThread()) inspection.run();
        else SwingUtilities.invokeAndWait(inspection);
        Files.write(output.resolve("native-model-inspection.json"), CanonicalJson.encode(inspection.get()));
        return new ProfileEvidence(path, hash, baseline);
    }

    static Map<String, Object> finish(ProfileEvidence profile, RuntimeVerificationResult beforeResync,
            RuntimeVerificationResult afterResync, Path replay, Path output) throws Exception {
        if (!profile.sha256().equals(ExternalOclConstraintService.sha256(Files.readAllBytes(profile.path()))))
            throw new IllegalStateException("EXTERNAL_OCL_FILE_CHANGED_DURING_RUN");
        if (!profile.sha256().equals(ExternalOclConstraintService.sha256(Files.readAllBytes(replay.resolve("constraints.ocl")))))
            throw new IllegalStateException("EXTERNAL_OCL_REPLAY_PROFILE_MISMATCH");
        var result = new LinkedHashMap<String, Object>(summarizeJournal(replay.resolve("runtime.jsonl"), profile.baseline()));
        result.put("sourceFile", profile.path().toString());
        result.put("sourceSha256", profile.sha256());
        result.put("baseline", profile.baseline().toMap());
        result.put("beforeResync", beforeResync.toMap());
        result.put("afterResync", afterResync.toMap());
        result.put("replayDirectory", replay.toString());
        result.put("compiler", "USE ASSLCompiler.compileInvariants against current native MModel");
        Files.write(output.resolve("ocl-runtime-evidence.json"), CanonicalJson.encode(result));
        return Map.copyOf(result);
    }

    static Map<String, Object> summarizeJournal(Path journal, RuntimeVerificationResult baseline) throws Exception {
        Map<String, LinkedHashSet<String>> observed = new LinkedHashMap<>();
        Map<String, String> baselineOutcomes = new LinkedHashMap<>();
        for (var outcome : baseline.outcomes()) if (outcome.constraintId().startsWith("EXTERNAL:")) {
            baselineOutcomes.put(outcome.constraintId(), outcome.outcome().name());
            observed.put(outcome.constraintId(), new LinkedHashSet<>(List.of(outcome.outcome().name())));
        }
        long entries = 0, runtimeChecks = 0, changedStatesWithFail = 0, stale = 0;
        long previousVersion = -1;
        String previousHash = "";
        List<Map<String, Object>> checkedEvents = new ArrayList<>();
        try (var lines = Files.lines(journal, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                var entry = CanonicalJson.object(CanonicalJson.decode(iterator.next().getBytes(StandardCharsets.UTF_8)));
                if ("GAP".equals(entry.get("kind"))) throw new IllegalStateException("EXTERNAL_OCL_JOURNAL_GAP");
                var result = CanonicalJson.object(entry.get("result"));
                long version = ((Number) result.get("stateVersion")).longValue();
                String hash = (String) result.get("stateHash");
                entries++;
                if ("STALE".equals(result.get("freshness"))) stale++;
                boolean mutation = "EVENT".equals(entry.get("kind")) && version > previousVersion
                        && version > baseline.stateVersion();
                boolean evaluated = false, failed = false;
                for (Object item : (List<?>) result.get("outcomes")) {
                    var outcome = CanonicalJson.object(item);
                    String id = (String) outcome.get("constraintId");
                    if (!id.startsWith("EXTERNAL:") || version < baseline.stateVersion()) continue;
                    if (!observed.containsKey(id)) throw new IllegalStateException("EXTERNAL_OCL_UNEXPECTED_CONSTRAINT:" + id);
                    String status = (String) outcome.get("outcome");
                    observed.get(id).add(status);
                    evaluated |= !status.equals("SKIPPED");
                    failed |= status.equals("FAIL");
                }
                if (mutation && evaluated) {
                    runtimeChecks++;
                    if (failed && !hash.equals(previousHash)) changedStatesWithFail++;
                    checkedEvents.add(Map.of("eventId", result.get("eventId"), "sourceId", result.get("sourceId"),
                            "sourceSequence", result.get("sourceSequence"), "stateVersion", version, "stateHash", hash,
                            "freshness", result.get("freshness"), "hasExternalFail", failed));
                }
                previousVersion = version; previousHash = hash;
            }
        }
        Map<String, Object> constraints = new LinkedHashMap<>();
        observed.forEach((id, outcomes) -> constraints.put(id, Map.of("baseline", baselineOutcomes.get(id),
                "observedOutcomes", List.copyOf(outcomes), "outcomeChanged", outcomes.size() > 1)));
        return Map.of("persistedEntries", entries, "postLoadRuntimeRechecks", runtimeChecks,
                "changedRuntimeStatesRetainedWithExternalFail", changedStatesWithFail, "staleEntries", stale,
                "perConstraint", constraints, "checkedRuntimeEvents", checkedEvents,
                "scope", "OBSERVED_SUPPORTED_PROJECTION_ONLY");
    }
}

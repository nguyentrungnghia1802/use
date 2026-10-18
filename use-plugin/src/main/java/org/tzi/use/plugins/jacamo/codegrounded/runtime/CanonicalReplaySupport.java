package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;

/** Canonical export only. Disk IO is outside the native writer; the captured
 * journal prefix is append-only and self-validating. No second MSystem. */
final class CanonicalReplaySupport {
    static final String VERSION="3.0.0";
    static final String PROGRAM="program.json";
    private record Cut(SemanticProgram program,Path journal,Path baseline,Map<String,Object> bindings,
            String profile,long entries,long bytes,String baselineHash,RuntimeVerificationResult result) { }
    static void export(NativeRuntimeProjector projector,String model,Path directory) {
        if(javax.swing.SwingUtilities.isEventDispatchThread())throw new IllegalStateException("SEMANTIC_REPLAY_EXPORT_REQUIRES_WORKER");
        if(model==null||model.isBlank())throw new IllegalArgumentException("SEMANTIC_REPLAY_BASELINE_MODEL_REQUIRED");
        var coordinator=projector.coordinator();coordinator.awaitEvaluation(coordinator.lastObservation(),30000);
        var cut=coordinator.read(()->{
            if(coordinator.journal().hasGap())throw new IllegalStateException("REPLAY_EXPORT_INCOMPLETE_JOURNAL:"+coordinator.journal().diagnostic());
            var last=coordinator.lastObservation();
            if(last==null||last.semantic()==null||!(last.semantic().observation().get("originalEngine") instanceof Map<?,?> core)
                    ||!Set.of("PINNED_NATIVE_CUT","ERROR").contains(core.get("evaluated")))
                throw new IllegalStateException("SEMANTIC_REPLAY_EXPORT_REQUIRES_STABLE_COMPLETED_CUT");
            if(coordinator.journal().persistedBytes()>64L*1024*1024)throw new IllegalStateException("SEMANTIC_REPLAY_ARCHIVE_CAPACITY_EXCEEDED");
            var profile=coordinator.constraints().profile();
            return new Cut(projector.mutations().canonicalProgram(),coordinator.journal().path(),coordinator.checkpoints().baseline().commands(),
                    projector.mutations().baselineBindings(),profile==null?"":profile.source(),coordinator.journal().persistedEntries(),coordinator.journal().persistedBytes(),
                    coordinator.checkpoints().baseline().stateHash(),last);
        });
        Path output=directory.toAbsolutePath().normalize();
        try {
            if(Files.isSymbolicLink(output))throw new IllegalArgumentException("REPLAY_EXPORT_DIRECTORY_UNSAFE");
            if(Files.exists(output))try(var files=Files.list(output)){if(files.findAny().isPresent())throw new IllegalArgumentException("REPLAY_EXPORT_DIRECTORY_NOT_EMPTY");}
            Files.createDirectories(output);
            Files.writeString(output.resolve("model.use"),model,StandardCharsets.UTF_8);
            Files.copy(cut.baseline(),output.resolve("baseline.cmd"));
            Files.write(output.resolve("bindings.json"),CanonicalJson.encode(cut.bindings()));
            Files.writeString(output.resolve("constraints.ocl"),cut.profile(),StandardCharsets.UTF_8);
            Files.write(output.resolve(PROGRAM),new SemanticProgramManifest().encode(cut.program()));
            try(var input=Files.newInputStream(cut.journal())) {
                byte[] prefix=input.readNBytes(Math.toIntExact(cut.bytes()));
                if(prefix.length!=cut.bytes())throw new IllegalArgumentException("SEMANTIC_REPLAY_JOURNAL_PREFIX_TRUNCATED");
                Files.write(output.resolve("runtime.jsonl"),prefix);
            }
            var hashes=new TreeMap<String,Object>();var names=new ArrayList<>(NativeRuntimeReplay.FILES);names.add(PROGRAM);
            for(String name:names)hashes.put(name,ExternalOclConstraintService.sha256(Files.readAllBytes(output.resolve(name))));
            Files.write(output.resolve("manifest.json"),CanonicalJson.encode(Map.of("schemaVersion",VERSION,"files",hashes,"entries",cut.entries(),
                    "finalStateHash",cut.result().stateHash(),"finalResultHash",cut.result().resultHash(),"baselineStateHash",cut.baselineHash(),
                    "scope","OBSERVED_SUPPORTED_PROJECTION_ONLY","temporalOrder","ACCEPTED_NATIVE_BOUNDARIES_AND_EXACT_ENGINE_COMPLETIONS","programHash",cut.program().hash())));
        }catch(java.io.IOException error){throw new IllegalStateException("SEMANTIC_REPLAY_EXPORT_IO_FAILED",error);}
    }
}

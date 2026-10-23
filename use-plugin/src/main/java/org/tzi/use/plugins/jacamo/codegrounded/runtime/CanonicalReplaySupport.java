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
    static final String CASE_VERSION="3.1.0";
    static final String BUDGETED_CASE_VERSION="3.2.0";
    static final long CASE_ARCHIVE_BYTES=256L*1024*1024;
    static final String PROGRAM="program.json";
    static boolean caseVersion(Object version){return CASE_VERSION.equals(version)||BUDGETED_CASE_VERSION.equals(version);}
    static boolean canonical(Object version){return VERSION.equals(version)||caseVersion(version);}
    private static long fileLimit(Object version,String file){return CASE_VERSION.equals(version)&&"runtime.jsonl".equals(file)?CASE_ARCHIVE_BYTES:64L*1024*1024;}
    static long fileLimit(Map<String,Object> manifest,String file) {
        Object version=manifest.get("schemaVersion"),budget=manifest.get("runtimeByteLimit");
        if(!BUDGETED_CASE_VERSION.equals(version)) {
            if(manifest.containsKey("runtimeByteLimit"))throw new IllegalArgumentException("REPLAY_BUDGET_SCHEMA_MISMATCH");
            return fileLimit(version,file);
        }
        if(!(budget instanceof Number number))throw new IllegalArgumentException("REPLAY_RUNTIME_BUDGET_REQUIRED");
        long bytes;
        try{bytes=new java.math.BigDecimal(number.toString()).longValueExact();}
        catch(ArithmeticException|NumberFormatException error){throw new IllegalArgumentException("REPLAY_RUNTIME_BUDGET_INVALID",error);}
        if(bytes<=CASE_ARCHIVE_BYTES||bytes>1024L*1024*1024)throw new IllegalArgumentException("REPLAY_RUNTIME_BUDGET_INVALID");
        return "runtime.jsonl".equals(file)?bytes:64L*1024*1024;
    }
    private record Cut(SemanticProgram program,Path journal,Path baseline,Map<String,Object> bindings,
            String profile,long entries,long bytes,long budget,String version,String baselineHash,RuntimeVerificationResult result) { }
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
            long budget=coordinator.journal().maxBytes();
            String version=projector.mutations().auctionCase()?(budget>CASE_ARCHIVE_BYTES?BUDGETED_CASE_VERSION:CASE_VERSION):VERSION;
            var policy=new TreeMap<String,Object>();policy.put("schemaVersion",version);if(BUDGETED_CASE_VERSION.equals(version))policy.put("runtimeByteLimit",budget);
            if(coordinator.journal().persistedBytes()>fileLimit(policy,"runtime.jsonl"))throw new IllegalStateException("SEMANTIC_REPLAY_ARCHIVE_CAPACITY_EXCEEDED");
            var profile=coordinator.constraints().profile();
            return new Cut(projector.mutations().canonicalProgram(),coordinator.journal().path(),coordinator.checkpoints().baseline().commands(),
                    projector.mutations().baselineBindings(),profile==null?"":profile.source(),coordinator.journal().persistedEntries(),coordinator.journal().persistedBytes(),
                    budget,version,coordinator.checkpoints().baseline().stateHash(),last);
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
            try(var input=Files.newInputStream(cut.journal());var target=Files.newOutputStream(output.resolve("runtime.jsonl"))) {
                byte[] buffer=new byte[65536];long remaining=cut.bytes();
                while(remaining>0){int read=input.read(buffer,0,(int)Math.min(buffer.length,remaining));
                    if(read<0)throw new IllegalArgumentException("SEMANTIC_REPLAY_JOURNAL_PREFIX_TRUNCATED");
                    target.write(buffer,0,read);remaining-=read;
                }
            }
            var hashes=new TreeMap<String,Object>();var names=new ArrayList<>(NativeRuntimeReplay.FILES);names.add(PROGRAM);
            for(String name:names)hashes.put(name,fileHash(output.resolve(name)));
            var manifest=new TreeMap<String,Object>(Map.of("schemaVersion",cut.version(),"files",hashes,"entries",cut.entries(),
                    "finalStateHash",cut.result().stateHash(),"finalResultHash",cut.result().resultHash(),"baselineStateHash",cut.baselineHash(),
                    "scope","OBSERVED_SUPPORTED_PROJECTION_ONLY","temporalOrder","ACCEPTED_NATIVE_BOUNDARIES_AND_EXACT_ENGINE_COMPLETIONS","programHash",cut.program().hash()));
            if(BUDGETED_CASE_VERSION.equals(cut.version()))manifest.put("runtimeByteLimit",cut.budget());
            Files.write(output.resolve("manifest.json"),CanonicalJson.encode(manifest));
        }catch(java.io.IOException error){throw new IllegalStateException("SEMANTIC_REPLAY_EXPORT_IO_FAILED",error);}
    }
    static String fileHash(Path file)throws java.io.IOException {
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(file)){byte[] buffer=new byte[65536];int read;while((read=input.read(buffer))!=-1)digest.update(buffer,0,read);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
}

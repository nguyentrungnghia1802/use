package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
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

/** One recorded dispatcher for batch validation and retained offline cursors. Never feeds a Bridge. */
public final class NativeRuntimeReplay {
    public record ReplayReport(boolean complete, int checkedEntries, String finalStateHash, String finalResultHash,
                               List<String> diagnostics) { public ReplayReport { diagnostics = List.copyOf(diagnostics); } }
    static final List<String> FILES = List.of("model.use", "baseline.cmd", "constraints.ocl", "runtime.jsonl");
    public void exportBundle(NativeRuntimeProjector projector, String coreUseText, Path directory) {
        projector.coordinator().read(() -> {
            var coordinator = projector.coordinator();
            if (coordinator.journal().hasGap()) throw new IllegalStateException("REPLAY_EXPORT_INCOMPLETE_JOURNAL:" + coordinator.journal().diagnostic());
            Path output = directory.toAbsolutePath().normalize();
            if (Files.exists(output)) try (var contents = Files.list(output)) {
                if (contents.findAny().isPresent()) throw new IllegalArgumentException("REPLAY_EXPORT_DIRECTORY_NOT_EMPTY");
            }
            Files.createDirectories(output);
            // Types/properties are needed by the recorded state. Operation descriptors and
            // loaded profiles must become available at their recorded journal positions;
            // printing future operations here would change earlier PRE/POST results.
            var schema=new StringWriter(); var writer=new PrintWriter(schema,true);
            var external=coordinator.constraints().savepoint().owned().keySet();
            projector.system().model().processWithVisitor(new org.tzi.use.uml.mm.MMPrintVisitor(writer) {
                @Override public void visitClassInvariant(org.tzi.use.uml.mm.MClassInvariant invariant) {
                    if(!external.contains(invariant.qualifiedName())) super.visitClassInvariant(invariant);
                }
                @Override public void visitOperation(org.tzi.use.uml.mm.MOperation operation) {
                    if(coordinator.operationAtBaseline(operation)) super.visitOperation(operation);
                    else if(!operation.preConditions().isEmpty() || !operation.postConditions().isEmpty())
                        throw new IllegalStateException("REPLAY_UNRECORDED_OPERATION_CONTRACT:"+operation.qualifiedName());
                }
                @Override public void visitPrePostCondition(org.tzi.use.uml.mm.MPrePostCondition condition) {
                    if(coordinator.operationAtBaseline(condition.operation())) super.visitPrePostCondition(condition);
                }
            });
            writer.flush(); Files.writeString(output.resolve("model.use"),schema.toString(),StandardCharsets.UTF_8);
            Files.copy(coordinator.checkpoints().baseline().commands(), output.resolve("baseline.cmd"));
            var profile = coordinator.constraints().profile();
            Files.writeString(output.resolve("constraints.ocl"), profile == null ? "" : profile.source(), StandardCharsets.UTF_8);
            Files.copy(coordinator.journal().path(), output.resolve("runtime.jsonl"));
            Map<String, Object> hashes = new LinkedHashMap<>();
            for (String file : FILES) hashes.put(file, ExternalOclConstraintService.sha256(Files.readAllBytes(output.resolve(file))));
            Map<String, Object> manifest = Map.of("schemaVersion", "1.1.0", "files", hashes,
                    "entries", coordinator.journal().persistedEntries(), "finalStateHash", coordinator.lastObservation().stateHash(),
                    "finalResultHash", coordinator.lastObservation().resultHash(), "baselineStateHash", coordinator.checkpoints().baseline().stateHash(),
                    "scope", "OBSERVED_SUPPORTED_PROJECTION_ONLY");
            Files.write(output.resolve("manifest.json"), CanonicalJson.encode(manifest));
            return null;
        });
    }
    public ReplayReport replay(Path directory) {
        try (var bundle = openBundle(directory)) {
            return bundle.report();
        } catch (Exception error) {
            return new ReplayReport(false, 0, "", "", List.of(error.getClass().getSimpleName() + ":" + error.getMessage()));
        }
    }

    /** Small index, not cached historical state. Metadata is that of the inclusive end ordinal. */
    public record Step(int stepIndex, long transitionOrdinal, long endOrdinal, long recordedStateVersion,
                       String event, String source, String stateHash) { }

    /** Copy once, validate and use exactly those bytes. The input recording is never written. */
    public Bundle openBundle(Path directory) {
        requireWorker();
        Path copy = RuntimeVerificationCoordinator.createEphemeralDirectory(RuntimeVerificationCoordinator.defaultRuntimeRoot());
        try {
            Path root = directory.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root) || Files.isSymbolicLink(root.resolve("manifest.json"))
                    || !Files.isRegularFile(root.resolve("manifest.json")) || Files.size(root.resolve("manifest.json")) > 65536)
                throw new IllegalArgumentException("REPLAY_MANIFEST_MISSING_OR_UNSAFE");
            Files.copy(root.resolve("manifest.json"), copy.resolve("manifest.json"));
            if (Files.size(copy.resolve("manifest.json")) > 65536) throw new IllegalArgumentException("REPLAY_MANIFEST_TOO_LARGE");
            Map<String, Object> manifest = CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(copy.resolve("manifest.json"))));
            if (!java.util.Set.of("1.0.0","1.1.0").contains(manifest.get("schemaVersion"))) throw new IllegalArgumentException("REPLAY_SCHEMA_UNSUPPORTED");
            if (!"OBSERVED_SUPPORTED_PROJECTION_ONLY".equals(manifest.get("scope"))) throw new IllegalArgumentException("REPLAY_SCOPE_UNSUPPORTED");
            Object entries = manifest.get("entries");
            if (!(entries instanceof Number count) || count.longValue() < 1 || count.longValue() > Integer.MAX_VALUE
                    || new java.math.BigDecimal(count.toString()).compareTo(java.math.BigDecimal.valueOf(count.longValue())) != 0)
                throw new IllegalArgumentException("REPLAY_ENTRY_COUNT_INVALID");
            Map<String, Object> hashes = CanonicalJson.object(manifest.get("files"));
            if (!hashes.keySet().equals(java.util.Set.copyOf(FILES))) throw new IllegalArgumentException("REPLAY_FILE_CATALOG_INVALID");
            for (String file : FILES) {
                Path path = root.resolve(file);
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 64L * 1024 * 1024)
                    throw new IllegalArgumentException("REPLAY_FILE_MISSING_OR_UNSAFE:" + file);
                Files.copy(path, copy.resolve(file));
                if (Files.size(copy.resolve(file)) > 64L * 1024 * 1024) throw new IllegalArgumentException("REPLAY_FILE_TOO_LARGE:" + file);
                if (!ExternalOclConstraintService.sha256(Files.readAllBytes(copy.resolve(file))).equals(hashes.get(file)))
                    throw new IllegalArgumentException("REPLAY_FILE_HASH_MISMATCH:" + file);
            }
            var bundle = new Bundle(copy, manifest);
            bundle.validate();
            return bundle;
        } catch (Exception error) {
            deleteOwnedCopy(copy);
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new IllegalArgumentException("REPLAY_OPEN_FAILED", error);
        }
    }

    public static final class Bundle implements AutoCloseable {
        private final Path root;
        private final Map<String,Object> manifest;
        private List<Step> steps;
        private ReplayReport report;
        private boolean closed;
        private Bundle(Path root, Map<String,Object> manifest) { this.root=root; this.manifest=manifest; }
        public List<Step> steps() { return steps; }
        public ReplayReport report() { return report; }
        private void validate() throws Exception {
            var index = new ArrayList<Step>();
            try (var cursor = reconstruct()) {
                var baseline=cursor.projector.coordinator().lastObservation();
                index.add(new Step(0,0,0,baseline.stateVersion(),baseline.eventId(),baseline.sourceId(),baseline.stateHash()));
                String previous = baseline.stateHash();
                while (cursor.hasNext()) {
                    var record = cursor.dispatchNext();
                    var result = cursor.projector.coordinator().lastObservation();
                    boolean transition = java.util.Set.of("EVENT","SNAPSHOT","OPERATION_PRE","OPERATION_POST").contains(record.kind())
                            && !previous.equals(result.stateHash());
                    if (transition) {
                        finishStep(index, cursor.previousResult, record.ordinal()-1);
                        index.add(new Step(index.size(), record.ordinal(), record.ordinal(), result.stateVersion(),
                                result.eventId(), result.sourceId(), result.stateHash()));
                    }
                    previous=result.stateHash();
                }
                cursor.assertEnd();
                if (index.isEmpty()) throw new IllegalArgumentException("REPLAY_BASELINE_REQUIRED");
                finishStep(index, cursor.projector.coordinator().lastObservation(), cursor.ordinal-1);
                steps=List.copyOf(index);
                var last=cursor.projector.coordinator().lastObservation();
                report=new ReplayReport(!cursor.stale, Math.toIntExact(cursor.ordinal), last.stateHash(), last.resultHash(),
                        List.of(cursor.stale ? "REPLAY_PARTIAL_COVERAGE_GAP" : "OBSERVED_SUPPORTED_PROJECTION_ONLY"));
            }
        }
        private static void finishStep(List<Step> index, RuntimeVerificationResult end, long ordinal) {
            var step=index.getLast();
            index.set(index.size()-1, new Step(step.stepIndex(),step.transitionOrdinal(),ordinal,end.stateVersion(),
                    step.event(),step.source(),end.stateHash()));
        }
        /** Fresh compiler/model/baseline and ORIGINAL mutation-engine baseline, never a SOIL checkpoint. */
        public Cursor reconstruct() throws Exception {
            requireWorker();
            if (closed) throw new IllegalStateException("REPLAY_BUNDLE_CLOSED");
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
            var cursor = new Cursor(this, system, index);
            try { cursor.dispatchNext(); return cursor; }
            catch (Exception error) { cursor.close(); throw error; }
        }
        public Cursor atStep(int step) throws Exception {
            if (report==null || !report.complete()) throw new IllegalArgumentException("STEP_REPLAY_RECORDED_VALIDATION_FAILED:" + report);
            var target=steps.get(step);
            var cursor=reconstruct();
            try { cursor.forward(target.endOrdinal()); return cursor; }
            catch (Exception error) { cursor.close(); throw error; }
        }
        @Override public void close() { if (!closed) { closed=true; deleteOwnedCopy(root); } }
    }

    private record Entry(long ordinal, String hash, String kind, Map<String,Object> payload, Map<String,Object> expected) { }
    public static final class Cursor implements AutoCloseable {
        private final Bundle bundle;
        private final org.tzi.use.uml.sys.MSystem system;
        private final Map<String,MObject> index;
        private final BufferedReader reader;
        private NativeRuntimeProjector projector;
        private String nextLine, chain="", lastProfile="";
        private long ordinal;
        private boolean stale, closed, failed;
        private RuntimeVerificationResult previousResult;
        private Cursor(Bundle bundle, org.tzi.use.uml.sys.MSystem system, Map<String,MObject> index) throws Exception {
            this.bundle=bundle; this.system=system; this.index=index;
            reader=Files.newBufferedReader(bundle.root.resolve("runtime.jsonl"),StandardCharsets.UTF_8);
            nextLine=reader.readLine();
        }
        public NativeRuntimeProjector projector() { return projector; }
        public boolean failed() { return failed; }
        public long endOrdinal() { return ordinal-1; }
        private boolean hasNext() { return nextLine!=null; }
        private Entry parse(String line, long position, String previous) {
            if (closed || line==null) throw new IllegalArgumentException("REPLAY_TRUNCATED:"+position);
            var body=new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(line.getBytes(StandardCharsets.UTF_8))));
            Object hash=body.remove("entryHash");
            if (((Number)body.get("ordinal")).longValue()!=position || !previous.equals(body.get("previousHash"))
                    || !ExternalOclConstraintService.sha256(CanonicalJson.encode(body)).equals(hash))
                throw new IllegalArgumentException("REPLAY_CHAIN_CORRUPT:"+position);
            String kind=(String)body.get("kind");
            if ("GAP".equals(kind)) throw new IllegalArgumentException("REPLAY_GAP:"+body.get("diagnostic"));
            var expected=CanonicalJson.object(body.get("result")); var payload=CanonicalJson.object(body.get("payload"));
            RuntimeVerificationResult.fromMap(expected);
            return new Entry(position,(String)hash,kind,payload,expected);
        }
        private Entry dispatchNext() throws Exception {
            var record=parse(nextLine,ordinal,chain);
            dispatch(record);
            nextLine=reader.readLine(); return record;
        }
        private void dispatch(Entry record) {
            var expected=record.expected(); var payload=record.payload(); String kind=record.kind();
            previousResult=projector==null ? null : projector.coordinator().lastObservation();
            if (projector==null) {
                if (!"BASELINE".equals(kind) || ((Number)expected.get("stateVersion")).longValue()!=0)
                    throw new IllegalArgumentException("REPLAY_BASELINE_REQUIRED");
                projector=new NativeRuntimeProjector(system,index,(String)expected.get("sessionId"),
                        ((Number)expected.get("generation")).longValue(),(String)expected.get("modelRevision"),new CodeGroundedRuntimeRuleRegistry());
                if (!projector.coordinator().latest().stateHash().equals(bundle.manifest.get("baselineStateHash")))
                    throw new IllegalArgumentException("REPLAY_BASELINE_HASH_MISMATCH");
            } else applyEntry(projector,kind,payload,expected,true);
            var actual=projector.coordinator().lastObservation();
            if (!actual.stateHash().equals(expected.get("stateHash")) || !actual.resultHash().equals(expected.get("resultHash"))
                    || actual.stateVersion()!=((Number)expected.get("stateVersion")).longValue())
                throw new IllegalArgumentException("REPLAY_STATE_OR_RESULT_MISMATCH:"+ordinal);
            if (projector.coordinator().journal().hasGap()) throw new IllegalArgumentException("REPLAY_LOCAL_PERSISTENCE_GAP");
            stale |= "STALE".equals(expected.get("freshness"));
            if ("PROFILE".equals(kind)) lastProfile=(String)payload.get("source");
            ordinal++; chain=record.hash();
        }
        public void forward(long endOrdinal) throws Exception {
            forward(endOrdinal, () -> { });
        }
        public void forward(long endOrdinal, Runnable admission) throws Exception {
            forward(endOrdinal, admission, () -> { });
        }
        public void forward(long endOrdinal, Runnable admission, Runnable committedSelection) throws Exception {
            requireWorker();
            if (closed || failed) throw new IllegalStateException("REPLAY_CURSOR_RECONSTRUCTION_REQUIRED");
            var pending=new ArrayList<Entry>(); long position=ordinal; String previous=chain;
            try {
                // IO and syntax/chain/result decoding stay on the worker, before visible writes.
                while (position<=endOrdinal) {
                    var record=parse(nextLine,position++,previous); pending.add(record); previous=record.hash();
                    nextLine=reader.readLine();
                }
                boolean end=!hasNext();
                String finalProfile=end ? Files.readString(bundle.root.resolve("constraints.ocl")) : null;
                projector.coordinator().replayBatch(() -> {
                    admission.run();
                    for (var record:pending) dispatch(record);
                    if (end) assertEnd(finalProfile);
                    admission.run();
                    // Select the fully checked boundary before its single GUI notification.
                    committedSelection.run(); return null;
                });
            } catch (Exception error) { failed=true; throw error; }
        }
        private void assertEnd() throws Exception {
            assertEnd(Files.readString(bundle.root.resolve("constraints.ocl")));
        }
        private void assertEnd(String profile) {
            var last=projector==null ? null : projector.coordinator().lastObservation();
            if (last==null || ordinal!=((Number)bundle.manifest.get("entries")).longValue()
                    || !last.stateHash().equals(bundle.manifest.get("finalStateHash"))
                    || !last.resultHash().equals(bundle.manifest.get("finalResultHash"))
                    || !lastProfile.equals(profile))
                throw new IllegalArgumentException("REPLAY_TRUNCATED_OR_PROFILE_MISMATCH");
        }
        @Override public void close() {
            if (closed) return; closed=true;
            if (projector!=null) projector.close();
            try { reader.close(); } catch (java.io.IOException ignored) { }
        }
    }
    private static void requireWorker() {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("REPLAY_REQUIRES_WORKER");
    }
    private static void deleteOwnedCopy(Path copy) {
        try (var paths=Files.walk(copy)) {
            for (Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (java.io.IOException error) { throw new IllegalStateException("REPLAY_COPY_CLEANUP_FAILED",error); }
    }
    /** The same production mutations serve both modes; only recorded PROFILE installation differs. */
    static void applyEntry(NativeRuntimeProjector projector, String kind, Map<String,Object> payload,
                           Map<String,Object> expected, boolean recordedProfile) {
        switch (kind) {
            case "SNAPSHOT" -> projector.applySnapshot(ContractPayloads.runtime(payload), (String)expected.get("sessionId"),
                    ((Number)expected.get("generation")).longValue());
            case "EVENT", "OPERATION_PRE", "OPERATION_POST", "STREAM_BOUNDARY" -> projector.apply(ContractPayloads.event(payload));
            case "CAPABILITIES" -> {
                Map<String,String> capabilities=new LinkedHashMap<>(),versions=new LinkedHashMap<>();
                CanonicalJson.object(payload.get("capabilities")).forEach((key,value)->capabilities.put(key,(String)value));
                CanonicalJson.object(payload.get("sourceVersions")).forEach((key,value)->versions.put(key,(String)value));
                projector.coordinator().runtimeCapabilities(capabilities,versions);
            }
            case "POLICY" -> {
                if(!recordedProfile){projector.coordinator().manualVerify();break;}
                projector.coordinator().configurePolicy((String)payload.get("constraintId"),
                        org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy.fromMap(CanonicalJson.object(payload.get("policy"))),
                        (String)payload.get("conditionFingerprint"));
            }
            case "PROFILE" -> {
                if (!recordedProfile) { projector.coordinator().manualVerify(); break; }
                Map<String,Boolean> enabled=new LinkedHashMap<>();
                Map<String,Boolean> negated=new LinkedHashMap<>();
                if (payload.containsKey("enabled")) CanonicalJson.object(payload.get("enabled")).forEach((id,value)->enabled.put(id,(Boolean)value));
                if (payload.containsKey("negated")) CanonicalJson.object(payload.get("negated")).forEach((id,value)->negated.put(id,(Boolean)value));
                projector.coordinator().loadProfileSource((String)payload.get("sourceFile"),(String)payload.get("source"),enabled,negated);
            }
            case "MANUAL" -> projector.coordinator().manualVerify();
            case "COVERAGE" -> projector.coordinator().coverageGap(payload.containsKey("event")
                    ? ContractPayloads.event(CanonicalJson.object(payload.get("event"))) : null,(String)payload.get("diagnostic"));
            case "REJECTED" -> {
                boolean rejected=false;
                try { projector.apply(ContractPayloads.event(payload)); } catch (RuntimeException error) { rejected=true; }
                if (!rejected) throw new IllegalArgumentException("REPLAY_EXPECTED_REJECTION");
            }
            default -> throw new IllegalArgumentException("REPLAY_ENTRY_UNSUPPORTED:" + kind);
        }
    }
}

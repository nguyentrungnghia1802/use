package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.sys.MObject;

/** Retrospective evaluation, not recorded parity and never a live runtime authority. */
public final class NativeRuntimeReanalysis {
    public record Record(long originalOrdinal, long originalVersion, String originalConstraintSet, String kind,
            RuntimeVerificationResult result) { }
    public record Report(boolean complete, long checkedEntries, String origin, String profileHash,
            String finalStateHash, Path output, List<Record> retainedTail, List<String> diagnostics) {
        public Report { retainedTail=List.copyOf(retainedTail); diagnostics=List.copyOf(diagnostics); }
    }
    public Report analyze(Path bundle, String file, String source, Map<String,Boolean> enabled, Path output) {
        return analyze(bundle,file,source,enabled,Map.of(),output);
    }
    public Report analyze(Path bundle, String file, String source, Map<String,Boolean> enabled,
                          Map<String,Boolean> negated, Path output) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("REANALYSIS_REQUIRES_WORKER");
        String profileHash=ExternalOclConstraintService.sha256(source.getBytes(StandardCharsets.UTF_8));
        Path snapshot=null; NativeRuntimeProjector projector=null; long checked=0; String stateHash=""; boolean outputOwned=false;
        NativeRuntimeReplay.Bundle recorded=null; NativeRuntimeReplay.Cursor recordedCursor=null;
        boolean projectionChanged=false;
        var tail=new ArrayDeque<Record>();
        try {
            Path original=bundle.toAbsolutePath().normalize(), destination=output.toAbsolutePath().normalize();
            if(Files.isSymbolicLink(original)||!Files.isDirectory(original))throw new IllegalArgumentException("REANALYSIS_BUNDLE_UNSAFE");
            if (destination.startsWith(original) || Files.isSymbolicLink(destination)) throw new IllegalArgumentException("REANALYSIS_OUTPUT_MUST_BE_SEPARATE");
            if (Files.exists(destination)) try(var entries=Files.list(destination)) {
                if(entries.findAny().isPresent()) throw new IllegalArgumentException("REANALYSIS_OUTPUT_NOT_EMPTY");
            }
            // Copy first, then validate and analyze the SAME immutable copy: no validation/use race on original evidence.
            snapshot=RuntimeVerificationCoordinator.createEphemeralDirectory(RuntimeVerificationCoordinator.defaultRuntimeRoot());
            var names=new java.util.ArrayList<>(NativeRuntimeReplay.FILES); names.add("manifest.json");
            for(String name:names) {
                Path input=original.resolve(name);
                if(Files.isSymbolicLink(input) || !Files.isRegularFile(input) || Files.size(input)>64L*1024*1024)
                    throw new IllegalArgumentException("REANALYSIS_FILE_MISSING_OR_UNSAFE:"+name);
                Files.copy(input,snapshot.resolve(name));
            }
            try { recorded=new NativeRuntimeReplay().openBundle(snapshot); }
            catch(RuntimeException rejected) { throw new IllegalArgumentException("REANALYSIS_RECORDED_VALIDATION_FAILED",rejected); }
            var validation=recorded.report();
            if(!validation.complete()) throw new IllegalArgumentException("REANALYSIS_RECORDED_VALIDATION_FAILED:"+validation.diagnostics());
            recordedCursor=recorded.reconstruct();
            StringWriter errors=new StringWriter();
            var model=USECompiler.compileSpecification(new ByteArrayInputStream(Files.readAllBytes(snapshot.resolve("model.use"))),
                    "model.use",snapshot.resolve("model.use").toUri(),new PrintWriter(errors,true),new ModelFactory());
            if(model==null || !errors.toString().isBlank()) throw new IllegalArgumentException("REANALYSIS_MODEL_INVALID:"+errors);
            var system=new NativeUseSoilExporter().replay(model,Files.readString(snapshot.resolve("baseline.cmd")));
            Map<String,MObject> index=new LinkedHashMap<>();
            for(var object:system.state().allObjects()) {
                var attribute=object.cls().attribute("semanticId",true);
                if(attribute!=null && object.state(system.state()).attributeValue(attribute) instanceof StringValue identity
                        && index.putIfAbsent(identity.value(),object)!=null) throw new IllegalArgumentException("REANALYSIS_DUPLICATE_ID");
            }
            boolean outputReady=false;
            try(var reader=Files.newBufferedReader(snapshot.resolve("runtime.jsonl"),StandardCharsets.UTF_8)) {
                String line;
                while((line=reader.readLine())!=null) {
                    var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(StandardCharsets.UTF_8)));
                    var expected=CanonicalJson.object(entry.get("result"));
                    var payload=CanonicalJson.object(entry.get("payload")); String kind=(String)entry.get("kind");
                    if(projector==null) {
                        if(!kind.equals("BASELINE")) throw new IllegalArgumentException("REANALYSIS_BASELINE_REQUIRED");
                        projector=new NativeRuntimeProjector(system,index,(String)expected.get("sessionId"),
                                ((Number)expected.get("generation")).longValue(),(String)expected.get("modelRevision"),new CodeGroundedRuntimeRuleRegistry());
                        projector.coordinator().loadProfileSource(file,source,enabled,negated);
                        Files.createDirectories(destination); outputReady=true; outputOwned=true;
                        Files.writeString(destination.resolve("current-profile.ocl"),source,StandardCharsets.UTF_8);
                    } else NativeRuntimeReplay.applyEntry(projector,kind,payload,expected,false);
                    var actual=projector.coordinator().lastObservation(); stateHash=actual.stateHash();
                    if(projector.coordinator().journal().hasGap()) throw new IllegalArgumentException("REANALYSIS_PERSISTENCE_GAP");
                    // Recorded replay still validates every exact state/result hash. A new profile
                    // may select different Belief instances; all other native domain state must agree.
                    recordedCursor.forward(checked);
                    String recordedDomainHash=domainStateHash(recordedCursor.projector().system());
                    String analysisDomainHash=domainStateHash(system);
                    if(!analysisDomainHash.equals(recordedDomainHash))
                        throw new IllegalArgumentException("REANALYSIS_DOMAIN_STATE_RECONSTRUCTION_MISMATCH:"+checked);
                    boolean selectionChanged=!stateHash.equals(expected.get("stateHash"));
                    projectionChanged|=selectionChanged;
                    var record=new Record(((Number)entry.get("ordinal")).longValue(),((Number)expected.get("stateVersion")).longValue(),
                            (String)expected.get("constraintSetHash"),kind,actual);
                    if(tail.size()==128) tail.removeFirst(); tail.addLast(record);
                    var evidence=new LinkedHashMap<String,Object>();
                    evidence.put("origin","REPLAY_REANALYSIS"); evidence.put("originalOrdinal",record.originalOrdinal());
                    evidence.put("originalVersion",record.originalVersion()); evidence.put("originalConstraintSet",record.originalConstraintSet());
                    evidence.put("kind",kind); evidence.put("newProfileFile",file); evidence.put("newProfileHash",profileHash);
                    evidence.put("enabled",enabled); evidence.put("negated",negated);
                    evidence.put("replayLocalVersion",actual.stateVersion()); evidence.put("result",actual.toMap());
                    evidence.put("recordedStateHash",expected.get("stateHash")); evidence.put("beliefProjectionChanged",selectionChanged);
                    evidence.put("recordedDomainStateHash",recordedDomainHash); evidence.put("analysisDomainStateHash",analysisDomainHash);
                    Files.writeString(destination.resolve("reanalysis.jsonl"),new String(CanonicalJson.encode(evidence),StandardCharsets.UTF_8)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                    checked++;
                }
            }
            if(!outputReady || checked!=validation.checkedEntries()) throw new IllegalArgumentException("REANALYSIS_TIMELINE_INCOMPLETE");
            Files.write(destination.resolve("reanalysis-summary.json"),CanonicalJson.encode(Map.ofEntries(
                    Map.entry("origin","REPLAY_REANALYSIS"),Map.entry("complete",true),Map.entry("entries",checked),
                    Map.entry("profileHash",profileHash),Map.entry("profileFile",file),Map.entry("finalStateHash",stateHash),
                    Map.entry("scope","OBSERVED_SUPPORTED_PROJECTION_ONLY"),Map.entry("recordedResultParity",false),
                    Map.entry("recordedStateParity",!projectionChanged),Map.entry("domainStateParity",true),
                    Map.entry("beliefProjectionChanged",projectionChanged))));
            return new Report(true,checked,"REPLAY_REANALYSIS",profileHash,stateHash,destination,List.copyOf(tail),List.of(projectionChanged
                    ? "DOMAIN_STATE_PARITY_ONLY; SELECTIVE_BELIEF_PROJECTION_PER_CURRENT_PROFILE; NEW_PROFILE_RESULTS_NOT_LIVE"
                    : "STATE_RECONSTRUCTION_PARITY_ONLY; NEW_PROFILE_RESULTS_NOT_LIVE"));
        } catch(Exception failure) {
            if(outputOwned) try { Files.write(output.resolve("reanalysis-summary.json"),CanonicalJson.encode(Map.of("origin","REPLAY_REANALYSIS",
                    "complete",false,"entries",checked,"profileHash",profileHash,"diagnostic",failure.toString()))); }
            catch(java.io.IOException ignored) { }
            return new Report(false,checked,"REPLAY_REANALYSIS",profileHash,stateHash,output,List.copyOf(tail),List.of(failure.getClass().getSimpleName()+":"+failure.getMessage()));
        } finally {
            if(projector!=null) projector.close();
            if(recordedCursor!=null) recordedCursor.close();
            if(recorded!=null) recorded.close();
            if(snapshot!=null) try(var paths=Files.walk(snapshot)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch(java.io.IOException ignored) { }
        }
    }

    /** Only the approved Belief objects/ownership links may differ between verification profiles. */
    private static String domainStateHash(org.tzi.use.uml.sys.MSystem system) {
        var state=new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(
                new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStateExporter().export(system,"")
                        .getBytes(StandardCharsets.UTF_8))));
        state.put("objects",((List<?>)state.get("objects")).stream()
                .filter(row->!"Belief".equals(CanonicalJson.object(row).get("class"))).toList());
        String relation=org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("hasBelief","Agent","Belief");
        state.put("links",((List<?>)state.get("links")).stream()
                .filter(row->!relation.equals(CanonicalJson.object(row).get("association"))).toList());
        return ExternalOclConstraintService.sha256(CanonicalJson.encode(state));
    }
}

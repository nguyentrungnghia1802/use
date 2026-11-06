import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;

/** Status/budget reads of the same authentic frozen House Building snapshot. */
public class SnapshotBudgetBenchmark {
    @SuppressWarnings("unchecked") static Map<String,String> strings(Object value){return (Map<String,String>)value;}
    @SuppressWarnings("unchecked") static List<String> list(Object value){return (List<String>)value;}
    public static void main(String[] args)throws Exception {
        Map<String,Object> data;try(var input=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){data=CanonicalJson.object(CanonicalJson.decode(input.readAllBytes()));}
        if(data.get("profile")!=null)throw new IllegalArgumentException("Benchmark requires the recorded authored profile, without an extra external OCL overlay");
        var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(data.get("result")));var m=CanonicalJson.object(data.get("metadata"));var i=CanonicalJson.object(data.get("image"));
        var source=new TreeMap<String,Completeness>();CanonicalJson.object(m.get("sourceCompleteness")).forEach((key,value)->source.put(key,Completeness.valueOf((String)value)));
        var metadata=new VerificationSnapshot.Metadata((String)m.get("snapshotId"),((Number)m.get("boundarySequence")).longValue(),Instant.parse((String)m.get("capturedAt")),((Number)m.get("generation")).longValue(),
                CheckpointType.valueOf((String)m.get("checkpoint")),new HashSet<>(list(m.get("correlations"))),VerificationSnapshot.SynchronizationState.valueOf((String)m.get("lifecycle")),strings(m.get("capabilities")),strings(m.get("sourceVersions")),Completeness.valueOf((String)m.get("completeness")),source,strings(m.get("evidence")));
        var objects=new TreeMap<String,VerificationSnapshot.ObjectState>();for(Object raw:(List<?>)i.get("objects")){
            var o=CanonicalJson.object(raw);var object=new VerificationSnapshot.ObjectState((String)o.get("name"),(String)o.get("className"),(String)o.get("semanticId"),list(o.get("exactIdentities")),strings(o.get("attributes")),strings(o.get("valueTypes")),strings(o.get("bindingMetadata")));objects.put(object.name(),object);
        }
        var links=new ArrayList<VerificationSnapshot.LinkState>();for(Object raw:(List<?>)i.get("links")){
            var link=CanonicalJson.object(raw);links.add(new VerificationSnapshot.LinkState((String)link.get("association"),list(link.get("participants")),((List<?>)link.get("qualifiers")).stream().map(SnapshotBudgetBenchmark::list).toList()));
        }
        var snapshot=new VerificationSnapshot(((Number)data.get("currentVersion")).longValue(),result,null,metadata,new VerificationSnapshot.StateImage(objects,links,(String)i.get("soil")));
        if(snapshot.estimatedBytes()!=CanonicalJson.encode(data).length)throw new AssertionError("Snapshot byte accounting differs from original recording");
        var retention=new SnapshotRetention(8,16L*1024*1024);retention.retain(snapshot);long expected=retention.retainedBytes(),start=System.nanoTime();
        for(int n=0;n<100;n++)if(retention.retainedBytes()!=expected)throw new AssertionError("Budget changed");
        long elapsed=System.nanoTime()-start,sizing=-1;
        try{var field=SnapshotRetention.class.getDeclaredField("sizingOperations");field.setAccessible(true);sizing=field.getLong(retention);}catch(NoSuchFieldException originalVersion){ }
        var receipt=Map.of("nativeResultHash",result.resultHash(),"snapshotBytes",snapshot.estimatedBytes(),"retainedBytes",expected,"statusReads",100,"statusReadNanos",elapsed,"sizingOperations",sizing);
        Files.write(Path.of(args[1]),CanonicalJson.encode(receipt));System.out.println(new String(CanonicalJson.encode(receipt),java.nio.charset.StandardCharsets.UTF_8));
    }
}

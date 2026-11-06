import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;

/** Same authentic immutable native result and checked range in both versions. */
public class HistoryReadBenchmark {
    public static void main(String[] args)throws Exception {
        Map<String,Object> recording;try(var input=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){recording=CanonicalJson.object(CanonicalJson.decode(input.readAllBytes()));}
        var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(recording.get("result")));
        var cut=new VerificationSnapshot(result.stateVersion(),result,null);
        Path output=Path.of(args[1]);Files.createDirectories(output);
        var journal=new RuntimeEventJournal(output,2,256*1024*1024,true);
        long writing=System.nanoTime();for(int i=0;i<180;i++)if(!journal.append(i%11==0?"CHECKPOINT_AUDIT":"ENGINE_RESULT",Map.of(),result))throw new AssertionError(journal.diagnostic());writing=System.nanoTime()-writing;
        var elapsed=new ArrayList<Long>();String expected="";
        for(int i=0;i<3;i++){
            long start=System.nanoTime();var checks=VerifiedCheckpointInventory.evaluatePrefix(cut,journal,150,180);elapsed.add(System.nanoTime()-start);
            String hash=VerifiedCheckpointProtocol.hash(checks.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().payload())));
            if(i>0&&!expected.equals(hash))throw new AssertionError("Native inventory changed");expected=hash;
        }
        var receipt=new TreeMap<String,Object>();receipt.put("nativeResultHash",result.resultHash());receipt.put("checksHash",expected);receipt.put("range",List.of(150,180));receipt.put("journalEntries",journal.persistedEntries());
        receipt.put("physicalBytes",journal.persistedBytes());receipt.put("decodedBytes",journal.decodedBytes());receipt.put("readNanos",elapsed);receipt.put("appendNanos",writing);
        try{var stats=journal.getClass().getMethod("lastRangeRead").invoke(journal);receipt.put("readStats",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(stats));}catch(NoSuchMethodException previousVersion){receipt.put("readStats","Original full-prefix scan");}
        Files.write(output.resolve("receipt.json"),CanonicalJson.encode(receipt));System.out.println(new String(CanonicalJson.encode(receipt),java.nio.charset.StandardCharsets.UTF_8));
    }
}

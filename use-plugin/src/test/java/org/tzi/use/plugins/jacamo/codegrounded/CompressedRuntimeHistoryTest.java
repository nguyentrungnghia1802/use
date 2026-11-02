package org.tzi.use.plugins.jacamo.codegrounded;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import static org.junit.jupiter.api.Assertions.*;
class CompressedRuntimeHistoryTest {
 @TempDir Path root;
 static RuntimeVerificationResult result(int n){var now=java.time.Instant.EPOCH;return new RuntimeVerificationResult("run",1,"revision",n,"event-"+n,"source",n,now,now,now,"cut","contracts","state-"+n,List.of(),0,"PARTIAL","CURRENT_OBSERVED","");}
 @Test void compressedStorageRetainsIdenticalCanonicalRecordsAndRecoversEvictedPrefix()throws Exception{
  var packed=new RuntimeEventJournal(root,2,32768,true);var plain=new RuntimeEventJournal(root,2,8*1024*1024);
  for(int i=0;i<20;i++){var payload=Map.<String,Object>of("repeated","canonical evidence ".repeat(3000));assertTrue(packed.append("EVENT",payload,result(i)));assertTrue(plain.append("EVENT",payload,result(i)));}
  assertFalse(packed.hasGap());assertTrue(packed.persistedBytes()<32768);assertTrue(packed.decodedBytes()>1024*1024);
  assertEquals(20,packed.page(0,128).entries().size());var records=new ArrayList<RuntimeHistoryPage.Entry>();packed.visitRange(0,20,records::add);assertEquals(20,records.size());
  Path exported=root.resolve("exported.jsonl");packed.exportPlain(exported,packed.persistedBytes(),packed.decodedBytes());assertArrayEquals(Files.readAllBytes(plain.path()),Files.readAllBytes(exported));
  byte[] bytes=Files.readAllBytes(packed.path());bytes[bytes.length-1]^=1;Files.write(packed.path(),bytes);assertThrows(RuntimeException.class,()->packed.page(0,128));
 }
 @Test void readersUseCommittedPhysicalPrefixesWhileNewGzipMembersAreAppended()throws Exception{
  var journal=new RuntimeEventJournal(root,4,4*1024*1024,true);var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{var writer=pool.submit(()->{for(int i=0;i<80;i++){assertTrue(journal.append("EVENT",Map.of("data","source".repeat(1000)),result(i)));try{Thread.sleep(1);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}});
   while(!writer.isDone()){var page=journal.page(0,128);assertEquals(page.nextOrdinal(),page.entries().size());}writer.get();assertEquals(80,journal.page(0,128).entries().size());
  }finally{pool.shutdownNow();}
 }
 @Test void physicalQuotaStillFailsClosedForNoncompressibleEvidence(){
  var journal=new RuntimeEventJournal(root,4,32768,true);var random=new java.util.Random(184);boolean accepted=true;
  for(int i=0;i<100&&accepted;i++){byte[] bytes=new byte[8192];random.nextBytes(bytes);accepted=journal.append("EVENT",Map.of("data",Base64.getEncoder().encodeToString(bytes)),result(i));}
  assertFalse(accepted);assertTrue(journal.hasGap());assertEquals("JOURNAL_OVERFLOW",journal.diagnostic());assertThrows(RuntimeException.class,()->journal.visitRange(0,journal.persistedEntries(),entry->{}));
 }
}

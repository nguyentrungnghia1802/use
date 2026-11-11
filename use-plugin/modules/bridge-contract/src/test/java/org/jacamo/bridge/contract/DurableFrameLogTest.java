package org.jacamo.bridge.contract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.RandomAccessFile;
import static org.junit.jupiter.api.Assertions.*;
class DurableFrameLogTest {
 @TempDir Path root;
 @Test void everyFrameSurvivesCloseAndReopenInOrder(){
  Path file=root.resolve("events.frames");
  try(var log=new DurableFrameLog(file,1024)){for(int i=0;i<500;i++)assertEquals(i,log.append(("event-"+i).getBytes()));log.checkpoint(17);}
  try(var log=new DurableFrameLog(file,1024)){assertEquals(500,log.size());for(int i=0;i<500;i++)assertEquals("event-"+i,new String(log.read(i)));}
  assertEquals("17",assertDoesNotThrow(()->Files.readString(Path.of(file+".cursor"))));
 }
 @Test void corruptionAndTruncationFailClosed()throws Exception {
  Path file=root.resolve("events.frames");try(var log=new DurableFrameLog(file,1024)){log.append("event".getBytes());}
  try(var raw=new RandomAccessFile(file.toFile(),"rw")){raw.seek(raw.length()-1);raw.writeByte(0);}
  assertThrows(IllegalStateException.class,()->new DurableFrameLog(file,1024));
  try(var raw=new RandomAccessFile(file.toFile(),"rw")){raw.setLength(12);}
  assertThrows(IllegalStateException.class,()->new DurableFrameLog(file,1024));
 }
}

package org.tzi.use.plugins.jacamo.bridge;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.adapter.LocalTcpBridgeServer;
import static org.junit.jupiter.api.Assertions.*;
class DurableIngressTest {
 @Test void retiringSubscriptionCannotStartASecondDeliveryWorker()throws Exception {
  byte[] secret="0123456789abcdef0123456789abcdef".getBytes();var loopback=InetAddress.getLoopbackAddress();
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var server=new LocalTcpBridgeServer(loopback,0,secret,1024,2,2048,()->new byte[]{1},()->new byte[]{1},()->new byte[]{1},()->new byte[]{0})){
   server.start();server.publish("zero",new byte[]{0});server.publish("one",new byte[]{1});
   try(var transport=new LocalTcpBridgeTransport(loopback,server.port(),secret,1024,2000)){
    var subscription=transport.subscribe("zero",b->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
    try{assertTrue(entered.await(3,TimeUnit.SECONDS));subscription.close();assertEquals("BRIDGE_DELIVERY_STILL_RETIRING",assertThrows(IllegalStateException.class,()->transport.subscribe("",b->{})).getMessage());}
    finally{release.countDown();subscription.close();}
   }
  }
 }
 @Test void slowProjectionDoesNotBlockDurableReceptionOrLoseOldResumeFrames()throws Exception {
  byte[] secret="0123456789abcdef0123456789abcdef".getBytes();var loopback=InetAddress.getLoopbackAddress();
  try(var server=new LocalTcpBridgeServer(loopback,0,secret,1024,2,2048,()->new byte[]{1},()->new byte[]{1},()->new byte[]{1},()->new byte[]{0})){
   server.start();for(int i=0;i<300;i++)server.publish("event:"+i,Integer.toString(i).getBytes());
   var gate=new CountDownLatch(1);var first=new CountDownLatch(1);var done=new CountDownLatch(299);var seen=new CopyOnWriteArrayList<Integer>();var error=new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
   try(var transport=new LocalTcpBridgeTransport(loopback,server.port(),secret,1024,2000)){
    transport.pauseDelivery();
    var subscription=transport.subscribe("event:0",bytes->{first.countDown();try{assertTrue(gate.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}seen.add(Integer.parseInt(new String(bytes)));done.countDown();},error::set);
    try{
     long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
     while(((Number)transport.ingressProgress().get("saved")).longValue()<299&&System.nanoTime()<end)Thread.sleep(10);
     assertEquals(299L,transport.ingressProgress().get("saved"));assertEquals(0L,transport.ingressProgress().get("processed"));
     assertEquals(1,first.getCount(),"Bootstrap stores frames on disk without overflowing the client memory handoff");
     transport.resumeDelivery();assertTrue(first.await(3,TimeUnit.SECONDS));
     gate.countDown();assertTrue(done.await(10,TimeUnit.SECONDS));assertNull(error.get());
     assertEquals(java.util.stream.IntStream.range(1,300).boxed().toList(),seen);
    }finally{gate.countDown();subscription.close();}
   }
  }
 }
}

package org.jacamo.bridge.adapter;

import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.*;

/** Source callbacks are persisted before the publisher can consume them. */
final class DurableRuntimeEventQueue implements AutoCloseable {
    private DurableFrameLog log=DurableFrameLog.create("source-",CanonicalJson.DEFAULT_MAX_BYTES);
    private long cursor;
    private boolean closed;
    synchronized void reopen(){if(closed){log=DurableFrameLog.create("source-",CanonicalJson.DEFAULT_MAX_BYTES);cursor=0;closed=false;}}
    synchronized boolean offer(RuntimeEvent event){if(closed)throw new IllegalStateException("SOURCE_SPOOL_CLOSED");log.append(CanonicalJson.encode(ContractPayloads.event(event)));notifyAll();return true;}
    synchronized RuntimeEvent poll(){if(cursor>=log.size())return null;return ContractPayloads.event(CanonicalJson.object(CanonicalJson.decode(log.read(cursor++))));}
    synchronized boolean isEmpty(){return cursor>=log.size();}
    synchronized void clear(){close();}
    synchronized RuntimeEvent poll(long timeout,TimeUnit unit)throws InterruptedException {
        long deadline=System.nanoTime()+unit.toNanos(timeout);
        while(cursor>=log.size()){long left=deadline-System.nanoTime();if(left<=0)return null;TimeUnit.NANOSECONDS.timedWait(this,left);}
        return poll();
    }
    @Override public synchronized void close(){if(closed)return;closed=true;log.checkpoint(cursor);log.close();}
}

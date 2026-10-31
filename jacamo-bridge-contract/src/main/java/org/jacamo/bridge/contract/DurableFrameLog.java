package org.jacamo.bridge.contract;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;

/** Append-only framed spool. A successful append is forced to disk before publication.
 * Files survive close, including unconsumed records; capacity failure is explicit. */
public final class DurableFrameLog implements AutoCloseable {
    private final Path path;
    private final RandomAccessFile file;
    private final ArrayList<Long> offsets = new ArrayList<>();
    private final int maxFrame;
    private boolean failed;
    public static DurableFrameLog create(String prefix,int maxFrame) {
        try {
            Path root=Path.of(System.getProperty("jacamo.bridge.spool.root",System.getProperty("java.io.tmpdir"))).resolve("jacamo-bridge-spool");
            Files.createDirectories(root);
            return new DurableFrameLog(Files.createTempFile(root,prefix,".frames"),maxFrame);
        } catch(IOException e){throw new IllegalStateException("SPOOL_CREATE_FAILED",e);}
    }
    public DurableFrameLog(Path path,int maxFrame) {
        this.path=path;this.maxFrame=maxFrame;
        RandomAccessFile opened=null;
        try {
            opened=new RandomAccessFile(path.toFile(),"rw");file=opened;
            long position=0;
            while(position<file.length()) {offsets.add(position);readAt(position);position=file.getFilePointer();}
        } catch(Exception e){if(opened!=null)try{opened.close();}catch(IOException close){e.addSuppressed(close);}throw new IllegalStateException("SPOOL_OPEN_OR_INTEGRITY_FAILED:"+path,e);}
    }
    public synchronized long append(byte[] frame) {
        if(failed)throw new IllegalStateException("SPOOL_PERSISTENCE_LOST:"+path);
        if(frame.length<1||frame.length>maxFrame)throw new IllegalArgumentException("SPOOL_FRAME_SIZE");
        try {
            long offset=file.length();file.seek(offset);file.writeInt(frame.length);file.write(digest(frame));file.write(frame);
            file.getChannel().force(true);
            long index=offsets.size();offsets.add(offset);notifyAll();return index;
        } catch(Exception e){failed=true;throw new IllegalStateException("SPOOL_PERSIST_FAILED:"+path,e);}
    }
    public synchronized byte[] read(long index) {
        try {return readAt(offsets.get(Math.toIntExact(index)));}
        catch(Exception e){throw new IllegalStateException("SPOOL_READ_OR_INTEGRITY_FAILED:"+path,e);}
    }
    private byte[] readAt(long position)throws Exception {
        file.seek(position);int size=file.readInt();if(size<1||size>maxFrame)throw new IOException("SPOOL_FRAME_SIZE");
        byte[] expected=new byte[32],frame=new byte[size];file.readFully(expected);file.readFully(frame);
        if(!MessageDigest.isEqual(expected,digest(frame)))throw new IOException("SPOOL_HASH_MISMATCH");return frame;
    }
    private static byte[] digest(byte[] bytes)throws Exception{return MessageDigest.getInstance("SHA-256").digest(bytes);}
    public synchronized long size(){return offsets.size();}
    public Path path(){return path;}
    public synchronized void checkpoint(long next) {
        try {Path cursor=Path.of(path+".cursor"),staging=Path.of(path+".cursor.tmp");
            Files.writeString(staging,Long.toString(next),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(staging,cursor,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException e){throw new IllegalStateException("SPOOL_CHECKPOINT_FAILED",e);}
    }
    @Override public synchronized void close(){try{file.close();}catch(IOException e){throw new IllegalStateException("SPOOL_CLOSE_FAILED",e);}}
}

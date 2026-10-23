package org.jacamo.bridge.adapter;

import cartago.*;
import java.lang.reflect.Field;
import java.util.concurrent.locks.ReentrantLock;

/** Fixed, pinned framework read guard. tryLock never waits, commits, signals or
 * opens an external session. Callers must return detached data only. */
final class CartagoArtifactReadScope {
    static final String ARTIFACT_SHA="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    record Cut<T>(T value,boolean alreadyHeld) { }
    @FunctionalInterface interface Reader<A extends Artifact,T>{T read(A artifact,ArtifactDescriptor descriptor,boolean alreadyHeld)throws Exception;}
    static <A extends Artifact,T> Cut<T> read(CartagoEnvironment environment,ArtifactId identity,Class<A> type,Reader<A,T> reader)throws Exception {
        var binary=DeclaredArtifactTypeProof.readActualBinary(Artifact.class);
        if(binary==null||!ARTIFACT_SHA.equals(AdapterEvidence.digest(binary.bytes())))throw new IllegalArgumentException("CARTAGO_READ_LOCK_BINARY_UNSUPPORTED");
        Field lockField=Artifact.class.getDeclaredField("lock"),idField=Artifact.class.getDeclaredField("id");
        if(lockField.getType()!=ReentrantLock.class||idField.getType()!=ArtifactId.class||!lockField.trySetAccessible()||!idField.trySetAccessible())throw new IllegalAccessException("CARTAGO_FIXED_READ_GUARD_UNAVAILABLE");
        var workspace=environment.resolveWSP(identity.getWorkspaceId().getFullName()).getWorkspace();
        if(workspace==null||!identity.getWorkspaceId().equals(workspace.getId()))throw new IllegalStateException("CARTAGO_WORKSPACE_REPLACED");
        var descriptor=workspace.getArtifactDescriptor(identity.getName());
        if(descriptor==null||descriptor.getArtifact().getClass()!=type)throw new IllegalStateException("CARTAGO_EXACT_INSTANCE_TYPE_UNAVAILABLE");
        var actual=type.cast(descriptor.getArtifact());var lock=(ReentrantLock)lockField.get(actual);boolean held=lock.isHeldByCurrentThread();
        if(!lock.tryLock())throw new IllegalStateException("CARTAGO_COHERENT_READ_BUSY");
        try {
            if(!identity.equals(idField.get(actual))||workspace.getArtifactDescriptor(identity.getName())!=descriptor)throw new IllegalStateException("CARTAGO_ARTIFACT_REPLACED");
            T result=reader.read(actual,descriptor,held);
            if(!identity.equals(idField.get(actual))||workspace.getArtifactDescriptor(identity.getName())!=descriptor)throw new IllegalStateException("CARTAGO_ARTIFACT_REPLACED");
            return new Cut<>(result,held);
        }finally{lock.unlock();}
    }
    private CartagoArtifactReadScope(){ }
}

package org.tzi.use.plugins.jacamo.codegrounded.runtime;

public enum CheckpointType {
    SNAPSHOT, AFTER_MUTATION, OPERATION_PRE, OPERATION_POST, STREAM_BOUNDARY;
    static CheckpointType journalKind(String kind) {
        return switch(kind) {
            case "OPERATION_PRE" -> OPERATION_PRE;
            case "OPERATION_POST" -> OPERATION_POST;
            case "STREAM_BOUNDARY" -> STREAM_BOUNDARY;
            case "EVENT" -> AFTER_MUTATION;
            default -> SNAPSHOT;
        };
    }
}

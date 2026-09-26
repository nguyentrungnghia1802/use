package org.tzi.use.plugins.jacamo.bridge;

public enum BridgeClientState {
    DISCONNECTED, NEGOTIATING, MODEL_SYNC, SNAPSHOT_SYNC, LIVE, RESYNC_REQUIRED, STALE
}

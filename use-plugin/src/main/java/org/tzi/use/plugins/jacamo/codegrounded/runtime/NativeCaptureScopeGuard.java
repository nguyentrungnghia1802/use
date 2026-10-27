package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.Map;
import org.jacamo.bridge.contract.*;

/** Admission proof for scoped population claims. It neither projects data nor
 * owns runtime state. Legacy full captures remain valid before a selective
 * epoch is established; a selective epoch cannot silently downgrade. */
final class NativeCaptureScopeGuard {
    private NativeCaptureScopeGuard() { }
    static boolean snapshot(RuntimeSnapshot snapshot, ObservationCaptureScope expected, boolean required) {
        boolean scoped=metadata(snapshot.captureMetadata(),expected);
        Object sources=snapshot.captureMetadata().get("sourceEvidence");
        if(sources instanceof Map<?,?> sourceMap && sourceMap.get("cartago") instanceof Map<?,?> cartago)
            scoped=metadata(CanonicalJson.object(cartago),expected)||scoped;
        for(var fact:snapshot.facts())if(fact.values().containsKey("producerCaptureScopeHash")) {
            hash(fact.values().get("producerCaptureScopeHash"),expected);scoped=true;
        }
        if(required&&!scoped)throw failure("DOWNGRADE");
        return scoped;
    }
    static boolean event(RuntimeEvent event,ObservationCaptureScope expected,boolean required) {
        if(!event.sourceId().equals("cartago")||event.kind()==RuntimeEventKind.GAP||event.kind()==RuntimeEventKind.MODEL_REVISION_CHANGED)return false;
        if(CartagoSourceCut.KIND.equals(event.after().get("normalizedEventKind")))return snapshot(CartagoSourceCut.require(event),expected,required);
        if(event.after().containsKey("producerCaptureScopeHash")){hash(event.after().get("producerCaptureScopeHash"),expected);return true;}
        if(required)throw failure("EVENT_DOWNGRADE");
        return false;
    }
    private static boolean metadata(Map<String,Object> value,ObservationCaptureScope expected) {
        if(!value.containsKey("captureScope")&&!value.containsKey("captureScopeHash"))return false;
        try {
            var scope=ObservationCaptureScope.fromMap(CanonicalJson.object(value.get("captureScope")));
            if(!scope.hash().equals(value.get("captureScopeHash")))throw failure("RECEIPT_HASH_INVALID");
            hash(scope.hash(),expected);return true;
        }catch(NativeRuntimeProtocolException rejected){throw rejected;}
        catch(RuntimeException invalid){throw new NativeRuntimeProtocolException("NATIVE_CAPTURE_SCOPE_RECEIPT_INVALID",invalid);}
    }
    private static void hash(Object hash,ObservationCaptureScope expected) {
        if(expected==null)throw failure("UNPREPARED");
        if(!expected.hash().equals(hash))throw failure("MISMATCH");
    }
    private static NativeRuntimeProtocolException failure(String reason){return new NativeRuntimeProtocolException("NATIVE_CAPTURE_SCOPE_"+reason);}
}

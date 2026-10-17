package org.jacamo.bridge.adapter;

import java.nio.file.*;
import java.util.Map;

/** Artifact identity is distinct from the public API dialect. NPL jars publish no version manifest. */
public final class NplRuntimeProfile {
    private static final String SUPPORTED_SHA256="956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332";
    private static final Map<String,Object> EVIDENCE=inspect();
    private NplRuntimeProfile() { }
    private static Map<String,Object> inspect() {
        try {
            var path=Path.of(npl.NormInstance.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String hash=Files.isRegularFile(path)?AdapterEvidence.digest(Files.readAllBytes(path)):"UNAVAILABLE_EXPLODED_CLASSES";
            String version=hash.equals(SUPPORTED_SHA256)?"0.6.1":hash.equals("fdf43a2cec55145fd31531c5eda95fa96c6440322887d2ff3b0598da3f2f1d43")?"0.6":"UNIDENTIFIED";
            return Map.of("artifactVersion",version,"artifactSha256",hash,"apiProfile","NPL_PUBLIC_CREATED_ANNOTATION_V1",
                "admission",hash.equals(SUPPORTED_SHA256)?"SUPPORTED_PINNED_BINARY":"UNSUPPORTED_BINARY");
        } catch(Exception error) {throw new IllegalStateException("NPL_BINARY_IDENTITY_UNAVAILABLE",error);}
    }
    public static Map<String,Object> evidence() {return EVIDENCE;}
    public static void requireSupported() {
        if(!"SUPPORTED_PINNED_BINARY".equals(EVIDENCE.get("admission")))throw new IllegalStateException("NPL_BINARY_UNSUPPORTED:"+EVIDENCE);
    }
}

package org.jacamo.bridge.adapter;
import java.nio.file.*;
import org.jacamo.bridge.contract.ObservationCapabilityRequest;

/** Bounded source-package check, separate from actual loaded bytecode/provider attestation. */
final class ProviderSourcePin {
    static void require(ObservationCapabilityRequest request,Path projectRoot)throws Exception {
        boolean present=request.parameters().keySet().stream().anyMatch(k->k.equals("sourceRef")||k.equals("sourcePath")||k.equals("sourceHash"));
        if(!present)return;
        if(projectRoot==null)throw new IllegalArgumentException("CAPABILITY_SOURCE_ROOT_REQUIRED");
        String path=text(request,"sourcePath"),hash=text(request,"sourceHash");text(request,"sourceRef");
        Path relative=Path.of(path);
        if(path.contains("\\")||path.contains(":")||relative.isAbsolute()||!relative.normalize().equals(relative)||relative.startsWith("..")||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("CAPABILITY_SOURCE_PATH_OR_HASH_INVALID");
        Path root=projectRoot.toRealPath(),file=root.resolve(relative).toRealPath();
        if(!file.startsWith(root)||!Files.isRegularFile(file)||Files.size(file)>8*1024*1024)throw new IllegalArgumentException("CAPABILITY_SOURCE_OUTSIDE_PROJECT_OR_CAPACITY");
        try(var stream=Files.newInputStream(file)) {
            byte[] bytes=stream.readNBytes(8*1024*1024+1);
            if(bytes.length>8*1024*1024||!hash.equals(AdapterEvidence.digest(bytes)))throw new IllegalArgumentException("CAPABILITY_SOURCE_CHECKSUM_MISMATCH");
        }
    }
    private static String text(ObservationCapabilityRequest request,String name) {
        var value=request.parameters().get(name);if(!(value instanceof String text)||text.isBlank())throw new IllegalArgumentException("CAPABILITY_SOURCE_PARAMETER_REQUIRED:"+name);return text;
    }
    private ProviderSourcePin(){ }
}

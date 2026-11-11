package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;

/** Provider of the actual controller-owned instance, never a class guessed from its name. */
final class CartagoArtifactProviderObservation {
    private CartagoArtifactProviderObservation(){ }
    private static final ClassValue<List<Map<String,String>>> LINEAGE=new ClassValue<>() {
        @Override protected List<Map<String,String>> computeValue(Class<?> type) {
            var result=new ArrayList<Map<String,String>>();
            for(Class<?> current=type;current!=null&&Artifact.class.isAssignableFrom(current);current=current.getSuperclass()) {
                if(result.size()>=64)throw new IllegalStateException("ARTIFACT_LINEAGE_CAPACITY");
                DeclaredArtifactTypeProof.Binary binary;
                try{binary=DeclaredArtifactTypeProof.readActualBinary(current);}catch(Exception unavailable){throw new IllegalStateException("ARTIFACT_LINEAGE_BYTES_UNAVAILABLE",unavailable);}
                if(binary==null)throw new IllegalStateException("ARTIFACT_LINEAGE_BYTES_UNAVAILABLE");
                result.add(Map.of("javaClass",current.getName(),"classHash",AdapterEvidence.digest(binary.bytes())));
            }
            if(result.isEmpty()||!result.getLast().get("javaClass").equals(Artifact.class.getName()))throw new IllegalStateException("ARTIFACT_LINEAGE_ROOT_UNAVAILABLE");
            return List.copyOf(result);
        }
    };
    static Map<String,Object> capture(CartagoEnvironment environment,ArtifactId identity) {
        try {
            var workspace=environment.resolveWSP(identity.getWorkspaceId().getFullName()).getWorkspace();
            if(workspace==null||!workspace.getId().equals(identity.getWorkspaceId()))throw new IllegalStateException("WORKSPACE_REPLACED");
            var descriptor=workspace.getArtifactDescriptor(identity.getName());
            var info=environment.getController(identity.getWorkspaceId().getFullName()).getArtifactInfo(identity.getName());
            if(descriptor==null||info==null||!identity.equals(info.getId()))throw new IllegalStateException("ARTIFACT_REPLACED");
            Class<?> actual=descriptor.getArtifact().getClass();
            if(!actual.getName().equals(identity.getArtifactType()))throw new IllegalStateException("ACTUAL_CLASS_MISMATCH");
            var binary=DeclaredArtifactTypeProof.readActualBinary(actual);
            if(binary==null)throw new IllegalStateException("ACTUAL_PROVIDER_BYTES_UNAVAILABLE");
            var after=environment.getController(identity.getWorkspaceId().getFullName()).getArtifactInfo(identity.getName());
            if(workspace.getArtifactDescriptor(identity.getName())!=descriptor||after==null||!identity.equals(after.getId()))
                throw new IllegalStateException("ARTIFACT_REPLACED");
            return Map.of("version","1.0.0","status","COMPLETE","javaClass",actual.getName(),
                    "provider",actual.getProtectionDomain().getCodeSource().getLocation().toExternalForm(),
                    "resource",binary.resource().toExternalForm(),"classHash",AdapterEvidence.digest(binary.bytes()),
                    "artifactUuid",identity.getId().toString(),
                    "publicGroupRoster",MoisePublicRoster.capture(environment,identity),
                    "classHierarchy",Map.of("version","JAVA_ARTIFACT_LINEAGE/1.0","types",LINEAGE.get(actual)));
        } catch(Exception|LinkageError unavailable) {
            return Map.of("version","1.0.0","status","UNAVAILABLE","diagnostic",unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());
        }
    }
}

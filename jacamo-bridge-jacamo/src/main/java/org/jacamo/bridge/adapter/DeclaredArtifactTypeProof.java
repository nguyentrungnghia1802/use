package org.jacamo.bridge.adapter;

import java.util.*;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.*;

/** Producer-only class evidence; never creates/initializes an artifact or claims live population. */
final class DeclaredArtifactTypeProof {
    private DeclaredArtifactTypeProof() { }
    static SemanticMetadata attach(SemanticMetadata declaration,String fqcn) {
        try {
            Class<?> type=Class.forName(fqcn,false,Thread.currentThread().getContextClassLoader());
            var provider=type.getProtectionDomain().getCodeSource();var platform=cartago.Artifact.class.getProtectionDomain().getCodeSource();
            if(!cartago.Artifact.class.isAssignableFrom(type)||provider==null||platform==null
                    ||provider.getLocation()==null||platform.getLocation()==null||provider.getLocation().equals(platform.getLocation())||fqcn.startsWith("ora4mas.nopl."))return declaration;
            var resource=type.getResource("/"+fqcn.replace('.','/')+".class");if(resource==null)return declaration;
            var connection=resource.openConnection();connection.setUseCaches(false);byte[] bytes;
            try(var input=connection.getInputStream()){bytes=input.readNBytes(4*1024*1024+1);}
            if(bytes.length==0||bytes.length>4*1024*1024)return declaration;
            var proof=new SourceEvidence(EvidenceAuthority.OFFICIAL_CARTAGO_API,resource.toExternalForm(),AdapterEvidence.digest(bytes),fqcn,
                    declaration.semanticId(),0,0,"","",0,Fidelity.EXACT,CapabilityStatus.COMPLETE,
                    List.of("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS","PROVIDER:"+provider.getLocation().toExternalForm()));
            var evidence=new ArrayList<>(declaration.evidence());evidence.add(proof);
            // The consumer deliberately has no framework/application classes. Copy
            // exact annotated Method interfaces here, in the producer, without
            // fabricating an invocation or a runtime descriptor/instance.
            var methods=new ArrayList<java.lang.reflect.Method>();
            for(Class<?> current=type;current!=null&&current!=cartago.Artifact.class;current=current.getSuperclass())
                for(var method:current.getDeclaredMethods())if(method.isAnnotationPresent(cartago.OPERATION.class)&&!method.isVarArgs()&&!java.lang.reflect.Modifier.isStatic(method.getModifiers()))methods.add(method);
            methods.sort(Comparator.comparing(java.lang.reflect.Method::toGenericString));
            for(var method:methods) {
                Class<?> declaring=method.getDeclaringClass();var methodResource=declaring.getResource("/"+declaring.getName().replace('.','/')+".class");if(methodResource==null)continue;
                var methodConnection=methodResource.openConnection();methodConnection.setUseCaches(false);byte[] methodBytes;
                try(var input=methodConnection.getInputStream()){methodBytes=input.readNBytes(4*1024*1024+1);}if(methodBytes.length>4*1024*1024)continue;
                String signature=new String(org.jacamo.bridge.contract.CanonicalJson.encode(List.of(declaring.getName(),method.getName(),Arrays.stream(method.getParameterTypes()).map(Class::getName).toList(),method.getReturnType().getName())),java.nio.charset.StandardCharsets.UTF_8);
                evidence.add(new SourceEvidence(EvidenceAuthority.OFFICIAL_CARTAGO_API,methodResource.toExternalForm(),AdapterEvidence.digest(methodBytes),declaring.getName(),
                        declaration.semanticId(),0,0,"","",0,Fidelity.EXACT,CapabilityStatus.COMPLETE,
                        List.of("DECLARED_CARTAGO_ARTIFACT_OPERATION","OWNER:"+fqcn,"SIGNATURE:"+signature)));
            }
            return new SemanticMetadata(declaration.semanticId(),declaration.sourceKind(),declaration.sourceJavaFqcn(),declaration.evidenceAuthority(),
                    declaration.fidelity(),declaration.capabilityStatus(),evidence,declaration.diagnostics());
        }catch(Exception|LinkageError unavailable){return declaration;}
    }
}

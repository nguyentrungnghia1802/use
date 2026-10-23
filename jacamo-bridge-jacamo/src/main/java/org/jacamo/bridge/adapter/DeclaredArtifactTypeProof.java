package org.jacamo.bridge.adapter;

import java.util.*;
import java.net.*;
import java.nio.file.*;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.*;

/** Producer-only class evidence; never creates/initializes an artifact or claims live population. */
final class DeclaredArtifactTypeProof {
    private DeclaredArtifactTypeProof() { }
    static SemanticMetadata attach(SemanticMetadata declaration,String fqcn) {
        return attach(declaration,fqcn,false);
    }
    /** A parsed class reference is not a JCM declaration or proof of creation. */
    static SemanticMetadata attachAction(SemanticMetadata action,jason.asSyntax.Term source) {
        if(!(source instanceof jason.asSyntax.Structure term)||!action.sourceKind().equals("JASON_EXTERNAL_ACTION")||action.evidenceAuthority()!=EvidenceAuthority.OFFICIAL_JASON_API
                ||!term.getFunctor().equals("makeArtifact")||term.getArity()!=4
                ||!(term.getTerm(1) instanceof jason.asSyntax.StringTerm literal))return action;
        return attach(action,literal.getString(),true);
    }
    private static SemanticMetadata attach(SemanticMetadata declaration,String fqcn,boolean actionReference) {
        try {
            Class<?> type=Class.forName(fqcn,false,Thread.currentThread().getContextClassLoader());
            var provider=type.getProtectionDomain().getCodeSource();var platform=cartago.Artifact.class.getProtectionDomain().getCodeSource();
            if(!cartago.Artifact.class.isAssignableFrom(type)||provider==null||platform==null
                    ||provider.getLocation()==null||platform.getLocation()==null||provider.getLocation().equals(platform.getLocation())||fqcn.startsWith("ora4mas.nopl."))return declaration;
            var binary=readBinary(type);if(binary==null)return declaration;
            String prefix=actionReference?"SOURCE_ACTION_":"DECLARED_";
            var proofTags=new ArrayList<>(List.of(prefix+"CARTAGO_ARTIFACT_APPLICATION_CLASS","PROVIDER:"+provider.getLocation().toExternalForm()));
            if(actionReference)proofTags.add("CLASS_REFERENCE:makeArtifact/4:1:"+fqcn);
            var proof=new SourceEvidence(EvidenceAuthority.OFFICIAL_CARTAGO_API,binary.resource().toExternalForm(),AdapterEvidence.digest(binary.bytes()),fqcn,
                    declaration.semanticId(),0,0,"","",0,Fidelity.EXACT,CapabilityStatus.COMPLETE,
                    proofTags);
            var evidence=new ArrayList<>(declaration.evidence());evidence.add(proof);
            // The consumer deliberately has no framework/application classes. Copy
            // exact annotated Method interfaces here, in the producer, without
            // fabricating an invocation or a runtime descriptor/instance.
            var methods=new ArrayList<java.lang.reflect.Method>();
            for(Class<?> current=type;current!=null&&current!=cartago.Artifact.class;current=current.getSuperclass())
                for(var method:current.getDeclaredMethods())if(method.isAnnotationPresent(cartago.OPERATION.class)&&!method.isVarArgs()&&!java.lang.reflect.Modifier.isStatic(method.getModifiers()))methods.add(method);
            methods.sort(Comparator.comparing(java.lang.reflect.Method::toGenericString));
            for(var method:methods) {
                Class<?> declaring=method.getDeclaringClass();var methodBinary=readBinary(declaring);if(methodBinary==null)return declaration;
                String signature=new String(org.jacamo.bridge.contract.CanonicalJson.encode(List.of(declaring.getName(),method.getName(),Arrays.stream(method.getParameterTypes()).map(Class::getName).toList(),method.getReturnType().getName())),java.nio.charset.StandardCharsets.UTF_8);
                evidence.add(new SourceEvidence(EvidenceAuthority.OFFICIAL_CARTAGO_API,methodBinary.resource().toExternalForm(),AdapterEvidence.digest(methodBinary.bytes()),declaring.getName(),
                        declaration.semanticId(),0,0,"","",0,Fidelity.EXACT,CapabilityStatus.COMPLETE,
                        List.of(prefix+"CARTAGO_ARTIFACT_OPERATION","OWNER:"+fqcn,"SIGNATURE:"+signature)));
            }
            return new SemanticMetadata(declaration.semanticId(),declaration.sourceKind(),declaration.sourceJavaFqcn(),declaration.evidenceAuthority(),
                    declaration.fidelity(),declaration.capabilityStatus(),evidence,declaration.diagnostics());
        }catch(Exception|LinkageError unavailable){return declaration;}
    }
    record Binary(URL resource,byte[] bytes) { }
    /** Bind bytes to the actual code provider and reject shadow resources.
     * No URL/network fallback, global cache, initialization or retained ClassLoader. */
    static Binary readBinary(Class<?> type)throws Exception {
        return readBinary(type,Thread.currentThread().getContextClassLoader());
    }
    /** An already controller-owned instance is resolved by its defining loader.
     * Framework worker threads need not inherit the application TCCL. The same
     * unique-resource and actual CodeSource checks still apply. */
    static Binary readActualBinary(Class<?> type)throws Exception {
        return readBinary(type,type.getClassLoader());
    }
    private static Binary readBinary(Class<?> type,ClassLoader loader)throws Exception {
        String name=type.getName().replace('.','/')+".class";
        var provider=type.getProtectionDomain().getCodeSource();
        if(provider==null||provider.getLocation()==null||!provider.getLocation().getProtocol().equals("file"))return null;
        if(loader==null)return null;
        var resource=type.getResource("/"+name);if(resource==null)return null;
        var urls=new LinkedHashSet<String>();for(var url:Collections.list(loader.getResources(name)))urls.add(url.toExternalForm());
        if(urls.size()!=1||!urls.contains(resource.toExternalForm()))return null;
        Path origin=Path.of(provider.getLocation().toURI()).toRealPath();
        var connection=resource.openConnection();connection.setUseCaches(false);
        if(Files.isDirectory(origin)) {
            if(!resource.getProtocol().equals("file"))return null;
            Path actual=Path.of(resource.toURI()).toRealPath();
            if(!actual.startsWith(origin)||!actual.equals(origin.resolve(name).toRealPath()))return null;
        } else {
            if(!(connection instanceof JarURLConnection jar)||!Path.of(jar.getJarFileURL().toURI()).toRealPath().equals(origin)||!jar.getEntryName().equals(name))return null;
        }
        byte[] bytes;try(var input=connection.getInputStream()){bytes=input.readNBytes(4*1024*1024+1);}
        return bytes.length==0||bytes.length>4*1024*1024?null:new Binary(resource,bytes);
    }
}

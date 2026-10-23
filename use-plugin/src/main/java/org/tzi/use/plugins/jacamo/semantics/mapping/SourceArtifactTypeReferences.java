package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.net.URI;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;

/** Validates copied producer interface evidence. No framework classes, reflection, IO or live instances. */
public final class SourceArtifactTypeReferences {
    public static final String CLASS="SOURCE_ACTION_CARTAGO_ARTIFACT_APPLICATION_CLASS";
    public static final String OPERATION="SOURCE_ACTION_CARTAGO_ARTIFACT_OPERATION";
    public record Reference(String sourceId,String javaClass,SourceEvidence classProof,List<SourceEvidence> operations) {
        public Reference {operations=List.copyOf(operations);}
    }
    private SourceArtifactTypeReferences(){ }
    /** Verified producer class evidence, shared by declarative capability binding and source views. */
    public static List<SourceEvidence> classProofs(ModelSnapshot input,String javaClass) {
        var proofs=new LinkedHashSet<SourceEvidence>();
        for(var reference:fromActions(input))if(reference.javaClass().equals(javaClass))proofs.add(reference.classProof());
        for(var declaration:input.semanticContract().artifactDeclarations())if(declaration.javaClass().equals(javaClass))
            for(var proof:declaration.metadata().evidence())if(proof.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS")) {
                requireEvidence(proof,declaration.metadata().semanticId());
                var providers=proof.diagnostics().stream().filter(s->s.startsWith("PROVIDER:")).toList();
                if(!proof.sourceJavaFqcn().equals(javaClass)||providers.size()!=1||!providerResource(providers.getFirst().substring(9),proof.sourceUri(),javaClass))
                    throw invalid("DECLARED_CLASS_PROVIDER_MISMATCH",declaration.metadata().semanticId());
                proofs.add(proof);
            }
        return List.copyOf(proofs);
    }
    public static List<Reference> fromActions(ModelSnapshot input) {
        var result=new ArrayList<Reference>();
        for(var program:input.semanticContract().jasonPrograms())for(var action:program.actions()) {
            var roots=action.metadata().evidence().stream().filter(e->e.diagnostics().contains(CLASS)).distinct().toList();
            if(roots.isEmpty())continue;
            String id=action.metadata().semanticId();
            if(roots.size()!=1||!action.kind().equals("EXTERNAL")||!action.functor().equals("makeArtifact")||action.arity()!=4
                    ||!action.metadata().sourceKind().equals("JASON_EXTERNAL_ACTION")
                    ||action.metadata().evidenceAuthority()!=EvidenceAuthority.OFFICIAL_JASON_API)throw invalid("ACTION_CARRIER_INVALID",id);
            var root=roots.getFirst();String fqcn=root.sourceJavaFqcn();
            requireEvidence(root,id);
            if(!root.diagnostics().contains("CLASS_REFERENCE:makeArtifact/4:1:"+fqcn))throw invalid("PARSED_CLASS_REFERENCE_REQUIRED",id);
            var providers=root.diagnostics().stream().filter(s->s.startsWith("PROVIDER:")).toList();
            if(providers.size()!=1||!providerResource(providers.getFirst().substring("PROVIDER:".length()),root.sourceUri(),fqcn))
                throw invalid("PROVIDER_RESOURCE_MISMATCH",id);
            var operations=new ArrayList<SourceEvidence>();var signatures=new HashMap<String,SourceEvidence>();
            for(var proof:action.metadata().evidence().stream().filter(e->e.diagnostics().contains(OPERATION)).distinct().toList()) {
                requireEvidence(proof,id);
                if(!proof.diagnostics().contains("OWNER:"+fqcn))throw invalid("METHOD_OWNER_MISMATCH",id);
                var tags=proof.diagnostics().stream().filter(s->s.startsWith("SIGNATURE:")).toList();
                if(tags.size()!=1)throw invalid("METHOD_SIGNATURE_REQUIRED",id);
                String signature=tags.getFirst().substring("SIGNATURE:".length());
                var raw=CanonicalJson.decode(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(!(raw instanceof List<?> decoded)||decoded.size()!=4||!proof.sourceJavaFqcn().equals(decoded.get(0))||!(decoded.get(1) instanceof String)
                        ||!(decoded.get(2) instanceof List<?> parameters)||parameters.stream().anyMatch(v->!(v instanceof String))
                        ||!(decoded.get(3) instanceof String))throw invalid("METHOD_SIGNATURE_INVALID",id);
                if(proof.sourceJavaFqcn().equals(fqcn)&&(!proof.sourceDigest().equals(root.sourceDigest())||!proof.sourceUri().equals(root.sourceUri())))
                    throw invalid("METHOD_HASH_OR_RESOURCE_MISMATCH",id);
                var old=signatures.putIfAbsent(signature,proof);
                if(old!=null&&!old.equals(proof))throw invalid("METHOD_SIGNATURE_CONFLICT",id);
                if(old==null)operations.add(proof);
            }
            result.add(new Reference(id,fqcn,root,operations));
        }
        return List.copyOf(result);
    }
    private static void requireEvidence(SourceEvidence proof,String id) {
        if(proof.authority()!=EvidenceAuthority.OFFICIAL_CARTAGO_API||proof.fidelity()!=Fidelity.EXACT
                ||proof.capabilityStatus()!=CapabilityStatus.COMPLETE||!proof.sourceSemanticId().equals(id)
                ||!proof.sourceDigest().matches("[0-9a-f]{64}")||proof.sourceJavaFqcn().isBlank()
                ||!(proof.sourceUri().startsWith("file:")||proof.sourceUri().startsWith("jar:file:")))
            throw invalid("INTERFACE_EVIDENCE_INVALID",id);
    }
    private static boolean providerResource(String provider,String resource,String fqcn) {
        try {
            var origin=URI.create(provider).normalize();var actual=URI.create(resource).normalize();
            if(!origin.getScheme().equals("file"))return false;
            String path=new URI(null,null,fqcn.replace('.','/')+".class",null).toASCIIString();
            if(actual.getScheme().equals("jar"))return actual.toASCIIString().equals("jar:"+origin.toASCIIString()+"!/"+path);
            var directory=URI.create(origin.toASCIIString().endsWith("/")?origin.toASCIIString():origin.toASCIIString()+"/");
            return actual.getScheme().equals("file")&&actual.toASCIIString().equals(directory.resolve(path).toASCIIString());
        }catch(Exception invalid){return false;}
    }
    private static IllegalArgumentException invalid(String code,String id){return new IllegalArgumentException("SOURCE_ARTIFACT_TYPE_REFERENCE_"+code+":"+id);}
}

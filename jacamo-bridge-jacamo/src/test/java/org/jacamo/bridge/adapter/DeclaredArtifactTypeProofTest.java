package org.jacamo.bridge.adapter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeclaredArtifactTypeProofTest {
    private static final AtomicInteger initializations=new AtomicInteger();
    public static class ProbeArtifact extends cartago.Artifact {static{initializations.incrementAndGet();}
        @cartago.OPERATION void increment() { }
        @cartago.OPERATION void varargs(String... values) { }
        void unannotated() { }
    }
    public static class FieldArtifact extends cartago.Artifact {
        static {initializations.incrementAndGet();}
        private String state;
        private int counter;
        static String shared;
    }
    @Test void copiesTypedDeclaredFieldSchemaWithoutInitializingOrReadingAnInstance() {
        var result=DeclaredArtifactTypeProof.attach(declaration(),FieldArtifact.class.getName());
        assertEquals(0,initializations.get());
        var fields=result.evidence().stream().filter(e->e.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_FIELD")).toList();
        assertEquals(2,fields.size());
        var signatures=fields.stream().flatMap(e->e.diagnostics().stream()).filter(t->t.startsWith("FIELD_SIGNATURE:")).toList();
        assertTrue(signatures.contains("FIELD_SIGNATURE:[\""+FieldArtifact.class.getName()+"\",\"state\",\"java.lang.String\"]"));
        assertTrue(signatures.contains("FIELD_SIGNATURE:[\""+FieldArtifact.class.getName()+"\",\"counter\",\"int\"]"));
        assertTrue(signatures.stream().noneMatch(t->t.contains("shared")));
        assertTrue(fields.stream().allMatch(e->e.sourceDigest().equals(result.evidence().getFirst().sourceDigest())));
    }
    private static SemanticMetadata declaration(){return new SemanticMetadata("declaration:counter","JACAMO_ARTIFACT_DECLARATION","jacamo.project.JaCaMoWorkspaceParameters",EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(),List.of());}
    @Test void exactAssignableApplicationClassCarriesByteHashWithoutInitializingIt()throws Exception {
        var input=declaration();var result=DeclaredArtifactTypeProof.attach(input,ProbeArtifact.class.getName());
        assertEquals(0,initializations.get());assertEquals(input.semanticId(),result.semanticId());assertEquals(2,result.evidence().size());
        var proof=result.evidence().getFirst();assertEquals(EvidenceAuthority.OFFICIAL_CARTAGO_API,proof.authority());assertEquals(ProbeArtifact.class.getName(),proof.sourceJavaFqcn());assertEquals(input.semanticId(),proof.sourceSemanticId());assertEquals(CapabilityStatus.COMPLETE,proof.capabilityStatus());
        assertTrue(proof.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS"));
        try(var bytes=ProbeArtifact.class.getResourceAsStream("/"+ProbeArtifact.class.getName().replace('.','/')+".class")){assertEquals(AdapterEvidence.digest(bytes.readAllBytes()),proof.sourceDigest());}
        assertEquals(List.of(),input.evidence());
        var operation=result.evidence().get(1);assertTrue(operation.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_OPERATION"));
        assertTrue(operation.diagnostics().contains("SIGNATURE:[\""+ProbeArtifact.class.getName()+"\",\"increment\",[],\"void\"]"));
        assertTrue(operation.diagnostics().contains("OWNER:"+ProbeArtifact.class.getName()));assertEquals(proof.sourceDigest(),operation.sourceDigest());
    }
    @Test void missingNonArtifactPlatformAndUnresolvableClassNeverBecomeApplicationProof() {
        var input=declaration();for(String type:List.of("missing.Counter",String.class.getName(),cartago.Artifact.class.getName()))assertSame(input,DeclaredArtifactTypeProof.attach(input,type));
        var original=Thread.currentThread().getContextClassLoader();try{Thread.currentThread().setContextClassLoader(new ClassLoader(null){});assertSame(input,DeclaredArtifactTypeProof.attach(input,ProbeArtifact.class.getName()));}finally{Thread.currentThread().setContextClassLoader(original);}
    }
}

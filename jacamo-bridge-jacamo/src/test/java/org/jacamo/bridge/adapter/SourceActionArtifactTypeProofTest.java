package org.jacamo.bridge.adapter;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.ToolProvider;
import jason.asSyntax.ASSyntax;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceActionArtifactTypeProofTest {
    private static final AtomicInteger INITIALIZED=new AtomicInteger();
    public static class ReferencedArtifact extends cartago.Artifact {
        static {INITIALIZED.incrementAndGet();}
        @cartago.OPERATION void increment(){ }
    }
    private static SemanticMetadata action(){return new SemanticMetadata("action:source","JASON_EXTERNAL_ACTION","jason.asSyntax.PlanBody",
            EvidenceAuthority.OFFICIAL_JASON_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(),List.of());}
    @Test void exactParsedClassLiteralCarriesProofWithoutBecomingADeclarationOrInstance() throws Exception {
        var metadata=action();
        var result=DeclaredArtifactTypeProof.attachAction(metadata,ASSyntax.parseLiteral("makeArtifact(id,\""+ReferencedArtifact.class.getName()+"\",[],A)"));
        assertEquals(0,INITIALIZED.get());assertEquals(metadata.semanticId(),result.semanticId());assertEquals("JASON_EXTERNAL_ACTION",result.sourceKind());
        assertEquals(2,result.evidence().size());
        assertTrue(result.evidence().getFirst().diagnostics().contains("SOURCE_ACTION_CARTAGO_ARTIFACT_APPLICATION_CLASS"));
        assertFalse(result.evidence().getFirst().diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS"));
        assertTrue(result.evidence().stream().allMatch(e->e.sourceSemanticId().equals(metadata.semanticId())));
        assertTrue(result.evidence().get(1).diagnostics().contains("SOURCE_ACTION_CARTAGO_ARTIFACT_OPERATION"));
    }
    @Test void variablesQuotedDocumentationOtherFunctorWrongProviderAndAmbiguousClassResourceAreNotProof() throws Exception {
        var metadata=action();String type=ReferencedArtifact.class.getName();
        for(String term:List.of("makeArtifact(id,Type,[],A)","print(\"makeArtifact(id, class, [], A)\")","other(id,\""+type+"\",[],A)",
                "makeArtifact(id,\"java.lang.String\",[],A)","makeArtifact(id,\"cartago.Artifact\",[],A)"))
            assertSame(metadata,DeclaredArtifactTypeProof.attachAction(metadata,ASSyntax.parseLiteral(term)));
        var original=Thread.currentThread().getContextClassLoader();String resource=type.replace('.','/')+".class";
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(original){
                @Override public Enumeration<URL> getResources(String name)throws IOException {
                    var urls=Collections.list(super.getResources(name));if(name.equals(resource))urls.add(new URL("file:/wrong-provider/"+resource));return Collections.enumeration(urls);
                }
            });
            assertSame(metadata,DeclaredArtifactTypeProof.attachAction(metadata,ASSyntax.parseLiteral("makeArtifact(id,\""+type+"\",[],A)")));
        } finally {Thread.currentThread().setContextClassLoader(original);}
    }
    static Path compilePinnedAuction()throws Exception {
        Path source=Path.of("../use-plugin/examples/auction/src/env/auction_env/AuctionArtifact.java").toRealPath();
        assertEquals("34036fc220274853e39f93682cdb22c350fe6a06739669860f2a66afe606ff4b",AdapterEvidence.digest(Files.readAllBytes(source)));
        Path classes=Files.createTempDirectory(Files.createDirectories(Path.of("target/type-proof")),"auction-");
        String classpath=String.join(File.pathSeparator,Path.of(cartago.Artifact.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                Path.of(jason.asSyntax.Atom.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        var diagnostics=new ByteArrayOutputStream();
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,diagnostics,"-proc:none","--release","21","-encoding","UTF-8","-cp",classpath,"-d",classes.toString(),source.toString()),diagnostics.toString());
        return classes;
    }
    @Test void pinnedAuctionSourcePreservesInitializationAndThreeBusinessInterfacesBeforeAnyArtifactExists() throws Exception {
        Path classes=compilePinnedAuction();
        var previous=Thread.currentThread().getContextClassLoader();
        try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},previous)) {
            Thread.currentThread().setContextClassLoader(loader);
            Path jcm=Path.of("../use-plugin/examples/auction/auction.jcm").toRealPath();
            var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
            assertTrue(snapshot.semanticContract().artifactDeclarations().isEmpty());assertTrue(snapshot.semanticContract().cartagoEnvironments().isEmpty());
            var actions=snapshot.semanticContract().jasonPrograms().stream().flatMap(p->p.actions().stream()).filter(a->a.functor().equals("makeArtifact")).toList();
            assertEquals(5,actions.size());
            for(var action:actions) {
                var methods=action.metadata().evidence().stream().filter(e->e.diagnostics().contains("SOURCE_ACTION_CARTAGO_ARTIFACT_OPERATION")).toList();
                assertEquals(4,methods.size(),"init is also annotated in the pinned source; do not hide it to fit the three OCL operation bindings");
                var signatures=methods.stream().flatMap(e->e.diagnostics().stream()).filter(s->s.startsWith("SIGNATURE:")).collect(java.util.stream.Collectors.toSet());
                assertEquals(Set.of("SIGNATURE:[\"auction_env.AuctionArtifact\",\"init\",[],\"void\"]",
                        "SIGNATURE:[\"auction_env.AuctionArtifact\",\"start\",[\"java.lang.String\"],\"void\"]",
                        "SIGNATURE:[\"auction_env.AuctionArtifact\",\"bid\",[\"double\"],\"void\"]","SIGNATURE:[\"auction_env.AuctionArtifact\",\"stop\",[],\"void\"]"),signatures);
                String expected=AdapterEvidence.digest(Files.readAllBytes(classes.resolve("auction_env/AuctionArtifact.class")));
                assertTrue(methods.stream().allMatch(e->e.sourceDigest().equals(expected)));
            }
        } finally {Thread.currentThread().setContextClassLoader(previous);}
    }
}

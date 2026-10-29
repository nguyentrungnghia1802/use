package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.uml.mm.MElementAnnotation;
import static org.junit.jupiter.api.Assertions.*;

class SourceActionArtifactReferenceTest {
    public static class ReferencedType extends cartago.Artifact {
        @cartago.OPERATION void start(String value) { }
        @cartago.OPERATION void bid(double value) { }
        @cartago.OPERATION void stop() { }
    }
    private static ModelSnapshot source(boolean actualReference)throws Exception {
        Path root=Files.createTempDirectory(Files.createDirectories(Path.of("target/action-source")),"case-").toAbsolutePath();
        Path jcm=root.resolve("case.jcm");Files.writeString(jcm,"mas source_reference { agent alice : entry.asl agent bob : entry.asl }");
        Files.writeString(root.resolve("entry.asl"),actualReference?"+!go <- makeArtifact(id,\""+ReferencedType.class.getName()+"\",[],A).":
                "+!go <- .print(\"makeArtifact(id, className, [], A)\").");
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        assertEquals(2,snapshot.semanticContract().jasonPrograms().size(),"Fixture must reach actual parsed programs before testing normalization");
        if(actualReference)assertEquals(2,snapshot.semanticContract().jasonPrograms().stream().flatMap(p->p.actions().stream())
                .filter(a->a.functor().equals("makeArtifact")&&a.metadata().evidence().stream().anyMatch(e->e.diagnostics().contains(SourceArtifactTypeReferences.CLASS))).count());
        return snapshot;
    }
    @Test void producerProvedActionReferencesCreateOneTypeAliasWithOnlyActualActionOrigins()throws Exception {
        var input=source(true);var normalized=new SourceModelNormalizer().normalize(input);
        String alias=DomainProjection.artifactId(ReferencedType.class.getName());
        assertTrue(normalized.nodes().stream().anyMatch(n->n.id().equals(alias)),"No fake JCM artifact declaration is needed for a proved source type");
        var node=normalized.require(alias);assertEquals("Artifact",node.concept());assertEquals(NormalizedSourceModel.Layer.MODEL_DEFINITION,node.layer());
        var origins=(List<?>)node.features().get("sourceDefinitions").value();
        var actions=input.semanticContract().jasonPrograms().stream().flatMap(p->p.actions().stream()).map(a->a.metadata().semanticId()).collect(java.util.stream.Collectors.toSet());
        assertEquals(2,origins.size());assertTrue(origins.stream().allMatch(o->actions.contains(CanonicalJson.object(o).get("reference"))));
        assertTrue(input.semanticContract().artifactDeclarations().isEmpty());assertTrue(input.semanticContract().cartagoEnvironments().isEmpty());
    }
    @Test void quotedDocumentationCannotDeclareAnArtifactType()throws Exception {
        var normalized=new SourceModelNormalizer().normalize(source(false));
        assertFalse(normalized.nodes().stream().anyMatch(n->n.id().equals(DomainProjection.artifactId(ReferencedType.class.getName()))));
    }
    @Test void consumerNeedsNoFrameworkClassLoaderToValidateSourceReferences()throws Exception {
        var input=source(true);var previous=Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null){});
            assertNotNull(new SourceModelNormalizer().normalize(input).require(DomainProjection.artifactId(ReferencedType.class.getName())));
        } finally {Thread.currentThread().setContextClassLoader(previous);}
    }
    @Test void wrongMethodBytesAndProviderResourceCannotBecomeTypeAuthority()throws Exception {
        var input=source(true);
        var wrongHash=mutate(input,"SOURCE_ACTION_CARTAGO_ARTIFACT_OPERATION",m->m.put("sourceDigest","0".repeat(64)));
        assertTrue(assertThrows(IllegalArgumentException.class,()->new SourceModelNormalizer().normalize(wrongHash)).getMessage().contains("METHOD_HASH"));
        var wrongProvider=mutate(input,"SOURCE_ACTION_CARTAGO_ARTIFACT_APPLICATION_CLASS",m->{
            var diagnostics=new ArrayList<Object>((List<?>)m.get("diagnostics"));diagnostics.removeIf(v->v.toString().startsWith("PROVIDER:"));
            diagnostics.add("PROVIDER:file:/wrong-provider/");m.put("diagnostics",diagnostics);
        });
        assertTrue(assertThrows(IllegalArgumentException.class,()->new SourceModelNormalizer().normalize(wrongProvider)).getMessage().contains("PROVIDER_RESOURCE"));
    }
    @Test void existingEntityAndOperationAdmissionReuseActionProofWithoutConsumerFrameworkLoading()throws Exception {
        var input=source(true);var normalized=new SourceModelNormalizer().normalize(input);
        var profile=JaCaMoMappingProfile.observedV1();String csl="csl v4.0 SourceView { entity DomainView; }";
        var entity=new SemanticBindingIndex.Binding(DomainProjection.artifactId(ReferencedType.class.getName()),SemanticBindingIndex.Kind.ENTITY,
                "DomainView","DomainView","M18","EXPLICIT_ARTIFACT_DOMAIN_VIEW",Map.of());
        var rules=new MappingRuleSet("2.0","source-view",profile.id(),profile.version(),normalized.sourceHash(),csl,"istar Empty {}",
                List.of(entity),List.of(),List.of(),Map.of("requireArtifactProofAtImport","true"));
        var model=new CslFrontend().compile(csl,"view.csl").requireModel();
        var reference=SourceArtifactTypeReferences.fromActions(input).getFirst();
        List<Object> signature=List.of(ReferencedType.class.getName(),"bid",List.of("double"),"void");
        String canonical=new String(CanonicalJson.encode(signature),java.nio.charset.StandardCharsets.UTF_8);
        var proof=reference.operations().stream().filter(e->e.diagnostics().contains("SIGNATURE:"+canonical)).findFirst().orElseThrow();
        var api=new UseModelApi("source-view");var nativeType=api.createClass("DomainView",false);
        nativeType.addAnnotation(new MElementAnnotation(DomainProjection.ANNOTATION,Map.of("javaClass64",DomainProjection.encode(ReferencedType.class.getName()))));
        var previous=Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null){});
            assertEquals(1,new JaCaMoCslMappingRules().map(input,normalized,rules,model).model().entities().size());
            var operation=DomainProjection.declaredOperation(api,"DomainView",signature,reference.sourceId(),proof);
            assertEquals(canonical,DomainProjection.decode(operation.getAnnotationValue("CartagoOperation","signature64")));
            assertEquals("PRODUCER_REFLECTED_SOURCE_ACTION_OPERATION",operation.getAnnotationValue("CslInterfaceAuthority","kind"));
            assertEquals(reference.sourceId(),DomainProjection.decode(operation.getAnnotationValue("CslInterfaceAuthority","sourceReference64")));
        } finally {Thread.currentThread().setContextClassLoader(previous);}
    }
    @SuppressWarnings("unchecked") private static ModelSnapshot mutate(ModelSnapshot input,String marker,java.util.function.Consumer<Map<String,Object>> change)throws Exception {
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(ContractPayloads.model(input)),Map.class);
        alter(tree,marker,change);return ContractPayloads.model(tree);
    }
    @SuppressWarnings("unchecked") private static void alter(Object value,String marker,java.util.function.Consumer<Map<String,Object>> change) {
        if(value instanceof List<?> list){list.forEach(v->alter(v,marker,change));return;}
        if(value instanceof Map<?,?> raw) {
            var map=(Map<String,Object>)raw;
            if(map.get("diagnostics") instanceof List<?> values&&values.contains(marker))change.accept(map);
            new ArrayList<>(map.values()).forEach(v->alter(v,marker,change));
        }
    }
}

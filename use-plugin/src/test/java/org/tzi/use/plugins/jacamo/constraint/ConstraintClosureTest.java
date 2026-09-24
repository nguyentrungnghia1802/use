package org.tzi.use.plugins.jacamo.constraint;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ConstraintClosureTest {
    @Test void nestedUnknownAndTypeErrorsNeverBecomeExactBooleanExpressions() {
        var parser=new ConstraintExpressionParser();
        var env=new TypeEnvironment(Map.of("x",Expression.ValueType.INTEGER),Map.of());
        for(String source:List.of("true & unknown(x)","missing > 0","1 & true","not 3","x > true","x / 2 > 0"))
            assertEquals(TranslationStatus.UNSUPPORTED,parser.parse(source,env).status(),source);
        assertEquals(TranslationStatus.EXACT,parser.parse("x > 0 & x <= 10",env).status());
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    @Test void distinctV2OwnersWithJavaHashCollisionsKeepDistinctConstraintIdentities() throws Exception {
        var agents=java.nio.file.Files.createDirectories(temporary.resolve("src/agt"));
        java.nio.file.Files.writeString(temporary.resolve("ids.jcm"),
                "mas ids { agent Aa:a.asl agent BB:a.asl asl-path:src/agt }");
        java.nio.file.Files.writeString(agents.resolve("a.asl"), "+!g : true <- .print(\"x\").");
        var imported=new org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter().importProject(temporary.resolve("ids.jcm"));
        assertTrue(imported.success(), imported.diagnostics().toString());
        var mapping=new org.tzi.use.plugins.jacamo.mapping.MappingLoader().loadCanonical(java.nio.file.Path.of("."));
        var plan=new org.tzi.use.plugins.jacamo.mapping.TransformationPlanner().plan(imported.model(),mapping);
        var extractor=new ConstraintExtractor();
        var constraints=extractor.extract(imported.model(),plan,Map.of());
        assertEquals(2,constraints.size());
        assertEquals(constraints.get(0).dependencies().getFirst().hashCode(),constraints.get(1).dependencies().getFirst().hashCode());
        assertEquals(2,constraints.stream().map(ConstraintSpec::id).distinct().count(),"Java hash collisions must not collapse V2 source constraints");
        assertEquals(constraints,extractor.extract(imported.model(),plan,Map.of()));
        assertTrue(constraints.stream().allMatch(c -> c.status()==TranslationStatus.UNSUPPORTED));
    }
    @Test void impureGuardArithmeticAndSignatureMismatchPreserveUnsupportedSource() throws Exception {
        var original=java.nio.file.Path.of("src/test/resources/counter-team");
        for(String body:List.of("if (value > 10) return false; return true;", "return value + 1 > 0;", "return value / 2 > 0;", "return missing(value) > 0;", "return (value = 1) > 0;")) {
            var dir=temporary.resolve("case"+Math.abs(body.hashCode()));copy(original,dir);
            var sourceFile=dir.resolve("src/env/demo/Counter.java");
            java.nio.file.Files.writeString(sourceFile,java.nio.file.Files.readString(sourceFile).replace("return value >= 0;",body));
            var data=extract(dir);
            var guard=data.constraints().stream().filter(c->c.sourceKind()==ConstraintSpec.SourceKind.CARTAGO_GUARD).findFirst().orElseThrow();
            assertEquals(TranslationStatus.UNSUPPORTED,guard.status(),body);
            var output=new org.tzi.use.plugins.jacamo.ocl.OclGenerator().generate("boundary",data.plan(),data.constraints(),List.of());
            assertFalse(output.useModel().contains("pre Guard_valid"));
            assertTrue(output.provenanceManifest().contains("NOT_EMITTED"));
            assertFalse(guard.assumptions().isEmpty());
            assertNotNull(guard.provenance().span());
        }
    }
    @Test void guardParameterNamesAreNotSilentlyReboundAndSharedGuardIdsAreDistinct() throws Exception {
        var dir=temporary.resolve("signature");copy(java.nio.file.Path.of("src/test/resources/counter-team"),dir);
        var file=dir.resolve("src/env/demo/Counter.java");
        java.nio.file.Files.writeString(file,java.nio.file.Files.readString(file).replace("boolean valid(int value) { return value >= 0; }","boolean valid(int other) { return other >= 0; }"));
        assertTrue(extract(dir).constraints().stream().filter(c->c.sourceKind()==ConstraintSpec.SourceKind.CARTAGO_GUARD).allMatch(c->c.status()==TranslationStatus.UNSUPPORTED));
        var logical=temporary.resolve("logical");copy(java.nio.file.Path.of("src/test/resources/counter-team"),logical);
        var logicalFile=logical.resolve("src/env/demo/Counter.java");
        java.nio.file.Files.writeString(logicalFile,java.nio.file.Files.readString(logicalFile).replace("return value >= 0;", "return value >= 0 && (value < 10 || value == 20);"));
        var logicalGuard=extract(logical).constraints().stream().filter(c->c.sourceKind()==ConstraintSpec.SourceKind.CARTAGO_GUARD).findFirst().orElseThrow();
        assertEquals(TranslationStatus.EXACT,logicalGuard.status());
        assertTrue(new org.tzi.use.plugins.jacamo.ocl.OclGenerator().render(logicalGuard.expression()).contains(" and "));
        var shared=temporary.resolve("shared");copy(java.nio.file.Path.of("src/test/resources/counter-team"),shared);
        var sharedFile=shared.resolve("src/env/demo/Counter.java");
        java.nio.file.Files.writeString(sharedFile,java.nio.file.Files.readString(sharedFile).replace("    @GUARD", "    @OPERATION(guard=\"valid\") public void another(int value) { }\n    @GUARD"));
        var guards=extract(shared).constraints().stream().filter(c->c.sourceKind()==ConstraintSpec.SourceKind.CARTAGO_GUARD).toList();
        assertEquals(2,guards.size());assertEquals(2,guards.stream().map(ConstraintSpec::id).distinct().count());
        java.nio.file.Files.writeString(sharedFile,java.nio.file.Files.readString(sharedFile).replace("int value", "Integer value"));
        assertTrue(extract(shared).constraints().stream().filter(c->c.sourceKind()==ConstraintSpec.SourceKind.CARTAGO_GUARD).allMatch(c->c.status()==TranslationStatus.UNSUPPORTED));
        var parser=new ConstraintExpressionParser();
        assertEquals(TranslationStatus.UNSUPPORTED,parser.parse("x > 0",new TypeEnvironment(Map.of("x",Expression.ValueType.INTEGER),Map.of("x",new TypeEnvironment.PropertyBinding("self.x",Expression.ValueType.INTEGER)))).status());
    }
    @Test void unapprovedLossyAndSoundSubsetNeverEmitButRemainInManifest() {
        var data=extract(java.nio.file.Path.of("src/test/resources/counter-team"));
        var exact=data.constraints().stream().filter(c->c.status()==TranslationStatus.EXACT).findFirst().orElseThrow();
        for(var status:List.of(TranslationStatus.LOSSY,TranslationStatus.SOUND_SUBSET,TranslationStatus.UNSUPPORTED)) {
            var c=new ConstraintSpec("boundary-"+status,exact.sourceKind(),exact.kind(),exact.contextClass(),exact.operationName(),exact.name(),exact.expression(),exact.expectedType(),status,exact.provenance(),List.of("No approved preservation contract"),exact.dependencies());
            var generated=new org.tzi.use.plugins.jacamo.ocl.OclGenerator().generate("boundary",data.plan(),List.of(c),List.of());
            assertTrue(generated.emitted().isEmpty());assertTrue(generated.provenanceManifest().contains(status.name()));
            assertTrue(generated.provenanceManifest().contains(exact.provenance().sourceHash()));
        }
    }
    @Test void obsoleteV1OclContextFailsAgainstActiveV2WithContextDiagnostic() {
        var data=extract(java.nio.file.Path.of("src/test/resources/counter-team"));
        var model=new org.tzi.use.plugins.jacamo.ocl.OclGenerator().generate("legacy",data.plan(),data.constraints(),List.of()).useModel();
        var error=assertThrows(org.tzi.use.plugins.jacamo.materialization.MaterializationException.class, () ->
                new org.tzi.use.plugins.jacamo.materialization.DirectUseBackend().materialize(
                        new org.tzi.use.plugins.jacamo.materialization.TextBackend.GeneratedArtifacts(
                                model+"\ncontext ExternalAction inv obsolete: true\n", ""),
                        new org.tzi.use.plugins.jacamo.materialization.InstancePlan(List.of(),List.of(),List.of())));
        assertEquals("USE_MODEL_INVALID",error.code());
        assertTrue(error.getMessage().contains("ExternalAction"),error.getMessage());
    }
    private Data extract(java.nio.file.Path directory) {
        var semantic=new org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter().importProject(directory.resolve("counter-team.jcm")).model();
        var mapping=new org.tzi.use.plugins.jacamo.mapping.MappingLoader().loadCanonical(java.nio.file.Path.of("."));
        var plan=new org.tzi.use.plugins.jacamo.mapping.TransformationPlanner().plan(semantic,mapping);
        return new Data(plan,new ConstraintExtractor().extract(semantic,plan,Map.of()));
    }
    private void copy(java.nio.file.Path source,java.nio.file.Path target) throws Exception {
        try(var paths=java.nio.file.Files.walk(source)) { for(var path:paths.toList()) {
            var destination=target.resolve(source.relativize(path));
            if(java.nio.file.Files.isDirectory(path)) java.nio.file.Files.createDirectories(destination);
            else java.nio.file.Files.copy(path,destination);
        }}
    }
    private record Data(org.tzi.use.plugins.jacamo.mapping.TransformationPlan plan,List<ConstraintSpec> constraints) { }
}

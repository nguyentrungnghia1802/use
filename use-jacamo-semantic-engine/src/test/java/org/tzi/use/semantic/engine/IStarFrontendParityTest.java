package org.tzi.use.semantic.engine;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.*;
import org.tzi.use.semantic.engine.oracle.PinnedOriginalOracle;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.istar.parser.IStarCompiler;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.semantic.engine.oracle.PinnedOriginalOracle.*;

class IStarFrontendParityTest {
    private static PinnedOriginalOracle original;
    @BeforeAll static void compileOriginal() throws Exception {original=new PinnedOriginalOracle();}
    @AfterAll static void closeOriginal() throws Exception {if(original!=null)original.close();}
    static Stream<String> admittedFixtures(){return Stream.of(
        "istar Types { agent A { goal achieve : Achieve condition {[ true ]} goal maintain : Maintain condition {[ true ]} goal sustain : Sustain condition {[ true ]} goal none : None condition {[ false ]} task step pre {[ true ]} post {[ true ]} } }",
        "istar AndModel { role R { goal parent goal a > parent goal b > parent } }",
        "istar OrModel { role R { goal parent goal a > or parent goal b > or parent } }",
        "istar DependencyModel { role A { goal requested } role B { goal supplied : Achieve condition {[ true ]} } depend A.requested -> goal promise -> B.supplied }",
        "istar ContributionModel { role R { quality q goal a > make q task t > break q } }",
        "istar Representation { role R { quality q > qualifies g resource r > needed-by t goal g task t } }",
        "istar ActorMetadata { role Base {} role Child { Child is-a Base } agent Person { Person participates-in Child } }",
        "istar LegacyClause { role R { goal g ocl: true; task t ocl: true; } }",
        "istar Invalid { role R { goal parent goal a > parent } }",
        "istar Invalid { role R { goal a > or b goal b > or a } }",
        "istar Invalid { role A { goal g } role B { goal g } }",
        "istar Invalid { role R { goal g condition {[ true ]} condition {[ false ]} } }",
        "istar Invalid { role R { task t pre {[ true ]} pre {[ false ]} } }",
        "istar Invalid { role R { goal parent : Maintain goal a : Achieve > parent goal b : Maintain > parent } }",
        "istar Invalid { role A { goal a } role B { goal b > or a } }",
        "istar Invalid { role A { goal a } role B { goal b } depend A.a -> task promise -> Missing.b }",
        "istar StrictDerived { role A { goal requested : Achieve } role B { goal supplied : Achieve task one > supplied task two > supplied } depend A.requested -> goal promise -> B.supplied }"
    );}
    @ParameterizedTest @MethodSource("admittedFixtures")
    void originalAstModelAndValidatorParityInAdmittedIntersection(String source) throws Exception {
        var migrated=IStarCompiler.compile(source);var reference=original.istar(source);
        assertEquals(property(reference,"ok"),migrated.ok());
        var referenceDiagnostics=canonicalIStarDiagnostics((List<?>)property(reference,"errors"));
        assertEquals(referenceDiagnostics,canonicalIStarDiagnostics(migrated.errors()));
        assertEquals(normalize(property(reference,"model")),normalize(migrated.model()));
        var frontend=new IStarFrontend().compile(source,"fixture.istar");
        assertEquals(migrated.ok(),frontend.ok(),frontend.diagnostics().toString());
        if(migrated.ok())assertEquals(normalize(original.istarAst(source)),normalize(frontend.ast()));
        else assertEquals(migrated.errors(),frontend.diagnostics().stream().map(EngineDiagnostic::message).toList());
        Path evidence=Path.of("target/oracle/istar-parity");Files.createDirectories(evidence);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(evidence.resolve(hash+".input.istar"),source);
        Files.writeString(evidence.resolve(hash+".raw-original.txt"),normalize(reference));
        Files.writeString(evidence.resolve(hash+".raw-migrated.txt"),normalize(migrated));
        Files.writeString(evidence.resolve(hash+".original.txt"),property(reference,"ok")+"\n"+normalize(property(reference,"model"))+"\n"+referenceDiagnostics);
        Files.writeString(evidence.resolve(hash+".migrated.txt"),migrated.ok()+"\n"+normalize(migrated.model())+"\n"+canonicalIStarDiagnostics(migrated.errors()));
    }
    static Stream<Arguments> unsupported(){return Stream.of(
        Arguments.of("istar M { role R { goal g activation {[ true ]} } }","DECLARED_GOAL_ACTIVATION"),
        Arguments.of("istar M { role R { goal p goal g > forall Person p } }","FORALL_REFINEMENT"),
        Arguments.of("istar M { role R { goal p goal g > pick Person p } }","PICK_REFINEMENT"),
        Arguments.of("istar M { role A {} role B {} is-a A B }","TOP_LEVEL_ACTOR_ASSOCIATION_NOT_BUILT"),
        Arguments.of("istar M { role A {} agent B {} participates-in B A }","TOP_LEVEL_ACTOR_ASSOCIATION_NOT_BUILT"),
        Arguments.of("istar M { role R { goal g description \"intent\" } }","UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE"),
        Arguments.of("istar M { role R { goal g condition lang OCL {[ true ]} } }","UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE"),
        Arguments.of("istar M \"description\" { role R {} }","UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE"),
        Arguments.of("istar M { role R \"description\" {} }","UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE")
    );}
    @ParameterizedTest @MethodSource("unsupported")
    void rejectsUnimplementedSyntaxBeforeOriginalBuilder(String source,String code) {
        var result=new IStarFrontend().compile(source,"unsupported.istar");
        assertFalse(result.ok());assertNull(result.model());
        assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals(code)&&d.kind()==EngineDiagnostic.Kind.UNSUPPORTED));
        assertFalse(result.findings().isEmpty());
        assertTrue(result.findings().stream().allMatch(f->!f.span().sourceText().isEmpty()&&f.span().endOffset()>f.span().startOffset()));
        assertThrows(EngineAdmissionException.class,result::requireModel);
    }
    @Test void unicodeSourceSpansAndSyntaxLocationsComeFromActualTokens() {
        var result=new IStarFrontend().compile("// 😀 kiểm tra\nistar M { role R { goal g activation {[ true ]} } }","unicode.istar");
        var finding=result.findings().getFirst();assertEquals(2,finding.diagnostic().line());
        assertEquals("activation {[ true ]}",finding.span().sourceText());
        var syntax=new IStarFrontend().compile("istar M { role { } }","syntax.istar");
        assertFalse(syntax.ok());assertTrue(syntax.diagnostics().stream().allMatch(d->d.stage()==EngineDiagnostic.Stage.PARSE));
    }
    @Test void emptyAuthoredContractIsRejectedRatherThanSilentlyRemoved() {
        for(String source:List.of("istar M { role R { goal g condition {[ ]} } }","istar M { role R { task t ocl:; } }")) {
            var result=new IStarFrontend().compile(source,"empty.istar");assertFalse(result.ok());
            assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals("EMPTY_ISTAR_OCL_CONTRACT")));
        }
    }
    @Test void mutableOriginalModelHasExplicitOwnerFenceAndApiValidation() {
        var result=new IStarFrontend().compile("istar M { role R { goal g : Achieve condition {[ true ]} } }","owned.istar");
        var owner=result.takeOwnership();String hash=owner.hash();owner.assertUnchanged();
        ((Goal)result.model().findElement("g").orElseThrow()).setCondition(new IStarOclConstraint("false"));
        assertNotEquals(hash,OwnedGoalModel.fingerprint(result.model()));
        assertThrows(IllegalStateException.class,owner::assertUnchanged);
        var invalid=new GoalModel("DuplicateActors");invalid.addActor(new Role("R",List.of(),List.of(),List.of(),List.of(),List.of(),List.of()));
        invalid.addActor(new Role("R",List.of(),List.of(),List.of(),List.of(),List.of(),List.of()));
        assertFalse(new IStarFrontend().validate(invalid,"<api>").ok());
        var stale=new IStarFrontend().compile("istar Stale { role R { goal g } }","stale.istar").requireModel();
        ((Goal)stale.findElement("g").orElseThrow()).setId("changed");
        assertFalse(new IStarFrontend().validate(stale,"<api>").ok());
    }
    @Test void activationGraphAndContextResolutionUseOriginalRealModel() {
        var model=new IStarFrontend().compile("istar Demand { role A { goal requester } role B { goal supplier } depend A.requester -> goal promise -> B.supplier }","demand.istar").requireModel();
        assertTrue(GoalActivationGraph.of(model).isRoot("requester"));
        assertFalse(GoalActivationGraph.of(model).isRoot("supplier"));
        assertEquals("requester",GoalActivationGraph.of(model).parentOf("supplier").orElseThrow());
        assertEquals(List.of("B","A"),ContextResolution.of(model).contextTypesOf(model,"supplier"));
        assertEquals(1,model.getDependencies().size());
        assertTrue(model.getDependencies().getFirst().dependum() instanceof Goal);
        assertFalse(model.allElements().containsKey("promise"));
    }
}

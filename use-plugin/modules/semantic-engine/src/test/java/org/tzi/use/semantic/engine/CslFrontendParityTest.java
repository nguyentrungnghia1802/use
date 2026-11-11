package org.tzi.use.semantic.engine;

import java.util.*;
import java.util.stream.Stream;
import java.nio.file.*;
import java.security.MessageDigest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.oracle.PinnedOriginalOracle;
import org.vnu.sme.goal.dsl.csl.mm.*;
import org.vnu.sme.goal.dsl.csl.parser.CslCompiler;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.semantic.engine.oracle.PinnedOriginalOracle.*;

class CslFrontendParityTest {
    private static PinnedOriginalOracle original;
    @BeforeAll static void compileOriginal() throws Exception { original = new PinnedOriginalOracle(); }
    @AfterAll static void closeOriginal() throws Exception { if (original != null) original.close(); }
    static Stream<String> fixtures() {
        return Stream.of(
            "csl v4.0 Full { enum Status { DRAFT, ACTIVE } datatype Money; entity Base { id : String required; } entity Counter extends Base { p : Integer mutable default 0; state : Status optional mutable default DRAFT; weight : Real default 1.5; enabled : Boolean default true; } orgContext Team { title : String optional; role Worker { score : Integer; } orgContext Sub { role Reviewer extends Worker; entity Note; } Worker compatible Reviewer; } association Owns { Counter [1] role counter; Note [*] role notes; } context Counter inv Nonnegative: self.p >= 0; }",
            "csl v4.0 Forward { entity Child extends Parent; entity Parent; }",
            "csl v4.0 Nary { entity A; entity B; role R; association Triple { A [*] role a; B [0..1] role b; R [*] role r; } }",
            "csl v4.0 Composition { orgContext C {} entity E; composition Owns { C [1] role owner; E [*] role parts; } }",
            "csl v4.0 Bad { group Team { Worker [1]; } }",
            "csl v4.0 Bad { entity A extends Missing; }",
            "csl v4.0 Bad { entity A extends B; entity B extends A; }",
            "csl v4.0 Bad { entity A; role R extends A; }",
            "csl v4.0 Bad { entity A { x : Missing; } }",
            "csl v4.0 Bad { entity A { x : Integer; x : Integer; } }",
            "csl v4.0 Bad { entity A; entity B; association X { A [2..1]; B [*]; } }",
            "csl v4.0 Bad { role R; R compatible R; }",
            "csl v4.0 Bad { enum State { A, B } entity E { s : State default Missing; } }",
            "csl v4.0 Bad { entity A; entity A; }",
            "csl v4.0 Bad { enum A { X } entity A; }",
            "csl v4.0 Bad { entity A { x : Integer; } entity B extends A { x : Integer; } }",
            "csl v4.0 Bad { entity E; role A; role B; association Invalid { E [*]; A [*]; B [*]; } }",
            "csl v4.0 SyntaxOnly { entity A; context A inv LaterTypeError: self.unknown = 1; }");
    }
    @ParameterizedTest @MethodSource("fixtures")
    void parserAstModelAndValidatorEqualIndependentOriginal(String source) throws Exception {
        var migrated = CslCompiler.compile(source, "fixture.csl");
        var reference = original.csl(source, "fixture.csl");
        assertEquals(property(reference, "ok"), migrated.ok());
        assertEquals(normalize(property(reference, "errors")), normalize(migrated.errors()));
        assertEquals(normalize(property(reference, "ast")), normalize(migrated.ast()));
        assertEquals(normalize(property(reference, "model")), normalize(migrated.model()));
        Path evidence = Path.of("target/oracle/csl-parity"); Files.createDirectories(evidence);
        String fixtureHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(evidence.resolve(fixtureHash + ".input.csl"), source);
        Files.writeString(evidence.resolve(fixtureHash + ".original.txt"), normalize(reference));
        Files.writeString(evidence.resolve(fixtureHash + ".migrated.txt"), normalize(migrated));
        var frontend = new CslFrontend().compile(source, "fixture.csl");
        assertEquals(migrated.ok(), frontend.ok());
        if (migrated.ok()) assertEquals(normalize(migrated.model()), normalize(frontend.requireModel()));
        else assertFalse(frontend.diagnostics().isEmpty());
    }
    @Test void canonicalModelPreservesModifiersNestingAndOrderedEnds() {
        var model = new CslFrontend().compile(fixtures().findFirst().orElseThrow(), "full.csl").requireModel();
        var counter = model.findEntity("Counter").orElseThrow();
        assertEquals("Base", counter.specializes().orElseThrow());
        assertEquals("0", counter.attributes().getFirst().defaultValue().orElseThrow());
        assertTrue(counter.attributes().getFirst().mutable());
        assertTrue(counter.attributes().get(1).optional());
        assertEquals(List.of("Counter", "Note"), model.relations().getFirst().endpoints().stream().map(CslEndpoint::type).toList());
        assertEquals(2, model.orgContexts().size());
        assertEquals(1, model.compatibilities().size());
        assertEquals(1, model.namedDataTypes().size());
    }
    @Test void programmaticModelsCannotBypassValidatorOrVersionAdmission() {
        var invalid = new CslModel("v4.0", "Bad", List.of(), List.of(new CslEntity("X", List.of()), new CslEntity("X", List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertFalse(new CslFrontend().validate(invalid, "<api>").ok());
        var legacy = new CslFrontend().compile("csl v2.0 Legacy { entity X; }", "legacy.csl");
        assertFalse(legacy.ok());
        assertTrue(legacy.diagnostics().stream().anyMatch(d -> d.code().equals("CSL_VERSION_NOT_ADMITTED")));
        assertThrows(IllegalArgumentException.class, legacy::requireModel);
    }
    @Test void syntaxOnlyOclIsNotAdvertisedAsNativeDomainTypechecking() throws Exception {
        String source = "csl v4.0 A { entity E; context E inv TypeLater: self.unknown = 1; }";
        var result = new CslFrontend().compile(source, "ocl.csl");
        assertTrue(result.ok());
        // Original visitor reconstructs the OCL token stream with spaces. Preserve
        // that exact original model, rather than imposing raw input formatting.
        assertEquals(normalize(property(original.csl(source, "ocl.csl"), "model")), normalize(result.model()));
    }
    @Test void originalSyntaxDiagnosticsRetainActualSourceLocation() {
        var result = new CslFrontend().compile("csl v4.0 Bad {\n entity ;\n}", "named.csl");
        assertFalse(result.ok());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.source().equals("named.csl") && d.line() == 2));
    }
    @Test void actualUpstreamV4AndLegacyFixturesRetainHashesAndOriginalSemantics() throws Exception {
        Path directory = Path.of("src/test/resources/oracle/csl");
        var provenance = new Properties();
        try (var input = Files.newInputStream(directory.resolve("upstream-fixtures.properties"))) { provenance.load(input); }
        for (String name : List.of("proposal_review.csl", "classroom.csl")) {
            byte[] bytes = Files.readAllBytes(directory.resolve(name));
            assertEquals(provenance.getProperty(name + ".sha256"),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            String source = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            var result = CslCompiler.compile(source, name); var reference = original.csl(source, name);
            assertEquals(property(reference, "ok"), result.ok());
            assertEquals(normalize(property(reference, "errors")), normalize(result.errors()));
            assertEquals(normalize(property(reference, "ast")), normalize(result.ast()));
            assertEquals(normalize(property(reference, "model")), normalize(result.model()));
            assertEquals(name.equals("proposal_review.csl"), result.ok());
            assertEquals(result.ok(), new CslFrontend().compile(source, name).ok());
        }
    }
}

package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jason.asSyntax.PlanBody;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialJasonAdapterTest {
    @TempDir Path temporary;

    @Test void officialJasonSnapshotRetainsEveryPlanAndEveryBodyChainNodeInOrder() throws Exception {
        Path jcm = Path.of("..", "use-plugin", "src", "test", "resources", "canonical-cases", "hello-world",
                "helloworld.jcm").toAbsolutePath().normalize();
        var snapshot = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm), jcm);
        long typedBodies = snapshot.semanticContract().jasonPrograms().stream()
                .flatMap(program -> program.planLibrary().plans().stream()).mapToLong(plan -> plan.body().size()).sum();
        long compatibilityBodies = snapshot.agentDeclarations().stream()
                .filter(fact -> fact.factKind().equals("plan-body-element")).count();
        assertEquals(compatibilityBodies, typedBodies, "the typed contract must not drop any official body node");
        snapshot.semanticContract().jasonPrograms().forEach(program -> {
            var plans = program.planLibrary().plans();
            for (int planIndex = 0; planIndex < plans.size(); planIndex++) {
                var plan = plans.get(planIndex);
                assertEquals(planIndex, plan.ordinal());
                for (int bodyIndex = 0; bodyIndex < plan.body().size(); bodyIndex++) {
                    var body = plan.body().get(bodyIndex);
                    assertEquals(bodyIndex, body.ordinal());
                    assertEquals(bodyIndex + 1 < plan.body().size()
                                    ? plan.body().get(bodyIndex + 1).metadata().semanticId() : "",
                            body.nextSemanticId());
                }
            }
        });
    }

    @Test void auditedJasonBodyTypeSurfaceIsExact() {
        assertEquals(Set.of("none", "action", "internalAction", "achieve", "test", "addBel", "addBelNewFocus",
                        "addBelBegin", "addBelEnd", "delBel", "delBelNewFocus", "delAddBel", "achieveNF", "constraint"),
                Arrays.stream(PlanBody.BodyType.values()).map(Enum::name).collect(Collectors.toSet()));
    }

    @Test void nestedIncludesPreserveExactFilesDigestsSpansAndCompatibilityEvidence() throws Exception {
        runIsolatedParser("nested");
    }

    private void checkNestedIncludes() throws Exception {
        Path included = Files.createDirectories(temporary.resolve("inc")).resolve("entry.asl");
        Path nested = included.getParent().resolve("nested.asl");
        Path source = temporary.resolve("entry.asl");
        Files.writeString(nested, "+!nested <-\n .print(\"nested\").\n");
        // Jason 3.3.2 SourcePath.addParentInPath strips the leading Unix slash.
        // Exact file URIs make this provenance fixture portable without replacing
        // the official Include implementation or weakening any source/span assertion.
        Files.writeString(included, "{ include(\"" + nested.toUri() + "\") }\n+!included <- .print(\"included\").\n");
        Files.writeString(source, "{ include(\"" + included.toUri() + "\") }\n+!root <- .print(\"root\").\n");
        var result = new OfficialJasonAdapter().adapt(temporary, source, "fixture", "agent");
        var plans = result.program().planLibrary().plans();
        assertEquals(List.of("nested", "included", "root"), plans.stream().map(p -> p.trigger().literal()).toList());
        var expected = List.of(nested, included, source);
        for (int i = 0; i < plans.size(); i++) {
            var plan = plans.get(i);
            var evidence = plan.metadata().evidence().getFirst();
            String uri = "project:/" + temporary.relativize(expected.get(i)).toString().replace('\\', '/');
            assertEquals(uri, evidence.sourceUri());
            assertEquals(AdapterEvidence.digest(Files.readAllBytes(expected.get(i))), evidence.sourceDigest());
            assertTrue(evidence.startLine() > 0);
            assertTrue(evidence.endLine() >= evidence.startLine());
            assertEquals(uri, plan.trigger().metadata().evidence().getFirst().sourceUri());
            assertTrue(plan.body().stream().allMatch(b -> b.metadata().evidence().getFirst().sourceUri().equals(uri)));
            assertEquals(uri, result.facts().stream().filter(f -> f.factKind().equals("plan"))
                    .toList().get(i).evidence().getFirst().sourceUri());
        }
        assertEquals(2, plans.getFirst().metadata().evidence().getFirst().endLine());
        assertEquals("project:/entry.asl", result.program().sourceUri());
        assertEquals(result.program(), new OfficialJasonAdapter().adapt(temporary, source, "fixture", "agent").program());
    }

    @Test void packageIncludesUseExactJarEntryBytesNotRootAgentEvidence() throws Exception {
        runIsolatedParser("package");
    }

    @Test void relativeFileUriIncludesRetainExactProjectProvenance() throws Exception {
        runIsolatedParser("relative");
    }

    private void checkRelativeFileIncludes() throws Exception {
        Path agentDirectory=Files.createDirectories(temporary.resolve("src/agt"));
        Path included=Files.createDirectories(agentDirectory.resolve("inc")).resolve("common.asl");
        Path source=agentDirectory.resolve("worker.asl");
        Files.writeString(included,"shared_fact.\n+!shared <- .print(\"shared\").\n");
        Files.writeString(source,"{ include(\"inc/common.asl\") }\n+!root <- .print(\"root\").\n");
        var paths=new jason.runtime.SourcePath(); paths.addPath("src/agt");
        ((jason.asSyntax.directives.Include)jason.asSyntax.directives.DirectiveProcessor.getDirective("include")).setSourcePath(paths);
        var program=new OfficialJasonAdapter().adapt(temporary,source,"generic","worker").program();
        assertEquals(List.of("shared","root"),program.planLibrary().plans().stream().map(p->p.trigger().literal()).toList());
        var evidence=program.planLibrary().plans().getFirst().metadata().evidence().getFirst();
        assertEquals("project:/src/agt/inc/common.asl",evidence.sourceUri());
        assertEquals(AdapterEvidence.digest(Files.readAllBytes(included)),evidence.sourceDigest());
        assertEquals(evidence.sourceUri(),program.beliefs().getFirst().metadata().evidence().getFirst().sourceUri());
        assertThrows(java.io.IOException.class,()->new JasonSourceEvidence(temporary).resolve("file:absent/path/common.asl"));
    }

    private void checkPackageIncludes() throws Exception {
        Path jcm = temporary.resolve("fixture.jcm");
        Files.writeString(jcm, "mas fixture { }");
        new OfficialProjectLoader().load(jcm); // official $jacamo package registration
        Path source = temporary.resolve("agent.asl");
        Files.writeString(source, "{ include(\"$jacamo/templates/common-cartago.asl\") }\n+!own <- .print(\"own\").\n");
        var program = new OfficialJasonAdapter().adapt(temporary, source, "fixture", "agent").program();
        var imported = program.planLibrary().plans().stream().filter(p -> !p.trigger().literal().equals("own")).toList();
        assertFalse(imported.isEmpty());
        var resource = jacamo.infra.JaCaMoLauncher.class.getResource("/templates/common-cartago.asl");
        assertNotNull(resource);
        String hash;
        try (var input = resource.openStream()) { hash = AdapterEvidence.digest(input.readAllBytes()); }
        final String expectedHash = hash;
        assertTrue(imported.stream().allMatch(p -> {
            var ev = p.metadata().evidence().getFirst();
            return ev.sourceUri().startsWith("jar:file:/")
                    && ev.sourceUri().endsWith("!/templates/common-cartago.asl") && ev.sourceDigest().equals(expectedHash);
        }));
        assertTrue(imported.stream().flatMap(p -> p.body().stream()).allMatch(b ->
                b.metadata().evidence().getFirst().sourceDigest().equals(expectedHash)));
        assertEquals("project:/agent.asl", program.planLibrary().plans().getLast().metadata().evidence().getFirst().sourceUri());
    }

    private void runIsolatedParser(String mode) throws Exception {
        // Jason 3.3.2 Include.process opens streams without closing them. Isolate the
        // real parser (and all assertions) so Windows can delete fixtures after JVM exit.
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                getClass().getName(), mode, temporary.toString()).redirectErrorStream(true);
        if(mode.equals("relative")) command.directory(temporary.toFile());
        var process=command.start();
        try {
            assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "include parser child timeout");
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("INCLUDE_PROVENANCE_ASSERTIONS_PASS"), output);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    public static void main(String[] args) throws Exception {
        var probe = new OfficialJasonAdapterTest();
        probe.temporary = Path.of(args[1]);
        if (args[0].equals("nested")) probe.checkNestedIncludes();
        else if (args[0].equals("package")) probe.checkPackageIncludes();
        else if (args[0].equals("relative")) probe.checkRelativeFileIncludes();
        else throw new IllegalArgumentException("unknown include probe");
        System.out.println("INCLUDE_PROVENANCE_ASSERTIONS_PASS");
    }

    @Test void sourceResolutionNeverSearchesBasenamesOrFetchesRemoteContent() throws Exception {
        var sources = new JasonSourceEvidence(temporary);
        Files.writeString(temporary.resolve("agent.asl"), "+!root.\n");
        assertThrows(java.io.IOException.class, () -> sources.resolve("agent.asl"));
        assertThrows(java.io.IOException.class, () -> sources.resolve("https://example.invalid/agent.asl"));
        assertThrows(java.io.IOException.class, () -> sources.resolve("jar:https://example.invalid/a.jar!/agent.asl"));
        assertThrows(java.io.IOException.class, () -> sources.resolve(""));
    }

    @Test void phase2UsesOfficialJasonFactsForActionsBeliefsGoalsRulesAndSourceInfo() throws Exception {
        Path source = temporary.resolve("phase2.asl");
        Files.writeString(source, "belief(a).\n"
                + "eligible(X) :- available(X).\n"
                + "!boot.\n"
                + "+!boot <- external(a); .print(\"ok\").\n");

        var program = new OfficialJasonAdapter().adapt(temporary, source, "phase2", "agent").program();

        assertEquals(List.of("belief(a)"), program.beliefs().stream().map(value -> value.literal()).toList());
        assertEquals(List.of("boot"), program.goals().stream().map(value -> value.literal()).toList());
        assertEquals("ACHIEVE", program.goals().getFirst().goalKind());
        assertEquals(List.of("eligible(X)"), program.beliefRules().stream().map(value -> value.head()).toList());
        assertEquals(List.of("available(X)"), program.beliefRules().stream().map(value -> value.body()).toList());
        assertEquals(Set.of("EXTERNAL", "INTERNAL"), program.actions().stream()
                .map(value -> value.kind()).collect(Collectors.toSet()));
        assertTrue(program.actions().stream().allMatch(value -> program.planLibrary().plans().stream()
                .flatMap(plan -> plan.body().stream())
                .anyMatch(body -> body.metadata().semanticId().equals(value.planBodySemanticId()))));
        assertTrue(program.beliefs().stream().allMatch(value -> value.metadata().evidence().getFirst().startLine() > 0));
        assertTrue(program.beliefRules().stream().allMatch(value -> value.metadata().evidence().getFirst().endLine()
                >= value.metadata().evidence().getFirst().startLine()));
    }

    @Test void j11UsesOfficialLexerOnlyForRecursiveProvenance() throws Exception {
        Path child = temporary.resolve("child.jcm");
        Path entry = temporary.resolve("entry.jcm");
        Files.writeString(child, "mas child { }");
        Files.writeString(entry, "mas entry uses child { agent fake : should_not_be_semantically_parsed.asl }");
        var provenance = new OfficialImportGraphCollector().collect(entry, temporary);
        assertEquals(1, provenance.size());
        var imported = provenance.getFirst();
        assertEquals("child", imported.requestedPath());
        assertEquals(child.toAbsolutePath().normalize().toString(), imported.canonicalPath());
        assertEquals(CapabilityStatus.COMPLETE, imported.status());
        assertEquals(EvidenceAuthority.OFFICIAL_GENERATED_LEXER, imported.metadata().evidenceAuthority());
        assertEquals(Fidelity.PROVENANCE_ONLY, imported.metadata().fidelity());
        assertTrue(imported.metadata().diagnostics().isEmpty());
    }

    @Test void unresolvedJ11PathRemainsProvenanceOnlyAndExplicitlyUnavailable() throws Exception {
        Path entry = temporary.resolve("entry.jcm");
        Files.writeString(entry, "mas entry uses absent { }");
        var imported = new OfficialImportGraphCollector().collect(entry, temporary).getFirst();
        assertEquals(CapabilityStatus.UNAVAILABLE, imported.status());
        assertEquals(List.of("UNRESOLVED_PROVENANCE"), imported.metadata().diagnostics());
        assertTrue(imported.sourceDigest().isEmpty());
    }
}

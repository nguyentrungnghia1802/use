package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.frontend.*;
import org.vnu.sme.goal.dsl.istar.parser.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionIStarSourceTest {
    private static final AuctionCaseBundle BUNDLE = AuctionCaseBundle.read(Path.of("examples/auction"));
    private final AuctionIStarSourceAdapter adapter = new AuctionIStarSourceAdapter();
    @Test void preservesAllDescriptionsAndSemanticAstWithExactActorCellAliases() {
        var view = adapter.prepare(BUNDLE);
        String original = new String(BUNDLE.require("target-istar").bytes(), StandardCharsets.UTF_8);
        assertEquals(15, view.descriptions().size());
        assertEquals(Map.of("O_actor", "Auction", "A_actor", "Auctioneer", "P_actor", "Participant"), view.actorAliases());
        assertEquals(BUNDLE.require("target-istar").sha256(), view.originalHash());
        assertNotEquals(view.originalHash(), view.executableHash());
        assertEquals(original.lines().count(), view.executableSource().lines().count());
        var before = new IStarCapabilityAdmission().scan(original, "original");
        var after = new IStarCapabilityAdmission().scan(view.executableSource(), "view");
        assertEquals(15, before.diagnostics().size()); assertTrue(after.diagnostics().isEmpty(), after.diagnostics().toString());
        assertEquals(IStarBuildingVisitor.build(before.tree()), IStarBuildingVisitor.build(after.tree()));
        assertTrue(view.descriptions().stream().allMatch(d -> d.span().sourceText().equals("description " + d.rawLiteral())));
        assertEquals(3, IStarCompiler.compile(original).errors().size());
        assertEquals(IStarCompiler.compile(original).errors(), IStarCompiler.compile(view.executableSource()).errors());
        assertFalse(new IStarFrontend().compile(view.executableSource(), "view").ok());
        assertThrows(UnsupportedOperationException.class, () -> view.descriptions().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.actorAliases().clear());
    }
    @Test void unicodeSpansAndOclStringLiteralsArePreservedWithoutRegexSourceRewriting() {
        String original = "// 😀 source\nistar X { role R { goal g : Achieve description \"ý😀\" condition {[ 'description' = 'description' ]} } }";
        var view = adapter.prepare(original, "unicode.istar");
        assertEquals("ý😀", view.descriptions().getFirst().text());
        assertTrue(view.executableSource().contains("'description' = 'description'"));
        assertEquals(2, view.descriptions().getFirst().span().line());
        assertTrue(new IStarFrontend().compile(view.executableSource(), "unicode-view").ok());
    }
    @Test void doesNotStripConditionLanguageActivationOrOtherUnsupportedSemantics() {
        for (String source : List.of(
                "istar M { role R { goal g description \"text\" condition lang OCL {[ true ]} } }",
                "istar M { role R { goal g description \"text\" activation {[ true ]} } }",
                "istar M { role R { goal p goal g description \"text\" > forall Person p } }")) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> adapter.prepare(source, "unsupported.istar")).getMessage().contains("ISTAR_SOURCE_UNSUPPORTED"));
        }
    }
    @Test void alteredDiagramOwnershipRelationAndTaskContractAreRejected() {
        String original = new String(BUNDLE.require("target-istar").bytes(), StandardCharsets.UTF_8);
        for (String changed : List.of(original.replace("task A_start_t1", "task wrongTask"),
                original.replace("> A_start", "> A_decide"), original.replace("post {[ step.succeeded ]}", "post {[ true ]}"))) {
            var view = adapter.prepare(changed, "changed.istar");
            assertTrue(assertThrows(IllegalArgumentException.class, () -> adapter.validateDiagram(view, BUNDLE.require("input-istar").bytes())).getMessage().contains("ISTAR_DIAGRAM"));
        }
    }
    @Test void syntaxAndExplicitDiagramOwnerOrBoundaryChangesCannotBeHiddenByMetadataAdapter() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> adapter.prepare("istar Bad { role R { goal g description \"unterminated } }", "broken.istar")).getMessage().contains("ISTAR_SOURCE_SYNTAX"));
        var view = adapter.prepare(BUNDLE);
        String drawing = new String(BUNDLE.require("input-istar").bytes(), StandardCharsets.UTF_8);
        for (String changed : List.of(drawing.replace("owner=\"Auctioneer\"", "owner=\"Participant\""), drawing.replace("parent=\"A_boundary\"", "parent=\"P_boundary\""))) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> adapter.validateDiagram(view, changed.getBytes(StandardCharsets.UTF_8))).getMessage().contains("ISTAR_DIAGRAM_ELEMENT_OWNER_OR_KIND_MISMATCH"));
        }
    }
}

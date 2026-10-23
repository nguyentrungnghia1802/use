package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;

class AuctionControlledCaseTest {
    @Test void explicitInputVariantsRunRealSelectorsAndNativePreCompilationWithoutChangingCanonicalInputs()throws Exception {
        Path original=Path.of("examples/auction");var before=AuctionCaseBundle.read(original);
        for(boolean late:new boolean[]{false,true}) {
            Path work=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"E02-input-variant-");
            Path variant=AuctionControlledCase.prepare(original,work,late);var selection=AuctionStaticMappingAdapter.read(variant.resolve("auction_jacamo_to_csl_rules.xml"));
            var project=new OfficialProjectLoader().load(variant.resolve("auction.jcm"));var source=new OfficialProjectAdapter().adapt(project,variant.resolve("auction.jcm"));
            var program=new AuctionStaticMappingAdapter().map(source,selection);assertEquals(18,program.casePlan().selectors().selections().size());
            assertEquals(late?4:5,program.casePlan().selectors().require("sel-players").size());assertTrue(program.rules().id().contains("e02"));
            var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
            assertEquals(5,built.binding().model().prePostConditions().size());
            assertTrue(built.binding().model().prePostConditions().stream().filter(c->c.name().equals("Opened")).findFirst().orElseThrow().expression().toString().contains("@pre"));
            assertEquals(before.hash(),AuctionCaseBundle.read(original).hash());assertNotEquals(before.hash(),selection.bundle().hash());
            assertEquals(0,built.nativeResult().state().system().state().numObjects(),"Static declared requests do not create Auction instances");
        }
    }
}

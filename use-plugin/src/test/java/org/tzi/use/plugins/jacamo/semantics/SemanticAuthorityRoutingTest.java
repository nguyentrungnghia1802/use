package org.tzi.use.plugins.jacamo.semantics;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.CslUseBinding;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.uml.mm.MElementAnnotation;
import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.verify.conformance.semantics.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compatibility is explicit routing, never a second authority in a canonical run. */
class SemanticAuthorityRoutingTest {
    private CodeGroundedNativePipeline.CanonicalBuildResult canonical() throws Exception {
        var source=CanonicalGoalMappingTest.source();
        var program=new JaCaMoStaticMappingAdapter().map(source,JaCaMoMappingProfile.observedV1(),CanonicalGoalMappingTest.rules(source));
        return new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
    }

    @Test void mixedLegacyAndCanonicalAuthorityIsRejectedBeforeEitherMapperIsCreated() throws Exception {
        var built=canonical();
        var source=CanonicalGoalMappingTest.source();
        var legacy=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        built.binding().model().addAnnotation(new MElementAnnotation(CslUseBinding.PROFILE,Map.of("configuration64",Base64.getEncoder().encodeToString(legacy.bytes()))));
        var failure=assertThrows(IllegalArgumentException.class,()->new NativeRuntimeMutationEngine(
                built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry(),built.program()));
        assertEquals("MIXED_SEMANTIC_AUTHORITIES",failure.getMessage());
        assertEquals("MIXED_SEMANTIC_AUTHORITIES",assertThrows(IllegalArgumentException.class,()->new NativeRuntimeMutationEngine(
                built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry())).getMessage());
    }

    @Test void canonicalRouteHasOriginalM1AndOneEngineWhileLegacyRouteStillWorksIndependently() throws Exception {
        assertEquals(SemanticProjectionProfile.NATIVE_CURRENT,SemanticProjectionProfile.configured(null));
        var built=canonical();
        assertFalse(CslUseBinding.active(built.binding().model()));
        assertEquals("org.vnu.sme.goal.dsl.csl.mm.CslModel",built.program().cslModel().getClass().getName());
        assertEquals("org.vnu.sme.goal.dsl.istar.mm.GoalModel",built.program().goalModel().getClass().getName());
        try(var projector=new NativeRuntimeProjector(built,"canonical",1,built.nativeResult().source().snapshot().project().metadata().semanticId())) {
            assertNotNull(projector.coordinator().originalEngine());assertFalse(projector.mutations().cslProfile());
            var old=RuntimeVerificationCoordinator.class.getDeclaredField("semanticVerifier");old.setAccessible(true);
            assertNull(old.get(projector.coordinator()));assertSame(built.nativeResult().state().system(),projector.system());
        }
        var source=CanonicalGoalMappingTest.source();
        var config=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        var legacy=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);
        try(var projector=new NativeRuntimeProjector(legacy,"legacy",1,source.modelRevision())) {
            assertNull(projector.coordinator().originalEngine());assertTrue(projector.mutations().cslProfile());
            assertEquals("CSL_OBSERVER_V2",legacy.binding().profileId());
        }
    }

    @Test void invalidNativeContractCannotFallBackToLocalSchemaOrSubsetOcl() throws Exception {
        var source=CanonicalGoalMappingTest.source();var base=CanonicalGoalMappingTest.rules(source);
        var invalid=new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),base.expectedSourceHash(),
                base.cslSource().replace("self.p >= 0","self.propertyDoesNotExist >= 0"),base.iStarSource(),base.bindings(),base.contracts(),base.decisions(),base.options());
        var failure=assertThrows(RuntimeException.class,()->{
            var program=new JaCaMoStaticMappingAdapter().map(source,JaCaMoMappingProfile.observedV1(),invalid);
            new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        });
        assertNotNull(failure.getMessage());
        assertEquals(SemanticProjectionProfile.USE4GOAL_OBSERVED_V1,SemanticProjectionProfile.configured("USE4GOAL_OBSERVED_V1"));
    }

    @Test void documentedNoneAndTaskDifferencesRemainExplicitWithoutChangingLegacyExpectations() {
        var binding=new RequirementModel.Binding("actor","player","role","context","self","domain","episode");
        var goal=new RequirementModel.Node("g","g",RequirementModel.Kind.GOAL,RequirementModel.Temporal.NONE,binding,"false","true",RequirementModel.Refinement.NONE,List.of(),"","");
        var task=new RequirementModel.Node("t","t",RequirementModel.Kind.TASK,RequirementModel.Temporal.NONE,binding,"","true",RequirementModel.Refinement.NONE,List.of(),"","contract");
        var old=new TemporalGoalMonitor(new RequirementModel(List.of(goal,task)));
        var failed=new TemporalGoalMonitor.Input(SemanticVerdict.PASS,SemanticVerdict.FAIL,"binding","");
        var first=old.evaluate(1,Map.of("g",failed,"t",failed),false);
        assertEquals(IntentionalMarking.Status.VIOLATED,first.get("g").evaluated());
        assertEquals(IntentionalMarking.Status.VIOLATED,first.get("t").evaluated());
        assertEquals(GoalTaskStatus.PENDING,GoalMarking.initial(GoalType.NONE).update(true,false).status());
        var original=TaskMarking.initial().update(true,true,false);
        assertEquals(GoalTaskStatus.PENDING,original.status());
        original=original.update(true,false,true);assertEquals(GoalTaskStatus.FULFILLED,original.status());
        assertThrows(IllegalArgumentException.class,()->TaskMarking.fromStatus(GoalTaskStatus.VIOLATED));
        assertEquals(GoalTaskStatus.FULFILLED,original.update(true,false,false).status());
    }
}

package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystemState;
import org.vnu.sme.goal.dsl.istar.mm.Task;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.semantic.engine.use75.NativeOclOutcome.Status.*;

class CanonicalOclContractMappingTest {
    private static ModelSnapshot source;private static MappingRuleSet rules;private static final JaCaMoMappingProfile PROFILE=JaCaMoMappingProfile.observedV1();
    @BeforeAll static void load() throws Exception {source=CanonicalGoalMappingTest.source();rules=CanonicalGoalMappingTest.rules(source);}
    private CodeGroundedNativePipeline.CanonicalBuildResult build(MappingRuleSet rules){return new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules));}
    @Test void canonicalSlotsCompileOnExactNativeOperationAndRealPairState() throws Exception {
        var built=build(rules);var system=built.nativeResult().state().system();assertSame(built.binding().model(),system.model());assertEquals(8,built.contracts().contracts().size());
        var pre=built.contracts().contracts().stream().filter(c->c.id().equals("increment:pre")).findFirst().orElseThrow();var post=built.contracts().contracts().stream().filter(c->c.id().equals("increment:post")).findFirst().orElseThrow();
        assertSame(pre.specification().operation(),post.specification().operation());assertEquals("PRODUCER_REFLECTED_DECLARED_OPERATION",post.specification().operation().getAnnotationValue("CslInterfaceAuthority","kind"));
        assertTrue(post.specification().operation().getAnnotationValue("CslInterfaceAuthority","bytecodeHash").matches("[0-9a-f]{64}"));
        var task=(Task)built.program().goalModel().findElement("increment").orElseThrow();assertEquals(SemanticJson.hash(task.postconditions().getFirst().oclBody()),post.bodyHash());
        assertEquals(task.postconditions().getFirst().oclBody(),post.specification().body());assertTrue(post.specification().operation().postConditions().getFirst().expression().requiresPreState());
        var api=UseSystemApi.create(system,false);var counter=api.createObject("Counter","counter");api.setAttributeValue("counter","p","0");var before=new MSystemState("actual-pre",system.state());
        try(var backend=new NativeOclBackend(system.model(),built.program().hash(),PROFILE.engine().name())) {
            var compiled=backend.compile(post.specification());api.setAttributeValue("counter","p","1");assertEquals(PASS,backend.evaluate(compiled,UseStateReadView.pin(before,system.state()),Map.of("self",counter.value()),"").status());
            api.setAttributeValue("counter","p","2");assertEquals(FAIL,backend.evaluate(compiled,UseStateReadView.pin(before,system.state()),Map.of("self",counter.value()),"").status());
            assertEquals(SKIPPED,backend.evaluate(compiled,UseStateReadView.pin(null,system.state()),Map.of("self",counter.value()),"").status());
        }
        assertEquals(built.nativeResult().export().originalStructuralHash(),built.nativeResult().export().recompiledStructuralHash());
        var inventory=built.contracts().contracts().stream().map(c->Map.of("id",c.id(),"element",c.elementId(),"kind",c.specification().kind().name(),"self",c.specification().self().name(),"body",c.specification().body(),"bodyHash",c.bodyHash(),"signature",c.signature(),"origin",c.origin())).toList();
        Path evidence=Path.of("target/semantic-migration/canonical-ocl-inventory.json");Files.createDirectories(evidence.getParent());Files.write(evidence,CanonicalJson.encode(inventory));
    }
    @Test void differentlyAuthoredBodyCannotReplaceCanonicalOriginalTaskSlot() {
        var invalid=change("increment:post",c->contract(c,c.kind(),"false",c.operationSignature(),c.origin()));
        var error=assertThrows(MappingAdmissionException.class,()->build(invalid));assertTrue(error.diagnostics().stream().anyMatch(d->d.code().equals("CONTRACT_CANONICAL_SLOT_MISMATCH")));
    }
    @Test void producerReflectedOperationCompilesWithoutApplicationOrCartagoOnConsumerLoader() {
        var original=Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null){});
            var built=build(rules);var post=built.contracts().contracts().stream().filter(c->c.id().equals("increment:post")).findFirst().orElseThrow();
            assertTrue(post.specification().operation().postConditions().getFirst().expression().requiresPreState());assertEquals("PRODUCER_REFLECTED_DECLARED_OPERATION",post.specification().operation().getAnnotationValue("CslInterfaceAuthority","kind"));
        } finally {Thread.currentThread().setContextClassLoader(original);}
    }
    @Test void aslNplAndNameOnlyOrWrongOverloadNeverBecomeOclContracts() {
        for(String origin:List.of("ASL_SOURCE","NPL_SOURCE")) {
            var invalid=change("increment:pre",c->contract(c,c.kind(),c.body(),c.operationSignature(),origin));
            var error=assertThrows(MappingAdmissionException.class,()->build(invalid));assertTrue(error.diagnostics().stream().anyMatch(d->d.code().equals("OCL_SOURCE_LANGUAGE_ABSTRACTION_REQUIRED")));
        }
        for(List<Object> signature:List.of(List.<Object>of("increment"),List.<Object>of(CslCounterArtifact.class.getName(),"set",List.of("java.lang.String"),"void"))) {
            var invalid=change("increment:pre",c->contract(c,c.kind(),c.body(),signature,c.origin()));assertThrows(MappingAdmissionException.class,()->build(invalid));
        }
    }
    @Test void preAndPostCannotReferToDifferentNativeOperations() {
        var invalid=change("increment:post",c->contract(c,c.kind(),c.body(),List.of(CslCounterArtifact.class.getName(),"incrementTwo",List.of(),"void"),c.origin()));
        var error=assertThrows(MappingAdmissionException.class,()->build(invalid));assertTrue(error.diagnostics().stream().anyMatch(d->d.code().equals("TASK_PRE_POST_OPERATION_IDENTITY_MISMATCH")));
    }
    @Test void nativeTypeErrorKeepsAlreadyActiveHostSystem() {
        var good=build(rules);var session=new Session();new NativeUseSessionActivator().activate(session,good.nativeResult());
        String invalid=rules.iStarSource().replace("self.p >= 1","self.missing > 0");var bad=new MappingRuleSet(rules.version(),rules.id(),rules.profileId(),rules.profileVersion(),rules.expectedSourceHash(),rules.cslSource(),invalid,rules.bindings(),rules.contracts(),rules.decisions(),rules.options());
        var error=assertThrows(MappingAdmissionException.class,()->build(bad));assertTrue(error.diagnostics().stream().anyMatch(d->d.kind()==MappingDiagnostic.Kind.ERROR));assertSame(good.nativeResult().state().system(),session.system());
    }
    private MappingRuleSet change(String id,java.util.function.UnaryOperator<MappingRuleSet.Contract> change){return new MappingRuleSet(rules.version(),rules.id(),rules.profileId(),rules.profileVersion(),rules.expectedSourceHash(),rules.cslSource(),rules.iStarSource(),rules.bindings(),rules.contracts().stream().map(c->c.id().equals(id)?change.apply(c):c).toList(),rules.decisions(),rules.options());}
    private MappingRuleSet.Contract contract(MappingRuleSet.Contract c,OclContractSpec.Kind kind,String body,List<Object> signature,String origin){return new MappingRuleSet.Contract(c.id(),kind,c.contextDeclaration(),c.targetElementId(),body,signature,c.variableTypes(),origin);}
}

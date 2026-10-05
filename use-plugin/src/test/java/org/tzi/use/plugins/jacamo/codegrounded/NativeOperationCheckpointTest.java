package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.jacamo.bridge.contract.semantic.CartagoSemanticContract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class NativeOperationCheckpointTest {
    private static SemanticMetadata metadata(String id,String kind){return new SemanticMetadata(id,kind,"official Method fixture",EvidenceAuthority.OFFICIAL_CARTAGO_API,
            Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(),List.of());}
    void contracts(NativeRuntimeProjector p) {
        p.coordinator().read(()->{
            String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();var api=new org.tzi.use.api.UseModelApi(p.system().model());
            var descriptor=new OperationDescriptorSemantic(metadata("operation:set","CARTAGO_OPERATION_DESCRIPTOR"),ARTIFACT,"set/1","set",1,false,false,false,false);
            var backing=new BackingJavaOperationSemantic(metadata("backing:set","CARTAGO_BACKING_JAVA_OPERATION"),"operation:set",LiveRuntimePropertyArtifact.class.getName(),
                    "set",List.of("java.lang.String"),"void",false,"actual fixture Method");
            var operation=DomainProjection.operation(api,owner,descriptor,backing);
            condition(api,operation,"ValueRequired","p0 <> ''",true);
            condition(api,operation,"PreservedPre","self.status@pre = 'A' and self.status = p0",false);return null;
        });
    }
    // UseModelApi's string overload does not declare operation parameters. Compile with
    // USE's actual parser/context, including the native operation's typed declarations.
    private void condition(org.tzi.use.api.UseModelApi api,org.tzi.use.uml.mm.MOperation operation,
            String name,String body,boolean pre) throws Exception {
        var errors=new java.io.PrintWriter(System.err,true);
        var lexer=new org.tzi.use.parser.generator.GeneratorLexer(new org.antlr.runtime.ANTLRStringStream(body));
        var parser=new org.tzi.use.parser.generator.GeneratorParser(new org.antlr.runtime.CommonTokenStream(lexer));
        var handler=new org.tzi.use.parser.ParseErrorHandler("operation-fixture",errors);
        lexer.init(handler);parser.init(handler);
        var ast=parser.expressionOnly();assertEquals(0,handler.errorCount());
        var context=new org.tzi.use.parser.Context("operation-fixture",errors,null,new org.tzi.use.uml.mm.ModelFactory());
        context.setModel(api.getModel());context.varTable().add("self",operation.cls(),null);
        context.exprContext().push("self",operation.cls());
        for(int i=0;i<operation.paramList().size();i++) {
            var parameter=operation.paramList().varDecl(i);context.varTable().add(parameter.name(),parameter.type(),null);
        }
        context.setInsidePostCondition(!pre);api.createPrePostConditionEx(name,operation,pre,ast.gen(context));
    }
    RuntimeEvent op(String id,long seq,RuntimeEventKind kind,String value,String correlation) {
        return new RuntimeEvent(id,SESSION,1,REVISION,"cartago","cartago",seq,Instant.EPOCH,kind,RuntimeFactKind.ARTIFACT,ProjectionStatus.EVIDENCE_ONLY,
                ID,null,correlation,"",Map.of(),Map.of("phase",kind==RuntimeEventKind.STARTED?"started":"completed","artifactSemanticId",ARTIFACT,
                    "operationSignature",List.of(LiveRuntimePropertyArtifact.class.getName(),"set",List.of("java.lang.String"),"void"),
                    "argumentValues",List.of(value),"argumentTypes",List.of("java.lang.String")),new SourceWatermark("cartago",seq),Completeness.COMPLETE,List.of());
    }
    @Test void exactPrePostCorrelationPreservesAtPreOnSameActiveSystem() throws Exception {
        try(var p=projector()){contracts(p);var system=p.system();p.apply(op("enter",1,RuntimeEventKind.STARTED,"B","op-one"));
            var pre=p.coordinator().verificationSnapshot();assertEquals(CheckpointType.OPERATION_PRE,pre.metadata().checkpoint());
            assertTrue(pre.result().outcomes().stream().anyMatch(o->o.constraintId().contains("ValueRequired") && o.outcome()==VerificationOutcome.PASS));
            p.apply(delta("mutation",2,"B"));p.apply(op("exit",3,RuntimeEventKind.SUCCEEDED,"B","op-one"));
            var post=p.coordinator().verificationSnapshot();assertEquals(CheckpointType.OPERATION_POST,post.metadata().checkpoint());
            assertTrue(post.result().outcomes().stream().anyMatch(o->o.constraintId().contains("PreservedPre") && o.outcome()==VerificationOutcome.PASS));
            assertSame(system,p.system());assertEquals("'A'",pre.image().objects().get(p.mutations().objectForSemanticId(ARTIFACT).name()).attributes().get("status"));
            p.apply(op("unmatched",4,RuntimeEventKind.SUCCEEDED,"B","wrong-op"));assertTrue(p.coordinator().latest().outcomes().stream().anyMatch(o->o.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")));
        }
    }
    @Test void falsePreIsReportOnlyAndFailedOperationSkipsPost() throws Exception {
        try(var p=projector()){contracts(p);
            var port=new RuntimeControlService.Port() {
                public RuntimeControlContract.Status control(RuntimeControlContract.Request request){throw new AssertionError("REPORT_ONLY PRE must not pause");}
                public VerificationSnapshot authoritativeResync(){throw new AssertionError("No pause, no confirmation");}
                public boolean ownsRuntime(String session,long generation,String revision){return true;}
                public boolean liveAvailable(){return true;}
            };
            try(var service=new RuntimeControlService(p.coordinator(),port,List::of,SESSION,1,REVISION,false,1000)) {
            p.apply(op("enter",1,RuntimeEventKind.STARTED,"","failed-operation"));
            assertTrue(p.coordinator().latest().outcomes().stream().anyMatch(o->o.constraintId().contains("ValueRequired") && o.outcome()==VerificationOutcome.FAIL));
            var outcome=p.coordinator().latest().outcomes().stream().filter(o->o.constraintId().contains("ValueRequired")).findFirst().orElseThrow();
            var artifact=p.mutations().objectForSemanticId(ARTIFACT);
            assertEquals(artifact.name(),outcome.contextObject());assertEquals(artifact.cls().name(),outcome.contextClass());
            assertEquals(p.coordinator().latest(),RuntimeVerificationResult.fromMap(p.coordinator().latest().toMap()));
            var violation=service.violations().stream().filter(v->v.constraintId().contains("ValueRequired")).findFirst().orElseThrow();
            assertEquals(artifact.name(),violation.contextObject());
            assertEquals("'uuid-1'",violation.involvedObjects().get(artifact.name()).attributes().get("uuid"));
            assertTrue(violation.traces().stream().allMatch(t->t.line()==0));
            assertTrue(p.coordinator().constraints().runtimeRegistry().stream().filter(c->c.id().contains("ValueRequired")).allMatch(c->c.policy().enforcement()==
                    org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy.Enforcement.REPORT_ONLY));
            p.apply(op("failed",2,RuntimeEventKind.FAILED,"","failed-operation"));
            assertTrue(p.coordinator().latest().outcomes().stream().anyMatch(o->o.constraintId().contains("PreservedPre") && o.outcome()==VerificationOutcome.SKIPPED
                    && o.diagnostic().equals("OPERATION_FAILED_OR_ABORTED")));
            }
        }
    }
}

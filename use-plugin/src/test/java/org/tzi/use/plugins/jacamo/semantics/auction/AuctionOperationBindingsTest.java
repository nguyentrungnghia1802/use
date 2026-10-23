package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.mapping.SourceArtifactTypeReferences;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.semantic.engine.use75.NativeOclOutcome.Status.*;

@Timeout(45)
public class AuctionOperationBindingsTest {
    private record Prepared(Use75ModelBinding binding,List<AuctionOclBindings.Operation> operations,AuctionOclSpecificationLoader.Prepared clauses) { }
    private static Prepared prepare(AuctionLiveFixture live)throws Exception {
        var csl=new AuctionCslRuleExecutor().generate(live.specification,live.bundle,live.selectors);
        var binding=new AuctionUseModelAdapter().compile(csl.model(),live.specification);
        var loader=new AuctionOclSpecificationLoader();var source=loader.load(live.bundle);
        var operations=new AuctionOclBindings().install(live.source,live.selectors,live.specification,source,binding);
        var clauses=loader.compile(source,binding,live.specification);clauses.install(new UseModelApi(binding.model()));
        return new Prepared(binding,operations,clauses);
    }
    /** Same real source proof/preparation reused by package-local checkpoint integration tests. */
    public static Use75ModelBinding preparedModel()throws Exception {try(var live=new AuctionLiveFixture()){return prepare(live).binding();}}
    @Test void actualJavaInterfacesHaveAuthoredNamesPositionalAliasesAndUnchangedBinaryIdentity()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            var prepared=prepare(live);var owner=prepared.binding().model().getClass("Auction");
            assertEquals(Set.of("start","bid","stop"),owner.operations().stream().map(o->o.name()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(List.of("service"),owner.operation("start",false).paramNames());assertEquals(List.of("amount"),owner.operation("bid",false).paramNames());
            assertEquals(List.of(),owner.operation("stop",false).paramNames());assertEquals(5,prepared.binding().model().prePostConditions().size());
            assertTrue(owner.operation("bid",false).postConditions().isEmpty());
            for(var operation:prepared.operations()) {
                assertFalse(operation.nativeOperation().hasResultType());
                assertEquals(operation.signature(),CanonicalJson.decode(DomainProjection.decode(operation.nativeOperation().getAnnotationValue("CartagoOperation","signature64")).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                assertEquals("PRODUCER_REFLECTED_SOURCE_ACTION_OPERATION",operation.nativeOperation().getAnnotationValue("CslInterfaceAuthority","kind"));
                assertEquals("false",operation.nativeOperation().getAnnotationValue("AuctionOperationBinding","taskContractInstalled"));
            }
            assertEquals(List.of("auction_env.AuctionArtifact","start",List.of("java.lang.String"),"void"),prepared.operations().getFirst().signature());
            assertEquals(List.of("auction_env.AuctionArtifact","bid",List.of("double"),"void"),prepared.operations().get(1).signature());
            var reference=SourceArtifactTypeReferences.fromActions(live.source).getFirst();
            var proof=reference.operations().stream().filter(e->e.diagnostics().contains("SIGNATURE:[\"auction_env.AuctionArtifact\",\"start\",[\"java.lang.String\"],\"void\"]")).findFirst().orElseThrow();
            // The existing late-descriptor entry path must enrich the same named operation.
            assertSame(owner.operation("start",false),DomainProjection.declaredOperation(new UseModelApi(prepared.binding().model()),"Auction",prepared.operations().getFirst().signature(),reference.sourceId(),proof));
            assertEquals(3,owner.operations().size());assertEquals(List.of("service"),owner.operation("start",false).paramNames());
            var exported=new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter().export(prepared.binding().model());
            assertEquals(exported.originalStructuralHash(),exported.recompiledStructuralHash());
            assertEquals(List.of("service"),exported.recompiledModel().getClass("Auction").operation("start",false).paramNames());
            var previous=Thread.currentThread().getContextClassLoader();
            try {Thread.currentThread().setContextClassLoader(new ClassLoader(null){});assertEquals(3,prepare(live).operations().size());}
            finally {Thread.currentThread().setContextClassLoader(previous);}
        }
    }
    @Test void nativeClausesUseActualStringAndRealArgumentsSameSelfAndActualBeforeAfter()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            var prepared=prepare(live);var system=new MSystem(prepared.binding().model());var api=UseSystemApi.create(system,false);
            var first=live.auction("a1");var second=live.auction("a2");
            var a=api.createObject("Auction","a1");var b=api.createObject("Auction","a2");copyState(live,first,api,a);copyState(live,second,api,b);
            String service="flight 'quoted' \\ route";
            try(var backend=new NativeOclBackend(system.model(),"O02-component","AUCTION_OPERATION_INTERFACE_V1")) {
                var compiled=new HashMap<String,NativeOclBackend.Compiled>();
                for(var clause:prepared.clauses().clauses())if(clause.condition()!=null) {
                    var row=clause.specification();var operation=clause.condition().operation();
                    compiled.put(row.name(),backend.compile(new OclContractSpec(row.name(),row.kind()==AuctionOclSpecificationLoader.Kind.PRE?OclContractSpec.Kind.TASK_PRE:OclContractSpec.Kind.TASK_POST,
                            prepared.binding().classifier(row.context().owner()),operation,row.body())));
                }
                var before=new MSystemState("source-start-before",system.state());var values=Map.<String,Value>of("self",a.value(),"service",new StringValue(service));
                assertEquals(PASS,backend.evaluate(compiled.get("Idle"),UseStateReadView.pin(null,before),values,"").status());
                assertEquals(SKIPPED,backend.evaluate(compiled.get("Opened"),UseStateReadView.pin(null,before),values,"").status());
                live.actor.doAction(first,new Op("start",service),5000);copyState(live,first,api,a);
                var pair=UseStateReadView.pin(before,system.state());assertSame(before.system(),system);
                assertEquals(PASS,backend.evaluate(compiled.get("Opened"),pair,values,"").status());
                assertEquals(FAIL,backend.evaluate(compiled.get("Opened"),pair,Map.of("self",a.value(),"service",new StringValue("other-service")),"").status());
                assertEquals(FAIL,backend.evaluate(compiled.get("Opened"),pair,Map.of("self",b.value(),"service",new StringValue(service)),"").status());
                assertEquals(SKIPPED,backend.evaluate(compiled.get("Opened"),pair,Map.of("self",a.value()),"").status());
                assertEquals(ERROR,backend.evaluate(compiled.get("Opened"),pair,Map.of("self",a.value(),"service",new RealValue(1.5)),"").status());
                var bid=Map.<String,Value>of("self",a.value(),"amount",new RealValue(42.5));
                assertEquals(PASS,backend.evaluate(compiled.get("RunningForBid"),UseStateReadView.pin(null,system.state()),bid,"").status());
                live.actor.doAction(first,new Op("bid",42.5),5000);
                var completion=live.events.stream().filter(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"bid".equals(e.after().get("operation"))).findFirst().orElseThrow();
                assertEquals(prepared.operations().get(1).signature(),completion.after().get("operationSignature"));
                assertEquals("4045400000000000",CanonicalJson.object(((List<?>)completion.after().get("typedArgumentValues")).getFirst()).get("rawBits"));
                assertEquals(PASS,backend.evaluate(compiled.get("RunningForStop"),UseStateReadView.pin(null,system.state()),Map.of("self",a.value()),"").status());
                var stopBefore=new MSystemState("source-stop-before",system.state());live.actor.doAction(first,new Op("stop"),5000);copyState(live,first,api,a);
                assertEquals(PASS,backend.evaluate(compiled.get("Closed"),UseStateReadView.pin(stopBefore,system.state()),Map.of("self",a.value()),"").status());
                assertEquals(FAIL,backend.evaluate(compiled.get("RunningForBid"),UseStateReadView.pin(null,system.state()),bid,"").status());
                assertEquals(SKIPPED,backend.evaluate(compiled.get("Closed"),UseStateReadView.pin(stopBefore,system.state()),Map.of("self",a.value()),"PARTIAL_SOURCE").status());
            }
        }
    }
    @Test void wrongArityTypeSignatureExposureAndExistingHeaderFailWithoutInventingResultOrExtraMethod()throws Exception {
        try(var live=new AuctionLiveFixture()) {
            var prepared=prepare(live);var api=new UseModelApi(prepared.binding().model());
            var source=SourceArtifactTypeReferences.fromActions(live.source).getFirst();var signature=prepared.operations().getFirst().signature();
            String exact="SIGNATURE:"+new String(CanonicalJson.encode(signature),java.nio.charset.StandardCharsets.UTF_8);
            var proof=source.operations().stream().filter(p->p.diagnostics().contains(exact)).findFirst().orElseThrow();
            for(var wrong:List.of(List.<Object>of("auction_env.AuctionArtifact","start",List.of(),"void"),
                    List.<Object>of("auction_env.AuctionArtifact","start",List.of("int"),"void"),List.<Object>of("auction_env.AuctionArtifact","stop",List.of("java.lang.String"),"void"),
                    List.<Object>of("auction_env.AuctionArtifact","start",List.of("java.lang.String"),"boolean"))) {
                assertTrue(assertThrows(IllegalArgumentException.class,()->DomainProjection.declaredOperation(api,"Auction",wrong,source.sourceId(),proof)).getMessage().contains("PRODUCER_PROOF_INVALID"));
            }
            assertThrows(IllegalArgumentException.class,()->new DomainProjection.OperationExposure("start",List.of("self")));
            assertThrows(IllegalArgumentException.class,()->DomainProjection.declaredOperation(api,"Auction",signature,source.sourceId(),proof,new DomainProjection.OperationExposure("alias",List.of("service","extra"))));
            assertThrows(IllegalArgumentException.class,()->DomainProjection.declaredOperation(api,"Auction",signature,source.sourceId(),proof,new DomainProjection.OperationExposure("alias",List.of("service"))));
            assertEquals(3,api.getModel().getClass("Auction").operations().size());
            assertThrows(IllegalArgumentException.class,()->NativeOclCompilation.operationPredicate(api.getModel(),api.getModel().getClass("Auction").operation("start",false),"no-result","result = true",false));
            var csl=new AuctionCslRuleExecutor().generate(live.specification,live.bundle,live.selectors);var candidate=new AuctionUseModelAdapter().compile(csl.model(),live.specification);
            new UseModelApi(candidate.model()).createOperation("Auction","start",new String[][]{{"p0","String"}},null);
            var error=assertThrows(IllegalArgumentException.class,()->new AuctionOclBindings().install(live.source,live.selectors,live.specification,new AuctionOclSpecificationLoader().load(live.bundle),candidate));
            assertTrue(error.getMessage().contains("NATIVE_OPERATION_ALREADY_DEFINED"));assertEquals(1,candidate.model().getClass("Auction").operations().size());
        }
    }
    private static void copyState(AuctionLiveFixture live,ArtifactId identity,UseSystemApi api,MObject target)throws Exception {
        var info=live.environment.getController(identity.getWorkspaceId().getFullName()).getArtifactInfo(identity.getName());assertEquals(identity,info.getId());
        for(var property:info.getObsProperties())if(property.getName().equals("running")||property.getName().equals("task")) {
            Object value=property.getValue(0);Value nativeValue=value instanceof Boolean bool?BooleanValue.get(bool):new StringValue((String)value);
            api.setAttributeValueEx(target,target.cls().attribute(property.getName(),true),nativeValue);
        }
    }
}

package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.main.Session;

/** Second authored vocabulary on a real different MAS; no new production mapper. */
class OwnedSecondCaseIT {
    @TempDir Path selectedInputs;
    @Test @Timeout(180) void stockSpecificationUsesTheSameOwnedServiceAndGenericNativePipeline()throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize(),jcm=Path.of("examples/csl-counter/counter.jcm").toAbsolutePath();
        Path input=jcm.getParent().resolve("authored-v1"),evidence=Files.createDirectories(root.resolve("use-plugin/verification/authored-models/P09-stock-live"));
        for(String file:List.of("stock-mapping.xml","stock.ocl","stock.csl","stock.istar"))Files.copy(input.resolve(file),selectedInputs.resolve(file));
        var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",selectedInputs.resolve("stock-mapping.xml"),selectedInputs.resolve("stock.ocl"),selectedInputs.resolve("stock.csl"),selectedInputs.resolve("stock.istar"));
        var session=new Session();
        try(var facade=new DefaultJaCaMoFacade(root,session)) {
            try {
                facade.prepareOwnedProducer(jcm,selection,null);long child=facade.ownedProducerStatus().pid();
                assertNotNull(facade.semanticModels().csl().findEntity("Pallet").orElse(null));
                assertNotNull(facade.semanticModels().goals().findElement("StockSafe").orElse(null));
                var system=session.system();facade.startRuntime();
                var field=DefaultJaCaMoFacade.class.getDeclaredField("nativeWorkspace");field.setAccessible(true);Object workspace=field.get(facade);
                var member=workspace.getClass().getDeclaredField("runtimeProjector");member.setAccessible(true);
                var projector=(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector)member.get(workspace);
                var seen=new TreeSet<String>();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
                while(System.nanoTime()<end){
                    assertSame(system,session.system());
                    seen.addAll(projector.coordinator().read(()->system.state().allObjects().stream().filter(o->o.cls().name().equals("Pallet")).map(o->o.state(system.state()).attributeValue("weight").toString()).toList()));
                    if(seen.contains("-1"))break;Thread.sleep(80);
                }
                assertTrue(seen.contains("-1"),"Real Counter.set(-1) must update Pallet.weight: "+seen+" "+facade.status());
                facade.pauseRuntime("second authored case stable cut").get(20,TimeUnit.SECONDS);
                facade.resyncRuntime();facade.runFullVerification();
                var result=facade.runtimeVerificationResult();assertNotNull(result);assertSame(system,session.system());
                Files.write(evidence.resolve("result.json"),CanonicalJson.encode(result.toMap()));
                Files.write(evidence.resolve("program.json"),CanonicalJson.encode(facade.semanticProgramEvidence()));
                assertTrue(result.semantic().constraints().stream().anyMatch(c->c.verdict().name().equals("FAIL")&&c.expression().contains("weight")),"Native OCL must reject the observed negative weight");
                var core=CanonicalJson.object(result.semantic().observation().get("originalEngine"));
                assertEquals("PINNED_NATIVE_CUT",core.get("evaluated"));
                // Manual verification deliberately has advance=false. Prove the
                // original calls on admitted live history, not on a display refresh.
                var calls=new TreeSet<String>();
                for(var observed:facade.runtimeVerificationHistory())if(observed.semantic()!=null&&observed.semantic().observation().get("originalEngine") instanceof Map<?,?> rawCore)
                    for(Object context:(List<?>)CanonicalJson.object(rawCore).getOrDefault("contexts",List.of()))if(CanonicalJson.object(context).get("originalApiCalls") instanceof List<?> api)api.forEach(call->calls.add(call.toString()));
                assertTrue(calls.contains("GoalMarking.update"),"Second case must really invoke the original engine on a live advancing cut: "+calls);
                Files.write(evidence.resolve("original-api-calls.json"),CanonicalJson.encode(calls));
                Path bundle=Files.createTempDirectory(evidence,"replay-");facade.exportRuntimeReplay(bundle);
                facade.stopOwnedProducer();assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
                for(String file:List.of("stock-mapping.xml","stock.ocl","stock.csl","stock.istar"))Files.delete(selectedInputs.resolve(file));
                var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay().replay(bundle);
                assertTrue(replay.complete(),replay.diagnostics().toString());
                var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(bundle.resolve("manifest.json"))));
                assertEquals(manifest.get("finalStateHash"),replay.finalStateHash());assertEquals(manifest.get("finalResultHash"),replay.finalResultHash());
                Files.write(evidence.resolve("replay-receipt.json"),CanonicalJson.encode(Map.of("bundle",bundle.toString(),"sourceInputsDeleted",true,"ownedStagingDeleted",true,"stateHash",replay.finalStateHash(),"resultHash",replay.finalResultHash(),"checkedEntries",replay.checkedEntries())));
                Files.write(evidence.resolve("receipt.json"),CanonicalJson.encode(Map.of("inputMode","AUTHORED","usePid",ProcessHandle.current().pid(),"producerPid",child,"nativeWeights",seen,"oneAuthoritativeMSystem",true,"productionCaseJavaAdded",false)));
                Files.deleteIfExists(evidence.resolve("failure.txt"));
            }catch(Exception|AssertionError failure){Files.writeString(evidence.resolve("failure.txt"),failure+"\n"+facade.status()+"\n"+facade.ownedProducerStatus());throw failure;}
        }
    }
}

package org.tzi.use.plugins.jacamo.semantics.xml;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;

class XmlQuantifiedCoverageTest {
    @TempDir Path temp;
    @Test void compilerBoundAllInstancesIteratorUsesExactObservedReceiversAndKeepsPopulationAndValueGates()throws Exception {
        var helper=new XmlRuntimeMappingTest();helper.temp=temp;var f=helper.fixture();var engine=f.engine();
        engine.applyAuctionCut(helper.cut(f,1,Map.of("A",3),Completeness.COMPLETE),"run",1,List.of());
        var object=engine.objectForSemanticId(XmlRuntimeMappingTest.id("A").canonical());var resolver=new ConstraintCoverageResolver();
        try(var backend=new NativeOclBackend(engine.system().model(),"test","quantified")) {
            var compiled=backend.compile(new OclContractSpec("qualified",OclContractSpec.Kind.GOAL,object.cls(),null,"Counter.allInstances()->forAll(p | p.p >= 0)"));
            assertEquals("",resolver.unavailableSelf(compiled.expression(),engine,object,Map.of("cartago",Completeness.COMPLETE),false));
            assertFalse(resolver.unavailableSelf(compiled.expression(),engine,object,Map.of("cartago",Completeness.PARTIAL),false).isBlank());
            var saved=engine.savepoint();UseSystemApi.create(engine.system(),false).setAttributeValue(object.name(),"p","-5");
            assertTrue(resolver.unavailableSelf(compiled.expression(),engine,object,Map.of("cartago",Completeness.COMPLETE),false).contains("NATIVE_VALUE_DIFFERS_FROM_ACCEPTED_OBSERVATION"));engine.rollback(saved);
            assertFalse(resolver.unavailableSelf(compiled.expression(),engine,object,Map.of("cartago",Completeness.COMPLETE),true).isBlank());
            var unbound=backend.compile(new OclContractSpec("unbound",OclContractSpec.Kind.GOAL,object.cls(),null,"external.p >= 0",Map.of("external",object.cls())));
            assertFalse(resolver.unavailableSelf(unbound.expression(),engine,object,Map.of("cartago",Completeness.COMPLETE),false).isBlank(),"A free parameter is not an allInstances iterator");
            var nested=backend.compile(new OclContractSpec("nested",OclContractSpec.Kind.GOAL,object.cls(),null,"Counter.allInstances()->forAll(p | Counter.allInstances()->exists(q | p.p = q.p))"));
            assertEquals("",resolver.unavailableSelf(nested.expression(),engine,object,Map.of("cartago",Completeness.COMPLETE),false));
        }
    }
}

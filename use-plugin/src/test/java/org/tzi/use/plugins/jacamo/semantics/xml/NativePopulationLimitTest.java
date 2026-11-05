package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.junit.jupiter.api.Assertions.*;

class NativePopulationLimitTest {
    @TempDir java.nio.file.Path temp;
    @Test void realNativeRolePopulationCrossesTheAuthoredUpperBoundAndRetiresExactlyOneMember()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;var f=helper.fixture(M0RuleExecutorTest.RUNTIME,true);
        var tables=new XmlRuntimeBatchTest();tables.temp=temp;var batch=tables.batch(f,XmlRuntimeBatchTest.ROLES);
        var players=new ArrayList<Map<String,String>>();
        for(int i=0;i<301;i++) {
            String id="actual-worker-"+i;f.port().upsert(id,"AtomicAgent","Worker"+i);
            players.add(Map.of("agentSemanticId",id,"roleSemanticId","worker"));
        }
        var inv=new UseModelApi(f.engine().system().model()).createInvariant("PopulationLimit","Team","self.Worker->size() <= 300",false);
        batch.apply(f.port(),"run",0,tables.members(1,players,Completeness.COMPLETE));
        assertEquals(301,f.engine().system().state().objectsOfClass(f.engine().system().model().getClass("Worker")).size());
        var evaluator=new SemanticInvariantEvaluator();
        var prepared=evaluator.prepareScoped("limit",inv,f.engine(),Map.of("moise:organization",Completeness.COMPLETE),false,Set.of(),"");
        assertEquals(SemanticVerdict.FAIL,SemanticInvariantEvaluator.evaluateScoped(prepared,f.engine().system().state()).getLast().verdict());
        batch.apply(f.port(),"run",0,tables.members(2,players.subList(0,300),Completeness.COMPLETE));
        assertEquals(300,f.engine().system().state().objectsOfClass(f.engine().system().model().getClass("Worker")).size());
        prepared=evaluator.prepareScoped("limit",inv,f.engine(),Map.of("moise:organization",Completeness.COMPLETE),false,Set.of(),"");
        assertEquals(SemanticVerdict.PASS,SemanticInvariantEvaluator.evaluateScoped(prepared,f.engine().system().state()).getLast().verdict());
    }
}

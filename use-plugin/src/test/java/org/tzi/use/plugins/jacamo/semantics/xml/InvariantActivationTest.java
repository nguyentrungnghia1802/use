package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.junit.jupiter.api.Assertions.*;

class InvariantActivationTest {
    @TempDir Path temp;
    @Test void sourceAndNativeReadinessActivateOnceAndLaterCompleteRoleLossIsNotHidden()throws Exception {
        var helper=new M0RuleExecutorTest();helper.temp=temp;var f=helper.fixture(M0RuleExecutorTest.RUNTIME,true);
        var inv=new org.tzi.use.api.UseModelApi(f.engine().system().model()).createInvariant("OneWorker","Team","self.Worker->size() = 1",false);
        String runtime="<runtime><tables><table id=\"ready\" dataset=\"source\"><column name=\"group\" path=\"identity\" required=\"true\"/></table></tables><invariantActivations><activate id=\"activate-one\" target=\"Team::OneWorker\" tableRef=\"ready\" contextColumn=\"group\" policy=\"LATCH_ONCE_QUALIFIED/1.0\"/></invariantActivations></runtime>";
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime));
        InvariantActivation.declare(spec,f.engine().system().model(),f.engine().canonicalProgramHash());
        var activation=new InvariantActivation(spec,f.engine().system().model(),f.engine().canonicalProgramHash());
        var group=f.port().upsert("source-group","Team","group");
        for(var e:Map.of("xml:m0:program",f.engine().canonicalProgramHash(),"xml:m0:quality","COMPLETE","xml:m0:epoch","epoch-one","xml:m0:cut","cut-one","xml:m0:roles-cut","cut-one","xml:m0:roles-quality","INCOMPLETE","xml:m0:population-authority","CARTAGO_BOARD_INVENTORY_V1").entrySet())f.port().metadata(group,e.getKey(),e.getValue());
        var row=new RuntimeTables.Row("board",Map.of("group","source-group"),Completeness.COMPLETE,Map.of("source-cut","proof"),Set.of("board"));
        var table=new RuntimeTables.Result(List.of(row),Completeness.COMPLETE,List.of());
        activation.apply(f.port(),"epoch-one","cut-one",Map.of("ready",table));
        assertEquals("CONSTRAINT_AWAITING_QUALIFIED_ACTIVATION",InvariantActivation.unavailable(inv,f.engine(),group));
        f.port().metadata(group,"xml:m0:roles-quality","COMPLETE");
        activation.apply(f.port(),"epoch-one","cut-one",Map.of("ready",new RuntimeTables.Result(List.of(row),Completeness.PARTIAL,List.of())));
        assertFalse(InvariantActivation.unavailable(inv,f.engine(),group).isBlank(),"An individual row cannot close a partial source table");
        var role=f.port().upsert("role-one","Worker","worker");f.port().connect("InContext",group,role);
        activation.apply(f.port(),"epoch-one","cut-one",Map.of("ready",table));assertEquals("",InvariantActivation.unavailable(inv,f.engine(),group));
        assertEquals(SemanticVerdict.PASS,verdict(inv,f,group));
        f.port().disconnect("InContext",group,role);
        activation.apply(f.port(),"epoch-one","cut-one",Map.of("ready",new RuntimeTables.Result(List.of(),Completeness.COMPLETE,List.of())));
        assertEquals(SemanticVerdict.FAIL,verdict(inv,f,group),"Activation must persist after a complete role removal");
        f.port().metadata(group,"xml:m0:roles-quality","INCOMPLETE");assertEquals(SemanticVerdict.SKIPPED,verdict(inv,f,group));
        f.port().metadata(group,"xml:m0:epoch","epoch-two");assertTrue(InvariantActivation.unavailable(inv,f.engine(),group).startsWith("ERROR:"),"A latched proof cannot leak into another epoch");
    }
    private SemanticVerdict verdict(org.tzi.use.uml.mm.MClassInvariant inv,M0RuleExecutorTest.Fixture f,org.tzi.use.uml.sys.MObject self){
        var evaluator=new SemanticInvariantEvaluator();var prepared=evaluator.prepareScoped("one",inv,f.engine(),Map.of("cartago",Completeness.COMPLETE),false,Set.of(),"");
        return SemanticInvariantEvaluator.evaluateScoped(prepared,f.engine().system().state()).stream().filter(r->r.kind().equals("INVARIANT_BODY")).findFirst().orElseThrow().verdict();
    }
}

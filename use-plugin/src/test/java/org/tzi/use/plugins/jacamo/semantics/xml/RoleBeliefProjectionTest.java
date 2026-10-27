package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.ocl.value.StringValue;
import static org.junit.jupiter.api.Assertions.*;

class RoleBeliefProjectionTest {
    static final String XML=MappingXmlReaderTest.XML.replace("<runtime/>","""
        <runtime><datasets><beliefViews id="stored" namespace="*" predicate="*" arity="*" selection="STORED"/></datasets>
          <beliefBindings><bind id="belief-field" roleType="Capability" attribute="knowledge" datasetRef="stored"/></beliefBindings>
        </runtime>
        """);
    static RuntimeTables.Row row(String player,String json) {return new RuntimeTables.Row(player,Map.of("player",player,"beliefJson",json),Completeness.COMPLETE,Map.of("source-cut","qualified","physical-identity",player),Set.of(player));}
    @Test void inheritedBeliefsUseSameNativeRoleAndRollbackWhileMissingDoesNotInvalidateMembership()throws Exception {
        var csl=new CslFrontend().compile("csl v4.0 Knowledge { role Capability { attribute knowledge : String optional mutable; } role Worker specializes Capability; }","knowledge.csl").requireModel();
        var nativeModel=new CslScopeModelAdapter().compile(csl).model();var system=new MSystem(nativeModel);
        var engine=new NativeRuntimeMutationEngine(system,new NativeObjectBindings(),new CodeGroundedRuntimeRuleRegistry());
        var method=NativeRuntimeMutationEngine.class.getDeclaredMethod("cslPort");assertTrue(method.trySetAccessible());Port port=(Port)method.invoke(engine);
        var alpha=port.upsert("role-alpha","Worker","AlphaRole");var beta=port.upsert("role-beta","Worker","BetaRole");
        for(var role:List.of(alpha,beta)) {
            port.metadata(role,"xml:m0:program","program");port.metadata(role,"xml:m0:epoch","epoch");port.metadata(role,"xml:m0:cut","cut");port.metadata(role,"xml:m0:quality","COMPLETE");
        }
        port.metadata(alpha,"rolePlayer","player-alpha");port.metadata(beta,"rolePlayer","player-beta");
        var projection=new RoleBeliefProjection(MappingXmlReaderTest.parse(XML),nativeModel,"program");
        var data=Map.of("stored",new RuntimeTables.Dataset(List.of(row("player-alpha","[\"private(alpha)\"]"),row("player-beta","[]")),Completeness.COMPLETE));
        projection.apply(port,"epoch","cut",data);
        assertEquals(new StringValue("[\"private(alpha)\"]"),port.currentValue(alpha,"knowledge"));assertEquals(new StringValue("[]"),port.currentValue(beta,"knowledge"));
        assertEquals(2,system.state().numObjects());assertSame(nativeModel,system.model());
        var before=engine.savepoint();
        projection.apply(port,"epoch","cut",Map.of("stored",new RuntimeTables.Dataset(List.of(row("player-beta","[\"private(beta)\"]")),Completeness.PARTIAL)));
        assertTrue(port.currentValue(alpha,"knowledge").isUndefined());assertEquals("true",port.metadata(alpha,"propertyAbsent:knowledge"));
        assertEquals("COMPLETE",port.metadata(alpha,"xml:m0:quality"),"Missing mental evidence cannot invent missing MOISE enactment");
        assertEquals("player-alpha",port.metadata(alpha,"rolePlayer"));assertEquals(new StringValue("[\"private(beta)\"]"),port.currentValue(beta,"knowledge"));
        engine.rollback(before);assertEquals(new StringValue("[\"private(alpha)\"]"),port.currentValue(alpha,"knowledge"));
        assertThrows(IllegalArgumentException.class,()->new RoleBeliefProjection(MappingXmlReaderTest.parse(XML.replace("attribute=\"knowledge\"","attribute=\"absent\"")),nativeModel,"program"));
    }
}

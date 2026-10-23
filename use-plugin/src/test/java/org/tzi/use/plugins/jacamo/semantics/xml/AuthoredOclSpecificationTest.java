package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.AuthoredOclSpecificationLoader;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredOclSpecificationTest {
    private static final String OCL="""
        context Stock inv Nonnegative: self.quantity >= 0
        context Stock::increase(amount:Integer):Integer
        pre Allowed: amount > 0
        post Increased: self.quantity = self.quantity@pre + amount and result = self.quantity
        """;
    private Use75ModelBinding model()throws Exception {
        var csl=new CslFrontend().compile("csl v4.0 Inventory { entity Stock { quantity : Integer; } }","generated-test").requireModel();
        var binding=new Use75CslModelBackend().compile(csl,M0ExtensionSpec.observedPlayers());
        new UseModelApi(binding.model()).createOperation("Stock","increase",new String[][]{{"amount","Integer"}},"Integer");return binding;
    }
    private Map<String,AuthoredOclSpecificationLoader.Context> bindings() {
        return Map.of("Nonnegative",AuthoredOclSpecificationLoader.parseContext("context Stock"),
            "Allowed",AuthoredOclSpecificationLoader.parseContext("context Stock::increase(amount:Integer):Integer"),
            "Increased",AuthoredOclSpecificationLoader.parseContext("context Stock::increase(amount:Integer):Integer"));
    }
    @Test void neutralCompilerUsesNativeBeforeAfterArgumentsResultAndOneStateOwner()throws Exception {
        var loader=new AuthoredOclSpecificationLoader();var source=loader.parse(OCL,"authored.ocl");var binding=model();
        var prepared=loader.compile(source,binding,source.sourceHash(),bindings());prepared.install(new UseModelApi(binding.model()));
        assertEquals(3,prepared.clauses().size());assertEquals(2,binding.model().prePostConditions().size());
        var system=new MSystem(binding.model());var api=UseSystemApi.create(system,false);var self=api.createObject("Stock","item");
        api.setAttributeValue("item","quantity","3");var before=new MSystemState("before",system.state());api.setAttributeValue("item","quantity","5");
        var variables=new VarBindings();variables.push("self",self.value());variables.push("amount",IntegerValue.valueOf(2));variables.push("result",IntegerValue.valueOf(5));
        var post=prepared.clauses().stream().filter(c->c.specification().name().equals("Increased")).findFirst().orElseThrow();
        assertEquals(BooleanValue.TRUE,new Evaluator().eval(post.expression(),before,system.state(),variables));
        assertEquals(BooleanValue.FALSE,new Evaluator().eval(post.expression(),system.state(),system.state(),variables));
        assertSame(system,before.system());assertSame(system,system.state().system());
    }
    @Test void wrongBindingsNonBooleanAndSourceTamperCannotInstallPartially()throws Exception {
        var loader=new AuthoredOclSpecificationLoader();var parsed=loader.parse(OCL,"input.ocl");var binding=model();
        assertThrows(IllegalArgumentException.class,()->loader.compile(parsed,binding,"0".repeat(64),bindings()));
        assertThrows(IllegalArgumentException.class,()->loader.compile(parsed,binding,parsed.sourceHash(),Map.of()));
        var changed=loader.parse(OCL.replace("amount > 0","amount + 1"),"nonboolean.ocl");
        assertThrows(IllegalArgumentException.class,()->loader.compile(changed,binding,changed.sourceHash(),bindings()));
        assertTrue(binding.model().prePostConditions().isEmpty());
        assertThrows(IllegalArgumentException.class,()->AuthoredOclSpecificationLoader.parseContext("context Stock::increase(amount:Integer) garbage"));
    }
}

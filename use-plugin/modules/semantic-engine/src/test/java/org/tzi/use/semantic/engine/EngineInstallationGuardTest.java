package org.tzi.use.semantic.engine;

import java.net.URL;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.api.EngineInstallationGuard;
import static org.junit.jupiter.api.Assertions.*;

class EngineInstallationGuardTest {
    @Test void currentOriginalClosureHostGrammarRuntimeAndLicenseArePinned() {
        var result=EngineInstallationGuard.verify();assertEquals("7.5.0",result.get("hostVersion"));
        assertEquals("4.9.3",result.get("antlrVersion"));assertEquals(64,result.get("provenanceHash").length());
        assertThrows(UnsupportedOperationException.class,()->result.clear());
    }
    @Test void missingGrammarProvenanceLicenseOrClassCannotFallBack() {
        for(String missing:List.of("grammars/CSL.g4","grammars/IStar.g4","META-INF/use4goal-provenance.json","META-INF/licenses/use4Goal-GPL-3.0.txt",
                "org/vnu/sme/goal/verify/conformance/semantics/GoalMarking.class")) {
            var resolver=new ClassLoader(EngineInstallationGuard.class.getClassLoader()) {
                @Override public Enumeration<URL> getResources(String name) throws java.io.IOException {return name.equals(missing)?Collections.emptyEnumeration():super.getResources(name);}
            };
            var error=assertThrows(IllegalStateException.class,()->EngineInstallationGuard.verify(resolver));
            assertTrue(error.getMessage().startsWith("ENGINE_INSTALLATION_RESOURCE_MISSING:"+missing),error.toString());
        }
    }
    @Test void duplicateOriginalOrAntlrResourcesFailBeforeParserExecution() throws Exception {
        for(String duplicate:List.of("org/vnu/sme/goal/dsl/csl/mm/CslModel.class","org/antlr/v4/runtime/RuntimeMetaData.class")) {
            var resolver=new ClassLoader(EngineInstallationGuard.class.getClassLoader()) {
                @Override public Enumeration<URL> getResources(String name) throws java.io.IOException {
                    var values=Collections.list(super.getResources(name));if(name.equals(duplicate))values.add(new URL("file:/duplicate-installed-engine/"+name));return Collections.enumeration(values);
                }
            };
            assertTrue(assertThrows(IllegalStateException.class,()->EngineInstallationGuard.verify(resolver)).getMessage().startsWith("ENGINE_INSTALLATION_DUPLICATE_RESOURCE:"+duplicate));
        }
    }
}

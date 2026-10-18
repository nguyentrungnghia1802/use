package org.tzi.use.plugins.jacamo;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.semantics.CanonicalGoalMappingTest;
import org.tzi.use.plugins.jacamo.semantics.mapping.MappingRuleSet;
import org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalFacadeSessionTest {
    @Test void sameRulesResyncKeepsHostSystemAndChangedRulesReimportCreatesFreshOwner() throws Exception {
        String oldProfile=System.getProperty(SemanticProjectionProfile.PROPERTY),oldRules=System.getProperty(MappingRuleSet.PROPERTY);
        var source=CanonicalGoalMappingTest.source();var rules=CanonicalGoalMappingTest.rules(source);
        var directory=Files.createTempDirectory(Path.of("target"),"canonical-facade-");var configuration=directory.resolve("rules.json");Files.write(configuration,rules.bytes());
        var jcm=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toAbsolutePath();var host=new Session();
        try {
            System.setProperty(SemanticProjectionProfile.PROPERTY,"USE4GOAL_OBSERVED_V1");System.setProperty(MappingRuleSet.PROPERTY,configuration.toAbsolutePath().toString());
            try(var facade=BridgeFacadeTestSupport.nativeFacade(jcm,host,()->false)) {
                facade.importProject(jcm);var first=host.system();assertNotNull(first);assertEquals("USE4GOAL_OBSERVED_V1",facade.runtimeVerificationResult().semantic().profile());assertEquals("CURRENT_OBSERVED",facade.runtimeVerificationResult().freshness());
                facade.resyncRuntime();assertSame(first,host.system());
                var replacement=new MappingRuleSet(rules.version(),rules.id()+"-replacement",rules.profileId(),rules.profileVersion(),rules.expectedSourceHash(),rules.cslSource(),rules.iStarSource(),rules.bindings(),rules.contracts(),rules.decisions(),rules.options());Files.write(configuration,replacement.bytes());
                facade.resyncRuntime();var second=host.system();assertNotSame(first,second);assertEquals(replacement.hash(),facade.runtimeVerificationResult().semantic().configurationHash());
                Files.writeString(configuration,"{}");assertThrows(IllegalArgumentException.class,facade::resyncRuntime);assertSame(second,host.system());assertEquals("CURRENT_OBSERVED",facade.runtimeVerificationResult().freshness());
                System.setProperty(SemanticProjectionProfile.PROPERTY,"NATIVE_CURRENT");facade.resyncRuntime();assertNotSame(second,host.system());assertNull(facade.runtimeVerificationResult().semantic());
            }
        } finally {restore(SemanticProjectionProfile.PROPERTY,oldProfile);restore(MappingRuleSet.PROPERTY,oldRules);}
    }
    @Test void canonicalImportRejectsMissingRulesAndEdtBeforeHandoffOrActivation() throws Exception {
        String oldProfile=System.getProperty(SemanticProjectionProfile.PROPERTY),oldRules=System.getProperty(MappingRuleSet.PROPERTY);
        var jcm=Path.of("src/test/resources/semantics/original-engine/monitor.jcm").toAbsolutePath();var host=new Session();
        try(var facade=BridgeFacadeTestSupport.nativeFacade(jcm,host,()->false)) {
            System.setProperty(SemanticProjectionProfile.PROPERTY,"USE4GOAL_OBSERVED_V1");System.clearProperty(MappingRuleSet.PROPERTY);
            assertThrows(IllegalArgumentException.class,()->facade.importProject(jcm));assertFalse(host.hasSystem());
            var file=Files.createTempDirectory(Path.of("target"),"canonical-edt-").resolve("rules.json");Files.write(file,CanonicalGoalMappingTest.rules(CanonicalGoalMappingTest.source()).bytes());System.setProperty(MappingRuleSet.PROPERTY,file.toAbsolutePath().toString());
            javax.swing.SwingUtilities.invokeAndWait(()->assertEquals("CANONICAL_IMPORT_REQUIRES_BACKGROUND_WORKER",assertThrows(IllegalStateException.class,()->facade.importProject(jcm)).getMessage()));assertFalse(host.hasSystem());
        } finally {restore(SemanticProjectionProfile.PROPERTY,oldProfile);restore(MappingRuleSet.PROPERTY,oldRules);}
    }
    private static void restore(String name,String value){if(value==null)System.clearProperty(name);else System.setProperty(name,value);}
}

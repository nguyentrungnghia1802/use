package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mde.JacamoEcoreSourceLoader;

class EcoreSourceLoaderTest {
    static final String MODEL="<agentmetamodel:Organization xmlns:agentmetamodel='http://www.example.org/agentmetamodel' id='org'><groups id='team'><roles id='worker' isAbstract='false'/></groups></agentmetamodel:Organization>";
    @Test void exactBundledMetamodelHasAllTwentyOneClassesAndSevenEnumerations() {
        var loader=new JacamoEcoreSourceLoader();var audit=loader.inspect(loader.bundledMetamodel());
        assertEquals(21,audit.classifiers().stream().filter(c->c.kind().equals("EClass")).count());assertEquals(7,audit.classifiers().stream().filter(c->c.kind().equals("EEnum")).count());
        assertEquals(List.of("SEQUENCE=sequence:0","PARALLEL=parallel:1","CHOICE=choice:2"),audit.classifiers().stream().filter(c->c.name().equals("OPlanOperator")).findFirst().orElseThrow().literals());
        assertNotNull(getClass().getResource("/org/tzi/use/plugins/jacamo/semantics/metamodel/CSLMonitor.ecore"));
    }
    @Test void everyFeatureAndReferenceIsPreservedIncludingExplicitDefaultValues() {
        var loader=new JacamoEcoreSourceLoader();var source=loader.load(loader.bundledMetamodel(),MODEL.getBytes(StandardCharsets.UTF_8));
        assertEquals(source,loader.load(loader.bundledMetamodel(),MODEL.getBytes(StandardCharsets.UTF_8)));
        var role=source.nodes().stream().filter(n->n.concept().equals("Role")).findFirst().orElseThrow();
        assertEquals(false,role.features().get("isAbstract").value());assertTrue(role.features().get("isAbstract").explicitlySet());
        assertFalse(role.features().get("minCardinality").explicitlySet());assertEquals("0",role.features().get("minCardinality").value());
        assertEquals(Set.of("id","isAbstract","minCardinality","maxCardinality","superRoles","agents"),role.features().keySet());
        var group=source.nodes().stream().filter(n->n.concept().equals("Group")).findFirst().orElseThrow();assertEquals(group.id(),role.container());
        assertThrows(UnsupportedOperationException.class,()->role.features().clear());
        assertTrue(source.diagnostics().contains("ECORE_ROLE_REFERENCES_ARE_DEFINITIONS_NOT_ACCEPTED_ENACTMENTS"));
    }
    @Test void malformedMissingRequiredFeaturesUnknownEnumAndExternalReferencesAreRejected() {
        var loader=new JacamoEcoreSourceLoader();byte[] metamodel=loader.bundledMetamodel();
        assertThrows(IllegalArgumentException.class,()->loader.load(metamodel,MODEL.replace("id='worker'","").getBytes(StandardCharsets.UTF_8)));
        String goal="<agentmetamodel:OGoal xmlns:agentmetamodel='http://www.example.org/agentmetamodel' id='g' type='performance'/>";
        assertThrows(IllegalArgumentException.class,()->loader.load(metamodel,goal.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->loader.load(metamodel,MODEL.replace("isAbstract='false'","superRoles='https://invalid.example/model.xmi#x'").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->loader.load(metamodel,("<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///private'>]>"+MODEL).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void dynamicEnumsAreValuesNotExternalEObjectReferences() {
        var loader=new JacamoEcoreSourceLoader();var goal=loader.load(loader.bundledMetamodel(),"<a:OGoal xmlns:a='http://www.example.org/agentmetamodel' id='g' type='maintenance'/>".getBytes(StandardCharsets.UTF_8));
        assertEquals(Map.of("name","MAINTENANCE","literal","maintenance","ordinal",java.math.BigDecimal.ONE),goal.nodes().getFirst().features().get("type").value());
    }
}

package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.semantic.engine.frontend.*;
import static org.junit.jupiter.api.Assertions.*;

/** Official source admission, with no live MAS or historical mapping assets. */
class CompactSourceSelectionRegressionTest {
    @TempDir Path temp;

    private SourceSymbolCatalog source(String organization,List<String> schemes) throws Exception {
        var xml=new StringBuilder("<organisational-specification id=\"").append(organization)
                .append("\" os-version=\"0.7\" xmlns=\"http://moise.sourceforge.net/os\">")
                .append("<structural-specification><role-definitions><role id=\"operator\"/></role-definitions>")
                .append("<group-specification id=\"team\"><roles><role id=\"operator\" min=\"0\" max=\"4\"/></roles></group-specification>")
                .append("</structural-specification><functional-specification>");
        for(String name:schemes)xml.append("<scheme id=\"").append(name).append("\"><goal id=\"root_").append(name)
                .append("\"/><mission id=\"mission_").append(name).append("\" min=\"0\" max=\"4\"><goal id=\"root_")
                .append(name).append("\"/></mission></scheme>");
        xml.append("</functional-specification><normative-specification/></organisational-specification>");
        Files.writeString(temp.resolve("rules.xml"),xml);
        var jcm=Files.writeString(temp.resolve("dispatch.jcm"),"mas dispatch { organisation board : rules.xml {} }");
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        return SourceSymbolCatalog.fromSnapshot(snapshot.semanticContract());
    }

    private CompiledObservationPlan compile(SourceSymbolCatalog sources,String org,String requirement) {
        var csl=new CslFrontend().compile("csl v4.0 Dispatch { role Operator; }","state.csl").requireModel();
        var goal=new IStarFrontend().compile("istar DispatchGoals { role View { goal Ready : Achieve } }","goals.istar").requireModel();
        var specification=CompactMappingReader.read("""
            <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0" id="renamed">
              <csl><map source="moise:%s/role:operator" target="Operator"/></csl>
              <runtime>%s</runtime>
            </mapping>
            """.formatted(org,requirement),"mapping.xml");
        return ObservationPlanCompiler.compile(specification,sources,NativeTargetCatalog.fromModels(csl,goal));
    }
    @Test void uniqueAdmittedSchemeSurvivesRenamingAndCarriesExactSourcePin() throws Exception {
        for(String suffix:List.of("delta","omega")) {
            String org="org_"+suffix,scheme="process_"+suffix;
            var plan=compile(source(org,List.of(scheme)),org,"<requirement id=\"lifecycle\" provider=\"moise.scheme-lifecycle.v1\"/>");
            var request=plan.capabilityRequests().getFirst();
            assertEquals(org,request.parameters().get("organization"));assertEquals(scheme,request.parameters().get("scheme"));
            assertEquals("rules.xml",request.parameters().get("sourcePath"));
            assertTrue(request.parameters().get("sourceHash").toString().matches("[0-9a-f]{64}"));
            assertTrue(plan.captureScope().moiseKinds().contains(RuntimeFactKind.SCHEME_BOARD));
            assertTrue(plan.captureScope().includesArtifactClass("ora4mas.nopl.SchemeBoard"));
            assertFalse(plan.checkpointRules().isEmpty());
        }
    }
    @Test void ambiguousSchemesRequireExplicitSelectionRatherThanFirstOrCaseDefault() throws Exception {
        var sources=source("dispatch_org",List.of("first","second"));
        var error=assertThrows(IllegalArgumentException.class,()->compile(sources,"dispatch_org","<requirement id=\"lifecycle\" provider=\"moise.scheme-lifecycle.v1\"/>"));
        assertTrue(error.getMessage().contains("CAPABILITY_EXACT_SCHEME_SELECTION_REQUIRED:candidates=2"),error.toString());
        var selected=compile(sources,"dispatch_org","<requirement id=\"lifecycle\" provider=\"moise.scheme-lifecycle.v1\" source=\"moise:dispatch_org/scheme:second\"/>");
        assertEquals("second",selected.capabilityRequests().getFirst().parameters().get("scheme"));
    }
    @Test void absentSchemeAndDuplicateShorthandNeverGenerateAnImplicitProvider() throws Exception {
        var sources=source("dispatch_org",List.of("only"));
        var noSelection=CompactMappingReader.read("<mapping xmlns=\"urn:use:jacamo-mapping:3\" version=\"3.0\" id=\"empty\"><runtime><requirement id=\"lifecycle\" provider=\"moise.scheme-lifecycle.v1\"/></runtime></mapping>","empty.xml");
        var missing=assertThrows(IllegalArgumentException.class,()->ObservationCapabilityPlanner.compile(noSelection,sources,List.of(),List.of()));
        assertTrue(missing.getMessage().contains("candidates=0"));
        var duplicate=assertThrows(IllegalArgumentException.class,()->compile(sources,"dispatch_org","<requirement id=\"one\" provider=\"moise.scheme-lifecycle.v1\"/><requirement id=\"two\" provider=\"moise.scheme-lifecycle.v1\"/>"));
        assertTrue(duplicate.getMessage().contains("CAPABILITY_DUPLICATE_PROVIDER_SHORTHAND"));
    }
}

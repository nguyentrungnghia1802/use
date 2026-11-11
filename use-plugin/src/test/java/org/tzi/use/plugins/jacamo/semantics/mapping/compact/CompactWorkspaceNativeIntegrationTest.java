package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native pipeline/session integration; no live MAS, desktop or migrated-case claim. */
class CompactWorkspaceNativeIntegrationTest {
    @TempDir Path temp;
    private record Fixture(SemanticProgram program,NativeRuntimeMutationEngine engine) { }
    private Fixture fixture() throws Exception {
        var project=temp.resolve("dispatch.jcm");Files.writeString(project,"mas dispatch {}");
        var csl=temp.resolve("state.csl");Files.writeString(csl,"csl v4.0 Dispatch { orgContext WorkArea { attribute path : String required; } }");
        var istar=temp.resolve("goals.istar");Files.writeString(istar,"""
            istar DispatchGoals { role Supervisor {
              goal Opened : Achieve condition {[ self.path = '/north' ]}
              goal Safe : Maintain condition {[ self.path <> '/south' ]}
              goal Awaiting : Achieve
            } }
            """);
        var mapping=temp.resolve("mapping.xml");Files.writeString(mapping,"""
            <mapping xmlns="urn:use:jacamo-mapping:3" version="3.0" id="workspace-test">
              <csl><map source="workspace:*" target="WorkArea"/>
                <map source="workspace:*/identity:path" target="WorkArea.path"/></csl>
              <istar><map source="workspace:*" target="Supervisor"/></istar>
              <runtime><actorRuntime actor="Supervisor" activationPolicy="PROJECT_EXECUTION_VIEW" episodePolicy="STATIC_LIFETIME" self="WorkArea"/>
                <verify target="Supervisor.Opened" scope="workspace:*"/></runtime>
            </mapping>
            """);
        var inputs=MappingInputs.read(project,mapping,null,csl,istar);var base=JacamoSemanticSnapshot.empty();
        var meta=new SemanticMetadata("project","JACAMO_PROJECT","fixture",EvidenceAuthority.OFFICIAL_JACAMO_API,Fidelity.EXACT,CapabilityStatus.COMPLETE,List.of(),List.of());
        var semantic=new JacamoSemanticSnapshot(base.contractVersion(),new JcmSemanticContract.ProjectSemantic(meta,"dispatch",inputs.projectDigest()),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var source=new ModelSnapshot("revision",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of(),semantic);
        var program=new GenericMappingEngine().map(source,inputs);
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        return new Fixture(program,new NativeRuntimeMutationEngine(built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry(),program));
    }
    private RuntimeSnapshot cut(Fixture fixture,boolean proven) {
        var facts=new ArrayList<RuntimeFact>();
        for(String path:List.of("/north","/south")) {
            var id=new BridgeEntityId("cartago","environment","workspace","environment",path,"uuid"+path);
            facts.add(new RuntimeFact(id,RuntimeFactKind.WORKSPACE,Map.of("semanticId","workspace:"+path,"name",path.substring(1),"fullName",path,"uuid","uuid"+path),
                    List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        }
        var marks=Map.of("cartago",new SourceWatermark("cartago",2));
        Map<String,Object> metadata=proven?Map.of("cutPolicy","PER_SOURCE_WATERMARK_AND_TOPOLOGY_VALIDATED","sourceEvidence",Map.of("cartago",Map.of(
                "captureScopeHash",fixture.program().compactPlan().captureScope().hash(),"captureScope",fixture.program().compactPlan().captureScope().evidence(),
                "capturedFactKinds",fixture.program().compactPlan().cartagoKinds().stream().map(Enum::name).sorted().toList(),
                "inventoryAcquisition","OFFICIAL_CONTROLLER_ENUMERATION_THEN_SELECTED_ARTIFACT_INFO_AND_VALUES"))):Map.of();
        return new RuntimeSnapshot("cut","revision",Instant.EPOCH,Instant.EPOCH,marks,marks,1,facts,Map.of("cartago",Completeness.COMPLETE),"fixture",metadata);
    }
    @Test void compactScopesUseTheOriginalEngineOnTheSameNativeSystemAndSeparatePhysicalWorkspaces() throws Exception {
        var f=fixture();var engine=f.engine();var system=engine.system();
        engine.applyMappingCut(cut(f,true),"run",1,List.of());
        var scopes=engine.xmlOccurrences("run",1,"revision");assertEquals(2,scopes.size());assertTrue(scopes.stream().allMatch(s->s.complete()));
        assertEquals(2,scopes.stream().map(s->s.id()).distinct().count());
        try(var owner=new RuntimeEngineSessionOwner(engine,"run",1,"revision")) {
            var checks=new ExternalOclConstraintService.PreparedEvaluation(system.model(),List.of());
            var prepared=owner.prepare(1,"cut","hash",Map.of("cartago",Completeness.COMPLETE),false,true,List.of(),checks,4096);
            assertSame(system,prepared.state().after().system());assertEquals(2,prepared.casts().size());
            var result=owner.submit(prepared).get(10,TimeUnit.SECONDS);
            var core=CanonicalJson.object(result.evidence().observation().get("originalEngine"));
            var contexts=(List<?>)core.get("contexts");assertEquals(2,contexts.size());
            var opened=new HashSet<String>();var safe=new HashSet<String>();
            for(var raw:contexts) {
                var context=CanonicalJson.object(raw);assertTrue(((List<?>)context.get("originalApiCalls")).contains("GoalMarking.update"));
                for(var value:CanonicalJson.object(context.get("occurrences")).values()) {
                    var occurrence=CanonicalJson.object(value);
                    if(occurrence.get("sourceElement").equals("Awaiting")) {
                        assertEquals("UNAVAILABLE:GOAL_SOURCE_OR_AUTHORED_CONDITION_REQUIRED",occurrence.get("quality"));
                        assertNotEquals("FULFILLED",occurrence.get("leafStatus"));continue;
                    }
                    assertEquals("KNOWN",occurrence.get("quality"));
                    if(occurrence.get("sourceElement").equals("Opened"))opened.add(occurrence.get("leafStatus").toString());
                    if(occurrence.get("sourceElement").equals("Safe"))safe.add(occurrence.get("leafStatus").toString());
                }
            }
            assertEquals(Set.of("FULFILLED","PENDING"),opened);assertEquals(Set.of("FULFILLED","VIOLATED"),safe);
            assertFalse(CanonicalJson.object(result.evidence().observation().get("presentationState")).isEmpty());
        }
        var inventory=RequirementInventory.from(f.program(),system.model());
        assertEquals(List.of("Opened"),RequirementInventory.targetsForContext(inventory,Map.of("scopeDefinition","workspace:*")));
        assertSame(system,engine.system());
    }
    @Test void transportCompletenessWithoutInventoryProofCannotAdvanceOrClaimPass() throws Exception {
        var f=fixture();var engine=f.engine();engine.applyMappingCut(cut(f,false),"run",1,List.of());
        assertTrue(engine.xmlOccurrences("run",1,"revision").stream().noneMatch(s->s.complete()));
        try(var owner=new RuntimeEngineSessionOwner(engine,"run",1,"revision")) {
            var prepared=owner.prepare(1,"cut","hash",Map.of("cartago",Completeness.COMPLETE),false,true,List.of(),new ExternalOclConstraintService.PreparedEvaluation(engine.system().model(),List.of()),4096);
            var result=owner.submit(prepared).get(10,TimeUnit.SECONDS);
            var core=CanonicalJson.object(result.evidence().observation().get("originalEngine"));
            for(var raw:(List<?>)core.get("contexts"))assertEquals("UNAVAILABLE",CanonicalJson.object(raw).get("quality"));
            assertTrue(result.evidence().constraints().stream().noneMatch(r->r.kind().equals("GOAL_CONDITION")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
        }
    }
    @Test void compactReplayRebuildsOnlyFrozenNativeInputsAndRejectsIdentityTampering() throws Exception {
        var f=fixture();var codec=new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest();
        byte[] frozen=codec.encode(f.program());
        assertEquals("1.4.0",CanonicalJson.object(CanonicalJson.decode(frozen)).get("schemaVersion"));
        // Poison every original selected input. Replay must not reopen those paths
        // or look for a legacy configuration to recover the original program.
        try(var files=Files.list(temp)){for(var path:files.toList())Files.writeString(path,"unusable selected input");}
        var decoded=codec.decode(frozen);assertTrue(decoded.program().isCompact());assertNull(decoded.program().xmlPlan());
        assertEquals(f.program().hash(),decoded.program().hash());
        assertEquals(f.program().compactPlan().fingerprint(),decoded.program().compactPlan().fingerprint());
        assertEquals(f.program().cslHash(),decoded.program().cslHash());assertEquals(f.program().goalHash(),decoded.program().goalHash());
        var tampered=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(frozen)));tampered.put("programHash","0".repeat(64));
        var mismatch=assertThrows(IllegalArgumentException.class,()->codec.decode(CanonicalJson.encode(tampered)));
        assertTrue(mismatch.getMessage().contains("REPLAY_COMPACT_FINGERPRINT_MISMATCH"));
    }
    @Test void unsupportedEpisodesMissingRequirementsAndUndeclaredVerificationScopesFailBeforeReadiness() throws Exception {
        var f=fixture();var xml=temp.resolve("mapping.xml");String original=Files.readString(xml);
        var asset=f.program().complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow();
        var source=ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(asset.bytes())));
        for(var scenario:List.of(
                Map.entry(original.replace("STATIC_LIFETIME","MODEL_REVISION"),"COMPACT_WORKSPACE_EPISODE_POLICY_UNSUPPORTED"),
                Map.entry(original.replace("<verify target=\"Supervisor.Opened\" scope=\"workspace:*\"/>",""),"COMPACT_EXPLICIT_INTENTIONAL_REQUIREMENT_SELECTION_REQUIRED"),
                Map.entry(original.replace("scope=\"workspace:*\"","scope=\"project:dispatch\""),"VERIFICATION_SCOPE_NOT_DECLARED_BY_ACTOR_MAPPING"))) {
            Files.writeString(xml,scenario.getKey());
            var error=assertThrows(IllegalArgumentException.class,()->{
                var program=new GenericMappingEngine().map(source,MappingInputs.read(temp.resolve("dispatch.jcm"),xml,null,temp.resolve("state.csl"),temp.resolve("goals.istar")));
                new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
            });
            assertTrue(error.getMessage().contains(scenario.getValue()),error.toString());
        }
    }
}

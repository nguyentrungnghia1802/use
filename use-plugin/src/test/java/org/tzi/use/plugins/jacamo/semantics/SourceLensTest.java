package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.roundtrip.*;
import org.tzi.use.plugins.jacamo.semantics.mde.*;
import static org.tzi.use.plugins.jacamo.semantics.roundtrip.SupportedEditProfile.Kind.*;
import static org.tzi.use.plugins.jacamo.semantics.roundtrip.SourcePatchPlan.Status.*;

class SourceLensTest {
    static byte[] source(String operator){return ("<a:Organization xmlns:a='http://www.example.org/agentmetamodel' id='org'><!--opaque comment retained only in complement--><groups id='team'><roles id='worker'/><roles id='other'/></groups><schemes id='s'><rootGoal id='root' description='opaque source description'><plan operator='"+operator+"'><subGoals id='g1'/><subGoals id='g2'/></plan></rootGoal><missions id='m' goals='//@schemes.0/@rootGoal/@plan/@subGoals.0'/></schemes></a:Organization>").getBytes(StandardCharsets.UTF_8);}
    static SupportedEditProfile profile(byte[] source){var loader=new JacamoEcoreSourceLoader();var model=loader.load(loader.bundledMetamodel(),source);var role=model.nodes().stream().filter(n->n.concept().equals("Role") && n.features().get("id").value().equals("worker")).findFirst().orElseThrow();var plan=model.nodes().stream().filter(n->n.concept().equals("OPlan")).findFirst().orElseThrow();return new SupportedEditProfile(SupportedEditProfile.VERSION,List.of(new SupportedEditProfile.Owner("role",role.id().substring(("emf:"+model.sourceHash()+":").length()),CSL_ROLE_DEFINITION),new SupportedEditProfile.Owner("plan",plan.id().substring(("emf:"+model.sourceHash()+":").length()),ISTAR_OPLAN_DESCRIPTOR)));}
    @Test void malformedPublicInputsHaveExplicitBoundaryDiagnostics() {
        var source=source("sequence");var lens=new SourceLens();var old=lens.get(source,profile(source));
        assertThrows(IllegalArgumentException.class,()->lens.get(null,old.profile()));assertThrows(IllegalArgumentException.class,()->lens.get(source,null));
        var missing=assertThrows(IllegalArgumentException.class,()->new SourceLens.Projection(old.profile(),old.target(),null));assertEquals("LENS_PROJECTION_COMPLEMENT_REQUIRED",missing.getMessage());
        assertThrows(IllegalArgumentException.class,()->new SourceLens.Projection(null,old.target(),old.complement()));assertThrows(IllegalArgumentException.class,()->new SourceLens.Projection(old.profile(),null,old.complement()));
        assertThrows(IllegalArgumentException.class,()->lens.put(source,Map.of(),null));assertThrows(IllegalArgumentException.class,()->lens.put(source,null,old));assertThrows(IllegalArgumentException.class,()->lens.put(null,Map.of(),old));assertArrayEquals(source,old.complement().assets().getFirst().bytes());
    }
    @Test void getPutPreservesUnchangedBytesAndPutGetNormalizesSelectedStructure() {
        var source=source("sequence");var lens=new SourceLens();var old=lens.get(source,profile(source));var unchanged=lens.put(source,old.target(),old);assertEquals(APPLIED,unchanged.status(),unchanged.diagnostics().toString());assertArrayEquals(source,unchanged.proposedBytes());assertEquals("BYTE_EXACT_UNCHANGED_SOURCE_ONLY",unchanged.preservation());
        var edited=lens.put(source,Map.of("role",Map.of("name","reviewer","minimum","1","maximum","2"),"plan",Map.of("operator","PARALLEL","orderedChildren",List.of("g2","g1"))),old);assertEquals(APPLIED,edited.status(),edited.diagnostics().toString());assertEquals("reviewer",edited.normalizedTarget().get("role").get("name"));assertEquals("PARALLEL",edited.normalizedTarget().get("plan").get("operator"));assertEquals("AND",edited.normalizedTarget().get("plan").get("outcome"));assertEquals(List.of("g2","g1"),edited.normalizedTarget().get("plan").get("orderedChildren"));assertEquals(edited.normalizedTarget(),lens.get(edited.proposedBytes(),edited.rebasedProfile()).target());
        assertArrayEquals(source,old.complement().assets().getFirst().bytes());assertTrue(edited.diagnostics().contains("COMMENTS_AND_LAYOUT_NOT_PRESERVED_ON_EDIT"));
        var loader=new JacamoEcoreSourceLoader();var nodes=loader.load(loader.bundledMetamodel(),edited.proposedBytes());var mission=nodes.nodes().stream().filter(n->n.concept().equals("Mission")).findFirst().orElseThrow();String goal=(String)((Map<?,?>)((List<?>)mission.features().get("goals").value()).getFirst()).get("reference");assertEquals("g1",nodes.require(goal).features().get("id").value());assertTrue(edited.portableBytes().length>100);
    }
    @Test void andCannotChooseSequenceOrParallelWithoutOperatorAndOutsideEffectsStayUnsupported() {
        var bytes=source("choice");var lens=new SourceLens();var old=lens.get(bytes,profile(bytes));var ambiguous=lens.put(bytes,Map.of("plan",Map.of("outcome","AND")),old);assertEquals(AMBIGUOUS,ambiguous.status());assertEquals("AND_REQUIRES_EXPLICIT_SEQUENCE_OR_PARALLEL",ambiguous.diagnostics().getFirst());
        var sequence=lens.put(bytes,Map.of("plan",Map.of("outcome","AND","operator","SEQUENCE")),old);assertEquals(APPLIED,sequence.status(),sequence.diagnostics().toString());assertEquals("SEQUENCE",sequence.normalizedTarget().get("plan").get("operator"));
        assertEquals(UNSUPPORTED,lens.put(bytes,Map.of("role",Map.of("operationEffect","increment Java field")),old).status());assertEquals(UNSUPPORTED,lens.put(bytes,Map.of("role",Map.of("scalarValue",12)),old).status());
        assertEquals(CONFLICT,lens.put(bytes,Map.of("plan",Map.of("outcome","AND","operator","CHOICE")),old).status());
    }
    @Test void staleDigestComplementTamperNameCollisionAndAmbiguousOwnershipNeverWriteSource() {
        var bytes=source("sequence");var lens=new SourceLens();var old=lens.get(bytes,profile(bytes));assertEquals(CONFLICT,lens.put(source("parallel"),Map.of(),old).status());assertEquals(CONFLICT,lens.put(bytes,Map.of("role",Map.of("name","other")),old).status());assertEquals(CONFLICT,lens.put(bytes,Map.of("role",Map.of("minimum","3","maximum","2")),old).status());
        var target=new TreeMap<>(old.target());target.put("role",Map.of("name","tampered"));var tampered=new SourceLens.Projection(old.profile(),target,old.complement());assertEquals(CONFLICT,lens.put(bytes,Map.of(),tampered).status());
        var owners=new ArrayList<>(old.profile().owners());owners.add(owners.getFirst());var multi=lens.get(bytes,new SupportedEditProfile(SupportedEditProfile.VERSION,owners));assertEquals(AMBIGUOUS,lens.put(bytes,Map.of("role",Map.of("name","reviewer")),multi).status());assertArrayEquals(bytes,old.complement().assets().getFirst().bytes());
    }
    @Test void propertyAndOperationInterfaceEditsDoNotInventMissingScalarTypesOrJavaBodies() {
        byte[] xml="<a:Artifact xmlns:a='http://www.example.org/agentmetamodel' name='counter' type='sample.Counter'><properties name='price' arity='1'/><operations name='bid' arity='1'/></a:Artifact>".getBytes(StandardCharsets.UTF_8);var loader=new JacamoEcoreSourceLoader();var model=loader.load(loader.bundledMetamodel(),xml);var owners=new ArrayList<SupportedEditProfile.Owner>();for(var node:model.nodes())if(node.concept().equals("Property") || node.concept().equals("Operation"))owners.add(new SupportedEditProfile.Owner(node.concept(),node.id().substring(("emf:"+model.sourceHash()+":").length()),node.concept().equals("Property")?PROPERTY_INTERFACE:OPERATION_INTERFACE));
        var lens=new SourceLens();var old=lens.get(xml,new SupportedEditProfile(SupportedEditProfile.VERSION,owners));var patch=lens.put(xml,Map.of("Property",Map.of("name","amount"),"Operation",Map.of("arity",2)),old);assertEquals(APPLIED,patch.status(),patch.diagnostics().toString());assertEquals("amount",patch.normalizedTarget().get("Property").get("name"));assertEquals(new java.math.BigDecimal("2"),patch.normalizedTarget().get("Operation").get("arity"));assertEquals(UNSUPPORTED,lens.put(xml,Map.of("Property",Map.of("type","Integer")),old).status());
    }
}

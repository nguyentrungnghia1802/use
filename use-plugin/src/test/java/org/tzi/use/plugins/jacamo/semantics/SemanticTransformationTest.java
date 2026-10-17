package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mde.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

class SemanticTransformationTest {
    static org.jacamo.bridge.contract.ModelSnapshot source()throws Exception {
        Path path=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm").toAbsolutePath();return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);
    }
    @Test void catalogCoversEverySuppliedClassAndEnumAndEveryOfficialFeatureHasOneDecision()throws Exception {
        var loader=new JacamoEcoreSourceLoader();var catalog=new MappingRuleCatalog();var meta=loader.inspect(loader.bundledMetamodel());
        assertEquals(28,catalog.rules().size());for(var classifier:meta.classifiers())assertNotNull(catalog.require(classifier.name()));
        var source=source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        var service=new SemanticTransformationService();var bundle=service.fromOfficial(source,cfg);
        assertEquals(bundle,new SemanticTransformationService().fromOfficial(source,cfg));
        assertEquals(bundle.source().nodes().stream().mapToLong(n->n.features().size()).sum(),bundle.complement().decisions().size());
        for(var node:bundle.source().nodes())for(var feature:node.features().entrySet()) {
            var decisions=bundle.complement().decisions().stream().filter(d->d.sourceId().equals(node.id()) && d.feature().equals(feature.getKey())).toList();
            assertEquals(1,decisions.size());assertEquals(feature.getValue().value(),decisions.getFirst().preservedValue());
        }
        var codec=new MappingBundleCodec();var restored=codec.decode(codec.encode(bundle));assertEquals(bundle,restored);
        var result=new CodeGroundedNativePipeline().buildSemanticBundle(source,NativeProjectionMode.AUTO,restored);
        assertSame(result.nativeResult().model().model(),result.nativeResult().state().system().model());assertTrue(result.nativeResult().state().system().state().allObjects().isEmpty());
        assertTrue(bundle.complement().decisions().stream().anyMatch(d->d.reason().contains("SOURCE_CONDITION_LANGUAGE") || d.reason().contains("SOURCE_FEATURE_RETAINED")));
    }
    @Test void supplementedEcoreAndOfficialFrontendsReachTheSameNativeProjectionWithoutInferringEnactment()throws Exception {
        var source=source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        String xml="<xmi:XMI xmlns:xmi='http://www.omg.org/XMI' xmlns:a='http://www.example.org/agentmetamodel'><a:Organization id='monitor'><groups id='team'><roles id='worker' superRoles='/1'/></groups></a:Organization><a:Role id='soc' isAbstract='true'/></xmi:XMI>";
        byte[] bytes=xml.getBytes(StandardCharsets.UTF_8);var loader=new JacamoEcoreSourceLoader();var emf=loader.load(loader.bundledMetamodel(),bytes);
        var ss=source.semanticContract().moiseOrganizations().getFirst().structuralSpecification();var bindings=new HashMap<String,String>();
        for(var node:emf.nodes()) {
            if(node.concept().equals("Group"))bindings.put(node.id(),ss.groups().getFirst().metadata().semanticId());
            if(node.concept().equals("Role"))bindings.put(node.id(),ss.roles().stream().filter(r->r.roleId().equals(node.features().get("id").value())).findFirst().orElseThrow().metadata().semanticId());
        }
        var transformer=new SemanticTransformationService();var official=transformer.fromOfficial(source,cfg);var augmented=transformer.fromEcore(emf,bytes,source,bindings,cfg);
        var pipeline=new CodeGroundedNativePipeline();var first=pipeline.buildSemanticBundle(source,NativeProjectionMode.AUTO,official);var second=pipeline.buildSemanticBundle(source,NativeProjectionMode.AUTO,augmented);
        assertEquals(first.nativeResult().export().originalStructuralHash(),second.nativeResult().export().originalStructuralHash());
        assertEquals(emf.nodes().stream().mapToLong(n->n.features().size()).sum()+official.complement().decisions().size()+1,augmented.complement().decisions().size());
        assertArrayEquals(bytes,augmented.complement().assets().stream().filter(a->a.format().equals("SOURCE_XMI_BYTES")).findFirst().orElseThrow().bytes());
        assertThrows(IllegalArgumentException.class,()->transformer.fromEcore(emf,bytes,null,bindings,cfg));
        var wrong=new HashMap<>(bindings);wrong.put(emf.nodes().stream().filter(n->n.concept().equals("Role")).findFirst().orElseThrow().id(),ss.groups().getFirst().metadata().semanticId());
        assertThrows(IllegalArgumentException.class,()->transformer.fromEcore(emf,bytes,source,wrong,cfg));
    }
    @Test void complementTamperAndMissingFeatureDecisionsCannotBeAdmitted()throws Exception {
        var source=source();var cfg=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        var bundle=new SemanticTransformationService().fromOfficial(source,cfg);var complement=bundle.complement();
        assertThrows(IllegalArgumentException.class,()->new TraceComplement.Asset("bad","XMI","0".repeat(64),Base64.getEncoder().encodeToString(new byte[]{1})));
        assertThrows(IllegalArgumentException.class,()->new MappingBundle(bundle.source(),cfg,new TraceComplement("1.0",MappingRuleCatalog.VERSION,complement.sourceHash(),complement.metamodelHash(),complement.assets(),List.of())));
        var decisions=new ArrayList<>(complement.decisions());var first=decisions.getFirst();decisions.set(0,new TraceComplement.Decision(first.sourceId(),first.feature(),first.ruleId(),first.disposition(),first.targets(),first.reason(),"corrupt",first.origin()));
        assertThrows(IllegalArgumentException.class,()->new MappingBundle(bundle.source(),cfg,new TraceComplement("1.0",MappingRuleCatalog.VERSION,complement.sourceHash(),complement.metamodelHash(),complement.assets(),decisions)));
        var codec=new MappingBundleCodec();var envelope=new HashMap<>(org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(codec.encode(bundle))));envelope.put("bundleHash","0".repeat(64));
        assertThrows(IllegalArgumentException.class,()->codec.decode(org.jacamo.bridge.contract.CanonicalJson.encode(envelope)));
    }
}

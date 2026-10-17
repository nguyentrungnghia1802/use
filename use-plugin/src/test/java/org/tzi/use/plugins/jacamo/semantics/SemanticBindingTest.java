package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.binding.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;

class SemanticBindingTest {
    @Test void sameAuthoritativeModelInventoryKeepsObserverKindsAndNaryEndOrderAndExportsExactly()throws Exception {
        var source=SemanticTransformationTest.source();var domain=new CslSchema.Classifier("domain",DomainProjection.artifactId(CslCounterArtifact.class.getName()),"Item",CslSchema.Kind.ENTITY,"",false,List.of(new CslSchema.Attribute("quantity","quantity","Integer","p",false)));var base=new OrganizationalMappingRules().derive(source,List.of(domain),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var context=base.schema().classifiers().stream().filter(c->c.kind()==CslSchema.Kind.ORG_CONTEXT).findFirst().orElseThrow();var relation=new CslSchema.Association("relation","Related",List.of(new CslSchema.End("domain","first","*"),new CslSchema.End("domain","second","*"),new CslSchema.End(context.id(),"relationshipContext","*")),0);var schema=new CslSchema(base.schema().id(),base.schema().classifiers(),List.of(relation),base.schema().invariants(),base.schema().contracts(),base.schema().policy());var config=new org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration(base.version(),base.profile(),schema,base.requirements(),base.decisions());
        var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);var binding=pipeline.binding();assertSame(pipeline.model().model(),binding.model());assertSame(binding.model(),pipeline.state().system().model());var inventory=binding.inventory();assertEquals(1,inventory.forSource(domain.sourceId()).size());assertTrue(inventory.types().stream().filter(t->t.symbol().endsWith("Observation")).allMatch(t->t.semanticKind().equals("OBSERVER_HELPER")));
        var actual=inventory.relations().stream().filter(r->r.symbol().equals("Related")).findFirst().orElseThrow();assertEquals(List.of("first","second","relationshipContext"),actual.ends().stream().map(ProjectionBindingRegistry.End::role).toList());assertEquals(3,actual.ends().size());assertEquals(pipeline.export().originalStructuralHash(),pipeline.export().recompiledStructuralHash());
        try(var projector=new NativeRuntimeProjector(pipeline,"binding",1,source.modelRevision())) {var view=ProjectionReadView.read(projector.coordinator());assertEquals("CSL_OBSERVER_V2",view.profile());assertThrows(UnsupportedOperationException.class,()->view.objects().clear());assertTrue(view.objects().isEmpty());}
    }
    @Test void packagedMetamodelV2ObserverAttributesMatchActualCompiledHelpers()throws Exception {
        var pkg=org.tzi.use.plugins.jacamo.mapping.V2EcoreAuditTest.load(java.nio.file.Files.readString(java.nio.file.Path.of("Core/Metamodel/csl-monitor-v1/CSLMonitor.ecore")));
        assertEquals(org.eclipse.emf.common.util.Diagnostic.OK,org.eclipse.emf.ecore.util.Diagnostician.INSTANCE.validate(pkg).getSeverity());
        var ext=pkg.getESubpackages().stream().filter(p->p.getName().equals("capabilityV2")).findFirst().orElseThrow();assertEquals("https://use-jacamo.org/csl-monitor/capability/2.0",ext.getNsURI());
        var source=SemanticTransformationTest.source();var config=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));var nativeModel=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config).model().model();
        for(var cls:ext.getEClassifiers()) {var target=nativeModel.getClass(cls.getName());assertNotNull(target);assertEquals("OBSERVER_HELPER",target.getAnnotationValue("CslClassifier","kind"));assertEquals(new TreeSet<>(((org.eclipse.emf.ecore.EClass)cls).getEAllAttributes().stream().map(org.eclipse.emf.ecore.EAttribute::getName).toList()),new TreeSet<>(target.attributes().stream().map(org.tzi.use.uml.mm.MAttribute::name).toList()));}
        assertEquals(8,ext.getEClassifiers().size());
    }
}

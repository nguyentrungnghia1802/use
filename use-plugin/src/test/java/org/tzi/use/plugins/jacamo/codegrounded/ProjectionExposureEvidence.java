package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;

/** Counts the public state and raw current Jason cuts at the same native-state boundary. */
public final class ProjectionExposureEvidence {
    private ProjectionExposureEvidence() { }
    public static Map<String,Object> assertAndReport(org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade facade) throws Exception {
        var workspaceField=facade.getClass().getDeclaredField("nativeWorkspace"); workspaceField.setAccessible(true);
        var workspace=workspaceField.get(facade); var projectorField=workspace.getClass().getDeclaredField("runtimeProjector"); projectorField.setAccessible(true);
        var projector=(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector)projectorField.get(workspace);
        return projector.coordinator().read(()->assertAndReport(projector.mutations()));
    }
    public static Map<String,Object> assertAndReport(NativeRuntimeMutationEngine engine) {
        var system=engine.system(); var model=system.model(); var state=system.state();
        assertNotNull(model.getClass("Belief"));
        assertTrue(model.classes().stream().filter(c->DomainProjection.kind(c).equals("artifact"))
                .allMatch(c->"APPLICATION".equals(c.getAnnotationValue("ArtifactProvider","origin"))));
        var raw=new ArrayList<Map<?,?>>();
        engine.savepoint().jasonCuts().values().forEach(cut->((List<?>)cut.get("beliefs")).forEach(row->raw.add((Map<?,?>)row)));
        var rawIds=raw.stream().map(b->b.get("semanticId").toString()).collect(java.util.stream.Collectors.toSet());
        var eligibleIds=raw.stream().filter(b->Boolean.TRUE.equals(b.get("domainAuthored"))
                && Boolean.FALSE.equals(b.get("authoritativeElsewhere"))).map(b->b.get("semanticId").toString()).collect(java.util.stream.Collectors.toSet());
        var beliefs=state.objectsOfClass(model.getClass("Belief"));
        for(var belief:beliefs) if(engine.metadata(belief,"sourceLayer").equals("RUNTIME")) {
            String id=engine.metadata(belief,"semanticId");
            assertTrue(rawIds.contains(id),"stale belief: "+id); assertTrue(eligibleIds.contains(id),"unproved belief: "+id);
        }
        if(!NativeProjectionPolicy.verificationUses(model,"Belief")) assertTrue(beliefs.isEmpty());
        var counts=new TreeMap<String,Integer>(); model.classes().forEach(c->counts.put(c.name(),state.objectsOfClass(c).size()));
        var attributes=new TreeMap<String,Object>();
        for(var cls:model.classes()) {
            assertTrue(cls.allAttributes().stream().allMatch(a->a.getAnnotation("ProjectionAttribute")!=null),"Unclassified native attribute: "+cls.name());
            attributes.put(cls.name(),cls.allAttributes().stream().sorted(Comparator.comparing(org.tzi.use.uml.mm.MAttribute::name))
                    .map(a->Map.of("name",a.name(),"type",a.type().toString(),"category",a.getAnnotationValue("ProjectionAttribute","category"),
                            "provenance",a.getAnnotationValue("ProjectionAttribute","provenance"))).toList());
        }
        return Map.ofEntries(Map.entry("policyVersion",NativeProjectionPolicy.VERSION),Map.entry("classes",model.classes().stream().map(c->c.name()).sorted().toList()),
                Map.entry("classCount",model.classes().size()),Map.entry("objectCount",state.numObjects()),Map.entry("linkCount",state.allLinks().size()),
                Map.entry("beliefCount",beliefs.size()),Map.entry("currentJasonBeliefCount",raw.size()),Map.entry("eligibleDomainBeliefCount",eligibleIds.size()),
                Map.entry("beliefVerificationDemand",NativeProjectionPolicy.verificationUses(model,"Belief")),Map.entry("objectCounts",counts),
                Map.entry("attributes",attributes),Map.entry("declaredAttributeCount",model.classes().stream().mapToInt(c->c.attributes().size()).sum()),
                Map.entry("inheritedAttributeCount",model.classes().stream().mapToInt(c->c.allAttributes().size()).sum()));
    }
}

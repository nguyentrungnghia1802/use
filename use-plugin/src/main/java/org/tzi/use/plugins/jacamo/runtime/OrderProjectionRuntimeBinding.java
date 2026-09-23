package org.tzi.use.plugins.jacamo.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.mapping.*;
import org.tzi.use.plugins.jacamo.materialization.*;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.trace.*;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MSystem;

/** Runs on the owning runtime mutation thread. A reorder never creates/deletes membership links. */
public final class OrderProjectionRuntimeBinding {
    public int reorder(MSystem system, TransformationPlan structure, InstancePlan membership,
            List<OrderProjectionPlanner.SourceOrder> authoritative, TraceIndex trace) {
        InstancePlan desired = new OrderProjectionPlanner().project(structure, membership, authoritative);
        Set<String> orderClasses = new HashSet<>(); structure.orderProjections().forEach(p -> orderClasses.add(p.entryClass()));
        // Validate every object and exact trace before any write, including the physical membership snapshot.
        for (var link : desired.links()) {
            var association = system.model().getAssociation(link.association());
            var source = system.state().objectByName(link.sourceObject()); var target = system.state().objectByName(link.targetObject());
            if (association == null || source == null || target == null ||
                    !system.state().hasLinkBetweenObjects(association, List.of(source, target), null))
                throw new IllegalArgumentException("ORDER_RESYNC_REQUIRED: " + link);
        }
        Set<String> associations = new HashSet<>(); desired.links().forEach(l -> associations.add(l.association()));
        structure.associations().forEach(a -> associations.add(a.name()));
        for (String name : associations) {
            long expected = desired.links().stream().filter(l -> l.association().equals(name)).count();
            if (system.state().linksOfAssociation(system.model().getAssociation(name)).size() != expected)
                throw new IllegalArgumentException("ORDER_MEMBERSHIP_DRIFT: " + name);
        }
        List<ObjectPlan> rows = desired.objects().stream().filter(o -> orderClasses.contains(o.className())).toList();
        for (var row : rows) {
            var object = system.state().objectByName(row.name());
            if (object == null || !object.cls().name().equals(row.className()) || trace.byUseId("object:" + row.name()).stream().noneMatch(t ->
                    t.targetKind().equals("ORDER_ENTRY") && t.sourceSemanticId().equals(row.semanticId()) && t.status() == TraceRecord.Status.PROJECTED))
                throw new IllegalArgumentException("ORDER_TRACE_UNRESOLVED: " + row.name());
        }
        int changed = 0;
        for (var row : rows) {
            var object = system.state().objectByName(row.name());
            var rank = new IntegerValue(Math.toIntExact(((AttributeValue.IntegerNumber) row.values().get("rank")).value()));
            if (!object.state(system.state()).attributeValue("rank").equals(rank)) {
                object.state(system.state()).setAttributeValue(object.cls().attribute("rank", true), rank); changed++;
            }
        }
        return changed;
    }

    /** Authoritative reconnect builds a fresh candidate; caller installs only a validated candidate atomically. */
    public DirectUseBackend.Result resync(String modelName, TransformationPlan structure, InstancePlan membership,
            List<OrderProjectionPlanner.SourceOrder> authoritative) {
        var desired = new OrderProjectionPlanner().project(structure, membership, authoritative);
        var result = new DirectUseBackend().materialize(new TextBackend().generate(modelName, structure, desired), desired);
        if (!result.structureValid() || !result.invariantsValid())
            throw new IllegalArgumentException("ORDER_RESYNC_INVALID: " + result.validationOutput());
        return result;
    }
}

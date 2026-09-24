package org.tzi.use.plugins.jacamo.trace;

import java.util.*;
import org.tzi.use.plugins.jacamo.mapping.*;
import org.tzi.use.plugins.jacamo.materialization.InstancePlan;

/** Exact feature, owner and target identity is retained in every order-entry semantic ID. */
public final class OrderProjectionTrace {
    public List<TraceRecord> build(TransformationPlan structure, InstancePlan instances) {
        List<TraceRecord> records = new ArrayList<>();
        for (var spec : structure.orderProjections()) {
            records.add(record(spec.sourceIdentity(), "class:" + spec.entryClass(), "CLASS", spec.ruleId()));
            records.add(record(spec.sourceIdentity(), "attribute:" + spec.entryClass() + ".rank", "ATTRIBUTE", spec.ruleId()));
            records.add(record(spec.sourceIdentity(), "association:" + spec.ownerAssociation(), "ASSOCIATION", spec.ruleId()));
            records.add(record(spec.sourceIdentity(), "association:" + spec.targetAssociation(), "ASSOCIATION", spec.ruleId()));
            records.add(record(spec.sourceIdentity(), "operation:" + spec.owner() + "." + spec.query(), "ORDER_NAVIGATION", spec.ruleId()));
            for (var object : instances.objects()) if (object.className().equals(spec.entryClass()))
                records.add(record(object.semanticId(), "object:" + object.name(), "ORDER_ENTRY", spec.ruleId()));
            for (var link : instances.links()) if (link.association().equals(spec.ownerAssociation()) || link.association().equals(spec.targetAssociation()))
                records.add(record(spec.sourceIdentity(), "link:" + link.association() + ":" + link.sourceObject() + ":" + link.targetObject(), "ORDER_LINK", spec.ruleId()));
        }
        return List.copyOf(records);
    }
    private String traceId(String target) {
        try { return "trace:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(target.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 24); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private TraceRecord record(String source, String target, String kind, String rule) {
        return new TraceRecord(traceId(target), source, target, "EREFERENCE_ORDER",
                kind, rule, "ORDER_V1", null, null, null, TraceRecord.Status.PROJECTED);
    }
}

package org.tzi.use.plugins.jacamo.runtime;

import java.util.List;

/** Derived report of active V2 binding families; not instance authorization. */
public final class RuntimeMappingCompatibility {
    public record Entry(String ruleId, String event, String action, String anchor, String useTarget,
                        String traceTargetKind, String mutation, String status, String migrationRisk) { }
    public List<Entry> report(RuntimeMapping mapping) {
        new RuntimeMappingValidator().validate(mapping);
        return mapping.rules().stream().map(rule -> new Entry(rule.id(), rule.eventKind().name(),
            rule.action().name(), rule.anchor(), switch (rule.targetKind()) {
                case "OBJECT" -> "MObject of traced compiled MClass";
                case "ATTRIBUTE" -> "VP002 projected MAttribute";
                case "ASSOCIATION" -> "Structural MAssociation and traced participants";
                case "OPERATION" -> "VP003 projected MOperation";
                case "ORDER_NAVIGATION" -> "Mapping V2 target-only order rows with independent authoritative ranks";
                default -> "RuntimeTrace only";
            }, rule.targetKind(), rule.mutation(), rule.support(), rule.migrationRisk())).toList();
    }
}

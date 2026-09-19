package org.tzi.use.plugins.jacamo.materialization;

public record LinkPlan(String association, String sourceObject, String targetObject,
                       boolean composition, String sourceSemanticId, String targetSemanticId,
                       String mappingRuleId) { }

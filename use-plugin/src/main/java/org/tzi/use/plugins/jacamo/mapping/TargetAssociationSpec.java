package org.tzi.use.plugins.jacamo.mapping;

public record TargetAssociationSpec(String name, String kind, MappingModel.AssociationEnd firstEnd,
                                    MappingModel.AssociationEnd secondEnd, String sourceIdentity, String ruleId) { }

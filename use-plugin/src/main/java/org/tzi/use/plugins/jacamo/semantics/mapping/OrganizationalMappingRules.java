package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.plugins.jacamo.semantics.profile.*;

/** Executable structural projection; omitted semantics remain explicit decisions and in the source complement. */
public final class OrganizationalMappingRules {
    public CslMonitorConfiguration derive(ModelSnapshot input,List<CslSchema.Classifier> domain,
            List<CslSchema.Association> associations,List<CslSchema.Invariant> invariants,
            List<CslSchema.OperationContract> contracts,RequirementModel requirements) {
        if(domain.stream().anyMatch(c->c.kind()!=CslSchema.Kind.ENTITY))throw new IllegalArgumentException("DOMAIN_VIEW_CLASSIFIERS_MUST_BE_ENTITIES");
        var types=new ArrayList<>(domain);var nesting=new ArrayList<CslStructuralPolicy.Nesting>();var cards=new ArrayList<CslStructuralPolicy.RoleCardinality>();var decisions=new ArrayList<UnsupportedFeature>();
        var candidates=new ArrayList<MoiseUseSymbols.Candidate>();var reserved=new HashSet<>(CslUseBinding.HELPERS);domain.forEach(c->reserved.add(c.symbol()));
        var excluded=new HashSet<String>();
        for(var organization:input.semanticContract().moiseOrganizations()) {
            MoiseDomainProjection.validate(organization);var ss=organization.structuralSpecification();
            ss.groups().forEach(g->candidates.add(new MoiseUseSymbols.Candidate(g.metadata().semanticId(),"Context","CslContext_"+g.groupId())));
            for(var role:ss.roles())if(role.superRoleSemanticIds().size()>1) {
                excluded.add(role.metadata().semanticId());decisions.add(new UnsupportedFeature(role.metadata().semanticId(),"Role","MULTIPLE_PARENT_NOT_ADMITTED",NativeUseModelBuilder.canonicalJson(role.superRoleSemanticIds())));
            }
            boolean changed;do{changed=false;for(var role:ss.roles())if(!excluded.contains(role.metadata().semanticId()) && role.superRoleSemanticIds().stream().anyMatch(excluded::contains)) {
                excluded.add(role.metadata().semanticId());changed=true;decisions.add(new UnsupportedFeature(role.metadata().semanticId(),"Role","EXCLUDED_PARENT_HIERARCHY",NativeUseModelBuilder.canonicalJson(role.superRoleSemanticIds())));
            }}while(changed);
            ss.roles().stream().filter(r->!excluded.contains(r.metadata().semanticId())).forEach(r->candidates.add(new MoiseUseSymbols.Candidate(r.metadata().semanticId(),"Role","CslRole_"+r.roleId())));
        }
        var symbols=MoiseUseSymbols.allocate(candidates,reserved);
        for(var organization:input.semanticContract().moiseOrganizations()) {
            var ss=organization.structuralSpecification();
            for(var group:ss.groups())types.add(new CslSchema.Classifier(group.metadata().semanticId(),group.metadata().semanticId(),symbols.get(group.metadata().semanticId()),CslSchema.Kind.ORG_CONTEXT,"",false,List.of()));
            for(var role:ss.roles())if(!excluded.contains(role.metadata().semanticId()))types.add(new CslSchema.Classifier(role.metadata().semanticId(),role.metadata().semanticId(),symbols.get(role.metadata().semanticId()),CslSchema.Kind.ROLE,
                role.superRoleSemanticIds().isEmpty()?"":role.superRoleSemanticIds().getFirst(),role.abstractRole(),List.of()));
            for(var group:ss.groups())if(!group.parentGroupSemanticId().isBlank()) {
                var bounds=ss.subGroupCardinalities().stream().filter(c->c.parentGroupId().equals(group.parentGroupSemanticId()) && c.subGroupId().equals(group.metadata().semanticId())).toList();
                if(bounds.size()!=1)throw new IllegalArgumentException("SOURCE_SUBGROUP_BOUND_BINDING_REQUIRED:"+group.metadata().semanticId());
                var bound=bounds.getFirst();nesting.add(new CslStructuralPolicy.Nesting(bound.metadata().semanticId(),bound.parentGroupId(),bound.subGroupId(),bound.min(),bound.max()));
            }
            for(var card:ss.groupRoleCardinalities()) {
                boolean leaf=ss.roles().stream().noneMatch(r->r.superRoleSemanticIds().contains(card.roleId()));
                if(!excluded.contains(card.roleId()) && leaf)cards.add(new CslStructuralPolicy.RoleCardinality(card.metadata().semanticId(),card.groupId(),card.roleId(),card.min(),card.max()));
                else decisions.add(new UnsupportedFeature(card.metadata().semanticId(),"RoleCardinality","ENTAILMENT_OR_EXCLUDED_ROLE_POLICY_NOT_ADMITTED","Original contextual bound retained; no guessed inherited count"));
            }
            for(var relation:ss.roleRelations())decisions.add(new UnsupportedFeature(relation.metadata().semanticId(),"Link/Compatibility","SCOPED_POLICY_REQUIRES_EXPLICIT_ABSTRACTION",NativeUseModelBuilder.canonicalJson(List.of(relation.groupSemanticId(),relation.sourceRoleSemanticId(),relation.targetRoleSemanticId(),relation.scope(),relation.extendsToSubGroups(),relation.bidirectional()))));
            for(var norm:organization.normativeSpecification().norms())decisions.add(new UnsupportedFeature(norm.metadata().semanticId(),"Norm","NORM_IS_NOT_A_STATE_INVARIANT","Activation/modality/deadline/lifecycle retained for normative observer"));
        }
        var schema=new CslSchema("derived:"+input.modelRevision(),types,associations,invariants,contracts,new CslStructuralPolicy(nesting,cards,List.of(),false));
        return new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V2,schema,requirements,decisions);
    }
}

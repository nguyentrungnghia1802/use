package org.tzi.use.plugins.jacamo.semantics.csl;

import java.util.*;
import static org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.required;

/** Authored core-CSL policies. No directional/scoped MOISE permission is silently weakened into this relation. */
public record CslStructuralPolicy(List<Nesting> nesting,List<RoleCardinality> cardinalities,
        List<Compatibility> compatibilities,boolean enforceCompatibility,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) List<Declaration> declarations) {
    public CslStructuralPolicy(List<Nesting> nesting,List<RoleCardinality> cardinalities,List<Compatibility> compatibilities,boolean enforceCompatibility){this(nesting,cardinalities,compatibilities,enforceCompatibility,List.of());}
    public record Declaration(String id,String contextType,String memberType){public Declaration{required(id);required(contextType);required(memberType);}}
    public record Nesting(String id,String parentContext,String childContext,int minimum,int maximum) {
        public Nesting(String id,String parentContext,String childContext){this(id,parentContext,childContext,0,-1);}
        public Nesting{required(id);required(parentContext);required(childContext);if(minimum<0 || maximum< -1 || maximum>=0 && maximum<minimum)throw new IllegalArgumentException("CSL_NESTING_CARDINALITY_INVALID:"+id);}
    }
    public record RoleCardinality(String id,String contextType,String roleType,int minimum,int maximum) {
        public RoleCardinality{required(id);required(contextType);required(roleType);if(minimum<0 || maximum< -1 || maximum>=0 && maximum<minimum)throw new IllegalArgumentException("CSL_ROLE_CARDINALITY_INVALID:"+id);}
    }
    public record Compatibility(String id,String firstRole,String secondRole) {
        public Compatibility{required(id);required(firstRole);required(secondRole);if(firstRole.equals(secondRole))throw new IllegalArgumentException("CSL_COMPATIBILITY_DISTINCT_ROLES_REQUIRED");}
    }
    public CslStructuralPolicy {nesting=List.copyOf(nesting);cardinalities=List.copyOf(cardinalities);compatibilities=List.copyOf(compatibilities);declarations=List.copyOf(Objects.requireNonNullElse(declarations,List.of()));}
    public static final class EmptyFilter {
        @Override public boolean equals(Object value){return value==null || value.equals(CslStructuralPolicy.empty());}
        @Override public int hashCode(){return 0;}
    }
    public static CslStructuralPolicy empty(){return new CslStructuralPolicy(List.of(),List.of(),List.of(),false);}
}

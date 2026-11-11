package org.vnu.sme.goal.dsl.csl.parser;


/**
 * =============================================================================
 * MODULE: CSL CST to semantic-model factory
 * =============================================================================
 * 1. PURPOSE:
 *    Resolves parsed CST declarations into an CSL structural model. CslModelCS is the input; linked model objects ready for validation/rendering are the output. Acceptance does not imply support in an execution backend.
 *
 * 2. CORE MAPPING / LOGIC RULES:
 *    - Register declarations before resolving references to support forward references.
 *    - Convert textual attributes/cardinalities into typed semantic values.
 *    - Reject missing or incompatible references at the semantic boundary.
 *    - Main operations exposed by this file: ok(), create(), build(), group(), relation(), compatibility(), attributes().
 *
 * 3. PIPELINE / WORKFLOW:
 *      1. create / build(CSLModelCS)
 *      2. index declarations
 *      3. resolve references and relationships
 *      4. assemble CSLModel
 * =============================================================================
 */
import java.math.BigInteger;
import java.util.*;
import org.vnu.sme.goal.dsl.csl.ast.*;
import org.vnu.sme.goal.dsl.csl.mm.*;

public final class CslModelFactory {
    public record SemanticError(CslSourceLocationCS location,String message){}
    public record Result(CslModel model,List<SemanticError> errors){ public Result{errors=List.copyOf(errors);} public boolean ok(){return model!=null&&errors.isEmpty();}}
    private CslModelFactory(){}
    public static Result create(CslModelCS ast){ Builder b=new Builder(ast); CslModel m=b.build(); if(m!=null) CslSemanticValidator.validate(m).forEach(x->b.error(ast.location(),x)); return new Result(m,b.errors); }

    private static final class Builder {
        private final CslModelCS ast; private final List<SemanticError> errors=new ArrayList<>();
        private final Map<String,CslEnum> enums=new LinkedHashMap<>();
        private final Map<String,CslNamedDataType> namedDataTypes=new LinkedHashMap<>();
        Builder(CslModelCS ast){this.ast=ast;}
        CslModel build(){
            List<CslEnum> enumList=new ArrayList<>();
            for(var x:ast.enums()){
                Set<String>s=new LinkedHashSet<>(x.literals());
                if(s.size()!=x.literals().size())error(x.location(),"duplicate literal in enum '"+x.name()+"'");
                CslEnum e=new CslEnum(x.name(),List.copyOf(s));
                if(enums.putIfAbsent(x.name(),e)!=null)error(x.location(),"duplicate enum name '"+x.name()+"'");
                else enumList.add(e);
            }
            for (var x : ast.dataTypes()) {
                CslNamedDataType type = new CslNamedDataType(x.name());
                if (enums.containsKey(x.name()) || namedDataTypes.putIfAbsent(x.name(), type) != null)
                    error(x.location(), "duplicate data type name '" + x.name() + "'");
            }
            List<CslEntity> entities=ast.entities().stream().map(x->new CslEntity(x.name(),x.specializes(),attributes(x.name(),x.attributes()))).toList();
            List<CslRole> roles=ast.roles().stream().map(x->new CslRole(x.name(),x.parentRoles(),attributes(x.name(),x.attributes()))).toList();
            Map<String,CslEntity> entityMap=indexEntity(entities); Map<String,CslRole> roleMap=indexRole(roles);
            List<CslGroup> groups=ast.groups().stream().map(x->group(x,entityMap,roleMap)).toList();
            List<CslCompatibility> compatibility=ast.compatibilities().stream().map(this::compatibility).toList();
            Map<String,CslGroup> declaredGroups=indexGroup(groups);
            groups=groups.stream().map(g->new CslGroup(g.name(),g.specializes(),g.attributes(),
                    g.members(),g.roles(),g.entities(),
                    g.members().stream().filter(m->declaredGroups.containsKey(m.type()))
                            .map(m->new CslSubgroupMembership(declaredGroups.get(m.type()),m.multiplicity())).toList(),
                    compatibility.stream().filter(c->c.groupName().equals(g.name())).toList(),
                    g.roleEntityRelations(),g.cardinalityConstraints(),g.isOrganizationalContext())).toList();
            Map<String,CslGroup> groupMap=indexGroup(groups);
            List<CslRelation> relations=new ArrayList<>();
            if (!ast.version().startsWith("v4."))
            for(var source:ast.groups()) for(var member:source.members()) {
                if (roleMap.containsKey(member.type()) || groupMap.containsKey(member.type())
                        || (source.organizationalContext() && entityMap.containsKey(member.type()))) {
                    relations.add(new CslRelation(RelationKind.COMPOSITION,
                            CslContainment.relationName(source.name(), member.type()),
                            new CslEndpoint(source.name(), CslCardinality.bounded(1, 1),
                                    Optional.of(source.organizationalContext()
                                            ? "orgContext" : CslContainment.wholeRoleName())),
                            new CslEndpoint(member.type(), cardinality(member.multiplicity()),
                                    Optional.of(lowerFirst(member.type())))));
                }
            }
            for(var x:ast.relations())relations.add(relation(x));
            for(var source:ast.groups()) for(var member:source.members()) {
                if(!source.organizationalContext() && entityMap.containsKey(member.type())) {
                    error(member.location(), "Entity '"+member.type()+"' cannot be a member of Group '"
                            +source.name()+"'; declare an explicit association, aggregation, or composition instead");
                }
            }
            List<CslGeneralization> gens=new ArrayList<>(); ast.entities().forEach(x->x.specializes().ifPresent(p->gens.add(new CslGeneralization(x.name(),p)))); ast.roles().forEach(x->x.parentRoles().forEach(p->gens.add(new CslGeneralization(x.name(),p)))); ast.groups().forEach(x->x.specializes().ifPresent(p->gens.add(new CslGeneralization(x.name(),p))));
            List<CslInvariant> invariants = ast.invariants().stream()
                    .map(x -> new CslInvariant(x.contextType(), x.name(), x.expression())).toList();
            Set<String> classifiers = new LinkedHashSet<>();
            entities.forEach(x -> classifiers.add(x.name())); roles.forEach(x -> classifiers.add(x.name()));
            groups.forEach(x -> classifiers.add(x.name()));
            Set<String> invariantNames = new LinkedHashSet<>();
            for (var x : ast.invariants()) {
                if (!classifiers.contains(x.contextType()))
                    error(x.location(), "unknown OCL context classifier '" + x.contextType() + "'");
                if (!invariantNames.add(x.contextType() + "::" + x.name()))
                    error(x.location(), "duplicate OCL invariant '" + x.contextType() + "::" + x.name() + "'");
            }
            return new CslModel(ast.version(),ast.name(),enumList,entities,roles,groups,relations,
                    List.of(),compatibility,gens,invariants,List.copyOf(namedDataTypes.values()));
        }
        private CslGroup group(CslGroupCS x,Map<String,CslEntity> entities,Map<String,CslRole> roles){
            List<CslGroupMember> members=x.members().stream().map(m->new CslGroupMember(m.type(),
                    ast.version().startsWith("v4.") ? CslCardinality.unlimited(0)
                            : cardinality(m.multiplicity()))).toList();
            List<CslRoleMembership> rp=members.stream().filter(m->roles.containsKey(m.type())).map(m->new CslRoleMembership(m.type(),m.multiplicity())).toList();
            List<CslEntityMembership> ep=members.stream().filter(m->entities.containsKey(m.type())).map(m->new CslEntityMembership(m.type(),m.multiplicity())).toList();
            return new CslGroup(x.name(),x.specializes(),attributes(x.name(),x.attributes()),
                    members,rp,ep,List.of(),List.of(),List.of(),List.of(),x.organizationalContext());
        }
        private CslRelation relation(CslRelationCS x) {
            List<CslEndpoint> ends = x.endpoints().stream()
                    .map(v -> new CslEndpoint(v.type(), cardinality(v.multiplicity()), v.roleName())).toList();
            return new CslRelation(RelationKind.fromSource(x.kind()), x.name(), ends);
        }
        private CslCompatibility compatibility(CslCompatibilityCS x){
            return new CslCompatibility(x.fromRole(),x.toRole(),CslCompatibilityType.COMPATIBLE,
                    CslScope.INTER_GROUP,true,true,Objects.toString(x.groupName(),"__model__"));
        }
        private List<CslAttribute> attributes(String owner, List<CslAttributeCS> src) {
            Map<String,CslAttribute> out = new LinkedHashMap<>();
            for (var x : src) {
                if (x.optional() && x.explicitRequired()) {
                    error(x.location(), "attribute '" + x.name()
                            + "' cannot be both optional and required");
                    continue;
                }
                if (out.containsKey(x.name())) {
                    error(x.location(), "duplicate attribute '" + x.name() + "' in '" + owner + "'");
                    continue;
                }
                Optional<CslDataType> t = CslPrimitiveType.fromSource(x.typeName()).map(v -> (CslDataType) v)
                        .or(() -> Optional.ofNullable(enums.get(x.typeName())))
                        .or(() -> Optional.ofNullable(namedDataTypes.get(x.typeName())));
                if (t.isEmpty()) {
                    error(x.location(), "unknown attribute type '" + x.typeName() + "'");
                    continue;
                }
                if (x.defaultValue().isPresent()) {
                    String defVal = x.defaultValue().get();
                    if (t.get() instanceof CslEnum cslEnum) {
                        if (!cslEnum.literals().contains(defVal)) {
                            error(x.location(), "default value '" + defVal + "' is not a literal of enum '"
                                    + cslEnum.name() + "'; valid literals are " + cslEnum.literals());
                        }
                    } else if (!validPrimitiveDefault(t.get().sourceName(), defVal)) {
                        error(x.location(), "default value '" + defVal + "' is not valid for type '"
                                + t.get().sourceName() + "'");
                    }
                }
                out.put(x.name(), new CslAttribute(x.name(), t.get(), x.optional(), x.mutable(), x.defaultValue()));
            }
            return List.copyOf(out.values());
        }
        private CslCardinality cardinality(CslCardinalityCS x){
            try{
                int min=new BigInteger(x.min()).intValueExact();
                CslCardinality result=x.max().isEmpty()?CslCardinality.unlimited(min):CslCardinality.bounded(min,new BigInteger(x.max().get()).intValueExact());
                // Multiplicity [0] or [0..0] means no members are ever allowed — reject as meaningless.
                if(!ast.version().startsWith("v4.") && result.min()==0&&result.max().isPresent()&&result.max().getAsInt()==0)
                    error(x.location(),"multiplicity [0..0] is not meaningful; remove the member declaration or use [0..1] for optional membership");
                return result;
            }catch(RuntimeException e){error(x.location(),"invalid cardinality");return CslCardinality.unlimited(0);}
        }
        private static Map<String,CslEntity> indexEntity(List<CslEntity>x){Map<String,CslEntity>m=new HashMap<>();x.forEach(v->m.put(v.name(),v));return m;}
        private static Map<String,CslRole> indexRole(List<CslRole>x){Map<String,CslRole>m=new HashMap<>();x.forEach(v->m.put(v.name(),v));return m;}
        private static Map<String,CslGroup> indexGroup(List<CslGroup>x){Map<String,CslGroup>m=new HashMap<>();x.forEach(v->m.put(v.name(),v));return m;}
        void error(CslSourceLocationCS l,String m){errors.add(new SemanticError(l,m));}

        private static String lowerFirst(String value) {
            if (value == null || value.isEmpty()) return value;
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }

        private static boolean validPrimitiveDefault(String type, String value) {
            return switch (type) {
                case "Boolean" -> value.equals("true") || value.equals("false");
                case "Integer" -> value.matches("-?[0-9]+");
                case "Real" -> value.matches("-?[0-9]+(?:\\.[0-9]+)?");
                case "String" -> value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")));
                default -> true;
            };
        }
    }
}

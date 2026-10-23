package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;
import static org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionMappingSpecification.Node;

/** Bounded M0 interpreter for the reviewed case dialect. Source qualification is
 * outside this interpreter; all writes use the existing native transaction Port.
 * It owns neither a second state nor an execution/goal/history engine. */
public final class AuctionRuntimeRules {
    public static final String CROSSWALK="SCHEME_ARTIFACT_CROSSWALK_V1", FIELD_CUT="LOCKED_FIELD_CUT_V1";
    public static final String REMOVAL="R06_AUTHORITATIVE_REMOVAL_V1", LIFETIME="R06_EXPECTED_LIFETIME_V1";
    private static final Map<String,String> EVIDENCE=Map.of(
            "schemeUid belongs to organizationUid and its auction.Id equals auctionId; creation/discovery resolves this exact workspaceUid/artifactUid",CROSSWALK,
            "all five values share the same accepted source cut; internal-field access is authorized and available",FIELD_CUT);
    public record Input(String inputRef,Map<String,Object> values,String sourceCut,Map<String,String> proofs) {
        public Input {values=SemanticJson.object(values);proofs=Map.copyOf(proofs);if(sourceCut==null||sourceCut.isBlank())throw new IllegalArgumentException("SOURCE_CUT_REQUIRED");}
    }
    public record Outcome(String rule,String status,String nativeKey,MObject object,String diagnostic) { }
    private static final class Incomplete extends RuntimeException {Incomplete(String reason){super(reason);}}
    private final AuctionMappingSpecification specification;
    private final MModel model;
    private final Map<String,Node> rules=new LinkedHashMap<>(),inputs=new LinkedHashMap<>(),converters=new LinkedHashMap<>();

    public AuctionRuntimeRules(AuctionMappingSpecification specification,MModel model) {
        this.specification=Objects.requireNonNull(specification);this.model=Objects.requireNonNull(model);
        for(var input:specification.root().child("runtimeMapping").child("adapterInputs").children("input"))inputs.put(input.attr("id"),input);
        for(var converter:specification.root().child("runtimeMapping").child("conversions").children("converter")) {
            validateConverter(converter);converters.put(converter.attr("id"),converter);
        }
        for(var rule:specification.runtimeRules()) {
            if(rules.putIfAbsent(rule.attr("inputRef"),rule)!=null)throw rule.location().error("M0_INPUT_RULE_AMBIGUOUS");
            var input=inputs.get(rule.attr("inputRef"));if(input==null)throw rule.location().error("M0_INPUT_UNRESOLVED");
            Set<String> fields=new HashSet<>();input.children("field").forEach(f->fields.add(f.attr("name")));
            if(!rule.children("reconcileRemoval").isEmpty()) {
                var removal=rule.child("reconcileRemoval");
                if(!rule.attr("id").equals("R06")||!input.attr("id").equals("in-removal")||!removal.attr("targetTypeByKind").equals("organization:AuctionOrg group:auctionGroup roleEnactment:existing-enactment-type auctionArtifact:Auction")||!removal.attr("identity").equals("same-native-key-as-R01-R04")||!removal.attr("remove").equals("projection-instance-and-its-context-membership"))throw rule.location().error("M0_REMOVAL_POLICY_UNSUPPORTED");
                if(!new HashSet<>(words(rule.child("whenIn").attr("values"))).equals(Set.of("organization","group","roleEnactment","auctionArtifact")))throw rule.location().error("M0_REMOVAL_KIND_UNSUPPORTED");
                if(!rule.child("whenIn").attr("field").equals("objectKind")||rule.children("whenIn").size()!=1||rule.children("reconcileRemoval").size()!=1||rule.children().stream().anyMatch(n->!Set.of("note","trace","whenIn","reconcileRemoval").contains(n.name())))throw rule.location().error("M0_REMOVAL_HANDLER_UNSUPPORTED");
                continue;
            }
            for(var node:rule.children())switch(node.name()) {
                case "note","trace" -> { }
                case "when" -> {field(fields,node,"field");expected(node.attr("equals"),input.children("field").stream().filter(f->f.attr("name").equals(node.attr("field"))).findFirst().orElseThrow().attr("type"));}
                case "whenIn" -> {field(fields,node,"field");words(node.attr("values"));}
                case "whenFieldsEqual" -> {field(fields,node,"left");field(fields,node,"right");}
                case "requireEvidence" -> {if(!EVIDENCE.containsKey(node.attr("fact")))throw node.location().error("M0_EVIDENCE_QUALIFIER_UNSUPPORTED");}
                case "requireResolvedInstance" -> {target(node.attr("targetType"),node);keyFields(node,"byFields",fields);}
                case "upsertInstance" -> {
                    if(node.attributes().containsKey("targetType"))target(node.attr("targetType"),node);else field(fields,node,"targetTypeFrom");
                    keyFields(node,"keyFields",fields);requireIdentity(node,input);
                    if(node.attributes().containsKey("contextType")) {
                        target(node.attr("contextType"),node);keyFields(node,"contextKeyFields",fields);
                        if(!Set.of("orgctx","roles","entities").contains(node.attr("membership")))throw node.location().error("M0_MEMBERSHIP_UNSUPPORTED");
                    }
                    if(node.attributes().containsKey("playerFrom"))field(fields,node,"playerFrom");
                    for(var assign:node.children("assign"))validateAssign(assign,fields,node.optional("targetType"),input.attr("id"));
                }
                case "assignSnapshot" -> {
                    var type=target(node.attr("targetType"),node);keyFields(node,"keyFields",fields);requireIdentity(node,input);
                    if(!node.attr("atomic").equals("true")||!node.attr("missingValuePolicy").equals("incomplete"))throw node.location().error("M0_SNAPSHOT_POLICY_UNSUPPORTED");
                    for(var assign:node.children("assign"))validateAssign(assign,fields,node.attr("targetType"),input.attr("id"));
                    var expected=type.allAttributes().stream().filter(a->"true".equals(a.getAnnotationValue(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE,"mutable"))).map(MAttribute::name).collect(java.util.stream.Collectors.toSet());
                    var assigned=node.children("assign").stream().map(a->a.attr("target")).collect(java.util.stream.Collectors.toSet());
                    if(!assigned.equals(expected)||assigned.size()!=node.children("assign").size())throw node.location().error("M0_FULL_MUTABLE_STATE_ASSIGNMENTS_REQUIRED");
                }
                default -> throw node.location().error("M0_HANDLER_UNSUPPORTED:"+node.name());
            }
            if(rule.children("upsertInstance").size()+rule.children("assignSnapshot").size()!=1)throw rule.location().error("M0_EXACTLY_ONE_WRITE_REQUIRED");
        }
        if(rules.size()!=6)throw specification.root().location().error("M0_RULE_COVERAGE_REQUIRED");
    }

    public Outcome execute(Port port,Input input)throws UseApiException {
        var rule=rules.get(input.inputRef());if(rule==null)throw new IllegalArgumentException("M0_INPUT_NOT_EXECUTABLE:"+input.inputRef());
        MObject object=null;String key="";
        try {
            validateInput(input);
            if(input.inputRef().equals("in-removal")) {
                key=string(input.values(),"nativeKey");object=port.find(key);
                if(object==null)return new Outcome("R06","ABSENT",key,null,"UNKNOWN_REMOVAL_KEY");
                String kind=string(input.values(),"objectKind"),expected=switch(kind){case "organization"->"AuctionOrg";case "group"->"auctionGroup";case "auctionArtifact"->"Auction";case "roleEnactment"->object.cls().name();default->throw new Incomplete("REMOVAL_KIND_UNSUPPORTED");};
                if(kind.equals("roleEnactment")&&!object.cls().isSubClassifierOf(model.getClass("RoleOccurrence")))throw new Incomplete("REMOVAL_ROLE_TYPE_REQUIRED");
                if(!object.cls().name().equals(expected)||!string(input.values(),"runId").equals(port.metadata(object,"auctionRun"))||!key.equals(port.metadata(object,"auctionRuleKey")))throw new Incomplete("REMOVAL_OWNER_MISMATCH");
                if(!string(input.values(),"sourceRevision").equals(input.proofs().get(REMOVAL))||port.metadata(object,"auctionLifetimeToken").isBlank()||!port.metadata(object,"auctionLifetimeToken").equals(input.proofs().get(LIFETIME)))throw new Incomplete("AUTHORITATIVE_REMOVAL_PROOF_UNAVAILABLE");
                if(kind.equals("roleEnactment"))port.deleteRebindableRole(key);else port.delete(key);
                return new Outcome("R06","MAPPED",key,object,"AUTHORITATIVE_PROJECTION_REMOVAL");
            }
            for(var when:rule.children("when"))if(!Objects.equals(input.values().get(when.attr("field")),expected(when.attr("equals"),fieldType(input.inputRef(),when.attr("field")))))return filtered(rule);
            for(var when:rule.children("whenIn"))if(!words(when.attr("values")).contains(input.values().get(when.attr("field"))))return filtered(rule);
            for(var when:rule.children("whenFieldsEqual"))if(!Objects.equals(input.values().get(when.attr("left")),input.values().get(when.attr("right"))))return filtered(rule);
            for(var required:rule.children("requireResolvedInstance"))if(port.find(key(required.attr("targetType"),required.attr("byFields"),input.values()))==null)throw new Incomplete("REQUIRED_INSTANCE_UNRESOLVED:"+required.attr("targetType"));
            for(var required:rule.children("requireEvidence"))if(input.proofs().getOrDefault(EVIDENCE.get(required.attr("fact")),"").isBlank())throw new Incomplete("REQUIRED_EVIDENCE_UNAVAILABLE:"+EVIDENCE.get(required.attr("fact")));
            if(!rule.children("assignSnapshot").isEmpty()) {
                var snapshot=rule.child("assignSnapshot");key=key(snapshot.attr("targetType"),snapshot.attr("keyFields"),input.values());object=port.find(key);
                if(object==null)throw new Incomplete("SNAPSHOT_INSTANCE_UNRESOLVED");
                var values=assignments(snapshot,input);
                for(var assignment:values.entrySet())write(port,object,assignment.getKey(),assignment.getValue());
                observeAssignments(port,object,snapshot,input);
            } else {
                var upsert=rule.child("upsertInstance");String type=upsert.attributes().containsKey("targetType")?upsert.attr("targetType"):string(input.values(),upsert.attr("targetTypeFrom"));
                target(type,upsert);key=key(type,upsert.attr("keyFields"),input.values());object=port.find(key);
                var assignments=assignments(upsert,input);
                if(object!=null)for(var assign:upsert.children("assign"))if(assign.optional("write").equals("create-or-identity-check")) {
                    var before=port.currentValue(object,assign.attr("target"));if(before!=null&&!before.isUndefined()&&!before.equals(assignments.get(assign.attr("target"))))throw new IllegalArgumentException("M0_IMMUTABLE_ID_CHANGED:"+assign.attr("target"));
                }
                MObject context=null,player=null;
                if(upsert.attributes().containsKey("contextType")) {
                    context=port.find(key(upsert.attr("contextType"),upsert.attr("contextKeyFields"),input.values()));if(context==null)throw new Incomplete("CONTEXT_INSTANCE_UNRESOLVED");
                }
                if(upsert.attributes().containsKey("playerFrom")) {
                    player=port.find(string(input.values(),upsert.attr("playerFrom")));if(player==null)throw new Incomplete("ACTUAL_PLAYER_UNRESOLVED");
                    if(!player.cls().isSubClassifierOf(model.getClass("RuntimePlayer")))throw new IllegalArgumentException("M0_PLAYER_TYPE_REQUIRED");
                }
                boolean created=object==null;object=port.upsert(key,type,type+"_"+SemanticJson.hash(player==null?key:List.of(key,input.sourceCut())).substring(0,20));
                if(created||port.metadata(object,"auctionLifetimeToken").isBlank())port.metadata(object,"auctionLifetimeToken",SemanticJson.hash(List.of(key,input.sourceCut(),input.values().get("runId"))));
                if(created&&object.cls().isSubClassifierOf(model.getClass("DomainEntity")))for(var attr:object.cls().allAttributes())if(attr.getDeriveExpression()==null) {
                    port.value(object,attr.name(),UndefinedValue.instance);port.metadata(object,"propertyAbsent:"+attr.name(),"true");
                }
                if(context!=null)switch(upsert.attr("membership")) {
                    case "orgctx" -> port.connect(AuctionUseModelAdapter.CONTEXT_SCOPES,context,object);
                    case "entities" -> port.connect(AuctionUseModelAdapter.ENTITY_SCOPES,context,object);
                    case "roles" -> {
                        if(player==null)throw new IllegalArgumentException("M0_ROLE_PLAYER_REQUIRED");
                        port.connect("InContext",context,object);port.connect("Plays",player,object);
                        port.metadata(object,"roleContext",port.metadata(context,"semanticId"));port.metadata(object,"rolePlayer",port.metadata(player,"semanticId"));
                    }
                    default -> throw upsert.location().error("M0_MEMBERSHIP_UNSUPPORTED");
                }
                for(var assignment:assignments.entrySet())write(port,object,assignment.getKey(),assignment.getValue());
                observeAssignments(port,object,upsert,input);
            }
            port.metadata(object,"auctionRule:"+rule.attr("id"),"MAPPED");port.metadata(object,"auctionCut:"+rule.attr("id"),input.sourceCut());
            port.metadata(object,"auctionRuleKey",key);port.metadata(object,"auctionRun",string(input.values(),"runId"));
            port.metadata(object,"auctionEvidence:"+rule.attr("id"),new String(CanonicalJson.encode(input.proofs()),java.nio.charset.StandardCharsets.UTF_8));
            port.metadata(object,"auctionInput:"+rule.attr("id"),new String(CanonicalJson.encode(input.values()),java.nio.charset.StandardCharsets.UTF_8));
            return new Outcome(rule.attr("id"),"MAPPED",key,object,"");
        }catch(Incomplete incomplete) {
            if(object!=null)port.metadata(object,"auctionRule:"+rule.attr("id"),"INCOMPLETE:"+incomplete.getMessage());
            return new Outcome(rule.attr("id"),"INCOMPLETE",key,object,incomplete.getMessage());
        }
    }
    private void validateInput(Input input) {
        var schema=inputs.get(input.inputRef());var allowed=new HashSet<String>();
        for(var field:schema.children("field")) {
            String name=field.attr("name"),type=field.attr("type");allowed.add(name);Object value=input.values().get(name);
            if(value==null){if(field.attr("required").equals("true"))throw new Incomplete("REQUIRED_FIELD_UNAVAILABLE:"+name);continue;}
            boolean valid=switch(type) {
                case "String" -> value instanceof String;
                case "Boolean" -> value instanceof Boolean;
                case "Integer" -> integer(value);
                case "JavaBinary64","JasonAtom" -> value instanceof Map<?,?>;
                default -> false;
            };
            if(!valid)throw new Incomplete("INPUT_TYPE_MISMATCH:"+name+":"+type);
        }
        if(!allowed.containsAll(input.values().keySet()))throw new IllegalArgumentException("M0_UNKNOWN_INPUT_FIELD");
    }
    private Map<String,Value> assignments(Node write,Input input) {
        var values=new LinkedHashMap<String,Value>();
        for(var assign:write.children("assign")) {
            String name=assign.attr("target");Value value=decode(converters.get(assign.attr("converter")),input.values().get(assign.attr("sourceField")));
            if(values.putIfAbsent(name,value)!=null)throw assign.location().error("M0_DUPLICATE_ASSIGNMENT");
            String type=write.attributes().containsKey("targetType")?write.attr("targetType"):string(input.values(),write.attr("targetTypeFrom"));
            var attr=model.getClass(type).attribute(name,true);if(attr==null||!attr.type().equals(value.type()))throw assign.location().error("M0_NATIVE_ATTRIBUTE_TYPE_MISMATCH");
        }
        return values;
    }
    private void write(Port port,MObject object,String attribute,Value value)throws UseApiException {
        port.value(object,attribute,value);port.metadata(object,"observedValueHash:"+attribute,CanonicalObservationMappingBindings.valueHash(value));port.metadata(object,"propertyAbsent:"+attribute,"false");
    }
    private static void observeAssignments(Port port,MObject object,Node write,Input input) {
        for(var assign:write.children("assign"))port.metadata(object,"auctionObserved:"+assign.attr("target"),new String(CanonicalJson.encode(Map.of(
                "rule",write.location().ruleId(),"sourceField",assign.attr("sourceField"),"converter",assign.attr("converter"),"cut",input.sourceCut(),"proofs",input.proofs())),java.nio.charset.StandardCharsets.UTF_8));
    }
    public Value decode(String converter,Object value) {var node=converters.get(converter);if(node==null)throw new IllegalArgumentException("M0_CONVERTER_UNRESOLVED");return decode(node,value);}
    private Value decode(Node converter,Object value) {
        try{return org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeValueConverters.decode(neutral(converter),value);}
        catch(org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeValueConverters.Unavailable unavailable){throw new Incomplete(unavailable.getMessage());}
    }
    private void validateConverter(Node converter){org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeValueConverters.validate(neutral(converter));}
    private static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node neutral(Node node) {
        var at=node.location();return new org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node(node.name(),node.attributes(),node.text(),
            node.children().stream().map(AuctionRuntimeRules::neutral).toList(),new org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Location(at.source(),at.line(),at.column(),at.ruleId()));
    }
    private void validateAssign(Node assign,Set<String> fields,String type,String input) {
        field(fields,assign,"sourceField");if(!converters.containsKey(assign.attr("converter")))throw assign.location().error("M0_CONVERTER_UNRESOLVED");
        if(!converters.get(assign.attr("converter")).attr("inputType").equals(fieldType(input,assign.attr("sourceField"))))throw assign.location().error("M0_CONVERTER_INPUT_TYPE_MISMATCH");
        if(!assign.optional("write").isBlank()&&!assign.attr("write").equals("create-or-identity-check"))throw assign.location().error("M0_ATTRIBUTE_WRITE_POLICY_UNSUPPORTED");
        if(!type.isBlank()&&model.getClass(type).attribute(assign.attr("target"),true)==null)throw assign.location().error("M0_TARGET_ATTRIBUTE_UNRESOLVED");
    }
    private static boolean integer(Object value){try{return value instanceof Number number&&new java.math.BigDecimal(number.toString()).toBigIntegerExact().signum()>=0;}catch(ArithmeticException|NumberFormatException invalid){return false;}}
    private static void requireIdentity(Node write,Node input) {
        Set<String> required=switch(input.attr("kind")) {
            case "OrganizationSnapshot" -> Set.of("runId","organizationUid");
            case "GroupSnapshot" -> Set.of("runId","groupUid");
            case "RoleEnactmentSnapshot" -> Set.of("runId","groupUid","agentUid","roleName");
            case "ArtifactSnapshot","AuctionStateSnapshot" -> Set.of("runId","workspaceUid","artifactUid");
            default -> throw input.location().error("M0_INPUT_KIND_UNSUPPORTED");
        };
        if(!Set.copyOf(words(write.attr("keyFields"))).equals(required))throw write.location().error("M0_REQUIRED_IDENTITY_FIELDS");
    }
    private MClass target(String name,Node at){var type=model.getClass(name);if(type==null||type.isAbstract())throw at.location().error("M0_TARGET_TYPE_UNRESOLVED:"+name);return type;}
    private String fieldType(String input,String field){return inputs.get(input).children("field").stream().filter(n->n.attr("name").equals(field)).findFirst().orElseThrow().attr("type");}
    private static void field(Set<String> fields,Node node,String attribute){if(!fields.contains(node.attr(attribute)))throw node.location().error("M0_INPUT_FIELD_UNRESOLVED:"+node.attr(attribute));}
    private static void keyFields(Node node,String attribute,Set<String> fields){for(String name:words(node.attr(attribute)))if(!fields.contains(name))throw node.location().error("M0_KEY_FIELD_UNRESOLVED:"+name);}
    private static Object expected(String text,String type){if(type.equals("Boolean")){if(!Set.of("true","false").contains(text))throw new IllegalArgumentException("M0_BOOLEAN_CONDITION_REQUIRED");return Boolean.valueOf(text);}if(!type.equals("String"))throw new IllegalArgumentException("M0_CONDITION_TYPE_UNSUPPORTED");return text;}
    public static String key(String type,String fields,Map<String,Object> values) {var selected=new TreeMap<String,Object>();for(String field:words(fields)){Object value=values.get(field);if(value==null)throw new Incomplete("KEY_FIELD_UNAVAILABLE:"+field);selected.put(field,value);}return "auction:m0:"+SemanticJson.hash(Map.of("type",type,"key",selected));}
    private static List<String> words(String text){var names=Arrays.asList(text.trim().split("\\s+"));if(names.isEmpty()||names.contains("")||names.stream().distinct().count()!=names.size())throw new IllegalArgumentException("M0_NONEMPTY_UNIQUE_WORDS_REQUIRED");return List.copyOf(names);}
    private static String string(Map<String,Object> values,String field){if(!(values.get(field) instanceof String text)||text.isBlank())throw new Incomplete("STRING_IDENTITY_REQUIRED:"+field);return text;}
    private static Outcome filtered(Node rule){return new Outcome(rule.attr("id"),"FILTERED","",null,"CONDITION_NOT_MATCHED");}
}

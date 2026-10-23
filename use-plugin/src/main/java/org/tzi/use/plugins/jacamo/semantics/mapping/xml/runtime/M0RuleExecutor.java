package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Qualified-row M0 instructions on the existing transaction Port. Admission and source joins are upstream. */
public final class M0RuleExecutor {
    public record Input(String inputRef,String epoch,String authorityIdentity,String sourceCut,Completeness quality,Map<String,Object> values,Map<String,String> proofs,Set<String> sourceAliases) {
        public Input(String inputRef,String epoch,String authorityIdentity,String sourceCut,Completeness quality,Map<String,Object> values,Map<String,String> proofs) {
            this(inputRef,epoch,authorityIdentity,sourceCut,quality,values,proofs,Set.of(authorityIdentity));
        }
        public Input {
            for(String value:List.of(inputRef,epoch,authorityIdentity,sourceCut))if(value.isBlank())throw new IllegalArgumentException("M0_ROW_IDENTITY_REQUIRED");
            Objects.requireNonNull(quality);values=SemanticJson.object(values);proofs=Map.copyOf(proofs);sourceAliases=Set.copyOf(sourceAliases);
            if(!sourceAliases.contains(authorityIdentity)||sourceAliases.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("M0_SOURCE_ALIASES_INVALID");
        }
    }
    public record Outcome(String rule,String status,String nativeKey,MObject object,String diagnostic) { }
    private record Rule(Node node,Node input,Node write,Map<String,Node> fields) { }
    private final MModel model;private final String program;
    private final Map<String,Rule> rules=new LinkedHashMap<>();private final Map<String,Node> converters=new LinkedHashMap<>();
    public M0RuleExecutor(MappingSpecification specification,MModel model,String program) {
        this.model=Objects.requireNonNull(model);this.program=Objects.requireNonNull(program);
        var runtime=specification.root().child("runtime");var inputs=new LinkedHashMap<String,Node>();
        runtime.child("inputs").children("input").forEach(n->inputs.put(n.attr("id"),n));
        for(Node converter:runtime.child("converters").children("converter")){RuntimeValueConverters.validate(converter);converters.put(converter.attr("id"),converter);}
        for(Node node:runtime.children("rule")) {
            var input=inputs.get(node.attr("inputRef"));if(input==null)throw node.location().error("M0_INPUT_UNRESOLVED");
            if(!Set.of("PHYSICAL","LOGICAL_ROLE").contains(input.attr("identityKind")))throw input.location().error("M0_IDENTITY_KIND_UNSUPPORTED");
            if(!input.optional("reconcileMissing").isEmpty()&&(!input.attr("reconcileMissing").equals("retire-logical-role")||!input.attr("identityKind").equals("LOGICAL_ROLE")))throw input.location().error("M0_RECONCILIATION_POLICY_UNSUPPORTED");
            if(!input.optional("reconcileScopeField").isEmpty()&&input.optional("reconcileMissing").isEmpty())throw input.location().error("M0_SCOPE_REQUIRES_LOGICAL_ROLE_RECONCILIATION");
            var fields=new LinkedHashMap<String,Node>();
            for(Node field:input.children("field")) {
                if(!field.attr("name").matches("[A-Za-z_][A-Za-z0-9_]*")||!Set.of("String","Boolean","Integer","JavaBinary64","JasonAtom").contains(field.attr("type"))||fields.putIfAbsent(field.attr("name"),field)!=null)throw field.location().error("M0_INPUT_FIELD_INVALID");
            }
            var writes=node.children().stream().filter(n->Set.of("upsertInstance","assignSnapshot","removeInstance").contains(n.name())).toList();
            if(writes.size()!=1)throw node.location().error("M0_EXACTLY_ONE_WRITE_REQUIRED");var write=writes.getFirst();
            var type=target(write.attr("targetType"),write);keys(write.attr("keyFields"),fields,write);
            if(!new HashSet<>(words(input.attr("identityFields"))).equals(new HashSet<>(words(write.attr("keyFields")))))throw write.location().error("M0_INPUT_IDENTITY_FIELDS_MISMATCH");
            for(String key:words(write.attr("keyFields")))if(!fields.get(key).attr("required").equals("true")||!fields.get(key).attr("type").equals("String"))throw write.location().error("M0_REQUIRED_STRING_IDENTITY");
            if(input.attr("identityKind").equals("LOGICAL_ROLE")&&!type.isSubClassifierOf(model.getClass("RoleOccurrence")))throw write.location().error("M0_LOGICAL_REENACTMENT_REQUIRES_ROLE");
            for(Node operation:node.children())switch(operation.name()) {
                case "when"->{field(operation.attr("field"),fields,operation);expected(operation.attr("equals"),fields.get(operation.attr("field")).attr("type"));}
                case "whenIn"->{field(operation.attr("field"),fields,operation);words(operation.attr("values"));}
                case "whenFieldsEqual"->{field(operation.attr("left"),fields,operation);field(operation.attr("right"),fields,operation);}
                case "requireEvidence"->{if(operation.attr("proof").isBlank())throw operation.location().error("M0_PROOF_ID_REQUIRED");}
                case "requireResolvedInstance"->{target(operation.attr("targetType"),operation);field(operation.attr("identityFrom"),fields,operation);}
                case "upsertInstance","assignSnapshot","removeInstance"->{ }
                default->throw operation.location().error("M0_HANDLER_UNSUPPORTED:"+operation.name());
            }
            if(write.name().equals("assignSnapshot")&&(!write.attr("atomic").equals("true")||!write.attr("missingValuePolicy").equals("incomplete")))throw write.location().error("M0_SNAPSHOT_POLICY_UNSUPPORTED");
            var assigned=new HashSet<String>();
            for(Node assign:write.children("assign")) {
                field(assign.attr("sourceField"),fields,assign);var converter=converters.get(assign.attr("converter"));var attribute=type.attribute(assign.attr("target"),true);
                if(converter==null||!converter.attr("inputType").equals(fields.get(assign.attr("sourceField")).attr("type"))||attribute==null||attribute.isDerived()||!attribute.type().toString().equals(converter.attr("outputType"))||!assigned.add(assign.attr("target")))throw assign.location().error("M0_ASSIGNMENT_TYPE_OR_DUPLICATE");
                if(!assign.optional("write").isEmpty()&&!assign.attr("write").equals("create-or-identity-check"))throw assign.location().error("M0_WRITE_POLICY_UNSUPPORTED");
            }
            if(write.name().equals("assignSnapshot")) {
                var mutable=type.allAttributes().stream().filter(a->"true".equals(a.getAnnotationValue(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.ATTRIBUTE,"mutable"))).map(MAttribute::name).collect(java.util.stream.Collectors.toSet());
                if(!assigned.equals(mutable))throw write.location().error("M0_FULL_MUTABLE_STATE_ASSIGNMENTS_REQUIRED");
            }
            if(!write.optional("contextType").isBlank()) {
                target(write.attr("contextType"),write);field(write.attr("contextFrom"),fields,write);
                if(!Set.of("orgctx","entities","roles").contains(write.attr("membership")))throw write.location().error("M0_MEMBERSHIP_UNSUPPORTED");
                if(write.attr("membership").equals("roles")&&write.optional("playerFrom").isBlank())throw write.location().error("M0_ROLE_PLAYER_BINDING_REQUIRED");
            }
            if(!write.optional("playerFrom").isBlank())field(write.attr("playerFrom"),fields,write);
            if(!write.optional("aliasFrom").isBlank())field(write.attr("aliasFrom"),fields,write);
            rules.put(node.attr("id"),new Rule(node,input,write,Collections.unmodifiableMap(fields)));
        }
        if(rules.isEmpty())throw runtime.location().error("M0_EXECUTABLE_RULE_REQUIRED");
    }
    public Outcome execute(Port port,String ruleId,Input input)throws UseApiException {
        var rule=rules.get(ruleId);if(rule==null||!rule.node.attr("inputRef").equals(input.inputRef()))throw new IllegalArgumentException("M0_RULE_INPUT_MISMATCH");
        Node write=rule.write;String type=write.attr("targetType"),key="";MObject object=null;
        try {
            key=key(type,write.attr("keyFields"),input);object=port.find(key);
            if(object!=null)owner(port,object,input);
            validateInput(rule,input);
            for(Node when:rule.node.children("when"))if(!Objects.equals(input.values.get(when.attr("field")),expected(when.attr("equals"),rule.fields.get(when.attr("field")).attr("type"))))return outcome(rule,"FILTERED",key,object,"CONDITION_NOT_MATCHED");
            for(Node when:rule.node.children("whenIn"))if(!words(when.attr("values")).contains(input.values.get(when.attr("field"))))return outcome(rule,"FILTERED",key,object,"CONDITION_NOT_MATCHED");
            for(Node when:rule.node.children("whenFieldsEqual"))if(!Objects.equals(input.values.get(when.attr("left")),input.values.get(when.attr("right"))))return outcome(rule,"FILTERED",key,object,"CONDITION_NOT_MATCHED");
            for(Node proof:rule.node.children("requireEvidence"))if(input.proofs.getOrDefault(proof.attr("proof"),"").isBlank())throw missing("REQUIRED_EVIDENCE_UNAVAILABLE:"+proof.attr("proof"));
            for(Node required:rule.node.children("requireResolvedInstance"))resolved(port,required.attr("targetType"),string(input.values,required.attr("identityFrom")),input);
            if(write.name().equals("removeInstance")) {
                if(!input.authorityIdentity.equals(input.proofs.get(write.attr("authorityProof"))))throw missing("AUTHORITATIVE_REMOVAL_PROOF_UNAVAILABLE");
                if(object==null)return outcome(rule,"ABSENT",key,null,"NO_CURRENT_INSTANCE");
                if(rule.input.attr("identityKind").equals("LOGICAL_ROLE"))port.deleteRebindableRole(key);else port.delete(key);
                return outcome(rule,"MAPPED",key,object,"AUTHORITATIVE_REMOVAL");
            }
            var values=new LinkedHashMap<String,Value>();
            for(Node assign:write.children("assign"))values.put(assign.attr("target"),RuntimeValueConverters.decode(converters.get(assign.attr("converter")),input.values.get(assign.attr("sourceField"))));
            if(object!=null)for(Node assign:write.children("assign"))if(assign.optional("write").equals("create-or-identity-check")) {
                var existing=port.currentValue(object,assign.attr("target"));
                if(existing!=null&&!existing.isUndefined()&&!existing.equals(values.get(assign.attr("target"))))throw new IllegalArgumentException("M0_IMMUTABLE_ID_CHANGED:"+assign.attr("target"));
            }
            MObject context=null,player=null;
            if(!write.optional("contextType").isBlank()) {
                context=resolved(port,write.attr("contextType"),string(input.values,write.attr("contextFrom")),input);
            }
            if(!write.optional("playerFrom").isBlank()) {
                player=port.find(string(input.values,write.attr("playerFrom")));
                if(player==null||!player.cls().isSubClassifierOf(model.getClass("RuntimePlayer")))throw missing("ACTUAL_PLAYER_UNAVAILABLE");
            }
            String alias=write.optional("aliasFrom").isEmpty()?"":string(input.values,write.attr("aliasFrom"));
            if(!alias.isEmpty()&&!input.sourceAliases.contains(alias))throw new IllegalArgumentException("M0_SOURCE_ALIAS_NOT_IN_VERIFIED_IDENTITIES");
            if(!alias.isEmpty()&&port.find(alias)!=null&&port.find(alias)!=object)throw new IllegalArgumentException("M0_SOURCE_ALIAS_CONFLICT");
            if(write.name().equals("assignSnapshot")&&object==null)throw missing("SNAPSHOT_INSTANCE_UNAVAILABLE");
            if(object==null) {
                object=port.upsert(key,type,type+"_"+SemanticJson.hash(rule.input.attr("identityKind").equals("LOGICAL_ROLE")?List.of(key,input.sourceCut):key).substring(0,20));
                for(var attr:object.cls().allAttributes())if(!attr.isDerived()) {port.value(object,attr.name(),UndefinedValue.instance);port.metadata(object,"propertyAbsent:"+attr.name(),"true");}
                port.metadata(object,"xml:m0:program",program);port.metadata(object,"xml:m0:epoch",input.epoch);port.metadata(object,"xml:m0:identity",input.authorityIdentity);
                port.metadata(object,"xml:m0:lifetime",SemanticJson.hash(List.of(key,input.sourceCut)));
            }
            if(context!=null)switch(write.attr("membership")) {
                case "orgctx"->port.connect(CslScopeModelAdapter.CONTEXT_SCOPES,context,object);
                case "entities"->port.connect(CslScopeModelAdapter.ENTITY_SCOPES,context,object);
                case "roles"->{port.connect("InContext",context,object);port.connect("Plays",player,object);port.metadata(object,"roleContext",port.metadata(context,"semanticId"));port.metadata(object,"rolePlayer",port.metadata(player,"semanticId"));}
                default->throw new IllegalStateException("ADMITTED_MEMBERSHIP_CHANGED");
            }
            if(!alias.isEmpty())port.alias(alias,object);
            for(var assignment:values.entrySet()) {
                port.value(object,assignment.getKey(),assignment.getValue());CanonicalObservationMappingBindings.recordPropertyWritten(port,object,object.cls().attribute(assignment.getKey(),true),assignment.getValue());
            }
            port.metadata(object,"xml:m0:key",key);port.metadata(object,"xml:m0:quality","COMPLETE");port.metadata(object,"xml:m0:rule",ruleId);port.metadata(object,"xml:m0:cut",input.sourceCut);
            return outcome(rule,"MAPPED",key,object,"");
        }catch(RuntimeValueConverters.Unavailable unavailable) {
            if(object!=null) {
                for(Node assign:write.children("assign"))if(!assign.optional("write").equals("create-or-identity-check")) {
                    port.value(object,assign.attr("target"),UndefinedValue.instance);port.metadata(object,"observedValueHash:"+assign.attr("target"),"");port.metadata(object,"propertyAbsent:"+assign.attr("target"),"true");
                }
                port.metadata(object,"xml:m0:quality","INCOMPLETE");
            }
            return outcome(rule,"INCOMPLETE",key,object,unavailable.getMessage());
        }
    }
    public boolean owns(Port port,MObject object){return program.equals(port.metadata(object,"xml:m0:program"));}
    /** Invalidate current mutable knowledge without inventing disposal or erasing immutable identity. */
    public void invalidate(Port port,MObject object,String reason)throws UseApiException {
        if(!owns(port,object))throw new IllegalArgumentException("M0_INVALIDATION_OWNER_MISMATCH");
        for(var rule:rules.values())if(object.cls().name().equals(rule.write.attr("targetType"))) {
            for(Node assignment:rule.write.children("assign"))if(!assignment.optional("write").equals("create-or-identity-check")) {
                String attribute=assignment.attr("target");port.value(object,attribute,UndefinedValue.instance);
                port.metadata(object,"propertyAbsent:"+attribute,"true");port.metadata(object,"observedValueHash:"+attribute,"");
            }
        }
        port.metadata(object,"xml:m0:quality","INCOMPLETE");port.metadata(object,"xml:m0:population","UNAVAILABLE");port.metadata(object,"xml:m0:unavailable",reason);
    }
    private MObject resolved(Port port,String type,String identity,Input input) {
        var object=port.find(identity);
        if(object==null||!object.cls().name().equals(type))throw missing("REQUIRED_INSTANCE_UNAVAILABLE:"+type);
        if(!owns(port,object)||!input.epoch.equals(port.metadata(object,"xml:m0:epoch"))||!identity.equals(port.metadata(object,"xml:m0:identity")))throw missing("REQUIRED_INSTANCE_OWNER_UNAVAILABLE");
        return object;
    }
    private void owner(Port port,MObject object,Input input) {
        if(!owns(port,object)||!input.epoch.equals(port.metadata(object,"xml:m0:epoch"))||!input.authorityIdentity.equals(port.metadata(object,"xml:m0:identity")))throw new IllegalArgumentException("M0_EXISTING_OWNER_MISMATCH");
    }
    private String key(String type,String fields,Input input) {
        var values=new TreeMap<String,Object>();for(String field:words(fields))values.put(field,string(input.values,field));
        return "xml:m0:"+SemanticJson.hash(Map.of("program",program,"epoch",input.epoch,"authorityIdentity",input.authorityIdentity,"type",type,"fields",values));
    }
    private static void validateInput(Rule rule,Input input) {
        if(!rule.fields.keySet().containsAll(input.values.keySet()))throw new IllegalArgumentException("M0_UNKNOWN_INPUT_FIELD");
        if(input.quality!=Completeness.COMPLETE)throw missing("INPUT_QUALITY_"+input.quality);
        for(Node field:rule.fields.values()) {
            Object value=input.values.get(field.attr("name"));if(value==null){if(field.attr("required").equals("true"))throw missing("REQUIRED_FIELD_UNAVAILABLE:"+field.attr("name"));continue;}
            boolean valid=switch(field.attr("type")){case "String"->value instanceof String;case "Boolean"->value instanceof Boolean;case "Integer"->value instanceof Number;case "JavaBinary64","JasonAtom"->value instanceof Map<?,?>;default->false;};
            if(valid&&field.attr("type").equals("Integer"))try{new java.math.BigDecimal(value.toString()).toBigIntegerExact();}catch(ArithmeticException|NumberFormatException invalid){valid=false;}
            if(!valid)throw missing("INPUT_TYPE_MISMATCH:"+field.attr("name"));
        }
    }
    private MClass target(String name,Node at){var type=model.getClass(name);if(type==null||type.isAbstract())throw at.location().error("M0_TARGET_TYPE_UNRESOLVED");return type;}
    private static void field(String name,Map<String,Node> fields,Node at){if(!fields.containsKey(name))throw at.location().error("M0_INPUT_FIELD_UNRESOLVED:"+name);}
    private static void keys(String value,Map<String,Node> fields,Node at){for(String name:words(value))field(name,fields,at);}
    private static List<String> words(String value){var result=List.of(value.trim().split("\\s+"));if(result.contains("")||new HashSet<>(result).size()!=result.size())throw new IllegalArgumentException("M0_UNIQUE_NONEMPTY_FIELDS_REQUIRED");return result;}
    private static Object expected(String text,String type){if(type.equals("String"))return text;if(type.equals("Boolean")&&Set.of("true","false").contains(text))return Boolean.parseBoolean(text);throw new IllegalArgumentException("M0_LITERAL_COMPARISON_TYPE_UNSUPPORTED");}
    private static String string(Map<String,Object> values,String key){var value=values.get(key);if(!(value instanceof String text)||text.isBlank())throw missing("STRING_IDENTITY_REQUIRED:"+key);return text;}
    private static RuntimeValueConverters.Unavailable missing(String reason){return new RuntimeValueConverters.Unavailable(reason);}
    private static Outcome outcome(Rule rule,String status,String key,MObject object,String diagnostic){return new Outcome(rule.node.attr("id"),status,key,object,diagnostic);}
}

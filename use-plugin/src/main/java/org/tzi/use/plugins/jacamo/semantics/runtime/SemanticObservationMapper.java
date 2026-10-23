package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;

/** Stateless mapping policy. All writes, identity, savepoints and rollback belong to the native engine. */
public final class SemanticObservationMapper {
    public interface Port {
        MObject find(String id);
        MObject upsert(String id, String type, String label) throws UseApiException;
        void alias(String id, MObject object);
        default void unalias(String id) {throw new UnsupportedOperationException("UNALIAS_NOT_IMPLEMENTED");}
        String metadata(MObject object, String key);
        void metadata(MObject object, String key, String value);
        Collection<MObject> objects();
        void value(MObject object, String attribute, Value value) throws UseApiException;
        void connect(String association, MObject first, MObject second) throws UseApiException;
        void delete(String id) throws UseApiException;
        /** A logical role tuple may be re-enacted; physical incarnations may not. */
        default void deleteRebindableRole(String id)throws UseApiException {throw new UnsupportedOperationException("REBINDABLE_ROLE_DELETE_NOT_IMPLEMENTED");}
        void disconnect(String association,MObject first,MObject second) throws UseApiException;
        default Value currentValue(MObject object,String attribute){return null;}
        /** Same authoritative state, only for host-native expression evaluation. No ownership transfer. */
        default org.tzi.use.uml.sys.MSystemState readState(){return null;}
    }
    public record Result(String identity, MObject object, String diagnostic) { }
    public static final class UnsupportedObservation extends RuntimeException {
        public UnsupportedObservation(String reason) { super(reason); }
    }
    private final ObservationMappingBindings bindings;
    private final boolean functional;
    public SemanticObservationMapper(org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration config) {this(new LegacyObservationMappingBindings(config));}
    public SemanticObservationMapper(ObservationMappingBindings bindings){this.bindings=Objects.requireNonNull(bindings);this.functional=bindings.functionalObservers();}
    public Result apply(Port port, BridgeEntityId runtime, String kind, Map<String,Object> data) throws UseApiException {
        return switch(kind) {
            case "UPSERT_CARTAGO_WORKSPACE" -> {
                String id=text(data,"semanticId"); var object=port.upsert(id,"WorkspaceObservation",text(data,"name"));
                copy(port,object,data,"fullName","uuid","name","environmentId");
                port.metadata(object,"authority","cartago"); alias(port,object,data,"workspaceDeclarationSemanticId");
                yield new Result(id,object,"CSL_WORKSPACE_OBSERVER");
            }
            case "UPSERT_CARTAGO_AGENT_IDENTITY", "UPSERT_JASON_AGENT_STATE" -> agent(port,runtime,kind,data);
            case "DELETE_CARTAGO_AGENT_IDENTITY" -> {
                if(!bindings.canonical())throw new UnsupportedObservation("CSL_OBSERVATION_NOT_IN_PROFILE:"+kind);
                var object=required(port,text(data,"semanticId"));String primary=port.metadata(object,"semanticId");
                var memberships=memberships(port,object);memberships.remove(runtime.canonical());
                port.metadata(object,"cartagoMemberships",NativeUseModelBuilder.canonicalJson(memberships));
                if(!primary.equals(runtime.canonical()))port.unalias(runtime.canonical());
                if(!primary.equals(text(data,"semanticId")))port.unalias(text(data,"semanticId"));
                if(memberships.isEmpty()&&port.metadata(object,"jasonScope").isBlank()) {
                    for(var dependent:List.copyOf(port.objects()))if(primary.equals(port.metadata(dependent,"rolePlayer")))port.delete(port.metadata(dependent,"semanticId"));
                    port.delete(primary);
                }
                yield new Result(primary,object,"CANONICAL_CARTAGO_MEMBERSHIP_RETIRED");
            }
            case "UPSERT_CARTAGO_ARTIFACT" -> artifact(port,runtime,data);
            case "UPSERT_CARTAGO_PROPERTY_SNAPSHOT" -> property(port,data);
            case "APPLY_CARTAGO_PROPERTY_DELTA" -> {
                var object=required(port,text(data,"semanticId"));
                for(Object id:list(data,"removedPropertySemanticIds")) removeProperty(port,object,(String)id);
                for(Object raw:list(data,"properties")) property(port,map(raw));
                port.metadata(object,"lastSignal",Objects.toString(data.get("signal"),""));
                yield new Result(text(data,"semanticId"),object,"CSL_TYPED_PROPERTY_DELTA");
            }
            case "DELETE_CARTAGO_ARTIFACT" -> {
                String id=text(data,"semanticId"); var object=port.find(id);
                for(var board:List.copyOf(port.objects()))if(runtime.incarnation().equals(port.metadata(board,"boardArtifactIdentity"))) {
                    String boardId=port.metadata(board,"semanticId");
                    for(var dependent:List.copyOf(port.objects()))if(boardId.equals(port.metadata(dependent,"roleContext")) || boardId.equals(port.metadata(dependent,"functionalScheme")) && dependent!=board)port.delete(port.metadata(dependent,"semanticId"));
                    port.delete(boardId);if(object==null){object=board;id=boardId;}
                }
                if(port.find(text(data,"semanticId"))!=null)port.delete(text(data,"semanticId"));
                if(object==null)throw new UnsupportedObservation("DISPOSED_ARTIFACT_NOT_IN_PROFILE");
                yield new Result(id,object,"CSL_ARTIFACT_AND_BOARD_INCARNATION_RETIRED");
            }
            case "OBSERVE_NPL_NORM_V1" -> {
                if(!functional)throw new UnsupportedObservation("NORMATIVE_OBSERVER_NOT_IN_PROFILE");
                yield new org.tzi.use.plugins.jacamo.semantics.normative.NormativeObservationMapper().apply(port,runtime,data);
            }
            case "UPSERT_MOISE_SCHEME" -> {
                if(!functional)throw new UnsupportedObservation("FUNCTIONAL_OBSERVER_NOT_IN_PROFILE");
                yield new org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservationMapper().apply(port,runtime,data);
            }
            case "UPSERT_MOISE_GROUP" -> group(port,runtime,data);
            case "UPSERT_MOISE_GROUP_PARENT" -> {
                if(!bindings.hasNesting())throw new UnsupportedObservation("CSL_OBSERVATION_NOT_IN_PROFILE:"+kind);
                yield parent(port,data);
            }
            default -> throw new UnsupportedObservation("CSL_OBSERVATION_NOT_IN_PROFILE:"+kind);
        };
    }
    private Result agent(Port port,BridgeEntityId runtime,String kind,Map<String,Object> data) throws UseApiException {
        boolean jason=kind.equals("UPSERT_JASON_AGENT_STATE");
        String primary=jason?runtime.canonical():Objects.toString(data.get("agentRuntimeSemanticId"),text(data,"semanticId"));
        if(jason)for(var old:List.copyOf(port.objects()))if(old.cls().name().equals("AtomicAgent") && text(data,"name").equals(port.metadata(old,"name")) && runtime.scope().equals(port.metadata(old,"jasonScope")) && !primary.equals(port.metadata(old,"semanticId"))) {
            String oldId=port.metadata(old,"semanticId");
            for(var dependent:List.copyOf(port.objects()))if(oldId.equals(port.metadata(dependent,"rolePlayer")) || oldId.equals(port.metadata(dependent,"mentalAgent")) || oldId.equals(port.metadata(dependent,"commitmentPlayer"))) {
                String dependentId=port.metadata(dependent,"semanticId");
                if(!port.metadata(dependent,"xml:m0:program").isBlank()&&dependent.cls().isSubClassifierOf(dependent.cls().model().getClass("RoleOccurrence")))port.deleteRebindableRole(dependentId);
                else port.delete(dependentId);
            }
            port.delete(oldId);
        }
        var object=port.upsert(primary,"AtomicAgent",text(data,"name"));
        if(jason)port.metadata(object,"jasonScope",runtime.scope());
        port.alias(runtime.canonical(),object);
        if(bindings.canonical()&&!jason) {var members=memberships(port,object);members.add(runtime.canonical());port.metadata(object,"cartagoMemberships",NativeUseModelBuilder.canonicalJson(members));}
        if(data.get("semanticId") instanceof String id && !id.isBlank())port.alias(id,object);
        if(data.get("agentDeclarationSemanticId") instanceof String declaration && !declaration.isBlank()) {
            var rows=(List<?>)CanonicalJson.decode(DomainProjection.decode(object.cls().model().getAnnotationValue("CslAgentDeclarations","payload64")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var matches=rows.stream().map(CanonicalJson::object).filter(r->declaration.equals(r.get("id"))).toList();
            if(matches.size()!=1)throw new IllegalArgumentException("CSL_AGENT_DECLARATION_UNRESOLVED");
            if(((Number)matches.getFirst().get("instances")).intValue()==1)port.alias(declaration,object);
            else port.metadata(object,"declarationBinding",declaration);
        }
        copy(port,object,data,"name","globalId","localId","workspaceSemanticId");
        port.metadata(object,"authority",jason?"jason":"cartago");
        port.metadata(object,"incarnation",runtime.incarnation());
        // Mental payload is kept under its own authority; it never writes domain attributes.
        if(jason) {
            port.metadata(object,"jasonObservation",NativeUseModelBuilder.canonicalJson(data));
            if(functional)new org.tzi.use.plugins.jacamo.semantics.observer.MentalObservationMapper().apply(port,object,primary,data);
        }
        return new Result(primary,object,"CSL_ATOMIC_PLAYER_OBSERVED");
    }
    private Result artifact(Port port,BridgeEntityId runtime,Map<String,Object> data) throws UseApiException {
        String id=text(data,"semanticId"),type=text(data,"artifactTypeSemanticId");
        var candidates=bindings.types().stream().filter(c->c.kind()==ObservationMappingBindings.Kind.ENTITY &&
                (c.sourceId().equals(type) || c.sourceId().equals(DomainProjection.artifactId(text(data,"artifactTypeJavaClassName"))) || c.javaClass().equals(text(data,"artifactTypeJavaClassName")))).toList();
        if(candidates.size()!=1)throw new UnsupportedObservation(candidates.isEmpty()?"DOMAIN_TYPE_NOT_REGISTERED:"+type:"DOMAIN_TYPE_BINDING_AMBIGUOUS:"+type);
        String workspace=text(data,"workspaceSemanticId"),name=text(data,"name"),uuid=text(data,"uuid");
        // A faithfully observed artifact at the same workspace/name with a new UUID proves replacement.
        for(var old:List.copyOf(port.objects())) if(!port.metadata(old,"uuid").isBlank()
                && workspace.equals(port.metadata(old,"workspaceSemanticId")) && name.equals(port.metadata(old,"name"))
                && !uuid.equals(port.metadata(old,"uuid")))port.delete(port.metadata(old,"semanticId"));
        var object=port.upsert(id,candidates.getFirst().symbol(),name);
        port.alias(runtime.canonical(),object); alias(port,object,data,"artifactDeclarationSemanticId");
        copy(port,object,data,"name","uuid","workspaceSemanticId","artifactTypeSemanticId","artifactTypeJavaClassName","creatorAgentSemanticId");
        port.metadata(object,"authority","cartago"); port.metadata(object,"incarnation",runtime.incarnation());
        if(data.containsKey("properties")) for(Object raw:list(data,"properties"))property(port,map(raw));
        return new Result(id,object,"CSL_DOMAIN_INSTANCE_OBSERVED");
    }
    private Result property(Port port,Map<String,Object> data) throws UseApiException {
        String owner=text(data,"artifactSemanticId"),id=text(data,"semanticId"),sourceName=text(data,"name");
        var object=port.find(owner);
        if(object==null)throw new UnsupportedObservation("PROPERTY_OWNER_NOT_IN_DOMAIN_PROFILE:"+owner);
        // Keep every unbound raw slot as evidence, without turning it into an Entity or guessed String attribute.
        port.metadata(object,"propertyEvidence:"+id,NativeUseModelBuilder.canonicalJson(data));
        var attributes=bindings.properties(object,sourceName);
        if(attributes.isEmpty()) {port.metadata(object,"propertyDecision:"+id,"UNSUPPORTED:UNBOUND_DOMAIN_PROPERTY");return new Result(owner,object,"UNSUPPORTED:UNBOUND_DOMAIN_PROPERTY:"+sourceName);}
        if(attributes.size()!=1)throw new IllegalArgumentException("PROPERTY_BINDING_AMBIGUOUS:"+sourceName);
        var attribute=attributes.getFirst(); String key="propertySlot:"+attribute.name();
        String previous=port.metadata(object,key);
        if(!previous.isBlank() && !previous.equals(id))throw new IllegalArgumentException("DUPLICATE_PROPERTY_NAME_REQUIRES_OCCURRENCE_BINDING:"+sourceName);
        var value=bindings.decode(object,attribute,list(data,"values"),list(data,"valueTypes"));
        var previousValue=port.currentValue(object,attribute.name());
        bindings.propertyWritten(port,object,attribute,previousValue,value);
        port.value(object,attribute.name(),value); port.metadata(object,key,id);
        port.metadata(object,"propertyDecision:"+id,"MAPPED:"+attribute.name());
        return new Result(owner,object,"CSL_TYPED_PROPERTY_OBSERVED:"+attribute.name());
    }
    public void removeProperty(Port port,MObject object,String id) throws UseApiException {
        for(var attribute:object.cls().allAttributes()) if(id.equals(port.metadata(object,"propertySlot:"+attribute.name()))) {
            port.value(object,attribute.name(),UndefinedValue.instance);port.metadata(object,"propertySlot:"+attribute.name(),"");
            if(bindings.canonical()){port.metadata(object,"observedValueHash:"+attribute.name(),"");port.metadata(object,"propertyAbsent:"+attribute.name(),"true");}
        }
        port.metadata(object,"propertyDecision:"+id,"REMOVED_AUTHORITATIVELY");
    }
    private Result group(Port port,BridgeEntityId runtime,Map<String,Object> data) throws UseApiException {
        String source=text(data,"groupSpecSemanticId");
        var definition=org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationFromTree(data.get("organisationDefinition"));
        MoiseDomainProjection.validate(definition);
        if(!definition.metadata().semanticId().equals(text(data,"organisationSpecSemanticId")))throw new IllegalArgumentException("CSL_OS_IDENTITY_MISMATCH");
        var groupDefinition=definition.structuralSpecification().groups().stream().filter(g->g.metadata().semanticId().equals(source)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("CSL_GROUP_DEFINITION_UNRESOLVED"));
        var contextType=registered(ObservationMappingBindings.Kind.ORG_CONTEXT,source);
        String id=runtime.canonical();
        if(data.get("boardArtifactIdentity") instanceof String uuid) {
            if(!runtime.incarnation().endsWith(":"+uuid))throw new IllegalArgumentException("CSL_GROUP_BOARD_UUID_MISMATCH");
            for(var old:List.copyOf(port.objects()))if("moise".equals(port.metadata(old,"authority")) && source.equals(port.metadata(old,"groupSpecSemanticId")) && runtime.scope().equals(port.metadata(old,"organisationName")) && text(data,"name").equals(port.metadata(old,"name")) && !id.equals(port.metadata(old,"semanticId"))) {
                String oldId=port.metadata(old,"semanticId");for(var role:List.copyOf(port.objects()))if(oldId.equals(port.metadata(role,"roleContext")))port.delete(port.metadata(role,"semanticId"));port.delete(oldId);
            }
        }
        var context=port.upsert(id,contextType.symbol(),text(data,"name"));
        port.alias(text(data,"semanticId"),context); copy(port,context,data,"name","organisationName","organisationSpecSemanticId","groupSpecSemanticId","boardArtifactIdentity","continuity");
        port.metadata(context,"authority","moise");port.metadata(context,"incarnation",runtime.incarnation());
        var accepted=new LinkedHashMap<String,Map.Entry<MObject,ObservationMappingBindings.Type>>();
        var unresolvedPlayers=new ArrayList<Map<String,Object>>();
        for(Object raw:list(data,"players")) {
            var player=map(raw);String roleSource=text(player,"roleSemanticId");
            if(bindings.canonical()&&bindings.types().stream().noneMatch(t->t.kind()==ObservationMappingBindings.Kind.ROLE&&t.sourceId().equals(roleSource))) {
                if(!groupDefinition.roleSemanticIds().contains(roleSource))throw new IllegalArgumentException("CSL_ROLE_NOT_DECLARED_IN_CONTEXT");
                port.metadata(context,"unmappedRoleEvidence:"+roleSource,NativeUseModelBuilder.canonicalJson(player));continue;
            }
            var role=registered(ObservationMappingBindings.Kind.ROLE,roleSource);
            if(!groupDefinition.roleSemanticIds().contains(role.sourceId()))throw new IllegalArgumentException("CSL_ROLE_NOT_DECLARED_IN_CONTEXT");
            String playerId=player.containsKey("agentSemanticId")?text(player,"agentSemanticId"):text(player,"agentDeclarationSemanticId");
            var actor=port.find(playerId);
            if(actor==null&&bindings.canonical()) {
                // GroupBoard.agKilled is asynchronous to the Cartago departure.
                // Preserve the actual role report without inventing its player
                // or rejecting unrelated observations in the admitted cut.
                unresolvedPlayers.add(player);continue;
            }
            if(actor==null)throw new IllegalArgumentException("CSL_RUNTIME_BINDING_UNRESOLVED:"+playerId);
            if(!actor.cls().isSubClassifierOf(context.cls().model().getClass("RuntimePlayer")))throw new IllegalArgumentException("CSL_PLAYER_KIND_REQUIRED");
            String tuple=NativeUseModelBuilder.canonicalJson(List.of(id,port.metadata(actor,"semanticId"),role.id()));
            if(accepted.putIfAbsent(tuple,Map.entry(actor,role))!=null)throw new IllegalArgumentException("DUPLICATE_ACCEPTED_ENACTMENT");
        }
        for(var old:List.copyOf(port.objects())) if(id.equals(port.metadata(old,"roleContext")) && !accepted.containsKey(port.metadata(old,"roleTuple")))port.delete(port.metadata(old,"semanticId"));
        for(var entry:accepted.entrySet()) {
            var previous=port.objects().stream().filter(o->entry.getKey().equals(port.metadata(o,"roleTuple"))).findFirst();
            MObject occurrence;
            if(previous.isPresent())occurrence=previous.get();
            else {
                long next=Long.parseLong(Objects.requireNonNullElse(nonempty(port.metadata(context,"roleSequence")),"0"))+1;
                port.metadata(context,"roleSequence",Long.toString(next));
                String roleId="csl-enactment:"+NativeUseModelBuilder.canonicalJson(List.of(entry.getKey(),next));
                occurrence=port.upsert(roleId,entry.getValue().getValue().symbol(),"enactment");
                port.metadata(occurrence,"roleTuple",entry.getKey());port.metadata(occurrence,"roleContext",id);
                port.metadata(occurrence,"roleTypeId",entry.getValue().getValue().id());
                if(bindings.canonical())port.metadata(occurrence,"roleSourceId",entry.getValue().getValue().sourceId());
                port.metadata(occurrence,"rolePlayer",port.metadata(entry.getValue().getKey(),"semanticId"));
                port.metadata(occurrence,"authority","moise");
            }
            port.connect("Plays",entry.getValue().getKey(),occurrence);port.connect("InContext",context,occurrence);
        }
        port.metadata(context,"lastGroupCut",org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(CanonicalJson.encode(data)));
        if(bindings.canonical()) {
            port.metadata(context,"rolePopulationQuality",unresolvedPlayers.isEmpty()?"COMPLETE_OBSERVED_BOARD":"INCOMPLETE");
            port.metadata(context,"unresolvedPlayerEvidence",NativeUseModelBuilder.canonicalJson(unresolvedPlayers));
        }
        return new Result(id,context,"CSL_ACCEPTED_ENACTMENTS_OBSERVED");
    }
    private static TreeSet<String> memberships(Port port,MObject object) {
        String text=port.metadata(object,"cartagoMemberships");var result=new TreeSet<String>();
        if(!text.isBlank())for(Object value:(List<?>)CanonicalJson.decode(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)))result.add((String)value);
        return result;
    }
    private Result parent(Port port,Map<String,Object> data)throws UseApiException {
        var child=required(port,text(data,"semanticId"));String parentId=Objects.toString(data.get("parentSemanticId"),"");
        MObject parent=parentId.isBlank()?null:required(port,parentId);
        if(parent!=null) {
            String childType=text(data,"groupSpecSemanticId"),parentType=text(data,"parentSpecSemanticId");
            boolean declared=bindings.nestingRegistered(childType,parentType);
            if(!declared)throw new UnsupportedObservation("CSL_NESTED_CONTEXT_DECLARATION_NOT_REGISTERED");
            String childIdentity=port.metadata(child,"semanticId");var seen=new HashSet<String>();
            for(MObject at=parent;at!=null;) {
                String current=port.metadata(at,"semanticId");if(current.equals(childIdentity)||!seen.add(current))throw new IllegalArgumentException("CSL_RUNTIME_CONTEXT_CYCLE");
                String next=port.metadata(at,"parentContextIdentity");at=next.isBlank()?null:required(port,next);
            }
        }
        port.disconnect("ContextNesting",null,child);if(parent!=null)port.connect("ContextNesting",parent,child);
        port.metadata(child,"parentContextIdentity",parent==null?"":port.metadata(parent,"semanticId"));
        return new Result(port.metadata(child,"semanticId"),child,"CSL_CONTEXT_PARENT_OBSERVED");
    }
    private ObservationMappingBindings.Type registered(ObservationMappingBindings.Kind kind,String source) {
        return bindings.types().stream().filter(c->c.kind()==kind && c.sourceId().equals(source)).findFirst()
                .orElseThrow(()->new UnsupportedObservation("CSL_SOURCE_TYPE_NOT_REGISTERED:"+kind+":"+source));
    }
    private static String nonempty(String text) {return text.isBlank()?null:text;}
    private static void alias(Port port,MObject object,Map<String,Object> data,String key) {if(data.get(key) instanceof String id && !id.isBlank())port.alias(id,object);}
    private static void copy(Port port,MObject object,Map<String,Object> data,String... keys) {for(String key:keys)if(data.containsKey(key))port.metadata(object,key,Objects.toString(data.get(key),""));}
    private static MObject required(Port port,String id) {var result=port.find(id);if(result==null)throw new IllegalArgumentException("CSL_RUNTIME_BINDING_UNRESOLVED:"+id);return result;}
    private static String text(Map<String,Object> data,String key) {if(!(data.get(key) instanceof String value)||value.isBlank())throw new IllegalArgumentException("CSL_REQUIRED_TEXT:"+key);return value;}
    private static List<?> list(Map<String,Object> data,String key) {if(!(data.get(key) instanceof List<?> value))throw new IllegalArgumentException("CSL_REQUIRED_LIST:"+key);return value;}
    private static Map<String,Object> map(Object raw) {return CanonicalJson.object(raw);}
}

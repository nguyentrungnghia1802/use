package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.verify.api.EngineSemanticsProfile;
import org.tzi.use.plugins.jacamo.dsl.csl.parser.*;
import org.tzi.use.plugins.jacamo.dsl.istar.parser.*;
import org.tzi.use.plugins.jacamo.verify.runtime.*;
import org.tzi.use.plugins.jacamo.verify.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Receives CSL data only. No Jason plan, action, success flag or Task mapping enters this evaluator. */
public final class CslStateVerifier implements AutoCloseable {
    private static final EngineSemanticsProfile PROFILE=EngineSemanticsProfile.ROLE_SPECIALIZATION_V1;
    public record Snapshot(String run,long sequence,boolean complete,List<Artifact> artifacts,List<Node> nodes,List<Enactment> enactments,List<Membership> memberships) {
        public Snapshot(String run,long sequence,boolean complete,List<Artifact> artifacts,List<Node> nodes,List<Enactment> enactments){this(run,sequence,complete,artifacts,nodes,enactments,List.of());}
        public Snapshot(String run,long sequence,boolean complete,List<Artifact> artifacts){this(run,sequence,complete,artifacts,List.of(),List.of());}
        public Snapshot {artifacts=List.copyOf(artifacts);nodes=List.copyOf(nodes);enactments=List.copyOf(enactments);memberships=List.copyOf(memberships);if(run==null||run.isBlank()||sequence<1)throw new IllegalArgumentException("STATE_SNAPSHOT_IDENTITY_REQUIRED");}
    }
    /** Physical organization/group/agent instance; parent is a physical node identity, not a CSL class. */
    public record Node(String identity,String kind,String type,String name,String workspace,String parent,Map<String,Object> properties) {
        public Node {properties=Map.copyOf(properties);if(!Set.of("organization","group","agent").contains(kind)||identity.isBlank()||type.isBlank()||name.isBlank())throw new IllegalArgumentException("STRUCTURAL_NODE_INVALID");}
    }
    public record Enactment(String context,String player,String role) { }
    public record Membership(String member,String group) { }
    public record Artifact(String identity,String javaClass,String name,String workspace,Map<String,Object> properties,Set<String> observers) {
        public Artifact(String identity,String javaClass,String name,String workspace,Map<String,Object> properties){this(identity,javaClass,name,workspace,properties,Set.of());}
        public Artifact {properties=Collections.unmodifiableMap(new LinkedHashMap<>(properties));observers=Set.copyOf(observers);for(String value:List.of(identity,javaClass,name,workspace))if(value.isBlank())throw new IllegalArgumentException("PHYSICAL_IDENTITY_REQUIRED");}
    }
    public record Check(String context,String id,String kind,String expression,String verdict,String diagnostic) { }
    public record ElementStatus(String context,String actor,String element,String status) { }
    public record Result(long sequence,String decision,List<Check> checks,List<ElementStatus> elements,List<Map<String,Object>> state) {
        public Result {checks=List.copyOf(checks);elements=List.copyOf(elements);state=List.copyOf(state);}
        public Map<String,Object> payload(){return Map.of("sequence",sequence,"decision",decision,"checks",checks.stream().map(c->Map.of("context",c.context(),"id",c.id(),"kind",c.kind(),"expression",c.expression(),"verdict",c.verdict(),"diagnostic",c.diagnostic())).toList(),"elements",elements.stream().map(e->Map.of("context",e.context(),"actor",e.actor(),"element",e.element(),"status",e.status())).toList(),"state",state);}
    }
    private record Predicate(String element,OclContractSpec.Kind kind,String actor,NativeOclBackend.Compiled compiled) { }
    private final AttributeMapping mapping;
    private final org.vnu.sme.goal.dsl.csl.mm.CslModel cslModel;
    private final GoalModel goals;
    private final ParticipationGoalViews participation;
    private final MSystem system;
    private final UseSystemApi api;
    private final NativeOclBackend ocl;
    private final CslSnapshotProjection projection;
    private final List<Predicate> predicates=new ArrayList<>();
    private final Map<String,String> actorClasses=new TreeMap<>();
    private final Map<String,MObject> objects=new TreeMap<>();
    private final Map<String,Use4GoalRuntimeSessionAdapter> sessions=new HashMap<>();
    private final Map<String,NativeOclBackend.Compiled> invariants=new TreeMap<>();
    private String run;
    private long lastSequence;
    private Snapshot previousSnapshot;
    private Result previousResult;
    public CslStateVerifier(Path mappingFile,Path cslFile,Path istarFile)throws Exception {
        mapping=AttributeMapping.read(mappingFile);
        String cslSource=Files.readString(cslFile),istar=Files.readString(istarFile);
        if(cslSource.contains("@use-native-ocl")||istar.contains("@use-native-ocl"))throw new IllegalArgumentException("STATE_MODE_INLINE_NATIVE_CONTRACTS_UNSUPPORTED");
        var csl=new CslFrontend().compile(cslSource,cslFile.toString()).requireModel();cslModel=csl;
        goals=new IStarFrontend().compileScoped(istar,istarFile.toString(),PROFILE).requireModel();
        participation=new ParticipationGoalViews(goals);
        new IStarFrontend().validate(participation.executionModel(),istarFile+":participation",PROFILE).requireModel();
        var binding=new Use75CslModelBackend().compile(csl,M0ExtensionSpec.unifiedContexts(),CslScopeSupport::prepare);
        system=new MSystem(binding.model());api=UseSystemApi.create(system,false);
        projection=new CslSnapshotProjection(binding,mapping);
        ocl=new NativeOclBackend(system.model(),"CSL_STATE_ONLY/1.0",PROFILE.name());
        for(var a:mapping.attributes()) {
            var cls=system.model().getClass(a.targetClass());
            if(cls==null||cls.attribute(a.targetName(),true)==null)throw new IllegalArgumentException("CSL_ATTRIBUTE_UNRESOLVED:"+a.targetClass()+"."+a.targetName());
        }
        for(var declaration:binding.classifiers().values())for(var attribute:binding.classifier(declaration.declaration()).allAttributes())
            if(!attribute.isDerived())
            if(mapping.attributes().stream().noneMatch(a->a.targetClass().equals(declaration.declaration())&&a.targetName().equals(attribute.name())))
                throw new IllegalArgumentException("CSL_ATTRIBUTE_MAPPING_REQUIRED:"+declaration.declaration()+"."+attribute.name());
        // Context binding belongs to the iStar model and names a CSL type, never a JaCaMo element.
        var stateContext=Pattern.compile("(?m)^\\s*//\\s*@csl-state\\s+(\\w+)\\s*$").matcher(istar);
        String defaultContext=null;
        while(stateContext.find()){if(defaultContext!=null)throw new IllegalArgumentException("DUPLICATE_CSL_STATE_CONTEXT");defaultContext=stateContext.group(1);}
        final String sharedContext=defaultContext;
        var context=Pattern.compile("(?m)^\\s*//\\s*@csl-context\\s+(\\w+)\\s*=\\s*(\\w+)\\s*$").matcher(istar);
        while(context.find())if(actorClasses.putIfAbsent(context.group(1),context.group(2))!=null)throw new IllegalArgumentException("DUPLICATE_CSL_CONTEXT");
        for(var actor:goals.getActors()) {
            String type=actorClasses.computeIfAbsent(actor.name(),k->sharedContext==null?k:sharedContext);var cls=system.model().getClass(type);
            if(cls==null)throw new IllegalArgumentException("ISTAR_CSL_CONTEXT_REQUIRED:"+actor.name());
            if(mapping.attributes().stream().noneMatch(a->a.targetClass().equals(type)))throw new IllegalArgumentException("ISTAR_CONTEXT_WITHOUT_CSL_STATE_MAPPING:"+type);
            if(!actor.neededBys().isEmpty())throw new IllegalArgumentException("STATE_RESOURCE_OCL_REQUIRED:"+actor.name());
            for(var element:actor.elements()) {
                if(element instanceof Quality||element instanceof Resource)throw new IllegalArgumentException("STATE_ELEMENT_NOT_SUPPORTED:"+element.id());
                if(element instanceof Goal goal&&!goal.conditions().isEmpty())add(goal.id(),actor.name(),OclContractSpec.Kind.GOAL,cls,goal.oclSource());
                if(element instanceof Task task) {
                    if(task.preconditions().size()!=1||task.postconditions().size()!=1)throw new IllegalArgumentException("STATE_TASK_PRE_POST_REQUIRED:"+task.id());
                    add(task.id(),actor.name(),OclContractSpec.Kind.TASK_PRE,cls,task.preconditions().getFirst().oclBody());
                    add(task.id(),actor.name(),OclContractSpec.Kind.TASK_POST,cls,task.postconditions().getFirst().oclBody());
                }
            }
        }
        if(new HashSet<>(actorClasses.values()).size()!=1)throw new IllegalArgumentException("STATE_ISTAR_REQUIRES_ONE_CSL_CONTEXT_TYPE_PER_GRAPH");
        for(var inv:system.model().classInvariants())invariants.put(inv.cls().name()+"::"+inv.name(),ocl.compile(new OclContractSpec(inv.name(),OclContractSpec.Kind.INVARIANT,system.model().getClass(inv.cls().name()),null,inv.bodyExpression().toString())));
    }
    private void add(String element,String actor,OclContractSpec.Kind kind,MClass self,String body) {
        if(Pattern.compile("(?<![.\\w])(step|player|orgContext)\\s*\\.").matcher(body).find())throw new IllegalArgumentException("ISTAR_MUST_READ_CSL_STATE_ONLY:"+element);
        // A Goal using @pre is an explicit relation between successive CSL snapshots.
        var compiledKind=kind==OclContractSpec.Kind.GOAL&&body.contains("@pre")?OclContractSpec.Kind.TRANSITION:kind;
        predicates.add(new Predicate(element,kind,actor,ocl.compile(new OclContractSpec(element+":"+kind,compiledKind,self,null,body))));
    }
    public AttributeMapping mapping(){return mapping;}
    public org.vnu.sme.goal.dsl.csl.mm.CslModel cslModel(){return cslModel;}
    public GoalModel goalModel(){return goals;}
    public MSystem system(){return system;}
    public synchronized Result update(Snapshot snapshot)throws Exception {
        if(run!=null&&!run.equals(snapshot.run()))throw new IllegalArgumentException("STATE_RUN_CHANGED");
        if(snapshot.sequence()==lastSequence) {
            if(!snapshot.equals(previousSnapshot))throw new IllegalArgumentException("STATE_SEQUENCE_COLLISION");
            return previousResult;
        }
        if(snapshot.sequence()!=lastSequence+1)throw new IllegalArgumentException("STATE_SEQUENCE_GAP");
        if(!snapshot.complete())return new Result(snapshot.sequence(),"HOLD_UNVERIFIED",List.of(new Check("","coverage","SOURCE_COVERAGE","","UNKNOWN","INCOMPLETE_SOURCE_SNAPSHOT")),List.of(),stateRows());
        var prepared=projection.prepare(snapshot); // Resolve all physical identities, scopes and types before mutation.
        MSystemState before=new MSystemState("previous-csl-state",system.state());
        var retained=new HashSet<String>();
        for(var row:prepared.objects()) {
            retained.add(row.identity());var object=objects.get(row.identity());
            if(object==null){object=api.createObject(row.type(),"state_"+hash(row.identity()).substring(0,24));objects.put(row.identity(),object);}
            for(var value:row.values().entrySet())api.setAttributeValueEx(object,object.cls().attribute(value.getKey(),true),value.getValue());
        }
        // Links are reconstructed from each complete physical snapshot; leaving a role removes Plays/InContext.
        for(var link:new ArrayList<>(system.state().allLinks()))api.deleteLinkEx(link);
        for(String id:new ArrayList<>(objects.keySet()))if(!retained.contains(id)) {
                api.deleteObjectEx(objects.remove(id));var retired=sessions.remove(id);if(retired!=null)retired.close();
        }
        for(var link:prepared.links())api.createLink(link.association(),objects.get(link.left()).name(),objects.get(link.right()).name());
        var view=UseStateReadView.pin(before,system.state());var checks=new ArrayList<Check>();var elements=new ArrayList<ElementStatus>();
        for(var object:objects.values())for(var entry:invariants.entrySet())if(object.cls().isSubClassifierOf(entry.getValue().specification().self())) {
            var result=ocl.evaluate(entry.getValue(),view,Map.of("self",object.value()),"");
            checks.add(new Check(object.name(),entry.getKey(),"CSL_INVARIANT",entry.getValue().specification().body(),result.status().name(),result.diagnostic()));
        }
        for(var entry:objects.entrySet()) {
            var object=entry.getValue();if(!actorClasses.containsValue(object.cls().name()))continue;
            var session=sessions.computeIfAbsent(entry.getKey(),id->new Use4GoalRuntimeSessionAdapter(participation.executionModel(),
                    actorClasses.keySet().stream().map(a->new ScenarioInstance("ctx_"+a+"_"+hash(id).substring(0,16),a)).toList(),(a,x,b,y)->true,"CSL:"+id,PROFILE,EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1));
            var goalInputs=new HashMap<String,EngineInputs.GoalInput>();var taskInputs=new HashMap<String,EngineInputs.TaskInput>();
            var outcomes=new HashMap<String,Boolean>();
            for(var p:predicates) {
                var result=ocl.evaluate(p.compiled(),view,Map.of("self",object.value()),"");
                String verdict=result.status().name();
                // A false state predicate is progress. Maintain/Sustain violations come from the original engine.
                checks.add(new Check(object.name(),p.element(),p.kind().name(),p.compiled().specification().body(),verdict,result.diagnostic()));
                outcomes.put(p.element()+":"+p.kind(),result.knownBoolean());
            }
            for(var occurrence:session.topology().occurrences().values()) {
                String source=occurrence.sourceElementId(),id=occurrence.occurrenceId();if(session.isDerivedOccurrence(id))continue;
                var element=goals.findElement(source).orElse(null);
                if(element instanceof Goal goal&&!goal.conditions().isEmpty())goalInputs.put(id,new EngineInputs.GoalInput(entry.getKey(),true,outcomes.get(source+":"+OclContractSpec.Kind.GOAL)));
                if(element instanceof Task)taskInputs.put(id,new EngineInputs.TaskInput(entry.getKey(),true,outcomes.get(source+":"+OclContractSpec.Kind.TASK_PRE),outcomes.get(source+":"+OclContractSpec.Kind.TASK_POST)));
            }
            // Never advance original histories from an unknown condition.
            boolean unavailable=checks.stream().filter(c->c.context().equals(object.name())).anyMatch(c->!Set.of("PASS","FAIL").contains(c.verdict()));
            if(unavailable)continue;
            var evaluated=session.step(new EngineInputs(goalInputs,taskInputs,Map.of()));
            for(var occurrence:session.topology().occurrences().values()) {
                var status=evaluated.aggregate().goalTaskStatuses().get(occurrence.occurrenceId());if(status==null)continue;
                for(String goalView:participation.viewsOf(occurrence.sourceElementId())) {
                    String actor=goals.ownerOf(goalView).orElse("");
                    elements.add(new ElementStatus(object.name(),actor,goalView,status.name()));
                }
            }
        }
        boolean failed=checks.stream().anyMatch(c->c.kind().equals("CSL_INVARIANT")&&c.verdict().equals("FAIL"))||elements.stream().anyMatch(e->e.status().equals("VIOLATED"));
        boolean unavailable=checks.stream().anyMatch(c->!Set.of("PASS","FAIL").contains(c.verdict()));
        var result=new Result(snapshot.sequence(),failed?"HOLD_FAILURE":unavailable?"HOLD_UNVERIFIED":"APPROVE",checks,elements,stateRows());
        run=snapshot.run();lastSequence=snapshot.sequence();previousSnapshot=snapshot;previousResult=result;return result;
    }
    static Value value(Object raw,String type,String target) {
        if(raw==null)throw new IllegalArgumentException("ATTRIBUTE_VALUE_MISSING:"+target);
        if(type.equals("String")&&raw instanceof String text)return new StringValue(text);
        if(type.equals("Boolean")&&raw instanceof Boolean truth)return BooleanValue.get(truth);
        if(type.equals("Real")&&raw instanceof Number number&&Double.isFinite(number.doubleValue()))return new RealValue(number.doubleValue());
        if(type.equals("Integer")&&raw instanceof Number number)try{return IntegerValue.valueOf(new java.math.BigDecimal(number.toString()).intValueExact());}catch(ArithmeticException invalid){throw new IllegalArgumentException("INTEGER_RANGE_OR_PRECISION:"+target);}
        throw new IllegalArgumentException("ATTRIBUTE_TYPE_MISMATCH:"+target+":"+type);
    }
    private List<Map<String,Object>> stateRows(){var rows=new ArrayList<Map<String,Object>>();for(var entry:objects.entrySet()){var values=new TreeMap<String,Object>();values.put("object",entry.getValue().name());values.put("type",entry.getValue().cls().name());values.put("sourceIdentity",entry.getKey());
        var variables=new VarBindings(system.state());variables.push("self",entry.getValue().value());
        entry.getValue().state(system.state()).attributeValueMap().forEach((a,v)->values.put(a.name(),(a.isDerived()?new org.tzi.use.uml.ocl.expr.Evaluator().eval(a.getDeriveExpression(),system.state(),system.state(),variables,null):v).toString()));
        var relations=new ArrayList<String>();var links=new ArrayList<Map<String,Object>>();for(var link:system.state().allLinks())if(link.linkedObjects().contains(entry.getValue())){relations.add(link.association().name()+":"+link.linkedObjects().stream().map(MObject::name).toList());links.add(Map.of("relation",link.association().name(),"ends",link.linkedObjects().stream().map(MObject::name).toList()));}
        links.sort(Comparator.comparing(l->l.get("relation")+":"+l.get("ends")));values.put("_links",List.copyOf(links));values.put("_kind",entry.getValue().cls().isSubClassifierOf(system.model().getClass("RoleOccurrence"))?"ROLE":entry.getValue().cls().isSubClassifierOf(system.model().getClass("OrgContext"))?"GROUP":"ENTITY");Collections.sort(relations);values.put("relations",String.join("; ",relations));rows.add(Map.copyOf(values));}return List.copyOf(rows);}
    static String hash(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}}
    @Override public synchronized void close(){sessions.values().forEach(Use4GoalRuntimeSessionAdapter::close);ocl.close();}
}

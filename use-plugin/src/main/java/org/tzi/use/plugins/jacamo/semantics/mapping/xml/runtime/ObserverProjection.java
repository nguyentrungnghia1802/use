package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.semantic.engine.use75.Use75CslModelBackend;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Explicit observer slot/history operators, on native metadata/savepoints. No clock or goal inference. */
public final class ObserverProjection {
    private record Project(Node node,Node view,MClass type,List<Node> writes,List<Node> references,NplObserverHistory lifecycle,List<NativeObserverExpression> derived) { }
    private final List<Project> projects;
    private final String program;
    public ObserverProjection(MappingSpecification specification,MModel model,String program) {
        this.program=Objects.requireNonNull(program);var compiled=new ArrayList<Project>();var usedViews=new HashSet<String>();
        for(Node node:specification.root().child("observers").children("project")) {
            Node view=specification.requireId(node.attr("viewRef")),table=specification.requireId(node.attr("tableRef"));
            if(!view.name().equals("view")||!view.attr("category").equals("CLASS")||!usedViews.add(view.attr("id"))||!table.name().equals("table"))throw node.location().error("OBSERVER_PROJECT_VIEW_OR_TABLE_INVALID");
            MClass type=model.getClass(view.attr("name"));
            if(type==null||!"EXTERNAL_OBSERVER_VIEW".equals(type.getAnnotationValue(Use75CslModelBackend.HELPER,"kind"))
                    ||!specification.sourceHash().equals(type.getAnnotationValue("DeclarativeObserverView","mappingHash")))throw node.location().error("OBSERVER_PROJECT_SCHEMA_OWNER_MISMATCH");
            var columns=RuntimeTables.outputColumns(table);
            if(!new HashSet<>(words(node.attr("keyFields"))).equals(new HashSet<>(words(view.attr("keyFields"))))||!columns.containsAll(words(node.attr("keyFields"))))throw node.location().error("OBSERVER_PROJECT_IDENTITY_COLUMNS_REQUIRED");
            var assigned=new HashSet<String>();
            for(Node write:node.children("write")) {
                if(!write.name().equals("write")||!assigned.add(write.attr("field"))||!columns.contains(write.attr("column")))throw write.location().error("OBSERVER_WRITE_FIELD_OR_COLUMN_INVALID");
                var field=type.attribute(write.attr("field"),true);
                if(field==null||field.isDerived()||!Set.of("Integer","String","Boolean","Set(String)").contains(field.type().toString()))throw write.location().error("UNSUPPORTED:OBSERVER_FIELD_REQUIRES_SCALAR_OR_STRING_SET");
                String mode=write.attr("mode");
                if(!Set.of("current","latch-true","freeze-first-qualified").contains(mode))throw write.location().error("OBSERVER_WRITE_POLICY_UNSUPPORTED");
                if(mode.equals("latch-true")&&!field.type().isTypeOfBoolean())throw write.location().error("OBSERVER_LATCH_BOOLEAN_REQUIRED");
                if(mode.equals("current")&&(!write.optional("historyColumn").isEmpty()||!write.optional("whenColumn").isEmpty()))throw write.location().error("OBSERVER_CURRENT_HISTORY_POLICY_UNSUPPORTED");
                if(!mode.equals("current")&&!columns.contains(write.optional("historyColumn")))throw write.location().error("OBSERVER_HISTORY_COLUMN_REQUIRED");
                if(mode.equals("freeze-first-qualified")&&!columns.contains(write.optional("whenColumn")))throw write.location().error("OBSERVER_FREEZE_CONDITION_REQUIRED");
                if(!write.optional("beforeFreeze").isEmpty()&&(!mode.equals("freeze-first-qualified")||!write.attr("beforeFreeze").equals("current")))throw write.location().error("OBSERVER_BEFORE_FREEZE_POLICY_UNSUPPORTED");
                if(mode.equals("latch-true")&&!write.optional("whenColumn").isEmpty())throw write.location().error("OBSERVER_LATCH_CONDITION_UNSUPPORTED");
            }
            for(Node reference:node.children("reference")) {
                if(!assigned.add(reference.attr("field"))||!columns.contains(reference.attr("subjectColumn")))throw reference.location().error("OBSERVER_REFERENCE_FIELD_OR_SUBJECT_INVALID");
                Project target=compiled.stream().filter(p->p.node().attr("id").equals(reference.attr("targetProject"))).findFirst().orElseThrow(()->reference.location().error("OBSERVER_REFERENCE_REQUIRES_PRIOR_PROJECT"));
                var field=view.children("field").stream().filter(f->f.attr("name").equals(reference.attr("field"))).findFirst().orElseThrow(()->reference.location().error("OBSERVER_REFERENCE_FIELD_UNRESOLVED"));
                if(!field.attr("type").equals(target.type().name())||model.getAssociation(association(view,reference))==null)throw reference.location().error("OBSERVER_REFERENCE_SCHEMA_TYPE_MISMATCH");
                if(!Set.of("current","retain-bound").contains(reference.attr("mode")))throw reference.location().error("OBSERVER_REFERENCE_MODE_UNSUPPORTED");
                var keys=new HashSet<String>();for(Node key:reference.children("key"))if(!keys.add(key.attr("field"))||!columns.contains(key.attr("column")))throw key.location().error("OBSERVER_REFERENCE_KEY_INVALID");
                if(!keys.equals(new HashSet<>(words(target.node().attr("keyFields")))))throw reference.location().error("OBSERVER_REFERENCE_EXACT_KEYS_REQUIRED");
            }
            var derived=new ArrayList<NativeObserverExpression>();
            for(Node expression:node.children("derive")) {
                if(!assigned.add(expression.attr("field")))throw expression.location().error("OBSERVER_DERIVED_FIELD_DUPLICATE");
                derived.add(new NativeObserverExpression(expression,model,type,columns));
            }
            if(!assigned.equals(view.children("field").stream().map(f->f.attr("name")).collect(java.util.stream.Collectors.toSet())))throw node.location().error("OBSERVER_PROJECT_FIELD_COVERAGE");
            NplObserverHistory lifecycle=null;
            if(!node.children("lifecycle").isEmpty()) {
                if(node.children("lifecycle").size()!=1)throw node.location().error("OBSERVER_LIFECYCLE_DUPLICATE");
                Node policy=node.child("lifecycle"),request=specification.requireId(policy.attr("requestRef")),dataset=specification.requireId(table.attr("dataset"));
                if(!policy.attr("kind").equals("NPL_REPORTED")||!request.name().equals("capability")||!request.attr("provider").equals("npl.lifecycle.v1")||!request.optional("hash").matches("[0-9a-f]{64}")
                        ||!dataset.name().equals("npl")||!dataset.attr("requestRef").equals(request.attr("id"))||!columns.contains(policy.attr("column")))throw policy.location().error("OBSERVER_NPL_LIFECYCLE_REQUIREMENT_OR_DATASET_INVALID");
                lifecycle=new NplObserverHistory(policy.attr("column"),request.attr("id"),request.attr("hash"));
            }
            compiled.add(new Project(node,view,type,List.copyOf(node.children("write")),List.copyOf(node.children("reference")),lifecycle,List.copyOf(derived)));
        }
        if(compiled.size()>64)throw specification.root().location().error("OBSERVER_PROJECT_CAPACITY");projects=List.copyOf(compiled);
    }
    public boolean active(){return !projects.isEmpty();}
    public List<M0RuleExecutor.Outcome> apply(Port port,String epoch,String cut,Map<String,RuntimeTables.Result> tables)throws UseApiException {
        return apply(port,epoch,cut,tables,null,-1);
    }
    public List<M0RuleExecutor.Outcome> apply(Port port,String epoch,String cut,Map<String,RuntimeTables.Result> tables,String run,long generation)throws UseApiException {
        var result=new ArrayList<M0RuleExecutor.Outcome>();
        long work=0;
        for(Project project:projects){var table=tables.get(project.node().attr("tableRef"));if(table==null)throw project.node().location().error("OBSERVER_TABLE_UNAVAILABLE");work+=(long)table.rows().size()*(project.writes().size()+project.references().size()+project.derived().stream().mapToInt(e->1+e.arguments()).sum());}
        if(work>65536)throw new IllegalArgumentException("OBSERVER_PROJECTION_WORK_CAPACITY");
        for(Project project:projects) {
            var table=tables.get(project.node().attr("tableRef"));
            if(table==null)throw project.node().location().error("OBSERVER_TABLE_UNAVAILABLE");
            var recordKeys=new HashSet<String>();
            for(var row:table.rows()){String key=key(project,row,epoch);if(key!=null&&!recordKeys.add(key))throw project.node().location().error("OBSERVER_ROW_KEY_AMBIGUOUS");}
            var touched=new HashSet<MObject>();
            for(var row:table.rows()) {
                String key=key(project,row,epoch);
                if(key==null){result.add(new M0RuleExecutor.Outcome(project.node().attr("id"),"INCOMPLETE","",null,"OBSERVER_IDENTITY_UNAVAILABLE"));continue;}
                var object=port.find(key);
                if(object==null&&row.quality()!=Completeness.COMPLETE){result.add(new M0RuleExecutor.Outcome(project.node().attr("id"),"INCOMPLETE",key,null,"OBSERVER_ROW_UNAVAILABLE"));continue;}
                if(object==null) {
                    if(port.objects().stream().filter(o->program.equals(port.metadata(o,"xml:observer:program"))).count()>=8192)throw new IllegalArgumentException("OBSERVER_OBJECT_CAPACITY");
                    object=port.upsert(key,project.type().name(),project.type().name()+"_"+SemanticJson.hash(key).substring(0,20));
                    port.metadata(object,"xml:observer:program",program);port.metadata(object,"xml:observer:epoch",epoch);port.metadata(object,"xml:observer:project",project.node().attr("id"));port.metadata(object,"xml:observer:key",key);
                    port.metadata(object,"xml:observer:subject",row.identity());
                    for(var field:project.type().allAttributes())port.value(object,field.name(),UndefinedValue.instance);
                }
                if(!owns(port,object,project,epoch))throw new IllegalArgumentException("OBSERVER_EXISTING_OWNER_MISMATCH");
                boolean complete=project(port,object,project,row,epoch,cut,run,generation);touched.add(object);
                port.metadata(object,"xml:observer:population",table.populationQuality().name());port.metadata(object,"xml:observer:cut",cut);
                result.add(new M0RuleExecutor.Outcome(project.node().attr("id"),complete?"MAPPED":"INCOMPLETE",key,object,"OBSERVER_VIEW_NOT_CSL_ENTITY"));
            }
            for(var object:List.copyOf(port.objects()))if(owns(port,object,project,epoch)&&!touched.contains(object)) {
                project(port,object,project,null,epoch,cut,run,generation);port.metadata(object,"xml:observer:population","UNAVAILABLE");
                result.add(new M0RuleExecutor.Outcome(project.node().attr("id"),"INCOMPLETE",port.metadata(object,"xml:observer:key"),object,"OBSERVER_CURRENT_ROW_UNAVAILABLE"));
            }
        }
        return List.copyOf(result);
    }
    private boolean project(Port port,MObject object,Project project,RuntimeTables.Row row,String epoch,String cut,String run,long generation)throws UseApiException {
        SemanticVerdict lifecycle=project.lifecycle()==null?SemanticVerdict.PASS:project.lifecycle().observe(port,object,row,run,generation);
        if(lifecycle==SemanticVerdict.ERROR) {
            for(Node write:project.writes()){port.value(object,write.attr("field"),UndefinedValue.instance);unknown(port,object,write.attr("field"),"ERROR","NPL_LIFECYCLE_CONFLICT");}
            for(var expression:project.derived()){port.value(object,expression.field(),UndefinedValue.instance);unknown(port,object,expression.field(),"ERROR","NPL_LIFECYCLE_CONFLICT");}
            for(Node reference:project.references()){port.disconnect(association(project.view(),reference),object,null);unknown(port,object,reference.attr("field"),"ERROR","NPL_LIFECYCLE_CONFLICT");}
            return false;
        }
        RuntimeTables.Row qualified=row;
        if(lifecycle!=SemanticVerdict.PASS&&row!=null)qualified=new RuntimeTables.Row(row.identity(),row.values(),Completeness.PARTIAL,row.proofs(),row.aliases());
        boolean complete=write(port,object,project,qualified,cut);complete=references(port,object,project,qualified,epoch,cut)&&complete;
        for(var expression:project.derived()) {
            var value=expression.evaluate(port,object,qualified);port.value(object,expression.field(),value.value());
            rememberValue(port,object,expression.field());
            port.metadata(object,"xml:observer:quality:"+expression.field(),value.quality());port.metadata(object,"xml:observer:diagnostic:"+expression.field(),value.diagnostic());port.metadata(object,"propertyAbsent:"+expression.field(),Boolean.toString(value.value().isUndefined()));
            if(!value.quality().equals("COMPLETE"))complete=false;
        }
        return complete;
    }
    private static void unknown(Port port,MObject object,String field,String quality,String diagnostic){port.metadata(object,"xml:observer:quality:"+field,quality);port.metadata(object,"xml:observer:diagnostic:"+field,diagnostic);port.metadata(object,"propertyAbsent:"+field,"true");}
    private boolean references(Port port,MObject object,Project project,RuntimeTables.Row row,String epoch,String cut)throws UseApiException {
        boolean complete=true;
        for(Node reference:project.references()) {
            String field=reference.attr("field"),association=association(project.view(),reference),quality="INCOMPLETE",diagnostic="OBSERVER_REFERENCE_IDENTITY_UNAVAILABLE";
            Project target=projects.stream().filter(p->p.node().attr("id").equals(reference.attr("targetProject"))).findFirst().orElseThrow();
            String candidate=null;var keys=new TreeMap<String,Object>();
            if(row!=null&&row.quality()==Completeness.COMPLETE&&row.values().get(reference.attr("subjectColumn")) instanceof String subject&&!subject.isBlank()) {
                boolean valid=true;
                for(Node key:reference.children("key")) {Object value=row.values().get(key.attr("column"));if(!(value instanceof String text)||text.isBlank()){valid=false;break;}keys.put(key.attr("field"),value);}
                if(valid)candidate=recordKey(target,subject,epoch,keys);
            }
            String stored=port.metadata(object,"xml:observer:reference:"+field),bound=null;
            if(reference.attr("mode").equals("retain-bound")&&!stored.isBlank()) {
                var evidence=CanonicalJson.object(CanonicalJson.decode(stored.getBytes(StandardCharsets.UTF_8)));var unsigned=new LinkedHashMap<>(evidence);Object hash=unsigned.remove("hash");
                if(!SemanticJson.hash(unsigned).equals(hash)||!epoch.equals(evidence.get("epoch"))||!program.equals(evidence.get("program"))||!target.node().attr("id").equals(evidence.get("project")))throw new IllegalArgumentException("OBSERVER_REFERENCE_STORED_OWNER_OR_HASH");
                bound=(String)evidence.get("target");
                if(candidate!=null&&!candidate.equals(bound)){quality="ERROR";diagnostic="OBSERVER_REFERENCE_BINDING_CONFLICT";port.metadata(object,"xml:observer:reference-conflict:"+field,"true");}
                else {candidate=bound;quality="HISTORICAL_COMPLETE";}
            }
            if("true".equals(port.metadata(object,"xml:observer:reference-conflict:"+field))){quality="ERROR";diagnostic="OBSERVER_REFERENCE_BINDING_CONFLICT";}
            MObject resolved=candidate==null?null:port.find(candidate);
            if(!quality.equals("ERROR")&&resolved!=null&&owns(port,resolved,target,epoch)) {
                if(!quality.equals("HISTORICAL_COMPLETE"))quality="COMPLETE";diagnostic="";
                if(reference.attr("mode").equals("retain-bound")&&bound==null) {
                    var evidence=new LinkedHashMap<String,Object>();evidence.put("program",program);evidence.put("epoch",epoch);evidence.put("project",target.node().attr("id"));evidence.put("target",candidate);evidence.put("cut",cut);evidence.put("hash",SemanticJson.hash(evidence));
                    port.metadata(object,"xml:observer:reference:"+field,new String(CanonicalJson.encode(evidence),StandardCharsets.UTF_8));
                }
            }else {resolved=null;if(!quality.equals("ERROR"))quality="INCOMPLETE";}
            // A missing current reference is undefined, never redirected to a similarly named occurrence.
            port.disconnect(association,object,null);if(resolved!=null)port.connect(association,object,resolved);
            port.metadata(object,"xml:observer:reference-target:"+field,resolved==null?"":resolved.name());
            if(!Set.of("COMPLETE","HISTORICAL_COMPLETE").contains(quality))complete=false;
            port.metadata(object,"xml:observer:quality:"+field,quality);port.metadata(object,"xml:observer:diagnostic:"+field,diagnostic);port.metadata(object,"propertyAbsent:"+field,Boolean.toString(resolved==null));
        }
        return complete;
    }
    private static String association(Node view,Node reference){return "Observer_"+view.attr("id").replace('-','_').replace('.','_')+"_"+reference.attr("field");}
    private String key(Project project,RuntimeTables.Row row,String epoch) {
        var keys=new TreeMap<String,Object>();
        for(String field:words(project.node().attr("keyFields"))) {
            Object value=row.values().get(field);if(!(value instanceof String text)||text.isBlank())return null;keys.put(field,value);
        }
        return recordKey(project,row.identity(),epoch,keys);
    }
    private String recordKey(Project project,String subject,String epoch,Map<String,Object> keys){return "xml:observer:"+SemanticJson.hash(Map.of("program",program,"epoch",epoch,"project",project.node().attr("id"),"subject",subject,"keys",keys));}
    private boolean write(Port port,MObject object,Project project,RuntimeTables.Row row,String cut)throws UseApiException {
        boolean complete=true;
        for(Node write:project.writes()) {
            String field=write.attr("field"),type=project.type().attribute(field,true).type().toString(),mode=write.attr("mode");
            String stored=port.metadata(object,"xml:observer:history:"+field),quality="INCOMPLETE",diagnostic="CURRENT_QUALIFIED_VALUE_UNAVAILABLE";Object raw=null;
            boolean observed=row!=null&&row.quality()==Completeness.COMPLETE;boolean history=observed&&Boolean.TRUE.equals(row.values().get(write.optional("historyColumn")));
            try {
                if(!mode.equals("current")&&!stored.isBlank()) {
                    var frozen=CanonicalJson.object(CanonicalJson.decode(stored.getBytes(StandardCharsets.UTF_8)));
                    if(!mode.equals(frozen.get("policy"))||!SemanticJson.hash(frozen.get("value")).equals(frozen.get("valueHash")))throw new IllegalArgumentException("OBSERVER_STORED_HISTORY_OWNER_OR_HASH");
                    raw=frozen.get("value");quality="HISTORICAL_COMPLETE";diagnostic="";
                } else if(observed&&row.values().containsKey(write.attr("column"))) {
                    Object candidate=row.values().get(write.attr("column"));boolean remember=false;
                    switch(mode) {
                        case "current"->{raw=candidate;quality="COMPLETE";diagnostic="";}
                        case "latch-true"->{
                            if(!(candidate instanceof Boolean))throw new IllegalArgumentException("OBSERVER_LATCH_TYPED_BOOLEAN_REQUIRED");
                            if(Boolean.TRUE.equals(candidate)||history){raw=candidate;quality="COMPLETE";diagnostic="";remember=Boolean.TRUE.equals(candidate);}
                        }
                        case "freeze-first-qualified"->{
                            Object condition=row.values().get(write.attr("whenColumn"));
                            if(history&&(Boolean.TRUE.equals(condition)||Boolean.FALSE.equals(condition)&&write.optional("beforeFreeze").equals("current"))){raw=candidate;quality="COMPLETE";diagnostic="";remember=Boolean.TRUE.equals(condition);}
                        }
                        default->throw new IllegalStateException("OBSERVER_ADMITTED_POLICY_CHANGED");
                    }
                    if(raw!=null&&remember) {
                        scalar(type,raw);var evidence=Map.of("policy",mode,"value",raw,"valueHash",SemanticJson.hash(raw),"cut",cut,"subject",row.identity(),"proofs",row.proofs());
                        port.metadata(object,"xml:observer:history:"+field,new String(CanonicalJson.encode(evidence),StandardCharsets.UTF_8));
                    }
                }
                Value value=raw==null?UndefinedValue.instance:scalar(type,raw);port.value(object,field,value);
            }catch(IllegalArgumentException|ContractException invalid) {
                port.value(object,field,UndefinedValue.instance);quality="ERROR";diagnostic=invalid.getMessage();
            }
            rememberValue(port,object,field);
            if(!Set.of("COMPLETE","HISTORICAL_COMPLETE").contains(quality))complete=false;
            port.metadata(object,"xml:observer:quality:"+field,quality);port.metadata(object,"xml:observer:diagnostic:"+field,diagnostic);
            port.metadata(object,"propertyAbsent:"+field,Boolean.toString(!Set.of("COMPLETE","HISTORICAL_COMPLETE").contains(quality)));
        }
        return complete;
    }
    private boolean owns(Port port,MObject object,Project project,String epoch){return object.cls()==project.type()&&program.equals(port.metadata(object,"xml:observer:program"))&&epoch.equals(port.metadata(object,"xml:observer:epoch"))&&project.node().attr("id").equals(port.metadata(object,"xml:observer:project"));}
    private static void rememberValue(Port port,MObject object,String field) {
        port.metadata(object,"xml:observer:value-hash:"+field,org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.valueHash(port.currentValue(object,field)));
    }
    static Value scalar(String type,Object value) {
        return switch(type) {
            case "Boolean"->{if(!(value instanceof Boolean b))throw new IllegalArgumentException("OBSERVER_TYPED_BOOLEAN_REQUIRED");yield BooleanValue.get(b);}
            case "String"->{if(!(value instanceof String text))throw new IllegalArgumentException("OBSERVER_TYPED_STRING_REQUIRED");yield new StringValue(text);}
            case "Integer"->{if(!(value instanceof Number n))throw new IllegalArgumentException("OBSERVER_TYPED_INTEGER_REQUIRED");try{yield IntegerValue.valueOf(new java.math.BigDecimal(n.toString()).intValueExact());}catch(ArithmeticException e){throw new IllegalArgumentException("OBSERVER_INTEGER_RANGE_OR_FRACTION");}}
            case "Set(String)"->{if(!(value instanceof List<?> list)||list.size()>4096||list.stream().anyMatch(v->!(v instanceof String)))throw new IllegalArgumentException("OBSERVER_TYPED_STRING_SET_REQUIRED");yield new SetValue(TypeFactory.mkString(),list.stream().map(v->(Value)new StringValue((String)v)).toList());}
            default->throw new IllegalArgumentException("OBSERVER_SCALAR_TYPE_UNSUPPORTED");
        };
    }
    private static List<String> words(String value){var result=List.of(value.trim().split("\\s+"));if(result.contains("")||new HashSet<>(result).size()!=result.size())throw new IllegalArgumentException("OBSERVER_NONEMPTY_UNIQUE_KEYS_REQUIRED");return result;}
}

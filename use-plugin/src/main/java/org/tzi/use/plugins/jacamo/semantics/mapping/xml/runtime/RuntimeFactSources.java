package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClock;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Framework fact envelopes from an admitted snapshot. Provider payloads require separate qualification. */
public final class RuntimeFactSources {
    public static Set<RuntimeFactKind> queryCartagoKinds(MappingSpecification specification) {
        var kinds=EnumSet.copyOf(producerCartagoKinds(specification));
        for(var section:specification.root().child("runtime").children("datasets"))for(var dataset:section.children())
            if(Set.of("operationCuts","operationReferences").contains(dataset.name()))kinds.add(RuntimeFactKind.OPERATION);
        return Set.copyOf(kinds);
    }
    /** Only requested populations need a coherent publication at an exact step boundary.
     * Operation descriptors remain in the initial snapshot and in their ordinary event lane;
     * a spec explicitly querying them includes them here as well. */
    public static Set<RuntimeFactKind> producerCartagoKinds(MappingSpecification specification) {
        var kinds=EnumSet.of(RuntimeFactKind.ARTIFACT,RuntimeFactKind.WORKSPACE,RuntimeFactKind.AGENT);
        for(var section:specification.root().child("runtime").children("datasets"))for(var dataset:section.children()) {
            if(Set.of("facts","removals").contains(dataset.name())&&dataset.attr("authority").equals("cartago"))kinds.add(RuntimeFactKind.valueOf(dataset.attr("kind")));
            if(dataset.name().equals("fieldCuts"))kinds.add(RuntimeFactKind.PROPERTY);
        }
        return Set.copyOf(kinds);
    }
    /** Exact source fields available to this dialect, plus the physical player crosswalk.
     * Mental state is not exposed by facts datasets; step evidence has its own capability lane. */
    public static Map<String,List<String>> producerFields(MappingSpecification specification) {
        var names=new TreeSet<>(Set.of("normalizedEventKind","semanticId","name","active","agentDeclarationSemanticId","agentRuntimeSemanticId"));
        for(var section:specification.root().child("runtime").children("datasets"))for(var dataset:section.children("facts"))
            if(dataset.attr("authority").equals("jason")&&dataset.attr("kind").equals("AGENT"))
                for(var tables:specification.root().child("runtime").children("tables"))for(var table:tables.children("table"))
                    if(table.attr("dataset").equals(dataset.attr("id")))agentFieldReads(table,names);
        for(var section:specification.root().child("runtime").children("datasets"))if(!section.children("beliefViews").isEmpty())names.addAll(Set.of("cycles","storedBeliefObservation"));
        return Map.of("jason",List.copyOf(names));
    }
    private static void agentFieldReads(Node node,Set<String> selected) {
        var available=fields("jason",RuntimeFactKind.AGENT);
        for(var entry:node.attributes().entrySet())if(entry.getKey().equals("path")||entry.getKey().endsWith("Path")||entry.getKey().equals("groupPaths"))
            for(String path:entry.getValue().split(" +")) {
                if(path.equals("values"))selected.addAll(available); // Explicit whole-value read is conservatively broad.
                else if(path.startsWith("values.")) {
                    String field=path.substring(7).split("\\.",2)[0];
                    if(available.contains(field))selected.add(field);
                }
            }
        node.children().forEach(child->agentFieldReads(child,selected));
    }
    private record Definition(Node node,String authority,RuntimeFactKind kind,Set<String> fields) { }
    private final List<Definition> definitions;
    private final Map<String,ObservationCapabilityRequest> requirements;
    private final Map<String,ArtifactFieldCutDecoder> fieldDecoders;
    private final Map<String,MoiseSchemeCutDecoder> schemeDecoders;
    private final Map<String,NplLifecycleCut> normDecoders;
    private final Map<String,OperationHistoryCutDecoder> operationDecoders;
    private final Map<String,JasonStepCutDecoder> stepDecoders;
    private final MoiseFactQualification moise;
    private final ModelSnapshot sourceModel;
    private final List<org.jacamo.bridge.contract.semantic.JcmSemanticContract.GroupDeploymentSemantic> groupDeclarations;
    public RuntimeFactSources(MappingSpecification specification) {
        this(specification,null,List.of());
    }
    public RuntimeFactSources(MappingSpecification specification,ModelSnapshot source,List<ObservationCapabilityRequest> requests) {
        sourceModel=source;
        moise=source==null?null:new MoiseFactQualification(source);
        groupDeclarations=source==null?List.of():List.copyOf(source.semanticContract().groupDeployments());
        var steps=new LinkedHashMap<String,JasonStepCutDecoder>();var operations=new LinkedHashMap<String,OperationHistoryCutDecoder>();var norms=new LinkedHashMap<String,NplLifecycleCut>();var requestIndex=new LinkedHashMap<String,ObservationCapabilityRequest>();
        for(var request:requests)if(requestIndex.putIfAbsent(request.id(),request)!=null)throw new IllegalArgumentException("RUNTIME_CAPABILITY_REQUEST_DUPLICATE");
        requirements=Map.copyOf(requestIndex);var decoders=new LinkedHashMap<String,ArtifactFieldCutDecoder>();var schemes=new LinkedHashMap<String,MoiseSchemeCutDecoder>();
        var sections=specification.root().child("runtime").children("datasets");
        if(sections.size()>1)throw specification.root().location().error("RUNTIME_DATASETS_DUPLICATE");
        var result=new ArrayList<Definition>();
        if(!sections.isEmpty())for(Node node:sections.getFirst().children()) {
            if(result.size()>=64)throw node.location().error("RUNTIME_DATASET_LIMIT");
            if(node.name().equals("beliefViews")) {
                JasonBeliefViews.validate(node);result.add(new Definition(node,"jason",RuntimeFactKind.AGENT,Set.of()));continue;
            }
            if(node.name().equals("initialGoals")) {
                if(source==null)throw node.location().error("RUNTIME_INITIAL_GOALS_SOURCE_REQUIRED");
                result.add(new Definition(node,"jason",RuntimeFactKind.AGENT,Set.of()));continue;
            }
            if(Set.of("jasonSteps","artifactReferences","schemeReferences").contains(node.name())) {
                var request=requirements.get(node.attr("requestRef"));
                if(source==null||request==null)throw node.location().error("RUNTIME_JASON_REQUIREMENT_UNRESOLVED");
                steps.put(node.attr("id"),new JasonStepCutDecoder(source,request));
                result.add(switch(node.name()) {
                    case "artifactReferences"->new Definition(node,"cartago",RuntimeFactKind.ARTIFACT,Set.of());
                    case "schemeReferences"->new Definition(node,"moise",RuntimeFactKind.SCHEME_BOARD,Set.of());
                    default->new Definition(node,"observer",RuntimeFactKind.RUNTIME_EVENT,Set.of());
                });continue;
            }
            if(Set.of("operationCuts","operationReferences").contains(node.name())) {
                var request=requirements.get(node.attr("requestRef"));
                if(source==null||request==null)throw node.location().error("RUNTIME_OPERATION_REQUIREMENT_UNRESOLVED");
                if(node.name().equals("operationReferences")&&!node.attr("argumentIndex").matches("[0-9]|[1-9][0-9]|1[01][0-9]|12[0-7]"))throw node.location().error("OPERATION_REFERENCE_ARGUMENT_INDEX_UNSUPPORTED");
                operations.put(node.attr("id"),new OperationHistoryCutDecoder(request));result.add(new Definition(node,"cartago",RuntimeFactKind.ARTIFACT,Set.of()));continue;
            }
            if(node.name().equals("npl")) {
                var request=requirements.get(node.attr("requestRef"));
                if(source==null||request==null||!request.provider().equals("npl.lifecycle.v1")||!request.parameters().keySet().equals(Set.of("hash"))||!(request.parameters().get("hash") instanceof String hash)||!hash.matches("[0-9a-f]{64}"))throw node.location().error("RUNTIME_NPL_PINNED_REQUIREMENT_REQUIRED");
                norms.put(node.attr("id"),new NplLifecycleCut(hash));result.add(new Definition(node,"npl",RuntimeFactKind.NORM_INSTANCE,Set.of()));continue;
            }
            if(node.name().equals("schemeCuts")) {
                var request=requirements.get(node.attr("requestRef"));
                if(source==null||request==null||!request.provider().equals("moise.scheme-lifecycle.v1"))throw node.location().error("RUNTIME_SCHEME_CAPABILITY_REQUIREMENT_UNRESOLVED");
                schemes.put(node.attr("id"),new MoiseSchemeCutDecoder(source,request));result.add(new Definition(node,"moise",RuntimeFactKind.SCHEME_BOARD,Set.of()));continue;
            }
            if(node.name().equals("clock")) {
                var request=requirements.get(node.attr("requestRef"));
                if(source==null||request==null||!request.provider().equals("npl.source-clock.v1")||!(request.parameters().get("hash") instanceof String hash)||!hash.matches("[0-9a-f]{64}"))throw node.location().error("RUNTIME_CLOCK_PINNED_REQUIREMENT_REQUIRED");
                result.add(new Definition(node,"observer",RuntimeFactKind.RUNTIME_EVENT,Set.of()));continue;
            }
            if(node.name().equals("fieldCuts")) {
                var request=requirements.get(node.attr("requestRef"));
                if(source==null||request==null||!request.provider().equals("cartago.artifact-field.v1"))throw node.location().error("RUNTIME_FIELD_CAPABILITY_REQUIREMENT_UNRESOLVED");
                decoders.put(node.attr("id"),new ArtifactFieldCutDecoder(source,request));
                result.add(new Definition(node,"cartago",RuntimeFactKind.ARTIFACT,Set.of()));continue;
            }
            if(!Set.of("facts","removals").contains(node.name()))throw node.location().error("RUNTIME_DATASET_UNSUPPORTED");
            RuntimeFactKind kind;
            try{kind=RuntimeFactKind.valueOf(node.attr("kind"));}catch(IllegalArgumentException invalid){throw node.location().error("RUNTIME_FACT_KIND_UNSUPPORTED");}
            Set<String> fields=fields(node.attr("authority"),kind);
            if(fields==null)throw node.location().error("FRAMEWORK_FACT_DATASET_UNSUPPORTED");
            if(node.name().equals("removals")&&(!node.attr("authority").equals("cartago")||!Set.of(RuntimeFactKind.ARTIFACT,RuntimeFactKind.AGENT).contains(kind)))throw node.location().error("FRAMEWORK_REMOVAL_DATASET_UNSUPPORTED");
            result.add(new Definition(node,node.attr("authority"),kind,fields));
            if(result.size()>64)throw node.location().error("RUNTIME_DATASET_LIMIT");
        }
        definitions=List.copyOf(result);
        fieldDecoders=Map.copyOf(decoders);
        schemeDecoders=Map.copyOf(schemes);
        normDecoders=Map.copyOf(norms);
        operationDecoders=Map.copyOf(operations);
        stepDecoders=Map.copyOf(steps);
    }
    public Map<String,RuntimeTables.Dataset> capture(RuntimeSnapshot cut,String run,long generation) {
        return capture(cut,run,generation,null);
    }
    /** Trusted clock context for this immutable admitted cut, never decoded from mapping row fields. */
    /** Validation reuse is confined to this immutable cut and epoch, never a live state/cache. */
    private final class CaptureContext {
        final RuntimeSnapshot cut;final String run;final long generation;
        final Map<RuntimeFact,VerificationObservation> decoded=new IdentityHashMap<>();
        final Set<RuntimeFact> qualified=Collections.newSetFromMap(new IdentityHashMap<>());
        CaptureContext(RuntimeSnapshot cut,String run,long generation){this.cut=cut;this.run=run;this.generation=generation;}
        VerificationObservation observation(RuntimeFact fact){return decoded.computeIfAbsent(fact,f->VerificationObservation.require(f,cut,run,generation));}
        List<VerificationObservation> observations(){return cut.facts().stream().filter(f->f.values().containsKey(VerificationObservation.KEY)).map(this::observation).toList();}
        void qualify(RuntimeFact fact){if(qualified.contains(fact))return;moise.require(fact,run);qualified.add(fact);}
    }
    public record Captured(Map<String,RuntimeTables.Dataset> datasets,Map<String,SourceClock> clocks) {
        public Captured {datasets=Map.copyOf(datasets);clocks=Map.copyOf(clocks);}
    }
    public Captured captureWithClocks(RuntimeSnapshot cut,String run,long generation,RuntimeEvent event) {
        var context=new CaptureContext(cut,run,generation);
        return new Captured(capture(cut,run,generation,event,context),clocks(cut,run,generation,context));
    }
    public Map<String,SourceClock> clocks(RuntimeSnapshot cut,String run,long generation) {
        return clocks(cut,run,generation,new CaptureContext(cut,run,generation));
    }
    private Map<String,SourceClock> clocks(RuntimeSnapshot cut,String run,long generation,CaptureContext context) {
        SnapshotAdmission.require(cut,Map.of());var result=new LinkedHashMap<String,SourceClock>();
        for(Definition definition:definitions)if(definition.node().name().equals("clock")) {
            var request=requirements.get(definition.node().attr("requestRef"));
            var frames=context.observations().stream().filter(o->o.kind()==VerificationObservation.Kind.CLOCK).toList();
            var clock=frames.size()==1?SourceClock.decode(frames.getFirst(),run,generation,cut.modelRevision(),(String)request.parameters().get("hash")):SourceClock.unavailable();
            if(cut.sourceCompleteness().getOrDefault(VerificationObservation.SOURCE_ID,Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE
                    ||!Objects.equals(cut.startWatermarks().get(VerificationObservation.SOURCE_ID),cut.endWatermarks().get(VerificationObservation.SOURCE_ID)))clock=SourceClock.unavailable();
            result.put(request.id(),clock);
        }
        return Collections.unmodifiableMap(result);
    }
    public Map<String,RuntimeTables.Dataset> capture(RuntimeSnapshot cut,String run,long generation,RuntimeEvent event) {
        return capture(cut,run,generation,event,new CaptureContext(cut,run,generation));
    }
    private Map<String,RuntimeTables.Dataset> capture(RuntimeSnapshot cut,String run,long generation,RuntimeEvent event,CaptureContext context) {
        if(run==null||run.isBlank()||generation<0)throw new IllegalArgumentException("RUNTIME_DATASET_EPOCH_REQUIRED");
        SnapshotAdmission.require(cut,Map.of());
        if(definitions.stream().anyMatch(d->(d.node().name().equals("facts")||d.node().name().equals("initialGoals"))&&d.authority().equals("jason")&&d.kind()==RuntimeFactKind.AGENT)
                &&JasonAgentInventory.supplied(cut)&&!JasonAgentInventory.complete(cut,run))throw new IllegalArgumentException("JASON_AGENT_INVENTORY_MISMATCH");
        if(cut.facts().size()>16384)throw new IllegalArgumentException("RUNTIME_FACT_CAPACITY");
        var identities=new HashSet<String>();for(var fact:cut.facts())if(!identities.add(fact.id().canonical()))throw new IllegalArgumentException("RUNTIME_FACT_IDENTITY_COLLISION");
        String proof=SemanticJson.hash(Map.of("run",run,"generation",generation,"revision",cut.modelRevision(),"cut",cut.snapshotId(),"state",cut.stateFingerprint(),"watermarks",SemanticJson.tree(cut.endWatermarks())));
        var result=new LinkedHashMap<String,RuntimeTables.Dataset>();
        for(Definition definition:definitions) {
            if(definition.node().name().equals("beliefViews")) {result.put(definition.node().attr("id"),JasonBeliefViews.capture(definition.node(),cut,run,generation,proof));continue;}
            if(definition.node().name().equals("initialGoals")) {result.put(definition.node().attr("id"),initialGoals(definition,cut,run,generation,proof));continue;}
            if(Set.of("artifactReferences","schemeReferences").contains(definition.node().name())) {result.put(definition.node().attr("id"),artifactReferences(definition,cut,run,generation,proof,context));continue;}
            if(definition.node().name().equals("jasonSteps")) {result.put(definition.node().attr("id"),steps(definition,cut,run,generation,proof,context));continue;}
            if(definition.node().name().equals("operationReferences")) {result.put(definition.node().attr("id"),operationReferences(definition,cut,run,generation,proof));continue;}
            if(definition.node().name().equals("operationCuts")) {result.put(definition.node().attr("id"),operationCuts(definition,cut,run,generation,proof));continue;}
            if(definition.node().name().equals("npl")) {result.put(definition.node().attr("id"),norms(definition,cut,run,generation,proof));continue;}
            if(definition.node().name().equals("schemeCuts")) {result.put(definition.node().attr("id"),schemeCuts(definition,cut,run,generation,proof,context));continue;}
            if(definition.node().name().equals("clock")) {result.put(definition.node().attr("id"),clock(definition,cut,run,generation,proof,context));continue;}
            if(definition.node().name().equals("fieldCuts")) {result.put(definition.node().attr("id"),fieldCuts(definition,cut,run,generation,proof));continue;}
            if(definition.node().name().equals("removals")) {result.put(definition.node().attr("id"),removals(definition,cut,run,generation,event,proof));continue;}
            var rows=new ArrayList<RuntimeTables.Row>();var sources=cut.sourceCompleteness().keySet().stream().filter(s->sourceFamily(s,definition.authority())).toList();
            Completeness population=sources.isEmpty()?Completeness.UNAVAILABLE:Completeness.COMPLETE;
            for(String source:sources)population=merge(population,cut.sourceCompleteness().get(source));
            for(RuntimeFact fact:cut.facts())if(fact.kind()==definition.kind()&&fact.id().authority().equals(definition.authority())) {
                String source=sourceFor(fact,cut);
                if(!cut.sourceCompleteness().containsKey(source)||!cut.endWatermarks().containsKey(source))throw new IllegalArgumentException("RUNTIME_FACT_SOURCE_UNAVAILABLE:"+source);
                Completeness quality=fact.completeness();
                if(fact.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY)quality=Completeness.UNAVAILABLE;
                if(cut.sourceCompleteness().get(source)==Completeness.UNAVAILABLE)quality=Completeness.UNAVAILABLE;
                if(!cut.startWatermarks().get(source).equals(cut.endWatermarks().get(source)))quality=merge(quality,Completeness.PARTIAL);
                if(quality==Completeness.COMPLETE){verifyPhysical(fact);if(moise!=null&&fact.id().authority().equals("moise"))context.qualify(fact);}
                var values=new LinkedHashMap<String,Object>();
                // Closed public API fields: a nested internal-field/step receipt cannot bypass its capability decoder.
                fact.values().forEach((key,value)->{if(definition.fields().contains(key))values.put(key,value);});
                var envelope=new LinkedHashMap<String,Object>();
                envelope.put("identity",fact.id().canonical());envelope.put("authority",fact.id().authority());envelope.put("dimension",fact.id().dimension());
                envelope.put("kind",fact.kind().name());envelope.put("scope",fact.id().scope());envelope.put("localId",fact.id().localId());envelope.put("incarnation",fact.id().incarnation());
                envelope.put("values",values);envelope.put("source",source);envelope.put("sourceSequence",cut.endWatermarks().get(source).sequence());
                if(fact.kind()==RuntimeFactKind.PROPERTY)scalar(values).ifPresent(value->envelope.put("scalar",value));
                envelope.put("run",run);envelope.put("generation",generation);envelope.put("revision",cut.modelRevision());
                rows.add(new RuntimeTables.Row(fact.id().canonical(),envelope,quality,Map.of("source-cut",proof,"physical-identity",fact.id().canonical()),Set.of(fact.id().canonical())));
                population=merge(population,quality);
            }
            result.put(definition.node().attr("id"),withPopulation(definition,rows,population,cut,run,proof));
        }
        return Collections.unmodifiableMap(result);
    }
    private RuntimeTables.Dataset initialGoals(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof) {
        if(sourceModel==null)throw new IllegalStateException("RUNTIME_INITIAL_GOALS_SOURCE_REQUIRED");
        var rows=new ArrayList<RuntimeTables.Row>();
        Completeness population=cut.sourceCompleteness().getOrDefault("jason",Completeness.UNAVAILABLE);
        if(!cut.startWatermarks().containsKey("jason")||!Objects.equals(cut.startWatermarks().get("jason"),cut.endWatermarks().get("jason")))population=merge(population,Completeness.PARTIAL);
        var declarations=new LinkedHashMap<String,String>();
        for(var decl:sourceModel.semanticContract().agentDeclarations())declarations.put(decl.metadata().semanticId(),decl.name());
        var programs=new LinkedHashMap<String,org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic>();
        for(var prog:sourceModel.semanticContract().jasonPrograms())programs.put(prog.declarationId(),prog);
        for(RuntimeFact fact:cut.facts())if(fact.kind()==RuntimeFactKind.AGENT&&fact.id().authority().equals("jason")) {
            if(!cut.sourceCompleteness().containsKey("jason")||!cut.endWatermarks().containsKey("jason"))throw new IllegalArgumentException("RUNTIME_FACT_SOURCE_UNAVAILABLE:jason");
            Completeness quality=fact.completeness();
            if(fact.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY)quality=Completeness.UNAVAILABLE;
            if(population==Completeness.UNAVAILABLE)quality=Completeness.UNAVAILABLE;
            else if(population==Completeness.PARTIAL)quality=merge(quality,Completeness.PARTIAL);
            String declId=Objects.toString(fact.values().get("agentDeclarationSemanticId"),"");
            String declName=declarations.get(declId);
            var program=declName==null?null:programs.get(declName);
            if(program==null){population=merge(population,Completeness.UNAVAILABLE);continue;}
            for(var goal:program.goals()) {
                String rowId=goal.metadata().semanticId()+"@"+fact.id().canonical();
                var parsed=canonicalGroundLiteral(goal.literal());
                var envelope=new LinkedHashMap<String,Object>();
                envelope.put("identity",rowId);envelope.put("goalSemanticId",goal.metadata().semanticId());
                envelope.put("player",fact.id().canonical());envelope.put("owners",List.of(fact.id().canonical()));
                envelope.put("agentName",Objects.toString(fact.values().get("name"),""));envelope.put("declaration",program.declarationId());
                envelope.put("scope",fact.id().scope());envelope.put("ordinal",goal.ordinal());envelope.put("goalKind",goal.goalKind());
                envelope.put("literal",goal.literal());envelope.put("values",parsed);
                envelope.put("run",run);envelope.put("generation",generation);envelope.put("revision",cut.modelRevision());
                var proofs=new LinkedHashMap<String,String>();proofs.put("source-cut",proof);proofs.put("physical-identity",fact.id().canonical());proofs.put("initial-goal",goal.metadata().semanticId());
                if(population==Completeness.COMPLETE&&JasonAgentInventory.complete(cut,run))proofs.put("jason-agent-inventory",proof);
                rows.add(new RuntimeTables.Row(rowId,envelope,quality,proofs,Set.of(rowId,goal.metadata().semanticId())));
                population=merge(population,quality);
            }
        }
        return new RuntimeTables.Dataset(rows,population);
    }
    private static Map<String,Object> canonicalGroundLiteral(String syntax) {
        String trimmed=syntax==null?"":syntax.trim();
        var term=parseGroundTerm(trimmed,0);
        if(term.get("key") instanceof Map<?,?> key&&"LITERAL".equals(key.get("kind"))) {
            var top=new LinkedHashMap<String,Object>();
            top.put("raw",trimmed);top.put("namespace",key.get("namespace"));top.put("functor",key.get("functor"));
            top.put("negative",key.get("negative"));top.put("ground",term.get("ground"));top.put("supported",term.get("supported"));
            var rawTerms=term.get("nestedTerms") instanceof List<?> list?list:List.of();
            top.put("arity",rawTerms.size());top.put("terms",rawTerms);
            return Collections.unmodifiableMap(top);
        }
        return Map.of("raw",trimmed,"namespace","default","functor",trimmed,"negative",false,"ground",false,"supported",false,"arity",0,"terms",List.of());
    }
    private static Map<String,Object> parseGroundTerm(String raw,int depth) {
        String s=raw==null?"":raw.trim();
        if(s.isEmpty()||depth>=32)return Map.of("raw",s,"text",s,"ground",false,"supported",false,"key",Map.of("unsupported",s));
        if(s.length()>=2&&s.startsWith("\"")&&s.endsWith("\"")) {
            var unescaped=new StringBuilder();
            for(int i=1;i<s.length()-1;i++) {
                char c=s.charAt(i);
                if(c=='\\'&&i+1<s.length()-1){char next=s.charAt(++i);unescaped.append(switch(next){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->next;});}
                else unescaped.append(c);
            }
            return Map.of("raw",s,"text",s,"ground",true,"supported",true,"key",Map.of("kind","STRING","value",unescaped.toString()));
        }
        try {
            var num=new java.math.BigDecimal(s).stripTrailingZeros();
            return Map.of("raw",s,"text",s,"ground",true,"supported",true,"key",Map.of("kind","NUMBER","value",num));
        }catch(NumberFormatException notNumber){ }
        if(s.startsWith("[")&&s.endsWith("]")) {
            String inner=s.substring(1,s.length()-1).trim();
            var parts=inner.isEmpty()?List.<String>of():splitTopLevelArgs(inner);
            var items=new ArrayList<Map<String,Object>>();
            for(String part:parts)items.add(parseGroundTerm(part,depth+1));
            boolean ground=items.stream().allMatch(i->Boolean.TRUE.equals(i.get("ground")));
            boolean supported=items.stream().allMatch(i->Boolean.TRUE.equals(i.get("supported")));
            var keys=items.stream().map(i->{var m=new LinkedHashMap<>(i);m.remove("nestedTerms");return m.get("key");}).toList();
            return Map.of("raw",s,"text",s,"ground",ground,"supported",supported,"key",Map.of("kind","LIST","items",keys,"tail",""));
        }
        if(Character.isUpperCase(s.charAt(0))||s.startsWith("_")) {
            return Map.of("raw",s,"text",s,"ground",false,"supported",false,"key",Map.of("unsupported",s));
        }
        String work=stripTrailingAnnotations(s);
        String namespace="default";
        int nsIdx=indexOfTopLevelNs(work);
        if(nsIdx>0){namespace=work.substring(0,nsIdx).trim();work=work.substring(nsIdx+2).trim();}
        boolean negative=false;
        if(work.startsWith("~")){negative=true;work=work.substring(1).trim();}
        int openParen=indexOfTopLevelOpenParen(work);
        String functor;List<String> argStrings;
        if(openParen>0&&work.endsWith(")")) {
            functor=work.substring(0,openParen).trim();
            String inside=work.substring(openParen+1,work.length()-1).trim();
            argStrings=inside.isEmpty()?List.of():splitTopLevelArgs(inside);
        }else {
            functor=work;argStrings=List.of();
        }
        var nested=new ArrayList<Map<String,Object>>();
        for(String arg:argStrings) {
            var parsedArg=new LinkedHashMap<>(parseGroundTerm(arg,depth+1));
            parsedArg.remove("nestedTerms");
            nested.add(Collections.unmodifiableMap(parsedArg));
        }
        boolean ground=!functor.isEmpty()&&nested.stream().allMatch(i->Boolean.TRUE.equals(i.get("ground")));
        boolean supported=ground&&nested.stream().allMatch(i->Boolean.TRUE.equals(i.get("supported")));
        var termKeys=nested.stream().map(i->i.get("key")).toList();
        var key=Map.of("kind","LITERAL","namespace",namespace,"functor",functor,"negative",negative,"terms",termKeys);
        return Map.of("raw",s,"text",s,"ground",ground,"supported",supported,"key",key,"nestedTerms",List.copyOf(nested));
    }
    private static String stripTrailingAnnotations(String s) {
        if(!s.endsWith("]"))return s;
        int depthParen=0,depthBracket=0;boolean inQuotes=false;
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c=='"'&&(i==0||s.charAt(i-1)!='\\'))inQuotes=!inQuotes;
            else if(!inQuotes) {
                if(c=='(')depthParen++;else if(c==')')depthParen--;
                else if(c=='['){if(depthParen==0&&depthBracket==0)return s.substring(0,i).trim();depthBracket++;}
                else if(c==']')depthBracket--;
            }
        }
        return s;
    }
    private static int indexOfTopLevelNs(String s) {
        boolean inQuotes=false;int depth=0;
        for(int i=0;i<s.length()-1;i++) {
            char c=s.charAt(i);
            if(c=='"'&&(i==0||s.charAt(i-1)!='\\'))inQuotes=!inQuotes;
            else if(!inQuotes) {
                if(c=='('||c=='[')depth++;else if(c==')'||c==']')depth--;
                else if(depth==0&&c==':'&&s.charAt(i+1)==':')return i;
            }
        }
        return -1;
    }
    private static int indexOfTopLevelOpenParen(String s) {
        boolean inQuotes=false;int depthBracket=0;
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c=='"'&&(i==0||s.charAt(i-1)!='\\'))inQuotes=!inQuotes;
            else if(!inQuotes) {
                if(c=='[')depthBracket++;else if(c==']')depthBracket--;
                else if(depthBracket==0&&c=='(')return i;
            }
        }
        return -1;
    }
    private static List<String> splitTopLevelArgs(String s) {
        var result=new ArrayList<String>();int start=0,depth=0;boolean inQuotes=false;
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c=='"'&&(i==0||s.charAt(i-1)!='\\'))inQuotes=!inQuotes;
            else if(!inQuotes) {
                if(c=='('||c=='[')depth++;
                else if(c==')'||c==']')depth--;
                else if(depth==0&&c==','){result.add(s.substring(start,i).trim());start=i+1;}
            }
        }
        result.add(s.substring(start).trim());
        return List.copyOf(result);
    }
    private RuntimeTables.Dataset artifactReferences(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof,CaptureContext context) {
        var frames=steps(definition,cut,run,generation,proof,context);var rows=new ArrayList<RuntimeTables.Row>();
        boolean schemeOnly=definition.node().name().equals("schemeReferences");
        for(var frame:frames.rows())if(frame.quality()==Completeness.COMPLETE)try {
            var bindings=CanonicalJson.object(frame.values().get("bindings"));
            Object rawArtifact=bindings.get("artifact");
            boolean hasArtifact=rawArtifact instanceof Map<?,?> map&&!map.isEmpty();
            var actual=hasArtifact||!schemeOnly?CartagoPhysicalReference.require(rawArtifact,cut):null;
            var values=new LinkedHashMap<>(frame.values());values.put("stepInvocation",frame.identity());
            if(actual!=null) {
                values.put("identity",actual.artifact().id().canonical());
                values.put("domainIdentity",actual.artifact().id().canonical());
                values.put("artifactSemanticId",actual.artifact().values().get("semanticId"));values.put("artifactName",actual.artifact().values().get("name"));
                values.put("javaClass",actual.artifact().values().get("artifactTypeJavaClassName"));values.put("workspaceIdentity",actual.workspace().id().canonical());
            }
            CartagoPhysicalReference referencedScheme=null;
            if(frame.values().get("scheme") instanceof String scheme&&!scheme.isBlank()) {
                var referenced=CartagoPhysicalReference.require(bindings.get("schemeArtifact"),cut);
                var boards=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD&&f.id().canonical().equals(scheme)&&f.completeness()==Completeness.COMPLETE&&f.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY).toList();
                if(boards.size()!=1||!referenced.artifact().id().incarnation().equals(boards.getFirst().values().get("boardArtifactIdentity")))continue;
                var board=boards.getFirst();String family=sourceFor(board,cut);
                if(!"1.0.0".equals(board.values().get("schemeArgumentObservationVersion")))continue;
                if(cut.sourceCompleteness().getOrDefault(family,Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE||!cut.startWatermarks().containsKey(family)||!Objects.equals(cut.startWatermarks().get(family),cut.endWatermarks().get(family)))continue;
                context.qualify(board);
                referencedScheme=referenced;
                values.put("schemeArtifactIdentity",referenced.artifact().id().canonical());values.put("schemeWorkspaceIdentity",referenced.workspace().id().canonical());
                values.put("schemeArtifactName",referenced.artifact().values().get("name"));
                values.put("schemeSpecSemanticId",board.values().get("schemeSpecSemanticId"));
                String creator=currentJasonPlayer(referenced.artifact().values().get("creatorAgentSemanticId"),cut);
                if(creator!=null)values.put("schemeCreatorRuntimeId",creator);
                values.put("schemeArguments",board.values().get("schemeArguments"));values.put("schemeArgumentTerms",board.values().get("schemeArgumentTerms"));
                publicSchemeBindings(values,board,referenced.artifact(),cut,run,context);
            }
            var refTarget=actual!=null?actual:referencedScheme;
            if(refTarget==null)continue;
            var proofs=new LinkedHashMap<>(frame.proofs());proofs.put("physical-identity",refTarget.artifact().id().canonical());
            proofs.put("typed-reference",SemanticJson.hash(Map.of("frame",frame.values(),"artifact",refTarget.artifact().id().canonical(),"workspace",refTarget.workspace().id().canonical())));
            if(schemeOnly) {
                if(!(values.get("scheme") instanceof String scheme)||scheme.isBlank()||!values.containsKey("schemeArtifactIdentity"))continue;
                values.put("identity",scheme);proofs.put("physical-identity",scheme);
                var schemeAliases=new LinkedHashSet<String>();schemeAliases.add(scheme);
                if(values.get("schemeArtifactIdentity") instanceof String artId&&!artId.isBlank())schemeAliases.add(artId);
                if(bindings.get("inputs") instanceof Map<?,?> inputs)for(Object v:inputs.values()) {
                    if(v instanceof String s&&!s.isBlank())schemeAliases.add(s);
                    else if(v instanceof Map<?,?> m&&m.get("value") instanceof String s&&!s.isBlank())schemeAliases.add(s);
                }
                rows.add(new RuntimeTables.Row(scheme,values,Completeness.COMPLETE,proofs,Set.copyOf(schemeAliases)));
            }else rows.add(new RuntimeTables.Row(actual.artifact().id().canonical(),values,Completeness.COMPLETE,proofs,actual.aliases()));
        }catch(IllegalArgumentException|ContractException unavailable){/* A past/missing handle never supplies a current physical world object. */}
        return new RuntimeTables.Dataset(rows,Completeness.PARTIAL);
    }
    private RuntimeTables.Dataset steps(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof,CaptureContext context) {
        var decoder=stepDecoders.get(definition.node().attr("id"));var request=requirements.get(definition.node().attr("requestRef"));var rows=new ArrayList<RuntimeTables.Row>();
        for(var fact:cut.facts())if(fact.values().containsKey(VerificationObservation.KEY)) {
            var observation=context.observation(fact);if(!decoder.accepts(observation))continue;
            var step=decoder.decode(observation,run,generation,cut.modelRevision());
            var current=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.AGENT&&f.id().canonical().equals(step.agent())).toList();
            boolean agentKnown=current.size()==1&&current.getFirst().completeness()==Completeness.COMPLETE&&current.getFirst().projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;
            Completeness quality=step.quality();
            for(String source:List.of("jason",VerificationObservation.SOURCE_ID)) {
                if(cut.sourceCompleteness().getOrDefault(source,Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE)quality=Completeness.UNAVAILABLE;
                if(!cut.startWatermarks().containsKey(source)||!Objects.equals(cut.startWatermarks().get(source),cut.endWatermarks().get(source)))quality=merge(quality,Completeness.PARTIAL);
            }
            if(!agentKnown)quality=merge(quality,Completeness.PARTIAL);
            var values=new LinkedHashMap<String,Object>();values.put("identity",step.invocation());values.put("agent",step.agent());values.put("declaration",step.declaration());values.put("task",step.task());values.put("goalInvocation",step.goalInvocation());values.put("invocation",step.invocation());values.put("phase",step.phase());
            if(quality==Completeness.COMPLETE) {
                values.put("enabled",step.enabled());if(step.succeeded()!=null)values.put("succeeded",step.succeeded());
                values.put("scheme",step.scheme());values.put("artifact",step.artifact());values.put("operation",step.operation());values.put("bindings",step.bindings());
            }else values.put("diagnostic",agentKnown?"STEP_OR_SOURCE_CUT_INCOMPLETE":"CURRENT_AGENT_INCARNATION_UNAVAILABLE");
            rows.add(new RuntimeTables.Row(step.invocation(),values,quality,Map.of("source-cut",proof,"capability:"+request.id(),SemanticJson.hash(Map.of("request",request.evidence(),"frame",observation.data()))),Set.of(step.invocation())));
        }
        return new RuntimeTables.Dataset(rows,Completeness.PARTIAL); // An observed invocation prefix is not a complete Task population.
    }
    private RuntimeTables.Dataset norms(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof) {
        var request=requirements.get(definition.node().attr("requestRef"));var decoder=normDecoders.get(definition.node().attr("id"));
        var rows=new ArrayList<RuntimeTables.Row>();
        // Discovery is selected-board only. Even an empty COMPLETE source flag cannot prove global absence.
        Completeness population=merge(Completeness.PARTIAL,cut.sourceCompleteness().getOrDefault("npl",Completeness.UNAVAILABLE));
        String boardClass=definition.node().optional("boardClass");
        if(!boardClass.isEmpty()&&NplPopulationCut.closed(cut,boardClass))population=Completeness.COMPLETE;
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.NORM_INSTANCE&&fact.id().authority().equals("npl")) {
            if(!boardClass.isEmpty()&&!boardClass.equals(CanonicalJson.object(fact.values().get("boardObservationProof")).get("artifactClass")))continue;
            var data=new LinkedHashMap<String,Object>();data.put("identity",fact.id().canonical());
            var proofs=new LinkedHashMap<String,String>();proofs.put("source-cut",proof);proofs.put("physical-identity",fact.id().canonical());
            Completeness quality=Completeness.UNAVAILABLE;String diagnostic="NPL_ACTIVATION_OR_PHYSICAL_BOARD_UNAVAILABLE";
            if(fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.EVIDENCE_ONLY&&"BOUND_PUBLIC_CREATED_ANNOTATION".equals(fact.values().get("activationIdentityStatus"))) {
                var decoded=decoder.decode(fact.id(),fact.values(),run,generation);
                var artifacts=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().authority().equals("cartago")&&f.id().incarnation().equals(decoded.norm().boardUuid())).toList();
                if(artifacts.size()>1)throw new IllegalArgumentException("NPL_PHYSICAL_BOARD_AMBIGUOUS");
                RuntimeFact artifact=artifacts.size()==1?artifacts.getFirst():null;
                var workspaces=artifact==null?List.<RuntimeFact>of():cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&f.id().authority().equals("cartago")&&Objects.equals(f.values().get("semanticId"),artifact.values().get("workspaceSemanticId"))).toList();
                if(workspaces.size()>1)throw new IllegalArgumentException("NPL_PHYSICAL_WORKSPACE_AMBIGUOUS");
                RuntimeFact workspace=workspaces.size()==1?workspaces.getFirst():null;
                if(faithfulCartago(artifact)&&faithfulCartago(workspace)) {
                    verifyPhysical(artifact);verifyPhysical(workspace);var board=CanonicalJson.object(fact.values().get("boardObservationProof"));
                    if(!Objects.equals(artifact.values().get("name"),board.get("artifactName"))||!Objects.equals(artifact.values().get("artifactTypeJavaClassName"),board.get("artifactClass"))
                            ||!workspace.id().incarnation().equals(board.get("workspaceUuid"))||!Objects.equals(workspace.values().get("fullName"),board.get("workspaceFullName"))
                            ||!artifact.id().scope().equals(board.get("workspaceFullName")))throw new IllegalArgumentException("NPL_PHYSICAL_BOARD_CROSSWALK_MISMATCH");
                    var mark=cut.endWatermarks().get("npl");
                    if(mark==null||decoded.sourceSequence()<0||decoded.sourceSequence()>mark.sequence())throw new IllegalArgumentException("NPL_SOURCE_SEQUENCE_OUTSIDE_CUT");
                    quality=cut.sourceCompleteness().getOrDefault("npl",Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE||cut.sourceCompleteness().getOrDefault("cartago",Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE?Completeness.UNAVAILABLE:Completeness.COMPLETE;
                    if(!Objects.equals(cut.startWatermarks().get("npl"),mark)||!Objects.equals(cut.startWatermarks().get("cartago"),cut.endWatermarks().get("cartago")))quality=merge(quality,Completeness.PARTIAL);
                    if(quality==Completeness.COMPLETE) {
                        var norm=decoded.norm();data.put("boardUuid",norm.boardUuid());data.put("boardArtifact",artifact.id().canonical());data.put("workspace",workspace.id().canonical());data.put("workspaceUuid",workspace.id().incarnation());
                        data.put("activation",norm.activation());data.put("normDefinition",norm.norm());data.put("subject",norm.subject());data.put("substitution",norm.substitution());data.put("modality",norm.modality());data.put("state",norm.state().name());
                        data.put("aim",fact.values().get("aim"));data.put("maintenance",fact.values().get("maintenance"));data.put("history",norm.history());data.put("continuous",decoded.continuous());data.put("timeComplete",decoded.timeComplete());data.put("clockIdentity",decoded.clockIdentity());
                        data.put("sourceSequence",decoded.sourceSequence());data.put("subscription",board.get("sourceSubscriptionNumber"));data.put("raw",fact.values());
                        if(decoded.createdEpochMillis()!=null)data.put("createdEpochMillis",decoded.createdEpochMillis().toString());
                        if(decoded.deadlineEpochMillis()!=null)data.put("deadlineEpochMillis",decoded.deadlineEpochMillis().toString());
                        if(decoded.terminalEpochMillis()!=null)data.put("terminalEpochMillis",decoded.terminalEpochMillis().toString());
                        if(decoded.reportedUnfulfilled()!=null)data.put("reportedUnfulfilled",decoded.reportedUnfulfilled());
                        proofs.put("capability:"+request.id(),SemanticJson.hash(Map.of("request",request.evidence(),"observation",fact.values())));diagnostic="";
                    }
                }
            }
            if(quality!=Completeness.COMPLETE)data.put("diagnostic",diagnostic);
            rows.add(new RuntimeTables.Row(fact.id().canonical(),data,quality,proofs,Set.of(fact.id().canonical())));population=merge(population,quality);
        }
        return new RuntimeTables.Dataset(rows,population);
    }
    private static boolean faithfulCartago(RuntimeFact fact){return fact!=null&&fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
    private RuntimeTables.Dataset operationCuts(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof) {
        var request=requirements.get(definition.node().attr("requestRef"));var decoder=operationDecoders.get(definition.node().attr("id"));
        var rows=new ArrayList<RuntimeTables.Row>();Completeness sourceQuality=cut.sourceCompleteness().getOrDefault("cartago",Completeness.UNAVAILABLE),population=sourceQuality;
        for(var artifact:cut.facts())if(artifact.kind()==RuntimeFactKind.ARTIFACT&&artifact.id().authority().equals("cartago")&&request.parameters().get("class").equals(artifact.values().get("artifactTypeJavaClassName"))) {
            var workspaces=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&f.id().authority().equals("cartago")&&Objects.equals(f.values().get("semanticId"),artifact.values().get("workspaceSemanticId"))).toList();
            Object receipt=null;if(artifact.values().get(CapabilityObservation.KEY) instanceof Map<?,?> receipts)receipt=receipts.get(request.id());
            var decoded=decoder.decode(receipt,artifact,workspaces.size()==1?workspaces.getFirst():null,cut,run,generation);Completeness quality=decoded.quality();
            if(sourceQuality==Completeness.UNAVAILABLE)quality=Completeness.UNAVAILABLE;
            if(!Objects.equals(cut.startWatermarks().get("cartago"),cut.endWatermarks().get("cartago")))quality=merge(quality,Completeness.PARTIAL);
            var values=new LinkedHashMap<String,Object>();values.put("identity",artifact.id().canonical());values.put("incarnation",artifact.id().incarnation());
            values.put("run",run);values.put("generation",generation);values.put("revision",cut.modelRevision());
            var proofs=new LinkedHashMap<String,String>();proofs.put("source-cut",proof);proofs.put("physical-identity",artifact.id().canonical());
            if(quality==Completeness.COMPLETE){values.putAll(decoded.values());proofs.put("capability:"+request.id(),decoded.cutId());}else values.put("diagnostic",decoded.diagnostic());
            rows.add(new RuntimeTables.Row(artifact.id().canonical(),values,quality,proofs,Set.of(artifact.id().canonical())));population=merge(population,quality);
        }
        return new RuntimeTables.Dataset(rows,population);
    }
    private RuntimeTables.Dataset operationReferences(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof) {
        var history=operationCuts(definition,cut,run,generation,proof);var rows=new ArrayList<RuntimeTables.Row>();
        int index=Integer.parseInt(definition.node().attr("argumentIndex"));
        for(var provider:history.rows())if(provider.quality()==Completeness.COMPLETE) {
            for(Object raw:(List<?>)provider.values().get("events"))try {
                var event=CanonicalJson.object(raw);
                // A positive witness needs its actual pair, even when global history is incomplete.
                if(!"SUCCEEDED".equals(event.get("outcome"))||!Boolean.TRUE.equals(event.get("correlated")))continue;
                var after=CanonicalJson.object(CanonicalJson.object(event.get("raw")).get("after"));
                if(!"1.0.0".equals(after.get("argumentObservationVersion"))||!(after.get("typedArgumentValues") instanceof List<?> arguments)||index>=arguments.size())continue;
                var feedback=CanonicalJson.object(arguments.get(index));
                if(!"FEEDBACK".equals(feedback.get("kind"))||!"cartago.OpFeedbackParam".equals(feedback.get("javaType"))||!Boolean.TRUE.equals(feedback.get("available")))continue;
                var actual=CartagoPhysicalReference.require(feedback.get("value"),cut);
                var values=new LinkedHashMap<String,Object>(event);values.put("identity",actual.artifact().id().canonical());
                values.put("artifactSemanticId",actual.artifact().values().get("semanticId"));values.put("artifactName",actual.artifact().values().get("name"));
                values.put("artifactUuid",actual.artifact().id().incarnation());values.put("javaClass",actual.artifact().values().get("artifactTypeJavaClassName"));
                values.put("creator",actual.artifact().values().get("creatorAgentSemanticId"));values.put("workspaceIdentity",actual.workspace().id().canonical());
                values.put("providerIdentity",provider.identity());values.put("argumentIndex",index);
                String player=currentJasonPlayer(event.get("performerId"),cut);if(player!=null)values.put("performerRuntimeId",player);
                var proofs=new LinkedHashMap<>(provider.proofs());proofs.put("physical-identity",actual.artifact().id().canonical());
                proofs.put("operation-reference",SemanticJson.hash(Map.of("event",event,"argument",index,"artifact",actual.artifact().id().canonical())));
                if(rows.size()>=4096)throw new IllegalStateException("RUNTIME_OPERATION_REFERENCE_CAPACITY");
                rows.add(new RuntimeTables.Row(actual.artifact().id().canonical(),values,Completeness.COMPLETE,proofs,actual.aliases()));
            }catch(IllegalArgumentException|ContractException unavailable){/* Unknown/stale feedback cannot create a current world identity. */}
        }
        return new RuntimeTables.Dataset(rows,Completeness.PARTIAL); // Positive receipts never establish a complete world population.
    }
    /** Registry-backed physical CArtAgO membership to current Jason incarnation; a name alone grants no alias. */
    private static String currentJasonPlayer(Object physical,RuntimeSnapshot cut) {
        if(!(physical instanceof String id)||cut.sourceCompleteness().getOrDefault("jason",Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE
                ||!cut.startWatermarks().containsKey("jason")||!Objects.equals(cut.startWatermarks().get("jason"),cut.endWatermarks().get("jason")))return null;
        var memberships=cut.facts().stream().filter(f->faithfulCartago(f)&&f.kind()==RuntimeFactKind.AGENT&&f.id().authority().equals("cartago")&&f.id().dimension().equals("environment")&&f.id().kind().equals("agent")&&(f.id().canonical().equals(id)||id.equals(f.values().get("semanticId")))).toList();
        if(memberships.size()!=1||!(memberships.getFirst().values().get("agentRuntimeSemanticId") instanceof String runtime))return null;
        var players=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.AGENT&&f.id().authority().equals("jason")&&f.id().dimension().equals("agent")&&f.id().kind().equals("runtime-agent")
            &&Objects.equals(f.values().get("name"),memberships.getFirst().values().get("name"))).toList();
        if(players.size()!=1||!players.getFirst().id().canonical().equals(runtime)||players.getFirst().completeness()!=Completeness.COMPLETE||players.getFirst().projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY||!players.getFirst().id().localId().equals(players.getFirst().values().get("name")))return null;
        return runtime;
    }
    private RuntimeTables.Dataset schemeCuts(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof,CaptureContext context) {
        var request=requirements.get(definition.node().attr("requestRef"));var decoder=schemeDecoders.get(definition.node().attr("id"));
        var rows=new ArrayList<RuntimeTables.Row>();Completeness population=cut.sourceCompleteness().getOrDefault("cartago",Completeness.UNAVAILABLE);
        var selected=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.SCHEME_BOARD&&f.id().authority().equals("moise")).toList();
        for(var board:selected) {
            // Static definition selection is the request; display/instance names never identify its incarnation.
            var declared=board.values().get("schemeSpecSemanticId");
            if(!(declared instanceof String value)||value.isBlank()||board.completeness()!=Completeness.COMPLETE||board.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY) {
                rows.add(new RuntimeTables.Row(board.id().canonical(),Map.of("identity",board.id().canonical(),"diagnostic","SOURCE_SCHEME_DEFINITION_UNAVAILABLE"),Completeness.UNAVAILABLE,Map.of("source-cut",proof),Set.of(board.id().canonical())));
                population=merge(population,Completeness.UNAVAILABLE);continue;
            }
            if(moise!=null)context.qualify(board);
            if(!decoder.acceptsDefinition(declared))continue;
            String source=sourceFor(board,cut);Completeness sourceQuality=merge(cut.sourceCompleteness().getOrDefault(source,Completeness.UNAVAILABLE),cut.sourceCompleteness().getOrDefault("cartago",Completeness.UNAVAILABLE));
            var artifacts=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().authority().equals("cartago")&&f.id().incarnation().equals(board.values().get("boardArtifactIdentity"))).toList();
            RuntimeFact artifact=artifacts.size()==1?artifacts.getFirst():null;
            var workspaces=artifact==null?List.<RuntimeFact>of():cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&f.id().authority().equals("cartago")&&Objects.equals(f.values().get("semanticId"),artifact.values().get("workspaceSemanticId"))).toList();
            Object receipt=null;if(artifact!=null&&artifact.values().get(CapabilityObservation.KEY) instanceof Map<?,?> receipts)receipt=receipts.get(request.id());
            var decoded=decoder.decode(receipt,artifact,workspaces.size()==1?workspaces.getFirst():null,board,cut,run,generation);Completeness quality=decoded.quality();
            if(sourceQuality==Completeness.UNAVAILABLE)quality=Completeness.UNAVAILABLE;
            if(cut.startWatermarks().containsKey(source)&&!cut.startWatermarks().get(source).equals(cut.endWatermarks().get(source)))quality=merge(quality,Completeness.PARTIAL);
            var data=new LinkedHashMap<String,Object>();data.put("identity",board.id().canonical());data.put("incarnation",board.id().incarnation());data.put("request",request.id());
            data.put("run",run);data.put("generation",generation);data.put("revision",cut.modelRevision());
            var proofs=new LinkedHashMap<String,String>();proofs.put("source-cut",proof);proofs.put("physical-identity",board.id().canonical());
            if(quality==Completeness.COMPLETE){data.putAll(decoded.values());publicSchemeBindings(data,board,artifact,cut,run,context);proofs.put("capability:"+request.id(),decoded.cutId());}else data.put("diagnostic",decoded.diagnostic());
            // The decoder checked the public MOISE UUID against this exact current
            // CArtAgO board/workspace. Both identifiers denote that same physical board.
            Set<String> aliases=quality==Completeness.COMPLETE?Set.of(board.id().canonical(),artifact.id().canonical()):Set.of(board.id().canonical());
            rows.add(new RuntimeTables.Row(board.id().canonical(),data,quality,proofs,aliases));population=merge(population,merge(sourceQuality,quality));
        }
        if(selected.isEmpty()&&cut.sourceCompleteness().keySet().stream().noneMatch(s->s.startsWith("moise:")))population=Completeness.UNAVAILABLE;
        return withPopulation(definition,rows,population,cut,run,proof);
    }
    private RuntimeTables.Dataset withPopulation(Definition definition,List<RuntimeTables.Row> rows,Completeness population,RuntimeSnapshot cut,String run,String proof) {
        if(definition.node().name().equals("facts")&&definition.authority().equals("jason")&&definition.kind()==RuntimeFactKind.AGENT
                &&population==Completeness.COMPLETE&&JasonAgentInventory.complete(cut,run)) {
            rows.replaceAll(row->{var qualified=new LinkedHashMap<>(row.proofs());qualified.put("jason-agent-inventory",proof);return new RuntimeTables.Row(row.identity(),row.values(),row.quality(),qualified,row.aliases());});
        }
        if(MoisePopulationClosure.POLICY.equals(definition.node().optional("population"))&&moise!=null
                &&rows.stream().allMatch(r->r.quality()==Completeness.COMPLETE)&&MoisePopulationClosure.complete(cut,definition.kind(),run)) {
            population=Completeness.COMPLETE;
            rows.replaceAll(row->{var qualified=new LinkedHashMap<>(row.proofs());qualified.put(MoisePopulationClosure.PROOF,proof);return new RuntimeTables.Row(row.identity(),row.values(),row.quality(),qualified,row.aliases());});
        }
        return new RuntimeTables.Dataset(rows,population);
    }
    /** Public normalized MOISE relations, independent of a case's domain Artifact or step completion. */
    private void publicSchemeBindings(Map<String,Object> data,RuntimeFact board,RuntimeFact artifact,RuntimeSnapshot cut,String run,CaptureContext context) {
        data.putIfAbsent("schemeArtifactIdentity",artifact.id().canonical());
        data.putIfAbsent("schemeWorkspaceIdentity",artifact.id().scope());
        data.putIfAbsent("schemeArtifactName",artifact.values().get("name"));
        data.putIfAbsent("schemeSpecSemanticId",board.values().get("schemeSpecSemanticId"));
        data.putIfAbsent("schemeCreatorRuntimeId",artifact.values().get("creatorRuntimeId"));
        data.putIfAbsent("schemeOwner",board.values().get("owner"));
        data.putIfAbsent("schemeArguments",board.values().getOrDefault("goalArguments",Map.of()));
        data.putIfAbsent("schemeArgumentTerms",board.values().getOrDefault("goalArgumentTerms",Map.of()));
        var observation=org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation.decode(board.id(),board.values());
        data.put("publicGoals",observation.goals().stream().map(goal->{
            var definition=observation.definition().goals().stream().filter(g->g.metadata().semanticId().equals(goal.definition())).findFirst().orElseThrow();
            return Map.of("definition",goal.definition(),"name",definition.goalId(),"state",goal.state().name(),"committed",goal.committed(),"achieved",goal.achieved());
        }).toList());
        data.put("publicCommitments",observation.commitments().stream().map(commitment->{
            var mission=observation.definition().missions().stream().filter(m->m.metadata().semanticId().equals(commitment.mission())).findFirst().orElseThrow();
            return Map.of("mission",mission.missionId(),"definition",commitment.mission(),"player",commitment.player());
        }).toList());
        var groups=new ArrayList<String>();boolean complete=true;
        for(String reference:observation.responsibleContexts()) {
            var selected=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD&&f.id().authority().equals("moise")&&f.id().scope().equals(board.id().scope())
                &&(f.id().canonical().equals(reference)||Objects.equals(f.values().get("semanticId"),reference))).toList();
            if(selected.size()!=1||!faithfulCartago(selected.getFirst())){complete=false;continue;}
            var group=selected.getFirst();context.qualify(group);verifyPhysical(group);
            var declarations=groupDeclarations.stream().filter(g->g.organization().equals(group.id().scope())&&g.name().equals(group.id().localId())).toList();
            if(declarations.size()>1)throw new IllegalArgumentException("SCHEME_RESPONSIBILITY_GROUP_DECLARATION_AMBIGUOUS");
            String expected=declarations.isEmpty()?group.id().canonical():declarations.getFirst().metadata().semanticId();
            if(!expected.equals(group.values().get("semanticId")))throw new IllegalArgumentException("SCHEME_RESPONSIBILITY_GROUP_DECLARATION_ALIAS_MISMATCH");
            if(!declarations.isEmpty()) {
                var definition=org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationFromTree(group.values().get("organisationDefinition"));
                var type=definition.structuralSpecification().groups().stream().filter(g->g.metadata().semanticId().equals(group.values().get("groupSpecSemanticId"))).findFirst().orElseThrow();
                if(!declarations.getFirst().type().equals(type.groupId()))throw new IllegalArgumentException("SCHEME_RESPONSIBILITY_GROUP_DECLARATION_TYPE_MISMATCH");
            }
            var physical=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().authority().equals("cartago")&&f.id().incarnation().equals(group.values().get("boardArtifactIdentity"))).toList();
            if(physical.size()!=1||!faithfulCartago(physical.getFirst())||!physical.getFirst().id().scope().equals(artifact.id().scope())
                    ||!Objects.equals(physical.getFirst().values().get("artifactTypeJavaClassName"),"ora4mas.nopl.GroupBoard")||!Objects.equals(physical.getFirst().values().get("name"),group.id().localId())){complete=false;continue;}
            verifyPhysical(physical.getFirst());String family=sourceFor(group,cut);
            if(cut.sourceCompleteness().getOrDefault(family,Completeness.UNAVAILABLE)==Completeness.UNAVAILABLE||!cut.startWatermarks().containsKey(family)||!Objects.equals(cut.startWatermarks().get(family),cut.endWatermarks().get(family))){complete=false;continue;}
            groups.add(group.id().canonical());
        }
        if(new HashSet<>(groups).size()!=groups.size())throw new IllegalArgumentException("SCHEME_RESPONSIBILITY_DUPLICATE_PHYSICAL_GROUP");
        data.put("responsibilityComplete",complete);if(complete)data.put("responsibleGroups",List.copyOf(groups));
    }
    private RuntimeTables.Dataset clock(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof,CaptureContext context) {
        var request=requirements.get(definition.node().attr("requestRef"));
        var clocks=context.observations().stream().filter(o->o.kind()==VerificationObservation.Kind.CLOCK).toList();
        if(clocks.isEmpty())return new RuntimeTables.Dataset(List.of(),Completeness.UNAVAILABLE);
        if(clocks.size()!=1)throw definition.node().location().error("SOURCE_CLOCK_POPULATION_AMBIGUOUS");
        var value=org.tzi.use.plugins.jacamo.semantics.observer.SourceClock.decode(clocks.getFirst(),run,generation,cut.modelRevision(),(String)request.parameters().get("hash"));
        var data=new LinkedHashMap<String,Object>();data.put("identity",value.clockIdentity());data.put("quality",value.quality().name());data.put("diagnostic",value.diagnostic());
        var proofs=new LinkedHashMap<String,String>();proofs.put("source-cut",proof);
        if(value.quality()==Completeness.COMPLETE) {
            data.put("anchorEpochMillis",Long.toString(value.anchorEpochMillis()));data.put("nowEpochMillis",Long.toString(value.nowEpochMillis()));
            data.put("relativeNow",value.relative(value.nowEpochMillis()).value());data.put("rawProof",value.proof());proofs.put("capability:"+request.id(),SemanticJson.hash(value.proof()));
        }
        var row=new RuntimeTables.Row(value.clockIdentity(),data,value.quality(),proofs,Set.of(value.clockIdentity()));
        return new RuntimeTables.Dataset(List.of(row),merge(value.quality(),cut.sourceCompleteness().getOrDefault(VerificationObservation.SOURCE_ID,Completeness.UNAVAILABLE)));
    }
    private RuntimeTables.Dataset fieldCuts(Definition definition,RuntimeSnapshot cut,String run,long generation,String proof) {
        var request=requirements.get(definition.node().attr("requestRef"));var decoder=fieldDecoders.get(definition.node().attr("id"));
        var rows=new ArrayList<RuntimeTables.Row>();Completeness sourceQuality=cut.sourceCompleteness().getOrDefault("cartago",Completeness.UNAVAILABLE),population=sourceQuality;
        if(!cut.endWatermarks().containsKey("cartago"))return new RuntimeTables.Dataset(List.of(),Completeness.UNAVAILABLE);
        for(var artifact:cut.facts())if(artifact.kind()==RuntimeFactKind.ARTIFACT&&artifact.id().authority().equals("cartago")&&request.parameters().get("class").equals(artifact.values().get("artifactTypeJavaClassName"))) {
            var workspaces=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&f.id().authority().equals("cartago")&&Objects.equals(f.values().get("semanticId"),artifact.values().get("workspaceSemanticId"))).toList();
            Object receipt=null;
            if(artifact.values().get(CapabilityObservation.KEY) instanceof Map<?,?> receipts)receipt=receipts.get(request.id());
            var decoded=decoder.decode(receipt,artifact,workspaces.size()==1?workspaces.getFirst():null,cut,run,generation);
            Completeness quality=decoded.quality();
            if(!cut.startWatermarks().get("cartago").equals(cut.endWatermarks().get("cartago")))quality=merge(quality,Completeness.PARTIAL);
            if(sourceQuality==Completeness.UNAVAILABLE)quality=Completeness.UNAVAILABLE;
            var values=new LinkedHashMap<String,Object>();values.put("identity",artifact.id().canonical());values.put("incarnation",artifact.id().incarnation());
            values.put("artifactSemanticId",artifact.values().get("semanticId"));values.put("request",request.id());
            var proofs=new LinkedHashMap<String,String>();proofs.put("source-cut",proof);proofs.put("physical-identity",artifact.id().canonical());
            if(quality==Completeness.COMPLETE){values.putAll(decoded.values());proofs.put("capability:"+request.id(),decoded.cutId());}
            else values.put("diagnostic",decoded.diagnostic());
            Set<String> aliases=quality==Completeness.COMPLETE?Set.of(artifact.id().canonical(),(String)artifact.values().get("semanticId")):Set.of(artifact.id().canonical());
            rows.add(new RuntimeTables.Row(artifact.id().canonical(),values,quality,proofs,aliases));
            population=merge(population,quality);
        }
        return new RuntimeTables.Dataset(rows,population);
    }
    private static RuntimeTables.Dataset removals(Definition definition,RuntimeSnapshot cut,String run,long generation,RuntimeEvent event,String proof) {
        // A callback-local relation, not an enumeration of source lifetimes or removal history.
        // A snapshot has no current callback, so the relation is known empty in that boundary.
        if(event==null)return new RuntimeTables.Dataset(List.of(),Completeness.COMPLETE);
        if(!run.equals(event.sessionId())||generation!=event.generation()||!cut.modelRevision().equals(event.modelRevision()))throw new IllegalArgumentException("RUNTIME_REMOVAL_EPOCH_MISMATCH");
        String expected=definition.kind()==RuntimeFactKind.ARTIFACT?"DELETE_CARTAGO_ARTIFACT":"DELETE_CARTAGO_AGENT_IDENTITY";
        if(!expected.equals(event.after().get("normalizedEventKind")))return new RuntimeTables.Dataset(List.of(),Completeness.COMPLETE);
        if(event.entityId()==null||!event.entityId().authority().equals(definition.authority())||event.factKind()!=definition.kind()
                ||event.kind()!=RuntimeEventKind.DISPOSED&&event.kind()!=RuntimeEventKind.REMOVED||!event.sourceId().equals("cartago")
                ||!event.watermark().equals(cut.endWatermarks().get(event.sourceId())))throw new IllegalArgumentException("RUNTIME_REMOVAL_RECEIPT_MISMATCH");
        Completeness quality=event.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY?event.completeness():Completeness.UNAVAILABLE;
        String identity=event.entityId().canonical();var values=new LinkedHashMap<String,Object>();
        values.put("identity",identity);values.put("authority",event.entityId().authority());values.put("kind",event.factKind().name());
        values.put("scope",event.entityId().scope());values.put("localId",event.entityId().localId());values.put("incarnation",event.entityId().incarnation());
        values.put("eventId",event.eventId());values.put("source",event.sourceId());values.put("sourceSequence",event.sourceSequence());
        values.put("run",run);values.put("generation",generation);values.put("revision",cut.modelRevision());
        var row=new RuntimeTables.Row(identity,values,quality,Map.of("source-cut",proof,"removed-identity",identity),Set.of(identity));
        return new RuntimeTables.Dataset(List.of(row),quality);
    }
    public Node definition(String id) {return definitions.stream().filter(d->d.node().attr("id").equals(id)).map(Definition::node).findFirst().orElseThrow(()->new IllegalArgumentException("RUNTIME_DATASET_UNRESOLVED:"+id));}
    public String authority(String id){return definitions.stream().filter(d->d.node().attr("id").equals(id)).findFirst().orElseThrow().authority();}
    public RuntimeFactKind kind(String id){return definitions.stream().filter(d->d.node().attr("id").equals(id)).findFirst().orElseThrow().kind();}
    private static Optional<Map<String,Object>> scalar(Map<String,Object> values) {
        if(!(values.get("values") instanceof List<?> raw)||!(values.get("valueTypes") instanceof List<?> types)||types.size()!=1||!(types.getFirst() instanceof String javaType))return Optional.empty();
        String type=switch(javaType) {
            case "java.lang.Integer","java.lang.Short","java.lang.Byte","int","short","byte"->"Integer";
            case "java.lang.Boolean","boolean"->"Boolean";
            case "java.lang.String","java.lang.Character","char"->"String";
            default->"";
        };
        if(type.isEmpty())return Optional.empty();
        try {
            var decoded=org.tzi.use.plugins.jacamo.semantics.csl.ScalarValueCodec.decode(type,raw,types);
            Object value=switch(decoded) {
                case org.tzi.use.uml.ocl.value.IntegerValue integer->integer.value();
                case org.tzi.use.uml.ocl.value.BooleanValue bool->bool.value();
                case org.tzi.use.uml.ocl.value.StringValue text->text.value();
                default->throw new IllegalArgumentException("RUNTIME_PROPERTY_SCALAR_UNSUPPORTED");
            };
            return Optional.of(Map.of("type",type,"javaType",javaType,"value",value));
        }catch(IllegalArgumentException unavailable){return Optional.empty();}
    }
    private static String sourceFor(RuntimeFact fact,RuntimeSnapshot cut) {
        if(fact.id().authority().equals("moise")) {
            String declared="moise:"+fact.id().scope();return cut.sourceCompleteness().containsKey(declared)?declared:"moise:dynamic";
        }
        return fact.id().authority();
    }
    private static boolean sourceFamily(String source,String authority){return authority.equals("moise")?source.startsWith("moise:"):source.equals(authority);}
    private static void verifyPhysical(RuntimeFact fact) {
        if(fact.id().authority().equals("cartago")&&Set.of(RuntimeFactKind.ARTIFACT,RuntimeFactKind.WORKSPACE).contains(fact.kind())
                &&!fact.id().incarnation().equals(fact.values().get("uuid")))throw new IllegalArgumentException("RUNTIME_PHYSICAL_UUID_MISMATCH");
        if(fact.id().authority().equals("moise")&&Set.of(RuntimeFactKind.GROUP_BOARD,RuntimeFactKind.SCHEME_BOARD).contains(fact.kind())) {
            Object uuid=fact.values().get("boardArtifactIdentity");
            if(!(uuid instanceof String id)||id.isBlank()||!fact.id().incarnation().endsWith(":"+id)||!fact.id().scope().equals(fact.values().get("organisationName")))throw new IllegalArgumentException("RUNTIME_BOARD_PHYSICAL_IDENTITY_MISMATCH");
        }
    }
    private static Set<String> fields(String authority,RuntimeFactKind kind) {
        String fields=switch(authority+":"+kind.name()) {
            case "cartago:WORKSPACE"->"normalizedEventKind semanticId fullName name uuid parentSemanticId environmentSemanticId local protocol remotePath address environmentName environmentId environmentVersion defaultInfrastructureLayer workspaceDeclarationSemanticId";
            case "cartago:ARTIFACT"->"normalizedEventKind semanticId name uuid artifactTypeSemanticId artifactTypeJavaClassName artifactTypeOrigin artifactTypeClassLoaderIdentity workspaceSemanticId creatorAgentSemanticId artifactDeclarationSemanticId";
            case "cartago:AGENT"->"normalizedEventKind semanticId globalId localId name role workspaceSemanticId agentDeclarationSemanticId agentRuntimeSemanticId";
            case "cartago:PROPERTY"->"normalizedEventKind semanticId artifactSemanticId propertyId name values valueTypes annotations";
            case "cartago:OPERATION"->"normalizedEventKind semanticId operationDescriptor backingOperation";
            case "moise:GROUP_BOARD"->"normalizedEventKind runtimeIdentity boardArtifactIdentity continuity organisationDefinition organisationSpecSemanticId groupSpecSemanticId organisationName name organisationSemanticId semanticId players";
            case "moise:SCHEME_BOARD"->"normalizedEventKind runtimeIdentity boardArtifactIdentity continuity organisationDefinition organisationSpecSemanticId schemeSpecSemanticId organisationName name organisationSemanticId semanticId responsibleGroupSemanticIds commitments goalObservationVersion schemeArguments schemeArgumentObservationVersion schemeArgumentTerms goalStates";
            case "jason:AGENT"->"normalizedEventKind semanticId name active cycles agentDeclarationSemanticId agentRuntimeSemanticId";
            default->null;
        };
        return fields==null?null:Set.of(fields.split(" "));
    }
    private static Completeness merge(Completeness a,Completeness b){return a.ordinal()>=b.ordinal()?a:b;}
}

package org.tzi.use.plugins.jacamo.semantics.csl;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.tzi.use.api.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.trace.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.binding.UseProjectionBinding;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.SemanticOclCompiler;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;

/** Compiles the explicit CSL/observer profile into the native pipeline's one MModel. */
public final class CslUseBinding implements UseProjectionBinding {
    public static final String PROFILE = "CslMonitorProfile";
    public static final String CLASSIFIER = "CslClassifier";
    public static final Set<String> HELPERS = Set.of("RuntimePlayer","AtomicAgent","OrgContext",
            "RoleOccurrence","DomainEntity","WorkspaceObservation");
    private final MModel model;
    private final CslMonitorConfiguration configuration;
    private CslUseBinding(MModel model, CslMonitorConfiguration configuration) {
        this.model = model; this.configuration = configuration;
    }
    @Override public String profileId() { return configuration.profile().name(); }
    @Override public MModel model() { return model; }
    public CslMonitorConfiguration configuration() { return configuration; }
    public static boolean active(MModel model) { return model.getAnnotation(PROFILE) != null; }
    public static CslMonitorConfiguration configuration(MModel model) {
        if (!active(model)) throw new IllegalArgumentException("CSL_MODEL_PROFILE_REQUIRED");
        return CslMonitorConfiguration.parse(Base64.getDecoder().decode(model.getAnnotationValue(PROFILE,"configuration64")));
    }
    public static String typeName(MModel model,String id){return typeName(configuration(model),id);}
    public static String typeName(CslMonitorConfiguration config, String id) { return org.tzi.use.plugins.jacamo.semantics.observer.FunctionalUseBinding.typeId(id) ? org.tzi.use.plugins.jacamo.semantics.observer.FunctionalUseBinding.typeName(id) : org.tzi.use.plugins.jacamo.semantics.observer.MentalUseBinding.typeId(id) ? org.tzi.use.plugins.jacamo.semantics.observer.MentalUseBinding.typeName(id) : config.schema().classifier(id).symbol(); }

    public static CslUseBinding compile(JacamoSpecificationModel source, CslMonitorConfiguration config,
            CodeGroundedTraceCollector trace) {
        var api = new UseModelApi(NativeUseModelBuilder.modelName(source.project().name()) + "_CSL");
        Map<String, SemanticMetadata> metadata = new HashMap<>();
        Map<String, String> javaTypes = new HashMap<>();
        Map<String, CslSchema.Kind> sourceKinds = new HashMap<>();
        Map<String,List<String>> roleParents=new HashMap<>();
        for (var os : source.snapshot().moiseOrganizations()) {
            os.structuralSpecification().roles().forEach(r -> {
                metadata.put(r.metadata().semanticId(),r.metadata()); sourceKinds.put(r.metadata().semanticId(),CslSchema.Kind.ROLE);
                roleParents.put(r.metadata().semanticId(),r.superRoleSemanticIds());
            });
            os.structuralSpecification().groups().forEach(g -> {
                metadata.put(g.metadata().semanticId(),g.metadata()); sourceKinds.put(g.metadata().semanticId(),CslSchema.Kind.ORG_CONTEXT);
            });
        }
        for (var environment : source.snapshot().cartagoEnvironments()) for (var type : environment.artifactTypes()) {
            String id = type.metadata().semanticId();
            javaTypes.put(id,type.javaClassName()); metadata.put(id,type.metadata()); sourceKinds.put(id,CslSchema.Kind.ENTITY);
            // This existing official class identity is a stable type alias, not a display-name match.
            String stable = DomainProjection.artifactId(type.javaClassName());
            javaTypes.put(stable,type.javaClassName()); metadata.put(stable,type.metadata()); sourceKinds.put(stable,CslSchema.Kind.ENTITY);
        }
        for (var declaration : source.snapshot().artifactDeclarations()) {
            String stable = DomainProjection.artifactId(declaration.javaClass());
            javaTypes.putIfAbsent(stable,declaration.javaClass()); metadata.putIfAbsent(stable,declaration.metadata());
            sourceKinds.putIfAbsent(stable,CslSchema.Kind.ENTITY);
        }
        try {
            api.getModel().addAnnotation(new MElementAnnotation(PROFILE,Map.of(
                    "version",config.version(),"profile",config.profile().name(),
                    "configuration64",Base64.getEncoder().encodeToString(config.bytes()))));
            api.getModel().addAnnotation(new MElementAnnotation("CslAgentDeclarations",Map.of("payload64",DomainProjection.encode(NativeUseModelBuilder.canonicalJson(source.snapshot().agentDeclarations().stream().map(a->Map.of("id",a.metadata().semanticId(),"instances",a.instances())).toList())))));
            for (String helper : HELPERS.stream().sorted().toList()) {
                var cls = api.createClass(helper,Set.of("RuntimePlayer","DomainEntity","RoleOccurrence","OrgContext").contains(helper));
                cls.addAnnotation(new MElementAnnotation(CLASSIFIER,Map.of("kind","OBSERVER_HELPER","sourceId64",DomainProjection.encode("profile:"+helper))));
                DomainProjection.annotate(cls,"observer-helper","profile:"+helper,"");
            }
            api.createGeneralization("AtomicAgent","RuntimePlayer"); api.createGeneralization("OrgContext","RuntimePlayer");
            api.createAssociation("Plays","RuntimePlayer","player","1",0,"RoleOccurrence","enactedRoles","*",0);
            api.createAssociation("InContext","OrgContext","orgContext","1",0,"RoleOccurrence","roles","*",0);
            if(config.profile()==SemanticProjectionProfile.CSL_OBSERVER_V2) {org.tzi.use.plugins.jacamo.semantics.observer.FunctionalUseBinding.install(api);org.tzi.use.plugins.jacamo.semantics.observer.MentalUseBinding.install(api);org.tzi.use.plugins.jacamo.semantics.normative.NormativeUseBinding.install(api); }
            for (var classifier : config.schema().classifiers()) {
                if (HELPERS.contains(classifier.symbol()) || org.tzi.use.plugins.jacamo.semantics.observer.FunctionalUseBinding.TYPES.contains(classifier.symbol()) || org.tzi.use.plugins.jacamo.semantics.observer.MentalUseBinding.TYPES.contains(classifier.symbol()) || classifier.symbol().equals(org.tzi.use.plugins.jacamo.semantics.normative.NormativeUseBinding.TYPE)) throw new IllegalArgumentException("CSL_RESERVED_CLASSIFIER:" + classifier.symbol());
                if (sourceKinds.get(classifier.sourceId()) != classifier.kind())
                    throw new IllegalArgumentException("CSL_SOURCE_KIND_OR_ID_UNRESOLVED:" + classifier.sourceId());
                var cls = api.createClass(classifier.symbol(),classifier.abstractType());
                cls.addAnnotation(new MElementAnnotation(CLASSIFIER,Map.of("kind",classifier.kind().name(),
                        "id64",DomainProjection.encode(classifier.id()),"sourceId64",DomainProjection.encode(classifier.sourceId()))));
                DomainProjection.annotate(cls,classifier.kind()==CslSchema.Kind.ENTITY?"artifact":"csl-"+classifier.kind().name().toLowerCase(Locale.ROOT),
                        classifier.sourceId(),javaTypes.getOrDefault(classifier.sourceId(),""));
                for (var attribute : classifier.attributes()) {
                    var slot = api.createAttribute(cls.name(),attribute.name(),attribute.type());
                    slot.addAnnotation(new MElementAnnotation("CslAttribute",Map.of("id64",DomainProjection.encode(attribute.id()),
                            "sourceName64",DomainProjection.encode(attribute.sourceName()),"optional",Boolean.toString(attribute.optional()))));
                }
                var origin = metadata.get(classifier.sourceId());
                trace.add(new CodeGroundedTraceRecord("CSL-M1-"+classifier.kind(),TracePhase.MODEL_DECLARATION,
                        origin.sourceKind(),origin.sourceJavaFqcn(),origin.semanticId(),"MClass","class:"+cls.name(),
                        origin.evidenceAuthority(),origin.fidelity(),origin.capabilityStatus(),
                        List.of("TARGET_PROFILE="+config.profile(),"SOURCE_TYPE="+classifier.sourceId()),origin.evidence()));
            }
            for (var classifier : config.schema().classifiers()) {
                String parent = classifier.parentId().isBlank() ? switch(classifier.kind()) {
                    case ENTITY -> "DomainEntity"; case ROLE -> "RoleOccurrence"; case ORG_CONTEXT -> "OrgContext";
                } : config.schema().classifier(classifier.parentId()).symbol();
                if(classifier.kind()==CslSchema.Kind.ROLE && !classifier.parentId().isBlank()) {
                    var parents=roleParents.get(classifier.sourceId());
                    if(parents.size()>1)throw new IllegalArgumentException("UNSUPPORTED:CSL_MULTIPLE_SOURCE_ROLE_PARENTS:"+classifier.sourceId());
                    if(!parents.contains(config.schema().classifier(classifier.parentId()).sourceId()))throw new IllegalArgumentException("CSL_SOURCE_ROLE_INHERITANCE_MISMATCH:"+classifier.sourceId());
                }
                api.createGeneralization(classifier.symbol(),parent);
            }
            for (var association : config.schema().associations()) {
                var ends = association.ends(); int[] aggregation = new int[ends.size()]; aggregation[0]=association.aggregationKind();
                api.createAssociation(association.symbol(),
                        ends.stream().map(e->config.schema().classifier(e.typeId()).symbol()).toArray(String[]::new),
                        ends.stream().map(CslSchema.End::roleName).toArray(String[]::new),
                        ends.stream().map(CslSchema.End::multiplicity).toArray(String[]::new),
                        aggregation,new boolean[ends.size()],new String[0][][]);
                api.getModel().getAssociation(association.symbol()).addAnnotation(new MElementAnnotation("CslAssociation",
                        Map.of("id64",DomainProjection.encode(association.id()),"kind","DOMAIN","lifecycle",association.aggregationKind()==2?"UNSUPPORTED_COMPOSITION_DESTRUCTION":"OBSERVED_ONLY")));
            }
            for (String name : List.of("Plays","InContext"))
                api.getModel().getAssociation(name).addAnnotation(new MElementAnnotation("CslAssociation",Map.of("kind","RUNTIME_ENACTMENT")));
            CslStructuralPolicyCompiler.install(api,config.schema());
            for (var invariant : config.schema().invariants()) {
                var inv = api.createInvariant(invariant.name(),config.schema().classifier(invariant.typeId()).symbol(),invariant.body(),false);
                inv.addAnnotation(new MElementAnnotation("CslConstraint",Map.of("id64",DomainProjection.encode(invariant.id()))));
            }
            // Reuse the audited descriptor/reflection/signature compiler. Contracts never synthesize effects.
            for (var environment : source.snapshot().cartagoEnvironments()) for (var descriptor : environment.operations()) {
                var artifact = environment.artifacts().stream().filter(a->a.metadata().semanticId().equals(descriptor.artifactSemanticId())).findFirst().orElseThrow();
                var sourceType = environment.artifactTypes().stream().filter(t->t.metadata().semanticId().equals(artifact.artifactTypeSemanticId())).findFirst().orElseThrow();
                var owners = config.schema().classifiers().stream().filter(c->c.kind()==CslSchema.Kind.ENTITY &&
                        javaTypes.get(c.sourceId()).equals(sourceType.javaClassName())).toList();
                var backing = environment.backingOperations().stream().filter(b->b.operationDescriptorId().equals(descriptor.metadata().semanticId())).toList();
                if (owners.size()==1 && backing.size()==1 && !descriptor.internal() && !descriptor.dynamic() && !backing.getFirst().varArgs()) {
                    try { DomainProjection.operation(api,owners.getFirst().symbol(),descriptor,backing.getFirst()); }
                    catch (IllegalArgumentException unsupported) {
                        api.getModel().addAnnotation(new MElementAnnotation("CslUnsupportedOperation_"+DomainProjection.hash(descriptor.metadata().semanticId()),
                                Map.of("source64",DomainProjection.encode(descriptor.metadata().semanticId()),"reason64",DomainProjection.encode(unsupported.getMessage()))));
                    }
                }
            }
            for (var contract : config.schema().contracts()) {
                var cls = api.getModel().getClass(config.schema().classifier(contract.typeId()).symbol());
                String signature = NativeUseModelBuilder.canonicalJson(contract.signature());
                var matches = cls.allOperations().stream().filter(op->DomainProjection.decode(op.getAnnotationValue("CartagoOperation","signature64")).equals(signature)).toList();
                if(matches.isEmpty())matches=List.of(DomainProjection.declaredOperation(api,cls.name(),contract.signature(),contract.id()));
                if (matches.size()!=1) throw new IllegalArgumentException("CSL_CONTRACT_EXACT_OPERATION_UNRESOLVED:" + contract.id());
                var op = matches.getFirst();
                for (boolean pre : new boolean[]{true,false}) {
                    var condition = SemanticOclCompiler.condition(api,op,(pre?"Pre_":"Post_")+DomainProjection.hash(contract.id()),
                            pre?contract.pre():contract.post(),pre);
                    condition.addAnnotation(new MElementAnnotation("CslConstraint",Map.of("id64",DomainProjection.encode(contract.id()+(pre?":pre":":post")))));
                }
            }
            for(var task:config.jasonTasks()) {
                var program=source.snapshot().jasonPrograms().stream().filter(p->p.metadata().semanticId().equals(task.sourceAgentProgram())).findFirst().orElseThrow(()->new IllegalArgumentException("JASON_TASK_SOURCE_PROGRAM_UNRESOLVED"));
                var approved=new org.tzi.use.plugins.jacamo.semantics.mapping.JasonAbstractionPolicy().admit(program,task,task.contextQuery(),config);
                api.getModel().addAnnotation(new MElementAnnotation("JasonTask_"+DomainProjection.hash(task.sourceBody()),Map.of("binding64",DomainProjection.encode(NativeUseModelBuilder.canonicalJson(task)),"decisions64",DomainProjection.encode(NativeUseModelBuilder.canonicalJson(approved.decisions())),"runtimeCorrespondence","UNSUPPORTED_WITHOUT_MATCHING_JASON_ACTION_AND_CARTAGO_INVOCATION_CAUSATION")));
            }
            for (var requirement : config.requirements().nodes()) {
                var cls = api.getModel().getClass(typeName(config,requirement.binding().selfTypeId()));
                SemanticOclCompiler.predicate(api.getModel(),cls,requirement.id()+":activation",requirement.activation());
                if (!requirement.condition().isBlank()) SemanticOclCompiler.predicate(api.getModel(),cls,requirement.id(),requirement.condition());
            }
            return new CslUseBinding(api.getModel(),config);
        } catch (UseApiException invalid) { throw new IllegalArgumentException("CSL_NATIVE_BINDING_FAILED",invalid); }
    }
}

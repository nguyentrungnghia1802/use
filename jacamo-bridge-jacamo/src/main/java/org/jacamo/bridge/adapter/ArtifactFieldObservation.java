package org.jacamo.bridge.adapter;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import cartago.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;

/** Explicit producer-only read allowlist. Never an arbitrary field/path request.
 * The pinned CArtAgO lock is acquired with tryLock; no commit, signal, wait or
 * beginExtSession/endExtSession is called. No runtime instance is retained. */
public final class ArtifactFieldObservation implements ObservationCapabilityRegistry.Handle {
    public static final String VERSION="1.0.0",PROVIDER="cartago.artifact-field.v1";
    private final ObservationCapabilityRequest request;
    private final String selectedClass,selectedField,selectedType;
    private final Map<String,String> propertyTypes;
    private static final String CARTAGO_ARTIFACT_HASH="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    private record Reference(String hash,String resource,String provider) { }
    private record Access(Field candidate,Field lock,Field identity) { }
    private final CartagoEnvironment environment;
    private final Predicate<Field> accessPolicy;
    private final Reference reference;
    private final String unavailable;
    private volatile boolean closed;

    public ArtifactFieldObservation(CartagoEnvironment environment,ModelSnapshot source,ObservationCapabilityRequest request) {this(environment,source,request,Field::trySetAccessible);}
    public ArtifactFieldObservation(CartagoEnvironment environment,ModelSnapshot source,ObservationCapabilityRequest request,Predicate<Field> accessPolicy) {
        this(environment,source,request,accessPolicy,null);
    }
    public ArtifactFieldObservation(CartagoEnvironment environment,ModelSnapshot source,ObservationCapabilityRequest request,java.nio.file.Path projectRoot) {
        this(environment,source,request,Field::trySetAccessible,projectRoot);
    }
    public ArtifactFieldObservation(CartagoEnvironment environment,ModelSnapshot source,ObservationCapabilityRequest request,Predicate<Field> accessPolicy,java.nio.file.Path projectRoot) {
        this.environment=Objects.requireNonNull(environment);this.accessPolicy=Objects.requireNonNull(accessPolicy);this.request=request;
        if(!request.provider().equals(PROVIDER)||!Set.of("class","field","type","hash","properties","sourceRef","sourceHash","sourcePath").containsAll(request.parameters().keySet()))throw new IllegalArgumentException("ARTIFACT_FIELD_REQUEST_UNSUPPORTED");
        selectedClass=required(request,"class");selectedField=required(request,"field");selectedType=required(request,"type");
        if(!selectedField.matches("[A-Za-z_][A-Za-z0-9_]*")||!Set.of("java.lang.String","int","long","boolean","double","java.lang.Integer","java.lang.Long","java.lang.Boolean","java.lang.Double").contains(selectedType))throw new IllegalArgumentException("ARTIFACT_FIELD_TYPE_OR_NAME_UNSUPPORTED");
        var properties=new TreeMap<String,String>();
        var declared=request.parameters().getOrDefault("properties",Map.of());
        for(var entry:CanonicalJson.object(declared).entrySet()) {
            if(!entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]*")||!(entry.getValue() instanceof String type)||!Set.of("STRING","BOOLEAN","JAVA_INTEGER","IEEE754_BINARY64","IEEE754_BINARY32","JASON_ATOM").contains(type))throw new IllegalArgumentException("ARTIFACT_PROPERTY_TYPE_UNSUPPORTED");
            properties.put(entry.getKey(),type);
        }
        if(properties.size()>64)throw new IllegalArgumentException("ARTIFACT_PROPERTY_CAPACITY");propertyTypes=Map.copyOf(properties);
        Reference selected=null;String failure="";
        try {
            ProviderSourcePin.require(request,projectRoot);
            var references=new LinkedHashSet<Reference>();
            for(var program:source.semanticContract().jasonPrograms())for(var action:program.actions())if(action.functor().equals("makeArtifact")&&action.arity()==4)
                for(var evidence:action.metadata().evidence())if(evidence.diagnostics().contains("SOURCE_ACTION_CARTAGO_ARTIFACT_APPLICATION_CLASS")
                        &&evidence.diagnostics().contains("CLASS_REFERENCE:makeArtifact/4:1:"+selectedClass)&&evidence.sourceJavaFqcn().equals(selectedClass)
                        &&evidence.sourceSemanticId().equals(action.metadata().semanticId())&&evidence.authority()==EvidenceAuthority.OFFICIAL_CARTAGO_API
                        &&evidence.fidelity()==Fidelity.EXACT&&evidence.capabilityStatus()==CapabilityStatus.COMPLETE) {
                    var providers=evidence.diagnostics().stream().filter(s->s.startsWith("PROVIDER:")).toList();
                    if(providers.size()!=1)throw new IllegalArgumentException("PROVIDER_PROOF_UNAVAILABLE");
                    references.add(new Reference(evidence.sourceDigest(),evidence.sourceUri(),providers.getFirst().substring(9)));
                }
            for(var declaration:source.semanticContract().artifactDeclarations())if(declaration.javaClass().equals(selectedClass))
                for(var evidence:declaration.metadata().evidence())if(evidence.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS")
                        &&evidence.sourceJavaFqcn().equals(selectedClass)&&evidence.sourceSemanticId().equals(declaration.metadata().semanticId())
                        &&evidence.authority()==EvidenceAuthority.OFFICIAL_CARTAGO_API&&evidence.fidelity()==Fidelity.EXACT&&evidence.capabilityStatus()==CapabilityStatus.COMPLETE) {
                    var providers=evidence.diagnostics().stream().filter(s->s.startsWith("PROVIDER:")).toList();
                    if(providers.size()!=1)throw new IllegalArgumentException("PROVIDER_PROOF_UNAVAILABLE");
                    references.add(new Reference(evidence.sourceDigest(),evidence.sourceUri(),providers.getFirst().substring(9)));
                }
            if(references.size()!=1)throw new IllegalArgumentException("EXACT_APPROVED_CLASS_REFERENCE_REQUIRED");selected=references.iterator().next();
            if(request.parameters().containsKey("hash")&&!selected.hash().equals(required(request,"hash")))throw new IllegalArgumentException("REQUESTED_BINARY_HASH_MISMATCH");
            Class<?> type=Class.forName(selectedClass,false,Thread.currentThread().getContextClassLoader());verifyType(type,selected);members(type);
        }catch(Exception|LinkageError error){failure=error.getClass().getSimpleName()+":"+error.getMessage();}
        reference=selected;unavailable=failure;
    }
    public boolean available(){return !closed&&reference!=null&&unavailable.isEmpty();}
    public String diagnostic(){return unavailable;}
    private void verifyType(Class<?> type,Reference expected)throws Exception {
        var framework=DeclaredArtifactTypeProof.readActualBinary(Artifact.class);
        if(framework==null||!CARTAGO_ARTIFACT_HASH.equals(AdapterEvidence.digest(framework.bytes())))throw new IllegalArgumentException("CARTAGO_LOCK_IMPLEMENTATION_UNSUPPORTED");
        if(!type.getName().equals(selectedClass)||!Artifact.class.isAssignableFrom(type))throw new IllegalArgumentException("ACTUAL_CLASS_NOT_ALLOWLISTED");
        var bytes=DeclaredArtifactTypeProof.readActualBinary(type);
        if(bytes==null||!expected.hash().equals(AdapterEvidence.digest(bytes.bytes()))||!expected.resource().equals(bytes.resource().toExternalForm())
                ||!expected.provider().equals(type.getProtectionDomain().getCodeSource().getLocation().toExternalForm()))throw new IllegalArgumentException("ACTUAL_PROVIDER_HASH_OR_RESOURCE_MISMATCH");
    }
    private Access members(Class<?> type)throws Exception {
        Field candidate=type.getDeclaredField(selectedField),lock=Artifact.class.getDeclaredField("lock"),identity=Artifact.class.getDeclaredField("id");
        if(!candidate.getType().getName().equals(selectedType)||Modifier.isStatic(candidate.getModifiers())||candidate.getDeclaringClass()!=type
                ||lock.getType()!=ReentrantLock.class||identity.getType()!=ArtifactId.class)throw new IllegalArgumentException("ALLOWLIST_MEMBER_TYPE_MISMATCH");
        for(Field field:List.of(candidate,lock,identity))if(!accessPolicy.test(field))throw new IllegalAccessException("FIXED_MEMBER_ACCESS_DENIED:"+field.getDeclaringClass().getName()+"."+field.getName());
        return new Access(candidate,lock,identity);
    }
    public Map<String,Object> capture(ArtifactId identity,String origin,String invocation) {
        if(!identity.getArtifactType().equals(selectedClass))return Map.of();
        if(!available())return incomplete(identity,closed?"PROBE_CLOSED":unavailable);
        try {
            var workspace=environment.resolveWSP(identity.getWorkspaceId().getFullName()).getWorkspace();
            if(workspace==null||!workspace.getId().equals(identity.getWorkspaceId()))throw new IllegalStateException("WORKSPACE_REPLACED");
            var descriptor=workspace.getArtifactDescriptor(identity.getName());if(descriptor==null)throw new IllegalStateException("ARTIFACT_UNAVAILABLE");
            var actual=descriptor.getArtifact();verifyType(actual.getClass(),reference);var members=members(actual.getClass());
            if(!identity.equals(members.identity().get(actual)))throw new IllegalStateException("ARTIFACT_REPLACED");
            var lock=(ReentrantLock)members.lock().get(actual);
            boolean alreadyHeld=lock.isHeldByCurrentThread();
            // A snapshot may hold captureLock while an operation logger needs the
            // source monitor. Fail immediately instead of introducing a lock cycle.
            if(!lock.tryLock())throw new IllegalStateException("ARTIFACT_BUSY_COHERENT_CUT_UNAVAILABLE");
            try {
                if(!identity.equals(members.identity().get(actual))||workspace.getArtifactDescriptor(identity.getName())!=descriptor)throw new IllegalStateException("ARTIFACT_REPLACED");
                Object candidate=members.candidate().get(actual);if(candidate==null)throw new IllegalStateException("CANDIDATE_VALUE_UNAVAILABLE");
                var properties=new LinkedHashMap<String,Object>();
                for(var property:descriptor.getAdapter().readProperties()) {
                    if(property.getValues().length!=1||properties.putIfAbsent(property.getName(),value(property.getValue()))!=null)throw new IllegalStateException("OBSERVABLE_ARITY_OR_DUPLICATE");
                }
                if(!properties.keySet().equals(propertyTypes.keySet()))throw new IllegalStateException("OBSERVABLE_INTERFACE_CHANGED");
                for(var entry:propertyTypes.entrySet()) {
                    var observed=CanonicalJson.object(properties.get(entry.getKey()));
                    boolean matches=switch(entry.getValue()) {
                        case "STRING","BOOLEAN"->entry.getValue().equals(observed.get("kind"));
                        case "JASON_ATOM"->observed.containsKey("atomName");
                        default->entry.getValue().equals(observed.get("encoding"));
                    };
                    if(!matches)throw new IllegalStateException("OBSERVABLE_TYPE_UNSUPPORTED:"+entry.getKey());
                }
                if(workspace.getArtifactDescriptor(identity.getName())!=descriptor||!identity.equals(members.identity().get(actual)))throw new IllegalStateException("ARTIFACT_REPLACED");
                String semanticId=OfficialCartagoAdapter.artifactId(environment.getId().toString(),identity);
                var result=new LinkedHashMap<String,Object>();result.put("version",VERSION);result.put("status","COMPLETE");result.put("coherence","LOCKED_ARTIFACT");
                result.put("artifactIdentity",semanticId);result.put("classHash",reference.hash());result.put("fieldCutId",AdapterEvidence.digest(CanonicalJson.encode(Map.of("artifact",semanticId,"properties",properties,"candidate",candidate,"origin",origin,"invocation",invocation))));
                result.put("properties",properties);result.put("fields",Map.of(selectedField,value(candidate)));
                var proof=new LinkedHashMap<String,Object>();proof.put("provider",reference.provider());proof.put("resource",reference.resource());
                proof.put("artifactUuid",identity.getId().toString());proof.put("workspaceUuid",identity.getWorkspaceId().getUUID().toString());
                proof.put("field",selectedClass+"."+selectedField+":"+selectedType);proof.put("lockPolicy","CARTAGO31_PRIVATE_REENTRANT_TRYLOCK_READ_V1");
                proof.put("frameworkArtifactHash",CARTAGO_ARTIFACT_HASH);proof.put("origin",origin);proof.put("invocation",invocation);
                proof.put("accessOutcome","FIXED_MEMBERS_TRY_SET_ACCESSIBLE_APPROVED");proof.put("lockAlreadyHeld",alreadyHeld);result.put("probeEvidence",proof);
                return JasonStructuredTerms.immutable(result);
            }finally{lock.unlock();}
        }catch(Exception|LinkageError error){return incomplete(identity,error.getClass().getSimpleName()+":"+error.getMessage());}
    }
    private Map<String,Object> value(Object actual) {
        var value=new LinkedHashMap<>(CartagoArgumentObservation.value(actual,environment.getId().toString()));
        if("UNSUPPORTED".equals(value.get("kind"))||"NULL".equals(value.get("kind")))throw new IllegalArgumentException("SOURCE_VALUE_UNAVAILABLE:"+value.get("diagnostic"));
        if(actual!=null&&actual.getClass()==jason.asSyntax.Atom.class) {
            value.put("atomName",((jason.asSyntax.Atom)actual).getFunctor());value.put("atomNameAuthority","JASON_ATOM_GET_FUNCTOR");
        }
        return JasonStructuredTerms.immutable(value);
    }
    private Map<String,Object> incomplete(ArtifactId identity,String reason){return Map.of("version",VERSION,"status","INCOMPLETE","coherence","UNAVAILABLE","artifactUuid",identity.getId().toString(),"diagnostic",reason);}
    private static String required(ObservationCapabilityRequest request,String key) {
        var value=request.parameters().get(key);if(!(value instanceof String text)||text.isBlank())throw new IllegalArgumentException("ARTIFACT_FIELD_PARAMETER_REQUIRED:"+key);return text;
    }
    @Override public Capability capability(){return new Capability(request.id(),available()?CapabilityStatus.COMPLETE:CapabilityStatus.UNAVAILABLE,List.of(CARTAGO_ARTIFACT_HASH),available()?"Verified request member; each capture retains coherent-cut quality":closed?"PROVIDER_CLOSED":unavailable);}
    @Override public void close(){closed=true;}
}

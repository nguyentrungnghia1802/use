package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.math.BigInteger;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SourceArtifactTypeReferences;

/** Consumer-side pinned field-cut proof. No producer classes, reflection or field access in USE. */
public final class ArtifactFieldCutDecoder {
    private static final String PROVIDER="cartago.artifact-field.v1",FRAMEWORK_HASH="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    private record Proof(String hash,String resource,String provider) { }
    public record Result(Completeness quality,Map<String,Object> values,String cutId,String diagnostic) {
        public Result {values=SemanticJson.object(values);Objects.requireNonNull(quality);}
    }
    private final ObservationCapabilityRequest request;
    private final String type,field,javaType;
    private final Map<String,Object> properties;
    private final Proof proof;
    public ArtifactFieldCutDecoder(ModelSnapshot source,ObservationCapabilityRequest request) {
        this.request=request;
        if(!request.provider().equals(PROVIDER))throw new IllegalArgumentException("FIELD_CUT_PROVIDER_UNSUPPORTED");
        type=text(request.parameters(),"class");field=text(request.parameters(),"field");javaType=text(request.parameters(),"type");
        properties=CanonicalJson.object(request.parameters().getOrDefault("properties",Map.of()));
        if(!field.matches("[A-Za-z_][A-Za-z0-9_]*")||!Set.of("java.lang.String","int","long","boolean","double","java.lang.Integer","java.lang.Long","java.lang.Boolean","java.lang.Double").contains(javaType))throw new IllegalArgumentException("FIELD_CUT_REQUEST_UNSUPPORTED");
        for(var value:properties.values())if(!(value instanceof String tag)||!Set.of("STRING","BOOLEAN","JAVA_INTEGER","IEEE754_BINARY64","IEEE754_BINARY32","JASON_ATOM").contains(tag))throw new IllegalArgumentException("FIELD_CUT_PROPERTY_TYPE_UNSUPPORTED");
        var proofs=new LinkedHashSet<Proof>();
        for(var ref:SourceArtifactTypeReferences.fromActions(source))if(ref.javaClass().equals(type))proofs.add(proof(ref.classProof()));
        for(var declaration:source.semanticContract().artifactDeclarations())if(declaration.javaClass().equals(type))for(var evidence:declaration.metadata().evidence())
            if(evidence.sourceSemanticId().equals(declaration.metadata().semanticId())&&evidence.sourceJavaFqcn().equals(type)
                    &&evidence.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS")&&evidence.authority()==EvidenceAuthority.OFFICIAL_CARTAGO_API
                    &&evidence.fidelity()==Fidelity.EXACT&&evidence.capabilityStatus()==CapabilityStatus.COMPLETE)proofs.add(proof(evidence));
        proof=proofs.size()==1?proofs.iterator().next():null;
    }
    private static Proof proof(SourceEvidence evidence) {
        var providers=evidence.diagnostics().stream().filter(v->v.startsWith("PROVIDER:")).toList();
        if(providers.size()!=1||!evidence.sourceDigest().matches("[0-9a-f]{64}")||!(evidence.sourceUri().startsWith("file:")||evidence.sourceUri().startsWith("jar:file:")))throw invalid("STATIC_PROVIDER_PROOF_REQUIRED");
        return new Proof(evidence.sourceDigest(),evidence.sourceUri(),providers.getFirst().substring(9));
    }
    public Result decode(Object raw,RuntimeFact artifact,RuntimeFact workspace,RuntimeSnapshot cut,String run,long generation) {
        try {
            if(proof==null)throw invalid("UNSUPPORTED:EXACT_SOURCE_CLASS_PROOF_UNAVAILABLE");
            if(request.parameters().containsKey("hash")&&!proof.hash().equals(request.parameters().get("hash")))throw invalid("REQUESTED_CLASS_HASH_MISMATCH");
            var receipt=CapabilityObservation.require(raw,request);var scope=receipt.scope();
            if(receipt.quality()!=Completeness.COMPLETE)throw invalid("INCOMPLETE:"+receipt.diagnostic());
            if(!run.equals(scope.run())||generation!=scope.generation()||!cut.modelRevision().equals(scope.revision())||!scope.sourceId().equals("cartago")
                    ||!new SourceWatermark("cartago",scope.sourceSequence()).equals(cut.endWatermarks().get("cartago")))throw invalid("CAPABILITY_CUT_SCOPE_MISMATCH");
            if(!faithful(artifact,RuntimeFactKind.ARTIFACT)||!faithful(workspace,RuntimeFactKind.WORKSPACE)||!cut.facts().contains(artifact)||!cut.facts().contains(workspace)
                    ||!type.equals(artifact.values().get("artifactTypeJavaClassName"))||!Objects.equals(artifact.values().get("workspaceSemanticId"),workspace.values().get("semanticId"))
                    ||!artifact.id().incarnation().equals(artifact.values().get("uuid"))||!workspace.id().incarnation().equals(workspace.values().get("uuid")))throw invalid("CAPABILITY_ACTUAL_IDENTITY_UNAVAILABLE");
            // OfficialCartagoAdapter.artifactId is environment + full workspace path + UUID.
            // The human artifact name is not an identity component and cannot supply an alias.
            CartagoPhysicalReference.aliases(artifact,workspace);
            var data=receipt.data();
            if(!data.keySet().equals(Set.of("version","status","coherence","artifactIdentity","classHash","fieldCutId","properties","fields","probeEvidence"))
                    ||!"1.0.0".equals(data.get("version"))||!"COMPLETE".equals(data.get("status"))||!"LOCKED_ARTIFACT".equals(data.get("coherence"))
                    ||!proof.hash().equals(data.get("classHash"))||!(artifact.values().get("semanticId") instanceof String owner)||!owner.equals(data.get("artifactIdentity")))throw invalid("FIELD_CUT_CLASS_OR_OWNER_MISMATCH");
            var evidence=CanonicalJson.object(data.get("probeEvidence"));
            if(!proof.provider().equals(evidence.get("provider"))||!proof.resource().equals(evidence.get("resource"))||!artifact.id().incarnation().equals(evidence.get("artifactUuid"))
                    ||!workspace.id().incarnation().equals(evidence.get("workspaceUuid"))||!(type+"."+field+":"+javaType).equals(evidence.get("field"))
                    ||!FRAMEWORK_HASH.equals(evidence.get("frameworkArtifactHash"))||!"CARTAGO31_PRIVATE_REENTRANT_TRYLOCK_READ_V1".equals(evidence.get("lockPolicy"))
                    ||!"FIXED_MEMBERS_TRY_SET_ACCESSIBLE_APPROVED".equals(evidence.get("accessOutcome")))throw invalid("FIELD_CUT_PROVIDER_OR_ACCESS_MISMATCH");
            var rawProperties=CanonicalJson.object(data.get("properties"));var rawFields=CanonicalJson.object(data.get("fields"));
            if(!rawProperties.keySet().equals(properties.keySet())||!rawFields.keySet().equals(Set.of(field)))throw invalid("FIELD_CUT_INTERFACE_MISMATCH");
            var observedField=CanonicalJson.object(rawFields.get(field));String boxed=switch(javaType){case "int"->"java.lang.Integer";case "long"->"java.lang.Long";case "boolean"->"java.lang.Boolean";case "double"->"java.lang.Double";default->javaType;};
            if(!boxed.equals(observedField.get("javaType")))throw invalid("FIELD_CUT_MEMBER_TYPE_MISMATCH");
            Object candidate=scalar(observedField);
            if(candidate instanceof Double number&&number==0.0d)throw invalid("UNSUPPORTED:V1_FIELD_HASH_DOES_NOT_BIND_SIGNED_ZERO");
            String expected=SemanticJson.hash(Map.of("artifact",data.get("artifactIdentity"),"properties",rawProperties,"candidate",candidate,"origin",text(evidence,"origin"),"invocation",text(evidence,"invocation")));
            if(!expected.equals(data.get("fieldCutId")))throw invalid("FIELD_CUT_HASH_MISMATCH");
            var decoded=new LinkedHashMap<String,Object>();
            for(var property:properties.entrySet()) {
                var value=CanonicalJson.object(rawProperties.get(property.getKey()));String tag=(String)property.getValue();
                boolean match=switch(tag) {
                    case "STRING","BOOLEAN"->tag.equals(value.get("kind"));
                    case "JAVA_INTEGER","IEEE754_BINARY64","IEEE754_BINARY32"->"NUMBER".equals(value.get("kind"))&&tag.equals(value.get("encoding"));
                    case "JASON_ATOM"->"JASON_TERM".equals(value.get("kind"));
                    default->false;
                };
                if(!match)throw invalid("FIELD_CUT_PROPERTY_TYPE_MISMATCH:"+property.getKey());
                Object converted;
                if(tag.equals("JASON_ATOM")){RuntimeValueConverters.atomName(value);converted=value;}
                else {Object checked=scalar(value);converted=Set.of("IEEE754_BINARY64","IEEE754_BINARY32").contains(tag)?value:checked;}
                decoded.put(property.getKey(),converted);
            }
            return new Result(Completeness.COMPLETE,Map.of("properties",decoded,"fields",Map.of(field,candidate),"rawProperties",rawProperties,"rawFields",rawFields),expected,"");
        }catch(IllegalArgumentException|ContractException|RuntimeValueConverters.Unavailable unavailable) {
            return new Result(Completeness.UNAVAILABLE,Map.of(),"",unavailable.getMessage());
        }
    }
    private static boolean faithful(RuntimeFact fact,RuntimeFactKind kind){return fact!=null&&fact.kind()==kind&&fact.id().authority().equals("cartago")&&fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
    private static Object scalar(Map<String,Object> value) {
        String kind=text(value,"kind"),type=text(value,"javaType");Object raw=value.get("value");
        if(kind.equals("STRING")&&type.equals("java.lang.String")&&raw instanceof String)return raw;
        if(kind.equals("BOOLEAN")&&type.equals("java.lang.Boolean")&&raw instanceof Boolean)return raw;
        if(kind.equals("NUMBER")&&"JAVA_INTEGER".equals(value.get("encoding"))&&raw instanceof String text) {
            var range=switch(type){case "java.lang.Byte"->List.of(BigInteger.valueOf(Byte.MIN_VALUE),BigInteger.valueOf(Byte.MAX_VALUE));case "java.lang.Short"->List.of(BigInteger.valueOf(Short.MIN_VALUE),BigInteger.valueOf(Short.MAX_VALUE));case "java.lang.Integer"->List.of(BigInteger.valueOf(Integer.MIN_VALUE),BigInteger.valueOf(Integer.MAX_VALUE));case "java.lang.Long"->List.of(BigInteger.valueOf(Long.MIN_VALUE),BigInteger.valueOf(Long.MAX_VALUE));default->throw invalid("FIELD_CUT_INTEGER_TYPE_UNSUPPORTED");};
            var number=new BigInteger(text);if(!number.toString().equals(text)||number.compareTo(range.getFirst())<0||number.compareTo(range.getLast())>0)throw invalid("FIELD_CUT_INTEGER_ENCODING_MISMATCH");return number;
        }
        if(kind.equals("NUMBER")&&type.equals("java.lang.Double"))return RuntimeValueConverters.binary64(value);
        if(kind.equals("NUMBER")&&type.equals("java.lang.Float")&&"IEEE754_BINARY32".equals(value.get("encoding"))&&value.get("rawBits") instanceof String bits&&bits.matches("[0-9a-f]{8}")&&raw instanceof String text) {
            float number=Float.intBitsToFloat(Integer.parseUnsignedInt(bits,16));if(!Float.isFinite(number)||!Float.toString(number).equals(text))throw invalid("FIELD_CUT_BINARY32_MISMATCH");return number;
        }
        throw invalid("FIELD_CUT_TYPED_SCALAR_UNAVAILABLE");
    }
    private static String text(Map<String,Object> data,String field){if(!(data.get(field) instanceof String value))throw invalid("FIELD_CUT_TEXT_REQUIRED:"+field);return value;}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}

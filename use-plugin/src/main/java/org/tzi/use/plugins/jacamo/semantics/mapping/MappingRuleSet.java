package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.use75.OclContractSpec;

/** Authored input for a canonical program, distinct from legacy CslMonitorConfiguration v1. */
public record MappingRuleSet(String version,String id,String profileId,String profileVersion,String expectedSourceHash,
        String cslSource,String iStarSource,List<SemanticBindingIndex.Binding> bindings,List<Contract> contracts,
        List<UnsupportedMappingDecision> decisions,Map<String,String> options) {
    public static final String PROPERTY="use.jacamo.mapping.rules";
    public static MappingRuleSet configured() {
        String path=System.getProperty(PROPERTY,"").trim();if(path.isBlank())throw new IllegalArgumentException("CANONICAL_MAPPING_RULESET_FILE_REQUIRED:"+PROPERTY);
        return read(java.nio.file.Path.of(path));
    }
    public static MappingRuleSet read(java.nio.file.Path path) {
        try{if(!java.nio.file.Files.isRegularFile(path)||java.nio.file.Files.size(path)>CanonicalJson.DEFAULT_MAX_BYTES)throw new IllegalArgumentException("CANONICAL_MAPPING_RULESET_FILE_INVALID");return parse(java.nio.file.Files.readAllBytes(path));}
        catch(java.io.IOException error){throw new IllegalArgumentException("CANONICAL_MAPPING_RULESET_FILE_UNREADABLE",error);}
    }
    /** Declaration remains unbound until MModel exists; Phase12 resolves it to neutral OclContractSpec. */
    public record Contract(String id,OclContractSpec.Kind kind,String contextDeclaration,String targetElementId,String body,
            List<Object> operationSignature,Map<String,String> variableTypes,String origin) {
        public Contract {
            Objects.requireNonNull(id);Objects.requireNonNull(kind);Objects.requireNonNull(contextDeclaration);Objects.requireNonNull(targetElementId);Objects.requireNonNull(body);Objects.requireNonNull(origin);
            if(id.isBlank()||contextDeclaration.isBlank()||body.isBlank()||origin.isBlank())throw new IllegalArgumentException("AUTHORED_CONTRACT_REQUIRED");
            operationSignature=List.copyOf((List<?>)SemanticJson.freeze(operationSignature));variableTypes=Collections.unmodifiableMap(new TreeMap<>(variableTypes));
        }
    }
    public MappingRuleSet {
        if(!"2.0".equals(version))throw new IllegalArgumentException("CANONICAL_MAPPING_RULESET_VERSION_UNSUPPORTED");
        Objects.requireNonNull(id);Objects.requireNonNull(profileId);Objects.requireNonNull(profileVersion);Objects.requireNonNull(expectedSourceHash);Objects.requireNonNull(cslSource);Objects.requireNonNull(iStarSource);
        if(id.isBlank()||cslSource.isBlank()||iStarSource.isBlank())throw new IllegalArgumentException("AUTHORED_PROGRAM_REQUIRED");
        if(profileId.isBlank()||profileVersion.isBlank()||!expectedSourceHash.isEmpty()&&!expectedSourceHash.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("MAPPING_PROFILE_OR_SOURCE_HASH_INVALID");
        bindings=List.copyOf(bindings);contracts=List.copyOf(contracts);decisions=List.copyOf(decisions);options=Collections.unmodifiableMap(new TreeMap<>(options));
        if(contracts.stream().map(Contract::id).distinct().count()!=contracts.size())throw new IllegalArgumentException("CONTRACT_ID_AMBIGUOUS");
        new SemanticBindingIndex(bindings);
    }
    public String hash(){return SemanticJson.hash(SemanticJson.tree(this));}
    public byte[] bytes(){return CanonicalJson.encode(SemanticJson.tree(this));}
    public static MappingRuleSet parse(byte[] bytes) {
        try{CanonicalJson.decode(bytes);return new com.fasterxml.jackson.databind.ObjectMapper().readValue(bytes,MappingRuleSet.class);}
        catch(java.io.IOException|org.jacamo.bridge.contract.ContractException invalid){throw new IllegalArgumentException("CANONICAL_MAPPING_RULESET_INVALID",invalid);}
    }
}

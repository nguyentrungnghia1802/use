package org.tzi.use.plugins.jacamo.semantics.profile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.csl.CslSchema;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;

/** Explicit M1/domain/requirement binding; no runtime UUID or world predicate is hard-coded in production. */
public record CslMonitorConfiguration(String version, SemanticProjectionProfile profile,
        CslSchema schema, RequirementModel requirements, List<UnsupportedFeature> decisions,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) List<org.tzi.use.plugins.jacamo.semantics.istar.TaskBinding> jasonTasks) {
    public CslMonitorConfiguration(String version,SemanticProjectionProfile profile,CslSchema schema,RequirementModel requirements,List<UnsupportedFeature> decisions){this(version,profile,schema,requirements,decisions,List.of());}
    public static final String PROPERTY = "use.jacamo.semantics.config";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    public CslMonitorConfiguration {
        if (!"1.0".equals(version) || profile == null || profile == SemanticProjectionProfile.NATIVE_CURRENT)
            throw new IllegalArgumentException("CSL_MONITOR_CONFIGURATION_VERSION_OR_PROFILE");
        Objects.requireNonNull(schema); Objects.requireNonNull(requirements);
        decisions = List.copyOf(decisions);
        jasonTasks=List.copyOf(jasonTasks==null?List.of():jasonTasks);
        if(jasonTasks.stream().map(t->List.of(t.sourceAgentProgram(),t.sourcePlan(),t.sourceBody(),t.occurrence())).distinct().count()!=jasonTasks.size())throw new IllegalArgumentException("JASON_TASK_BINDING_DUPLICATED");
        var capabilities=profile == SemanticProjectionProfile.CSL_OBSERVER_V1 ? SemanticsProfile.v1() : SemanticsProfile.v2();
        requirements.admit(capabilities);
        if(!jasonTasks.isEmpty()) {capabilities.require("jason.execution-observer");for(var task:jasonTasks)if(requirements.nodes().stream().noneMatch(n->n.kind()==RequirementModel.Kind.TASK && n.contractId().equals(task.targetContract()) && n.binding().equals(task.occurrence())))throw new IllegalArgumentException("JASON_TASK_REQUIREMENT_BINDING_MISSING");}
        if(!schema.policy().equals(org.tzi.use.plugins.jacamo.semantics.csl.CslStructuralPolicy.empty()))capabilities.require("csl.structural-policy");
        for (var requirement : requirements.nodes()) {
            if(org.tzi.use.plugins.jacamo.semantics.observer.FunctionalUseBinding.typeId(requirement.binding().selfTypeId()))capabilities.require("moise.functional-observer");
            else if(org.tzi.use.plugins.jacamo.semantics.observer.MentalUseBinding.typeId(requirement.binding().selfTypeId()))capabilities.require("jason.mental-observer");
            else schema.classifier(requirement.binding().selfTypeId());
            if(schema.classifier(requirement.binding().roleTypeId()).kind()!=CslSchema.Kind.ROLE)throw new IllegalArgumentException("REQUIREMENT_ROLE_BINDING_TYPE_REQUIRED");
            if(requirement.kind()==RequirementModel.Kind.TASK && schema.contracts().stream().noneMatch(c->c.id().equals(requirement.contractId())))throw new IllegalArgumentException("TASK_CONTRACT_UNRESOLVED:"+requirement.contractId());
        }
    }
    public static CslMonitorConfiguration parse(byte[] bytes) {
        CanonicalJson.decode(bytes); // bounded input, duplicate-key and nesting guards before record decoding
        try { return JSON.readValue(bytes, CslMonitorConfiguration.class); }
        catch (IOException invalid) { throw new IllegalArgumentException("CSL_MONITOR_CONFIGURATION_INVALID", invalid); }
    }
    public byte[] bytes() {
        try { return CanonicalJson.encode(CanonicalJson.decode(JSON.writeValueAsBytes(this))); }
        catch (IOException invalid) { throw new IllegalStateException("CSL_MONITOR_CONFIGURATION_ENCODING", invalid); }
    }
    public static CslMonitorConfiguration configured() {
        String path = System.getProperty(PROPERTY);
        if (path == null || path.isBlank()) throw new IllegalArgumentException("CSL_MONITOR_CONFIGURATION_REQUIRED:" + PROPERTY);
        try {
            var config = parse(Files.readAllBytes(Path.of(path)));
            if (config.profile() != SemanticProjectionProfile.configured(System.getProperty(SemanticProjectionProfile.PROPERTY)))
                throw new IllegalArgumentException("CSL_MONITOR_PROFILE_CONFIGURATION_MISMATCH");
            return config;
        } catch (IOException invalid) { throw new IllegalArgumentException("CSL_MONITOR_CONFIGURATION_READ_FAILED:" + path, invalid); }
    }
}

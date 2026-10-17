package org.tzi.use.plugins.jacamo.semantics.istar;

import java.util.Objects;
import static org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.required;

/** A selected source step maps to an existing invocation contract; no effects are inferred from its name. */
public record TaskBinding(String sourceAgentProgram,String sourcePlan,String sourceBody,String targetContract,RequirementModel.Binding occurrence,java.util.Map<String,Object> contextQuery) {
    public TaskBinding(String sourceAgentProgram,String sourcePlan,String sourceBody,String targetContract,RequirementModel.Binding occurrence){this(sourceAgentProgram,sourcePlan,sourceBody,targetContract,occurrence,java.util.Map.of());}
    public TaskBinding {required(sourceAgentProgram);required(sourcePlan);required(sourceBody);required(targetContract);Objects.requireNonNull(occurrence);contextQuery=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.object(contextQuery);}
    public RequirementModel.Node requirement(String id,String requirement) {
        return new RequirementModel.Node(id,requirement,RequirementModel.Kind.TASK,RequirementModel.Temporal.NONE,occurrence,"","true",RequirementModel.Refinement.NONE,java.util.List.of(),"",targetContract);
    }
}

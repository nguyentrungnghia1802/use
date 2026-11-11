package org.tzi.use.semantic.engine.frontend;

import java.util.*;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.istar.ast.IStarModelCS;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.istar.parser.*;
import static org.tzi.use.semantic.engine.api.EngineDiagnostic.*;

/** Original iStar compiler/AST/M1 plus explicit profile admission, without a runtime backend. */
public final class IStarFrontend {
    public record Result(IStarModelCS ast,GoalModel model,List<EngineDiagnostic> diagnostics,List<IStarCapabilityAdmission.Finding> findings,EngineSemanticsProfile profile) {
        public Result(IStarModelCS ast,GoalModel model,List<EngineDiagnostic> diagnostics,List<IStarCapabilityAdmission.Finding> findings) {
            this(ast,model,diagnostics,findings,EngineSemanticsProfile.USE4GOAL_OBSERVED_V1);
        }
        public Result {diagnostics=List.copyOf(diagnostics);findings=List.copyOf(findings);}
        public boolean ok(){return model!=null&&diagnostics.isEmpty();}
        public GoalModel requireModel(){if(!ok())throw new EngineAdmissionException(diagnostics);return model;}
        public OwnedGoalModel takeOwnership(){return new OwnedGoalModel(requireModel(),profile);}
    }
    public Result compile(String source,String name) {
        return compile(source,name,EngineSemanticsProfile.USE4GOAL_OBSERVED_V1);
    }
    public Result compile(String source,String name,EngineSemanticsProfile profile) {
        Objects.requireNonNull(profile);
        var admission=new IStarCapabilityAdmission().scan(source,name);
        if(!admission.diagnostics().isEmpty())return new Result(null,null,admission.diagnostics(),admission.findings(),profile);
        var original=IStarCompiler.compile(source,profile.validationPolicy());
        var diagnostics=new ArrayList<EngineDiagnostic>();
        original.errors().forEach(error->diagnostics.add(EngineDiagnostic.error(Stage.MODEL_VALIDATION,"ORIGINAL_ISTAR_VALIDATOR",name,error)));
        if(!original.ok())return new Result(null,null,diagnostics,admission.findings(),profile);
        var validated=validate(original.model(),name,profile);
        return new Result(IStarBuildingVisitor.build(admission.tree()),original.model(),validated.diagnostics(),admission.findings(),profile);
    }
    public Result validate(GoalModel model,String name) {
        return validate(model,name,EngineSemanticsProfile.USE4GOAL_OBSERVED_V1);
    }
    public Result validate(GoalModel model,String name,EngineSemanticsProfile profile) {
        return new Result(null,model,OwnedGoalModel.validateDefinition(model,name,profile),List.of(),profile);
    }
}

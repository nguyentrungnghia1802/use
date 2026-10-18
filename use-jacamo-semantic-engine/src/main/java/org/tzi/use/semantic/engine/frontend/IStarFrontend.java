package org.tzi.use.semantic.engine.frontend;

import java.util.*;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.istar.ast.IStarModelCS;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.istar.parser.*;
import static org.tzi.use.semantic.engine.api.EngineDiagnostic.*;

/** Original iStar compiler/AST/M1 plus explicit profile admission, without a runtime backend. */
public final class IStarFrontend {
    public record Result(IStarModelCS ast,GoalModel model,List<EngineDiagnostic> diagnostics,List<IStarCapabilityAdmission.Finding> findings) {
        public Result {diagnostics=List.copyOf(diagnostics);findings=List.copyOf(findings);}
        public boolean ok(){return model!=null&&diagnostics.isEmpty();}
        public GoalModel requireModel(){if(!ok())throw new EngineAdmissionException(diagnostics);return model;}
        public OwnedGoalModel takeOwnership(){return new OwnedGoalModel(requireModel());}
    }
    public Result compile(String source,String name) {
        var admission=new IStarCapabilityAdmission().scan(source,name);
        if(!admission.diagnostics().isEmpty())return new Result(null,null,admission.diagnostics(),admission.findings());
        var original=IStarCompiler.compile(source);
        var diagnostics=new ArrayList<EngineDiagnostic>();
        original.errors().forEach(error->diagnostics.add(EngineDiagnostic.error(Stage.MODEL_VALIDATION,"ORIGINAL_ISTAR_VALIDATOR",name,error)));
        if(!original.ok())return new Result(null,null,diagnostics,admission.findings());
        var validated=validate(original.model(),name);
        return new Result(IStarBuildingVisitor.build(admission.tree()),original.model(),validated.diagnostics(),admission.findings());
    }
    public Result validate(GoalModel model,String name) {
        return new Result(null,model,OwnedGoalModel.validateDefinition(model,name),List.of());
    }
}

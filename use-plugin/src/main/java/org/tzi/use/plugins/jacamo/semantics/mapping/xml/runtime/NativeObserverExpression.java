package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.io.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.parser.*;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;

/** Typed value transformation using native USE OCL. No world query, substitute evaluator or extra state. */
public final class NativeObserverExpression {
    record Result(Value value,String quality,String diagnostic) { }
    private final Node node;
    private final MModel model;
    private final Expression expression;
    NativeObserverExpression(Node node,MModel model,MClass owner,Set<String> columns) {
        this.node=node;this.model=model;
        if(node.attr("expression").length()>4096||node.children().size()>16||!columns.contains(node.attr("completeColumn")))throw node.location().error("OBSERVER_NATIVE_EXPRESSION_BOUND_OR_COVERAGE");
        var attribute=owner.attribute(node.attr("field"),true);if(attribute==null||attribute.isDerived())throw node.location().error("OBSERVER_NATIVE_TARGET_ATTRIBUTE_REQUIRED");
        var variables=new Symtable();var names=new HashSet<String>();var diagnostics=new StringWriter();
        try {
            for(Node arg:node.children("arg")) {
                String name=arg.attr("name");
                if(!arg.optional("onMissing").isEmpty()&&!arg.attr("onMissing").equals("undefined"))throw arg.location().error("OBSERVER_NATIVE_MISSING_POLICY_UNSUPPORTED");
                if(!name.matches("[A-Za-z_][A-Za-z0-9_]*")||name.equals("self")||!names.add(name)||!columns.contains(arg.attr("column")))throw arg.location().error("OBSERVER_NATIVE_ARGUMENT_INVALID");
                variables.add(name,type(arg.attr("type")),null);
            }
            expression=OCLCompiler.compileExpression(model,node.attr("expression"),"observer:"+owner.name()+"::"+node.attr("field"),new PrintWriter(diagnostics,true),variables);
            if(expression==null||!expression.type().conformsTo(attribute.type())||expression.requiresPreState())throw node.location().error("OBSERVER_NATIVE_COMPILATION_OR_RESULT_TYPE:"+diagnostics);
            // Traverse the actual host AST, including nested branches. A source collection's
            // explicit coverage cannot certify arbitrary allInstances/navigation population.
            requirePureValue(expression);
        }catch(SemanticException invalid){throw node.location().error("OBSERVER_NATIVE_BINDING:"+invalid.getMessage());}
    }
    String field(){return node.attr("field");}
    int arguments(){return node.children("arg").size();}
    Result evaluate(Port port,MObject object,RuntimeTables.Row row) {
        if(row==null||row.quality()!=Completeness.COMPLETE||!Boolean.TRUE.equals(row.values().get(node.attr("completeColumn"))))return unknown("INCOMPLETE","OBSERVER_EXPRESSION_COVERAGE_UNAVAILABLE");
        var state=port.readState();
        if(state==null)return unknown("INCOMPLETE","AUTHORITATIVE_NATIVE_STATE_UNAVAILABLE");
        if(state.system().model()!=model||state.objectByName(object.name())!=object)return unknown("ERROR","OBSERVER_EXPRESSION_STATE_OWNER_MISMATCH");
        var bindings=new VarBindings();var missing=new HashSet<String>();
        try {
            for(Node arg:node.children("arg")) {
                if(!row.values().containsKey(arg.attr("column"))) {
                    if(arg.optional("onMissing").equals("undefined")){bindings.push(arg.attr("name"),UndefinedValue.instance);missing.add(arg.attr("name"));continue;}
                    return unknown("INCOMPLETE","OBSERVER_EXPRESSION_ARGUMENT_UNAVAILABLE:"+arg.attr("name"));
                }
                bindings.push(arg.attr("name"),ObserverProjection.scalar(arg.attr("type"),row.values().get(arg.attr("column"))));
            }
            var evaluator=new Evaluator(!missing.isEmpty());Value result=evaluator.eval(expression,state,bindings);
            if(result!=null&&!result.isUndefined()&&!missing.isEmpty()&&readsMissing(evaluator.getEvalNodeRoot(),missing))return unknown("INCOMPLETE","OBSERVER_EXPRESSION_READ_MISSING_ARGUMENT");
            return result==null||result.isUndefined()?unknown("UNDEFINED","NATIVE_OCL_UNDEFINED"):new Result(result,"COMPLETE","");
        }catch(RuntimeException invalid){return unknown("ERROR","NATIVE_OCL:"+invalid.getClass().getSimpleName()+":"+invalid.getMessage());}
    }
    /** Inspect the host's actual evaluation trace; a non-selected IF branch is not evaluated.
     * No expression interpretation or replacement Boolean semantics lives in this adapter. */
    private static boolean readsMissing(EvalNode root,Set<String> missing) {
        if(root==null)return true;var pending=new ArrayDeque<EvalNode>();pending.add(root);int visited=0;
        while(!pending.isEmpty()) {
            if(++visited>16384)return true;var current=pending.removeFirst();
            if(current.getExpression() instanceof ExpVariable variable&&missing.contains(variable.getVarname()))return true;
            pending.addAll(current.children());if(pending.size()>16384)return true;
        }
        return false;
    }
    private static Result unknown(String quality,String reason){return new Result(UndefinedValue.instance,quality,reason);}
    private static Type type(String type){return switch(type){case "Boolean"->TypeFactory.mkBoolean();case "String"->TypeFactory.mkString();case "Integer"->TypeFactory.mkInteger();case "Set(String)"->TypeFactory.mkSet(TypeFactory.mkString());default->throw new IllegalArgumentException("OBSERVER_NATIVE_ARGUMENT_TYPE_UNSUPPORTED");};}
    public static void requirePureValue(Expression expression){expression.processWithVisitor(new PureValueGuard());}
    private static final class PureValueGuard extends ExpressionPrintVisitor {
        PureValueGuard(){super(new PrintWriter(Writer.nullWriter()));}
        private void reject(){throw new IllegalArgumentException("OBSERVER_NATIVE_WORLD_QUERY_UNSUPPORTED");}
        @Override public void visitAllInstances(ExpAllInstances e){reject();}
        @Override public void visitAttrOp(ExpAttrOp e){reject();}
        @Override public void visitNavigation(ExpNavigation e){reject();}
        @Override public void visitNavigationClassifierSource(ExpNavigationClassifierSource e){reject();}
        @Override public void visitInstanceOp(ExpInstanceOp e){reject();}
        @Override public void visitObjRef(ExpObjRef e){reject();}
        @Override public void visitObjectByUseId(ExpObjectByUseId e){reject();}
        @Override public void visitOclInState(ExpOclInState e){reject();}
    }
}

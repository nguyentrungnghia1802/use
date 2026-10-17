package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.io.*;
import org.antlr.runtime.*;
import org.tzi.use.api.*;
import org.tzi.use.parser.*;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.parser.generator.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;

/** Uses the host compiler, including actual parameter/result and @pre contexts. */
public final class SemanticOclCompiler {
    private SemanticOclCompiler() { }
    public static Expression predicate(MModel model, MClass context, String id, String body) {
        var diagnostics = new StringWriter();
        try {
            var variables = new Symtable();
            variables.add("self", context, null);
            var expression = OCLCompiler.compileExpression(model, body, id, new PrintWriter(diagnostics, true), variables);
            if (expression == null || !expression.type().isTypeOfBoolean())
                throw new IllegalArgumentException("OCL_BOOLEAN_COMPILATION_FAILED:" + id + ":" + diagnostics);
            return expression;
        } catch (SemanticException invalid) { throw new IllegalArgumentException("OCL_CONTEXT_INVALID:" + id, invalid); }
    }
    /** Pair-state predicate without adding a synthetic operation to the borrowed model. */
    public static Expression transitionPredicate(MModel model,MClass self,String id,String body) {
        var diagnostics=new StringWriter();var errors=new PrintWriter(diagnostics,true);var handler=new ParseErrorHandler(id,errors);
        var lexer=new GeneratorLexer(new ANTLRStringStream(body));var parser=new GeneratorParser(new CommonTokenStream(lexer));lexer.init(handler);parser.init(handler);
        try {var ast=parser.expressionOnly();if(handler.errorCount()!=0)throw new IllegalArgumentException("OCL_SYNTAX_ERROR:"+id+":"+diagnostics);
            var context=new Context(id,errors,null,new ModelFactory());context.setModel(model);context.varTable().add("self",self,null);context.exprContext().push("self",self);context.setInsidePostCondition(true);
            var expression=ast.gen(context);if(!expression.type().isTypeOfBoolean())throw new IllegalArgumentException("OCL_BOOLEAN_CONDITION_REQUIRED:"+id);return expression;
        }catch(RecognitionException|SemanticException invalid){throw new IllegalArgumentException("OCL_TRANSITION_COMPILATION_FAILED:"+id+":"+diagnostics,invalid);}
    }
    public static MPrePostCondition condition(UseModelApi api, MOperation operation, String id, String body, boolean pre) {
        var diagnostics = new StringWriter(); var errors = new PrintWriter(diagnostics, true);
        var handler = new ParseErrorHandler(id, errors);
        var lexer = new GeneratorLexer(new ANTLRStringStream(body));
        var parser = new GeneratorParser(new CommonTokenStream(lexer));
        lexer.init(handler); parser.init(handler);
        try {
            var ast = parser.expressionOnly();
            if (handler.errorCount() != 0) throw new IllegalArgumentException("OCL_SYNTAX_ERROR:" + id + ":" + diagnostics);
            var context = new Context(id, errors, null, new ModelFactory());
            context.setModel(api.getModel()); context.varTable().add("self", operation.cls(), null);
            context.exprContext().push("self", operation.cls());
            for (int i = 0; i < operation.paramList().size(); i++) {
                var parameter = operation.paramList().varDecl(i);
                context.varTable().add(parameter.name(), parameter.type(), null);
            }
            if (!pre && operation.hasResultType()) context.varTable().add("result", operation.resultType(), null);
            context.setInsidePostCondition(!pre);
            var expression = ast.gen(context);
            if (!expression.type().isTypeOfBoolean()) throw new IllegalArgumentException("OCL_BOOLEAN_CONDITION_REQUIRED:" + id);
            return api.createPrePostConditionEx(id, operation, pre, expression);
        } catch (RecognitionException | SemanticException | UseApiException invalid) {
            throw new IllegalArgumentException("OCL_CONDITION_COMPILATION_FAILED:" + id + ":" + diagnostics, invalid);
        }
    }
}

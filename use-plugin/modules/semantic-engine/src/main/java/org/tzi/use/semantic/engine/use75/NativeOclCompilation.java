package org.tzi.use.semantic.engine.use75;

import java.io.*;
import java.util.*;
import org.antlr.runtime.*;
import org.tzi.use.api.*;
import org.tzi.use.parser.*;
import org.tzi.use.parser.generator.*;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.Type;

/** Shared host compiler boundary extracted from SemanticOclCompiler. No interpreter fallback. */
public final class NativeOclCompilation {
    private NativeOclCompilation() { }
    public static Expression predicate(MModel model,MClass self,String id,String body) {
        requireContext(model,self);var diagnostics=new StringWriter();
        try {
            var variables=new Symtable();variables.add("self",self,null);
            var expression=OCLCompiler.compileExpression(model,body,id,new PrintWriter(diagnostics,true),variables);
            if(expression==null||!expression.type().isTypeOfBoolean())throw new IllegalArgumentException("OCL_BOOLEAN_COMPILATION_FAILED:"+id+":"+diagnostics);
            return expression;
        }catch(SemanticException invalid){throw new IllegalArgumentException("OCL_CONTEXT_INVALID:"+id,invalid);}
    }
    public static Expression transitionPredicate(MModel model,MClass self,String id,String body) {
        return expression(model,self,null,id,body,true);
    }
    public static Expression compile(MModel model,OclContractSpec specification) {
        return expression(model,specification.self(),specification.operation(),specification.id(),specification.body(),specification.requiresBefore(),specification.bindings());
    }
    public static Expression operationPredicate(MModel model,MOperation operation,String id,String body,boolean pre) {
        requireContext(model,operation.cls());return expression(model,operation.cls(),operation,id,body,!pre);
    }
    public static MPrePostCondition condition(UseModelApi api,MOperation operation,String id,String body,boolean pre) {
        var expression=operationPredicate(api.getModel(),operation,id,body,pre);
        try{return api.createPrePostConditionEx(id,operation,pre,expression);}
        catch(UseApiException invalid){throw new IllegalArgumentException("OCL_CONDITION_COMPILATION_FAILED:"+id,invalid);}
    }
    private static Expression expression(MModel model,MClassifier self,MOperation operation,String id,String body,boolean post) {
        return expression(model,self,operation,id,body,post,Map.of());
    }
    private static Expression expression(MModel model,MClassifier self,MOperation operation,String id,String body,boolean post,Map<String,Type> bindings) {
        requireContext(model,self);var diagnostics=new StringWriter();var errors=new PrintWriter(diagnostics,true);var handler=new ParseErrorHandler(id,errors);
        var lexer=new GeneratorLexer(new ANTLRStringStream(body));var parser=new GeneratorParser(new CommonTokenStream(lexer));lexer.init(handler);parser.init(handler);
        try {
            var ast=parser.expressionOnly();if(handler.errorCount()!=0)throw new IllegalArgumentException("OCL_SYNTAX_ERROR:"+id+":"+diagnostics);
            var context=new Context(id,errors,null,new ModelFactory());context.setModel(model);
            context.varTable().add("self",self,null);context.exprContext().push("self",self);
            for(var binding:bindings.entrySet())context.varTable().add(binding.getKey(),binding.getValue(),null);
            if(operation!=null)for(int i=0;i<operation.paramList().size();i++) {
                var parameter=operation.paramList().varDecl(i);context.varTable().add(parameter.name(),parameter.type(),null);
            }
            if(post&&operation!=null&&operation.hasResultType())context.varTable().add("result",operation.resultType(),null);
            context.setInsidePostCondition(post);var expression=ast.gen(context);
            if(!expression.type().isTypeOfBoolean())throw new IllegalArgumentException("OCL_BOOLEAN_CONDITION_REQUIRED:"+id);
            return expression;
        }catch(RecognitionException|SemanticException invalid){throw new IllegalArgumentException("OCL_CONDITION_COMPILATION_FAILED:"+id+":"+diagnostics,invalid);}
    }
    private static void requireContext(MModel model,MClassifier self) {
        if(self==null||self.model()!=model||model.getClassifier(self.name())!=self)throw new IllegalArgumentException("OCL_CONTEXT_MODEL_MISMATCH");
    }
}

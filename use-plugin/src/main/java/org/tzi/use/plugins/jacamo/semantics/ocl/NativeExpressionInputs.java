package org.tzi.use.plugins.jacamo.semantics.ocl;

import java.util.*;
import org.tzi.use.analysis.coverage.CoverageCalculationVisitor;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.Type;

/** Native AST input discovery shared by profile adapters. Does not evaluate OCL. */
public final class NativeExpressionInputs {
    private NativeExpressionInputs() { }
    public static Set<String> variables(Expression expression) {
        var result=new HashSet<String>();expression.processWithVisitor(new CoverageCalculationVisitor(true) {
            @Override public void visitVariable(ExpVariable variable){result.add(variable.getVarname());super.visitVariable(variable);}
            @Override public void visitTupleSelectOp(ExpTupleSelectOp select){select.getTupleExp().processWithVisitor(this);}
        });return Set.copyOf(result);
    }
    public static Map<String,Set<String>> tupleParts(Expression expression) {
        var result=new TreeMap<String,Set<String>>();expression.processWithVisitor(new CoverageCalculationVisitor(true) {
            @Override public void visitTupleSelectOp(ExpTupleSelectOp select){if(select.getTupleExp() instanceof ExpVariable variable)result.computeIfAbsent(variable.getVarname(),ignored->new HashSet<>()).add(select.getPart().name());select.getTupleExp().processWithVisitor(this);}
        });return Collections.unmodifiableMap(result);
    }
    public static Set<String> wholeTupleVariables(Expression expression) {
        var result=new HashSet<String>();expression.processWithVisitor(new CoverageCalculationVisitor(true) {
            @Override public void visitVariable(ExpVariable variable){if(variable.type() instanceof org.tzi.use.uml.ocl.type.TupleType)result.add(variable.getVarname());super.visitVariable(variable);}
            @Override public void visitTupleSelectOp(ExpTupleSelectOp select){
                // A direct part selection consumes that part, not the whole tuple. Other
                // receiver expressions are traversed conservatively, including all branches.
                if(!(select.getTupleExp() instanceof ExpVariable))select.getTupleExp().processWithVisitor(this);
            }
        });return Set.copyOf(result);
    }
    public static NativeOclBackend.Compiled compile(NativeOclBackend backend,MModel model,OclContractSpec specification) {
        var used=variables(NativeOclCompilation.compile(model,specification));var actual=new TreeMap<String,Type>();specification.bindings().forEach((name,type)->{if(used.contains(name))actual.put(name,type);});
        return backend.compile(new OclContractSpec(specification.id(),specification.kind(),specification.self(),specification.operation(),specification.body(),actual));
    }
}

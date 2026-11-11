package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;

import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.semantic.engine.use75.UseStateReadView;

/** Typed input boundary of the existing native Task PRE/POST journal.
 * Source adapters qualify identities/variables; the journal owns pairing,
 * savepoints, capacity, duplicate/overlap handling and ordered evaluation.
 * Implementations cannot substitute a predicate result or approve a checkpoint. */
public interface NativeTaskBindings extends AutoCloseable {
    Set<String> tasks();
    int expressionCount();
    boolean traceOnly(String sourceStep);
    boolean permitsLateSelf(String task,NativeInvocationEvaluation.Condition entry);
    boolean references(String task,OccurrenceProjection.Scope scope,JasonStepCutDecoder.Step step,NativeRuntimeMutationEngine engine,boolean pre);
    Map<String,Object> referenceIdentity(OccurrenceProjection.Scope scope,OccurrenceProjection.Member member,String task);
    String intervalFloor(NativeRuntimeMutationEngine engine,OccurrenceProjection.Member member);
    boolean currentInterval(NativeRuntimeMutationEngine engine,String player,String role,String interval);
    NativeInvocationEvaluation.Condition prepare(String task,boolean pre,NativeRuntimeMutationEngine engine,OccurrenceProjection.Scope scope,OccurrenceProjection.Member member,
            TaskDemandProjection.Demand demand,JasonStepCutDecoder.Step step,UseStateReadView view,Map<String,Completeness> sources,boolean lost);
    @Override void close();
}

package org.tzi.use.semantic.engine.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Ownership guard for the mutable original M1 API. Runtime owns markings, never edits definitions.
 * This does not pretend that the original Java model is immutable; a mutation fails the owner fence.
 */
public final class OwnedGoalModel {
    private final GoalModel model;
    private final String hash;
    public OwnedGoalModel(GoalModel model) {
        var diagnostics=validateDefinition(model,"<owned-goal-model>");
        if(!diagnostics.isEmpty())throw new EngineAdmissionException(diagnostics);
        this.model=model;this.hash=fingerprint(model);
    }
    public GoalModel model(){assertUnchanged();return model;}
    public String hash(){return hash;}
    public void assertUnchanged(){if(!hash.equals(fingerprint(model)))throw new IllegalStateException("CANONICAL_GOAL_MODEL_MUTATED_AFTER_OWNERSHIP");}
    public static List<EngineDiagnostic> validateDefinition(GoalModel model,String name) {
        Objects.requireNonNull(model);Objects.requireNonNull(name);
        var diagnostics=new ArrayList<EngineDiagnostic>();
        GoalModelValidator.validate(model).forEach(error->diagnostics.add(EngineDiagnostic.error(EngineDiagnostic.Stage.MODEL_VALIDATION,"ORIGINAL_ISTAR_VALIDATOR",name,error)));
        var actorIds=new HashSet<String>();
        for(var actor:model.getActors()) {
            if(!actorIds.add(actor.name()))diagnostics.add(EngineDiagnostic.error(EngineDiagnostic.Stage.MODEL_VALIDATION,"ISTAR_ACTOR_ID_AMBIGUOUS",name,actor.name()));
            if(model.findActor(actor.name()).orElse(null)!=actor)diagnostics.add(EngineDiagnostic.error(EngineDiagnostic.Stage.MODEL_VALIDATION,"ISTAR_ACTOR_INDEX_STALE",name,actor.name()));
            for(var element:actor.elements())if(model.findElement(element.id()).orElse(null)!=element||!model.ownerOf(element.id()).orElse("").equals(actor.name()))
                diagnostics.add(EngineDiagnostic.error(EngineDiagnostic.Stage.MODEL_VALIDATION,"ISTAR_ELEMENT_INDEX_STALE",name,element.id()));
        }
        return List.copyOf(diagnostics);
    }
    public static String fingerprint(GoalModel model) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");put(digest,"GoalModel",model.getName(),Integer.toString(model.getActors().size()));
            for(var actor:model.getActors()) {
                put(digest,"Actor",actor.getClass().getName(),actor.name(),Integer.toString(actor.elements().size()));
                for(var element:actor.elements())element(digest,element);
                put(digest,"Refinements",Integer.toString(actor.refinements().size()));
                for(var refinement:actor.refinements()){put(digest,refinement.getClass().getName(),refinement.parent());for(var child:GoalActivationGraph.children(refinement))put(digest,child);put(digest,"/Refinement");}
                put(digest,"Contributions",Integer.toString(actor.contributions().size()));
                for(var contribution:actor.contributions())put(digest,contribution.element(),String.valueOf(contribution.type()),contribution.quality());
                put(digest,"Qualifications",Integer.toString(actor.qualifications().size()));
                for(var qualification:actor.qualifications())put(digest,qualification.quality(),qualification.element());
                put(digest,"NeededBy",Integer.toString(actor.neededBys().size()));
                for(var needed:actor.neededBys())put(digest,needed.resource(),needed.task());
                put(digest,"Associations",Integer.toString(actor.associations().size()));
                for(var association:actor.associations())put(digest,association.actor(),String.valueOf(association.kind()),association.target());
            }
            put(digest,"Dependencies",Integer.toString(model.getDependencies().size()));
            for(var dependency:model.getDependencies()){
                put(digest,dependency.depender(),dependency.dependerElmt(),dependency.dependee(),dependency.dependeeElmt());element(digest,dependency.dependum());
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static void element(MessageDigest digest,IntentionalElement element) {
        if(element==null){put(digest,(String)null);return;}
        put(digest,element.getClass().getName(),element.id());
        if(element instanceof Goal goal){put(digest,String.valueOf(goal.goalType()),goal.oclSource());}
        if(element instanceof Task task){put(digest,"Pre",Integer.toString(task.preconditions().size()));task.preconditions().forEach(c->put(digest,c.oclBody()));put(digest,"Post",Integer.toString(task.postconditions().size()));task.postconditions().forEach(c->put(digest,c.oclBody()));}
    }
    private static void put(MessageDigest digest,String... fields) {
        for(String field:fields) {
            byte[] bytes=field==null?null:field.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes==null?-1:bytes.length).array());
            if(bytes!=null)digest.update(bytes);
        }
    }
}

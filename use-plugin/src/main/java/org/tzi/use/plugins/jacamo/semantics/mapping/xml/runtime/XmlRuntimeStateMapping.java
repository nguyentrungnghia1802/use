package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MObject;

/** Neutral XML adapter on the existing cut transaction. Owns no model/state/history/transport. */
public final class XmlRuntimeStateMapping implements CaseRuntimeMapping {
    private final SemanticProgram program;
    private final XmlRuntimeBatch batch;
    private final String revision;
    public XmlRuntimeStateMapping(SemanticProgram program,MModel model) {
        this.program=Objects.requireNonNull(program);
        var plan=Objects.requireNonNull(program.xmlPlan(),"XML_PROGRAM_REQUIRED");
        var asset=program.complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow();
        var source=ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(asset.bytes())));
        program.requireSourceHash(new SourceModelNormalizer().normalize(source).sourceHash());revision=source.modelRevision();
        batch=new XmlRuntimeBatch(plan.inputs().specification(),model,program.hash(),source,plan.requirements(),program.bindings(),program.goalModel());
    }
    @Override public void validateObservation(VerificationObservation observation,String run,long generation,String revision) {
        program.assertUnchanged();
        if(!this.revision.equals(revision)||run==null||run.isBlank()||generation<0)throw new IllegalArgumentException("XML_OBSERVATION_EPOCH_MISMATCH");
        if(observation.kind()==VerificationObservation.Kind.CLOCK)for(var request:program.xmlPlan().requirements())if(request.provider().equals("npl.source-clock.v1")&&request.parameters().get("hash") instanceof String hash)
            org.tzi.use.plugins.jacamo.semantics.observer.SourceClock.decode(observation,run,generation,revision,hash);
        // VerificationObservation.require already verifies envelope scope at admission. Raw fact datasets
        // never expose provider payloads; provider-specific evidence is not qualified by this callback.
    }
    @Override public List<CaseRuntimeMapping.Outcome> applyCut(Port port,String run,long generation,RuntimeSnapshot cut,List<RuntimeEvent> prefix,RuntimeEvent event)throws UseApiException {
        program.assertUnchanged();if(!revision.equals(cut.modelRevision()))throw new IllegalArgumentException("XML_RUNTIME_REVISION_MISMATCH");
        if(event!=null&&(!run.equals(event.sessionId())||generation!=event.generation()||!revision.equals(event.modelRevision())))throw new IllegalArgumentException("XML_RUNTIME_EVENT_EPOCH_MISMATCH");
        var results=new ArrayList<CaseRuntimeMapping.Outcome>();
        for(var outcome:batch.apply(port,run,generation,cut,event))results.add(new CaseRuntimeMapping.Outcome(outcome.status().equals("MAPPED")||outcome.status().equals("INCOMPLETE")&&outcome.object()!=null,"XML-M0-"+outcome.rule(),outcome.nativeKey(),outcome.object(),outcome.status()+":"+outcome.diagnostic()));
        return List.copyOf(results);
    }
    @Override public boolean ownsRemoval(Port port,MObject object){return batch.owns(port,object);}
    public List<OccurrenceProjection.Scope> occurrences(Port port,String run,long generation,String revision){
        program.assertUnchanged();if(!this.revision.equals(revision))throw new IllegalArgumentException("XML_OCCURRENCE_REVISION_MISMATCH");
        return batch.occurrences(port,run,generation,revision);
    }
    public List<TaskDemandProjection.Demand> taskDemands(Port port,String run,long generation,String revision){
        program.assertUnchanged();if(!this.revision.equals(revision))throw new IllegalArgumentException("XML_TASK_DEMAND_REVISION_MISMATCH");return batch.taskDemands(port,run,generation,revision);
    }
    @Override public String rawInputRuleId(){return "XML-RAW-INPUT";}
    @Override public String observationRejectionCode(){return "XML_OBSERVATION_REJECTED";}
    @Override public String mappingRejectionCode(){return "XML_M0_MAPPING_REJECTED";}
}

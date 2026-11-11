package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.observer.FunctionalObservation;

/** Typed framework evidence for one admitted cut. Owns no live state or verdict. */
public final class CompactSourceCut {
    public record Frame(JasonStepCutDecoder.Step step,long sequence) { }
    public record Scheme(RuntimeFact board,RuntimeFact artifact,RuntimeFact workspace,
                         FunctionalObservation publicState,MoiseSchemeCutDecoder.Result history) { }
    private final ModelSnapshot source;
    private final List<JasonStepCutDecoder> steps;
    private record SchemeDecoder(ObservationCapabilityRequest request,MoiseSchemeCutDecoder decoder) { }
    private final List<SchemeDecoder> schemes;
    private final MoiseFactQualification qualifier;

    public CompactSourceCut(SemanticProgram program) {
        var asset=program.complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow();
        source=ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(asset.bytes())));
        qualifier=new MoiseFactQualification(source);
        steps=program.compactPlan().capabilityRequests().stream().filter(r->r.provider().equals("jason.plan-boundaries.v1"))
                .map(r->new JasonStepCutDecoder(source,r)).toList();
        schemes=program.compactPlan().capabilityRequests().stream().filter(r->r.provider().equals("moise.scheme-lifecycle.v1"))
                .map(r->new SchemeDecoder(r,new MoiseSchemeCutDecoder(source,r))).toList();
    }
    public ModelSnapshot source(){return source;}
    public void qualify(RuntimeFact fact,String run){qualifier.require(fact,run);}
    public List<Frame> frames(RuntimeSnapshot cut,String run,long generation) {
        var result=new ArrayList<Frame>();
        for(var fact:cut.facts())if(fact.values().containsKey(VerificationObservation.KEY)) {
            var observation=VerificationObservation.require(fact,cut,run,generation);
            if(observation.kind()!=VerificationObservation.Kind.JASON_STEP)continue;
            var selected=steps.stream().filter(d->d.accepts(observation)).toList();
            if(selected.size()!=1)throw new IllegalArgumentException("COMPACT_STEP_SOURCE_NOT_UNIQUE");
            var envelope=object(fact.values().get(VerificationObservation.KEY));
            long sequence=((Number)object(envelope.get("scope")).get("sourceSequence")).longValue();
            result.add(new Frame(selected.getFirst().decode(observation,run,generation,cut.modelRevision()),sequence));
        }
        result.sort(Comparator.comparingLong(Frame::sequence));return List.copyOf(result);
    }
    public List<Scheme> schemes(RuntimeSnapshot cut,String run,long generation) {
        var result=new ArrayList<Scheme>();
        for(var board:cut.facts())if(board.kind()==RuntimeFactKind.SCHEME_BOARD&&board.id().authority().equals("moise")&&faithful(board)) {
            qualifier.require(board,run);
            var selected=schemes.stream().filter(d->d.decoder().acceptsDefinition(board.values().get("schemeSpecSemanticId"))).toList();
            if(selected.isEmpty())continue;
            if(selected.size()!=1)throw new IllegalArgumentException("COMPACT_SCHEME_REQUEST_NOT_UNIQUE");
            var artifacts=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT&&f.id().authority().equals("cartago")
                    &&f.id().incarnation().equals(board.values().get("boardArtifactIdentity"))).toList();
            var artifact=artifacts.size()==1?artifacts.getFirst():null;
            var workspace=artifact==null?null:workspace(cut,artifact);
            var request=selected.getFirst();var receipts=artifact==null?Map.<String,Object>of():object(artifact.values().get(CapabilityObservation.KEY));
            var decoded=request.decoder().decode(receipts.get(request.request().id()),artifact,workspace,board,cut,run,generation);
            result.add(new Scheme(board,artifact,workspace,FunctionalObservation.decode(board.id(),board.values()),decoded));
        }
        return List.copyOf(result);
    }
    public static RuntimeFact workspace(RuntimeSnapshot cut,RuntimeFact artifact) {
        var matches=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE&&f.id().authority().equals("cartago")
                &&Objects.equals(f.values().get("semanticId"),artifact.values().get("workspaceSemanticId"))).toList();
        if(matches.size()>1)throw new IllegalArgumentException("COMPACT_PHYSICAL_WORKSPACE_AMBIGUOUS");
        return matches.isEmpty()?null:matches.getFirst();
    }
    public static boolean faithful(RuntimeFact fact){return fact!=null&&fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY;}
    public static Map<String,Object> object(Object raw){return raw instanceof Map<?,?>?CanonicalJson.object(raw):Map.of();}
    public static String text(Map<String,Object> raw,String key){return raw.get(key) instanceof String s?s:"";}
}

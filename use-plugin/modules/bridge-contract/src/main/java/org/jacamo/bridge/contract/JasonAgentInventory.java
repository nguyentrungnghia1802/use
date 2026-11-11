package org.jacamo.bridge.contract;

import java.util.*;

/** A current runner roster, not a claim about continuous agent history. */
public final class JasonAgentInventory {
    public static final String VERSION="JASON_AGENT_INVENTORY/1.0";
    public static final String POLICY="JASON_AGENT_INVENTORY_V1";
    private JasonAgentInventory(){ }

    public static boolean supplied(RuntimeSnapshot cut) {
        var sources=cut.captureMetadata().get("sourceEvidence");
        if(!(sources instanceof Map<?,?> all)||!(all.get("jason") instanceof Map<?,?> jason))return false;
        Object inventory=jason.get("agentInventory");return inventory!=null&&(!(inventory instanceof Map<?,?> map)||!map.isEmpty());
    }

    public static Map<String,Object> payload(String session,long sequence,Collection<String> identities) {
        var members=new TreeSet<>(identities);
        if(session.isBlank()||sequence<0||members.size()!=identities.size()||members.size()>4096)throw new ContractException("JASON_INVENTORY_INVALID");
        var body=new TreeMap<String,Object>();
        body.put("version",VERSION);body.put("session",session);body.put("sourceSequence",sequence);body.put("members",List.copyOf(members));
        body.put("hash",VerifiedCheckpointProtocol.hash(body));return Collections.unmodifiableMap(body);
    }

    /** A missing/stale certificate stays unknown; it never upgrades source quality. */
    public static boolean complete(RuntimeSnapshot cut,String session) {
        try {
            var mark=cut.startWatermarks().get("jason");
            if(cut.sourceCompleteness().get("jason")!=Completeness.COMPLETE||mark==null||!mark.equals(cut.endWatermarks().get("jason")))return false;
            var sources=CanonicalJson.object(cut.captureMetadata().get("sourceEvidence"));
            var source=CanonicalJson.object(sources.get("jason"));
            var body=new TreeMap<>(CanonicalJson.object(source.get("agentInventory")));Object hash=body.remove("hash");
            if(!VerifiedCheckpointProtocol.hash(body).equals(hash)||!VERSION.equals(body.get("version"))||!session.equals(body.get("session"))
                    ||!(body.get("sourceSequence") instanceof Number sequence)||sequence.longValue()!=mark.sequence()
                    ||!(body.get("members") instanceof List<?> members)||members.size()>4096)return false;
            var expected=new TreeSet<String>();for(Object raw:members) {
                if(!(raw instanceof String id)||!expected.add(id))return false;
                var entity=BridgeEntityId.parse(id);
                if(!entity.authority().equals("jason")||!entity.dimension().equals("agent")||!entity.kind().equals("runtime-agent")||entity.incarnation().startsWith("unobserved:"))return false;
            }
            var actual=new TreeSet<String>();
            for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.AGENT&&fact.id().authority().equals("jason")) {
                if(fact.completeness()!=Completeness.COMPLETE||fact.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY
                        ||!fact.id().dimension().equals("agent")||!fact.id().kind().equals("runtime-agent")||!actual.add(fact.id().canonical()))return false;
            }
            return expected.equals(actual);
        }catch(RuntimeException invalid){return false;}
    }
}

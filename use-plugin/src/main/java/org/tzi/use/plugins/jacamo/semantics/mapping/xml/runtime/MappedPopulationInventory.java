package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.uml.mm.MClassifier;

/** Current native output reconciliation. Metadata is owned by the MSystem savepoint. */
public final class MappedPopulationInventory {
    private static final String KEY="xml:m0:population-inventory",VERSION="MAPPED_CURRENT_POPULATIONS/1.0";
    private static final Set<String> AUTHORITIES=Set.of("CARTAGO_BOARD_INVENTORY_V1","CARTAGO_WORKSPACE_INVENTORY_V1",JasonAgentInventory.POLICY);
    private MappedPopulationInventory(){ }

    static void record(Port port,String program,String epoch,String cut,Map<String,Object> rules) {
        var body=new TreeMap<String,Object>();body.putAll(Map.of("version",VERSION,"program",program,"epoch",epoch,"cut",cut,"rules",rules));
        body.put("hash",SemanticJson.hash(body));String encoded=new String(CanonicalJson.encode(body),StandardCharsets.UTF_8);
        boolean stored=false;
        for(var object:port.objects().stream().sorted(Comparator.comparing(org.tzi.use.uml.sys.MObject::name)).toList())if(program.equals(port.metadata(object,"xml:m0:program"))) {
            boolean current=epoch.equals(port.metadata(object,"xml:m0:epoch"))&&cut.equals(port.metadata(object,"xml:m0:cut"))
                    &&"COMPLETE".equals(port.metadata(object,"xml:m0:quality"));
            port.metadata(object,KEY,current&&!stored?encoded:"");
            stored|=current;
        }
    }

    public static boolean complete(MClassifier type,NativeRuntimeMutationEngine engine) {
        var specification=engine.canonicalProgram().xmlPlan().inputs().specification();
        var declarations=specification.root().child("runtime").children("rule").stream().filter(rule->rule.children("upsertInstance").size()==1
                &&engine.system().model().getClass(rule.children("upsertInstance").getFirst().attr("targetType")).isSubClassifierOf(type)).toList();
        if(declarations.isEmpty())return false;
        for(var carrier:engine.system().state().allObjects())try {
            if(!engine.canonicalProgramHash().equals(engine.metadata(carrier,"xml:m0:program"))||!"COMPLETE".equals(engine.metadata(carrier,"xml:m0:quality")))continue;
            String encoded=engine.metadata(carrier,KEY);if(encoded.isBlank())continue;
            var body=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(encoded.getBytes(StandardCharsets.UTF_8))));Object hash=body.remove("hash");
            if(!SemanticJson.hash(body).equals(hash)||!VERSION.equals(body.get("version"))||!engine.canonicalProgramHash().equals(body.get("program"))
                    ||!engine.metadata(carrier,"xml:m0:epoch").equals(body.get("epoch"))||!engine.metadata(carrier,"xml:m0:cut").equals(body.get("cut")))continue;
            var receipts=CanonicalJson.object(body.get("rules"));var all=new HashSet<String>();boolean valid=true;
            for(var declaration:declarations) {
                String rule=declaration.attr("id"),target=declaration.children("upsertInstance").getFirst().attr("targetType");
                var receipt=CanonicalJson.object(receipts.getOrDefault(rule,Map.of()));
                if(!Boolean.TRUE.equals(receipt.get("complete"))||!target.equals(receipt.get("target"))||!AUTHORITIES.contains(receipt.get("authority"))
                        ||!(receipt.get("members") instanceof List<?> members)||members.size()!=new HashSet<>(members).size()){valid=false;break;}
                var actual=new TreeSet<String>();
                for(var object:engine.system().state().allObjects())if(rule.equals(engine.metadata(object,"xml:m0:rule"))&&object.cls().isSubClassifierOf(type)) {
                    if(!target.equals(object.cls().name())||!body.get("program").equals(engine.metadata(object,"xml:m0:program"))
                            ||!body.get("epoch").equals(engine.metadata(object,"xml:m0:epoch"))||!body.get("cut").equals(engine.metadata(object,"xml:m0:cut"))
                            ||!"COMPLETE".equals(engine.metadata(object,"xml:m0:quality"))||!"COMPLETE".equals(engine.metadata(object,"xml:m0:population"))
                            ||!body.get("cut").equals(engine.metadata(object,"xml:m0:population-cut"))
                            ||!engine.metadata(object,"xml:m0:population-proof").matches("[0-9a-f]{64}")){valid=false;break;}
                    actual.add(object.name());
                }
                if(!valid||!actual.equals(new TreeSet<>(members.stream().map(String.class::cast).toList()))){valid=false;break;}
                all.addAll(actual);
            }
            var actual=engine.system().state().allObjects().stream().filter(o->o.cls().isSubClassifierOf(type)).map(o->o.name()).collect(java.util.stream.Collectors.toSet());
            if(valid&&all.equals(actual))return true;
        }catch(RuntimeException invalid){/* Unrecognized or stale proof is not completeness. */}
        return false;
    }
}

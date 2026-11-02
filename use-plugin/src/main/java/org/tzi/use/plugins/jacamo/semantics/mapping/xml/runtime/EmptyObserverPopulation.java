package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;

/** Known-empty derived populations need an actual complete source/table certificate.
 * Metadata lives on existing M0 objects and participates in their native savepoint. */
public final class EmptyObserverPopulation {
 private static final String KEY="xml:observer:empty-populations";
 private EmptyObserverPopulation(){}
 static void record(Port port,String program,String epoch,String cut,Map<String,Object> populations){
  for(var carrier:port.objects())if(program.equals(port.metadata(carrier,"xml:m0:program"))){
   String proof=port.metadata(carrier,"xml:m0:population-proof");
   boolean current=epoch.equals(port.metadata(carrier,"xml:m0:epoch"))&&cut.equals(port.metadata(carrier,"xml:m0:cut"))
     &&cut.equals(port.metadata(carrier,"xml:m0:population-cut"))&&"COMPLETE".equals(port.metadata(carrier,"xml:m0:quality"))
     &&"COMPLETE".equals(port.metadata(carrier,"xml:m0:population"))&&proof.matches("[0-9a-f]{64}");
   var certificate=new TreeMap<String,Object>();
   if(current&&!populations.isEmpty()){
    certificate.putAll(Map.of("version","EXACT_EMPTY_OBSERVER_POPULATION/1.0","program",program,"epoch",epoch,"cut",cut,"sourceProof",proof,"populations",populations));
    certificate.put("hash",SemanticJson.hash(certificate));
   }
   port.metadata(carrier,KEY,new String(CanonicalJson.encode(certificate),StandardCharsets.UTF_8));
  }
 }
 public static boolean complete(org.tzi.use.uml.mm.MClassifier cls,NativeRuntimeMutationEngine engine){
  if(cls.getAnnotation("DeclarativeObserverView")==null||!engine.canonicalProgram().xmlPlan().inputs().xml().sha256().equals(cls.getAnnotationValue("DeclarativeObserverView","mappingHash")))return false;
  if(engine.system().state().allObjects().stream().anyMatch(o->o.cls().isSubClassifierOf(cls)))return false;
  for(var carrier:engine.system().state().allObjects()){
   if(!engine.canonicalProgramHash().equals(engine.metadata(carrier,"xml:m0:program")))continue;
   String encoded=engine.metadata(carrier,KEY);if(encoded.isBlank())continue;
   var proof=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(encoded.getBytes(StandardCharsets.UTF_8))));
   Object hash=proof.remove("hash");if(!SemanticJson.hash(proof).equals(hash))continue;
   if(!"EXACT_EMPTY_OBSERVER_POPULATION/1.0".equals(proof.get("version"))||!engine.canonicalProgramHash().equals(proof.get("program"))
     ||!engine.metadata(carrier,"xml:m0:epoch").equals(proof.get("epoch"))||!engine.metadata(carrier,"xml:m0:cut").equals(proof.get("cut"))
     ||!engine.metadata(carrier,"xml:m0:population-cut").equals(proof.get("cut"))||!engine.metadata(carrier,"xml:m0:population-proof").equals(proof.get("sourceProof"))
     ||!"COMPLETE".equals(engine.metadata(carrier,"xml:m0:population")))continue;
   var populations=CanonicalJson.object(proof.get("populations"));if(!(populations.get(cls.name()) instanceof Map<?,?> raw))continue;
   var population=CanonicalJson.object(raw);var declaration=engine.canonicalProgram().xmlPlan().inputs().specification().requireId((String)population.get("project"));
   var view=engine.canonicalProgram().xmlPlan().inputs().specification().requireId(declaration.attr("viewRef"));
   if(declaration.name().equals("project")&&declaration.attr("tableRef").equals(population.get("table"))&&view.attr("name").equals(cls.name())&&"0".equals(population.get("rowCount")))return true;
  }
  return false;
 }
}

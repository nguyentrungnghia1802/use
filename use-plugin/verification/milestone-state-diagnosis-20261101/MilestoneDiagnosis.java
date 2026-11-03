import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;

class MilestoneDiagnosis {
 static String hash(Object x){return org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(x);}
 static Map<String,Object> rows(Cut cut){
  var rows=new TreeMap<String,Object>();for(var r:cut.evaluations())if(!r.kind().equals("CHECKPOINT_CHECK")){
   String key=r.kind()+" / "+r.element()+" / "+r.player()+" / "+r.context()+" / "+r.occurrence();
   rows.put(key,Map.of("status",r.status(),"raw",r.raw(),"quality",r.quality(),"required",r.required(),"evidence",r.evidence()));
  }return rows;
 }
 public static void main(String[] args)throws Exception {
  var base=Path.of(args[0]);byte[] raw;try(var in=new java.util.zip.GZIPInputStream(Files.newInputStream(base.resolve("exact-results.json.gz")))){raw=in.readAllBytes();}
  System.out.println("DIAGNOSIS_PLUGIN_CODE_SOURCE="+ObservedConformancePanel.class.getProtectionDomain().getCodeSource().getLocation());
  var samples=(List<?>)CanonicalJson.decode(raw,128*1024*1024,64,CanonicalJson.DEFAULT_MAX_STRING);
  var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "frozen diagnosis";}});
  List<Cut> cuts=List.of();var ordinals=new HashMap<String,Object>();
  for(var sample:samples){var value=CanonicalJson.object(sample);var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(value.get("result")));
   if(Boolean.TRUE.equals(value.get("settled"))){
    var method=ObservedConformancePresenter.class.getDeclaredMethod("cut",RuntimeVerificationResult.class,VerificationSnapshot.StateImage.class,String.class,String.class,Map.class,Map.class);method.setAccessible(true);
    var cut=(Cut)method.invoke(null,result,null,"Final settled evidence",result.resultHash(),new HashMap<String,String>(),new HashMap<String,String>());
    var copy=new ArrayList<Cut>(cuts);copy.add(cut);cuts=List.copyOf(copy);ordinals.put(cut.id(),value.get("ordinal"));
   }else{
    var request=CanonicalJson.object(value.get("request"));var identity=CanonicalJson.object(request.get("identity"));
    var view=new VerifiedCheckpointService.View("VERIFYING",identity.get("token").toString(),request.get("checkpointId").toString(),"","",result.stateHash(),result.resultHash(),Set.of(),false,false,request);
    cuts=presenter.captureCheckpoint(new VerificationSnapshot(result.stateVersion(),result,null),view,Map.of());ordinals.put(cuts.getLast().id(),value.get("ordinal"));
   }
  }
  var report=new ArrayList<Object>();int execution=0;
  for(var choice:ExecutionScopeViews.admittedChoices(cuts).entrySet()){
   execution++;var prepared=new PreparationCache().prepare(cuts,choice.getKey(),true,true);var milestones=new ArrayList<Object>();Cut prior=null;int number=0;
   for(var cut:prepared.milestones()){
    number++;var changes=new TreeMap<String,Object>();var before=prior==null?Map.<String,Object>of():rows(prior);var after=rows(cut);var keys=new TreeSet<String>(before.keySet());keys.addAll(after.keySet());
    for(var key:keys)if(!Objects.equals(before.get(key),after.get(key)))changes.put(key,Map.of("before",before.getOrDefault(key,Map.of()),"after",after.getOrDefault(key,Map.of())));
    boolean same=prior!=null&&prior.businessState().equals(cut.businessState());
    var projection=ExecutionScopeViews.object(cut.visualEvidence().get("presentationState"));
    var row=new LinkedHashMap<String,Object>();row.put("milestone",number);row.put("id",cut.id());row.put("ordinal",ordinals.get(cut.id()));row.put("cslHash",hash(cut.businessState()));row.put("scopedObjectsHash",hash(projection.getOrDefault("objects",Map.of())));row.put("scopedLinksHash",hash(projection.getOrDefault("links",List.of())));row.put("nativeMSystemHash",cut.visualEvidence().getOrDefault("verifiedCheckpointUseHash","settled"));row.put("sameCslAsPrevious",same);row.put("businessState",cut.businessState());row.put("changedRows",changes);row.put("evaluations",rows(cut));
    milestones.add(row);System.out.println("EXEC "+execution+" M"+number+" ordinal="+ordinals.get(cut.id())+" sameCSL="+same+" changedRows="+changes.size());
    if(same)for(var entry:changes.entrySet()){var c=CanonicalJson.object(entry.getValue());var a=CanonicalJson.object(c.get("before"));var b=CanonicalJson.object(c.get("after"));if(!Objects.equals(a.get("status"),b.get("status"))||!Objects.equals(a.get("raw"),b.get("raw")))System.out.println("  "+entry.getKey()+": "+a.get("status")+" -> "+b.get("status")+" raw "+a.get("raw")+" -> "+b.get("raw"));}
    prior=cut;
   }
   report.add(Map.of("execution",execution,"scope",choice.getKey(),"label",choice.getValue(),"auditCount",prepared.audit().size(),"milestones",milestones));
  }
  Files.write(base.resolve("exact-milestone-comparison.json"),CanonicalJson.encode(report));
 }
}

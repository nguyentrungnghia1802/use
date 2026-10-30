import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;

public class PopulationAudit {
 public static void main(String[] args)throws Exception {
  Path recording=Path.of(args[0]);var results=new LinkedHashMap<String,RuntimeVerificationResult>();var ordinals=new HashMap<String,Long>();
  try(var lines=Files.lines(recording.resolve("runtime.jsonl"))){lines.forEach(line->{var entry=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));var r=RuntimeVerificationResult.fromMap(CanonicalJson.object(entry.get("result")));String key=r.stateVersion()+":"+r.eventId()+":"+r.checkpointId();results.put(key,r);ordinals.put(r.resultHash(),((Number)entry.get("ordinal")).longValue());});}
  var all=List.copyOf(results.values());var latest=all.getLast();
  var facade=new JaCaMoFacade(){public String status(){return "read-only recorded audit";}public List<RuntimeVerificationResult> runtimeVerificationHistory(){return all;}public RuntimeVerificationResult runtimeVerificationResult(){return latest;}};
  var cuts=new ObservedConformancePresenter(facade).capture();var milestones=BusinessTimeline.summarize(cuts);var numbers=BusinessTimeline.milestoneNumbers(cuts,milestones);
  var transitions=new ArrayList<Object>();String last="";int capped=0;var firstPoints=new ArrayList<Object>();var previous=Set.<String>of();var statusCounts=new TreeMap<String,Integer>();
  for(var cut:cuts){
   var population=cut.evaluations().stream().filter(r->r.element().endsWith("AuctioneerPopulation")).toList();
   var agg=population.stream().filter(r->r.kind().equals("INVARIANT")).findFirst();
   if(agg.isPresent()){
    String status=agg.get().status();statusCounts.merge(status,1,Integer::sum);
    if(!last.equals(status)&&capped++<14){transitions.add(Map.of("cut",cut.id(),"milestone",numbers.getOrDefault(cut.id(),0),"status",status,"rows",population.stream().map(r->Map.of("kind",r.kind(),"quality",r.quality())).toList()));last=status;}
   }
   var active=new HashSet<String>();for(var row:population)if(Set.of("FAIL","ERROR").contains(row.status())){
    String key=row.kind()+":"+row.element()+":"+row.context()+":"+row.occurrence()+":"+row.status();active.add(key);
    if(!previous.contains(key)&&firstPoints.size()<10)firstPoints.add(Map.of("milestone",numbers.getOrDefault(cut.id(),0),"cut",cut.id(),"kind",row.kind(),"quality",row.quality()));
   }previous=active;
  }
  var decoder=new SemanticProgramManifest().decode(Files.readAllBytes(recording.resolve("program.json")));var program=decoder.program();
  var goals=new LiveGoalSummary(program.goalModel()).rows(cuts.getLast().evaluations());
  var skill=goals.stream().filter(r->r.label().startsWith("auction_capabilities::")).map(r->Map.of("definition",r.id(),"raw",r.originalStatus(),"certified",r.status(),"role",r.resultRole(),"counts",r.counts(),"instances",r.instances().stream().map(i->Map.of("player",i.player(),"context",i.context(),"status",i.status(),"quality",i.quality(),"raw",i.originalStatus())).toList())).toList();
  var built=new org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline().buildCanonicalProgram(decoder.source(),org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.AUTO,program);
  var system=built.nativeResult().state().system();var index=built.nativeResult().state().semanticObjectIndex();
  var api=org.tzi.use.api.UseSystemApi.create(system,false);var group=api.createObject("auctionGroup","auditGroup");index.put("audit-group",group);
  index.metadata(group,"xml:m0:program",program.hash());index.metadata(group,"xml:m0:quality","COMPLETE");index.metadata(group,"xml:m0:cut","audit-cut");index.metadata(group,"xml:m0:roles-cut","audit-cut");index.metadata(group,"xml:m0:roles-quality","INCOMPLETE");index.metadata(group,"xml:m0:population-authority","CARTAGO_BOARD_INVENTORY_V1");
  var engine=new NativeRuntimeMutationEngine(system,index,new org.tzi.use.plugins.jacamo.codegrounded.runtime.CodeGroundedRuntimeRuleRegistry(),program);
  var inv=system.model().classInvariants().stream().filter(i->i.qualifiedName().equals("auctionGroup::AuctioneerPopulation")).findFirst().orElseThrow();
  var visitor=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.dependencyVisitor();inv.bodyExpression().processWithVisitor(visitor);
  var resolver=new ConstraintCoverageResolver();var quality=resolver.unavailableSelf(inv.bodyExpression(),engine,group,Map.of("cartago",Completeness.COMPLETE),false);
  var prepared=new SemanticInvariantEvaluator().prepareScoped("audit",inv,engine,Map.of("cartago",Completeness.COMPLETE),false,Set.of(),"");var evaluated=SemanticInvariantEvaluator.evaluateScoped(prepared,system.state());
  var expanded=new org.tzi.use.api.UseModelApi(system.model()).createInvariant("AuditExpandedPopulation","auctionGroup","self.roles->select(r | r.oclIsKindOf(auctioneer))->size() = 1",false);
  var expandedReason=resolver.unavailableSelf(expanded.bodyExpression(),engine,group,Map.of("cartago",Completeness.COMPLETE),false);
  var probe=Map.of("expandedEquivalentCoverageReason",expandedReason,"rolesQuality",engine.metadata(group,"xml:m0:roles-quality"),"coverageReason",quality,"directAttributeReads",visitor.attributeReads().stream().map(r->r.attribute().qualifiedName()).toList(),"navigationReadCount",visitor.navigationReads().size(),"bodyVerdict",evaluated.stream().filter(r->r.kind().equals("INVARIANT_BODY")).findFirst().orElseThrow().verdict().name(),"derivedAttribute",system.model().getClass("auctionGroup").attribute("auctioneer",true).getDeriveExpression().toString());
  var output=Map.of("recording",recording.toString(),"auditCuts",cuts.size(),"milestones",milestones.size(),"populationStatusCounts",statusCounts,"populationTransitions",transitions,"failurePoints",firstPoints,"capabilitySummary",skill,"derivedCoverageProbe",probe,"finalSelectedRoot",goals.stream().filter(r->r.id().equals("O_root")).map(r->Map.of("raw",r.originalStatus(),"certified",r.status())).toList());
  Files.write(Path.of(args[1]),CanonicalJson.encode(output));System.out.println("AUDIT cuts="+cuts.size()+" milestones="+milestones.size()+" population="+statusCounts);System.out.println("DERIVED_COVERAGE_PROBE "+probe);
 }
}

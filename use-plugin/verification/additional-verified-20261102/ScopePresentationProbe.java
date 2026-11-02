import java.util.*;
import java.nio.file.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;

/** Frozen presentation diagnostic, not a claim that this source passed strict admission. */
class ScopePresentationProbe {
 public static void main(String[] args)throws Exception{
  var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(Path.of(args[0])))));
  var method=ObservedConformancePresenter.class.getDeclaredMethod("cut",RuntimeVerificationResult.class,VerificationSnapshot.StateImage.class,String.class,String.class,Map.class,Map.class);method.setAccessible(true);
  var cut=(Cut)method.invoke(null,result,null,"Frozen observed control",result.resultHash(),new HashMap<String,String>(),new HashMap<String,String>());
  var choices=ExecutionScopeViews.choices(List.of(cut));var prepared=new PreparationCache().prepare(List.of(cut),"",true,true);
  var contexts=new ArrayList<Object>();
  for(var context:ExecutionScopeViews.contexts(cut))contexts.add(Map.of("context",context.get("context"),"scopeView",context.get("scopeView"),"executionConstraints",context.getOrDefault("executionConstraints",List.of())));
  var report=Map.of("pluginCodeSource",ObservedConformancePanel.class.getProtectionDomain().getCodeSource().getLocation().toString(),"nativeResultHashVerified",result.resultHash(),"availableContexts",choices,"checkpointExecutionLabels",prepared.labels(),"checkpointAuditStates",prepared.audit().size(),"checkpointMilestones",prepared.milestones().size(),"contexts",contexts);
  Files.write(Path.of(args[1]),CanonicalJson.encode(report));
  System.out.println("FROZEN_PRESENTATION availableContexts="+choices.size()+" admittedExecutions="+prepared.labels().size()+" displayedStates="+prepared.milestones().size());
 }
}

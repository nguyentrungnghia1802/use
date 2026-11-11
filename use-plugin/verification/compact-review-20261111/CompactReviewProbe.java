import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.compact.*;
import org.tzi.use.semantic.engine.frontend.*;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;

/** Read-only feature review: FAIL means an unmet acceptance requirement, not an expected domain verdict. */
public class CompactReviewProbe {
  static int failures;
  static void check(String name, boolean ok, Object observed) {
    System.out.println((ok ? "PASS " : "FAIL ")+name+" observed="+observed);
    if(!ok) failures++;
  }
  static void rejects(String name, Runnable body) {
    try {body.run();check(name,false,"accepted unresolved source");}
    catch(IllegalArgumentException expected) {check(name,true,expected.getMessage());}
  }
  public static void main(String[] args) throws Exception {
    rejects("missing_agent_rejected",()->new SourceSymbolCatalog().resolve(SourceReference.parse("agent:does_not_exist")));
    rejects("missing_plan_rejected",()->new SourceSymbolCatalog().resolve(SourceReference.parse("asl:missing.asl/plan:unknown/step:attempt[0..1]")));
    var csl=new CslFrontend().compile("csl v4.0 Delivery { entity Parcel { attribute value : Integer; } }","review.csl").requireModel();
    var goals=new IStarFrontend().compile("istar DeliveryGoals { role Worker { goal dispatch : Achieve task pack > dispatch task send > dispatch } }","review.istar").requireModel();
    var source=new SourceSymbolCatalog();
    source.register(SourceSymbol.artifact("sample.ParcelArtifact","source-digest"));
    source.register(SourceSymbol.property("sample.ParcelArtifact","value"));
    source.register(SourceSymbol.agent("worker"));
    source.registerPlan("worker.asl","+!dispatch","dispatch",2);
    String xml="""
      <mapping id="review" xmlns="urn:use:jacamo-mapping:3" version="3.0">
        <csl>
          <map source="artifact:sample.ParcelArtifact" target="Parcel"/>
          <map source="artifact:sample.ParcelArtifact/property:value" target="Parcel.value"/>
        </csl>
        <istar>
          <map source="agent:worker" target="Worker"/>
          <map source="asl:worker.asl/plan:dispatch/step:pack[0..1]" target="Worker.pack"/>
          <map source="asl:worker.asl/plan:dispatch/step:send[1..2]" target="Worker.send"/>
        </istar>
      </mapping>
      """;
    var spec=CompactMappingReader.read(xml,"review.xml");
    var plan=ObservationPlanCompiler.compile(spec,source,NativeTargetCatalog.fromModels(csl,goals));
    var a=new PhysicalCutFact.ArtifactInstance("first","sample.ParcelArtifact","workspace-a",Map.of("value",1),Map.of());
    var b=new PhysicalCutFact.ArtifactInstance("second","sample.ParcelArtifact","workspace-a",Map.of("value",2),Map.of());
    var cut=new PhysicalCutFact("cut-two",1,List.of(a,b),List.of(),List.of(),List.of());
    var tx=RuntimeInstanceLinker.link(plan,cut);
    check("both_instances_projected",tx.objectMutations().size()==2,tx.objectMutations().size());
    var sameNameOtherWorkspace=new PhysicalCutFact.ArtifactInstance("first","sample.ParcelArtifact","workspace-b",Map.of("value",3),Map.of());
    var other=RuntimeInstanceLinker.link(plan,new PhysicalCutFact("cut-other",2,List.of(sameNameOtherWorkspace),List.of(),List.of(),List.of()));
    check("workspace_in_identity",!tx.objectMutations().getFirst().objectName().equals(other.objectMutations().getFirst().objectName()),tx.objectMutations().getFirst().objectName()+" / "+other.objectMutations().getFirst().objectName());
    var model=USECompiler.compileSpecification(new ByteArrayInputStream("model Delivery class Parcel attributes value : Integer end".getBytes(StandardCharsets.UTF_8)),"review.use",new PrintWriter(new StringWriter()),new ModelFactory());
    var sys=new MSystem(Objects.requireNonNull(model));
    check("task_rules_exist",!plan.checkpointRules().isEmpty(),plan.checkpointRules().size());
    var assessment=NativeVerificationCoordinator.evaluate(plan,sys,new PhysicalCutFact("empty-cut",3,List.of(),List.of(),List.of(),List.of()));
    check("no_approval_without_task_evidence",assessment.recommendedAction()!=VerifiedCheckpointProtocol.Action.APPROVE,assessment.recommendedAction()+" "+assessment.checkpointChecks());
    var receipt=new PhysicalCutFact.StepReceipt("dispatch","pack","pack",0,null);
    check("missing_status_not_success",!receipt.status().equals("SUCCEEDED"),receipt.status());
    var created=sys.state().createObject(model.getClass("Parcel"),"retained");
    var dispose=new RuntimeInstanceLinker.ProjectionTransaction("dispose",List.of(),List.of(),List.of("retained"));
    var applied=AuthoritativeMSystemProjector.project(sys,dispose);
    applied.rollback(sys);
    check("rollback_restores_disposed_object",sys.state().objectByName("retained")!=null,sys.state().objectByName("retained"));
    check("invalid_boolean_not_false",RuntimeInstanceLinker.convertValue("not-a-boolean",CslBinding.TypeConversion.TO_BOOLEAN).isUndefined(),RuntimeInstanceLinker.convertValue("not-a-boolean",CslBinding.TypeConversion.TO_BOOLEAN));
    System.out.println("REVIEW_FAILURES="+failures);
  }
}

package org.jacamo.bridge.adapter;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import jason.asSemantics.TransitionSystem;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;
/** Historical case capture policy; listener/correlation/cleanup use the neutral provider. */
public final class AuctionStepObservation extends JasonStepObservation {
    public static final String PROPERTY="jacamo.bridge.auction.step-markers";
    private static List<Capture> captures() {
        var all=List.of("Sch","Id","S","ArtId","AId");var result=new ArrayList<Capture>();
        for(String task:List.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2"))
            result.add(new Capture(task,all,task.startsWith("P_")?"AId":"ArtId",AuctionArtifactStateProbe.CLASS,"Sch",task.equals("A_start_t1"),task.equals("A_start_t2")?List.of(new Input("service","S","java.lang.String")):List.of()));
        return List.copyOf(result);
    }
    public AuctionStepObservation(TransitionSystem ts,BridgeEntityId agent,String run,long generation,String revision,AgentProgramSemantic source,Path root,Consumer<VerificationObservation> sink,Consumer<String> gap)throws Exception {
        super(ts,agent,run,generation,revision,source,root,sink,gap,captures(),"AUCTION_STEP_VALUES/1.0",(bindings,inputs)->{
            if(inputs.get("service") instanceof Map<?,?> raw){var legacy=new LinkedHashMap<>(org.jacamo.bridge.contract.CanonicalJson.object(raw));legacy.put("authority","JACA_PUBLIC_TERM_TO_OBJECT_START_VARIABLE_V1");bindings.put("serviceInput",Map.copyOf(legacy));}
        },AuctionStagingInstrumentation::prepare);
    }
}

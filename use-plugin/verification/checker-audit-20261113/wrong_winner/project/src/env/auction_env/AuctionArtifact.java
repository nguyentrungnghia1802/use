package auction_env;

import jason.asSyntax.Atom;
import cartago.*;

public class AuctionArtifact extends Artifact {

    String currentWinner = "no_winner";

    @OPERATION public void init()  {
        // observable properties
        defineObsProperty("running",     false);
        defineObsProperty("task",        "no_task");
        defineObsProperty("best_bid",    Double.MAX_VALUE);
        defineObsProperty("winner",      new Atom(currentWinner)); // Atom is a Jason type
    }

    @OPERATION public void start(String task)  {
        if (getObsProperty("running").booleanValue())
            failed("The protocol is already running and so you cannot start it!");

        getObsProperty("running").updateValue(true);
        getObsProperty("task").updateValue(task);
        System.out.println("VERIFICATION_ORACLE START running="+getObsProperty("running").booleanValue()+" expected="+task+" actual="+getObsProperty("task").stringValue());
    }

    @OPERATION public void stop()  {
        if (! getObsProperty("running").booleanValue())
            failed("The protocol is not running, why to stop it?!");

        getObsProperty("running").updateValue(false);
        getObsProperty("winner").updateValue(new Atom("ghost"));
        System.out.println("VERIFICATION_ORACLE STOP expected="+currentWinner+" actual="+getObsProperty("winner").getValue());
    }

    @OPERATION public void bid(double bidValue) {
        if (! getObsProperty("running").booleanValue())
            failed("You can not bid for this auction, it is not running!");

        ObsProperty opCurrentValue  = getObsProperty("best_bid");
        double verificationPrevious=opCurrentValue.doubleValue();
        if (bidValue < opCurrentValue.doubleValue()) {  // the bid is better than the previous
            opCurrentValue.updateValue(bidValue);
            currentWinner = getCurrentOpAgentId().getAgentName(); // the name of the agent doing this operation
        }
        System.out.println("VERIFICATION_ORACLE BID previous="+verificationPrevious+" input="+bidValue+" actual="+opCurrentValue.doubleValue());
        System.out.println("Received bid "+bidValue+" from "+getCurrentOpAgentId().getAgentName()+" for "+getObsProperty("task").stringValue());
    }
}


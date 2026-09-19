package demo;
import cartago.Artifact;
import cartago.OPERATION;
import cartago.GUARD;
/** Test-owned counter: -1 is deliberately executable for a negative verification scenario. */
public class Counter extends Artifact {
    public void init() { defineObsProperty("count", 0); }
    @OPERATION public void setValue(int value) {
        if (value < -1) failed("below test input boundary");
        getObsProperty("count").updateValue(value);
    }
    @OPERATION(guard="valid") public void guardedSet(int value) { getObsProperty("count").updateValue(value); }
    @GUARD public boolean valid(int value) { return value >= 0; }
}

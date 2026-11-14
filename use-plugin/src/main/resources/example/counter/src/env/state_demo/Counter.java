package state_demo;
import cartago.*;
public class Counter extends Artifact {
    @OPERATION public void init(){defineObsProperty("value",0);}
    @OPERATION public void set(int value){getObsProperty("value").updateValue(value);}
}

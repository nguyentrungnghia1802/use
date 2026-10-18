package demo.counter;

import cartago.Artifact;
import cartago.OPERATION;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real observable example. Step=2 is an explicit postcondition fault fixture. */
public class Counter extends Artifact {
    private int step;
    protected void init() {
        step=Integer.getInteger("counter.demo.step",1);
        if(step!=1&&step!=2)throw new IllegalArgumentException("DEMO_STEP_MUST_BE_ONE_OR_TWO");
        defineObsProperty("p",0);
    }
    @OPERATION void awaitRelease() throws Exception {
        String ready=System.getProperty("counter.demo.ready","");
        if(!ready.isBlank())Files.writeString(Path.of(ready),"actual artifact ready");
        String release=System.getProperty("counter.demo.release","");
        if(!release.isBlank())while(!Files.exists(Path.of(release)))await_time(25);
    }
    @OPERATION void increment() {
        getObsProperty("p").updateValue(getObsProperty("p").intValue()+step);
        signal("changed",getObsProperty("p").intValue());
    }
    @OPERATION void set(int value) {getObsProperty("p").updateValue(value);signal("changed",value);}
    @OPERATION void awaitStopMas() throws Exception {
        Path stop=Path.of(System.getProperty("counter.demo.stopMas","stop-mas.flag"));
        while(!Files.exists(stop))await_time(100);
    }
}

package org.tzi.use.plugins.jacamo.semantics;
public class LiveRegressionCounterArtifact extends cartago.Artifact {
    protected void init(){defineObsProperty("p",0);}
    @cartago.OPERATION void gate() throws Exception {
        var boot=java.nio.file.Path.of(System.getProperty("use.fix.bootReady"));
        var release=java.nio.file.Path.of(System.getProperty("use.fix.release"));
        java.nio.file.Files.writeString(boot,"waiting");
        while(!java.nio.file.Files.exists(release))await_time(25);
    }
    @cartago.OPERATION void increment(){getObsProperty("p").updateValue(getObsProperty("p").intValue()+1);signal("audit_changed",getObsProperty("p").intValue());}
    @cartago.OPERATION void set(int value){getObsProperty("p").updateValue(value);signal("audit_changed",value);}
    @cartago.OPERATION void awaitStopMas() throws Exception {
        var requested=java.nio.file.Path.of(System.getProperty("use.fix.stopMas"));
        while(!java.nio.file.Files.exists(requested))await_time(25);
    }
}

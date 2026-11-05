import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Loads packaged classes without starting or mutating a MAS. */
public class PackagedIdentityProbe {
    public static void main(String[] args) throws Exception {
        var urls=Arrays.stream(args[0].split(java.util.regex.Pattern.quote(System.getProperty("path.separator"))))
                .map(Path::of).map(p->{try{return p.toUri().toURL();}catch(Exception e){throw new IllegalStateException(e);}}).toArray(URL[]::new);
        try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader())) {
            for(String name:List.of("jacamo.infra.JaCaMoLauncher","org.jacamo.checkpoint.runtime.ExecutionBarrier",
                    "org.jacamo.bridge.contract.JasonAgentInventory",
                    "org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.MappedPopulationInventory",
                    "org.tzi.use.plugins.jacamo.ui.use4goal.conformance.VerificationDataCoverage",
                    "jason.infra.local.LocalAgArch","jason.asSemantics.TransitionSystem","cartago.Artifact","npl.NormInstance","org.jacamo.bridge.adapter.VerifiedCheckpointBridge",
                    "org.tzi.use.plugins.jacamo.semantics.runtime.GoalViewRealizations",
                    "org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.NplPopulationCut",
                    "org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter",
                    "org.vnu.sme.goal.verify.conformance.semantics.TaskMarking")) {
                var type=Class.forName(name,false,loader);
                var path=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
                System.out.println(name+"\t"+path+"\t"+hash);
            }
        }
    }
}

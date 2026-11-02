import java.nio.file.Path;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Installed loader smoke only; no MAS, verification session, or desktop GUI. */
public class InstalledCheckpointS01Smoke {
    public static void main(String[] args) throws Exception {
        Path install=Path.of(args[0]).toRealPath();
        org.tzi.use.config.Options.processArgs(new String[]{"-H="+install,"-nogui","-nr"});
        MainPluginRuntime.run(install.resolve("lib/plugins"));
        var descriptor=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo");
        if(descriptor==null)throw new AssertionError("PLUGIN_NOT_DISCOVERED");
        var loader=descriptor.getPluginClassLoader();
        Path expected=install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar").toRealPath();
        var type=loader.loadClass("org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade");
        Path actual=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if(!expected.equals(actual))throw new AssertionError("STALE_PLUGIN:"+actual);
        if(loader.loadClass("org.tzi.use.uml.sys.MSystem")!=org.tzi.use.uml.sys.MSystem.class)
            throw new AssertionError("DUPLICATE_MSYSTEM");
        for(String name:new String[]{"jacamo.infra.JaCaMoLauncher","cartago.Artifact"}) {
            try {loader.loadClass(name);throw new AssertionError("PRODUCER_CLASS_IN_USE:"+name);}
            catch(ClassNotFoundException expectedMissing) { }
        }
        System.out.println("INSTALLED_PLUGIN="+actual);
        System.out.println("S01_INSTALLED_LOADER_SMOKE_PASS; strict checkpoints remain unsupported");
    }
}

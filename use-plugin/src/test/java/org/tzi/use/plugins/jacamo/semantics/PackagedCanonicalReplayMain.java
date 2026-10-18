package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.Path;
import java.util.*;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Isolated driver only: no plugin, engine, bridge, framework or JUnit linkage. */
public final class PackagedCanonicalReplayMain {
    private PackagedCanonicalReplayMain() { }
    private static Object get(Object target,String method) throws Exception {return target.getClass().getMethod(method).invoke(target);}
    public static void main(String[] args) throws Exception {
        Path install=Path.of(args[0]).toRealPath(),jar=install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar").toRealPath();
        MainPluginRuntime.run(install.resolve("lib/plugins"));
        var descriptor=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo");
        if(descriptor==null)throw new AssertionError("Plugin not discovered");
        var loader=descriptor.getPluginClassLoader();
        for(String name:List.of("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay",
                "org.vnu.sme.goal.dsl.csl.mm.CslModel","org.vnu.sme.goal.dsl.istar.mm.GoalModel",
                "org.vnu.sme.goal.verify.conformance.semantics.GoalMarking",
                "org.vnu.sme.goal.verify.conformance.semantics.TaskMarking",
                "org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation")) {
            Class<?> type=loader.loadClass(name);
            if(!jar.equals(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()))
                throw new AssertionError("Wrong installed engine source: "+type);
        }
        if(loader.loadClass("org.tzi.use.uml.sys.MSystem")!=org.tzi.use.uml.sys.MSystem.class)
            throw new AssertionError("Duplicate host system class");
        try {loader.loadClass("cartago.Artifact");throw new AssertionError("Framework leaked into USE");}catch(ClassNotFoundException expected) { }
        Class<?> replayType=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay");
        Object replay=replayType.getConstructor().newInstance();
        Object report=replayType.getMethod("replay",Path.class).invoke(replay,Path.of(args[1]));
        if(!Boolean.TRUE.equals(get(report,"complete"))) {
            // Keep a test-owned comparison outside the immutable recording.
            Class<?> codec=loader.loadClass("org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest");
            Object decoded=codec.getMethod("decode",byte[].class).invoke(codec.getConstructor().newInstance(),
                    (Object)java.nio.file.Files.readAllBytes(Path.of(args[1]).resolve("program.json")));
            Class<?> pipeline=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline");
            Class<?> mode=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode");
            Object auto=Arrays.stream(mode.getEnumConstants()).filter(v->v.toString().equals("AUTO")).findFirst().orElseThrow();
            Object built=pipeline.getMethod("buildCanonicalProgram",loader.loadClass("org.jacamo.bridge.contract.ModelSnapshot"),mode,
                    loader.loadClass("org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram")).invoke(pipeline.getConstructor().newInstance(),get(decoded,"source"),auto,get(decoded,"program"));
            java.nio.file.Files.writeString(install.resolve("regenerated-model.use"),(String)get(get(get(built,"nativeResult"),"export"),"useText"));
            throw new AssertionError(get(report,"diagnostics"));
        }
        if(!args[2].equals(get(report,"finalStateHash"))||!args[3].equals(get(report,"finalResultHash")))
            throw new AssertionError("Reactor/installed replay hashes differ");
        var host=new Session();
        Class<?> controllerType=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController");
        Object controller=controllerType.getConstructor(Session.class).newInstance(host);
        try {
            controllerType.getMethod("open",Path.class).invoke(controller,Path.of(args[1]));
            while(!get(get(controller,"status"),"step").equals(get(get(controller,"status"),"total")))controllerType.getMethod("next").invoke(controller);
            Object result=get(get(controller,"verificationSnapshot"),"result"),semantic=get(result,"semantic");
            if(!args[3].equals(get(result,"resultHash")))throw new AssertionError("Step result mismatch");
            if(!((Map<?,?>)get(semantic,"requirements")).isEmpty())throw new AssertionError("Local recurrence authority");
            var observation=(Map<?,?>)get(semantic,"observation");var core=(Map<?,?>)observation.get("originalEngine");
            if(core==null||!"PINNED_NATIVE_CUT".equals(core.get("evaluated")))throw new AssertionError("Original engine not called");
            var contexts=(List<?>)core.get("contexts");if(contexts.isEmpty())throw new AssertionError("No contextual markings");
            if(!host.system().isReadOnly())throw new AssertionError("Replay state writable");
            var expression=org.tzi.use.parser.ocl.OCLCompiler.compileExpression(host.system().model(),host.system().state(),
                    "Counter.allInstances()->one(c | c.p = 2)","installed-manual",new java.io.PrintWriter(System.err),host.system().varBindings());
            if(expression==null||!"true".equals(new org.tzi.use.uml.ocl.expr.Evaluator().eval(expression,host.system().state(),host.system().state(),host.system().varBindings()).toString()))
                throw new AssertionError("Native manual OCL failed");
            controllerType.getMethod("previous").invoke(controller);controllerType.getMethod("reset").invoke(controller);
        } finally {((AutoCloseable)controller).close();}
        if(host.hasSystem())throw new AssertionError("Offline owner leaked");
        System.out.println("ISOLATED_ORIGINAL_ENGINE_REPLAY_PASS state="+args[2]+" result="+args[3]);
    }
}

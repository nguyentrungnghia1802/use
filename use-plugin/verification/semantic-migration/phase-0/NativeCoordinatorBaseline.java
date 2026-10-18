import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.api.*;
import org.tzi.use.uml.sys.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;

/** Synthetic timing probe of existing native coordinator; not a live MAS test. */
public final class NativeCoordinatorBaseline {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        UseModelApi model = new UseModelApi("NativeCoordinatorBaseline");
        model.createClass("Counter", false);
        model.createAttribute("Counter", "p", "Integer");
        model.createInvariant("Nonnegative", "Counter", "self.p >= 0", false);
        MSystem system = new MSystem(model.getModel());
        UseSystemApi api = UseSystemApi.create(system, false);
        MObject object = api.createObject("Counter", "counter");
        api.setAttributeValue("counter", "p", "0");
        NativeObjectBindings bindings = new NativeObjectBindings();
        bindings.put("baseline:counter", object);
        NativeRuntimeMutationEngine engine = new NativeRuntimeMutationEngine(
                system, bindings, new CodeGroundedRuntimeRuleRegistry());
        List<Long> delays = Collections.synchronizedList(new ArrayList<>());
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "phase0-heartbeat"); t.setDaemon(true); return t;
        });
        long started = System.nanoTime();
        try (RuntimeVerificationCoordinator owner = new RuntimeVerificationCoordinator(
                engine, "phase0-native-probe", 1, "baseline", root.resolve("native-probe-journal"),
                512, 64L * 1024 * 1024)) {
            timer.scheduleAtFixedRate(() -> {
                long submitted = System.nanoTime();
                SwingUtilities.invokeLater(() -> delays.add(System.nanoTime() - submitted));
            }, 0, 50, TimeUnit.MILLISECONDS);
            for (int i = 1; i <= 200; i++) {
                final int value = i;
                owner.transaction("BENCHMARK", "probe:" + i, "synthetic", i, Instant.now(),
                        Map.of("synthetic", true, "value", i), true, () -> {
                            api.setAttributeValue("counter", "p", Integer.toString(value));
                            return new RuntimeVerificationCoordinator.Mutation<>(value, true);
                        });
            }
            timer.shutdown();
            if (!timer.awaitTermination(5, TimeUnit.SECONDS)) throw new AssertionError("TIMER_NOT_STOPPED");
            SwingUtilities.invokeAndWait(() -> {});
            if (owner.system() != system) throw new AssertionError("SYSTEM_IDENTITY_CHANGED");
            if (!object.state(system.state()).attributeValue("p").toString().equals("200"))
                throw new AssertionError("NATIVE_MUTATION_NOT_APPLIED");
            long[] sorted = delays.stream().mapToLong(Long::longValue).sorted().toArray();
            if (sorted.length == 0) throw new AssertionError("NO_EDT_HEARTBEAT");
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("scope", "SYNTHETIC_EXISTING_NATIVE_COORDINATOR_NOT_LIVE_JACAMO");
            result.put("boundaries", 200);
            result.put("elapsedNanos", System.nanoTime() - started);
            result.put("heartbeatSamples", sorted.length);
            result.put("heartbeatP95DelayNanos", sorted[(int)Math.ceil(sorted.length * .95) - 1]);
            result.put("heartbeatMaxDelayNanos", sorted[sorted.length - 1]);
            result.put("nativeMetrics", owner.performanceMetrics());
            Files.write(root.resolve("native-baseline-performance.json"), CanonicalJson.encode(result));
            System.out.println("NATIVE_COORDINATOR_BASELINE_OK boundaries=200 heartbeatSamples=" + sorted.length);
        } finally {
            timer.shutdownNow();
        }
    }
}

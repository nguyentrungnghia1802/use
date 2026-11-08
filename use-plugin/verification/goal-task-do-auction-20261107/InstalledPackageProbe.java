import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.semantic.engine.api.EngineInstallationGuard;

/** Verifies installed Prepare + Import and bounded installed Auction run on use-assembly/target/new. */
public final class InstalledPackageProbe {
    public static void main(String[] args) {
        try {
            run(args);
            System.out.println("INSTALLED_PACKAGE_PROBE_PASS");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        Path install = Path.of(args[0]).toRealPath();
        Path models = Path.of(args[1]).toRealPath();
        Path jcm = Path.of(args[2]).toRealPath();
        Path output = Path.of(args[3]).toAbsolutePath();

        org.tzi.use.config.Options.processArgs(new String[]{"-H=" + install, "-nogui", "-nr"});
        var guard = EngineInstallationGuard.verify();

        var loadedJars = new TreeMap<String, Map<String, String>>();
        for (String name : List.of(
                "org.jacamo.bridge.contract.JasonAgentInventory",
                "org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources",
                "org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter",
                "org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation")) {
            var type = Class.forName(name);
            Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if (!jar.startsWith(install)) {
                throw new AssertionError("Non-installed production class: " + name + " -> " + jar);
            }
            String sha = sha256(jar);
            loadedJars.put(name, Map.of("jar", jar.toString(), "sha256", sha));
            System.out.println("LOADED " + name + " " + jar + " SHA256=" + sha);
        }

        Path classpathFile = install.resolve("bridge/runtime-classpath.txt").toRealPath();
        String runtimeClasspath = Files.readString(classpathFile).trim();
        Path contractJar = install.resolve("bridge/lib/jacamo-bridge-contract-1.0.0.jar").toRealPath();
        Path bridgeJar = install.resolve("bridge/lib/jacamo-bridge-jacamo-1.0.0.jar").toRealPath();
        Path pluginJar = install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar").toRealPath();

        Session host = new Session();
        DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(install, host);
        facade.verifiedCheckpointMode(true);
        facade.nextJournalBudgetBytes(536870912L);
        try {
            facade.prepareOwnedProducer(
                    jcm,
                    new JaCaMoFacade.ImportSelection(
                            "JACAMO_DECLARATIVE_XML",
                            models.resolve("auction-mapping.xml"),
                            models.resolve("auction.ocl"),
                            models.resolve("auction.csl"),
                            models.resolve("auction.istar"),
                            false),
                    classpathFile,
                    false);
            if (host.system() == null) {
                throw new AssertionError("Authoritative MSystem was not imported");
            }
            long producerPid = facade.ownedProducerStatus().pid();
            String runId = facade.ownedProducerStatus().runId();
            if (producerPid <= 0) {
                throw new AssertionError("Owned producer missing");
            }
            String preparedClasspath = "";
            try (var dirs = Files.list(install.resolve("work/owned-runtime"))) {
                for (Path dir : dirs.toList()) {
                    Path prep = dir.resolve("prepared.json");
                    if (Files.isRegularFile(prep)) {
                        var map = CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(prep)));
                        if (runId.equals(Objects.toString(map.get("runId"), ""))) {
                            preparedClasspath = Objects.toString(map.get("classpath"), "");
                            break;
                        }
                    }
                }
            }
            System.out.println("IMPORTED model=" + host.system().model().name() + " producerPID=" + producerPid);

            facade.startRuntime();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            var seenTokens = new LinkedHashSet<String>();
            while (!complete(facade) && System.nanoTime() < end) {
                var state = facade.verifiedCheckpointState();
                if (!state.token().isBlank()) {
                    seenTokens.add(state.token());
                }
                if (state.reviewable()) {
                    throw new AssertionError("Unmodified installed Auction held at checkpoint: " + state.reason() + " / " + state.checkpoint());
                }
                Thread.sleep(80);
            }
            if (!complete(facade)) {
                throw new AssertionError("Installed Auction run did not complete within budget; state=" + facade.verifiedCheckpointState());
            }

            var contexts = contexts(facade);
            var summary = new ArrayList<Map<String, Object>>();
            for (var c : contexts) {
                var occs = CanonicalJson.object(c.getOrDefault("occurrences", Map.of()));
                var rootStatuses = new TreeMap<String, Object>();
                for (var entry : occs.entrySet()) {
                    var o = CanonicalJson.object(entry.getValue());
                    String src = Objects.toString(o.get("sourceElement"), "");
                    if (Set.of("do_auction", "createScheme", "commitMission", "O_root").contains(src)) {
                        rootStatuses.put(entry.getKey(), Map.of(
                                "sourceElement", src,
                                "player", Objects.toString(o.get("player"), ""),
                                "aggregateStatus", Objects.toString(o.get("aggregateStatus"), ""),
                                "executionQuality", Objects.toString(o.get("executionQuality"), "")));
                    }
                }
                summary.add(Map.of(
                        "context", Objects.toString(c.get("context"), ""),
                        "scopeDefinition", Objects.toString(c.get("scopeDefinition"), ""),
                        "elements", rootStatuses));
            }

            var receipt = new TreeMap<String, Object>();
            receipt.put("status", "PASS");
            receipt.put("installRoot", install.toString());
            receipt.put("consumerClasspath", System.getProperty("java.class.path"));
            receipt.put("producerClasspathFile", classpathFile.toString());
            receipt.put("producerClasspathHasInstalledBridge", preparedClasspath.contains(bridgeJar.toString()) && preparedClasspath.contains(contractJar.toString()));
            receipt.put("producerRuntimeClasspathHasCheckpointJars", runtimeClasspath.contains("jason-interpreter-3.3.2-checkpoint-SNAPSHOT.jar")
                    && runtimeClasspath.contains("cartago-3.1-checkpoint-SNAPSHOT.jar"));
            receipt.put("installedJars", Map.of(
                    "plugin", Map.of("path", pluginJar.toString(), "sha256", sha256(pluginJar)),
                    "contract", Map.of("path", contractJar.toString(), "sha256", sha256(contractJar)),
                    "bridge", Map.of("path", bridgeJar.toString(), "sha256", sha256(bridgeJar))));
            receipt.put("loadedClasses", loadedJars);
            receipt.put("guard", guard.toString());
            receipt.put("consumerPid", ProcessHandle.current().pid());
            receipt.put("producerPid", producerPid);
            receipt.put("approvedCheckpointTokens", seenTokens.size());
            receipt.put("completedContexts", summary);
            Files.createDirectories(output.getParent());
            Files.write(output, CanonicalJson.encode(receipt));
        } finally {
            facade.stopOwnedProducer();
            facade.close();
        }
    }

    static List<Map<String, Object>> contexts(DefaultJaCaMoFacade facade) {
        var result = facade.verificationSnapshot().result();
        if (result != null && "PENDING_EVALUATION".equals(result.freshness())) {
            var history = facade.runtimeVerificationHistory();
            if (!history.isEmpty()) result = history.getLast();
        }
        if (result == null || result.semantic() == null) return List.of();
        return ((List<?>) CanonicalJson.object(result.semantic().observation().getOrDefault("originalEngine", Map.of()))
                .getOrDefault("contexts", List.of())).stream().map(CanonicalJson::object).toList();
    }

    static boolean complete(DefaultJaCaMoFacade facade) {
        var values = contexts(facade);
        long setup = values.stream().filter(c -> "initial-auction-setup".equals(c.get("scopeDefinition"))
                && CanonicalJson.object(c.getOrDefault("occurrences", Map.of())).values().stream().map(CanonicalJson::object)
                .anyMatch(o -> "do_auction".equals(o.get("sourceElement"))
                        && "FULFILLED".equals(o.get("aggregateStatus"))
                        && Set.of("KNOWN_DERIVED", "KNOWN_DERIVED_HISTORY").contains(Objects.toString(o.get("executionQuality"), "")))).count();
        long exec = values.stream().filter(c -> "auction-execution".equals(c.get("scopeDefinition"))
                && CanonicalJson.object(c.getOrDefault("occurrences", Map.of())).values().stream().map(CanonicalJson::object)
                .anyMatch(o -> "O_root".equals(o.get("sourceElement"))
                        && "FULFILLED".equals(o.get("aggregateStatus"))
                        && Set.of("KNOWN_DERIVED", "KNOWN_DERIVED_HISTORY").contains(Objects.toString(o.get("executionQuality"), "")))).count();
        return values.size() == 4 && setup == 2 && exec == 2;
    }

    static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}

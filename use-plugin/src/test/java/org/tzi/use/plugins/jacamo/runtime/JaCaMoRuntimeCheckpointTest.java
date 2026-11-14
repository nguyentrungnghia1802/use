package org.tzi.use.plugins.jacamo.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;
import static org.junit.jupiter.api.Assertions.*;

/** Actual imported Jason execution; no CSL, mapping or state-change filter is involved. */
class JaCaMoRuntimeCheckpointTest {
    @TempDir Path directory;

    @Test void nestedLocalCallsAreOneCheckpointEvenWithoutAnyStateChange() throws Exception {
        var rows = run(Map.of("alice", "!root.\n+!root <- !child; !child.\n+!child <- !leaf.\n+!leaf <- .print(leaf)."), "",
                r -> terminal(r, "alice", "+!root"));
        var roots = application(rows, "+!root", "+!child", "+!leaf");
        assertEquals(1, roots.size()); assertEquals(4, ((Number) roots.getFirst().get("merged")).intValue());
        assertEquals("SUCCEEDED", roots.getFirst().get("outcome"));
    }
    @Test void independentPlansOnTheSameAgentRemainIndependent() throws Exception {
        var rows = run(Map.of("alice", "!one. !two.\n+!one <- !leaf.\n+!two <- !leaf.\n+!leaf <- .print(leaf)."), "",
                r -> terminal(r, "alice", "+!two"));
        var roots = application(rows, "+!one", "+!two", "+!leaf");
        assertEquals(2, roots.size());
        assertEquals(2, roots.stream().map(r -> r.get("invocation")).distinct().count());
        assertTrue(roots.stream().allMatch(r -> ((Number) r.get("merged")).intValue() == 1));
    }
    @Test void atomicParentWaitingForLocalSubgoalsIsNotCancelled() throws Exception {
        var rows = run(Map.of("alice", "!root.\n@atomic_root[atomic]\n+!root <- !child; !child.\n+!child <- !leaf.\n+!leaf <- .print(leaf)."), "",
                r -> terminal(r, "alice", "+!root") && r.get("outcome").equals("SUCCEEDED"));
        var roots = application(rows, "+!root", "+!child", "+!leaf");
        assertEquals(1, roots.size(), roots.toString());
        assertEquals(4, ((Number)roots.getFirst().get("merged")).intValue());
        assertEquals("SUCCEEDED", roots.getFirst().get("outcome"));
    }
    @Test void detachedLocalGoalIsAnIndependentExecution() throws Exception {
        var rows = run(Map.of("alice", "!root.\n+!root <- !!detached; !leaf.\n+!detached <- !leaf.\n+!leaf <- .print(leaf)."), "",
                r -> terminal(r, "alice", "+!root"));
        var roots = application(rows, "+!root", "+!detached", "+!leaf");
        assertEquals(2, roots.size(), roots.toString());
        assertEquals(2, roots.stream().map(r -> r.get("intention")).distinct().count());
    }
    @Test void forkedLocalBranchesKeepTheirOwnBoundaries() throws Exception {
        var rows = run(Map.of("alice", "!root.\n+!root <- !left |&| !right; .print(joined).\n+!left <- .print(left).\n+!right <- .print(right)."), "",
                r -> terminal(r, "alice", "+!root"));
        var roots = application(rows, "+!root", "+!left", "+!right");
        assertEquals(3, roots.size(), roots.toString());
        assertTrue(roots.stream().allMatch(r -> r.get("outcome").equals("SUCCEEDED")), roots.toString());
    }
    @Test void delegatedWorkChecksBeforeDispatchThenAtCalleeAndCallerCompletion() throws Exception {
        var rows = run(delegation(), "", r -> terminal(r, "alice", "+!root"));
        var roots = application(rows, "+!root", "+!work", "+!leaf");
        assertEquals(3, roots.size(), roots.toString());
        assertEquals(List.of("AGENT_HANDOFF", "PLAN_COMPLETED", "PLAN_COMPLETED"), roots.stream().map(r -> r.get("boundary")).toList());
        assertEquals(List.of("alice", "bob", "alice"), roots.stream().map(r -> r.get("agent")).toList());
        assertEquals(roots.getFirst().get("invocation"), roots.getLast().get("invocation"));
        assertFalse(((List<?>) roots.get(1).get("messages")).isEmpty(), "Final reply is released with callee approval");
    }
    @Test void rejectedHandoffNeverReachesTheReceivingAgent() throws Exception {
        var rows = run(delegation(), "AGENT_HANDOFF", r -> r.get("boundary").equals("AGENT_HANDOFF"));
        assertEquals(1, application(rows, "+!root", "+!work", "+!leaf").size());
        assertFalse(Files.readString(directory.resolve("source.log")).contains("BOB_EXECUTED"));
    }
    @Test void rejectedCalleeCompletionCannotPublishItsReplyOrResumeCaller() throws Exception {
        var rows = run(delegation(), "+!work", r -> terminal(r, "bob", "+!work"));
        assertEquals(2, application(rows, "+!root", "+!work", "+!leaf").size());
        assertFalse(Files.readString(directory.resolve("source.log")).contains("ALICE_RESUMED"));
    }
    @Test void failedLocalChildProducesOneFailedParentCheckpoint() throws Exception {
        var rows = run(Map.of("alice", "!root.\n+!root <- !child.\n+!child <- .fail."), "",
                r -> r.get("outcome").equals("FAILED"));
        var roots = application(rows, "+!root", "+!child");
        assertEquals(1, roots.size(), roots.toString()); assertTrue(roots.getFirst().get("plan").toString().contains("+!root"));
        assertEquals("FAILED", roots.getFirst().get("outcome"));
    }
    @Test void explicitCancellationStillProducesAParentCheckpoint() throws Exception {
        var rows = run(Map.of("alice", "!root. !cancel.\n+!root <- .wait(10000).\n+!cancel <- .wait(100); .drop_intention(root)."), "",
                r -> terminal(r, "alice", "+!root"));
        var roots = application(rows, "+!root");
        assertEquals(1, roots.size()); assertEquals("CANCELLED", roots.getFirst().get("outcome"));
    }
    @Test void failedForkBranchDoesNotLeaveAnUnfinishedParentGroup() throws Exception {
        var rows = run(Map.of("alice", "!root.\n+!root <- !left |&| !right; .print(joined).\n+!left <- .fail.\n+!right <- .wait(10000)."), "",
                r -> terminal(r, "alice", "+!root"));
        var roots = application(rows, "+!root");
        assertEquals(1, roots.size(), rows.toString()); assertEquals("FAILED", roots.getFirst().get("outcome"));
    }
    @Test void missingRootPlanStillRequestsVerification() throws Exception {
        var rows = run(Map.of("alice", "!missing."), "", r -> r.get("boundary").equals("GOAL_FAILED"));
        assertTrue(rows.stream().anyMatch(r -> r.get("plan").toString().contains("missing") && r.get("outcome").equals("FAILED")));
    }
    private static Map<String,String> delegation() {
        return Map.of("alice", "!root.\n+!root <- .send(bob,achieve,work); .wait(done); .print(\"ALICE_RESUMED\").",
                "bob", "+!work <- !leaf; .send(alice,tell,done).\n+!leaf <- .print(\"BOB_EXECUTED\").");
    }
    private static boolean terminal(Map<String,Object> row, String agent, String plan) {
        return row.get("agent").equals(agent) && row.get("plan").toString().contains(plan) && row.get("boundary").equals("PLAN_COMPLETED");
    }
    private static List<Map<String,Object>> application(List<Map<String,Object>> rows, String... names) {
        return rows.stream().filter(r -> Arrays.stream(names).anyMatch(n -> r.get("plan").toString().contains(n))).toList();
    }
    private List<Map<String,Object>> run(Map<String,String> sources, String hold, Predicate<Map<String,Object>> finished) throws Exception {
        Path agents = Files.createDirectories(directory.resolve("src/agt"));
        for (var entry : sources.entrySet()) Files.writeString(agents.resolve(entry.getKey()+".asl"), entry.getValue());
        String declarations = String.join("\n", sources.keySet().stream().sorted().map(n -> "agent "+n+" : "+n+".asl").toList());
        Path project = directory.resolve("test.jcm"), trace = directory.resolve("events.jsonl");
        Files.writeString(project, "mas checkpoints {\n" + declarations + "\n}");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"), Source.class.getName(),
                project.toString(), trace.toString(), hold).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(directory.resolve("source.log").toFile()).start();
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
            List<Map<String,Object>> rows;
            do {
                rows = read(trace);
                if (rows.stream().anyMatch(finished)) { Thread.sleep(350); return read(trace); }
                if (!process.isAlive()) break;
                Thread.sleep(30);
            } while (System.nanoTime() < deadline);
            fail("Source checkpoint not reached: " + rows + "\n" + Files.readString(directory.resolve("source.log")));
            return List.of();
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor();
        }
    }
    private static List<Map<String,Object>> read(Path trace) throws Exception {
        if (!Files.exists(trace)) return List.of();
        // The last line may still be in flight; only complete JSON lines are consumed.
        var rows = new ArrayList<Map<String,Object>>();
        for (String line : Files.readString(trace).split("\n")) try {
            rows.add(CanonicalJson.object(CanonicalJson.decode(line.getBytes(StandardCharsets.UTF_8))));
        } catch (IllegalArgumentException ignored) { }
        return rows;
    }
    public static final class Source {
        public static void main(String[] args) throws Exception {
            var runtime = new OfficialJaCaMoRuntimeAdapter(UUID.randomUUID().toString(), c -> {
                var row = Map.<String,Object>of("agent", c.agent(), "plan", c.plan(), "boundary", c.boundary(),
                        "outcome", c.outcome(), "invocation", c.invocation(), "intention", c.intention(), "merged", c.mergedPlans(), "messages", c.messages());
                Files.writeString(Path.of(args[1]), new String(CanonicalJson.encode(row), StandardCharsets.UTF_8)+"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return args[2].isEmpty() || !(c.boundary().contains(args[2]) || c.plan().contains(args[2]));
            });
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));
            runtime.initialize(Path.of(args[0])); runtime.create(); runtime.start(); new CountDownLatch(1).await();
        }
    }
}

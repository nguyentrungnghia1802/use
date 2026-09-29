package org.tzi.use.plugins.jacamo.bridge;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.PipelineMode;
import org.tzi.use.plugins.jacamo.SemanticAuthority;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;

/**
 * Production headless consumer for the generic PowerShell launcher.  It deliberately delegates import,
 * native projection, runtime synchronization and verification to the same facade used by the USE GUI.
 */
public final class JaCaMoBridgeNativeConsumerMain {
    private static final Logger LOG = Logger.getLogger(JaCaMoBridgeNativeConsumerMain.class.getName());

    private JaCaMoBridgeNativeConsumerMain() { }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Files.createDirectories(options.evidenceDirectory());
        BridgeConnectionConfig configuration = new BridgeConnectionConfig(
                URI.create("tcp://127.0.0.1:" + options.port()), options.secretFile(), options.distribution(),
                Set.of("official.model", "runtime.snapshot"), 4 * 1024 * 1024,
                options.requestTimeoutMillis(), options.maxBufferedEvents());

        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(options.useCheckout(), SemanticAuthority.BRIDGE,
                () -> configuration, BridgeTransportFactory.localTcp())) {
            if (facade.pipelineMode() != PipelineMode.CODE_GROUNDED_NATIVE)
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_PIPELINE_REQUIRED");
            facade.configureBridge(configuration);
            JaCaMoFacade.ProjectSummary imported = facade.importProject(options.jcmFile());
            JaCaMoFacade.AuthorityStatus before = facade.authorityStatus();
            if (!imported.structureValid() || before.readiness() != BridgeClientState.LIVE)
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_IMPORT_NOT_LIVE:" + before.diagnostic());
            var first = facade.runFullVerification();
            if (!first.structureValid()) throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_VERIFICATION_INVALID");
            if (options.observationSeconds() > 0)
                Thread.sleep(Math.multiplyExact(options.observationSeconds(), 1_000L));
            facade.resyncRuntime();
            JaCaMoFacade.AuthorityStatus after = facade.authorityStatus();
            var second = facade.runFullVerification();
            if (after.readiness() != BridgeClientState.LIVE || !second.structureValid())
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_RESYNC_NOT_LIVE:" + after.diagnostic());
            if (!before.sessionId().equals(after.sessionId()) || before.generation() != after.generation()
                    || !before.modelRevision().equals(after.modelRevision()))
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_RESYNC_IDENTITY_CHANGED");
            JaCaMoFacade.FormalStateStatus formal = facade.formalStateStatus();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("status", "PASS");
            evidence.put("pipelineMode", facade.pipelineMode().name());
            evidence.put("jcm", options.jcmFile().toString());
            evidence.put("projectId", imported.projectId());
            evidence.put("modelRevision", after.modelRevision());
            evidence.put("sessionId", after.sessionId());
            evidence.put("generation", after.generation());
            evidence.put("classCount", formal.classCount());
            evidence.put("objectCount", formal.objectCount());
            evidence.put("linkCount", formal.linkCount());
            evidence.put("stateSha256", formal.sha256());
            evidence.put("verificationOutcomeCount", second.results().size());
            evidence.put("resync", true);
            Files.write(options.evidenceDirectory().resolve("summary.json"), CanonicalJson.encode(evidence));
            facade.exportVerificationReport(options.evidenceDirectory().resolve("verification-report.json"));
            LOG.info("JACAMO_BRIDGE_NATIVE_OK pipeline=CODE_GROUNDED_NATIVE project="
                    + imported.projectId() + " classes=" + formal.classCount() + " objects="
                    + formal.objectCount() + " links=" + formal.linkCount() + " resync=true");
        }
    }

    record Options(int port, Path secretFile, String distribution, Path jcmFile, Path useCheckout,
                   Path evidenceDirectory, int observationSeconds, int maxBufferedEvents,
                   int requestTimeoutMillis) {
        static Options parse(String[] args) {
            if (args.length < 9) throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_ARGS_REQUIRED");
            try {
                int port = Integer.parseInt(args[0]);
                Path secret = Path.of(args[1]).toAbsolutePath().normalize();
                Path jcm = Path.of(args[3]).toAbsolutePath().normalize();
                Path checkout = Path.of(args[4]).toAbsolutePath().normalize();
                Path evidence = Path.of(args[5]).toAbsolutePath().normalize();
                int observation = Integer.parseInt(args[6]);
                int maxBuffered = Integer.parseInt(args[7]);
                int timeout = Integer.parseInt(args[8]);
                if (port < 1 || port > 65535 || !Files.isRegularFile(secret) || !Files.isRegularFile(jcm)
                        || !Files.isDirectory(checkout) || observation < 0 || maxBuffered < 1 || timeout < 100)
                    throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_ARGS_INVALID");
                if (!args[2].matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_DISTRIBUTION_INVALID");
                return new Options(port, secret, args[2], jcm, checkout, evidence, observation, maxBuffered, timeout);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_ARGS_INVALID", error);
            }
        }
    }
}

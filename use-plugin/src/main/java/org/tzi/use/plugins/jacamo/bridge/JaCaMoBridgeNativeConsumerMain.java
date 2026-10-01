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
import org.tzi.use.main.Session;

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

        Session session = new Session();
        try (DefaultJaCaMoFacade facade = new DefaultJaCaMoFacade(options.useCheckout(), SemanticAuthority.BRIDGE,
                () -> configuration, BridgeTransportFactory.localTcp(), PipelineMode.CODE_GROUNDED_NATIVE, session)) {
            if (facade.pipelineMode() != PipelineMode.CODE_GROUNDED_NATIVE)
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_PIPELINE_REQUIRED");
            facade.configureBridge(configuration);
            JaCaMoFacade.ProjectSummary imported = facade.importProject(options.jcmFile());
            JaCaMoFacade.AuthorityStatus before = facade.authorityStatus();
            if (!imported.structureValid() || before.readiness() != BridgeClientState.LIVE)
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_IMPORT_NOT_LIVE:" + before.diagnostic());
            var activeSystem = session.system();
            if (activeSystem == null) throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_SESSION_NOT_ACTIVE");
            NativeOclLaunchEvidence.ProfileEvidence profile = options.oclProfile() == null ? null
                    : NativeOclLaunchEvidence.load(facade, session, options.oclProfile(), options.evidenceDirectory());
            if (session.system() != activeSystem)
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_PROFILE_CHANGED_SYSTEM");
            var first = facade.runFullVerification();
            if (!first.structureValid()) throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_VERIFICATION_INVALID");
            if (options.consumerReadyFile() != null) {
                Path temporary = Files.createTempFile(options.consumerReadyFile().getParent(), "consumer-ready-", ".tmp");
                try {
                    byte[] readiness = CanonicalJson.encode(Map.of("status", "PROFILE_COMPILED_AND_BASELINE_VERIFIED",
                            "profileSha256", profile.sha256(), "stateVersion", profile.baseline().stateVersion(),
                            "baselineVerifiedAt", profile.baseline().verifiedAt().toString()));
                    Files.write(temporary, readiness);
                    Files.write(options.evidenceDirectory().resolve("consumer-ready.json"), readiness);
                    Files.move(temporary, options.consumerReadyFile(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            }
            if (options.observationSeconds() > 0)
                Thread.sleep(Math.multiplyExact(options.observationSeconds(), 1_000L));
            var beforeResync = facade.runtimeVerificationResult();
            facade.resyncRuntime();
            JaCaMoFacade.AuthorityStatus after = facade.authorityStatus();
            var second = facade.runFullVerification();
            if (after.readiness() != BridgeClientState.LIVE || !second.structureValid())
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_RESYNC_NOT_LIVE:" + after.diagnostic());
            if (!before.sessionId().equals(after.sessionId()) || before.generation() != after.generation()
                    || !before.modelRevision().equals(after.modelRevision()))
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_RESYNC_IDENTITY_CHANGED");
            if (session.system() != activeSystem)
                throw new IllegalStateException("JACAMO_BRIDGE_NATIVE_RESYNC_CHANGED_SYSTEM");
            JaCaMoFacade.FormalStateStatus formal = facade.formalStateStatus();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("status", "PASS");
            evidence.put("pipelineMode", facade.pipelineMode().name());
            evidence.put("projectionMode", facade.projectionMode().name());
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
            evidence.put("sameActiveMSystem", true);
            evidence.put("systemIdentity", System.identityHashCode(activeSystem));
            evidence.put("modelIdentity", System.identityHashCode(activeSystem.model()));
            // PASS here means the workflow executed, never that intentionally false OCL passed.
            evidence.put("scope", "OBSERVED_SUPPORTED_PROJECTION_ONLY");
            if (profile != null) {
                Path replay = options.evidenceDirectory().resolve("replay");
                facade.exportRuntimeReplay(replay);
                evidence.put("externalOcl", NativeOclLaunchEvidence.finish(profile, beforeResync,
                        facade.runtimeVerificationResult(), replay, options.evidenceDirectory()));
            }
            if (options.exportArtifacts()) {
                Path nativeUse = options.evidenceDirectory().resolve(imported.projectId() + ".use");
                Path nativeSoil = options.evidenceDirectory().resolve(imported.projectId() + ".cmd");
                facade.exportNativeUse(nativeUse);
                facade.exportNativeSoil(nativeSoil);
                evidence.put("nativeUse", nativeUse.toString());
                evidence.put("nativeSoil", nativeSoil.toString());
            }
            Files.write(options.evidenceDirectory().resolve("summary.json"), CanonicalJson.encode(evidence));
            facade.exportVerificationReport(options.evidenceDirectory().resolve("verification-report.json"));
            LOG.info("JACAMO_BRIDGE_NATIVE_OK pipeline=CODE_GROUNDED_NATIVE project="
                    + imported.projectId() + " projection=" + facade.projectionMode() + " classes=" + formal.classCount() + " objects="
                    + formal.objectCount() + " links=" + formal.linkCount() + " resync=true");
        }
    }

    record Options(int port, Path secretFile, String distribution, Path jcmFile, Path useCheckout,
                   Path evidenceDirectory, int observationSeconds, int maxBufferedEvents,
                   int requestTimeoutMillis, boolean exportArtifacts, Path oclProfile, Path consumerReadyFile) {
        static Options parse(String[] args) {
            if (args.length < 10 || args.length > 12) throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_ARGS_REQUIRED");
            try {
                int port = Integer.parseInt(args[0]);
                Path secret = Path.of(args[1]).toAbsolutePath().normalize();
                Path jcm = Path.of(args[3]).toAbsolutePath().normalize();
                Path checkout = Path.of(args[4]).toAbsolutePath().normalize();
                Path evidence = Path.of(args[5]).toAbsolutePath().normalize();
                int observation = Integer.parseInt(args[6]);
                int maxBuffered = Integer.parseInt(args[7]);
                int timeout = Integer.parseInt(args[8]);
                if (!args[9].equalsIgnoreCase("true") && !args[9].equalsIgnoreCase("false"))
                    throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_EXPORT_FLAG_INVALID");
                if (port < 1 || port > 65535 || !Files.isRegularFile(secret) || !Files.isRegularFile(jcm)
                        || !Files.isDirectory(checkout) || observation < 0 || maxBuffered < 1 || timeout < 100)
                    throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_ARGS_INVALID");
                if (!args[2].matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_DISTRIBUTION_INVALID");
                Path profile = args.length >= 11 && !args[10].isBlank()
                        ? Path.of(args[10]).toAbsolutePath().normalize() : null;
                if (profile != null && (!profile.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ocl")
                        || !Files.isRegularFile(profile)))
                    throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_OCL_PROFILE_INVALID:" + profile);
                Path ready = args.length == 12 && !args[11].isBlank()
                        ? Path.of(args[11]).toAbsolutePath().normalize() : null;
                if (ready != null && (profile == null || !ready.getParent().equals(jcm.getParent())
                        || Files.exists(ready, java.nio.file.LinkOption.NOFOLLOW_LINKS)))
                    throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_READY_FILE_INVALID");
                return new Options(port, secret, args[2], jcm, checkout, evidence, observation, maxBuffered, timeout,
                        Boolean.parseBoolean(args[9]), profile, ready);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("JACAMO_BRIDGE_NATIVE_ARGS_INVALID", error);
            }
        }
    }
}

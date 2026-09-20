import jacamo.infra.JaCaMoLauncher;
import cartago.CartagoEnvironment;

/** Isolated JVM probe. Never treats launcher.start() returning as proof of readiness. */
public class LauncherProbe {
    public static void main(String[] args) {
        JaCaMoLauncher launcher = new JaCaMoLauncher() {
            { // Same bootstrap as pinned JaCaMoLauncher.main, without interactive registries.
                runner = this;
                jason.runtime.RuntimeServicesFactory.set(new jacamo.infra.JaCaMoRuntimeServices(this));
            }
            protected java.io.InputStream getDefaultLogProperties() {
                return new java.io.ByteArrayInputStream("handlers=java.util.logging.ConsoleHandler\n.level=INFO\n".getBytes());
            }
        };
        int exit = 3;
        try {
            int result = launcher.init(new String[]{args[0], "--no-net"});
            System.out.println("PHASE20_INIT_RESULT=" + result);
            if (result != 0) throw new IllegalStateException("PHASE20_PROJECT_PARSE_FAILED:" + result);
            launcher.create();
            launcher.start();
            var environment = CartagoEnvironment.getInstance();
            if (environment.getController("/main/market").getArtifactInfo("auction1") == null)
                throw new IllegalStateException("PHASE20_ARTIFACT_NOT_READY");
            // The fixture requires organisational infrastructure as well as the environment.
            var org = environment.getController("/main/auction_org");
            var names = java.util.Arrays.stream(org.getCurrentArtifacts()).map(cartago.ArtifactId::getName).toList();
            if (!names.contains("auction_group") || !names.contains("auction_scheme")
                    || ora4mas.nopl.OrgBoard.getOrbBoards().isEmpty()
                    || ora4mas.nopl.GroupBoard.getGroupBoards().stream().noneMatch(board -> board.getSpec() != null)
                    || ora4mas.nopl.SchemeBoard.getSchemeBoards().stream().noneMatch(board -> board.getSpec() != null))
                throw new IllegalStateException("PHASE20_ORGANISATION_NOT_READY");
            System.out.println("PHASE20_PLATFORM_PROBE_RETURNED");
            exit = 0;
        } catch (Throwable error) {
            error.printStackTrace();
        } finally {
            try { launcher.finish(0, false, 0); } catch (Throwable error) { error.printStackTrace(); }
        }
        System.exit(exit);
    }
}

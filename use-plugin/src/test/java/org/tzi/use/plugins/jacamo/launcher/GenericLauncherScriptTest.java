package org.tzi.use.plugins.jacamo.launcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Static contract evidence for the production generic .jcm launcher. */
class GenericLauncherScriptTest {
    @Test
    void launcherIsGenericAndUsesNativeProductionConsumer() throws IOException {
        String script = Files.readString(findLauncher()).toLowerCase(Locale.ROOT);

        assertTrue(script.contains("$jcmpath"));
        assertTrue(script.contains("[validateset(\"auto\", \"full\")]"));
        assertTrue(script.contains("[string]$projectionmode = \"auto\""));
        assertTrue(script.contains("\"-duse.jacamo.projection.mode=$projectionmode\","));
        assertTrue(script.contains("-jvmarguments @(\"-duse.jacamo.runtime.root=$runtimedirectory\", \"-duse.jacamo.projection.mode=$projectionmode\")"));
        assertTrue(script.contains("jcm_path_required"));
        assertTrue(script.contains("jcm_file_not_found"));
        assertTrue(script.contains("$oclprofilepath"));
        assertTrue(script.contains("ocl_profile_not_found_or_invalid"));
        assertTrue(script.contains("ocl_auto_load_headless_only"));
        assertTrue(script.contains("$persistevidence.tostring().tolowerinvariant(), $externalocl, $consumerreadyfile"));
        assertTrue(script.contains("consumer-ready.flag"));
        assertTrue(script.contains("awaitoclbaselinebeforeagents"));
        assertTrue(script.contains("copy-item -literalpath $sourceproject -destination $stagedproject -recurse"));
        assertTrue(script.contains("jacamobridgenativeconsumermain"));
        assertTrue(script.contains("code_grounded_native"));
        assertTrue(script.contains("interactivegui"));
        assertTrue(script.contains("runtimeprojectclasses"));
        assertTrue(script.contains("$stagedproject"));
        assertTrue(script.contains("workingdirectory"));
        assertTrue(script.contains("redirectstandarderror = $true"));
        assertTrue(script.contains("readtoendasync"));
        assertTrue(script.contains("runtime_project_build_failed:$gradleexitcode"));
        assertTrue(script.contains("runtime_project_classes_not_required"));
        assertTrue(script.contains("gradle_classes_not_required"));
        assertFalse(script.contains("& $stagedgradlewrapper"));
        assertTrue(script.contains("psobject.properties['argumentlist']"));
        assertTrue(script.contains("$startinfo.arguments ="));
        assertTrue(script.contains("use.jacamo.workbench.auto-import=true"));
        assertTrue(script.contains("use.jacamo.workbench.ready-file=$guireadyfile"));
        assertTrue(script.contains("interactive_gui_model_ready"));
        assertTrue(script.contains("use.plugin.auto-action-id=org.tzi.use.plugins.jacamo.workbench.action"));
        assertTrue(script.contains("use.jacamo.workbench.project-file=$jcmfile"));
        assertTrue(script.contains("$producerheadless = if ($interactivegui) { $false } else { $headless }"));
        assertTrue(script.contains("$producerlifetimeseconds = if ($interactivegui) { 0 } else { $timeoutseconds }"));
        assertTrue(script.contains("$producerlifetimeseconds.tostring()"));
        assertTrue(script.contains("wait-loopbackport $port $producer $timeoutseconds"));
        assertTrue(script.contains("interactive_gui_import_timeout"));
        assertTrue(script.contains("target\\jacamo-runtime"));
        assertTrue(script.contains("$persistevidence"));
        assertTrue(script.contains("$stagedproject = join-path $runevidence (\"staging-"));
        assertTrue(script.contains("$expectedstageroot = [io.path]::getfullpath($runevidence)"));
        assertFalse(script.contains("[io.path]::gettemppath()"));
        assertTrue(script.contains("use.jacamo.runtime.root=$runtimedirectory"));
        assertTrue(script.contains("use.gui.default-layout-directory=$layoutdirectory"));
        assertTrue(script.contains("remove-item -literalpath $stagedproject -recurse -force"));
        assertTrue(script.contains("remove-item -literalpath $runevidence -recurse -force"));
        assertTrue(script.contains("bridge-secret.hex"));
        assertTrue(script.contains("stop.flag"));
        assertFalse(script.contains("live-hello-bridge.ps1"));
        assertFalse(script.contains("helloworld"));
        assertFalse(script.contains("auction"));
        assertFalse(script.contains("house"));
        assertFalse(script.contains("projectkey"));
        assertFalse(script.contains("casename"));
    }

    private static Path findLauncher() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; depth < 8 && cursor != null; depth++) {
            Path modulePath = cursor.resolve("tools/jacamo-bridge.ps1");
            if (Files.isRegularFile(modulePath)) return modulePath;
            Path repositoryPath = cursor.resolve("use-plugin/tools/jacamo-bridge.ps1");
            if (Files.isRegularFile(repositoryPath)) return repositoryPath;
            cursor = cursor.getParent();
        }
        throw new AssertionError("generic launcher script not found from " + System.getProperty("user.dir"));
    }
}

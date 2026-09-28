[CmdletBinding()]
param(
    [string]$EvidenceDirectory = "",
    [int]$TimeoutSeconds = 60,
    [string]$JcmPath = "",
    [string]$ProjectKey = "helloworld",
    [string]$CaseName = "",
    [int]$ObservationSeconds = 10,
    [int]$MaxBufferedEvents = 1024,
    [int]$BridgeTimeoutMillis = 60000,
    [bool]$Headless = $true,
    [switch]$InteractiveGui,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$useRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\")).Path
$usePluginRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path $usePluginRoot "target\live-hello-evidence"
}
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$runEvidence = Join-Path $EvidenceDirectory $runId
New-Item -ItemType Directory -Force -Path $runEvidence | Out-Null
if ([string]::IsNullOrWhiteSpace($CaseName)) { $CaseName = $ProjectKey }
$safeCaseName = $CaseName -replace '[^A-Za-z0-9_-]', '-'
$stagedProject = Join-Path ([IO.Path]::GetTempPath()) ("jacamo-live-$safeCaseName-" + [Guid]::NewGuid().ToString("N"))
if ([string]::IsNullOrWhiteSpace($JcmPath)) {
    $sourceProject = Join-Path $usePluginRoot "src\test\resources\canonical-cases\hello-world"
    $sourceJcm = Join-Path $sourceProject "helloworld.jcm"
} else {
    $sourceJcm = [IO.Path]::GetFullPath($JcmPath)
    $sourceProject = Split-Path -Parent $sourceJcm
}
$jcmFile = Join-Path $stagedProject (Split-Path -Leaf $sourceJcm)
$secretFile = Join-Path $stagedProject "bridge-secret.hex"
$stopFile = Join-Path $stagedProject "stop.flag"
$producer = $null
$runCompleted = $false
$runtimeProjectBuild = "NOT_REQUIRED"
$runtimeProjectClasses = ""

function Invoke-MavenBuild([string[]]$Arguments) {
    & mvn @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "MAVEN_FAILED:$LASTEXITCODE"
    }
}

function Start-CapturedJava([string]$ClassPath, [string]$MainClass, [string]$WorkingDirectory,
                            [string[]]$Arguments) {
    $java = (Get-Command java -ErrorAction Stop).Source
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $java
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $javaArguments = @("-cp", $ClassPath, $MainClass) + $Arguments
    foreach ($argument in $javaArguments) {
        [void]$info.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    [void]$process.Start()
    [pscustomobject]@{
        Process = $process
        Output = $process.StandardOutput.ReadToEndAsync()
        Error = $process.StandardError.ReadToEndAsync()
    }
}

function Start-InteractiveJava([string]$WorkingDirectory, [string[]]$Arguments) {
    $java = (Get-Command javaw.exe -ErrorAction Stop).Source
    # Start-Process is intentional for the user-facing GUI. Captured ProcessStartInfo is used for background
    # producer/consumer JVMs, while a detached javaw process preserves the Swing event loop on Windows.
    return Start-Process -FilePath $java -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -PassThru
}

function Get-CapturedOutput($Captured) {
    $stdout = $Captured.Output.GetAwaiter().GetResult()
    $stderr = $Captured.Error.GetAwaiter().GetResult()
    return ($stdout + $stderr)
}

function Wait-LoopbackPort([int]$Port, $Captured, [int]$Seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Captured.Process.HasExited) {
            throw "PRODUCER_EXITED_BEFORE_BRIDGE_READY:`n$(Get-CapturedOutput $Captured)"
        }
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $client.Connect("127.0.0.1", $Port)
            return
        } catch {
            Start-Sleep -Milliseconds 100
        } finally {
            $client.Dispose()
        }
    }
    throw "BRIDGE_PORT_TIMEOUT:$Port"
}

function Get-FilteredConsumerClasspath([string]$ClasspathFile) {
    $forbidden = @("jacamo-bridge-jacamo", "jason-interpreter-", "cartago-", "moise-",
        "npl-", "jacamo-1.3", "jaca-", "intmas-", "sai-")
    $entries = (Get-Content -Raw -LiteralPath $ClasspathFile).Trim().Split([IO.Path]::PathSeparator)
    $kept = foreach ($entry in $entries) {
        $lower = $entry.ToLowerInvariant()
        if (-not ($forbidden | Where-Object { $lower.Contains($_) })) { $entry }
    }
    $required = @(
        (Join-Path $useRoot "jacamo-bridge-contract\target\classes"),
        (Join-Path $useRoot "use-core\target\classes"),
        (Join-Path $useRoot "use-gui\target\classes"),
        (Join-Path $usePluginRoot "target\classes"),
        (Join-Path $usePluginRoot "target\test-classes")
    )
    return [string]::Join([IO.Path]::PathSeparator, @($required + $kept))
}

try {
    if (-not (Test-Path -LiteralPath $sourceProject -PathType Container) -or
        -not (Test-Path -LiteralPath $sourceJcm -PathType Leaf)) {
        throw "CANONICAL_HELLO_PROJECT_MISSING:$sourceProject"
    }
    if ($TimeoutSeconds -lt 15) { throw "TIMEOUT_TOO_SHORT" }
    if (-not $InteractiveGui -and
        ($ObservationSeconds -lt 0 -or $ObservationSeconds -ge ($TimeoutSeconds - 5))) {
        throw "OBSERVATION_WINDOW_INVALID"
    }
    if ($MaxBufferedEvents -lt 1) { throw "BUFFER_LIMIT_INVALID" }
    if ($BridgeTimeoutMillis -lt 100) { throw "BRIDGE_TIMEOUT_INVALID" }

    if (-not $SkipBuild) {
        if ($InteractiveGui) {
            # Build current binaries without touching the source-tree plugin staging location. The GUI demo uses a
            # disposable install next to the derived case instead.
            Invoke-MavenBuild @("-B", "-pl", "use-plugin", "-am", "-DskipTests",
                "-Dmaven.antrun.skip=true", "package")
        } else {
            Invoke-MavenBuild @("-B", "-pl", "use-plugin", "-am", "-DskipTests", "test-compile")
        }
        Invoke-MavenBuild @("-B", "-pl", "jacamo-bridge-jacamo", "-am", "dependency:build-classpath",
            "-Dmdep.outputFile=target/live-hello-bridge-classpath.txt", "-Dmdep.includeScope=test")
    }

    $bridgeClasspathFile = Join-Path $useRoot "jacamo-bridge-jacamo\target\live-hello-bridge-classpath.txt"
    if (-not (Test-Path -LiteralPath $bridgeClasspathFile -PathType Leaf)) {
        throw "BRIDGE_CLASSPATH_MISSING:$bridgeClasspathFile"
    }
    $bridgeDependencies = (Get-Content -Raw -LiteralPath $bridgeClasspathFile).Trim()
    $producerClasspath = [string]::Join([IO.Path]::PathSeparator, @(
        (Join-Path $useRoot "jacamo-bridge-contract\target\classes"),
        (Join-Path $useRoot "jacamo-bridge-jacamo\target\classes"),
        (Join-Path $useRoot "jacamo-bridge-jacamo\target\test-classes"),
        $bridgeDependencies
    ))

    $fingerprintLines = & java -cp $producerClasspath org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain
    if ($LASTEXITCODE -ne 0) { throw "FINGERPRINT_FAILED:$LASTEXITCODE" }
    $fingerprintLines | Out-File -LiteralPath (Join-Path $runEvidence "distribution-fingerprint.txt") -Encoding utf8
    $digestMatch = $fingerprintLines | Select-String -Pattern "^BRIDGE_RUNTIME_DISTRIBUTION_SHA256=([0-9a-f]{64})$"
    if ($null -eq $digestMatch) { throw "FINGERPRINT_DIGEST_MISSING" }
    $distribution = $digestMatch.Matches[0].Groups[1].Value
    $sourceDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourceJcm).Hash.ToLowerInvariant()

    Copy-Item -LiteralPath $sourceProject -Destination $stagedProject -Recurse -Force
    # JaCaMo/logback may resolve its file appender relative to the staged project. Keep that writable runtime
    # concern inside the disposable derived copy instead of changing the original case study.
    New-Item -ItemType Directory -Force -Path (Join-Path $stagedProject "log") | Out-Null
    # A normal JaCaMo Gradle launch depends on `classes`. The direct launcher below must preserve that generic
    # build step so case-provided artifact classes (for example src/env) remain discoverable. Build only the
    # disposable project copy and add its outputs to the producer JVM; the canonical source tree stays read-only.
    $stagedGradleWrapper = Join-Path $stagedProject "gradlew.bat"
    if ((Test-Path -LiteralPath $stagedGradleWrapper -PathType Leaf) -and
        (Test-Path -LiteralPath (Join-Path $stagedProject "build.gradle") -PathType Leaf)) {
        $projectBuildLog = Join-Path $runEvidence "runtime-project-build.log"
        Push-Location $stagedProject
        try {
            & $stagedGradleWrapper --no-daemon --console=plain classes 2>&1 |
                Tee-Object -FilePath $projectBuildLog | Out-Host
            if ($LASTEXITCODE -ne 0) { throw "RUNTIME_PROJECT_BUILD_FAILED:$LASTEXITCODE" }
        } finally {
            Pop-Location
        }
        $runtimeProjectClasses = Join-Path $stagedProject "build\classes\java\main"
        if (-not (Test-Path -LiteralPath $runtimeProjectClasses -PathType Container)) {
            throw "RUNTIME_PROJECT_CLASSES_MISSING:$runtimeProjectClasses"
        }
        $runtimeProjectBuild = "GRADLE_CLASSES_PASS"
        $runtimeResources = Join-Path $stagedProject "build\resources\main"
        $runtimeEntries = @($runtimeProjectClasses)
        if (Test-Path -LiteralPath $runtimeResources -PathType Container) { $runtimeEntries += $runtimeResources }
        $producerClasspath = [string]::Join([IO.Path]::PathSeparator, @($runtimeEntries + $producerClasspath))
    }
    [byte[]]$secret = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($secret)
    } finally {
        $random.Dispose()
    }
    $secretHex = [BitConverter]::ToString($secret).Replace('-', '').ToLowerInvariant()
    [IO.File]::WriteAllText($secretFile, $secretHex, [Text.UTF8Encoding]::new($false))
    New-Item -ItemType File -Force -Path $stopFile | Out-Null
    Remove-Item -LiteralPath $stopFile -Force

    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    $listener.Stop()
    $jcmText = [IO.File]::ReadAllText($jcmFile)
    $closing = $jcmText.LastIndexOf("}")
    if ($closing -lt 1) { throw "CANONICAL_HELLO_JCM_INVALID" }
    $platform = '    platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform("port={0}", "secretFile={1}", "jcmFile={2}", "distributionSha256={3}")' -f `
        $port, $secretFile.Replace('\', '/'), $jcmFile.Replace('\', '/'), $distribution
    $jcmText = $jcmText.Substring(0, $closing) + $platform + [Environment]::NewLine + $jcmText.Substring($closing)
    [IO.File]::WriteAllText($jcmFile, $jcmText, [Text.UTF8Encoding]::new($false))
    $injectedDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $jcmFile).Hash.ToLowerInvariant()

    $producer = Start-CapturedJava $producerClasspath "jason.infra.local.LiveJaCaMoLauncherMain" $stagedProject @(
        $jcmFile, $stopFile, $TimeoutSeconds.ToString(), $Headless.ToString().ToLowerInvariant())
    Wait-LoopbackPort $port $producer $TimeoutSeconds

    if ($InteractiveGui) {
        $guiInstall = Join-Path $stagedProject "use-gui-demo"
        $guiRuntime = Join-Path $guiInstall "target"
        $guiPlugins = Join-Path $guiInstall "lib\plugins"
        New-Item -ItemType Directory -Force -Path $guiRuntime, $guiPlugins | Out-Null
        $useResources = Join-Path $useRoot "use-core\src\main\resources"
        Copy-Item -LiteralPath (Join-Path $useResources "oclextensions") -Destination $guiInstall -Recurse -Force
        Copy-Item -LiteralPath (Join-Path $useResources "etc") -Destination $guiInstall -Recurse -Force
        $guiJarSource = Join-Path $useRoot "use-gui\target\use-gui.jar"
        $pluginJarSource = Join-Path $usePluginRoot "target\use-plugin-1.0.1.jar"
        if (-not (Test-Path -LiteralPath $guiJarSource -PathType Leaf)) { throw "GUI_JAR_MISSING:$guiJarSource" }
        if (-not (Test-Path -LiteralPath $pluginJarSource -PathType Leaf)) { throw "PLUGIN_JAR_MISSING:$pluginJarSource" }
        # USE resolves its installation home as the parent of the directory containing the executable JAR. Mirror
        # the source-tree layout (home/target/use-gui.jar and home/lib/plugins) in the disposable install.
        $guiJar = Join-Path $guiRuntime "use-gui.jar"
        $guiPlugin = Join-Path $guiPlugins "use-jacamo-plugin-1.0.1.jar"
        Copy-Item -LiteralPath $guiJarSource -Destination $guiJar -Force
        Copy-Item -LiteralPath $pluginJarSource -Destination $guiPlugin -Force
        if ((Get-FileHash -LiteralPath $pluginJarSource -Algorithm SHA256).Hash -ne
            (Get-FileHash -LiteralPath $guiPlugin -Algorithm SHA256).Hash) {
            throw "GUI_PLUGIN_STAGING_HASH_MISMATCH"
        }

        $guiArguments = @(
            "-Duser.dir=$guiInstall",
            "-Duse.jacamo.bridge.endpoint=tcp://127.0.0.1:$port",
            "-Duse.jacamo.bridge.secret-file=$secretFile",
            "-Duse.jacamo.bridge.distribution-sha256=$distribution",
            "-jar", $guiJar
        )
        $producerCommand = 'java -cp "{0}" {1} "{2}" "{3}" {4} {5}' -f $producerClasspath,
            "jason.infra.local.LiveJaCaMoLauncherMain", $jcmFile, $stopFile, $TimeoutSeconds,
            $Headless.ToString().ToLowerInvariant()
        $guiCommand = 'javaw "-Duser.dir={0}" "-Duse.jacamo.bridge.endpoint=tcp://127.0.0.1:{1}" "-Duse.jacamo.bridge.secret-file={2}" "-Duse.jacamo.bridge.distribution-sha256={3}" -jar "{4}"' -f `
            $guiInstall, $port, $secretFile, $distribution, $guiJar
        [IO.File]::WriteAllLines((Join-Path $runEvidence "launch-commands.txt"),
            @($producerCommand, $guiCommand), [Text.UTF8Encoding]::new($false))

        Write-Host "INTERACTIVE_GUI_READY"
        Write-Host "Open: Plugins > JaCaMo > Open Workbench..."
        Write-Host "Import this exact derived JCM: $jcmFile"
        Write-Host "The original source remains unchanged: $sourceJcm"
        Write-Host "Close the USE window to stop the derived JaCaMo producer."
        $gui = Start-InteractiveJava $guiInstall $guiArguments
        while (-not $gui.HasExited) {
            if ($producer.Process.HasExited) {
                throw "PRODUCER_EXITED_DURING_INTERACTIVE_GUI:`n$(Get-CapturedOutput $producer)"
            }
            Start-Sleep -Milliseconds 500
        }
        [IO.File]::WriteAllText((Join-Path $runEvidence "summary.json"), (@{
            status = "MANUAL_GUI_SESSION_COMPLETED"
            classification = "INTERACTIVE_DEMO_ONLY"
            caseName = $CaseName
            sourceJcm = $sourceJcm
            sourceJcmSha256 = $sourceDigest
            derivedJcm = $jcmFile
            projectKey = $ProjectKey
            distributionSha256 = $distribution
            port = $port
            guiInstall = $guiInstall
            headless = $Headless
        } | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
        Write-Host "INTERACTIVE_GUI_SESSION_COMPLETE evidence=$runEvidence"
        $runCompleted = $true
        return
    }

    $consumerClasspathFile = Join-Path $usePluginRoot "target\case-study-audit\classpath.txt"
    if (-not (Test-Path -LiteralPath $consumerClasspathFile -PathType Leaf)) {
        Invoke-MavenBuild @("-B", "-pl", "use-plugin", "-am", "dependency:build-classpath",
            "-Dmdep.outputFile=target/live-hello-consumer-classpath.txt", "-Dmdep.includeScope=test")
        $consumerClasspathFile = Join-Path $usePluginRoot "target\live-hello-consumer-classpath.txt"
    }
    if (-not (Test-Path -LiteralPath $consumerClasspathFile -PathType Leaf)) {
        throw "CONSUMER_CLASSPATH_MISSING:$consumerClasspathFile"
    }
    $consumerClasspath = Get-FilteredConsumerClasspath $consumerClasspathFile
    $producerCommand = 'java -cp "{0}" {1} "{2}" "{3}" {4} {5}' -f $producerClasspath,
        "jason.infra.local.LiveJaCaMoLauncherMain", $jcmFile, $stopFile, $TimeoutSeconds,
        $Headless.ToString().ToLowerInvariant()
    $consumerCommand = 'java -cp "{0}" {1} {2} "{3}" {4} "{5}" {6} "{7}" "{8}" "{9}" {10} {11} {12}' -f `
        $consumerClasspath, "org.tzi.use.plugins.jacamo.bridge.LiveBridgeConsumerMain", $port, $secretFile,
        $distribution, $stagedProject, $ProjectKey, $jcmFile, $useRoot, $runEvidence, $ObservationSeconds,
        $MaxBufferedEvents, $BridgeTimeoutMillis
    [IO.File]::WriteAllLines((Join-Path $runEvidence "launch-commands.txt"),
        @($producerCommand, $consumerCommand), [Text.UTF8Encoding]::new($false))
    $consumer = Start-CapturedJava $consumerClasspath "org.tzi.use.plugins.jacamo.bridge.LiveBridgeConsumerMain" $stagedProject @(
        $port.ToString(), $secretFile, $distribution, $stagedProject, $ProjectKey, $jcmFile, $useRoot,
        $runEvidence, $ObservationSeconds.ToString(), $MaxBufferedEvents.ToString(), $BridgeTimeoutMillis.ToString())
    if (-not $consumer.Process.WaitForExit($TimeoutSeconds * 1000)) {
        $consumer.Process.Kill($true)
        throw "CONSUMER_TIMEOUT"
    }
    $consumerOutput = Get-CapturedOutput $consumer
    $consumerOutput | Out-File -LiteralPath (Join-Path $runEvidence "consumer.log") -Encoding utf8
    if ($consumer.Process.ExitCode -ne 0) { throw "CONSUMER_FAILED:$($consumer.Process.ExitCode)`n$consumerOutput" }
    if ($consumerOutput -notmatch "REAL_BRIDGE_CONSUMER_OK") { throw "CONSUMER_EVIDENCE_MARKER_MISSING" }
    if ($consumerOutput -notmatch "facts=[1-9][0-9]*") { throw "CONSUMER_FACTS_EMPTY" }
    if ($consumerOutput -notmatch "objects=[1-9][0-9]*") { throw "CONSUMER_USE_OBJECTS_EMPTY" }
    if ($consumerOutput -notmatch "reconnect=true") { throw "CONSUMER_RECONNECT_EVIDENCE_MISSING" }
    if ($consumerOutput -notmatch "REAL_BRIDGE_FACADE_OK") { throw "CONSUMER_FACADE_EVIDENCE_MISSING" }

    $summary = @{
        status = "PASS"
        classification = "SUPPORTED_SCOPE_PASS"
        caseName = $CaseName
        producer = "separate-JVM JaCaMoLauncher + JaCaMoBridgePlatform"
        consumer = "separate-JVM USE workbench facade + production BridgeClient without JaCaMo runtime jars"
        sourceJcm = $sourceJcm
        sourceJcmSha256 = $sourceDigest
        injectedJcmSha256 = $injectedDigest
        projectKey = $ProjectKey
        distributionSha256 = $distribution
        port = $port
        stagedProject = $stagedProject
        observationSeconds = $ObservationSeconds
        maxBufferedEvents = $MaxBufferedEvents
        bridgeTimeoutMillis = $BridgeTimeoutMillis
        headless = $Headless
        runtimeProjectBuild = $runtimeProjectBuild
        runtimeProjectClasses = $runtimeProjectClasses
        detailedEvidence = (Join-Path $runEvidence "consumer-evidence.json")
        launchCommands = (Join-Path $runEvidence "launch-commands.txt")
        consumerEvidence = ($consumerOutput | Select-String -Pattern "REAL_BRIDGE_CONSUMER_OK.*").Line
        facadeEvidence = ($consumerOutput | Select-String -Pattern "REAL_BRIDGE_FACADE_OK.*").Line
    } | ConvertTo-Json -Depth 4
    [IO.File]::WriteAllText((Join-Path $runEvidence "summary.json"), $summary, [Text.UTF8Encoding]::new($false))
    Write-Host "REAL_LIVE_JACAMO_EVIDENCE_PASS case=$CaseName evidence=$runEvidence"
    $runCompleted = $true
} finally {
    if ($null -ne $producer) {
        if (-not $producer.Process.HasExited) {
            [IO.File]::WriteAllText($stopFile, "stop", [Text.UTF8Encoding]::new($false))
            if (-not $producer.Process.WaitForExit(20000)) { $producer.Process.Kill($true) }
        }
        try {
            Get-CapturedOutput $producer | Out-File -LiteralPath (Join-Path $runEvidence "producer.log") -Encoding utf8
        } catch { }
        try {
            $runtimeLog = Join-Path $stagedProject "log"
            if (Test-Path -LiteralPath $runtimeLog -PathType Container) {
                Copy-Item -LiteralPath $runtimeLog -Destination (Join-Path $runEvidence "runtime-log") -Recurse -Force
            }
        } catch { }
        if ($runCompleted -and $producer.Process.HasExited -and $producer.Process.ExitCode -ne 0) {
            throw "PRODUCER_FAILED:$($producer.Process.ExitCode)"
        }
    }
}

[CmdletBinding()]
param(
    [string]$JcmPath = "",
    [string]$EvidenceDirectory = "",
    [int]$TimeoutSeconds = 60,
    [int]$ObservationSeconds = 10,
    [int]$MaxBufferedEvents = 1024,
    [int]$BridgeTimeoutMillis = 60000,
    [bool]$Headless = $true,
    [switch]$InteractiveGui,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$useRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$usePluginRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($JcmPath)) { throw "JCM_PATH_REQUIRED" }
$sourceJcm = [IO.Path]::GetFullPath($JcmPath)
if (-not $sourceJcm.ToLowerInvariant().EndsWith(".jcm")) { throw "JCM_EXTENSION_REQUIRED:$sourceJcm" }
$sourceProject = Split-Path -Parent $sourceJcm
if (-not (Test-Path -LiteralPath $sourceProject -PathType Container)) { throw "JCM_PROJECT_ROOT_MISSING:$sourceProject" }
if (-not (Test-Path -LiteralPath $sourceJcm -PathType Leaf)) { throw "JCM_FILE_NOT_FOUND:$sourceJcm" }

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path $usePluginRoot "target\jacamo-bridge-evidence"
}
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$runEvidence = Join-Path $EvidenceDirectory $runId
New-Item -ItemType Directory -Force -Path $runEvidence | Out-Null
$projectStem = [IO.Path]::GetFileNameWithoutExtension($sourceJcm)
$safeProjectStem = $projectStem -replace '[^A-Za-z0-9_-]', '-'
$stagedProject = Join-Path ([IO.Path]::GetTempPath()) ("jacamo-bridge-" + $safeProjectStem + "-" + [Guid]::NewGuid().ToString("N"))
$jcmFile = Join-Path $stagedProject (Split-Path -Leaf $sourceJcm)
$secretFile = Join-Path $stagedProject "bridge-secret.hex"
$stopFile = Join-Path $stagedProject "stop.flag"
$producer = $null
$runCompleted = $false
$runtimeProjectBuild = "NOT_REQUIRED"
$runtimeProjectClasses = ""

function Invoke-MavenBuild([string[]]$Arguments) {
    & mvn @Arguments
    if ($LASTEXITCODE -ne 0) { throw "MAVEN_FAILED:$LASTEXITCODE" }
}

function ConvertTo-WindowsProcessArgument([AllowEmptyString()][string]$Argument) {
    if ($Argument.Length -gt 0 -and $Argument -notmatch '[\s"]') { return $Argument }

    $quoted = New-Object Text.StringBuilder
    [void]$quoted.Append('"')
    $backslashes = 0
    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq '\') {
            $backslashes++
            continue
        }
        if ($character -eq '"') {
            for ($index = 0; $index -lt (($backslashes * 2) + 1); $index++) {
                [void]$quoted.Append('\')
            }
            [void]$quoted.Append('"')
            $backslashes = 0
            continue
        }
        for ($index = 0; $index -lt $backslashes; $index++) { [void]$quoted.Append('\') }
        $backslashes = 0
        [void]$quoted.Append($character)
    }
    for ($index = 0; $index -lt ($backslashes * 2); $index++) { [void]$quoted.Append('\') }
    [void]$quoted.Append('"')
    return $quoted.ToString()
}

function Set-ProcessStartInfoArguments([Diagnostics.ProcessStartInfo]$StartInfo, [string[]]$Arguments) {
    # ArgumentList is available on modern .NET but not on Windows PowerShell 5.1/.NET Framework.
    if ($null -ne $StartInfo.PSObject.Properties['ArgumentList']) {
        foreach ($argument in $Arguments) { [void]$StartInfo.ArgumentList.Add($argument) }
        return
    }
    $StartInfo.Arguments = (($Arguments | ForEach-Object { ConvertTo-WindowsProcessArgument $_ }) -join ' ')
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
    Set-ProcessStartInfoArguments $info (@("-cp", $ClassPath, $MainClass) + $Arguments)
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
    return Start-Process -FilePath $java -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -PassThru
}

function Get-CapturedOutput($Captured) {
    return ($Captured.Output.GetAwaiter().GetResult() + $Captured.Error.GetAwaiter().GetResult())
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
        (Join-Path $usePluginRoot "target\classes")
    )
    return [string]::Join([IO.Path]::PathSeparator, @($required + $kept))
}

try {
    if ($TimeoutSeconds -lt 15) { throw "TIMEOUT_TOO_SHORT" }
    if (-not $InteractiveGui -and ($ObservationSeconds -lt 0 -or $ObservationSeconds -ge ($TimeoutSeconds - 5))) {
        throw "OBSERVATION_WINDOW_INVALID"
    }
    if ($MaxBufferedEvents -lt 1) { throw "BUFFER_LIMIT_INVALID" }
    if ($BridgeTimeoutMillis -lt 100) { throw "BRIDGE_TIMEOUT_INVALID" }

    if (-not $SkipBuild) {
        if ($InteractiveGui) {
            Invoke-MavenBuild @("-B", "-pl", "use-plugin", "-am", "-DskipTests",
                "-Dmaven.antrun.skip=true", "package")
        } else {
            Invoke-MavenBuild @("-B", "-pl", "use-plugin", "-am", "-DskipTests", "test-compile")
        }
        Invoke-MavenBuild @("-B", "-pl", "jacamo-bridge-jacamo", "-am", "dependency:build-classpath",
            "-Dmdep.outputFile=target/jacamo-bridge-classpath.txt", "-Dmdep.includeScope=test")
    }

    $bridgeClasspathFile = Join-Path $useRoot "jacamo-bridge-jacamo\target\jacamo-bridge-classpath.txt"
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
    New-Item -ItemType Directory -Force -Path (Join-Path $stagedProject "log") | Out-Null
    $stagedGradleWrapper = Join-Path $stagedProject "gradlew.bat"
    if ((Test-Path -LiteralPath $stagedGradleWrapper -PathType Leaf) -and
        (Test-Path -LiteralPath (Join-Path $stagedProject "build.gradle") -PathType Leaf)) {
        $projectBuildLog = Join-Path $runEvidence "runtime-project-build.log"
        Push-Location $stagedProject
        try {
            # Windows PowerShell 5.1 promotes native stderr records to terminating errors when
            # ErrorActionPreference is Stop, even when Gradle exits successfully. javac writes
            # ordinary diagnostics such as "uses unchecked or unsafe operations" to stderr, so
            # capture both streams at the process boundary and judge the build by its exit code.
            $gradleStartInfo = New-Object System.Diagnostics.ProcessStartInfo
            $gradleStartInfo.FileName = $env:ComSpec
            $gradleStartInfo.Arguments = '/d /s /c ""{0}" --no-daemon --console=plain classes"' -f `
                $stagedGradleWrapper.Replace('"', '""')
            $gradleStartInfo.WorkingDirectory = $stagedProject
            $gradleStartInfo.UseShellExecute = $false
            $gradleStartInfo.CreateNoWindow = $true
            $gradleStartInfo.RedirectStandardOutput = $true
            $gradleStartInfo.RedirectStandardError = $true

            $gradleProcess = New-Object System.Diagnostics.Process
            $gradleProcess.StartInfo = $gradleStartInfo
            try {
                if (-not $gradleProcess.Start()) { throw "RUNTIME_PROJECT_BUILD_START_FAILED" }
                $gradleStdoutTask = $gradleProcess.StandardOutput.ReadToEndAsync()
                $gradleStderrTask = $gradleProcess.StandardError.ReadToEndAsync()
                $gradleProcess.WaitForExit()
                $gradleStdout = $gradleStdoutTask.GetAwaiter().GetResult()
                $gradleStderr = $gradleStderrTask.GetAwaiter().GetResult()
                $gradleExitCode = $gradleProcess.ExitCode
            } finally {
                $gradleProcess.Dispose()
            }

            $gradleOutput = @($gradleStdout, $gradleStderr) |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            $gradleOutput | Out-File -LiteralPath $projectBuildLog -Encoding utf8
            $gradleOutput | Out-Host
            if ($gradleExitCode -ne 0) { throw "RUNTIME_PROJECT_BUILD_FAILED:$gradleExitCode" }
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
    try { $random.GetBytes($secret) } finally { $random.Dispose() }
    [IO.File]::WriteAllText($secretFile, [BitConverter]::ToString($secret).Replace('-', '').ToLowerInvariant(),
        [Text.UTF8Encoding]::new($false))
    New-Item -ItemType File -Force -Path $stopFile | Out-Null
    Remove-Item -LiteralPath $stopFile -Force

    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    $listener.Stop()
    $jcmText = [IO.File]::ReadAllText($jcmFile)
    $closing = $jcmText.LastIndexOf("}")
    if ($closing -lt 1) { throw "JCM_ROOT_OBJECT_INVALID" }
    $platform = '    platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform("port={0}", "secretFile={1}", "jcmFile={2}", "distributionSha256={3}")' -f `
        $port, $secretFile.Replace('\', '/'), $jcmFile.Replace('\', '/'), $distribution
    $jcmText = $jcmText.Substring(0, $closing) + $platform + [Environment]::NewLine + $jcmText.Substring($closing)
    [IO.File]::WriteAllText($jcmFile, $jcmText, [Text.UTF8Encoding]::new($false))
    $injectedDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $jcmFile).Hash.ToLowerInvariant()

    $producerHeadless = if ($InteractiveGui) { $false } else { $Headless }
    $producer = Start-CapturedJava $producerClasspath "jason.infra.local.LiveJaCaMoLauncherMain" $stagedProject @(
        $jcmFile, $stopFile, $TimeoutSeconds.ToString(), $producerHeadless.ToString().ToLowerInvariant())
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
        $guiJar = Join-Path $guiRuntime "use-gui.jar"
        $guiPlugin = Join-Path $guiPlugins "use-jacamo-plugin-1.0.1.jar"
        $guiReadyFile = Join-Path $runEvidence "gui-ready.json"
        Copy-Item -LiteralPath $guiJarSource -Destination $guiJar -Force
        Copy-Item -LiteralPath $pluginJarSource -Destination $guiPlugin -Force
        if ((Get-FileHash -LiteralPath $pluginJarSource -Algorithm SHA256).Hash -ne
            (Get-FileHash -LiteralPath $guiPlugin -Algorithm SHA256).Hash) {
            throw "GUI_PLUGIN_STAGING_HASH_MISMATCH"
        }
        $guiArguments = @(
            "-Duser.dir=$guiInstall",
            "-Duse.plugin.auto-action-id=org.tzi.use.plugins.jacamo.workbench.action",
            "-Duse.jacamo.workbench.project-file=$jcmFile",
            "-Duse.jacamo.workbench.auto-import=true",
            "-Duse.jacamo.workbench.ready-file=$guiReadyFile",
            "-Duse.jacamo.bridge.endpoint=tcp://127.0.0.1:$port",
            "-Duse.jacamo.bridge.secret-file=$secretFile",
            "-Duse.jacamo.bridge.distribution-sha256=$distribution",
            "-jar", $guiJar
        )
        [IO.File]::WriteAllLines((Join-Path $runEvidence "launch-commands.txt"),
            @("java -cp <official-runtime> jason.infra.local.LiveJaCaMoLauncherMain $jcmFile $stopFile",
              "javaw -Duse.plugin.auto-action-id=org.tzi.use.plugins.jacamo.workbench.action -Duse.jacamo.workbench.auto-import=true -Duse.jacamo.workbench.project-file=$jcmFile -jar $guiJar"),
            [Text.UTF8Encoding]::new($false))
        Write-Host "INTERACTIVE_GUI_READY"
        Write-Host "The JaCaMo Workbench is opening and importing the derived JCM automatically."
        Write-Host "Derived JCM: $jcmFile"
        Write-Host "Original source remains unchanged: $sourceJcm"
        Write-Host "Close the USE window to stop the derived JaCaMo producer."
        $gui = Start-InteractiveJava $guiInstall $guiArguments
        $guiReadyReported = $false
        while (-not $gui.HasExited) {
            if ($producer.Process.HasExited) { throw "PRODUCER_EXITED_DURING_INTERACTIVE_GUI:`n$(Get-CapturedOutput $producer)" }
            if (-not $guiReadyReported -and (Test-Path -LiteralPath $guiReadyFile -PathType Leaf)) {
                Write-Host "INTERACTIVE_GUI_MODEL_READY evidence=$guiReadyFile"
                Get-Content -Raw -LiteralPath $guiReadyFile | Write-Host
                $guiReadyReported = $true
            }
            Start-Sleep -Milliseconds 500
        }
        [IO.File]::WriteAllText((Join-Path $runEvidence "summary.json"), (@{
            status = "MANUAL_GUI_SESSION_COMPLETED"
            classification = "INTERACTIVE_DEMO_ONLY"
            sourceJcm = $sourceJcm
            sourceJcmSha256 = $sourceDigest
            derivedJcm = $jcmFile
            pipeline = "CODE_GROUNDED_NATIVE"
            distributionSha256 = $distribution
            port = $port
        } | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
        Write-Host "JACAMO_BRIDGE_GUI_SESSION_COMPLETE evidence=$runEvidence"
        $runCompleted = $true
        return
    }

    $consumerClasspath = Get-FilteredConsumerClasspath $bridgeClasspathFile
    $consumer = Start-CapturedJava $consumerClasspath "org.tzi.use.plugins.jacamo.bridge.JaCaMoBridgeNativeConsumerMain" $stagedProject @(
        $port.ToString(), $secretFile, $distribution, $jcmFile, $useRoot, $runEvidence,
        $ObservationSeconds.ToString(), $MaxBufferedEvents.ToString(), $BridgeTimeoutMillis.ToString())
    if (-not $consumer.Process.WaitForExit($TimeoutSeconds * 1000)) {
        $consumer.Process.Kill($true)
        throw "CONSUMER_TIMEOUT"
    }
    $consumerOutput = Get-CapturedOutput $consumer
    $consumerOutput | Out-File -LiteralPath (Join-Path $runEvidence "consumer.log") -Encoding utf8
    if ($consumer.Process.ExitCode -ne 0) { throw "CONSUMER_FAILED:$($consumer.Process.ExitCode)`n$consumerOutput" }
    if ($consumerOutput -notmatch "JACAMO_BRIDGE_NATIVE_OK") { throw "NATIVE_EVIDENCE_MARKER_MISSING" }
    if ($consumerOutput -notmatch "pipeline=CODE_GROUNDED_NATIVE") { throw "NATIVE_PIPELINE_MARKER_MISSING" }

    [IO.File]::WriteAllText((Join-Path $runEvidence "launch-commands.txt"),
        "java -cp <official-runtime> jason.infra.local.LiveJaCaMoLauncherMain $jcmFile`n" +
        "java -cp <native-consumer> org.tzi.use.plugins.jacamo.bridge.JaCaMoBridgeNativeConsumerMain ...",
        [Text.UTF8Encoding]::new($false))
    $summary = @{
        status = "PASS"
        classification = "GENERIC_JCM_SUPPORTED_SCOPE_PASS"
        pipeline = "CODE_GROUNDED_NATIVE"
        sourceJcm = $sourceJcm
        sourceJcmSha256 = $sourceDigest
        injectedJcmSha256 = $injectedDigest
        derivedJcm = $jcmFile
        distributionSha256 = $distribution
        stagedProject = $stagedProject
        observationSeconds = $ObservationSeconds
        maxBufferedEvents = $MaxBufferedEvents
        bridgeTimeoutMillis = $BridgeTimeoutMillis
        runtimeProjectBuild = $runtimeProjectBuild
        runtimeProjectClasses = $runtimeProjectClasses
        evidence = (Join-Path $runEvidence "summary.json")
    } | ConvertTo-Json -Depth 4
    [IO.File]::WriteAllText((Join-Path $runEvidence "launcher-summary.json"), $summary, [Text.UTF8Encoding]::new($false))
    Write-Host "JACAMO_BRIDGE_NATIVE_PASS evidence=$runEvidence"
    $runCompleted = $true
} finally {
    if ($null -ne $producer) {
        if (-not $producer.Process.HasExited) {
            [IO.File]::WriteAllText($stopFile, "stop", [Text.UTF8Encoding]::new($false))
            if (-not $producer.Process.WaitForExit(20000)) { $producer.Process.Kill($true) }
        }
        try { Get-CapturedOutput $producer | Out-File -LiteralPath (Join-Path $runEvidence "producer.log") -Encoding utf8 } catch { }
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

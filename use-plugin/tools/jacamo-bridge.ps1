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
    [switch]$PrepareOnly,
    [string]$PreparationResultPath = "",
    [string]$PreparationId = "",
    [switch]$SkipBuild,
    [string]$OclProfilePath = "",
    [ValidateSet("AUTO", "FULL")]
    [string]$ProjectionMode = "AUTO",
    [ValidateSet("NATIVE_CURRENT", "JACAMO_DECLARATIVE_XML", "USE4GOAL_OBSERVED_V1", "AUCTION_OBSERVED_V1", "CSL_OBSERVER_V1", "CSL_OBSERVER_V2")]
    [string]$SemanticProfile = "NATIVE_CURRENT",
    [string]$MappingSpecificationPath = "",
    [string]$AuthoredOclPath = "",
    [string]$AuthoredCslPath = "",
    [string]$AuthoredIstarPath = "",
    [ValidateRange(32768, 1073741824)]
    [long]$RuntimeJournalMaxBytes = 67108864,
    [ValidateSet("AUTO", "WORLD", "FULL")]
    [string]$ObservationProfile = "AUTO",
    [string]$RuntimeClasspathFile = "",
    [switch]$AuctionStepMarkers,
    [switch]$AuctionSourceClock,
    [switch]$AuctionSchemeObserver
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProjectionMode = $ProjectionMode.ToUpperInvariant()
$SemanticProfile = $SemanticProfile.ToUpperInvariant()
$ObservationProfile = $ObservationProfile.ToUpperInvariant()
if ($ObservationProfile -eq "AUTO") {
    # The original observed profile maps world/organisation outcomes, not Jason
    # mental/execution internals. Keep the rich trace default for old profiles.
    $ObservationProfile = if ($SemanticProfile -in @("USE4GOAL_OBSERVED_V1", "AUCTION_OBSERVED_V1", "JACAMO_DECLARATIVE_XML")) { "WORLD" } else { "FULL" }
}
if ($SemanticProfile -eq "AUCTION_OBSERVED_V1") {
    # Explicit profile selection opts into the pinned source observers; no case
    # is inferred from the JCM name/path. I04/T02 measured the full journal load.
    $AuctionStepMarkers = $true
    $AuctionSourceClock = $true
    $AuctionSchemeObserver = $true
    if (-not $PSBoundParameters.ContainsKey('RuntimeJournalMaxBytes')) { $RuntimeJournalMaxBytes = 268435456 }
}
if ($SemanticProfile -eq "JACAMO_DECLARATIVE_XML" -and -not $PSBoundParameters.ContainsKey('RuntimeJournalMaxBytes')) {
    # Declarative observers retain exact source cuts and original-engine history.
    # Keep a finite archive budget; callers may select a smaller/larger explicit bound.
    $RuntimeJournalMaxBytes = 268435456
}
$producerObservationArguments = @("-Djacamo.bridge.structured-observation=$(([bool]($ObservationProfile -eq 'FULL')).ToString().ToLowerInvariant())")
if ($AuctionStepMarkers) { $producerObservationArguments += '-Djacamo.bridge.auction.step-markers=true' }
if ($AuctionSourceClock) { $producerObservationArguments += '-Djacamo.bridge.auction.clock=true' }
if ($AuctionSchemeObserver) { $producerObservationArguments += '-Djacamo.bridge.auction.scheme-observer=true' }
$semanticJvmArguments = @("-Duse.jacamo.semantics.profile=$SemanticProfile", "-Duse.jacamo.runtime.journal.maxBytes=$RuntimeJournalMaxBytes")
if ($PrepareOnly -and ($InteractiveGui -or [string]::IsNullOrWhiteSpace($PreparationResultPath) -or $PreparationId -notmatch '^[0-9a-f-]{36}$')) { throw 'GUI_PREPARATION_OWNER_REQUIRED' }
if (([string]::IsNullOrWhiteSpace($AuthoredCslPath) -ne [string]::IsNullOrWhiteSpace($AuthoredIstarPath)) -or
    ((-not [string]::IsNullOrWhiteSpace($AuthoredCslPath)) -and $SemanticProfile -ne 'JACAMO_DECLARATIVE_XML')) { throw 'AUTHORED_MODEL_SELECTION_PAIR_AND_DECLARATIVE_MODE_REQUIRED' }
if (-not [string]::IsNullOrWhiteSpace($AuthoredCslPath)) {
    foreach ($entry in @(@($AuthoredCslPath,'.csl'),@($AuthoredIstarPath,'.istar'))) {
        if (-not (Test-Path -LiteralPath $entry[0] -PathType Leaf) -or -not $entry[0].EndsWith($entry[1],[StringComparison]::OrdinalIgnoreCase)) { throw 'AUTHORED_MODEL_FILE_INVALID' }
    }
    $semanticJvmArguments += @("-Duse.jacamo.mapping.csl=$([IO.Path]::GetFullPath($AuthoredCslPath))","-Duse.jacamo.mapping.istar=$([IO.Path]::GetFullPath($AuthoredIstarPath))")
}
if ($SemanticProfile -ne "NATIVE_CURRENT") {
    if ([string]::IsNullOrWhiteSpace($MappingSpecificationPath)) { throw "AUTHORED_SPECIFICATION_FILE_REQUIRED:$SemanticProfile" }
    $semanticSpecification = [IO.Path]::GetFullPath($MappingSpecificationPath)
    if (-not (Test-Path -LiteralPath $semanticSpecification -PathType Leaf) -or (Get-Item -LiteralPath $semanticSpecification).Length -gt 16777216) { throw "AUTHORED_SPECIFICATION_FILE_INVALID:$semanticSpecification" }
    if ($SemanticProfile -in @("AUCTION_OBSERVED_V1", "JACAMO_DECLARATIVE_XML") -and -not $semanticSpecification.EndsWith('.xml', [StringComparison]::OrdinalIgnoreCase)) { throw "AUCTION_XML_SPECIFICATION_REQUIRED:$semanticSpecification" }
    if ($SemanticProfile -eq "JACAMO_DECLARATIVE_XML") {
        if ([string]::IsNullOrWhiteSpace($AuthoredOclPath)) { throw "AUTHORED_OCL_FILE_REQUIRED:$SemanticProfile" }
        $authoredOcl = [IO.Path]::GetFullPath($AuthoredOclPath)
        if (-not (Test-Path -LiteralPath $authoredOcl -PathType Leaf) -or -not $authoredOcl.EndsWith('.ocl', [StringComparison]::OrdinalIgnoreCase)) { throw "AUTHORED_OCL_FILE_INVALID:$authoredOcl" }
        $semanticJvmArguments += @("-Duse.jacamo.mapping.xml=$semanticSpecification", "-Duse.jacamo.mapping.ocl=$authoredOcl")
    } else {
        $semanticProperty = if ($SemanticProfile -eq "AUCTION_OBSERVED_V1") { "use.jacamo.auction.specification" } elseif ($SemanticProfile -eq "USE4GOAL_OBSERVED_V1") { "use.jacamo.mapping.rules" } else { "use.jacamo.semantics.config" }
        $semanticJvmArguments += "-D${semanticProperty}=$semanticSpecification"
    }
} elseif (-not [string]::IsNullOrWhiteSpace($MappingSpecificationPath)) { throw "NATIVE_PROFILE_HAS_NO_AUTHORED_SPECIFICATION" }
elseif (-not [string]::IsNullOrWhiteSpace($AuthoredOclPath)) { throw "NATIVE_PROFILE_HAS_NO_AUTHORED_OCL" }

$usePluginRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$installedLayout = Test-Path -LiteralPath (Join-Path $usePluginRoot "bridge\lib\jacamo-bridge-jacamo-1.0.0.jar") -PathType Leaf
$useRoot = if ($installedLayout) { $usePluginRoot } else { (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path }
if ([string]::IsNullOrWhiteSpace($JcmPath)) { throw "JCM_PATH_REQUIRED" }
$sourceJcm = [IO.Path]::GetFullPath($JcmPath)
if (-not $sourceJcm.ToLowerInvariant().EndsWith(".jcm")) { throw "JCM_EXTENSION_REQUIRED:$sourceJcm" }
$sourceProject = Split-Path -Parent $sourceJcm
if (-not (Test-Path -LiteralPath $sourceProject -PathType Container)) { throw "JCM_PROJECT_ROOT_MISSING:$sourceProject" }
if (-not (Test-Path -LiteralPath $sourceJcm -PathType Leaf)) { throw "JCM_FILE_NOT_FOUND:$sourceJcm" }

# External profiles remain in USE (or another caller-selected location), never in the staged JCM.
$externalOcl = ""
if (-not [string]::IsNullOrWhiteSpace($OclProfilePath)) {
    $externalOcl = [IO.Path]::GetFullPath($OclProfilePath)
    if (-not $externalOcl.ToLowerInvariant().EndsWith(".ocl") -or
        -not (Test-Path -LiteralPath $externalOcl -PathType Leaf)) {
        throw "OCL_PROFILE_NOT_FOUND_OR_INVALID:$externalOcl"
    }
    if ($InteractiveGui) { throw "OCL_AUTO_LOAD_HEADLESS_ONLY: use the Workbench profile loader in interactive mode" }
}

$persistEvidence = -not [string]::IsNullOrWhiteSpace($EvidenceDirectory)
if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $EvidenceDirectory = Join-Path $usePluginRoot "target\jacamo-runtime"
}
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$runId = (Get-Date -Format "yyyyMMdd-HHmmss-fff") + "-" + ([Guid]::NewGuid().ToString("N").Substring(0, 8))
$runEvidence = Join-Path $EvidenceDirectory $runId
New-Item -ItemType Directory -Force -Path $runEvidence | Out-Null
$runtimeDirectory = Join-Path $runEvidence "runtime"
$layoutDirectory = Join-Path $runEvidence "layout"
$projectStem = [IO.Path]::GetFileNameWithoutExtension($sourceJcm)
$safeProjectStem = $projectStem -replace '[^A-Za-z0-9_-]', '-'
# Keep the derived project inside the managed run directory.  It is deleted in
# finally, while persistent evidence keeps only the explicitly exported files.
$stagedProject = Join-Path $runEvidence ("staging-" + $safeProjectStem)
$jcmFile = Join-Path $stagedProject (Split-Path -Leaf $sourceJcm)
$secretFile = Join-Path $stagedProject "bridge-secret.hex"
$stopFile = Join-Path $stagedProject "stop.flag"
$consumerReadyFile = if ([string]::IsNullOrWhiteSpace($externalOcl) -and $SemanticProfile -ne 'JACAMO_DECLARATIVE_XML') { "" } else { Join-Path $stagedProject "consumer-ready.flag" }
$observationPlan = ""
if ($SemanticProfile -eq 'JACAMO_DECLARATIVE_XML') {
    if ($AuctionStepMarkers -or $AuctionSourceClock -or $AuctionSchemeObserver) { throw 'DECLARATIVE_PROFILE_FORBIDS_LEGACY_OBSERVERS' }
    $observationPlan = Join-Path $runEvidence 'observation-requests.json'
    $producerObservationArguments += "-Djacamo.bridge.observation.plan=$observationPlan"
    $semanticJvmArguments += "-Djacamo.bridge.observation.plan=$observationPlan"
}
$startupControlDirectory = if ($InteractiveGui -or $PrepareOnly) { Join-Path $runEvidence "startup-control" } else { "" }
if ($InteractiveGui -or $PrepareOnly) { New-Item -ItemType Directory -Path $startupControlDirectory | Out-Null }
$producer = $null
$consumer = $null
$gui = $null
$runCompleted = $false
$producerFailure = $null
$handoffPrepared = $false
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
                            [string[]]$Arguments, [string[]]$JvmArguments = @()) {
    $java = (Get-Command java -ErrorAction Stop).Source
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $java
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    Set-ProcessStartInfoArguments $info (@($JvmArguments) + @("-cp", $ClassPath, $MainClass) + $Arguments)
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
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $java
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    Set-ProcessStartInfoArguments $info $Arguments
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw "GUI_PROCESS_START_FAILED" }
    return $process
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
    # Both profiles need the selected engine and its ANTLR4 runtime. Use the
    # tested shaded artifact and host, without framework/test/source directories.
    $packagedHost = Join-Path $useRoot "lib\use-gui.jar"
    $packagedPlugin = Join-Path $useRoot "lib\plugins\use-jacamo-plugin-1.0.1.jar"
    $consumerHostArtifact = if (Test-Path -LiteralPath $packagedHost -PathType Leaf) { $packagedHost } else { Join-Path $useRoot "use-gui\target\use-gui.jar" }
    $consumerPluginArtifact = if (Test-Path -LiteralPath $packagedPlugin -PathType Leaf) { $packagedPlugin } else { Join-Path $usePluginRoot "target\use-plugin-1.0.1.jar" }
    $required = @(
        $consumerHostArtifact,
        $consumerPluginArtifact
    )
    foreach ($artifact in $required) { if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "CONSUMER_ARTIFACT_MISSING:$artifact" } }
    return [string]::Join([IO.Path]::PathSeparator, $required)
}

try {
    if ($TimeoutSeconds -lt 15) { throw "TIMEOUT_TOO_SHORT" }
    if (-not $InteractiveGui -and ($ObservationSeconds -lt 0 -or $ObservationSeconds -ge ($TimeoutSeconds - 5))) {
        throw "OBSERVATION_WINDOW_INVALID"
    }
    if ($MaxBufferedEvents -lt 1) { throw "BUFFER_LIMIT_INVALID" }
    if ($BridgeTimeoutMillis -lt 100) { throw "BRIDGE_TIMEOUT_INVALID" }

    if (-not $SkipBuild -and -not $installedLayout) {
        if ($InteractiveGui) {
            Invoke-MavenBuild @("-B", "-pl", "use-plugin", "-am", "-Dmaven.test.skip=true",
                "-Dmaven.antrun.skip=true", "package")
        } else {
            Invoke-MavenBuild @("-B", "-pl", "use-plugin", "-am", "-Dmaven.test.skip=true", "-Dmaven.antrun.skip=true", "package")
        }
        Invoke-MavenBuild @("-B", "-pl", "jacamo-bridge-jacamo", "-am", "dependency:build-classpath",
            "-Dmdep.outputFile=target/jacamo-bridge-classpath.txt", "-Dmdep.includeScope=runtime")
    }

    $bridgeClasspathFile = if (-not [string]::IsNullOrWhiteSpace($RuntimeClasspathFile)) { [IO.Path]::GetFullPath($RuntimeClasspathFile) } else { Join-Path $useRoot "jacamo-bridge-jacamo\target\jacamo-bridge-classpath.txt" }
    if (-not (Test-Path -LiteralPath $bridgeClasspathFile -PathType Leaf)) {
        throw "BRIDGE_CLASSPATH_MISSING:$bridgeClasspathFile"
    }
    $bridgeDependencies = (Get-Content -Raw -LiteralPath $bridgeClasspathFile).Trim()
    if ($installedLayout) {
        # Runtime libraries are user-supplied; release Bridge JARs own the contract/adapters.
        $bridgeDependencies = [string]::Join([IO.Path]::PathSeparator, @($bridgeDependencies.Split([IO.Path]::PathSeparator) | Where-Object {
            (Test-Path -LiteralPath $_ -PathType Leaf) -and $_.EndsWith('.jar', [StringComparison]::OrdinalIgnoreCase) -and
            $_ -notmatch '[\\/]org[\\/]tzi[\\/]use[\\/]' -and $_ -notmatch 'jacamo-bridge-(contract|jacamo)'
        }))
    }
    $contractRuntime = if ($installedLayout) { Join-Path $useRoot "bridge\lib\jacamo-bridge-contract-1.0.0.jar" } else { Join-Path $useRoot "jacamo-bridge-contract\target\classes" }
    $adapterRuntime = if ($installedLayout) { Join-Path $useRoot "bridge\lib\jacamo-bridge-jacamo-1.0.0.jar" } else { Join-Path $useRoot "jacamo-bridge-jacamo\target\classes" }
    $producerClasspath = [string]::Join([IO.Path]::PathSeparator, @(
        $contractRuntime,
        $adapterRuntime,
        $bridgeDependencies
    ))

    $fingerprintLines = & java -cp $producerClasspath org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain
    if ($LASTEXITCODE -ne 0) { throw "FINGERPRINT_FAILED:$LASTEXITCODE" }
    $fingerprintLines | Out-File -LiteralPath (Join-Path $runEvidence "distribution-fingerprint.txt") -Encoding utf8
    $digestMatch = $fingerprintLines | Select-String -Pattern "^BRIDGE_RUNTIME_DISTRIBUTION_SHA256=([0-9a-f]{64})$"
    if ($null -eq $digestMatch) { throw "FINGERPRINT_DIGEST_MISSING" }
    $distribution = $digestMatch.Matches[0].Groups[1].Value
    $sourceDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourceJcm).Hash.ToLowerInvariant()

    if ($PrepareOnly -and ($runEvidence + [IO.Path]::DirectorySeparatorChar).StartsWith($sourceProject.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'GUI_STAGING_MUST_BE_OUTSIDE_SELECTED_SOURCE_PROJECT' }
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
        if (Test-Path -LiteralPath $runtimeProjectClasses -PathType Container) {
            $runtimeProjectBuild = "GRADLE_CLASSES_PASS"
        } elseif ($gradleStdout -match '(?m)^> Task :compileJava NO-SOURCE\s*$') {
            # A valid JaCaMo project may contain only ASL/JCM/XML resources and no custom
            # Java environment classes. Gradle reports compileJava NO-SOURCE in that case;
            # the official runtime classpath is still sufficient to launch the project.
            $runtimeProjectBuild = "GRADLE_CLASSES_NOT_REQUIRED"
            Write-Host "RUNTIME_PROJECT_CLASSES_NOT_REQUIRED:$runtimeProjectClasses"
        } else {
            throw "RUNTIME_PROJECT_CLASSES_MISSING:$runtimeProjectClasses"
        }
        $runtimeResources = Join-Path $stagedProject "build\resources\main"
        $runtimeEntries = @()
        if (Test-Path -LiteralPath $runtimeProjectClasses -PathType Container) { $runtimeEntries += $runtimeProjectClasses }
        if (Test-Path -LiteralPath $runtimeResources -PathType Container) { $runtimeEntries += $runtimeResources }
        $producerClasspath = [string]::Join([IO.Path]::PathSeparator, @($runtimeEntries + $producerClasspath))
    } elseif (Test-Path -LiteralPath (Join-Path $stagedProject "src\env") -PathType Container) {
        $environmentSources = @(Get-ChildItem -LiteralPath (Join-Path $stagedProject "src\env") -Filter '*.java' -File -Recurse | ForEach-Object FullName)
        if ($environmentSources.Count -gt 0) {
            $runtimeProjectClasses = Join-Path $stagedProject "build\classes\java\main"
            New-Item -ItemType Directory -Force -Path $runtimeProjectClasses | Out-Null
            $javac = Join-Path (Split-Path -Parent (Get-Command java -ErrorAction Stop).Source) "javac.exe"
            & $javac -proc:none -encoding UTF-8 -cp $producerClasspath -d $runtimeProjectClasses @environmentSources
            if ($LASTEXITCODE -ne 0) { throw "ENVIRONMENT_JAVA_COMPILE_FAILED:$LASTEXITCODE" }
            $runtimeProjectBuild = "JAVAC_ENVIRONMENT_CLASSES_PASS"
            $producerClasspath = [string]::Join([IO.Path]::PathSeparator, @($runtimeProjectClasses, $producerClasspath))
        }
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

    if ($PrepareOnly) {
        # Transfer only prepared paths/arguments to the existing USE JVM. The GUI
        # owns the actual Java child and bounded stream readers; no second USE.
        $prepared = @{
            version = 'OWNED_JACAMO_PREPARATION/1.0'; preparationId = $PreparationId
            runId = $runId; sourceJcm = $sourceJcm; sourceSha256 = $sourceDigest
            derivedJcm = $jcmFile; derivedSha256 = $injectedDigest
            runEvidence = $runEvidence; stagedProject = $stagedProject; classpath = $producerClasspath
            stopFile = $stopFile; secretFile = $secretFile; startupDirectory = $startupControlDirectory
            observationPlan = $observationPlan; endpoint = "tcp://127.0.0.1:$port"; distributionSha256 = $distribution
            producerJvmArguments = @($producerObservationArguments); startupSeconds = $TimeoutSeconds
        }
        $destination = [IO.Path]::GetFullPath($PreparationResultPath)
        [IO.File]::WriteAllText($destination + '.tmp',($prepared | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath ($destination + '.tmp') -Destination $destination
        $handoffPrepared = $true
        Write-Host 'GUI_JACAMO_PREPARED_NO_PROCESS_STARTED'
        return
    }

    $producerHeadless = if ($InteractiveGui) { $false } else { $Headless }
    # GUI lifetime follows the USE window. TimeoutSeconds still bounds startup and headless runs.
    $producerLifetimeSeconds = if ($InteractiveGui) { 0 } else { $TimeoutSeconds }
    $producer = Start-CapturedJava $producerClasspath "jason.infra.local.LiveJaCaMoLauncherMain" $stagedProject @(
        $jcmFile, $stopFile, $producerLifetimeSeconds.ToString(), $producerHeadless.ToString().ToLowerInvariant(),
        $consumerReadyFile, $TimeoutSeconds.ToString(), $startupControlDirectory, $runId) -JvmArguments $producerObservationArguments
    Wait-LoopbackPort $port $producer $TimeoutSeconds

    if ($InteractiveGui) {
        $guiInstall = Join-Path $stagedProject "use-gui-demo"
        $guiRuntime = Join-Path $guiInstall "target"
        $guiPlugins = Join-Path $guiInstall "lib\plugins"
        New-Item -ItemType Directory -Force -Path $guiRuntime, $guiPlugins | Out-Null
        $useResources = if ($installedLayout) { $useRoot } else { Join-Path $useRoot "use-core\src\main\resources" }
        Copy-Item -LiteralPath (Join-Path $useResources "oclextensions") -Destination $guiInstall -Recurse -Force
        Copy-Item -LiteralPath (Join-Path $useResources "etc") -Destination $guiInstall -Recurse -Force
        $guiJarSource = if ($installedLayout) { Join-Path $useRoot "lib\use-gui.jar" } else { Join-Path $useRoot "use-gui\target\use-gui.jar" }
        $pluginJarSource = if ($installedLayout) { Join-Path $useRoot "lib\plugins\use-jacamo-plugin-1.0.1.jar" } else { Join-Path $usePluginRoot "target\use-plugin-1.0.1.jar" }
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
        $guiArguments = $semanticJvmArguments + @(
            "-Duser.dir=$guiInstall",
            "-Duse.jacamo.runtime.root=$runtimeDirectory",
            "-Duse.jacamo.projection.mode=$ProjectionMode",
            "-Duse.gui.default-layout-directory=$layoutDirectory",
            "-Duse.plugin.auto-action-id=org.tzi.use.plugins.jacamo.workbench.action",
            "-Duse.jacamo.workbench.project-file=$jcmFile",
            "-Duse.jacamo.workbench.auto-import=true",
            "-Duse.jacamo.workbench.ready-file=$guiReadyFile",
            "-Duse.jacamo.startup.directory=$startupControlDirectory",
            "-Duse.jacamo.startup.run-id=$runId",
            "-Duse.jacamo.bridge.endpoint=tcp://127.0.0.1:$port",
            "-Duse.jacamo.bridge.secret-file=$secretFile",
            "-Duse.jacamo.bridge.distribution-sha256=$distribution",
            "-jar", $guiJar
        )
        [IO.File]::WriteAllLines((Join-Path $runEvidence "launch-commands.txt"),
            @("java -cp <official-runtime> jason.infra.local.LiveJaCaMoLauncherMain $jcmFile $stopFile",
              "javaw -Duse.jacamo.projection.mode=$ProjectionMode -Duse.plugin.auto-action-id=org.tzi.use.plugins.jacamo.workbench.action -Duse.jacamo.workbench.auto-import=true -Duse.jacamo.workbench.project-file=$jcmFile -jar $guiJar"),
            [Text.UTF8Encoding]::new($false))
        Write-Host "INTERACTIVE_GUI_READY"
        Write-Host "Native USE projection: $ProjectionMode"
        Write-Host "The JaCaMo Workbench is opening and importing the derived JCM automatically."
        Write-Host "Local agents wait at the official startAgs hook. Optional Load OCL, then click Start Runtime."
        Write-Host "Platforms/environment timers already bootstrap; this delay is not original timing equivalence."
        Write-Host "Derived JCM: $jcmFile"
        Write-Host "Original source remains unchanged: $sourceJcm"
        Write-Host "Close the USE window to stop the derived JaCaMo producer."
        $gui = Start-InteractiveJava $guiInstall $guiArguments
        $guiReadyReported = $false
        $producerExitReported = $false
        $guiStartupDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while (-not $gui.HasExited) {
            if ($producer.Process.HasExited -and -not $producerExitReported) {
                Write-Host "JACAMO_PRODUCER_EXITED_USE_REMAINS_OPEN exit=$($producer.Process.ExitCode)"
                $producerExitReported = $true
            }
            if (-not $guiReadyReported -and (Test-Path -LiteralPath $guiReadyFile -PathType Leaf)) {
                Write-Host "INTERACTIVE_GUI_MODEL_READY evidence=$guiReadyFile"
                Get-Content -Raw -LiteralPath $guiReadyFile | Write-Host
                $guiReadyReported = $true
            }
            if (-not $guiReadyReported -and -not $producerExitReported -and [DateTime]::UtcNow -ge $guiStartupDeadline) {
                throw "INTERACTIVE_GUI_IMPORT_TIMEOUT:$guiReadyFile"
            }
            Start-Sleep -Milliseconds 500
        }
        if (-not $guiReadyReported) { throw "INTERACTIVE_GUI_EXITED_BEFORE_IMPORT:$guiReadyFile" }
        [IO.File]::WriteAllText((Join-Path $runEvidence "summary.json"), (@{
            status = "MANUAL_GUI_SESSION_COMPLETED"
            classification = "INTERACTIVE_DEMO_ONLY"
            sourceJcm = $sourceJcm
            sourceJcmSha256 = $sourceDigest
            derivedJcm = $jcmFile
            pipeline = "CODE_GROUNDED_NATIVE"
            projectionMode = $ProjectionMode
            observationProfile = $ObservationProfile
            semanticProfile = $SemanticProfile
            mappingSpecification = $MappingSpecificationPath
            runtimeJournalMaxBytes = $RuntimeJournalMaxBytes
            distributionSha256 = $distribution
            port = $port
        } | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
        Write-Host "JACAMO_BRIDGE_GUI_SESSION_COMPLETE evidence=$runEvidence"
        $runCompleted = $true
        return
    }

    $consumerClasspath = Get-FilteredConsumerClasspath $bridgeClasspathFile
    $consumer = Start-CapturedJava -ClassPath $consumerClasspath -MainClass "org.tzi.use.plugins.jacamo.bridge.JaCaMoBridgeNativeConsumerMain" `
        -WorkingDirectory $stagedProject -Arguments @(
            $port.ToString(), $secretFile, $distribution, $jcmFile, $useRoot, $runEvidence,
            $ObservationSeconds.ToString(), $MaxBufferedEvents.ToString(), $BridgeTimeoutMillis.ToString(),
            $persistEvidence.ToString().ToLowerInvariant(), $externalOcl, $consumerReadyFile) `
        -JvmArguments (@("-Duse.jacamo.runtime.root=$runtimeDirectory", "-Duse.jacamo.projection.mode=$ProjectionMode") + $semanticJvmArguments)
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
        "java -Duse.jacamo.projection.mode=$ProjectionMode -cp <native-consumer> org.tzi.use.plugins.jacamo.bridge.JaCaMoBridgeNativeConsumerMain ...",
        [Text.UTF8Encoding]::new($false))
    $summary = @{
        status = "PASS"
        classification = "GENERIC_JCM_SUPPORTED_SCOPE_PASS"
        pipeline = "CODE_GROUNDED_NATIVE"
        projectionMode = $ProjectionMode
        observationProfile = $ObservationProfile
        semanticProfile = $SemanticProfile
        mappingSpecification = $MappingSpecificationPath
        runtimeJournalMaxBytes = $RuntimeJournalMaxBytes
        sourceJcm = $sourceJcm
        sourceJcmSha256 = $sourceDigest
        oclProfile = $externalOcl
        awaitOclBaselineBeforeAgents = -not [string]::IsNullOrWhiteSpace($consumerReadyFile)
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
    if ($null -ne $gui -and -not $gui.HasExited) {
        try { $gui.CloseMainWindow() | Out-Null } catch { }
        try { if (-not $gui.WaitForExit(5000)) { $gui.Kill($true) } } catch { }
    }
    if ($null -ne $consumer -and -not $consumer.Process.HasExited) {
        try { $consumer.Process.Kill($true) } catch { }
    }
    if ($null -ne $producer) {
        if (-not $producer.Process.HasExited) {
            try { [IO.File]::WriteAllText($stopFile, "stop", [Text.UTF8Encoding]::new($false)) } catch { }
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
            $producerFailure = "PRODUCER_FAILED:$($producer.Process.ExitCode)"
        }
    }
    if (-not $handoffPrepared) {
    foreach ($generatedFile in @($stopFile, $secretFile, $consumerReadyFile) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) {
        try { if (Test-Path -LiteralPath $generatedFile) { Remove-Item -LiteralPath $generatedFile -Force } } catch { }
    }
    try {
        $resolvedStage = [IO.Path]::GetFullPath($stagedProject)
        $expectedStageRoot = [IO.Path]::GetFullPath($runEvidence).TrimEnd([IO.Path]::DirectorySeparatorChar)
        if (-not [IO.Path]::GetDirectoryName($resolvedStage).Equals($expectedStageRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not [IO.Path]::GetFileName($resolvedStage).StartsWith("staging-", [StringComparison]::Ordinal)) {
            throw "STAGING_CLEANUP_PATH_INVALID:$resolvedStage"
        }
        if (Test-Path -LiteralPath $resolvedStage) { Remove-Item -LiteralPath $stagedProject -Recurse -Force }
    } catch { Write-Warning "Staging cleanup skipped/failed: $($_.Exception.Message)" }
    if (-not $persistEvidence) {
        try {
            $resolvedRun = [IO.Path]::GetFullPath($runEvidence)
            $expectedEvidenceRoot = [IO.Path]::GetFullPath($EvidenceDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar)
            if (-not [IO.Path]::GetDirectoryName($resolvedRun).Equals($expectedEvidenceRoot, [StringComparison]::OrdinalIgnoreCase) -or
                [IO.Path]::GetFileName($resolvedRun) -ne $runId) { throw "EVIDENCE_CLEANUP_PATH_INVALID:$resolvedRun" }
            if (Test-Path -LiteralPath $resolvedRun) { Remove-Item -LiteralPath $runEvidence -Recurse -Force }
        } catch { Write-Warning "Evidence cleanup skipped/failed: $($_.Exception.Message)" }
        try {
            if ((Test-Path -LiteralPath $EvidenceDirectory -PathType Container) -and
                -not (Get-ChildItem -LiteralPath $EvidenceDirectory -Force | Select-Object -First 1)) {
                Remove-Item -LiteralPath $EvidenceDirectory -Force
            }
        } catch { }
    }
    if ($null -ne $producerFailure) { throw $producerFailure }
    }
}

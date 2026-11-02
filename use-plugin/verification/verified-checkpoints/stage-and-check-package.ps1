[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$install=(Resolve-Path (Join-Path $repo 'use-assembly/target/new')).Path
$runtime=Join-Path $install 'bridge/runtime'
$null=New-Item -ItemType Directory -Path $runtime -Force
$entries=(Get-Content -Raw -LiteralPath (Join-Path $repo 'jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt')).Trim().Split([IO.Path]::PathSeparator)
$staged=@()
$ignoredMetadata=@()
foreach($entry in $entries) {
    if(-not (Test-Path -LiteralPath $entry -PathType Leaf)) {throw "RUNTIME_ENTRY_INVALID:$entry"}
    if($entry.EndsWith('.pom')) {$ignoredMetadata+=$entry;continue}
    if(-not $entry.EndsWith('.jar')) {throw "RUNTIME_ENTRY_INVALID:$entry"}
    $target=Join-Path $runtime ([IO.Path]::GetFileName($entry))
    if((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $entry).Hash -ne (Get-FileHash -LiteralPath $target).Hash) {throw "RUNTIME_NAME_COLLISION:$target"}
    Copy-Item -LiteralPath $entry -Destination $target -Force
    $staged+=$target
}
if(@(Get-ChildItem -LiteralPath $runtime -Filter '*.jar').Count -ne $staged.Count) {throw 'STALE_RUNTIME_FILES'}
$jacamo=@($staged | Where-Object {[IO.Path]::GetFileName($_) -match '^jacamo-[0-9].*\.jar$'})
if($jacamo.Count -ne 1 -or [IO.Path]::GetFileName($jacamo[0]) -ne 'jacamo-1.3.1-checkpoint-s01-SNAPSHOT.jar') {throw 'JACAMO_RUNTIME_AMBIGUOUS'}
$built=Join-Path $repo '../jacamo/build/libs/jacamo-1.3.1-checkpoint-s01-SNAPSHOT.jar'
if((Get-FileHash -LiteralPath $built).Hash -ne (Get-FileHash -LiteralPath $jacamo[0]).Hash) {throw 'JACAMO_BUILD_PACKAGE_MISMATCH'}
$cpFile=Join-Path $install 'bridge/runtime-classpath.txt'
[IO.File]::WriteAllText($cpFile,($staged -join [IO.Path]::PathSeparator))
# The normal UI gives an explicit configured classpath priority over checkout defaults.
$launcher=Join-Path $install 'bin/start_use_s01.bat'
$batch='@echo off' + "`r`n" + 'setlocal' + "`r`n" +
    'java "-Duse.jacamo.runtime.classpath-file=%~dp0..\bridge\runtime-classpath.txt" -jar "%~dp0..\lib\use-gui.jar" -nr %*' + "`r`n"
[IO.File]::WriteAllText($launcher,$batch)
$contract=Join-Path $install 'bridge/lib/jacamo-bridge-contract-1.0.0.jar'
$adapter=Join-Path $install 'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar'
$cp=(@($contract,$adapter)+$staged) -join [IO.Path]::PathSeparator
$fingerprint=& java -cp $cp org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain 2>&1
if($LASTEXITCODE -ne 0) {throw "PACKAGED_FINGERPRINT_FAILED:$fingerprint"}
$fingerprint | Set-Content (Join-Path $PSScriptRoot 'S01-packaged-code-sources.log')
if(-not ($fingerprint -contains "BRIDGE_RUNTIME_CODE_SOURCE=jacamo:$($jacamo[0])")) {throw 'PACKAGED_JACAMO_NOT_LOADED'}
if(-not ($fingerprint -contains 'BRIDGE_CHECKPOINT_STRICT_SUPPORTED=false')) {throw 'FALSE_STRICT_CAPABILITY'}
$hash=(Get-FileHash -LiteralPath $jacamo[0]).Hash.ToLowerInvariant()
if(-not ($fingerprint -contains "BRIDGE_RUNTIME_COMPONENT=jacamo:$hash")) {throw 'LOADED_JACAMO_HASH_MISMATCH'}
$smoke=& java '-Djava.awt.headless=true' -cp (Join-Path $install 'lib/use-gui.jar') (Join-Path $PSScriptRoot 'InstalledCheckpointS01Smoke.java') $install 2>&1
$smoke | Set-Content (Join-Path $PSScriptRoot 'S01-installed-loader-smoke.log')
if($LASTEXITCODE -ne 0) {throw "INSTALLED_LOADER_FAILED:$smoke"}
$auction=(Resolve-Path (Join-Path $repo '../jacamo/examples/auction/auction.jcm')).Path
$rejected=& java '-Djava.awt.headless=true' '-Djacamo.execution.mode=VERIFIED_CHECKPOINTS' -cp $cp jason.infra.local.LiveJaCaMoLauncherMain $auction (Join-Path $PSScriptRoot 'never-started.stop') 15 true 2>&1
$rejectionExit=$LASTEXITCODE
$rejected | Set-Content (Join-Path $PSScriptRoot 'S01-packaged-strict-rejection.log')
if($rejectionExit -eq 0 -or -not (($rejected -join "`n").Contains('VERIFIED_CHECKPOINTS_UNSUPPORTED:S01:'))) {throw 'STRICT_START_WAS_NOT_REJECTED'}
$receipt=[ordered]@{
    status='S01_FEASIBILITY_BLOCKED'; strictSupported=$false; packageKind='S01 gate and passive runtime; not Verified checkpoints acceptance';
    launcher=$launcher; runtimeClasspath=$cpFile; jacamoCodeSource=$jacamo[0]; jacamoSha256=$hash;
    jacamoVersion='1.3.1-checkpoint-s01-SNAPSHOT'; runtimeJarCount=$staged.Count;
    ignoredPomMetadata=$ignoredMetadata;
    installedLoaderSmoke=$true; strictStartupRejected=$true; strictRejectionExit=$rejectionExit;
    liveAuctionAcceptance=$false; manualDesktopAcceptance=$false; commitOrPush=$false;
    runtimeJars=@($staged | ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant()}})
}
$receipt | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $PSScriptRoot 'S01-package-receipt.json')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'S01-boundary.md') -Destination (Join-Path $install 'bridge/CHECKPOINT-SUPPORT.md') -Force
Write-Output "S01_PACKAGE_CHECK_PASS; STRICT_FEASIBILITY_BLOCKED; $launcher"
exit 0

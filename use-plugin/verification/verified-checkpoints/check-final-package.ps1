[CmdletBinding()]
param([string]$EvidencePrefix='S06',[string]$OutputDirectory=$PSScriptRoot)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$install=(Resolve-Path (Join-Path $repo 'use-assembly/target/new')).Path
& (Join-Path $install 'tools/prepare-runtime-classpath.ps1')
$runtime=Join-Path $install 'bridge/runtime'
$lock=Get-Content -LiteralPath (Join-Path $install 'bridge/dependency-lock.json') -Raw | ConvertFrom-Json
$jacamo=@($lock.components | Where-Object component -eq jacamo)
if($jacamo.Count -ne 1){throw 'PINNED_JACAMO_REQUIRED'}
$jar=Join-Path $runtime "$($jacamo[0].artifactId)-$($jacamo[0].version).jar"
$built=Join-Path $repo '../jacamo/build/libs/jacamo-1.3.1-checkpoint-SNAPSHOT.jar'
if((Get-FileHash -LiteralPath $jar).Hash -ne (Get-FileHash -LiteralPath $built).Hash){throw 'JACAMO_BUILD_PACKAGE_MISMATCH'}
$copies=@{
    'jacamo-bridge-contract/target/jacamo-bridge-contract-1.0.0-SNAPSHOT.jar'='bridge/lib/jacamo-bridge-contract-1.0.0.jar'
    'jacamo-bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar'='bridge/lib/jacamo-bridge-jacamo-1.0.0.jar'
    'use-plugin/target/use-plugin-1.0.1.jar'='lib/plugins/use-jacamo-plugin-1.0.1.jar'
    'use-gui/target/use-gui.jar'='lib/use-gui.jar'
}
foreach($source in $copies.Keys){
    if((Get-FileHash -LiteralPath (Join-Path $repo $source)).Hash -ne (Get-FileHash -LiteralPath (Join-Path $install $copies[$source])).Hash){throw "BUILD_PACKAGE_MISMATCH:$source"}
}
$cp=@((Join-Path $install 'bridge/lib/jacamo-bridge-contract-1.0.0.jar'),(Join-Path $install 'bridge/lib/jacamo-bridge-jacamo-1.0.0.jar'),(Get-Content -Raw -LiteralPath (Join-Path $install 'bridge/runtime-classpath.txt')).Trim()) -join [IO.Path]::PathSeparator
$loaded=& java -cp $cp org.jacamo.bridge.adapter.RuntimeDistributionFingerprintMain 2>&1
if($LASTEXITCODE -ne 0){throw "PACKAGED_RUNTIME_IDENTITY_FAILED:$loaded"}
$loaded | Set-Content -LiteralPath (Join-Path $OutputDirectory "$EvidencePrefix-packaged-code-sources.log")
if(-not ($loaded -contains "BRIDGE_RUNTIME_CODE_SOURCE=jacamo:$jar")){throw 'PACKAGED_JACAMO_NOT_LOADED'}
if(-not ($loaded -contains "BRIDGE_RUNTIME_COMPONENT=jacamo:$($jacamo[0].sha256)")){throw 'LOADED_JACAMO_HASH_MISMATCH'}
if(-not ($loaded -contains 'BRIDGE_CHECKPOINT_STRICT_SUPPORTED=true')){throw 'PINNED_SOURCE_HOOKS_NOT_LOADED'}
$smoke=& java '-Djava.awt.headless=true' -cp (Join-Path $install 'lib/use-gui.jar') (Join-Path $PSScriptRoot 'InstalledVerifiedCheckpointSmoke.java') $install 2>&1
$smoke | Set-Content -LiteralPath (Join-Path $OutputDirectory "$EvidencePrefix-installed-loader-smoke.log")
if($LASTEXITCODE -ne 0){throw "INSTALLED_PLUGIN_LOADER_FAILED:$smoke"}
$receipt=[ordered]@{
    status='PACKAGED_PINNED_RUNTIME_AND_PLUGIN_LOADER_PASS';launcher=(Join-Path $install 'bin/start_use.bat');
    jacamoCodeSource=$jar;jacamoSha256=$jacamo[0].sha256;jacamoVersion=$jacamo[0].version;
    strictSourceHooks=$true;installedPluginLoader=$true;currentBuildBytesVerified=$true;manualDesktopAcceptance=$false;commitOrPush=$false;
    files=@(Get-ChildItem -LiteralPath (Join-Path $install 'bridge') -Recurse -File | Where-Object Extension -eq '.jar' | ForEach-Object {@{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}})
}
$receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutputDirectory "$EvidencePrefix-final-package-receipt.json")
Write-Output "FINAL_PACKAGE_IDENTITY_PASS $jar $($jacamo[0].sha256)"

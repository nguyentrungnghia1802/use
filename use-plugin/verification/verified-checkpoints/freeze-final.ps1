[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$LiveEvidenceDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$use=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$jacamo=(Resolve-Path (Join-Path $use '../jacamo')).Path
$sources=@()
foreach($repo in @($use,$jacamo)) {
    Push-Location $repo
    try {
        if((& git branch --show-current) -ne 'main'){throw "EXPECTED_MAIN:$repo"}
        $paths=@(& git diff --name-only)+@(& git ls-files --others --exclude-standard)
        foreach($relative in @($paths | Sort-Object -Unique)) {
            if($relative -match '^use-plugin/verification/' -and $relative -notmatch '^use-plugin/verification/verified-checkpoints/(check-final-package\.ps1|freeze-final\.ps1|InstalledVerifiedCheckpointSmoke\.java)$'){continue}
            if($relative -notmatch '\.(java|py|gradle|properties|xml|ps1|bat|md|json)$'){continue}
            $path=Join-Path $repo $relative
            if(Test-Path -LiteralPath $path -PathType Leaf){$sources+=@{repo=[IO.Path]::GetFileName($repo);path=$relative;sha256=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()}}
        }
    }finally{Pop-Location}
}
$live=(Resolve-Path -LiteralPath $LiveEvidenceDirectory).Path
$receipt=Get-Content -Raw -LiteralPath (Join-Path $live 'live-receipt.json') | ConvertFrom-Json
$package=Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'S06-final-package-receipt.json') | ConvertFrom-Json
$evidence=@(Get-ChildItem -LiteralPath $live -Recurse -File | Where-Object {$_.Extension -in @('.json','.jsonl','.cmd','.use','.ocl','.txt','.log','.jar')} | ForEach-Object {@{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}})
$acceptance=@(Get-ChildItem -LiteralPath $PSScriptRoot -File | Where-Object {$_.Name -in @('FINAL-SUPPORT.md','S01-supported-boundary.md','S01-final-probes.log','S01-final-dependencies.log','S02-final-source-tests.log','S02-auth-protocol-tests.log','S05-service-ui-tests.log','S07-final-confirmed.log','S08-final-package-confirmed.log','S06-final-package-receipt.json','S06-packaged-code-sources.log','S06-installed-loader-smoke.log','offscreen-controls.png')} | ForEach-Object {@{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}})
$installed=@($package.files)+@(Get-ChildItem -LiteralPath (Join-Path $use 'use-assembly/target/new/lib') -Recurse -File | Where-Object Extension -eq '.jar' | ForEach-Object {@{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}})
$freeze=[ordered]@{
    version='VERIFIED_CHECKPOINT_FINAL_FREEZE/1.0';recordedAt=[DateTime]::UtcNow.ToString('o');branch='main';
    jacamoSha256=$package.jacamoSha256;launcher=$package.launcher;
    liveEvidence=$live;automaticReleaseReceipts=$receipt.automaticCuts;reviewedReleaseReceipts=$receipt.reviewedReleaseReceipts;
    genuineNativeFailedCuts=$receipt.genuineNativeFailedCuts;originalAuctionUnchanged=$receipt.originalAuctionUnchanged;
    manualDesktopAcceptance=$false;commitOrPush=$false;sourceFiles=$sources;installedFiles=$installed;acceptanceFiles=$acceptance;evidenceFiles=$evidence;
}
$freeze | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'final-source-artifact-evidence-freeze.json')
Write-Output "FINAL_SOURCE_ARTIFACT_EVIDENCE_FREEZE_PASS $($package.jacamoSha256)"

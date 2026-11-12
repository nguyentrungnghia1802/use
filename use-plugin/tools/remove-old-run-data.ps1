# Manual cleanup entry point. Close USE and JaCaMo before running this script.
# No backup is created. Application binaries, source and regression fixtures remain.
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'Medium')]
param()
$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
if (-not (Test-Path -LiteralPath (Join-Path $workspaceRoot 'use/use-plugin/pom.xml')) -or
    -not (Test-Path -LiteralPath (Join-Path $workspaceRoot 'jacamo/.git'))) {
    throw 'Expected USE/JaCaMo workspace was not found.'
}
$relativeTargets = @(
    'use/use-plugin/verification',
    'use/use-plugin/evidence',
    'use/use-plugin/use-plugin',
    'use/use-plugin/target/history',
    'work/history',
    'work/owned-runtime',
    'use/use-assembly/target/new/work',
    'use/use-assembly/target/new/use-plugin'
)
# Validate every target before the first deletion. Do not follow junctions.
$validatedTargets = foreach ($relative in $relativeTargets) {
    $candidate = [IO.Path]::GetFullPath((Join-Path $workspaceRoot $relative))
    if (-not $candidate.StartsWith($workspaceRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) { throw "Outside workspace: $candidate" }
    if (Test-Path -LiteralPath $candidate) {
        $item = Get-Item -LiteralPath $candidate -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse target: $candidate" }
        $links = @(Get-ChildItem -LiteralPath $candidate -Force -Recurse |
            Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
        if ($links.Count) { throw "Reparse child requires manual review: $candidate" }
        $item.FullName
    }
}
foreach ($candidate in $validatedTargets) {
    if ($PSCmdlet.ShouldProcess($candidate, 'Delete old run data without backup')) {
        Remove-Item -LiteralPath $candidate -Recurse -Force
    }
}

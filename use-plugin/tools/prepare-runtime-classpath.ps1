[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Get-PinnedSha256([string]$Path) {
    # Windows PowerShell launched through cmd can inherit PowerShell 7's module
    # path. Hashing pinned runtime bytes must not depend on module auto-loading.
    $algorithm=[Security.Cryptography.SHA256]::Create()
    $stream=[IO.File]::OpenRead($Path)
    try { [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
    finally { $stream.Dispose(); $algorithm.Dispose() }
}
$install=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtime=Join-Path $install 'bridge/runtime'
$lockFile=Join-Path $install 'bridge/dependency-lock.json'
if(-not (Test-Path -LiteralPath $runtime -PathType Container)){throw 'PACKAGED_PRODUCER_RUNTIME_MISSING'}
$lock=Get-Content -LiteralPath $lockFile -Raw | ConvertFrom-Json
$jars=@(Get-ChildItem -LiteralPath $runtime -Filter '*.jar' -File | Sort-Object Name)
foreach($component in @($lock.components)) {
    $name="$($component.artifactId)-$($component.version).jar"
    $path=Join-Path $runtime $name
    if(-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-PinnedSha256 $path) -ne $component.sha256){throw "PACKAGED_COMPONENT_HASH_MISMATCH:$name"}
    $same=@($jars | Where-Object {$_.Name -match ('^'+[regex]::Escape($component.artifactId)+'-[0-9].*\.jar$')})
    if($same.Count -ne 1){throw "PACKAGED_COMPONENT_AMBIGUOUS:$name"}
}
$kernel=Join-Path $runtime 'checkpoint-runtime-1.0.0-SNAPSHOT.jar'
if(-not (Test-Path -LiteralPath $kernel) -or (Get-PinnedSha256 $kernel) -ne $lock.runtime.sha256){throw 'PACKAGED_BARRIER_RUNTIME_HASH_MISMATCH'}
[IO.File]::WriteAllText((Join-Path $install 'bridge/runtime-classpath.txt'),(($jars | ForEach-Object {$_.FullName}) -join [IO.Path]::PathSeparator))

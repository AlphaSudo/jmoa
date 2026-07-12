param(
    [Parameter(Mandatory)][string]$BaselineApplicationLayer,
    [Parameter(Mandatory)][string]$ReducedApplicationClasses,
    [Parameter(Mandatory)][string]$OutputApplicationLayer,
    [string]$OutputDir = "target/jmoa-application-materialization",
    [switch]$FailOnMismatch
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

foreach ($path in @($BaselineApplicationLayer, $ReducedApplicationClasses)) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Required directory does not exist: $path" }
}
$baselineResolved = (Resolve-Path -LiteralPath $BaselineApplicationLayer).Path
$reducedResolved = (Resolve-Path -LiteralPath $ReducedApplicationClasses).Path
$outputResolved = [System.IO.Path]::GetFullPath($OutputApplicationLayer)
if ($outputResolved -eq $baselineResolved -or $outputResolved -eq $reducedResolved) {
    throw 'OutputApplicationLayer must be a new destination, not an input directory.'
}
if (Test-Path -LiteralPath $OutputApplicationLayer) { Remove-Item -LiteralPath $OutputApplicationLayer -Recurse -Force }
Copy-Item -LiteralPath $BaselineApplicationLayer -Destination $OutputApplicationLayer -Recurse -Force
New-JmoaDirectory -Path $OutputDir

$reducedFiles = @(Get-ChildItem -LiteralPath $ReducedApplicationClasses -Recurse -File | Sort-Object FullName)
$applicationClassesRoot = Join-Path $OutputApplicationLayer 'BOOT-INF/classes'
if (-not (Test-Path -LiteralPath $applicationClassesRoot -PathType Container)) {
    throw "Baseline application layer does not contain BOOT-INF/classes: $OutputApplicationLayer"
}
$records = @()
$mismatches = @()
foreach ($file in $reducedFiles) {
    $relative = [System.IO.Path]::GetRelativePath((Resolve-Path $ReducedApplicationClasses), $file.FullName)
    $target = Join-Path $applicationClassesRoot $relative
    New-JmoaDirectory -Path (Split-Path $target -Parent)
    Copy-Item -LiteralPath $file.FullName -Destination $target -Force
    $sourceHash = Get-JmoaSha256 -Path $file.FullName
    $targetHash = Get-JmoaSha256 -Path $target
    $matches = $sourceHash -eq $targetHash
    if (-not $matches) { $mismatches += $relative }
    $records += [ordered]@{ relativePath = $relative.Replace('\', '/'); sourceSha256 = $sourceHash; outputSha256 = $targetHash; matches = $matches }
}
$report = [ordered]@{
    metadataVersion = 'v2q-application-layer-materialization'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = if ($mismatches.Count -eq 0) { 'PASSED' } else { 'FAILED_HASH_MISMATCH' }
    baselineApplicationLayer = $baselineResolved
    reducedApplicationClasses = $reducedResolved
    outputApplicationLayer = (Resolve-Path -LiteralPath $OutputApplicationLayer).Path
    applicationClassesRoot = (Resolve-Path -LiteralPath $applicationClassesRoot).Path
    overlayFiles = $records.Count
    hashMismatches = $mismatches
    records = $records
    claimBoundary = 'Application-layer materialization only. Semantic smoke and V2-C/V2-D evidence remain required before any runtime claim.'
}
Write-JmoaJson -Value $report -Path (Join-Path $OutputDir 'v2q-application-materialization.json')
Write-JmoaText -Value "# V2-Q Application Layer Materialization`n`n- Status: ``$($report.status)```n- Overlay files: ``$($report.overlayFiles)```n- Hash mismatches: ``$($mismatches.Count)```n" -Path (Join-Path $OutputDir 'v2q-application-materialization.md')
if ($FailOnMismatch -and $mismatches.Count -gt 0) { exit 1 }

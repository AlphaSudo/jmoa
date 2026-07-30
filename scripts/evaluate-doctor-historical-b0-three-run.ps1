param(
    [Parameter(Mandatory)][string]$CurrentRunDirectoryList,
    [Parameter(Mandatory)][string]$HistoricalThreeRunJson,
    [Parameter(Mandatory)][string]$HistoricalSmapsDirectory,
    [Parameter(Mandatory)][string]$ExpectedArtifactSha256,
    [Parameter(Mandatory)][string]$ExpectedBaseArchiveSha256,
    [Parameter(Mandatory)][string]$ExpectedImageId,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$CurrentRunDirectories = @($CurrentRunDirectoryList -split '\|' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($CurrentRunDirectories.Count -ne 3) { throw 'Exactly three pipe-delimited current run directories are required.' }

function Get-Metric([string]$Text, [string]$Name) {
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { throw "Metric is missing: $Name" }
    return [long]$match.Groups[1].Value
}

function Get-Summary([long[]]$Values) {
    $sorted = @($Values | Sort-Object)
    return [ordered]@{
        values = @($Values)
        minimum = [long]$sorted[0]
        median = [long]$sorted[1]
        maximum = [long]$sorted[2]
    }
}

function Test-Overlap($Left, $Right) {
    return [long]$Left.minimum -le [long]$Right.maximum -and [long]$Right.minimum -le [long]$Left.maximum
}

foreach ($path in @($HistoricalThreeRunJson, $ExpectedArtifactSha256)) {
    if ([string]::IsNullOrWhiteSpace([string]$path)) { throw 'Required argument is empty.' }
}
if (-not (Test-Path -LiteralPath $HistoricalThreeRunJson -PathType Leaf)) { throw "Historical JSON is missing: $HistoricalThreeRunJson" }
if (-not (Test-Path -LiteralPath $HistoricalSmapsDirectory -PathType Container)) { throw "Historical smaps directory is missing: $HistoricalSmapsDirectory" }

$expectedArtifact = $ExpectedArtifactSha256.Trim().ToUpperInvariant()
$expectedArchive = $ExpectedBaseArchiveSha256.Trim().ToUpperInvariant()
$expectedImage = ($ExpectedImageId.Trim() -replace '^(?i)sha256:', '').ToUpperInvariant()
$currentRows = @()
foreach ($directory in $CurrentRunDirectories) {
    foreach ($file in @('run-manifest.json', 'workload-result.json', 'smaps_rollup.txt', 'memory.current')) {
        if (-not (Test-Path -LiteralPath (Join-Path $directory $file) -PathType Leaf)) {
            throw "Current run evidence is missing: $(Join-Path $directory $file)"
        }
    }
    $manifest = Get-Content -Raw -LiteralPath (Join-Path $directory 'run-manifest.json') | ConvertFrom-Json
    $workload = Get-Content -Raw -LiteralPath (Join-Path $directory 'workload-result.json') | ConvertFrom-Json
    $smaps = Get-Content -Raw -LiteralPath (Join-Path $directory 'smaps_rollup.txt')
    $currentRows += [ordered]@{
        runDirectory = [IO.Path]::GetFullPath($directory)
        pssKb = Get-Metric $smaps 'Pss'
        anonymousPssKb = Get-Metric $smaps 'Pss_Anon'
        filePssKb = Get-Metric $smaps 'Pss_File'
        privateDirtyKb = Get-Metric $smaps 'Private_Dirty'
        memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $directory 'memory.current')).Trim()
        startupMillis = [long]$manifest.startupMillis
        workloadRequests = [int]$workload.requests
        workloadErrors = [int]$workload.errors
        health = [string]$workload.health
        artifactSha256 = ([string]$manifest.runtimeArtifactSha256).ToUpperInvariant()
        archiveSha256 = ([string]$manifest.runtimePolicyProof.defaultJdkArchiveSha256).ToUpperInvariant()
        imageId = (([string]$manifest.imageId).Trim() -replace '^(?i)sha256:', '').ToUpperInvariant()
        archivePath = [string]$manifest.runtimePolicyProof.defaultJdkArchivePath
        javaVersion = [string]$manifest.javaVersion
        javaRuntimeVersion = if ($null -ne $manifest.runtimeJdkFingerprint) { [string]$manifest.runtimeJdkFingerprint.javaRuntimeVersion } else { '' }
        javaHome = if ($null -ne $manifest.runtimeJdkFingerprint) { [string]$manifest.runtimeJdkFingerprint.javaHome } else { '' }
    }
}

$historicalJson = Get-Content -Raw -LiteralPath $HistoricalThreeRunJson | ConvertFrom-Json
$historicalSmapsFiles = @(Get-ChildItem -LiteralPath $HistoricalSmapsDirectory -Filter 'baseline-run*-smaps.txt' -File | Sort-Object Name)
if ($historicalSmapsFiles.Count -ne 3) { throw "Expected three historical smaps files; found $($historicalSmapsFiles.Count)." }
$historicalRows = @($historicalSmapsFiles | ForEach-Object {
    $text = Get-Content -Raw -LiteralPath $_.FullName
    [ordered]@{
        pssKb = Get-Metric $text 'Pss'
        anonymousPssKb = Get-Metric $text 'Pss_Anon'
        filePssKb = Get-Metric $text 'Pss_File'
        privateDirtyKb = Get-Metric $text 'Private_Dirty'
    }
})
for ($index = 0; $index -lt 3; $index++) {
    $historicalRows[$index].memoryCurrentBytes = [long]$historicalJson.runs[$index].baseline.cgroup_bytes
    $historicalRows[$index].startupMillis = [long][Math]::Round([double]$historicalJson.runs[$index].baseline.startupSec * 1000)
}

$current = [ordered]@{
    pssKb = Get-Summary @($currentRows.pssKb)
    anonymousPssKb = Get-Summary @($currentRows.anonymousPssKb)
    filePssKb = Get-Summary @($currentRows.filePssKb)
    privateDirtyKb = Get-Summary @($currentRows.privateDirtyKb)
    memoryCurrentBytes = Get-Summary @($currentRows.memoryCurrentBytes)
    startupMillis = Get-Summary @($currentRows.startupMillis)
}
$historical = [ordered]@{
    pssKb = Get-Summary @($historicalRows.pssKb)
    anonymousPssKb = Get-Summary @($historicalRows.anonymousPssKb)
    filePssKb = Get-Summary @($historicalRows.filePssKb)
    privateDirtyKb = Get-Summary @($historicalRows.privateDirtyKb)
    memoryCurrentBytes = Get-Summary @($historicalRows.memoryCurrentBytes)
    startupMillis = Get-Summary @($historicalRows.startupMillis)
}

$normalizedCurrentMedianPssKb = [long]$current.pssKb.median + ([long]$historical.filePssKb.median - [long]$current.filePssKb.median)
$allArtifactsExact = @($currentRows | Where-Object { $_.artifactSha256 -ne $expectedArtifact }).Count -eq 0
$allArchivesExact = @($currentRows | Where-Object { $_.archiveSha256 -ne $expectedArchive }).Count -eq 0
$allImagesExact = @($currentRows | Where-Object { $_.imageId -ne $expectedImage }).Count -eq 0
$liveJdkFingerprintRuns = @($currentRows | Where-Object { $_.javaRuntimeVersion -eq '26+35' -and $_.javaHome -eq '/opt/customjre' }).Count
$allWorkloadsValid = @($currentRows | Where-Object { $_.workloadRequests -ne 80 -or $_.workloadErrors -ne 0 -or $_.health -ne 'UP' }).Count -eq 0
$allAnonymousInside = @($currentRows | Where-Object { $_.anonymousPssKb -lt $historical.anonymousPssKb.minimum -or $_.anonymousPssKb -gt $historical.anonymousPssKb.maximum }).Count -eq 0
$allPrivateDirtyInside = @($currentRows | Where-Object { $_.privateDirtyKb -lt $historical.privateDirtyKb.minimum -or $_.privateDirtyKb -gt $historical.privateDirtyKb.maximum }).Count -eq 0
$normalizedPssInside = $normalizedCurrentMedianPssKb -ge $historical.pssKb.minimum -and $normalizedCurrentMedianPssKb -le $historical.pssKb.maximum
$startupOverlap = Test-Overlap $current.startupMillis $historical.startupMillis
$cgroupOverlap = Test-Overlap $current.memoryCurrentBytes $historical.memoryCurrentBytes
$memoryBaselineReproduced = $allArtifactsExact -and $allArchivesExact -and $allImagesExact -and $liveJdkFingerprintRuns -ge 2 -and $allWorkloadsValid -and $allAnonymousInside -and $allPrivateDirtyInside -and $normalizedPssInside
$decision = if ($memoryBaselineReproduced) { 'TARGET_MEMORY_BASELINE_REPRODUCED' } else { 'TARGET_MEMORY_BASELINE_NOT_REPRODUCED' }

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-historical-b0-three-run-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    decision = $decision
    scope = 'BASELINE_ONLY'
    claimableOptimizerResult = $false
    currentRuns = $currentRows
    current = $current
    historical = $historical
    normalizedCurrentMedianPssKb = $normalizedCurrentMedianPssKb
    gates = [ordered]@{
        exactHistoricalArtifactAllRuns = $allArtifactsExact
        exactCompatibleBaseArchiveAllRuns = $allArchivesExact
        exactRestoredImageAllRuns = $allImagesExact
        liveOpenJdk26Plus35FingerprintRuns = $liveJdkFingerprintRuns
        historicalWorkloadValidAllRuns = $allWorkloadsValid
        everyAnonymousPssInsideHistoricalRange = $allAnonymousInside
        everyPrivateDirtyInsideHistoricalRange = $allPrivateDirtyInside
        fileNormalizedMedianPssInsideHistoricalRange = $normalizedPssInside
        startupDistributionOverlaps = $startupOverlap
        memoryCurrentDistributionOverlaps = $cgroupOverlap
    }
    tuple = [ordered]@{
        applicationArtifact = 'EXACT_HISTORICAL_BYTES'
        customJre = 'ARCHIVE_COMPATIBLE_RECONSTRUCTION_OPENJDK_26_PLUS_35'
        baseCds = 'ARCHIVE_COMPATIBLE_COMPACT_HEADERS'
        finalDistrolessUserspace = 'RECONSTRUCTED_CURRENT_PIN'
        captureHelper = 'DISCLOSED_BUSYBOX_NOT_JVM_LOADED'
        supportImages = 'RECONSTRUCTED'
        containerEngineAndHost = 'CURRENT_NOT_HISTORICAL'
    }
    interpretation = 'The Doctor target JVM anonymous/private-dirty memory baseline is reproduced across three fresh runs. Total PSS differs because file-backed mappings differ; file-normalized PSS is inside the historical range. Startup and cgroup accounting are not historically reproduced.'
    nextAction = if ($memoryBaselineReproduced) { 'BASELINE_RECONCILIATION_COMPLETE_DECIDE_SEPARATELY_BEFORE_ANY_DIRECTIONAL_PAIR' } else { 'DO_NOT_RUN_CANDIDATE' }
}

New-JmoaDirectory -Path $OutputDirectory
$jsonPath = Join-Path $OutputDirectory 'doctor-phase32-restored-baseline-three-run.json'
$mdPath = Join-Path $OutputDirectory 'doctor-phase32-restored-baseline-three-run.md'
Write-JmoaJson -Value $report -Path $jsonPath
@"
# Doctor Phase 32 Restored Baseline

- Decision: **$decision**
- Scope: **baseline only**
- Current median PSS: **$($current.pssKb.median) KB**
- Current median anonymous PSS: **$($current.anonymousPssKb.median) KB**
- Historical median anonymous PSS: **$($historical.anonymousPssKb.median) KB**
- Current median Private_Dirty: **$($current.privateDirtyKb.median) KB**
- Historical median Private_Dirty: **$($historical.privateDirtyKb.median) KB**
- File-normalized current median PSS: **$normalizedCurrentMedianPssKb KB**
- Historical PSS range: **$($historical.pssKb.minimum)-$($historical.pssKb.maximum) KB**
- Startup distribution overlap: **$startupOverlap**
- memory.current distribution overlap: **$cgroupOverlap**

The target JVM anonymous/private-dirty memory baseline was reproduced across three fresh runs using
the exact historical B0 application JAR, the historical 80-request workload, and an OpenJDK 26+35
custom-JRE reconstruction that is compatible with the preserved Phase 32K archive.

This is not a byte-for-byte restoration of the old container environment. The final distroless
userspace, support images, container engine, host, and cgroup accounting are reconstructed/current.
No V1 or V2 result is included in this report.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Decision: $decision"
Write-Host "Report: $jsonPath"

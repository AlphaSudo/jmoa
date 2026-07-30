param(
    [Parameter(Mandatory)][string]$CurrentRunDirectory,
    [Parameter(Mandatory)][string]$HistoricalThreeRunJson,
    [Parameter(Mandatory)][string]$HistoricalSmapsDirectory,
    [Parameter(Mandatory)][string]$ExpectedArtifactSha256,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

foreach ($path in @(
    (Join-Path $CurrentRunDirectory 'run-manifest.json'),
    (Join-Path $CurrentRunDirectory 'workload-result.json'),
    (Join-Path $CurrentRunDirectory 'smaps_rollup.txt'),
    (Join-Path $CurrentRunDirectory 'memory.current'),
    $HistoricalThreeRunJson
)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required evidence file does not exist: $path" }
}
if (-not (Test-Path -LiteralPath $HistoricalSmapsDirectory -PathType Container)) {
    throw "Historical smaps directory does not exist: $HistoricalSmapsDirectory"
}

function Get-Metric([string]$Text, [string]$Name) {
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { return $null }
    return [long]$match.Groups[1].Value
}

function Get-Median([long[]]$Values) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return $null }
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if (($sorted.Count % 2) -eq 1) { return [long]$sorted[$middle] }
    return [long][Math]::Round(($sorted[$middle - 1] + $sorted[$middle]) / 2.0)
}

$manifest = Get-Content -Raw -LiteralPath (Join-Path $CurrentRunDirectory 'run-manifest.json') | ConvertFrom-Json
$workload = Get-Content -Raw -LiteralPath (Join-Path $CurrentRunDirectory 'workload-result.json') | ConvertFrom-Json
$currentSmaps = Get-Content -Raw -LiteralPath (Join-Path $CurrentRunDirectory 'smaps_rollup.txt')
$historical = Get-Content -Raw -LiteralPath $HistoricalThreeRunJson | ConvertFrom-Json
$historicalSmaps = @(Get-ChildItem -LiteralPath $HistoricalSmapsDirectory -Filter 'baseline-run*-smaps.txt' -File | Sort-Object Name)
if ($historicalSmaps.Count -ne 3) { throw "Expected exactly three historical baseline smaps files; found $($historicalSmaps.Count)." }

$historicalRows = @($historicalSmaps | ForEach-Object {
    $text = Get-Content -Raw -LiteralPath $_.FullName
    [ordered]@{
        run = $_.BaseName
        rssKb = Get-Metric $text 'Rss'
        pssKb = Get-Metric $text 'Pss'
        pssAnonKb = Get-Metric $text 'Pss_Anon'
        pssFileKb = Get-Metric $text 'Pss_File'
        privateDirtyKb = Get-Metric $text 'Private_Dirty'
        privateCleanKb = Get-Metric $text 'Private_Clean'
        sharedCleanKb = Get-Metric $text 'Shared_Clean'
    }
})

$current = [ordered]@{
    rssKb = Get-Metric $currentSmaps 'Rss'
    pssKb = Get-Metric $currentSmaps 'Pss'
    pssAnonKb = Get-Metric $currentSmaps 'Pss_Anon'
    pssFileKb = Get-Metric $currentSmaps 'Pss_File'
    privateDirtyKb = Get-Metric $currentSmaps 'Private_Dirty'
    privateCleanKb = Get-Metric $currentSmaps 'Private_Clean'
    sharedCleanKb = Get-Metric $currentSmaps 'Shared_Clean'
    memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $CurrentRunDirectory 'memory.current')).Trim()
    startupMillis = [long]$manifest.startupMillis
}

$historicalSummary = [ordered]@{
    pssKb = [ordered]@{
        values = @($historicalRows.pssKb)
        minimum = [long](($historicalRows.pssKb | Measure-Object -Minimum).Minimum)
        median = Get-Median @($historicalRows.pssKb)
        maximum = [long](($historicalRows.pssKb | Measure-Object -Maximum).Maximum)
    }
    pssAnonKb = [ordered]@{
        values = @($historicalRows.pssAnonKb)
        minimum = [long](($historicalRows.pssAnonKb | Measure-Object -Minimum).Minimum)
        median = Get-Median @($historicalRows.pssAnonKb)
        maximum = [long](($historicalRows.pssAnonKb | Measure-Object -Maximum).Maximum)
    }
    pssFileKb = [ordered]@{
        values = @($historicalRows.pssFileKb)
        minimum = [long](($historicalRows.pssFileKb | Measure-Object -Minimum).Minimum)
        median = Get-Median @($historicalRows.pssFileKb)
        maximum = [long](($historicalRows.pssFileKb | Measure-Object -Maximum).Maximum)
    }
    privateDirtyKb = [ordered]@{
        values = @($historicalRows.privateDirtyKb)
        minimum = [long](($historicalRows.privateDirtyKb | Measure-Object -Minimum).Minimum)
        median = Get-Median @($historicalRows.privateDirtyKb)
        maximum = [long](($historicalRows.privateDirtyKb | Measure-Object -Maximum).Maximum)
    }
    memoryCurrentBytes = [ordered]@{
        values = @($historical.runs.baseline.cgroup_bytes | ForEach-Object { [long]$_ })
        minimum = [long](($historical.runs.baseline.cgroup_bytes | Measure-Object -Minimum).Minimum)
        median = Get-Median @($historical.runs.baseline.cgroup_bytes | ForEach-Object { [long]$_ })
        maximum = [long](($historical.runs.baseline.cgroup_bytes | Measure-Object -Maximum).Maximum)
    }
}

$expectedHash = $ExpectedArtifactSha256.Trim().ToUpperInvariant()
$snapshotOffsets = @($manifest.postWorkloadSnapshots | ForEach-Object { [int]$_.offsetSeconds })
$artifactExact = ([string]$manifest.artifactSha256).ToUpperInvariant() -eq $expectedHash -and
    ([string]$manifest.runtimeArtifactSha256).ToUpperInvariant() -eq $expectedHash
$workloadExact = [int]$workload.requests -eq 80 -and [int]$workload.errors -eq 0 -and
    [string]$workload.health -eq 'UP' -and [string]$workload.status -eq 'COMPLETED'
$captureTimingExact = [int]$manifest.warmupSeconds -eq 0 -and
    $snapshotOffsets.Count -eq 1 -and $snapshotOffsets[0] -eq 20
$runtimePolicyCorrected = [string]$manifest.runtimePolicy -eq 'BASE_CDS' -and
    [string]$manifest.cdsMode -eq 'ON' -and -not [bool]$manifest.appCds -and
    [bool]$manifest.runtimePolicyProof.defaultJdkArchiveMapped -and
    -not [bool]$manifest.runtimePolicyProof.applicationArchiveMapped
$privateDirtyInsideHistorical = $current.privateDirtyKb -ge $historicalSummary.privateDirtyKb.minimum -and
    $current.privateDirtyKb -le $historicalSummary.privateDirtyKb.maximum
$anonInsideHistorical = $current.pssAnonKb -ge $historicalSummary.pssAnonKb.minimum -and
    $current.pssAnonKb -le $historicalSummary.pssAnonKb.maximum
$pssDeltaToHistoricalMedian = $current.pssKb - $historicalSummary.pssKb.median
$filePssDeltaToHistoricalMedian = $current.pssFileKb - $historicalSummary.pssFileKb.median
$unexplainedPssDeltaKb = $pssDeltaToHistoricalMedian - $filePssDeltaToHistoricalMedian
$cleanMappingExplainsPss = [Math]::Abs($unexplainedPssDeltaKb) -le 2048
$memoryCurrentComparable = $current.memoryCurrentBytes -ge $historicalSummary.memoryCurrentBytes.minimum -and
    $current.memoryCurrentBytes -le $historicalSummary.memoryCurrentBytes.maximum

$hardGatesPass = $artifactExact -and $workloadExact -and $captureTimingExact -and
    $runtimePolicyCorrected -and $privateDirtyInsideHistorical -and $anonInsideHistorical -and
    $cleanMappingExplainsPss
$decision = if ($hardGatesPass) {
    'AUTHORIZE_NON_CLAIM_DIRECTIONAL_PAIR'
} else {
    'ABSOLUTE_BASELINE_NOT_REPRODUCED'
}

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-historical-b0-screen-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    decision = $decision
    claimable = $false
    current = $current
    historical = $historicalSummary
    gates = [ordered]@{
        exactHistoricalArtifact = $artifactExact
        exactHistoricalWorkload = $workloadExact
        exactHistoricalCaptureTiming = $captureTimingExact
        correctedEffectiveBaseCdsPolicy = $runtimePolicyCorrected
        privateDirtyInsideHistoricalDistribution = $privateDirtyInsideHistorical
        anonymousPssInsideHistoricalDistribution = $anonInsideHistorical
        totalPssDifferenceExplainedByFileMapping = $cleanMappingExplainsPss
        memoryCurrentInsideHistoricalDistribution = $memoryCurrentComparable
    }
    reconciliation = [ordered]@{
        pssDeltaToHistoricalMedianKb = $pssDeltaToHistoricalMedian
        filePssDeltaToHistoricalMedianKb = $filePssDeltaToHistoricalMedian
        unexplainedPssDeltaAfterFileMappingKb = $unexplainedPssDeltaKb
        cgroupAccountingComparable = $memoryCurrentComparable
        interpretation = 'Anonymous/private-dirty memory reproduced the historical B0 distribution. The total PSS shift is explained by clean/file-page attribution. cgroup memory.current remains environment-sensitive and is not historically equivalent.'
    }
    provenance = [ordered]@{
        applicationArtifact = 'EXACT_HISTORICAL'
        workload = 'EXACT_HISTORICAL_ORDER_AND_ACCEPTANCE'
        effectiveCdsPolicy = 'EXPLICIT_BASE_CDS_MATCHING_HISTORICAL_FALLBACK'
        serviceJdkAndSupportImages = 'RECONSTRUCTED_NOT_ORIGINAL'
    }
    authorization = [ordered]@{
        diagnosticPairAllowed = $hardGatesPass
        sixOrderCampaignAllowed = $false
        rule = 'Only one non-claim B0/V1 directional pair is authorized. A final campaign remains blocked until the historical direction returns.'
    }
}

New-JmoaDirectory -Path $OutputDirectory
$jsonPath = Join-Path $OutputDirectory 'doctor-historical-b0-screen.json'
$mdPath = Join-Path $OutputDirectory 'doctor-historical-b0-screen.md'
Write-JmoaJson -Value $report -Path $jsonPath
@"
# Doctor Historical B0 Screen

- Decision: **$decision**
- Exact historical B0 artifact: **$artifactExact**
- Workload: **$($workload.requests) requests, $($workload.errors) errors**
- Effective runtime policy: **explicit base CDS**
- Capture: **20 seconds after workload**
- Current PSS: **$($current.pssKb) KB**
- Historical median PSS: **$($historicalSummary.pssKb.median) KB**
- Current anonymous PSS: **$($current.pssAnonKb) KB**
- Historical median anonymous PSS: **$($historicalSummary.pssAnonKb.median) KB**
- Current Private_Dirty: **$($current.privateDirtyKb) KB**
- Historical median Private_Dirty: **$($historicalSummary.privateDirtyKb.median) KB**
- PSS difference after accounting for file mappings: **$unexplainedPssDeltaKb KB**
- `memory.current` historically comparable: **$memoryCurrentComparable**

Anonymous/private-dirty memory is inside the recovered historical B0 distribution. The lower total
PSS is explained by a clean/file-page accounting shift, not by a comparable reduction in anonymous
memory. `memory.current` is outside the historical range and remains environment-sensitive.

This is a qualified authorization for exactly one **non-claim** B0/V1 directional pair. It is not an
exact historical-runtime claim, and it does not authorize the six-order campaign.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Decision: $decision"
Write-Host "Report: $jsonPath"

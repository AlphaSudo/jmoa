param(
    [Parameter(Mandatory)][string[]]$PairReportPaths,
    [Parameter(Mandatory)][string[]]$PairManifestPaths,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

if ($PairReportPaths.Count -eq 1 -and $PairReportPaths[0].Contains('|')) {
    $PairReportPaths = @($PairReportPaths[0] -split '\|')
}
if ($PairManifestPaths.Count -eq 1 -and $PairManifestPaths[0].Contains('|')) {
    $PairManifestPaths = @($PairManifestPaths[0] -split '\|')
}
if ($PairReportPaths.Count -ne $PairManifestPaths.Count) {
    throw 'Pair report and pair manifest counts must match.'
}
if ($PairReportPaths.Count -lt 2) { throw 'At least two opposite-order pairs are required.' }

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required JSON is missing: $Path" }
    Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}
function Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[[int][math]::Floor($sorted.Count / 2)] }
    $upper = [int]($sorted.Count / 2)
    ([double]$sorted[$upper - 1] + [double]$sorted[$upper]) / 2.0
}
function Mean([double[]]$Values) {
    [double](($Values | Measure-Object -Average).Average)
}

$pairs = @()
for ($i = 0; $i -lt $PairReportPaths.Count; $i++) {
    $report = Read-Json $PairReportPaths[$i]
    $manifest = Read-Json $PairManifestPaths[$i]
    $order = [string]$manifest.firstVariant
    $period = if ($order -eq 'BASELINE_FIRST') {
        [ordered]@{
            pssKb = [long]$report.deltaV2MinusB0.pssKb
            privateDirtyKb = [long]$report.deltaV2MinusB0.privateDirtyKb
            memoryCurrentBytes = [long]$report.deltaV2MinusB0.memoryCurrentBytes
        }
    } elseif ($order -eq 'CANDIDATE_FIRST') {
        [ordered]@{
            pssKb = -[long]$report.deltaV2MinusB0.pssKb
            privateDirtyKb = -[long]$report.deltaV2MinusB0.privateDirtyKb
            memoryCurrentBytes = -[long]$report.deltaV2MinusB0.memoryCurrentBytes
        }
    } else {
        throw "Unsupported pair order: $order"
    }
    $pairs += [pscustomobject][ordered]@{
        pair = [int]$manifest.pairIndex
        order = $order
        valid = [bool]$report.pairValid
        baseline = $report.baseline
        candidate = $report.candidate
        delta = $report.deltaV2MinusB0
        secondPeriodMinusFirstPeriod = $period
    }
}

$baselineFirstCount = @($pairs | Where-Object order -eq 'BASELINE_FIRST').Count
$candidateFirstCount = @($pairs | Where-Object order -eq 'CANDIDATE_FIRST').Count
$balanced = $baselineFirstCount -eq $candidateFirstCount
$allValid = @($pairs | Where-Object { -not $_.valid }).Count -eq 0

$metrics = @(
    @{ name='pssKb'; absolute=$true },
    @{ name='privateDirtyKb'; absolute=$true },
    @{ name='memoryCurrentBytes'; absolute=$true },
    @{ name='anonymousPssKb'; absolute=$true },
    @{ name='filePssKb'; absolute=$true },
    @{ name='heapPssKb'; absolute=$false },
    @{ name='anonymousRwOutsideHeapPssKb'; absolute=$false },
    @{ name='nmtCommittedKb'; absolute=$false },
    @{ name='loadedClasses'; absolute=$false },
    @{ name='heapUsedKb'; absolute=$false },
    @{ name='metaspaceCommittedKb'; absolute=$false }
)
$summary = [ordered]@{}
foreach ($metric in $metrics) {
    $name = $metric.name
    $deltas = [double[]]@($pairs | ForEach-Object { [double]$_.delta.$name })
    $baselineValues = if ($metric.absolute) { [double[]]@($pairs | ForEach-Object { [double]$_.baseline.$name }) } else { [double[]]@() }
    $candidateValues = if ($metric.absolute) { [double[]]@($pairs | ForEach-Object { [double]$_.candidate.$name }) } else { [double[]]@() }
    $summary[$name] = [ordered]@{
        deltas = $deltas
        medianDelta = Median $deltas
        meanDelta = Mean $deltas
        baselineMedian = if ($metric.absolute) { Median $baselineValues } else { $null }
        candidateMedian = if ($metric.absolute) { Median $candidateValues } else { $null }
        balancedVariantMeanDifference = if ($metric.absolute) { (Mean $candidateValues) - (Mean $baselineValues) } else { $null }
        candidateWins = @($deltas | Where-Object { $_ -lt 0 }).Count
    }
}

$periodSummary = [ordered]@{}
foreach ($name in @('pssKb','privateDirtyKb','memoryCurrentBytes')) {
    $values = [double[]]@($pairs | ForEach-Object { [double]$_.secondPeriodMinusFirstPeriod.$name })
    $periodSummary[$name] = [ordered]@{
        values = $values
        medianSecondMinusFirst = Median $values
        meanSecondMinusFirst = Mean $values
    }
}

$runtimeGate = $allValid -and $balanced -and
    $summary.pssKb.medianDelta -le -1024 -and
    $summary.privateDirtyKb.medianDelta -le -1024 -and
    $summary.memoryCurrentBytes.medianDelta -le -1048576 -and
    $summary.pssKb.candidateWins -ge 3
$regressionGate = $allValid -and $balanced -and
    $summary.pssKb.medianDelta -ge 1024 -and
    $summary.privateDirtyKb.medianDelta -ge 1024 -and
    $summary.memoryCurrentBytes.medianDelta -ge 1048576 -and
    $summary.pssKb.candidateWins -le 1
$decision = if (-not $allValid -or -not $balanced) {
    'BALANCED_SCREEN_INVALID'
} elseif ($runtimeGate) {
    'BALANCED_SCREEN_DIRECTIONAL_WIN_PROMOTE'
} elseif ($regressionGate) {
    'BALANCED_SCREEN_DIRECTIONAL_REGRESSION_STOP'
} else {
    'BALANCED_SCREEN_NO_STABLE_V2_WIN'
}

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-restored-b0-v2-balanced-screen-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    decision = $decision
    claimable = $false
    runtimePolicy = 'PER_CANDIDATE_APP_CDS'
    pairCount = $pairs.Count
    order = [ordered]@{
        baselineFirst = $baselineFirstCount
        candidateFirst = $candidateFirstCount
        balanced = $balanced
    }
    allPairsValid = $allValid
    pairs = $pairs
    metrics = $summary
    periodEffect = $periodSummary
    gates = [ordered]@{
        directionalWin = $runtimeGate
        directionalRegression = $regressionGate
    }
    historicalReference = [ordered]@{
        correctedPairedDeltaMedianPssKb = -2036
        correctedIndependentMedianDeltaPssKb = -2728
        invalidOldMedianDeltaPssKb = -6048
    }
    claimBoundary = 'Four balanced diagnostic pairs diagnose order sensitivity. They do not constitute a new V2-C confirmation or replace the frozen historical claim.'
    nextAction = if ($decision -eq 'BALANCED_SCREEN_DIRECTIONAL_WIN_PROMOTE') {
        'Run a separately frozen V2-C confirmation campaign.'
    } else {
        'Do not transfer the historical Doctor win to the current reconstructed runtime. Investigate period-sensitive anonymous memory before any new claim.'
    }
}

New-JmoaDirectory -Path $OutputDirectory
$jsonPath = Join-Path $OutputDirectory 'doctor-restored-b0-v2-balanced-screen.json'
$mdPath = Join-Path $OutputDirectory 'doctor-restored-b0-v2-balanced-screen.md'
Write-JmoaJson -Value $report -Path $jsonPath
$rows = $pairs | ForEach-Object {
    "| $($_.pair) | $($_.order) | $($_.delta.pssKb) | $($_.delta.privateDirtyKb) | $($_.delta.memoryCurrentBytes) | $($_.delta.anonymousRwOutsideHeapPssKb) | $($_.delta.heapPssKb) |"
}
@"
# Doctor Restored B0/V2 Balanced Screen

- Decision: **$decision**
- Claimable: **false**
- Valid pairs: **$(@($pairs | Where-Object valid).Count)/$($pairs.Count)**
- Order balance: **$baselineFirstCount B0-first / $candidateFirstCount V2-first**
- Median V2 - B0 PSS: **$($summary.pssKb.medianDelta) KB**
- Median V2 - B0 Private_Dirty: **$($summary.privateDirtyKb.medianDelta) KB**
- Median V2 - B0 `memory.current`: **$($summary.memoryCurrentBytes.medianDelta) bytes**
- PSS wins: **$($summary.pssKb.candidateWins)/$($pairs.Count)**
- Mean second-period PSS effect: **$($periodSummary.pssKb.meanSecondMinusFirst) KB**

| Pair | Order | PSS delta KB | Private Dirty delta KB | memory.current delta B | Anonymous RW outside heap delta KB | Heap PSS delta KB |
|---:|---|---:|---:|---:|---:|---:|
$($rows -join "`n")

The exact Phase 32K per-candidate archives mapped in every arm. Opposite orders
produced opposite directions, so this balanced diagnostic does not automatically
transfer the historical Doctor claim to the current reconstructed runtime.
"@ | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "Decision: $decision"
Write-Host "Report: $jsonPath"

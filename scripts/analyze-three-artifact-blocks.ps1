param(
    [Parameter(Mandatory)][string]$SessionIndexPath,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [long]$SubstantialPssGateKb = -4096,
    [long]$PrivateDirtyGateKb = -1024,
    [long]$MemoryCurrentGateBytes = -1048576
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')

function Get-Median {
    param([double[]]$Values)
    if ($Values.Count -eq 0) { throw 'A median requires at least one value.' }
    $ordered = @($Values | Sort-Object)
    if (($ordered.Count % 2) -eq 1) {
        return [double]$ordered[[int][math]::Floor($ordered.Count / 2)]
    }
    return ([double]$ordered[$ordered.Count / 2 - 1] + [double]$ordered[$ordered.Count / 2]) / 2.0
}

function Get-Percentile {
    param([double[]]$SortedValues, [double]$Probability)
    if ($SortedValues.Count -eq 0) { throw 'A percentile requires at least one value.' }
    $rank = ($SortedValues.Count - 1) * $Probability
    $lower = [int][math]::Floor($rank)
    $upper = [int][math]::Ceiling($rank)
    if ($lower -eq $upper) { return [double]$SortedValues[$lower] }
    $weight = $rank - $lower
    return ([double]$SortedValues[$lower] * (1.0 - $weight)) + ([double]$SortedValues[$upper] * $weight)
}

function Get-ExactMedianBootstrap {
    param([double[]]$Values)
    if ($Values.Count -ne 6) {
        throw "The frozen balanced campaign requires exactly six block deltas; got $($Values.Count)."
    }
    # Enumerating all 6^6 ordered resamples makes the bootstrap deterministic.
    $medians = [double[]]::new(46656)
    $sample = [double[]]::new(6)
    $cursor = 0
    for ($a = 0; $a -lt 6; $a++) {
        $sample[0] = $Values[$a]
        for ($b = 0; $b -lt 6; $b++) {
            $sample[1] = $Values[$b]
            for ($c = 0; $c -lt 6; $c++) {
                $sample[2] = $Values[$c]
                for ($d = 0; $d -lt 6; $d++) {
                    $sample[3] = $Values[$d]
                    for ($e = 0; $e -lt 6; $e++) {
                        $sample[4] = $Values[$e]
                        for ($f = 0; $f -lt 6; $f++) {
                            $sample[5] = $Values[$f]
                            $medians[$cursor++] = Get-Median $sample
                        }
                    }
                }
            }
        }
    }
    [Array]::Sort($medians)
    [ordered]@{
        method = 'EXACT_NONPARAMETRIC_PERCENTILE_BOOTSTRAP_OF_MEDIAN'
        resamples = 46656
        confidence = 0.95
        lower = Get-Percentile -SortedValues $medians -Probability 0.025
        upper = Get-Percentile -SortedValues $medians -Probability 0.975
    }
}

function Get-MetricSummary {
    param([double[]]$Values)
    $median = Get-Median $Values
    $deviations = @($Values | ForEach-Object { [math]::Abs($_ - $median) })
    [ordered]@{
        values = @($Values)
        median = $median
        mean = [math]::Round(($Values | Measure-Object -Average).Average, 3)
        minimum = ($Values | Measure-Object -Minimum).Minimum
        maximum = ($Values | Measure-Object -Maximum).Maximum
        medianAbsoluteDeviation = Get-Median $deviations
        pairedWins = @($Values | Where-Object { $_ -lt 0 }).Count
        bootstrap95 = Get-ExactMedianBootstrap $Values
    }
}

function Get-Comparison {
    param(
        [object[]]$Blocks,
        [string]$From,
        [string]$To
    )
    $deltas = foreach ($block in $Blocks) {
        $fromRun = @($block.sessions | Where-Object variant -eq $From)
        $toRun = @($block.sessions | Where-Object variant -eq $To)
        if ($fromRun.Count -ne 1 -or $toRun.Count -ne 1) {
            throw "Block $($block.block) does not contain exactly one $From and one $To session."
        }
        [ordered]@{
            block = [int]$block.block
            order = @($block.order)
            fromSessionId = [string]$fromRun[0].sessionId
            toSessionId = [string]$toRun[0].sessionId
            pssKb = [long]$toRun[0].pssKb - [long]$fromRun[0].pssKb
            privateDirtyKb = [long]$toRun[0].privateDirtyKb - [long]$fromRun[0].privateDirtyKb
            memoryCurrentBytes = [long]$toRun[0].memoryCurrentBytes - [long]$fromRun[0].memoryCurrentBytes
            heapPssKb = [long]$toRun[0].heapPssKb - [long]$fromRun[0].heapPssKb
            loadedClasses = [long]$toRun[0].loadedClasses - [long]$fromRun[0].loadedClasses
            metaspaceUsedKb = [long]$toRun[0].metaspaceUsedKb - [long]$fromRun[0].metaspaceUsedKb
            startupMillis = [long]$toRun[0].startupMillis - [long]$fromRun[0].startupMillis
        }
    }
    $pss = [double[]]@($deltas | ForEach-Object pssKb)
    $dirty = [double[]]@($deltas | ForEach-Object privateDirtyKb)
    $current = [double[]]@($deltas | ForEach-Object memoryCurrentBytes)
    [ordered]@{
        id = "${From}_TO_${To}"
        from = $From
        to = $To
        deltas = @($deltas)
        metrics = [ordered]@{
            pssKb = Get-MetricSummary $pss
            privateDirtyKb = Get-MetricSummary $dirty
            memoryCurrentBytes = Get-MetricSummary $current
            heapPssKb = Get-MetricSummary ([double[]]@($deltas | ForEach-Object heapPssKb))
            loadedClasses = Get-MetricSummary ([double[]]@($deltas | ForEach-Object loadedClasses))
            metaspaceUsedKb = Get-MetricSummary ([double[]]@($deltas | ForEach-Object metaspaceUsedKb))
            startupMillis = Get-MetricSummary ([double[]]@($deltas | ForEach-Object startupMillis))
        }
    }
}

if (-not (Test-Path -LiteralPath $SessionIndexPath -PathType Leaf)) {
    throw "Session index does not exist: $SessionIndexPath"
}
$index = Get-Content -Raw -LiteralPath $SessionIndexPath | ConvertFrom-Json
$blocks = @($index.blocks | Sort-Object block)
if ($blocks.Count -ne 6) { throw "Expected six blocks, found $($blocks.Count)." }
$sessions = @($blocks | ForEach-Object sessions)
$validSessions = @($sessions | Where-Object { [bool]$_.valid }).Count
$semanticErrors = [int](($sessions | Measure-Object -Property semanticErrors -Sum).Sum)

$b0v1 = Get-Comparison -Blocks $blocks -From B0 -To V1
$v1v2 = Get-Comparison -Blocks $blocks -From V1 -To V2
$b0v2 = Get-Comparison -Blocks $blocks -From B0 -To V2
$directGate = [ordered]@{
    allSessionsValid = ($sessions.Count -eq 18 -and $validSessions -eq 18)
    pairedWins = ([int]$b0v2.metrics.pssKb.pairedWins -ge 4)
    substantialPss = ([double]$b0v2.metrics.pssKb.median -le $SubstantialPssGateKb)
    pssBootstrapUpperBelowZero = ([double]$b0v2.metrics.pssKb.bootstrap95.upper -lt 0)
    privateDirty = ([double]$b0v2.metrics.privateDirtyKb.median -le $PrivateDirtyGateKb)
    memoryCurrent = ([double]$b0v2.metrics.memoryCurrentBytes.median -le $MemoryCurrentGateBytes)
    semantics = ($semanticErrors -eq 0)
}
$directGatePassed = -not ($directGate.Values -contains $false)
$terminal = if ($directGatePassed) {
    'COMPLETE_PRODUCT_WIN'
} elseif (
    $directGate.allSessionsValid -and $directGate.pairedWins -and
    [double]$b0v2.metrics.pssKb.median -lt 0 -and
    $directGate.pssBootstrapUpperBelowZero -and $directGate.semantics
) {
    'COMPLETE_PRODUCT_WIN_BELOW_4MIB'
} elseif ($semanticErrors -ne 0) {
    'SEMANTIC_INCOMPATIBILITY'
} elseif (-not $directGate.allSessionsValid) {
    'RUNTIME_ENVIRONMENT_INVALID'
} elseif ([double]$b0v1.metrics.pssKb.median -ge 0) {
    'B0_TO_V1_NOT_REPRODUCED'
} elseif ([double]$v1v2.metrics.pssKb.median -ge 0) {
    'V1_TO_V2_NOT_REPRODUCED'
} else {
    'PRODUCT_EFFECT_NOT_CONFIRMED'
}

$report = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-balanced-analysis-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    service = [string]$index.service
    protocol = [string]$index.protocol
    sessions = $sessions.Count
    validSessions = $validSessions
    semanticErrors = $semanticErrors
    comparisons = @($b0v1, $v1v2, $b0v2)
    directProductGate = $directGate
    directProductGatePassed = $directGatePassed
    terminalOutcome = $terminal
    claimBoundary = 'All effects are direct within-block measurements. Historical medians are diagnostic references only and are never added.'
}
New-JmoaDirectory $OutputDirectory
$jsonPath = Join-Path $OutputDirectory 'three-artifact-analysis.json'
Write-JmoaJson $report $jsonPath

$rows = foreach ($comparison in $report.comparisons) {
    "| $($comparison.id) | $($comparison.metrics.pssKb.pairedWins)/6 | $($comparison.metrics.pssKb.median) | $($comparison.metrics.pssKb.mean) | $($comparison.metrics.pssKb.medianAbsoluteDeviation) | [$($comparison.metrics.pssKb.bootstrap95.lower), $($comparison.metrics.pssKb.bootstrap95.upper)] | $($comparison.metrics.privateDirtyKb.median) | $($comparison.metrics.memoryCurrentBytes.median) |"
}
$markdown = @"
# Three-Artifact Balanced Campaign Analysis

- Service: ``$($report.service)``
- Protocol: ``$($report.protocol)``
- Sessions: $validSessions/18 valid
- Semantic errors: $semanticErrors
- Terminal outcome: **$terminal**

| Comparison | PSS wins | Median PSS KB | Mean PSS KB | PSS MAD KB | Exact bootstrap 95% CI KB | Median Private_Dirty KB | Median memory.current B |
| --- | ---: | ---: | ---: | ---: | --- | ---: | ---: |
$($rows -join "`n")

The confidence interval is the percentile interval of the median over all
``6^6 = 46,656`` ordered nonparametric bootstrap resamples. Historical medians
are not arithmetic inputs.
"@
Write-JmoaText $markdown (Join-Path $OutputDirectory 'three-artifact-analysis.md')
$report | ConvertTo-Json -Depth 20

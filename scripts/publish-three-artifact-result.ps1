param(
    [Parameter(Mandatory)][string]$FinalVerdictPath,
    [Parameter(Mandatory)][string]$LaunchMode,
    [Parameter(Mandatory)][string]$RuntimePolicy,
    [Parameter(Mandatory)][string]$OutputJson,
    [Parameter(Mandatory)][string]$OutputMarkdown
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

if (-not (Test-Path -LiteralPath $FinalVerdictPath -PathType Leaf)) {
    throw "Final verdict does not exist: $FinalVerdictPath"
}
$verdict = Get-Content -Raw -LiteralPath $FinalVerdictPath | ConvertFrom-Json

function Get-Comparison {
    param([string]$Id)
    $comparison = @($verdict.blockAnalysis.comparisons | Where-Object id -eq $Id)
    if ($comparison.Count -ne 1) { throw "Final verdict requires exactly one $Id comparison." }
    $evidence = @($verdict.evidence | Where-Object leg -eq $Id)
    if ($evidence.Count -ne 1) { throw "Final verdict requires exactly one $Id evidence result." }
    $pss = $comparison[0].metrics.pssKb
    $dirty = $comparison[0].metrics.privateDirtyKb
    $current = $comparison[0].metrics.memoryCurrentBytes
    [ordered]@{
        id = $Id
        blockDeltas = @($comparison[0].deltas | ForEach-Object {
            [ordered]@{
                block = [int]$_.block
                order = @($_.order)
                pssKb = [long]$_.pssKb
                privateDirtyKb = [long]$_.privateDirtyKb
                memoryCurrentBytes = [long]$_.memoryCurrentBytes
            }
        })
        pssKb = [ordered]@{
            median = [double]$pss.median
            mean = [double]$pss.mean
            minimum = [double]$pss.minimum
            maximum = [double]$pss.maximum
            medianAbsoluteDeviation = [double]$pss.medianAbsoluteDeviation
            pairedWins = [int]$pss.pairedWins
            bootstrap95 = [ordered]@{ lower = [double]$pss.bootstrap95.lower; upper = [double]$pss.bootstrap95.upper }
        }
        privateDirtyKb = [ordered]@{
            median = [double]$dirty.median
            pairedWins = [int]$dirty.pairedWins
            bootstrap95 = [ordered]@{ lower = [double]$dirty.bootstrap95.lower; upper = [double]$dirty.bootstrap95.upper }
        }
        memoryCurrentBytes = [ordered]@{
            median = [double]$current.median
            pairedWins = [int]$current.pairedWins
            bootstrap95 = [ordered]@{ lower = [double]$current.bootstrap95.lower; upper = [double]$current.bootstrap95.upper }
        }
        v2cVerdict = [string]$evidence[0].v2cVerdict
        v2dPassed = [bool]$evidence[0].v2dPassed
    }
}

$comparisons = @(
    Get-Comparison 'B0_TO_V1'
    Get-Comparison 'V1_TO_V2'
    Get-Comparison 'B0_TO_V2'
)
$directEvidence = @($verdict.evidence | Where-Object leg -eq 'B0_TO_V2')[0]
$attributionPath = Join-Path ([string]$directEvidence.attributionDirectory) 'jmoa-memory-attribution.json'
$attribution = if (Test-Path -LiteralPath $attributionPath -PathType Leaf) {
    $report = Get-Content -Raw -LiteralPath $attributionPath | ConvertFrom-Json
    [ordered]@{
        reconciliation = [string]$report.smapsNmtReconciliation.classification
        heapObjectClassification = [string]$report.heapObjectAttribution.classification
        hypotheses = @($report.causalHypotheses | ForEach-Object {
            [ordered]@{ hypothesis = [string]$_.hypothesis; confidence = [string]$_.confidence; evidence = @($_.evidence) }
        })
    }
} else {
    [ordered]@{ reconciliation = 'UNAVAILABLE'; heapObjectClassification = 'UNAVAILABLE'; hypotheses = @() }
}

$public = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-public-result-v2'
    service = [string]$verdict.service
    protocol = [string]$verdict.protocol
    terminalOutcome = [string]$verdict.terminalOutcome
    runtime = [ordered]@{
        launchMode = $LaunchMode
        policy = $RuntimePolicy
        validFinalSessions = [int]$verdict.blockAnalysis.validSessions
        finalSessions = [int]$verdict.blockAnalysis.sessions
        balancedBlocks = 6
        semanticErrors = [int]$verdict.blockAnalysis.semanticErrors
    }
    qualification = [ordered]@{
        passed = [bool]$verdict.qualification.passed
        directDiagnosticDeltas = $verdict.qualification.directDiagnosticDeltas
        claimBoundary = [string]$verdict.qualification.claimBoundary
    }
    comparisons = $comparisons
    directProductGate = $verdict.checks
    attribution = $attribution
    evidenceBoundary = 'Raw run-level evidence, local paths, private configuration, service source, credentials, runtime images, and CDS archives are excluded from Git.'
}
Write-JmoaJson -Value $public -Path $OutputJson

$lines = [Collections.Generic.List[string]]::new()
$lines.Add("# $($public.service) Direct B0/V1/V2 Result")
$lines.Add('')
$lines.Add("- Terminal outcome: **$($public.terminalOutcome)**")
$lines.Add("- Protocol: ``$($public.protocol)``")
$lines.Add("- Runtime: ``$LaunchMode`` / ``$RuntimePolicy``")
$lines.Add("- Final observations: $($public.runtime.validFinalSessions)/$($public.runtime.finalSessions) valid; semantic errors: $($public.runtime.semanticErrors)")
$lines.Add('')
$lines.Add('| Comparison | PSS median | PSS wins | PSS 95% CI | Private Dirty median | memory.current median | V2-C | V2-D |')
$lines.Add('|---|---:|---:|---:|---:|---:|---|---|')
foreach ($comparison in $comparisons) {
    $lines.Add("| $($comparison.id) | $($comparison.pssKb.median) KB | $($comparison.pssKb.pairedWins)/6 | [$($comparison.pssKb.bootstrap95.lower), $($comparison.pssKb.bootstrap95.upper)] KB | $($comparison.privateDirtyKb.median) KB | $($comparison.memoryCurrentBytes.median) B | $($comparison.v2cVerdict) | $($comparison.v2dPassed) |")
}
$lines.Add('')
$lines.Add('## B0 To V2 Blocks')
$lines.Add('')
$lines.Add('| Block | Order | PSS | Private Dirty | memory.current |')
$lines.Add('|---:|---|---:|---:|---:|')
foreach ($delta in @($comparisons | Where-Object id -eq 'B0_TO_V2')[0].blockDeltas) {
    $lines.Add("| $($delta.block) | $($delta.order -join ',') | $($delta.pssKb) KB | $($delta.privateDirtyKb) KB | $($delta.memoryCurrentBytes) B |")
}
$lines.Add('')
$lines.Add('## Attribution')
$lines.Add('')
$lines.Add("- smaps/NMT reconciliation: ``$($attribution.reconciliation)``")
$lines.Add("- heap/object classification: ``$($attribution.heapObjectClassification)``")
foreach ($hypothesis in $attribution.hypotheses) {
    $lines.Add("- $($hypothesis.hypothesis) ($($hypothesis.confidence)): $($hypothesis.evidence -join '; ')")
}
$lines.Add('')
$lines.Add('Qualification is diagnostic only. All headline effects above come from six direct within-block comparisons; historical medians were not added.')
$lines.Add('')
$lines.Add($public.evidenceBoundary)
Write-JmoaText -Value ($lines -join "`n") -Path $OutputMarkdown

Write-Host "Published sanitized three-artifact result for $($public.service)."

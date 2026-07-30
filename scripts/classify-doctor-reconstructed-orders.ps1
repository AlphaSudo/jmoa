param(
    [Parameter(Mandatory)][string]$B0FirstAttribution,
    [Parameter(Mandatory)][string]$V1FirstAttribution,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$b0First = Get-Content -Raw -LiteralPath $B0FirstAttribution | ConvertFrom-Json
$v1First = Get-Content -Raw -LiteralPath $V1FirstAttribution | ConvertFrom-Json
$d1 = [long]$b0First.headline.pssDeltaKb
$d2 = [long]$v1First.headline.pssDeltaKb
$dirty1 = [long]$b0First.headline.privateDirtyDeltaKb
$dirty2 = [long]$v1First.headline.privateDirtyDeltaKb
$secondPositionPss = [ordered]@{
    v1SecondMinusB0FirstKb = $d1
    b0SecondMinusV1FirstKb = -$d2
}
$sameSign = [math]::Sign($d1) -eq [math]::Sign($d2)
$similarOpposite = [math]::Sign($d1) -ne [math]::Sign($d2) -and
    [math]::Abs([math]::Abs($d1) - [math]::Abs($d2)) -le 2048
$bothSecondPositive = $secondPositionPss.v1SecondMinusB0FirstKb -gt 0 -and
    $secondPositionPss.b0SecondMinusV1FirstKb -gt 0
$classification = if ($d1 -gt 0 -and $d2 -gt 0) {
    'V1_RUNTIME_COST'
} elseif ($bothSecondPositive -or $similarOpposite) {
    'SECOND_POSITION_EFFECT'
} else {
    'MIXED_OR_UNRESOLVED'
}
$artifactAverage = [math]::Round(($d1 + $d2) / 2.0, 1)
$positionAverage = [math]::Round(($d1 - $d2) / 2.0, 1)
$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-reconstructed-order-classification-v1'
    status = 'TWO_ORDER_DIAGNOSTIC_COMPLETE'
    classification = $classification
    pss = [ordered]@{
        d1V1SecondMinusB0FirstKb = $d1
        d2V1FirstMinusB0SecondKb = $d2
        orderBalancedArtifactEstimateKb = $artifactAverage
        orderPositionEstimateKb = $positionAverage
        secondPosition = $secondPositionPss
    }
    privateDirty = [ordered]@{
        d1V1SecondMinusB0FirstKb = $dirty1
        d2V1FirstMinusB0SecondKb = $dirty2
        orderBalancedArtifactEstimateKb = [math]::Round(($dirty1 + $dirty2) / 2.0, 1)
        orderPositionEstimateKb = [math]::Round(($dirty1 - $dirty2) / 2.0, 1)
    }
    timing = [ordered]@{
        b0FirstPair = $b0First.timing.classification
        v1FirstPair = $v1First.timing.classification
    }
    interpretation = switch ($classification) {
        'V1_RUNTIME_COST' { 'V1 is more expensive in both observed orders; this supports a reconstructed-runtime V1 cost, not an exact historical replay claim.' }
        'SECOND_POSITION_EFFECT' { 'The sign reverses with execution order and the second arm is more expensive; execution position dominates this two-order diagnostic.' }
        default { 'The two order results do not isolate one stable artifact or position effect. No stronger V1 direction claim is allowed.' }
    }
    claimBoundary = @(
        'This is a two-order reconstructed diagnostic, not a six-order performance campaign.',
        'The support environment and JDK are reconstructed even though the application artifacts are exact.',
        'The historical workload is weakly business-active and does not prove broad V1 mechanism activation.',
        'No third performance pair follows automatically.'
    )
}
New-JmoaDirectory $OutputDirectory
Write-JmoaJson $report (Join-Path $OutputDirectory 'doctor-reconstructed-order-classification.json')
Write-JmoaText @"
# Doctor Reconstructed Order Classification

- Classification: **$classification**
- d1, V1 second - B0 first: **$d1 KB PSS**
- d2, V1 first - B0 second: **$d2 KB PSS**
- Order-balanced artifact estimate: **$artifactAverage KB PSS**
- Order-position estimate: **$positionAverage KB PSS**
- B0-first pair timing: **$($b0First.timing.classification)**
- V1-first pair timing: **$($v1First.timing.classification)**

$($report.interpretation)

This is a two-order reconstructed diagnostic, not a historical replay or a
product performance claim. The exact artifacts ran in a reconstructed support
environment, and the workload primarily exercises Actuator plus one read-only
business endpoint.
"@ (Join-Path $OutputDirectory 'doctor-reconstructed-order-classification.md')
Write-Host "Doctor reconstructed order classification: $classification (d1=$d1 KB, d2=$d2 KB)"

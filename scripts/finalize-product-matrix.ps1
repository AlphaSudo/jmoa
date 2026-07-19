param(
    [Parameter(Mandatory)][string[]]$ServiceResult,
    [Parameter(Mandatory)][string]$OutputDir,
    [switch]$UnderRuntimeReconciliation
)

$ErrorActionPreference = 'Stop'
$rows = foreach ($path in $ServiceResult) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing service result: $path" }
    Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}

foreach ($row in $rows) {
    foreach ($field in @('service','runtimePolicy','status','validRuns','pairedWins','pairs','medianPssDeltaKb','medianPrivateDirtyDeltaKb','medianMemoryCurrentDeltaBytes','v2cVerdict','v2dPassed')) {
        if ($null -eq $row.$field) { throw "Service result is missing '$field': $($row.service)" }
    }
    $row | Add-Member -NotePropertyName substantialProductGatePassed -NotePropertyValue ([bool](
        $row.status -eq 'CONFIRMED' -and
        [int]$row.validRuns -eq 6 -and
        [int]$row.pairedWins -ge 2 -and
        [int]$row.pairs -eq 3 -and
        [long]$row.medianPssDeltaKb -le -4096 -and
        [long]$row.medianPrivateDirtyDeltaKb -le -1024 -and
        [long]$row.medianMemoryCurrentDeltaBytes -le -1048576 -and
        [int]$row.workloadErrors -eq 0 -and
        [int]$row.semanticErrors -eq 0 -and
        $row.v2cVerdict -eq 'CONFIRMED_WIN' -and
        [bool]$row.v2dPassed
    )) -Force
}

$passes = @($rows | Where-Object substantialProductGatePassed).Count
$overall = switch ($passes) {
    3 { 'THREE_SERVICE_PRODUCT_WIN' }
    2 { 'TWO_SERVICE_PRODUCT_WIN' }
    1 { 'ONE_SERVICE_PRODUCT_WIN' }
    default { 'PRODUCT_EFFECT_NOT_CONFIRMED' }
}
$measuredOverall = $overall
if ($UnderRuntimeReconciliation) { $overall = 'DIRECT_PRODUCT_MATRIX_UNDER_RECONCILIATION' }

$report = [ordered]@{
    metadataVersion = 'jmoa-vs-no-jmoa-matrix-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    comparison = 'NO_JMOA_B0_TO_FINAL_JMOA_V2'
    overallState = $overall
    measuredGateState = $measuredOverall
    substantialProductWins = $passes
    evaluatedServices = $rows.Count
    services = @($rows)
    claimBoundary = if($UnderRuntimeReconciliation){'Direct measurements are retained, but no aggregate adoption verdict is permitted until runtime-equivalence and artifact-lineage gates close.'}else{'Direct measured comparisons only. No B0-to-V1 and V1-to-V2 arithmetic is used.'}
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $OutputDir 'jmoa-vs-no-jmoa-matrix.json') -Encoding UTF8
$lines = @(
    '# JMOA Versus No-JMOA Product Matrix',
    '',
    "Overall state: ``$overall``",
    '',
    '| Service | Policy | Status | Valid | Wins | Median PSS | PSS % | Private_Dirty | memory.current | Gate |',
    '| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |'
)
foreach ($row in $rows) {
    $pct = if ($null -eq $row.medianPssPercent) { 'n/a' } else { ('{0:N2}%' -f [double]$row.medianPssPercent) }
    $validLabel = if ($row.status -eq 'CONFIRMED') { "$($row.validRuns)/6" } else { "$($row.validRuns)/$($row.validRuns) screen" }
    $lines += "| $($row.service) | ``$($row.runtimePolicy)`` | $($row.status) | $validLabel | $($row.pairedWins)/$($row.pairs) | $($row.medianPssDeltaKb) KB | $pct | $($row.medianPrivateDirtyDeltaKb) KB | $($row.medianMemoryCurrentDeltaBytes) B | $($row.substantialProductGatePassed) |"
}
$lines += ''
$lines += $report.claimBoundary
$lines | Set-Content -LiteralPath (Join-Path $OutputDir 'jmoa-vs-no-jmoa-matrix.md') -Encoding UTF8
Write-Host "Final product state: $overall"

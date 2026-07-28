param(
    [Parameter(Mandatory)][string[]]$ResultPaths,
    [Parameter(Mandatory)][string]$OutputJson,
    [Parameter(Mandatory)][string]$OutputMarkdown
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$results = @($ResultPaths | ForEach-Object {
    if (-not (Test-Path -LiteralPath $_ -PathType Leaf)) { throw "Sanitized result is missing: $_" }
    Get-Content -Raw -LiteralPath $_ | ConvertFrom-Json
})
if ($results.Count -ne 3) { throw 'The final matrix requires exactly three sanitized service results.' }
if (@($results.service | Select-Object -Unique).Count -ne 3) { throw 'The final matrix requires three distinct services.' }

$rows = @($results | ForEach-Object {
    $result = $_
    $direct = @($result.comparisons | Where-Object id -eq 'B0_TO_V2')
    if ($direct.Count -ne 1) { throw "$($result.service) is missing one direct B0_TO_V2 comparison." }
    [ordered]@{
        service = [string]$result.service
        protocol = [string]$result.protocol
        launchMode = [string]$result.runtime.launchMode
        runtimePolicy = [string]$result.runtime.policy
        terminalOutcome = [string]$result.terminalOutcome
        validSessions = [int]$result.runtime.validFinalSessions
        pairedWins = [int]$direct[0].pssKb.pairedWins
        medianPssDeltaKb = [double]$direct[0].pssKb.median
        meanPssDeltaKb = [double]$direct[0].pssKb.mean
        pssBootstrap95LowerKb = [double]$direct[0].pssKb.bootstrap95.lower
        pssBootstrap95UpperKb = [double]$direct[0].pssKb.bootstrap95.upper
        medianPrivateDirtyDeltaKb = [double]$direct[0].privateDirtyKb.median
        medianMemoryCurrentDeltaBytes = [double]$direct[0].memoryCurrentBytes.median
        v2cVerdict = [string]$direct[0].v2cVerdict
        v2dPassed = [bool]$direct[0].v2dPassed
    }
})

$completeWins = @($rows | Where-Object terminalOutcome -eq 'COMPLETE_PRODUCT_WIN')
$matrix = [ordered]@{
    schemaVersion = 'jmoa-three-service-direct-matrix-v1'
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    comparison = 'DIRECT_B0_TO_V2_SIX_BALANCED_BLOCKS'
    services = $rows
    launchCriterion = [ordered]@{
        requiredServices = 3
        completeProductWins = $completeWins.Count
        passed = ($completeWins.Count -eq 3)
    }
    claimBoundary = 'Every row is a direct within-runtime B0-to-V2 result. Historical medians and V1-to-V2 medians are not added to these values.'
}
Write-JmoaJson -Value $matrix -Path $OutputJson

$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# JMOA Direct B0 To V2 Three-Service Matrix')
$lines.Add('')
$lines.Add("| Service | Runtime | Valid | Wins | Median PSS | 95% CI | Private Dirty | memory.current | Outcome |")
$lines.Add('|---|---|---:|---:|---:|---:|---:|---:|---|')
foreach ($row in $rows) {
    $lines.Add("| $($row.service) | $($row.launchMode) / $($row.runtimePolicy) | $($row.validSessions)/18 | $($row.pairedWins)/6 | $($row.medianPssDeltaKb) KB | [$($row.pssBootstrap95LowerKb), $($row.pssBootstrap95UpperKb)] KB | $($row.medianPrivateDirtyDeltaKb) KB | $($row.medianMemoryCurrentDeltaBytes) B | $($row.terminalOutcome) |")
}
$lines.Add('')
$lines.Add("Launch criterion: **$(if ($matrix.launchCriterion.passed) { 'PASSED' } else { 'NOT PASSED' })** ($($completeWins.Count)/3 complete product wins).")
$lines.Add('')
$lines.Add($matrix.claimBoundary)
Write-JmoaText -Value ($lines -join "`n") -Path $OutputMarkdown

Write-Host "Built direct three-service matrix: $($matrix.launchCriterion.completeProductWins)/3 complete wins."

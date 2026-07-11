param(
    [Parameter(Mandatory)][string[]]$WorkflowReport,
    [string]$ClaimRegisterPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/v2-claim-register.md'),
    [string]$OutputDir = 'target/jmoa-claim-register-guard',
    [switch]$FailOnViolation
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if (-not (Test-Path -LiteralPath $ClaimRegisterPath -PathType Leaf)) { throw "Claim register is missing: $ClaimRegisterPath" }
New-JmoaDirectory -Path $OutputDir
$register = Get-Content -LiteralPath $ClaimRegisterPath -Raw
$checks = @()
foreach ($path in $WorkflowReport) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Workflow report is missing: $path" }
    $report = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    $violations = @()
    if ($report.claimDeclared -eq $true) { $violations += 'Workflow reports must not declare claims; update the claim register through reviewed documentation.' }
    if ($report.claimAllowed -eq $true) {
        if ([string]::IsNullOrWhiteSpace([string]$report.claimRegisterReference)) { $violations += 'CLAIM_ALLOWED requires claimRegisterReference.' }
        elseif (-not $register.Contains([string]$report.claimRegisterReference)) { $violations += "Claim register does not contain reference: $($report.claimRegisterReference)" }
        $confirmation = @($report.gates | Where-Object name -eq 'confirmation' | Select-Object -First 1)
        $attribution = @($report.gates | Where-Object name -eq 'attribution' | Select-Object -First 1)
        if ($confirmation.status -ne 'CONFIRMED_WIN') { $violations += 'CLAIM_ALLOWED requires V2-C CONFIRMED_WIN.' }
        if ($attribution.status -notmatch '^(PASSED|ANALYZED|CONFIRMED)') { $violations += 'CLAIM_ALLOWED requires a V2-D attribution report.' }
    }
    if ($report.workflowState -match '(BLOCKED|FAILED)' -and $report.claimAllowed -eq $true) { $violations += 'Blocked or failed workflow cannot be claim-allowed.' }
    $checks += [ordered]@{ workflowReport = $path; service = $report.service; workflowState = $report.workflowState; claimAllowed = [bool]$report.claimAllowed; passed = $violations.Count -eq 0; violations = $violations }
}
$guard = [ordered]@{ metadataVersion = 'v2p-claim-register-guard'; generatedAt = (Get-Date).ToUniversalTime().ToString('o'); claimRegister = $ClaimRegisterPath; reports = $checks.Count; passedReports = @($checks | Where-Object passed).Count; failedReports = @($checks | Where-Object {-not $_.passed}).Count; checks = $checks }
Write-JmoaJson -Value $guard -Path (Join-Path $OutputDir 'jmoa-claim-register-guard.json')
$lines = @('# JMOA Claim Register Guard', '', "Reports: $($guard.reports)", "Passed: $($guard.passedReports)", "Failed: $($guard.failedReports)") + @($checks | ForEach-Object { "- ``$($_.service)``: ``$($_.workflowState)`` / passed=$($_.passed)" })
Write-JmoaText -Value ($lines -join [Environment]::NewLine) -Path (Join-Path $OutputDir 'jmoa-claim-register-guard.md')
if ($FailOnViolation -and $guard.failedReports -gt 0) { throw "Claim register guard failed with $($guard.failedReports) violating report(s)." }
Write-Host "Claim register guard written to $OutputDir"

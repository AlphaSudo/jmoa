param(
  [string]$BlockerFile = "docs/v2-final/v2-release-blockers.json",
  [switch]$AllowP0
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $BlockerFile)) {
  throw "V2 final blocker file not found: $BlockerFile"
}

$blockers = Get-Content -LiteralPath $BlockerFile -Raw | ConvertFrom-Json
$p0 = @($blockers.p0)
$p1 = @($blockers.p1)

Write-Host "V2 final release decision: $($blockers.decision)"
Write-Host "P0 blockers: $($p0.Count)"
foreach ($blocker in $p0) {
  Write-Host "  - $($blocker.id): $($blocker.requiredFix)"
}

Write-Host "P1 blockers: $($p1.Count)"
foreach ($blocker in $p1) {
  Write-Host "  - $blocker"
}

if ($p0.Count -gt 0 -and -not $AllowP0) {
  throw "V2 final release gate failed: unresolved P0 blockers remain. Use -AllowP0 only for diagnostic CI jobs, never for rc/release tagging."
}

Write-Host "V2 final release gate passed for this invocation."

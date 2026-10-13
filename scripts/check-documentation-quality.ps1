param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$SkipPublicationSafety
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

function Get-TrackedFiles([string]$Pattern) {
    $files = & git -C $RepoRoot ls-files --cached --others --exclude-standard $Pattern
    if ($LASTEXITCODE -ne 0) { throw "Could not enumerate working-tree $Pattern files." }
    return @($files |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique |
        Where-Object { Test-Path -LiteralPath (Join-Path $RepoRoot $_) -PathType Leaf })
}

function Assert-LocalMarkdownLinks {
    $errors = [System.Collections.Generic.List[string]]::new()
    foreach ($relative in Get-TrackedFiles '*.md') {
        $source = Join-Path $RepoRoot $relative
        $text = Get-Content -Raw -LiteralPath $source
        $matches = [regex]::Matches($text, '!?(?:\[[^\]]*\])\(([^)]+)\)')
        foreach ($match in $matches) {
            $target = $match.Groups[1].Value.Trim()
            if ($target.StartsWith('<') -and $target.EndsWith('>')) { $target = $target.Substring(1, $target.Length - 2) }
            $target = ($target -split '\s+"', 2)[0]
            if ($target -match '^(?i:https?://|mailto:|#)') { continue }
            $pathPart = ($target -split '#', 2)[0]
            if ([string]::IsNullOrWhiteSpace($pathPart)) { continue }
            $decoded = [Uri]::UnescapeDataString($pathPart)
            $resolved = Join-Path (Split-Path -Parent $source) $decoded
            if (-not (Test-Path -LiteralPath $resolved)) {
                $errors.Add("$relative -> $target")
            }
        }
    }
    if ($errors.Count -gt 0) { throw "Broken local Markdown links:`n$($errors -join "`n")" }
}

function Assert-JsonParses {
    foreach ($relative in Get-TrackedFiles '*.json') {
        $path = Join-Path $RepoRoot $relative
        try { Get-Content -Raw -LiteralPath $path | ConvertFrom-Json | Out-Null }
        catch { throw "Invalid JSON: $relative`n$($_.Exception.Message)" }
    }
}

function Assert-ClaimConsistency {
    $matrix = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'docs/v2-final/v2-three-service-memory-matrix.json') | ConvertFrom-Json
    $contract = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'docs/v2-final/v2-three-service-acceptance-contract.json') | ConvertFrom-Json
    $claimRegister = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'docs/v2-claim-register.json') | ConvertFrom-Json
    $readme = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'README.md')
    foreach ($service in $matrix.services) {
        $expected = $contract.auditedResults.($service.service)
        if ($null -eq $expected) { throw "Missing contract result for $($service.service)." }
        $claimName = switch ($service.service) {
            'petclinic-customers' { 'Spring PetClinic customers-service' }
            'doctor' { 'Doctor-service' }
            'patient' { 'Patient-service' }
        }
        $registered = @($claimRegister.threeServiceAcceptance.services | Where-Object { $_.service -eq $claimName }) | Select-Object -First 1
        if ($null -eq $registered) { throw "Missing claim-register result for $($service.service)." }
        foreach ($field in @('runtimePolicy','validRuns','pairedWins','pairs','medianPssDeltaKb','medianPrivateDirtyDeltaKb','medianMemoryCurrentDeltaBytes','v2cVerdict')) {
            if ($service.$field -ne $expected.$field) { throw "Matrix/contract mismatch: $($service.service).$field" }
        }
        foreach ($field in @('runtimePolicy','validRuns','pairedWins','medianPssDeltaKb','medianPrivateDirtyDeltaKb','medianMemoryCurrentDeltaBytes','v2cVerdict')) {
            if ($service.$field -ne $registered.$field) { throw "Matrix/claim-register mismatch: $($service.service).$field" }
        }
    }

    $v21 = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'docs/product-evidence/petclinic-r41f-b0-t7r23-result.json') | ConvertFrom-Json
    $accepted = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'docs/product-evidence/petclinic-accepted-deployment-v1.json') | ConvertFrom-Json
    if (-not $v21.claimable -or $v21.decision -ne 'T7R23_R487_SCALE_DIRECT_RAM_WIN') { throw 'JMOA 2.1 result is not the frozen claimable terminal.' }
    if ($v21.primary.processPssKiB.median -ne -15241.5 -or $v21.primary.processPssKiB.favorableBlocks -ne 12) { throw 'JMOA 2.1 PSS headline drifted.' }
    if ($v21.primary.memoryCurrentBytes.median -ne -17033216) { throw 'JMOA 2.1 cgroup headline drifted.' }
    if ($accepted.revision -ne 3 -or $accepted.acceptedDeployment.id -ne 'R41F') { throw 'Accepted PetClinic pointer is not exact R41F revision 3.' }
    $resultHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $accepted.runtimePolicy.evidencePath) -Algorithm SHA256).Hash
    if ($resultHash -ne $accepted.runtimePolicy.evidenceSha256) { throw 'Accepted PetClinic evidence hash does not match the published result.' }
    foreach ($required in @('15,241.5 KiB','17,033,216 bytes','14.71%','packaging-inclusive')) {
        if (-not $readme.Contains($required)) { throw "README is missing the JMOA 2.1 claim boundary: $required" }
    }
}

function Assert-CurrentReleaseBoundaries {
    $patient = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'docs/product-evidence/patient-scoped-ram-benchmark.json') | ConvertFrom-Json
    if ($patient.medianSavingKiB -ne 8586 -or $patient.lowerSavingBoundKiB -ne 8384 -or $patient.pairs.Count -ne 12 -or @($patient.costs.PSObject.Properties).Count -ne 82) { throw 'Provisional Patient result drifted.' }
    if ($patient.originalNull.memoryPassed -or $patient.performanceBoundsQualified -or $patient.fullProductAcceptance -or -not $patient.calibrationException.postObservation) { throw 'Patient limitations drifted.' }
    foreach ($name in @('v2-claim-register','v2-foundation-closure','v2-phase-closure-taxonomy')) {
        $register = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot "docs/$name.json") | ConvertFrom-Json
        $claims = $register.publicationContext.currentReleaseClaims
        if ($claims.stable.version -ne '2.1.0' -or $claims.stable.processPssSavingKiB -ne 15241.5 -or $claims.provisional.medianStoppedPssSavingKiB -ne $patient.medianSavingKiB -or $claims.provisional.conditionalLowerSavingBoundKiB -ne $patient.lowerSavingBoundKiB -or $claims.provisional.originalCalibrationPassed -or $claims.provisional.performanceBoundsQualified -or $claims.provisional.newMavenBinaries) { throw "Current release registry drifted: $name" }
    }
    $readme = Get-Content -Raw -LiteralPath (Join-Path $RepoRoot 'README.md')
    $section = ($readme -split '## Patient benchmark:',2)[1] -split '\n## ',2 | Select-Object -First 1
    $links = [regex]::Matches($section, '\[[^\]]+\]\(([^)]+)\)')
    if ($links.Count -ne 1 -or $links[0].Groups[1].Value -ne 'https://github.com/AlphaSudo/jmoa/releases/tag/v2.15') { throw 'Patient README section must link only to the 2.15 release.' }
    foreach ($relative in @('README.md','docs/paper/jmoa-v2.15-patient-leyden-memory-evidence.md','docs/releases/current-state.md')) {
        $text = (Get-Content -Raw -LiteralPath (Join-Path $RepoRoot $relative)) -replace '\s+', ' '
        foreach ($required in @('8.38 MiB','8.19 MiB','calibration failed','unqualified or failed','no new Maven binaries')) {
            if ($text -notmatch [regex]::Escape($required)) { throw "Patient qualification missing in ${relative}: $required" }
        }
    }
    foreach ($relative in Get-TrackedFiles '*') {
        if ($relative -match '^(DRAFTS/|docs/services/patient-service/|docs/v2-[a-w]/)' -or $relative -eq 'docs/assets/jmoa-social-preview.png') { throw "Removed publication material returned: $relative" }
    }
}

Assert-LocalMarkdownLinks
Assert-JsonParses
Assert-ClaimConsistency
Assert-CurrentReleaseBoundaries
if (-not $SkipPublicationSafety) { & (Join-Path $PSScriptRoot 'check-publication-safety.ps1') }
& git -C $RepoRoot diff --check
if ($LASTEXITCODE -ne 0) { throw 'git diff --check failed.' }
Write-Host 'Documentation quality checks passed.'

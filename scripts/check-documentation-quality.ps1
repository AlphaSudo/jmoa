param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$SkipPublicationSafety
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

function Get-TrackedFiles([string]$Pattern) {
    $files = & git -C $RepoRoot ls-files $Pattern
    if ($LASTEXITCODE -ne 0) { throw "Could not enumerate tracked $Pattern files." }
    return @($files | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
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
        $formattedPss = '{0:N0}' -f [math]::Abs([long]$service.medianPssDeltaKb)
        if (-not $readme.Contains("-$formattedPss KB")) { throw "README is missing the matrix PSS value for $($service.service)." }
        if (-not $readme.Contains([string]$service.runtimePolicy) -and -not ($service.service -eq 'doctor' -and $readme.Contains('Application CDS'))) { throw "README is missing runtime policy $($service.runtimePolicy)." }
    }
    foreach ($required in @('JDK_BASE_CDS_LOW_DIRTY','NO_CDS_LOW_DIRTY','Dynamic Patient application CDS')) {
        if (-not $readme.Contains($required)) { throw "README is missing Patient policy taxonomy: $required" }
    }
    if ($readme -match 'Patient CDS remains blocked|Patient CDS policy remains blocked') {
        throw 'README contains stale ambiguous Patient CDS wording.'
    }
}

Assert-LocalMarkdownLinks
Assert-JsonParses
Assert-ClaimConsistency
if (-not $SkipPublicationSafety) { & (Join-Path $PSScriptRoot 'check-publication-safety.ps1') }
& git -C $RepoRoot diff --check
if ($LASTEXITCODE -ne 0) { throw 'git diff --check failed.' }
Write-Host 'Documentation quality checks passed.'

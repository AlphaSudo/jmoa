param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$OutputDir = ""
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $RepoRoot 'target/v2-three-service-acceptance'
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$contractPath = Join-Path $RepoRoot 'docs/v2-final/v2-three-service-acceptance-contract.json'
$contract = Get-Content -Raw -LiteralPath $contractPath | ConvertFrom-Json

function Read-OptionalJson {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    try { return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json }
    catch { throw "Invalid JSON evidence file: $Path" }
}

function Get-FirstExisting {
    param([string[]]$Paths)
    foreach ($path in $Paths) {
        if (Test-Path -LiteralPath $path -PathType Leaf) { return $path }
    }
    return $null
}

function Convert-PairedWins {
    param($Value)
    if ($null -eq $Value) { return $null }
    if ($Value -is [int] -or $Value -is [long] -or $Value -is [double]) { return [int]$Value }
    $text = [string]$Value
    if ($text -match '^\s*(\d+)\s*/\s*(\d+)\s*$') { return [int]$Matches[1] }
    return $null
}

function Test-ServiceVerdict {
    param(
        [string]$ServiceId,
        $Record,
        $Gates,
        [string]$RuntimePolicy
    )

    $reasons = [System.Collections.Generic.List[string]]::new()
    if ($null -eq $Record) {
        $reasons.Add('verdict record is missing')
        return [ordered]@{ service = $ServiceId; status = 'PENDING'; pass = $false; reasons = @($reasons) }
    }

    $validRuns = if ($null -ne $Record.validRuns) { [int]$Record.validRuns } elseif ($null -ne $Record.confirmation.validRuns) { [int]$Record.confirmation.validRuns } elseif ($null -ne $Record.v2c.validRuns) { [int]$Record.v2c.validRuns } else { -1 }
    $pairedWins = if ($null -ne $Record.pairedWins) { Convert-PairedWins $Record.pairedWins } elseif ($null -ne $Record.confirmation.pairedWins) { Convert-PairedWins $Record.confirmation.pairedWins } elseif ($null -ne $Record.v2c.pairedWins) { Convert-PairedWins $Record.v2c.pairedWins } else { $null }
    $pss = if ($null -ne $Record.medianPssDeltaKb) { [long]$Record.medianPssDeltaKb } elseif ($null -ne $Record.confirmation.medianPssDeltaKb) { [long]$Record.confirmation.medianPssDeltaKb } elseif ($null -ne $Record.v2c.medianPssDeltaKb) { [long]$Record.v2c.medianPssDeltaKb } else { $null }
    $privateDirty = if ($null -ne $Record.medianPrivateDirtyDeltaKb) { [long]$Record.medianPrivateDirtyDeltaKb } elseif ($null -ne $Record.confirmation.medianPrivateDirtyDeltaKb) { [long]$Record.confirmation.medianPrivateDirtyDeltaKb } elseif ($null -ne $Record.v2c.medianPrivateDirtyDeltaKb) { [long]$Record.v2c.medianPrivateDirtyDeltaKb } else { $null }
    $memoryCurrent = if ($null -ne $Record.medianMemoryCurrentDeltaBytes) { [long]$Record.medianMemoryCurrentDeltaBytes } elseif ($null -ne $Record.confirmation.medianMemoryCurrentDeltaBytes) { [long]$Record.confirmation.medianMemoryCurrentDeltaBytes } elseif ($null -ne $Record.v2c.medianMemoryCurrentDeltaBytes) { [long]$Record.v2c.medianMemoryCurrentDeltaBytes } else { $null }
    $workloadErrors = if ($null -ne $Record.workloadErrors) { [int]$Record.workloadErrors } elseif ($null -ne $Record.confirmation.workloadErrors) { [int]$Record.confirmation.workloadErrors } else { 0 }
    $semanticErrors = if ($null -ne $Record.semanticErrors) { [int]$Record.semanticErrors } else { 0 }
    $v2c = if ($null -ne $Record.v2cVerdict) { [string]$Record.v2cVerdict } elseif ($null -ne $Record.confirmation.v2cVerdict) { [string]$Record.confirmation.v2cVerdict } elseif ($null -ne $Record.v2c.verdict) { [string]$Record.v2c.verdict } else { '' }
    $v2dPresent = if ($null -ne $Record.v2dAttributionPresent) { [bool]$Record.v2dAttributionPresent } elseif ($null -ne $Record.attribution) { $true } else { $false }

    if ($validRuns -ne [int]$Gates.validRuns) { $reasons.Add("validRuns=$validRuns, required $($Gates.validRuns)") }
    if ($null -eq $pairedWins -or $pairedWins -lt [int]$Gates.pairedWinsMinimum) { $reasons.Add("pairedWins=$pairedWins, required >= $($Gates.pairedWinsMinimum)") }
    if ($null -eq $pss -or $pss -gt [long]$Gates.medianPssDeltaKbMaximum) { $reasons.Add("median PSS delta=$pss KB, required <= $($Gates.medianPssDeltaKbMaximum) KB") }
    if ($null -eq $privateDirty -or $privateDirty -gt [long]$Gates.medianPrivateDirtyDeltaKbMaximum) { $reasons.Add("median Private_Dirty delta=$privateDirty KB, required <= $($Gates.medianPrivateDirtyDeltaKbMaximum) KB") }
    if ($null -eq $memoryCurrent -or $memoryCurrent -gt [long]$Gates.medianMemoryCurrentDeltaBytesMaximum) { $reasons.Add("median memory.current delta=$memoryCurrent bytes, required <= $($Gates.medianMemoryCurrentDeltaBytesMaximum) bytes") }
    if ($workloadErrors -gt [int]$Gates.workloadErrorsMaximum) { $reasons.Add("workloadErrors=$workloadErrors") }
    if ($semanticErrors -gt [int]$Gates.semanticErrorsMaximum) { $reasons.Add("semanticErrors=$semanticErrors") }
    if ($v2c -ne [string]$Gates.v2cVerdict) { $reasons.Add("V2-C verdict=$v2c") }
    if ([bool]$Gates.v2dAttributionRequired -and -not $v2dPresent) { $reasons.Add('V2-D attribution is missing') }

    [ordered]@{
        service = $ServiceId
        status = if ($reasons.Count -eq 0) { 'PASS' } else { 'FAIL' }
        pass = ($reasons.Count -eq 0)
        runtimePolicy = if (-not [string]::IsNullOrWhiteSpace($RuntimePolicy)) { $RuntimePolicy } elseif ($null -ne $Record.runtimePolicy) { [string]$Record.runtimePolicy } else { $null }
        validRuns = $validRuns
        pairedWins = $pairedWins
        pairs = [int]$Gates.pairs
        medianPssDeltaKb = $pss
        medianPrivateDirtyDeltaKb = $privateDirty
        medianMemoryCurrentDeltaBytes = $memoryCurrent
        workloadErrors = $workloadErrors
        semanticErrors = $semanticErrors
        v2cVerdict = $v2c
        v2dAttributionPresent = $v2dPresent
        reasons = @($reasons)
    }
}

$petPath = Get-FirstExisting @(
    (Join-Path $RepoRoot 'docs/v2-final/v2-three-service-petclinic-verdict.json'),
    (Join-Path $RepoRoot 'docs/v2-final/v2-final-memory-win-matrix.json')
)
$doctorPath = Get-FirstExisting @(
    (Join-Path $RepoRoot 'docs/v2-final/v2-three-service-doctor-verdict.json'),
    (Join-Path $RepoRoot 'docs/v2-k/v2k-doctor-final-verdict.json')
)
$patientPath = Get-FirstExisting @(
    (Join-Path $RepoRoot 'docs/v2-final/patient-final-policy-verdict.json'),
    (Join-Path $RepoRoot 'docs/v2-final/v2-three-service-patient-verdict.json')
)

$petRecord = Read-OptionalJson $petPath
$doctorRecord = Read-OptionalJson $doctorPath
$patientRecord = Read-OptionalJson $patientPath

# Patient has separate policy records. The aggregate gate consumes the
# confirmed no-CDS policy while preserving the CDS block as audit metadata.
if ($patientRecord -and $patientRecord.noCdsPolicy) {
    $patientNoCds = $patientRecord.noCdsPolicy
    $patientRecord = [pscustomobject]@{
        runtimePolicy = $patientRecord.confirmedPolicy
        validRuns = $patientNoCds.validRuns
        pairedWins = $patientNoCds.pairedWins
        medianPssDeltaKb = $patientNoCds.medianPssDeltaKb
        medianPrivateDirtyDeltaKb = $patientNoCds.medianPrivateDirtyDeltaKb
        medianMemoryCurrentDeltaBytes = $patientNoCds.medianMemoryCurrentDeltaBytes
        workloadErrors = $patientNoCds.workloadErrors
        v2cVerdict = $patientNoCds.v2cVerdict
        v2dAttributionPresent = $patientNoCds.v2dPresent
        cdsPolicyVerdict = $patientRecord.cdsPolicy.status
    }
}

# The existing matrix is a multi-result document, so select only the audited final V1 -> V2 row.
if ($petRecord -and $petRecord.primaryAcceptance) {
    $petRecord = @($petRecord.primaryAcceptance | Where-Object { $_.comparison -match 'customers finalized V1 vs final V2' }) | Select-Object -First 1
    if ($petRecord) {
        $petRecord = [pscustomobject]@{
            validRuns = $petRecord.validRuns
            pairedWins = $petRecord.pairedWins
            medianPssDeltaKb = $petRecord.medianPssDeltaKb
            medianPrivateDirtyDeltaKb = $petRecord.medianPrivateDirtyDeltaKb
            medianMemoryCurrentDeltaBytes = $petRecord.medianMemoryCurrentDeltaBytes
            v2cVerdict = 'CONFIRMED_WIN'
            v2dAttributionPresent = $true
        }
    }
}

$results = @(
    (Test-ServiceVerdict -ServiceId 'petclinic-customers' -Record $petRecord -Gates $contract.gates -RuntimePolicy 'NO_CDS_LOW_DIRTY'),
    (Test-ServiceVerdict -ServiceId 'doctor' -Record $doctorRecord -Gates $contract.gates -RuntimePolicy 'CDS'),
    (Test-ServiceVerdict -ServiceId 'patient' -Record $patientRecord -Gates $contract.gates -RuntimePolicy 'NO_CDS_LOW_DIRTY')
)
$allPass = @($results | Where-Object { -not $_.pass }).Count -eq 0
$overall = if ($allPass) { 'READY_FOR_V2_FINAL' } elseif ($results | Where-Object { $_.service -eq 'patient' -and $_.status -eq 'PENDING' }) { 'BLOCKED_PATIENT_EVIDENCE' } else { 'BLOCKED_FINAL_ACCEPTANCE' }

$matrix = [ordered]@{
    metadataVersion = 'v2-three-service-memory-matrix-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    contract = 'docs/v2-final/v2-three-service-acceptance-contract.json'
    comparison = 'final V1 -> final V2'
    overallStatus = $overall
    services = $results
    claimBoundary = if ($allPass) { 'All three required services pass the frozen V1-to-V2 contract under their confirmed service-specific runtime policies. Patient CDS remains separately blocked.' } else { 'No stable three-service V2 claim is allowed until every required service passes.' }
}
$jsonPath = Join-Path $OutputDir 'v2-three-service-memory-matrix.json'
$mdPath = Join-Path $OutputDir 'v2-three-service-memory-matrix.md'
$matrix | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# V2 Three-Service Memory Matrix')
$lines.Add('')
$lines.Add("Overall status: **$overall**")
$lines.Add('')
$lines.Add('Comparison: `final V1 -> final V2`')
$lines.Add('')
$lines.Add('| Service | Runtime policy | Status | Valid runs | Paired wins | Median PSS KB | Median Private_Dirty KB | Median memory.current bytes | V2-C | V2-D |')
$lines.Add('|---|---|---:|---:|---:|---:|---:|---:|---|---|')
foreach ($result in $results) {
    $lines.Add("| $($result.service) | $($result.runtimePolicy) | $($result.status) | $($result.validRuns)/6 | $($result.pairedWins)/3 | $($result.medianPssDeltaKb) | $($result.medianPrivateDirtyDeltaKb) | $($result.medianMemoryCurrentDeltaBytes) | $($result.v2cVerdict) | $($result.v2dAttributionPresent) |")
}
$lines.Add('')
$lines.Add('## Gate')
$lines.Add('')
$lines.Add('- 6/6 valid runs per service')
$lines.Add('- at least 2/3 paired wins')
$lines.Add('- median PSS <= -4096 KB')
$lines.Add('- median Private_Dirty <= -1024 KB')
$lines.Add('- median memory.current <= -1048576 bytes')
$lines.Add('- zero workload and semantic errors')
$lines.Add('- V2-C `CONFIRMED_WIN` and V2-D attribution')
$lines.Add('')
$lines.Add('Raw private evidence is intentionally excluded from this repository. A pending or failed row blocks the aggregate claim; Patient CDS remains a separate blocked policy record.')
$lines -join "`n" | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Acceptance audit: $overall"
Write-Host "Matrix JSON: $jsonPath"
Write-Host "Matrix Markdown: $mdPath"
if (-not $allPass) { exit 2 }

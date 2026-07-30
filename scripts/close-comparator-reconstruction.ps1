param(
    [string]$ComparatorReportDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/product-evidence/comparator-reconstruction'),
    [string]$HistoricalBudgetDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/product-evidence/historical-baseline-recovery'),
    [string]$DoctorOrderClassificationPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Read-RequiredJson([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required comparator report does not exist: $Path"
    }
    Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

$doctor = Read-RequiredJson (Join-Path $ComparatorReportDirectory 'doctor-b0-v1-directional-pair.json')
$resolvedOrderClassification = if ([string]::IsNullOrWhiteSpace($DoctorOrderClassificationPath)) {
    Join-Path $ComparatorReportDirectory 'doctor-reconstructed-order-classification.json'
} else {
    $DoctorOrderClassificationPath
}
$doctorOrder = Read-RequiredJson $resolvedOrderClassification
$doctorB0 = Read-RequiredJson (Join-Path $ComparatorReportDirectory 'doctor-historical-b0-screen.json')
$petclinic = Read-RequiredJson (Join-Path $ComparatorReportDirectory 'petclinic-comparator-entry-audit.json')
$patient = Read-RequiredJson (Join-Path $ComparatorReportDirectory 'patient-historical-comparator-recovery.json')

if ($doctor.decision -ne 'DOCTOR_HISTORICAL_V1_DIRECTION_NOT_REPRODUCED_IN_SINGLE_ORDER') {
    throw "Unexpected Doctor decision: $($doctor.decision)"
}
if ($doctorOrder.classification -ne 'V1_RUNTIME_COST') {
    throw "Unexpected Doctor two-order classification: $($doctorOrder.classification)"
}
if ($petclinic.summary.comparatorDecision -ne 'HISTORICAL_BASELINE_CONTAMINATED') {
    throw "Unexpected PetClinic decision: $($petclinic.summary.comparatorDecision)"
}
if ($patient.decision -ne 'PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE') {
    throw "Unexpected Patient decision: $($patient.decision)"
}

$petclinicClosure = [ordered]@{
    schemaVersion = 'jmoa-petclinic-historical-comparator-closure-v1'
    decision = 'HISTORICAL_PETCLINIC_B0_INVALID'
    performanceRunAuthorized = $false
    historicalArtifactSha256 = $petclinic.artifactIdentity.historicalSha256
    currentCleanArtifactSha256 = $petclinic.artifactIdentity.currentSha256
    historicalJmoaEntryCount = [int]$petclinic.contamination.historicalJmoaEntryCount
    disqualifyingDifferenceCount = [int]$petclinic.summary.disqualifyingDifferenceCount
    reason = 'The historical artifact contains JMOA output and a semantically different application class. It is not a clean no-JMOA B0.'
    currentEvidencePreserved = $true
    currentEvidenceScope = 'The current six-order result remains authoritative only for its frozen current artifacts and protocol.'
}
Write-JmoaJson -Value $petclinicClosure -Path (Join-Path $ComparatorReportDirectory 'petclinic-historical-comparator-closure.json')
Write-JmoaText -Value @"
# PetClinic Historical Comparator Closure

- Decision: **HISTORICAL_PETCLINIC_B0_INVALID**
- Performance run authorized: **False**
- Historical JMOA entries: **$($petclinicClosure.historicalJmoaEntryCount)**
- Disqualifying artifact differences: **$($petclinicClosure.disqualifyingDifferenceCount)**

The historical artifact is not a clean no-JMOA B0. It contains JMOA output and
a semantically different application class. Running it against a newly built
baseline would compare different source/output universes and would not recover
the historical B0-to-V1 product effect.

The current six-order result remains authoritative for its own frozen current
artifacts and protocol. No historical-comparator performance run is authorized.
"@ -Path (Join-Path $ComparatorReportDirectory 'petclinic-historical-comparator-closure.md')

$services = @(
    [ordered]@{
        service = 'doctor-service'
        decision = [string]$doctorOrder.classification
        exactHistoricalB0 = $true
        exactHistoricalV1 = $true
        runtimeEquivalenceComplete = $false
        diagnosticPssDeltaKb = [double]$doctor.deltaCandidateMinusBaseline.pssKb
        reversedDiagnosticPssDeltaKb = [double]$doctorOrder.pss.d2V1FirstMinusB0SecondKb
        orderBalancedArtifactEstimatePssKb = [double]$doctorOrder.pss.orderBalancedArtifactEstimateKb
        currentDirectProductEffectPssKb = -4715.5
        performanceCampaignAuthorized = $false
        reason = 'V1 was more expensive in both reconstructed orders. The order-balanced estimate is a reconstructed-runtime diagnostic, not an exact historical replay; both observations remain timing-confounded.'
    }
    [ordered]@{
        service = 'patient-service'
        decision = [string]$patient.decision
        exactHistoricalB0 = $false
        exactHistoricalV1 = $false
        runtimeEquivalenceComplete = $false
        diagnosticPssDeltaKb = $null
        currentDirectProductEffectPssKb = [double]$patient.currentAuthoritativeResult.b0ToV2MedianPssDeltaKb
        performanceCampaignAuthorized = $false
        reason = 'The historical B0 artifact, source revision, and support/config identity were not recovered. Later artifacts cannot substitute for that tuple.'
    }
    [ordered]@{
        service = 'spring-petclinic-customers-service'
        decision = 'HISTORICAL_PETCLINIC_B0_INVALID'
        exactHistoricalB0 = $false
        exactHistoricalV1 = $true
        runtimeEquivalenceComplete = $false
        diagnosticPssDeltaKb = $null
        currentDirectProductEffectPssKb = -2947.0
        performanceCampaignAuthorized = $false
        reason = 'The historical B0 contains JMOA output and semantic application drift, so it cannot serve as a clean baseline.'
    }
)

$closure = [ordered]@{
    schemaVersion = 'jmoa-historical-comparator-reconstruction-closure-v1'
    status = 'CLOSED_WITHOUT_NEW_PRODUCT_CLAIM'
    currentMatrixPreserved = $true
    broadCampaignAuthorized = $false
    services = $services
    rules = @(
        'Historical directional budgets are not current product effects.',
        'A historical comparator requires exact source, dependency, generated output, runtime, workload, capture, and deployment identity.',
        'A failed or contaminated comparator is closed explicitly; it is not replaced with a convenient later artifact.'
    )
}
Write-JmoaJson -Value $closure -Path (Join-Path $ComparatorReportDirectory 'comparator-reconstruction-closure.json')
$closureLines = @(
    '# Historical Comparator Reconstruction Closure', '',
    'Status: **CLOSED_WITHOUT_NEW_PRODUCT_CLAIM**', '',
    '| Service | Decision | Performance campaign | Current direct B0->V2 PSS |',
    '|---|---|---|---:|'
)
foreach ($service in $services) {
    $closureLines += "| $($service.service) | ``$($service.decision)`` | $($service.performanceCampaignAuthorized) | $($service.currentDirectProductEffectPssKb) KB |"
}
$closureLines += @(
    '',
    'Doctor V1 was more expensive in both reconstructed orders. This supports a reconstructed-runtime V1 cost, not an exact historical replay.',
    'PetClinic historical B0 is invalid because it contains JMOA output and semantic application drift.',
    'Patient lacks the historical B0/source/support tuple required for a valid reconstruction.',
    '',
    'No six-order historical reconstruction campaign is authorized. The current sealed matrix remains authoritative for its own artifacts and protocol.',
    '',
    'The [command ledger index](doctor-command-ledger-index.md) records complete and failed scenario attempts. Raw commands and responses remain private; sanitized summaries and hashes are published here.'
)
Write-JmoaText -Value ($closureLines -join [Environment]::NewLine) -Path (Join-Path $ComparatorReportDirectory 'comparator-reconstruction-closure.md')

$budgetPath = Join-Path $HistoricalBudgetDirectory 'historical-expected-engineering-budget.json'
$budget = Read-RequiredJson $budgetPath
$statusByService = @{
    'doctor-service' = [ordered]@{
        reconstructionDecision = [string]$doctorOrder.classification
        reconstructedB0ToV1DiagnosticPssKb = [double]$doctorOrder.pss.orderBalancedArtifactEstimateKb
        historicalDirectionReproduced = $false
        qualification = 'Engineering-only historical budget. V1 was more expensive in both reconstructed orders; the +5,401 KB order-balanced estimate is not an exact historical replay.'
    }
    'patient-service' = [ordered]@{
        reconstructionDecision = [string]$patient.decision
        reconstructedB0ToV1DiagnosticPssKb = $null
        historicalDirectionReproduced = $null
        qualification = 'Engineering-only historical budget. The historical comparator tuple is not recoverable.'
    }
    'spring-petclinic-customers-service' = [ordered]@{
        reconstructionDecision = 'HISTORICAL_PETCLINIC_B0_INVALID'
        reconstructedB0ToV1DiagnosticPssKb = $null
        historicalDirectionReproduced = $null
        qualification = 'Engineering-only historical budget. The historical baseline is not a clean no-JMOA B0.'
    }
}
foreach ($row in $budget.services) {
    $status = $statusByService[[string]$row.service]
    $row | Add-Member -NotePropertyName evidenceLabel -NotePropertyValue 'HISTORICAL_EXPECTED_ENGINEERING_BUDGET' -Force
    $row | Add-Member -NotePropertyName currentEffectLabel -NotePropertyValue 'CURRENT_DIRECT_PRODUCT_EFFECT' -Force
    foreach ($property in $status.GetEnumerator()) {
        $row | Add-Member -NotePropertyName $property.Key -NotePropertyValue $property.Value -Force
    }
}
$budget.schemaVersion = 'jmoa-historical-expected-engineering-budget-v2'
$budget | Add-Member -NotePropertyName reconstructionClosure -NotePropertyValue 'docs/product-evidence/comparator-reconstruction/comparator-reconstruction-closure.json' -Force
$budget.warning = 'Medians from separate campaigns are not additive. Historical budgets are engineering diagnostics only; current direct B0-to-V2 results are the product evidence.'
Write-JmoaJson -Value $budget -Path $budgetPath

$budgetLines = @(
    '# Historical Expected Engineering Budget', '',
    'These numbers are labeled `HISTORICAL_EXPECTED_ENGINEERING_BUDGET`. They are not `CURRENT_DIRECT_PRODUCT_EFFECT` measurements and must not be added across campaigns.', '',
    '| Service | Historical directional sum | Current direct B0->V2 | Reconstruction decision |',
    '|---|---:|---:|---|'
)
foreach ($row in $budget.services) {
    $budgetLines += "| $($row.service) | $($row.historicalExpectedEngineeringBudgetPssKb) KB | $($row.currentDirectB0ToV2MedianPssKb) KB | ``$($row.reconstructionDecision)`` |"
}
$budgetLines += @(
    '',
    'Doctor''s old published `-6,048 KB` figure was a median-calculation error. The corrected historical independent-median delta is `-2,728 KB`; the reconstructed two-order artifact estimate is `+5,401 KB` and remains timing/provenance scoped.',
    '',
    'PetClinic''s historical baseline is contaminated. Patient''s historical comparator tuple is not recoverable. No row authorizes a new performance campaign.',
    '',
    $budget.warning
)
Write-JmoaText -Value ($budgetLines -join [Environment]::NewLine) -Path (Join-Path $HistoricalBudgetDirectory 'historical-expected-engineering-budget.md')

Write-Host 'Comparator reconstruction closed without a new product claim.'

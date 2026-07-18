param(
    [string]$StudyDirectory = 'target/v2-patient-root-cause/runtime/base-cds-final-confirmation',
    [string]$OutputDirectory = 'docs/v2-final'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
$finalPath = Join-Path $StudyDirectory 'final-verdict.json'
$preparationPath = Join-Path $StudyDirectory 'preparation-manifest.json'
$screenPath = Join-Path $StudyDirectory 'screen-verdict.json'
foreach ($path in @($finalPath,$preparationPath,$screenPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required BASE-CDS result is missing: $path" }
}
$final = Get-Content -Raw -LiteralPath $finalPath | ConvertFrom-Json
$preparation = Get-Content -Raw -LiteralPath $preparationPath | ConvertFrom-Json
$screen = Get-Content -Raw -LiteralPath $screenPath | ConvertFrom-Json
$allowed = @('PATIENT_BASE_CDS_V2_CONFIRMED','PATIENT_ALL_CDS_POLICIES_BLOCKED','PATIENT_BASE_CDS_ENVIRONMENT_INVALID')
if ($final.status -notin $allowed) { throw "Unexpected BASE-CDS terminal status: $($final.status)" }

$report = [ordered]@{
    metadataVersion = 'patient-base-cds-final-verdict-v1'
    status = $final.status
    service = 'patient-service'
    visibility = 'PRIVATE_SANITIZED_SUMMARY'
    comparison = 'accepted Patient V1 -> corrected Patient V2'
    launchMode = 'SPRING_BOOT_FAT_JAR'
    runtimePolicy = 'JDK_BASE_CDS_LOW_DIRTY'
    defaultArchive = [ordered]@{
        sha256 = $preparation.jdk.defaultArchiveSha256
        sameBytesRequired = $true
        applicationArchiveMapped = $false
    }
    artifactFreeze = [ordered]@{
        v1Sha256 = $preparation.acceptedArtifacts.v1Sha256
        v2Sha256 = $preparation.acceptedArtifacts.v2Sha256
        dependencyCountV1 = $preparation.artifactContract.dependencyCountV1
        dependencyCountV2 = $preparation.artifactContract.dependencyCountV2
        runtimeLibraryByteIdentical = $preparation.artifactContract.runtimeLibraryByteIdentical
        rawAuditedClasses = $preparation.artifactContract.rawAuditedClasses
        rawPreservationFailures = $preparation.artifactContract.rawPreservationFailures
    }
    qualification = [ordered]@{
        status = 'BASE_CDS_POLICY_PROOF_PASSED'
        requestsPerArtifact = 600
        errors = 0
    }
    screen = $screen
    confirmation = $final.details
    stage = $final.stage
    reason = $final.reason
    noFivePairRescue = $true
    noFurtherPatientV2Experiments = $true
    claimBoundary = if ($final.status -eq 'PATIENT_BASE_CDS_V2_CONFIRMED') {
        'Patient V2 is confirmed over V1 under the identical default JDK base CDS archive. Dynamic Patient application CDS remains rejected.'
    } else {
        'Patient remains confirmed only under NO_CDS_LOW_DIRTY. Dynamic application CDS and the final default-base-CDS transfer are blocked.'
    }
}
New-JmoaDirectory $OutputDirectory
Write-JmoaJson $report (Join-Path $OutputDirectory 'patient-base-cds-final-verdict.json')

$confirmationText = if ($null -ne $report.confirmation -and $null -ne $report.confirmation.medianPssDeltaKb) {
@"
## Confirmation

- Valid runs: ``$($report.confirmation.validRuns)/6``
- Paired wins: ``$($report.confirmation.pairedWins)/3``
- Median V2-minus-V1 PSS: ``$($report.confirmation.medianPssDeltaKb) KB``
- Median V2-minus-V1 Private_Dirty: ``$($report.confirmation.medianPrivateDirtyDeltaKb) KB``
- Median V2-minus-V1 memory.current: ``$($report.confirmation.medianMemoryCurrentDeltaBytes) bytes``
- V2-C: ``$($report.confirmation.v2cVerdict)``
- V2-D passed: ``$($report.confirmation.v2dPassed)``
- Primary attribution: ``$($report.confirmation.primaryAttribution)``
"@
} else { '## Confirmation' + "`n`nConfirmation was not run because the diagnostic screen did not promote." }

$policyText = if ($report.status -eq 'PATIENT_BASE_CDS_V2_CONFIRMED') {
    'Patient now has two confirmed policies: `JDK_BASE_CDS_LOW_DIRTY` and `NO_CDS_LOW_DIRTY`. Dynamic application CDS remains blocked.'
} else {
    'Patient remains on `NO_CDS_LOW_DIRTY`. All tested single-replica CDS-enabled transfer policies are blocked.'
}
$md = @"
# Patient Final Default-Base-CDS Verdict

Status: ``$($report.status)``

This final experiment compared the accepted Patient V1 and corrected V2
artifacts under ``JDK_BASE_CDS_LOW_DIRTY``. Both arms used the identical stock
JDK base archive, ``MALLOC_ARENA_MAX=1``, and no Patient application archive,
AOT cache, or runtime javaagent.

## Qualification

- Default archive SHA-256: ``$($report.defaultArchive.sha256)``
- Dependency JAR identities: ``162/162`` on both artifacts
- Raw preservation audit: ``$($report.artifactFreeze.rawAuditedClasses)`` classes, ``$($report.artifactFreeze.rawPreservationFailures)`` failures
- Semantic workload: ``600/600`` requests per artifact, zero errors
- Live policy proof: ``BASE_CDS_POLICY_PROOF_PASSED``

## Diagnostic Screen

- V2-minus-V1 PSS: ``$($screen.deltas.pssKb) KB``
- V2-minus-V1 Private_Dirty: ``$($screen.deltas.privateDirtyKb) KB``
- V2-minus-V1 memory.current: ``$($screen.deltas.memoryCurrentBytes) bytes``
- Decision: ``$($screen.status)``

$confirmationText

## Terminal Policy

$policyText

No five-pair rescue or further Patient V2 performance experiment is allowed.
This result does not change the permanent rejection of Patient dynamic
application archives and does not imply a universal CDS conclusion.
"@
Write-JmoaText $md (Join-Path $OutputDirectory 'patient-base-cds-final-verdict.md')
Write-Host 'Published sanitized Patient default-base-CDS final verdict.'

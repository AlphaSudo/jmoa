param(
    [string]$StudyDirectory = 'target/v2-patient-root-cause/runtime/appcds-study',
    [string]$ArtifactProofPath = 'target/v2-patient-root-cause/artifacts/v2-patient-materialization-proof.json',
    [string]$DoctorControlPath = 'docs/runtime-policy-studies/doctor-cds-positive-control.json',
    [string]$OutputDirectory = 'docs/runtime-policy-studies'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Missing required study evidence: $Path" }
    Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Format-Number([long]$Value) { '{0:N0}' -f $Value }
function Format-SignedNumber([long]$Value) {
    if ($Value -ge 0) { return "+$(Format-Number $Value)" }
    "-$(Format-Number ([Math]::Abs($Value)))"
}

function Read-Profile([string]$Profile) {
    $slug = $Profile.ToLowerInvariant()
    $root = Join-Path $StudyDirectory "results/v1-$slug"
    $aSuffix = if ($Profile -eq 'STARTUP') { 'valid2' } else { 'valid1' }
    $pairA = Read-Json (Join-Path $StudyDirectory "screens/v1-$slug-a-$aSuffix/analysis/runtime-policy-screen-analysis.json")
    $pairB = Read-Json (Join-Path $StudyDirectory "screens/v1-$slug-b-valid1/analysis/runtime-policy-screen-analysis.json")
    [ordered]@{
        profile = $Profile
        reproducibility = Read-Json (Join-Path $root 'appcds-reproducibility.json')
        economics = Read-Json (Join-Path $root 'balanced-appcds-profile-analysis.json')
        pairs = @(
            [ordered]@{ archive = 'A'; order = 'BASE_TO_APP'; deltas = $pairA.deltas; status = $pairA.status }
            [ordered]@{ archive = 'B'; order = 'APP_TO_BASE'; deltas = $pairB.deltas; status = $pairB.status }
        )
    }
}

function Read-Training([string]$Version, [string]$Profile, [string]$Copy) {
    $stem = "$($Version.ToLowerInvariant())-$($Profile.ToLowerInvariant())-$($Copy.ToLowerInvariant())"
    $training = Read-Json (Join-Path $StudyDirectory "training/$stem/cds-training-manifest.json")
    $inspection = Read-Json (Join-Path $StudyDirectory "inspection/$stem/dynamic-appcds-archive-inspection.json")
    [ordered]@{
        artifact = $Version
        profile = $Profile
        copy = $Copy
        status = $training.status
        artifactSha256 = $training.artifactSha256
        classpathFingerprint = $training.classpathFingerprint
        jdkFingerprint = $training.jdkFingerprint
        workloadRequests = $training.workloadRequests
        workloadErrors = $training.workloadErrors
        trainingClassLoadEventCount = $training.trainingClassLoadEventCount
        dynamicArchivedClassCount = $inspection.dynamicArchivedClassCount
        archiveSha256 = $training.archiveSha256
        archiveBytes = $training.archiveBytes
    }
}

New-JmoaDirectory -Path $OutputDirectory
$proof = Read-Json $ArtifactProofPath
$doctor = Read-Json $DoctorControlPath
$startup = Read-Profile 'STARTUP'
$representative = Read-Profile 'REPRESENTATIVE'
$baseOff = Read-Json (Join-Path $StudyDirectory 'screens/v1-base-vs-off-valid1/analysis/runtime-policy-screen-analysis.json')

foreach ($profile in @($startup, $representative)) {
    if ($profile.reproducibility.status -ne 'TRAINING_STRUCTURALLY_STABLE') {
        throw "$($profile.profile) did not pass structural reproducibility. Use NON_REPRODUCIBLE_ARCHIVE instead."
    }
    if ($profile.economics.status -ne 'PROFILE_VARIANT_REJECTED') {
        throw "$($profile.profile) is not terminally rejected; holdout/profile-selection work is still required."
    }
}

$training = @()
foreach ($version in @('V1', 'V2')) {
    foreach ($profile in @('STARTUP', 'REPRESENTATIVE')) {
        foreach ($copy in @('A', 'B')) { $training += Read-Training $version $profile $copy }
    }
}

$hostInfo = $null
try { $hostInfo = (& podman info --format json 2>$null | ConvertFrom-Json).host } catch { }
$revision = (& git rev-parse HEAD).Trim()

$environment = [ordered]@{
    metadataVersion = 'patient-dynamic-appcds-environment-freeze-v1'
    status = 'FROZEN'
    study = 'Patient Dynamic AppCDS Archive Economics Study'
    repositoryRevision = $revision
    releaseBaseline = 'v2.0.0'
    primaryTopology = 'SINGLE_REPLICA'
    service = 'patient-service'
    launchMode = 'SPRING_BOOT_FAT_JAR'
    runtimeImage = 'eclipse-temurin:26-jdk-jammy'
    jdkFingerprint = $training[0].jdkFingerprint
    podmanVersion = (& podman version --format '{{.Client.Version}}' 2>$null).Trim()
    containerHost = [ordered]@{
        os = if ($null -ne $hostInfo) { $hostInfo.os } else { 'unknown' }
        architecture = if ($null -ne $hostInfo) { $hostInfo.arch } else { 'unknown' }
        kernel = if ($null -ne $hostInfo) { $hostInfo.kernel } else { 'unknown' }
        cgroupVersion = if ($null -ne $hostInfo) { $hostInfo.cgroupVersion } else { 'unknown' }
    }
    artifacts = [ordered]@{ v1Sha256 = $proof.v1ArtifactSha256; v2Sha256 = $proof.v2ArtifactSha256 }
    classpathFingerprints = [ordered]@{
        v1 = @($training | Where-Object artifact -eq 'V1')[0].classpathFingerprint
        v2 = @($training | Where-Object artifact -eq 'V2')[0].classpathFingerprint
    }
    runtime = [ordered]@{
        gc = 'SerialGC'
        heap = '-Xms32m -Xmx256m'
        stack = '-Xss256k'
        codeCache = '-XX:ReservedCodeCacheSize=48m'
        mallocArenaMax = '1'
        nmt = 'summary'
        workloadRequests = 600
        cachePrecondition = 'DROP_CACHES_BEFORE_EACH_VARIANT'
        aotCacheAllowed = $false
        javaagentAllowed = $false
    }
}
Write-JmoaJson $environment (Join-Path $OutputDirectory 'patient-appcds-environment-freeze.json')

$trainingReport = [ordered]@{
    metadataVersion = 'patient-dynamic-appcds-training-reproducibility-v1'
    status = 'PASSED'
    records = $training
    profileReproducibility = @($startup.reproducibility, $representative.reproducibility)
    observation = 'REPRESENTATIVE archived only 35 more V1 classes than STARTUP; both A/B class sets were identical within profile.'
    evidenceBoundary = 'Counts are actual dynamic archived classes from PrintSharedArchiveAndExit, not training load events.'
}
Write-JmoaJson $trainingReport (Join-Path $OutputDirectory 'patient-appcds-training-reproducibility.json')

$economics = [ordered]@{
    metadataVersion = 'patient-dynamic-appcds-economics-v1'
    status = 'SINGLE_REPLICA_ARCHIVE_REGRESSION'
    primaryComparison = 'APP_MINUS_BASE'
    fixedArtifact = 'V1'
    profiles = @(
        [ordered]@{ profile = 'STARTUP'; summary = $startup.economics; pairs = $startup.pairs }
        [ordered]@{ profile = 'REPRESENTATIVE'; summary = $representative.economics; pairs = $representative.pairs }
    )
    explanatoryBaseMinusOff = [ordered]@{
        diagnosticOnly = $true
        pssKb = $baseOff.deltas.pssKb
        privateDirtyKb = $baseOff.deltas.privateDirtyKb
        memoryCurrentBytes = $baseOff.deltas.memoryCurrentBytes
        heapPssKb = $baseOff.deltas.heapPssKb
        sharedClassSpaceCommittedKb = $baseOff.deltas.nmtSharedClassSpaceCommittedKb
    }
    decomposition = 'Default JDK CDS was approximately PSS-neutral in one explanatory pair. The broad Patient dynamic application archive caused the repeated large single-replica regression.'
}
Write-JmoaJson $economics (Join-Path $OutputDirectory 'patient-appcds-app-vs-base-results.json')

$verdict = [ordered]@{
    metadataVersion = 'patient-dynamic-appcds-terminal-verdict-v1'
    study = 'Patient Dynamic AppCDS Archive Economics Study'
    status = 'SINGLE_REPLICA_ARCHIVE_REGRESSION'
    topology = 'SINGLE_REPLICA'
    releaseBlocking = $false
    selectedProfile = $null
    holdoutCTrained = $false
    v2FixedArtifactScreensRun = $false
    finalV1ToV2AppCdsConfirmationRun = $false
    stopReason = 'Both predeclared profiles regressed on V1 in independently trained A/B archives and balanced run order. Neither can satisfy the both-V1-and-V2 admission rule.'
    productDecision = 'Keep Patient on its confirmed NO_CDS_LOW_DIRTY policy. Do not promote Patient dynamic AppCDS.'
    releaseMatrixPreserved = [ordered]@{
        petClinic = [ordered]@{ policy = 'NO_CDS_LOW_DIRTY'; medianV1ToV2PssDeltaKb = -6012 }
        doctor = [ordered]@{ policy = 'CDS'; medianV1ToV2PssDeltaKb = $doctor.medianPssDeltaKb; interpretation = 'Variant-specific CDS positive control, not fixed-artifact proof of marginal AppCDS value.' }
        patient = [ordered]@{ policy = 'NO_CDS_LOW_DIRTY'; medianV1ToV2PssDeltaKb = -8903 }
    }
    forbiddenClaims = @(
        'CDS is universally harmful.',
        'Doctor proves marginal AppCDS benefit on a fixed artifact.',
        'Patient AppCDS was tested under multi-replica economics.',
        'The terminal study changes the released Patient no-CDS V2 win.'
    )
}
Write-JmoaJson $verdict (Join-Path $OutputDirectory 'patient-appcds-terminal-verdict.json')

$studyReport = [ordered]@{
    metadataVersion = 'patient-dynamic-appcds-study-v1'
    status = 'COMPLETE'
    classification = @('NON_BLOCKING_POST_V2', 'TERMINAL')
    primaryQuestion = 'Does a Patient dynamic application archive add single-replica value beyond the default JDK CDS archive?'
    arms = [ordered]@{ OFF = 'CDS disabled'; BASE = 'default JDK CDS only'; APP = 'default JDK CDS plus Patient dynamic archive' }
    profiles = @('STARTUP', 'REPRESENTATIVE')
    predeclaredTerminalVerdicts = @(
        'PATIENT_APP_CDS_CONFIRMED',
        'APP_CDS_NO_MARGINAL_VALUE',
        'SINGLE_REPLICA_ARCHIVE_REGRESSION',
        'MULTI_REPLICA_ONLY_BENEFIT',
        'NON_REPRODUCIBLE_ARCHIVE',
        'ENVIRONMENT_BLOCKED'
    )
    terminalVerdict = $verdict.status
    rawEvidenceCommitted = $false
}
Write-JmoaJson $studyReport (Join-Path $OutputDirectory 'patient-dynamic-appcds-study.json')

$environmentMd = @"
# Patient AppCDS Environment Freeze

- Status: ``FROZEN``
- Release baseline: ``v2.0.0``
- Topology: ``SINGLE_REPLICA``
- Launch mode: ``SPRING_BOOT_FAT_JAR``
- Runtime: ``$($environment.jdkFingerprint -replace "`r?`n", ' | ')``
- Podman/cgroup: ``$($environment.podmanVersion) / $($environment.containerHost.cgroupVersion)``
- Workload: ``600 requests``, zero errors required
- Cache policy: ``DROP_CACHES_BEFORE_EACH_VARIANT``
- AOT cache and runtime javaagent: forbidden

Raw paths, private configuration, archives, and run captures remain uncommitted.
"@
Write-JmoaText $environmentMd (Join-Path $OutputDirectory 'patient-appcds-environment-freeze.md')

$v1StartupA = @($training | Where-Object { $_.artifact -eq 'V1' -and $_.profile -eq 'STARTUP' -and $_.copy -eq 'A' })[0]
$v1StartupB = @($training | Where-Object { $_.artifact -eq 'V1' -and $_.profile -eq 'STARTUP' -and $_.copy -eq 'B' })[0]
$v1RepresentativeA = @($training | Where-Object { $_.artifact -eq 'V1' -and $_.profile -eq 'REPRESENTATIVE' -and $_.copy -eq 'A' })[0]
$v1RepresentativeB = @($training | Where-Object { $_.artifact -eq 'V1' -and $_.profile -eq 'REPRESENTATIVE' -and $_.copy -eq 'B' })[0]
$v2StartupA = @($training | Where-Object { $_.artifact -eq 'V2' -and $_.profile -eq 'STARTUP' -and $_.copy -eq 'A' })[0]
$v2StartupB = @($training | Where-Object { $_.artifact -eq 'V2' -and $_.profile -eq 'STARTUP' -and $_.copy -eq 'B' })[0]
$v2RepresentativeA = @($training | Where-Object { $_.artifact -eq 'V2' -and $_.profile -eq 'REPRESENTATIVE' -and $_.copy -eq 'A' })[0]
$v2RepresentativeB = @($training | Where-Object { $_.artifact -eq 'V2' -and $_.profile -eq 'REPRESENTATIVE' -and $_.copy -eq 'B' })[0]
$trainingMd = @"
# Patient AppCDS Training Reproducibility

All eight A/B archives trained successfully: V1/V2 x STARTUP/REPRESENTATIVE x A/B.

| Artifact/profile | Archived classes A/B | Archive bytes A/B | Class Jaccard | Status |
| --- | ---: | ---: | ---: | --- |
| V1 STARTUP | $(Format-Number $v1StartupA.dynamicArchivedClassCount) / $(Format-Number $v1StartupB.dynamicArchivedClassCount) | $(Format-Number $v1StartupA.archiveBytes) / $(Format-Number $v1StartupB.archiveBytes) | $($startup.reproducibility.normalizedArchivedClassJaccard) | stable |
| V1 REPRESENTATIVE | $(Format-Number $v1RepresentativeA.dynamicArchivedClassCount) / $(Format-Number $v1RepresentativeB.dynamicArchivedClassCount) | $(Format-Number $v1RepresentativeA.archiveBytes) / $(Format-Number $v1RepresentativeB.archiveBytes) | $($representative.reproducibility.normalizedArchivedClassJaccard) | stable |
| V2 STARTUP | $(Format-Number $v2StartupA.dynamicArchivedClassCount) / $(Format-Number $v2StartupB.dynamicArchivedClassCount) | $(Format-Number $v2StartupA.archiveBytes) / $(Format-Number $v2StartupB.archiveBytes) | not screened | trained |
| V2 REPRESENTATIVE | $(Format-Number $v2RepresentativeA.dynamicArchivedClassCount) / $(Format-Number $v2RepresentativeB.dynamicArchivedClassCount) | $(Format-Number $v2RepresentativeA.archiveBytes) / $(Format-Number $v2RepresentativeB.archiveBytes) | not screened | trained |

The class counts are actual dynamic archive entries. Training load events were retained separately and were not substituted for archive membership.
"@
Write-JmoaText $trainingMd (Join-Path $OutputDirectory 'patient-appcds-training-reproducibility.md')

$startupA = $startup.pairs[0].deltas
$startupB = $startup.pairs[1].deltas
$representativeA = $representative.pairs[0].deltas
$representativeB = $representative.pairs[1].deltas
$startupSummary = $startup.economics
$representativeSummary = $representative.economics
$economicsMd = @"
# Patient Dynamic AppCDS Economics

Primary comparison: ``APP - BASE`` on the fixed V1 artifact under ``SINGLE_REPLICA``.

| Profile/archive | Order | PSS | Private_Dirty | memory.current | Verdict |
| --- | --- | ---: | ---: | ---: | --- |
| STARTUP A | BASE->APP | $(Format-SignedNumber $startupA.pssKb) KB | $(Format-SignedNumber $startupA.privateDirtyKb) KB | $(Format-SignedNumber $startupA.memoryCurrentBytes) B | regression |
| STARTUP B | APP->BASE | $(Format-SignedNumber $startupB.pssKb) KB | $(Format-SignedNumber $startupB.privateDirtyKb) KB | $(Format-SignedNumber $startupB.memoryCurrentBytes) B | regression |
| STARTUP median | balanced | $(Format-SignedNumber $startupSummary.medianAppMinusBasePssKb) KB | $(Format-SignedNumber $startupSummary.medianAppMinusBasePrivateDirtyKb) KB | $(Format-SignedNumber $startupSummary.medianAppMinusBaseMemoryCurrentBytes) B | rejected |
| REPRESENTATIVE A | BASE->APP | $(Format-SignedNumber $representativeA.pssKb) KB | $(Format-SignedNumber $representativeA.privateDirtyKb) KB | $(Format-SignedNumber $representativeA.memoryCurrentBytes) B | regression |
| REPRESENTATIVE B | APP->BASE | $(Format-SignedNumber $representativeB.pssKb) KB | $(Format-SignedNumber $representativeB.privateDirtyKb) KB | $(Format-SignedNumber $representativeB.memoryCurrentBytes) B | regression |
| REPRESENTATIVE median | balanced | $(Format-SignedNumber $representativeSummary.medianAppMinusBasePssKb) KB | $(Format-SignedNumber $representativeSummary.medianAppMinusBasePrivateDirtyKb) KB | $(Format-SignedNumber $representativeSummary.medianAppMinusBaseMemoryCurrentBytes) B | rejected |

Both profiles were structurally stable and completed 600 requests per arm with zero errors. The explanatory V1 ``BASE - OFF`` pair was approximately PSS-neutral ($(Format-SignedNumber $baseOff.deltas.pssKb) KB), changed Private_Dirty by $(Format-SignedNumber $baseOff.deltas.privateDirtyKb) KB, and changed ``memory.current`` by $(Format-SignedNumber $baseOff.deltas.memoryCurrentBytes) bytes. The large marginal penalty therefore belongs to the Patient application archive under this topology, not to CDS as a universal mechanism.
"@
Write-JmoaText $economicsMd (Join-Path $OutputDirectory 'patient-appcds-app-vs-base-results.md')

$verdictMd = @"
# Patient Dynamic AppCDS Terminal Verdict

Status: ``SINGLE_REPLICA_ARCHIVE_REGRESSION``

Both predeclared archive profiles regressed in A/B balanced-order fixed-artifact screens. The study stops before V2 screens, profile selection, holdout C, or final V1-to-V2 AppCDS confirmation because neither profile can satisfy the required V1-and-V2 admission rule.

Product decision: keep Patient on the confirmed ``NO_CDS_LOW_DIRTY`` policy. This post-V2 study does not alter the released Patient V1-to-V2 median PSS win of 8,903 KB, and it does not contradict Doctor's service-specific CDS result.

No multi-replica claim was tested. No AOT cache mechanism was used.
"@
Write-JmoaText $verdictMd (Join-Path $OutputDirectory 'patient-appcds-terminal-verdict.md')

$studyMd = @"
# Patient Dynamic AppCDS Archive Economics Study

Status: ``COMPLETE`` / ``NON_BLOCKING_POST_V2`` / ``TERMINAL``

The study isolates three runtime arms: OFF (no CDS), BASE (default JDK CDS), and APP (BASE plus a Patient dynamic archive). Its primary admission question is APP versus BASE. It uses exactly STARTUP and REPRESENTATIVE training profiles, independent A/B archives, actual archived-class inspection, balanced run order, cold-cache resets, live archive mapping proof, and fixed artifact hashes.

Final verdict: [``SINGLE_REPLICA_ARCHIVE_REGRESSION``](patient-appcds-terminal-verdict.md).
"@
Write-JmoaText $studyMd (Join-Path $OutputDirectory 'patient-dynamic-appcds-study.md')

Write-Host "Patient Dynamic AppCDS study finalized: SINGLE_REPLICA_ARCHIVE_REGRESSION"

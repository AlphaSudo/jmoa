param(
    [Parameter(Mandatory)][string]$EvidenceRoot,
    [Parameter(Mandatory)][string]$OutputRoot,
    [int[]]$PairIndexes = @(1, 2, 3),
    [string]$ContainerCli = 'podman',
    [string]$HealthUrl = 'http://localhost:18084/actuator/health',
    [int]$WarmupSeconds = 20,
    [int]$PostWorkloadSnapshotSeconds = 20,
    [int]$HealthTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

foreach ($relative in @(
    'runtime/patient-v1-nocds-launch.ps1',
    'runtime/patient-v2-nocds-launch.ps1',
    'runtime/patient-stop.ps1',
    'runtime/patient-workload.ps1',
    'artifacts/v1-final.jar',
    'artifacts/v2-final.jar',
    'artifacts/v2-patient-materialization-proof.json'
)) {
    $path = Join-Path $EvidenceRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required Patient evidence input is missing: $path"
    }
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$screen = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
$launchV1 = Join-Path $EvidenceRoot 'runtime/patient-v1-nocds-launch.ps1'
$launchV2 = Join-Path $EvidenceRoot 'runtime/patient-v2-nocds-launch.ps1'
$stop = Join-Path $EvidenceRoot 'runtime/patient-stop.ps1'
$workload = Join-Path $EvidenceRoot 'runtime/patient-workload.ps1'
$v1Artifact = Join-Path $EvidenceRoot 'artifacts/v1-final.jar'
$v2Artifact = Join-Path $EvidenceRoot 'artifacts/v2-final.jar'
$materializationProof = Join-Path $EvidenceRoot 'artifacts/v2-patient-materialization-proof.json'

$orders = @{
    1 = 'BASELINE_FIRST'
    2 = 'CANDIDATE_FIRST'
    3 = 'BASELINE_FIRST'
}

foreach ($pairIndex in $PairIndexes) {
    if (-not $orders.ContainsKey($pairIndex)) {
        throw "Only the frozen balanced pair indexes 1, 2, and 3 are supported; received $pairIndex."
    }
    $pairOutput = Join-Path $OutputRoot ("pair-{0}" -f $pairIndex)
    New-Item -ItemType Directory -Force -Path $pairOutput | Out-Null
    $parameters = @{
        BaselineLaunchScript = $launchV1
        CandidateLaunchScript = $launchV2
        BaselineContainerName = 'jmoa-v2-patient-app'
        CandidateContainerName = 'jmoa-v2-patient-app'
        WorkloadScript = $workload
        HealthUrl = $HealthUrl
        Service = 'patient-service'
        LaunchMode = 'SPRING_BOOT_FAT_JAR'
        RuntimePolicy = 'NO_CDS_LOW_DIRTY'
        BaselineArtifactPath = $v1Artifact
        CandidateArtifactPath = $v2Artifact
        StopScript = $stop
        ContainerCli = $ContainerCli
        JcmdExecutable = 'jcmd'
        PairIndex = $pairIndex
        FirstVariant = $orders[$pairIndex]
        CaptureRoot = $pairOutput
        BaselineRuntimeVerificationPath = $materializationProof
        CandidateRuntimeVerificationPath = $materializationProof
        MallocArenaMax = '1'
        CdsEnabled = $false
        AppCdsEnabled = $false
        LeydenEnabled = $false
        JavaagentPresent = $false
        WorkloadId = 'patient-v1-v2-final-nocds'
        WarmupSeconds = $WarmupSeconds
        PostWorkloadSnapshotSeconds = @($PostWorkloadSnapshotSeconds)
        DropPageCacheBeforeVariant = $true
        HealthTimeoutSeconds = $HealthTimeoutSeconds
        FailOnFailure = $true
    }
    & $screen @parameters
    if ($LASTEXITCODE -ne 0) {
        throw "Patient no-CDS confirmation pair $pairIndex failed. See $pairOutput."
    }
}

Write-Host "Patient no-CDS confirmation captured pair indexes: $($PairIndexes -join ', ')"

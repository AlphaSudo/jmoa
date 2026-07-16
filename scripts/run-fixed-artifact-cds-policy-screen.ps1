param(
    [Parameter(Mandatory)][string]$NoCdsLaunchScript,
    [Parameter(Mandatory)][string]$CdsLaunchScript,
    [Parameter(Mandatory)][string]$StopScript,
    [Parameter(Mandatory)][string]$WorkloadScript,
    [Parameter(Mandatory)][string]$ArtifactPath,
    [Parameter(Mandatory)][string]$CdsArchivePath,
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string]$LaunchMode,
    [string[]]$NoCdsLaunchArguments = @(),
    [string[]]$CdsLaunchArguments = @(),
    [string[]]$WorkloadArguments = @(),
    [string]$RuntimeVerificationPath = '',
    [string]$RuntimeArtifactPath = '',
    [string]$ContainerName = 'jmoa-policy-screen',
    [string]$ContainerCli = 'podman',
    [int]$PairIndex = 1,
    [ValidateSet('BASELINE_FIRST','CANDIDATE_FIRST')][string]$FirstVariant = 'BASELINE_FIRST',
    [string]$CaptureRoot = 'target/jmoa-runtime-policy-screen',
    [int]$WarmupSeconds = 20,
    [int]$PostWorkloadSnapshotSeconds = 20,
    [int]$HealthTimeoutSeconds = 180,
    [switch]$DropPageCacheBeforeVariant,
    [switch]$FailOnFailure
)

$ErrorActionPreference = 'Stop'
foreach ($path in @($NoCdsLaunchScript,$CdsLaunchScript,$StopScript,$WorkloadScript,$ArtifactPath,$CdsArchivePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required input does not exist: $path" }
}
if (-not [string]::IsNullOrWhiteSpace($RuntimeVerificationPath) -and -not (Test-Path -LiteralPath $RuntimeVerificationPath -PathType Leaf)) {
    throw "Runtime verification report does not exist: $RuntimeVerificationPath"
}

$screen = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
$parameters = @{
    BaselineLaunchScript = $NoCdsLaunchScript
    CandidateLaunchScript = $CdsLaunchScript
    BaselineContainerName = $ContainerName
    CandidateContainerName = $ContainerName
    WorkloadScript = $WorkloadScript
    HealthUrl = $HealthUrl
    Service = $Service
    LaunchMode = $LaunchMode
    RuntimePolicy = 'MIXED_POLICY_DIAGNOSTIC'
    BaselineRuntimePolicy = 'NO_CDS_LOW_DIRTY'
    CandidateRuntimePolicy = 'CDS'
    BaselineCdsEnabled = $false
    CandidateCdsEnabled = $true
    CandidateCdsArchivePath = $CdsArchivePath
    BaselineRuntimeArtifactPath = $RuntimeArtifactPath
    CandidateRuntimeArtifactPath = $RuntimeArtifactPath
    BaselineMallocArenaMax = '1'
    CandidateMallocArenaMax = '1'
    BaselineArtifactPath = $ArtifactPath
    CandidateArtifactPath = $ArtifactPath
    BaselineLaunchArguments = $NoCdsLaunchArguments
    CandidateLaunchArguments = $CdsLaunchArguments
    WorkloadArguments = $WorkloadArguments
    StopScript = $StopScript
    ContainerCli = $ContainerCli
    PairIndex = $PairIndex
    FirstVariant = $FirstVariant
    CaptureRoot = $CaptureRoot
    BaselineRuntimeVerificationPath = $RuntimeVerificationPath
    CandidateRuntimeVerificationPath = $RuntimeVerificationPath
    WorkloadId = 'fixed-artifact-nocds-vs-cds-diagnostic'
    WarmupSeconds = $WarmupSeconds
    PostWorkloadSnapshotSeconds = @($PostWorkloadSnapshotSeconds)
    HealthTimeoutSeconds = $HealthTimeoutSeconds
    DiagnosticOnly = $true
    DropPageCacheBeforeVariant = $DropPageCacheBeforeVariant
    FailOnFailure = $FailOnFailure
}
& $screen @parameters
exit $LASTEXITCODE

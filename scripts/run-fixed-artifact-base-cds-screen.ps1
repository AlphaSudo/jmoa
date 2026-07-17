param(
    [Parameter(Mandatory)][string]$OffLaunchScript,[Parameter(Mandatory)][string]$BaseLaunchScript,
    [Parameter(Mandatory)][string]$StopScript,[Parameter(Mandatory)][string]$WorkloadScript,
    [Parameter(Mandatory)][string]$ArtifactPath,[Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$Service,[Parameter(Mandatory)][string]$LaunchMode,
    [string]$RuntimeVerificationPath='',[string]$RuntimeArtifactPath='',[string]$ContainerName='jmoa-base-cds-screen',
    [string]$ContainerCli='podman',[int]$PairIndex=1,[ValidateSet('OFF_FIRST','BASE_FIRST')][string]$Order='OFF_FIRST',
    [string]$CaptureRoot='target/jmoa-base-cds-screen',[int]$WarmupSeconds=20,[int]$PostWorkloadSnapshotSeconds=20,
    [int]$HealthTimeoutSeconds=180,[switch]$DropPageCacheBeforeVariant,[switch]$FailOnFailure
)
$ErrorActionPreference='Stop'
$parameters=@{
    BaselineLaunchScript=$OffLaunchScript;CandidateLaunchScript=$BaseLaunchScript;BaselineContainerName=$ContainerName;CandidateContainerName=$ContainerName
    WorkloadScript=$WorkloadScript;HealthUrl=$HealthUrl;Service=$Service;LaunchMode=$LaunchMode;RuntimePolicy='BASE_VS_OFF_DIAGNOSTIC'
    BaselineRuntimePolicy='NO_CDS_LOW_DIRTY';CandidateRuntimePolicy='BASE_CDS';BaselineCdsEnabled=$false;CandidateCdsEnabled=$true
    BaselineMallocArenaMax='1';CandidateMallocArenaMax='1';BaselineArtifactPath=$ArtifactPath;CandidateArtifactPath=$ArtifactPath
    StopScript=$StopScript;ContainerCli=$ContainerCli;PairIndex=$PairIndex;FirstVariant=if($Order-eq'OFF_FIRST'){'BASELINE_FIRST'}else{'CANDIDATE_FIRST'}
    CaptureRoot=$CaptureRoot;BaselineRuntimeVerificationPath=$RuntimeVerificationPath;CandidateRuntimeVerificationPath=$RuntimeVerificationPath
    BaselineRuntimeArtifactPath=$RuntimeArtifactPath;CandidateRuntimeArtifactPath=$RuntimeArtifactPath
    WorkloadId='fixed-artifact-base-vs-off-diagnostic';WarmupSeconds=$WarmupSeconds;PostWorkloadSnapshotSeconds=@($PostWorkloadSnapshotSeconds)
    HealthTimeoutSeconds=$HealthTimeoutSeconds;DiagnosticOnly=$true;DropPageCacheBeforeVariant=$DropPageCacheBeforeVariant;FailOnFailure=$FailOnFailure
}
& (Join-Path $PSScriptRoot 'runtime-screen-pair.ps1') @parameters
exit $LASTEXITCODE

param(
    [Parameter(Mandatory)][string]$BaseLaunchScript,
    [Parameter(Mandatory)][string]$AppLaunchScript,
    [Parameter(Mandatory)][string]$StopScript,
    [Parameter(Mandatory)][string]$WorkloadScript,
    [Parameter(Mandatory)][string]$ArtifactPath,
    [Parameter(Mandatory)][string]$AppArchivePath,
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string]$LaunchMode,
    [string[]]$BaseLaunchArguments=@(),[string[]]$AppLaunchArguments=@(),[string[]]$WorkloadArguments=@(),
    [string]$RuntimeVerificationPath='',[string]$RuntimeArtifactPath='',[string]$ContainerName='jmoa-appcds-screen',
    [string]$ContainerCli='podman',[int]$PairIndex=1,
    [ValidateSet('BASE_FIRST','APP_FIRST')][string]$Order='BASE_FIRST',
    [string]$CaptureRoot='target/jmoa-appcds-screen',[int]$WarmupSeconds=20,[int]$PostWorkloadSnapshotSeconds=20,
    [int]$HealthTimeoutSeconds=180,[switch]$DropPageCacheBeforeVariant,[switch]$FailOnFailure
)
$ErrorActionPreference='Stop'
foreach($path in @($BaseLaunchScript,$AppLaunchScript,$StopScript,$WorkloadScript,$ArtifactPath,$AppArchivePath)){if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Required input does not exist: $path"}}
$parameters=@{
    BaselineLaunchScript=$BaseLaunchScript;CandidateLaunchScript=$AppLaunchScript
    BaselineContainerName=$ContainerName;CandidateContainerName=$ContainerName;WorkloadScript=$WorkloadScript
    HealthUrl=$HealthUrl;Service=$Service;LaunchMode=$LaunchMode;RuntimePolicy='APP_VS_BASE_DIAGNOSTIC'
    BaselineRuntimePolicy='BASE_CDS';CandidateRuntimePolicy='APP_CDS';BaselineCdsEnabled=$true;CandidateCdsEnabled=$true
    CandidateCdsArchivePath=$AppArchivePath;BaselineMallocArenaMax='1';CandidateMallocArenaMax='1'
    BaselineArtifactPath=$ArtifactPath;CandidateArtifactPath=$ArtifactPath;BaselineLaunchArguments=$BaseLaunchArguments
    CandidateLaunchArguments=$AppLaunchArguments;WorkloadArguments=$WorkloadArguments;StopScript=$StopScript;ContainerCli=$ContainerCli
    PairIndex=$PairIndex;FirstVariant=if($Order-eq'BASE_FIRST'){'BASELINE_FIRST'}else{'CANDIDATE_FIRST'};CaptureRoot=$CaptureRoot
    BaselineRuntimeVerificationPath=$RuntimeVerificationPath;CandidateRuntimeVerificationPath=$RuntimeVerificationPath
    BaselineRuntimeArtifactPath=$RuntimeArtifactPath;CandidateRuntimeArtifactPath=$RuntimeArtifactPath
    WorkloadId='fixed-artifact-app-vs-base-diagnostic';WarmupSeconds=$WarmupSeconds
    PostWorkloadSnapshotSeconds=@($PostWorkloadSnapshotSeconds);HealthTimeoutSeconds=$HealthTimeoutSeconds
    DiagnosticOnly=$true;DropPageCacheBeforeVariant=$DropPageCacheBeforeVariant;FailOnFailure=$FailOnFailure
}
& (Join-Path $PSScriptRoot 'runtime-screen-pair.ps1') @parameters
exit $LASTEXITCODE

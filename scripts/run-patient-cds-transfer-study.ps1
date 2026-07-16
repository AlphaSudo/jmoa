param(
    [Parameter(Mandatory)][string]$EvidenceRoot,
    [Parameter(Mandatory)][string]$OutputRoot,
    [ValidateSet('SCREENS','COMPARE_ARCHIVES','ALL')][string]$Mode='ALL',
    [string]$ContainerCli='podman',
    [string]$HealthUrl='http://localhost:18084/actuator/health',
    [int]$WarmupSeconds=20,
    [int]$PostWorkloadSnapshotSeconds=20,
    [int]$HealthTimeoutSeconds=180
)

$ErrorActionPreference='Stop'
$required=@(
    'runtime/patient-v1-nocds-launch.ps1','runtime/patient-v1-launch.ps1',
    'runtime/patient-v2-nocds-launch.ps1','runtime/patient-v2-launch.ps1',
    'runtime/patient-stop.ps1','runtime/patient-workload.ps1',
    'runtime/v1.jsa','runtime/v2.jsa','artifacts/v1-final.jar','artifacts/v2-final.jar'
)
foreach($relative in $required){$path=Join-Path $EvidenceRoot $relative;if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Missing Patient study input: $path"}}
New-Item -ItemType Directory -Force -Path $OutputRoot|Out-Null
$proof=Join-Path $EvidenceRoot 'artifacts/v2-patient-materialization-proof.json'
if(-not(Test-Path -LiteralPath $proof -PathType Leaf)){$proof=''}

if($Mode -in @('SCREENS','ALL')){
    foreach($version in @('v1','v2')){
        $artifact=Join-Path $EvidenceRoot "artifacts/$version-final.jar"
        $screenArgs=@{
            NoCdsLaunchScript=Join-Path $EvidenceRoot "runtime/patient-$version-nocds-launch.ps1"
            CdsLaunchScript=Join-Path $EvidenceRoot "runtime/patient-$version-launch.ps1"
            StopScript=Join-Path $EvidenceRoot 'runtime/patient-stop.ps1'
            WorkloadScript=Join-Path $EvidenceRoot 'runtime/patient-workload.ps1'
            ArtifactPath=$artifact;CdsArchivePath=Join-Path $EvidenceRoot "runtime/$version.jsa"
            HealthUrl=$HealthUrl;Service='patient-service';LaunchMode='SPRING_BOOT_FAT_JAR'
            RuntimeVerificationPath=$proof;ContainerName='jmoa-v2-patient-app';ContainerCli=$ContainerCli
            RuntimeArtifactPath='/app/app.jar'
            CaptureRoot=Join-Path $OutputRoot "$version-policy-screen";WarmupSeconds=$WarmupSeconds
            PostWorkloadSnapshotSeconds=$PostWorkloadSnapshotSeconds;DropPageCacheBeforeVariant=$true;FailOnFailure=$true
            HealthTimeoutSeconds=$HealthTimeoutSeconds
        }
        & (Join-Path $PSScriptRoot 'run-fixed-artifact-cds-policy-screen.ps1') @screenArgs
        if($LASTEXITCODE -ne 0){throw "Patient $version fixed-artifact policy screen failed."}
    }
}
if($Mode -in @('COMPARE_ARCHIVES','ALL')){
    $v1Log=Join-Path $EvidenceRoot 'runtime/v1-class-load.log'
    $v2Log=Join-Path $EvidenceRoot 'runtime/v2-class-load.log'
    if(-not(Test-Path -LiteralPath $v1Log) -or -not(Test-Path -LiteralPath $v2Log)){throw 'Archive comparison requires V1 and V2 training class-load logs.'}
    & (Join-Path $PSScriptRoot 'compare-cds-archives.ps1') -LeftArchivePath (Join-Path $EvidenceRoot 'runtime/v1.jsa') `
        -RightArchivePath (Join-Path $EvidenceRoot 'runtime/v2.jsa') -LeftClassLoadLog $v1Log -RightClassLoadLog $v2Log `
        -LeftLabel 'Patient V1' -RightLabel 'Patient V2' -OutputDir (Join-Path $OutputRoot 'archive-comparison')
    if($LASTEXITCODE -ne 0){throw 'Patient archive comparison failed.'}
}
exit 0

param(
    [Parameter(Mandatory)][string]$EvidenceRoot,[Parameter(Mandatory)][string]$StudyDirectory,
    [ValidateSet('v1','v2')][string[]]$Versions=@('v1','v2'),
    [ValidateSet('STARTUP','REPRESENTATIVE')][string[]]$Profiles=@('STARTUP','REPRESENTATIVE'),
    [ValidateSet('A','B')][string[]]$Copies=@('A','B'),
    [string]$HealthUrl='http://localhost:18084/actuator/health',[string]$ContainerCli='podman',[int]$HealthTimeoutSeconds=180
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
foreach($relative in @('runtime/patient-stop.ps1','runtime/patient-workload.ps1')){if(-not(Test-Path -LiteralPath (Join-Path $EvidenceRoot $relative)-PathType Leaf)){throw "Missing Patient input: $relative"}}
New-JmoaDirectory $StudyDirectory
function Get-ClasspathFingerprint([string]$version){
    $artifact=Join-Path $EvidenceRoot "artifacts/$version-final.jar";$libDir=Join-Path $EvidenceRoot "artifacts/$version-embedded-libs"
    $lines=[Collections.Generic.List[string]]::new();$lines.Add("artifact=$(Get-JmoaSha256 $artifact)")
    if(Test-Path -LiteralPath $libDir -PathType Container){Get-ChildItem -LiteralPath $libDir -File|Sort-Object Name|ForEach-Object{$lines.Add("$($_.Name)=$(Get-JmoaSha256 $_.FullName)")}}
    Get-JmoaTextSha256 ($lines-join"`n")
}
foreach($version in $Versions){
    $artifact=Join-Path $EvidenceRoot "artifacts/$version-final.jar";$fingerprint=Get-ClasspathFingerprint $version
    foreach($profile in $Profiles){foreach($copy in $Copies){
        $stem="$version-$($profile.ToLowerInvariant())-$($copy.ToLowerInvariant())"
        $archive=Join-Path $StudyDirectory "$stem.jsa";$classLog=Join-Path $StudyDirectory "$stem-training.log"
        $runDir=Join-Path $StudyDirectory "training/$stem";$launch=Join-Path $StudyDirectory "patient-$stem-train-launch.ps1"
        foreach($path in @($artifact,$launch)){if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Missing training input: $path"}}
        $args=@{
            LaunchScript=$launch;StopScript=Join-Path $EvidenceRoot 'runtime/patient-stop.ps1';HealthUrl=$HealthUrl
            ContainerName='jmoa-v2-patient-app';Variant=$stem;RunDirectory=$runDir;ExpectedArchive=$archive
            ContainerCli=$ContainerCli;HealthTimeoutSeconds=$HealthTimeoutSeconds;ReadinessSettleSeconds=20
            TrainingProfile=$profile;ArtifactPath=$artifact;ClasspathFingerprint=$fingerprint;ClassLoadLogPath=$classLog
        }
        if($profile-eq'REPRESENTATIVE'){$args.WorkloadScript=Join-Path $EvidenceRoot 'runtime/patient-workload.ps1'}
        & (Join-Path $PSScriptRoot 'train-compose-cds-variant.ps1') @args
        if($LASTEXITCODE-ne0){throw "Training failed: $stem"}
        & (Join-Path $PSScriptRoot 'inspect-dynamic-appcds-archive.ps1') -ArchivePath $archive -ArtifactPath $artifact -ContainerCli $ContainerCli -OutputDir (Join-Path $StudyDirectory "inspection/$stem")
        if($LASTEXITCODE-ne0){throw "Archive inspection failed: $stem"}
    }}
}
Write-Host 'Patient AppCDS training matrix completed.'
exit 0

param(
    [Parameter(Mandatory)][string]$ArchivePath,
    [Parameter(Mandatory)][string]$ArtifactPath,
    [string]$ContainerCli='podman',[string]$JdkImage='eclipse-temurin:26-jdk-jammy',
    [string]$OutputDir='target/jmoa-appcds-archive-inspection'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
foreach($path in @($ArchivePath,$ArtifactPath)){if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "Required input does not exist: $path"}}
New-JmoaDirectory $OutputDir
$archiveMount=(Resolve-Path $ArchivePath).Path.Replace('\','/')+':/archive.jsa:ro'
$artifactMount=(Resolve-Path $ArtifactPath).Path.Replace('\','/')+':/app/app.jar:ro'
$result=Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('run','--rm','-v',$archiveMount,'-v',$artifactMount,$JdkImage,
    'java','-XX:+UseCompactObjectHeaders','-XX:SharedArchiveFile=/archive.jsa','-XX:+PrintSharedArchiveAndExit','-jar','/app/app.jar')
Write-JmoaText $result.output (Join-Path $OutputDir 'print-shared-archive.txt')
if($result.exitCode-ne0-or$result.output-notmatch'Dynamic archive name:'){throw "Dynamic archive inspection failed: $($result.output | Select-Object -First 1)"}
$dynamic=$false;$classes=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$loaders=[ordered]@{}
foreach($line in ($result.output-split"`r?`n")){
    if($line-match'^Dynamic archive name:'){$dynamic=$true;continue}
    if($dynamic-and$line-match'^Archived TrainingData Dictionary'){$dynamic=$false}
    if($dynamic-and$line-match'^\s*\d+:\s+(\S+)\s+(\S+_loader)\s*$'){
        $name=$Matches[1]-replace'/0x[0-9a-fA-F]+$','/<hidden>'-replace'\$\$Lambda\$\d+','$$Lambda$<generated>'
        [void]$classes.Add($name);$loader=$Matches[2];if(-not$loaders.Contains($loader)){$loaders[$loader]=0};$loaders[$loader]++
    }
}
$report=[ordered]@{
    metadataVersion='jmoa-dynamic-appcds-archive-inspection-v1';status='PASSED'
    archiveSha256=Get-JmoaSha256 $ArchivePath;archiveBytes=[long](Get-Item $ArchivePath).Length
    artifactSha256=Get-JmoaSha256 $ArtifactPath;dynamicArchivedClassCount=$classes.Count
    normalizedDynamicArchivedClasses=@($classes|Sort-Object);classLoaderCounts=$loaders
    jdkImage=$JdkImage;classpathValidation='PASSED';aotCacheMode=$false
}
Write-JmoaJson $report (Join-Path $OutputDir 'dynamic-appcds-archive-inspection.json')
$md="# Dynamic AppCDS Archive Inspection`n`n- Status: ``PASSED```n- Dynamic archived classes: ``$($classes.Count)```n- Archive bytes: ``$($report.archiveBytes)```n- Classpath validation: ``PASSED```n"
Write-JmoaText $md (Join-Path $OutputDir 'dynamic-appcds-archive-inspection.md')
exit 0

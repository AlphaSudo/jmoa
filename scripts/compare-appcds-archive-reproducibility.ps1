param(
    [Parameter(Mandatory)][string]$InspectionA,[Parameter(Mandatory)][string]$InspectionB,
    [Parameter(Mandatory)][string]$RuntimeAnalysisA,[Parameter(Mandatory)][string]$RuntimeAnalysisB,
    [Parameter(Mandatory)][string]$ArtifactLabel,[Parameter(Mandatory)][ValidateSet('STARTUP','REPRESENTATIVE')][string]$Profile,
    [double]$MinimumArchivedClassJaccard=0.999,[double]$MaximumArchiveBytesDeltaPercent=1.0,
    [double]$MaximumMappedPssDeltaPercent=5.0,[double]$MaximumSharedClassSpaceDeltaPercent=5.0,
    [string]$OutputDir='target/jmoa-appcds-reproducibility'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
New-JmoaDirectory $OutputDir
$a=Get-Content -Raw -LiteralPath $InspectionA|ConvertFrom-Json;$b=Get-Content -Raw -LiteralPath $InspectionB|ConvertFrom-Json
$ra=Get-Content -Raw -LiteralPath $RuntimeAnalysisA|ConvertFrom-Json;$rb=Get-Content -Raw -LiteralPath $RuntimeAnalysisB|ConvertFrom-Json
$setA=[Collections.Generic.HashSet[string]]::new([string[]]$a.normalizedDynamicArchivedClasses,[StringComparer]::Ordinal)
$setB=[Collections.Generic.HashSet[string]]::new([string[]]$b.normalizedDynamicArchivedClasses,[StringComparer]::Ordinal)
$intersection=@($setA|Where-Object{$setB.Contains($_)}).Count;$union=$setA.Count+$setB.Count-$intersection
$jaccard=if($union-eq0){1.0}else{[Math]::Round($intersection/$union,6)}
function PercentDelta([double]$x,[double]$y){$max=[Math]::Max([Math]::Abs($x),[Math]::Abs($y));if($max-eq0){return 0.0};return [Math]::Round(100.0*[Math]::Abs($x-$y)/$max,6)}
$archiveDelta=PercentDelta $a.archiveBytes $b.archiveBytes
$mappedDelta=PercentDelta $ra.candidate.categories.archiveMappedPssKb $rb.candidate.categories.archiveMappedPssKb
$sharedDelta=PercentDelta $ra.candidate.nmt.sharedClassSpaceCommittedKb $rb.candidate.nmt.sharedClassSpaceCommittedKb
$fingerprints=$a.artifactSha256-eq$b.artifactSha256
$stable=$jaccard-ge$MinimumArchivedClassJaccard-and$archiveDelta-le$MaximumArchiveBytesDeltaPercent-and$mappedDelta-le$MaximumMappedPssDeltaPercent-and$sharedDelta-le$MaximumSharedClassSpaceDeltaPercent-and$fingerprints
$report=[ordered]@{
    metadataVersion='jmoa-appcds-reproducibility-v1';artifact=$ArtifactLabel;profile=$Profile
    status=if($stable){'TRAINING_STRUCTURALLY_STABLE'}else{'NON_REPRODUCIBLE_ARCHIVE'}
    normalizedArchivedClassJaccard=$jaccard;archiveBytesDeltaPercent=$archiveDelta;mappedArchivePssDeltaPercent=$mappedDelta
    sharedClassSpaceDeltaPercent=$sharedDelta;artifactFingerprintEqual=$fingerprints
    gates=[ordered]@{minimumArchivedClassJaccard=$MinimumArchivedClassJaccard;maximumArchiveBytesDeltaPercent=$MaximumArchiveBytesDeltaPercent;maximumMappedPssDeltaPercent=$MaximumMappedPssDeltaPercent;maximumSharedClassSpaceDeltaPercent=$MaximumSharedClassSpaceDeltaPercent}
    behavioralDirectionGate='ASSESSED_BY_BALANCED_APP_VS_BASE_SCREENS'
}
Write-JmoaJson $report (Join-Path $OutputDir 'appcds-reproducibility.json')
$md="# AppCDS Archive Reproducibility`n`n- Artifact/profile: ``$ArtifactLabel / $Profile```n- Status: ``$($report.status)```n- Archived-class Jaccard: ``$jaccard```n- Archive bytes delta: ``$archiveDelta%```n- Mapped PSS delta: ``$mappedDelta%```n- Shared Class Space delta: ``$sharedDelta%```n"
Write-JmoaText $md (Join-Path $OutputDir 'appcds-reproducibility.md')
exit 0

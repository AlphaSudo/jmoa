param(
    [Parameter(Mandatory)][string]$ArchiveA,
    [Parameter(Mandatory)][string]$ArchiveB,
    [Parameter(Mandatory)][string]$ClassLoadLogA,
    [Parameter(Mandatory)][string]$ClassLoadLogB,
    [string]$Label = 'artifact',
    [double]$MinimumClassSetJaccard = 0.995,
    [double]$MaximumArchiveSizeDeltaPercent = 1.0,
    [string]$OutputDir = 'target/jmoa-cds-training-reproducibility'
)

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
New-JmoaDirectory $OutputDir
$comparisonDir = Join-Path $OutputDir 'comparison'
& (Join-Path $PSScriptRoot 'compare-cds-archives.ps1') -LeftArchivePath $ArchiveA -RightArchivePath $ArchiveB `
    -LeftClassLoadLog $ClassLoadLogA -RightClassLoadLog $ClassLoadLogB -LeftLabel "$Label-A" -RightLabel "$Label-B" -OutputDir $comparisonDir
if ($LASTEXITCODE -ne 0) { throw 'Archive comparison failed.' }
$comparison = Get-Content -Raw -LiteralPath (Join-Path $comparisonDir 'cds-archive-comparison.json') | ConvertFrom-Json
$maxBytes = [Math]::Max([double]$comparison.left.archiveBytes,[double]$comparison.right.archiveBytes)
$sizeDeltaPercent = if ($maxBytes -eq 0) { 0 } else { [Math]::Round(100.0 * [Math]::Abs($comparison.left.archiveBytes-$comparison.right.archiveBytes)/$maxBytes,6) }
$stable = $comparison.normalizedClassSets.jaccard -ge $MinimumClassSetJaccard -and $sizeDeltaPercent -le $MaximumArchiveSizeDeltaPercent
$report=[ordered]@{
    metadataVersion='jmoa-cds-training-reproducibility-v1'; label=$Label
    status=if($stable){'TRAINING_STRUCTURALLY_STABLE'}else{'CDS_ARCHIVE_TRAINING_UNSTABLE'}
    archiveHashesEqual=$comparison.left.archiveSha256 -eq $comparison.right.archiveSha256
    archiveSizeDeltaPercent=$sizeDeltaPercent
    normalizedClassSetJaccard=$comparison.normalizedClassSets.jaccard
    gates=[ordered]@{minimumClassSetJaccard=$MinimumClassSetJaccard;maximumArchiveSizeDeltaPercent=$MaximumArchiveSizeDeltaPercent}
    runtimeScreenRequired=$true
    claimBoundary='Structural stability does not prove runtime-memory stability. Screen both archives; do not select only the favorable archive.'
}
Write-JmoaJson $report (Join-Path $OutputDir 'cds-training-reproducibility.json')
$md="# CDS Training Reproducibility`n`n- Status: ``$($report.status)```n- Normalized class-set Jaccard: ``$($report.normalizedClassSetJaccard)```n- Archive-size delta: ``$sizeDeltaPercent%```n`nBoth archives still require diagnostic runtime screens; neither may be cherry-picked.`n"
Write-JmoaText $md (Join-Path $OutputDir 'cds-training-reproducibility.md')
exit 0

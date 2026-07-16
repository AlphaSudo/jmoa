param(
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string]$HistoricalReportPath,
    [string[]]$ArchivePaths = @(),
    [string]$HistoricalArtifactPath = '',
    [string]$SourceRevision = '',
    [switch]$FixedArtifactPolicyComparison,
    [int]$AcceptedRunCount = 0,
    [int]$AcceptedPairCount = 0,
    [string]$OutputDir = 'target/jmoa-historical-cds-audit'
)

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if (-not (Test-Path -LiteralPath $HistoricalReportPath -PathType Leaf)) { throw "Historical report does not exist: $HistoricalReportPath" }
New-JmoaDirectory $OutputDir
$archiveRecords=@()
foreach($archive in $ArchivePaths){
    if(Test-Path -LiteralPath $archive -PathType Leaf){
        $archiveRecords += [ordered]@{fileName=[IO.Path]::GetFileName($archive);sha256=Get-JmoaSha256 $archive;bytes=[long](Get-Item $archive).Length}
    }
}
$artifactKnown = -not [string]::IsNullOrWhiteSpace($HistoricalArtifactPath) -and (Test-Path -LiteralPath $HistoricalArtifactPath -PathType Leaf)
$revisionKnown = -not [string]::IsNullOrWhiteSpace($SourceRevision)
$classification = if ($FixedArtifactPolicyComparison -and $AcceptedPairCount -ge 3 -and $artifactKnown -and $archiveRecords.Count -gt 0) {
    'V1_CDS_CONFIRMED'
} elseif ($AcceptedRunCount -ge 2 -and $AcceptedPairCount -lt 3) {
    'V1_CDS_SCREEN_ONLY'
} elseif (-not $artifactKnown -or -not $revisionKnown -or -not $FixedArtifactPolicyComparison) {
    'V1_CDS_HISTORICAL_INCOMPLETE'
} else {
    'V1_CDS_NOT_REPRODUCIBLE'
}
$report=[ordered]@{
    metadataVersion='jmoa-historical-cds-audit-v1';service=$Service;classification=$classification
    reportFile=[IO.Path]::GetFileName($HistoricalReportPath)
    acceptedRunCount=$AcceptedRunCount;acceptedPairCount=$AcceptedPairCount
    fixedArtifactPolicyComparison=[bool]$FixedArtifactPolicyComparison
    artifact=if($artifactKnown){[ordered]@{fileName=[IO.Path]::GetFileName($HistoricalArtifactPath);sha256=Get-JmoaSha256 $HistoricalArtifactPath;bytes=[long](Get-Item $HistoricalArtifactPath).Length}}else{$null}
    sourceRevision=if($revisionKnown){$SourceRevision}else{'UNKNOWN'}
    archives=$archiveRecords
    missingFields=@(
        if(-not $artifactKnown){'FROZEN_HISTORICAL_ARTIFACT_SHA256'}
        if(-not $revisionKnown){'SOURCE_REVISION'}
        if(-not $FixedArtifactPolicyComparison){'FIXED_ARTIFACT_NO_CDS_VS_CDS_COMPARISON'}
    )
    claimBoundary='Historical optimized-vs-baseline evidence may be valid for its original claim while remaining insufficient to prove the isolated CDS policy effect.'
    nextAction=if($classification -eq 'V1_CDS_CONFIRMED'){'Proceed to archive transfer analysis.'}else{'Run one current fixed-artifact V1 no-CDS vs V1 CDS diagnostic screen before attributing the historical result to CDS.'}
}
Write-JmoaJson $report (Join-Path $OutputDir 'historical-cds-audit.json')
$missing=if($report.missingFields.Count -eq 0){'None'}else{$report.missingFields -join ', '}
$md=@"
# Historical CDS Evidence Audit

- Service: ``$Service``
- Classification: ``$classification``
- Accepted runs/pairs recorded: ``$AcceptedRunCount`` / ``$AcceptedPairCount``
- Fixed-artifact CDS policy comparison: ``$([bool]$FixedArtifactPolicyComparison)``
- Missing current-gate fields: $missing

The audit preserves the original result but does not treat an optimized-artifact comparison with per-variant archives as proof that CDS itself helped a fixed artifact.
"@
Write-JmoaText $md (Join-Path $OutputDir 'historical-cds-audit.md')
exit 0

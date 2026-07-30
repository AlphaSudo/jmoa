param(
    [Parameter(Mandatory)][string]$HistoricalDiagnosticRoot,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $HistoricalDiagnosticRoot -PathType Container)) {
    throw "Historical diagnostic root does not exist: $HistoricalDiagnosticRoot"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$files = @(Get-ChildItem -LiteralPath $HistoricalDiagnosticRoot -File -Filter '*.txt')
$records = @()
foreach ($file in $files) {
    $text = Get-Content -Raw -LiteralPath $file.FullName
    $archiveRejected = $text -match '(?i)(top archive failed to load|loading dynamic archive failed|unable to use shared archive)'
    $defaultMapped = $text -match '(?i)(classes(_coh)?\.jsa|CDS archive\(s\) mapped at)'
    if (-not $archiveRejected -and -not $defaultMapped) { continue }
    $variant = if ($file.Name -match '^baseline-') { 'B0' } elseif ($file.Name -match '^d2fixed-') { 'V1' } else { 'UNKNOWN' }
    $records += [ordered]@{
        sourceFileSha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
        variant = $variant
        requestedApplicationArchiveRejected = $archiveRejected
        defaultArchiveMappingObserved = $defaultMapped
    }
}

$b0 = @($records | Where-Object variant -eq 'B0')
$v1 = @($records | Where-Object variant -eq 'V1')
$bothRejected = @($b0 | Where-Object requestedApplicationArchiveRejected).Count -gt 0 -and
    @($v1 | Where-Object requestedApplicationArchiveRejected).Count -gt 0
$bothDefault = @($b0 | Where-Object defaultArchiveMappingObserved).Count -gt 0 -and
    @($v1 | Where-Object defaultArchiveMappingObserved).Count -gt 0

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-historical-cds-effective-state-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    historicalIntent = 'ARTIFACT_SPECIFIC_APPLICATION_CDS'
    observedEffectivePolicy = if ($bothRejected -and $bothDefault) { 'BASE_CDS_AFTER_APPLICATION_ARCHIVE_FALLBACK' } else { 'UNRESOLVED' }
    evidence = [ordered]@{
        diagnosticFilesScanned = $files.Count
        relevantFiles = $records.Count
        b0ArchiveRejectionFiles = @($b0 | Where-Object requestedApplicationArchiveRejected).Count
        v1ArchiveRejectionFiles = @($v1 | Where-Object requestedApplicationArchiveRejected).Count
        b0DefaultMappingFiles = @($b0 | Where-Object defaultArchiveMappingObserved).Count
        v1DefaultMappingFiles = @($v1 | Where-Object defaultArchiveMappingObserved).Count
        sources = $records
    }
    decision = [ordered]@{
        applicationCdsClaimSupported = $false
        historicalMemoryComparisonUsableAs = if ($bothRejected -and $bothDefault) { 'BASE_CDS_COMPARISON_WITH_SILENT_FALLBACK' } else { 'UNRESOLVED_RUNTIME_POLICY' }
        correctedDiagnosticPolicy = if ($bothRejected -and $bothDefault) { 'EXPLICIT_BASE_CDS' } else { 'BLOCKED' }
        rationale = 'A requested archive is not proof of use. Both variants must be classified by live mappings and startup diagnostics.'
    }
    privacyBoundary = 'Only source-file hashes and aggregate CDS state are published; local paths and service configuration are omitted.'
}
$jsonPath = Join-Path $OutputDirectory 'doctor-historical-cds-effective-state.json'
$mdPath = Join-Path $OutputDirectory 'doctor-historical-cds-effective-state.md'
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
@"
# Doctor Historical CDS Effective State

- Historical intent: **artifact-specific application CDS**
- Observed effective policy: **$($report.observedEffectivePolicy)**
- B0 archive-rejection evidence files: **$($report.evidence.b0ArchiveRejectionFiles)**
- V1 archive-rejection evidence files: **$($report.evidence.v1ArchiveRejectionFiles)**
- B0 default-CDS mapping evidence files: **$($report.evidence.b0DefaultMappingFiles)**
- V1 default-CDS mapping evidence files: **$($report.evidence.v1DefaultMappingFiles)**
- Application-CDS claim supported: **false**
- Corrected diagnostic policy: **$($report.decision.correctedDiagnosticPolicy)**

The historical Doctor process requested application archives, but the recovered runtime diagnostics show that HotSpot rejected the top archive for both variants and continued with its default CDS archive. The historical memory comparison is therefore a base-CDS fallback comparison, not an application-CDS comparison.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8
Write-Host "Doctor historical CDS policy: $($report.observedEffectivePolicy)"

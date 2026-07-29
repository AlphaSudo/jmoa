<#
.SYNOPSIS
    Reconciles historical B0/V1 evidence with sealed current campaigns.

.DESCRIPTION
    Read-only analyzer. It parses evidence and artifacts but never launches a
    service, changes an input, or writes below an evidence source directory.
#>
param(
    [Parameter(Mandatory)][string]$DoctorCampaignRoot,
    [Parameter(Mandatory)][string]$PatientCampaignRoot,
    [Parameter(Mandatory)][string]$PetClinicCampaignRoot,
    [Parameter(Mandatory)][string]$DoctorHistoricalReport,
    [Parameter(Mandatory)][string]$PatientHistoricalRoot,
    [Parameter(Mandatory)][string]$PetClinicHistoricalReport,
    [Parameter(Mandatory)][string]$DoctorHistoricalB0Artifact,
    [Parameter(Mandatory)][string]$DoctorCurrentB0Artifact,
    [Parameter(Mandatory)][string]$DoctorHistoricalV1Artifact,
    [Parameter(Mandatory)][string]$DoctorCurrentV1Artifact,
    [Parameter(Mandatory)][string]$DoctorHistoricalB0Cds,
    [Parameter(Mandatory)][string]$DoctorHistoricalV1Cds,
    [Parameter(Mandatory)][string]$PatientCurrentV1Artifact,
    [Parameter(Mandatory)][string]$PatientLaterHistoricalV1Artifact,
    [Parameter(Mandatory)][string]$PetClinicHistoricalB0Artifact,
    [Parameter(Mandatory)][string]$PetClinicCurrentB0Artifact,
    [Parameter(Mandatory)][string]$PetClinicHistoricalV1Artifact,
    [Parameter(Mandatory)][string]$PetClinicCurrentV1Artifact,
    [string]$ClaimRegister = 'docs/v2-claim-register.json',
    [string]$CurrentForensicMatrix = 'docs/product-evidence/b0-v1-v2-final-forensic-matrix.json',
    [string]$OutputDirectory = 'docs/product-evidence/historical-baseline-recovery'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-File([string]$Path) {
    $resolved = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) { throw "Expected file: $Path" }
    $resolved
}
function Resolve-Directory([string]$Path) {
    $resolved = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    if (-not (Test-Path -LiteralPath $resolved -PathType Container)) { throw "Expected directory: $Path" }
    $resolved
}
function Read-Json([string]$Path) {
    Get-Content -Raw -LiteralPath (Resolve-File $Path) | ConvertFrom-Json
}
function Get-Median([AllowEmptyCollection()][object[]]$Values) {
    $numbers = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ } | Sort-Object)
    if ($numbers.Count -eq 0) { return $null }
    if ($numbers.Count % 2 -eq 1) { return $numbers[[int][Math]::Floor($numbers.Count / 2)] }
    ($numbers[$numbers.Count / 2 - 1] + $numbers[$numbers.Count / 2]) / 2.0
}
function Get-Range([AllowEmptyCollection()][object[]]$Values) {
    $numbers = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ })
    if ($numbers.Count -eq 0) { return [ordered]@{ min = $null; max = $null } }
    [ordered]@{
        min = [double](($numbers | Measure-Object -Minimum).Minimum)
        max = [double](($numbers | Measure-Object -Maximum).Maximum)
    }
}
function Get-FileIdentity([string]$Path) {
    $resolved = Resolve-File $Path
    [ordered]@{
        sha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToUpperInvariant()
        bytes = [long](Get-Item -LiteralPath $resolved).Length
    }
}
function Get-ZipContentMap([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression
    $stream = [IO.File]::OpenRead((Resolve-File $Path))
    try {
        $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read, $false)
        try {
            $map = @{}
            foreach ($entry in $archive.Entries) {
                if ([string]::IsNullOrEmpty($entry.Name)) { continue }
                $entryStream = $entry.Open()
                try {
                    $sha = [Security.Cryptography.SHA256]::Create()
                    try { $hash = [Convert]::ToHexString($sha.ComputeHash($entryStream)) }
                    finally { $sha.Dispose() }
                } finally { $entryStream.Dispose() }
                $map[$entry.FullName] = [ordered]@{ sha256 = $hash; bytes = [long]$entry.Length }
            }
            $map
        } finally { $archive.Dispose() }
    } finally { $stream.Dispose() }
}
function Get-EntryCategory([string]$Name) {
    if ($Name -match '^BOOT-INF/classes/.+\$\$SpringCGLIB\$\$') { return 'APP_GENERATED_CGLIB_CLASS' }
    if ($Name -match '^BOOT-INF/classes/.+(__BeanDefinitions|__BeanFactoryRegistrations)') { return 'APP_GENERATED_AOT_CLASS' }
    if ($Name -match '^BOOT-INF/classes/.+\.class$') { return 'APP_CLASS' }
    if ($Name -match '^BOOT-INF/lib/') { return 'DEPENDENCY_ENTRY' }
    if ($Name -match '^META-INF/(maven|native-image)/') { return 'BUILD_METADATA' }
    if ($Name -match '^META-INF/') { return 'ARCHIVE_METADATA' }
    'OTHER'
}
function Compare-ZipContent([string]$HistoricalPath, [string]$CurrentPath) {
    $historical = Get-ZipContentMap $HistoricalPath
    $current = Get-ZipContentMap $CurrentPath
    $categories = @{}
    $differenceCount = 0
    foreach ($name in @($historical.Keys + $current.Keys | Sort-Object -Unique)) {
        $left = $historical[$name]
        $right = $current[$name]
        if ($null -eq $left -or $null -eq $right -or $left.sha256 -ne $right.sha256) {
            $differenceCount++
            $category = Get-EntryCategory $name
            if (-not $categories.ContainsKey($category)) { $categories[$category] = 0 }
            $categories[$category]++
        }
    }
    [ordered]@{
        historicalEntries = $historical.Count
        currentEntries = $current.Count
        differingEntries = $differenceCount
        categoryCounts = [ordered]@{} + $categories
        normalizedContentEqual = ($differenceCount -eq 0)
        privacyBoundary = 'Entry names are not emitted; only aggregate categories are public.'
    }
}
function Get-CurrentSessions([string]$CampaignRoot) {
    $index = Read-Json (Join-Path (Resolve-Directory $CampaignRoot) 'reports/session-index.json')
    @($index.blocks | ForEach-Object { $_.sessions })
}
function Convert-CurrentRun($Session) {
    [ordered]@{
        runId = [string]$Session.sessionId; variant = [string]$Session.variant
        pssKb = [double]$Session.pssKb; privateDirtyKb = [double]$Session.privateDirtyKb
        memoryCurrentBytes = [double]$Session.memoryCurrentBytes; heapPssKb = [double]$Session.heapPssKb
        heapUsedKb = $null; loadedClasses = [double]$Session.loadedClasses
        metaspaceUsedKb = [double]$Session.metaspaceUsedKb
        metaspaceCommittedKb = [double]$Session.metaspaceCommittedKb
        startupMillis = [double]$Session.startupMillis; captureAgeSeconds = $null
        artifactSha256 = [string]$Session.artifactSha256; imageId = [string]$Session.imageId
    }
}
function Convert-DoctorRun($Run, [ValidateSet('baseline', 'd2fixed')][string]$Variant) {
    $value = $Run.$Variant
    [ordered]@{
        runId = "doctor-historical-$($Run.run)-$Variant"
        variant = if ($Variant -eq 'baseline') { 'B0' } else { 'V1' }
        pssKb = [double]$value.pss_kb; privateDirtyKb = [double]$value.private_dirty_kb
        memoryCurrentBytes = [double]$value.cgroup_bytes; heapPssKb = $null; heapUsedKb = $null
        loadedClasses = $null; metaspaceUsedKb = $null; metaspaceCommittedKb = $null
        startupMillis = [double]$value.startupSec * 1000.0; captureAgeSeconds = $null
        artifactSha256 = $null; imageId = $null
    }
}
function Convert-PatientRun([string]$Path, [ValidateSet('B0', 'V1')][string]$Variant, [int]$Run) {
    $value = Read-Json $Path
    [ordered]@{
        runId = "patient-historical-$Run-$($Variant.ToLowerInvariant())"; variant = $Variant
        pssKb = [double]$value.smaps_pss_kb; privateDirtyKb = [double]$value.smaps_private_dirty_kb
        memoryCurrentBytes = [double]$value.memory_current_bytes; heapPssKb = $null; heapUsedKb = $null
        loadedClasses = [double]$value.nmt_classes; metaspaceUsedKb = $null
        metaspaceCommittedKb = [double]$value.nmt_metaspace_committed_kb
        startupMillis = [double]$value.startup_seconds * 1000.0; captureAgeSeconds = $null
        artifactSha256 = $null; imageId = $null
    }
}
function Convert-PetClinicRun($Sample) {
    [ordered]@{
        runId = "petclinic-historical-$($Sample.pair)-$($Sample.variant)"
        variant = if ($Sample.variant -eq 'baseline') { 'B0' } else { 'V1' }
        pssKb = [double]$Sample.smaps_pss_kb; privateDirtyKb = [double]$Sample.smaps_private_dirty_kb
        memoryCurrentBytes = [double]$Sample.memory_current_bytes
        heapPssKb = [double]$Sample.smaps_heap_pss_kb; heapUsedKb = [double]$Sample.heap_info_used_kb
        loadedClasses = [double]$Sample.loaded_classes; metaspaceUsedKb = $null; metaspaceCommittedKb = $null
        startupMillis = [double]$Sample.startup_seconds * 1000.0; captureAgeSeconds = $null
        artifactSha256 = $null; imageId = $null
    }
}
function Get-VariantSummary([object[]]$Runs, [string]$Variant) {
    $selected = @($Runs | Where-Object variant -eq $Variant)
    $metrics = [ordered]@{}
    foreach ($name in @('pssKb', 'privateDirtyKb', 'memoryCurrentBytes', 'heapPssKb', 'heapUsedKb',
            'loadedClasses', 'metaspaceUsedKb', 'metaspaceCommittedKb', 'startupMillis', 'captureAgeSeconds')) {
        $values = @($selected | ForEach-Object { $_[$name] })
        $range = Get-Range $values
        $metrics[$name] = [ordered]@{
            values = @($values | Where-Object { $null -ne $_ }); median = Get-Median $values
            min = $range.min; max = $range.max
        }
    }
    [ordered]@{ runCount = $selected.Count; metrics = $metrics }
}
function Get-PairedMedianDelta([object[]]$Runs, [string]$Metric) {
    $b0 = @($Runs | Where-Object variant -eq 'B0')
    $v1 = @($Runs | Where-Object variant -eq 'V1')
    $count = [Math]::Min($b0.Count, $v1.Count)
    if ($count -eq 0) { return $null }
    Get-Median @(for ($index = 0; $index -lt $count; $index++) {
        if ($null -ne $b0[$index][$Metric] -and $null -ne $v1[$index][$Metric]) {
            [double]$v1[$index][$Metric] - [double]$b0[$index][$Metric]
        }
    })
}
function Get-EvidenceDigest([string]$Path) {
    [ordered]@{
        logicalSource = Split-Path -Leaf $Path
        sha256 = (Get-FileHash -LiteralPath (Resolve-File $Path) -Algorithm SHA256).Hash.ToUpperInvariant()
    }
}
function Get-TextSha256([string]$Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { [Convert]::ToHexString($sha.ComputeHash($bytes)) }
    finally { $sha.Dispose() }
}
function Write-Json($Value, [string]$Path) {
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 30) + "`n"), [Text.UTF8Encoding]::new($false))
}
function Write-Text([string[]]$Lines, [string]$Path) {
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllText($Path, (($Lines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
}
function New-Diff([string]$Dimension, $Historical, $Current, [string]$Classification, [string]$Reason) {
    [ordered]@{ dimension = $Dimension; historical = $Historical; current = $Current; classification = $Classification; reason = $Reason }
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resolvedOutput = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else { [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory)) }
[IO.Directory]::CreateDirectory($resolvedOutput) | Out-Null

$doctorReport = Read-Json $DoctorHistoricalReport
$doctorRuns = @(foreach ($run in $doctorReport.runs) {
    Convert-DoctorRun $run baseline
    Convert-DoctorRun $run d2fixed
})
$patientRoot = Resolve-Directory $PatientHistoricalRoot
$patientRuns = @(
    Convert-PatientRun (Join-Path $patientRoot 'baseline-post-workload-real.json') B0 1
    Convert-PatientRun (Join-Path $patientRoot 'c2-post-workload.json') V1 1
    Convert-PatientRun (Join-Path $patientRoot 'p2-run2-baseline-post.json') B0 2
    Convert-PatientRun (Join-Path $patientRoot 'p2-run2-c2-post.json') V1 2
    Convert-PatientRun (Join-Path $patientRoot 'p2-run3-baseline-post.json') B0 3
    Convert-PatientRun (Join-Path $patientRoot 'p2-run3-c2-post.json') V1 3
)
$petclinicReport = Read-Json $PetClinicHistoricalReport
$petclinicRuns = @($petclinicReport.samples | ForEach-Object { Convert-PetClinicRun $_ })
$patientSourceHashes = @(
    'baseline-post-workload-real.json', 'c2-post-workload.json',
    'p2-run2-baseline-post.json', 'p2-run2-c2-post.json',
    'p2-run3-baseline-post.json', 'p2-run3-c2-post.json'
) | ForEach-Object {
    (Get-FileHash -LiteralPath (Join-Path $patientRoot $_) -Algorithm SHA256).Hash.ToUpperInvariant()
}
$patientSourceSetSha = Get-TextSha256 ([string]::Join(':', $patientSourceHashes))
$doctorCurrent = @(Get-CurrentSessions $DoctorCampaignRoot | ForEach-Object { Convert-CurrentRun $_ })
$patientCurrent = @(Get-CurrentSessions $PatientCampaignRoot | ForEach-Object { Convert-CurrentRun $_ })
$petclinicCurrent = @(Get-CurrentSessions $PetClinicCampaignRoot | ForEach-Object { Convert-CurrentRun $_ })

$doctorHistoricalB0 = Get-FileIdentity $DoctorHistoricalB0Artifact
$doctorCurrentB0 = Get-FileIdentity $DoctorCurrentB0Artifact
$doctorHistoricalV1 = Get-FileIdentity $DoctorHistoricalV1Artifact
$doctorCurrentV1 = Get-FileIdentity $DoctorCurrentV1Artifact
$patientCurrentV1 = Get-FileIdentity $PatientCurrentV1Artifact
$patientLaterHistoricalV1 = Get-FileIdentity $PatientLaterHistoricalV1Artifact
$petclinicHistoricalB0 = Get-FileIdentity $PetClinicHistoricalB0Artifact
$petclinicCurrentB0 = Get-FileIdentity $PetClinicCurrentB0Artifact
$petclinicHistoricalV1 = Get-FileIdentity $PetClinicHistoricalV1Artifact
$petclinicCurrentV1 = Get-FileIdentity $PetClinicCurrentV1Artifact

$recoveries = @(
    [ordered]@{
        id = 'doctor'; service = 'doctor-service'; historicalProtocol = 'PHASE_32K_I_3RUN_CORRECTED_D2'
        classification = 'HISTORICAL_ABSOLUTE_RUNS_RECOVERED'; runs = $doctorRuns
        source = Get-EvidenceDigest $DoctorHistoricalReport
        primaryDeltaConvention = 'INDEPENDENT_VARIANT_MEDIANS'
        sourceStated = [ordered]@{
            b0MedianPssKb = [double]$doctorReport.statistics.baselineMedianPSS
            v1MedianPssKb = [double]$doctorReport.statistics.d2fixedMedianPSS
            deltaMedianPssKb = [double]$doctorReport.statistics.deltaMedianPSS
        }
        auditFinding = 'HISTORICAL_REPORT_MEDIAN_INDEX_BUG'
    }
    [ordered]@{
        id = 'patient'; service = 'patient-service'; historicalProtocol = 'PHASE_31D_P2_3RUN'
        classification = 'HISTORICAL_ABSOLUTE_RUNS_RECOVERED'; runs = $patientRuns
        source = [ordered]@{
            logicalSource = 'phase31d per-run post-workload JSON set'
            sha256 = $patientSourceSetSha
        }
        primaryDeltaConvention = 'INDEPENDENT_VARIANT_MEDIANS'
        sourceStated = $null; auditFinding = 'RAW_RUNS_RECOVERED_ARTIFACT_IDENTITY_INCOMPLETE'
    }
    [ordered]@{
        id = 'petclinic'; service = 'spring-petclinic-customers-service'; historicalProtocol = 'PHASE_33M_EXPLODED_BOOT_NO_CDS'
        classification = 'HISTORICAL_ABSOLUTE_RUNS_RECOVERED'; runs = $petclinicRuns
        source = Get-EvidenceDigest $PetClinicHistoricalReport
        primaryDeltaConvention = 'PAIRED_DELTA_MEDIAN'
        sourceStated = $null; auditFinding = 'RAW_RUNS_AND_EXACT_CURRENT_V1_ARTIFACT_RECOVERED'
    }
)
foreach ($recovery in $recoveries) {
    $recovery.b0 = Get-VariantSummary $recovery.runs B0
    $recovery.v1 = Get-VariantSummary $recovery.runs V1
    $recovery.recomputed = [ordered]@{
        independentMedianDeltaPssKb = [double]$recovery.v1.metrics.pssKb.median - [double]$recovery.b0.metrics.pssKb.median
        pairedMedianDeltaPssKb = Get-PairedMedianDelta $recovery.runs pssKb
    }
    $recovery.historicalPrimaryMedianPssDeltaKb = if ($recovery.primaryDeltaConvention -eq 'PAIRED_DELTA_MEDIAN') {
        $recovery.recomputed.pairedMedianDeltaPssKb
    } else {
        $recovery.recomputed.independentMedianDeltaPssKb
    }
    $public = [ordered]@{
        schemaVersion = 'jmoa-historical-baseline-recovery-v1'; service = $recovery.service
        classification = $recovery.classification; historicalProtocol = $recovery.historicalProtocol
        runs = $recovery.runs; b0 = $recovery.b0; v1 = $recovery.v1; source = $recovery.source
        recomputed = $recovery.recomputed; primaryDeltaConvention = $recovery.primaryDeltaConvention
        historicalPrimaryMedianPssDeltaKb = $recovery.historicalPrimaryMedianPssDeltaKb
        auditFinding = $recovery.auditFinding
        privacyBoundary = 'No local paths, private class names, credentials, or raw service configuration are emitted.'
    }
    if ($null -ne $recovery.sourceStated) { $public.sourceStated = $recovery.sourceStated }
    Write-Json $public (Join-Path $resolvedOutput "$($recovery.id).json")
}

$doctorFreeze = Read-Json (Join-Path (Resolve-Directory $DoctorCampaignRoot) 'campaign-freeze.json')
$patientFreeze = Read-Json (Join-Path (Resolve-Directory $PatientCampaignRoot) 'campaign-freeze.json')
$services = @(
    [ordered]@{
        service = 'doctor-service'; historical = $recoveries[0]; currentRuns = $doctorCurrent
        historicalArtifact = $doctorHistoricalB0; currentArtifact = $doctorCurrentB0
        zip = Compare-ZipContent $DoctorHistoricalB0Artifact $DoctorCurrentB0Artifact
        decision = 'B0_SOURCE_OR_DEPENDENCY_MISMATCH'
        reason = 'JAR SHA and normalized application/generated content differ; application CDS archives also differ.'
    }
    [ordered]@{
        service = 'patient-service'; historical = $recoveries[1]; currentRuns = $patientCurrent
        historicalArtifact = $null
        currentArtifact = Get-FileIdentity (($patientFreeze.artifacts | Where-Object variant -eq B0).artifactPath)
        zip = $null; decision = 'B0_RUNTIME_POLICY_MISMATCH'
        reason = 'Historical per-candidate application CDS and long workload differ from the current base-CDS health workload; the exact historical B0 JAR was not recovered.'
    }
    [ordered]@{
        service = 'spring-petclinic-customers-service'; historical = $recoveries[2]; currentRuns = $petclinicCurrent
        historicalArtifact = $petclinicHistoricalB0; currentArtifact = $petclinicCurrentB0
        zip = Compare-ZipContent $PetClinicHistoricalB0Artifact $PetClinicCurrentB0Artifact
        decision = 'B0_SOURCE_OR_DEPENDENCY_MISMATCH'
        reason = 'JAR SHA and normalized content differ even though both protocols use exploded Boot no-CDS execution.'
    }
)
$absoluteRows = foreach ($service in $services) {
    $current = Get-VariantSummary $service.currentRuns B0
    [ordered]@{
        service = $service.service; recoveryClassification = $service.historical.classification
        historicalB0 = $service.historical.b0; currentB0 = $current
        artifactIdentity = [ordered]@{
            historical = $service.historicalArtifact; current = $service.currentArtifact
            normalizedJarComparison = $service.zip
        }
        pssMedianDeltaCurrentMinusHistoricalKb = [double]$current.metrics.pssKb.median - [double]$service.historical.b0.metrics.pssKb.median
        conclusion = $service.decision; reason = $service.reason
    }
}
$absolute = [ordered]@{
    schemaVersion = 'jmoa-historical-current-b0-absolute-v1'; comparison = 'HISTORICAL_B0_TO_CURRENT_B0'
    services = @($absoluteRows); campaignAuthorization = 'NO_PERFORMANCE_CAMPAIGN_STARTED'
}
Write-Json $absolute (Join-Path $resolvedOutput 'historical-vs-current-b0-absolute.json')
$lines = @(
    '# Historical Versus Current B0: Absolute Audit', '',
    'Read-only identity and absolute-footprint audit. It does not replace the sealed current matrix.', '',
    '| Service | Historical PSS KB | Current PSS KB | Current - historical KB | Artifact exact | Decision |',
    '|---|---:|---:|---:|---|---|'
)
foreach ($row in $absoluteRows) {
    $exact = if ($null -eq $row.artifactIdentity.historical) { 'NOT_RECOVERED' } else {
        [string]($row.artifactIdentity.historical.sha256 -eq $row.artifactIdentity.current.sha256)
    }
    $lines += "| $($row.service) | $($row.historicalB0.metrics.pssKb.median) | $($row.currentB0.metrics.pssKb.median) | $($row.pssMedianDeltaCurrentMinusHistoricalKb) | $exact | ``$($row.conclusion)`` |"
}
$lines += '', 'Absolute drift is descriptive only where source/runtime identity differs; it is not an optimizer delta.'
Write-Text $lines (Join-Path $resolvedOutput 'historical-vs-current-b0-absolute.md')

$runtimeDiffs = @(
    [ordered]@{ id = 'doctor'; service = 'doctor-service'; dimensions = @(
        (New-Diff 'B0 artifact SHA' $doctorHistoricalB0.sha256 $doctorCurrentB0.sha256 DISQUALIFYING 'Normalized application/generated content differs.'),
        (New-Diff 'V1 artifact SHA' $doctorHistoricalV1.sha256 $doctorCurrentV1.sha256 EXPECTED 'Exact JAR recovered.'),
        (New-Diff 'B0 CDS archive SHA' (Get-FileIdentity $DoctorHistoricalB0Cds).sha256 (($doctorFreeze.artifacts | Where-Object variant -eq B0).cdsArchiveSha256) POTENTIALLY_MEMORY_RELEVANT 'Application CDS is artifact/runtime specific.'),
        (New-Diff 'V1 CDS archive SHA' (Get-FileIdentity $DoctorHistoricalV1Cds).sha256 (($doctorFreeze.artifacts | Where-Object variant -eq V1).cdsArchiveSha256) POTENTIALLY_MEMORY_RELEVANT 'The historical and current V1 archives differ.'),
        (New-Diff 'Capture design' 'three paired runs' 'six balanced orders' EXPECTED 'Current design controls order more strongly.'),
        (New-Diff 'Five JDK identities' INCOMPLETE_HISTORICAL_RECORD CURRENT_RUNTIME_FINGERPRINTED UNKNOWN 'The historical five-way split was not frozen.')
    )}
    [ordered]@{ id = 'patient'; service = 'patient-service'; dimensions = @(
        (New-Diff 'B0 artifact SHA' NOT_RECOVERED (($patientFreeze.artifacts | Where-Object variant -eq B0).artifactSha256) UNKNOWN 'Raw runs exist but the historical B0 JAR was not recovered.'),
        (New-Diff 'V1 artifact SHA' $patientLaterHistoricalV1.sha256 $patientCurrentV1.sha256 POTENTIALLY_MEMORY_RELEVANT 'The nearest retained later candidate is not the current V1 or proven Phase 31D artifact.'),
        (New-Diff 'CDS policy' PER_CANDIDATE_APPLICATION_CDS JDK_BASE_CDS_LOW_DIRTY DISQUALIFYING 'CDS policy changes absolute and incremental memory.'),
        (New-Diff 'Workload' '300 operation pairs / 600 requests' 'frozen health-oriented workload' DISQUALIFYING 'Mechanism activation and retained state differ.'),
        (New-Diff 'Capture design' 'three paired runs' 'six balanced orders' EXPECTED 'Current design controls order more strongly.'),
        (New-Diff 'Five JDK identities' INCOMPLETE_HISTORICAL_RECORD CURRENT_RUNTIME_FINGERPRINTED UNKNOWN 'The historical five-way split was not frozen.')
    )}
    [ordered]@{ id = 'petclinic'; service = 'spring-petclinic-customers-service'; dimensions = @(
        (New-Diff 'B0 artifact SHA' $petclinicHistoricalB0.sha256 $petclinicCurrentB0.sha256 DISQUALIFYING 'Normalized JAR content differs.'),
        (New-Diff 'V1 artifact SHA' $petclinicHistoricalV1.sha256 $petclinicCurrentV1.sha256 EXPECTED 'Exact artifact recovered.'),
        (New-Diff 'Launch mode' EXPLODED_BOOT_APP EXPLODED_BOOT_APP EXPECTED 'Same deployment shape.'),
        (New-Diff 'CDS policy' NO_CDS_LOW_DIRTY NO_CDS_LOW_DIRTY EXPECTED 'Same high-level policy.'),
        (New-Diff 'Capture design' 'three paired runs' 'six balanced orders' EXPECTED 'Current design controls order more strongly.'),
        (New-Diff 'Five JDK identities' INCOMPLETE_HISTORICAL_RECORD CURRENT_RUNTIME_FINGERPRINTED UNKNOWN 'The historical five-way split was not frozen.')
    )}
)
foreach ($diff in $runtimeDiffs) {
    $document = [ordered]@{
        schemaVersion = 'jmoa-historical-current-runtime-diff-v1'; service = $diff.service
        conclusion = 'DISQUALIFYING_DIFFERENCES_PRESENT'; dimensions = $diff.dimensions
        privacyBoundary = 'Private paths, configuration values, and service internals are not emitted.'
    }
    Write-Json $document (Join-Path $resolvedOutput "$($diff.id)-historical-current-runtime-diff.json")
    $lines = @("# $($diff.service): Historical/Current Runtime Diff", '', '| Dimension | Historical | Current | Classification | Reason |', '|---|---|---|---|---|')
    foreach ($row in $diff.dimensions) {
        $lines += "| $($row.dimension) | $($row.historical) | $($row.current) | ``$($row.classification)`` | $($row.reason) |"
    }
    $lines += '', '**Conclusion:** `DISQUALIFYING_DIFFERENCES_PRESENT`'
    Write-Text $lines (Join-Path $resolvedOutput "$($diff.id)-historical-current-runtime-diff.md")
}

$v1Identity = [ordered]@{
    schemaVersion = 'jmoa-historical-current-v1-identity-v1'
    services = @(
        [ordered]@{
            service = 'doctor-service'; historicalArtifact = $doctorHistoricalV1; currentArtifact = $doctorCurrentV1
            artifactExact = ($doctorHistoricalV1.sha256 -eq $doctorCurrentV1.sha256)
            cdsArchiveExact = ((Get-FileIdentity $DoctorHistoricalV1Cds).sha256 -eq (($doctorFreeze.artifacts | Where-Object variant -eq V1).cdsArchiveSha256))
            conclusion = 'V1_EXACT_IDENTITY'; qualification = 'JAR exact; application CDS archive not exact, so runtime identity is not exact.'
        }
        [ordered]@{
            service = 'patient-service'; historicalArtifact = $null
            nearestLaterHistoricalArtifact = $patientLaterHistoricalV1; currentArtifact = $patientCurrentV1
            artifactExact = $false; conclusion = 'V1_IDENTITY_INCONCLUSIVE'
            qualification = 'Phase 31D candidate JAR not recovered; a later corrected candidate cannot substitute for it.'
        }
        [ordered]@{
            service = 'spring-petclinic-customers-service'; historicalArtifact = $petclinicHistoricalV1; currentArtifact = $petclinicCurrentV1
            artifactExact = ($petclinicHistoricalV1.sha256 -eq $petclinicCurrentV1.sha256)
            conclusion = 'V1_EXACT_IDENTITY'; qualification = 'Historical Phase 33M full-P2 JAR is the exact finalized current V1 artifact.'
        }
    )
    unprovenDimensions = @('historical profile SHA where absent', 'historical admission-set SHA where absent',
        'historical plugin/runtime-library identity where absent', 'historical five-way JDK identity')
}
Write-Json $v1Identity (Join-Path $resolvedOutput 'historical-vs-current-v1-identity.json')
$lines = @('# Historical Versus Current V1 Identity', '', '| Service | Artifact exact | Decision | Qualification |', '|---|---|---|---|')
foreach ($row in $v1Identity.services) { $lines += "| $($row.service) | $($row.artifactExact) | ``$($row.conclusion)`` | $($row.qualification) |" }
Write-Text $lines (Join-Path $resolvedOutput 'historical-vs-current-v1-identity.md')

$claimPath = if ([IO.Path]::IsPathRooted($ClaimRegister)) { $ClaimRegister } else { Join-Path $repositoryRoot $ClaimRegister }
$matrixPath = if ([IO.Path]::IsPathRooted($CurrentForensicMatrix)) { $CurrentForensicMatrix } else { Join-Path $repositoryRoot $CurrentForensicMatrix }
$claims = Read-Json $claimPath
$matrix = Read-Json $matrixPath
$incremental = @{}
foreach ($row in $claims.threeServiceAcceptance.services) {
    $key = if ($row.service -like 'Doctor*') { 'doctor-service' } elseif ($row.service -like 'Patient*') { 'patient-service' } else { 'spring-petclinic-customers-service' }
    $incremental[$key] = [double]$row.medianPssDeltaKb
}
$budgetRows = foreach ($recovery in $recoveries) {
    $historicalB0V1 = [double]$recovery.historicalPrimaryMedianPssDeltaKb
    $historicalV1V2 = [double]$incremental[$recovery.service]
    $current = @($matrix.services | Where-Object service -eq $recovery.service)[0]
    $expected = $historicalB0V1 + $historicalV1V2
    [ordered]@{
        service = $recovery.service
        historicalB0ToV1PrimaryConvention = $recovery.primaryDeltaConvention
        historicalB0ToV1RecomputedMedianPssKb = $historicalB0V1
        historicalV1ToV2ProtocolScopedMedianPssKb = $historicalV1V2
        historicalExpectedEngineeringBudgetPssKb = $expected
        currentDirectB0ToV2MedianPssKb = [double]$current.b0ToV2PssMedianKb
        currentMinusHistoricalBudgetGapKb = [double]$current.b0ToV2PssMedianKb - $expected
        authoritative = $false; comparability = 'CROSS_PROTOCOL_DIRECTIONAL_BUDGET_ONLY'
    }
}
$budget = [ordered]@{
    schemaVersion = 'jmoa-historical-expected-engineering-budget-v1'; label = 'HISTORICAL_EXPECTED_ENGINEERING_BUDGET'
    services = @($budgetRows)
    warning = 'Medians from separate campaigns are not additive. This is a directional engineering budget, never an authoritative product delta.'
}
Write-Json $budget (Join-Path $resolvedOutput 'historical-expected-engineering-budget.json')
$lines = @('# Historical Expected Engineering Budget', '', '| Service | Historical B0->V1 KB | Historical V1->V2 KB | Directional sum KB | Current direct KB | Gap KB |', '|---|---:|---:|---:|---:|---:|')
foreach ($row in $budgetRows) {
    $lines += "| $($row.service) | $($row.historicalB0ToV1RecomputedMedianPssKb) | $($row.historicalV1ToV2ProtocolScopedMedianPssKb) | $($row.historicalExpectedEngineeringBudgetPssKb) | $($row.currentDirectB0ToV2MedianPssKb) | $($row.currentMinusHistoricalBudgetGapKb) |"
}
$lines += '', $budget.warning
Write-Text $lines (Join-Path $resolvedOutput 'historical-expected-engineering-budget.md')

$decisions = [ordered]@{
    schemaVersion = 'jmoa-baseline-acceptance-decision-v1'; currentMatrixPreserved = $true
    performanceCampaignStarted = $false
    services = @(
        [ordered]@{
            service = 'doctor-service'; decision = 'REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR'
            provenDefect = 'B0_SOURCE_OR_DEPENDENCY_MISMATCH'; correctedMeasurementAuthorization = 'AUTHORIZED_AFTER_NEW_FREEZE'
            boundedScope = 'One balanced Doctor campaign with a source/dependency-equivalent B0, exact V1 JAR, fresh artifact-specific CDS archives, and frozen workload.'
        }
        [ordered]@{
            service = 'patient-service'; decision = 'B0_COMPARISON_INCONCLUSIVE'
            provenDefect = 'B0_RUNTIME_POLICY_MISMATCH'; correctedMeasurementAuthorization = 'NOT_AUTHORIZED'
            boundedScope = 'Recover/rebuild the historical source universe and choose one common runtime policy first.'
        }
        [ordered]@{
            service = 'spring-petclinic-customers-service'; decision = 'REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR'
            provenDefect = 'B0_SOURCE_OR_DEPENDENCY_MISMATCH'; correctedMeasurementAuthorization = 'AUTHORIZED_AFTER_NEW_FREEZE'
            boundedScope = 'One balanced public campaign with historical-equivalent B0 and exact V1 under frozen no-CDS exploded Boot.'
        }
    )
    globalDecision = 'NO_IMMEDIATE_RUN'
    reason = 'Conditional authorization requires a new artifact/runtime/workload/JDK freeze and complete command ledgers.'
}
Write-Json $decisions (Join-Path $resolvedOutput 'baseline-acceptance-decision.json')
Write-Text @(
    '# Baseline Acceptance Decision', '',
    '| Service | Decision | Proven issue | Corrected measurement |', '|---|---|---|---|',
    '| doctor-service | `REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR` | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` | `AUTHORIZED_AFTER_NEW_FREEZE` |',
    '| patient-service | `B0_COMPARISON_INCONCLUSIVE` | `B0_RUNTIME_POLICY_MISMATCH` | `NOT_AUTHORIZED` |',
    '| spring-petclinic-customers-service | `REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR` | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` | `AUTHORIZED_AFTER_NEW_FREEZE` |',
    '', 'No run was started. Conditional authorization is not permission to reuse an old image, archive, or workload.'
) (Join-Path $resolvedOutput 'baseline-acceptance-decision.md')

$activation = [ordered]@{
    schemaVersion = 'jmoa-mechanism-activation-study-contract-v1'
    status = 'BLOCKED_PENDING_COMPARATOR_RECONCILIATION'; evidenceClass = 'DIAGNOSTIC_ONLY_NOT_PRODUCT_PERFORMANCE'
    requiredCounters = @('siteId', 'declaringClassId', 'methodId', 'classLoaded', 'methodReached',
        'transformedBranchExecuted', 'adapterCreated', 'adapterInvocationCount', 'fallbackCount',
        'originalAllocationAvoidedCount')
    hardRules = @('Counters are disabled in product performance campaigns.',
        'Diagnostic captures cannot update the B0/V1/V2 claim matrix.',
        'Private class/method names are hashed before publication.',
        'Activation is not inferred from histograms.',
        'The study starts only after B0 and V1 identity are accepted.')
}
Write-Json $activation (Join-Path $resolvedOutput 'mechanism-activation-study-contract.json')
Write-Text @(
    '# Mechanism Activation Study Contract', '',
    '**Status:** `BLOCKED_PENDING_COMPARATOR_RECONCILIATION`', '',
    'This diagnostic-only contract does not enable counters, alter the optimizer, or authorize a performance run.', '',
    'Counter-enabled runs are perturbing evidence and can never update the product memory matrix.'
) (Join-Path $resolvedOutput 'mechanism-activation-study-contract.md')

[ordered]@{
    outputDirectory = $resolvedOutput; recoveredServices = @($recoveries | ForEach-Object service)
    decisions = @($decisions.services | ForEach-Object {
        [ordered]@{ service = $_.service; decision = $_.decision; authorization = $_.correctedMeasurementAuthorization }
    })
    performanceCampaignStarted = $false
} | ConvertTo-Json -Depth 6

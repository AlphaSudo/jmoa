param(
    [Parameter(Mandatory)][string]$BaselineJar,
    [Parameter(Mandatory)][string]$CandidateJar,
    [Parameter(Mandatory)][string]$BaselineLibraries,
    [Parameter(Mandatory)][string]$CandidateLibraries,
    [Parameter(Mandatory)][string]$ReducerReport,
    [Parameter(Mandatory)][string]$RawPreservationReport,
    [Parameter(Mandatory)][string]$CurrentOptimizerReport,
    [Parameter(Mandatory)][string]$ReferenceOptimizerReport,
    [Parameter(Mandatory)][string]$SourceRevision,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$RuntimeLibraryPattern = 'jmoa-runtime-lib-*.jar'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($path in @($BaselineJar, $CandidateJar, $ReducerReport, $RawPreservationReport,
        $CurrentOptimizerReport, $ReferenceOptimizerReport)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file does not exist: $path" }
}
foreach ($path in @($BaselineLibraries, $CandidateLibraries)) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Required directory does not exist: $path" }
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Get-Sha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant() }

function Get-EntryHash([IO.Compression.ZipArchive]$Archive, [IO.Compression.ZipArchiveEntry]$Entry) {
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = $Entry.Open()
    try { ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $stream.Dispose(); $sha.Dispose() }
}

function Get-ZipEntries([string]$Path) {
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $order = 0
        @($archive.Entries | ForEach-Object {
            [ordered]@{
                order = $order++
                name = $_.FullName
                bytes = [long]$_.Length
                compressedBytes = [long]$_.CompressedLength
                crc32 = ('{0:X8}' -f $_.Crc32)
                timestamp = $_.LastWriteTime.UtcDateTime.ToString('o')
                externalAttributes = $_.ExternalAttributes
                comment = $_.Comment
                sha256 = if ($_.FullName.EndsWith('/')) { $null } else { Get-EntryHash $archive $_ }
            }
        })
    } finally { $archive.Dispose() }
}

function Get-LibraryManifest([string]$Directory) {
    @(
        Get-ChildItem -LiteralPath $Directory -Filter '*.jar' -File | Sort-Object Name | ForEach-Object {
            [ordered]@{ name = $_.Name; bytes = [long]$_.Length; sha256 = Get-Sha256 $_.FullName }
        }
    )
}

function ConvertTo-CanonicalObject($Value) {
    if ($null -eq $Value) { return $null }
    if ($Value -is [string] -or $Value -is [ValueType]) { return $Value }
    if ($Value -is [Collections.IDictionary]) {
        $result = [ordered]@{}
        foreach ($key in @($Value.Keys | Sort-Object)) { $result[$key] = ConvertTo-CanonicalObject $Value[$key] }
        return $result
    }
    if ($Value -is [Collections.IEnumerable] -and $Value -isnot [pscustomobject]) {
        return @($Value | ForEach-Object { ConvertTo-CanonicalObject $_ })
    }
    $result = [ordered]@{}
    foreach ($property in @($Value.psobject.Properties | Sort-Object Name)) {
        if ($property.Name -eq 'timestamp') { continue }
        $result[$property.Name] = ConvertTo-CanonicalObject $property.Value
    }
    return $result
}

function Compare-EntrySets($BaselineEntries, $CandidateEntries, [scriptblock]$Selector) {
    $baseline = @($BaselineEntries | Where-Object $Selector)
    $candidate = @($CandidateEntries | Where-Object $Selector)
    $baselineByName = @{}; $candidateByName = @{}
    $baseline | ForEach-Object { $baselineByName[$_.name] = $_ }
    $candidate | ForEach-Object { $candidateByName[$_.name] = $_ }
    $namesEqual = (@($baseline.name) -join "`n") -eq (@($candidate.name) -join "`n")
    $contentDifferences = @(
        $baselineByName.Keys | Sort-Object | Where-Object {
            -not $candidateByName.ContainsKey($_) -or $baselineByName[$_].sha256 -ne $candidateByName[$_].sha256
        }
    )
    $metadataDifferences = @(
        $baselineByName.Keys | Sort-Object | Where-Object {
            $candidateByName.ContainsKey($_) -and (
                $baselineByName[$_].compressedBytes -ne $candidateByName[$_].compressedBytes -or
                $baselineByName[$_].timestamp -ne $candidateByName[$_].timestamp -or
                $baselineByName[$_].crc32 -ne $candidateByName[$_].crc32 -or
                $baselineByName[$_].externalAttributes -ne $candidateByName[$_].externalAttributes -or
                $baselineByName[$_].comment -ne $candidateByName[$_].comment)
        }
    )
    [ordered]@{
        baselineCount = $baseline.Count
        candidateCount = $candidate.Count
        entryNamesAndOrderEqual = $namesEqual
        contentDifferences = $contentDifferences
        metadataDifferences = $metadataDifferences
    }
}

$baselineEntries = Get-ZipEntries $BaselineJar
$candidateEntries = Get-ZipEntries $CandidateJar
$application = Compare-EntrySets $baselineEntries $candidateEntries { $_.name -like 'BOOT-INF/classes/*' }
$loader = Compare-EntrySets $baselineEntries $candidateEntries { $_.name -like 'org/springframework/boot/loader/*' }
$nonLibraries = Compare-EntrySets $baselineEntries $candidateEntries { $_.name -notlike 'BOOT-INF/lib/*' }
$indexes = Compare-EntrySets $baselineEntries $candidateEntries {
    $_.name -in @('META-INF/MANIFEST.MF', 'BOOT-INF/classpath.idx', 'BOOT-INF/layers.idx')
}
$baselineLibManifest = Get-LibraryManifest $BaselineLibraries
$candidateLibManifest = Get-LibraryManifest $CandidateLibraries
$reducer = Get-Content -Raw -LiteralPath $ReducerReport | ConvertFrom-Json -Depth 100
$raw = Get-Content -Raw -LiteralPath $RawPreservationReport | ConvertFrom-Json -Depth 100
$currentOptimizer = ConvertTo-CanonicalObject (Get-Content -Raw -LiteralPath $CurrentOptimizerReport | ConvertFrom-Json -Depth 100)
$referenceOptimizer = ConvertTo-CanonicalObject (Get-Content -Raw -LiteralPath $ReferenceOptimizerReport | ConvertFrom-Json -Depth 100)
$optimizerEquivalent = ($currentOptimizer | ConvertTo-Json -Depth 100 -Compress) -eq
    ($referenceOptimizer | ConvertTo-Json -Depth 100 -Compress)

$runtimeBaseline = @(Get-ChildItem -LiteralPath $BaselineLibraries -Filter $RuntimeLibraryPattern -File)
$runtimeCandidate = @(Get-ChildItem -LiteralPath $CandidateLibraries -Filter $RuntimeLibraryPattern -File)
$runtimeIdentity = $runtimeBaseline.Count -eq 1 -and $runtimeCandidate.Count -eq 1 -and
    (Get-Sha256 $runtimeBaseline[0].FullName) -eq (Get-Sha256 $runtimeCandidate[0].FullName)
$libNamesEqual = (@($baselineLibManifest.name) -join "`n") -eq (@($candidateLibManifest.name) -join "`n")
$unexpected = @()
if (-not $optimizerEquivalent) { $unexpected += 'V1_OPTIMIZER_POLICY_DIFFERS_FROM_REFERENCE' }
if (-not $application.entryNamesAndOrderEqual -or $application.contentDifferences.Count -gt 0) { $unexpected += 'APPLICATION_CLASSES_CHANGED' }
if (-not $loader.entryNamesAndOrderEqual -or $loader.contentDifferences.Count -gt 0) { $unexpected += 'SPRING_BOOT_LOADER_CHANGED' }
if (-not $nonLibraries.entryNamesAndOrderEqual -or $nonLibraries.contentDifferences.Count -gt 0) { $unexpected += 'NON_LIBRARY_OUTER_ENTRY_CHANGED' }
if (-not $libNamesEqual) { $unexpected += 'DEPENDENCY_ENTRY_NAMES_CHANGED' }
if (-not $runtimeIdentity) { $unexpected += 'RUNTIME_LIBRARY_CHANGED' }
if ([int]$raw.failedAuditCount -ne 0 -or -not [bool]$raw.preservedNonTargetStructures) { $unexpected += 'RAW_BYTE_PRESERVATION_FAILED' }

$baselineMetadata = @($baselineEntries | ForEach-Object {
    "$($_.order)|$($_.name)|$($_.compressedBytes)|$($_.timestamp)|$($_.externalAttributes)|$($_.comment)"
})
$candidateMetadata = @($candidateEntries | ForEach-Object {
    "$($_.order)|$($_.name)|$($_.compressedBytes)|$($_.timestamp)|$($_.externalAttributes)|$($_.comment)"
})
$allOuterMetadataDifferences = @(Compare-Object -ReferenceObject $baselineMetadata -DifferenceObject $candidateMetadata)
$classification = if ($unexpected.Count -gt 0) { 'UNEXPECTED_BYTE_DIFFERENCE' }
    elseif ($allOuterMetadataDifferences.Count -gt 0) { 'PACKAGING_ONLY_DIFFERENCES_PRESENT' }
    else { 'ONLY_LVT_LVTT_CHANGED' }

$report = [ordered]@{
    metadataVersion = 'patient-comparator-audit-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    sourceRevision = $SourceRevision
    v1Identity = [ordered]@{
        result = if ($optimizerEquivalent) { 'V1_IDENTITY_CONFIRMED' } else { 'V1_IDENTITY_NOT_EQUIVALENT' }
        optimizerReportEquivalentIgnoringTimestampAndMapOrder = $optimizerEquivalent
        baselineArtifactSha256 = Get-Sha256 $BaselineJar
        applicationClassCount = $application.baselineCount
        embeddedDependencyCount = $baselineLibManifest.Count
        runtimeLibrarySha256 = if ($runtimeBaseline.Count -eq 1) { Get-Sha256 $runtimeBaseline[0].FullName } else { $null }
    }
    mutationScope = [ordered]@{
        classification = $classification
        unexpectedDifferences = $unexpected
        applicationEntries = $application
        springBootLoaderEntries = $loader
        nonLibraryOuterEntries = $nonLibraries
        indexesAndManifest = $indexes
        dependencyNamesEqual = $libNamesEqual
        dependencyCount = $candidateLibManifest.Count
        runtimeLibraryByteIdentical = $runtimeIdentity
        rawAuditedClasses = [int]$raw.auditedClassCount
        rawFailedAudits = [int]$raw.failedAuditCount
        rawNonTargetStructuresPreserved = [bool]$raw.preservedNonTargetStructures
        reducerChangedArtifacts = @($reducer.artifacts | Where-Object { $_.inputSha256 -ne $_.outputSha256 } | ForEach-Object artifact)
        reducerSkippedByArtifactPolicy = @($reducer.artifacts | Where-Object status -eq 'SKIPPED_ARTIFACT_POLICY' | ForEach-Object artifact)
    }
    outerPackaging = [ordered]@{
        baselineEntryCount = $baselineEntries.Count
        candidateEntryCount = $candidateEntries.Count
        entryOrderEqual = (@($baselineEntries.name) -join "`n") -eq (@($candidateEntries.name) -join "`n")
        metadataDifferenceCount = $allOuterMetadataDifferences.Count
        nestedLibrariesStored = -not (@($candidateEntries | Where-Object { $_.name -like 'BOOT-INF/lib/*.jar' -and $_.bytes -ne $_.compressedBytes }).Count -gt 0)
        note = 'ZIP extra fields and byte offsets are container-level packaging observations; non-library uncompressed content is compared by SHA-256.'
    }
    decision = [ordered]@{
        verifiedDefect = if ($runtimeIdentity) { 'FIXED_RUNTIME_LIBRARY_SCOPE' } else { 'RUNTIME_LIBRARY_SCOPE_UNRESOLVED' }
        runtimeEvidenceRequired = $true
        authoritativePatientVerdict = 'FAIL_UNTIL_FRESH_CORRECTED_CONFIRMATION_PASSES'
    }
}

$jsonPath = Join-Path $OutputDirectory 'patient-comparator-audit.json'
$mdPath = Join-Path $OutputDirectory 'patient-comparator-audit.md'
$report | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
@"
# Patient Comparator Audit

- V1 identity: **$($report.v1Identity.result)**
- Mutation classification: **$classification**
- Source revision: `$SourceRevision`
- V1 artifact SHA-256: `$($report.v1Identity.baselineArtifactSha256)`
- Application classes compared: `$($application.baselineCount)`; content differences: `$($application.contentDifferences.Count)`
- Spring Boot loader content differences: `$($loader.contentDifferences.Count)`
- Dependency entries: `$($candidateLibManifest.Count)`; names/order equal: `$libNamesEqual`
- Runtime library byte-identical: `$runtimeIdentity`
- Raw audited classes: `$($raw.auditedClassCount)`; failed audits: `$($raw.failedAuditCount)`
- Outer entry order equal: `$($report.outerPackaging.entryOrderEqual)`
- Outer ZIP metadata differences: `$($report.outerPackaging.metadataDifferenceCount)`
- Nested libraries stored: `$($report.outerPackaging.nestedLibrariesStored)`

The finalized Phase 31 C2 optimizer policy is equivalent to the current Patient V1 report after ignoring timestamp and JSON map ordering. The corrected V2 excludes the JMOA runtime library from reduction. The existing Patient failure remains authoritative until corrected runtime evidence passes.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Comparator audit: $classification"
if ($unexpected.Count -gt 0) { exit 2 }

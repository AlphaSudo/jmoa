param(
    [Parameter(Mandatory)][string]$SearchRootList,
    [Parameter(Mandatory)][string]$ImageInventoryJson,
    [Parameter(Mandatory)][string]$HistoricalProtocolInventoryJson,
    [Parameter(Mandatory)][string]$CurrentArtifactLineageJson,
    [Parameter(Mandatory)][string]$PrivateOutputDirectory,
    [Parameter(Mandatory)][string]$PublicOutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$searchRoots = @($SearchRootList -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($searchRoots.Count -eq 0) { throw 'SearchRootList did not contain any roots.' }

foreach ($path in @($ImageInventoryJson, $HistoricalProtocolInventoryJson, $CurrentArtifactLineageJson)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required inventory does not exist: $path" }
}

function Get-TextSha256([string]$Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    [BitConverter]::ToString([Security.Cryptography.SHA256]::HashData($bytes)).Replace('-','')
}
function Get-Manifest([string]$JarPath) {
    try {
        $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
        try {
            $entry = $archive.Entries | Where-Object FullName -eq 'META-INF/MANIFEST.MF' | Select-Object -First 1
            if ($null -eq $entry) { return [ordered]@{ entryCount = $archive.Entries.Count; manifest = @{}; jmoaEntries = 0 } }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $values = [ordered]@{}
            foreach ($line in ($text -split '\r?\n')) {
                if ($line -match '^([^:]+):\s*(.*)$') { $values[$matches[1]] = $matches[2] }
            }
            [ordered]@{
                entryCount = $archive.Entries.Count
                manifest = $values
                jmoaEntries = @($archive.Entries | Where-Object { $_.FullName -match '(?i)jmoa' }).Count
            }
        } finally {
            $archive.Dispose()
        }
    } catch {
        [ordered]@{ entryCount = 0; manifest = @{}; jmoaEntries = 0; error = $_.Exception.Message }
    }
}

$knownLineage = Get-Content -Raw -LiteralPath $CurrentArtifactLineageJson | ConvertFrom-Json
$knownHashes = @{}
foreach ($variant in @($knownLineage.variants)) {
    if ($null -ne $variant.artifact -and -not [string]::IsNullOrWhiteSpace([string]$variant.artifact.sha256)) {
        $knownHashes[([string]$variant.artifact.sha256).ToUpperInvariant()] = [string]$variant.id
    }
}

$candidatePaths = New-Object System.Collections.Generic.HashSet[string]([StringComparer]::OrdinalIgnoreCase)
foreach ($root in $searchRoots) {
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
    try {
        foreach ($path in [IO.Directory]::EnumerateFiles($root, '*patient*.jar', [IO.SearchOption]::AllDirectories)) {
            [void]$candidatePaths.Add([IO.Path]::GetFullPath($path))
        }
        foreach ($path in [IO.Directory]::EnumerateFiles($root, '*.jar', [IO.SearchOption]::AllDirectories)) {
            if ($path -match '(?i)(\\|/)phase31([^0-9]|$)') { [void]$candidatePaths.Add([IO.Path]::GetFullPath($path)) }
        }
    } catch {
        # An inaccessible subtree is retained in the private report without exposing it publicly.
    }
}

$privateCandidates = @($candidatePaths | Sort-Object | ForEach-Object {
    $item = Get-Item -LiteralPath $_
    $sha = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToUpperInvariant()
    $jar = Get-Manifest $_
    $manifest = $jar.manifest
    $title = if ($manifest.Contains('Implementation-Title')) { [string]$manifest['Implementation-Title'] } else { '' }
    $startClass = if ($manifest.Contains('Start-Class')) { [string]$manifest['Start-Class'] } else { '' }
    $bootVersion = if ($manifest.Contains('Spring-Boot-Version')) { [string]$manifest['Spring-Boot-Version'] } else { '' }
    $buildJdk = if ($manifest.Contains('Build-Jdk-Spec')) { [string]$manifest['Build-Jdk-Spec'] } else { '' }
    $knownIdentity = if ($knownHashes.ContainsKey($sha)) { $knownHashes[$sha] } else { '' }
    $deployablePatient = $title -match '(?i)patient' -or $startClass -match '(?i)patient'
    $classification = if (-not $deployablePatient) {
        'NOT_PATIENT_DEPLOYABLE_ARTIFACT'
    } elseif (-not [string]::IsNullOrWhiteSpace($knownIdentity)) {
        'KNOWN_CURRENT_OR_LATER_ARTIFACT'
    } elseif ($bootVersion -match '^3\.1(\.|$)' -and $buildJdk -eq '17') {
        'INCOMPATIBLE_SERVICE_UNIVERSE'
    } else {
        'UNVERIFIED_PATIENT_CANDIDATE'
    }
    [ordered]@{
        path = $item.FullName
        pathSha256 = Get-TextSha256 $item.FullName
        fileName = $item.Name
        sha256 = $sha
        bytes = [long]$item.Length
        lastWriteUtc = $item.LastWriteTimeUtc.ToString('o')
        entryCount = [int]$jar.entryCount
        springBootVersion = $bootVersion
        buildJdkSpec = $buildJdk
        jmoaEntryCount = [int]$jar.jmoaEntries
        knownLineageIdentity = $knownIdentity
        classification = $classification
    }
})

$images = @(Get-Content -Raw -LiteralPath $ImageInventoryJson | ConvertFrom-Json)
$patientImages = @($images | Where-Object {
    $imageNames = @()
    foreach ($propertyName in @('Names', 'RepoTags', 'RepoDigests', 'History')) {
        $property = $_.PSObject.Properties[$propertyName]
        if ($null -ne $property -and $null -ne $property.Value) {
            $imageNames += @($property.Value)
        }
    }
    ($imageNames -join ' ') -match '(?i)patient|phase31'
})
$historicalProtocol = Get-Content -Raw -LiteralPath $HistoricalProtocolInventoryJson | ConvertFrom-Json
$publicCandidates = @($privateCandidates | ForEach-Object {
    [ordered]@{
        pathSha256 = $_.pathSha256
        sha256 = $_.sha256
        bytes = $_.bytes
        lastWriteUtc = $_.lastWriteUtc
        entryCount = $_.entryCount
        springBootVersion = $_.springBootVersion
        buildJdkSpec = $_.buildJdkSpec
        jmoaEntryCount = $_.jmoaEntryCount
        knownLineageIdentity = $_.knownLineageIdentity
        classification = $_.classification
    }
})
$unverified = @($privateCandidates | Where-Object classification -eq 'UNVERIFIED_PATIENT_CANDIDATE')
$historicalArtifactRecovered = $false
$decision = 'PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE'

$report = [ordered]@{
    schemaVersion = 'jmoa-patient-historical-comparator-recovery-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    decision = $decision
    search = [ordered]@{
        rootsSearched = @($searchRoots | ForEach-Object { Get-TextSha256 ([IO.Path]::GetFullPath($_)) })
        jarCandidatesInspected = $privateCandidates.Count
        localPatientImageCandidates = $patientImages.Count
        fullPathsPublished = $false
    }
    candidates = $publicCandidates
    tuple = [ordered]@{
        historicalB0ArtifactSha256 = $null
        historicalB0ArtifactRecovered = $historicalArtifactRecovered
        historicalB0SourceRevisionRecovered = $false
        historicalRuntimePolicyRecovered = $true
        historicalStockCdsArchiveRecovered = [bool](@($historicalProtocol.artifacts | Where-Object logicalPath -match 'baseline.*\.jsa').Count -gt 0)
        supportAndConfigIdentityRecovered = $false
        workloadIdentityRecovered = $true
        captureTimingRecovered = $true
        historicalAbsoluteRunVectorsRecovered = $true
    }
    missingEssentials = @(
        'historical B0 artifact identity',
        'historical B0 source revision',
        'historical support/config identity'
    )
    authorization = [ordered]@{
        patientPerformanceRunAllowed = $false
        reason = 'The complete historical comparator tuple is unavailable. Later/current artifacts and incompatible service-universe JARs cannot substitute for the missing historical B0.'
    }
    currentAuthoritativeResult = [ordered]@{
        b0ToV2MedianPssDeltaKb = -1266.5
        pairedWins = '4/6'
        confidenceIntervalCrossesZero = $true
        verdict = 'NOT_CONFIRMED_SUBSTANTIAL_PRODUCT_WIN'
    }
}

New-JmoaDirectory -Path $PrivateOutputDirectory
New-JmoaDirectory -Path $PublicOutputDirectory
Write-JmoaJson -Value ([ordered]@{
    schemaVersion = 'jmoa-private-patient-comparator-candidate-inventory-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    candidates = $privateCandidates
    patientImages = $patientImages
}) -Path (Join-Path $PrivateOutputDirectory 'patient-historical-comparator-candidates.private.json')
Write-JmoaJson -Value $report -Path (Join-Path $PublicOutputDirectory 'patient-historical-comparator-recovery.json')
@"
# Patient Historical Comparator Recovery

- Decision: **$decision**
- JAR candidates inspected: **$($privateCandidates.Count)**
- Local Patient/Phase 31 image candidates: **$($patientImages.Count)**
- Unverified deployable Patient candidates: **$($unverified.Count)**
- Historical B0 artifact recovered: **False**
- Historical B0 source revision recovered: **False**

The search found later/current Patient artifacts, optimizer staging JARs, and an incompatible
Spring Boot 3.1 / JDK 17 service artifact. None establishes the exact historical Phase 31 B0
identity. The historical runtime scripts, workload, capture timing, absolute vectors, and a stock
CDS archive survive, but the B0 artifact/source and support/config identity do not.

No Patient performance run is authorized. The current corrected B0-to-V2 result remains
authoritative: **-1,266.5 KB median PSS, 4/6 wins, confidence interval crossing zero**.
"@ | Set-Content -LiteralPath (Join-Path $PublicOutputDirectory 'patient-historical-comparator-recovery.md') -Encoding UTF8

Write-Host "Decision: $decision"
Write-Host "Candidates inspected: $($privateCandidates.Count)"

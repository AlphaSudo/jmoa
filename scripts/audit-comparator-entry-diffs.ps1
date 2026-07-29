param(
    [Parameter(Mandatory)][string]$HistoricalArtifact,
    [Parameter(Mandatory)][string]$CurrentArtifact,
    [Parameter(Mandatory)][ValidateSet('doctor','petclinic','patient')][string]$Service,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$Javap = 'javap'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($path in @($HistoricalArtifact, $CurrentArtifact)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required artifact does not exist: $path"
    }
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Get-Sha256Bytes([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '') }
    finally { $sha.Dispose() }
}

function Get-Sha256Text([string]$Value) {
    Get-Sha256Bytes ([Text.Encoding]::UTF8.GetBytes($Value))
}

function Get-ZipEntryMap([string]$Path) {
    $map = @{}
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            $memory = [IO.MemoryStream]::new()
            $stream = $entry.Open()
            try {
                $stream.CopyTo($memory)
                $bytes = $memory.ToArray()
            } finally {
                $stream.Dispose()
                $memory.Dispose()
            }
            $map[$entry.FullName] = [ordered]@{
                name = $entry.FullName
                bytes = [long]$entry.Length
                sha256 = Get-Sha256Bytes $bytes
                content = $bytes
            }
        }
    } finally {
        $archive.Dispose()
    }
    return $map
}

function Get-EntryCategory([string]$Name) {
    if ($Name -like 'BOOT-INF/lib/*.jar') { return 'DEPENDENCY' }
    if ($Name -like 'org/springframework/boot/loader/*') { return 'BOOT_LOADER' }
    if ($Name -eq 'META-INF/MANIFEST.MF') { return 'MANIFEST' }
    if ($Name -match '(^|/)(pom\.xml|pom\.properties|git\.properties|build-info\.properties|native-image\.properties|reachability-metadata\.json)$') {
        return 'METADATA'
    }
    if ($Name -match '\.(yml|yaml|properties|json|xml)$' -and $Name -notmatch '\.class$') { return 'CONFIG_RESOURCE' }
    if ($Name -match '\.class$') {
        if ($Name -match '(\$\$|__Bean|__ApplicationContext|__Autowiring|jdk/proxy|\$Proxy|ByteBuddy|HibernateProxy)') {
            return 'GENERATED_CLASS'
        }
        return 'APPLICATION_CLASS'
    }
    return 'UNKNOWN'
}

function Test-JmoaEntry([string]$Name) {
    $normalized = $Name.Replace('\','/').ToLowerInvariant()
    return $normalized -match '(^|/)(jmoa)(/|[-.])' -or $normalized -match 'io/github/.*/jmoa'
}

function Get-LikelySource([string]$Name, [string]$Category) {
    if (Test-JmoaEntry $Name) { return 'JMOA_OUTPUT' }
    if ($Category -eq 'GENERATED_CLASS') {
        if ($Name -match 'SpringCGLIB|FastClass|__Bean') { return 'SPRING_GENERATED_OUTPUT' }
        if ($Name -match 'ByteBuddy|HibernateProxy') { return 'FRAMEWORK_GENERATED_OUTPUT' }
        return 'GENERATED_OUTPUT'
    }
    if ($Category -eq 'METADATA' -or $Category -eq 'MANIFEST') { return 'BUILD_METADATA' }
    if ($Category -eq 'APPLICATION_CLASS') { return 'APPLICATION_COMPILATION' }
    if ($Category -eq 'DEPENDENCY') { return 'DEPENDENCY_RESOLUTION_OR_REWRITE' }
    if ($Category -eq 'BOOT_LOADER') { return 'SPRING_BOOT_PACKAGING' }
    if ($Category -eq 'CONFIG_RESOURCE') { return 'BUILD_OR_RUNTIME_CONFIGURATION' }
    return 'UNKNOWN'
}

function Get-MemoryRelevance([string]$Category, [bool]$JmoaRelated) {
    if ($JmoaRelated) { return 'HIGH' }
    switch ($Category) {
        'DEPENDENCY' { return 'HIGH' }
        'APPLICATION_CLASS' { return 'MEDIUM' }
        'GENERATED_CLASS' { return 'MEDIUM' }
        'BOOT_LOADER' { return 'MEDIUM' }
        'CONFIG_RESOURCE' { return 'MEDIUM' }
        'MANIFEST' { return 'LOW' }
        'METADATA' { return 'LOW' }
        default { return 'UNKNOWN' }
    }
}

function Get-JavapFingerprint([byte[]]$Bytes) {
    $tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('jmoa-javap-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
    $classPath = Join-Path $tempRoot 'Entry.class'
    try {
        [IO.File]::WriteAllBytes($classPath, $Bytes)
        $output = & $Javap -p -c -s -constants $classPath 2>&1
        if ($LASTEXITCODE -ne 0) { return $null }
        $normalized = @(
            $output | ForEach-Object {
                $line = [string]$_
                $line = $line -replace '#\d+', '#'
                $line = $line -replace 'Classfile .+$', 'Classfile <redacted>'
                $line.TrimEnd()
            } | Where-Object {
                $_ -notmatch '^\s*(Last modified|SHA-256 checksum|Compiled from)'
            }
        ) -join "`n"
        return Get-Sha256Text $normalized
    } catch {
        return $null
    } finally {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Get-Classification(
    [string]$DifferenceType,
    [string]$Category,
    [bool]$JmoaRelated,
    [Nullable[bool]]$SemanticFingerprintEqual
) {
    if ($JmoaRelated) { return 'DISQUALIFYING_COMPARATOR_CONTAMINATION' }
    if ($DifferenceType -ne 'CONTENT_CHANGED') { return 'DISQUALIFYING_COMPARATOR_DRIFT' }
    if ($Category -in @('METADATA','MANIFEST')) { return 'EXPECTED_NONFUNCTIONAL_BUILD_DRIFT' }
    if ($Category -eq 'APPLICATION_CLASS' -and $SemanticFingerprintEqual -eq $true) {
        return 'BENIGN_CLASSFILE_NONDETERMINISM'
    }
    if ($Category -eq 'GENERATED_CLASS') { return 'DISQUALIFYING_GENERATED_OUTPUT_DRIFT' }
    return 'DISQUALIFYING_COMPARATOR_DRIFT'
}

$historical = Get-ZipEntryMap $HistoricalArtifact
$current = Get-ZipEntryMap $CurrentArtifact
$allNames = @($historical.Keys + $current.Keys | Sort-Object -Unique)
$differences = @()

foreach ($name in $allNames) {
    $h = $historical[$name]
    $c = $current[$name]
    if ($null -ne $h -and $null -ne $c -and $h.sha256 -eq $c.sha256) { continue }

    $differenceType = if ($null -eq $h) { 'CURRENT_ONLY' } elseif ($null -eq $c) { 'HISTORICAL_ONLY' } else { 'CONTENT_CHANGED' }
    $category = Get-EntryCategory $name
    $jmoaRelated = Test-JmoaEntry $name
    $semanticEqual = $null
    if ($differenceType -eq 'CONTENT_CHANGED' -and $name.EndsWith('.class') -and $null -ne $h -and $null -ne $c) {
        $historicalFingerprint = Get-JavapFingerprint $h.content
        $currentFingerprint = Get-JavapFingerprint $c.content
        if ($null -ne $historicalFingerprint -and $null -ne $currentFingerprint) {
            $semanticEqual = $historicalFingerprint -eq $currentFingerprint
        }
    }

    $differences += [ordered]@{
        logicalPathSha256 = Get-Sha256Text $name
        category = $category
        differenceType = $differenceType
        historicalBytes = if ($null -ne $h) { $h.bytes } else { $null }
        currentBytes = if ($null -ne $c) { $c.bytes } else { $null }
        historicalContentSha256 = if ($null -ne $h) { $h.sha256 } else { $null }
        currentContentSha256 = if ($null -ne $c) { $c.sha256 } else { $null }
        semanticFingerprintEqual = $semanticEqual
        jmoaRelated = $jmoaRelated
        likelySource = Get-LikelySource $name $category
        memoryRelevance = Get-MemoryRelevance $category $jmoaRelated
        classification = Get-Classification $differenceType $category $jmoaRelated $semanticEqual
    }
}

$historicalJmoa = @($historical.Keys | Where-Object { Test-JmoaEntry $_ })
$currentJmoa = @($current.Keys | Where-Object { Test-JmoaEntry $_ })
$disqualifying = @($differences | Where-Object { $_.classification -like 'DISQUALIFYING_*' })
$report = [ordered]@{
    schemaVersion = 'jmoa-comparator-entry-audit-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    service = $Service
    privacy = [ordered]@{
        logicalPathsPublished = $false
        logicalPathHashesPublished = $true
        note = 'Entry names remain private; SHA-256 logical path identifiers support stable reconciliation.'
    }
    artifactIdentity = [ordered]@{
        historicalSha256 = (Get-FileHash -LiteralPath $HistoricalArtifact -Algorithm SHA256).Hash.ToUpperInvariant()
        currentSha256 = (Get-FileHash -LiteralPath $CurrentArtifact -Algorithm SHA256).Hash.ToUpperInvariant()
        historicalBytes = (Get-Item -LiteralPath $HistoricalArtifact).Length
        currentBytes = (Get-Item -LiteralPath $CurrentArtifact).Length
    }
    contamination = [ordered]@{
        historicalJmoaEntryCount = $historicalJmoa.Count
        currentJmoaEntryCount = $currentJmoa.Count
        historicalCleanB0 = $historicalJmoa.Count -eq 0
        currentCleanB0 = $currentJmoa.Count -eq 0
    }
    summary = [ordered]@{
        historicalEntryCount = $historical.Count
        currentEntryCount = $current.Count
        differenceCount = $differences.Count
        disqualifyingDifferenceCount = $disqualifying.Count
        semanticNondeterminismCount = @($differences | Where-Object classification -eq 'BENIGN_CLASSFILE_NONDETERMINISM').Count
        comparatorDecision = if ($disqualifying.Count -eq 0 -and $historicalJmoa.Count -eq 0) {
            'COMPARATOR_EQUIVALENT'
        } elseif ($historicalJmoa.Count -gt 0) {
            'HISTORICAL_BASELINE_CONTAMINATED'
        } else {
            'COMPARATOR_DRIFT_REQUIRES_EXACT_HISTORICAL_REUSE'
        }
    }
    categoryCounts = [ordered]@{}
    classificationCounts = [ordered]@{}
    differences = $differences
}
foreach ($group in @($differences | Group-Object { $_.category } | Sort-Object Name)) {
    $report.categoryCounts[$group.Name] = $group.Count
}
foreach ($group in @($differences | Group-Object { $_.classification } | Sort-Object Name)) {
    $report.classificationCounts[$group.Name] = $group.Count
}

$jsonPath = Join-Path $OutputDirectory "$Service-comparator-entry-audit.json"
$mdPath = Join-Path $OutputDirectory "$Service-comparator-entry-audit.md"
$report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$categoryRows = @($report.categoryCounts.GetEnumerator() | ForEach-Object { "| $($_.Key) | $($_.Value) |" })
$classificationRows = @($report.classificationCounts.GetEnumerator() | ForEach-Object { "| $($_.Key) | $($_.Value) |" })
@"
# $Service Comparator Entry Audit

- Decision: **$($report.summary.comparatorDecision)**
- Historical artifact SHA-256: ``$($report.artifactIdentity.historicalSha256)``
- Current artifact SHA-256: ``$($report.artifactIdentity.currentSha256)``
- Entry differences: **$($report.summary.differenceCount)**
- Disqualifying differences: **$($report.summary.disqualifyingDifferenceCount)**
- Historical JMOA entries: **$($report.contamination.historicalJmoaEntryCount)**
- Current JMOA entries: **$($report.contamination.currentJmoaEntryCount)**

Logical paths are deliberately omitted. Stable SHA-256 path identifiers are retained in the JSON report.

## Categories

| Category | Count |
|---|---:|
$($categoryRows -join "`n")

## Classifications

| Classification | Count |
|---|---:|
$($classificationRows -join "`n")

This is an artifact-comparator audit, not runtime evidence. A disqualifying result requires exact historical comparator reuse or a new clean baseline campaign.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Comparator audit: $($report.summary.comparatorDecision)"
Write-Host "JSON: $jsonPath"
Write-Host "Markdown: $mdPath"

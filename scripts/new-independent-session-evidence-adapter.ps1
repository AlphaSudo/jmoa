param(
    [Parameter(Mandatory)][string]$SessionIndexPath,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$index = Get-Content -Raw -LiteralPath $SessionIndexPath | ConvertFrom-Json
if ([string]$index.schemaVersion -ne 'jmoa-independent-session-index-v1') {
    throw 'Unsupported independent-session index schema.'
}
New-JmoaDirectory $OutputDirectory
$adapterRuns = [Collections.Generic.List[object]]::new()
$supportSessionIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$baselinePairIndexes = [Collections.Generic.HashSet[int]]::new()
$candidatePairIndexes = [Collections.Generic.HashSet[int]]::new()
foreach ($mapping in @($index.mappings)) {
    $source = (Resolve-Path -LiteralPath ([string]$mapping.sourceRunDirectory)).Path
    $name = [string]$mapping.adapterRunId
    if ($name -notmatch '^[bc]([1-9][0-9]*)$') { throw "Invalid adapter run ID: $name" }
    $destination = Join-Path $OutputDirectory $name
    New-JmoaDirectory $destination
    foreach ($file in @(Get-ChildItem -LiteralPath $source -File | Where-Object Name -ne 'run-manifest.json')) {
        $link = Join-Path $destination $file.Name
        if (Test-Path -LiteralPath $link) { throw "Adapter output already exists: $link" }
        New-Item -ItemType SymbolicLink -Path $link -Target $file.FullName | Out-Null
    }
    $sourceManifestPath = Join-Path $source 'run-manifest.json'
    $manifest = Get-Content -Raw -LiteralPath $sourceManifestPath | ConvertFrom-Json
    if (-not [bool]$manifest.independentSupportSession) {
        throw "Source run is not marked as an independent support session: $source"
    }
    if ([string]::IsNullOrWhiteSpace([string]$manifest.supportSessionId)) {
        throw "Source run has no supportSessionId: $source"
    }
    if (-not $supportSessionIds.Add([string]$manifest.supportSessionId)) {
        throw "Support session was reused across product arms: $($manifest.supportSessionId)"
    }
    if ([string]$manifest.supportSessionId -ne [string]$mapping.supportSessionId) {
        throw "Session index support ID does not match source manifest for $source"
    }
    $pairIndex = [int]$name.Substring(1)
    $variant = if ($name.StartsWith('b')) { 'BASELINE' } else { 'CANDIDATE' }
    if ($variant -eq 'BASELINE') {
        if (-not $baselinePairIndexes.Add($pairIndex)) {
            throw "Duplicate BASELINE mapping for pair $pairIndex."
        }
    } else {
        if (-not $candidatePairIndexes.Add($pairIndex)) {
            throw "Duplicate CANDIDATE mapping for pair $pairIndex."
        }
    }
    $manifest | Add-Member -NotePropertyName runId -NotePropertyValue $name -Force
    $manifest | Add-Member -NotePropertyName pairIndex -NotePropertyValue $pairIndex -Force
    $manifest | Add-Member -NotePropertyName variant -NotePropertyValue $variant -Force
    $manifest | Add-Member -NotePropertyName independentSessionAdapter -NotePropertyValue $true -Force
    $manifest | Add-Member -NotePropertyName sourceRunManifestPath -NotePropertyValue $sourceManifestPath -Force
    $manifest | Add-Member -NotePropertyName sourceRunManifestSha256 -NotePropertyValue ((Get-JmoaSha256 $sourceManifestPath).ToUpperInvariant()) -Force
    Write-JmoaJson $manifest (Join-Path $destination 'run-manifest.json')
    $adapterRuns.Add([ordered]@{
        adapterRunId = $name
        pairIndex = $pairIndex
        variant = $variant
        sourceSessionId = [string]$mapping.sessionId
        sourceRunDirectory = $source
        sourceRunManifestSha256 = (Get-JmoaSha256 $sourceManifestPath).ToUpperInvariant()
        adaptedRunManifestSha256 = (Get-JmoaSha256 (Join-Path $destination 'run-manifest.json')).ToUpperInvariant()
        capturesLinkedReadOnly = $true
    })
}
$allPairIndexes = @(@($baselinePairIndexes) + @($candidatePairIndexes) | Sort-Object -Unique)
$completePairs = @(
    $allPairIndexes | Where-Object {
        $baselinePairIndexes.Contains([int]$_) -and $candidatePairIndexes.Contains([int]$_)
    }
)
$expectedRunCount = $completePairs.Count * 2
$report = [ordered]@{
    schemaVersion = 'jmoa-independent-session-evidence-adapter-v1'
    sourceIndexSha256 = (Get-JmoaSha256 $SessionIndexPath).ToUpperInvariant()
    capturePolicy = 'SYMLINK_RAW_CAPTURES_AND_DERIVE_PAIR_MANIFEST_ONLY'
    distinctIndependentSupportSessions = $supportSessionIds.Count
    completePairs = $completePairs.Count
    runs = $adapterRuns.ToArray()
    passed = (
        $completePairs.Count -ge 3 -and
        $allPairIndexes.Count -eq $completePairs.Count -and
        $adapterRuns.Count -eq $expectedRunCount -and
        $supportSessionIds.Count -eq $expectedRunCount
    )
}
Write-JmoaJson $report (Join-Path $OutputDirectory 'independent-session-adapter.json')
if (-not $report.passed) {
    throw "Expected at least three complete pairs with one independent support session per run; found $($completePairs.Count) complete pairs and $($adapterRuns.Count) runs."
}
$report | ConvertTo-Json -Depth 10

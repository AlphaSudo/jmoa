param(
    [Parameter(Mandatory)][string]$PairDirectory,
    [Parameter(Mandatory)][string]$ExpectedB0Sha256,
    [Parameter(Mandatory)][string]$ExpectedV1Sha256,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$pairPath = Join-Path $PairDirectory 'v2o-runtime-screen-pair-1.json'
$baselineDirectory = Join-Path $PairDirectory 'b1'
$candidateDirectory = Join-Path $PairDirectory 'c1'
foreach ($path in @(
    $pairPath,
    (Join-Path $baselineDirectory 'run-manifest.json'),
    (Join-Path $candidateDirectory 'run-manifest.json'),
    (Join-Path $baselineDirectory 'smaps_rollup.txt'),
    (Join-Path $candidateDirectory 'smaps_rollup.txt'),
    (Join-Path $baselineDirectory 'memory.current'),
    (Join-Path $candidateDirectory 'memory.current'),
    (Join-Path $baselineDirectory 'nmt-summary.txt'),
    (Join-Path $candidateDirectory 'nmt-summary.txt'),
    (Join-Path $baselineDirectory 'classloader-stats.txt'),
    (Join-Path $candidateDirectory 'classloader-stats.txt')
)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required pair evidence is missing: $path" }
}

function Get-SmapsMetric([string]$Directory, [string]$Name) {
    $text = Get-Content -Raw -LiteralPath (Join-Path $Directory 'smaps_rollup.txt')
    $match = [regex]::Match($text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { throw "smaps_rollup is missing $Name in $Directory" }
    [long]$match.Groups[1].Value
}
function Get-NmtCommitted([string]$Directory) {
    $text = Get-Content -Raw -LiteralPath (Join-Path $Directory 'nmt-summary.txt')
    $match = [regex]::Match($text, '(?m)^Total:\s+reserved=\d+KB,\s+committed=(\d+)KB\s*$')
    if (-not $match.Success) { throw "NMT summary total is missing in $Directory" }
    [long]$match.Groups[1].Value
}
function Get-LoadedClasses([string]$Directory) {
    $text = Get-Content -Raw -LiteralPath (Join-Path $Directory 'classloader-stats.txt')
    $match = [regex]::Match($text, '(?m)^Total\s+=\s+\d+\s+(\d+)\s+')
    if (-not $match.Success) { throw "Classloader total is missing in $Directory" }
    [long]$match.Groups[1].Value
}

$pair = Get-Content -Raw -LiteralPath $pairPath | ConvertFrom-Json
$bManifest = Get-Content -Raw -LiteralPath (Join-Path $baselineDirectory 'run-manifest.json') | ConvertFrom-Json
$cManifest = Get-Content -Raw -LiteralPath (Join-Path $candidateDirectory 'run-manifest.json') | ConvertFrom-Json
$bWorkload = Get-Content -Raw -LiteralPath (Join-Path $baselineDirectory 'workload-result.json') | ConvertFrom-Json
$cWorkload = Get-Content -Raw -LiteralPath (Join-Path $candidateDirectory 'workload-result.json') | ConvertFrom-Json

$baseline = [ordered]@{
    pssKb = Get-SmapsMetric $baselineDirectory 'Pss'
    privateDirtyKb = Get-SmapsMetric $baselineDirectory 'Private_Dirty'
    pssAnonKb = Get-SmapsMetric $baselineDirectory 'Pss_Anon'
    pssFileKb = Get-SmapsMetric $baselineDirectory 'Pss_File'
    memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $baselineDirectory 'memory.current')).Trim()
    nmtCommittedKb = Get-NmtCommitted $baselineDirectory
    loadedClasses = Get-LoadedClasses $baselineDirectory
}
$candidate = [ordered]@{
    pssKb = Get-SmapsMetric $candidateDirectory 'Pss'
    privateDirtyKb = Get-SmapsMetric $candidateDirectory 'Private_Dirty'
    pssAnonKb = Get-SmapsMetric $candidateDirectory 'Pss_Anon'
    pssFileKb = Get-SmapsMetric $candidateDirectory 'Pss_File'
    memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $candidateDirectory 'memory.current')).Trim()
    nmtCommittedKb = Get-NmtCommitted $candidateDirectory
    loadedClasses = Get-LoadedClasses $candidateDirectory
}
$delta = [ordered]@{
    pssKb = $candidate.pssKb - $baseline.pssKb
    privateDirtyKb = $candidate.privateDirtyKb - $baseline.privateDirtyKb
    pssAnonKb = $candidate.pssAnonKb - $baseline.pssAnonKb
    pssFileKb = $candidate.pssFileKb - $baseline.pssFileKb
    memoryCurrentBytes = $candidate.memoryCurrentBytes - $baseline.memoryCurrentBytes
    nmtCommittedKb = $candidate.nmtCommittedKb - $baseline.nmtCommittedKb
    loadedClasses = $candidate.loadedClasses - $baseline.loadedClasses
}

$bHash = $ExpectedB0Sha256.Trim().ToUpperInvariant()
$cHash = $ExpectedV1Sha256.Trim().ToUpperInvariant()
$artifactsExact = ([string]$bManifest.runtimeArtifactSha256).ToUpperInvariant() -eq $bHash -and
    ([string]$cManifest.runtimeArtifactSha256).ToUpperInvariant() -eq $cHash
$workloadsValid = @(@($bWorkload, $cWorkload) | Where-Object {
    [int]$_.requests -eq 80 -and [int]$_.errors -eq 0 -and [string]$_.health -eq 'UP' -and [string]$_.status -eq 'COMPLETED'
}).Count -eq 2
$policiesEqual = [string]$bManifest.runtimePolicy -eq 'BASE_CDS' -and
    [string]$cManifest.runtimePolicy -eq 'BASE_CDS' -and
    [bool]$pair.baseArchiveIdentity.sameSha256 -and
    [bool]$pair.baseArchiveIdentity.samePath -and
    [bool]$pair.baseArchiveIdentity.sameDeviceInode
$pairValid = [string]$pair.status -eq 'CAPTURED' -and $artifactsExact -and $workloadsValid -and $policiesEqual
$historicalDirectionReturned = $delta.pssKb -lt 0 -and $delta.privateDirtyKb -lt 0
$decision = if (-not $pairValid) {
    'DOCTOR_DIRECTIONAL_PAIR_INVALID'
} elseif ($historicalDirectionReturned) {
    'DOCTOR_BASELINE_DRIFT_PROVEN'
} else {
    'DOCTOR_HISTORICAL_V1_NOT_REPRODUCED'
}
$attribution = if ($delta.pssAnonKb -gt 0 -and $delta.nmtCommittedKb -le 0) {
    'NMT_INVISIBLE_ANONYMOUS_DIRTY_GROWTH'
} elseif ($delta.pssFileKb -gt 0) {
    'FILE_MAPPING_GROWTH'
} else {
    'UNKNOWN'
}

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-b0-v1-directional-pair-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    decision = $decision
    claimable = $false
    pairValid = $pairValid
    baseline = $baseline
    candidate = $candidate
    deltaCandidateMinusBaseline = $delta
    historicalReference = [ordered]@{
        pairedDeltaMedianPssKb = -2036
        independentMedianDeltaPssKb = -2728
        invalidPublishedMedianPssKb = -6048
    }
    gates = [ordered]@{
        artifactsExact = $artifactsExact
        workloadsValid = $workloadsValid
        effectiveBaseCdsPolicyEqual = $policiesEqual
        historicalDirectionReturned = $historicalDirectionReturned
    }
    attribution = [ordered]@{
        category = $attribution
        explanation = 'V1 loaded fewer classes and had slightly lower NMT committed memory, but anonymous PSS and Private_Dirty increased. The reconstructed pair therefore does not reproduce the historical V1 direction.'
    }
    provenance = [ordered]@{
        b0Artifact = 'EXACT_HISTORICAL'
        v1Artifact = 'EXACT_HISTORICAL'
        effectiveCdsPolicy = 'EXPLICIT_BASE_CDS_MATCHING_HISTORICAL_FALLBACK'
        serviceJdkAndSupportImages = 'RECONSTRUCTED_NOT_ORIGINAL'
    }
    authorization = [ordered]@{
        sixOrderCampaignAllowed = ($decision -eq 'DOCTOR_BASELINE_DRIFT_PROVEN')
        mechanismActivationStudyAllowed = $false
        nextAction = if ($decision -eq 'DOCTOR_HISTORICAL_V1_NOT_REPRODUCED') {
            'Stop Doctor reruns. Compare historical host/JDK/support-image provenance; runtime equivalence is not complete enough to authorize mechanism instrumentation.'
        } else {
            'A corrected six-order B0/V1/V2 campaign may be prepared.'
        }
    }
}

New-JmoaDirectory -Path $OutputDirectory
$jsonPath = Join-Path $OutputDirectory 'doctor-b0-v1-directional-pair.json'
$mdPath = Join-Path $OutputDirectory 'doctor-b0-v1-directional-pair.md'
Write-JmoaJson -Value $report -Path $jsonPath
@"
# Doctor B0/V1 Directional Pair

- Decision: **$decision**
- Evidence status: **$(if ($pairValid) { 'valid diagnostic' } else { 'invalid' })**
- B0 PSS: **$($baseline.pssKb) KB**
- V1 PSS: **$($candidate.pssKb) KB**
- V1 - B0 PSS: **$($delta.pssKb) KB**
- V1 - B0 Private_Dirty: **$($delta.privateDirtyKb) KB**
- V1 - B0 `memory.current`: **$($delta.memoryCurrentBytes) bytes**
- Loaded-class delta: **$($delta.loadedClasses)**
- NMT committed delta: **$($delta.nmtCommittedKb) KB**
- Attribution: **$attribution**

Historical reference:

- paired-delta median: **-2,036 KB**
- independent-median delta: **-2,728 KB**
- old published **-6,048 KB**: **invalid historical median calculation**

The exact historical B0 and V1 application artifacts were compared under the same reconstructed
base-CDS runtime. V1 reduced loaded classes but increased anonymous PSS and Private_Dirty. The old
negative direction did not return. This diagnostic is not a performance claim, and it does not
authorize the six-order Doctor campaign.
"@ | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Decision: $decision"
Write-Host "Report: $jsonPath"

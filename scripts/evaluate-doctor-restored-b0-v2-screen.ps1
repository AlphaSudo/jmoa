param(
    [Parameter(Mandatory)][string]$PairDirectory,
    [ValidateRange(1, 99)][int]$PairIndex = 1,
    [Parameter(Mandatory)][string]$AttributionJson,
    [Parameter(Mandatory)][string]$AcceptedBaselineJson,
    [Parameter(Mandatory)][string]$ExpectedB0Sha256,
    [Parameter(Mandatory)][string]$ExpectedV2Sha256,
    [Parameter(Mandatory)][string]$ExpectedB0ImageId,
    [Parameter(Mandatory)][string]$ExpectedV2ImageId,
    [ValidateSet('BASE_CDS','APP_CDS')][string]$ExpectedRuntimePolicy = 'BASE_CDS',
    [string]$ExpectedB0ArchiveSha256 = '',
    [string]$ExpectedV2ArchiveSha256 = '',
    [ValidateRange(0, 16384)][int]$BaselineToleranceKb = 1024,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required JSON is missing: $Path" }
    Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}
function Read-SmapsMetric([string]$Directory, [string]$Name) {
    $path = Join-Path $Directory 'smaps_rollup.txt'
    $text = Get-Content -Raw -LiteralPath $path
    $match = [regex]::Match($text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { throw "$path does not contain $Name." }
    [long]$match.Groups[1].Value
}
function In-Range([long]$Value, $Range, [long]$Tolerance = 0) {
    $Value -ge ([long]$Range.minimum - $Tolerance) -and $Value -le ([long]$Range.maximum + $Tolerance)
}

$pairPath = Join-Path $PairDirectory "v2o-runtime-screen-pair-$PairIndex.json"
$b0Directory = Join-Path $PairDirectory "b$PairIndex"
$v2Directory = Join-Path $PairDirectory "c$PairIndex"
$pair = Read-Json $pairPath
$attribution = Read-Json $AttributionJson
$accepted = Read-Json $AcceptedBaselineJson
$b0Manifest = Read-Json (Join-Path $b0Directory 'run-manifest.json')
$v2Manifest = Read-Json (Join-Path $v2Directory 'run-manifest.json')
$b0Workload = Read-Json (Join-Path $b0Directory 'workload-result.json')
$v2Workload = Read-Json (Join-Path $v2Directory 'workload-result.json')

$b0 = [ordered]@{
    pssKb = Read-SmapsMetric $b0Directory 'Pss'
    anonymousPssKb = Read-SmapsMetric $b0Directory 'Pss_Anon'
    privateDirtyKb = Read-SmapsMetric $b0Directory 'Private_Dirty'
    filePssKb = Read-SmapsMetric $b0Directory 'Pss_File'
    memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $b0Directory 'memory.current')).Trim()
    startupMillis = [long]$b0Manifest.startupMillis
}
$v2 = [ordered]@{
    pssKb = Read-SmapsMetric $v2Directory 'Pss'
    anonymousPssKb = Read-SmapsMetric $v2Directory 'Pss_Anon'
    privateDirtyKb = Read-SmapsMetric $v2Directory 'Private_Dirty'
    filePssKb = Read-SmapsMetric $v2Directory 'Pss_File'
    memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $v2Directory 'memory.current')).Trim()
    startupMillis = [long]$v2Manifest.startupMillis
}
$delta = [ordered]@{
    pssKb = $v2.pssKb - $b0.pssKb
    anonymousPssKb = $v2.anonymousPssKb - $b0.anonymousPssKb
    privateDirtyKb = $v2.privateDirtyKb - $b0.privateDirtyKb
    filePssKb = $v2.filePssKb - $b0.filePssKb
    memoryCurrentBytes = $v2.memoryCurrentBytes - $b0.memoryCurrentBytes
    startupMillis = $v2.startupMillis - $b0.startupMillis
    heapPssKb = [long]$attribution.smapsCategories.JAVA_HEAP.pssKb.delta
    anonymousRwOutsideHeapPssKb = [long]$attribution.smapsCategories.ANONYMOUS_RW_OUTSIDE_HEAP.pssKb.delta
    nmtCommittedKb = [long]$attribution.headline.nmtCommittedDeltaKb
    loadedClasses = [long]$attribution.jvm.loadedClasses.delta
    heapUsedKb = [long]$attribution.jvm.heapUsedKb.delta
    histogramBytes = [long]$attribution.jvm.histogramBytes.delta
    metaspaceCommittedKb = [long]$attribution.jvm.metaspaceCommittedKb.delta
}

$expectedB0Hash = $ExpectedB0Sha256.Trim().ToUpperInvariant()
$expectedV2Hash = $ExpectedV2Sha256.Trim().ToUpperInvariant()
$expectedB0Image = $ExpectedB0ImageId.Trim().ToLowerInvariant()
$expectedV2Image = $ExpectedV2ImageId.Trim().ToLowerInvariant()
$artifactsExact = ([string]$b0Manifest.runtimeArtifactSha256).ToUpperInvariant() -eq $expectedB0Hash -and
    ([string]$v2Manifest.runtimeArtifactSha256).ToUpperInvariant() -eq $expectedV2Hash
$imagesExact = ([string]$b0Manifest.imageId).ToLowerInvariant() -eq $expectedB0Image -and
    ([string]$v2Manifest.imageId).ToLowerInvariant() -eq $expectedV2Image
$jdkExact = [string]$b0Manifest.runtimeJdkFingerprint.fingerprintSha256 -eq [string]$v2Manifest.runtimeJdkFingerprint.fingerprintSha256 -and
    [string]$b0Manifest.runtimeJdkFingerprint.javaRuntimeVersion -eq '26+35'
$workloadsValid = @($b0Workload, $v2Workload | Where-Object {
    [int]$_.requests -eq 80 -and [int]$_.errors -eq 0 -and [string]$_.health -eq 'UP' -and [string]$_.status -eq 'COMPLETED'
}).Count -eq 2
$policyExact = [string]$pair.baselineRuntimePolicy -eq $ExpectedRuntimePolicy -and
    [string]$pair.candidateRuntimePolicy -eq $ExpectedRuntimePolicy
if ($ExpectedRuntimePolicy -eq 'BASE_CDS') {
    $policyExact = $policyExact -and
        $null -ne $pair.baseArchiveIdentity -and
        [bool]$pair.baseArchiveIdentity.sameSha256 -and
        [bool]$pair.baseArchiveIdentity.samePath -and
        [bool]$pair.baseArchiveIdentity.sameDeviceInode
} else {
    $policyExact = $policyExact -and
        -not [string]::IsNullOrWhiteSpace($ExpectedB0ArchiveSha256) -and
        -not [string]::IsNullOrWhiteSpace($ExpectedV2ArchiveSha256) -and
        ([string]$b0Manifest.cdsArchiveSha256).ToUpperInvariant() -eq $ExpectedB0ArchiveSha256.Trim().ToUpperInvariant() -and
        ([string]$v2Manifest.cdsArchiveSha256).ToUpperInvariant() -eq $ExpectedV2ArchiveSha256.Trim().ToUpperInvariant() -and
        [bool]$pair.baseline.runtimePolicyProof.applicationArchiveMapped -and
        [bool]$pair.candidate.runtimePolicyProof.applicationArchiveMapped -and
        [string]$pair.baseline.runtimePolicyProof.defaultJdkArchiveSha256 -eq [string]$pair.candidate.runtimePolicyProof.defaultJdkArchiveSha256
}
$ledgersComplete = [bool]$pair.armCommandLedgers.baseline.passed -and [bool]$pair.armCommandLedgers.candidate.passed
$semanticClean = [int]$pair.baseline.semanticLinkageErrors -eq 0 -and [int]$pair.candidate.semanticLinkageErrors -eq 0

$b0AnonymousAccepted = In-Range $b0.anonymousPssKb $accepted.historical.anonymousPssKb $BaselineToleranceKb
$b0DirtyAccepted = In-Range $b0.privateDirtyKb $accepted.historical.privateDirtyKb $BaselineToleranceKb
$fileNormalizedB0Pss = $b0.pssKb - $b0.filePssKb + [long]$accepted.historical.filePssKb.median
$b0FileNormalizedAccepted = In-Range $fileNormalizedB0Pss $accepted.historical.pssKb $BaselineToleranceKb
$baselineMemoryAccepted = $b0AnonymousAccepted -and $b0DirtyAccepted -and $b0FileNormalizedAccepted
$pairValid = [string]$pair.status -eq 'CAPTURED' -and $artifactsExact -and $imagesExact -and $jdkExact -and
    $workloadsValid -and $policyExact -and $ledgersComplete -and $semanticClean -and $baselineMemoryAccepted
$allPrimaryImprove = $delta.pssKb -lt 0 -and $delta.privateDirtyKb -lt 0 -and $delta.memoryCurrentBytes -lt 0
$decision = if (-not $pairValid) {
    'DOCTOR_B0_V2_DIRECTIONAL_SCREEN_INVALID'
} elseif ($allPrimaryImprove) {
    'DOCTOR_V2_DIRECTIONAL_WIN_PROMOTE_TO_CONFIRMATION'
} elseif ($delta.pssKb -gt 0 -and $delta.privateDirtyKb -gt 0 -and $delta.memoryCurrentBytes -gt 0) {
    'DOCTOR_V2_DIRECTIONAL_REGRESSION_STOP'
} else {
    'DOCTOR_V2_DIRECTIONAL_MIXED_NEEDS_REVERSED_DIAGNOSTIC'
}

$report = [ordered]@{
    schemaVersion = 'jmoa-doctor-restored-b0-v2-screen-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    decision = $decision
    claimable = $false
    comparison = 'PHASE32K_D2_FIXED_MINUS_EXACT_PHASE32D_B0'
    runtimePolicy = $ExpectedRuntimePolicy
    pairValid = $pairValid
    baseline = $b0
    candidate = $v2
    deltaV2MinusB0 = $delta
    restoredBaselineGate = [ordered]@{
        toleranceKb = $BaselineToleranceKb
        anonymousPssInsideHistoricalRange = $b0AnonymousAccepted
        privateDirtyInsideHistoricalRange = $b0DirtyAccepted
        fileNormalizedPssKb = $fileNormalizedB0Pss
        fileNormalizedPssInsideHistoricalRange = $b0FileNormalizedAccepted
        startupReproduced = In-Range $b0.startupMillis $accepted.historical.startupMillis
        memoryCurrentReproduced = In-Range $b0.memoryCurrentBytes $accepted.historical.memoryCurrentBytes ($BaselineToleranceKb * 1KB)
    }
    evidenceGates = [ordered]@{
        artifactsExact = $artifactsExact
        imagesExact = $imagesExact
        identicalJdkFingerprint = $jdkExact
        identicalBaseCdsMapping = $policyExact
        workloadsValid = $workloadsValid
        commandLedgersComplete = $ledgersComplete
        semanticLinkageClean = $semanticClean
        baselineTargetMemoryAccepted = $baselineMemoryAccepted
    }
    attribution = [ordered]@{
        reconciliation = [string]$attribution.headline.reconciliation
        primaryMappingMovement = [string]$attribution.headline.primaryMappingMovement
    }
    claimBoundary = 'One B0-first/V2-second diagnostic pair only. The result can authorize confirmation but cannot establish a V2 memory claim.'
    nextAction = switch ($decision) {
        'DOCTOR_V2_DIRECTIONAL_WIN_PROMOTE_TO_CONFIRMATION' { 'Run three fresh alternating B0/V2 pairs under this frozen tuple, then apply V2-C and V2-D.' }
        'DOCTOR_V2_DIRECTIONAL_MIXED_NEEDS_REVERSED_DIAGNOSTIC' { 'Run exactly one V2-first/B0-second diagnostic before deciding whether order or drift dominates.' }
        'DOCTOR_V2_DIRECTIONAL_REGRESSION_STOP' { 'Stop promotion and investigate the V2 mechanism/runtime attribution.' }
        default { 'Repair the failed evidence gate and rerun the screen.' }
    }
}

New-JmoaDirectory -Path $OutputDirectory
$jsonPath = Join-Path $OutputDirectory 'doctor-restored-b0-v2-directional-screen.json'
$mdPath = Join-Path $OutputDirectory 'doctor-restored-b0-v2-directional-screen.md'
Write-JmoaJson -Value $report -Path $jsonPath
@"
# Doctor Restored B0 to V2 Directional Screen

- Decision: **$decision**
- Evidence valid: **$pairValid**
- Claimable: **false**
- B0 PSS: **$($b0.pssKb) KB**
- V2 PSS: **$($v2.pssKb) KB**
- V2 - B0 PSS: **$($delta.pssKb) KB**
- V2 - B0 Private_Dirty: **$($delta.privateDirtyKb) KB**
- V2 - B0 `memory.current`: **$($delta.memoryCurrentBytes) bytes**
- V2 - B0 heap PSS: **$($delta.heapPssKb) KB**
- V2 - B0 anonymous writable PSS outside heap: **$($delta.anonymousRwOutsideHeapPssKb) KB**
- V2 - B0 NMT committed: **$($delta.nmtCommittedKb) KB**
- V2 - B0 loaded classes: **$($delta.loadedClasses)**
- Primary mapping movement: **$($report.attribution.primaryMappingMovement)**

## Baseline Gate

- Anonymous PSS inside historical range: **$b0AnonymousAccepted**
- Private_Dirty inside historical range: **$b0DirtyAccepted**
- File-normalized PSS: **$fileNormalizedB0Pss KB**
- File-normalized PSS inside historical range: **$b0FileNormalizedAccepted**
- Startup reproduced: **$($report.restoredBaselineGate.startupReproduced)**
- `memory.current` reproduced: **$($report.restoredBaselineGate.memoryCurrentReproduced)**

The accepted baseline contract is target-process memory shape, not startup or cgroup
parity with the old host. This is one B0-first/V2-second diagnostic pair and is not
a performance claim.
"@ | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "Decision: $decision"
Write-Host "Report: $jsonPath"

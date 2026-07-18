param(
    [string]$EvidenceRoot = 'target/v2-patient-root-cause',
    [string]$StudyDirectory = '',
    [int]$WarmupSeconds = 20,
    [int]$PostWorkloadSnapshotSeconds = 20,
    [string]$MavenExecutable = 'mvn',
    [string]$PluginCoordinates = 'com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2',
    [switch]$DropPageCacheBeforeVariant
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if ([string]::IsNullOrWhiteSpace($StudyDirectory)) { $StudyDirectory = Join-Path $EvidenceRoot 'runtime/extracted-common-appcds-study' }
$manifestPath = Join-Path $StudyDirectory 'preparation-manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Run prepare-patient-extracted-common-appcds-study.ps1 first.' }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifest.status -ne 'PREPARED' -or ($manifest.reproducibility | Where-Object { -not $_.passed })) { throw 'Preparation or archive reproducibility is not valid.' }

$stop = Join-Path $EvidenceRoot 'runtime/patient-stop.ps1'
$workload = Join-Path $EvidenceRoot 'runtime/patient-workload.ps1'
$results = Join-Path $StudyDirectory 'fixed-artifact-gates'
New-JmoaDirectory $results

function Invoke-GatePair([string]$Version, [string]$Copy, [int]$PairIndex, [string]$Order) {
    $slug = $Version.ToLowerInvariant()
    $root = Join-Path $results "$slug-$copy"
    $baseline = Join-Path $root "b$PairIndex"
    $candidate = Join-Path $root "c$PairIndex"
    $analysisPath = Join-Path $root 'analysis/runtime-policy-screen-analysis.json'
    $captured = (Test-Path -LiteralPath (Join-Path $baseline 'run-manifest.json') -PathType Leaf) `
        -and (Test-Path -LiteralPath (Join-Path $candidate 'run-manifest.json') -PathType Leaf)
    if (-not $captured) {
        & (Join-Path $PSScriptRoot 'run-fixed-artifact-appcds-screen.ps1') `
            -BaseLaunchScript (Join-Path $StudyDirectory "patient-$slug-extracted-base-launch.ps1") `
            -AppLaunchScript (Join-Path $StudyDirectory "patient-$slug-extracted-common-$copy-app-launch.ps1") `
            -StopScript $stop -WorkloadScript $workload `
            -ArtifactPath (Join-Path $StudyDirectory "$slug-extracted/app.jar") `
            -AppArchivePath (Join-Path $StudyDirectory "$slug-common-$copy.jsa") `
            -HealthUrl 'http://localhost:18084/actuator/health' -Service 'patient-service' `
            -LaunchMode 'EXTRACTED_CLASSPATH' -ContainerName 'jmoa-v2-patient-app' `
            -RuntimeArtifactPath '/application/app.jar' -PairIndex $PairIndex -Order $Order `
            -CaptureRoot $root -WarmupSeconds $WarmupSeconds `
            -PostWorkloadSnapshotSeconds $PostWorkloadSnapshotSeconds `
            -HealthTimeoutSeconds 240 -DropPageCacheBeforeVariant:$DropPageCacheBeforeVariant -FailOnFailure
        if ($LASTEXITCODE -ne 0) { throw "$Version fixed-artifact pair $Copy failed." }
    }
    if (-not (Test-Path -LiteralPath $analysisPath -PathType Leaf)) {
        & (Join-Path $PSScriptRoot 'analyze-runtime-policy-screen.ps1') -PairRoot $root `
            -BaselineRunDirectory $baseline -CandidateRunDirectory $candidate `
            -CandidateArchivePath (Join-Path $StudyDirectory "$slug-common-$copy.jsa") `
            -BaselineLabel 'BASE_CDS_EXTRACTED' -CandidateLabel 'COMMON_APP_CDS_EXTRACTED'
        if ($LASTEXITCODE -ne 0) { throw "$Version fixed-artifact pair $Copy analysis failed." }
    }
    return Get-Content -Raw -LiteralPath $analysisPath | ConvertFrom-Json
}

$gateRecords = @()
foreach ($version in @('V1', 'V2')) {
    $a = Invoke-GatePair $version 'a' 1 'BASE_FIRST'
    $b = Invoke-GatePair $version 'b' 2 'APP_FIRST'
    $pss = @([long]$a.deltas.pssKb, [long]$b.deltas.pssKb) | Sort-Object
    $memory = @([long]$a.deltas.memoryCurrentBytes, [long]$b.deltas.memoryCurrentBytes) | Sort-Object
    $mapped = @([long]$a.deltas.archiveMappedPssKb, [long]$b.deltas.archiveMappedPssKb)
    $pssMedian = [long][Math]::Round(($pss[0] + $pss[1]) / 2.0)
    $memoryMedian = [long][Math]::Round(($memory[0] + $memory[1]) / 2.0)
    $sameDirection = ([Math]::Sign([long]$a.deltas.pssKb) -eq [Math]::Sign([long]$b.deltas.pssKb))
    $mappedMaximum = [Math]::Max([long]$mapped[0], [long]$mapped[1])
    $mappedDeltaPercent = if ($mappedMaximum -eq 0) { 0.0 } else { 100.0 * [Math]::Abs($mapped[0] - $mapped[1]) / $mappedMaximum }
    $passed = $pssMedian -le 1024 -and $memoryMedian -le 1048576 -and $sameDirection -and $mappedDeltaPercent -le 5.0 `
        -and [int]$a.workload.baselineErrors -eq 0 -and [int]$a.workload.candidateErrors -eq 0 `
        -and [int]$b.workload.baselineErrors -eq 0 -and [int]$b.workload.candidateErrors -eq 0
    $gateRecords += [ordered]@{
        version = $version; status = if ($passed) { 'ADMISSIBLE' } else { 'REJECTED' }
        medianAppMinusBasePssKb = $pssMedian; medianAppMinusBaseMemoryCurrentBytes = $memoryMedian
        samePssDirectionInBothOrders = $sameDirection; mappedPssDeltaPercent = $mappedDeltaPercent
        pairs = @([ordered]@{copy='A';order='BASE_TO_APP';deltas=$a.deltas}, [ordered]@{copy='B';order='APP_TO_BASE';deltas=$b.deltas})
    }
}

$allPassed = @($gateRecords | Where-Object status -ne 'ADMISSIBLE').Count -eq 0
$verdict = [ordered]@{
    metadataVersion = 'patient-extracted-common-appcds-fixed-artifact-gates-v1'
    status = if ($allPassed) { 'FIXED_ARTIFACT_GATES_PASSED' } else { 'PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED' }
    gates = $gateRecords
    v1ToV2ScreenAllowed = $allPassed
    permanentStop = -not $allPassed
    stopReason = if ($allPassed) { $null } else { 'At least one extracted common-class APP archive failed its balanced APP-minus-BASE admission gate.' }
    productPolicy = if ($allPassed) { 'HOLD_PENDING_V1_TO_V2_SCREEN' } else { 'NO_CDS_LOW_DIRTY' }
    noMorePatientAppCdsWork = -not $allPassed
}
Write-JmoaJson $verdict (Join-Path $StudyDirectory 'fixed-artifact-gates.json')
if (-not $allPassed) {
    Write-Host 'PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED: fixed-artifact gate failed. V1-to-V2 APP comparison is forbidden.'
    exit 0
}

Write-Host 'Both fixed-artifact gates passed. Starting the gated V1-to-V2 APP screen.'

function Invoke-V1V2AppPair([string]$CaptureRoot, [int]$PairIndex, [string]$FirstVariant) {
    & (Join-Path $PSScriptRoot 'runtime-screen-pair.ps1') `
        -BaselineLaunchScript (Join-Path $StudyDirectory 'patient-v1-extracted-common-a-app-launch.ps1') `
        -CandidateLaunchScript (Join-Path $StudyDirectory 'patient-v2-extracted-common-a-app-launch.ps1') `
        -BaselineContainerName 'jmoa-v2-patient-app' -CandidateContainerName 'jmoa-v2-patient-app' `
        -WorkloadScript $workload -HealthUrl 'http://localhost:18084/actuator/health' `
        -Service 'patient-service' -LaunchMode 'EXTRACTED_CLASSPATH' -RuntimePolicy 'APP_CDS' `
        -BaselineRuntimePolicy 'APP_CDS' -CandidateRuntimePolicy 'APP_CDS' `
        -BaselineCdsEnabled $true -CandidateCdsEnabled $true `
        -BaselineCdsArchivePath (Join-Path $StudyDirectory 'v1-common-a.jsa') `
        -CandidateCdsArchivePath (Join-Path $StudyDirectory 'v2-common-a.jsa') `
        -BaselineArtifactPath (Join-Path $StudyDirectory 'v1-extracted/app.jar') `
        -CandidateArtifactPath (Join-Path $StudyDirectory 'v2-extracted/app.jar') `
        -BaselineRuntimeArtifactPath '/application/app.jar' -CandidateRuntimeArtifactPath '/application/app.jar' `
        -BaselineRuntimeVerificationPath $manifestPath -CandidateRuntimeVerificationPath $manifestPath `
        -BaselineMallocArenaMax '1' -CandidateMallocArenaMax '1' `
        -StopScript $stop -PairIndex $PairIndex -FirstVariant $FirstVariant -CaptureRoot $CaptureRoot `
        -WorkloadId 'patient-extracted-common-appcds-v1-v2' -WarmupSeconds $WarmupSeconds `
        -PostWorkloadSnapshotSeconds @($PostWorkloadSnapshotSeconds) -HealthTimeoutSeconds 240 `
        -DropPageCacheBeforeVariant:$DropPageCacheBeforeVariant -FailOnFailure
    if ($LASTEXITCODE -ne 0) { throw "V1-to-V2 APP pair $PairIndex failed." }
}

function Read-PrimaryMetrics([string]$RunDirectory) {
    $rollup = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath (Join-Path $RunDirectory 'smaps_rollup.txt')) {
        if ($line -match '^(Pss|Private_Dirty):\s+(\d+)\s+kB') { $rollup[$Matches[1]] = [long]$Matches[2] }
    }
    return [ordered]@{
        pssKb = [long]$rollup.Pss
        privateDirtyKb = [long]$rollup.Private_Dirty
        memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $RunDirectory 'memory.current'))
    }
}

$screenRoot = Join-Path $StudyDirectory 'v1-v2-screen'
Invoke-V1V2AppPair $screenRoot 1 'BASELINE_FIRST'
$screenV1 = Read-PrimaryMetrics (Join-Path $screenRoot 'b1')
$screenV2 = Read-PrimaryMetrics (Join-Path $screenRoot 'c1')
$screenDeltas = [ordered]@{
    pssKb = $screenV2.pssKb - $screenV1.pssKb
    privateDirtyKb = $screenV2.privateDirtyKb - $screenV1.privateDirtyKb
    memoryCurrentBytes = $screenV2.memoryCurrentBytes - $screenV1.memoryCurrentBytes
}
$screenPassed = $screenDeltas.pssKb -le -1024 -and $screenDeltas.privateDirtyKb -le -1024 -and $screenDeltas.memoryCurrentBytes -le -1048576
$screenReport = [ordered]@{
    metadataVersion = 'patient-extracted-common-appcds-v1-v2-screen-v1'
    status = if ($screenPassed) { 'PROMOTED_TO_CONFIRMATION' } else { 'PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED' }
    baseline = $screenV1; candidate = $screenV2; deltas = $screenDeltas
    gates = [ordered]@{ pssKbMaximum=-1024;privateDirtyKbMaximum=-1024;memoryCurrentBytesMaximum=-1048576 }
}
Write-JmoaJson $screenReport (Join-Path $StudyDirectory 'v1-v2-screen.json')
if (-not $screenPassed) {
    Write-Host 'PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED: V1-to-V2 APP screen failed. Confirmation is forbidden.'
    exit 0
}

$confirmationRoot = Join-Path $StudyDirectory 'v1-v2-confirmation'
for ($pair = 1; $pair -le 3; $pair++) {
    $order = if ($pair -eq 2) { 'CANDIDATE_FIRST' } else { 'BASELINE_FIRST' }
    Invoke-V1V2AppPair $confirmationRoot $pair $order
}

$evidenceOutput = Join-Path $confirmationRoot 'jmoa-evidence'
$evidence = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments @(
    '-N', "${PluginCoordinates}:evidence", '-Djmoa.evidence.enabled=true', "-Djmoa.evidence.inputDir=$confirmationRoot",
    "-Djmoa.evidence.outputDir=$evidenceOutput", '-Djmoa.evidence.expectedPolicy=APP_CDS',
    '-Djmoa.evidence.requireArtifactHashes=true', '-Djmoa.evidence.requireWorkloadZeroErrors=true',
    '-Djmoa.evidence.requireSmapsArithmetic=true', '-Djmoa.evidence.failOnInvalidRun=true'
)
Write-JmoaText $evidence.output (Join-Path $confirmationRoot 'v2c-command.log')
if ($evidence.exitCode -ne 0) { throw 'V2-C rejected the extracted common-class confirmation.' }

$attributionOutput = Join-Path $confirmationRoot 'jmoa-attribution'
$attribution = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments @(
    '-N', "${PluginCoordinates}:attribution", '-Djmoa.attribution.enabled=true', "-Djmoa.attribution.inputDir=$confirmationRoot",
    "-Djmoa.attribution.outputDir=$attributionOutput", '-Djmoa.evidence.expectedPolicy=APP_CDS',
    '-Djmoa.attribution.requireV2CValid=true', '-Djmoa.attribution.diagnosticOnly=true'
)
Write-JmoaText $attribution.output (Join-Path $confirmationRoot 'v2d-command.log')
if ($attribution.exitCode -ne 0) { throw 'V2-D could not attribute the extracted common-class confirmation.' }

$pairDeltas = @()
for ($pair = 1; $pair -le 3; $pair++) {
    $v1 = Read-PrimaryMetrics (Join-Path $confirmationRoot "b$pair")
    $v2 = Read-PrimaryMetrics (Join-Path $confirmationRoot "c$pair")
    $pairDeltas += [ordered]@{ pair=$pair;pssKb=$v2.pssKb-$v1.pssKb;privateDirtyKb=$v2.privateDirtyKb-$v1.privateDirtyKb;memoryCurrentBytes=$v2.memoryCurrentBytes-$v1.memoryCurrentBytes }
}
function Get-Median([long[]]$Values) { $sorted=@($Values|Sort-Object); return [long]$sorted[[int][Math]::Floor($sorted.Count/2)] }
$medianPss = Get-Median @($pairDeltas | ForEach-Object { [long]$_.pssKb })
$medianDirty = Get-Median @($pairDeltas | ForEach-Object { [long]$_.privateDirtyKb })
$medianCurrent = Get-Median @($pairDeltas | ForEach-Object { [long]$_.memoryCurrentBytes })
$wins = @($pairDeltas | Where-Object { $_.pssKb -lt 0 -and $_.privateDirtyKb -lt 0 -and $_.memoryCurrentBytes -lt 0 }).Count
$confirmed = $wins -ge 2 -and $medianPss -le -4096 -and $medianDirty -le -1024 -and $medianCurrent -le -1048576
$final = [ordered]@{
    metadataVersion='patient-extracted-common-appcds-final-verdict-v1'
    status=if($confirmed){'PATIENT_EXTRACTED_COMMON_APP_CDS_CONFIRMED'}else{'PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED'}
    validPairs=3;pairedWins=$wins;medianPssDeltaKb=$medianPss;medianPrivateDirtyDeltaKb=$medianDirty;medianMemoryCurrentDeltaBytes=$medianCurrent
    pairs=$pairDeltas;v2cOutput=$evidenceOutput;v2dOutput=$attributionOutput
    productPolicy=if($confirmed){'EXTRACTED_COMMON_APP_CDS'}else{'NO_CDS_LOW_DIRTY'}
    permanentStop=-not$confirmed
}
Write-JmoaJson $final (Join-Path $StudyDirectory 'final-verdict.json')
Write-Host $final.status

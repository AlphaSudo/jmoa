<#
.SYNOPSIS
    Final PetClinic independent-support-session campaign.

.DESCRIPTION
    Runs one attribution pass, one excluded three-target diagnostic sequence,
    three B0 qualification sessions, three V2 qualification sessions when
    admitted, and one final six-session product block when both artifacts are
    reproducible. No target shares a support lifecycle in qualification or
    product evidence.
#>
param(
    [Parameter(Mandatory)][string]$CampaignManifest,
    [Parameter(Mandatory)][string]$RunRoot,
    [Parameter(Mandatory)][string]$FixturesReport,
    [Parameter(Mandatory)][string]$ExistingB0CaptureRoot,
    [int]$SupportSettleSeconds = 180,
    [int]$WarmupSeconds = 20,
    [int]$SettleSeconds = 5,
    [int]$HealthTimeoutSeconds = 900,
    [int]$MaxPssRangeKb = 1024,
    [int]$MaxPrivateDirtyRangeKb = 1024,
    [long]$MaxMemoryCurrentRangeBytes = 2097152,
    [int]$TrustedPssGateKb = -4096,
    [int]$ConfirmedPrivateDirtyGateKb = -1024,
    [long]$ConfirmedMemoryCurrentGateBytes = -1048576,
    [string]$ContainerCli = '',
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'scenario-ledger-common.ps1')
. (Join-Path $PSScriptRoot 'petclinic-independent-session-common.ps1')

$protocol = 'PETCLINIC_INDEPENDENT_SESSION_V1'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$supportLaunch = Join-Path $PSScriptRoot 'campaign-launch-petclinic-support.ps1'
$supportStop = Join-Path $PSScriptRoot 'campaign-stop-petclinic-support.ps1'
$targetLaunch = Join-Path $PSScriptRoot 'campaign-launch-petclinic-target.ps1'
$targetStop = Join-Path $PSScriptRoot 'campaign-stop-petclinic-target.ps1'
$screenScript = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
$workloadScript = Join-Path $PSScriptRoot 'campaign-workload-petclinic.ps1'
$attributionScript = Join-Path $PSScriptRoot 'analyze-petclinic-b0-period-effect.ps1'
$adapterScript = Join-Path $PSScriptRoot 'new-independent-session-evidence-adapter.ps1'

$manifest = Get-Content -Raw -LiteralPath $CampaignManifest | ConvertFrom-Json
if ([string](Get-CampaignJsonProp $manifest 'schemaVersion') -ne 'jmoa-petclinic-campaign-manifest-v1') {
    throw 'Unsupported campaign manifest schema.'
}
$recordedCampaignSha = [string](Get-CampaignJsonProp $manifest 'campaignSha256')
$actualCampaignSha = Get-CampaignManifestSha256 -ManifestObject $manifest
if ($recordedCampaignSha.ToUpperInvariant() -ne $actualCampaignSha.ToUpperInvariant()) {
    throw 'Campaign manifest integrity failed.'
}
$images = Get-CampaignJsonProp $manifest 'images'
$artifacts = Get-CampaignJsonProp $manifest 'artifacts'
$config = Get-CampaignJsonProp $manifest 'configRepo'
$environment = Get-CampaignJsonProp $manifest 'environment'
$artifactLineage = Get-CampaignJsonProp $manifest 'artifactLineage'
$b0Image = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'baseline') 'ref')
$v2Image = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'candidate') 'ref')
$configImage = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'config') 'ref')
$discoveryImage = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'discovery') 'ref')
$expectedImageIds = [ordered]@{
    b0 = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'baseline') 'id')
    v2 = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'candidate') 'id')
    config = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'config') 'id')
    discovery = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $images 'discovery') 'id')
}
$b0Artifact = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'baseline') 'path')
$v2Artifact = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'candidate') 'path')
$b0Sha = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'baseline') 'sha256')
$v2Sha = [string](Get-CampaignJsonProp (Get-CampaignJsonProp $artifacts 'candidate') 'sha256')
$materializationManifestAsset = Get-CampaignJsonProp $artifacts 'materializationManifest'
$materializationManifest = [string](Get-CampaignJsonProp $materializationManifestAsset 'path')
$materializationManifestSha = [string](Get-CampaignJsonProp $materializationManifestAsset 'sha256')
$artifactLineagePath = [string](Get-CampaignJsonProp $artifactLineage 'path')
$artifactLineageSha = [string](Get-CampaignJsonProp $artifactLineage 'sha256')
$configRepo = [string](Get-CampaignJsonProp $config 'path')
$configTreeSha = [string](Get-CampaignJsonProp $config 'contentTreeSha256')
$maven = [string](Get-CampaignJsonProp $environment 'mavenExecutable')
$pluginCoordinates = [string](Get-CampaignJsonProp $environment 'pluginCoordinates')
$runtimePolicy = [string](Get-CampaignJsonProp $environment 'runtimePolicy')
if ([string]::IsNullOrWhiteSpace($ContainerCli)) {
    $ContainerCli = [string](Get-CampaignJsonProp $environment 'containerCli')
}
if ($runtimePolicy -ne 'NO_CDS_LOW_DIRTY') { throw "Protocol requires NO_CDS_LOW_DIRTY, got $runtimePolicy." }
foreach ($path in @(
    $b0Artifact, $v2Artifact, $materializationManifest, $artifactLineagePath,
    $configRepo, $FixturesReport, $ExistingB0CaptureRoot
)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Frozen input missing: $path" }
}
foreach ($check in @(
    @{ path = $b0Artifact; sha = $b0Sha; role = 'B0 artifact' },
    @{ path = $v2Artifact; sha = $v2Sha; role = 'V2 artifact' },
    @{ path = $materializationManifest; sha = $materializationManifestSha; role = 'materialization manifest' },
    @{ path = $artifactLineagePath; sha = $artifactLineageSha; role = 'artifact lineage' }
)) {
    if ((Get-JmoaSha256 $check.path).ToUpperInvariant() -ne $check.sha.ToUpperInvariant()) {
        throw "$($check.role) SHA mismatch."
    }
}
if ((Get-CampaignTreeSha256 -Root $configRepo) -ne $configTreeSha) { throw 'Config tree SHA mismatch.' }

$fixtures = Get-Content -Raw -LiteralPath $FixturesReport | ConvertFrom-Json
if (-not [bool]$fixtures.passed) { throw 'Gate A fixtures are not passing.' }
foreach ($fixture in @($fixtures.testedFiles)) {
    $local = Join-Path $repositoryRoot (([string]$fixture.logicalPath) -replace '/', '\')
    if (-not (Test-Path -LiteralPath $local) -or
        (Get-JmoaSha256 $local).ToUpperInvariant() -ne ([string]$fixture.sha256).ToUpperInvariant()) {
        throw "Gate A fixture report is stale for $($fixture.logicalPath)."
    }
}

$runId = 'petclinic-independent-session-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$resolvedRunRoot = if ([IO.Path]::IsPathRooted($RunRoot)) {
    [IO.Path]::GetFullPath($RunRoot)
} else {
    [IO.Path]::GetFullPath((Join-Path $repositoryRoot $RunRoot))
}
$runDirectory = Join-Path $resolvedRunRoot $runId
$reports = Join-Path $runDirectory 'reports'
$sessionsRoot = Join-Path $runDirectory 'sessions'
$diagnosticRoot = Join-Path $runDirectory 'diagnostic-sequential-b0'
$rootLedgers = Join-Path $runDirectory 'root-ledgers'
foreach ($directory in @($runDirectory, $reports, $sessionsRoot, $rootLedgers)) { New-JmoaDirectory $directory }
Start-ScenarioLedger -ScenarioId $runId -OutputDirectory $runDirectory -Description 'Final preregistered PetClinic campaign: attribution, one excluded sequential B0 diagnostic, independent B0/V2 qualification, then one final six-session product block if admitted.'
Add-ScenarioNote -Title 'Permanent stop condition' -Text 'If three independent B0 sessions exceed the frozen 1 MiB PSS or Private_Dirty range, or 2 MiB memory.current range, no more PetClinic direct-product protocol is authorized on this Hyper-V VM.'
Add-ScenarioAsset -Role 'Frozen campaign manifest' -Path $CampaignManifest -Provenance REUSED_FROZEN_INPUT -Note "campaignSha256=$actualCampaignSha" | Out-Null

function Resolve-Image {
    param([string]$Reference, [string]$Role)
    (Invoke-ScenarioCommand -Step "resolve frozen $Role image" -Executable $ContainerCli -Arguments @('image', 'inspect', '--format', '{{.Id}}', $Reference)).stdout.Trim()
}
$resolvedImages = [ordered]@{
    b0 = Resolve-Image $b0Image B0
    v2 = Resolve-Image $v2Image V2
    config = Resolve-Image $configImage config
    discovery = Resolve-Image $discoveryImage discovery
}
foreach ($key in @('b0', 'v2', 'config', 'discovery')) {
    if (($resolvedImages[$key] -replace '^sha256:', '') -ne ($expectedImageIds[$key] -replace '^sha256:', '')) {
        Complete-ScenarioLedger -Status STOPPED_ARTIFACT_GATE -Result @{ role = $key } | Out-Null
        throw "Frozen $key image identity mismatch."
    }
}

$preflightLedger = Join-Path $rootLedgers 'artifact-preflight'
Initialize-CampaignAuditLedger -LedgerDirectory $preflightLedger -Stage 'artifact-preflight' -Variant B0_V2 -Description 'Independent-session immutable artifact gate.' | Out-Null
$baselineFingerprints = Get-CampaignImageArtifactFingerprints -ContainerCli $ContainerCli -Image $b0Image -LedgerDirectory $preflightLedger -Step 'baseline artifact fingerprints'
$baselineGate = Test-CampaignBaselineClean -ContainerCli $ContainerCli -Image $b0Image -LedgerDirectory $preflightLedger
$candidateGate = Test-CampaignCandidateTransformed -ContainerCli $ContainerCli -Image $v2Image -MaterializationManifestPath $materializationManifest -BaselineFingerprints $baselineFingerprints -LedgerDirectory $preflightLedger
$lineageGate = Test-CampaignArtifactLineage -LineagePath $artifactLineagePath -ExpectedB0Sha256 $b0Sha -ExpectedV2Sha256 $v2Sha -ExpectedMaterializationManifestSha256 $materializationManifestSha
$artifactGate = [ordered]@{ baseline = $baselineGate; candidate = $candidateGate; lineage = $lineageGate; passed = ($baselineGate.passed -and $candidateGate.passed -and $lineageGate.passed) }
Write-JmoaJson $artifactGate (Join-Path $reports 'artifact-gate.json')
Complete-CampaignAuditLedger -LedgerDirectory $preflightLedger -Status $(if ($artifactGate.passed) { 'COMPLETE' } else { 'FAILED' }) -Stage 'artifact-preflight' -Variant B0_V2 | Out-Null
if (-not $artifactGate.passed) {
    Complete-ScenarioLedger -Status STOPPED_ARTIFACT_GATE -Result $artifactGate | Out-Null
    throw 'Artifact gate failed.'
}

$frozenConfig = Join-Path $runDirectory 'frozen-config-repo'
New-JmoaDirectory $frozenConfig
foreach ($entry in @(Get-ChildItem -LiteralPath $configRepo -Force | Where-Object Name -ne '.git')) {
    Copy-Item -LiteralPath $entry.FullName -Destination $frozenConfig -Recurse -Force
}
if ((Get-CampaignTreeSha256 -Root $frozenConfig) -ne $configTreeSha) { throw 'Frozen config copy SHA mismatch.' }
$bootId = (Invoke-ScenarioCommand -Step 'capture host boot ID' -Executable '/bin/bash' -Arguments @('-lc', 'cat /proc/sys/kernel/random/boot_id')).stdout.Trim()
$hostIdentity = (Invoke-ScenarioCommand -Step 'capture host identity' -Executable '/bin/bash' -Arguments @('-lc', 'uname -a; cat /etc/os-release; nproc; cat /proc/meminfo')).stdout
$hostFingerprint = (Get-JmoaTextSha256 $hostIdentity).ToUpperInvariant()

$context = [pscustomobject]@{
    b0Image = $b0Image
    v2Image = $v2Image
    configImage = $configImage
    discoveryImage = $discoveryImage
    b0Artifact = $b0Artifact
    v2Artifact = $v2Artifact
    frozenConfig = $frozenConfig
    containerCli = $ContainerCli
    hostFingerprint = $hostFingerprint
}
$contract = [ordered]@{
    schemaVersion = 'jmoa-petclinic-independent-session-contract-v1'
    protocol = $protocol
    preRegisteredAt = [DateTime]::UtcNow.ToString('o')
    measurementUnit = @('one fresh support stack', 'one target JVM', 'one workload', 'one capture', 'complete teardown')
    runtime = [ordered]@{
        supportSettleSeconds = $SupportSettleSeconds
        targetWarmupSeconds = $WarmupSeconds
        requests = 81
        targetSettleSeconds = $SettleSeconds
        policy = 'NO_CDS_LOW_DIRTY'
        mallocArenaMax = '1'
        cds = false
        javaagent = false
    }
    qualification = [ordered]@{
        observations = 3
        maxPairwisePssKb = $MaxPssRangeKb
        maxPairwisePrivateDirtyKb = $MaxPrivateDirtyRangeKb
        maxPairwiseMemoryCurrentBytes = $MaxMemoryCurrentRangeBytes
    }
    finalOrder = @('B0', 'V2', 'V2', 'B0', 'B0', 'V2')
    finalGate = [ordered]@{
        validSessions = 6
        pairedWins = 2
        pssKb = $TrustedPssGateKb
        privateDirtyKb = $ConfirmedPrivateDirtyGateKb
        memoryCurrentBytes = $ConfirmedMemoryCurrentGateBytes
        semanticErrors = 0
        v2c = 'CONFIRMED_WIN'
        v2d = 'PASSED'
    }
    permanentStop = 'Independent B0 qualification failure ends PetClinic direct-product work on this Hyper-V VM.'
    redesignAfterTargetEvidence = 'FORBIDDEN'
}
Write-JmoaJson $contract (Join-Path $reports 'petclinic-independent-session-v1-contract.json')
if ($DryRun) {
    Complete-ScenarioLedger -Status READY -Result $contract | Out-Null
    exit 0
}

function Assert-HostContinuity {
    param([string]$ContextLabel)
    $current = (Invoke-ScenarioCommand -Step "host continuity $ContextLabel" -Executable '/bin/bash' -Arguments @('-lc', 'cat /proc/sys/kernel/random/boot_id')).stdout.Trim()
    if ($current -ne $bootId) {
        Complete-ScenarioLedger -Status CAMPAIGN_INTERRUPTED_BY_HOST_POWER_EVENT -Result @{ context = $ContextLabel } | Out-Null
        throw 'Host boot ID changed.'
    }
}
function Get-Median {
    param([double[]]$Values)
    $ordered = @($Values | Sort-Object)
    if ($ordered.Count % 2 -eq 1) { return [double]$ordered[[int]($ordered.Count / 2)] }
    ([double]$ordered[$ordered.Count / 2 - 1] + [double]$ordered[$ordered.Count / 2]) / 2.0
}
function Get-Qualification {
    param([string]$Variant, [object[]]$Sessions)
    $pss = @($Sessions | ForEach-Object { [double]$_.pssKb })
    $dirty = @($Sessions | ForEach-Object { [double]$_.privateDirtyKb })
    $current = @($Sessions | ForEach-Object { [double]$_.memoryCurrentBytes })
    $medianPss = Get-Median $pss
    $absoluteDeviations = @($pss | ForEach-Object { [math]::Abs($_ - $medianPss) })
    $meanPss = ($pss | Measure-Object -Average).Average
    $sumSquares = ($pss | ForEach-Object { ($_ - $meanPss) * ($_ - $meanPss) } | Measure-Object -Sum).Sum
    $stddev = [math]::Sqrt($sumSquares / $pss.Count)
    $report = [ordered]@{
        schemaVersion = 'jmoa-independent-session-qualification-v1'
        variant = $Variant
        observations = $Sessions
        statistics = [ordered]@{
            pssRangeKb = [long](($pss | Measure-Object -Maximum).Maximum - ($pss | Measure-Object -Minimum).Minimum)
            privateDirtyRangeKb = [long](($dirty | Measure-Object -Maximum).Maximum - ($dirty | Measure-Object -Minimum).Minimum)
            memoryCurrentRangeBytes = [long](($current | Measure-Object -Maximum).Maximum - ($current | Measure-Object -Minimum).Minimum)
            medianPssKb = [long]$medianPss
            pssMadKb = [long](Get-Median $absoluteDeviations)
            pssCoefficientOfVariationPercent = [math]::Round(100.0 * $stddev / $meanPss, 4)
        }
        thresholds = [ordered]@{
            pssRangeKb = $MaxPssRangeKb
            privateDirtyRangeKb = $MaxPrivateDirtyRangeKb
            memoryCurrentRangeBytes = $MaxMemoryCurrentRangeBytes
        }
    }
    $report.passed = (
        $report.statistics.pssRangeKb -le $MaxPssRangeKb -and
        $report.statistics.privateDirtyRangeKb -le $MaxPrivateDirtyRangeKb -and
        $report.statistics.memoryCurrentRangeBytes -le $MaxMemoryCurrentRangeBytes -and
        @($Sessions | Where-Object { -not $_.valid -or $_.workloadErrors -ne 0 }).Count -eq 0
    )
    $report.terminalOutcome = if ($report.passed) {
        "${Variant}_INDEPENDENT_SESSION_REPRODUCIBLE"
    } elseif ($Variant -eq 'B0') {
        'PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST'
    } else {
        'V2_ARTIFACT_RUNTIME_VARIANCE'
    }
    $report
}

$attributionDirectory = Join-Path $reports 'period-effect-attribution'
& $attributionScript -CaptureRoot $ExistingB0CaptureRoot -OutputDirectory $attributionDirectory | Out-Null
if (-not $?) { throw 'Existing B0 attribution failed.' }
$periodAttribution = Get-Content -Raw -LiteralPath (Join-Path $attributionDirectory 'petclinic-b0-period-effect-attribution.json') | ConvertFrom-Json

function Invoke-SequentialDiagnostic {
    $scenarioId = 'diagnostic-b0-three-targets'
    $supportDirectory = Join-Path $diagnosticRoot 'support'
    $captureRoot = Join-Path $diagnosticRoot 'capture'
    $ledgerRoot = Join-Path $diagnosticRoot 'child-ledgers'
    $safe = $scenarioId -replace '[^A-Za-z0-9_.-]', '-'
    $network = "jmoa-$safe-net"
    $configName = "jmoa-$safe-cfg"
    $discoveryName = "jmoa-$safe-disc"
    & $supportLaunch -OutputDirectory $supportDirectory -PairId $scenarioId -ConfigImage $configImage -DiscoveryImage $discoveryImage -ConfigRepo $frozenConfig -SettleSeconds $SupportSettleSeconds -MinAvailableMemoryBytes 734003200 -ContainerCli $ContainerCli -LedgerDirectory (Join-Path $ledgerRoot 'support-launch')
    if (-not $?) { throw 'Diagnostic support admission failed.' }
    $results = [Collections.Generic.List[object]]::new()
    try {
        for ($index = 1; $index -le 3; $index++) {
            $diagnosticLedger = Join-Path $ledgerRoot "support-diagnostic-$index"
            Invoke-PetclinicSupportDiagnosticCapture -ContainerCli $ContainerCli -ConfigContainer $configName -DiscoveryContainer $discoveryName -Point "PRE_TARGET_$index" -OutputDirectory (Join-Path $supportDirectory "pre-target-$index") -LedgerDirectory $diagnosticLedger
            $common = @{ SupportNetwork = $network; CustomerReadyTimeoutSeconds = $HealthTimeoutSeconds; MinAvailableMemoryBeforeTargetBytes = 314572800; ContainerCli = $ContainerCli }
            $arguments = @{
                BaselineLaunchScript = $targetLaunch; CandidateLaunchScript = $targetLaunch
                BaselineContainerName = "$safe-b$index"; CandidateContainerName = "$safe-unused-c$index"
                WorkloadScript = $workloadScript; HealthUrl = 'http://localhost:8081/actuator/health'
                Service = 'customers-service'; LaunchMode = 'EXPLODED_BOOT_APP_SHARED_SUPPORT_DIAGNOSTIC'
                RuntimePolicy = 'NO_CDS_LOW_DIRTY'; BaselineArtifactPath = $b0Artifact; CandidateArtifactPath = $b0Artifact
                BaselineLaunchParameters = (@{} + $common + @{ Image = $b0Image })
                CandidateLaunchParameters = (@{} + $common + @{ Image = $b0Image })
                StopScript = $targetStop; ContainerCli = $ContainerCli; PairIndex = $index
                FirstVariant = 'BASELINE_FIRST'; ExecutionMode = 'BASELINE_ONLY'; CaptureRoot = $captureRoot
                LedgerDirectory = (Join-Path $ledgerRoot "target-$index"); MallocArenaMax = '1'
                WarmupSeconds = $WarmupSeconds; PostWorkloadSnapshotSeconds = @($SettleSeconds)
                HealthTimeoutSeconds = $HealthTimeoutSeconds; FailOnFailure = $true
                CapturePodmanMachinePressure = $true; MinPodmanAvailableMemoryBytes = 314572800
                MinPostArmAvailableMemoryBytes = 314572800; MaxPodmanSwapUsedBytes = 0
                RequireSwapDisabled = $true; RequireZeroOomEvents = $true
                MaxPodmanMemoryPressureSomeAvg10 = 999.0; MaxPodmanMemoryPressureFullAvg10 = 0.0
                DropPageCacheBeforeVariant = $true; WorkloadId = 'petclinic-corrected-27x3'
            }
            & $screenScript @arguments
            if (-not $?) { throw "Diagnostic B0 target $index failed." }
            $armDirectory = Join-Path $captureRoot "b$index"
            Add-PetclinicSessionManifestFields -RunDirectory $armDirectory -SupportSessionId $scenarioId -TargetSessionId "$scenarioId-target-$index" -LogicalVariant B0 -ExecutionOrdinal $index -HostFingerprint $hostFingerprint
            $memory = Read-CampaignRunMemory $armDirectory
            $results.Add([ordered]@{ target = "B0-$index"; position = $index; pssKb = [long]$memory.pssKb; privateDirtyKb = [long]$memory.privateDirtyKb; memoryCurrentBytes = [long]$memory.memoryCurrentBytes; runDirectory = $armDirectory })
            Invoke-PetclinicSupportDiagnosticCapture -ContainerCli $ContainerCli -ConfigContainer $configName -DiscoveryContainer $discoveryName -Point "POST_TARGET_$index" -OutputDirectory (Join-Path $supportDirectory "post-target-$index") -LedgerDirectory $diagnosticLedger
            Complete-CampaignAuditLedger -LedgerDirectory $diagnosticLedger -Status COMPLETE -Stage 'support-diagnostic' -Variant SUPPORT | Out-Null
        }
    } finally {
        & $supportStop -OutputDirectory $supportDirectory -PairId $scenarioId -ContainerCli $ContainerCli -LedgerDirectory (Join-Path $ledgerRoot 'support-stop')
        Write-PetclinicScenarioCommandLedger -ScenarioId $scenarioId -LedgerRoot $ledgerRoot -OutputDirectory $diagnosticRoot | Out-Null
    }
    $values = @($results | ForEach-Object { [long]$_.pssKb })
    $oneMiB = 1024
    $pattern = if ((($values | Measure-Object -Maximum).Maximum - ($values | Measure-Object -Minimum).Minimum) -le $oneMiB) {
        'ALL_WITHIN_1MIB'
    } elseif ($values[1] - $values[0] -gt $oneMiB -and $values[2] - $values[0] -gt $oneMiB -and [math]::Abs($values[2] - $values[1]) -le $oneMiB) {
        'FIRST_VERSUS_ALL_LATER'
    } elseif ($values[1] - $values[0] -gt $oneMiB -and [math]::Abs($values[2] - $values[0]) -le $oneMiB) {
        'ALTERNATING'
    } elseif ($values[1] - $values[0] -gt $oneMiB -and $values[2] - $values[1] -gt $oneMiB) {
        'MONOTONIC_GROWTH'
    } else {
        'RANDOM_OR_MIXED'
    }
    $report = [ordered]@{
        schemaVersion = 'jmoa-petclinic-b0-sequential-diagnostic-v1'
        includedInProductEvidence = false
        supportSessionId = $scenarioId
        results = $results.ToArray()
        pattern = $pattern
        productThresholdApplied = false
    }
    Write-JmoaJson $report (Join-Path $reports 'petclinic-b0-sequential-diagnostic.json')
    $report
}

$diagnostic = Invoke-SequentialDiagnostic
Assert-HostContinuity 'after diagnostic B0 sequence'

$ordinal = 0
$b0QualificationSessions = [Collections.Generic.List[object]]::new()
for ($index = 1; $index -le 3; $index++) {
    $ordinal++
    $sessionId = "b0-q$index"
    $session = Invoke-PetclinicIndependentSession -Context $context -Variant B0 -SessionId $sessionId -ExecutionOrdinal $ordinal -OutputDirectory (Join-Path $sessionsRoot $sessionId) -SupportSettleSeconds $SupportSettleSeconds -WarmupSeconds $WarmupSeconds -SettleSeconds $SettleSeconds -HealthTimeoutSeconds $HealthTimeoutSeconds
    $b0QualificationSessions.Add($session)
    Assert-HostContinuity "after $sessionId"
}
$b0Qualification = Get-Qualification -Variant B0 -Sessions $b0QualificationSessions.ToArray()
Write-JmoaJson $b0Qualification (Join-Path $reports 'b0-independent-session-qualification.json')
if (-not $b0Qualification.passed) {
    $closure = [ordered]@{
        schemaVersion = 'jmoa-petclinic-independent-session-closure-v1'
        protocol = $protocol
        terminalOutcome = 'PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST'
        periodAttribution = $periodAttribution.classification
        diagnosticPattern = $diagnostic.pattern
        b0Qualification = $b0Qualification
        v2Qualification = 'NOT_RUN_BY_PERMANENT_STOP_RULE'
        productCampaign = 'NOT_RUN_BY_PERMANENT_STOP_RULE'
        futurePetclinicProtocolOnThisVmAuthorized = false
    }
    Write-JmoaJson $closure (Join-Path $reports 'campaign-closure.json')
    Complete-ScenarioLedger -Status PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST -Result $closure | Out-Null
    exit 2
}

$v2QualificationSessions = [Collections.Generic.List[object]]::new()
for ($index = 1; $index -le 3; $index++) {
    $ordinal++
    $sessionId = "v2-q$index"
    $session = Invoke-PetclinicIndependentSession -Context $context -Variant V2 -SessionId $sessionId -ExecutionOrdinal $ordinal -OutputDirectory (Join-Path $sessionsRoot $sessionId) -SupportSettleSeconds $SupportSettleSeconds -WarmupSeconds $WarmupSeconds -SettleSeconds $SettleSeconds -HealthTimeoutSeconds $HealthTimeoutSeconds
    $v2QualificationSessions.Add($session)
    Assert-HostContinuity "after $sessionId"
}
$v2Qualification = Get-Qualification -Variant V2 -Sessions $v2QualificationSessions.ToArray()
Write-JmoaJson $v2Qualification (Join-Path $reports 'v2-independent-session-qualification.json')
if (-not $v2Qualification.passed) {
    $closure = [ordered]@{
        schemaVersion = 'jmoa-petclinic-independent-session-closure-v1'
        protocol = $protocol
        terminalOutcome = 'V2_ARTIFACT_RUNTIME_VARIANCE'
        periodAttribution = $periodAttribution.classification
        diagnosticPattern = $diagnostic.pattern
        b0Qualification = $b0Qualification
        v2Qualification = $v2Qualification
        productCampaign = 'NOT_RUN_BY_PRE_REGISTERED_STOP_RULE'
    }
    Write-JmoaJson $closure (Join-Path $reports 'campaign-closure.json')
    Complete-ScenarioLedger -Status V2_ARTIFACT_RUNTIME_VARIANCE -Result $closure | Out-Null
    exit 2
}

$productOrder = @('B0', 'V2', 'V2', 'B0', 'B0', 'V2')
$productSessions = [Collections.Generic.List[object]]::new()
for ($index = 1; $index -le $productOrder.Count; $index++) {
    $ordinal++
    $variant = $productOrder[$index - 1]
    $sessionId = "product-s$index-$($variant.ToLower())"
    $session = Invoke-PetclinicIndependentSession -Context $context -Variant $variant -SessionId $sessionId -ExecutionOrdinal $ordinal -OutputDirectory (Join-Path $sessionsRoot $sessionId) -SupportSettleSeconds $SupportSettleSeconds -WarmupSeconds $WarmupSeconds -SettleSeconds $SettleSeconds -HealthTimeoutSeconds $HealthTimeoutSeconds
    $productSessions.Add($session)
    Assert-HostContinuity "after $sessionId"
}

$mappingDefinitions = @(
    @{ adapterRunId = 'b1'; session = $productSessions[0] },
    @{ adapterRunId = 'c1'; session = $productSessions[1] },
    @{ adapterRunId = 'c2'; session = $productSessions[2] },
    @{ adapterRunId = 'b2'; session = $productSessions[3] },
    @{ adapterRunId = 'b3'; session = $productSessions[4] },
    @{ adapterRunId = 'c3'; session = $productSessions[5] }
)
$sessionIndex = [ordered]@{
    schemaVersion = 'jmoa-independent-session-index-v1'
    protocol = $protocol
    mappings = @($mappingDefinitions | ForEach-Object {
        [ordered]@{
            adapterRunId = $_.adapterRunId
            sessionId = $_.session.sessionId
            sourceRunDirectory = $_.session.runDirectory
            supportSessionId = $_.session.supportSessionId
            targetSessionId = $_.session.targetSessionId
            artifactSha256 = $_.session.artifactSha256
            hostFingerprint = $hostFingerprint
        }
    })
}
$sessionIndexPath = Join-Path $reports 'independent-session-index.json'
Write-JmoaJson $sessionIndex $sessionIndexPath
$adapterDirectory = Join-Path $runDirectory 'product-evidence-adapter'
& $adapterScript -SessionIndexPath $sessionIndexPath -OutputDirectory $adapterDirectory | Out-Null
if (-not $?) { throw 'Independent-session V2-C adapter failed.' }

$semanticPairs = [Collections.Generic.List[object]]::new()
$semanticErrors = 0
$dataStatePassed = $true
foreach ($pair in @(
    @{ index = 1; baseline = $productSessions[0]; candidate = $productSessions[1] },
    @{ index = 2; baseline = $productSessions[3]; candidate = $productSessions[2] },
    @{ index = 3; baseline = $productSessions[4]; candidate = $productSessions[5] }
)) {
    $semantic = Compare-CampaignSemantics -PairIndex $pair.index `
        -BaselineSemanticPath (Join-Path $pair.baseline.runDirectory 'semantic-requests.json') `
        -CandidateSemanticPath (Join-Path $pair.candidate.runDirectory 'semantic-requests.json')
    $dataState = Compare-CampaignDataState -PairIndex $pair.index `
        -BaselineDataStatePath (Join-Path $pair.baseline.runDirectory 'data-state.json') `
        -CandidateDataStatePath (Join-Path $pair.candidate.runDirectory 'data-state.json')
    $semanticErrors += [int]$semantic.semanticErrors
    if (-not [bool]$dataState.passed) { $dataStatePassed = $false }
    $semanticPairs.Add([ordered]@{ pairIndex = $pair.index; semantic = $semantic; dataState = $dataState })
}
Write-JmoaJson ([ordered]@{
    schemaVersion = 'jmoa-independent-session-semantic-equivalence-v1'
    semanticErrors = $semanticErrors
    dataStatePassed = $dataStatePassed
    pairs = $semanticPairs.ToArray()
}) (Join-Path $reports 'semantic-equivalence.json')

$evidenceDirectory = Join-Path $runDirectory 'jmoa-evidence'
$attributionDirectory = Join-Path $runDirectory 'jmoa-attribution'
$evidenceArguments = @(
    '-N', "${pluginCoordinates}:evidence", '-Djmoa.evidence.enabled=true',
    "-Djmoa.evidence.inputDir=$adapterDirectory", "-Djmoa.evidence.outputDir=$evidenceDirectory",
    "-Djmoa.evidence.expectedPolicy=$runtimePolicy", '-Djmoa.evidence.requireArtifactHashes=true',
    '-Djmoa.evidence.requireWorkloadZeroErrors=true', '-Djmoa.evidence.requireSmapsArithmetic=true',
    '-Djmoa.evidence.failOnInvalidRun=true'
)
$evidenceCommand = Invoke-ScenarioCommand -Step 'V2-C independent-session evidence analysis' -Executable $maven -Arguments $evidenceArguments -WorkingDirectory $repositoryRoot -AllowFailure
$attributionArguments = @(
    '-N', "${pluginCoordinates}:attribution", '-Djmoa.attribution.enabled=true',
    "-Djmoa.attribution.inputDir=$adapterDirectory", "-Djmoa.attribution.outputDir=$attributionDirectory",
    "-Djmoa.evidence.expectedPolicy=$runtimePolicy", '-Djmoa.attribution.requireV2CValid=true',
    '-Djmoa.attribution.diagnosticOnly=false'
)
$attributionCommand = Invoke-ScenarioCommand -Step 'V2-D independent-session attribution' -Executable $maven -Arguments $attributionArguments -WorkingDirectory $repositoryRoot -AllowFailure
$confirmation = Get-Content -Raw -LiteralPath (Join-Path $evidenceDirectory 'jmoa-paired-confirmation.json') | ConvertFrom-Json
$validation = Get-Content -Raw -LiteralPath (Join-Path $evidenceDirectory 'jmoa-evidence-validation.json') | ConvertFrom-Json
$attribution = Get-Content -Raw -LiteralPath (Join-Path $attributionDirectory 'jmoa-memory-attribution.json') | ConvertFrom-Json
$checks = [ordered]@{
    sixValidSessions = ([int]$validation.runs -eq 6 -and [int]$validation.invalidRuns -eq 0)
    independentSupportSessions = (@($productSessions.supportSessionId | Select-Object -Unique).Count -eq 6)
    pairedWins = ([int]$confirmation.pairedWins -ge 2)
    strictPss = ([long]$confirmation.medianPssDeltaKb -le $TrustedPssGateKb)
    privateDirty = ([long]$confirmation.medianPrivateDirtyDeltaKb -le $ConfirmedPrivateDirtyGateKb)
    memoryCurrent = ([long]$confirmation.medianMemoryCurrentDeltaBytes -le $ConfirmedMemoryCurrentGateBytes)
    semantics = ([int]$semanticErrors -eq 0 -and $dataStatePassed)
    v2c = ([string]$confirmation.verdict -eq 'CONFIRMED_WIN' -and $evidenceCommand.exitCode -eq 0)
    v2d = ($attributionCommand.exitCode -eq 0 -and [bool](Get-CampaignJsonProp $attribution 'v2cValid'))
}
$passed = -not ($checks.Values -contains $false)
$terminal = if ($passed) { 'TRUSTED_PRODUCT_WIN' } else { 'PRODUCT_EFFECT_NOT_CONFIRMED' }
$final = [ordered]@{
    schemaVersion = 'jmoa-petclinic-independent-session-final-v1'
    protocol = $protocol
    terminalOutcome = $terminal
    checks = $checks
    productSessions = $productSessions.ToArray()
    medians = [ordered]@{
        pssKb = [long]$confirmation.medianPssDeltaKb
        privateDirtyKb = [long]$confirmation.medianPrivateDirtyDeltaKb
        memoryCurrentBytes = [long]$confirmation.medianMemoryCurrentDeltaBytes
    }
    pairedWins = [int]$confirmation.pairedWins
    v2cVerdict = [string]$confirmation.verdict
    v2d = $attribution
}
Write-JmoaJson $final (Join-Path $reports 'final-product-gate.json')
Complete-ScenarioLedger -Status $terminal -Result $final | Out-Null
if (-not $passed) { exit 2 }

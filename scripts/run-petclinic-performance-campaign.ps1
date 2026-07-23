<#
.SYNOPSIS
    PetClinic performance campaign: a frozen-artifact, balanced, fail-closed memory comparison that
    decides a TRUSTED baseline-vs-V2 runtime win (median PSS <= -4096 KB) for the customers-service.

    Distinct from the adoption workflow (run-petclinic-audited-baseline-v2-scenario.ps1): this engine
    NEVER builds artifacts. It consumes a SIGNED campaign manifest (Gate C) that pins every frozen image
    ID, artifact SHA-256, config freeze, and artifact-lineage reference, and orchestrates the proven
    runtime-screen-pair.ps1 capture/policy engine plus the real V2-C (evidence) and V2-D (attribution)
    mojos. There are NO machine-specific default paths (review Issue #15): all frozen identity comes from
    the manifest supplied on the command line.

    Pipeline (fail-closed at every gate):
      Gate C   Manifest integrity      Recompute the campaign SHA-256 over the canonical manifest body.
      Step 1   Environment ledger      Record java -version and mvn -version before any analysis.
      Step 2   Image identity          Resolve every ref to its immutable ID and require == manifest ID.
      Step 3a  Artifact SHA gate       B0/V2/manifest jars must hash to the manifest-recorded SHA-256.
      Step 3b  Transformation gates    B0 clean; V2 carries all 24 reduced deps + runtime lib + classes.
      Step 3c  Artifact lineage gate   Consume the adoption scenario's artifact-lineage.json (Issue #4).
      Step 5   Config freeze           Freeze the config repo; re-validate before EVERY screen pair.
      Step 6   Same-artifact noise      TWO reversed pairs for B0 and TWO for V2; qualify or STOP.
      Step 7   Balanced pairs           3 pairs, order [B0->V2, V2->B0, B0->V2].
      Step 4   Semantic + data state    81-request status/body-hash + initial/final data-state equality.
      V2-C/D  Evidence + attribution   real mojos; V2-D is non-diagnostic and its report is parsed.
      Step 8   Frozen product gate      median PSS <= -4096 KB + full criteria + JDK parity => TRUSTED_WIN.
      Step 9   Reconciliation           Phase 33M vs replay vs fresh vs this campaign (exact wording).
      Step 10 Reports                  campaign-summary.json + campaign-report.md + campaign-readiness.*.
#>
param(
    [Parameter(Mandatory)][string]$CampaignManifest,
    [Parameter(Mandatory)][string]$RunRoot,
    [int]$Pairs = 3,
    [int]$WarmupSeconds = 20,
    [int]$SettleSeconds = 5,
    [int]$HealthTimeoutSeconds = 900,
    [bool]$DropPageCacheBeforeVariant = $true,
    [int]$TrustedPssGateKb = -4096,
    [int]$TrustedPrivateDirtyGateKb = -1024,
    [long]$TrustedMemoryCurrentGateBytes = -1048576,
    [int]$MaxNoisePssDriftKb = 1024,
    [int]$MaxNoisePrivateDirtyDriftKb = 1024,
    [long]$MaxNoiseMemoryCurrentDriftBytes = 2097152,
    [int]$MinReversedNoisePairsPerControl = 2,
    [long]$MinPodmanAvailableMemoryBytes = 1073741824,
    [long]$MaxPodmanSwapUsedBytes = 0,
    [double]$MaxPodmanMemoryPressureSomeAvg10 = 1.0,
    [double]$MaxPodmanMemoryPressureFullAvg10 = 0.1,
    [Parameter(Mandatory)][string]$FixturesReport,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')
. (Join-Path $PSScriptRoot 'scenario-ledger-common.ps1')

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$launchScript = Join-Path $PSScriptRoot 'campaign-launch-petclinic-stack.ps1'
$workloadScript = Join-Path $PSScriptRoot 'campaign-workload-petclinic.ps1'
$stopScript = Join-Path $PSScriptRoot 'campaign-stop-petclinic-stack.ps1'
$screenScript = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'
$noiseAnalyzer = Join-Path $PSScriptRoot 'analyze-same-artifact-noise.ps1'
$hostPreflightScript = Join-Path $PSScriptRoot 'capture-campaign-host-preflight.ps1'

$healthUrl = 'http://localhost:8081/actuator/health'
$service = 'customers-service'
$launchMode = 'FROZEN_IMAGE_FULL_STACK'

# ---- Gate C: load and verify the signed campaign manifest (fail-closed, pre-ledger) ------------
if (-not (Test-Path -LiteralPath $CampaignManifest -PathType Leaf)) { throw "Campaign manifest not found: $CampaignManifest" }
$resolvedManifestPath = (Resolve-Path -LiteralPath $CampaignManifest).Path
$manifest = Get-Content -Raw -LiteralPath $resolvedManifestPath | ConvertFrom-Json
$manifestSchema = [string](Get-CampaignJsonProp $manifest 'schemaVersion')
if ($manifestSchema -ne 'jmoa-petclinic-campaign-manifest-v1') { throw "Unsupported campaign manifest schema: $manifestSchema" }
$expectedCampaignSha = [string](Get-CampaignJsonProp $manifest 'campaignSha256')
$actualCampaignSha = Get-CampaignManifestSha256 -ManifestObject $manifest
$manifestGatePassed = (-not [string]::IsNullOrWhiteSpace($expectedCampaignSha)) -and ($expectedCampaignSha.ToUpperInvariant() -eq $actualCampaignSha.ToUpperInvariant())
if (-not $manifestGatePassed) {
    throw "Gate C failed: campaign manifest SHA-256 mismatch (recorded $expectedCampaignSha, recomputed $actualCampaignSha). The manifest was altered after signing."
}

# ---- Resolve every frozen identity from the manifest (single source of truth) ------------------
$mImages = Get-CampaignJsonProp $manifest 'images'
$mArtifacts = Get-CampaignJsonProp $manifest 'artifacts'
$mConfig = Get-CampaignJsonProp $manifest 'configRepo'
$mLineage = Get-CampaignJsonProp $manifest 'artifactLineage'
$mEnv = Get-CampaignJsonProp $manifest 'environment'
if ($null -eq $mImages -or $null -eq $mArtifacts -or $null -eq $mConfig -or $null -eq $mLineage -or $null -eq $mEnv) {
    throw 'Campaign manifest is missing one of: images, artifacts, configRepo, artifactLineage, environment.'
}

$mImgB0 = Get-CampaignJsonProp $mImages 'baseline'
$mImgV2 = Get-CampaignJsonProp $mImages 'candidate'
$mImgConfig = Get-CampaignJsonProp $mImages 'config'
$mImgDiscovery = Get-CampaignJsonProp $mImages 'discovery'
$B0Image = [string](Get-CampaignJsonProp $mImgB0 'ref')
$V2Image = [string](Get-CampaignJsonProp $mImgV2 'ref')
$ConfigImageId = [string](Get-CampaignJsonProp $mImgConfig 'ref')
$DiscoveryImageId = [string](Get-CampaignJsonProp $mImgDiscovery 'ref')
$expectedB0ImageId = [string](Get-CampaignJsonProp $mImgB0 'id')
$expectedV2ImageId = [string](Get-CampaignJsonProp $mImgV2 'id')
$expectedConfigImageId = [string](Get-CampaignJsonProp $mImgConfig 'id')
$expectedDiscoveryImageId = [string](Get-CampaignJsonProp $mImgDiscovery 'id')

$mArtB0 = Get-CampaignJsonProp $mArtifacts 'baseline'
$mArtV2 = Get-CampaignJsonProp $mArtifacts 'candidate'
$mArtManifest = Get-CampaignJsonProp $mArtifacts 'materializationManifest'
$B0Artifact = [string](Get-CampaignJsonProp $mArtB0 'path')
$V2Artifact = [string](Get-CampaignJsonProp $mArtV2 'path')
$MaterializationManifest = [string](Get-CampaignJsonProp $mArtManifest 'path')
$ExpectedB0Sha256 = [string](Get-CampaignJsonProp $mArtB0 'sha256')
$ExpectedV2Sha256 = [string](Get-CampaignJsonProp $mArtV2 'sha256')
$ExpectedMaterializationManifestSha256 = [string](Get-CampaignJsonProp $mArtManifest 'sha256')

$ConfigRepo = [string](Get-CampaignJsonProp $mConfig 'path')
$expectedConfigTreeSha = [string](Get-CampaignJsonProp $mConfig 'contentTreeSha256')
$ArtifactLineage = [string](Get-CampaignJsonProp $mLineage 'path')
$expectedLineageSha = [string](Get-CampaignJsonProp $mLineage 'sha256')

$JavaHome = [string](Get-CampaignJsonProp $mEnv 'javaHome')
$MavenExecutable = [string](Get-CampaignJsonProp $mEnv 'mavenExecutable')
$ContainerCli = [string](Get-CampaignJsonProp $mEnv 'containerCli')
$PluginCoordinates = [string](Get-CampaignJsonProp $mEnv 'pluginCoordinates')
$RuntimePolicy = [string](Get-CampaignJsonProp $mEnv 'runtimePolicy')
$SourceRevision = [string](Get-CampaignJsonProp $manifest 'sourceRevision')

# ---- Mandatory environment (review Issue #9): no defaults, must resolve from the manifest --------
foreach ($pair in @(
    @{ name = 'environment.javaHome'; value = $JavaHome },
    @{ name = 'environment.mavenExecutable'; value = $MavenExecutable },
    @{ name = 'environment.containerCli'; value = $ContainerCli },
    @{ name = 'environment.runtimePolicy'; value = $RuntimePolicy },
    @{ name = 'environment.pluginCoordinates'; value = $PluginCoordinates }
)) {
    if ([string]::IsNullOrWhiteSpace([string]$pair.value)) { throw "Campaign manifest is missing mandatory field: $($pair.name)" }
}
if (-not (Test-Path -LiteralPath $JavaHome -PathType Container)) { throw "JavaHome (manifest) does not exist: $JavaHome" }
$resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$javaExecutable = Join-Path $resolvedJavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) { throw "java.exe not found under JavaHome: $javaExecutable" }
$env:JAVA_HOME = $resolvedJavaHome
$env:Path = "$(Join-Path $resolvedJavaHome 'bin');$env:Path"

$runId = 'petclinic-performance-campaign-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$resolvedRunRoot = if ([IO.Path]::IsPathRooted($RunRoot)) { [IO.Path]::GetFullPath($RunRoot) } else { [IO.Path]::GetFullPath((Join-Path $repositoryRoot $RunRoot)) }
$runDir = Join-Path $resolvedRunRoot $runId
$captureBase = Join-Path $runDir 'captures'
$reportDir = Join-Path $runDir 'reports'
$childLedgerRoot = Join-Path $runDir 'child-ledgers'
$noiseB0Root = Join-Path $captureBase 'noise-b0'
$noiseV2Root = Join-Path $captureBase 'noise-v2'
$balancedRoot = Join-Path $captureBase 'balanced'
$evidenceDir = Join-Path $runDir 'jmoa-evidence'
$attributionDir = Join-Path $runDir 'jmoa-attribution'

foreach ($dir in @($runDir, $captureBase, $reportDir, $childLedgerRoot)) { New-JmoaDirectory -Path $dir }
$artifactPreflightLedger = Join-Path $childLedgerRoot 'preflight-artifact'

function Publish-CampaignChildLedgerIndex {
    $records = New-Object System.Collections.Generic.List[object]
    $reasons = New-Object System.Collections.Generic.List[string]
    foreach ($summaryFile in @(Get-ChildItem -LiteralPath $childLedgerRoot -Filter 'child-ledger-summary.json' -Recurse -File | Sort-Object FullName)) {
        $summary = Get-Content -Raw -LiteralPath $summaryFile.FullName | ConvertFrom-Json
        $ledgerDirectory = $summaryFile.Directory.FullName
        $integrityPath = Join-Path $ledgerDirectory ([string]$summary.integrityPath)
        $integrityExists = Test-Path -LiteralPath $integrityPath -PathType Leaf
        $actualIntegritySha = if ($integrityExists) { (Get-JmoaSha256 -Path $integrityPath).ToUpperInvariant() } else { '' }
        $expectedIntegritySha = ([string]$summary.integritySha256).ToUpperInvariant()
        $filesValid = $integrityExists
        $invalidFiles = New-Object System.Collections.Generic.List[string]
        if ($integrityExists) {
            $integrity = Get-Content -Raw -LiteralPath $integrityPath | ConvertFrom-Json
            foreach ($entry in @($integrity.files)) {
                $entryPath = Join-Path $ledgerDirectory (([string]$entry.path) -replace '/', '\')
                if (-not (Test-Path -LiteralPath $entryPath -PathType Leaf)) {
                    $filesValid = $false
                    $invalidFiles.Add("$($entry.path):missing") | Out-Null
                    continue
                }
                $actual = (Get-JmoaSha256 -Path $entryPath).ToUpperInvariant()
                if ($actual -ne ([string]$entry.sha256).ToUpperInvariant()) {
                    $filesValid = $false
                    $invalidFiles.Add("$($entry.path):sha256") | Out-Null
                }
            }
        }
        $passed = ($integrityExists -and $actualIntegritySha -eq $expectedIntegritySha -and $filesValid)
        $relative = $ledgerDirectory.Substring($childLedgerRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        if (-not $passed) { $reasons.Add("child ledger '$relative' failed integrity validation") | Out-Null }
        $records.Add([ordered]@{
            kind                      = 'STAGE'
            ledger                    = $relative
            stage                     = [string]$summary.stage
            variant                   = [string]$summary.variant
            status                    = [string]$summary.status
            commandCount              = [int]$summary.commandCount
            integritySha256Expected   = $expectedIntegritySha
            integritySha256Actual     = $actualIntegritySha
            filesValid                = $filesValid
            invalidFiles              = $invalidFiles.ToArray()
            passed                    = $passed
        }) | Out-Null
    }
    foreach ($armSummaryFile in @(Get-ChildItem -LiteralPath $childLedgerRoot -Filter 'arm-ledger-summary.json' -Recurse -File | Sort-Object FullName)) {
        $armSummary = Get-Content -Raw -LiteralPath $armSummaryFile.FullName | ConvertFrom-Json
        $armDirectory = $armSummaryFile.Directory.FullName
        $markdownPath = Join-Path $armDirectory ([string]$armSummary.markdownPath)
        $markdownExists = Test-Path -LiteralPath $markdownPath -PathType Leaf
        $actualMarkdownSha = if ($markdownExists) { (Get-JmoaSha256 -Path $markdownPath).ToUpperInvariant() } else { '' }
        $expectedMarkdownSha = ([string]$armSummary.markdownSha256).ToUpperInvariant()
        $sourcesValid = $true
        foreach ($source in @($armSummary.sourceLedgers)) {
            $sourceIntegrityPath = Join-Path ([string]$source.ledgerDirectory) 'child-ledger-integrity.json'
            if (-not (Test-Path -LiteralPath $sourceIntegrityPath -PathType Leaf) -or
                (Get-JmoaSha256 -Path $sourceIntegrityPath).ToUpperInvariant() -ne ([string]$source.integritySha256).ToUpperInvariant()) {
                $sourcesValid = $false
            }
        }
        $passed = ([bool]$armSummary.passed -and $markdownExists -and $actualMarkdownSha -eq $expectedMarkdownSha -and $sourcesValid)
        $relative = $armDirectory.Substring($childLedgerRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        if (-not $passed) { $reasons.Add("arm ledger '$relative' failed integrity validation") | Out-Null }
        $records.Add([ordered]@{
            kind                    = 'ARM'
            ledger                  = $relative
            stage                   = 'complete-arm'
            variant                 = [string]$armSummary.variant
            status                  = if ($passed) { 'COMPLETE' } else { 'FAILED' }
            commandCount            = [int]$armSummary.commandCount
            integritySha256Expected = $expectedMarkdownSha
            integritySha256Actual   = $actualMarkdownSha
            filesValid              = $sourcesValid
            invalidFiles            = @()
            passed                  = $passed
        }) | Out-Null
    }
    $totalCommands = 0
    foreach ($record in $records) {
        if ([string]$record.kind -eq 'STAGE') { $totalCommands += [int]$record.commandCount }
    }
    $index = [ordered]@{
        schemaVersion = 'jmoa-campaign-child-ledger-index-v1'
        generatedAt   = [DateTime]::UtcNow.ToString('o')
        root          = $childLedgerRoot
        ledgerCount   = $records.Count
        commandCount  = $totalCommands
        passed        = ($reasons.Count -eq 0)
        reasons       = $reasons.ToArray()
        ledgers       = $records.ToArray()
    }
    $indexPath = Join-Path $reportDir 'child-ledger-index.json'
    Write-JmoaJson -Value $index -Path $indexPath
    Add-ScenarioAsset -Role 'Child command-ledger integrity index' -Path $indexPath -Provenance GENERATED_IN_SCENARIO `
        -Note 'Hashes and verifies every launch/workload/capture/teardown/preflight child ledger and all raw command responses.' | Out-Null
    if (-not $index.passed) { throw "One or more child command ledgers failed integrity validation: $($reasons -join '; ')" }
    return $index
}

# ---- Input validation (fail-closed) -----------------------------------------------------------
foreach ($script in @($launchScript, $workloadScript, $stopScript, $screenScript, $noiseAnalyzer, $hostPreflightScript)) {
    if (-not (Test-Path -LiteralPath $script -PathType Leaf)) { throw "Required campaign script missing: $script" }
}
foreach ($artifact in @($B0Artifact, $V2Artifact, $MaterializationManifest, $ArtifactLineage)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Required frozen artifact missing: $artifact" }
}
if (-not (Test-Path -LiteralPath $FixturesReport -PathType Leaf)) {
    throw "Gate A fixture report missing: $FixturesReport"
}
if (-not (Test-Path -LiteralPath $ConfigRepo -PathType Container)) { throw "Config repository missing: $ConfigRepo" }
if ($Pairs -lt 3) { throw 'A trusted product gate requires at least three balanced pairs.' }

Start-ScenarioLedger -ScenarioId $runId -OutputDirectory $runDir -Description @'
Frozen-artifact PetClinic performance campaign. Immutable B0/V2 images and jars are measured under an
identical full stack (config + discovery + customers), identical NO_CDS_LOW_DIRTY policy, identical
workload, warmup, settle, and cold page cache. Gate C manifest integrity, image-identity, artifact-SHA,
transformation, and artifact-lineage gates run first; the config repo is frozen and re-validated before
every screen pair; two reversed same-artifact noise controls, three balanced pairs, semantic and
data-state equivalence, and the V2-C/V2-D mojos decide a trusted median-PSS win.
'@

$fixtures = Get-Content -Raw -LiteralPath $FixturesReport | ConvertFrom-Json
$fixtureReasons = New-Object System.Collections.Generic.List[string]
if ([string](Get-CampaignJsonProp $fixtures 'schemaVersion') -ne 'jmoa-campaign-fixtures-v1') {
    $fixtureReasons.Add('unsupported Gate A fixture report schema') | Out-Null
}
if (-not [bool](Get-CampaignJsonProp $fixtures 'passed')) {
    $fixtureReasons.Add('Gate A fixture report is not passing') | Out-Null
}
foreach ($testedFile in @($fixtures.testedFiles)) {
    $logicalPath = [string]$testedFile.logicalPath
    $expectedSha = [string]$testedFile.sha256
    $localPath = Join-Path $repositoryRoot ($logicalPath -replace '/', '\')
    if (-not (Test-Path -LiteralPath $localPath -PathType Leaf)) {
        $fixtureReasons.Add("Gate A tested file is missing: $logicalPath") | Out-Null
        continue
    }
    $actualSha = (Get-JmoaSha256 -Path $localPath).ToUpperInvariant()
    if ($actualSha -ne $expectedSha.ToUpperInvariant()) {
        $fixtureReasons.Add("Gate A fixture report is stale for $logicalPath") | Out-Null
    }
}
$fixturesPassed = ($fixtureReasons.Count -eq 0)
Add-ScenarioAsset -Role 'Gate A fixture report' -Path $FixturesReport -Provenance REUSED_FROZEN_INPUT -Note 'Must pass and match the current SHA-256 of every tested campaign script.' | Out-Null
if (-not $fixturesPassed) {
    Add-ScenarioNote -Title 'Gate A fixtures FAILED' -Text ($fixtureReasons -join '; ')
    Complete-ScenarioLedger -Status 'STOPPED_FIXTURE_GATE' -Result @{ reasons = @($fixtureReasons) } | Out-Null
    throw 'Gate A fixtures failed or are stale; campaign stopped before measurement.'
}

Add-ScenarioNote -Title 'Gate C - Campaign manifest integrity' -Text "Manifest: $resolvedManifestPath. campaignSha256 recomputed=$actualCampaignSha (matches recorded)."
Add-ScenarioAsset -Role 'Signed campaign manifest' -Path $resolvedManifestPath -Provenance REUSED_FROZEN_INPUT -Note 'Gate C: campaign SHA-256 verified over the canonical manifest body.' | Out-Null

# ---- Step 1: environment ledger (review Issue #9) ---------------------------------------------
$javaVersionCmd = Invoke-ScenarioCommand -Step 'Record campaign java -version' -Executable $javaExecutable -Arguments @('-version')
$mvnVersionCmd = Invoke-ScenarioCommand -Step 'Record campaign mvn -version' -Executable $MavenExecutable -Arguments @('-version')
$environmentLedger = [ordered]@{
    javaHome         = $resolvedJavaHome
    javaVersionText  = ($javaVersionCmd.stderr + $javaVersionCmd.stdout).Trim()
    mavenExecutable  = $MavenExecutable
    mavenVersionText = ($mvnVersionCmd.stdout + $mvnVersionCmd.stderr).Trim()
    containerCli     = $ContainerCli
    runtimePolicy    = $RuntimePolicy
    pluginCoordinates = $PluginCoordinates
}
Write-JmoaJson -Value $environmentLedger -Path (Join-Path $reportDir 'environment.json')

# ---- Step 2: image identity gate (review Issue #2) --------------------------------------------
function Resolve-CampaignImageId {
    param([string]$Ref, [string]$Step)
    $cmd = Invoke-ScenarioCommand -Step $Step -Executable $ContainerCli -Arguments @('image', 'inspect', '--format', '{{.Id}}', $Ref)
    return $cmd.stdout.Trim()
}
$resolvedB0Id = Resolve-CampaignImageId -Ref $B0Image -Step 'Resolve frozen B0 image identity'
$resolvedV2Id = Resolve-CampaignImageId -Ref $V2Image -Step 'Resolve frozen V2 image identity'
$resolvedConfigId = Resolve-CampaignImageId -Ref $ConfigImageId -Step 'Resolve config support image identity'
$resolvedDiscoveryId = Resolve-CampaignImageId -Ref $DiscoveryImageId -Step 'Resolve discovery support image identity'
$imageIdentityChecks = [ordered]@{
    baselineMatched  = ($resolvedB0Id -eq $expectedB0ImageId)
    candidateMatched = ($resolvedV2Id -eq $expectedV2ImageId)
    configMatched    = ($resolvedConfigId -eq $expectedConfigImageId)
    discoveryMatched = ($resolvedDiscoveryId -eq $expectedDiscoveryImageId)
}
$imageIdentityGate = [ordered]@{
    metadataVersion = 'jmoa-campaign-image-identity-v1'
    resolved        = [ordered]@{ baseline = $resolvedB0Id; candidate = $resolvedV2Id; config = $resolvedConfigId; discovery = $resolvedDiscoveryId }
    expected        = [ordered]@{ baseline = $expectedB0ImageId; candidate = $expectedV2ImageId; config = $expectedConfigImageId; discovery = $expectedDiscoveryImageId }
    checks          = $imageIdentityChecks
    passed          = -not ($imageIdentityChecks.Values -contains $false)
}
Write-JmoaJson -Value $imageIdentityGate -Path (Join-Path $reportDir 'image-identity-gate.json')
if (-not $imageIdentityGate.passed) {
    Add-ScenarioNote -Title 'Image identity gate FAILED' -Text 'A running image ID does not match the manifest-pinned immutable ID.'
    Complete-ScenarioLedger -Status 'STOPPED_ARTIFACT_GATE' -Result @{ imageIdentityGate = $imageIdentityGate } | Out-Null
    throw 'Image identity gate failed; campaign stopped before any measurement.'
}

Add-ScenarioAsset -Role 'Frozen B0 application jar' -Path $B0Artifact -Provenance REUSED_FROZEN_INPUT -Note 'Strict baseline, measured but never rebuilt.' | Out-Null
Add-ScenarioAsset -Role 'Frozen V2 application jar' -Path $V2Artifact -Provenance REUSED_FROZEN_INPUT -Note 'Transformed candidate, measured but never rebuilt.' | Out-Null
Add-ScenarioAsset -Role 'Frozen V2 materialization manifest' -Path $MaterializationManifest -Provenance REUSED_FROZEN_INPUT -Note 'Authoritative record of replaced jars and runtime library.' | Out-Null
Add-ScenarioAsset -Role 'Artifact lineage' -Path $ArtifactLineage -Provenance REUSED_FROZEN_INPUT -Note 'Produced by the adoption scenario; consumed by the lineage gate.' | Out-Null

# ---- Step 3a: artifact SHA gate ----------------------------------------------------------------
$b0ArtifactSha = (Get-JmoaSha256 -Path $B0Artifact).ToUpperInvariant()
$v2ArtifactSha = (Get-JmoaSha256 -Path $V2Artifact).ToUpperInvariant()
$manifestArtifactSha = (Get-JmoaSha256 -Path $MaterializationManifest).ToUpperInvariant()
$lineageFileSha = (Get-JmoaSha256 -Path $ArtifactLineage).ToUpperInvariant()
$artifactShaChecks = [ordered]@{
    baselineMatched      = ($b0ArtifactSha -eq $ExpectedB0Sha256.ToUpperInvariant())
    candidateMatched     = ($v2ArtifactSha -eq $ExpectedV2Sha256.ToUpperInvariant())
    manifestMatched      = ($manifestArtifactSha -eq $ExpectedMaterializationManifestSha256.ToUpperInvariant())
    lineageFileMatched   = ([string]::IsNullOrWhiteSpace($expectedLineageSha) -or $lineageFileSha -eq $expectedLineageSha.ToUpperInvariant())
}
$artifactShaGate = [ordered]@{
    metadataVersion = 'jmoa-campaign-artifact-sha-gate-v1'
    resolved        = [ordered]@{ baseline = $b0ArtifactSha; candidate = $v2ArtifactSha; materializationManifest = $manifestArtifactSha; lineageFile = $lineageFileSha }
    expected        = [ordered]@{ baseline = $ExpectedB0Sha256; candidate = $ExpectedV2Sha256; materializationManifest = $ExpectedMaterializationManifestSha256; lineageFile = $expectedLineageSha }
    checks          = $artifactShaChecks
    passed          = -not ($artifactShaChecks.Values -contains $false)
}
Write-JmoaJson -Value $artifactShaGate -Path (Join-Path $reportDir 'artifact-sha-gate.json')
if (-not $artifactShaGate.passed) {
    Add-ScenarioNote -Title 'Artifact SHA gate FAILED' -Text 'A frozen artifact hash does not match the manifest-recorded SHA-256.'
    Complete-ScenarioLedger -Status 'STOPPED_ARTIFACT_GATE' -Result @{ artifactShaGate = $artifactShaGate } | Out-Null
    throw 'Artifact SHA gate failed; campaign stopped before any measurement.'
}

# ---- Step 3b: hard transformation gates (fail-closed) -----------------------------------------
Add-ScenarioNote -Title 'Step 3b - Transformation gates' -Text 'Assert B0 carries no JMOA transformation and V2 carries the full transformation (all 24 reduced deps), against the running images.'
Initialize-CampaignAuditLedger -LedgerDirectory $artifactPreflightLedger -Stage 'preflight-artifact' -Variant 'B0_V2' `
    -Description 'Immutable image artifact probes for strict B0 cleanliness and complete V2 materialization.' | Out-Null
$baselineFingerprints = Get-CampaignImageArtifactFingerprints -ContainerCli $ContainerCli -Image $B0Image -LedgerDirectory $artifactPreflightLedger -Step 'baseline gate: artifact fingerprints'
$baselineGate = Test-CampaignBaselineClean -ContainerCli $ContainerCli -Image $B0Image -LedgerDirectory $artifactPreflightLedger
$candidateGate = Test-CampaignCandidateTransformed -ContainerCli $ContainerCli -Image $V2Image -MaterializationManifestPath $MaterializationManifest -BaselineFingerprints $baselineFingerprints -LedgerDirectory $artifactPreflightLedger
$artifactGate = [ordered]@{
    metadataVersion = 'jmoa-campaign-artifact-gate-v1'
    generatedAt     = [DateTime]::UtcNow.ToString('o')
    baseline        = $baselineGate
    candidate       = $candidateGate
    passed          = ($baselineGate.passed -and $candidateGate.passed)
}
Write-JmoaJson -Value $artifactGate -Path (Join-Path $reportDir 'artifact-gate.json')
Complete-CampaignAuditLedger -LedgerDirectory $artifactPreflightLedger -Status $(if ($artifactGate.passed) { 'COMPLETE' } else { 'FAILED' }) `
    -Stage 'preflight-artifact' -Variant 'B0_V2' | Out-Null
if (-not $artifactGate.passed) {
    Publish-CampaignChildLedgerIndex | Out-Null
    Add-ScenarioNote -Title 'Artifact gate FAILED' -Text (($baselineGate.reasons + $candidateGate.reasons) -join '; ')
    Complete-ScenarioLedger -Status 'STOPPED_ARTIFACT_GATE' -Result @{ artifactGate = $artifactGate } | Out-Null
    throw 'Artifact gate failed; campaign stopped before any measurement.'
}

# ---- Step 3c: artifact lineage gate (review Issue #4) -----------------------------------------
$lineageGate = Test-CampaignArtifactLineage -LineagePath $ArtifactLineage `
    -ExpectedB0Sha256 $ExpectedB0Sha256 -ExpectedV2Sha256 $ExpectedV2Sha256 `
    -ExpectedMaterializationManifestSha256 $ExpectedMaterializationManifestSha256
Write-JmoaJson -Value $lineageGate -Path (Join-Path $reportDir 'artifact-lineage-gate.json')
if (-not $lineageGate.passed) {
    Add-ScenarioNote -Title 'Artifact lineage gate FAILED' -Text ($lineageGate.reasons -join '; ')
    Complete-ScenarioLedger -Status 'STOPPED_ARTIFACT_GATE' -Result @{ lineageGate = $lineageGate } | Out-Null
    throw 'Artifact lineage gate failed; campaign stopped before any measurement.'
}

# ---- Step 5: config freeze (review Issue #5) --------------------------------------------------
$initialConfigLedger = Join-Path $childLedgerRoot 'config-freeze-initial'
Initialize-CampaignAuditLedger -LedgerDirectory $initialConfigLedger -Stage 'config-freeze' -Variant 'SHARED' -Description 'Initial Git identity and cleanliness capture for the frozen config repository.' | Out-Null
$configFreeze = Get-CampaignConfigFreeze -ConfigRepo $ConfigRepo -LedgerDirectory $initialConfigLedger
Complete-CampaignAuditLedger -LedgerDirectory $initialConfigLedger -Status 'COMPLETE' -Stage 'config-freeze' -Variant 'SHARED' | Out-Null
Write-JmoaJson -Value $configFreeze -Path (Join-Path $reportDir 'config-freeze.json')
$configFreezeMatchesManifest = ($configFreeze.contentTreeSha256 -eq $expectedConfigTreeSha)
if (-not $configFreezeMatchesManifest) {
    Publish-CampaignChildLedgerIndex | Out-Null
    Add-ScenarioNote -Title 'Config freeze mismatch' -Text "Config content tree ($($configFreeze.contentTreeSha256)) does not match the manifest-frozen tree ($expectedConfigTreeSha)."
    Complete-ScenarioLedger -Status 'STOPPED_CONFIG_DRIFT' -Result @{ configFreeze = $configFreeze } | Out-Null
    throw 'Config repository does not match the manifest-frozen content tree; campaign stopped (STOPPED_CONFIG_DRIFT).'
}

# Snapshot the verified config tree into the campaign run. Runtime containers mount this copy, never
# the mutable source checkout. The source and snapshot are both re-hashed before every pair.
$frozenConfigRepo = Join-Path $runDir 'frozen-config-repo'
New-JmoaDirectory -Path $frozenConfigRepo
foreach ($entry in @(Get-ChildItem -LiteralPath $ConfigRepo -Force | Where-Object { $_.Name -ne '.git' })) {
    Copy-Item -LiteralPath $entry.FullName -Destination $frozenConfigRepo -Recurse -Force
}
$frozenConfigTreeSha = Get-CampaignTreeSha256 -Root $frozenConfigRepo
$frozenConfigPassed = ($frozenConfigTreeSha -eq $expectedConfigTreeSha)
$frozenConfigManifest = [ordered]@{
    schemaVersion         = 'jmoa-frozen-config-snapshot-v1'
    sourceRepo            = $ConfigRepo
    sourceGitHead         = $configFreeze.gitHead
    sourceContentSha256   = $configFreeze.contentTreeSha256
    snapshotDirectory     = $frozenConfigRepo
    snapshotContentSha256 = $frozenConfigTreeSha
    expectedContentSha256 = $expectedConfigTreeSha
    passed                = $frozenConfigPassed
    generatedAt           = [DateTime]::UtcNow.ToString('o')
}
$frozenConfigManifestPath = Join-Path $reportDir 'frozen-config-snapshot.json'
Write-JmoaJson -Value $frozenConfigManifest -Path $frozenConfigManifestPath
Add-ScenarioAsset -Role 'Frozen config snapshot manifest' -Path $frozenConfigManifestPath -Provenance GENERATED_IN_SCENARIO `
    -Note 'Runtime mounts the campaign-owned snapshot; source and snapshot hashes must both remain frozen.' | Out-Null
if (-not $frozenConfigPassed) {
    Publish-CampaignChildLedgerIndex | Out-Null
    Complete-ScenarioLedger -Status 'STOPPED_CONFIG_DRIFT' -Result @{ frozenConfig = $frozenConfigManifest } | Out-Null
    throw 'Frozen config snapshot does not match the manifest-frozen content tree.'
}

# Re-freeze and prove both config content trees are byte-identical before EVERY screen pair.
function Assert-CampaignConfigUnchanged {
    param([Parameter(Mandatory)][string]$Context)
    $safeContext = ($Context -replace '[^A-Za-z0-9._-]+', '-').Trim('-').ToLowerInvariant()
    $ledger = Join-Path $childLedgerRoot "config-$safeContext"
    Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'config-drift-check' -Variant 'SHARED' -Description "Config freeze immediately before $Context." | Out-Null
    $check = Test-CampaignConfigUnchanged -ConfigRepo $ConfigRepo -ExpectedContentTreeSha256 $expectedConfigTreeSha -LedgerDirectory $ledger
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status $(if ($check.passed) { 'COMPLETE' } else { 'FAILED' }) -Stage 'config-drift-check' -Variant 'SHARED' | Out-Null
    if (-not $check.passed) {
        Add-ScenarioNote -Title "Config drift before $Context" -Text ($check.reasons -join '; ')
        Publish-CampaignChildLedgerIndex | Out-Null
        Complete-ScenarioLedger -Status 'STOPPED_CONFIG_DRIFT' -Result @{ configDrift = $check; context = $Context } | Out-Null
        throw "Config repository drifted before $Context; campaign stopped (STOPPED_CONFIG_DRIFT)."
    }
    $snapshotSha = Get-CampaignTreeSha256 -Root $frozenConfigRepo
    if ($snapshotSha -ne $expectedConfigTreeSha) {
        Add-ScenarioNote -Title "Frozen config snapshot drift before $Context" -Text "snapshot contentTreeSha256=$snapshotSha, expected=$expectedConfigTreeSha."
        Publish-CampaignChildLedgerIndex | Out-Null
        Complete-ScenarioLedger -Status 'STOPPED_CONFIG_DRIFT' -Result @{ snapshotSha256 = $snapshotSha; context = $Context } | Out-Null
        throw "Frozen config snapshot drifted before $Context; campaign stopped (STOPPED_CONFIG_DRIFT)."
    }
    Add-ScenarioNote -Title "Config unchanged before $Context" -Text "source and snapshot contentTreeSha256=$($check.actualContentTreeSha256) (matches manifest)."
    return $check
}

function Invoke-CampaignScreenPair {
    param(
        [Parameter(Mandatory)][string]$CaptureRoot,
        [Parameter(Mandatory)][int]$PairIndex,
        [Parameter(Mandatory)][string]$FirstVariant,
        [Parameter(Mandatory)][string]$BaselineImage,
        [Parameter(Mandatory)][string]$CandidateImage,
        [Parameter(Mandatory)][string]$BaselineArtifact,
        [Parameter(Mandatory)][string]$CandidateArtifact,
        [Parameter(Mandatory)][string]$BaselineContainerName,
        [Parameter(Mandatory)][string]$CandidateContainerName,
        [Parameter(Mandatory)][string]$LedgerDirectory,
        [Parameter(Mandatory)][string]$Context
    )
    Assert-CampaignConfigUnchanged -Context $Context | Out-Null
    $baseParameters = @{
        Image = $BaselineImage
        ConfigImage = $ConfigImageId
        DiscoveryImage = $DiscoveryImageId
        ConfigRepo = $frozenConfigRepo
        CustomerReadyTimeoutSeconds = $HealthTimeoutSeconds
    }
    $candidateParameters = @{
        Image = $CandidateImage
        ConfigImage = $ConfigImageId
        DiscoveryImage = $DiscoveryImageId
        ConfigRepo = $frozenConfigRepo
        CustomerReadyTimeoutSeconds = $HealthTimeoutSeconds
    }
    $screenArguments = @{
        BaselineLaunchScript        = $launchScript
        CandidateLaunchScript       = $launchScript
        BaselineContainerName       = $BaselineContainerName
        CandidateContainerName      = $CandidateContainerName
        WorkloadScript              = $workloadScript
        HealthUrl                   = $healthUrl
        Service                     = $service
        LaunchMode                  = $launchMode
        RuntimePolicy               = $RuntimePolicy
        BaselineArtifactPath        = $BaselineArtifact
        CandidateArtifactPath       = $CandidateArtifact
        BaselineLaunchParameters    = $baseParameters
        CandidateLaunchParameters   = $candidateParameters
        StopScript                  = $stopScript
        ContainerCli                = $ContainerCli
        PairIndex                   = $PairIndex
        FirstVariant                = $FirstVariant
        CaptureRoot                 = $CaptureRoot
        LedgerDirectory             = $LedgerDirectory
        MallocArenaMax              = '1'
        WarmupSeconds               = $WarmupSeconds
        PostWorkloadSnapshotSeconds = @($SettleSeconds)
        HealthTimeoutSeconds        = $HealthTimeoutSeconds
        FailOnFailure               = $true
        CapturePodmanMachinePressure = $true
        MinPodmanAvailableMemoryBytes = $MinPodmanAvailableMemoryBytes
        MaxPodmanSwapUsedBytes = $MaxPodmanSwapUsedBytes
        MaxPodmanMemoryPressureSomeAvg10 = $MaxPodmanMemoryPressureSomeAvg10
        MaxPodmanMemoryPressureFullAvg10 = $MaxPodmanMemoryPressureFullAvg10
    }
    if ($DropPageCacheBeforeVariant) { $screenArguments.DropPageCacheBeforeVariant = $true }
    Add-ScenarioNote -Title "Screen pair $PairIndex ($FirstVariant) into $CaptureRoot" -Text "Baseline image: $BaselineImage; Candidate image: $CandidateImage; child ledger: $LedgerDirectory."
    & $screenScript @screenArguments
    if (-not $?) { throw "Screen pair $PairIndex failed in $CaptureRoot." }
}

# JDK identity is collected from every measured arm for a cross-arm parity gate (review item 12).
$armJdkIdentities = New-Object System.Collections.Generic.List[object]
function Add-CampaignArmJdk { param([string]$RunDirectory, [string]$Arm) $id = Get-CampaignArmJdkIdentity -RunDirectory $RunDirectory; $id.arm = $Arm; $armJdkIdentities.Add($id) | Out-Null }

$hostPreflightDir = Join-Path $reportDir 'host-preflight'
$hostPreflightLedger = Join-Path $childLedgerRoot 'host-preflight'
Add-ScenarioNote -Title 'Host and Podman preflight' -Text 'Capture Windows, WSL, Podman, port, container, VM-memory, swap, and PSI state immediately before any measured arm.'
& $hostPreflightScript -OutputDirectory $hostPreflightDir -ContainerCli $ContainerCli `
    -MinPodmanAvailableMemoryBytes $MinPodmanAvailableMemoryBytes -MaxPodmanSwapUsedBytes $MaxPodmanSwapUsedBytes `
    -MaxPodmanMemoryPressureSomeAvg10 $MaxPodmanMemoryPressureSomeAvg10 `
    -MaxPodmanMemoryPressureFullAvg10 $MaxPodmanMemoryPressureFullAvg10 -LedgerDirectory $hostPreflightLedger
if (-not $?) {
    Publish-CampaignChildLedgerIndex | Out-Null
    Complete-ScenarioLedger -Status 'STOPPED_HOST_PREFLIGHT' -Result @{ terminalVerdict = 'ENVIRONMENT_VARIANCE_TOO_HIGH'; report = (Join-Path $hostPreflightDir 'host-podman-preflight.json') } | Out-Null
    throw 'Host/Podman preflight did not qualify; no measured arm was launched.'
}
$hostPreflight = Get-Content -Raw -LiteralPath (Join-Path $hostPreflightDir 'host-podman-preflight.json') | ConvertFrom-Json
Add-ScenarioAsset -Role 'Host and Podman preflight' -Path (Join-Path $hostPreflightDir 'host-podman-preflight.json') `
    -Provenance GENERATED_IN_SCENARIO -Note 'Fail-closed environment fingerprint and pressure admission before controls.' | Out-Null

if ($DryRun) {
    Add-ScenarioNote -Title 'Dry run' -Text 'Manifest (Gate C), image identity, artifact SHA, transformation, lineage, and config-freeze gates validated. No pairs were executed.'
    $readiness = [ordered]@{
        metadataVersion = 'jmoa-campaign-readiness-v1'
        runId           = $runId
        generatedAt     = [DateTime]::UtcNow.ToString('o')
        manifest        = [ordered]@{ path = $resolvedManifestPath; campaignSha256 = $actualCampaignSha; gatePassed = $manifestGatePassed }
        environment     = $environmentLedger
        imageIdentity   = $imageIdentityGate
        artifactSha     = $artifactShaGate
        artifactGate    = $artifactGate
        lineageGate     = $lineageGate
        configFreeze    = [ordered]@{ contentTreeSha256 = $configFreeze.contentTreeSha256; gitHead = $configFreeze.gitHead; workingTreeClean = $configFreeze.workingTreeClean; matchesManifest = $configFreezeMatchesManifest }
        frozenConfig    = $frozenConfigManifest
        hostPreflight   = $hostPreflight
        sourceRevision  = $SourceRevision
        fixtures        = $fixtures
        plan            = [ordered]@{
            noiseControls = @('B0->B0 x2 reversed', 'V2->V2 x2 reversed')
            balancedOrder = @('B0->V2 (BASELINE_FIRST)', 'V2->B0 (CANDIDATE_FIRST)', 'B0->V2 (BASELINE_FIRST)')
            trustedGate   = "median PSS <= $TrustedPssGateKb KB"
        }
        gatesPassed     = ($fixturesPassed -and $manifestGatePassed -and $imageIdentityGate.passed -and $artifactShaGate.passed -and $artifactGate.passed -and $lineageGate.passed -and $configFreezeMatchesManifest -and [bool]$hostPreflight.passed)
    }
    $childLedgerIndex = Publish-CampaignChildLedgerIndex
    $readiness.childLedgers = $childLedgerIndex
    $readiness.gatesPassed = ($readiness.gatesPassed -and $childLedgerIndex.passed)
    Write-JmoaJson -Value $readiness -Path (Join-Path $reportDir 'campaign-readiness.json')
    $readinessMd = @"
# PetClinic Performance Campaign - Readiness Report

- Run (dry): ``$runId``
- Manifest: ``$resolvedManifestPath`` (Gate C: **$manifestGatePassed**, campaignSha256 ``$actualCampaignSha``)
- Source revision: ``$SourceRevision``

## Pre-flight gates
- Image identity: **$($imageIdentityGate.passed)**
- Artifact SHA: **$($artifactShaGate.passed)**
- Transformation (B0 clean / V2 all-24): **$($baselineGate.passed)** / **$($candidateGate.passed)**
- Artifact lineage: **$($lineageGate.passed)** (source revision matched: $($lineageGate.sourceRevisionMatched))
- Config freeze matches manifest: **$configFreezeMatchesManifest** (git HEAD ``$($configFreeze.gitHead)``, clean=$($configFreeze.workingTreeClean))
- Frozen config snapshot: **$frozenConfigPassed** (content SHA-256 ``$frozenConfigTreeSha``); this snapshot, not the mutable checkout, is mounted at runtime
- Host/Podman preflight: **$($hostPreflight.passed)** (VM available memory $($hostPreflight.podmanMachine.availableMemoryBytes) B, swap used $($hostPreflight.podmanMachine.swapUsedBytes) B)

## Environment
- java: $($environmentLedger.javaVersionText)
- mvn: $($environmentLedger.mavenVersionText)

## Fixtures
$(if ($null -ne $fixtures) { "- schema/parse fixtures: $([string](Get-CampaignJsonProp $fixtures 'passed'))" } else { '- (no fixtures report supplied)' })

## Overall
- Ready to launch: **$($readiness.gatesPassed)**

This is a DRY RUN. No screen pairs were executed. Review this report before authorizing the long run.
"@
    Write-JmoaText -Value $readinessMd -Path (Join-Path $reportDir 'campaign-readiness.md')
    Complete-ScenarioLedger -Status 'DRY_RUN_OK' -Result @{ readiness = $readiness } | Out-Null
    Write-Host "Dry run complete: readiness report at $(Join-Path $reportDir 'campaign-readiness.md') (ready=$($readiness.gatesPassed))."
    return
}

# ---- Step 6: staged same-artifact controls ------------------------------------------------------
function Test-CampaignNoiseControl {
    param(
        [Parameter(Mandatory)][string]$Label,
        [Parameter(Mandatory)][string]$Artifact,
        [Parameter(Mandatory)][string]$CaptureRoot,
        [Parameter(Mandatory)]$SemanticPair1,
        [Parameter(Mandatory)]$SemanticPair2
    )
    $safeLabel = $Label.ToLowerInvariant().Replace('_', '-')
    $input = [ordered]@{
        schema = 'jmoa-same-artifact-noise-input-v2'
        controls = @(
            [ordered]@{
                label = $Label
                artifactSha256 = (Get-JmoaSha256 -Path $Artifact)
                pairs = @(
                    [ordered]@{ id = "${Label}_p1"; order = 'FIRST_THEN_SECOND'; first = (Read-CampaignRunMemory -RunDirectory (Join-Path $CaptureRoot 'b1')); second = (Read-CampaignRunMemory -RunDirectory (Join-Path $CaptureRoot 'c1')); semanticErrors = $SemanticPair1.semanticErrors },
                    [ordered]@{ id = "${Label}_p2"; order = 'SECOND_THEN_FIRST'; first = (Read-CampaignRunMemory -RunDirectory (Join-Path $CaptureRoot 'c2')); second = (Read-CampaignRunMemory -RunDirectory (Join-Path $CaptureRoot 'b2')); semanticErrors = $SemanticPair2.semanticErrors }
                )
            }
        )
    }
    $inputPath = Join-Path $reportDir "noise-$safeLabel-input.json"
    $outputDir = Join-Path $reportDir "noise-$safeLabel"
    Write-JmoaJson -Value $input -Path $inputPath
    & $noiseAnalyzer -InputPath $inputPath -OutputDirectory $outputDir `
        -MaxPssDriftKb $MaxNoisePssDriftKb -MaxPrivateDirtyDriftKb $MaxNoisePrivateDirtyDriftKb `
        -MaxMemoryCurrentDriftBytes $MaxNoiseMemoryCurrentDriftBytes -MinReversedPairsPerControl $MinReversedNoisePairsPerControl | Out-Null
    $report = Get-Content -Raw -LiteralPath (Join-Path $outputDir 'same-artifact-noise.json') | ConvertFrom-Json
    $semanticErrors = [int]$SemanticPair1.semanticErrors + [int]$SemanticPair2.semanticErrors
    $environmentReports = @(
        foreach ($cell in @('b1', 'c1', 'b2', 'c2')) {
            Get-Content -Raw -LiteralPath (Join-Path $CaptureRoot "$cell\environment-validity.json") | ConvertFrom-Json
        }
    )
    $environmentQualified = @($environmentReports | Where-Object { -not [bool]$_.passed }).Count -eq 0
    return [ordered]@{
        metadataVersion = 'jmoa-campaign-noise-control-v3'
        label            = $Label
        driftQualified   = [bool]$report.qualified
        semanticErrors   = $semanticErrors
        environmentQualified = $environmentQualified
        qualified        = ([bool]$report.qualified -and $semanticErrors -eq 0 -and $environmentQualified)
        controls         = $report.controls
        thresholds       = $report.thresholds
        semanticPairs    = @($SemanticPair1, $SemanticPair2)
        environment      = $environmentReports
    }
}

Add-ScenarioNote -Title 'Step 6A - B0 same-artifact controls' -Text 'Run two reversed B0->B0 pairs and decide B0 repeatability before any V2 control is launched.'
Invoke-CampaignScreenPair -CaptureRoot $noiseB0Root -PairIndex 1 -FirstVariant 'BASELINE_FIRST' `
    -BaselineImage $B0Image -CandidateImage $B0Image -BaselineArtifact $B0Artifact -CandidateArtifact $B0Artifact `
    -BaselineContainerName 'pccamp-noiseb0-b1' -CandidateContainerName 'pccamp-noiseb0-c1' `
    -LedgerDirectory (Join-Path $childLedgerRoot 'noise-b0-pair1') -Context 'noise B0 pair 1'
Invoke-CampaignScreenPair -CaptureRoot $noiseB0Root -PairIndex 2 -FirstVariant 'CANDIDATE_FIRST' `
    -BaselineImage $B0Image -CandidateImage $B0Image -BaselineArtifact $B0Artifact -CandidateArtifact $B0Artifact `
    -BaselineContainerName 'pccamp-noiseb0-b2' -CandidateContainerName 'pccamp-noiseb0-c2' `
    -LedgerDirectory (Join-Path $childLedgerRoot 'noise-b0-pair2') -Context 'noise B0 pair 2'
foreach ($cell in @('b1', 'c1', 'b2', 'c2')) { Add-CampaignArmJdk -RunDirectory (Join-Path $noiseB0Root $cell) -Arm "noise-b0-$cell" }
$noiseB0Sem1 = Compare-CampaignSemantics -PairIndex 1 -BaselineSemanticPath (Join-Path $noiseB0Root 'b1\semantic-requests.json') -CandidateSemanticPath (Join-Path $noiseB0Root 'c1\semantic-requests.json')
$noiseB0Sem2 = Compare-CampaignSemantics -PairIndex 2 -BaselineSemanticPath (Join-Path $noiseB0Root 'b2\semantic-requests.json') -CandidateSemanticPath (Join-Path $noiseB0Root 'c2\semantic-requests.json')
$noiseB0Summary = Test-CampaignNoiseControl -Label 'B0_SAME' -Artifact $B0Artifact -CaptureRoot $noiseB0Root -SemanticPair1 $noiseB0Sem1 -SemanticPair2 $noiseB0Sem2
Write-JmoaJson -Value $noiseB0Summary -Path (Join-Path $reportDir 'noise-b0-summary.json')
if (-not $noiseB0Summary.qualified) {
    Publish-CampaignChildLedgerIndex | Out-Null
    Add-ScenarioNote -Title 'B0 controls did NOT qualify' -Text 'V2 controls and balanced pairs were not run.'
    Complete-ScenarioLedger -Status 'STOPPED_B0_RUNTIME_VARIANCE' -Result @{ terminalVerdict = 'ENVIRONMENT_VARIANCE_TOO_HIGH'; noise = $noiseB0Summary; artifactGate = $artifactGate } | Out-Null
    throw 'B0 same-artifact controls did not qualify (STOPPED_B0_RUNTIME_VARIANCE).'
}

Add-ScenarioNote -Title 'Step 6B - V2 same-artifact controls' -Text 'B0 qualified. Run two reversed V2->V2 pairs and decide transformed-artifact repeatability before balanced pairs.'
Invoke-CampaignScreenPair -CaptureRoot $noiseV2Root -PairIndex 1 -FirstVariant 'BASELINE_FIRST' `
    -BaselineImage $V2Image -CandidateImage $V2Image -BaselineArtifact $V2Artifact -CandidateArtifact $V2Artifact `
    -BaselineContainerName 'pccamp-noisev2-b1' -CandidateContainerName 'pccamp-noisev2-c1' `
    -LedgerDirectory (Join-Path $childLedgerRoot 'noise-v2-pair1') -Context 'noise V2 pair 1'
Invoke-CampaignScreenPair -CaptureRoot $noiseV2Root -PairIndex 2 -FirstVariant 'CANDIDATE_FIRST' `
    -BaselineImage $V2Image -CandidateImage $V2Image -BaselineArtifact $V2Artifact -CandidateArtifact $V2Artifact `
    -BaselineContainerName 'pccamp-noisev2-b2' -CandidateContainerName 'pccamp-noisev2-c2' `
    -LedgerDirectory (Join-Path $childLedgerRoot 'noise-v2-pair2') -Context 'noise V2 pair 2'
foreach ($cell in @('b1', 'c1', 'b2', 'c2')) { Add-CampaignArmJdk -RunDirectory (Join-Path $noiseV2Root $cell) -Arm "noise-v2-$cell" }
$noiseV2Sem1 = Compare-CampaignSemantics -PairIndex 1 -BaselineSemanticPath (Join-Path $noiseV2Root 'b1\semantic-requests.json') -CandidateSemanticPath (Join-Path $noiseV2Root 'c1\semantic-requests.json')
$noiseV2Sem2 = Compare-CampaignSemantics -PairIndex 2 -BaselineSemanticPath (Join-Path $noiseV2Root 'b2\semantic-requests.json') -CandidateSemanticPath (Join-Path $noiseV2Root 'c2\semantic-requests.json')
$noiseV2Summary = Test-CampaignNoiseControl -Label 'V2_SAME' -Artifact $V2Artifact -CaptureRoot $noiseV2Root -SemanticPair1 $noiseV2Sem1 -SemanticPair2 $noiseV2Sem2
Write-JmoaJson -Value $noiseV2Summary -Path (Join-Path $reportDir 'noise-v2-summary.json')
if (-not $noiseV2Summary.qualified) {
    Publish-CampaignChildLedgerIndex | Out-Null
    Add-ScenarioNote -Title 'V2 controls did NOT qualify' -Text 'B0 was quiet, but V2 repeatability failed. Balanced pairs were not run.'
    Complete-ScenarioLedger -Status 'STOPPED_V2_RUNTIME_VARIANCE' -Result @{ terminalVerdict = 'ENVIRONMENT_VARIANCE_TOO_HIGH'; b0Noise = $noiseB0Summary; v2Noise = $noiseV2Summary; artifactGate = $artifactGate } | Out-Null
    throw 'V2 same-artifact controls did not qualify (STOPPED_V2_RUNTIME_VARIANCE).'
}

$noiseSummary = [ordered]@{
    metadataVersion = 'jmoa-campaign-noise-v3'
    qualified = $true
    b0 = $noiseB0Summary
    v2 = $noiseV2Summary
}
Write-JmoaJson -Value $noiseSummary -Path (Join-Path $reportDir 'noise-summary.json')

# ---- Step 7: three balanced pairs [B0->V2, V2->B0, B0->V2] -------------------------------------
Add-ScenarioNote -Title 'Step 7 - Balanced pairs' -Text 'Run three balanced pairs with alternating first variant.'
for ($pair = 1; $pair -le $Pairs; $pair++) {
    $firstVariant = if (($pair % 2) -eq 0) { 'CANDIDATE_FIRST' } else { 'BASELINE_FIRST' }
    Invoke-CampaignScreenPair -CaptureRoot $balancedRoot -PairIndex $pair -FirstVariant $firstVariant `
        -BaselineImage $B0Image -CandidateImage $V2Image -BaselineArtifact $B0Artifact -CandidateArtifact $V2Artifact `
        -BaselineContainerName "pccamp-p$pair-b" -CandidateContainerName "pccamp-p$pair-c" `
        -LedgerDirectory (Join-Path $childLedgerRoot "balanced-pair$pair") -Context "balanced pair $pair"
    Add-CampaignArmJdk -RunDirectory (Join-Path $balancedRoot "b$pair") -Arm "balanced-b$pair"
    Add-CampaignArmJdk -RunDirectory (Join-Path $balancedRoot "c$pair") -Arm "balanced-c$pair"
}

# ---- Step 4: semantic equivalence + data-state proof per balanced pair -------------------------
$semanticResults = New-Object System.Collections.Generic.List[object]
$dataStateResults = New-Object System.Collections.Generic.List[object]
$totalSemanticErrors = 0
$dataStateConsistent = $true
for ($pair = 1; $pair -le $Pairs; $pair++) {
    $cmp = Compare-CampaignSemantics -PairIndex $pair `
        -BaselineSemanticPath (Join-Path $balancedRoot "b$pair\semantic-requests.json") `
        -CandidateSemanticPath (Join-Path $balancedRoot "c$pair\semantic-requests.json")
    $totalSemanticErrors += $cmp.semanticErrors
    $semanticResults.Add($cmp) | Out-Null
    $ds = Compare-CampaignDataState -PairIndex $pair `
        -BaselineDataStatePath (Join-Path $balancedRoot "b$pair\data-state.json") `
        -CandidateDataStatePath (Join-Path $balancedRoot "c$pair\data-state.json")
    if (-not $ds.passed) { $dataStateConsistent = $false }
    $dataStateResults.Add($ds) | Out-Null
}
$semanticReport = [ordered]@{
    metadataVersion = 'jmoa-campaign-semantic-v1'
    totalSemanticErrors = $totalSemanticErrors
    dataStateConsistent = $dataStateConsistent
    pairs = $semanticResults.ToArray()
    dataState = $dataStateResults.ToArray()
}
Write-JmoaJson -Value $semanticReport -Path (Join-Path $reportDir 'semantic-equivalence.json')

$balancedEnvironmentReports = @(
    for ($pair = 1; $pair -le $Pairs; $pair++) {
        foreach ($label in @('b', 'c')) {
            Get-Content -Raw -LiteralPath (Join-Path $balancedRoot "$label$pair\environment-validity.json") | ConvertFrom-Json
        }
    }
)
$balancedEnvironmentQualified = @($balancedEnvironmentReports | Where-Object { -not [bool]$_.passed }).Count -eq 0
Write-JmoaJson -Value ([ordered]@{
    schemaVersion = 'jmoa-balanced-environment-validity-v1'
    passed = $balancedEnvironmentQualified
    arms = $balancedEnvironmentReports
}) -Path (Join-Path $reportDir 'balanced-environment-validity.json')

# ---- JDK fingerprint parity across every measured arm (review item 12) -------------------------
$distinctJdk = @($armJdkIdentities | ForEach-Object { $_.identity } | Sort-Object -Unique)
$expectedJdkArmCount = 8 + (2 * $Pairs)
$singleJdkIdentity = ($distinctJdk.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace([string]$distinctJdk[0]))
$jdkParity = [ordered]@{
    metadataVersion = 'jmoa-campaign-jdk-parity-v1'
    armCount        = $armJdkIdentities.Count
    distinctCount   = $distinctJdk.Count
    expectedArmCount = $expectedJdkArmCount
    passed          = ($armJdkIdentities.Count -eq $expectedJdkArmCount -and $singleJdkIdentity)
    arms            = $armJdkIdentities.ToArray()
}
Write-JmoaJson -Value $jdkParity -Path (Join-Path $reportDir 'jdk-parity.json')

# Cross-check memory triple from the raw captures (independent of the mojo).
$balancedMemory = New-Object System.Collections.Generic.List[object]
for ($pair = 1; $pair -le $Pairs; $pair++) {
    $b = Read-CampaignRunMemory -RunDirectory (Join-Path $balancedRoot "b$pair")
    $c = Read-CampaignRunMemory -RunDirectory (Join-Path $balancedRoot "c$pair")
    $balancedMemory.Add([ordered]@{
        pair = $pair
        deltaPssKb = $c.pssKb - $b.pssKb
        deltaPrivateDirtyKb = $c.privateDirtyKb - $b.privateDirtyKb
        deltaMemoryCurrentBytes = $c.memoryCurrentBytes - $b.memoryCurrentBytes
        baseline = $b
        candidate = $c
    }) | Out-Null
}
$rawMedianPssKb = Get-CampaignMedian -Values @($balancedMemory | ForEach-Object { [double]$_.deltaPssKb })

# ---- V2-C evidence + V2-D attribution over the balanced captures -------------------------------
Add-ScenarioNote -Title 'V2-C evidence and V2-D attribution' -Text 'Run the real evidence and non-diagnostic attribution mojos over the balanced captures.'
$evidenceArgs = @(
    '-N', "${PluginCoordinates}:evidence", '-Djmoa.evidence.enabled=true', "-Djmoa.evidence.inputDir=$balancedRoot",
    "-Djmoa.evidence.outputDir=$evidenceDir", "-Djmoa.evidence.expectedPolicy=$RuntimePolicy",
    '-Djmoa.evidence.requireArtifactHashes=true', '-Djmoa.evidence.requireWorkloadZeroErrors=true',
    '-Djmoa.evidence.requireSmapsArithmetic=true', '-Djmoa.evidence.failOnInvalidRun=true'
)
$evidenceCmd = Invoke-ScenarioCommand -Step 'V2-C evidence analysis' -Executable $MavenExecutable -Arguments $evidenceArgs -WorkingDirectory $repositoryRoot -AllowFailure
$attributionArgs = @(
    '-N', "${PluginCoordinates}:attribution", '-Djmoa.attribution.enabled=true', "-Djmoa.attribution.inputDir=$balancedRoot",
    "-Djmoa.attribution.outputDir=$attributionDir", "-Djmoa.evidence.expectedPolicy=$RuntimePolicy",
    '-Djmoa.attribution.requireV2CValid=true', '-Djmoa.attribution.diagnosticOnly=false'
)
$attributionCmd = Invoke-ScenarioCommand -Step 'V2-D attribution analysis' -Executable $MavenExecutable -Arguments $attributionArgs -WorkingDirectory $repositoryRoot -AllowFailure

$confirmationPath = Join-Path $evidenceDir 'jmoa-paired-confirmation.json'
$validationPath = Join-Path $evidenceDir 'jmoa-evidence-validation.json'
$attributionPath = Join-Path $attributionDir 'jmoa-memory-attribution.json'
$confirmation = if (Test-Path -LiteralPath $confirmationPath -PathType Leaf) { Get-Content -Raw -LiteralPath $confirmationPath | ConvertFrom-Json } else { $null }
$validation = if (Test-Path -LiteralPath $validationPath -PathType Leaf) { Get-Content -Raw -LiteralPath $validationPath | ConvertFrom-Json } else { $null }
$attribution = if (Test-Path -LiteralPath $attributionPath -PathType Leaf) { Get-Content -Raw -LiteralPath $attributionPath | ConvertFrom-Json } else { $null }
foreach ($assetPair in @(
    @{ role = 'V2-C paired confirmation'; path = $confirmationPath },
    @{ role = 'V2-C evidence validation'; path = $validationPath },
    @{ role = 'V2-D memory attribution'; path = $attributionPath }
)) {
    if (Test-Path -LiteralPath $assetPair.path -PathType Leaf) { Add-ScenarioAsset -Role $assetPair.role -Path $assetPair.path -Provenance GENERATED_IN_SCENARIO -Note 'Mojo output, referenced and hashed by the parent ledger.' | Out-Null }
}

$verdict = if ($confirmation) { [string]$confirmation.verdict } else { 'NO_EVIDENCE' }
$pairedWins = if ($confirmation) { [int]$confirmation.pairedWins } else { 0 }
$medianPssKb = if ($confirmation) { [long]$confirmation.medianPssDeltaKb } else { 0 }
$medianPrivateDirtyKb = if ($confirmation) { [long]$confirmation.medianPrivateDirtyDeltaKb } else { 0 }
$medianMemoryCurrentBytes = if ($confirmation) { [long]$confirmation.medianMemoryCurrentDeltaBytes } else { 0 }
$runs = if ($validation) { [int]$validation.runs } else { 0 }
$invalidRuns = if ($validation) { [int]$validation.invalidRuns } else { 999 }

# V2-D verdict is parsed from the report (v2cValid + evidenceVerdict), not the exit code (review item 10).
$attributionV2cValid = if ($attribution) { [bool](Get-CampaignJsonProp $attribution 'v2cValid') } else { $false }
$attributionEvidenceVerdict = if ($attribution) { [string](Get-CampaignJsonProp $attribution 'evidenceVerdict') } else { 'NO_ATTRIBUTION' }
$v2dPassed = ($null -ne $attribution) -and $attributionV2cValid -and ($attributionCmd.exitCode -eq 0) -and (-not [string]::IsNullOrWhiteSpace($attributionEvidenceVerdict)) -and ($attributionEvidenceVerdict -ne 'NO_ATTRIBUTION')

# ---- Step 8: frozen product gate ---------------------------------------------------------------
$gateChecks = [ordered]@{
    verdictConfirmedWin       = ($verdict -eq 'CONFIRMED_WIN')
    sixValidArms              = ($runs -eq (2 * $Pairs) -and $invalidRuns -eq 0)
    pairedWinsAtLeastTwo      = ($pairedWins -ge 2)
    medianPssAtOrBelowGate    = ($medianPssKb -le $TrustedPssGateKb)
    medianPrivateDirtyAtGate  = ($medianPrivateDirtyKb -le $TrustedPrivateDirtyGateKb)
    medianMemoryCurrentAtGate = ($medianMemoryCurrentBytes -le $TrustedMemoryCurrentGateBytes)
    zeroSemanticErrors        = ($totalSemanticErrors -eq 0)
    dataStateConsistent       = $dataStateConsistent
    environmentQualified      = $balancedEnvironmentQualified
    jdkParity                 = $jdkParity.passed
    evidenceCommandOk         = ($evidenceCmd.exitCode -eq 0)
    attributionPassed         = $v2dPassed
}
$trustedWin = -not ($gateChecks.Values -contains $false)
$confirmationChecks = [ordered]@{}
foreach ($entry in $gateChecks.GetEnumerator()) {
    if ($entry.Key -ne 'medianPssAtOrBelowGate') { $confirmationChecks[$entry.Key] = $entry.Value }
}
$confirmedProductWin = -not ($confirmationChecks.Values -contains $false)
$terminalVerdict = if ($trustedWin) {
    'TRUSTED_PRODUCT_WIN'
} elseif ($confirmedProductWin) {
    'CONFIRMED_PRODUCT_WIN'
} else {
    'PRODUCT_EFFECT_NOT_CONFIRMED'
}
$substantialGate = if ($medianPssKb -le $TrustedPssGateKb) { 'SUBSTANTIAL_4MIB_GATE_MET' } else { 'SUBSTANTIAL_4MIB_GATE_NOT_MET' }
$productGate = [ordered]@{
    metadataVersion = 'jmoa-campaign-product-gate-v1'
    generatedAt     = [DateTime]::UtcNow.ToString('o')
    trustedWin      = $trustedWin
    confirmedProductWin = $confirmedProductWin
    terminalVerdict = $terminalVerdict
    substantialGate = $substantialGate
    verdict         = $verdict
    checks          = $gateChecks
    medians         = [ordered]@{
        pssKb              = $medianPssKb
        privateDirtyKb     = $medianPrivateDirtyKb
        memoryCurrentBytes = $medianMemoryCurrentBytes
        rawPssKbCrossCheck = $rawMedianPssKb
    }
    thresholds      = [ordered]@{
        pssKb              = $TrustedPssGateKb
        privateDirtyKb     = $TrustedPrivateDirtyGateKb
        memoryCurrentBytes = $TrustedMemoryCurrentGateBytes
    }
    attribution     = [ordered]@{ v2cValid = $attributionV2cValid; evidenceVerdict = $attributionEvidenceVerdict; passed = $v2dPassed }
    pairedWins      = $pairedWins
    validArms       = ($runs - $invalidRuns)
    totalArms       = $runs
}
Write-JmoaJson -Value $productGate -Path (Join-Path $reportDir 'product-gate.json')

# ---- Step 9: reconciliation --------------------------------------------------------------------
$reconciliation = [ordered]@{
    metadataVersion = 'jmoa-campaign-reconciliation-v1'
    generatedAt     = [DateTime]::UtcNow.ToString('o')
    observations    = @(
        [ordered]@{ source = 'phase-33m'; pairs = 3; wins = 3; medianPssKb = -4758; note = 'Historical balanced engine; consistent large win.' },
        [ordered]@{ source = 'historical-replay'; pairs = 3; wins = 0; medianPssKb = 8647; note = 'docs/v2-c/v2c-historical-replay-results.json; sign-reversed result retained as conflicting historical evidence.' },
        [ordered]@{ source = 'fresh-single-screen'; pairs = 1; wins = 1; medianPssKb = -3615; note = 'Marginal single-screen; authorized confirmation but below the trusted -4096 KB gate.' },
        [ordered]@{ source = 'this-campaign'; pairs = $Pairs; wins = $pairedWins; medianPssKb = $medianPssKb; note = "Balanced product gate under cold cache and validated arms." }
    )
    narrative = "The historical replay produced a sign-reversed result. A specific causal difference has not yet been proven. The replay is retained as conflicting historical evidence and is not used in the final campaign median."
}
Write-JmoaJson -Value $reconciliation -Path (Join-Path $reportDir 'reconciliation.json')

# ---- Step 10: reports --------------------------------------------------------------------------
$campaignSummary = [ordered]@{
    metadataVersion = 'jmoa-performance-campaign-v1'
    runId           = $runId
    generatedAt     = [DateTime]::UtcNow.ToString('o')
    trustedWin      = $trustedWin
    verdict         = $verdict
    manifest        = [ordered]@{ path = $resolvedManifestPath; campaignSha256 = $actualCampaignSha }
    sourceRevision  = $SourceRevision
    productGate     = $productGate
    imageIdentityGate = $imageIdentityGate
    artifactShaGate = $artifactShaGate
    artifactGate    = $artifactGate
    lineageGate     = $lineageGate
    configFreeze    = $configFreeze
    frozenConfig    = $frozenConfigManifest
    hostPreflight   = $hostPreflight
    jdkParity       = $jdkParity
    noise           = $noiseSummary
    semantic        = $semanticReport
    environment     = [ordered]@{ qualified = $balancedEnvironmentQualified; arms = $balancedEnvironmentReports }
    balancedMemory  = $balancedMemory.ToArray()
    reconciliation  = $reconciliation
    images          = [ordered]@{ baseline = $resolvedB0Id; candidate = $resolvedV2Id; config = $resolvedConfigId; discovery = $resolvedDiscoveryId }
    evidenceOutputDir = $evidenceDir
    attributionOutputDir = $attributionDir
    v2dPassed       = $v2dPassed
}
$childLedgerIndex = Publish-CampaignChildLedgerIndex
$campaignSummary.childLedgers = $childLedgerIndex
Write-JmoaJson -Value $campaignSummary -Path (Join-Path $reportDir 'campaign-summary.json')

$verdictBanner = $terminalVerdict
$reportMd = @"
# PetClinic Performance Campaign

- Run: ``$runId``
- Result: **$verdictBanner**
- Substantial gate: **$substantialGate**
- Manifest campaignSha256: ``$actualCampaignSha``
- V2-C verdict: ``$verdict``
- Median PSS delta: **$medianPssKb KB** (gate <= $TrustedPssGateKb KB)
- Median Private_Dirty delta: **$medianPrivateDirtyKb KB** (gate <= $TrustedPrivateDirtyGateKb KB)
- Median memory.current delta: **$medianMemoryCurrentBytes bytes** (gate <= $TrustedMemoryCurrentGateBytes B)
- Paired wins: **$pairedWins / $Pairs**
- Valid arms: **$($runs - $invalidRuns) / $runs**
- Semantic errors: **$totalSemanticErrors**; data-state consistent: **$dataStateConsistent**
- JDK parity: **$($jdkParity.passed)** ($($jdkParity.distinctCount) distinct across $($jdkParity.armCount) arms)
- V2-D attribution: **$(if ($v2dPassed) { 'PASSED' } else { 'FAILED' })** (v2cValid=$attributionV2cValid, evidenceVerdict=$attributionEvidenceVerdict)

## Pre-flight gates
- Image identity: **$($imageIdentityGate.passed)**; Artifact SHA: **$($artifactShaGate.passed)**
- Baseline clean: **$($baselineGate.passed)**; Candidate transformed: **$($candidateGate.passed)**
- Artifact lineage: **$($lineageGate.passed)**; Config freeze matches manifest: **$configFreezeMatchesManifest**

## Same-artifact noise
- Qualified: **$($noiseSummary.qualified)**
- B0: **$($noiseSummary.b0.qualified)** (drift=$($noiseSummary.b0.driftQualified), semantic errors=$($noiseSummary.b0.semanticErrors), environment=$($noiseSummary.b0.environmentQualified))
- V2: **$($noiseSummary.v2.qualified)** (drift=$($noiseSummary.v2.driftQualified), semantic errors=$($noiseSummary.v2.semanticErrors), environment=$($noiseSummary.v2.environmentQualified))

## Product gate checks
$(( $gateChecks.GetEnumerator() | ForEach-Object { "- $($_.Key): **$($_.Value)**" }) -join "`n")

## Reconciliation
$($reconciliation.narrative)
"@
Write-JmoaText -Value $reportMd -Path (Join-Path $reportDir 'campaign-report.md')

$finalStatus = $terminalVerdict
Complete-ScenarioLedger -Status $finalStatus -Result @{ trustedWin = $trustedWin; confirmedProductWin = $confirmedProductWin; substantialGate = $substantialGate; verdict = $verdict; medianPssKb = $medianPssKb; reportDir = $reportDir } | Out-Null

Write-Host "Campaign $runId complete: $verdictBanner (verdict=$verdict, median PSS=$medianPssKb KB). Reports: $reportDir"
if ($terminalVerdict -eq 'PRODUCT_EFFECT_NOT_CONFIRMED') { exit 2 }
exit 0

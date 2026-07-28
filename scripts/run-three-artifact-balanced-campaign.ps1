param(
    [Parameter(Mandatory)][string]$ConfigPath,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('Qualification', 'Final', 'Explain', 'All')][string]$Stage = 'All',
    [switch]$DryRun,
    [switch]$NoResume
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'three-artifact-campaign-common.ps1')

function Get-RequiredProperty {
    param($Object, [string]$Name)
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value -or ([string]$property.Value).Length -eq 0) {
        throw "Campaign config is missing required property: $Name"
    }
    $property.Value
}

function Assert-ArtifactFreeze {
    param($Config)
    $checks = [Collections.Generic.List[object]]::new()
    foreach ($variant in @('B0', 'V1', 'V2')) {
        $entry = $Config.variants.$variant
        $requiredPaths = @('artifactPath')
        if ([bool]$Config.appCdsEnabled) { $requiredPaths += 'cdsArchivePath' }
        foreach ($pathName in $requiredPaths) {
            $path = [string]$entry.$pathName
            $requiresLeaf = $pathName -eq 'cdsArchivePath'
            if (-not (Test-Path -LiteralPath $path) -or ($requiresLeaf -and -not (Test-Path -LiteralPath $path -PathType Leaf))) {
                throw "$variant $pathName is missing: $path"
            }
        }
        $actualArtifact = Get-CampaignArtifactSha256 ([string]$entry.artifactPath)
        $actualArchive = if ([bool]$Config.appCdsEnabled) {
            (Get-JmoaSha256 ([string]$entry.cdsArchivePath)).ToUpperInvariant()
        } else {
            ''
        }
        if ($actualArtifact -ne ([string]$entry.artifactSha256).ToUpperInvariant()) {
            throw "$variant artifact hash mismatch: expected $($entry.artifactSha256), got $actualArtifact"
        }
        if ([bool]$Config.appCdsEnabled -and $actualArchive -ne ([string]$entry.cdsArchiveSha256).ToUpperInvariant()) {
            throw "$variant CDS archive hash mismatch: expected $($entry.cdsArchiveSha256), got $actualArchive"
        }
        $checks.Add([ordered]@{
            variant = $variant
            artifactPath = [string]$entry.artifactPath
            artifactKind = if (Test-Path -LiteralPath ([string]$entry.artifactPath) -PathType Container) { 'DIRECTORY_TREE' } else { 'FILE' }
            artifactSha256 = $actualArtifact
            cdsArchivePath = [string]$entry.cdsArchivePath
            cdsArchiveSha256 = $actualArchive
            image = [string]$entry.image
        })
    }
    $checks.ToArray()
}

function Get-CampaignImplementationChecks {
    param($Config)
    $paths = @(
        $PSCommandPath,
        (Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'),
        (Join-Path $PSScriptRoot 'runtime-automation-common.ps1'),
        (Join-Path $PSScriptRoot 'campaign-audit-common.ps1'),
        (Join-Path $PSScriptRoot 'campaign-common.ps1'),
        (Join-Path $PSScriptRoot 'three-artifact-campaign-common.ps1'),
        (Join-Path $PSScriptRoot 'analyze-three-artifact-blocks.ps1'),
        (Join-Path $PSScriptRoot 'new-independent-session-evidence-adapter.ps1'),
        [string]$Config.launchScript,
        [string]$Config.stopScript,
        [string]$Config.workloadScript
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } | Select-Object -Unique
    @($paths | ForEach-Object {
        if (-not (Test-Path -LiteralPath $_ -PathType Leaf)) { throw "Frozen campaign script is missing: $_" }
        [ordered]@{
            path = (Resolve-Path -LiteralPath $_).Path
            sha256 = (Get-JmoaSha256 -Path $_).ToUpperInvariant()
        }
    })
}

function Assert-CampaignImplementationUnchanged {
    $current = Get-CampaignImplementationChecks -Config $config
    if ($current.Count -ne $implementationChecks.Count) {
        throw 'Campaign implementation file set changed after freeze. Use a new OutputDirectory.'
    }
    foreach ($expected in $implementationChecks) {
        $actual = @($current | Where-Object path -eq $expected.path)
        if ($actual.Count -ne 1 -or [string]$actual[0].sha256 -ne [string]$expected.sha256) {
            $actualHash = if ($actual.Count -eq 1) { [string]$actual[0].sha256 } else { '<missing>' }
            throw "Campaign implementation changed after freeze: $($expected.path); expected $($expected.sha256), got $actualHash. Preserve this campaign and use a new OutputDirectory."
        }
    }
}

function Get-Session {
    param(
        [string]$Variant,
        [string]$SessionId,
        [int]$Ordinal,
        [string]$Directory,
        [int]$Block = 0,
        [int]$Position = 0
    )
    Assert-CampaignImplementationUnchanged
    $expected = ([string]$config.variants.$Variant.artifactSha256).ToUpperInvariant()
    $attemptDirectories = @($Directory) + @(1..2 | ForEach-Object { "$Directory-retry-$_" })
    if (-not $NoResume) {
        foreach ($attemptDirectory in $attemptDirectories) {
            $resultPath = Join-Path $attemptDirectory 'session-result.json'
            if (Test-Path -LiteralPath $resultPath -PathType Leaf) {
                $existing = Get-Content -Raw -LiteralPath $resultPath | ConvertFrom-Json
                if (
                    [bool]$existing.valid -and [string]$existing.variant -eq $Variant -and
                    [string]$existing.artifactSha256 -eq $expected -and
                    [string]$existing.protocol -eq [string]$config.protocol
                ) {
                    Write-Host "Reusing valid frozen session $SessionId ($Variant) from $attemptDirectory."
                    return $existing
                }
                throw "Session $SessionId has a result that does not match the frozen protocol: $resultPath"
            }
        }
    }
    $effectiveDirectory = @(
        $attemptDirectories | Where-Object {
            -not (Test-Path -LiteralPath $_) -or
            @(Get-ChildItem -LiteralPath $_ -Force -ErrorAction SilentlyContinue).Count -eq 0
        }
    ) | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace([string]$effectiveDirectory)) {
        throw "Session $SessionId exhausted three preserved attempts. Inspect the invalid attempt ledgers before continuing."
    }
    Invoke-ThreeArtifactIndependentSession -Config $config -VariantConfig $config.variants.$Variant -Variant $Variant `
        -SessionId $SessionId -ExecutionOrdinal $Ordinal -OutputDirectory $effectiveDirectory -Block $Block -Position $Position
}

function New-LegAdapter {
    param(
        [string]$LegId,
        [string]$From,
        [string]$To,
        [object[]]$Blocks
    )
    $mappings = [Collections.Generic.List[object]]::new()
    foreach ($block in $Blocks) {
        $fromSession = @($block.sessions | Where-Object variant -eq $From)[0]
        $toSession = @($block.sessions | Where-Object variant -eq $To)[0]
        $mappings.Add([ordered]@{
            adapterRunId = "b$($block.block)"
            sessionId = [string]$fromSession.sessionId
            supportSessionId = [string]$fromSession.supportSessionId
            sourceRunDirectory = [string]$fromSession.runDirectory
            artifactSha256 = [string]$fromSession.artifactSha256
        })
        $mappings.Add([ordered]@{
            adapterRunId = "c$($block.block)"
            sessionId = [string]$toSession.sessionId
            supportSessionId = [string]$toSession.supportSessionId
            sourceRunDirectory = [string]$toSession.runDirectory
            artifactSha256 = [string]$toSession.artifactSha256
        })
    }
    $legRoot = Join-Path $analysisRoot $LegId.ToLowerInvariant()
    $indexPath = Join-Path $legRoot 'session-index.json'
    New-JmoaDirectory $legRoot
    Write-JmoaJson ([ordered]@{
        schemaVersion = 'jmoa-independent-session-index-v1'
        protocol = [string]$config.protocol
        service = [string]$config.service
        leg = $LegId
        mappings = $mappings.ToArray()
    }) $indexPath
    $adapter = Join-Path $legRoot 'evidence-adapter'
    & (Join-Path $PSScriptRoot 'new-independent-session-evidence-adapter.ps1') -SessionIndexPath $indexPath -OutputDirectory $adapter | Out-Null
    if (-not $?) { throw "$LegId evidence adapter failed." }
    [ordered]@{ leg = $LegId; adapter = $adapter; root = $legRoot }
}

function Invoke-LegEvidence {
    param($Leg)
    $evidenceDirectory = Join-Path $Leg.root 'v2c'
    $attributionDirectory = Join-Path $Leg.root 'v2d'
    $evidenceArguments = @(
        '-N', "$($config.pluginCoordinates):evidence", '-Djmoa.evidence.enabled=true',
        "-Djmoa.evidence.inputDir=$($Leg.adapter)", "-Djmoa.evidence.outputDir=$evidenceDirectory",
        "-Djmoa.evidence.expectedPolicy=$($config.runtimePolicy)",
        '-Djmoa.evidence.requireArtifactHashes=true',
        '-Djmoa.evidence.requireWorkloadZeroErrors=true',
        '-Djmoa.evidence.requireSmapsArithmetic=true',
        '-Djmoa.evidence.failOnInvalidRun=true'
    )
    $evidence = Invoke-AuditedExternal -Executable ([string]$config.mavenExecutable) -Arguments $evidenceArguments `
        -WorkingDirectory ([string]$config.repositoryRoot) -LedgerDirectory $analysisLedger -Step "V2-C $($Leg.leg) evidence"
    if ($evidence.exitCode -ne 0) { throw "V2-C failed for $($Leg.leg)." }
    $attributionArguments = @(
        '-N', "$($config.pluginCoordinates):attribution", '-Djmoa.attribution.enabled=true',
        "-Djmoa.attribution.inputDir=$($Leg.adapter)", "-Djmoa.attribution.outputDir=$attributionDirectory",
        "-Djmoa.evidence.expectedPolicy=$($config.runtimePolicy)",
        '-Djmoa.attribution.requireV2CValid=true',
        '-Djmoa.attribution.diagnosticOnly=false'
    )
    $attribution = Invoke-AuditedExternal -Executable ([string]$config.mavenExecutable) -Arguments $attributionArguments `
        -WorkingDirectory ([string]$config.repositoryRoot) -LedgerDirectory $analysisLedger -Step "V2-D $($Leg.leg) attribution"
    if ($attribution.exitCode -ne 0) { throw "V2-D failed for $($Leg.leg)." }
    $confirmation = Get-Content -Raw -LiteralPath (Join-Path $evidenceDirectory 'jmoa-paired-confirmation.json') | ConvertFrom-Json
    $attributionReport = Get-Content -Raw -LiteralPath (Join-Path $attributionDirectory 'jmoa-memory-attribution.json') | ConvertFrom-Json
    [ordered]@{
        leg = $Leg.leg
        v2cVerdict = [string]$confirmation.verdict
        pairedWins = [int]$confirmation.pairedWins
        pairs = [int]$confirmation.pairs
        medianPssDeltaKb = [long]$confirmation.medianPssDeltaKb
        medianPrivateDirtyDeltaKb = [long]$confirmation.medianPrivateDirtyDeltaKb
        medianMemoryCurrentDeltaBytes = [long]$confirmation.medianMemoryCurrentDeltaBytes
        v2dPassed = [bool]$attributionReport.v2cValid
        evidenceDirectory = $evidenceDirectory
        attributionDirectory = $attributionDirectory
    }
}

if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) { throw "Config does not exist: $ConfigPath" }
$config = Get-Content -Raw -LiteralPath $ConfigPath | ConvertFrom-Json
foreach ($name in @(
    'protocol', 'service', 'launchMode', 'runtimePolicy', 'launchScript', 'stopScript',
    'workloadScript', 'healthUrl', 'workloadId', 'repositoryRoot', 'mavenExecutable',
    'pluginCoordinates'
)) { [void](Get-RequiredProperty $config $name) }

$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
$sessionsRoot = Join-Path $resolvedOutput 'sessions'
$reportsRoot = Join-Path $resolvedOutput 'reports'
$analysisRoot = Join-Path $resolvedOutput 'analysis'
$analysisLedger = Join-Path $resolvedOutput 'analysis-command-ledger'
New-JmoaDirectory $resolvedOutput
New-JmoaDirectory $sessionsRoot
New-JmoaDirectory $reportsRoot
$configSha = (Get-JmoaSha256 $ConfigPath).ToUpperInvariant()
$implementationChecks = Get-CampaignImplementationChecks -Config $config
$freezePath = Join-Path $resolvedOutput 'campaign-freeze.json'
$artifactChecks = Assert-ArtifactFreeze $config
$orders = @(
    @('B0', 'V1', 'V2'),
    @('B0', 'V2', 'V1'),
    @('V1', 'B0', 'V2'),
    @('V1', 'V2', 'B0'),
    @('V2', 'B0', 'V1'),
    @('V2', 'V1', 'B0')
)
$freeze = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-campaign-freeze-v2'
    protocol = [string]$config.protocol
    service = [string]$config.service
    configPath = [IO.Path]::GetFullPath($ConfigPath)
    configSha256 = $configSha
    implementation = $implementationChecks
    artifacts = $artifactChecks
    qualificationOrder = @('B0', 'V1', 'V2')
    blockOrders = $orders
    protocolChangesAfterFirstBlock = 'FORBIDDEN'
    validLosingRunReplacement = 'FORBIDDEN'
    invalidRunReplacementReasons = @(
        'health failure', 'wrong artifact', 'wrong image', 'workload error',
        'capture failure', 'host interruption', 'swap or OOM', 'pressure violation'
    )
}
if (Test-Path -LiteralPath $freezePath -PathType Leaf) {
    $existingFreeze = Get-Content -Raw -LiteralPath $freezePath | ConvertFrom-Json
    if ([string]$existingFreeze.configSha256 -ne $configSha) {
        throw 'The campaign config changed after the campaign directory was frozen. Use a new OutputDirectory.'
    }
    $existingImplementation = @($existingFreeze.implementation)
    if ($existingImplementation.Count -ne $implementationChecks.Count) {
        throw 'The frozen campaign implementation file set differs from the current runner. Use a new OutputDirectory.'
    }
    foreach ($expected in $existingImplementation) {
        $actual = @($implementationChecks | Where-Object path -eq $expected.path)
        if ($actual.Count -ne 1 -or [string]$actual[0].sha256 -ne [string]$expected.sha256) {
            throw "The campaign implementation changed after the campaign directory was frozen: $($expected.path)"
        }
    }
} else {
    Write-JmoaJson $freeze $freezePath
}
if ($DryRun) {
    $freeze | ConvertTo-Json -Depth 12
    exit 0
}

$ordinal = 0
$qualification = [Collections.Generic.List[object]]::new()
foreach ($variant in @('B0', 'V1', 'V2')) {
    $ordinal++
    $sessionId = "qualification-$($variant.ToLowerInvariant())"
    $qualification.Add((Get-Session -Variant $variant -SessionId $sessionId -Ordinal $ordinal -Directory (Join-Path $sessionsRoot $sessionId)))
}
$qualificationDeltas = [ordered]@{
    b0ToV1 = [ordered]@{
        pssKb = [long]$qualification[1].pssKb - [long]$qualification[0].pssKb
        privateDirtyKb = [long]$qualification[1].privateDirtyKb - [long]$qualification[0].privateDirtyKb
        memoryCurrentBytes = [long]$qualification[1].memoryCurrentBytes - [long]$qualification[0].memoryCurrentBytes
    }
    v1ToV2 = [ordered]@{
        pssKb = [long]$qualification[2].pssKb - [long]$qualification[1].pssKb
        privateDirtyKb = [long]$qualification[2].privateDirtyKb - [long]$qualification[1].privateDirtyKb
        memoryCurrentBytes = [long]$qualification[2].memoryCurrentBytes - [long]$qualification[1].memoryCurrentBytes
    }
    b0ToV2 = [ordered]@{
        pssKb = [long]$qualification[2].pssKb - [long]$qualification[0].pssKb
        privateDirtyKb = [long]$qualification[2].privateDirtyKb - [long]$qualification[0].privateDirtyKb
        memoryCurrentBytes = [long]$qualification[2].memoryCurrentBytes - [long]$qualification[0].memoryCurrentBytes
    }
}
$qualificationReport = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-qualification-v1'
    service = [string]$config.service
    sessions = $qualification.ToArray()
    directDiagnosticDeltas = $qualificationDeltas
    passed = (@($qualification | Where-Object { -not [bool]$_.valid -or [int]$_.semanticErrors -ne 0 }).Count -eq 0)
    claimBoundary = 'Qualification establishes runtime and semantic validity. It is not a performance claim and has no arbitrary same-artifact range gate.'
}
Write-JmoaJson $qualificationReport (Join-Path $reportsRoot 'qualification.json')
if ($Stage -eq 'Qualification') { exit $(if ($qualificationReport.passed) { 0 } else { 2 }) }
if (-not $qualificationReport.passed) {
    Write-JmoaJson ([ordered]@{ terminalOutcome = 'RUNTIME_ENVIRONMENT_INVALID'; qualification = $qualificationReport }) (Join-Path $reportsRoot 'final-verdict.json')
    exit 2
}

$blocks = [Collections.Generic.List[object]]::new()
for ($block = 1; $block -le 6; $block++) {
    $order = $orders[$block - 1]
    $blockSessions = [Collections.Generic.List[object]]::new()
    for ($position = 1; $position -le 3; $position++) {
        $variant = $order[$position - 1]
        $ordinal++
        $sessionId = "block-$block-position-$position-$($variant.ToLowerInvariant())"
        $blockSessions.Add((Get-Session -Variant $variant -SessionId $sessionId -Ordinal $ordinal -Directory (Join-Path $sessionsRoot $sessionId) -Block $block -Position $position))
    }
    $blocks.Add([ordered]@{ block = $block; order = $order; sessions = $blockSessions.ToArray() })
}
$index = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-session-index-v1'
    protocol = [string]$config.protocol
    service = [string]$config.service
    blocks = $blocks.ToArray()
}
$indexPath = Join-Path $reportsRoot 'session-index.json'
Write-JmoaJson $index $indexPath
Assert-CampaignImplementationUnchanged
& (Join-Path $PSScriptRoot 'analyze-three-artifact-blocks.ps1') -SessionIndexPath $indexPath -OutputDirectory $analysisRoot | Out-Null
if (-not $?) { throw 'Three-artifact block analysis failed.' }
$blockAnalysis = Get-Content -Raw -LiteralPath (Join-Path $analysisRoot 'three-artifact-analysis.json') | ConvertFrom-Json
if ($Stage -eq 'Final') { exit 0 }

Initialize-CampaignAuditLedger -LedgerDirectory $analysisLedger -Stage analysis -Variant ALL -Description 'V2-C and V2-D analysis commands for all three direct campaign legs.' | Out-Null
$legs = @(
    (New-LegAdapter -LegId B0_TO_V1 -From B0 -To V1 -Blocks $blocks.ToArray()),
    (New-LegAdapter -LegId V1_TO_V2 -From V1 -To V2 -Blocks $blocks.ToArray()),
    (New-LegAdapter -LegId B0_TO_V2 -From B0 -To V2 -Blocks $blocks.ToArray())
)
$legResults = @($legs | ForEach-Object { Invoke-LegEvidence $_ })
Complete-CampaignAuditLedger -LedgerDirectory $analysisLedger -Status COMPLETE -Stage analysis -Variant ALL | Out-Null
$directEvidence = @($legResults | Where-Object leg -eq B0_TO_V2)[0]
$checks = [ordered]@{}
foreach ($property in $blockAnalysis.directProductGate.PSObject.Properties) {
    $checks[$property.Name] = [bool]$property.Value
}
$checks.v2c = ([string]$directEvidence.v2cVerdict -eq 'CONFIRMED_WIN')
$checks.v2d = [bool]$directEvidence.v2dPassed
$passed = -not ($checks.Values -contains $false)
$terminal = if ($passed) {
    'COMPLETE_PRODUCT_WIN'
} elseif ([string]$blockAnalysis.terminalOutcome -eq 'COMPLETE_PRODUCT_WIN_BELOW_4MIB' -and $checks.v2c -and $checks.v2d) {
    'COMPLETE_PRODUCT_WIN_BELOW_4MIB'
} else {
    [string]$blockAnalysis.terminalOutcome
}
$final = [ordered]@{
    schemaVersion = 'jmoa-three-artifact-final-verdict-v1'
    service = [string]$config.service
    protocol = [string]$config.protocol
    terminalOutcome = $terminal
    checks = $checks
    qualification = $qualificationReport
    blockAnalysis = $blockAnalysis
    evidence = $legResults
    claimBoundary = 'The headline B0-to-V2 result is measured directly across six balanced blocks. Historical results are references, not arithmetic inputs.'
}
Write-JmoaJson $final (Join-Path $reportsRoot 'final-verdict.json')
$final | ConvertTo-Json -Depth 20
if ($terminal -notin @('COMPLETE_PRODUCT_WIN', 'COMPLETE_PRODUCT_WIN_BELOW_4MIB')) { exit 2 }

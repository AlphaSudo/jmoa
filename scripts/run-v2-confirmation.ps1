param(
    [Parameter(Mandatory)][string]$BaselineLaunchScript,
    [Parameter(Mandatory)][string]$CandidateLaunchScript,
    [Parameter(Mandatory)][string]$BaselineContainerName,
    [Parameter(Mandatory)][string]$CandidateContainerName,
    [Parameter(Mandatory)][string]$WorkloadScript,
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string]$LaunchMode,
    [Parameter(Mandatory)][string]$RuntimePolicy,
    [Parameter(Mandatory)][string]$BaselineArtifactPath,
    [Parameter(Mandatory)][string]$CandidateArtifactPath,
    [Parameter(Mandatory)][string]$BaselineSemanticSmokeReport,
    [Parameter(Mandatory)][string]$CandidateSemanticSmokeReport,
    [Parameter(Mandatory)][string]$BaselineMaterializationProof,
    [Parameter(Mandatory)][string]$CandidateMaterializationProof,
    [string]$BaselineRuntimeVerificationPath = "",
    [string]$CandidateRuntimeVerificationPath = "",
    [string[]]$BaselineLaunchArguments = @(),
    [string[]]$CandidateLaunchArguments = @(),
    [string[]]$WorkloadArguments = @(),
    [string]$StopScript = "",
    [string[]]$StopScriptArguments = @(),
    [string]$ContainerCli = "podman",
    [int]$Pairs = 3,
    [string]$CaptureRoot = "target/jmoa-v2-confirmation",
    [string]$EvidenceOutputDir = "",
    [string]$AttributionOutputDir = "",
    [string]$MavenExecutable = "mvn",
    [string]$JavaHome = "",
    [string]$PluginCoordinates = "com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2",
    [ValidateSet('FIXED_BASELINE_FIRST', 'BALANCED')][string]$PairOrder = 'FIXED_BASELINE_FIRST',
    [int]$WarmupSeconds = 20,
    [switch]$DropPageCacheBeforeVariant,
    [string]$MallocArenaMax = "",
    [bool]$CdsEnabled = $false,
    [bool]$AppCdsEnabled = $false,
    [bool]$LeydenEnabled = $false,
    [bool]$JavaagentPresent = $false,
    [int]$HealthTimeoutSeconds = 90,
    [switch]$FailOnFailure
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Assert-PassedReport {
    param([string]$Path, [string]$Role)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Role report is missing: $Path" }
    $report = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    if ($report.status -ne 'PASSED') { throw "$Role report did not pass: $($report.status)" }
}

Assert-PassedReport -Path $BaselineSemanticSmokeReport -Role 'baseline semantic smoke'
Assert-PassedReport -Path $CandidateSemanticSmokeReport -Role 'candidate semantic smoke'
Assert-PassedReport -Path $BaselineMaterializationProof -Role 'baseline materialization proof'
Assert-PassedReport -Path $CandidateMaterializationProof -Role 'candidate materialization proof'
if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
    if (-not (Test-Path -LiteralPath $JavaHome -PathType Container)) {
        throw "JavaHome does not exist: $JavaHome"
    }
    $resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:JAVA_HOME = $resolvedJavaHome
    $env:Path = "$(Join-Path $resolvedJavaHome 'bin');$env:Path"
}
if ($Pairs -lt 3) { throw 'V2-C confirmation requires at least three valid pairs.' }
New-JmoaDirectory -Path $CaptureRoot
$screenScript = Join-Path $PSScriptRoot 'runtime-screen-pair.ps1'

for ($pair = 1; $pair -le $Pairs; $pair++) {
    $screenArguments = @{
        BaselineLaunchScript = $BaselineLaunchScript
        CandidateLaunchScript = $CandidateLaunchScript
        BaselineContainerName = $BaselineContainerName
        CandidateContainerName = $CandidateContainerName
        WorkloadScript = $WorkloadScript
        HealthUrl = $HealthUrl
        Service = $Service
        LaunchMode = $LaunchMode
        RuntimePolicy = $RuntimePolicy
        BaselineArtifactPath = $BaselineArtifactPath
        CandidateArtifactPath = $CandidateArtifactPath
        BaselineLaunchArguments = $BaselineLaunchArguments
        CandidateLaunchArguments = $CandidateLaunchArguments
        WorkloadArguments = $WorkloadArguments
        StopScript = $StopScript
        StopScriptArguments = $StopScriptArguments
        ContainerCli = $ContainerCli
        PairIndex = $pair
        HealthTimeoutSeconds = $HealthTimeoutSeconds
        CaptureRoot = $CaptureRoot
        BaselineRuntimeVerificationPath = if ($BaselineRuntimeVerificationPath) { $BaselineRuntimeVerificationPath } else { $BaselineMaterializationProof }
        CandidateRuntimeVerificationPath = if ($CandidateRuntimeVerificationPath) { $CandidateRuntimeVerificationPath } else { $CandidateMaterializationProof }
        FirstVariant = if ($PairOrder -eq 'BALANCED' -and ($pair % 2) -eq 0) { 'CANDIDATE_FIRST' } else { 'BASELINE_FIRST' }
        MallocArenaMax = $MallocArenaMax
        CdsEnabled = $CdsEnabled
        AppCdsEnabled = $AppCdsEnabled
        LeydenEnabled = $LeydenEnabled
        JavaagentPresent = $JavaagentPresent
        WarmupSeconds = $WarmupSeconds
        DropPageCacheBeforeVariant = $DropPageCacheBeforeVariant
        FailOnFailure = $true
    }
    & $screenScript @screenArguments
    if ($LASTEXITCODE -ne 0) { throw "Runtime screen pair $pair failed; confirmation stopped." }
}

$evidenceDir = if ([string]::IsNullOrWhiteSpace($EvidenceOutputDir)) { Join-Path $CaptureRoot 'jmoa-evidence' } else { $EvidenceOutputDir }
$evidenceArgs = @(
    '-N', "${PluginCoordinates}:evidence", '-Djmoa.evidence.enabled=true', "-Djmoa.evidence.inputDir=$CaptureRoot",
    "-Djmoa.evidence.outputDir=$evidenceDir", "-Djmoa.evidence.expectedPolicy=$RuntimePolicy",
    '-Djmoa.evidence.requireArtifactHashes=true', '-Djmoa.evidence.requireWorkloadZeroErrors=true',
    '-Djmoa.evidence.requireSmapsArithmetic=true', '-Djmoa.evidence.failOnInvalidRun=true'
)
$evidence = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments $evidenceArgs
Write-JmoaText -Value $evidence.output -Path (Join-Path $CaptureRoot 'v2o-evidence-command.log')
if ($evidence.exitCode -ne 0) { throw 'V2-C evidence analysis failed. No attribution or claim was produced.' }

$attributionDir = if ([string]::IsNullOrWhiteSpace($AttributionOutputDir)) { Join-Path $CaptureRoot 'jmoa-attribution' } else { $AttributionOutputDir }
$attributionArgs = @(
    '-N', "${PluginCoordinates}:attribution", '-Djmoa.attribution.enabled=true', "-Djmoa.attribution.inputDir=$CaptureRoot",
    "-Djmoa.attribution.outputDir=$attributionDir", "-Djmoa.evidence.expectedPolicy=$RuntimePolicy",
    '-Djmoa.attribution.requireV2CValid=true', '-Djmoa.attribution.diagnosticOnly=true'
)
$attribution = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments $attributionArgs
Write-JmoaText -Value $attribution.output -Path (Join-Path $CaptureRoot 'v2o-attribution-command.log')
if ($attribution.exitCode -ne 0) { throw 'V2-D attribution failed. Evidence may be valid, but no causal explanation was produced.' }

$summary = [ordered]@{
    metadataVersion = 'v2o-confirmation-wrapper'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = 'ANALYZED'
    pairs = $Pairs
    captureRoot = $CaptureRoot
    evidenceOutputDir = $evidenceDir
    attributionOutputDir = $attributionDir
    evidenceCommand = $evidence
    attributionCommand = $attribution
    claimBoundary = 'This wrapper delegates verdicts to V2-C and explanations to V2-D. It does not declare a runtime claim itself.'
}
Write-JmoaJson -Value $summary -Path (Join-Path $CaptureRoot 'v2o-confirmation-wrapper.json')
Write-Host "V2-O confirmation wrapper completed. Inspect V2-C and V2-D reports before making any claim."

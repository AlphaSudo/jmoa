param(
    [string]$EvidenceRoot = 'target/v2-patient-root-cause',
    [string]$StudyDirectory = '',
    [string]$MavenExecutable = 'mvn',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PluginCoordinates = 'com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2',
    [string]$ContainerCli = 'podman',
    [int]$WarmupSeconds = 20,
    [int]$PostWorkloadSnapshotSeconds = 20
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if ([string]::IsNullOrWhiteSpace($StudyDirectory)) {
    $StudyDirectory = Join-Path $EvidenceRoot 'runtime/base-cds-final-confirmation'
}
$preparationPath = Join-Path $StudyDirectory 'preparation-manifest.json'
if (-not (Test-Path -LiteralPath $preparationPath -PathType Leaf)) {
    throw 'Run prepare-patient-base-cds-final-confirmation.ps1 first.'
}
$preparation = Get-Content -Raw -LiteralPath $preparationPath | ConvertFrom-Json
if ($preparation.status -ne 'PREPARED') { throw 'BASE-CDS preparation did not pass.' }

$v1Launch = Join-Path $StudyDirectory 'patient-v1-jdk-base-cds-launch.ps1'
$v2Launch = Join-Path $StudyDirectory 'patient-v2-jdk-base-cds-launch.ps1'
$v1Artifact = Join-Path $EvidenceRoot 'artifacts/v1-final.jar'
$v2Artifact = Join-Path $EvidenceRoot 'artifacts/v2-final.jar'
$verification = Join-Path $EvidenceRoot 'artifacts/v2-patient-materialization-proof.json'
$stop = Join-Path $EvidenceRoot 'runtime/patient-stop.ps1'
$workload = Join-Path $EvidenceRoot 'runtime/patient-workload.ps1'
foreach ($path in @($v1Launch,$v2Launch,$v1Artifact,$v2Artifact,$verification,$stop,$workload)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required final experiment input is absent: $path" }
}

function Write-Terminal([string]$Status, [string]$Stage, [string]$Reason, $Details) {
    $terminal = [ordered]@{
        metadataVersion = 'patient-base-cds-final-verdict-v1'
        status = $Status
        stage = $Stage
        reason = $Reason
        runtimePolicy = 'JDK_BASE_CDS_LOW_DIRTY'
        details = $Details
        noFurtherPatientV2Experiments = $true
        generatedAt = [DateTime]::UtcNow.ToString('o')
    }
    Write-JmoaJson $terminal (Join-Path $StudyDirectory 'final-verdict.json')
    Write-Host $Status
}

function Invoke-BasePair([string]$Root, [int]$PairIndex, [string]$Order, [bool]$DiagnosticOnly) {
    New-JmoaDirectory $Root
    $pairPath = Join-Path $Root ("v2o-runtime-screen-pair-{0}.json" -f $PairIndex)
    if (-not (Test-Path -LiteralPath $pairPath -PathType Leaf)) {
        $parameters = @{
            BaselineLaunchScript = $v1Launch
            CandidateLaunchScript = $v2Launch
            BaselineContainerName = 'jmoa-v2-patient-app'
            CandidateContainerName = 'jmoa-v2-patient-app'
            WorkloadScript = $workload
            HealthUrl = 'http://localhost:18084/actuator/health'
            Service = 'patient-service'
            LaunchMode = 'SPRING_BOOT_FAT_JAR'
            RuntimePolicy = 'JDK_BASE_CDS_LOW_DIRTY'
            BaselineRuntimePolicy = 'JDK_BASE_CDS_LOW_DIRTY'
            CandidateRuntimePolicy = 'JDK_BASE_CDS_LOW_DIRTY'
            BaselineCdsEnabled = $true
            CandidateCdsEnabled = $true
            BaselineMallocArenaMax = '1'
            CandidateMallocArenaMax = '1'
            BaselineArtifactPath = $v1Artifact
            CandidateArtifactPath = $v2Artifact
            BaselineRuntimeArtifactPath = '/app/app.jar'
            CandidateRuntimeArtifactPath = '/app/app.jar'
            BaselineRuntimeVerificationPath = $verification
            CandidateRuntimeVerificationPath = $verification
            StopScript = $stop
            ContainerCli = $ContainerCli
            PairIndex = $PairIndex
            FirstVariant = $Order
            CaptureRoot = $Root
            WorkloadId = 'patient-final-v1-v2-jdk-base-cds'
            WarmupSeconds = $WarmupSeconds
            PostWorkloadSnapshotSeconds = @($PostWorkloadSnapshotSeconds)
            DropPageCacheBeforeVariant = $true
            DiagnosticOnly = $DiagnosticOnly
            HealthTimeoutSeconds = 240
            FailOnFailure = $true
        }
        & (Join-Path $PSScriptRoot 'runtime-screen-pair.ps1') @parameters
        if ($LASTEXITCODE -ne 0) { throw "Patient BASE-CDS pair $PairIndex failed. See $Root." }
    }
    $pair = Get-Content -Raw -LiteralPath $pairPath | ConvertFrom-Json
    if ($pair.status -ne 'CAPTURED' -or $pair.baseArchiveIdentity.status -ne 'PASSED') {
        throw "Patient BASE-CDS pair $PairIndex did not prove an identical default archive."
    }
    foreach ($label in @('b','c')) {
        $run = Join-Path $Root "$label$PairIndex"
        $manifest = Get-Content -Raw -LiteralPath (Join-Path $run 'run-manifest.json') | ConvertFrom-Json
        $workloadResult = Get-Content -Raw -LiteralPath (Join-Path $run 'workload-result.json') | ConvertFrom-Json
        if ($manifest.runtimePolicy -ne 'JDK_BASE_CDS_LOW_DIRTY' -or $manifest.cdsMode -ne 'ON') { throw "Run $label$PairIndex has the wrong BASE-CDS policy." }
        if (-not $manifest.runtimePolicyProof.defaultJdkArchiveMapped -or $manifest.runtimePolicyProof.applicationArchiveMapped) { throw "Run $label$PairIndex failed archive mapping policy." }
        if ($manifest.runtimePolicyProof.defaultJdkArchiveSha256 -ne $preparation.jdk.defaultArchiveSha256) { throw "Run $label$PairIndex used a different default JDK archive than the preparation proof." }
        if ($workloadResult.requests -ne 600 -or $workloadResult.errorCount -ne 0) { throw "Run $label$PairIndex failed the 600-request semantic workload." }
    }
    return $pair
}

function Read-RunMetrics([string]$RunDirectory) {
    $rollup = @{}
    foreach ($line in Get-Content -LiteralPath (Join-Path $RunDirectory 'smaps_rollup.txt')) {
        if ($line -match '^(Pss|Private_Dirty):\s+(\d+)\s+kB') { $rollup[$Matches[1]] = [long]$Matches[2] }
    }
    return [ordered]@{
        pssKb = [long]$rollup.Pss
        privateDirtyKb = [long]$rollup.Private_Dirty
        memoryCurrentBytes = [long](Get-Content -Raw -LiteralPath (Join-Path $RunDirectory 'memory.current'))
    }
}

function Get-Delta([string]$Root, [int]$PairIndex) {
    $v1 = Read-RunMetrics (Join-Path $Root "b$PairIndex")
    $v2 = Read-RunMetrics (Join-Path $Root "c$PairIndex")
    return [ordered]@{
        pair = $PairIndex
        pssKb = $v2.pssKb - $v1.pssKb
        privateDirtyKb = $v2.privateDirtyKb - $v1.privateDirtyKb
        memoryCurrentBytes = $v2.memoryCurrentBytes - $v1.memoryCurrentBytes
    }
}

function Get-Median([long[]]$Values) {
    $sorted = @($Values | Sort-Object)
    return [long]$sorted[[int][Math]::Floor($sorted.Count / 2)]
}

try {
    $qualificationRoot = Join-Path $StudyDirectory 'qualification'
    Invoke-BasePair $qualificationRoot 0 'BASELINE_FIRST' $true | Out-Null
    $qualification = [ordered]@{
        status = 'BASE_CDS_POLICY_PROOF_PASSED'
        v1Requests = 600
        v2Requests = 600
        errors = 0
        defaultArchiveSha256 = $preparation.jdk.defaultArchiveSha256
    }
    Write-JmoaJson $qualification (Join-Path $StudyDirectory 'qualification.json')

    $screenRoot = Join-Path $StudyDirectory 'screen'
    Invoke-BasePair $screenRoot 1 'BASELINE_FIRST' $true | Out-Null
    $screenDelta = Get-Delta $screenRoot 1
    $screenPassed = $screenDelta.pssKb -le -1024 `
        -and $screenDelta.privateDirtyKb -le -1024 `
        -and $screenDelta.memoryCurrentBytes -le -1048576
    $screen = [ordered]@{
        metadataVersion = 'patient-base-cds-screen-v1'
        status = if ($screenPassed) { 'PROMOTED_TO_CONFIRMATION' } else { 'REJECTED' }
        deltas = $screenDelta
        gate = [ordered]@{ pssKbMaximum=-1024;privateDirtyKbMaximum=-1024;memoryCurrentBytesMaximum=-1048576 }
        workloadErrors = 0
        semanticLinkageErrors = 0
    }
    Write-JmoaJson $screen (Join-Path $StudyDirectory 'screen-verdict.json')
    if (-not $screenPassed) {
        Write-Terminal 'PATIENT_ALL_CDS_POLICIES_BLOCKED' 'SCREEN' 'The valid V1-to-V2 default-base-CDS screen failed the frozen promotion gate.' $screen
        exit 0
    }

    $confirmationRoot = Join-Path $StudyDirectory 'confirmation'
    $orders = @('BASELINE_FIRST','CANDIDATE_FIRST','BASELINE_FIRST')
    for ($index = 1; $index -le 3; $index++) {
        Invoke-BasePair $confirmationRoot $index $orders[$index - 1] $false | Out-Null
    }

    $v2cOutput = Join-Path $StudyDirectory 'v2c'
    if (-not (Test-Path -LiteralPath $JavaHome -PathType Container)) { throw "Configured JavaHome does not exist: $JavaHome" }
    $priorJavaHome = $env:JAVA_HOME
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $v2c = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments @(
        '-N', "${PluginCoordinates}:evidence",
        '-Djmoa.evidence.enabled=true',
        "-Djmoa.evidence.inputDir=$confirmationRoot",
        "-Djmoa.evidence.outputDir=$v2cOutput",
        '-Djmoa.evidence.expectedPolicy=JDK_BASE_CDS_LOW_DIRTY',
        '-Djmoa.evidence.requireArtifactHashes=true',
        '-Djmoa.evidence.requireWorkloadZeroErrors=true',
        '-Djmoa.evidence.requireSmapsArithmetic=true',
        '-Djmoa.evidence.failOnInvalidRun=true'
    )
    Write-JmoaText $v2c.output (Join-Path $StudyDirectory 'v2c-command.log')
    if ($v2c.exitCode -ne 0) {
        $env:JAVA_HOME = $priorJavaHome
        throw 'V2-C rejected the final BASE-CDS confirmation evidence.'
    }
    $v2cReport = Get-Content -Raw -LiteralPath (Join-Path $v2cOutput 'jmoa-evidence-analysis.json') | ConvertFrom-Json

    $v2dOutput = Join-Path $StudyDirectory 'v2d'
    $v2d = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments @(
        '-N', "${PluginCoordinates}:attribution",
        '-Djmoa.attribution.enabled=true',
        "-Djmoa.attribution.inputDir=$confirmationRoot",
        "-Djmoa.attribution.outputDir=$v2dOutput",
        '-Djmoa.evidence.expectedPolicy=JDK_BASE_CDS_LOW_DIRTY',
        '-Djmoa.attribution.requireV2CValid=true',
        '-Djmoa.attribution.diagnosticOnly=true'
    )
    Write-JmoaText $v2d.output (Join-Path $StudyDirectory 'v2d-command.log')
    $env:JAVA_HOME = $priorJavaHome
    if ($v2d.exitCode -ne 0) { throw 'V2-D could not attribute the final BASE-CDS confirmation.' }
    $v2dReport = Get-Content -Raw -LiteralPath (Join-Path $v2dOutput 'jmoa-memory-attribution.json') | ConvertFrom-Json

    $pairDeltas = @(1..3 | ForEach-Object { Get-Delta $confirmationRoot $_ })
    $medianPss = Get-Median @($pairDeltas | ForEach-Object { [long]$_.pssKb })
    $medianDirty = Get-Median @($pairDeltas | ForEach-Object { [long]$_.privateDirtyKb })
    $medianCurrent = Get-Median @($pairDeltas | ForEach-Object { [long]$_.memoryCurrentBytes })
    $pairedWins = @($pairDeltas | Where-Object { $_.pssKb -lt 0 -and $_.privateDirtyKb -lt 0 -and $_.memoryCurrentBytes -lt 0 }).Count
    $validRuns = [int]$v2cReport.validation.validRuns
    $v2cPassed = $v2cReport.verdict -eq 'CONFIRMED_WIN'
    $v2dPassed = [bool]$v2dReport.v2cValid -and $v2dReport.evidenceVerdict -eq 'CONFIRMED_WIN'
    $confirmed = $validRuns -eq 6 -and $pairedWins -ge 2 `
        -and $medianPss -le -4096 -and $medianDirty -le -1024 -and $medianCurrent -le -1048576 `
        -and $v2cPassed -and $v2dPassed
    $details = [ordered]@{
        validRuns = $validRuns
        pairedWins = $pairedWins
        medianPssDeltaKb = $medianPss
        medianPrivateDirtyDeltaKb = $medianDirty
        medianMemoryCurrentDeltaBytes = $medianCurrent
        pairs = $pairDeltas
        workloadErrors = 0
        semanticLinkageErrors = 0
        v2cVerdict = $v2cReport.verdict
        v2dPassed = $v2dPassed
        primaryAttribution = if ($v2dReport.causalHypotheses.Count -gt 0) { $v2dReport.causalHypotheses[0].hypothesis } else { 'UNKNOWN' }
    }
    if ($confirmed) {
        Write-Terminal 'PATIENT_BASE_CDS_V2_CONFIRMED' 'CONFIRMATION' 'The frozen final BASE-CDS gate passed.' $details
    } else {
        Write-Terminal 'PATIENT_ALL_CDS_POLICIES_BLOCKED' 'CONFIRMATION' 'The valid final BASE-CDS confirmation did not satisfy the frozen gate.' $details
    }
} catch {
    Write-Terminal 'PATIENT_BASE_CDS_ENVIRONMENT_INVALID' 'ENVIRONMENT' $_.Exception.Message $null
    throw
}

param(
    [Parameter(Mandatory)][string]$ArtifactPath,
    [Parameter(Mandatory)][string]$ArchivePath,
    [Parameter(Mandatory)][string]$TrainerExecutable,
    [string[]]$TrainerArguments = @(),
    [string]$TrainingWorkloadScript = "",
    [string[]]$TrainingWorkloadArguments = @(),
    [string]$HealthUrl = "",
    [int]$HealthTimeoutSeconds = 90,
    [string]$RuntimePolicy = "CDS",
    [string]$OutputDir = "target/jmoa-cds-training",
    [switch]$FailOnFailure
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

New-JmoaDirectory -Path $OutputDir
$artifactPresent = Test-Path -LiteralPath $ArtifactPath -PathType Leaf
if (-not $artifactPresent) {
    throw "Artifact does not exist: $ArtifactPath"
}
if ([string]::IsNullOrWhiteSpace($TrainerExecutable)) {
    throw 'TrainerExecutable is required. Pass an explicit command that trains the supplied archive path.'
}

$started = [DateTime]::UtcNow
$trainer = Invoke-JmoaExternal -Executable $TrainerExecutable -Arguments $TrainerArguments
$workload = $null
if ($trainer.exitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($TrainingWorkloadScript)) {
    if (-not (Test-Path -LiteralPath $TrainingWorkloadScript -PathType Leaf)) {
        throw "Training workload script does not exist: $TrainingWorkloadScript"
    }
    $workload = Invoke-JmoaExternal -Executable $TrainingWorkloadScript -Arguments $TrainingWorkloadArguments
}
$health = $null
if ($trainer.exitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($HealthUrl)) {
    $health = Wait-JmoaHttpHealth -HealthUrl $HealthUrl -TimeoutSeconds $HealthTimeoutSeconds
}

$archivePresent = Test-Path -LiteralPath $ArchivePath -PathType Leaf
$status = if ($trainer.exitCode -ne 0) {
    'FAILED_TRAINING_COMMAND'
} elseif (-not $archivePresent) {
    'FAILED_ARCHIVE_NOT_CREATED'
} elseif ($null -ne $workload -and $workload.exitCode -ne 0) {
    'FAILED_TRAINING_WORKLOAD'
} elseif ($null -ne $health -and -not $health.passed) {
    'FAILED_HEALTH'
} else {
    'TRAINED_NOT_MEASURED'
}

$report = [ordered]@{
    metadataVersion = 'v2o-cds-training-report'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = $status
    runtimePolicy = $RuntimePolicy
    artifactPath = $ArtifactPath
    artifactSha256 = Get-JmoaSha256 -Path $ArtifactPath
    cdsArchivePath = $ArchivePath
    cdsArchiveSha256 = Get-JmoaSha256 -Path $ArchivePath
    cdsArchiveBytes = if ($archivePresent) { (Get-Item -LiteralPath $ArchivePath).Length } else { 0 }
    trainer = $trainer
    trainingWorkload = $workload
    health = $health
    timestampStart = $started.ToString('o')
    timestampEnd = (Get-Date).ToUniversalTime().ToString('o')
    claimBoundary = 'CDS training helper only. Archive creation does not prove runtime mapping, semantic correctness, or a performance result.'
    nextGate = if ($status -eq 'TRAINED_NOT_MEASURED') { 'Run prove-runtime-materialization.ps1, then runtime-semantic-smoke.ps1.' } else { 'Repair the failed training prerequisite. Do not screen or claim performance.' }
}

Write-JmoaJson -Value $report -Path (Join-Path $OutputDir 'cds-training-report.json')
$markdown = @"
# JMOA CDS Training Report

- Status: ``$status``
- Artifact SHA-256: ``$($report.artifactSha256)``
- Archive SHA-256: ``$($report.cdsArchiveSha256)``
- Archive bytes: ``$($report.cdsArchiveBytes)``

This is a training record only. It does not establish archive mapping, semantic correctness, or a runtime memory claim.
"@
Write-JmoaText -Value $markdown -Path (Join-Path $OutputDir 'cds-training-report.md')

Write-Host "CDS training report written to $OutputDir"
if ($FailOnFailure -and $status -ne 'TRAINED_NOT_MEASURED') { exit 1 }
exit 0

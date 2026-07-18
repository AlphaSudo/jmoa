param(
    [string]$EvidenceRoot = 'target/v2-patient-root-cause',
    [string]$StudyDirectory = '',
    [string]$JdkImage = 'eclipse-temurin:26-jdk-jammy',
    [string]$ContainerCli = 'podman'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

if ([string]::IsNullOrWhiteSpace($StudyDirectory)) {
    $StudyDirectory = Join-Path $EvidenceRoot 'runtime/base-cds-final-confirmation'
}

$required = @(
    'artifacts/v1-final.jar',
    'artifacts/v2-final.jar',
    'artifacts/v2-patient-artifact-freeze.json',
    'artifacts/v2-patient-materialization-proof.json',
    'artifacts/v2-reduced-libs/raw-reducer-byte-preservation-report.json',
    'runtime/overlay-v1-nocds.yml',
    'runtime/overlay-v2-nocds.yml',
    'runtime/patient-v1-nocds-launch.ps1',
    'runtime/patient-v2-nocds-launch.ps1',
    'runtime/patient-stop.ps1',
    'runtime/patient-workload.ps1'
)
foreach ($relative in $required) {
    $path = Join-Path $EvidenceRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required Patient input is missing: $path" }
}

New-JmoaDirectory $StudyDirectory
$StudyDirectory = (Resolve-Path -LiteralPath $StudyDirectory).Path
$freeze = Get-Content -Raw -LiteralPath (Join-Path $EvidenceRoot 'artifacts/v2-patient-artifact-freeze.json') | ConvertFrom-Json
$materialization = Get-Content -Raw -LiteralPath (Join-Path $EvidenceRoot 'artifacts/v2-patient-materialization-proof.json') | ConvertFrom-Json
$preservation = Get-Content -Raw -LiteralPath (Join-Path $EvidenceRoot 'artifacts/v2-reduced-libs/raw-reducer-byte-preservation-report.json') | ConvertFrom-Json
$v1Artifact = Join-Path $EvidenceRoot 'artifacts/v1-final.jar'
$v2Artifact = Join-Path $EvidenceRoot 'artifacts/v2-final.jar'
$v1Sha = Get-JmoaSha256 $v1Artifact
$v2Sha = Get-JmoaSha256 $v2Artifact
if ($v1Sha -ne $freeze.v1.sha256 -or $v1Sha -ne $materialization.v1ArtifactSha256) { throw 'Accepted V1 artifact identity failed.' }
if ($v2Sha -ne $freeze.v2.sha256 -or $v2Sha -ne $materialization.v2ArtifactSha256) { throw 'Corrected V2 artifact identity failed.' }
if (-not [bool]$freeze.runtimeLibraryByteIdentical) { throw 'Runtime support library equality is not proven.' }
if ([int]$freeze.v1.dependencyManifest.count -ne 162 -or [int]$freeze.v2.dependencyManifest.count -ne 162) {
    throw 'The frozen 162-dependency identity contract is not satisfied.'
}
if ([int]$preservation.auditedClassCount -ne 32616 -or [int]$preservation.failedAuditCount -ne 0 -or -not [bool]$preservation.preservedNonTargetStructures) {
    throw 'The frozen reducer preservation audit is not the accepted 32,616-class zero-failure proof.'
}

function Write-Generated([string]$Value, [string]$Path) {
    Set-Content -LiteralPath $Path -Value $Value -Encoding utf8
}

function New-Launch([string]$Template, [string]$Overlay, [string]$Destination) {
    $text = Get-Content -Raw -LiteralPath $Template
    $updated = [regex]::Replace($text, "-OverlayFile\s+'[^']+'", ("-OverlayFile '" + $Overlay.Replace("'", "''") + "'"))
    if ($updated -eq $text) { throw "Could not replace OverlayFile in launch template: $Template" }
    Write-Generated $updated $Destination
}

$launches = [ordered]@{}
foreach ($version in @('v1', 'v2')) {
    $sourceOverlay = Join-Path $EvidenceRoot "runtime/overlay-$version-nocds.yml"
    $overlay = (Get-Content -Raw -LiteralPath $sourceOverlay).Replace('-Xshare:off', '-Xshare:on')
    if ($overlay -notmatch '(?i)-Xshare:on') { throw "Could not enable default JDK CDS for $version." }
    if ($overlay -match '(?i)(SharedArchiveFile|ArchiveClassesAtExit|AOTCache|AOTCacheOutput|AOTMode|AOTConfiguration|-javaagent:)') {
        throw "Generated $version BASE-CDS overlay contains a forbidden application archive, AOT, or javaagent option."
    }
    $overlayPath = Join-Path $StudyDirectory "overlay-$version-jdk-base-cds.yml"
    Write-Generated $overlay $overlayPath
    $launchPath = Join-Path $StudyDirectory "patient-$version-jdk-base-cds-launch.ps1"
    New-Launch (Join-Path $EvidenceRoot "runtime/patient-$version-nocds-launch.ps1") $overlayPath $launchPath
    $launches[$version] = [ordered]@{
        overlaySha256 = Get-JmoaSha256 $overlayPath
        launchSha256 = Get-JmoaSha256 $launchPath
    }
}

$probeCommand = 'p="$(dirname "$(readlink -f "$(command -v java)")")/../lib/server/classes_coh.jsa"; p="$(readlink -f "$p")"; test -f "$p"; echo "PATH=$p"; echo "SHA=$(sha256sum "$p" | awk ''{print $1}'')"; echo "IDENTITY=$(stat -Lc ''%d:%i'' "$p")"; java -XX:+UseCompactObjectHeaders -Xshare:on -version 2>&1 | head -n 1 | sed ''s/^/JAVA=/'''
$probe = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('run', '--rm', $JdkImage, 'sh', '-lc', $probeCommand)
if ($probe.exitCode -ne 0) { throw "Could not inspect the default JDK archive in $JdkImage.`n$($probe.output)" }
$probeValues = @{}
foreach ($line in ($probe.output -split "`r?`n")) {
    if ($line -match '^(PATH|SHA|IDENTITY|JAVA)=(.*)$') { $probeValues[$Matches[1]] = $Matches[2].Trim() }
}
foreach ($key in @('PATH', 'SHA', 'IDENTITY', 'JAVA')) {
    if ([string]::IsNullOrWhiteSpace($probeValues[$key])) { throw "Default archive probe did not emit $key." }
}

$manifest = [ordered]@{
    metadataVersion = 'patient-base-cds-final-preparation-v1'
    status = 'PREPARED'
    runtimePolicy = 'JDK_BASE_CDS_LOW_DIRTY'
    acceptedArtifacts = [ordered]@{ v1Sha256=$v1Sha;v2Sha256=$v2Sha }
    artifactContract = [ordered]@{
        dependencyCountV1 = [int]$freeze.v1.dependencyManifest.count
        dependencyCountV2 = [int]$freeze.v2.dependencyManifest.count
        runtimeLibraryByteIdentical = [bool]$freeze.runtimeLibraryByteIdentical
        rawAuditedClasses = [int]$preservation.auditedClassCount
        rawPreservationFailures = [int]$preservation.failedAuditCount
    }
    jdk = [ordered]@{
        image = $JdkImage
        build = $probeValues.JAVA
        defaultArchivePath = $probeValues.PATH
        defaultArchiveSha256 = $probeValues.SHA.ToUpperInvariant()
        defaultArchiveDeviceInodeAtProbe = $probeValues.IDENTITY
    }
    generatedLaunches = $launches
    forbiddenMechanisms = @('PATIENT_APPLICATION_ARCHIVE','ARCHIVE_TRAINING','AOT_CACHE','RUNTIME_JAVAAGENT')
    generatedAt = [DateTime]::UtcNow.ToString('o')
}
Write-JmoaJson $manifest (Join-Path $StudyDirectory 'preparation-manifest.json')
Write-Host "Prepared Patient final default-base-CDS experiment: $StudyDirectory"

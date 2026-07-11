param(
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$ArtifactPath,
    [Parameter(Mandatory)][string]$ArtifactPathInContainer,
    [Parameter(Mandatory)][string]$DependencySourceDir,
    [Parameter(Mandatory)][string]$DependencyDirInContainer,
    [Parameter(Mandatory)][string]$RuntimePolicy,
    [string]$CdsArchivePath = "",
    [string]$CdsArchivePathInContainer = "",
    [string]$ContainerCli = "podman",
    [string]$JavaProcessPattern = "java",
    [bool]$CdsEnabled = $false,
    [bool]$AppCdsEnabled = $false,
    [bool]$LeydenEnabled = $false,
    [bool]$JavaagentPresent = $false,
    [string]$OutputDir = "target/jmoa-runtime-materialization-proof",
    [switch]$FailOnFailure
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Get-ContainerSha256 {
    param([string]$Path)
    $escaped = $Path.Replace("'", "'`"'`"'")
    $result = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command "sha256sum '$escaped'"
    if ($result.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.output)) { return '' }
    return ($result.output -split '\s+' | Select-Object -First 1).ToUpperInvariant()
}

function Get-ContainerDependencyManifest {
    param([string]$Directory)
    $escaped = $Directory.Replace("'", "'`"'`"'")
    $result = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName `
        -Command "find '$escaped' -maxdepth 1 -type f -name '*.jar' -exec sha256sum {} \; | sort"
    if ($result.exitCode -ne 0) {
        return [pscustomobject]@{ present = $false; entries = @(); fingerprint = ''; command = $result }
    }
    $entries = @()
    foreach ($line in ($result.output -split "`r?`n")) {
        if ($line -notmatch '^([A-Fa-f0-9]{64})\s+(.+)$') { continue }
        $entries += [ordered]@{
            name = [System.IO.Path]::GetFileName($matches[2].Trim())
            sha256 = $matches[1].ToUpperInvariant()
        }
    }
    $entries = @($entries | Sort-Object name)
    $canonical = (($entries | ForEach-Object { "$($_.name)|$($_.sha256)" }) -join "`n")
    return [pscustomobject]@{
        present = $true
        entries = $entries
        fingerprint = Get-JmoaTextSha256 -Value $canonical
        command = $result
    }
}

New-JmoaDirectory -Path $OutputDir
$container = Test-JmoaContainerRunning -ContainerCli $ContainerCli -ContainerName $ContainerName
$artifactPresent = Test-Path -LiteralPath $ArtifactPath -PathType Leaf
$sourceDependencies = Get-JmoaDependencyManifest -Directory $DependencySourceDir
$checks = [ordered]@{
    containerRunning = $container.available
    artifactSourcePresent = $artifactPresent
    dependencySourcePresent = $sourceDependencies.present
    appArtifactHashMatches = $false
    dependencyLayerHashMatches = $false
    cdsArchiveHashMatches = $false
    cdsMappedAtRuntime = $false
    noCdsStateDeclared = ((-not $CdsEnabled) -and (-not $AppCdsEnabled) -and (-not $LeydenEnabled) -and (-not $JavaagentPresent))
}

$containerArtifactSha = ''
$containerDependencies = [pscustomobject]@{ present = $false; entries = @(); fingerprint = ''; command = $null }
$containerCdsSha = ''
$javaPid = ''
if ($container.available) {
    $containerArtifactSha = Get-ContainerSha256 -Path $ArtifactPathInContainer
    $checks.appArtifactHashMatches = $artifactPresent -and ($containerArtifactSha -eq (Get-JmoaSha256 -Path $ArtifactPath))
    $containerDependencies = Get-ContainerDependencyManifest -Directory $DependencyDirInContainer
    $checks.dependencyLayerHashMatches = $sourceDependencies.present -and $containerDependencies.present `
        -and ($sourceDependencies.fingerprint -eq $containerDependencies.fingerprint)
    $javaPid = Get-JmoaJavaPid -ContainerCli $ContainerCli -ContainerName $ContainerName -JavaProcessPattern $JavaProcessPattern
    if ($CdsEnabled -and -not [string]::IsNullOrWhiteSpace($CdsArchivePathInContainer)) {
        $containerCdsSha = Get-ContainerSha256 -Path $CdsArchivePathInContainer
        $hostCdsSha = Get-JmoaSha256 -Path $CdsArchivePath
        $checks.cdsArchiveHashMatches = -not [string]::IsNullOrWhiteSpace($hostCdsSha) -and ($hostCdsSha -eq $containerCdsSha)
        if (-not [string]::IsNullOrWhiteSpace($javaPid)) {
            $name = [System.IO.Path]::GetFileName($CdsArchivePathInContainer).Replace("'", "'`"'`"'")
            $maps = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName `
                -Command "grep -F '$name' /proc/$javaPid/maps || true"
            $checks.cdsMappedAtRuntime = -not [string]::IsNullOrWhiteSpace($maps.output)
        }
    }
}

$requiresCds = $RuntimePolicy.Trim().ToUpperInvariant() -match '(^|_)CDS($|_)' -and $RuntimePolicy.Trim().ToUpperInvariant() -notmatch '^NO_CDS'
$status = if (-not $checks.containerRunning) {
    'BLOCKED_CONTAINER_NOT_RUNNING'
} elseif (-not $checks.appArtifactHashMatches) {
    'FAILED_ARTIFACT_HASH_MISMATCH'
} elseif (-not $checks.dependencyLayerHashMatches) {
    'FAILED_DEPENDENCY_LAYER_MISMATCH'
} elseif ($requiresCds -and (-not $checks.cdsArchiveHashMatches -or -not $checks.cdsMappedAtRuntime)) {
    'FAILED_CDS_PROOF'
} elseif (-not $requiresCds -and -not $checks.noCdsStateDeclared) {
    'FAILED_NO_CDS_STATE'
} else {
    'PASSED'
}

$report = [ordered]@{
    metadataVersion = 'v2o-runtime-materialization-proof'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = $status
    containerName = $ContainerName
    containerId = if ($container.available) { Get-JmoaContainerId -ContainerCli $ContainerCli -ContainerName $ContainerName } else { '' }
    imageId = if ($container.available) { Get-JmoaContainerImageId -ContainerCli $ContainerCli -ContainerName $ContainerName } else { '' }
    javaPid = $javaPid
    runtimePolicy = $RuntimePolicy
    cdsEnabled = $CdsEnabled
    appCdsEnabled = $AppCdsEnabled
    leydenEnabled = $LeydenEnabled
    javaagentPresent = $JavaagentPresent
    appJarSha256 = Get-JmoaSha256 -Path $ArtifactPath
    containerAppJarSha256 = $containerArtifactSha
    candidateDependencyLayerSha256 = $sourceDependencies.fingerprint
    containerDependencyLayerSha256 = $containerDependencies.fingerprint
    cdsArchiveSha256 = Get-JmoaSha256 -Path $CdsArchivePath
    containerCdsArchiveSha256 = $containerCdsSha
    allMaterializedHashesMatchReduced = ($checks.appArtifactHashMatches -and $checks.dependencyLayerHashMatches)
    dynamicOriginsVerified = $false
    cdsMappedInMeasuredRuns = $checks.cdsMappedAtRuntime
    originalJarShadowing = (-not $checks.dependencyLayerHashMatches)
    checks = $checks
    sourceDependencies = $sourceDependencies.entries
    runtimeDependencies = $containerDependencies.entries
    claimBoundary = 'Materialization proof only. Hash equality does not prove semantic correctness, V2-C confirmation, V2-D attribution, or a performance result.'
}

Write-JmoaJson -Value $report -Path (Join-Path $OutputDir 'runtime-materialization-proof.json')
$markdown = @"
# JMOA Runtime Materialization Proof

- Status: ``$status``
- Runtime policy: ``$RuntimePolicy``
- Artifact hash matches: ``$($checks.appArtifactHashMatches)``
- Dependency-layer fingerprint matches: ``$($checks.dependencyLayerHashMatches)``
- CDS archive hash matches: ``$($checks.cdsArchiveHashMatches)``
- CDS mapped at runtime: ``$($checks.cdsMappedAtRuntime)``

This report proves the intended materialized layer was inspected. It does not make a semantic or performance claim.
"@
Write-JmoaText -Value $markdown -Path (Join-Path $OutputDir 'runtime-materialization-proof.md')

Write-Host "Runtime materialization proof written to $OutputDir"
if ($FailOnFailure -and $status -ne 'PASSED') { exit 1 }
exit 0

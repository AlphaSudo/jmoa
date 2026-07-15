param(
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$ArtifactPath,
    [Parameter(Mandatory)][string]$ArtifactPathInContainer,
    [Parameter(Mandatory)][string]$RuntimePolicy,
    [string]$ExpectedDependencyDir = '',
    [string]$CdsArchivePath = '',
    [string]$CdsArchivePathInContainer = '',
    [string]$ContainerCli = 'podman',
    [bool]$CdsEnabled = $false,
    [bool]$AppCdsEnabled = $false,
    [bool]$LeydenEnabled = $false,
    [bool]$JavaagentPresent = $false,
    [string]$OutputDir = 'target/jmoa-fat-jar-materialization-proof',
    [switch]$FailOnFailure
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-HashHex([byte[]]$Bytes) {
    ([BitConverter]::ToString($Bytes)).Replace('-', '').ToUpperInvariant()
}

function Get-EmbeddedManifest([string]$JarPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $records = @()
        foreach ($entry in @($archive.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' } | Sort-Object FullName)) {
            $sha = [Security.Cryptography.SHA256]::Create()
            $stream = $entry.Open()
            try { $hash = Get-HashHex $sha.ComputeHash($stream) } finally { $stream.Dispose(); $sha.Dispose() }
            $records += [ordered]@{ name = [IO.Path]::GetFileName($entry.FullName); bytes = [long]$entry.Length; sha256 = $hash }
        }
        $canonical = (($records | ForEach-Object { "$($_.name)|$($_.bytes)|$($_.sha256)" }) -join "`n")
        return [ordered]@{
            count = $records.Count
            bytes = [long](($records | ForEach-Object { [long]$_.bytes } | Measure-Object -Sum).Sum)
            fingerprint = Get-JmoaTextSha256 -Value $canonical
            entries = $records
        }
    } finally { $archive.Dispose() }
}

function Get-DirectoryManifest([string]$Directory) {
    if ([string]::IsNullOrWhiteSpace($Directory) -or -not (Test-Path -LiteralPath $Directory -PathType Container)) {
        return [ordered]@{ present = $false; count = 0; bytes = 0; fingerprint = ''; entries = @() }
    }
    $entries = @(
        Get-ChildItem -LiteralPath $Directory -Filter '*.jar' -File | Sort-Object Name | ForEach-Object {
            [ordered]@{ name = $_.Name; bytes = [long]$_.Length; sha256 = Get-JmoaSha256 -Path $_.FullName }
        }
    )
    $canonical = (($entries | ForEach-Object { "$($_.name)|$($_.bytes)|$($_.sha256)" }) -join "`n")
    [ordered]@{
        present = $true
        count = $entries.Count
        bytes = [long](($entries | ForEach-Object { [long]$_.bytes } | Measure-Object -Sum).Sum)
        fingerprint = Get-JmoaTextSha256 -Value $canonical
        entries = $entries
    }
}

function Get-ContainerSha256([string]$Path) {
    $escaped = $Path.Replace("'", "'`"'`"'")
    $result = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command "sha256sum '$escaped'"
    if ($result.exitCode -ne 0) { return '' }
    (($result.output -split '\s+' | Select-Object -First 1).Trim()).ToUpperInvariant()
}

New-JmoaDirectory -Path $OutputDir
$containerState = Test-JmoaContainerRunning -ContainerCli $ContainerCli -ContainerName $ContainerName
$artifactPresent = Test-Path -LiteralPath $ArtifactPath -PathType Leaf
$embedded = if ($artifactPresent) { Get-EmbeddedManifest -JarPath $ArtifactPath } else { [ordered]@{ count = 0; bytes = 0; fingerprint = ''; entries = @() } }
$expected = Get-DirectoryManifest -Directory $ExpectedDependencyDir
$containerArtifactSha = if ($containerState.available) { Get-ContainerSha256 -Path $ArtifactPathInContainer } else { '' }
$hostArtifactSha = Get-JmoaSha256 -Path $ArtifactPath
$cmdline = if ($containerState.available) {
    (Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command 'tr -d "\000" < /proc/1/cmdline').output
} else { '' }
$observedJavaagent = $cmdline -match '-javaagent'
$javaPid = if ($containerState.available) { Get-JmoaJavaPid -ContainerCli $ContainerCli -ContainerName $ContainerName } else { '' }
$hostCdsSha = Get-JmoaSha256 -Path $CdsArchivePath
$containerCdsSha = if ($containerState.available -and -not [string]::IsNullOrWhiteSpace($CdsArchivePathInContainer)) { Get-ContainerSha256 -Path $CdsArchivePathInContainer } else { '' }
$cdsMapped = $false
if ($containerState.available -and -not [string]::IsNullOrWhiteSpace($javaPid) -and -not [string]::IsNullOrWhiteSpace($CdsArchivePathInContainer)) {
    $archiveName = [IO.Path]::GetFileName($CdsArchivePathInContainer)
    $maps = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName -Command "grep -F '$archiveName' /proc/$javaPid/maps || true"
    $cdsMapped = -not [string]::IsNullOrWhiteSpace($maps.output)
}
$artifactMatches = $artifactPresent -and $hostArtifactSha -eq $containerArtifactSha
$dependencyMatches = [string]::IsNullOrWhiteSpace($ExpectedDependencyDir) -or ($expected.present -and $embedded.count -eq $expected.count -and $embedded.bytes -eq $expected.bytes -and $embedded.fingerprint -eq $expected.fingerprint)
$requiresCds = $RuntimePolicy.Trim().ToUpperInvariant() -match '(^|_)CDS($|_)' -and $RuntimePolicy.Trim().ToUpperInvariant() -notmatch '^NO_CDS'
$status = if (-not $containerState.available) {
    'BLOCKED_CONTAINER_NOT_RUNNING'
} elseif (-not $artifactMatches) {
    'FAILED_ARTIFACT_HASH_MISMATCH'
} elseif (-not $dependencyMatches) {
    'FAILED_EMBEDDED_DEPENDENCY_MANIFEST'
} elseif ($observedJavaagent -or $JavaagentPresent) {
    'FAILED_JAVAAGENT_POLICY'
} elseif ($requiresCds -and (-not $hostCdsSha -or $hostCdsSha -ne $containerCdsSha -or -not $cdsMapped)) {
    'FAILED_CDS_PROOF'
} elseif (-not $requiresCds -and ($CdsEnabled -or $AppCdsEnabled -or $LeydenEnabled)) {
    'FAILED_NO_CDS_POLICY'
} else {
    'PASSED'
}

$report = [ordered]@{
    metadataVersion = 'jmoa-fat-jar-materialization-proof-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = $status
    containerName = $ContainerName
    containerId = if ($containerState.available) { Get-JmoaContainerId -ContainerCli $ContainerCli -ContainerName $ContainerName } else { '' }
    imageId = if ($containerState.available) { Get-JmoaContainerImageId -ContainerCli $ContainerCli -ContainerName $ContainerName } else { '' }
    runtimePolicy = $RuntimePolicy
    cdsEnabled = $CdsEnabled
    appCdsEnabled = $AppCdsEnabled
    leydenEnabled = $LeydenEnabled
    javaagentPresent = $JavaagentPresent
    javaagentObservedInProcess = $observedJavaagent
    processCommandLine = $cmdline
    javaPid = $javaPid
    artifactPath = $ArtifactPath
    artifactSha256 = $hostArtifactSha
    containerArtifactPath = $ArtifactPathInContainer
    containerArtifactSha256 = $containerArtifactSha
    artifactMountHashMatches = $artifactMatches
    embeddedDependencyManifest = $embedded
    expectedDependencyManifest = $expected
    embeddedDependencyManifestMatches = $dependencyMatches
    cdsArchiveSha256 = $hostCdsSha
    containerCdsArchiveSha256 = $containerCdsSha
    cdsArchiveHashMatches = (-not $requiresCds) -or ($hostCdsSha -and $hostCdsSha -eq $containerCdsSha)
    cdsMappedAtRuntime = $cdsMapped
    claimBoundary = 'Materialization proof only. This report does not establish semantic correctness, V2-C confirmation, V2-D attribution, or a performance result.'
}
$report | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath (Join-Path $OutputDir 'fat-jar-materialization-proof.json') -Encoding UTF8
@"
# JMOA Fat-JAR Materialization Proof

- Status: ``$status``
- Runtime policy: ``$RuntimePolicy``
- Application mount hash matches: ``$artifactMatches``
- Embedded dependency manifest matches: ``$dependencyMatches``
- CDS archive hash matches: ``$($report.cdsArchiveHashMatches)``
- CDS archive mapped at runtime: ``$cdsMapped``
- Runtime javaagent observed: ``$observedJavaagent``

This is a materialization gate only. It is not a semantic or performance claim.
"@ | Set-Content -LiteralPath (Join-Path $OutputDir 'fat-jar-materialization-proof.md') -Encoding UTF8
Write-Host "Fat-JAR materialization proof written to $OutputDir"
if ($FailOnFailure -and $status -ne 'PASSED') { exit 1 }

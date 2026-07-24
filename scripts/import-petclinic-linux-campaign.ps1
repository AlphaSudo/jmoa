param(
    [Parameter(Mandatory)][string]$InputArchive,
    [Parameter(Mandatory)][string]$ExpectedArchiveSha256,
    [string]$InstallRoot = "$HOME/jmoa/petclinic-linux-campaign",
    [string]$JavaHome = '/opt/jdk-26',
    [string]$MavenExecutable = '/usr/bin/mvn',
    [string]$ContainerCli = '/usr/bin/podman'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$archive = (Resolve-Path -LiteralPath $InputArchive).Path
$actualArchiveSha = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
if ($actualArchiveSha -ne $ExpectedArchiveSha256.ToUpperInvariant()) {
    throw "Linux campaign archive hash mismatch: expected $ExpectedArchiveSha256, actual $actualArchiveSha"
}

$temp = Join-Path ([IO.Path]::GetTempPath()) ('jmoa-linux-import-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $temp | Out-Null
try {
    & tar -xf $archive -C $temp
    if ($LASTEXITCODE -ne 0) { throw 'Could not unpack campaign archive.' }
    $source = Join-Path $temp 'petclinic-linux-campaign'
    $portablePath = Join-Path $source 'manifest/portable-campaign-manifest.json'
    $portable = Get-Content -LiteralPath $portablePath -Raw | ConvertFrom-Json
    $runtimeCommon = Join-Path $source 'inputs/runtime-automation-common.ps1'
    if (-not (Test-Path -LiteralPath $runtimeCommon)) {
        $repoTar = Join-Path $source 'inputs/jmoa-repository.tar'
        $repoExtract = Join-Path $temp 'repo-check'
        New-Item -ItemType Directory -Force -Path $repoExtract | Out-Null
        & tar -xf $repoTar -C $repoExtract
        $runtimeCommon = Join-Path $repoExtract 'scripts/runtime-automation-common.ps1'
    }
    . $runtimeCommon
    $canonicalPath = Join-Path (Split-Path -Parent $runtimeCommon) 'campaign-canonical-json.ps1'
    $commonPath = Join-Path (Split-Path -Parent $runtimeCommon) 'campaign-common.ps1'
    . $canonicalPath
    . $commonPath
    $packageSha = Get-CampaignManifestSha256 -ManifestObject $portable
    if ($packageSha -ne ([string]$portable.packageSha256).ToUpperInvariant()) {
        throw "Portable package manifest hash mismatch: expected $($portable.packageSha256), actual $packageSha"
    }
    foreach ($file in @($portable.files)) {
        $path = Join-Path $source ([string]$file.path)
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Package file missing: $($file.path)" }
        $sha = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        if ($sha -ne ([string]$file.sha256).ToUpperInvariant()) { throw "Package file hash mismatch: $($file.path)" }
    }

    if (Test-Path -LiteralPath $InstallRoot) { Remove-Item -LiteralPath $InstallRoot -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
    Copy-Item -Path (Join-Path $source '*') -Destination $InstallRoot -Recurse -Force
    $repoRoot = Join-Path $InstallRoot 'repo'
    $configRoot = Join-Path $InstallRoot 'config'
    New-Item -ItemType Directory -Force -Path $repoRoot,$configRoot | Out-Null
    & tar -xf (Join-Path $InstallRoot 'inputs/jmoa-repository.tar') -C $repoRoot
    if ($LASTEXITCODE -ne 0) { throw 'Could not unpack JMOA repository.' }
    & tar -xf (Join-Path $InstallRoot 'inputs/config-repository.tar') -C $configRoot
    if ($LASTEXITCODE -ne 0) { throw 'Could not unpack config repository.' }
    & chmod +x (Join-Path $repoRoot 'scripts/linux-podman-compat.sh')

    $ledger = Join-Path $InstallRoot 'import-ledger'
    . (Join-Path $repoRoot 'scripts/campaign-audit-common.ps1')
    Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'linux-campaign-import' -Variant 'FROZEN' `
        -Description 'Verifies the exported package and imports exact OCI images. No image rebuild.' | Out-Null
    foreach ($image in @($portable.images)) {
        $result = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('load', '-i', (Join-Path $InstallRoot ([string]$image.archive))) `
            -LedgerDirectory $ledger -Step "load $($image.role) OCI archive"
        if ($result.exitCode -ne 0) { throw "Could not load $($image.role) image." }
        $inspect = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('image', 'inspect', '--format', '{{.Id}}', [string]$image.reference) `
            -LedgerDirectory $ledger -Step "verify $($image.role) loaded image ID"
        $loaded = ($inspect.stdout.Trim() -replace '^sha256:', '')
        $expected = ([string]$image.expectedImageId -replace '^sha256:', '')
        if ($loaded -ne $expected) { throw "Loaded $($image.role) image ID $loaded does not match $expected." }
    }

    $windowsManifest = Get-Content -LiteralPath (Join-Path $InstallRoot 'manifest/windows-campaign-manifest.json') -Raw | ConvertFrom-Json
    $configRepo = Get-ChildItem -LiteralPath $configRoot -Directory | Select-Object -First 1
    $windowsManifest.artifacts.baseline.path = Join-Path $InstallRoot 'inputs/petclinic-customers-b0.jar'
    $windowsManifest.artifacts.candidate.path = Join-Path $InstallRoot 'inputs/petclinic-customers-v2.jar'
    $windowsManifest.artifacts.materializationManifest.path = Join-Path $InstallRoot 'inputs/jmoa-materialization-manifest.json'
    $windowsManifest.artifactLineage.path = Join-Path $InstallRoot 'inputs/artifact-lineage.json'
    $windowsManifest.configRepo.path = $configRepo.FullName
    $windowsManifest.environment.javaHome = $JavaHome
    $windowsManifest.environment.mavenExecutable = $MavenExecutable
    $windowsManifest.environment.containerCli = Join-Path $repoRoot 'scripts/linux-podman-compat.sh'
    if (-not $windowsManifest.PSObject.Properties['sourceCampaignSha256']) {
        $windowsManifest | Add-Member -NotePropertyName sourceCampaignSha256 -NotePropertyValue ([string]$portable.sourceCampaignSha256)
    }
    if (-not $windowsManifest.PSObject.Properties['portablePackageSha256']) {
        $windowsManifest | Add-Member -NotePropertyName portablePackageSha256 -NotePropertyValue ([string]$portable.packageSha256)
    }
    $windowsManifest.campaignSha256 = Get-CampaignManifestSha256 -ManifestObject $windowsManifest
    $hostManifest = Join-Path $InstallRoot 'manifest/linux-campaign-manifest.json'
    Write-JmoaJson -Value $windowsManifest -Path $hostManifest
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status 'COMPLETE' -Stage 'linux-campaign-import' -Variant 'FROZEN' | Out-Null
    [ordered]@{
        installRoot = $InstallRoot
        hostManifest = $hostManifest
        sourceCampaignSha256 = $portable.sourceCampaignSha256
        hostBindingCampaignSha256 = $windowsManifest.campaignSha256
        loadedImages = @($portable.images).Count
    } | ConvertTo-Json -Depth 8
} finally {
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}

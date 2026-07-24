param(
    [Parameter(Mandatory)][string]$CampaignManifest,
    [Parameter(Mandatory)][string]$FixturesReport,
    [Parameter(Mandatory)][string]$OutputArchive,
    [string]$ContainerCli = 'podman'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-canonical-json.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$manifestPath = (Resolve-Path -LiteralPath $CampaignManifest).Path
$fixturePath = (Resolve-Path -LiteralPath $FixturesReport).Path
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$actualCampaignSha = Get-CampaignManifestSha256 -ManifestObject $manifest
if ($actualCampaignSha -ne ([string]$manifest.campaignSha256).ToUpperInvariant()) {
    throw "Campaign manifest hash mismatch: expected $($manifest.campaignSha256), actual $actualCampaignSha"
}
$fixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
if (-not $fixture.passed) { throw 'Campaign fixture report is not passing.' }

$archiveFull = [IO.Path]::GetFullPath($OutputArchive)
$work = Join-Path ([IO.Path]::GetDirectoryName($archiveFull)) ('petclinic-linux-export-' + [guid]::NewGuid().ToString('N'))
$bundle = Join-Path $work 'petclinic-linux-campaign'
$ledger = Join-Path $work 'export-ledger'
foreach ($dir in @($bundle, (Join-Path $bundle 'images'), (Join-Path $bundle 'inputs'), (Join-Path $bundle 'manifest'))) {
    New-JmoaDirectory -Path $dir
}
Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'linux-campaign-export' -Variant 'FROZEN' `
    -Description 'Exports exact OCI images and immutable campaign inputs. No image or artifact rebuild is permitted.' | Out-Null

try {
    Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $bundle 'manifest/windows-campaign-manifest.json')
    Copy-Item -LiteralPath $fixturePath -Destination (Join-Path $bundle 'inputs/campaign-fixtures.json')
    Copy-Item -LiteralPath ([string]$manifest.artifacts.baseline.path) -Destination (Join-Path $bundle 'inputs/petclinic-customers-b0.jar')
    Copy-Item -LiteralPath ([string]$manifest.artifacts.candidate.path) -Destination (Join-Path $bundle 'inputs/petclinic-customers-v2.jar')
    Copy-Item -LiteralPath ([string]$manifest.artifacts.materializationManifest.path) -Destination (Join-Path $bundle 'inputs/jmoa-materialization-manifest.json')
    Copy-Item -LiteralPath ([string]$manifest.artifactLineage.path) -Destination (Join-Path $bundle 'inputs/artifact-lineage.json')

    $tar = (Get-Command tar).Source
    $configParent = Split-Path -Parent ([string]$manifest.configRepo.path)
    $configName = Split-Path -Leaf ([string]$manifest.configRepo.path)
    $configTar = Join-Path $bundle 'inputs/config-repository.tar'
    $r = Invoke-AuditedExternal -Executable $tar -Arguments @('-cf', $configTar, '-C', $configParent, $configName) -LedgerDirectory $ledger -Step 'archive frozen config repository'
    if ($r.exitCode -ne 0) { throw 'Could not archive the frozen config repository.' }

    $repoTar = Join-Path $bundle 'inputs/jmoa-repository.tar'
    $r = Invoke-AuditedExternal -Executable 'git' -Arguments @('-C', $repo, 'archive', '--format=tar', '-o', $repoTar, 'HEAD') -LedgerDirectory $ledger -Step 'archive committed JMOA source'
    if ($r.exitCode -ne 0) { throw 'Could not archive committed JMOA source.' }

    $roles = [ordered]@{
        baseline = $manifest.images.baseline
        candidate = $manifest.images.candidate
        config = $manifest.images.config
        discovery = $manifest.images.discovery
    }
    $imageExports = [Collections.Generic.List[object]]::new()
    foreach ($entry in $roles.GetEnumerator()) {
        $oci = Join-Path $bundle "images/$($entry.Key).oci"
        $r = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('save', '--format', 'oci-archive', '-o', $oci, [string]$entry.Value.ref) `
            -LedgerDirectory $ledger -Step "export $($entry.Key) OCI image"
        if ($r.exitCode -ne 0) { throw "OCI export failed for $($entry.Key)." }
        $inspect = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('image', 'inspect', '--format', '{{.Id}}|{{.Digest}}|{{.Architecture}}|{{.Os}}', [string]$entry.Value.ref) `
            -LedgerDirectory $ledger -Step "inspect $($entry.Key) source image"
        $imageExports.Add([ordered]@{
            role = $entry.Key
            reference = [string]$entry.Value.ref
            expectedImageId = [string]$entry.Value.id
            sourceInspect = $inspect.stdout.Trim()
            archive = "images/$($entry.Key).oci"
            archiveSha256 = (Get-FileHash -LiteralPath $oci -Algorithm SHA256).Hash
            archiveBytes = (Get-Item -LiteralPath $oci).Length
        })
    }

    $files = [Collections.Generic.List[object]]::new()
    Get-ChildItem -LiteralPath $bundle -Recurse -File |
        Where-Object { $_.Name -ne 'portable-campaign-manifest.json' } |
        Sort-Object FullName |
        ForEach-Object {
            $files.Add([ordered]@{
                path = $_.FullName.Substring($bundle.Length + 1).Replace('\', '/')
                sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
                bytes = $_.Length
            })
        }
    $portable = [ordered]@{
        schemaVersion = 'jmoa-petclinic-linux-package-v1'
        packageSha256 = ''
        sourceCampaignSha256 = [string]$manifest.campaignSha256
        sourceRevision = [string]$manifest.sourceRevision
        runtimePolicy = [string]$manifest.environment.runtimePolicy
        workload = [ordered]@{ id = 'petclinic-81-request'; endpoints = 27; rounds = 3; requestsPerArm = 81 }
        logicalArtifacts = [ordered]@{
            baselineSha256 = [string]$manifest.artifacts.baseline.sha256
            candidateSha256 = [string]$manifest.artifacts.candidate.sha256
            materializationManifestSha256 = [string]$manifest.artifacts.materializationManifest.sha256
            artifactLineageSha256 = [string]$manifest.artifactLineage.sha256
            configTreeSha256 = [string]$manifest.configRepo.contentTreeSha256
        }
        images = $imageExports.ToArray()
        files = $files.ToArray()
    }
    $portable.packageSha256 = Get-CampaignManifestSha256 -ManifestObject $portable
    Write-JmoaJson -Value $portable -Path (Join-Path $bundle 'manifest/portable-campaign-manifest.json')

    New-JmoaDirectory -Path ([IO.Path]::GetDirectoryName($archiveFull))
    if (Test-Path -LiteralPath $archiveFull) { Remove-Item -LiteralPath $archiveFull -Force }
    $r = Invoke-AuditedExternal -Executable $tar -Arguments @('-a', '-cf', $archiveFull, '-C', $work, 'petclinic-linux-campaign') `
        -LedgerDirectory $ledger -Step 'create Linux campaign archive'
    if ($r.exitCode -ne 0) { throw 'Could not create Linux campaign archive.' }
    $result = [ordered]@{
        archive = $archiveFull
        sha256 = (Get-FileHash -LiteralPath $archiveFull -Algorithm SHA256).Hash
        bytes = (Get-Item -LiteralPath $archiveFull).Length
        packageSha256 = $portable.packageSha256
        sourceCampaignSha256 = $portable.sourceCampaignSha256
        imageCount = $imageExports.Count
    }
    Write-JmoaJson -Value $result -Path "$archiveFull.json"
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status 'COMPLETE' -Stage 'linux-campaign-export' -Variant 'FROZEN' | Out-Null
    $result | ConvertTo-Json -Depth 8
} finally {
    if (Test-Path -LiteralPath $bundle) { Remove-Item -LiteralPath $bundle -Recurse -Force }
}

<#
.SYNOPSIS
    Builds the SIGNED PetClinic performance-campaign manifest (Gate C, review Issue #15 + item 12).

    The campaign runner (run-petclinic-performance-campaign.ps1) has NO machine-specific default paths:
    every frozen identity is supplied through this manifest. This one-time authoring tool resolves each
    image to its immutable ID, hashes each frozen artifact, freezes the config repository, references the
    adoption scenario's artifact-lineage.json, and computes a self-certifying campaign SHA-256 over the
    canonical manifest body so any later edit to a recorded identity is detectable before launch.

    Output schema: jmoa-petclinic-campaign-manifest-v1.
#>
param(
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][string]$B0Image,
    [Parameter(Mandatory)][string]$V2Image,
    [Parameter(Mandatory)][string]$ConfigImageId,
    [Parameter(Mandatory)][string]$DiscoveryImageId,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [Parameter(Mandatory)][string]$B0Artifact,
    [Parameter(Mandatory)][string]$V2Artifact,
    [Parameter(Mandatory)][string]$MaterializationManifest,
    [Parameter(Mandatory)][string]$ArtifactLineage,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$MavenExecutable,
    [Parameter(Mandatory)][string]$SourceRevision,
    [string]$ContainerCli = 'podman',
    [string]$PluginCoordinates = 'com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2',
    [string]$RuntimePolicy = 'NO_CDS_LOW_DIRTY'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-common.ps1')

foreach ($artifact in @($B0Artifact, $V2Artifact, $MaterializationManifest, $ArtifactLineage)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Frozen artifact not found: $artifact" }
}
if (-not (Test-Path -LiteralPath $ConfigRepo -PathType Container)) { throw "Config repository not found: $ConfigRepo" }
if (-not (Test-Path -LiteralPath $JavaHome -PathType Container)) { throw "JavaHome not found: $JavaHome" }

function Resolve-ImageId {
    param([string]$Ref)
    $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('image', 'inspect', '--format', '{{.Id}}', $Ref)
    if ($result.exitCode -ne 0) { throw "Could not resolve image ID for '$Ref': $($result.output)" }
    return $result.output.Trim()
}

$configFreeze = Get-CampaignConfigFreeze -ConfigRepo $ConfigRepo

$manifest = [ordered]@{
    schemaVersion   = 'jmoa-petclinic-campaign-manifest-v1'
    campaignSha256  = ''
    generatedUtc    = [DateTime]::UtcNow.ToString('o')
    sourceRevision  = $SourceRevision
    environment     = [ordered]@{
        javaHome          = (Resolve-Path -LiteralPath $JavaHome).Path
        mavenExecutable   = $MavenExecutable
        containerCli      = $ContainerCli
        pluginCoordinates = $PluginCoordinates
        runtimePolicy     = $RuntimePolicy
    }
    images          = [ordered]@{
        baseline  = [ordered]@{ ref = $B0Image; id = (Resolve-ImageId -Ref $B0Image) }
        candidate = [ordered]@{ ref = $V2Image; id = (Resolve-ImageId -Ref $V2Image) }
        config    = [ordered]@{ ref = $ConfigImageId; id = (Resolve-ImageId -Ref $ConfigImageId) }
        discovery = [ordered]@{ ref = $DiscoveryImageId; id = (Resolve-ImageId -Ref $DiscoveryImageId) }
    }
    artifacts       = [ordered]@{
        baseline                = [ordered]@{ path = (Resolve-Path -LiteralPath $B0Artifact).Path; sha256 = (Get-JmoaSha256 -Path $B0Artifact).ToUpperInvariant() }
        candidate               = [ordered]@{ path = (Resolve-Path -LiteralPath $V2Artifact).Path; sha256 = (Get-JmoaSha256 -Path $V2Artifact).ToUpperInvariant() }
        materializationManifest = [ordered]@{ path = (Resolve-Path -LiteralPath $MaterializationManifest).Path; sha256 = (Get-JmoaSha256 -Path $MaterializationManifest).ToUpperInvariant() }
    }
    configRepo      = [ordered]@{
        path              = (Resolve-Path -LiteralPath $ConfigRepo).Path
        contentTreeSha256 = $configFreeze.contentTreeSha256
        gitHead           = $configFreeze.gitHead
        workingTreeClean  = $configFreeze.workingTreeClean
    }
    artifactLineage = [ordered]@{
        path   = (Resolve-Path -LiteralPath $ArtifactLineage).Path
        sha256 = (Get-JmoaSha256 -Path $ArtifactLineage).ToUpperInvariant()
    }
}

$manifest.campaignSha256 = Get-CampaignManifestSha256 -ManifestObject $manifest

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) { New-JmoaDirectory -Path $outputDirectory }
Write-JmoaJson -Value $manifest -Path $OutputPath

Write-Host "Campaign manifest written: $OutputPath"
Write-Host "  campaignSha256 = $($manifest.campaignSha256)"
Write-Host "  baseline image id  = $($manifest.images.baseline.id)"
Write-Host "  candidate image id = $($manifest.images.candidate.id)"
$manifest | ConvertTo-Json -Depth 32

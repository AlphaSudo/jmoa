param(
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string]$SourceRevision,
    [Parameter(Mandatory)][string]$Deployment,
    [Parameter(Mandatory)][string]$RuntimePolicy,
    [Parameter(Mandatory)][string]$WorkloadId,
    [Parameter(Mandatory)][string]$B0Artifact,
    [Parameter(Mandatory)][string]$V2Artifact,
    [string]$B0Image = "",
    [string]$V2Image = "",
    [string]$B0CdsArchive = "",
    [string]$V2CdsArchive = "",
    [string]$SharedBaseArchive = "",
    [Parameter(Mandatory)][string]$OutputDir
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

function Get-Sha256([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return $null }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Missing file: $Path" }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Get-ImageIdentity([string]$Image) {
    if ([string]::IsNullOrWhiteSpace($Image)) { return $null }
    $id = (& podman image inspect $Image --format '{{.Id}}' 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($id)) { throw "Missing image: $Image" }
    return [ordered]@{ name = $Image; id = $id }
}

function Get-JarInventory([string]$Path) {
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $names = @($archive.Entries | ForEach-Object FullName)
        $runtime = @($names | Where-Object { $_ -match '(?i)(^|/)jmoa-runtime-lib[^/]*\.jar$' })
        $reports = @($names | Where-Object { $_ -match '(?i)jmoa-(reducer|lambda|mode-c|rewrite)' })
        return [ordered]@{
            entries = $names.Count
            bootInfLibraries = @($names | Where-Object { $_ -match '^BOOT-INF/lib/[^/]+\.jar$' }).Count
            applicationClasses = @($names | Where-Object { $_ -match '^BOOT-INF/classes/.+\.class$' }).Count
            jmoaRuntimeLibraries = $runtime
            embeddedJmoaReports = $reports
        }
    } finally {
        $archive.Dispose()
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$b0Inventory = Get-JarInventory $B0Artifact
$v2Inventory = Get-JarInventory $V2Artifact
$b0Clean = $b0Inventory.jmoaRuntimeLibraries.Count -eq 0 -and $b0Inventory.embeddedJmoaReports.Count -eq 0
$v2HasRuntime = $v2Inventory.jmoaRuntimeLibraries.Count -gt 0

$report = [ordered]@{
    metadataVersion = 'jmoa-product-artifact-freeze-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    service = $Service
    sourceRevision = $SourceRevision
    deployment = $Deployment
    runtimePolicy = $RuntimePolicy
    workloadId = $WorkloadId
    b0 = [ordered]@{
        artifactSha256 = Get-Sha256 $B0Artifact
        artifactBytes = (Get-Item -LiteralPath $B0Artifact).Length
        image = Get-ImageIdentity $B0Image
        inventory = $b0Inventory
        noJmoaRuntimeOrEmbeddedReports = $b0Clean
    }
    v2 = [ordered]@{
        artifactSha256 = Get-Sha256 $V2Artifact
        artifactBytes = (Get-Item -LiteralPath $V2Artifact).Length
        image = Get-ImageIdentity $V2Image
        inventory = $v2Inventory
        jmoaRuntimePresent = $v2HasRuntime
    }
    runtimeArchives = [ordered]@{
        b0ApplicationCdsSha256 = Get-Sha256 $B0CdsArchive
        v2ApplicationCdsSha256 = Get-Sha256 $V2CdsArchive
        sharedBaseArchiveSha256 = Get-Sha256 $SharedBaseArchive
    }
    gates = [ordered]@{
        b0ContainsNoJmoaRuntimeOrReports = $b0Clean
        artifactsDiffer = ((Get-Sha256 $B0Artifact) -ne (Get-Sha256 $V2Artifact))
        v2RuntimeSupportPresent = $v2HasRuntime
    }
    claimBoundary = 'Identity and packaging proof only. This report does not establish semantic equivalence or a memory result.'
}

$json = Join-Path $OutputDir "$Service-b0-v2-artifact-freeze.json"
$md = Join-Path $OutputDir "$Service-b0-v2-artifact-freeze.md"
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $json -Encoding UTF8
@(
    "# $Service B0/V2 Artifact Freeze",
    "",
    "- Source revision: ``$SourceRevision``",
    "- Deployment: ``$Deployment``",
    "- Runtime policy: ``$RuntimePolicy``",
    "- B0 SHA-256: ``$($report.b0.artifactSha256)``",
    "- V2 SHA-256: ``$($report.v2.artifactSha256)``",
    "- B0 excludes JMOA runtime/reports: ``$b0Clean``",
    "- V2 runtime support present: ``$v2HasRuntime``",
    "",
    $report.claimBoundary
) | Set-Content -LiteralPath $md -Encoding UTF8

if (-not $b0Clean) { throw 'B0 artifact contains JMOA runtime or embedded JMOA report entries.' }
Write-Host "Frozen product comparison: $json"

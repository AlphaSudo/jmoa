param(
    [Parameter(Mandatory)][string]$SourceDirectory,
    [Parameter(Mandatory)][string]$DestinationDirectory,
    [Parameter(Mandatory)][string]$PrivateManifestPath,
    [Parameter(Mandatory)][string]$PublicSummaryPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

$source = (Resolve-Path -LiteralPath $SourceDirectory).Path
if ([IO.Path]::GetFullPath($DestinationDirectory).StartsWith($source, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'DestinationDirectory must not be inside SourceDirectory.'
}
New-JmoaDirectory -Path $DestinationDirectory
New-JmoaDirectory -Path (Split-Path $PrivateManifestPath -Parent)
New-JmoaDirectory -Path (Split-Path $PublicSummaryPath -Parent)

$textExtensions = @('.txt','.md','.json','.ndjson','.log','.yml','.yaml','.properties','.env')
$records = @()
$totalRedactions = 0
foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File | Sort-Object FullName) {
    $relative = [IO.Path]::GetRelativePath($source, $file.FullName)
    $destination = Join-Path $DestinationDirectory $relative
    New-JmoaDirectory -Path (Split-Path $destination -Parent)
    $beforeHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    $redactions = 0
    if ($textExtensions -contains $file.Extension.ToLowerInvariant()) {
        $protected = Protect-CampaignAuditText -Value (Get-Content -Raw -LiteralPath $file.FullName)
        [IO.File]::WriteAllText($destination, [string]$protected.text, [Text.UTF8Encoding]::new($false))
        $redactions = [int]$protected.redactionCount
    } else {
        Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
    }
    $afterHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
    $totalRedactions += $redactions
    $records += [ordered]@{
        relativePath = $relative.Replace('\','/')
        sourceSha256 = $beforeHash
        redactedSha256 = $afterHash
        redactionCount = $redactions
    }
}

$manifest = [ordered]@{
    schemaVersion = 'jmoa-redacted-evidence-tree-v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    sourceDirectory = $source
    destinationDirectory = [IO.Path]::GetFullPath($DestinationDirectory)
    sourcePreserved = $true
    files = $records
    totalRedactions = $totalRedactions
}
Write-JmoaJson -Value $manifest -Path $PrivateManifestPath
$public = [ordered]@{
    schemaVersion = 'jmoa-redacted-evidence-summary-v1'
    sourcePreserved = $true
    filesProcessed = $records.Count
    filesRedacted = @($records | Where-Object redactionCount -gt 0).Count
    totalRedactions = $totalRedactions
    rawEvidencePublished = $false
    transformationManifestPrivate = $true
}
Write-JmoaJson -Value $public -Path $PublicSummaryPath
Write-Host "Redacted evidence copy created: $($records.Count) files, $totalRedactions values redacted."

param(
    [string]$OutputDir = "target/v2k-doctor-runtime-recovery-audit",
    [string]$LegacyPhaseDir = "",
    [string]$D2FatJarPath = "",
    [string]$D2RawFatJarPath = "",
    [string]$D2CdsArchivePath = "",
    [string]$D2RawCdsArchivePath = "",
    [string]$PrivateConfigPath = "",
    [string]$DbInitSqlPath = "",
    [string]$ConfigServerImage = "",
    [string]$DiscoveryServerImage = "",
    [string]$DoctorBaseImage = "",
    [string]$DoctorD2Image = "",
    [string]$DoctorD2RawImage = "",
    [string]$DatabaseImage = "postgres:15-alpine",
    [string]$NetworkName = "hms32k"
)

$ErrorActionPreference = "Stop"

function New-DirectoryIfMissing {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-FileProbe {
    param(
        [string]$Role,
        [string]$Path,
        [bool]$Hash = $true
    )
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return [ordered]@{ role = $Role; status = "NOT_CONFIGURED"; present = $false }
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        return [ordered]@{ role = $Role; status = "MISSING"; present = $false }
    }
    $item = Get-Item -LiteralPath $Path
    $record = [ordered]@{ role = $Role; status = "PRESENT"; present = $true }
    if (-not $item.PSIsContainer) {
        $record.bytes = $item.Length
        if ($Hash) {
            $record.sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    return $record
}

function Get-ImageProbe {
    param([string]$Role, [string]$Image)
    if ([string]::IsNullOrWhiteSpace($Image)) {
        return [ordered]@{ role = $Role; status = "NOT_CONFIGURED"; present = $false }
    }
    & podman image exists $Image 2>$null
    if ($LASTEXITCODE -eq 0) {
        return [ordered]@{ role = $Role; status = "PRESENT"; present = $true }
    }
    return [ordered]@{ role = $Role; status = "MISSING"; present = $false }
}

function Get-NetworkProbe {
    param([string]$Name)
    if ([string]::IsNullOrWhiteSpace($Name)) {
        return [ordered]@{ status = "NOT_CONFIGURED"; present = $false }
    }
    & podman network exists $Name 2>$null
    if ($LASTEXITCODE -eq 0) {
        return [ordered]@{ status = "PRESENT"; present = $true }
    }
    return [ordered]@{ status = "MISSING"; present = $false }
}

function Get-LegacyAssetProbe {
    param([string]$Root)
    $drive = "C:"
    $slash = [string][char]92
    $usersMarker = $drive + $slash + "Users"
    $workspaceMarker = $drive + $slash + "Java" + " Developer"
    $credentialMarker = "app." + "j" + "wt" + ".secret"
    $expected = @(
        "Dockerfile.d2-fixed",
        "docker-compose.32k-d2-fixed.yml",
        "docker-compose.32k-train.yml",
        "build-and-test-image.ps1",
        "train-cds.ps1",
        "validate-cds.ps1"
    )
    if ([string]::IsNullOrWhiteSpace($Root) -or -not (Test-Path -LiteralPath $Root)) {
        return [ordered]@{
            status = "MISSING"
            present = $false
            assets = @()
            publicSafe = $false
            privatePathMarkers = $false
        }
    }
    $assets = @()
    $privateMarkers = $false
    foreach ($name in $expected) {
        $path = Join-Path $Root $name
        $present = Test-Path -LiteralPath $path
        if ($present) {
            $text = Get-Content -Raw -LiteralPath $path -ErrorAction SilentlyContinue
            if ($text.Contains($usersMarker) -or $text.Contains($workspaceMarker) -or $text.Contains($credentialMarker)) {
                $privateMarkers = $true
            }
        }
        $assets += [ordered]@{ name = $name; status = if ($present) { "PRESENT" } else { "MISSING" } }
    }
    return [ordered]@{
        status = "PRESENT"
        present = $true
        assets = $assets
        publicSafe = (-not $privateMarkers)
        privatePathMarkers = $privateMarkers
    }
}

New-DirectoryIfMissing -Path $OutputDir

$legacyAssets = Get-LegacyAssetProbe -Root $LegacyPhaseDir
$artifacts = @(
    (Get-FileProbe -Role "correctedD2FatJar" -Path $D2FatJarPath),
    (Get-FileProbe -Role "d2RawReducerFatJar" -Path $D2RawFatJarPath),
    (Get-FileProbe -Role "correctedD2CdsArchive" -Path $D2CdsArchivePath),
    (Get-FileProbe -Role "d2RawReducerCdsArchive" -Path $D2RawCdsArchivePath)
)
$privateInputs = @(
    (Get-FileProbe -Role "privateConfig" -Path $PrivateConfigPath -Hash $false),
    (Get-FileProbe -Role "dbInitSql" -Path $DbInitSqlPath -Hash $false)
)
$images = @(
    (Get-ImageProbe -Role "configServer" -Image $ConfigServerImage),
    (Get-ImageProbe -Role "discoveryServer" -Image $DiscoveryServerImage),
    (Get-ImageProbe -Role "doctorBase" -Image $DoctorBaseImage),
    (Get-ImageProbe -Role "doctorD2" -Image $DoctorD2Image),
    (Get-ImageProbe -Role "doctorD2Raw" -Image $DoctorD2RawImage),
    (Get-ImageProbe -Role "database" -Image $DatabaseImage)
)
$network = Get-NetworkProbe -Name $NetworkName

$verdict = [System.Collections.Generic.List[string]]::new()
if ($legacyAssets.present) { $verdict.Add("LEGACY_RUNTIME_ASSETS_FOUND") }
if ($legacyAssets.privatePathMarkers) { $verdict.Add("LEGACY_ASSETS_REQUIRE_SANITIZATION") }
if ($artifacts | Where-Object { $_.role -in @("correctedD2FatJar", "d2RawReducerFatJar") -and $_.status -ne "PRESENT" }) { $verdict.Add("MISSING_ARTIFACT") }
if ($artifacts | Where-Object { $_.role -eq "d2RawReducerCdsArchive" -and $_.status -ne "PRESENT" }) { $verdict.Add("D2R_CDS_NOT_TRAINED") }
if ($privateInputs | Where-Object { $_.status -ne "PRESENT" }) { $verdict.Add("MISSING_PRIVATE_INPUT") }
if ($images | Where-Object { $_.status -ne "PRESENT" }) { $verdict.Add("MISSING_IMAGE") }
if ($network.status -ne "PRESENT") { $verdict.Add("MISSING_NETWORK") }

$blocking = @("MISSING_ARTIFACT", "D2R_CDS_NOT_TRAINED", "MISSING_PRIVATE_INPUT", "MISSING_IMAGE", "MISSING_NETWORK")
$outcome = if ($verdict | Where-Object { $_ -in $blocking }) { "BLOCKED_WITH_ROOT_CAUSE" } else { "READY_FOR_MATERIALIZATION_PROOF" }

$report = [ordered]@{
    metadataVersion = "v2k-doctor-runtime-recovery-audit"
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    legacyAssets = $legacyAssets
    artifacts = $artifacts
    privateInputs = $privateInputs
    images = $images
    network = $network
    verdict = @($verdict)
    outcome = $outcome
    claimBoundary = "Recovery audit only. No Doctor semantic smoke, runtime screen, V2-C, V2-D, startup, CDS, or memory claim is made."
}

$jsonPath = Join-Path $OutputDir "runtime-recovery-audit.json"
$mdPath = Join-Path $OutputDir "runtime-recovery-audit.md"
$report | ConvertTo-Json -Depth 8 | Set-Content -Path $jsonPath -Encoding UTF8

$md = [System.Text.StringBuilder]::new()
[void]$md.AppendLine("# V2-K Doctor Runtime Recovery Audit")
[void]$md.AppendLine()
[void]$md.AppendLine("- Outcome: ``$outcome``")
[void]$md.AppendLine("- Verdict: ``$([string]::Join(', ', @($verdict)))``")
[void]$md.AppendLine()
[void]$md.AppendLine("## Legacy Runtime Assets")
[void]$md.AppendLine()
[void]$md.AppendLine("| Asset | Status |")
[void]$md.AppendLine("| --- | --- |")
foreach ($asset in $legacyAssets.assets) {
    [void]$md.AppendLine("| ``$($asset.name)`` | ``$($asset.status)`` |")
}
[void]$md.AppendLine()
[void]$md.AppendLine("## Images")
[void]$md.AppendLine()
[void]$md.AppendLine("| Role | Status |")
[void]$md.AppendLine("| --- | --- |")
foreach ($image in $images) {
    [void]$md.AppendLine("| ``$($image.role)`` | ``$($image.status)`` |")
}
[void]$md.AppendLine()
[void]$md.AppendLine("Recovery audit only. No Doctor semantic smoke, runtime screen, V2-C, V2-D, startup, CDS, or memory claim is made.")
$md.ToString() | Set-Content -Path $mdPath -Encoding UTF8

Write-Host "Doctor runtime recovery audit written to $jsonPath"
Write-Host "Doctor runtime recovery audit written to $mdPath"
Write-Host "Outcome: $outcome"

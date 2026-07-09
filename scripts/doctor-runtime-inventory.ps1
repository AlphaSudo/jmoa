param(
    [string]$OutputDir = "target/v2k-doctor-runtime-inventory",
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
    [int[]]$Ports = @(8888, 8761, 5432, 8082),
    [string]$NetworkName = "hms32k"
)

$ErrorActionPreference = "Stop"

function New-DirectoryIfMissing {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-FileStatus {
    param(
        [string]$Label,
        [string]$Path,
        [bool]$Hash = $false
    )
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return [ordered]@{
            label = $Label
            status = "NOT_CONFIGURED"
            present = $false
        }
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        return [ordered]@{
            label = $Label
            status = "MISSING"
            present = $false
        }
    }
    $item = Get-Item -LiteralPath $Path
    $record = [ordered]@{
        label = $Label
        status = "PRESENT"
        present = $true
    }
    if (-not $item.PSIsContainer) {
        $record.bytes = $item.Length
        if ($Hash) {
            $record.sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    return $record
}

function Test-PodmanAvailable {
    try {
        $version = (& podman --version 2>$null)
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($version)) {
            return [ordered]@{
                status = "PRESENT"
                available = $true
                version = $version.Trim()
            }
        }
    } catch {
        # handled below
    }
    return [ordered]@{
        status = "MISSING"
        available = $false
    }
}

function Get-ImageStatus {
    param(
        [string]$Role,
        [string]$Image,
        [bool]$PodmanAvailable
    )
    if ([string]::IsNullOrWhiteSpace($Image)) {
        return [ordered]@{
            role = $Role
            status = "NOT_CONFIGURED"
            present = $false
        }
    }
    if (-not $PodmanAvailable) {
        return [ordered]@{
            role = $Role
            status = "PODMAN_UNAVAILABLE"
            present = $false
        }
    }
    & podman image exists $Image 2>$null
    if ($LASTEXITCODE -eq 0) {
        return [ordered]@{
            role = $Role
            status = "PRESENT"
            present = $true
        }
    }
    return [ordered]@{
        role = $Role
        status = "MISSING"
        present = $false
    }
}

function Get-NetworkStatus {
    param(
        [string]$Name,
        [bool]$PodmanAvailable
    )
    if ([string]::IsNullOrWhiteSpace($Name)) {
        return [ordered]@{
            status = "NOT_CONFIGURED"
            present = $false
        }
    }
    if (-not $PodmanAvailable) {
        return [ordered]@{
            status = "PODMAN_UNAVAILABLE"
            present = $false
        }
    }
    & podman network exists $Name 2>$null
    if ($LASTEXITCODE -eq 0) {
        return [ordered]@{
            status = "PRESENT"
            present = $true
        }
    }
    return [ordered]@{
        status = "MISSING"
        present = $false
    }
}

function Get-PortStatus {
    param([int]$Port)
    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        return [ordered]@{
            port = $Port
            status = "AVAILABLE"
            available = $true
        }
    } catch {
        return [ordered]@{
            port = $Port
            status = "BUSY_OR_BLOCKED"
            available = $false
        }
    } finally {
        if ($listener -ne $null) {
            $listener.Stop()
        }
    }
}

New-DirectoryIfMissing -Path $OutputDir

$podman = Test-PodmanAvailable
$podmanAvailable = [bool]$podman.available

$artifacts = @(
    (Get-FileStatus -Label "correctedD2FatJar" -Path $D2FatJarPath -Hash $true),
    (Get-FileStatus -Label "d2RawReducerFatJar" -Path $D2RawFatJarPath -Hash $true),
    (Get-FileStatus -Label "correctedD2CdsArchive" -Path $D2CdsArchivePath -Hash $true),
    (Get-FileStatus -Label "d2RawReducerCdsArchive" -Path $D2RawCdsArchivePath -Hash $true)
)

$privateInputs = @(
    (Get-FileStatus -Label "privateConfig" -Path $PrivateConfigPath -Hash $false),
    (Get-FileStatus -Label "dbInitSql" -Path $DbInitSqlPath -Hash $false)
)

$images = @(
    (Get-ImageStatus -Role "configServer" -Image $ConfigServerImage -PodmanAvailable $podmanAvailable),
    (Get-ImageStatus -Role "discoveryServer" -Image $DiscoveryServerImage -PodmanAvailable $podmanAvailable),
    (Get-ImageStatus -Role "doctorBase" -Image $DoctorBaseImage -PodmanAvailable $podmanAvailable),
    (Get-ImageStatus -Role "doctorD2" -Image $DoctorD2Image -PodmanAvailable $podmanAvailable),
    (Get-ImageStatus -Role "doctorD2Raw" -Image $DoctorD2RawImage -PodmanAvailable $podmanAvailable),
    (Get-ImageStatus -Role "database" -Image $DatabaseImage -PodmanAvailable $podmanAvailable)
)

$portsStatus = @($Ports | ForEach-Object { Get-PortStatus -Port $_ })
$network = Get-NetworkStatus -Name $NetworkName -PodmanAvailable $podmanAvailable

$verdict = [System.Collections.Generic.List[string]]::new()
if (-not $podmanAvailable) {
    $verdict.Add("PODMAN_UNAVAILABLE")
}
if ($artifacts | Where-Object { $_.status -in @("MISSING", "NOT_CONFIGURED") -and $_.label -in @("correctedD2FatJar", "d2RawReducerFatJar") }) {
    $verdict.Add("MISSING_ARTIFACT")
}
if ($images | Where-Object { $_.status -in @("MISSING", "NOT_CONFIGURED", "PODMAN_UNAVAILABLE") }) {
    $verdict.Add("MISSING_IMAGE")
}
if ($privateInputs | Where-Object { $_.label -eq "privateConfig" -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
    $verdict.Add("MISSING_CONFIG")
}
if ($privateInputs | Where-Object { $_.label -eq "dbInitSql" -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
    $verdict.Add("MISSING_DATABASE")
}
if ($artifacts | Where-Object { $_.label -eq "d2RawReducerCdsArchive" -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
    $verdict.Add("D2R_CDS_NOT_TRAINED")
}
if ($verdict | Where-Object { $_ -in @("MISSING_IMAGE", "MISSING_CONFIG", "MISSING_DATABASE") }) {
    $verdict.Add("BLOCKED_PRIVATE_STACK")
}
if ($verdict.Count -eq 0) {
    $verdict.Add("READY_FOR_SEMANTIC_SMOKE")
}

$report = [ordered]@{
    metadataVersion = "v2k-doctor-runtime-inventory-script"
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    podman = $podman
    artifacts = $artifacts
    privateInputs = $privateInputs
    images = $images
    network = $network
    ports = $portsStatus
    verdict = @($verdict)
    claimBoundary = "Inventory only. No Doctor semantic, runtime, V2-C, V2-D, or memory claim is made."
}

$jsonPath = Join-Path $OutputDir "runtime-inventory.json"
$mdPath = Join-Path $OutputDir "runtime-inventory.md"
$report | ConvertTo-Json -Depth 8 | Set-Content -Path $jsonPath -Encoding UTF8

$md = [System.Text.StringBuilder]::new()
[void]$md.AppendLine("# V2-K Doctor Runtime Inventory")
[void]$md.AppendLine()
[void]$md.AppendLine("- Metadata version: ``v2k-doctor-runtime-inventory-script``")
[void]$md.AppendLine("- Podman: ``$($podman.status)``")
[void]$md.AppendLine("- Network: ``$($network.status)``")
[void]$md.AppendLine("- Verdict: ``$([string]::Join(', ', @($verdict)))``")
[void]$md.AppendLine()
[void]$md.AppendLine("## Artifacts")
[void]$md.AppendLine()
[void]$md.AppendLine("| Item | Status | Bytes | SHA-256 |")
[void]$md.AppendLine("| --- | --- | ---: | --- |")
foreach ($item in $artifacts) {
    $bytes = if ($item.Contains("bytes")) { $item.bytes } else { "" }
    $sha = if ($item.Contains("sha256")) { $item.sha256 } else { "" }
    [void]$md.AppendLine("| ``$($item.label)`` | ``$($item.status)`` | $bytes | ``$sha`` |")
}
[void]$md.AppendLine()
[void]$md.AppendLine("## Private Inputs")
[void]$md.AppendLine()
[void]$md.AppendLine("| Item | Status |")
[void]$md.AppendLine("| --- | --- |")
foreach ($item in $privateInputs) {
    [void]$md.AppendLine("| ``$($item.label)`` | ``$($item.status)`` |")
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
[void]$md.AppendLine("## Ports")
[void]$md.AppendLine()
[void]$md.AppendLine("| Port | Status |")
[void]$md.AppendLine("| ---: | --- |")
foreach ($port in $portsStatus) {
    [void]$md.AppendLine("| $($port.port) | ``$($port.status)`` |")
}
[void]$md.AppendLine()
[void]$md.AppendLine("Inventory only. No Doctor semantic, runtime, V2-C, V2-D, or memory claim is made.")
$md.ToString() | Set-Content -Path $mdPath -Encoding UTF8

Write-Host "Doctor runtime inventory written to $jsonPath"
Write-Host "Doctor runtime inventory written to $mdPath"
Write-Host "Verdict: $([string]::Join(', ', @($verdict)))"

param(
    [string]$OutputDir = "target/v2k-doctor-runtime-unblock-gate",
    [ValidateSet("RetrainD2RCds", "NoCdsDiagnostic")]
    [string]$RuntimePolicy = "RetrainD2RCds",
    [string]$D2FatJarPath = "",
    [string]$D2RawFatJarPath = "",
    [string]$D2CdsArchivePath = "",
    [string]$D2RawCdsArchivePath = "",
    [string]$ExpectedD2FatJarSha256 = "",
    [string]$ExpectedD2RawFatJarSha256 = "",
    [string]$ExpectedD2CdsArchiveSha256 = "",
    [string]$ExpectedD2RawCdsArchiveSha256 = "",
    [string]$PrivateConfigPath = "",
    [string]$DbInitSqlPath = "",
    [string]$ConfigServerImage = "",
    [string]$DiscoveryServerImage = "",
    [string]$DoctorD2Image = "",
    [string]$DoctorD2RawImage = "",
    [string]$DatabaseImage = "postgres:15-alpine",
    [string]$NetworkName = "hms32k",
    [int[]]$Ports = @(8888, 8761, 5432, 8082)
)

$ErrorActionPreference = "Stop"

function New-DirectoryIfMissing {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Test-Podman {
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
        # reported below
    }
    return [ordered]@{
        status = "MISSING"
        available = $false
    }
}

function Get-FileProbe {
    param(
        [string]$Role,
        [string]$Path,
        [string]$ExpectedSha256 = "",
        [bool]$Hash = $true
    )
    $record = [ordered]@{
        role = $Role
    }
    if ([string]::IsNullOrWhiteSpace($Path)) {
        $record.status = "NOT_CONFIGURED"
        $record.present = $false
        return $record
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        $record.status = "MISSING"
        $record.present = $false
        return $record
    }
    $item = Get-Item -LiteralPath $Path
    $record.status = "PRESENT"
    $record.present = $true
    if (-not $item.PSIsContainer) {
        $record.bytes = $item.Length
        if ($Hash) {
            $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
            $record.sha256 = $actual
            if (-not [string]::IsNullOrWhiteSpace($ExpectedSha256)) {
                $expected = $ExpectedSha256.ToLowerInvariant()
                $record.expectedSha256 = $expected
                $record.hashMatches = ($actual -eq $expected)
                if ($actual -ne $expected) {
                    $record.status = "HASH_MISMATCH"
                }
            }
        }
    }
    return $record
}

function Get-ImageProbe {
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

function Get-NetworkProbe {
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

function Get-PortProbe {
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
        if ($null -ne $listener) {
            $listener.Stop()
        }
    }
}

function Add-UniqueVerdict {
    param(
        [System.Collections.Generic.List[string]]$Verdicts,
        [string]$Value
    )
    if (-not $Verdicts.Contains($Value)) {
        $Verdicts.Add($Value)
    }
}

New-DirectoryIfMissing -Path $OutputDir

$podman = Test-Podman
$podmanAvailable = [bool]$podman.available

$artifacts = @(
    (Get-FileProbe -Role "correctedD2FatJar" -Path $D2FatJarPath -ExpectedSha256 $ExpectedD2FatJarSha256),
    (Get-FileProbe -Role "d2RawReducerFatJar" -Path $D2RawFatJarPath -ExpectedSha256 $ExpectedD2RawFatJarSha256),
    (Get-FileProbe -Role "correctedD2CdsArchive" -Path $D2CdsArchivePath -ExpectedSha256 $ExpectedD2CdsArchiveSha256),
    (Get-FileProbe -Role "d2RawReducerCdsArchive" -Path $D2RawCdsArchivePath -ExpectedSha256 $ExpectedD2RawCdsArchiveSha256)
)

$privateInputs = @(
    (Get-FileProbe -Role "privateConfig" -Path $PrivateConfigPath -Hash $false),
    (Get-FileProbe -Role "dbInitSql" -Path $DbInitSqlPath -Hash $false)
)

$images = @(
    (Get-ImageProbe -Role "configServer" -Image $ConfigServerImage -PodmanAvailable $podmanAvailable),
    (Get-ImageProbe -Role "discoveryServer" -Image $DiscoveryServerImage -PodmanAvailable $podmanAvailable),
    (Get-ImageProbe -Role "doctorD2" -Image $DoctorD2Image -PodmanAvailable $podmanAvailable),
    (Get-ImageProbe -Role "doctorD2Raw" -Image $DoctorD2RawImage -PodmanAvailable $podmanAvailable),
    (Get-ImageProbe -Role "database" -Image $DatabaseImage -PodmanAvailable $podmanAvailable)
)

$network = Get-NetworkProbe -Name $NetworkName -PodmanAvailable $podmanAvailable
$portsStatus = @($Ports | ForEach-Object { Get-PortProbe -Port $_ })

$verdict = [System.Collections.Generic.List[string]]::new()
if (-not $podmanAvailable) {
    Add-UniqueVerdict -Verdicts $verdict -Value "PODMAN_UNAVAILABLE"
}
if ($artifacts | Where-Object { $_.role -in @("correctedD2FatJar", "d2RawReducerFatJar") -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
    Add-UniqueVerdict -Verdicts $verdict -Value "MISSING_ARTIFACT"
}
if ($artifacts | Where-Object { $_.status -eq "HASH_MISMATCH" }) {
    Add-UniqueVerdict -Verdicts $verdict -Value "ARTIFACT_HASH_MISMATCH"
}
if ($RuntimePolicy -eq "RetrainD2RCds") {
    if ($artifacts | Where-Object { $_.role -eq "correctedD2CdsArchive" -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
        Add-UniqueVerdict -Verdicts $verdict -Value "MISSING_BASELINE_CDS"
    }
    if ($artifacts | Where-Object { $_.role -eq "d2RawReducerCdsArchive" -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
        Add-UniqueVerdict -Verdicts $verdict -Value "D2R_CDS_NOT_TRAINED"
    }
}
if ($images | Where-Object { $_.status -in @("MISSING", "NOT_CONFIGURED", "PODMAN_UNAVAILABLE") }) {
    Add-UniqueVerdict -Verdicts $verdict -Value "MISSING_IMAGE"
}
if ($privateInputs | Where-Object { $_.role -eq "privateConfig" -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
    Add-UniqueVerdict -Verdicts $verdict -Value "MISSING_CONFIG"
}
if ($privateInputs | Where-Object { $_.role -eq "dbInitSql" -and $_.status -in @("MISSING", "NOT_CONFIGURED") }) {
    Add-UniqueVerdict -Verdicts $verdict -Value "MISSING_DATABASE"
}
if ($network.status -in @("MISSING", "NOT_CONFIGURED", "PODMAN_UNAVAILABLE")) {
    Add-UniqueVerdict -Verdicts $verdict -Value "MISSING_NETWORK"
}
if ($portsStatus | Where-Object { -not $_.available }) {
    Add-UniqueVerdict -Verdicts $verdict -Value "PORT_BUSY_OR_BLOCKED"
}
if ($verdict | Where-Object { $_ -in @("MISSING_IMAGE", "MISSING_CONFIG", "MISSING_DATABASE") }) {
    Add-UniqueVerdict -Verdicts $verdict -Value "BLOCKED_PRIVATE_STACK"
}
if ($verdict.Count -eq 0) {
    Add-UniqueVerdict -Verdicts $verdict -Value "READY_FOR_RUNTIME_MATERIALIZATION_PROOF"
}

$nextGate = if ($verdict.Contains("READY_FOR_RUNTIME_MATERIALIZATION_PROOF")) {
    "Run runtime materialization proof, then semantic smoke."
} elseif ($verdict.Contains("D2R_CDS_NOT_TRAINED") -and -not ($verdict.Contains("MISSING_IMAGE") -or $verdict.Contains("MISSING_CONFIG") -or $verdict.Contains("MISSING_DATABASE"))) {
    "Train fresh D2R CDS archive or explicitly switch to NoCdsDiagnostic."
} else {
    "Restore private runtime stack inputs, images, network, and CDS archive before semantic smoke."
}

$report = [ordered]@{
    metadataVersion = "v2k-doctor-runtime-unblock-gate"
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    runtimePolicy = $RuntimePolicy
    podman = $podman
    artifacts = $artifacts
    privateInputs = $privateInputs
    images = $images
    network = $network
    ports = $portsStatus
    verdict = @($verdict)
    nextGate = $nextGate
    claimBoundary = "Pre-smoke runtime unblock gate only. No Doctor semantic, runtime screen, V2-C, V2-D, or memory claim is made."
}

$jsonPath = Join-Path $OutputDir "runtime-unblock-gate.json"
$mdPath = Join-Path $OutputDir "runtime-unblock-gate.md"
$report | ConvertTo-Json -Depth 8 | Set-Content -Path $jsonPath -Encoding UTF8

$md = [System.Text.StringBuilder]::new()
[void]$md.AppendLine("# V2-K Doctor Runtime Unblock Gate")
[void]$md.AppendLine()
[void]$md.AppendLine("- Metadata version: ``v2k-doctor-runtime-unblock-gate``")
[void]$md.AppendLine("- Runtime policy: ``$RuntimePolicy``")
[void]$md.AppendLine("- Podman: ``$($podman.status)``")
[void]$md.AppendLine("- Network: ``$($network.status)``")
[void]$md.AppendLine("- Verdict: ``$([string]::Join(', ', @($verdict)))``")
[void]$md.AppendLine("- Next gate: $nextGate")
[void]$md.AppendLine()
[void]$md.AppendLine("## Artifacts")
[void]$md.AppendLine()
[void]$md.AppendLine("| Role | Status | Bytes | SHA-256 |")
[void]$md.AppendLine("| --- | --- | ---: | --- |")
foreach ($artifact in $artifacts) {
    $bytes = if ($artifact.Contains("bytes")) { $artifact.bytes } else { "" }
    $sha = if ($artifact.Contains("sha256")) { $artifact.sha256 } else { "" }
    [void]$md.AppendLine("| ``$($artifact.role)`` | ``$($artifact.status)`` | $bytes | ``$sha`` |")
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
[void]$md.AppendLine("## Private Inputs")
[void]$md.AppendLine()
[void]$md.AppendLine("| Role | Status |")
[void]$md.AppendLine("| --- | --- |")
foreach ($input in $privateInputs) {
    [void]$md.AppendLine("| ``$($input.role)`` | ``$($input.status)`` |")
}
[void]$md.AppendLine()
[void]$md.AppendLine("Pre-smoke runtime unblock gate only. No Doctor semantic, runtime screen, V2-C, V2-D, or memory claim is made.")
$md.ToString() | Set-Content -Path $mdPath -Encoding UTF8

Write-Host "Doctor runtime unblock gate written to $jsonPath"
Write-Host "Doctor runtime unblock gate written to $mdPath"
Write-Host "Verdict: $([string]::Join(', ', @($verdict)))"

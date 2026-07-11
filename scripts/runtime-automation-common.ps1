Set-StrictMode -Version Latest

function New-JmoaDirectory {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Get-JmoaSha256 {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return ''
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return ""
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Get-JmoaTextSha256 {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Value)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '')
    } finally {
        $sha.Dispose()
    }
}

function Write-JmoaJson {
    param(
        [Parameter(Mandatory)]$Value,
        [Parameter(Mandatory)][string]$Path
    )
    $Value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Write-JmoaText {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Path
    )
    Set-Content -LiteralPath $Path -Value $Value -Encoding utf8
}

function Invoke-JmoaExternal {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [string[]]$Arguments = @()
    )
    try {
        $output = & $Executable @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } catch {
        $output = $_.Exception.Message
        $exitCode = 127
    }
    return [pscustomobject]@{
        executable = $Executable
        arguments = @($Arguments)
        exitCode = $exitCode
        output = (($output | Out-String).Trim())
    }
}

function Test-JmoaContainerRunning {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$ContainerName
    )
    $inspect = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('inspect', '--format', '{{.State.Running}}', $ContainerName)
    return [pscustomobject]@{
        available = $inspect.exitCode -eq 0 -and $inspect.output.Trim().ToLowerInvariant() -eq 'true'
        inspect = $inspect
    }
}

function Get-JmoaContainerId {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$ContainerName
    )
    $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('inspect', '--format', '{{.Id}}', $ContainerName)
    if ($result.exitCode -ne 0) { return '' }
    return $result.output.Trim()
}

function Get-JmoaContainerImageId {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$ContainerName
    )
    $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('inspect', '--format', '{{.Image}}', $ContainerName)
    if ($result.exitCode -ne 0) { return '' }
    return $result.output.Trim()
}

function Invoke-JmoaContainerShell {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$ContainerName,
        [Parameter(Mandatory)][string]$Command
    )
    return Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('exec', $ContainerName, 'sh', '-lc', $Command)
}

function Get-JmoaJavaPid {
    param(
        [Parameter(Mandatory)][string]$ContainerCli,
        [Parameter(Mandatory)][string]$ContainerName,
        [string]$JavaProcessPattern = 'java'
    )
    $escaped = $JavaProcessPattern.Replace("'", "'`"'`"'")
    $result = Invoke-JmoaContainerShell -ContainerCli $ContainerCli -ContainerName $ContainerName `
        -Command "ps -eo pid,args | awk '/[j]ava/ && index(`$0, \"$escaped\") { print `$1; exit }'"
    if ($result.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.output)) {
        return ''
    }
    return ($result.output -split '\s+' | Select-Object -First 1).Trim()
}

function Wait-JmoaHttpHealth {
    param(
        [Parameter(Mandatory)][string]$HealthUrl,
        [int]$TimeoutSeconds = 90,
        [int[]]$ExpectedStatus = @(200)
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastStatus = 0
    $lastError = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -Method Get -SkipHttpErrorCheck -TimeoutSec 10
            $lastStatus = [int]$response.StatusCode
            if ($ExpectedStatus -contains $lastStatus) {
                return [pscustomobject]@{ passed = $true; statusCode = $lastStatus; error = '' }
            }
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 2
    }
    return [pscustomobject]@{ passed = $false; statusCode = $lastStatus; error = $lastError }
}

function Get-JmoaDependencyManifest {
    param([Parameter(Mandatory)][string]$Directory)
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        return [pscustomobject]@{ present = $false; entries = @(); fingerprint = '' }
    }
    $entries = @(
        Get-ChildItem -LiteralPath $Directory -Filter '*.jar' -File |
            Sort-Object Name |
            ForEach-Object {
                [ordered]@{
                    name = $_.Name
                    bytes = $_.Length
                    sha256 = Get-JmoaSha256 -Path $_.FullName
                }
            }
    )
    $canonical = (($entries | ForEach-Object { "$($_.name)|$($_.sha256)" }) -join "`n")
    return [pscustomobject]@{
        present = $true
        entries = $entries
        fingerprint = Get-JmoaTextSha256 -Value $canonical
    }
}

function ConvertTo-JmoaHashtable {
    param($Value)
    $table = @{}
    if ($null -eq $Value) { return $table }
    foreach ($property in $Value.PSObject.Properties) {
        $table[$property.Name] = [string]$property.Value
    }
    return $table
}

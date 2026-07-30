param(
    [Parameter(Mandatory)][string]$ComposeFile,
    [Parameter(Mandatory)][string]$ProjectName,
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'launch',
    [string]$LedgerVariant = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

if (-not (Test-Path -LiteralPath $ComposeFile -PathType Leaf)) { throw "Compose file does not exist: $ComposeFile" }
New-JmoaDirectory -Path $RunDirectory
if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $LedgerVariant `
        -Description "Audited Compose launch for $Variant." | Out-Null
}

$down = Invoke-AuditedExternal -Executable 'podman' -Arguments @('compose','-p',$ProjectName,'-f',$ComposeFile,'down','-v','--remove-orphans') `
    -LedgerDirectory $LedgerDirectory -Step "clean pre-existing $Variant stack" -TimeoutSeconds 180 -AllowFailure
$up = Invoke-AuditedExternal -Executable 'podman' -Arguments @('compose','-p',$ProjectName,'-f',$ComposeFile,'up','-d') `
    -LedgerDirectory $LedgerDirectory -Step "launch $Variant stack" -TimeoutSeconds 300
if ($up.exitCode -ne 0) { throw "Compose launch failed for $Variant`: $($up.output)" }

$inspect = Invoke-AuditedExternal -Executable 'podman' -Arguments @('inspect',$ContainerName) `
    -LedgerDirectory $LedgerDirectory -Step "inspect launched $Variant container"
if ($inspect.exitCode -ne 0) { throw "Launched container not found: $ContainerName" }

$jdkProbe = Invoke-AuditedExternal -Executable 'podman' -Arguments @(
    'exec', $ContainerName, 'sh', '-lc',
    'env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS java -XshowSettings:properties -version 2>&1'
) -LedgerDirectory $LedgerDirectory -Step "capture live $Variant JDK fingerprint" -AllowFailure
if ($jdkProbe.exitCode -eq 0) {
    $raw = [string]$jdkProbe.output
    function Get-JdkSetting([string]$Name) {
        $match = [regex]::Match($raw, "(?m)^\s*$([regex]::Escape($Name))\s*=\s*(.+?)\s*$")
        if ($match.Success) { return $match.Groups[1].Value.Trim() }
        return ''
    }
    $versionMatch = [regex]::Match($raw, '(?m)^(openjdk|java) version .+$')
    $javaVersionRaw = if ($versionMatch.Success) { $versionMatch.Value.Trim() } else { $raw.Trim() }
    $fingerprint = [ordered]@{
        metadataVersion = 'jmoa-runtime-jdk-fingerprint-v1'
        variant = $Variant
        containerName = $ContainerName
        javaVersionRaw = $javaVersionRaw
        javaVendor = Get-JdkSetting 'java.vendor'
        javaRuntimeVersion = Get-JdkSetting 'java.runtime.version'
        javaVmVersion = Get-JdkSetting 'java.vm.version'
        javaHome = Get-JdkSetting 'java.home'
        osArch = Get-JdkSetting 'os.arch'
        fingerprintSha256 = Get-JmoaTextSha256 -Value $raw
    }
    Write-JmoaJson -Value $fingerprint -Path (Join-Path $RunDirectory 'runtime-jdk-fingerprint.json')
}

if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'COMPLETE' -Stage $LedgerStage -Variant $LedgerVariant | Out-Null
}

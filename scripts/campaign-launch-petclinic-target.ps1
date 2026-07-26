<#
.SYNOPSIS
    Starts one customers-service target against an already-running pair-scoped support stack.
#>
param(
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$Image,
    [Parameter(Mandatory)][string]$SupportNetwork,
    [int]$Port = 8081,
    [int]$CustomerReadyTimeoutSeconds = 900,
    [long]$MinAvailableMemoryBeforeTargetBytes = 314572800,
    [string]$ContainerCli = 'podman',
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'launch',
    [string]$LedgerVariant = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
New-JmoaDirectory $RunDirectory
if (-not $LedgerVariant) { $LedgerVariant=$Variant }
if ($LedgerDirectory) { Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $LedgerVariant -Description "Target-only customers-service launch for $Variant." | Out-Null }
function Invoke-Cli {
    param(
        [Parameter(Mandatory)][string]$Step,
        [Parameter(Mandatory)][string[]]$CliArguments,
        [switch]$AllowFailure
    )
    Invoke-AuditedExternal -Executable $ContainerCli -Arguments $CliArguments -LedgerDirectory $LedgerDirectory -Step $Step -AllowFailure:$AllowFailure
}
try {
    $preCleanResult = Invoke-Cli -Step 'pre-clean target' -CliArguments @('rm','-f',$ContainerName) -AllowFailure
    $preCleanResult | Out-Null
    $imageResult = Invoke-Cli -Step 'resolve target image' -CliArguments @('image','inspect','--format','{{.Id}}',$Image)
    $imageId = $imageResult.stdout.Trim()
    $headroomResult = Invoke-Cli -Step 'capture MemAvailable before target' -CliArguments @('machine','ssh',"awk '/^MemAvailable:/ {print `$2 * 1024}' /proc/meminfo")
    $headroom = [long]$headroomResult.stdout.Trim()
    if ($headroom -lt $MinAvailableMemoryBeforeTargetBytes) { throw "MemAvailable $headroom is below $MinAvailableMemoryBeforeTargetBytes." }
    $flags='-XX:+UseContainerSupport -XX:+UseSerialGC -Xms32m -Xmx256m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -XX:NativeMemoryTracking=summary -Xshare:off'
    $startResult = Invoke-Cli -Step 'start customers-service target' -CliArguments @('run','-d','--name',$ContainerName,'--network',$SupportNetwork,'--network-alias','customers-service','-p',"${Port}:8081",'-e','SPRING_PROFILES_ACTIVE=docker','-e','CONFIG_SERVER_URI=http://config-server:8888','-e',"EUREKA_INSTANCE_INSTANCE_ID=$ContainerName",'-e',"JAVA_TOOL_OPTIONS=$flags",'-e','MALLOC_ARENA_MAX=1',$imageId)
    $startResult | Out-Null
    $deadline=[DateTime]::UtcNow.AddSeconds($CustomerReadyTimeoutSeconds); $probe=0; $healthy=$false
    while ([DateTime]::UtcNow -lt $deadline) {
        $probe++; $h=Invoke-AuditedHttp -Method GET -Uri "http://localhost:$Port/actuator/health" -LedgerDirectory $LedgerDirectory -Step "target health probe $probe" -TimeoutSeconds 10
        if ([int]$h.status -eq 200) { $healthy=$true; break }; Start-Sleep 2
    }
    if (-not $healthy) { throw 'customers-service did not become healthy.' }
    $liveResult = Invoke-Cli -Step 'verify target live image' -CliArguments @('inspect','--format','{{.Image}}',$ContainerName)
    $live = $liveResult.stdout.Trim()
    if (($live -replace '^sha256:','') -ne ($imageId -replace '^sha256:','')) { throw 'Target live image mismatch.' }
    $jdkVersionResult = Invoke-Cli -Step 'target JDK java -version' -CliArguments @('exec',$ContainerName,'java','-version')
    $settingsResult = Invoke-Cli -Step 'target JDK settings' -CliArguments @('exec',$ContainerName,'java','-XshowSettings:properties','-version')
    $jdkVersion = $jdkVersionResult.output.Trim()
    $settings = $settingsResult.output
    $fp=[ordered]@{metadataVersion='jmoa-runtime-jdk-fingerprint-v1';variant=$Variant;containerName=$ContainerName;javaVersionRaw=$jdkVersion;settingsRaw=$settings;fingerprintSha256=Get-JmoaTextSha256 -Value "$jdkVersion|$settings"}
    Write-JmoaJson $fp (Join-Path $RunDirectory 'runtime-jdk-fingerprint.json')
    $registered=$false
    for($i=1;$i -le 45;$i++) {
        $e=Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8761/eureka/apps/CUSTOMERS-SERVICE' -LedgerDirectory $LedgerDirectory -Step "target registration probe $i" -TimeoutSeconds 10
        if ([int]$e.status -eq 200 -and "$($e.body)" -match [regex]::Escape($ContainerName)) { $registered=$true; break }
        Start-Sleep 2
    }
    if (-not $registered) { throw "Unique discovery registration $ContainerName was not observed." }
    Write-JmoaJson ([ordered]@{schemaVersion='jmoa-target-launch-v1';variant=$Variant;containerName=$ContainerName;instanceId=$ContainerName;supportNetwork=$SupportNetwork;requestedImage=$Image;resolvedImageId=$imageId;launchedImageId=$live;availableMemoryBeforeTargetBytes=$headroom;customerFlags=$flags;mallocArenaMax='1';registered=$registered;healthProbes=$probe;jdkFingerprint=$fp}) (Join-Path $RunDirectory 'stack-launch-info.json')
    if ($LedgerDirectory) { Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status COMPLETE -Stage $LedgerStage -Variant $LedgerVariant | Out-Null }
} catch {
    $failedLogsResult = Invoke-Cli -Step 'capture failed target logs' -CliArguments @('logs','--tail','200',$ContainerName) -AllowFailure
    $failedLogsResult | Out-Null
    $failedRemoveResult = Invoke-Cli -Step 'remove failed target' -CliArguments @('rm','-f',$ContainerName) -AllowFailure
    $failedRemoveResult | Out-Null
    if ($LedgerDirectory) { Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status FAILED -Stage $LedgerStage -Variant $LedgerVariant | Out-Null }
    throw
}

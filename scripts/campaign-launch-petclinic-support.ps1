<#
.SYNOPSIS
    Starts the frozen PetClinic config/discovery support stack for one target-only pair.
#>
param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$PairId,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [int]$SettleSeconds = 180,
    [int]$ConfigReadyTimeoutSeconds = 180,
    [int]$DiscoveryReadyTimeoutSeconds = 180,
    [long]$MinAvailableMemoryBytes = 734003200,
    [string]$ContainerCli = 'podman',
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'support-launch'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
New-JmoaDirectory $OutputDirectory
if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant 'SUPPORT' -Description "PETCLINIC_TARGET_ONLY_V1 pair-scoped support startup and fixed settle." | Out-Null
}

$safePair = $PairId -replace '[^A-Za-z0-9_.-]', '-'
$network = "jmoa-$safePair-net"
$configName = "jmoa-$safePair-cfg"
$discoveryName = "jmoa-$safePair-disc"
$configFlags = '-XX:+UseContainerSupport -XX:+UseSerialGC -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off'
$discoveryFlags = '-XX:+UseContainerSupport -XX:+UseSerialGC -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off'

function Invoke-Cli {
    param(
        [Parameter(Mandatory)][string]$Step,
        [Parameter(Mandatory)][string[]]$CliArguments,
        [switch]$AllowFailure
    )
    Invoke-AuditedExternal -Executable $ContainerCli -Arguments $CliArguments -LedgerDirectory $LedgerDirectory -Step $Step -AllowFailure:$AllowFailure
}
function Resolve-Image([string]$Reference, [string]$Role) {
    $r = Invoke-Cli -Step "resolve $Role image" -CliArguments @('image', 'inspect', '--format', '{{.Id}}', $Reference)
    $id = $r.stdout.Trim()
    if ([string]::IsNullOrWhiteSpace($id)) { throw "Could not resolve $Role image $Reference." }
    $id
}
function Wait-Health([string]$Role, [string]$Uri, [int]$Timeout) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Timeout)
    $probe = 0
    while ([DateTime]::UtcNow -lt $deadline) {
        $probe++
        $r = Invoke-AuditedHttp -Method GET -Uri $Uri -LedgerDirectory $LedgerDirectory -Step "$Role health probe $probe" -TimeoutSeconds 10
        if ([int]$r.status -eq 200) { return $probe }
        Start-Sleep 2
    }
    throw "$Role did not become healthy."
}
function Capture-HostValidity([string]$Point) {
    $cmd = @'
printf '%s\n' '---MEMINFO---'; cat /proc/meminfo
printf '%s\n' '---PRESSURE---'; cat /proc/pressure/memory
printf '%s\n' '---USER_EVENTS---'; cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.events
printf '%s\n' '---USER_SWAP---'; cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.swap.current
'@.Replace("`r", '')
    $r = Invoke-Cli -Step "capture host validity $Point" -CliArguments @('machine', 'ssh', $cmd)
    $text = $r.stdout
    $available = [long]([regex]::Match($text, '(?m)^MemAvailable:\s+(\d+)\s+kB$').Groups[1].Value) * 1024
    $swapTotal = [long]([regex]::Match($text, '(?m)^SwapTotal:\s+(\d+)\s+kB$').Groups[1].Value) * 1024
    $full = [double]([regex]::Match($text, '(?m)^full\s+avg10=([0-9.]+)').Groups[1].Value)
    $oom = [long]([regex]::Match($text, '(?m)^oom\s+(\d+)$').Groups[1].Value)
    $oomKill = [long]([regex]::Match($text, '(?m)^oom_kill\s+(\d+)$').Groups[1].Value)
    $swapCurrent = [long]([regex]::Matches($text, '(?m)^\d+$') | Select-Object -Last 1).Value
    [ordered]@{ point=$Point; availableMemoryBytes=$available; swapTotalBytes=$swapTotal; cgroupSwapCurrentBytes=$swapCurrent; psiFullAvg10=$full; oomEvents=$oom; oomKillEvents=$oomKill }
}

try {
    foreach ($name in @($configName, $discoveryName)) {
        $preCleanResult = Invoke-Cli -Step "pre-clean $name" -CliArguments @('rm','-f',$name) -AllowFailure
        $preCleanResult | Out-Null
    }
    $preCleanNetworkResult = Invoke-Cli -Step "pre-clean $network" -CliArguments @('network','rm',$network) -AllowFailure
    $preCleanNetworkResult | Out-Null
    $configId = Resolve-Image $ConfigImage 'config'
    $discoveryId = Resolve-Image $DiscoveryImage 'discovery'
    $networkResult = Invoke-Cli -Step "create network $network" -CliArguments @('network','create',$network)
    $networkResult | Out-Null
    $configStartResult = Invoke-Cli -Step 'start config-server' -CliArguments @('run','-d','--name',$configName,'--network',$network,'--network-alias','config-server','-p','8888:8888','-v',"${ConfigRepo}:/app/config-repo:ro",'-e','SPRING_PROFILES_ACTIVE=native','-e','GIT_REPO=/app/config-repo','-e','MANAGEMENT_TRACING_ENABLED=false','-e','MANAGEMENT_METRICS_ENABLED=false','-e',"JAVA_TOOL_OPTIONS=$configFlags",$configId)
    $configStartResult | Out-Null
    $configProbes = Wait-Health 'config' 'http://localhost:8888/actuator/health' $ConfigReadyTimeoutSeconds
    $discoveryStartResult = Invoke-Cli -Step 'start discovery-server' -CliArguments @('run','-d','--name',$discoveryName,'--network',$network,'--network-alias','discovery-server','-p','8761:8761','-e','SPRING_PROFILES_ACTIVE=docker','-e','CONFIG_SERVER_URI=http://config-server:8888','-e',"JAVA_TOOL_OPTIONS=$discoveryFlags",$discoveryId)
    $discoveryStartResult | Out-Null
    $discoveryProbes = Wait-Health 'discovery' 'http://localhost:8761/actuator/health' $DiscoveryReadyTimeoutSeconds
    $healthyAt = [DateTime]::UtcNow
    Start-Sleep -Seconds $SettleSeconds
    $validity = Capture-HostValidity 'POST_FIXED_SETTLE'
    $configRestartResult = Invoke-Cli -Step 'config restart count' -CliArguments @('inspect','--format','{{.RestartCount}}',$configName)
    $discoveryRestartResult = Invoke-Cli -Step 'discovery restart count' -CliArguments @('inspect','--format','{{.RestartCount}}',$discoveryName)
    $configLiveResult = Invoke-Cli -Step 'config live image' -CliArguments @('inspect','--format','{{.Image}}',$configName)
    $discoveryLiveResult = Invoke-Cli -Step 'discovery live image' -CliArguments @('inspect','--format','{{.Image}}',$discoveryName)
    $configJdkResult = Invoke-Cli -Step 'config JDK fingerprint' -CliArguments @('exec',$configName,'java','-version')
    $discoveryJdkResult = Invoke-Cli -Step 'discovery JDK fingerprint' -CliArguments @('exec',$discoveryName,'java','-version')
    $configRestart = [int]$configRestartResult.stdout.Trim()
    $discoveryRestart = [int]$discoveryRestartResult.stdout.Trim()
    $configLive = $configLiveResult.stdout.Trim()
    $discoveryLive = $discoveryLiveResult.stdout.Trim()
    $configJdk = $configJdkResult.output.Trim()
    $discoveryJdk = $discoveryJdkResult.output.Trim()
    $reasons = [Collections.Generic.List[string]]::new()
    if ($validity.availableMemoryBytes -lt $MinAvailableMemoryBytes) { $reasons.Add('MemAvailable below 700 MiB') }
    if ($validity.swapTotalBytes -ne 0 -or $validity.cgroupSwapCurrentBytes -ne 0) { $reasons.Add('swap is not zero') }
    if ($validity.psiFullAvg10 -ne 0) { $reasons.Add('PSI full avg10 is not zero') }
    if ($validity.oomEvents -ne 0 -or $validity.oomKillEvents -ne 0) { $reasons.Add('OOM events are not zero') }
    if ($configRestart -ne 0 -or $discoveryRestart -ne 0) { $reasons.Add('support restart count is not zero') }
    if ($configLive -notmatch [regex]::Escape(($configId -replace '^sha256:','')) -and $configId -notmatch [regex]::Escape(($configLive -replace '^sha256:',''))) { $reasons.Add('config image mismatch') }
    if ($discoveryLive -notmatch [regex]::Escape(($discoveryId -replace '^sha256:','')) -and $discoveryId -notmatch [regex]::Escape(($discoveryLive -replace '^sha256:',''))) { $reasons.Add('discovery image mismatch') }
    $report = [ordered]@{
        schemaVersion='jmoa-petclinic-target-only-support-v1'; protocol='PETCLINIC_TARGET_ONLY_V1'; pairId=$PairId
        network=$network; configName=$configName; discoveryName=$discoveryName; configImageId=$configId; discoveryImageId=$discoveryId
        configLiveImageId=$configLive; discoveryLiveImageId=$discoveryLive; configJdk=$configJdk; discoveryJdk=$discoveryJdk
        configRepo=(Resolve-Path $ConfigRepo).Path; configTreeSha256=Get-CampaignTreeSha256 -Root $ConfigRepo
        fixedSettleSeconds=$SettleSeconds; healthyAtUtc=$healthyAt.ToString('o'); validity=$validity
        restartCounts=[ordered]@{config=$configRestart;discovery=$discoveryRestart}; healthProbes=[ordered]@{config=$configProbes;discovery=$discoveryProbes}
        passed=($reasons.Count -eq 0); reasons=$reasons.ToArray()
    }
    Write-JmoaJson $report (Join-Path $OutputDirectory 'support-admission.json')
    if (-not $report.passed) { throw "Support validity failed: $($report.reasons -join '; ')" }
    if ($LedgerDirectory) { Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status COMPLETE -Stage $LedgerStage -Variant SUPPORT | Out-Null }
} catch {
    if ($LedgerDirectory) { Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status FAILED -Stage $LedgerStage -Variant SUPPORT | Out-Null }
    throw
}

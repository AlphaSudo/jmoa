param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [string]$ContainerCli = '/usr/bin/podman',
    [ValidateSet('STANDARD_FIXED_8G', 'HYPERV_DEBIAN_FIXED_2G')][string]$HostProfile = 'STANDARD_FIXED_8G',
    [int]$Samples = 10,
    [int]$SampleIntervalSeconds = 7,
    [long]$MaxAggregateMemoryCurrentDriftBytes = 2097152
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-linux-host-profiles.ps1')

$profile = Get-CampaignLinuxHostProfile -Name $HostProfile
New-JmoaDirectory -Path $OutputDirectory
$ledger = Join-Path $OutputDirectory 'command-ledger'
Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'linux-support-stack-calibration' -Variant 'SUPPORT_ONLY' `
    -Description "One frozen config/discovery support stack, sampled $Samples times over at least $(($Samples - 1) * $SampleIntervalSeconds) seconds. No target JVM." | Out-Null

function Invoke-Cli {
    param([string]$Step, [string[]]$Arguments, [switch]$AllowFailure)
    return Invoke-AuditedExternal -Executable $ContainerCli -Arguments $Arguments -LedgerDirectory $ledger -Step $Step -AllowFailure:$AllowFailure
}
function Invoke-Host {
    param([string]$Step, [string]$Command)
    return Invoke-AuditedExternal -Executable '/bin/bash' -Arguments @('-lc', $Command) -LedgerDirectory $ledger -Step $Step
}
function Wait-Health {
    param([string]$Role, [string]$Uri, [int]$TimeoutSeconds = 240)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $probe = 0
    while ([DateTime]::UtcNow -lt $deadline) {
        $probe++
        $response = Invoke-AuditedHttp -Method GET -Uri $Uri -LedgerDirectory $ledger -Step "$Role health probe $probe" -TimeoutSeconds 10
        if ($response.status -eq 200) { return }
        Start-Sleep -Seconds 2
    }
    throw "$Role did not become healthy."
}
function Read-MemInfoBytes {
    param([string]$Text, [string]$Name)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { throw "/proc/meminfo is missing $Name." }
    return [long]$match.Groups[1].Value * 1024
}
function Read-Psi {
    param([string]$Text, [string]$Kind)
    $match = [regex]::Match($Text, "(?m)^$Kind\s+avg10=([0-9.]+)")
    if (-not $match.Success) { throw "Memory PSI output is missing $Kind avg10." }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}
function Get-ContainerPid {
    param([string]$Name)
    $result = Invoke-Cli -Step "capture $Name host PID" -Arguments @('inspect', '--format', '{{.State.Pid}}', $Name)
    return [int]$result.stdout.Trim()
}
function Get-ContainerRestartCount {
    param([string]$Name)
    $result = Invoke-Cli -Step "capture $Name restart count" -Arguments @('inspect', '--format', '{{.RestartCount}}', $Name)
    return [int]$result.stdout.Trim()
}
function Get-ContainerMemoryCurrent {
    param([string]$Name)
    $result = Invoke-Cli -Step "capture $Name memory.current" -Arguments @('exec', $Name, 'sh', '-lc', 'cat /sys/fs/cgroup/memory.current')
    return [long]$result.stdout.Trim()
}
function Get-SmapsRollup {
    param([int]$Pid, [string]$Name)
    $result = Invoke-Host -Step "capture $Name smaps_rollup" -Command "cat /proc/$Pid/smaps_rollup"
    $pss = [regex]::Match($result.stdout, '(?m)^Pss:\s+(\d+)\s+kB$')
    $dirty = [regex]::Match($result.stdout, '(?m)^Private_Dirty:\s+(\d+)\s+kB$')
    if (-not $pss.Success -or -not $dirty.Success) { throw "Could not parse $Name smaps_rollup." }
    return [pscustomobject]@{ pssKb = [long]$pss.Groups[1].Value; privateDirtyKb = [long]$dirty.Groups[1].Value }
}

$network = 'jmoa-cal-support-net'
$config = 'jmoa-cal-support-cfg'
$discovery = 'jmoa-cal-support-disc'
$rows = [Collections.Generic.List[object]]::new()
$finalStatus = 'FAILED'
try {
    foreach ($name in @($discovery, $config)) { Invoke-Cli -Step "pre-clean $name" -Arguments @('rm', '-f', $name) -AllowFailure | Out-Null }
    Invoke-Cli -Step "pre-clean $network" -Arguments @('network', 'rm', $network) -AllowFailure | Out-Null
    Invoke-Cli -Step "create $network" -Arguments @('network', 'create', $network) | Out-Null
    Invoke-Cli -Step 'start frozen config support service' -Arguments @(
        'run','-d','--name',$config,'--network',$network,'--network-alias','config-server','-p','8888:8888',
        '-v',"${ConfigRepo}:/app/config-repo:ro",'-e','SPRING_PROFILES_ACTIVE=native','-e','GIT_REPO=/app/config-repo',
        '-e','MANAGEMENT_TRACING_ENABLED=false','-e','MANAGEMENT_METRICS_ENABLED=false',
        '-e','JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:+UseSerialGC -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off',
        $ConfigImage
    ) | Out-Null
    Wait-Health -Role 'config' -Uri 'http://localhost:8888/actuator/health'
    Invoke-Cli -Step 'start frozen discovery support service' -Arguments @(
        'run','-d','--name',$discovery,'--network',$network,'--network-alias','discovery-server','-p','8761:8761',
        '-e','SPRING_PROFILES_ACTIVE=docker','-e','CONFIG_SERVER_URI=http://config-server:8888',
        '-e','JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:+UseSerialGC -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off',
        $DiscoveryImage
    ) | Out-Null
    Wait-Health -Role 'discovery' -Uri 'http://localhost:8761/actuator/health'
    $configPid = Get-ContainerPid -Name $config
    $discoveryPid = Get-ContainerPid -Name $discovery

    for ($sample = 1; $sample -le $Samples; $sample++) {
        $configHealth = Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8888/actuator/health' -LedgerDirectory $ledger -Step "sample $sample config health"
        $discoveryHealth = Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8761/actuator/health' -LedgerDirectory $ledger -Step "sample $sample discovery health"
        $meminfo = Invoke-Host -Step "sample $sample meminfo" -Command 'cat /proc/meminfo'
        $psi = Invoke-Host -Step "sample $sample memory PSI" -Command 'cat /proc/pressure/memory'
        $events = Invoke-Host -Step "sample $sample memory events" -Command 'cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.events'
        $swapCurrent = Invoke-Host -Step "sample $sample cgroup swap current" -Command 'cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.swap.current'
        $configSmaps = Get-SmapsRollup -Pid $configPid -Name $config
        $discoverySmaps = Get-SmapsRollup -Pid $discoveryPid -Name $discovery
        $configMemory = Get-ContainerMemoryCurrent -Name $config
        $discoveryMemory = Get-ContainerMemoryCurrent -Name $discovery
        $rows.Add([ordered]@{
            sample = $sample
            configHealthStatus = $configHealth.status
            discoveryHealthStatus = $discoveryHealth.status
            configRestartCount = Get-ContainerRestartCount -Name $config
            discoveryRestartCount = Get-ContainerRestartCount -Name $discovery
            aggregatePssKb = $configSmaps.pssKb + $discoverySmaps.pssKb
            aggregatePrivateDirtyKb = $configSmaps.privateDirtyKb + $discoverySmaps.privateDirtyKb
            aggregateMemoryCurrentBytes = $configMemory + $discoveryMemory
            hostAvailableMemoryBytes = Read-MemInfoBytes -Text $meminfo.stdout -Name 'MemAvailable'
            swapTotalBytes = Read-MemInfoBytes -Text $meminfo.stdout -Name 'SwapTotal'
            cgroupSwapCurrentBytes = [long]$swapCurrent.stdout.Trim()
            memoryPressureSomeAvg10 = Read-Psi -Text $psi.stdout -Kind 'some'
            memoryPressureFullAvg10 = Read-Psi -Text $psi.stdout -Kind 'full'
            oomEvents = Get-CampaignMemoryEventValue -Text $events.stdout -Name 'oom'
            oomKillEvents = Get-CampaignMemoryEventValue -Text $events.stdout -Name 'oom_kill'
        }) | Out-Null
        if ($sample -lt $Samples -and $SampleIntervalSeconds -gt 0) { Start-Sleep -Seconds $SampleIntervalSeconds }
    }

    $memoryValues = @($rows | ForEach-Object aggregateMemoryCurrentBytes)
    $drift = ([long]($memoryValues | Measure-Object -Maximum).Maximum) - ([long]($memoryValues | Measure-Object -Minimum).Minimum)
    $minimumAvailable = [long](@($rows | Measure-Object -Property hostAvailableMemoryBytes -Minimum).Minimum)
    $reasons = [Collections.Generic.List[string]]::new()
    if ($drift -gt $MaxAggregateMemoryCurrentDriftBytes) { $reasons.Add("support aggregate memory.current drift $drift exceeds $MaxAggregateMemoryCurrentDriftBytes bytes") | Out-Null }
    if ($minimumAvailable -lt $profile.minSupportReadyMemoryBytes) { $reasons.Add("minimum MemAvailable $minimumAvailable is below $($profile.minSupportReadyMemoryBytes) bytes") | Out-Null }
    if (@($rows | Where-Object { $_.configHealthStatus -ne 200 -or $_.discoveryHealthStatus -ne 200 }).Count -ne 0) { $reasons.Add('support health failed') | Out-Null }
    if (@($rows | Where-Object { $_.configRestartCount -ne 0 -or $_.discoveryRestartCount -ne 0 }).Count -ne 0) { $reasons.Add('support container restarted') | Out-Null }
    if (@($rows | Where-Object { $_.swapTotalBytes -ne 0 -or $_.cgroupSwapCurrentBytes -ne 0 }).Count -ne 0) { $reasons.Add('swap was configured or used') | Out-Null }
    if (@($rows | Where-Object { $_.memoryPressureSomeAvg10 -gt 0 -or $_.memoryPressureFullAvg10 -gt 0 }).Count -ne 0) { $reasons.Add('memory PSI some/full avg10 was nonzero') | Out-Null }
    if (@($rows | Where-Object { $_.oomEvents -ne 0 -or $_.oomKillEvents -ne 0 }).Count -ne 0) { $reasons.Add('OOM counters were nonzero') | Out-Null }
    $report = [ordered]@{
        schemaVersion = 'jmoa-linux-support-calibration-v2'
        hostProfile = $profile.name
        resourceClass = $profile.resourceClass
        samples = $rows.ToArray()
        aggregateMemoryCurrentDriftBytes = $drift
        maxAggregateMemoryCurrentDriftBytes = $MaxAggregateMemoryCurrentDriftBytes
        minimumAvailableMemoryBytes = $minimumAvailable
        minRequiredAvailableMemoryBytes = $profile.minSupportReadyMemoryBytes
        passed = ($reasons.Count -eq 0)
        terminalVerdict = if ($reasons.Count -eq 0) { 'SUPPORT_STACK_CALIBRATION_PASSED' } else { 'STOPPED_INSUFFICIENT_SUPPORT_STACK_HEADROOM' }
        reasons = $reasons.ToArray()
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'linux-host-calibration.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'linux-host-calibration.md') -Value @"
# Linux Support-Stack Calibration

- Host profile: $($profile.name)
- Samples: $Samples
- Sampling duration: at least $(($Samples - 1) * $SampleIntervalSeconds) seconds
- Aggregate memory.current drift: $drift bytes
- Frozen drift limit: $MaxAggregateMemoryCurrentDriftBytes bytes
- Minimum MemAvailable: $minimumAvailable bytes
- Required support-ready MemAvailable: $($profile.minSupportReadyMemoryBytes) bytes
- Passed: **$($report.passed)**
- Terminal verdict: $($report.terminalVerdict)

## Reasons
$(if ($reasons.Count -eq 0) { '- none' } else { ($reasons | ForEach-Object { "- $_" }) -join "`n" })
"@
    $finalStatus = if ($report.passed) { 'COMPLETE' } else { 'FAILED' }
    if (-not $report.passed) { exit 2 }
} catch {
    $finalStatus = 'FAILED'
    throw
} finally {
    foreach ($name in @($discovery, $config)) { Invoke-Cli -Step "final remove $name" -Arguments @('rm', '-f', $name) -AllowFailure | Out-Null }
    Invoke-Cli -Step "final remove $network" -Arguments @('network', 'rm', $network) -AllowFailure | Out-Null
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status $finalStatus `
        -Stage 'linux-support-stack-calibration' -Variant 'SUPPORT_ONLY' | Out-Null
}

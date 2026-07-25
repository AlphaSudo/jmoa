param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$ContainerCli = '/usr/bin/podman',
    [int[]]$RequiredFreePorts = @(8081, 8761, 8888),
    [ValidateSet('STANDARD_FIXED_8G', 'HYPERV_DEBIAN_FIXED_2G')][string]$HostProfile = 'STANDARD_FIXED_8G',
    [long]$MinTotalMemoryBytes = 8589934592,
    [long]$MinPodmanAvailableMemoryBytes = 1073741824,
    [int]$MinLogicalProcessorCount = 4,
    [bool]$RequireSwapDisabled = $true,
    [long]$MaxPodmanSwapUsedBytes = 0,
    [double]$MaxPodmanMemoryPressureSomeAvg10 = 1.0,
    [double]$MaxPodmanMemoryPressureFullAvg10 = 0.1,
    [string]$LedgerDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-linux-host-profiles.ps1')

$profile = Get-CampaignLinuxHostProfile -Name $HostProfile
if ($profile.name -eq 'HYPERV_DEBIAN_FIXED_2G') {
    $MinTotalMemoryBytes = $profile.minTotalMemoryBytes
    $MinPodmanAvailableMemoryBytes = $profile.minPreflightAvailableMemoryBytes
    $MinLogicalProcessorCount = $profile.minLogicalProcessorCount
    $RequireSwapDisabled = $profile.requireSwapDisabled
    $MaxPodmanSwapUsedBytes = $profile.maxSwapUsedBytes
    $MaxPodmanMemoryPressureSomeAvg10 = $profile.maxMemoryPressureSomeAvg10
    $MaxPodmanMemoryPressureFullAvg10 = $profile.maxMemoryPressureFullAvg10
} else {
    $profile.minTotalMemoryBytes = $MinTotalMemoryBytes
    $profile.minPreflightAvailableMemoryBytes = $MinPodmanAvailableMemoryBytes
    $profile.minLogicalProcessorCount = $MinLogicalProcessorCount
    $profile.requireSwapDisabled = $RequireSwapDisabled
    $profile.maxSwapUsedBytes = $MaxPodmanSwapUsedBytes
    $profile.maxMemoryPressureSomeAvg10 = $MaxPodmanMemoryPressureSomeAvg10
    $profile.maxMemoryPressureFullAvg10 = $MaxPodmanMemoryPressureFullAvg10
}

New-JmoaDirectory -Path $OutputDirectory
if ([string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    $LedgerDirectory = Join-Path $OutputDirectory 'command-ledger'
}
Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage 'linux-host-preflight' -Variant 'SHARED' `
    -Description 'Audited native-Linux host, Hyper-V, Podman, port, memory, swap, PSI, cgroup, and background-work preflight.' | Out-Null

function Invoke-LinuxCheck {
    param([string]$Step, [string]$Command, [switch]$AllowFailure)
    return Invoke-AuditedExternal -Executable '/bin/bash' -Arguments @('-lc', $Command) `
        -LedgerDirectory $LedgerDirectory -Step $Step -AllowFailure:$AllowFailure
}
function Read-MemInfoBytes {
    param([string]$Text, [string]$Name)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { throw "/proc/meminfo is missing $Name." }
    return [long]$match.Groups[1].Value * 1024
}
function Read-Psi {
    param([string]$Text, [ValidateSet('some', 'full')][string]$Kind, [string]$Metric)
    $line = @($Text -split '\r?\n' | Where-Object { $_ -match "^$Kind\s" } | Select-Object -First 1)
    if ($line.Count -ne 1) { throw "Linux PSI output is missing '$Kind'." }
    $match = [regex]::Match([string]$line[0], "(?:^|\s)$([regex]::Escape($Metric))=([0-9.]+)(?:\s|$)")
    if (-not $match.Success) { throw "Linux PSI '$Kind' is missing $Metric." }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}

try {
    $commands = [ordered]@{
        uname = 'uname -a'
        bootId = 'cat /proc/sys/kernel/random/boot_id'
        uptime = 'cat /proc/uptime'
        osRelease = 'cat /etc/os-release'
        virtualization = 'systemd-detect-virt'
        cpu = 'lscpu'
        memory = 'free -b'
        meminfo = 'cat /proc/meminfo'
        swaps = 'cat /proc/swaps'
        memoryPsi = 'cat /proc/pressure/memory'
        cpuPsi = 'cat /proc/pressure/cpu'
        cgroup = 'stat -fc %T /sys/fs/cgroup; cat /sys/fs/cgroup/cgroup.controllers'
        memoryEvents = 'cat /sys/fs/cgroup/memory.events'
        cgroupSwapCurrent = 'cat /sys/fs/cgroup/memory.swap.current 2>/dev/null || echo 0'
        clocksource = 'cat /sys/devices/system/clocksource/clocksource0/current_clocksource; cat /sys/devices/system/clocksource/clocksource0/available_clocksource'
        governors = 'found=0; for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do found=1; printf "%s=" "$f"; cat "$f"; done; [ "$found" -eq 0 ] && echo UNAVAILABLE || true'
        podmanVersion = "$ContainerCli version"
        podmanInfo = "$ContainerCli info"
        runningContainers = "$ContainerCli ps --format json"
        java = 'if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then "$JAVA_HOME/bin/java" -version; else java -version; fi'
        maven = 'mvn -version'
        powershell = 'pwsh --version'
        filesystems = 'findmnt'
        timers = 'systemctl list-timers --all --no-pager'
        topProcesses = 'ps -eo pid,comm,%cpu,%mem --sort=-%cpu | head -20'
        listeningPorts = "ss -H -ltn '( sport = :8081 or sport = :8761 or sport = :8888 )' || true"
    }
    $results = [ordered]@{}
    foreach ($entry in $commands.GetEnumerator()) {
        $results[$entry.Key] = Invoke-LinuxCheck -Step $entry.Key -Command $entry.Value
    }

    $totalMemoryBytes = Read-MemInfoBytes -Text $results.meminfo.stdout -Name 'MemTotal'
    $availableBytes = Read-MemInfoBytes -Text $results.meminfo.stdout -Name 'MemAvailable'
    $swapTotalBytes = Read-MemInfoBytes -Text $results.meminfo.stdout -Name 'SwapTotal'
    $swapFreeBytes = Read-MemInfoBytes -Text $results.meminfo.stdout -Name 'SwapFree'
    $swapUsedBytes = $swapTotalBytes - $swapFreeBytes
    $someAvg10 = Read-Psi -Text $results.memoryPsi.stdout -Kind 'some' -Metric 'avg10'
    $fullAvg10 = Read-Psi -Text $results.memoryPsi.stdout -Kind 'full' -Metric 'avg10'
    $oomEvents = Get-CampaignMemoryEventValue -Text $results.memoryEvents.stdout -Name 'oom'
    $oomKillEvents = Get-CampaignMemoryEventValue -Text $results.memoryEvents.stdout -Name 'oom_kill'
    $cgroupSwapCurrentBytes = [long]$results.cgroupSwapCurrent.stdout.Trim()
    $containerObjects = @()
    if (-not [string]::IsNullOrWhiteSpace($results.runningContainers.stdout)) {
        $parsedContainers = $results.runningContainers.stdout | ConvertFrom-Json
        if ($null -ne $parsedContainers) { $containerObjects = @($parsedContainers) }
    }
    $portLines = @($results.listeningPorts.stdout -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $processorMatch = [regex]::Match([string]$results.cpu.stdout, '(?m)^CPU\(s\):\s+(\d+)\s*$')
    if (-not $processorMatch.Success) { throw 'lscpu output is missing the logical CPU count.' }
    $logicalProcessorCount = [int]$processorMatch.Groups[1].Value
    $admission = Test-CampaignLinuxHostAdmission -Profile $profile -Snapshot ([pscustomobject]@{
        virtualization = ([string]$results.virtualization.stdout).Trim()
        cgroupV2 = (([string]$results.cgroup.stdout) -match 'cgroup2fs')
        runningContainerCount = $containerObjects.Count
        occupiedRequiredPortCount = $portLines.Count
        totalMemoryBytes = $totalMemoryBytes
        availableMemoryBytes = $availableBytes
        logicalProcessorCount = $logicalProcessorCount
        swapTotalBytes = $swapTotalBytes
        swapUsedBytes = $swapUsedBytes
        cgroupSwapCurrentBytes = $cgroupSwapCurrentBytes
        memoryPressureSomeAvg10 = $someAvg10
        memoryPressureFullAvg10 = $fullAvg10
        oomEvents = $oomEvents
        oomKillEvents = $oomKillEvents
    })
    $reasons = [Collections.Generic.List[string]]::new()
    foreach ($reason in $admission.reasons) { $reasons.Add($reason) | Out-Null }

    $report = [ordered]@{
        schemaVersion = 'jmoa-linux-campaign-host-preflight-v1'
        capturedAt = [DateTime]::UtcNow.ToString('o')
        passed = ($reasons.Count -eq 0)
        reasons = $reasons.ToArray()
        hostProfile = $profile.name
        resourceClass = $profile.resourceClass
        bootId = ([string]$results.bootId.stdout).Trim()
        uptimeSeconds = [double](($results.uptime.stdout.Trim() -split '\s+')[0])
        virtualization = ([string]$results.virtualization.stdout).Trim()
        totalMemoryBytes = $totalMemoryBytes
        availableMemoryBytes = $availableBytes
        logicalProcessorCount = $logicalProcessorCount
        swapTotalBytes = $swapTotalBytes
        swapUsedBytes = $swapUsedBytes
        cgroupSwapCurrentBytes = $cgroupSwapCurrentBytes
        oomEvents = $oomEvents
        oomKillEvents = $oomKillEvents
        memoryPressureSomeAvg10 = $someAvg10
        memoryPressureFullAvg10 = $fullAvg10
        runningContainerCount = $containerObjects.Count
        occupiedRequiredPorts = $portLines
        thresholds = [ordered]@{
            minTotalMemoryBytes = $MinTotalMemoryBytes
            minAvailableMemoryBytes = $MinPodmanAvailableMemoryBytes
            minLogicalProcessorCount = $MinLogicalProcessorCount
            requireSwapDisabled = $RequireSwapDisabled
            maxSwapUsedBytes = $MaxPodmanSwapUsedBytes
            maxMemoryPressureSomeAvg10 = $MaxPodmanMemoryPressureSomeAvg10
            maxMemoryPressureFullAvg10 = $MaxPodmanMemoryPressureFullAvg10
        }
        commandLedger = [IO.Path]::GetFullPath($LedgerDirectory)
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'host-linux-preflight.json')
    $markdown = @"
# Linux Campaign Host Preflight

- Passed: **$($report.passed)**
- Host profile: $($report.hostProfile)
- Resource class: $($report.resourceClass)
- Virtualization: $($report.virtualization)
- Total memory: $totalMemoryBytes bytes
- Available memory: $availableBytes bytes
- Logical processors: $logicalProcessorCount
- Swap used: $swapUsedBytes bytes
- cgroup swap current: $cgroupSwapCurrentBytes bytes
- OOM / OOM-kill events: $oomEvents / $oomKillEvents
- Memory PSI some/full avg10: $someAvg10 / $fullAvg10
- Running containers: $($containerObjects.Count)
- Occupied required ports: $($portLines -join '; ')

## Reasons
$(if ($reasons.Count -eq 0) { '- none' } else { ($reasons | ForEach-Object { "- $_" }) -join "`n" })
"@
    Write-JmoaText -Value $markdown -Path (Join-Path $OutputDirectory 'host-linux-preflight.md')
    Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status $(if ($report.passed) { 'COMPLETE' } else { 'FAILED' }) -Stage 'linux-host-preflight' -Variant 'SHARED' | Out-Null
    if (-not $report.passed) { exit 2 }
} catch {
    if (-not (Test-Path -LiteralPath (Join-Path $LedgerDirectory 'child-ledger-summary.json'))) {
        Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'FAILED' -Stage 'linux-host-preflight' -Variant 'SHARED' | Out-Null
    }
    throw
}

param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$ContainerCli = '/usr/bin/podman',
    [int[]]$RequiredFreePorts = @(8081, 8761, 8888),
    [long]$MinPodmanAvailableMemoryBytes = 1073741824,
    [long]$MaxPodmanSwapUsedBytes = 0,
    [double]$MaxPodmanMemoryPressureSomeAvg10 = 1.0,
    [double]$MaxPodmanMemoryPressureFullAvg10 = 0.1,
    [string]$LedgerDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

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
        osRelease = 'cat /etc/os-release'
        virtualization = 'systemd-detect-virt'
        cpu = 'lscpu'
        memory = 'free -b'
        meminfo = 'cat /proc/meminfo'
        swaps = 'cat /proc/swaps'
        memoryPsi = 'cat /proc/pressure/memory'
        cpuPsi = 'cat /proc/pressure/cpu'
        cgroup = 'stat -fc %T /sys/fs/cgroup; cat /sys/fs/cgroup/cgroup.controllers'
        clocksource = 'cat /sys/devices/system/clocksource/clocksource0/current_clocksource; cat /sys/devices/system/clocksource/clocksource0/available_clocksource'
        governors = 'found=0; for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do found=1; printf "%s=" "$f"; cat "$f"; done; [ "$found" -eq 0 ] && echo UNAVAILABLE || true'
        podmanVersion = "$ContainerCli version"
        podmanInfo = "$ContainerCli info"
        runningContainers = "$ContainerCli ps --format json"
        java = 'java -version'
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

    $availableBytes = Read-MemInfoBytes -Text $results.meminfo.stdout -Name 'MemAvailable'
    $swapTotalBytes = Read-MemInfoBytes -Text $results.meminfo.stdout -Name 'SwapTotal'
    $swapFreeBytes = Read-MemInfoBytes -Text $results.meminfo.stdout -Name 'SwapFree'
    $swapUsedBytes = $swapTotalBytes - $swapFreeBytes
    $someAvg10 = Read-Psi -Text $results.memoryPsi.stdout -Kind 'some' -Metric 'avg10'
    $fullAvg10 = Read-Psi -Text $results.memoryPsi.stdout -Kind 'full' -Metric 'avg10'
    $containerObjects = if ([string]::IsNullOrWhiteSpace($results.runningContainers.stdout)) { @() } else { @($results.runningContainers.stdout | ConvertFrom-Json) }
    $portLines = @($results.listeningPorts.stdout -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $reasons = [Collections.Generic.List[string]]::new()
    if (([string]$results.virtualization.stdout).Trim() -ne 'microsoft') { $reasons.Add('systemd-detect-virt did not report microsoft') }
    if (([string]$results.cgroup.stdout) -notmatch 'cgroup2fs') { $reasons.Add('cgroup v2 is not mounted') }
    if ($containerObjects.Count -ne 0) { $reasons.Add("$($containerObjects.Count) running container(s) found") }
    if ($portLines.Count -ne 0) { $reasons.Add('one or more required ports are already listening') }
    if ($availableBytes -lt $MinPodmanAvailableMemoryBytes) { $reasons.Add("MemAvailable $availableBytes is below $MinPodmanAvailableMemoryBytes bytes") }
    if ($swapUsedBytes -gt $MaxPodmanSwapUsedBytes) { $reasons.Add("swap used $swapUsedBytes exceeds $MaxPodmanSwapUsedBytes bytes") }
    if ($someAvg10 -gt $MaxPodmanMemoryPressureSomeAvg10) { $reasons.Add("memory PSI some avg10 $someAvg10 exceeds $MaxPodmanMemoryPressureSomeAvg10") }
    if ($fullAvg10 -gt $MaxPodmanMemoryPressureFullAvg10) { $reasons.Add("memory PSI full avg10 $fullAvg10 exceeds $MaxPodmanMemoryPressureFullAvg10") }

    $report = [ordered]@{
        schemaVersion = 'jmoa-linux-campaign-host-preflight-v1'
        capturedAt = [DateTime]::UtcNow.ToString('o')
        passed = ($reasons.Count -eq 0)
        reasons = $reasons.ToArray()
        virtualization = ([string]$results.virtualization.stdout).Trim()
        availableMemoryBytes = $availableBytes
        swapTotalBytes = $swapTotalBytes
        swapUsedBytes = $swapUsedBytes
        memoryPressureSomeAvg10 = $someAvg10
        memoryPressureFullAvg10 = $fullAvg10
        runningContainerCount = $containerObjects.Count
        occupiedRequiredPorts = $portLines
        thresholds = [ordered]@{
            minAvailableMemoryBytes = $MinPodmanAvailableMemoryBytes
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
- Virtualization: $($report.virtualization)
- Available memory: $availableBytes bytes
- Swap used: $swapUsedBytes bytes
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

param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('STANDARD_FIXED_8G', 'HYPERV_DEBIAN_FIXED_2G')][string]$HostProfile = 'STANDARD_FIXED_8G',
    [string]$LedgerDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-linux-host-profiles.ps1')

New-JmoaDirectory -Path $OutputDirectory
$profile = Get-CampaignLinuxHostProfile -Name $HostProfile
if ([string]::IsNullOrWhiteSpace($LedgerDirectory)) { $LedgerDirectory = Join-Path $OutputDirectory 'command-ledger' }
Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage 'linux-host-fingerprint' -Variant 'SHARED' `
    -Description 'Immutable Linux/Hyper-V campaign host fingerprint captured before image import and runtime.' | Out-Null

$specs = [ordered]@{
    uname = 'uname -a'
    bootId = 'cat /proc/sys/kernel/random/boot_id'
    uptime = 'cat /proc/uptime'
    osRelease = 'cat /etc/os-release'
    virtualization = 'systemd-detect-virt'
    processors = 'nproc'
    meminfo = "grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo"
    memoryEvents = 'cat /sys/fs/cgroup/memory.events'
    memorySwapCurrent = 'cat /sys/fs/cgroup/memory.swap.current 2>/dev/null || echo UNAVAILABLE'
    hypervDmesg = 'dmesg | grep -i hyper-v || true'
    hypervModules = "lsmod | grep '^hv_' || true"
    cpu = 'lscpu'
    memory = 'free -b'
    swaps = 'cat /proc/swaps'
    mounts = 'mount'
    cgroup = 'findmnt -t cgroup2; cat /sys/fs/cgroup/cgroup.controllers'
    podmanVersion = 'podman version'
    podmanInfo = 'podman info'
    java = 'java -version'
    maven = 'mvn -version'
    powershell = 'pwsh --version'
    governor = 'found=0; for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do found=1; printf "%s=" "$f"; cat "$f"; done; [ "$found" -eq 0 ] && echo UNAVAILABLE || true'
    clocksource = 'cat /sys/devices/system/clocksource/clocksource0/current_clocksource; cat /sys/devices/system/clocksource/clocksource0/available_clocksource'
    memoryPsi = 'cat /proc/pressure/memory'
    cpuPsi = 'cat /proc/pressure/cpu'
    timers = 'systemctl list-timers --all --no-pager'
    processes = 'ps -eo pid,comm,%cpu,%mem --sort=-%cpu | head -30'
    filesystems = 'df -hT'
}
$records = [ordered]@{}
foreach ($entry in $specs.GetEnumerator()) {
    $result = Invoke-AuditedExternal -Executable '/bin/bash' -Arguments @('-lc', $entry.Value) `
        -LedgerDirectory $LedgerDirectory -Step $entry.Key -AllowFailure
    $records[$entry.Key] = [ordered]@{
        exitCode = $result.exitCode
        stdout = $result.stdout.Trim()
        stderr = $result.stderr.Trim()
    }
}
$fingerprint = [ordered]@{
    schemaVersion = 'jmoa-linux-host-fingerprint-v2'
    capturedAt = [DateTime]::UtcNow.ToString('o')
    hostProfile = $profile.name
    resourceClass = $profile.resourceClass
    admissionThresholds = $profile
    records = $records
}
$canonical = $fingerprint | ConvertTo-Json -Depth 12 -Compress
$fingerprint.fingerprintSha256 = Get-JmoaTextSha256 -Value $canonical
Write-JmoaJson -Value $fingerprint -Path (Join-Path $OutputDirectory 'linux-host-fingerprint.json')
$markdown = @"
# Linux Host Fingerprint

- Captured UTC: $($fingerprint.capturedAt)
- Fingerprint SHA-256: `$($fingerprint.fingerprintSha256)`
- Host profile: **$($profile.name)**
- Resource class: **$($profile.resourceClass)**
- Kernel: $($records.uname.stdout)
- Virtualization: $($records.virtualization.stdout)
- Clocksource: $($records.clocksource.stdout -replace "`n", '; ')
- CPU governor: $($records.governor.stdout -replace "`n", '; ')
- Java: $($records.java.stderr -replace "`n", '; ')
- Maven: $($records.maven.stdout -replace "`n", '; ')
- PowerShell: $($records.powershell.stdout)

Complete command responses are retained in the adjacent command ledger.
"@
Write-JmoaText -Value $markdown -Path (Join-Path $OutputDirectory 'linux-host-fingerprint.md')
Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'COMPLETE' -Stage 'linux-host-fingerprint' -Variant 'SHARED' | Out-Null
$fingerprint | ConvertTo-Json -Depth 12

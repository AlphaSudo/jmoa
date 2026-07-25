param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('STANDARD_FIXED_8G', 'HYPERV_DEBIAN_FIXED_2G')][string]$HostProfile = 'STANDARD_FIXED_8G',
    [int]$Samples = 10,
    [int]$SampleIntervalSeconds = 5
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-linux-host-profiles.ps1')

$profile = Get-CampaignLinuxHostProfile -Name $HostProfile
New-JmoaDirectory -Path $OutputDirectory
$ledger = Join-Path $OutputDirectory 'command-ledger'
Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'linux-host-idle-calibration' -Variant 'HOST_IDLE' `
    -Description "$Samples host-only samples at $SampleIntervalSeconds-second intervals. No campaign container is allowed." | Out-Null

function Invoke-IdleCheck {
    param([string]$Step, [string]$Command)
    return Invoke-AuditedExternal -Executable '/bin/bash' -Arguments @('-lc', $Command) -LedgerDirectory $ledger -Step $Step
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
    if (-not $match.Success) { throw "PSI output is missing $Kind avg10." }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}

$rows = [Collections.Generic.List[object]]::new()
try {
    for ($sample = 1; $sample -le $Samples; $sample++) {
        $timestamp = Invoke-IdleCheck -Step "sample $sample date" -Command 'date --iso-8601=ns'
        $free = Invoke-IdleCheck -Step "sample $sample free" -Command 'free -b'
        $memoryPsi = Invoke-IdleCheck -Step "sample $sample memory PSI" -Command 'cat /proc/pressure/memory'
        $cpuPsi = Invoke-IdleCheck -Step "sample $sample CPU PSI" -Command 'cat /proc/pressure/cpu'
        $ioPsi = Invoke-IdleCheck -Step "sample $sample IO PSI" -Command 'cat /proc/pressure/io'
        $meminfo = Invoke-IdleCheck -Step "sample $sample meminfo" -Command 'cat /proc/meminfo'
        $events = Invoke-IdleCheck -Step "sample $sample memory events" -Command 'cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.events'
        $swapCurrent = Invoke-IdleCheck -Step "sample $sample cgroup swap current" -Command 'cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.swap.current'
        $processes = Invoke-IdleCheck -Step "sample $sample top processes" -Command 'ps -eo pid,comm,%cpu,%mem --sort=-%cpu | head -30'
        $containers = Invoke-IdleCheck -Step "sample $sample running containers" -Command 'podman ps --format json'

        $someAvg10 = Read-Psi -Text $memoryPsi.stdout -Kind 'some'
        $fullAvg10 = Read-Psi -Text $memoryPsi.stdout -Kind 'full'
        $processNoise = @($processes.stdout -split '\r?\n' | Where-Object {
            $_ -match '(?i)\b(apt|apt-get|dpkg|unattended-upgr|packagekitd|updatedb)\b'
        })
        $containerCount = 0
        if (-not [string]::IsNullOrWhiteSpace($containers.stdout)) {
            $parsed = $containers.stdout | ConvertFrom-Json
            if ($null -ne $parsed) { $containerCount = @($parsed).Count }
        }
        $rows.Add([ordered]@{
            sample = $sample
            timestamp = $timestamp.stdout.Trim()
            availableMemoryBytes = Read-MemInfoBytes -Text $meminfo.stdout -Name 'MemAvailable'
            swapTotalBytes = Read-MemInfoBytes -Text $meminfo.stdout -Name 'SwapTotal'
            swapFreeBytes = Read-MemInfoBytes -Text $meminfo.stdout -Name 'SwapFree'
            cgroupSwapCurrentBytes = [long]$swapCurrent.stdout.Trim()
            memoryPressureSomeAvg10 = $someAvg10
            memoryPressureFullAvg10 = $fullAvg10
            oomEvents = Get-CampaignMemoryEventValue -Text $events.stdout -Name 'oom'
            oomKillEvents = Get-CampaignMemoryEventValue -Text $events.stdout -Name 'oom_kill'
            runningContainerCount = $containerCount
            packageActivity = $processNoise
            free = $free.stdout.Trim()
            cpuPressure = $cpuPsi.stdout.Trim()
            ioPressure = $ioPsi.stdout.Trim()
        }) | Out-Null
        if ($sample -lt $Samples -and $SampleIntervalSeconds -gt 0) { Start-Sleep -Seconds $SampleIntervalSeconds }
    }

    $reasons = [Collections.Generic.List[string]]::new()
    $minimumAvailable = [long](@($rows | Measure-Object -Property availableMemoryBytes -Minimum).Minimum)
    if ($minimumAvailable -lt $profile.minPreflightAvailableMemoryBytes) {
        $reasons.Add("minimum MemAvailable $minimumAvailable is below $($profile.minPreflightAvailableMemoryBytes) bytes") | Out-Null
    }
    if (@($rows | Where-Object { $_.swapTotalBytes -ne 0 -or $_.cgroupSwapCurrentBytes -ne 0 }).Count -ne 0) {
        $reasons.Add('swap was configured or used during idle calibration') | Out-Null
    }
    if (@($rows | Where-Object { $_.memoryPressureSomeAvg10 -gt 0 -or $_.memoryPressureFullAvg10 -gt 0 }).Count -ne 0) {
        $reasons.Add('memory PSI some/full avg10 was nonzero during idle calibration') | Out-Null
    }
    if (@($rows | Where-Object { $_.oomEvents -ne 0 -or $_.oomKillEvents -ne 0 }).Count -ne 0) {
        $reasons.Add('OOM counters were nonzero during idle calibration') | Out-Null
    }
    if (@($rows | Where-Object { $_.runningContainerCount -ne 0 }).Count -ne 0) {
        $reasons.Add('one or more containers ran during idle calibration') | Out-Null
    }
    if (@($rows | Where-Object { @($_.packageActivity).Count -ne 0 }).Count -ne 0) {
        $reasons.Add('package update or indexing activity was observed') | Out-Null
    }
    $report = [ordered]@{
        schemaVersion = 'jmoa-linux-idle-calibration-v1'
        hostProfile = $profile.name
        resourceClass = $profile.resourceClass
        samples = $rows.ToArray()
        minimumAvailableMemoryBytes = $minimumAvailable
        passed = ($reasons.Count -eq 0)
        terminalVerdict = if ($reasons.Count -eq 0) { 'IDLE_CALIBRATION_PASSED' } else { 'STOPPED_CONSTRAINED_HOST_NOT_IDLE' }
        reasons = $reasons.ToArray()
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'linux-idle-calibration.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'linux-idle-calibration.md') -Value @"
# Linux Host-Idle Calibration

- Host profile: $($profile.name)
- Samples: $Samples
- Sample interval: $SampleIntervalSeconds seconds
- Minimum MemAvailable: $minimumAvailable bytes
- Passed: **$($report.passed)**
- Terminal verdict: $($report.terminalVerdict)

## Reasons
$(if ($reasons.Count -eq 0) { '- none' } else { ($reasons | ForEach-Object { "- $_" }) -join "`n" })
"@
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status $(if ($report.passed) { 'COMPLETE' } else { 'FAILED' }) `
        -Stage 'linux-host-idle-calibration' -Variant 'HOST_IDLE' | Out-Null
    if (-not $report.passed) { exit 2 }
} catch {
    if (-not (Test-Path -LiteralPath (Join-Path $ledger 'child-ledger-summary.json'))) {
        Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status 'FAILED' -Stage 'linux-host-idle-calibration' -Variant 'HOST_IDLE' | Out-Null
    }
    throw
}

param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$ContainerCli = 'podman',
    [int[]]$RequiredFreePorts = @(8081, 8761, 8888),
    [long]$MinPodmanAvailableMemoryBytes = 1073741824,
    [long]$MaxPodmanSwapUsedBytes = 0,
    [double]$MaxPodmanMemoryPressureSomeAvg10 = 1.0,
    [double]$MaxPodmanMemoryPressureFullAvg10 = 0.1,
    [string]$LedgerDirectory = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

New-JmoaDirectory -Path $OutputDirectory
if ([string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    $LedgerDirectory = Join-Path $OutputDirectory 'command-ledger'
}
Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage 'host-podman-preflight' -Variant 'SHARED' `
    -Description 'Audited Windows, WSL, Podman, port, container, memory, swap, and PSI preflight immediately before the performance campaign.' | Out-Null

$pwsh = (Get-Process -Id $PID).Path
function Invoke-PreflightProcess {
    param([string]$Step, [string]$Executable, [string[]]$Arguments, [switch]$AllowFailure)
    return Invoke-AuditedExternal -Executable $Executable -Arguments $Arguments -LedgerDirectory $LedgerDirectory `
        -Step $Step -AllowFailure:$AllowFailure
}
function Invoke-PreflightPowerShell {
    param([string]$Step, [string]$Command, [switch]$AllowFailure)
    return Invoke-PreflightProcess -Step $Step -Executable $pwsh -Arguments @('-NoProfile', '-Command', $Command) -AllowFailure:$AllowFailure
}
function Read-MemInfoBytes {
    param([string]$Text, [string]$Name)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { throw "Podman machine /proc/meminfo is missing $Name." }
    return [long]$match.Groups[1].Value * 1024
}
function Read-Psi {
    param([string]$Text, [ValidateSet('some', 'full')][string]$Kind, [string]$Metric)
    $line = @($Text -split '\r?\n' | Where-Object { $_ -match "^$Kind\s" } | Select-Object -First 1)
    if ($line.Count -ne 1) { throw "Podman machine PSI output is missing '$Kind'." }
    $match = [regex]::Match([string]$line[0], "(?:^|\s)$([regex]::Escape($Metric))=([0-9.]+)(?:\s|$)")
    if (-not $match.Success) { throw "Podman machine PSI '$Kind' is missing $Metric." }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}

$results = [ordered]@{}
try {
    $results.computerInfo = Invoke-PreflightPowerShell -Step 'Windows Get-ComputerInfo' `
        -Command 'Get-ComputerInfo | ConvertTo-Json -Depth 5'
    $results.processor = Invoke-PreflightPowerShell -Step 'Windows processor inventory' `
        -Command 'Get-CimInstance Win32_Processor | ConvertTo-Json -Depth 5'
    $results.computerSystem = Invoke-PreflightPowerShell -Step 'Windows system and physical memory inventory' `
        -Command 'Get-CimInstance Win32_ComputerSystem | ConvertTo-Json -Depth 5'
    $results.cpuCounter = Invoke-PreflightPowerShell -Step 'Windows total CPU counter' `
        -Command "(Get-Counter '\Processor(_Total)\% Processor Time').CounterSamples | Select-Object Path,CookedValue,Timestamp | ConvertTo-Json"
    $results.topProcesses = Invoke-PreflightPowerShell -Step 'Windows top CPU processes' `
        -Command 'Get-Process | Sort-Object CPU -Descending | Select-Object -First 20 Id,ProcessName,CPU,WorkingSet64 | ConvertTo-Json'
    $results.availableMemory = Invoke-PreflightPowerShell -Step 'Windows available memory counter' `
        -Command "(Get-Counter '\Memory\Available Bytes').CounterSamples | Select-Object Path,CookedValue,Timestamp | ConvertTo-Json"
    $results.powerPlan = Invoke-PreflightProcess -Step 'Windows active power plan' -Executable 'powercfg.exe' -Arguments @('/GETACTIVESCHEME')
    $results.battery = Invoke-PreflightPowerShell -Step 'Windows battery and AC state' `
        -Command 'Get-CimInstance Win32_Battery | ConvertTo-Json -Depth 4' -AllowFailure

    $results.wslVersion = Invoke-PreflightProcess -Step 'WSL version' -Executable 'wsl.exe' -Arguments @('--version')
    $results.wslStatus = Invoke-PreflightProcess -Step 'WSL status' -Executable 'wsl.exe' -Arguments @('--status')
    $results.wslList = Invoke-PreflightProcess -Step 'WSL distributions' -Executable 'wsl.exe' -Arguments @('--list', '--verbose')
    $results.podmanVersion = Invoke-PreflightProcess -Step 'Podman version' -Executable $ContainerCli -Arguments @('version')
    $results.podmanMachine = Invoke-PreflightProcess -Step 'Podman machine inspect' -Executable $ContainerCli -Arguments @('machine', 'inspect')
    $results.podmanInfo = Invoke-PreflightProcess -Step 'Podman info' -Executable $ContainerCli -Arguments @('info')
    $results.runningContainers = Invoke-PreflightProcess -Step 'running Podman containers' -Executable $ContainerCli -Arguments @('ps', '--format', 'json')

    $portCsv = ($RequiredFreePorts -join ',')
    $results.ports = Invoke-PreflightPowerShell -Step 'required host ports' -Command @"
`$ports = @($portCsv)
@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { `$_.LocalPort -in `$ports } |
  Select-Object LocalAddress,LocalPort,OwningProcess) | ConvertTo-Json -Compress
"@

    $machineCommand = "sh -lc 'echo ---UNAME---; uname -a; echo ---NPROC---; nproc; echo ---FREE---; free -b; echo ---MEMINFO---; cat /proc/meminfo; echo ---SWAPS---; cat /proc/swaps; echo ---MEMORY-PSI---; cat /proc/pressure/memory; echo ---CPU-PSI---; cat /proc/pressure/cpu; echo ---CONTROLLERS---; cat /sys/fs/cgroup/cgroup.controllers'"
    $results.machineRuntime = Invoke-PreflightProcess -Step 'Podman machine runtime fingerprint and pressure' `
        -Executable $ContainerCli -Arguments @('machine', 'ssh', $machineCommand)

    $machineText = $results.machineRuntime.stdout
    $memInfoMatch = [regex]::Match($machineText, '(?s)---MEMINFO---\s*(.*?)\s*---SWAPS---')
    $memoryPsiMatch = [regex]::Match($machineText, '(?s)---MEMORY-PSI---\s*(.*?)\s*---CPU-PSI---')
    if (-not $memInfoMatch.Success -or -not $memoryPsiMatch.Success) {
        throw 'Could not parse Podman machine preflight sections.'
    }
    $memInfo = $memInfoMatch.Groups[1].Value
    $memoryPsi = $memoryPsiMatch.Groups[1].Value
    $availableBytes = Read-MemInfoBytes -Text $memInfo -Name 'MemAvailable'
    $swapTotalBytes = Read-MemInfoBytes -Text $memInfo -Name 'SwapTotal'
    $swapFreeBytes = Read-MemInfoBytes -Text $memInfo -Name 'SwapFree'
    $swapUsedBytes = $swapTotalBytes - $swapFreeBytes
    $someAvg10 = Read-Psi -Text $memoryPsi -Kind 'some' -Metric 'avg10'
    $fullAvg10 = Read-Psi -Text $memoryPsi -Kind 'full' -Metric 'avg10'

    $containerObjects = @()
    if (-not [string]::IsNullOrWhiteSpace($results.runningContainers.stdout)) {
        $parsedContainers = $results.runningContainers.stdout | ConvertFrom-Json
        $containerObjects = @($parsedContainers)
    }
    $portObjects = @()
    if (-not [string]::IsNullOrWhiteSpace($results.ports.stdout)) {
        $parsedPorts = $results.ports.stdout | ConvertFrom-Json
        $portObjects = @($parsedPorts)
    }
    $reasons = New-Object System.Collections.Generic.List[string]
    if ($containerObjects.Count -ne 0) { $reasons.Add("$($containerObjects.Count) unrelated/running Podman container(s) found") | Out-Null }
    if ($portObjects.Count -ne 0) { $reasons.Add("required port(s) already listening: $(@($portObjects | ForEach-Object LocalPort) -join ',')") | Out-Null }
    if ($availableBytes -lt $MinPodmanAvailableMemoryBytes) { $reasons.Add("Podman MemAvailable $availableBytes is below $MinPodmanAvailableMemoryBytes bytes") | Out-Null }
    if ($swapUsedBytes -gt $MaxPodmanSwapUsedBytes) { $reasons.Add("Podman swap used $swapUsedBytes exceeds $MaxPodmanSwapUsedBytes bytes") | Out-Null }
    if ($someAvg10 -gt $MaxPodmanMemoryPressureSomeAvg10) { $reasons.Add("memory PSI some avg10 $someAvg10 exceeds $MaxPodmanMemoryPressureSomeAvg10") | Out-Null }
    if ($fullAvg10 -gt $MaxPodmanMemoryPressureFullAvg10) { $reasons.Add("memory PSI full avg10 $fullAvg10 exceeds $MaxPodmanMemoryPressureFullAvg10") | Out-Null }

    $report = [ordered]@{
        schemaVersion = 'jmoa-campaign-host-preflight-v1'
        capturedAt = [DateTime]::UtcNow.ToString('o')
        passed = ($reasons.Count -eq 0)
        reasons = $reasons.ToArray()
        requiredFreePorts = $RequiredFreePorts
        runningContainerCount = $containerObjects.Count
        occupiedRequiredPorts = @($portObjects)
        podmanMachine = [ordered]@{
            availableMemoryBytes = $availableBytes
            swapTotalBytes = $swapTotalBytes
            swapFreeBytes = $swapFreeBytes
            swapUsedBytes = $swapUsedBytes
            memoryPressureSomeAvg10 = $someAvg10
            memoryPressureFullAvg10 = $fullAvg10
        }
        thresholds = [ordered]@{
            minPodmanAvailableMemoryBytes = $MinPodmanAvailableMemoryBytes
            maxPodmanSwapUsedBytes = $MaxPodmanSwapUsedBytes
            maxPodmanMemoryPressureSomeAvg10 = $MaxPodmanMemoryPressureSomeAvg10
            maxPodmanMemoryPressureFullAvg10 = $MaxPodmanMemoryPressureFullAvg10
        }
        commandLedger = [IO.Path]::GetFullPath($LedgerDirectory)
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'host-podman-preflight.json')
    $markdown = @"
# Campaign Host And Podman Preflight

- Captured UTC: $($report.capturedAt)
- Passed: **$($report.passed)**
- Running Podman containers: $($report.runningContainerCount)
- Occupied required ports: $(@($portObjects | ForEach-Object LocalPort) -join ', ')
- Podman VM available memory: $availableBytes bytes
- Podman VM swap used: $swapUsedBytes bytes
- Memory PSI some avg10: $someAvg10
- Memory PSI full avg10: $fullAvg10

## Reasons
$(if ($reasons.Count -eq 0) { '- none' } else { ($reasons | ForEach-Object { "- $_" }) -join "`n" })

All Windows, WSL, Podman, process, power, port, and guest-kernel commands and their complete outputs
are retained in the hashed child command ledger.
"@
    Write-JmoaText -Value $markdown -Path (Join-Path $OutputDirectory 'host-podman-preflight.md')
    Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status $(if ($report.passed) { 'COMPLETE' } else { 'FAILED' }) `
        -Stage 'host-podman-preflight' -Variant 'SHARED' | Out-Null
    if (-not $report.passed) { exit 2 }
} catch {
    $failure = [ordered]@{
        schemaVersion = 'jmoa-campaign-host-preflight-v1'
        capturedAt = [DateTime]::UtcNow.ToString('o')
        passed = $false
        reasons = @($_.Exception.Message)
        commandLedger = [IO.Path]::GetFullPath($LedgerDirectory)
    }
    Write-JmoaJson -Value $failure -Path (Join-Path $OutputDirectory 'host-podman-preflight.json')
    if (-not (Test-Path -LiteralPath (Join-Path $LedgerDirectory 'child-ledger-summary.json') -PathType Leaf)) {
        Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'FAILED' -Stage 'host-podman-preflight' -Variant 'SHARED' | Out-Null
    }
    throw
}

param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [string]$ContainerCli = '/usr/bin/podman',
    [int]$Samples = 3,
    [int]$SettleSeconds = 20,
    [long]$MaxAggregateMemoryCurrentDriftBytes = 2097152
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

New-JmoaDirectory -Path $OutputDirectory
$ledger = Join-Path $OutputDirectory 'command-ledger'
Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'linux-support-stack-calibration' -Variant 'SUPPORT_ONLY' `
    -Description 'Three fresh support-stack-only samples. No target JVM is launched.' | Out-Null

function Invoke-Cli {
    param([string]$Step, [string[]]$Arguments, [switch]$AllowFailure)
    Invoke-AuditedExternal -Executable $ContainerCli -Arguments $Arguments -LedgerDirectory $ledger -Step $Step -AllowFailure:$AllowFailure
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
function Get-MemoryCurrent {
    param([string]$Name)
    $result = Invoke-Cli -Step "capture $Name memory.current" -Arguments @('exec', $Name, 'sh', '-lc', 'cat /sys/fs/cgroup/memory.current')
    return [long]$result.stdout.Trim()
}
function Read-MemAvailable {
    $result = Invoke-AuditedExternal -Executable '/bin/bash' -Arguments @('-lc', "awk '/^MemAvailable:/ {print `$2 * 1024}' /proc/meminfo") -LedgerDirectory $ledger -Step 'capture host MemAvailable'
    return [long]$result.stdout.Trim()
}

$rows = [Collections.Generic.List[object]]::new()
try {
    for ($sample = 1; $sample -le $Samples; $sample++) {
        $prefix = "jmoa-cal-$sample"
        $network = "$prefix-net"
        $config = "$prefix-cfg"
        $discovery = "$prefix-disc"
        foreach ($name in @($discovery, $config)) { Invoke-Cli -Step "pre-clean $name" -Arguments @('rm', '-f', $name) -AllowFailure | Out-Null }
        Invoke-Cli -Step "pre-clean $network" -Arguments @('network', 'rm', $network) -AllowFailure | Out-Null
        Invoke-Cli -Step "create $network" -Arguments @('network', 'create', $network) | Out-Null
        Invoke-Cli -Step "start config sample $sample" -Arguments @(
            'run','-d','--name',$config,'--network',$network,'--network-alias','config-server','-p','8888:8888',
            '-v',"${ConfigRepo}:/app/config-repo:ro",'-e','SPRING_PROFILES_ACTIVE=native','-e','GIT_REPO=/app/config-repo',
            '-e','MANAGEMENT_TRACING_ENABLED=false','-e','MANAGEMENT_METRICS_ENABLED=false',
            '-e','JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:+UseSerialGC -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off',
            $ConfigImage
        ) | Out-Null
        Wait-Health -Role 'config' -Uri 'http://localhost:8888/actuator/health'
        Invoke-Cli -Step "start discovery sample $sample" -Arguments @(
            'run','-d','--name',$discovery,'--network',$network,'--network-alias','discovery-server','-p','8761:8761',
            '-e','SPRING_PROFILES_ACTIVE=docker','-e','CONFIG_SERVER_URI=http://config-server:8888',
            '-e','JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:+UseSerialGC -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off',
            $DiscoveryImage
        ) | Out-Null
        Wait-Health -Role 'discovery' -Uri 'http://localhost:8761/actuator/health'
        Start-Sleep -Seconds $SettleSeconds
        $configMemory = Get-MemoryCurrent -Name $config
        $discoveryMemory = Get-MemoryCurrent -Name $discovery
        $memoryPsi = Invoke-AuditedExternal -Executable '/bin/cat' -Arguments @('/proc/pressure/memory') -LedgerDirectory $ledger -Step "sample $sample memory PSI"
        $swap = Invoke-AuditedExternal -Executable '/bin/cat' -Arguments @('/proc/swaps') -LedgerDirectory $ledger -Step "sample $sample swaps"
        $rows.Add([ordered]@{
            sample = $sample
            configMemoryCurrentBytes = $configMemory
            discoveryMemoryCurrentBytes = $discoveryMemory
            aggregateMemoryCurrentBytes = $configMemory + $discoveryMemory
            hostAvailableMemoryBytes = Read-MemAvailable
            memoryPressure = $memoryPsi.stdout.Trim()
            swaps = $swap.stdout.Trim()
        })
        foreach ($name in @($discovery, $config)) { Invoke-Cli -Step "stop $name" -Arguments @('rm', '-f', $name) -AllowFailure | Out-Null }
        Invoke-Cli -Step "remove $network" -Arguments @('network', 'rm', $network) -AllowFailure | Out-Null
    }
    $values = @($rows | ForEach-Object aggregateMemoryCurrentBytes)
    $drift = ([long]($values | Measure-Object -Maximum).Maximum) - ([long]($values | Measure-Object -Minimum).Minimum)
    $swapActive = @($rows | Where-Object { ($_.swaps -split '\r?\n').Count -gt 1 }).Count -gt 0
    $psiNonZero = @($rows | Where-Object { $_.memoryPressure -match '(some|full) avg10=(?!0\.00)' }).Count -gt 0
    $report = [ordered]@{
        schemaVersion = 'jmoa-linux-support-calibration-v1'
        samples = $rows.ToArray()
        aggregateMemoryCurrentDriftBytes = $drift
        maxAggregateMemoryCurrentDriftBytes = $MaxAggregateMemoryCurrentDriftBytes
        swapActive = $swapActive
        sustainedMemoryPressure = $psiNonZero
        passed = ($drift -le $MaxAggregateMemoryCurrentDriftBytes -and -not $swapActive -and -not $psiNonZero)
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'linux-host-calibration.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'linux-host-calibration.md') -Value @"
# Linux Support-Stack Calibration

- Samples: $Samples
- Aggregate memory.current drift: $drift bytes
- Frozen limit: $MaxAggregateMemoryCurrentDriftBytes bytes
- Swap active: $swapActive
- Sustained memory PSI: $psiNonZero
- Passed: **$($report.passed)**
"@
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status $(if ($report.passed) {'COMPLETE'} else {'FAILED'}) -Stage 'linux-support-stack-calibration' -Variant 'SUPPORT_ONLY' | Out-Null
    if (-not $report.passed) { exit 2 }
} finally {
    $containers = Invoke-Cli -Step 'final calibration container inventory' -Arguments @('ps','-aq','--filter','name=jmoa-cal-') -AllowFailure
    foreach ($id in @($containers.stdout -split '\r?\n' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
        Invoke-Cli -Step "final remove calibration container $id" -Arguments @('rm','-f',$id) -AllowFailure | Out-Null
    }
    $networks = Invoke-Cli -Step 'final calibration network inventory' -Arguments @('network','ls','--format','{{.Name}}') -AllowFailure
    foreach ($name in @($networks.stdout -split '\r?\n' | Where-Object { $_ -like 'jmoa-cal-*-net' })) {
        Invoke-Cli -Step "final remove calibration network $name" -Arguments @('network','rm',$name) -AllowFailure | Out-Null
    }
}

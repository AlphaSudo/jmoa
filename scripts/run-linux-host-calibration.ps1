param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [string]$ContainerCli = '/usr/bin/podman',
    [ValidateSet('STANDARD_FIXED_8G', 'HYPERV_DEBIAN_FIXED_2G')][string]$HostProfile = 'STANDARD_FIXED_8G',
    [string]$CalibrationId = 'calibration-1',
    [int]$Samples = 36,
    [int]$SampleIntervalSeconds = 5,
    [int]$FinalWindowSamples = 12,
    [long]$MaxFinalWindowPssRangeKb = 2048,
    [long]$MaxFinalWindowPrivateDirtyRangeKb = 2048,
    [long]$MaxFinalWindowAnonRangeBytes = 2097152,
    [long]$MaxPositiveSlopeBytesPerSecond = 65536
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-linux-host-profiles.ps1')

if ($Samples -lt 12) { throw 'Support calibration requires at least 12 samples.' }
if ($FinalWindowSamples -lt 3 -or $FinalWindowSamples -gt $Samples) {
    throw 'FinalWindowSamples must be between 3 and Samples.'
}
if ($SampleIntervalSeconds -lt 1) { throw 'SampleIntervalSeconds must be positive.' }

$profile = Get-CampaignLinuxHostProfile -Name $HostProfile
New-JmoaDirectory -Path $OutputDirectory
$ledger = Join-Path $OutputDirectory 'command-ledger'
Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'linux-support-stack-calibration-v2' -Variant $CalibrationId `
    -Description "$Samples lightweight samples at $SampleIntervalSeconds-second intervals; only the final $FinalWindowSamples samples are stability-gated." | Out-Null

function Invoke-Cli {
    param([string]$Step, [string[]]$Arguments, [switch]$AllowFailure)
    return Invoke-AuditedExternal -Executable $ContainerCli -Arguments $Arguments -LedgerDirectory $ledger -Step $Step -AllowFailure:$AllowFailure
}
function Invoke-Host {
    param([string]$Step, [string]$Command, [switch]$AllowFailure)
    return Invoke-AuditedExternal -Executable '/bin/bash' -Arguments @('-lc', $Command) -LedgerDirectory $ledger -Step $Step -AllowFailure:$AllowFailure
}
function Wait-Health {
    param([string]$Role, [string]$Uri, [int]$TimeoutSeconds = 240)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $probe = 0
    while ([DateTime]::UtcNow -lt $deadline) {
        $probe++
        $response = Invoke-AuditedHttp -Method GET -Uri $Uri -LedgerDirectory $ledger -Step "[STARTUP] $Role health probe $probe" -TimeoutSeconds 10
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
    if (-not $match.Success) { throw "PSI output is missing $Kind avg10." }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}
function Read-KeyValue {
    param([string]$Text, [string]$Name)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name))\s+(\d+)\s*$")
    if (-not $match.Success) { return 0L }
    return [long]$match.Groups[1].Value
}
function Read-SmapsKb {
    param([string]$Text, [string]$Name)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB\s*$")
    if (-not $match.Success) { throw "smaps_rollup is missing $Name." }
    return [long]$match.Groups[1].Value
}
function Read-MarkedSection {
    param([string]$Text, [string]$Name)
    $match = [regex]::Match($Text, "(?ms)^---$([regex]::Escape($Name))---\r?\n(.*?)(?=^---[A-Z_]+---|\z)")
    if (-not $match.Success) { throw "Capture output is missing section $Name." }
    return $match.Groups[1].Value.Trim()
}
function Get-Range {
    param([object[]]$Values)
    if (@($Values).Count -eq 0) { return 0L }
    return [long](($Values | Measure-Object -Maximum).Maximum) - [long](($Values | Measure-Object -Minimum).Minimum)
}
function Get-LinearSlope {
    param([object[]]$Rows, [string]$Property)
    if (@($Rows).Count -lt 2) { return 0.0 }
    $xMean = [double](($Rows | Measure-Object -Property secondsSinceHealthy -Average).Average)
    $yValues = @($Rows | ForEach-Object { [double]($_.$Property) })
    $yMean = [double](($yValues | Measure-Object -Average).Average)
    $numerator = 0.0
    $denominator = 0.0
    foreach ($row in $Rows) {
        $dx = [double]$row.secondsSinceHealthy - $xMean
        $dy = [double]$row.$Property - $yMean
        $numerator += $dx * $dy
        $denominator += $dx * $dx
    }
    if ($denominator -eq 0.0) { return 0.0 }
    return $numerator / $denominator
}
function Get-ContainerIdentity {
    param([string]$Name)
    $inspect = Invoke-Cli -Step "[CGROUP_SCOPE] inspect $Name" -Arguments @('inspect', $Name)
    $inspectJson = $inspect.stdout | ConvertFrom-Json
    $processIdResult = Invoke-Cli -Step "[CGROUP_SCOPE] capture $Name host PID" -Arguments @('inspect', '--format', '{{.State.Pid}}', $Name)
    $processId = [int]$processIdResult.stdout.Trim()
    $cgroupResult = Invoke-Host -Step "[CGROUP_SCOPE] capture $Name /proc cgroup" -Command "cat /proc/$processId/cgroup"
    $match = [regex]::Match($cgroupResult.stdout, '(?m)^0::(.+)$')
    if (-not $match.Success) { throw "Could not resolve unified cgroup path for $Name." }
    $relative = $match.Groups[1].Value.Trim()
    $hostPath = '/sys/fs/cgroup/' + $relative.TrimStart('/')
    $required = @('memory.current', 'memory.stat', 'memory.events', 'memory.swap.current', 'memory.pressure')
    $probeCommand = ($required | ForEach-Object { "test -f '$hostPath/$_'" }) -join ' && '
    $probe = Invoke-Host -Step "[CGROUP_SCOPE] verify $Name exact memory controller files" -Command $probeCommand -AllowFailure
    return [pscustomobject][ordered]@{
        name = $Name
        containerId = [string]@($inspectJson)[0].Id
        processId = $processId
        procCgroupRaw = $cgroupResult.stdout.Trim()
        relativeCgroupPath = $relative
        hostCgroupPath = $hostPath
        exactControllerFilesPresent = ($probe.exitCode -eq 0)
        looksLikeIndividualLibpodScope = ($relative -match '(?i)libpod[-_/].+')
    }
}
function Get-LightweightContainerSample {
    param([Parameter(Mandatory)]$Identity, [int]$Sample)
    $path = [string]$Identity.hostCgroupPath
    $processId = [int]$Identity.processId
    $command = @"
printf '%s\n' '---MEMORY_CURRENT---'; cat '$path/memory.current'
printf '%s\n' '---MEMORY_STAT---'; cat '$path/memory.stat'
printf '%s\n' '---MEMORY_EVENTS---'; cat '$path/memory.events'
printf '%s\n' '---MEMORY_SWAP_CURRENT---'; cat '$path/memory.swap.current'
printf '%s\n' '---MEMORY_PRESSURE---'; cat '$path/memory.pressure'
printf '%s\n' '---SMAPS_ROLLUP---'; cat '/proc/$processId/smaps_rollup'
"@
    $capture = Invoke-Host -Step "[STABILITY_SAMPLE] $Sample $($Identity.name) exact cgroup and smaps_rollup" -Command $command
    $stat = Read-MarkedSection -Text $capture.stdout -Name 'MEMORY_STAT'
    $events = Read-MarkedSection -Text $capture.stdout -Name 'MEMORY_EVENTS'
    $pressure = Read-MarkedSection -Text $capture.stdout -Name 'MEMORY_PRESSURE'
    $smaps = Read-MarkedSection -Text $capture.stdout -Name 'SMAPS_ROLLUP'
    return [pscustomobject][ordered]@{
        memoryCurrentBytes = [long](Read-MarkedSection -Text $capture.stdout -Name 'MEMORY_CURRENT')
        memorySwapCurrentBytes = [long](Read-MarkedSection -Text $capture.stdout -Name 'MEMORY_SWAP_CURRENT')
        anonBytes = Read-KeyValue -Text $stat -Name 'anon'
        fileBytes = Read-KeyValue -Text $stat -Name 'file'
        fileMappedBytes = Read-KeyValue -Text $stat -Name 'file_mapped'
        fileDirtyBytes = Read-KeyValue -Text $stat -Name 'file_dirty'
        fileWritebackBytes = Read-KeyValue -Text $stat -Name 'file_writeback'
        shmemBytes = Read-KeyValue -Text $stat -Name 'shmem'
        slabBytes = Read-KeyValue -Text $stat -Name 'slab'
        kernelStackBytes = Read-KeyValue -Text $stat -Name 'kernel_stack'
        pageTablesBytes = Read-KeyValue -Text $stat -Name 'pagetables'
        sockBytes = Read-KeyValue -Text $stat -Name 'sock'
        activeFileBytes = Read-KeyValue -Text $stat -Name 'active_file'
        inactiveFileBytes = Read-KeyValue -Text $stat -Name 'inactive_file'
        pageFaults = Read-KeyValue -Text $stat -Name 'pgfault'
        majorPageFaults = Read-KeyValue -Text $stat -Name 'pgmajfault'
        oomEvents = Get-CampaignMemoryEventValue -Text $events -Name 'oom'
        oomKillEvents = Get-CampaignMemoryEventValue -Text $events -Name 'oom_kill'
        memoryPressureSomeAvg10 = Read-Psi -Text $pressure -Kind 'some'
        memoryPressureFullAvg10 = Read-Psi -Text $pressure -Kind 'full'
        pssKb = Read-SmapsKb -Text $smaps -Name 'Pss'
        privateDirtyKb = Read-SmapsKb -Text $smaps -Name 'Private_Dirty'
    }
}
function Write-PostWindowDiagnostics {
    param([Parameter(Mandatory)]$Identity)
    $name = [string]$Identity.name
    $processId = [int]$Identity.processId
    $smaps = Invoke-Host -Step "[POST_WINDOW_DIAGNOSTIC] $name full smaps" -Command "cat /proc/$processId/smaps" -AllowFailure
    Write-JmoaText -Path (Join-Path $OutputDirectory "$name-post-smaps.txt") -Value $smaps.stdout
    $jcmd = if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { Join-Path $env:JAVA_HOME 'bin/jcmd' } else { 'jcmd' }
    foreach ($diagnostic in @('VM.native_memory summary', 'GC.heap_info', 'VM.metaspace', 'VM.classloader_stats', 'Compiler.codecache')) {
        $safe = ($diagnostic -replace '[^A-Za-z0-9]+', '-').Trim('-').ToLowerInvariant()
        $result = Invoke-AuditedExternal -Executable $jcmd -Arguments (@("$processId") + @($diagnostic -split ' ')) `
            -LedgerDirectory $ledger -Step "[POST_WINDOW_DIAGNOSTIC] $name $diagnostic" -AllowFailure
        Write-JmoaText -Path (Join-Path $OutputDirectory "$name-$safe.txt") -Value $result.output
    }
    $logs = Invoke-Cli -Step "[POST_WINDOW_DIAGNOSTIC] $name container logs" -Arguments @('logs', $name) -AllowFailure
    Write-JmoaText -Path (Join-Path $OutputDirectory "$name-container.log") -Value $logs.output
}

$safeId = ($CalibrationId -replace '[^A-Za-z0-9_.-]', '-').ToLowerInvariant()
$network = "jmoa-cal-support-$safeId-net"
$config = "jmoa-cal-support-$safeId-cfg"
$discovery = "jmoa-cal-support-$safeId-disc"
$rows = [Collections.Generic.List[object]]::new()
$finalStatus = 'FAILED'
try {
    foreach ($name in @($discovery, $config)) { Invoke-Cli -Step "[SETUP] pre-clean $name" -Arguments @('rm', '-f', $name) -AllowFailure | Out-Null }
    Invoke-Cli -Step "[SETUP] pre-clean $network" -Arguments @('network', 'rm', $network) -AllowFailure | Out-Null
    Invoke-Cli -Step '[SETUP] create support network' -Arguments @('network', 'create', $network) | Out-Null
    Invoke-Cli -Step '[SETUP] start frozen config support service' -Arguments @(
        'run','-d','--name',$config,'--network',$network,'--network-alias','config-server','-p','8888:8888',
        '-v',"${ConfigRepo}:/app/config-repo:ro",'-e','SPRING_PROFILES_ACTIVE=native','-e','GIT_REPO=/app/config-repo',
        '-e','MANAGEMENT_TRACING_ENABLED=false','-e','MANAGEMENT_METRICS_ENABLED=false',
        '-e','JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:+UseSerialGC -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off',
        $ConfigImage
    ) | Out-Null
    Wait-Health -Role 'config' -Uri 'http://localhost:8888/actuator/health'
    Invoke-Cli -Step '[SETUP] start frozen discovery support service' -Arguments @(
        'run','-d','--name',$discovery,'--network',$network,'--network-alias','discovery-server','-p','8761:8761',
        '-e','SPRING_PROFILES_ACTIVE=docker','-e','CONFIG_SERVER_URI=http://config-server:8888',
        '-e','JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:+UseSerialGC -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off',
        $DiscoveryImage
    ) | Out-Null
    Wait-Health -Role 'discovery' -Uri 'http://localhost:8761/actuator/health'

    $healthyAt = [DateTime]::UtcNow
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $configIdentity = Get-ContainerIdentity -Name $config
    $discoveryIdentity = Get-ContainerIdentity -Name $discovery
    $scopeReasons = [Collections.Generic.List[string]]::new()
    foreach ($identity in @($configIdentity, $discoveryIdentity)) {
        if (-not $identity.exactControllerFilesPresent) { $scopeReasons.Add("$($identity.name) exact cgroup controller files are incomplete") | Out-Null }
        if (-not $identity.looksLikeIndividualLibpodScope) { $scopeReasons.Add("$($identity.name) is not in an identifiable individual libpod cgroup") | Out-Null }
    }
    if ($configIdentity.relativeCgroupPath -eq $discoveryIdentity.relativeCgroupPath) {
        $scopeReasons.Add('config and discovery resolved to the same cgroup path') | Out-Null
    }
    $scopeAudit = [ordered]@{
        schemaVersion = 'jmoa-support-cgroup-scope-audit-v1'
        calibrationId = $CalibrationId
        determination = if ($scopeReasons.Count -eq 0) { 'INDIVIDUAL_CONTAINER_CGROUPS_CONFIRMED' } else { 'PARENT_USER_SLICE_CONTAMINATED' }
        passed = ($scopeReasons.Count -eq 0)
        containers = @($configIdentity, $discoveryIdentity)
        priorCalibrationAccounting = 'SUM_OF_CONTAINER_NAMESPACE_MEMORY_CURRENT'
        reasons = $scopeReasons.ToArray()
    }
    Write-JmoaJson -Value $scopeAudit -Path (Join-Path $OutputDirectory 'support-cgroup-scope-audit.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'support-cgroup-scope-audit.md') -Value @"
# Support cgroup Scope Audit

- Calibration: $CalibrationId
- Determination: **$($scopeAudit.determination)**
- Config cgroup: ``$($configIdentity.relativeCgroupPath)``
- Discovery cgroup: ``$($discoveryIdentity.relativeCgroupPath)``

The stability sampler reads each exact host cgroup independently. Parent
``user.slice`` accounting is used only for host OOM history, never as the
support-stack memory delta.
"@

    for ($sample = 1; $sample -le $Samples; $sample++) {
        Start-Sleep -Seconds $SampleIntervalSeconds
        $configHealth = Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8888/actuator/health' -LedgerDirectory $ledger -Step "[STABILITY_SAMPLE] $sample config health"
        $discoveryHealth = Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8761/actuator/health' -LedgerDirectory $ledger -Step "[STABILITY_SAMPLE] $sample discovery health"
        $hostCapture = Invoke-Host -Step "[STABILITY_SAMPLE] $sample host capacity and pressure" -Command @'
cat /proc/meminfo
printf '%s\n' '---HOST_PRESSURE---'
cat /proc/pressure/memory
printf '%s\n' '---USER_EVENTS---'
cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.events
'@
        $configSample = Get-LightweightContainerSample -Identity $configIdentity -Sample $sample
        $discoverySample = Get-LightweightContainerSample -Identity $discoveryIdentity -Sample $sample
        $configRestart = Invoke-Cli -Step "[STABILITY_SAMPLE] $sample config restart count" -Arguments @('inspect', '--format', '{{.RestartCount}}', $config)
        $discoveryRestart = Invoke-Cli -Step "[STABILITY_SAMPLE] $sample discovery restart count" -Arguments @('inspect', '--format', '{{.RestartCount}}', $discovery)
        $hostPressure = Read-MarkedSection -Text $hostCapture.stdout -Name 'HOST_PRESSURE'
        $userEvents = Read-MarkedSection -Text $hostCapture.stdout -Name 'USER_EVENTS'
        $rows.Add([pscustomobject][ordered]@{
            sample = $sample
            timestampUtc = [DateTime]::UtcNow.ToString('o')
            secondsSinceHealthy = [math]::Round($timer.Elapsed.TotalSeconds, 3)
            configHealthStatus = $configHealth.status
            discoveryHealthStatus = $discoveryHealth.status
            configRestartCount = [int]$configRestart.stdout.Trim()
            discoveryRestartCount = [int]$discoveryRestart.stdout.Trim()
            aggregatePssKb = $configSample.pssKb + $discoverySample.pssKb
            aggregatePrivateDirtyKb = $configSample.privateDirtyKb + $discoverySample.privateDirtyKb
            aggregateMemoryCurrentBytes = $configSample.memoryCurrentBytes + $discoverySample.memoryCurrentBytes
            aggregateAnonBytes = $configSample.anonBytes + $discoverySample.anonBytes
            aggregateFileBytes = $configSample.fileBytes + $discoverySample.fileBytes
            aggregateFileMappedBytes = $configSample.fileMappedBytes + $discoverySample.fileMappedBytes
            aggregateFileDirtyBytes = $configSample.fileDirtyBytes + $discoverySample.fileDirtyBytes
            aggregateFileWritebackBytes = $configSample.fileWritebackBytes + $discoverySample.fileWritebackBytes
            aggregateShmemBytes = $configSample.shmemBytes + $discoverySample.shmemBytes
            aggregateSlabBytes = $configSample.slabBytes + $discoverySample.slabBytes
            aggregateKernelStackBytes = $configSample.kernelStackBytes + $discoverySample.kernelStackBytes
            aggregatePageTablesBytes = $configSample.pageTablesBytes + $discoverySample.pageTablesBytes
            aggregateSockBytes = $configSample.sockBytes + $discoverySample.sockBytes
            aggregateActiveFileBytes = $configSample.activeFileBytes + $discoverySample.activeFileBytes
            aggregateInactiveFileBytes = $configSample.inactiveFileBytes + $discoverySample.inactiveFileBytes
            aggregatePageFaults = $configSample.pageFaults + $discoverySample.pageFaults
            aggregateMajorPageFaults = $configSample.majorPageFaults + $discoverySample.majorPageFaults
            aggregateCgroupSwapCurrentBytes = $configSample.memorySwapCurrentBytes + $discoverySample.memorySwapCurrentBytes
            containerMemoryPressureSomeAvg10 = [math]::Max($configSample.memoryPressureSomeAvg10, $discoverySample.memoryPressureSomeAvg10)
            containerMemoryPressureFullAvg10 = [math]::Max($configSample.memoryPressureFullAvg10, $discoverySample.memoryPressureFullAvg10)
            containerOomEvents = $configSample.oomEvents + $discoverySample.oomEvents
            containerOomKillEvents = $configSample.oomKillEvents + $discoverySample.oomKillEvents
            hostAvailableMemoryBytes = Read-MemInfoBytes -Text $hostCapture.stdout -Name 'MemAvailable'
            swapTotalBytes = Read-MemInfoBytes -Text $hostCapture.stdout -Name 'SwapTotal'
            hostMemoryPressureSomeAvg10 = Read-Psi -Text $hostPressure -Kind 'some'
            hostMemoryPressureFullAvg10 = Read-Psi -Text $hostPressure -Kind 'full'
            userSliceOomEvents = Get-CampaignMemoryEventValue -Text $userEvents -Name 'oom'
            userSliceOomKillEvents = Get-CampaignMemoryEventValue -Text $userEvents -Name 'oom_kill'
        }) | Out-Null
    }
    $timer.Stop()

    $finalWindow = @($rows | Select-Object -Last $FinalWindowSamples)
    $pssRangeKb = Get-Range -Values @($finalWindow.aggregatePssKb)
    $privateDirtyRangeKb = Get-Range -Values @($finalWindow.aggregatePrivateDirtyKb)
    $anonRangeBytes = Get-Range -Values @($finalWindow.aggregateAnonBytes)
    $fileRangeBytes = Get-Range -Values @($finalWindow.aggregateFileBytes)
    $memoryCurrentRangeBytes = Get-Range -Values @($finalWindow.aggregateMemoryCurrentBytes)
    $pssSlopeBytesPerSecond = (Get-LinearSlope -Rows $finalWindow -Property 'aggregatePssKb') * 1024.0
    $anonSlopeBytesPerSecond = Get-LinearSlope -Rows $finalWindow -Property 'aggregateAnonBytes'
    $minimumAvailable = [long](($rows | Measure-Object -Property hostAvailableMemoryBytes -Minimum).Minimum)

    $capacityReasons = [Collections.Generic.List[string]]::new()
    $stabilityReasons = [Collections.Generic.List[string]]::new()
    if ($minimumAvailable -lt $profile.minSupportReadyMemoryBytes) { $capacityReasons.Add("minimum MemAvailable $minimumAvailable is below $($profile.minSupportReadyMemoryBytes) bytes") | Out-Null }
    if (@($rows | Where-Object { $_.configHealthStatus -ne 200 -or $_.discoveryHealthStatus -ne 200 }).Count -ne 0) { $capacityReasons.Add('support health failed') | Out-Null }
    if (@($rows | Where-Object { $_.configRestartCount -ne 0 -or $_.discoveryRestartCount -ne 0 }).Count -ne 0) { $capacityReasons.Add('support container restarted') | Out-Null }
    if (@($rows | Where-Object { $_.swapTotalBytes -ne 0 -or $_.aggregateCgroupSwapCurrentBytes -ne 0 }).Count -ne 0) { $capacityReasons.Add('swap was configured or used') | Out-Null }
    if (@($rows | Where-Object {
        $_.hostMemoryPressureSomeAvg10 -gt $profile.maxMemoryPressureSomeAvg10 -or
        $_.hostMemoryPressureFullAvg10 -gt $profile.maxMemoryPressureFullAvg10 -or
        $_.containerMemoryPressureSomeAvg10 -gt $profile.maxMemoryPressureSomeAvg10 -or
        $_.containerMemoryPressureFullAvg10 -gt $profile.maxMemoryPressureFullAvg10
    }).Count -ne 0) { $capacityReasons.Add('host or exact-container memory PSI avg10 exceeded the frozen profile') | Out-Null }
    if (@($rows | Where-Object {
        $_.containerOomEvents -ne 0 -or $_.containerOomKillEvents -ne 0 -or
        $_.userSliceOomEvents -ne 0 -or $_.userSliceOomKillEvents -ne 0
    }).Count -ne 0) { $capacityReasons.Add('OOM counters were nonzero') | Out-Null }
    if ($pssRangeKb -gt $MaxFinalWindowPssRangeKb) { $stabilityReasons.Add("final-window PSS range $pssRangeKb KB exceeds $MaxFinalWindowPssRangeKb KB") | Out-Null }
    if ($privateDirtyRangeKb -gt $MaxFinalWindowPrivateDirtyRangeKb) { $stabilityReasons.Add("final-window Private_Dirty range $privateDirtyRangeKb KB exceeds $MaxFinalWindowPrivateDirtyRangeKb KB") | Out-Null }
    if ($anonRangeBytes -gt $MaxFinalWindowAnonRangeBytes) { $stabilityReasons.Add("final-window anon range $anonRangeBytes bytes exceeds $MaxFinalWindowAnonRangeBytes bytes") | Out-Null }
    if ($pssSlopeBytesPerSecond -gt $MaxPositiveSlopeBytesPerSecond) { $stabilityReasons.Add("final-window PSS slope $pssSlopeBytesPerSecond B/s exceeds $MaxPositiveSlopeBytesPerSecond B/s") | Out-Null }
    if ($anonSlopeBytesPerSecond -gt $MaxPositiveSlopeBytesPerSecond) { $stabilityReasons.Add("final-window anon slope $anonSlopeBytesPerSecond B/s exceeds $MaxPositiveSlopeBytesPerSecond B/s") | Out-Null }

    $outcome = if (-not $scopeAudit.passed) {
        'SUPPORT_CGROUP_SCOPE_INVALID'
    } elseif ($capacityReasons.Count -ne 0) {
        'HOST_CAPACITY_INSUFFICIENT'
    } elseif ($stabilityReasons.Count -ne 0) {
        'SUPPORT_STACK_PRIVATE_MEMORY_UNSTABLE'
    } else {
        'SUPPORT_STACK_STABLE'
    }
    $fileCacheVariable = $fileRangeBytes -gt 2097152
    $attribution = if (-not $scopeAudit.passed) {
        'PARENT_CGROUP_CONTAMINATION'
    } elseif ($stabilityReasons.Count -ne 0 -and $anonRangeBytes -gt $MaxFinalWindowAnonRangeBytes) {
        'ANON_PRIVATE_GROWTH'
    } elseif ($fileCacheVariable -and $pssRangeKb -le $MaxFinalWindowPssRangeKb -and
        $privateDirtyRangeKb -le $MaxFinalWindowPrivateDirtyRangeKb -and $anonRangeBytes -le $MaxFinalWindowAnonRangeBytes) {
        'FILE_CACHE_CHARGE'
    } elseif ($stabilityReasons.Count -ne 0) {
        'MIXED'
    } else {
        'JVM_WARMUP_CONVERGENCE'
    }

    $driftReport = [ordered]@{
        schemaVersion = 'jmoa-support-memory-drift-attribution-v2'
        calibrationId = $CalibrationId
        verdict = $attribution
        completeStartupWindow = [ordered]@{
            memoryCurrentRangeBytes = Get-Range -Values @($rows.aggregateMemoryCurrentBytes)
            pssRangeKb = Get-Range -Values @($rows.aggregatePssKb)
            privateDirtyRangeKb = Get-Range -Values @($rows.aggregatePrivateDirtyKb)
            anonRangeBytes = Get-Range -Values @($rows.aggregateAnonBytes)
            fileRangeBytes = Get-Range -Values @($rows.aggregateFileBytes)
        }
        finalWindow = [ordered]@{
            sampleCount = $FinalWindowSamples
            pssRangeKb = $pssRangeKb
            privateDirtyRangeKb = $privateDirtyRangeKb
            anonRangeBytes = $anonRangeBytes
            fileRangeBytes = $fileRangeBytes
            memoryCurrentRangeBytes = $memoryCurrentRangeBytes
            pssSlopeBytesPerSecond = [math]::Round($pssSlopeBytesPerSecond, 3)
            anonSlopeBytesPerSecond = [math]::Round($anonSlopeBytesPerSecond, 3)
            pageFaultDelta = [long]$finalWindow[-1].aggregatePageFaults - [long]$finalWindow[0].aggregatePageFaults
            majorPageFaultDelta = [long]$finalWindow[-1].aggregateMajorPageFaults - [long]$finalWindow[0].aggregateMajorPageFaults
        }
        fileCacheVariable = $fileCacheVariable
    }
    Write-JmoaJson -Value $driftReport -Path (Join-Path $OutputDirectory 'support-memory-drift-attribution.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'support-memory-drift-attribution.md') -Value @"
# Support Memory Drift Attribution

- Calibration: $CalibrationId
- Attribution: **$attribution**
- Final-window PSS range: $pssRangeKb KB
- Final-window Private Dirty range: $privateDirtyRangeKb KB
- Final-window anonymous range: $anonRangeBytes bytes
- Final-window file range: $fileRangeBytes bytes
- Final-window total ``memory.current`` range: $memoryCurrentRangeBytes bytes
- PSS slope: $([math]::Round($pssSlopeBytesPerSecond, 3)) bytes/second
- Anonymous slope: $([math]::Round($anonSlopeBytesPerSecond, 3)) bytes/second

Total ``memory.current`` is supporting evidence. File-cache variation alone
does not fail a stable private-memory window.
"@

    $report = [ordered]@{
        schemaVersion = 'jmoa-linux-support-calibration-v3'
        contract = 'SUPPORT_CALIBRATION_V2'
        calibrationId = $CalibrationId
        hostProfile = $profile.name
        resourceClass = $profile.resourceClass
        healthyAtUtc = $healthyAt.ToString('o')
        observationSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
        samples = $rows.ToArray()
        finalWindowSamples = $FinalWindowSamples
        finalWindowStartSample = $Samples - $FinalWindowSamples + 1
        minimumAvailableMemoryBytes = $minimumAvailable
        minRequiredAvailableMemoryBytes = $profile.minSupportReadyMemoryBytes
        cgroupScope = $scopeAudit
        finalWindow = $driftReport.finalWindow
        limits = [ordered]@{
            maxPssRangeKb = $MaxFinalWindowPssRangeKb
            maxPrivateDirtyRangeKb = $MaxFinalWindowPrivateDirtyRangeKb
            maxAnonRangeBytes = $MaxFinalWindowAnonRangeBytes
            maxPositiveSlopeBytesPerSecond = $MaxPositiveSlopeBytesPerSecond
        }
        capacityPassed = ($capacityReasons.Count -eq 0)
        pressurePassed = -not (@($capacityReasons | Where-Object { $_ -match 'PSI' }).Count -ne 0)
        stabilityPassed = ($stabilityReasons.Count -eq 0)
        passed = ($outcome -eq 'SUPPORT_STACK_STABLE')
        terminalOutcome = $outcome
        fileCacheVariable = $fileCacheVariable
        attribution = $attribution
        capacityReasons = $capacityReasons.ToArray()
        stabilityReasons = $stabilityReasons.ToArray()
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'linux-host-calibration.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'linux-host-calibration.md') -Value @"
# Linux Support-Stack Calibration V2

- Calibration: $CalibrationId
- Observation: $($report.observationSeconds) seconds
- Samples: $Samples; final window: $FinalWindowSamples
- Exact cgroups: **$($scopeAudit.determination)**
- Capacity passed: **$($report.capacityPassed)**
- Stability passed: **$($report.stabilityPassed)**
- File-cache variation: **$fileCacheVariable**
- Outcome: **$outcome**

## Capacity reasons
$(if ($capacityReasons.Count -eq 0) { '- none' } else { ($capacityReasons | ForEach-Object { "- $_" }) -join "`n" })

## Stability reasons
$(if ($stabilityReasons.Count -eq 0) { '- none' } else { ($stabilityReasons | ForEach-Object { "- $_" }) -join "`n" })
"@

    foreach ($identity in @($configIdentity, $discoveryIdentity)) { Write-PostWindowDiagnostics -Identity $identity }
    $finalStatus = if ($report.passed) { 'COMPLETE' } else { 'FAILED' }
    if (-not $report.passed) { exit 2 }
} catch {
    $finalStatus = 'FAILED'
    throw
} finally {
    foreach ($name in @($discovery, $config)) { Invoke-Cli -Step "[TEARDOWN] final remove $name" -Arguments @('rm', '-f', $name) -AllowFailure | Out-Null }
    Invoke-Cli -Step "[TEARDOWN] final remove $network" -Arguments @('network', 'rm', $network) -AllowFailure | Out-Null
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status $finalStatus `
        -Stage 'linux-support-stack-calibration-v2' -Variant $CalibrationId | Out-Null
}

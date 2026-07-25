param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$B0Image,
    [Parameter(Mandatory)][string]$ConfigImage,
    [Parameter(Mandatory)][string]$DiscoveryImage,
    [Parameter(Mandatory)][string]$ConfigRepo,
    [string]$ContainerCli = '/usr/bin/podman',
    [ValidateSet('STANDARD_FIXED_8G', 'HYPERV_DEBIAN_FIXED_2G')][string]$HostProfile = 'HYPERV_DEBIAN_FIXED_2G',
    [int]$WarmupSeconds = 20,
    [int]$SettleSeconds = 5,
    [int]$HealthTimeoutSeconds = 900
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-linux-host-profiles.ps1')

$profile = Get-CampaignLinuxHostProfile -Name $HostProfile
$launchScript = Join-Path $PSScriptRoot 'campaign-launch-petclinic-stack.ps1'
$workloadScript = Join-Path $PSScriptRoot 'campaign-workload-petclinic.ps1'
$stopScript = Join-Path $PSScriptRoot 'campaign-stop-petclinic-stack.ps1'
$containerName = 'jmoa-capacity-b0'
New-JmoaDirectory -Path $OutputDirectory
$launchLedger = Join-Path $OutputDirectory 'child-ledgers/launch'
$workloadLedger = Join-Path $OutputDirectory 'child-ledgers/workload'
$environmentLedger = Join-Path $OutputDirectory 'child-ledgers/environment'
$teardownLedger = Join-Path $OutputDirectory 'child-ledgers/teardown'
Initialize-CampaignAuditLedger -LedgerDirectory $environmentLedger -Stage 'capacity-environment' -Variant 'CAPACITY_QUALIFICATION_ONLY' `
    -Description 'Non-evidence host headroom, swap, PSI, and OOM samples around one frozen B0 target arm.' | Out-Null

function Invoke-HostCapture {
    param([string]$Point)
    $command = @'
printf '%s\n' '--- meminfo ---'
cat /proc/meminfo
printf '%s\n' '--- pressure ---'
cat /proc/pressure/memory
printf '%s\n' '--- events ---'
cat /sys/fs/cgroup/memory.events
printf '%s\n' '--- swap-current ---'
cat /sys/fs/cgroup/memory.swap.current 2>/dev/null || echo 0
'@
    $result = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('machine', 'ssh', "sh -lc '$command'") `
        -LedgerDirectory $environmentLedger -Step "capture $Point constrained-host state"
    $text = $result.stdout
    $availableMatch = [regex]::Match($text, '(?m)^MemAvailable:\s+(\d+)\s+kB$')
    $swapTotalMatch = [regex]::Match($text, '(?m)^SwapTotal:\s+(\d+)\s+kB$')
    $someMatch = [regex]::Match($text, '(?m)^some\s+avg10=([0-9.]+).*total=(\d+)$')
    $fullMatch = [regex]::Match($text, '(?m)^full\s+avg10=([0-9.]+).*total=(\d+)$')
    $swapCurrent = @($text -split '\r?\n' | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
    if (-not $availableMatch.Success -or -not $swapTotalMatch.Success -or -not $someMatch.Success -or -not $fullMatch.Success -or $swapCurrent.Count -ne 1) {
        throw "Could not parse constrained-host state at $Point."
    }
    return [pscustomobject][ordered]@{
        point = $Point
        capturedAt = [DateTime]::UtcNow.ToString('o')
        availableMemoryBytes = [long]$availableMatch.Groups[1].Value * 1024
        swapTotalBytes = [long]$swapTotalMatch.Groups[1].Value * 1024
        cgroupSwapCurrentBytes = [long]$swapCurrent[0]
        memoryPressureSomeAvg10 = [double]::Parse($someMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
        memoryPressureFullAvg10 = [double]::Parse($fullMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
        memoryPressureSomeTotal = [long]$someMatch.Groups[2].Value
        memoryPressureFullTotal = [long]$fullMatch.Groups[2].Value
        oomEvents = Get-CampaignMemoryEventValue -Text $text -Name 'oom'
        oomKillEvents = Get-CampaignMemoryEventValue -Text $text -Name 'oom_kill'
    }
}
function Write-ConsolidatedLedger {
    $output = Join-Path $OutputDirectory 'capacity-qualification-command-ledger.md'
    $builder = [Text.StringBuilder]::new()
    [void]$builder.AppendLine('# PetClinic B0 Capacity Qualification Command Ledger')
    [void]$builder.AppendLine()
    [void]$builder.AppendLine('This is a non-evidence arm. It is never included in product or same-artifact medians.')
    foreach ($entry in @(
        @{ name = 'Launch'; path = Join-Path $launchLedger 'command-ledger.md' },
        @{ name = 'Environment'; path = Join-Path $environmentLedger 'command-ledger.md' },
        @{ name = 'Workload'; path = Join-Path $workloadLedger 'command-ledger.md' },
        @{ name = 'Teardown'; path = Join-Path $teardownLedger 'command-ledger.md' }
    )) {
        [void]$builder.AppendLine()
        [void]$builder.AppendLine("## $($entry.name)")
        [void]$builder.AppendLine()
        if (Test-Path -LiteralPath $entry.path -PathType Leaf) {
            [void]$builder.AppendLine((Get-Content -Raw -LiteralPath $entry.path))
        } else {
            [void]$builder.AppendLine("*Ledger missing: $($entry.path)*")
        }
    }
    [IO.File]::WriteAllText($output, $builder.ToString(), [Text.UTF8Encoding]::new($false))
    return [pscustomobject]@{ path = $output; sha256 = (Get-JmoaSha256 -Path $output).ToUpperInvariant() }
}

$samples = [Collections.Generic.List[object]]::new()
$passed = $false
$failure = ''
try {
    $samples.Add((Invoke-HostCapture -Point 'PRE_STACK')) | Out-Null
    & $launchScript -RunDirectory $OutputDirectory -ContainerName $containerName -Variant 'BASELINE' `
        -Image $B0Image -ConfigImage $ConfigImage -DiscoveryImage $DiscoveryImage -ConfigRepo $ConfigRepo `
        -CustomerReadyTimeoutSeconds $HealthTimeoutSeconds -ContainerCli $ContainerCli `
        -MinAvailableMemoryBeforeTargetBytes $profile.minPreTargetMemoryBytes `
        -LedgerDirectory $launchLedger -LedgerStage 'capacity-launch' -LedgerVariant 'CAPACITY_QUALIFICATION_ONLY'
    if (-not $?) { throw 'Capacity qualification stack launch failed.' }
    $samples.Add((Invoke-HostCapture -Point 'POST_HEALTH')) | Out-Null
    if ($WarmupSeconds -gt 0) { Start-Sleep -Seconds $WarmupSeconds }
    $samples.Add((Invoke-HostCapture -Point 'POST_WARMUP')) | Out-Null
    $workloadPath = Join-Path $OutputDirectory 'workload-result.json'
    & $workloadScript -OutputPath $workloadPath -BaseUrl 'http://localhost:8081/actuator/health' `
        -ContainerName $containerName -Variant 'BASELINE' -Rounds 3 -LedgerDirectory $workloadLedger -LedgerStage 'capacity-workload'
    if (-not $?) { throw 'Capacity qualification workload failed.' }
    $workload = Get-Content -Raw -LiteralPath $workloadPath | ConvertFrom-Json
    $samples.Add((Invoke-HostCapture -Point 'POST_WORKLOAD')) | Out-Null
    if ($SettleSeconds -gt 0) { Start-Sleep -Seconds $SettleSeconds }
    $samples.Add((Invoke-HostCapture -Point 'POST_SETTLE')) | Out-Null
    $restartCounts = [ordered]@{}
    foreach ($name in @("$containerName-config", "$containerName-discovery", $containerName)) {
        $restart = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @(
            'inspect', '--format', '{{.RestartCount}}', $name
        ) -LedgerDirectory $environmentLedger -Step "capture restart count for $name"
        $restartCounts[$name] = [int]$restart.stdout.Trim()
    }

    $reasons = [Collections.Generic.List[string]]::new()
    $minimumAvailable = [long](@($samples | Measure-Object -Property availableMemoryBytes -Minimum).Minimum)
    if ($minimumAvailable -lt $profile.minInArmMemoryBytes) { $reasons.Add("minimum MemAvailable $minimumAvailable is below $($profile.minInArmMemoryBytes) bytes") | Out-Null }
    if (@($samples | Where-Object { $_.swapTotalBytes -ne 0 -or $_.cgroupSwapCurrentBytes -ne 0 }).Count -ne 0) { $reasons.Add('swap appeared') | Out-Null }
    if (@($samples | Where-Object { $_.memoryPressureSomeAvg10 -gt 0 -or $_.memoryPressureFullAvg10 -gt 0 }).Count -ne 0) { $reasons.Add('memory PSI some/full avg10 became nonzero') | Out-Null }
    if (@($samples | Where-Object { $_.oomEvents -ne 0 -or $_.oomKillEvents -ne 0 }).Count -ne 0) { $reasons.Add('OOM counters became nonzero') | Out-Null }
    if (@($restartCounts.Values | Where-Object { [int]$_ -ne 0 }).Count -ne 0) { $reasons.Add('one or more campaign containers restarted') | Out-Null }
    if ([int]$workload.requests -ne 81 -or [int]$workload.errors -ne 0 -or [string]$workload.health -ne 'UP') { $reasons.Add('81-request workload validity failed') | Out-Null }
    $passed = ($reasons.Count -eq 0)
    $report = [ordered]@{
        schemaVersion = 'jmoa-petclinic-capacity-qualification-v1'
        classification = 'CAPACITY_QUALIFICATION_ONLY'
        includedInProductMedians = $false
        hostProfile = $profile.name
        samples = $samples.ToArray()
        minimumAvailableMemoryBytes = $minimumAvailable
        minRequiredInArmMemoryBytes = $profile.minInArmMemoryBytes
        workload = $workload
        containerRestartCounts = $restartCounts
        passed = $passed
        terminalVerdict = if ($passed) { 'CAPACITY_QUALIFICATION_PASSED' } else { 'STOPPED_INSUFFICIENT_2G_CAPACITY' }
        reasons = $reasons.ToArray()
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'capacity-qualification.json')
} catch {
    $failure = $_.Exception.Message
    $report = [ordered]@{
        schemaVersion = 'jmoa-petclinic-capacity-qualification-v1'
        classification = 'CAPACITY_QUALIFICATION_ONLY'
        includedInProductMedians = $false
        hostProfile = $profile.name
        samples = $samples.ToArray()
        passed = $false
        terminalVerdict = 'STOPPED_INSUFFICIENT_2G_CAPACITY'
        reasons = @($failure)
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'capacity-qualification.json')
} finally {
    & $stopScript -RunDirectory $OutputDirectory -ContainerName $containerName -Variant 'BASELINE' `
        -ContainerCli $ContainerCli -LedgerDirectory $teardownLedger -LedgerStage 'capacity-teardown' -LedgerVariant 'CAPACITY_QUALIFICATION_ONLY'
    Complete-CampaignAuditLedger -LedgerDirectory $environmentLedger -Status $(if ($passed) { 'COMPLETE' } else { 'FAILED' }) `
        -Stage 'capacity-environment' -Variant 'CAPACITY_QUALIFICATION_ONLY' | Out-Null
    $consolidated = Write-ConsolidatedLedger
    $report | Add-Member -NotePropertyName commandLedger -NotePropertyValue $consolidated.path -Force
    $report | Add-Member -NotePropertyName commandLedgerSha256 -NotePropertyValue $consolidated.sha256 -Force
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'capacity-qualification.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'capacity-qualification.md') -Value @"
# PetClinic B0 Capacity Qualification

- Classification: CAPACITY_QUALIFICATION_ONLY
- Included in product medians: **false**
- Host profile: $($profile.name)
- Passed: **$($report.passed)**
- Terminal verdict: $($report.terminalVerdict)
- Consolidated command ledger: $($consolidated.path)
- Ledger SHA-256: $($consolidated.sha256)

## Reasons
$(if (@($report.reasons).Count -eq 0) { '- none' } else { (@($report.reasons) | ForEach-Object { "- $_" }) -join "`n" })
"@
}
if (-not $passed) { exit 2 }

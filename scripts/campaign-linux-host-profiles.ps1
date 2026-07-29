Set-StrictMode -Version Latest

function Get-CampaignLinuxHostProfile {
    param([Parameter(Mandatory)][string]$Name)

    switch ($Name.Trim().ToUpperInvariant()) {
        'STANDARD_FIXED_8G' {
            return [pscustomobject][ordered]@{
                name                            = 'STANDARD_FIXED_8G'
                resourceClass                   = 'STANDARD_FIXED_MEMORY'
                minTotalMemoryBytes             = 8589934592L
                minPreflightAvailableMemoryBytes = 1073741824L
                minSupportReadyMemoryBytes      = 1073741824L
                minPreTargetMemoryBytes         = 1073741824L
                minInArmMemoryBytes             = 0L
                minLogicalProcessorCount        = 4
                requireSwapDisabled             = $true
                maxSwapUsedBytes                = 0L
                maxMemoryPressureSomeAvg10      = 1.0
                maxMemoryPressureFullAvg10      = 0.1
                requireZeroOomEvents            = $false
            }
        }
        'HYPERV_DEBIAN_FIXED_2G' {
            return [pscustomobject][ordered]@{
                name                            = 'HYPERV_DEBIAN_FIXED_2G'
                resourceClass                   = 'CONSTRAINED_FIXED_MEMORY'
                minTotalMemoryBytes             = 1992294400L
                minPreflightAvailableMemoryBytes = 1415577600L
                minSupportReadyMemoryBytes      = 734003200L
                minPreTargetMemoryBytes         = 629145600L
                minInArmMemoryBytes             = 314572800L
                minLogicalProcessorCount        = 4
                requireSwapDisabled             = $true
                maxSwapUsedBytes                = 0L
                maxMemoryPressureSomeAvg10      = 0.0
                maxMemoryPressureFullAvg10      = 0.0
                requireZeroOomEvents            = $true
            }
        }
        default { throw "Unsupported Linux campaign host profile: $Name" }
    }
}

function Get-CampaignMemoryEventValue {
    param(
        [AllowEmptyString()][string]$Text,
        [Parameter(Mandatory)][string]$Name
    )
    $match = [regex]::Match([string]$Text, "(?m)^$([regex]::Escape($Name))\s+(\d+)\s*$")
    if (-not $match.Success) { return 0L }
    return [long]$match.Groups[1].Value
}

function Test-CampaignLinuxHostAdmission {
    param(
        [Parameter(Mandatory)]$Profile,
        [Parameter(Mandatory)]$Snapshot
    )

    $reasons = [Collections.Generic.List[string]]::new()
    if ([string]$Snapshot.virtualization -ne 'microsoft') {
        $reasons.Add('systemd-detect-virt did not report microsoft') | Out-Null
    }
    if (-not [bool]$Snapshot.cgroupV2) {
        $reasons.Add('cgroup v2 is not mounted') | Out-Null
    }
    if ([int]$Snapshot.runningContainerCount -ne 0) {
        $reasons.Add("$($Snapshot.runningContainerCount) running container(s) found") | Out-Null
    }
    if ([int]$Snapshot.occupiedRequiredPortCount -ne 0) {
        $reasons.Add('one or more required ports are already listening') | Out-Null
    }
    if ([long]$Snapshot.totalMemoryBytes -lt [long]$Profile.minTotalMemoryBytes) {
        $reasons.Add("MemTotal $($Snapshot.totalMemoryBytes) is below $($Profile.minTotalMemoryBytes) bytes") | Out-Null
    }
    if ([long]$Snapshot.availableMemoryBytes -lt [long]$Profile.minPreflightAvailableMemoryBytes) {
        $reasons.Add("MemAvailable $($Snapshot.availableMemoryBytes) is below $($Profile.minPreflightAvailableMemoryBytes) bytes") | Out-Null
    }
    if ([int]$Snapshot.logicalProcessorCount -lt [int]$Profile.minLogicalProcessorCount) {
        $reasons.Add("logical processor count $($Snapshot.logicalProcessorCount) is below $($Profile.minLogicalProcessorCount)") | Out-Null
    }
    if ([bool]$Profile.requireSwapDisabled -and [long]$Snapshot.swapTotalBytes -ne 0) {
        $reasons.Add("swap is configured ($($Snapshot.swapTotalBytes) bytes); authoritative campaign requires swap disabled") | Out-Null
    }
    if ([long]$Snapshot.swapUsedBytes -gt [long]$Profile.maxSwapUsedBytes) {
        $reasons.Add("swap used $($Snapshot.swapUsedBytes) exceeds $($Profile.maxSwapUsedBytes) bytes") | Out-Null
    }
    if ([long]$Snapshot.cgroupSwapCurrentBytes -gt 0) {
        $reasons.Add("cgroup memory.swap.current is $($Snapshot.cgroupSwapCurrentBytes) bytes") | Out-Null
    }
    if ([double]$Snapshot.memoryPressureSomeAvg10 -gt [double]$Profile.maxMemoryPressureSomeAvg10) {
        $reasons.Add("memory PSI some avg10 $($Snapshot.memoryPressureSomeAvg10) exceeds $($Profile.maxMemoryPressureSomeAvg10)") | Out-Null
    }
    if ([double]$Snapshot.memoryPressureFullAvg10 -gt [double]$Profile.maxMemoryPressureFullAvg10) {
        $reasons.Add("memory PSI full avg10 $($Snapshot.memoryPressureFullAvg10) exceeds $($Profile.maxMemoryPressureFullAvg10)") | Out-Null
    }
    if ([bool]$Profile.requireZeroOomEvents -and
        ([long]$Snapshot.oomEvents -ne 0 -or [long]$Snapshot.oomKillEvents -ne 0)) {
        $reasons.Add("OOM counters are nonzero (oom=$($Snapshot.oomEvents), oom_kill=$($Snapshot.oomKillEvents))") | Out-Null
    }

    return [pscustomobject][ordered]@{
        passed = ($reasons.Count -eq 0)
        reasons = $reasons.ToArray()
    }
}

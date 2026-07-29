param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$PairId,
    [string]$ContainerCli='podman',
    [string]$LedgerDirectory='',
    [string]$LedgerStage='support-teardown'
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
New-JmoaDirectory $OutputDirectory
if ($LedgerDirectory) { Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant SUPPORT -Description "Pair-scoped support final validity and teardown." | Out-Null }
$safePair=$PairId -replace '[^A-Za-z0-9_.-]','-'
$network="jmoa-$safePair-net"; $configName="jmoa-$safePair-cfg"; $discoveryName="jmoa-$safePair-disc"
function Invoke-Cli {
    param(
        [Parameter(Mandatory)][string]$Step,
        [Parameter(Mandatory)][string[]]$CliArguments,
        [switch]$AllowFailure
    )
    Invoke-AuditedExternal -Executable $ContainerCli -Arguments $CliArguments -LedgerDirectory $LedgerDirectory -Step $Step -AllowFailure:$AllowFailure
}
$reasons=[Collections.Generic.List[string]]::new()
try {
    $configHealth=Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8888/actuator/health' -LedgerDirectory $LedgerDirectory -Step 'final config health'
    $discoveryHealth=Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8761/actuator/health' -LedgerDirectory $LedgerDirectory -Step 'final discovery health'
    $configRestartResult = Invoke-Cli -Step 'final config restart count' -CliArguments @('inspect','--format','{{.RestartCount}}',$configName)
    $discoveryRestartResult = Invoke-Cli -Step 'final discovery restart count' -CliArguments @('inspect','--format','{{.RestartCount}}',$discoveryName)
    $configRestart = [int]$configRestartResult.stdout.Trim()
    $discoveryRestart = [int]$discoveryRestartResult.stdout.Trim()
    $hostCmd=@'
printf '%s\n' '---MEMINFO---'; cat /proc/meminfo
printf '%s\n' '---PRESSURE---'; cat /proc/pressure/memory
printf '%s\n' '---EVENTS---'; cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.events
printf '%s\n' '---SWAP---'; cat /sys/fs/cgroup/user.slice/user-$(id -u).slice/memory.swap.current
'@.Replace("`r",'')
    $hostResult = Invoke-Cli -Step 'final support host validity' -CliArguments @('machine','ssh',$hostCmd)
    $hostText = $hostResult.stdout
    $full=[double]([regex]::Match($hostText,'(?m)^full\s+avg10=([0-9.]+)').Groups[1].Value)
    $oom=[long]([regex]::Match($hostText,'(?m)^oom\s+(\d+)$').Groups[1].Value)
    $oomKill=[long]([regex]::Match($hostText,'(?m)^oom_kill\s+(\d+)$').Groups[1].Value)
    $swapCurrent=[long]([regex]::Matches($hostText,'(?m)^\d+$')|Select-Object -Last 1).Value
    if([int]$configHealth.status-ne 200 -or [int]$discoveryHealth.status-ne 200){$reasons.Add('support health failed')}
    if($configRestart-ne 0 -or $discoveryRestart-ne 0){$reasons.Add('support restarted')}
    if($full-ne 0){$reasons.Add('PSI full avg10 is nonzero')}
    if($oom-ne 0 -or $oomKill-ne 0){$reasons.Add('OOM events are nonzero')}
    if($swapCurrent-ne 0){$reasons.Add('cgroup swap is nonzero')}
    $report=[ordered]@{schemaVersion='jmoa-petclinic-target-only-support-final-v1';pairId=$PairId;health=[ordered]@{config=[int]$configHealth.status;discovery=[int]$discoveryHealth.status};restartCounts=[ordered]@{config=$configRestart;discovery=$discoveryRestart};psiFullAvg10=$full;oomEvents=$oom;oomKillEvents=$oomKill;cgroupSwapCurrentBytes=$swapCurrent;passed=($reasons.Count-eq 0);reasons=$reasons.ToArray()}
    Write-JmoaJson $report (Join-Path $OutputDirectory 'support-final-validity.json')
} finally {
    foreach($n in @($discoveryName,$configName)){
        $removeResult = Invoke-Cli -Step "remove $n" -CliArguments @('rm','-f',$n) -AllowFailure
        $removeResult | Out-Null
    }
    $removeNetworkResult = Invoke-Cli -Step "remove $network" -CliArguments @('network','rm',$network) -AllowFailure
    $removeNetworkResult | Out-Null
    if($LedgerDirectory){
        $ledgerStatus = if($reasons.Count-eq 0){'COMPLETE'}else{'FAILED'}
        Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status $ledgerStatus -Stage $LedgerStage -Variant SUPPORT | Out-Null
    }
}
if($reasons.Count-ne 0){throw "Support final validity failed: $($reasons -join '; ')"}

param(
    [Parameter(Mandatory)][int]$PairIndex,
    [Parameter(Mandatory)][string]$FirstContainerName,
    [Parameter(Mandatory)][string]$FirstVariant,
    [Parameter(Mandatory)][string]$SecondContainerName,
    [Parameter(Mandatory)][string]$SecondVariant,
    [Parameter(Mandatory)][string]$FirstRunDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$ConfigContainerName,
    [Parameter(Mandatory)][string]$DiscoveryContainerName,
    [int]$RegistrationCleanupTimeoutSeconds=120,
    [string]$ContainerCli='podman',
    [string]$LedgerDirectory='',
    [string]$LedgerStage='transition'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
New-JmoaDirectory $OutputDirectory
if ($LedgerDirectory) { Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant TRANSITION -Description "Target transition proof for pair $PairIndex." | Out-Null }
$exists=Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('container','exists',$FirstContainerName) -LedgerDirectory $LedgerDirectory -Step 'prove first target container absent' -AllowFailure
$teardownPath=Join-Path $FirstRunDirectory 'target-teardown-info.json'
if(-not(Test-Path -LiteralPath $teardownPath -PathType Leaf)){throw "First target teardown proof missing: $teardownPath"}
$teardown=Get-Content -Raw -LiteralPath $teardownPath|ConvertFrom-Json
$firstPid=([string]$teardown.identityBeforeStop -split '\|')[0]
$pidProbe=Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('machine','ssh',"if [ -n '$firstPid' ] && [ -e '/proc/$firstPid' ]; then echo RESIDUAL_PID:$firstPid; fi") -LedgerDirectory $LedgerDirectory -Step 'prove no residual first target PID' -AllowFailure
$configHealth=Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8888/actuator/health' -LedgerDirectory $LedgerDirectory -Step 'verify config health during transition'
$discoveryHealth=Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8761/actuator/health' -LedgerDirectory $LedgerDirectory -Step 'verify discovery health during transition'
$configRestart=[int](Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('inspect','--format','{{.RestartCount}}',$ConfigContainerName) -LedgerDirectory $LedgerDirectory -Step 'config restart count during transition').stdout.Trim()
$discoveryRestart=[int](Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('inspect','--format','{{.RestartCount}}',$DiscoveryContainerName) -LedgerDirectory $LedgerDirectory -Step 'discovery restart count during transition').stdout.Trim()
$removed=$false; $lastStatus=0; $lastBody=''
$deadline=[DateTime]::UtcNow.AddSeconds($RegistrationCleanupTimeoutSeconds)
while([DateTime]::UtcNow -lt $deadline) {
    $e=Invoke-AuditedHttp -Method GET -Uri 'http://localhost:8761/eureka/apps/CUSTOMERS-SERVICE' -LedgerDirectory $LedgerDirectory -Step 'check first registration removal' -TimeoutSeconds 10
    $lastStatus=[int]$e.status; $lastBody="$($e.body)"
    if ($lastStatus -eq 404 -or $lastBody -notmatch [regex]::Escape($FirstContainerName)) { $removed=$true; break }
    Start-Sleep 5
}
$isolated=($FirstContainerName -ne $SecondContainerName)
$reasons=[Collections.Generic.List[string]]::new()
if ($exists.exitCode -eq 0) {$reasons.Add('first target container still exists')}
if ($pidProbe.stdout -match '\S') {$reasons.Add('residual first target process found')}
if ([int]$configHealth.status -ne 200 -or [int]$discoveryHealth.status -ne 200) {$reasons.Add('support health failed during transition')}
if ($configRestart -ne 0 -or $discoveryRestart -ne 0) {$reasons.Add('support restarted during transition')}
if (-not $removed -and -not $isolated) {$reasons.Add('first discovery registration neither removed nor identity-isolated')}
$report=[ordered]@{schemaVersion='jmoa-petclinic-target-transition-v1';pairIndex=$PairIndex;firstVariant=$FirstVariant;secondVariant=$SecondVariant;firstContainerName=$FirstContainerName;secondContainerName=$SecondContainerName;firstPid=$firstPid;firstContainerAbsent=($exists.exitCode -ne 0);residualPidAbsent=($pidProbe.stdout -notmatch '\S');registrationRemoved=$removed;registrationIsolated=$isolated;lastDiscoveryStatus=$lastStatus;configHealthStatus=[int]$configHealth.status;discoveryHealthStatus=[int]$discoveryHealth.status;supportRestartCounts=[ordered]@{config=$configRestart;discovery=$discoveryRestart};passed=($reasons.Count -eq 0);reasons=$reasons.ToArray()}
Write-JmoaJson $report (Join-Path $OutputDirectory 'target-transition-proof.json')
if ($LedgerDirectory) { Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status $(if($report.passed){'COMPLETE'}else{'FAILED'}) -Stage $LedgerStage -Variant TRANSITION | Out-Null }
if (-not $report.passed) { throw "Target transition failed: $($report.reasons -join '; ')" }

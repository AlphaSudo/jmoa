param(
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [string]$ContainerCli='podman',
    [string]$LedgerDirectory='',
    [string]$LedgerStage='teardown',
    [string]$LedgerVariant=''
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')
if (-not $LedgerVariant) {$LedgerVariant=$Variant}
if ($LedgerDirectory) { Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $LedgerVariant -Description "Target-only teardown for $Variant." | Out-Null }
$inspect=Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('inspect','--format','{{.State.Pid}}|{{.Id}}',$ContainerName) -LedgerDirectory $LedgerDirectory -Step 'capture target identity before stop' -AllowFailure
$remove=Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('rm','-f',$ContainerName) -LedgerDirectory $LedgerDirectory -Step 'remove target container' -AllowFailure
Write-JmoaJson ([ordered]@{variant=$Variant;containerName=$ContainerName;identityBeforeStop=$inspect.stdout.Trim();removeExitCode=$remove.exitCode;stoppedAtUtc=[DateTime]::UtcNow.ToString('o')}) (Join-Path $RunDirectory 'target-teardown-info.json')
if ($LedgerDirectory) { Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status COMPLETE -Stage $LedgerStage -Variant $LedgerVariant | Out-Null }


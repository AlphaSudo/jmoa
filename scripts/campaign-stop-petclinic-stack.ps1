<#
.SYNOPSIS
    Frozen-stack teardown that satisfies the runtime-screen-pair.ps1 stop contract:
    & <script> -RunDirectory <dir> -ContainerName <name> -Variant <variant> @StopScriptArguments

    Removes the customers container plus the derived discovery/config containers and the isolated
    network created by campaign-launch-petclinic-stack.ps1. Teardown is best-effort and idempotent so
    the screen engine's finally-block can always reach a clean state between arms.

    Review hardening (Issue #1): when -LedgerDirectory is supplied, every podman teardown command is
    routed through the audited child ledger (campaign-audit-common.ps1) so the parent campaign can
    reconstruct the full teardown command stream.
#>
param(
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [string]$ContainerCli = 'podman',
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'teardown',
    [string]$LedgerVariant = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

if ([string]::IsNullOrWhiteSpace($LedgerVariant)) { $LedgerVariant = $Variant }
if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $LedgerVariant -Description "Frozen full-stack teardown for $Variant ($ContainerName)." | Out-Null
}

$network = "$ContainerName-net"
$configName = "$ContainerName-cfg"
$discoveryName = "$ContainerName-disc"

$teardown = New-Object System.Collections.Generic.List[object]
foreach ($name in @($ContainerName, $discoveryName, $configName)) {
    $result = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('rm', '-f', $name) -LedgerDirectory $LedgerDirectory -Step "remove container $name" -AllowFailure
    $teardown.Add([ordered]@{ target = $name; kind = 'container'; exitCode = $result.exitCode }) | Out-Null
}
$networkResult = Invoke-AuditedExternal -Executable $ContainerCli -Arguments @('network', 'rm', $network) -LedgerDirectory $LedgerDirectory -Step "remove network $network" -AllowFailure
$teardown.Add([ordered]@{ target = $network; kind = 'network'; exitCode = $networkResult.exitCode }) | Out-Null

if (Test-Path -LiteralPath $RunDirectory -PathType Container) {
    $record = [ordered]@{
        variant       = $Variant
        containerName = $ContainerName
        teardown      = $teardown.ToArray()
        stoppedAtUtc  = [DateTime]::UtcNow.ToString('o')
    }
    Write-JmoaJson -Value $record -Path (Join-Path $RunDirectory 'stack-teardown-info.json')
}

if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'COMPLETE' -Stage $LedgerStage -Variant $LedgerVariant | Out-Null
}

Write-Host "Stopped $Variant stack ($ContainerName)."
exit 0

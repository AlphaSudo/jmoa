param(
    [Parameter(Mandatory)][string]$BaselineComposeFile,
    [Parameter(Mandatory)][string]$CandidateComposeFile,
    [Parameter(Mandatory)][string]$BaselineProjectName,
    [Parameter(Mandatory)][string]$CandidateProjectName,
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [string]$LedgerDirectory = '',
    [string]$LedgerStage = 'teardown',
    [string]$LedgerVariant = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Stage $LedgerStage -Variant $LedgerVariant `
        -Description "Audited Compose teardown for $Variant." | Out-Null
}
foreach ($stack in @(
    [ordered]@{ file = $BaselineComposeFile; project = $BaselineProjectName },
    [ordered]@{ file = $CandidateComposeFile; project = $CandidateProjectName }
)) {
    if (-not (Test-Path -LiteralPath $stack.file -PathType Leaf)) { continue }
    [void](Invoke-AuditedExternal -Executable 'podman' `
        -Arguments @('compose','-p',$stack.project,'-f',$stack.file,'down','-v','--remove-orphans') `
        -LedgerDirectory $LedgerDirectory -Step "tear down $($stack.project)" -TimeoutSeconds 180 -AllowFailure)
}
if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
    Complete-CampaignAuditLedger -LedgerDirectory $LedgerDirectory -Status 'COMPLETE' -Stage $LedgerStage -Variant $LedgerVariant | Out-Null
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$files = @(
    'scenario-ledger-common.ps1',
    'run-petclinic-audited-baseline-v2-scenario.ps1',
    'recover-historical-protocol-inventories.ps1',
    'run-public-jmoa-evaluation.ps1'
)
foreach ($file in $files) {
    $tokens = $null
    $errors = $null
    $path = Join-Path $PSScriptRoot $file
    [Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors) | Out-Null
    if ($errors.Count -gt 0) { throw "$file parse errors: $($errors | Out-String)" }
}

. (Join-Path $PSScriptRoot 'scenario-ledger-common.ps1')
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $repositoryRoot ('target/scenario-ledger-test/' + [guid]::NewGuid().ToString('N'))
Start-ScenarioLedger -ScenarioId 'fixture' -OutputDirectory $output -Description 'Recorder fixture'

$shell = (Get-Command pwsh).Source
Invoke-ScenarioCommand -Step 'allowed probe' -Executable $shell -Arguments @(
    '-NoProfile',
    '-Command',
    '[Console]::Out.Write("probe-out"); [Console]::Error.Write("probe-err"); exit 3'
) -AllowFailure | Out-Null
Invoke-ScenarioCommand -Step 'successful command' -Executable $shell -Arguments @(
    '-NoProfile',
    '-Command',
    'Write-Output "ok"'
) | Out-Null

$summary = Complete-ScenarioLedger -Status 'COMPLETE'
if ($summary.hardFailedCommands -ne 0) { throw 'Allowed nonzero command was counted as a hard failure.' }
if ($summary.allowedNonZeroCommands -ne 1) { throw 'Allowed nonzero command was not counted.' }
if ($summary.commandCount -ne 2) { throw 'Unexpected command count.' }

$ledger = Get-Content -LiteralPath $summary.markdownLedger -Raw
foreach ($expected in @('probe-out','probe-err','Nonzero exit allowed: True')) {
    if ($ledger -notmatch [regex]::Escape($expected)) { throw "Ledger is missing: $expected" }
}

Write-Output 'PowerShell parse and scenario-ledger fixture passed.'

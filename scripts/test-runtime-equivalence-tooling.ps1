param([string]$OutputDirectory = 'target/runtime-equivalence/tooling-test')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

$rootBase = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path $OutputDirectory
$root = Join-Path $rootBase ("run-" + [guid]::NewGuid().ToString('N'))
New-JmoaDirectory $root
$runner = Join-Path $root 'fixture-variant-runner.ps1'
$echoScript = Join-Path $root 'fixture-echo.ps1'
$configPath = Join-Path $root 'fixture-config.json'
$noisePath = Join-Path $root 'fixture-noise.json'

@'
param([string]$VariantId,[string]$RunId,[string]$CaptureRoot,[string]$ConfigPath)
$record = [ordered]@{ variant=$VariantId; runId=$RunId; config=(Split-Path $ConfigPath -Leaf) }
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $CaptureRoot 'fixture-run.json') -Encoding utf8
'@ | Set-Content -LiteralPath $runner -Encoding utf8
@'
param([string]$Value)
Write-Output $Value
'@ | Set-Content -LiteralPath $echoScript -Encoding utf8

$auditDirectory = Join-Path $root 'audit'
& (Join-Path $PSScriptRoot 'invoke-audited-command.ps1') -CommandId 'quoted-argument-fixture' -Executable (Get-Command pwsh).Source -ArgumentList @('-NoProfile','-File',$echoScript,'argument with spaces') -WorkingDirectory $root -AuditDirectory $auditDirectory | Out-Null
$auditRecord = Get-Content -LiteralPath (Join-Path $auditDirectory 'commands.ndjson') -Raw | ConvertFrom-Json
if ($auditRecord.arguments[-1] -ne 'argument with spaces') { throw 'Audited wrapper changed a quoted argument.' }
if ((Get-Content -LiteralPath $auditRecord.stdoutFile -Raw).Trim() -ne 'argument with spaces') { throw 'Audited wrapper did not preserve stdout.' }
if (-not (Test-Path -LiteralPath (Join-Path $auditDirectory 'campaign-summary.md'))) { throw 'Audited wrapper did not write a campaign summary.' }

[ordered]@{
  service='fixture'
  admission=[ordered]@{lineageQualified=$true;historicalReplayQualified=$true;sameArtifactNoiseQualified=$true}
  variants=@(@{id='B0'},@{id='V1'},@{id='V2'})
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $configPath -Encoding utf8

[ordered]@{
  artifactSha256=('A'*64)
  pairs=@(
    @{id='p1';left=@{pssKb=1000;privateDirtyKb=800;memoryCurrentBytes=1000000};right=@{pssKb=1010;privateDirtyKb=805;memoryCurrentBytes=1000100}},
    @{id='p2';left=@{pssKb=990;privateDirtyKb=790;memoryCurrentBytes=999000};right=@{pssKb=1000;privateDirtyKb=800;memoryCurrentBytes=1000000}}
  )
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $noisePath -Encoding utf8

& (Join-Path $PSScriptRoot 'analyze-same-artifact-noise.ps1') -InputPath $noisePath -OutputDirectory (Join-Path $root 'noise') | Out-Null
$noise = Get-Content -LiteralPath (Join-Path $root 'noise/same-artifact-noise.json') -Raw | ConvertFrom-Json
if (-not $noise.qualified) { throw 'Noise fixture should qualify.' }

& (Join-Path $PSScriptRoot 'runtime-three-arm-block.ps1') -VariantRunnerScript $runner -ConfigPath $configPath -OutputDirectory (Join-Path $root 'three-arm') -FailOnFailure | Out-Null
$manifest = Get-Content -LiteralPath (Join-Path $root 'three-arm/three-arm-run-manifest.json') -Raw | ConvertFrom-Json
if (-not $manifest.complete -or $manifest.runs.Count -ne 9) { throw 'Three-arm fixture did not produce nine successful runs.' }
$actual = @($manifest.runs | ForEach-Object variant) -join ','
if ($actual -ne 'B0,V1,V2,V1,V2,B0,V2,B0,V1') { throw "Unexpected rotation: $actual" }

$blockedConfig = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$blockedConfig.admission.sameArtifactNoiseQualified = $false
$blockedPath = Join-Path $root 'fixture-config-blocked.json'
$blockedConfig | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $blockedPath -Encoding utf8
$blocked = $false
try {
  & (Join-Path $PSScriptRoot 'runtime-three-arm-block.ps1') -VariantRunnerScript $runner -ConfigPath $blockedPath -OutputDirectory (Join-Path $root 'blocked') -FailOnFailure | Out-Null
} catch {
  $blocked = $_.Exception.Message -like '*sameArtifactNoiseQualified*'
}
if (-not $blocked) { throw 'Three-arm runner did not enforce the failed noise gate.' }

[ordered]@{ status='PASS'; auditedArgumentPreserved=$true; noiseQualified=$noise.qualified; threeArmRuns=$manifest.runs.Count; failedGateBlocked=$blocked } | ConvertTo-Json

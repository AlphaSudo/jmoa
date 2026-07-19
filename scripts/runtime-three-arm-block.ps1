param(
    [Parameter(Mandatory)][string]$VariantRunnerScript,
    [Parameter(Mandatory)][string]$ConfigPath,
    [string]$OutputDirectory = 'target/runtime-equivalence/three-arm',
    [switch]$FailOnFailure
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if (-not (Test-Path $VariantRunnerScript -PathType Leaf)) { throw "Variant runner not found: $VariantRunnerScript" }
$config=Get-Content $ConfigPath -Raw|ConvertFrom-Json
$ids=@($config.variants.id)
$actualIds = @($ids | Sort-Object) -join ','
$requiredIds = @('B0','V1','V2') -join ','
if ($actualIds -ne $requiredIds) { throw 'Config must define exactly B0, V1, and V2.' }
if (-not ($config.PSObject.Properties.Name -contains 'admission')) { throw 'Config must contain an explicit three-arm admission block.' }
$requiredAdmission = @('lineageQualified','historicalReplayQualified','sameArtifactNoiseQualified')
foreach ($gate in $requiredAdmission) {
  if (-not ($config.admission.PSObject.Properties.Name -contains $gate) -or -not [bool]$config.admission.$gate) {
    throw "Three-arm campaign blocked: admission gate '$gate' is not qualified."
  }
}
New-JmoaDirectory $OutputDirectory
$orders=@(@('B0','V1','V2'),@('V1','V2','B0'),@('V2','B0','V1'))
$runs=@(); $failed=$false
for($block=0;$block-lt$orders.Count;$block++) {
  foreach($variantId in $orders[$block]) {
    $runId="block-$($block+1)-$variantId"
    $runDir=Join-Path $OutputDirectory $runId
    New-JmoaDirectory $runDir
    $started=[DateTime]::UtcNow
    & (Get-Command pwsh).Source -NoProfile -File $VariantRunnerScript -VariantId $variantId -RunId $runId -CaptureRoot $runDir -ConfigPath $ConfigPath
    $exit=$LASTEXITCODE
    if($null-eq$exit){$exit=0}
    $runs += [ordered]@{block=$block+1;order=@($orders[$block]);variant=$variantId;runId=$runId;startedUtc=$started.ToString('o');endedUtc=[DateTime]::UtcNow.ToString('o');exitCode=$exit;captureRoot=$runDir}
    if($exit-ne0){$failed=$true;if($FailOnFailure){break}}
  }
  if($failed-and$FailOnFailure){break}
}
$report=[ordered]@{schemaVersion='jmoa-three-arm-block-v1';service=$config.service;orders=$orders;runs=$runs;complete=(-not$failed-and$runs.Count-eq9);status=if($failed){'FAILED'}elseif($runs.Count-eq9){'COMPLETE'}else{'INCOMPLETE'}}
Write-JmoaJson $report (Join-Path $OutputDirectory 'three-arm-run-manifest.json')
$report|ConvertTo-Json -Depth 12
if($failed-and$FailOnFailure){throw 'Three-arm block failed.'}

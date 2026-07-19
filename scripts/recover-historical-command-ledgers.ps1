param(
    [string]$OutputDirectory = 'docs/product-evidence/command-ledgers',
    [string]$PrivateOutputDirectory = 'target/runtime-equivalence/command-ledgers'
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
New-JmoaDirectory $OutputDirectory
New-JmoaDirectory $PrivateOutputDirectory

$repositoryRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$root=Split-Path $repositoryRoot -Parent
$doctorPhase32k=Join-Path $root 'v3.3\phase32-doctor-service\out\phase32k'
$doctorD2Jar=(Get-ChildItem -LiteralPath $doctorPhase32k -Filter '*-d2-fixed.jar' -File | Select-Object -First 1).FullName
$specs=@(
  [ordered]@{service='petclinic';era='historical';classification=@('HISTORICAL_COMMANDS_COMPLETE','HISTORICAL_ARTIFACT_COMPLETE','HISTORICAL_RUNTIME_NOT_REPRODUCIBLE');scripts=@(
    "$root\v3.3\phase33-petclinic\out\phase33l\run-33l7-exploded-boot-materialization.ps1",
    "$root\v3.3\phase33-petclinic\out\phase33m\run-33m-integrated-nocds-confirmation.ps1");artifacts=@(
    "$root\v3.3\phase33-petclinic\out\phase33k\baseline.jar",
    "$root\v3.3\phase33-petclinic\out\phase33k\optimized.jar");notes='Phase 33M is the authoritative exploded-Boot, no-CDS full-P2 protocol. Its frozen 2026-07-19 replay reversed direction and is classified as historical runtime drift.'},
  [ordered]@{service='doctor';era='historical';classification=@('HISTORICAL_COMMANDS_COMPLETE','HISTORICAL_ARTIFACT_COMPLETE','HISTORICAL_RUNTIME_REPRODUCIBLE');scripts=@(
    "$root\v3.3\phase32-doctor-service\out\phase32k\build-d2-fixed.ps1",
    "$root\v3.3\phase32-doctor-service\out\phase32k\train-cds.ps1",
    "$root\v3.3\phase32-doctor-service\out\phase32k\measure-32i.ps1");artifacts=@(
    "$root\v3.3\phase32-doctor-service\out\phase32d\doctor-baseline.jar",
    $doctorD2Jar,
    "$root\v3.3\phase32-doctor-service\out\phase32k\leyden-baseline\baseline-doctor-management.jsa",
    "$root\v3.3\phase32-doctor-service\out\phase32k\leyden-d2-fixed\d2-fixed-doctor-management.jsa");artifactAliases=@('doctor-baseline.jar','d2-fixed.jar','baseline-app-cds.jsa','d2-fixed-app-cds.jsa');notes='D2 is the corrected historical optimized comparator, not a strict B0.'},
  [ordered]@{service='patient';era='historical';classification=@('HISTORICAL_COMMANDS_COMPLETE','HISTORICAL_ARTIFACT_MISSING','HISTORICAL_RUNTIME_NOT_REPRODUCIBLE');scripts=@(
    "$root\v3.3\phase31-warmed-production-reprofile\out\phase31d\build-c2.ps1",
    "$root\v3.3\phase31-warmed-production-reprofile\out\phase31d\measure-31d.ps1",
    "$root\v3.3\phase31-warmed-production-reprofile\out\phase31d\docker-compose.31d-c2-runtime.yml");artifacts=@(
    "$root\v3.3\phase31-warmed-production-reprofile\out\phase31d\leyden-cache\baseline-patient-management.jsa",
    "$root\v3.3\phase31-warmed-production-reprofile\out\phase31d\leyden-cache\c2-patient-management.jsa");notes='Phase 31D used expanded classpath and artifact-specific application CDS. Original baseline/C2 application artifacts are not frozen as standalone JARs in Phase 31D.'},
  [ordered]@{service='petclinic';era='current';classification=@('CURRENT_COMMANDS_COMPLETE','CURRENT_ARTIFACT_COMPLETE');scripts=@(
    "$root\jmoa\scripts\run-v2-final-customers-comparison.ps1",
    "$root\jmoa\scripts\run-layered-petclinic-pair.ps1");artifacts=@(
    "$root\jmoa\target\product-evidence\petclinic-b0-clean.jar",
    "$root\v3.3\phase33-petclinic\out\phase33k\optimized.jar");notes='Current direct screen used a newly rebuilt strict B0 against a historically frozen V2; source-universe reconciliation is open.'},
  [ordered]@{service='doctor';era='current';classification=@('CURRENT_COMMANDS_COMPLETE','CURRENT_ARTIFACT_COMPLETE');scripts=@(
    "$root\jmoa\target\product-evidence\doctor-product\launch-doctor-b0.ps1",
    "$root\jmoa\target\product-evidence\doctor-product\launch-doctor-v2.ps1",
    "$root\jmoa\target\product-evidence\doctor-product\train-product-cds.ps1",
    "$root\jmoa\scripts\runtime-screen-pair.ps1");artifacts=@(
    "$root\jmoa\target\product-evidence\doctor-product\b0-no-jmoa.jar",
    "$root\jmoa\target\product-evidence\doctor-product\v2-final.jar",
    "$root\jmoa\target\product-evidence\doctor-product\b0-cds-corrected\b0-doctor-management.jsa",
    "$root\jmoa\target\product-evidence\doctor-product\v2-cds-corrected\v2-doctor-management.jsa");artifactAliases=@('b0-no-jmoa.jar','v2-final.jar','b0-app-cds.jsa','v2-app-cds.jsa');notes='Current strict B0 to final V2 confirmation with separately trained archives.'},
  [ordered]@{service='patient';era='current';classification=@('CURRENT_COMMANDS_COMPLETE','CURRENT_ARTIFACT_COMPLETE');scripts=@(
    "$root\jmoa\target\product-evidence\patient-product\launch-patient-b0.ps1",
    "$root\jmoa\target\product-evidence\patient-product\launch-patient-v2.ps1",
    "$root\jmoa\target\product-evidence\patient-product\patient-workload.ps1",
    "$root\jmoa\scripts\runtime-screen-pair.ps1");artifacts=@(
    "$root\jmoa\target\product-evidence\patient-product\b0-no-jmoa.jar",
    "$root\jmoa\target\product-evidence\patient-product\v2-final.jar");notes='Current direct screens used one stock JDK base archive for both variants.'}
)

foreach($spec in $specs){
  $commands=@($spec.scripts|ForEach-Object{
    $exists=Test-Path $_ -PathType Leaf
    [ordered]@{logicalPath="historical/$($spec.service)/$([IO.Path]::GetFileName($_))";privatePath=$_;exists=$exists;sha256=if($exists){Get-JmoaSha256 $_}else{''};bytes=if($exists){(Get-Item $_).Length}else{$null}}
  })
  $artifactIndex=0
  $artifacts=@($spec.artifacts|ForEach-Object{
    $privatePath=$_
    $exists=Test-Path $privatePath -PathType Leaf
    $publicLeaf=if($spec.Contains('artifactAliases')){$spec.artifactAliases[$artifactIndex]}else{[IO.Path]::GetFileName($privatePath)}
    $artifactIndex++
    [ordered]@{logicalPath="artifact/$($spec.service)/$publicLeaf";privatePath=$privatePath;exists=$exists;sha256=if($exists){Get-JmoaSha256 $privatePath}else{''};bytes=if($exists){(Get-Item $privatePath).Length}else{$null}}
  })
  $private=[ordered]@{schemaVersion='jmoa-command-ledger-v1';service=$spec.service;era=$spec.era;generatedUtc=[DateTime]::UtcNow.ToString('o');classification=$spec.classification;notes=$spec.notes;commands=$commands;artifacts=$artifacts}
  Write-JmoaJson $private (Join-Path $PrivateOutputDirectory "$($spec.service)-$($spec.era).json")
  $public=[ordered]@{schemaVersion=$private.schemaVersion;service=$spec.service;era=$spec.era;classification=$spec.classification;notes=$spec.notes;commands=@($commands|ForEach-Object{[ordered]@{logicalPath=$_.logicalPath;exists=$_.exists;sha256=$_.sha256;bytes=$_.bytes}});artifacts=@($artifacts|ForEach-Object{[ordered]@{logicalPath=$_.logicalPath;exists=$_.exists;sha256=$_.sha256;bytes=$_.bytes}})}
  Write-JmoaJson $public (Join-Path $OutputDirectory "$($spec.service)-$($spec.era).json")
  $commandRows=@($public.commands|ForEach-Object{"| $($_.logicalPath) | $($_.exists) | $($_.sha256) |"})
  $artifactRows=@($public.artifacts|ForEach-Object{"| $($_.logicalPath) | $($_.exists) | $($_.sha256) |"})
  $md="# $($spec.service) $($spec.era) Command Ledger`n`nClassification: **$($spec.classification -join ', ')**`n`n$($spec.notes)`n`n## Commands`n`n| Logical path | Present | SHA-256 |`n|---|---:|---|`n$($commandRows-join"`n")`n`n## Artifacts`n`n| Logical path | Present | SHA-256 |`n|---|---:|---|`n$($artifactRows-join"`n")"
  Write-JmoaText $md (Join-Path $OutputDirectory "$($spec.service)-$($spec.era).md")
}

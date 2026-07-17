param(
    [Parameter(Mandatory)][string[]]$PairAnalysisPaths,
    [Parameter(Mandatory)][string]$ArtifactLabel,
    [Parameter(Mandatory)][ValidateSet('STARTUP','REPRESENTATIVE')][string]$Profile,
    [Parameter(Mandatory)][string]$ReproducibilityReportPath,
    [string]$OutputDir='target/jmoa-appcds-profile-analysis'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if(@($PairAnalysisPaths).Count-ne2){throw 'Exactly two balanced APP-vs-BASE pair analyses are required.'}
if(-not(Test-Path -LiteralPath $ReproducibilityReportPath -PathType Leaf)){throw "Missing reproducibility report: $ReproducibilityReportPath"}
New-JmoaDirectory $OutputDir
$pairs=@($PairAnalysisPaths|ForEach-Object{Get-Content -Raw -LiteralPath $_|ConvertFrom-Json})
$repro=Get-Content -Raw -LiteralPath $ReproducibilityReportPath|ConvertFrom-Json
function Median([long[]]$values){$s=@($values|Sort-Object);if($s.Count%2){return [long]$s[[int][Math]::Floor($s.Count/2)]};return [long][Math]::Round(($s[$s.Count/2-1]+$s[$s.Count/2])/2.0)}
$pss=@($pairs|%{[long]$_.deltas.pssKb});$pd=@($pairs|%{[long]$_.deltas.privateDirtyKb});$memory=@($pairs|%{[long]$_.deltas.memoryCurrentBytes})
$directions=@($pss|%{if($_-lt0){'WIN'}elseif($_-gt0){'REGRESSION'}else{'NEUTRAL'}}|Sort-Object -Unique)
$semantic=@($pairs|Where-Object{$_.workload.baselineErrors-ne0-or$_.workload.candidateErrors-ne0}).Count-eq0
$structural=$repro.status-eq'TRAINING_STRUCTURALLY_STABLE'
$medianPss=Median $pss;$medianMemory=Median $memory
$directionCount=@($directions).Count
$admitted=$structural-and$semantic-and$directionCount-eq1-and$medianPss-le1024-and$medianMemory-le1048576
$report=[ordered]@{
    metadataVersion='jmoa-balanced-appcds-profile-analysis-v1';artifact=$ArtifactLabel;profile=$Profile
    status=if($admitted){'PROFILE_VARIANT_ADMITTED'}else{'PROFILE_VARIANT_REJECTED'}
    pairCount=2;pssDirection=$directions;medianAppMinusBasePssKb=$medianPss;medianAppMinusBasePrivateDirtyKb=Median $pd
    medianAppMinusBaseMemoryCurrentBytes=$medianMemory;semanticPassed=$semantic;structuralReproducibilityPassed=$structural
    gates=[ordered]@{sameDirectionInBothOrders=$directionCount-eq1;medianPssMaximumKb=1024;medianMemoryCurrentMaximumBytes=1048576}
    claimBoundary='Diagnostic profile admission for one fixed artifact. This is not a V1-to-V2 result.'
}
Write-JmoaJson $report (Join-Path $OutputDir 'balanced-appcds-profile-analysis.json')
$md="# Balanced APP vs BASE Profile Analysis`n`n- Artifact: ``$ArtifactLabel```n- Profile: ``$Profile```n- Status: ``$($report.status)```n- Median APP-BASE PSS: ``$medianPss KB```n- Median APP-BASE memory.current: ``$medianMemory bytes```n- Direction(s): ``$($directions -join ', ')```n"
Write-JmoaText $md (Join-Path $OutputDir 'balanced-appcds-profile-analysis.md')
exit 0

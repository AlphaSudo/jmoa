param(
  [Parameter(Mandatory)][string[]]$RunDirectory,
  [Parameter(Mandatory)][string]$ArtifactSha256,
  [Parameter(Mandatory)][string]$OutputPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
if($RunDirectory.Count-lt2){throw 'At least two run directories are required.'}
function Read-Metrics([string]$Directory){
  $smaps=Get-Content (Join-Path $Directory 'smaps_rollup.txt') -Raw
  $pss=[regex]::Match($smaps,'(?m)^Pss:\s+(\d+)\s+kB').Groups[1].Value
  $pd=[regex]::Match($smaps,'(?m)^Private_Dirty:\s+(\d+)\s+kB').Groups[1].Value
  $current=(Get-Content (Join-Path $Directory 'memory.current') -Raw).Trim()
  if(-not$pss-or-not$pd-or-not$current){throw "Incomplete metrics in $Directory"}
  [ordered]@{pssKb=[long]$pss;privateDirtyKb=[long]$pd;memoryCurrentBytes=[long]$current}
}
$metrics=@($RunDirectory|ForEach-Object{Read-Metrics $_})
$pairs=@()
for($i=0;$i-lt$metrics.Count-1;$i+=2){$pairs+=[ordered]@{id="control-$([int]($i/2)+1)";left=$metrics[$i];right=$metrics[$i+1]}}
if($pairs.Count-eq0){throw 'No complete pairs were produced.'}
$payload=[ordered]@{schemaVersion='jmoa-same-artifact-input-v1';artifactSha256=$ArtifactSha256;pairs=$pairs;source='retrospective repeated runs; qualification remains diagnostic unless campaign order was predeclared'}
$parent=Split-Path $OutputPath -Parent;if($parent){New-JmoaDirectory $parent}
Write-JmoaJson $payload $OutputPath
$payload|ConvertTo-Json -Depth 8

param(
    [Parameter(Mandatory)][string]$InputPath,
    [string]$OutputDirectory = 'target/runtime-equivalence/noise',
    [int]$MaxPssDriftKb = 1024,
    [int]$MaxPrivateDirtyDriftKb = 1024,
    [int]$MaxMemoryCurrentDriftBytes = 1048576
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
New-JmoaDirectory $OutputDirectory
$input = Get-Content -LiteralPath $InputPath -Raw | ConvertFrom-Json
function Median([double[]]$Values) { $s=@($Values|Sort-Object); if (-not $s.Count) { return 0 }; if ($s.Count%2) { return $s[[int][math]::Floor($s.Count/2)] }; ($s[$s.Count/2-1]+$s[$s.Count/2])/2 }
$pairs = @($input.pairs | ForEach-Object {
    [ordered]@{ id=$_.id; pssAbsKb=[math]::Abs([double]$_.right.pssKb-[double]$_.left.pssKb); privateDirtyAbsKb=[math]::Abs([double]$_.right.privateDirtyKb-[double]$_.left.privateDirtyKb); memoryCurrentAbsBytes=[math]::Abs([double]$_.right.memoryCurrentBytes-[double]$_.left.memoryCurrentBytes) }
})
$medians=[ordered]@{ pssAbsKb=Median @($pairs.pssAbsKb); privateDirtyAbsKb=Median @($pairs.privateDirtyAbsKb); memoryCurrentAbsBytes=Median @($pairs.memoryCurrentAbsBytes) }
$report=[ordered]@{ schemaVersion='jmoa-same-artifact-noise-v1'; artifactSha256=$input.artifactSha256; pairs=$pairs; medians=$medians; thresholds=[ordered]@{maxPssDriftKb=$MaxPssDriftKb;maxPrivateDirtyDriftKb=$MaxPrivateDirtyDriftKb;maxMemoryCurrentDriftBytes=$MaxMemoryCurrentDriftBytes}; qualified=($medians.pssAbsKb-le$MaxPssDriftKb-and$medians.privateDirtyAbsKb-le$MaxPrivateDirtyDriftKb-and$medians.memoryCurrentAbsBytes-le$MaxMemoryCurrentDriftBytes) }
Write-JmoaJson $report (Join-Path $OutputDirectory 'same-artifact-noise.json')
Write-JmoaText "# Same-Artifact Noise`n`nQualified: **$($report.qualified)**`n`nMedian absolute PSS drift: $($medians.pssAbsKb) KB.`nMedian absolute Private_Dirty drift: $($medians.privateDirtyAbsKb) KB.`nMedian absolute memory.current drift: $($medians.memoryCurrentAbsBytes) bytes." (Join-Path $OutputDirectory 'same-artifact-noise.md')
$report|ConvertTo-Json -Depth 10

param(
    [Parameter(Mandatory)][string]$V1StartupAnalysis,[Parameter(Mandatory)][string]$V2StartupAnalysis,
    [Parameter(Mandatory)][string]$V1RepresentativeAnalysis,[Parameter(Mandatory)][string]$V2RepresentativeAnalysis,
    [Parameter(Mandatory)][long]$StartupMedianMappedPssKb,[Parameter(Mandatory)][long]$RepresentativeMedianMappedPssKb,
    [string]$OutputDir='target/jmoa-appcds-profile-selection'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1');New-JmoaDirectory $OutputDir
function Read-Analysis([string]$path){Get-Content -Raw -LiteralPath $path|ConvertFrom-Json}
$v1Startup=Read-Analysis $V1StartupAnalysis;$v2Startup=Read-Analysis $V2StartupAnalysis
$v1Representative=Read-Analysis $V1RepresentativeAnalysis;$v2Representative=Read-Analysis $V2RepresentativeAnalysis
$analyses=@($v1Startup,$v2Startup,$v1Representative,$v2Representative)
$startup=$v1Startup.status-eq'PROFILE_VARIANT_ADMITTED'-and$v2Startup.status-eq'PROFILE_VARIANT_ADMITTED'
$representative=$v1Representative.status-eq'PROFILE_VARIANT_ADMITTED'-and$v2Representative.status-eq'PROFILE_VARIANT_ADMITTED'
$selected=$null;$verdict=$null
if($startup-and-not$representative){$selected='STARTUP'}elseif($representative-and-not$startup){$selected='REPRESENTATIVE'}elseif($startup-and$representative){$selected=if($StartupMedianMappedPssKb-le$RepresentativeMedianMappedPssKb){'STARTUP'}else{'REPRESENTATIVE'}}else{
    if(@($analyses|Where-Object{-not$_.structuralReproducibilityPassed}).Count-gt0){$verdict='NON_REPRODUCIBLE_ARCHIVE'}
    elseif(@($analyses|Where-Object{$_.medianAppMinusBasePssKb-gt1024-or$_.medianAppMinusBaseMemoryCurrentBytes-gt1048576}).Count-gt0){$verdict='SINGLE_REPLICA_ARCHIVE_REGRESSION'}
    else{$verdict='APP_CDS_NO_MARGINAL_VALUE'}
}
$report=[ordered]@{
    metadataVersion='jmoa-appcds-profile-selection-v1';status=if($selected){'PROFILE_SELECTED'}else{'TERMINAL_STOP'}
    selectedProfile=$selected;terminalVerdict=$verdict;startupAdmitted=$startup;representativeAdmitted=$representative
    selectionRule='One passing profile wins; if both pass choose smaller median mapped archive PSS; if neither passes stop.'
    nextGate=if($selected){'Train independent V1/V2 holdout C for the selected profile.'}else{'Keep Patient APP_CDS blocked permanently for V2.'}
}
Write-JmoaJson $report (Join-Path $OutputDir 'appcds-profile-selection.json')
$md="# AppCDS Profile Selection`n`n- Status: ``$($report.status)```n- Selected: ``$selected```n- Terminal verdict: ``$verdict```n"
Write-JmoaText $md (Join-Path $OutputDir 'appcds-profile-selection.md');exit 0

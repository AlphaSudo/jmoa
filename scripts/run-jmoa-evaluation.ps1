param(
    [Parameter(Mandatory)]
    [ValidateSet('Doctor', 'Patient', 'PetClinicCustomers')]
    [string]$Service,

    [ValidateSet('B0', 'V1', 'V2', 'Final', 'Explain')]
    [string[]]$Stages = @('B0', 'V1', 'V2', 'Final', 'Explain'),

    [string]$ConfigPath = '',
    [Parameter(Mandatory)][string]$OutputDirectory,
    [switch]$DryRun,
    [switch]$NoResume
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run-three-artifact-balanced-campaign.ps1'
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw "Three-artifact campaign runner is missing: $runner"
}

$environmentName = 'JMOA_{0}_CAMPAIGN_CONFIG' -f ($Service -replace '([a-z])([A-Z])', '$1_$2').ToUpperInvariant()
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = [Environment]::GetEnvironmentVariable($environmentName)
}
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    throw "Supply -ConfigPath or set $environmentName to a private service campaign config."
}
if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw "Campaign config does not exist: $ConfigPath"
}

$qualificationStages = @($Stages | Where-Object { $_ -in @('B0', 'V1', 'V2') })
if ($qualificationStages.Count -gt 0 -and $qualificationStages.Count -ne 3) {
    throw 'B0, V1, and V2 qualification is one frozen unit. Request all three together or none of them.'
}

$invocation = @{
    ConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path
    OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
    DryRun = $DryRun
    NoResume = $NoResume
}

if ($qualificationStages.Count -eq 3) {
    & $runner @invocation -Stage Qualification
    if (-not $?) { throw "$Service qualification failed." }
}
if ($Stages -contains 'Final') {
    & $runner @invocation -Stage Final
    if (-not $?) { throw "$Service final balanced campaign failed." }
}
if ($Stages -contains 'Explain') {
    & $runner @invocation -Stage Explain
    if (-not $?) { throw "$Service evidence explanation failed." }
}

Write-Host "$Service evaluation stages completed: $($Stages -join ', ')"

param(
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$ComposeFile,
    [Parameter(Mandatory)][string]$OverlayFile,
    [Parameter(Mandatory)][string]$ComposeProject,
    [Parameter(Mandatory)][string]$ComposeWorkingDirectory,
    [string]$ContainerCli = 'podman',
    [string]$Services = '',
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $RunDirectory | Out-Null
if (-not (Test-Path -LiteralPath $ComposeFile -PathType Leaf)) { throw "Compose file does not exist: $ComposeFile" }
if (-not (Test-Path -LiteralPath $OverlayFile -PathType Leaf)) { throw "Compose overlay does not exist: $OverlayFile" }
if (-not (Test-Path -LiteralPath $ComposeWorkingDirectory -PathType Container)) { throw "Compose working directory does not exist: $ComposeWorkingDirectory" }

$arguments = @(
    'compose', '-p', $ComposeProject,
    '-f', $ComposeFile,
    '-f', $OverlayFile,
    'up', '-d'
)
if ($NoBuild) { $arguments += '--no-build' }
if (-not [string]::IsNullOrWhiteSpace($Services)) {
    $arguments += @($Services.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

Push-Location $ComposeWorkingDirectory
try {
    $output = & $ContainerCli @arguments 2>&1
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
$output | Set-Content -LiteralPath (Join-Path $RunDirectory 'compose-launch.log') -Encoding UTF8
if ($exitCode -ne 0) {
    throw "Compose launch failed for $Variant ($ContainerName) with exit code $exitCode. See $(Join-Path $RunDirectory 'compose-launch.log')."
}
Write-Host "Launched $Variant container $ContainerName."

param(
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][string]$ContainerName,
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$ComposeFile,
    [Parameter(Mandatory)][string]$OverlayFile,
    [Parameter(Mandatory)][string]$ComposeProject,
    [Parameter(Mandatory)][string]$ComposeWorkingDirectory,
    [string]$ContainerCli = 'podman'
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $RunDirectory | Out-Null
$arguments = @(
    'compose', '-p', $ComposeProject,
    '-f', $ComposeFile,
    '-f', $OverlayFile,
    'down', '--remove-orphans'
)
Push-Location $ComposeWorkingDirectory
try {
    $output = & $ContainerCli @arguments 2>&1
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
$output | Set-Content -LiteralPath (Join-Path $RunDirectory 'compose-stop.log') -Encoding UTF8
if ($exitCode -ne 0) {
    throw "Compose stop failed for $Variant ($ContainerName) with exit code $exitCode. See $(Join-Path $RunDirectory 'compose-stop.log')."
}
Write-Host "Stopped $Variant compose project."

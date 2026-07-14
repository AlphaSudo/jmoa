param(
    [Parameter(Mandatory)][string]$InputDir,
    [Parameter(Mandatory)][string]$ArtifactPath,
    [Parameter(Mandatory)][string]$Service,
    [Parameter(Mandatory)][string]$LaunchMode,
    [Parameter(Mandatory)][string]$RuntimePolicy,
    [string]$CdsArchivePath = "",
    [string]$ReducerEngine = "raw",
    [ValidateSet('PUBLIC', 'PRIVATE', 'INTERNAL', 'UNKNOWN')][string]$Scope = 'UNKNOWN',
    [string]$RegistryPath = "",
    [string]$OutputDir = "target/jmoa-runtime-preflight",
    [string]$MavenExecutable = "mvn",
    [string]$PluginCoordinates = "com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc1",
    [switch]$FailOnMavenError
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

if (-not (Test-Path -LiteralPath $InputDir -PathType Container)) { throw "Input directory does not exist: $InputDir" }
if (-not (Test-Path -LiteralPath $ArtifactPath -PathType Leaf)) { throw "Artifact does not exist: $ArtifactPath" }
if (-not [string]::IsNullOrWhiteSpace($CdsArchivePath) -and -not (Test-Path -LiteralPath $CdsArchivePath -PathType Leaf)) {
    throw "CDS archive does not exist: $CdsArchivePath"
}
New-JmoaDirectory -Path $OutputDir
if ([string]::IsNullOrWhiteSpace($RegistryPath)) {
    $RegistryPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/v2-n/v2n-runtime-protocol-registry.json'
}
if (-not (Test-Path -LiteralPath $RegistryPath -PathType Leaf)) { throw "Runtime protocol registry does not exist: $RegistryPath" }

$arguments = @(
    '-N', "${PluginCoordinates}:runtime-preflight", '-Djmoa.runtimePreflight.enabled=true',
    "-Djmoa.runtimePreflight.inputDir=$InputDir", "-Djmoa.runtimePreflight.outputDir=$OutputDir",
    "-Djmoa.runtimePreflight.registry=$RegistryPath", "-Djmoa.runtimePreflight.artifact=$ArtifactPath",
    "-Djmoa.runtimePreflight.service=$Service", "-Djmoa.runtimePreflight.launchMode=$LaunchMode",
    "-Djmoa.runtimePreflight.runtimePolicy=$RuntimePolicy", "-Djmoa.runtimePreflight.reducerEngine=$ReducerEngine",
    "-Djmoa.runtimePreflight.scope=$Scope"
)
if (-not [string]::IsNullOrWhiteSpace($CdsArchivePath)) { $arguments += "-Djmoa.runtimePreflight.cdsArchive=$CdsArchivePath" }
$result = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments $arguments
Write-JmoaText -Value $result.output -Path (Join-Path $OutputDir 'runtime-preflight-command.log')
if ($result.exitCode -ne 0 -and $FailOnMavenError) { exit $result.exitCode }
if ($result.exitCode -ne 0) { throw "Runtime preflight Maven goal failed. See $(Join-Path $OutputDir 'runtime-preflight-command.log')." }
Write-Host "Runtime preflight report written to $OutputDir"

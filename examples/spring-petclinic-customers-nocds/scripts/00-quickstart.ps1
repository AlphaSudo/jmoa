param(
    [Parameter(Mandatory)][string]$ProfilePath,
    [Parameter(Mandatory)][string]$AdmissionPath,
    [Parameter(Mandatory)][string]$SafeSamsPath,
    [Parameter(Mandatory)][string]$RuntimeJar,
    [string]$WorkDir = "./petclinic-work",
    [string]$Maven = "mvn",
    [string]$PluginCoordinates = "com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc1",
    [switch]$SkipSemanticSmoke
)

$ErrorActionPreference = "Stop"
$scripts = $PSScriptRoot
& (Join-Path $scripts "01-clone-petclinic.ps1") -WorkDir $WorkDir
$root = Join-Path $WorkDir "spring-petclinic-microservices"
& (Join-Path $scripts "02-build-baseline.ps1") -PetclinicRoot $root -Maven $Maven
& (Join-Path $scripts "03-build-jmoa-p2.ps1") -PetclinicRoot $root -ProfilePath $ProfilePath -AdmissionPath $AdmissionPath -SafeSamsPath $SafeSamsPath -RuntimeJar $RuntimeJar -PluginCoordinates $PluginCoordinates -Maven $Maven
& (Join-Path $scripts "09-reduce-bytecode.ps1") -PetclinicRoot $root -PluginCoordinates $PluginCoordinates -Maven $Maven
$service = Join-Path $root "spring-petclinic-customers-service"
$exploded = Join-Path $WorkDir "exploded-customers-v2"
& (Join-Path $scripts "04-materialize-exploded-boot.ps1") -CustomersServiceDir $service -ReducedLibDir (Join-Path $service "target/jmoa-reduced-libs") -RuntimeJar $RuntimeJar -OutputDir $exploded
if (-not $SkipSemanticSmoke) { & (Join-Path $scripts "10-semantic-smoke.ps1") -ExplodedDir $exploded -OutputDir (Join-Path $WorkDir "semantic-smoke") }
Write-Host "JMOA V2 public quickstart completed."

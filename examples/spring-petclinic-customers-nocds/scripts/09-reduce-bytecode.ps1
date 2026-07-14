param(
    [string]$PetclinicRoot = "./petclinic-work/spring-petclinic-microservices",
    [string]$PluginCoordinates = "com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc1",
    [string]$Maven = "mvn"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $PetclinicRoot).Path
$service = Join-Path $root "spring-petclinic-customers-service"
$input = Join-Path $service "target/jmoa-optimized-libs"
$output = Join-Path $service "target/jmoa-reduced-libs"
if (-not (Test-Path -LiteralPath $input -PathType Container)) { throw "Optimized dependency directory missing: $input" }

Push-Location $service
try {
    & $Maven -q "${PluginCoordinates}:reduce-bytecode" `
        "-Djmoa.reducer.enabled=true" `
        "-Djmoa.reducer.reportOnly=false" `
        "-Djmoa.reducer.optimize=true" `
        "-Djmoa.reducer.profile=release-low-footprint" `
        "-Djmoa.reducer.engine=raw" `
        "-Djmoa.reducer.stripLocalVariableTable=true" `
        "-Djmoa.reducer.stripLocalVariableTypeTable=true" `
        "-Djmoa.reducer.inputDir=$input" `
        "-Djmoa.reducer.outputDir=$output"
    if ($LASTEXITCODE -ne 0) { throw "JMOA raw reducer failed." }
} finally { Pop-Location }

Write-Host "Raw-reduced dependencies: $output"

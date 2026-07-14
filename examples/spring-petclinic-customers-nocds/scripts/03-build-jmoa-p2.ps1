param(
    [string]$PetclinicRoot = "./petclinic-work/spring-petclinic-microservices",
    [Parameter(Mandatory)][string]$ProfilePath,
    [Parameter(Mandatory)][string]$AdmissionPath,
    [Parameter(Mandatory)][string]$SafeSamsPath,
    [Parameter(Mandatory)][string]$RuntimeJar,
    [string]$PluginCoordinates = "com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2",
    [string]$Maven = "mvn"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $PetclinicRoot).Path
$service = Join-Path $root "spring-petclinic-customers-service"
$profile = (Resolve-Path -LiteralPath $ProfilePath).Path
$admission = (Resolve-Path -LiteralPath $AdmissionPath).Path
$safeSams = (Get-Content -LiteralPath (Resolve-Path -LiteralPath $SafeSamsPath).Path -Raw).Trim()
$runtime = (Resolve-Path -LiteralPath $RuntimeJar).Path

Push-Location $root
try {
    & $Maven -q -pl spring-petclinic-customers-service -am -DskipTests clean compile
    if ($LASTEXITCODE -ne 0) { throw "PetClinic compile failed." }
    Push-Location $service
    try {
        & $Maven -q "${PluginCoordinates}:deduplicate-lambdas" `
            "-Djmoa.mode=MODE_C" `
            "-Djmoa.profilePath=$profile" `
            "-Djmoa.enableObservedSiteAdmission=true" `
            "-Djmoa.observedAdmissionSitesFile=$admission" `
            "-Djmoa.additionalSafeSamInterfaces=$safeSams" `
            "-Djmoa.generateTier1Runtime=true" `
            "-Djmoa.failOnMissingRuntimeLibrary=true" `
            "-Djmoa.additionalClasspathJars=$runtime" `
            "-Djmoa.expandDependencies=true" `
            "-Djmoa.expandIncludes=spring" `
            "-Djmoa.frameworkFiltering=true" `
            "-Djmoa.allowSpringAotFrameworkSites=true" `
            "-Djmoa.allowExpandedDependencySites=true" `
            "-Djmoa.tier2AdapterConsolidation=PACKAGE_SAM" `
            "-Djmoa.hybridOverlayCoordinates=org.springframework:spring-core:7.0.2" `
            "-Djmoa.maxExpandedClasses=100000" `
            "-Djmoa.packageOptimizedDependencies=true" `
            "-Djmoa.reportOnly=false"
        if ($LASTEXITCODE -ne 0) { throw "JMOA full-P2 optimization failed." }
    } finally { Pop-Location }
    Push-Location $service
    try {
        & $Maven -q jar:jar "org.springframework.boot:spring-boot-maven-plugin:4.0.1:repackage" -DskipTests
    } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw "Optimized PetClinic package failed." }
} finally { Pop-Location }

$optimized = Join-Path $service "target/jmoa-optimized-libs"
if (-not (Test-Path -LiteralPath $optimized -PathType Container)) { throw "Optimized dependency directory missing: $optimized" }
Write-Host "Full-P2 artifact and optimized dependencies are ready under $service/target."

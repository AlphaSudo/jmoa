param(
    [string]$PetclinicRoot = "./petclinic-work/spring-petclinic-microservices",
    [string]$ProfilePath,
    [string]$Maven = "mvn"
)

$ErrorActionPreference = "Stop"
if (-not $ProfilePath) {
    throw "ProfilePath is required. Generate or provide a PetClinic customers-service training profile."
}

Push-Location $PetclinicRoot
try {
    & $Maven -pl spring-petclinic-customers-service -am `
        -DskipTests `
        -Djmoa.mode=MODE_C `
        -Djmoa.profilePath=$ProfilePath `
        -Djmoa.packageOptimizedDependencies=true `
        jmoa:optimize package
} finally {
    Pop-Location
}

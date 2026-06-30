param(
    [string]$PetclinicRoot = "./petclinic-work/spring-petclinic-microservices",
    [string]$Maven = "mvn"
)

$ErrorActionPreference = "Stop"
Push-Location $PetclinicRoot
try {
    & $Maven -pl spring-petclinic-customers-service -am -DskipTests package
} finally {
    Pop-Location
}

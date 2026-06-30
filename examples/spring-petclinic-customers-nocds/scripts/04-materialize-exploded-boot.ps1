param(
    [string]$CustomersServiceDir = "./petclinic-work/spring-petclinic-microservices/spring-petclinic-customers-service",
    [string]$OutputDir = "./petclinic-work/exploded-customers"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$jar = Get-ChildItem -Path (Join-Path $CustomersServiceDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc" } |
    Select-Object -First 1

if (-not $jar) {
    throw "No Spring Boot jar found under $CustomersServiceDir/target."
}

Push-Location $OutputDir
try {
    java -Djarmode=layertools -jar $jar.FullName extract
    Write-Host "Extracted Spring Boot layers into $OutputDir."
    Write-Host "TODO: replace dependency-layer jars with target/jmoa-optimized-libs entries and verify no shadowing."
} finally {
    Pop-Location
}

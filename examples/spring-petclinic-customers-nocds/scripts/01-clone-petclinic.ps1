param(
    [string]$WorkDir = "./petclinic-work",
    [string]$RepoUrl = "https://github.com/spring-petclinic/spring-petclinic-microservices.git"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
Push-Location $WorkDir
try {
    if (-not (Test-Path "spring-petclinic-microservices")) {
        git clone $RepoUrl spring-petclinic-microservices
    }
} finally {
    Pop-Location
}

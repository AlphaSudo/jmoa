param(
    [string]$WorkDir = "./petclinic-work",
    [string]$RepoUrl = "https://github.com/spring-petclinic/spring-petclinic-microservices.git",
    [string]$Revision = "305a1f13e4f961001d4e6cb50a9db51dc3fc5967"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
Push-Location $WorkDir
try {
    if (-not (Test-Path "spring-petclinic-microservices")) {
        git clone $RepoUrl spring-petclinic-microservices
    }
    Push-Location spring-petclinic-microservices
    try {
        git fetch --tags origin
        git checkout --detach $Revision
        if ($LASTEXITCODE -ne 0) { throw "Could not check out PetClinic revision $Revision" }
    } finally { Pop-Location }
} finally {
    Pop-Location
}

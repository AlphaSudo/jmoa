param(
    [string]$Repository = "https://github.com/AlphaSudo/jmoa.git",
    [string]$Revision = "main",
    [string]$WorkDir = "target/v2-clean-clone",
    [string]$MavenLocalRepository = "target/v2-clean-m2",
    [string]$Maven = "mvn",
    [string]$Version = "2.0.0-rc2",
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PublicCustomersProfile,
    [string]$PublicCustomersAdmission,
    [string]$AdditionalSafeSams,
    [switch]$RunPublicQuickstart
)

$ErrorActionPreference = "Stop"
if (Test-Path $WorkDir) { Remove-Item -LiteralPath $WorkDir -Recurse -Force }
if (Test-Path $MavenLocalRepository) { Remove-Item -LiteralPath $MavenLocalRepository -Recurse -Force }
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$isolatedMavenRepo = [IO.Path]::GetFullPath($MavenLocalRepository)
New-Item -ItemType Directory -Force -Path $isolatedMavenRepo | Out-Null
$clone = Join-Path $WorkDir "jmoa"
git clone $Repository $clone
if ($LASTEXITCODE -ne 0) { throw "Clean clone failed." }
$previousMavenOpts = $env:MAVEN_OPTS
$previousJavaHome = $env:JAVA_HOME
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)) {
    throw "A valid -JavaHome is required for clean-clone qualification."
}
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
$isolatedRepoOption = '-Dmaven.repo.local="' + $isolatedMavenRepo + '"'
$env:MAVEN_OPTS = ((@($previousMavenOpts, $isolatedRepoOption) | Where-Object { $_ }) -join " ").Trim()
Push-Location $clone
try {
    git checkout $Revision
    if ($LASTEXITCODE -ne 0) { throw "Could not checkout $Revision" }
    & $Maven -q clean test
    if ($LASTEXITCODE -ne 0) { throw "Clean-clone tests failed." }
    & .\scripts\check-v2-schema-compatibility.ps1
    & .\scripts\check-publication-safety.ps1
    & .\scripts\build-v2-release-artifacts.ps1 -Version $Version -Maven $Maven -PublicCustomersProfile $PublicCustomersProfile -PublicCustomersAdmission $PublicCustomersAdmission -AdditionalSafeSams $AdditionalSafeSams
    & .\scripts\install-v2-release-artifacts.ps1 -Version $Version -Maven $Maven -MavenRepoLocal $isolatedMavenRepo
    if ($RunPublicQuickstart) {
        if (-not $PublicCustomersProfile -or -not $PublicCustomersAdmission -or -not $AdditionalSafeSams) { throw "RunPublicQuickstart requires all public PetClinic reproduction assets." }
        $runtimeJar = Join-Path (Resolve-Path 'target/v2-release').Path "jmoa-runtime-lib-$Version.jar"
        & .\examples\spring-petclinic-customers-nocds\scripts\00-quickstart.ps1 -ProfilePath $PublicCustomersProfile -AdmissionPath $PublicCustomersAdmission -SafeSamsPath $AdditionalSafeSams -RuntimeJar $runtimeJar -Maven $Maven -PluginCoordinates "com.yourorg.jmoa:jmoa-maven-plugin:$Version"
    }
    [ordered]@{ metadataVersion = "v2-clean-clone-qualification-v2"; repository = $Repository; revision = (git rev-parse HEAD); javaHome = $env:JAVA_HOME; isolatedMavenRepository = $isolatedMavenRepo; tests = "PASSED"; schema = "PASSED"; publicationSafety = "PASSED"; releaseBundle = "PASSED"; publicQuickstart = if ($RunPublicQuickstart) { "PASSED" } else { "NOT_RUN" } } | ConvertTo-Json | Set-Content (Join-Path (Resolve-Path ..).Path "v2-clean-clone-qualification.json") -Encoding UTF8
} finally {
    Pop-Location
    $env:MAVEN_OPTS = $previousMavenOpts
    $env:JAVA_HOME = $previousJavaHome
}
Write-Host "Clean-clone qualification completed at $WorkDir."

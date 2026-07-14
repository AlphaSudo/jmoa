param(
    [string]$ReleaseDir = "target/v2-release",
    [string]$Version = "2.0.0-rc2",
    [string]$Maven = "mvn",
    [string]$MavenRepoLocal
)

$ErrorActionPreference = "Stop"
$assets = @(
    @{ artifactId = "jmoa-runtime-lib"; packaging = "jar" },
    @{ artifactId = "jmoa-maven-plugin"; packaging = "maven-plugin" }
)
foreach ($asset in $assets) {
    $jar = Join-Path $ReleaseDir "$($asset.artifactId)-$Version.jar"
    $pom = Join-Path $ReleaseDir "$($asset.artifactId)-$Version.pom"
    if (-not (Test-Path $jar -PathType Leaf) -or -not (Test-Path $pom -PathType Leaf)) { throw "Missing release POM/JAR for $($asset.artifactId)" }
    $mavenArguments = @('-q', 'install:install-file', "-Dfile=$jar", "-DpomFile=$pom", "-Dpackaging=$($asset.packaging)")
    if ($MavenRepoLocal) {
        $mavenArguments += "-Dmaven.repo.local=$([IO.Path]::GetFullPath($MavenRepoLocal))"
    }
    & $Maven @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Could not install $($asset.artifactId)" }
}
Write-Host "Installed JMOA $Version release assets into the requested Maven repository."

param(
    [string]$ReleaseDir = "target/v2-release",
    [string]$Version = "2.0.0-rc1",
    [string]$Maven = "mvn"
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
    & $Maven -q install:install-file "-Dfile=$jar" "-DpomFile=$pom" "-Dpackaging=$($asset.packaging)"
    if ($LASTEXITCODE -ne 0) { throw "Could not install $($asset.artifactId)" }
}
Write-Host "Installed JMOA $Version release assets into the local Maven repository."

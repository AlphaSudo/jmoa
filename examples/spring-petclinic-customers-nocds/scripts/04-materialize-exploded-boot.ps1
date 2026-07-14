param(
    [string]$CustomersServiceDir = "./petclinic-work/spring-petclinic-microservices/spring-petclinic-customers-service",
    [string]$ReducedLibDir = "./petclinic-work/spring-petclinic-microservices/spring-petclinic-customers-service/target/jmoa-reduced-libs",
    [Parameter(Mandatory)][string]$RuntimeJar,
    [string]$OutputDir = "./petclinic-work/exploded-customers-v2",
    [string]$Java = "java"
)

$ErrorActionPreference = "Stop"
$jar = Get-ChildItem -LiteralPath (Join-Path $CustomersServiceDir "target") -Filter "*.jar" -File |
    Where-Object { $_.Name -notmatch "sources|javadoc|original" } |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if (-not $jar) { throw "No Spring Boot jar found under $CustomersServiceDir/target." }
if (-not (Test-Path -LiteralPath $ReducedLibDir -PathType Container)) { throw "Reduced dependency directory missing: $ReducedLibDir" }
$runtime = (Resolve-Path -LiteralPath $RuntimeJar).Path

if (Test-Path -LiteralPath $OutputDir) { Remove-Item -LiteralPath $OutputDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
& $Java -Djarmode=tools -jar $jar.FullName extract --launcher --layers --destination $OutputDir
if ($LASTEXITCODE -ne 0) { throw "Spring Boot layered extraction failed." }

$dependencyRoot = Join-Path $OutputDir "dependencies/BOOT-INF/lib"
if (-not (Test-Path -LiteralPath $dependencyRoot -PathType Container)) {
    $dependencyRoot = Join-Path $OutputDir "BOOT-INF/lib"
}
if (-not (Test-Path -LiteralPath $dependencyRoot -PathType Container)) { throw "Extracted BOOT-INF/lib not found under $OutputDir" }

$replacements = @()
foreach ($reduced in Get-ChildItem -LiteralPath $ReducedLibDir -Filter "*.jar" -File) {
    $originalName = $reduced.Name -replace '-jmoa(?=\.jar$)', ''
    $target = Join-Path $dependencyRoot $originalName
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "No original dependency matches reduced JAR $($reduced.Name) (expected $originalName)." }
    $before = Get-FileHash -LiteralPath $target -Algorithm SHA256
    Copy-Item -LiteralPath $reduced.FullName -Destination $target -Force
    $after = Get-FileHash -LiteralPath $target -Algorithm SHA256
    $replacements += [ordered]@{ originalName = $originalName; reducedName = $reduced.Name; beforeSha256 = $before.Hash; afterSha256 = $after.Hash; bytes = (Get-Item $target).Length }
}

$runtimeTarget = Join-Path $dependencyRoot (Split-Path -Leaf $runtime)
Copy-Item -LiteralPath $runtime -Destination $runtimeTarget -Force
$runtimeMaterialization = [ordered]@{
    source = $runtime
    sourceSha256 = (Get-FileHash -LiteralPath $runtime -Algorithm SHA256).Hash
    target = $runtimeTarget
    targetSha256 = (Get-FileHash -LiteralPath $runtimeTarget -Algorithm SHA256).Hash
    bytes = (Get-Item -LiteralPath $runtimeTarget).Length
}
if ($runtimeMaterialization.sourceSha256 -ne $runtimeMaterialization.targetSha256) {
    throw "JMOA runtime library hash mismatch after materialization."
}

$manifest = [ordered]@{
    metadataVersion = "v2-public-materialization-v2"
    bootJar = $jar.FullName
    bootJarSha256 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash
    outputDir = [IO.Path]::GetFullPath($OutputDir)
    dependencyRoot = [IO.Path]::GetFullPath($dependencyRoot)
    runtimeLibrary = $runtimeMaterialization
    replacedJars = $replacements.Count
    replacements = $replacements
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDir "jmoa-materialization-manifest.json") -Encoding UTF8
if ($replacements.Count -eq 0) { throw "Materialization replaced zero dependency JARs." }
Write-Host "Materialized $($replacements.Count) reduced dependency JARs into $OutputDir."

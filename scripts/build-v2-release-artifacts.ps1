param(
    [string]$Version = "2.0.0-rc2",
    [string]$OutputDir = "target/v2-release",
    [string]$Maven = "mvn",
    [string]$PublicCustomersProfile,
    [string]$PublicCustomersAdmission,
    [string]$AdditionalSafeSams,
    [string]$PublicEvidenceArchive
)

$ErrorActionPreference = "Stop"
$repo = Split-Path $PSScriptRoot -Parent
$output = [IO.Path]::GetFullPath((Join-Path $repo $OutputDir))

Push-Location $repo
try {
    foreach ($moduleTarget in @('jmoa-runtime-lib/target', 'jmoa-maven-plugin/target')) {
        $resolvedTarget = [IO.Path]::GetFullPath((Join-Path $repo $moduleTarget))
        if (-not $resolvedTarget.StartsWith(([IO.Path]::GetFullPath($repo) + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to clean release module outside repository: $resolvedTarget"
        }
        if (Test-Path -LiteralPath $resolvedTarget) {
            Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
        }
    }
    & $Maven -q -pl jmoa-runtime-lib,jmoa-maven-plugin -am package source:jar-no-fork
    if ($LASTEXITCODE -ne 0) { throw "Maven release build failed." }
    New-Item -ItemType Directory -Force -Path $output | Out-Null

    $assets = @(
        @{ source = "jmoa-maven-plugin/target/jmoa-maven-plugin-$Version.jar"; name = "jmoa-maven-plugin-$Version.jar" },
        @{ source = "jmoa-maven-plugin/target/jmoa-maven-plugin-$Version-sources.jar"; name = "jmoa-maven-plugin-$Version-sources.jar" },
        @{ source = "jmoa-maven-plugin/pom.xml"; name = "jmoa-maven-plugin-$Version.pom" },
        @{ source = "jmoa-runtime-lib/target/jmoa-runtime-lib-$Version.jar"; name = "jmoa-runtime-lib-$Version.jar" },
        @{ source = "jmoa-runtime-lib/target/jmoa-runtime-lib-$Version-sources.jar"; name = "jmoa-runtime-lib-$Version-sources.jar" },
        @{ source = "jmoa-runtime-lib/pom.xml"; name = "jmoa-runtime-lib-$Version.pom" }
    )
    foreach ($asset in $assets) {
        if (-not (Test-Path -LiteralPath $asset.source -PathType Leaf)) { throw "Missing release asset: $($asset.source)" }
        Copy-Item -LiteralPath $asset.source -Destination (Join-Path $output $asset.name) -Force
    }
    if ($PublicCustomersProfile) {
        if (-not (Test-Path -LiteralPath $PublicCustomersProfile -PathType Leaf)) { throw "Profile not found: $PublicCustomersProfile" }
        Copy-Item -LiteralPath $PublicCustomersProfile -Destination (Join-Path $output "petclinic-customers-profile.json") -Force
    }
    if ($PublicCustomersAdmission) {
        if (-not (Test-Path -LiteralPath $PublicCustomersAdmission -PathType Leaf)) { throw "Admission file not found: $PublicCustomersAdmission" }
        Copy-Item -LiteralPath $PublicCustomersAdmission -Destination (Join-Path $output "petclinic-customers-admission.txt") -Force
    }
    if ($AdditionalSafeSams) {
        if (-not (Test-Path -LiteralPath $AdditionalSafeSams -PathType Leaf)) { throw "SAM allowlist not found: $AdditionalSafeSams" }
        Copy-Item -LiteralPath $AdditionalSafeSams -Destination (Join-Path $output "jmoa-additional-safe-sams.txt") -Force
    }
    if ($PublicEvidenceArchive) {
        if (-not (Test-Path -LiteralPath $PublicEvidenceArchive -PathType Leaf)) { throw "Public evidence archive not found: $PublicEvidenceArchive" }
        Copy-Item -LiteralPath $PublicEvidenceArchive -Destination (Join-Path $output "jmoa-v2-public-evidence-$Version.zip") -Force
    }

    $files = Get-ChildItem -LiteralPath $output -File | Where-Object Name -NotIn @("SHA256SUMS.txt", "jmoa-release-manifest.json") | Sort-Object Name
    $records = @($files | ForEach-Object {
        [ordered]@{ name = $_.Name; bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    })
    $records | ForEach-Object { "$($_.sha256)  $($_.name)" } | Set-Content -LiteralPath (Join-Path $output "SHA256SUMS.txt") -Encoding ASCII
    [ordered]@{
        metadataVersion = "v2-release-manifest-v1"
        version = $Version
        distribution = "GITHUB_RELEASE"
        gitRevision = (git rev-parse HEAD)
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        assets = $records
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output "jmoa-release-manifest.json") -Encoding UTF8
} finally {
    Pop-Location
}

Write-Host "Release assets: $output"
